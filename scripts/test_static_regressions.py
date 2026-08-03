from pathlib import Path
import hashlib
import json
import re
import struct
import unittest
import zlib

ROOT = Path(__file__).resolve().parents[1]
FUSION_PACK = ROOT / "src/main/resources/resourcepacks/fusion_connected_casing"


class StaticRegressionTests(unittest.TestCase):
    def read_required(self, relative_path: str) -> str:
        path = ROOT / relative_path
        self.assertTrue(path.exists(), f"missing {relative_path}")
        return path.read_text()

    def read_compat_module(self, module_id: str) -> str:
        return self.read_required(f"src/compat/{module_id}/compat-module.json")

    def assert_descriptor_driven_fixture(
        self,
        build: str,
        module_id: str,
        fixture: str,
        expected_tests: int,
    ):
        descriptor = json.loads(self.read_compat_module(module_id))
        self.assertEqual(fixture, descriptor["fixture"])
        self.assertEqual(expected_tests, descriptor["expectedTests"])
        self.assertIn("def fixture = sourceSets.maybeCreate(spec.fixture)", build)
        self.assertIn(
            "fixture.runtimeClasspath += fixture.output + "
            "sourceSets.main.runtimeClasspath",
            build,
        )
        self.assertIn("tasks.named(spec.runTask).configure", build)
        self.assertIn(
            'text.contains("All ${expectedTests} required tests passed")',
            build,
        )
        self.assertIn("expectedSelfTestSummary", build)
        self.assertIn("text.contains('TESTS FAILED!')", build)

    def png_dimensions(self, path: Path) -> tuple[int, int]:
        with path.open("rb") as texture_file:
            header = texture_file.read(24)
        self.assertEqual(b"\x89PNG\r\n\x1a\n", header[:8], f"not a PNG: {path}")
        self.assertEqual(b"IHDR", header[12:16], f"missing PNG IHDR: {path}")
        return int.from_bytes(header[16:20], "big"), int.from_bytes(header[20:24], "big")

    def rgba_png_pixels(self, path: Path) -> tuple[int, int, list[tuple[int, int, int, int]]]:
        payload = path.read_bytes()
        self.assertEqual(b"\x89PNG\r\n\x1a\n", payload[:8], f"not a PNG: {path}")
        offset = 8
        width = height = None
        compressed = bytearray()
        while offset < len(payload):
            length = struct.unpack(">I", payload[offset:offset + 4])[0]
            chunk_type = payload[offset + 4:offset + 8]
            chunk = payload[offset + 8:offset + 8 + length]
            offset += length + 12
            if chunk_type == b"IHDR":
                width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(
                    ">IIBBBBB", chunk
                )
                self.assertEqual((8, 6, 0, 0, 0),
                                 (bit_depth, color_type, compression, filtering, interlace),
                                 f"expected non-interlaced RGBA8 PNG: {path}")
            elif chunk_type == b"IDAT":
                compressed.extend(chunk)
            elif chunk_type == b"IEND":
                break
        self.assertIsNotNone(width, f"missing PNG dimensions: {path}")
        raw = zlib.decompress(bytes(compressed))
        stride = width * 4
        previous = bytearray(stride)
        pixels = []
        cursor = 0
        for _ in range(height):
            filter_type = raw[cursor]
            cursor += 1
            scanline = bytearray(raw[cursor:cursor + stride])
            cursor += stride
            for index in range(stride):
                left = scanline[index - 4] if index >= 4 else 0
                up = previous[index]
                upper_left = previous[index - 4] if index >= 4 else 0
                if filter_type == 1:
                    scanline[index] = (scanline[index] + left) & 0xFF
                elif filter_type == 2:
                    scanline[index] = (scanline[index] + up) & 0xFF
                elif filter_type == 3:
                    scanline[index] = (scanline[index] + ((left + up) // 2)) & 0xFF
                elif filter_type == 4:
                    prediction = left + up - upper_left
                    distances = (
                        abs(prediction - left),
                        abs(prediction - up),
                        abs(prediction - upper_left),
                    )
                    predictor = (left, up, upper_left)[distances.index(min(distances))]
                    scanline[index] = (scanline[index] + predictor) & 0xFF
                elif filter_type != 0:
                    self.fail(f"unsupported PNG filter {filter_type}: {path}")
            pixels.extend(tuple(scanline[index:index + 4]) for index in range(0, stride, 4))
            previous = scanline
        return width, height, pixels

    def expected_texture_family(self) -> dict[str, str]:
        return {
            "auto_storage:block/storage_core": "core_rune_crystal",
            "auto_storage:block/storage_terminal": "storage_item_grid",
            "auto_storage:block/crafting_terminal": "crafting_grid_mark",
            **{
                f"auto_storage:block/storage_unit_t{tier}": f"storage_cell_tier_{tier}"
                for tier in range(1, 7)
            },
            "auto_storage:block/creative_storage_unit": "creative_infinity_cell",
            "auto_storage:block/import_bus_top": "import_casing_top",
            "auto_storage:block/import_bus_side": "import_casing_side",
            "auto_storage:block/import_bus_front": "import_inward_arrow",
            "auto_storage:block/export_bus_top": "export_casing_top",
            "auto_storage:block/export_bus_side": "export_casing_side",
            "auto_storage:block/export_bus_front": "export_outward_arrow",
            "auto_storage:item/remote_terminal": "remote_display",
        }

    def expected_connected_texture_family(self) -> set[str]:
        return {
            "auto_storage:block/storage_core_connected",
            *{
                f"auto_storage:block/storage_unit_t{tier}_connected"
                for tier in range(1, 7)
            },
            "auto_storage:block/creative_storage_unit_connected",
            "auto_storage:block/storage_terminal_connected",
            "auto_storage:block/crafting_terminal_connected",
            "auto_storage:block/import_bus_top_connected",
            "auto_storage:block/import_bus_side_connected",
            "auto_storage:block/export_bus_top_connected",
            "auto_storage:block/export_bus_side_connected",
        }

    def java_block(self, text: str, declaration: str, description: str) -> str:
        match = re.search(declaration, text, re.MULTILINE)
        if match is None:
            self.fail(f"missing {description}")
        opening = text.find("{", match.end())
        if opening < 0:
            self.fail(f"missing body for {description}")
        depth = 0
        for index in range(opening, len(text)):
            if text[index] == "{":
                depth += 1
            elif text[index] == "}":
                depth -= 1
                if depth == 0:
                    return text[opening + 1:index]
        self.fail(f"unterminated body for {description}")

    def test_terminal_preferences_use_rs2_style_client_global_persistence(self):
        mod = self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        )
        config = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalClientPreferences.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        crafting_screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )

        self.assertRegex(mod, r"AutoStorage\(IEventBus modEventBus,\s*ModContainer modContainer\)")
        self.assertIn(
            "modContainer.registerConfig(ModConfig.Type.CLIENT, TerminalClientPreferences.SPEC)",
            mod,
        )
        self.assertIn("TerminalClientPreferences.load()", screen)
        self.assertIn("TerminalClientPreferences.save(preferences)", screen)
        self.assertIn("TerminalPreferenceSession", screen)
        self.assertIn(
            "preferenceSession.presentation(menu.getTerminalPreferences())", screen
        )
        self.assertIn("displayedPreferences().page()", crafting_screen)
        self.assertIn("displayedPreferences().transformTarget()", crafting_screen)
        self.assertIn("preferences.usePlayerInventory()", crafting_screen)
        self.assertIn("preferences.outputDestination()", crafting_screen)
        for stale_read in (
            "menu.getPage()",
            "menu.getSelectedFuelTarget()",
            "menu.isUsePlayerInventory()",
            "menu.getOutputDestination()",
        ):
            self.assertNotIn(stale_read, crafting_screen)
        for stale_read in (
            "menu.getSortMode()",
            "menu.getSortOrder()",
            "menu.getSearchMode()",
            "menu.getResourceView()",
        ):
            self.assertNotIn(stale_read, screen)
        self.assertIn("SPEC.save()", config)
        self.assertNotIn("search query", config.lower())

    def test_terminal_search_uses_raw_prefixes_and_public_emi_synchronization(self):
        mode = self.read_required(
            "src/main/java/com/swear/autostorage/SearchMode.java"
        )
        config = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalClientPreferences.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        synchronizer = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalSearchSynchronizer.java"
        )
        emi = self.read_required(
            "src/main/java/com/swear/autostorage/compat/EmiTerminalSearchSynchronizer.java"
        )

        self.assertIn("OFF", mode)
        self.assertIn("EMI_TWO_WAY", mode)
        self.assertIn("synchronizesToEmi", mode)
        self.assertIn("synchronizesFromEmi", mode)
        self.assertIn('case "AUTO", "NORMAL", "TAG", "MOD" -> OFF', mode)
        self.assertIn("ConfigValue<String> searchMode", config)
        self.assertIn("BooleanValue searchBoxAutoSelected", config)
        self.assertIn("searchBoxAutoSelected()", config)
        self.assertIn("saveSearchBoxAutoSelected", config)
        self.assertIn("SearchMode.fromConfigValue", config)
        self.assertIn("autoFocusBtn", screen)
        self.assertIn("searchBox.setCanLoseFocus", screen)
        self.assertIn("TerminalClientPreferences.searchBoxAutoSelected()", screen)

        send_search = self.java_block(
            screen,
            r"\bprivate\s+void\s+sendSearchPacket\s*\(",
            "raw terminal search sender",
        )
        self.assertIn("searchBox.getValue()", send_search)
        self.assertNotIn(".apply(", send_search)
        self.assertIn("TerminalSearchSynchronizer", screen)
        self.assertIn("synchronizeFromTerminal", screen)
        self.assertIn("textToSynchronizeToTerminal", screen)

        self.assertNotIn("import dev.emi.", synchronizer)
        self.assertIn("dev.emi.emi.api.EmiApi", emi)
        self.assertIn("EmiApi.setSearchText", emi)
        self.assertIn("EmiApi.getSearchText", emi)
        self.assertNotIn("dev.emi.emi.screen", emi)

    def test_all_terminal_search_boxes_share_input_and_query_pipeline(self):
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )

        self.assertIn("searchBox.setResponder(this::scheduleSearch)", storage)
        self.assertIn("protected EditBox activeSearchBox()", storage)
        self.assertIn("active.isFocused()", storage)
        self.assertIn("return keyPressedOutsideSearch(", storage)
        self.assertIn("protected EditBox activeSearchBox()", crafting)
        for duplicate in (
            "transformTargetSearchBox.keyPressed(",
            "fuelSearchBox.keyPressed(",
            "transformTargetSearchBox.charTyped(",
            "fuelSearchBox.charTyped(",
        ):
            self.assertNotIn(duplicate, crafting)

        transform_filter = self.java_block(
            crafting,
            r"\bprivate\s+void\s+refreshTransformTargets\s*\(",
            "CraftingTerminalScreen.refreshTransformTargets",
        )
        self.assertIn("TerminalSearchQuery.compile", transform_filter)
        self.assertIn("query.matches(option.searchEntry())", transform_filter)

    def test_terminal_search_compiles_once_uses_core_metadata_cache_and_prefilters_craftable(self):
        query = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalSearchQuery.java"
        )
        core = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )

        self.assertIn("static TerminalSearchQuery compile", query)
        self.assertNotIn('split("\\\\s+")', core)
        self.assertIn("private final Map<ItemKey, IndexedItem> itemIndex", core)
        display = self.java_block(
            core,
            r"\bpublic\s+List<ItemStack>\s+getDisplayStacks\s*\(\s*String\s+filter\s*\)",
            "StorageCoreBlockEntity.getDisplayStacks",
        )
        self.assertEqual(1, display.count("TerminalSearchQuery.compile"))
        self.assertIn("query.matches(item.search())", display)

        craftable = self.java_block(
            crafting,
            r"\bprivate\s+CraftableBuildResult\s+buildCraftableDisplayStacks\s*\(",
            "CraftingTerminalMenu.buildCraftableDisplayStacks",
        )
        self.assertEqual(1, craftable.count("TerminalSearchQuery.compile"))
        self.assertLess(
            craftable.index("matchesCraftableFilter"),
            craftable.index("computeCraftableStatus"),
        )
        self.assertIn("SEARCH_DEBOUNCE_TICKS = 2", screen)

    def test_terminal_preference_wire_change_bumps_network_protocol(self):
        mod = self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        )

        self.assertIn('event.registrar(MODID).versioned("1.5")', mod)

    def test_emi_public_widget_holder_matches_emi_unbounded_add_contract(self):
        renderer = self.read_required(
            "src/main/java/com/swear/autostorage/compat/EmiRecipeDiagramRenderer.java"
        )
        holder = self.java_block(
            renderer,
            r"\bprivate\s+static\s+final\s+class\s+PublicWidgetHolder\b",
            "EmiRecipeDiagramRenderer.PublicWidgetHolder",
        )
        add = self.java_block(
            holder,
            r"\bpublic\s+<T\s+extends\s+Widget>\s+T\s+add\s*\(",
            "PublicWidgetHolder.add",
        )
        self.assertIn("widgets.add(widget)", add)
        self.assertNotIn("bounds.x()", add)
        self.assertNotIn("bounds.y()", add)
        self.assertNotIn("right > width", add)
        self.assertNotIn("bottom > height", add)

    def test_utility_pages_reuse_storage_sort_controls_and_comparator(self):
        storage_screen = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        crafting_screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        crafting_menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )

        self.assertIn("protected void setSortControlsVisible", storage_screen)
        update = self.java_block(
            crafting_screen,
            r"\bprivate\s+void\s+updatePageWidgets\s*\(",
            "CraftingTerminalScreen.updatePageWidgets",
        )
        self.assertIn("setSortControlsVisible(true)", update)
        self.assertIn("TerminalEntryComparator.forMode", crafting_screen)
        self.assertIn("TerminalEntryComparator.forMode", crafting_menu)

    def test_chemical_internal_carrier_preserves_identity_without_becoming_a_station(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        module = self.read_required(
            "src/compat/mekanism/java/com/swear/autostorage/compat/"
            "mekanism/MekanismCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/mekanism/java/com/swear/autostorage/"
            "MekanismRecipeCompat.java"
        )

        self.assertIn(
            "KINDS.register(StorageResourceKindApi.CHEMICAL_KIND.getPath()",
            module,
        )
        self.assertIn("StorageResourceKind.variantless(", module)
        self.assertIn('"basic_chemical_tank"', module)
        self.assertNotIn("Items.BREWING_STAND", module)
        self.assertNotIn("case GAS -> Items.BREWING_STAND", screen)
        self.assertNotIn("new ItemStack(Items.BREWING_STAND)", compat)

    def nested_java_classes(self, text: str) -> list[tuple[str, str]]:
        classes = []
        declaration = re.compile(
            r"^[ \t]{4,}(?:(?:private|protected|public)\s+)?"
            r"(?:static\s+)?(?:final\s+)?class\s+([A-Za-z_]\w*)\b",
            re.MULTILINE,
        )
        for match in declaration.finditer(text):
            classes.append((
                match.group(1),
                self.java_block(text[match.start():], declaration.pattern, match.group(1)),
            ))
        return classes

    def java_int_constant(self, text: str, name: str) -> int:
        seen = set()
        current = name
        while current not in seen:
            seen.add(current)
            match = re.search(
                rf"\b{re.escape(current)}\s*=\s*(\d+|[A-Z][A-Z0-9_]*)\s*;",
                text,
            )
            if match is None:
                self.fail(f"missing integer constant {current}")
            value = match.group(1)
            if value.isdigit():
                return int(value)
            current = value
        self.fail(f"cyclic integer constant starting at {name}")

    def test_every_gametest_class_is_registered_with_neoforge(self):
        java_root = ROOT / "src/main/java"
        unregistered = []
        for path in sorted(java_root.rglob("*.java")):
            text = path.read_text()
            if "@GameTest(" in text and "@GameTestHolder(" not in text:
                unregistered.append(str(path.relative_to(ROOT)))
        self.assertEqual(
            [],
            unregistered,
            "GameTest methods compile but never execute without @GameTestHolder",
        )

    def test_terminal_layout_has_one_profile_driven_entrypoint(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        profile = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalProfile.java"
        )
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )

        entrypoints = re.findall(
            r"^[ \t]{4}(?:(?:public|protected)\s+)?static\s+Geometry\s+"
            r"([A-Za-z_]\w*)\s*\(([^)]*)\)",
            layout,
            re.MULTILINE,
        )
        self.assertEqual(
            1,
            len(entrypoints),
            f"TerminalLayout must expose one non-private Geometry entrypoint, found {[name for name, _ in entrypoints]}",
        )
        entrypoint, parameters = entrypoints[0]
        self.assertIn("TerminalProfile", parameters)
        self.assertIsNone(
            re.search(r"\bstatic\s+Geometry\s+(?:storage|crafting)\s*\(", layout),
            "TerminalLayout.storage()/crafting() split entrypoints must be removed",
        )
        self.assertTrue(
            f"TerminalLayout.{entrypoint}(" in storage + crafting,
            f"terminal screens must use TerminalLayout.{entrypoint}()",
        )
        self.assertIn("static final TerminalProfile STORAGE", profile)
        self.assertIn("static final TerminalProfile CRAFTING", profile)
        self.assertTrue(
            "TerminalProfile.STORAGE" in storage,
            "StorageTerminalScreen must select the reduced STORAGE profile",
        )
        self.assertTrue(
            "TerminalProfile.CRAFTING" in crafting,
            "CraftingTerminalScreen must select the CRAFTING profile",
        )

    def test_shared_shell_alone_creates_common_terminal_controls(self):
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )

        self.assertFalse(
            "setViewButtonsVisible(false)" in crafting,
            "CraftingTerminalScreen must not hide inherited common controls after super.init()",
        )
        for duplicate in ["sortOrderRailBtn", "sortModeRailBtn", "searchModeRailBtn"]:
            self.assertFalse(
                duplicate in crafting,
                f"CraftingTerminalScreen must not recreate inherited {duplicate}",
            )
        for control in ["sortOrderBtn", "sortModeBtn", "searchModeBtn"]:
            self.assertIn(f"{control} = addCycleButton(", storage)
        self.assertIn("resourceViewBtn = addItemCycleButton(", storage)
        self.assertIn("NEXT_RESOURCE_VIEW_BUTTON", storage)
        self.assertNotIn("NEXT_RESOURCE_VIEW_BUTTON", crafting)
        self.assertIn("isResourceViewControlActive()", crafting)

        emi = self.read_required(
            "src/main/java/com/swear/autostorage/compat/AutoStorageEmiPlugin.java"
        )
        self.assertIn(
            "!TerminalResourceDisplay.isTyped(slot.getItem())",
            emi,
            "typed resource representatives must not become EMI item inputs",
        )
        for button_id in [11, 12, 13]:
            self.assertIsNone(
                re.search(rf"clickMenuButton\(\s*{button_id}\s*\)", crafting),
                f"CraftingTerminalScreen must not recreate common button id {button_id}",
            )
        for action in [
            "SORT_ORDER_BUTTON",
            "NEXT_SORT_MODE_BUTTON",
            "PREVIOUS_SORT_MODE_BUTTON",
            "NEXT_SEARCH_MODE_BUTTON",
            "PREVIOUS_SEARCH_MODE_BUTTON",
        ]:
            self.assertNotIn(action, crafting)

    def test_resource_view_and_player_inventory_controls_have_distinct_item_icons(self):
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )

        resource_view = self.java_block(
            storage,
            r"\bprivate\s+ItemStack\s+resourceViewIcon\s*\(",
            "StorageTerminalScreen.resourceViewIcon",
        )
        profile_controls = self.java_block(
            crafting,
            r"\bprotected\s+void\s+addTerminalProfileControls\s*\(",
            "CraftingTerminalScreen.addTerminalProfileControls",
        )
        resource_item_icon = re.search(
            r"case\s+ITEM\s*->\s*Items\.([A-Z0-9_]+)\.getDefaultInstance\(\)",
            resource_view,
        )
        player_inventory_icon = re.search(
            r"playerInventoryRailBtn\s*=\s*addItemCycleButton\(\s*"
            r"Items\.([A-Z0-9_]+)\.getDefaultInstance\(\)",
            profile_controls,
        )
        self.assertIsNotNone(resource_item_icon)
        self.assertIsNotNone(player_inventory_icon)
        self.assertNotEqual(
            resource_item_icon.group(1),
            player_inventory_icon.group(1),
            "Items resource view must not be visually identical to Use Player Inventory",
        )

    def test_terminal_controls_use_18px_hitboxes_and_16px_icon_canvas(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        self.assertEqual(18, self.java_int_constant(layout, "CONTROL_SIZE"))
        self.assertEqual(18, self.java_int_constant(layout, "RAIL_BUTTON_SIZE"))
        self.assertEqual(16, self.java_int_constant(layout, "ICON_CANVAS_SIZE"))

        rail_buttons = self.java_block(
            layout,
            r"\bprivate\s+static\s+List<Rect>\s+railButtons\s*\(",
            "TerminalLayout.railButtons",
        )
        self.assertRegex(
            rail_buttons,
            r"new\s+Rect\([\s\S]*?RAIL_BUTTON_SIZE\s*,\s*RAIL_BUTTON_SIZE\s*\)",
        )

    def test_terminal_rail_icons_are_not_text_glyphs_or_procedural_shapes(self):
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )

        screens = storage + crafting
        for old_glyph in ["⌕", "#", "@", "≡", "MOD", "ID", "↓", "↑"]:
            self.assertFalse(
                f'Component.literal("{old_glyph}")' in screens,
                f"shared rail must not use the {old_glyph!r} text glyph as an icon",
            )
        self.assertFalse(
            "drawRailIcon(" in screens,
            "rail icons must use shared texture/item rendering, not procedural drawRailIcon shapes",
        )
        self.assertIsNone(
            re.search(r"\bclass\s+TerminalIconButton\b", crafting),
            "CraftingTerminalScreen must use the shared shell icon control",
        )
        self.assertIn("TERMINAL_CONTROLS_TEXTURE", storage)
        self.assertRegex(
            storage,
            r"graphics\.blit\(\s*TERMINAL_CONTROLS_TEXTURE",
        )

        icon_controls = dict(self.nested_java_classes(storage))
        self.assertIn("TerminalIconButton", icon_controls)
        icon_control = icon_controls["TerminalIconButton"]
        self.assertIn("renderTerminalIcon(", icon_control)
        self.assertIn("blitControlIcon(", icon_control)
        self.assertNotIn("graphics.fill(", icon_control)

    def test_network_amount_renderer_uses_one_screen_wide_scale(self):
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        renderer = self.java_block(
            storage,
            r"\b(?:private|protected)\s+void\s+renderNetworkAmount\s*\(",
            "StorageTerminalScreen.renderNetworkAmount",
        )

        self.assertNotRegex(renderer, r"\b(?:large|small)\b")
        self.assertNotRegex(renderer, r"font\.width\(\s*text\s*\)\s*(?:<=|>=|<|>)")
        scale = re.search(
            r"graphics\.pose\(\)\.scale\(\s*([A-Za-z_][\w.]*)\s*,\s*\1\s*,\s*1(?:\.0)?F\s*\)",
            renderer,
        )
        self.assertIsNotNone(
            scale,
            "network amounts must always use one named screen-wide scale",
        )
        scale_name = scale.group(1).rsplit(".", 1)[-1]
        self.assertIsNone(
            re.search(rf"\b(?:float|double)\s+{re.escape(scale_name)}\b", renderer),
            f"{scale_name} must not be recomputed inside the per-value renderer",
        )
        self.assertGreaterEqual(
            (storage + layout).count(scale_name),
            2,
            f"{scale_name} must be declared outside the per-value renderer",
        )

    def test_shared_cycle_input_maps_click_and_wheel_directions(self):
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        direction = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalCycleDirection.java"
        )

        self.assertRegex(direction, r"case\s+0\s*->\s*NEXT\s*;")
        self.assertRegex(direction, r"case\s+1\s*->\s*PREVIOUS\s*;")
        self.assertRegex(
            direction,
            r"delta\s*<\s*0(?:\.0)?\s*\?\s*NEXT\s*:\s*PREVIOUS",
        )
        cycle_control = self.java_block(
            storage,
            r"\bclass\s+TerminalCycleButton\b",
            "StorageTerminalScreen.TerminalCycleButton",
        )
        self.assertRegex(cycle_control, r"button\s*==\s*0\s*\|\|\s*button\s*==\s*1")
        self.assertIn("TerminalCycleDirection.fromMouseButton(button)", cycle_control)
        self.assertIn("TerminalCycleDirection.fromScroll(scrollY)", cycle_control)
        self.assertRegex(
            cycle_control,
            r"(?:isMouseOver|clicked)\(\s*mouseX\s*,\s*mouseY\s*\)",
        )
        self.assertFalse(
            "import net.minecraft.client.gui.components.CycleButton;" in crafting,
            "CraftingTerminalScreen must use the shared cycle input instead of vanilla CycleButton",
        )
        self.assertFalse(
            "CycleButton<" in crafting,
            "CraftingTerminalScreen must not retain a vanilla CycleButton field",
        )
        self.assertNotIn("fuelTargetSelector", crafting)
        self.assertRegex(crafting, r"\bTerminalCycleButton\s+outputDestinationRailBtn\b")

    def test_terminal_output_destination_is_distinct_from_emi_destination(self):
        destination = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalOutputDestination.java"
        )
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )

        self.assertRegex(destination, r"enum\s+TerminalOutputDestination\s*\{")
        self.assertRegex(destination, r"\bPLAYER\s*,")
        self.assertRegex(destination, r"\bSTORAGE\b")
        self.assertIn(
            "private TerminalOutputDestination outputDestination = TerminalOutputDestination.PLAYER;",
            menu,
        )
        self.assertIn("from(CraftingDestination destination)", menu)
        self.assertIn("from(TerminalOutputDestination destination)", menu)
        self.assertIn("DeliveryTarget.from(destination)", menu)
        self.assertIn("DeliveryTarget.from(outputDestination)", menu)

    def test_output_destination_is_a_server_synced_item_page_control(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        profile = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalProfile.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )

        self.assertIn("static final int OUTPUT_DESTINATION_BUTTON", menu)
        self.assertIn("case 7 -> outputDestination.ordinal();", menu)
        self.assertIn("case 7 -> outputDestination = TerminalOutputDestination.byId(value);", menu)
        self.assertIn(
            "buttonId == OUTPUT_DESTINATION_BUTTON",
            menu,
            "the server menu must own the output-destination transition",
        )
        self.assertIn("OUTPUT_DESTINATION", profile)
        self.assertIn("int outputDestinationIndex()", profile)
        self.assertIn("playerInventorySourceIndex() + 1", profile)
        self.assertIn("List.of(PAGE_CONTROL_COUNT, VIEW_CONTROL_COUNT, 2)", profile)
        self.assertRegex(screen, r"\bTerminalCycleButton\s+outputDestinationRailBtn\b")
        self.assertIn("AutoStorage.STORAGE_CORE_ITEM.get().getDefaultInstance()", screen)
        self.assertIn("CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON", screen)
        self.assertIn("setWidgetVisible(outputDestinationRailBtn, itemPage);", screen)

    def test_output_destination_tooltips_name_current_value_in_both_languages(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        en_us = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/en_us.json")
        )
        zh_tw = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/zh_tw.json")
        )

        self.assertIn("gui.auto_storage.output_destination", screen)
        self.assertIn("gui.auto_storage.output_destination.player", screen)
        self.assertIn("gui.auto_storage.output_destination.storage", screen)
        expected = {
            "gui.auto_storage.output_destination",
            "gui.auto_storage.output_destination.player",
            "gui.auto_storage.output_destination.storage",
        }
        self.assertTrue(expected.issubset(en_us))
        self.assertTrue(expected.issubset(zh_tw))

    def test_transform_and_stations_have_no_consumables_station_category(self):
        lang = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/en_us.json")
        )
        self.assertEqual("Transform", lang["gui.auto_storage.page_transform"])
        self.assertEqual("Stations", lang["gui.auto_storage.page_stations"])
        self.assertEqual("Processing Stations", lang["gui.auto_storage.fuel_group.timed_stations"])
        self.assertEqual("Instant Stations", lang["gui.auto_storage.fuel_group.instant_stations"])
        self.assertEqual("No transformations", lang["gui.auto_storage.no_transformations"])
        self.assertEqual("Processing", lang["gui.auto_storage.resource_view.station_work"])
        self.assertIn("gui.auto_storage.transform_search", lang)
        self.assertNotIn("gui.auto_storage.transform_source_direct", lang)
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        table = self.read_required(
            "src/main/java/com/swear/autostorage/MachineEnergyTable.java"
        )
        self.assertNotIn("Category.CONSUMABLE", screen)
        self.assertNotIn("CONSUMABLE", table)
        self.assertIn("MachineCategory.PROCESS", screen)
        self.assertIn("MachineCategory.INSTANT", screen)

    def test_emi_uses_terminal_display_slot_contract_without_54_slot_hardcode(self):
        text = self.read_required("src/main/java/com/swear/autostorage/compat/AutoStorageEmiPlugin.java")
        self.assertNotIn("DISPLAY_SLOTS = 54", text)
        self.assertIn("StorageTerminalMenu.DISPLAY_SLOTS", text)
        self.assertNotRegex(text, r"canCraft\([^)]*\)\s*\{\s*return true;\s*\}")

    def test_emi_does_not_claim_third_party_recipe_workstations(self):
        text = self.read_required(
            "src/main/java/com/swear/autostorage/compat/AutoStorageEmiPlugin.java"
        )
        register = self.java_block(
            text,
            r"\bpublic\s+void\s+register\s*\(",
            "AutoStorageEmiPlugin.register",
        )

        self.assertNotIn("MachineEnergyTable", text)
        self.assertNotIn("VanillaEmiRecipeCategories", text)
        self.assertNotIn("registry.addWorkstation", register)
        self.assertNotIn("IronFurnacesCompat", text)

    def test_emi_inventory_strips_terminal_display_metadata_and_keeps_exact_amount(self):
        text = self.read_required(
            "src/main/java/com/swear/autostorage/compat/AutoStorageEmiPlugin.java"
        )
        self.assertIn("EmiPlayerInventory getInventory", text)
        self.assertIn("TerminalDisplayStack.strip(stack)", text)
        self.assertIn("TerminalDisplayStack.amount(stack)", text)

    def test_emi_requires_an_item_page_and_supported_exact_backing_recipe(self):
        text = self.read_required("src/main/java/com/swear/autostorage/compat/AutoStorageEmiPlugin.java")
        supports_recipe = text[text.index("public boolean supportsRecipe"):text.index("@Override", text.index("public boolean supportsRecipe"))]
        can_craft = text[text.index("public boolean canCraft"):text.index("@Override", text.index("public boolean canCraft"))]
        craft = text[text.index("public boolean craft"):]

        self.assertIn("RecipeHolder<?> backingRecipe = recipe.getBackingRecipe();", supports_recipe)
        self.assertIn("backingRecipe != null", supports_recipe)
        self.assertIn("RecipeHolder<?> currentHolder", supports_recipe)
        self.assertIn("byKey(backingRecipe.id())", supports_recipe)
        self.assertIn("currentHolder == backingRecipe", supports_recipe)
        self.assertIn("CraftingTerminalMenu.supportsRecipeHolder(currentHolder)", supports_recipe)
        self.assertNotIn("supportsRecipeContract", supports_recipe)
        self.assertIn("getPage().isItemPage()", can_craft)
        self.assertIn("supportsRecipe(recipe)", can_craft)
        self.assertIn("!menu.getPage().isItemPage()", craft)
        self.assertIn("supportsRecipe(recipe)", craft)

    def test_recipe_family_policy_is_owned_only_by_complete_built_in_adapter_matches(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        adapters = self.read_required(
            "src/main/java/com/swear/autostorage/BuiltInRecipeAdapters.java"
        )
        match = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeAdapterMatch.java"
        )

        self.assertNotIn("SUPPORTED_RECIPE_TYPES", menu)
        self.assertNotIn("CraftingStationTable.", menu)
        self.assertNotIn("RecipeEnergyTable.", menu)
        compatibility_start = menu.index("public static boolean supportsRecipeContract")
        compatibility_end = menu.index("\n    }", compatibility_start) + len("\n    }")
        operational_menu = menu[:compatibility_start] + menu[compatibility_end:]
        self.assertNotIn("supportsRecipeContract(", operational_menu)
        self.assertIn("BuiltInRecipeAdapters.registry().classify", menu[compatibility_start:compatibility_end])
        for family_policy in (
            "AbstractCookingRecipe",
            "ShapedRecipe",
            "ShapelessRecipe",
            "SmithingRecipeInput",
            "SmithingTransformRecipe",
            "SmithingTrimRecipe",
            "AxeTransformationRecipe",
        ):
            self.assertNotIn(family_policy, menu)
            self.assertIn(family_policy, adapters)
        for obligation in (
            "orderedInputs",
            "stationDescriptorId",
            "energyCost",
            "toolCost",
            "checkedOutput",
            "remainders",
            "presentation",
            "resolveVariants",
            "validatesSimulation",
            "validatesCommit",
        ):
            self.assertIn(obligation, match)
        self.assertIn("getCookingTime()", adapters)
        self.assertIn("SmithingRecipeInput", adapters)
        self.assertIn("MachineEnergyTable.AXE_ID", adapters)
        self.assertFalse((ROOT / "src/main/java/com/swear/autostorage/CraftingStationTable.java").exists())
        self.assertFalse((ROOT / "src/main/java/com/swear/autostorage/RecipeEnergyTable.java").exists())

    def test_smithing_weak_cache_values_do_not_retain_recipe_keys(self):
        adapters = self.read_required(
            "src/main/java/com/swear/autostorage/BuiltInRecipeAdapters.java"
        )

        self.assertRegex(
            adapters,
            r"Map<Recipe<\?>,\s*SmithingRepresentatives>\s+SMITHING_INPUT_CACHE",
        )
        self.assertRegex(
            adapters,
            r"private record SmithingRepresentatives\(\s*"
            r"List<ItemStack> templates,\s*"
            r"List<ItemStack> bases,\s*"
            r"List<ItemStack> additions\s*\)",
        )
        cache_values = self.java_block(
            adapters,
            r"private static SmithingRepresentatives smithingRepresentatives\(",
            "detached smithing cache value factory",
        )
        self.assertIn("SMITHING_INPUT_CACHE.computeIfAbsent", cache_values)
        self.assertNotIn("RecipeAdapterMatch.Input", cache_values)
        self.assertNotIn("SmithingInputIdentity", cache_values)
        self.assertNotRegex(cache_values, r"recipe::is(?:Template|Base|Addition)Ingredient")

        smithing_inputs = self.java_block(
            adapters,
            r"private static List<RecipeAdapterMatch\.Input> smithingInputs\(",
            "transient exact smithing inputs",
        )
        self.assertEqual(3, smithing_inputs.count("new SmithingInputIdentity(recipe,"))
        self.assertEqual(3, len(re.findall(
            r"recipe::is(?:Template|Base|Addition)Ingredient",
            smithing_inputs,
        )))
        component_identity = self.java_block(
            adapters,
            r"private static Optional<String> componentIdentity\(",
            "component identity cache",
        )
        self.assertIn("ignored -> new WeakHashMap<>()", component_identity)
        self.assertNotIn("ignored -> new HashMap<>()", component_identity)

    def test_emi_sends_context_amount_and_destination_for_exact_backing_recipe(self):
        text = self.read_required("src/main/java/com/swear/autostorage/compat/AutoStorageEmiPlugin.java")
        self.assertIn("context.getAmount()", text)
        self.assertIn("context.getDestination()", text)
        self.assertIn("private static final int MAX_CRAFT_AMOUNT = 64;", text)
        self.assertIn(
            "int amount = Math.max(1, Math.min(context.getAmount(), MAX_CRAFT_AMOUNT));",
            text,
        )
        self.assertIn("case NONE -> CraftingDestination.NONE;", text)
        self.assertIn("case CURSOR -> CraftingDestination.CURSOR;", text)
        self.assertIn("case INVENTORY -> CraftingDestination.INVENTORY;", text)
        self.assertRegex(
            text,
            r"new CraftingRecipeSelectionPacket\(\s*menu\.containerId,\s*backingRecipe\.id\(\),\s*amount,\s*destination\s*\)",
        )

    def test_emi_recipe_request_has_no_visible_output_slot_gate(self):
        text = self.read_required("src/main/java/com/swear/autostorage/compat/AutoStorageEmiPlugin.java")
        self.assertNotIn("handleInventoryMouseClick", text)
        self.assertNotIn("findOutputSlot", text)
        self.assertNotIn("recipe.getOutputs()", text)
        self.assertNotIn("menu.getSlot(", text)
        self.assertNotIn("ItemStack.isSameItemSameComponents", text)

        entrypoint = self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        )
        self.assertIn("CraftingRecipeSelectionPacket.TYPE", entrypoint)
        self.assertIn(
            "menu.handleRecipeRequest(player.level(), packet.recipeId(), packet.amount(), packet.destination(), player);",
            entrypoint,
        )

    def test_release_requires_compatible_emi_client_range_without_exact_pin(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")

        self.assertRegex(
            build,
            r'compileOnly\s+"maven\.modrinth:emi:\$\{emi_runtime_version\}"',
        )
        self.assertRegex(
            build,
            r'fusionRuntimeRuntimeOnly\s+"maven\.modrinth:emi:\$\{emi_runtime_version\}"',
            "the isolated client/data runtime must use the Modrinth full EMI artifact",
        )
        self.assertNotRegex(
            build,
            r'(?m)^\s*runtimeOnly\s+"dev\.emi:emi-neoforge:',
            "dedicated server and GameTest must not receive the full EMI runtime",
        )
        self.assertIn("emiRuntime", build)
        self.assertRegex(
            build,
            r'emiRuntime\s+"maven\.modrinth:emi:\$\{emi_runtime_version\}"',
        )
        self.assertIn('tasks.register("stageEmiRuntime", Copy)', build)
        self.assertIn("from configurations.emiRuntime", build)
        self.assertIn('into layout.buildDirectory.dir("client-smoke-mods")', build)
        self.assertIn("def stagedEmiVersion = project.emi_version.toString()", build)
        self.assertIn('rename { "emi-neoforge-${stagedEmiVersion}.jar" }', build)
        self.assertNotIn('rename { "emi-neoforge-${emi_version}.jar" }', build)
        self.assertIn("emi_version=1.1.24+1.21.1", properties)
        self.assertIn("emi_runtime_version=5sIPA1To", properties)
        self.assertIn("emi_version_range=[1.1.24,2)", properties)
        self.assertNotRegex(
            build,
            r'(?m)^\s*(?:compileOnly|runtimeOnly|fusionRuntimeRuntimeOnly|emiRuntime)\s+"dev\.emi:emi-neoforge:',
            "all Auto Storage EMI compile and runtime artifacts must come from Modrinth",
        )
        self.assertIn("clientSmokePatchouli", build)
        self.assertIn("clientSmokeFusion", build)
        self.assertIn('tasks.register("stageClientSmokeSupportMods", Copy)', build)
        self.assertIn('rename { "patchouli-neoforge.jar" }', build)
        self.assertIn('rename { "fusion-connected-textures.jar" }', build)
        self.assertRegex(build, r"emi_version_range\s*:\s*emi_version_range")
        self.assertRegex(
            metadata,
            r'''(?s)\[\[dependencies\.\$\{mod_id\}\]\]\s*
\s*modId="emi"\s*
\s*type="required"\s*
\s*versionRange="\$\{emi_version_range\}"\s*
\s*ordering="NONE"\s*
\s*side="CLIENT"''',
        )
        self.assertNotIn('versionRange="[1.1.24]"', metadata)

    def test_ci_stages_exact_ae2_ancestry_before_script_tests(self):
        build = self.read_required("build.gradle")
        workflow = self.read_required(".github/workflows/ci.yml")

        self.assertIn("ae2CompatAuditAncestry", build)
        for dependency in (
            "mcp.mobius.waila:wthit-api:neo-12.1.2",
            "mcjty.theoneprobe:theoneprobe:1.20.4_neo-11.0.1-2",
            "org.appliedenergistics:guideme:21.1.1",
            "me.shedaniel:RoughlyEnoughItems-neoforge:16.0.729",
            "dev.emi:emi-neoforge:1.1.22+1.21.1:api",
            "curse.maven:jade-324717:5427817",
            "net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7",
        ):
            self.assertIn(dependency, build)
        for unreachable_dependency in (
            "me.shedaniel.cloth:cloth-config-neoforge:15.0.127",
            "dev.architectury:architectury-neoforge:13.0.1",
            "org.jetbrains:annotations:24.1.0",
        ):
            self.assertNotIn(unreachable_dependency, build)
        self.assertIn('tasks.register("stageAe2CompatAuditAncestry")', build)
        self.assertIn("enable {", build)
        self.assertIn("disableRecompilation = true", build)
        stage_block = build[
            build.index('tasks.register("stageAe2CompatAuditAncestry")'):
            build.index("\ndef compatArtifactVerificationTasks")
        ]
        self.assertIn("audit.ancestry_classpath.collectEntries", stage_block)
        self.assertIn("configurations.additionalRuntimeClasspath", stage_block)
        self.assertIn(
            'tasks.named("createMinecraftArtifacts").get().outputs.files',
            build,
        )
        self.assertIn("compatKitMinecraftArtifacts", stage_block)
        self.assertIn("normalize-jar", stage_block)
        self.assertIn(
            'new ProcessBuilder(\n                            '
            '"python3",\n                            '
            '"tools/compat-kit/compat_kit.py",',
            stage_block,
        )
        self.assertNotIn(
            '"python3",\n                            '
            '"tools/compat-kit/compat-kit",',
            stage_block,
        )
        self.assertIn("ae2-audit-canonical", stage_block)
        self.assertIn("observedCanonical", stage_block)
        self.assertIn("observed ModDev canonical artifacts", stage_block)
        self.assertIn(
            'digest.digest().encodeHex().toString()',
            stage_block,
        )
        self.assertIn("it.size.longValue()", stage_block)
        self.assertNotIn(
            'artifacts.size() != expected.size()',
            stage_block,
        )
        stage = "./gradlew stageAe2CompatAuditAncestry"
        tests = "PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts"
        self.assertIn(stage, workflow)
        self.assertEqual(1, workflow.count(stage))
        self.assertLess(workflow.index(stage), workflow.index("./gradlew runGameTestServer"))
        self.assertLess(workflow.index(stage), workflow.index(tests))

    def test_release_stages_exact_ae2_ancestry_before_gametests(self):
        workflow = self.read_required(".github/workflows/release.yml")
        stage = "./gradlew stageAe2CompatAuditAncestry"

        self.assertEqual(1, workflow.count(stage))
        self.assertLess(
            workflow.index(stage),
            workflow.index("./gradlew runGameTestServer"),
        )
        self.assertIn("build/ci-logs/ae2-audit-ancestry.log", workflow)

    def test_build_script_uses_gradle_10_safe_repository_url_assignment(self):
        build = self.read_required("build.gradle")
        self.assertNotRegex(build, r'(?m)^\s*url\s+"')
        self.assertIn('url = uri("file://${project.projectDir}/repo")', build)

    def test_all_gametest_gates_reject_any_selftest_failure(self):
        build = self.read_required("build.gradle")
        explicit_expected = {
            "runGameTestServer": 406,
            "runRecipeAddonGameTestServer": 17,
            "runPneumaticCraftGameTestServer": 9,
            "runCompatibilityMatrixGameTestServer": 3,
        }
        for task, count in explicit_expected.items():
            match = re.search(
                rf"tasks\.named\('{task}'\)\.configure \{{(?P<body>.*?)\n\}}",
                build,
                re.DOTALL,
            )
            self.assertIsNotNone(match, task)
            body = match.group("body")
            self.assertIn(f"All {count} required tests passed", body, task)
            self.assertIn("expectedSelfTestSummary", body, task)
            self.assertIn("TESTS FAILED!", body, task)
        descriptors = sorted((ROOT / "src/compat").glob("*/compat-module.json"))
        self.assertTrue(descriptors)
        for descriptor_path in descriptors:
            descriptor = json.loads(descriptor_path.read_text())
            self.assert_descriptor_driven_fixture(
                build,
                descriptor_path.parent.name,
                descriptor["fixture"],
                descriptor["expectedTests"],
            )
        self.assertIn("SelfTest: 204927 passed, 0 failed, 204927 total", build)
        self.assertNotIn("SelfTest: 1 TESTS FAILED!", build)

    def test_compatibility_matrix_uses_descriptor_owned_recipe_inventories(self):
        matrix = self.read_required(
            "src/compatibilityMatrixFixture/java/com/swear/autostorage/fixture/"
            "compatibilitymatrix/CraftablePerformanceGameTests.java"
        )
        coexistence = self.read_required(
            "src/compatibilityMatrixFixture/java/com/swear/autostorage/fixture/"
            "compatibilitymatrix/CompatibilityMatrixGameTests.java"
        )
        self.assertNotIn("EXPECTED_RECIPE_COUNT", matrix)
        self.assertIn(
            "MAX_BASELINE_INDEX_RETAINED_BYTES = 9L * 1024L * 1024L",
            matrix,
        )
        self.assertIn("assertRecipeInventories", matrix)
        self.assertIn("CompatibilityMatrixManifest", matrix)
        self.assertIn("CompatibilityMatrixManifest", coexistence)
        self.assertNotIn('"ae2"', coexistence)
        self.assertNotIn("ae2_inscriber", coexistence)
        companions = self.read_required(
            "src/compatibilityMatrixFixture/resources/META-INF/auto_storage/"
            "compatibility-matrix-companions.json"
        )
        self.assertNotIn("unclaimedRecipeInventory", companions)
        self.assertNotIn("coexistenceRecipeInventory", companions)
        self.assertIn("pneumaticcraft", companions)
        build = self.read_required("build.gradle")
        self.assertIn("generateCompatibilityMatrixManifest", build)
        self.assertIn("runCompatFixtureGameTestServers", build)
        self.assertIn("compatibility-matrix-manifest.json", build)

    def test_enderio_compat_is_optional_and_isolated(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("enderio")
        descriptor = json.loads(module_index)
        module = self.read_required(
            "src/compat/enderio/java/com/swear/autostorage/compat/"
            "enderio/EnderioCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/enderio/java/com/swear/autostorage/compat/"
            "enderio/EnderioCompat.java"
        )
        fixture_metadata = self.read_required(
            "src/enderIoFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required("docs/enderio-compatibility.md")

        self.assertRegex(properties, r"(?m)^enderio_ci_version=Tfs8aJPH$")
        self.assertIn(
            "maven.modrinth:enderio:Tfs8aJPH",
            descriptor["dependencies"],
        )
        self.assertNotRegex(
            build,
            r'(?m)^\s*runtimeOnly\s+"maven\.modrinth:enderio:',
        )
        self.assert_descriptor_driven_fixture(
            build, "enderio", "enderIoFixture", 6
        )
        self.assertNotIn('modId="enderio"', metadata)
        self.assertEqual(["enderio"], descriptor["requires"])
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn("EnderioCompat.register(MACHINES, RECIPES)", module)
        self.assertNotIn("import com.enderio.enderio.", module)
        self.assertIn("AlloySmeltingRecipe.class", compat)
        self.assertNotIn("SagMillingRecipe.class", compat)
        self.assertNotIn("SlicingRecipe.class", compat)
        supports = self.java_block(
            compat,
            r"\bprivate\s+static\s+boolean\s+supports\s*\(",
            "EnderioCompat.supports",
        )
        self.assertIn("!recipe.output().isEmpty()", supports)
        self.assertIn("!recipe.inputs().isEmpty()", supports)
        self.assertNotIn(
            "recipe.output() != null",
            supports,
            "AlloySmeltingRecipe codec requires output; speculative null checks are not a behavior gap",
        )
        self.assertNotIn(
            "recipe.inputs() != null",
            supports,
            "AlloySmeltingRecipe codec requires inputs; speculative null checks are not a behavior gap",
        )
        self.assertIn("!keysWithoutRegistries(ingredient).isEmpty()", compat)
        self.assertRegex(
            compat,
            r"StorageResourceKey\.item\(\s*"
            r"stack\.copyWithCount\(1\),\s*registries\)\s*\)\s*"
            r"\.distinct\(\)",
            "keys() must distinct StorageResourceKey identities, not ItemStack identity",
        )
        self.assertRegex(
            compat,
            r"Item item = BuiltInRegistries\.ITEM\.get\(id\);\s*"
            r"if \(item == Items\.AIR\) \{\s*"
            r"throw new IllegalStateException\(\"Missing Ender IO station item \" \+ id\);",
        )
        self.assertNotRegex(
            compat,
            r"item\s*==\s*null\s*\|\|",
            "BuiltInRegistries.ITEM is DefaultedRegistry; ITEM.get never returns null",
        )
        self.assertIn('modId="enderio"', fixture_metadata)
        self.assertIn('versionRange="[0,)"', fixture_metadata)
        self.assertNotIn("8.2.11-beta", fixture_metadata)
        self.assertIn("representative CI artifact", compatibility_doc)
        self.assertIn("not an exact player", compatibility_doc)
        self.assertIn("dependency pin", compatibility_doc)

    def test_mekanism_chemical_compat_is_optional_and_ci_exercised(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("mekanism")
        module = self.read_required(
            "src/compat/mekanism/java/com/swear/autostorage/compat/"
            "mekanism/MekanismCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/mekanism/java/com/swear/autostorage/"
            "MekanismChemicalCompat.java"
        )
        fixture_metadata = self.read_required(
            "src/mekanismFixture/resources/META-INF/neoforge.mods.toml"
        )

        self.assertIn('"maven.modrinth:mekanism:${mekanism_ci_version}"', build)
        self.assertIn(
            'mekanismFixtureRuntimeOnly "maven.modrinth:mekanism:${mekanism_ci_version}"',
            build,
        )
        self.assertNotRegex(build, r'(?m)^\s*runtimeOnly\s+"[^"]*mekanism')
        self.assertRegex(properties, r"(?m)^mekanism_ci_version=[A-Za-z0-9]+$")
        self.assertNotIn('modId="mekanism"', metadata)
        self.assertEqual(["mekanism"], json.loads(module_index)["requires"])
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn(".capabilities(MekanismChemicalCompat::register)", module)
        self.assertNotIn("import mekanism.", module)
        self.assertIn(
            "AutoStorageCapabilityApi.registerSidedResourceCapability(",
            compat,
        )
        self.assertIn("WeakReference<IChemicalHandler>", compat)
        self.assertNotIn(
            "Map<StorageCoreBlockEntity, IChemicalHandler>",
            compat,
            "a weak-key map still leaks when each strongly held handler references its Core key",
        )
        self.assert_descriptor_driven_fixture(
            build, "mekanism", "mekanismFixture", 47
        )
        self.assertIn('modId="mekanism"', fixture_metadata)
        self.assertIn('versionRange="[10.7,)"', fixture_metadata)
        self.assertNotRegex(fixture_metadata, r'versionRange="\[10\.7\.\d')
        self.assertRegex(
            build,
            r"(?s)recipeAddonFixture\s*\{.*?"
            r"runtimeClasspath\s*\+=\s*output\s*\+\s*"
            r"sourceSets\.main\.runtimeClasspath.*?\}",
            "recipeAddonFixture runtime must not inherit main compileOnly mods",
        )

    def test_botania_mana_and_recipe_compat_is_optional_and_isolated(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("botania")
        module = self.read_required(
            "src/compat/botania/java/com/swear/autostorage/compat/"
            "botania/BotaniaCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/botania/java/com/swear/autostorage/compat/"
            "botania/BotaniaCompat.java"
        )
        kinds = self.read_required(
            "src/main/java/com/swear/autostorage/"
            "StorageResourceKindApi.java"
        )
        fixture_metadata = self.read_required(
            "src/botaniaFixture/resources/META-INF/neoforge.mods.toml"
        )

        self.assertRegex(
            properties,
            r"(?m)^botania_ci_version=455-20260723\.172746-31$",
        )
        self.assertRegex(
            properties,
            r"(?m)^botania_ci_sha256="
            r"cfba1589f25d317b2a99b5b5c7b7a3966d5f18999535392d62fcf28b1f2b8908$",
        )
        self.assertRegex(properties, r"(?m)^neo_version=21\.1\.(?:22[9]|2[3-9]\d|[3-9]\d\d)$")
        self.assertIn(
            '"vazkii.botania:botania-neoforge-1.21.1:${botania_ci_version}"',
            build,
        )
        self.assertRegex(
            build,
            r'(?s)botaniaFixtureRuntimeOnly\(\s*'
            r'"vazkii\.botania:botania-neoforge-1\.21\.1:\$\{botania_ci_version\}"'
            r'\s*\)\s*\{.*?'
            r'exclude group: "vazkii\.patchouli", module: "Patchouli".*?'
            r'exclude group: "mezz\.jei", module: "jei-1\.21\.1-neoforge".*?\}',
        )
        self.assertNotRegex(
            build,
            r'(?m)^\s*runtimeOnly\s+"vazkii\.botania:botania-neoforge-1\.21\.1',
        )
        self.assertNotIn("455-SNAPSHOT", build)
        self.assertIn('url = "https://maven.theillusivec4.top/"', build)
        self.assertIn(
            'def botaniaFixtureRuntime = '
            'configurations.named("botaniaFixtureRuntimeClasspath")',
            build,
        )
        self.assertIn('tasks.register("verifyBotaniaFixtureArtifact")', build)
        self.assertIn("Botania CI fixture SHA-256 mismatch", build)
        self.assertIn("inputs.files(botaniaFixtureRuntime)", build)
        self.assertIn("inputs.properties.expectedSha256", build)
        verify = self.java_block(
            build,
            r'tasks\.register\("verifyBotaniaFixtureArtifact"\)',
            "verifyBotaniaFixtureArtifact",
        )
        self.assertNotIn("configurations", verify)
        self.assertNotIn("resolvedConfiguration", verify)
        self.assertNotIn("project.", verify)
        self.assertIn('dependsOn tasks.named("verifyBotaniaFixtureArtifact")', build)
        self.assert_descriptor_driven_fixture(
            build, "botania", "botaniaFixture", 14
        )
        self.assertNotIn('modId="botania"', metadata)
        self.assertEqual(["botania"], json.loads(module_index)["requires"])
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn("BotaniaCompat.register(MACHINES, RECIPES)", module)
        self.assertIn(".containerStrategies(CONTAINERS)", module)
        self.assertNotIn("import vazkii.botania.", module)
        self.assertIn("BOTANIA_MANA_KIND", kinds)
        self.assertIn("ManaItem.LOOKUP.find", compat)
        self.assertIn("copyWithCount(1)", compat)
        self.assertNotIn("ManaReceiver", compat)
        self.assertIn('modId="botania"', fixture_metadata)
        self.assertIn('versionRange="[455-SNAPSHOT,)"', fixture_metadata)
        self.assertNotIn("455-20260723.172746-31", fixture_metadata)
        self.assertNotIn("455-20260723.172746-31", metadata)

    def test_modern_industrialization_recipe_compat_is_optional_and_isolated(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("modern_industrialization")
        module = self.read_required(
            "src/compat/modern_industrialization/java/com/swear/autostorage/"
            "compat/modernindustrialization/ModernIndustrializationCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/modern_industrialization/java/com/swear/autostorage/compat/"
            "modernindustrialization/ModernIndustrializationCompat.java"
        )
        fixture_metadata = self.read_required(
            "src/modernIndustrializationFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required(
            "docs/modern-industrialization-compatibility.md"
        )

        self.assertRegex(
            properties,
            r"(?m)^modern_industrialization_ci_version=[A-Za-z0-9]+$",
        )
        self.assertRegex(properties, r"(?m)^guideme_ci_version=[A-Za-z0-9]+$")
        self.assertIn(
            '"maven.modrinth:modern-industrialization:'
            '${modern_industrialization_ci_version}"',
            build,
        )
        self.assertIn(
            'modernIndustrializationFixtureRuntimeOnly(\n'
            '            "maven.modrinth:modern-industrialization:'
            '${modern_industrialization_ci_version}")',
            build,
        )
        self.assertNotRegex(
            build,
            r'(?m)^\s*runtimeOnly\s+"[^"]*modern-industrialization',
        )
        self.assert_descriptor_driven_fixture(
            build,
            "modern_industrialization",
            "modernIndustrializationFixture",
            7,
        )
        self.assertNotIn('modId="modern_industrialization"', metadata)
        self.assertEqual(
            ["modern_industrialization"],
            json.loads(module_index)["requires"],
        )
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn(
            "ModernIndustrializationCompat.register(MACHINES, RECIPES)",
            module,
        )
        self.assertNotIn("import aztech.modern_industrialization.", module)
        self.assertIn("MachineRecipe.class", compat)
        self.assertIn("MIMachineRecipeTypes", compat)
        self.assertIn('modId="modern_industrialization"', fixture_metadata)
        self.assertIn('versionRange="[2.5,)"', fixture_metadata)
        self.assertNotRegex(fixture_metadata, r'versionRange="\[2\.5\.\d')
        self.assertIn("representative CI artifact", compatibility_doc)
        self.assertIn("not an exact player dependency pin", compatibility_doc)

    def test_ars_nouveau_source_and_recipe_compat_is_optional_and_isolated(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("ars_nouveau")
        module = self.read_required(
            "src/compat/ars_nouveau/java/com/swear/autostorage/compat/"
            "arsnouveau/ArsNouveauCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/ars_nouveau/java/com/swear/autostorage/compat/"
            "arsnouveau/ArsNouveauCompat.java"
        )
        kinds = self.read_required(
            "src/main/java/com/swear/autostorage/"
            "StorageResourceKindApi.java"
        )
        fixture_metadata = self.read_required(
            "src/arsNouveauFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required("docs/ars-nouveau-compatibility.md")

        self.assertRegex(properties, r"(?m)^ars_nouveau_ci_version=[A-Za-z0-9]+$")
        self.assertRegex(properties, r"(?m)^geckolib_ci_version=[A-Za-z0-9]+$")
        self.assertRegex(properties, r"(?m)^ars_curios_ci_version=[A-Za-z0-9]+$")
        self.assertIn(
            '"maven.modrinth:ars-nouveau:${ars_nouveau_ci_version}"',
            build,
        )
        self.assertIn(
            'arsNouveauFixtureRuntimeOnly "maven.modrinth:ars-nouveau:'
            '${ars_nouveau_ci_version}"',
            build,
        )
        self.assertIn(
            'arsNouveauFixtureRuntimeOnly "maven.modrinth:geckolib:'
            '${geckolib_ci_version}"',
            build,
        )
        self.assertIn(
            'arsNouveauFixtureRuntimeOnly "maven.modrinth:curios:'
            '${ars_curios_ci_version}"',
            build,
        )
        self.assertNotRegex(
            build,
            r'(?m)^\s*runtimeOnly\s+"maven\.modrinth:ars-nouveau:',
        )
        self.assert_descriptor_driven_fixture(
            build, "ars_nouveau", "arsNouveauFixture", 11
        )
        self.assertNotIn('modId="ars_nouveau"', metadata)
        self.assertEqual(["ars_nouveau"], json.loads(module_index)["requires"])
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn("ArsNouveauCompat.register(MACHINES, RECIPES)", module)
        self.assertIn(".blockStrategies(BLOCKS)", module)
        self.assertNotIn("import com.hollingsworth.arsnouveau.", module)
        self.assertIn("ImbuementRecipe.class", compat)
        self.assertIn("EnchantingApparatusRecipe.class", compat)
        self.assertIn("CapabilityRegistry.SOURCE_CAPABILITY", compat)
        self.assertIn("ARS_NOUVEAU_SOURCE_KIND", kinds)
        self.assertIn('modId="ars_nouveau"', fixture_metadata)
        self.assertIn('versionRange="[5.12,)"', fixture_metadata)
        self.assertNotRegex(fixture_metadata, r'versionRange="\[5\.12\.\d')
        self.assertIn("representative CI artifact", compatibility_doc)
        self.assertIn("not an exact player dependency pin", compatibility_doc)

    def test_evilcraft_blood_infuser_compat_is_optional_and_isolated(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("evilcraft")
        module = self.read_required(
            "src/compat/evilcraft/java/com/swear/autostorage/compat/"
            "evilcraft/EvilCraftCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/evilcraft/java/com/swear/autostorage/compat/"
            "evilcraft/EvilCraftCompat.java"
        )
        fixture_metadata = self.read_required(
            "src/evilCraftFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required("docs/evilcraft-compatibility.md")

        self.assertRegex(properties, r"(?m)^evilcraft_ci_version=[A-Za-z0-9]+$")
        self.assertRegex(properties, r"(?m)^cyclops_core_ci_version=[A-Za-z0-9]+$")
        self.assertIn(
            '"maven.modrinth:evilcraft:${evilcraft_ci_version}"',
            build,
        )
        self.assertIn(
            '"maven.modrinth:cyclops-core:${cyclops_core_ci_version}"',
            build,
        )
        self.assertIn(
            'evilCraftFixtureRuntimeOnly "maven.modrinth:evilcraft:'
            '${evilcraft_ci_version}"',
            build,
        )
        self.assertIn(
            'evilCraftFixtureRuntimeOnly "maven.modrinth:cyclops-core:'
            '${cyclops_core_ci_version}"',
            build,
        )
        self.assertNotRegex(
            build,
            r'(?m)^\s*runtimeOnly\s+"maven\.modrinth:(evilcraft|cyclops-core):',
        )
        self.assert_descriptor_driven_fixture(
            build, "evilcraft", "evilCraftFixture", 10
        )
        self.assertNotIn('modId="evilcraft"', metadata)
        self.assertEqual(["evilcraft"], json.loads(module_index)["requires"])
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn("EvilCraftCompat.register(MACHINES, RECIPES)", module)
        self.assertNotIn("import org.cyclops.", module)
        self.assertIn("RecipeBloodInfuser.class", compat)
        self.assertIn("RegistryEntries.RECIPETYPE_BLOOD_INFUSER", compat)
        self.assertIn("getOutputItem().left()", compat)
        self.assertIn('modId="evilcraft"', fixture_metadata)
        self.assertIn('versionRange="[1.2.91,)"', fixture_metadata)
        self.assertNotRegex(fixture_metadata, r'versionRange="\[1\.2\.91\.\d')
        self.assertIn("representative CI artifact", compatibility_doc)
        self.assertIn("not an exact player dependency pin", compatibility_doc)

    def test_powah_energizing_compat_is_optional_and_isolated(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("powah")
        module = self.read_required(
            "src/compat/powah/java/com/swear/autostorage/compat/"
            "powah/PowahCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/powah/java/com/swear/autostorage/compat/"
            "powah/PowahCompat.java"
        )
        fixture_metadata = self.read_required(
            "src/powahFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required("docs/powah-compatibility.md")

        self.assertRegex(properties, r"(?m)^powah_ci_version=[A-Za-z0-9]+$")
        self.assertRegex(
            properties,
            r"(?m)^powah_cloth_config_ci_version=[A-Za-z0-9]+$",
        )
        self.assertIn(
            '"maven.modrinth:powah:${powah_ci_version}"',
            build,
        )
        self.assertIn(
            'powahFixtureRuntimeOnly "maven.modrinth:powah:'
            '${powah_ci_version}"',
            build,
        )
        self.assertIn(
            'powahFixtureRuntimeOnly "maven.modrinth:cloth-config:'
            '${powah_cloth_config_ci_version}"',
            build,
        )
        self.assertIn(
            'powahFixtureRuntimeOnly "maven.modrinth:guideme:'
            '${guideme_ci_version}"',
            build,
        )
        self.assertNotRegex(
            build,
            r'(?m)^\s*runtimeOnly\s+"maven\.modrinth:'
            r'(powah|cloth-config|guideme):',
        )
        self.assert_descriptor_driven_fixture(
            build, "powah", "powahFixture", 11
        )
        self.assertNotIn('modId="powah"', metadata)
        self.assertEqual(["powah"], json.loads(module_index)["requires"])
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn(
            "PowahCompat.register(MACHINES, RECIPES, TRANSFORMS)",
            module,
        )
        self.assertIn(".transformProviders(TRANSFORMS)", module)
        self.assertNotIn("import owmii.powah.", module)
        self.assertIn("EnergizingRecipe.class", compat)
        self.assertIn("recipe.getScaledEnergy()", compat)
        self.assertIn("StorageResourceKey.neoforgeEnergy()", compat)
        self.assertIn("Tier.getNormalVariants()", compat)
        self.assertIn('modId="powah"', fixture_metadata)
        self.assertIn('versionRange="[6.2,)"', fixture_metadata)
        self.assertNotRegex(fixture_metadata, r'versionRange="\[6\.2\.\d')
        self.assertIn("representative CI artifact", compatibility_doc)
        self.assertIn("not an exact player dependency pin", compatibility_doc)

    def test_industrial_foregoing_compat_is_optional_and_isolated(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("industrial_foregoing")
        module = self.read_required(
            "src/compat/industrial_foregoing/java/com/swear/autostorage/"
            "compat/industrialforegoing/IndustrialForegoingCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/industrial_foregoing/java/com/swear/autostorage/compat/"
            "industrialforegoing/IndustrialForegoingCompat.java"
        )
        fixture_metadata = self.read_required(
            "src/industrialForegoingFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required(
            "docs/industrial-foregoing-compatibility.md"
        )

        self.assertRegex(
            properties,
            r"(?m)^industrial_foregoing_ci_version=[A-Za-z0-9]+$",
        )
        self.assertRegex(properties, r"(?m)^titanium_ci_version=[A-Za-z0-9]+$")
        self.assertIn(
            '"maven.modrinth:industrial-foregoing:'
            '${industrial_foregoing_ci_version}"',
            build,
        )
        self.assertIn(
            'industrialForegoingFixtureRuntimeOnly(\n'
            '            "maven.modrinth:industrial-foregoing:'
            '${industrial_foregoing_ci_version}")',
            build,
        )
        self.assertIn(
            'industrialForegoingFixtureRuntimeOnly "maven.modrinth:titanium:'
            '${titanium_ci_version}"',
            build,
        )
        self.assertNotRegex(
            build,
            r'(?m)^\s*runtimeOnly\s+"maven\.modrinth:'
            r'(industrial-foregoing|titanium):',
        )
        self.assert_descriptor_driven_fixture(
            build,
            "industrial_foregoing",
            "industrialForegoingFixture",
            9,
        )
        self.assertNotIn('modId="industrialforegoing"', metadata)
        self.assertEqual(["industrialforegoing"], json.loads(module_index)["requires"])
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn(
            "IndustrialForegoingCompat.register(MACHINES, RECIPES)",
            module,
        )
        self.assertNotIn("import com.buuz135.", module)
        self.assertIn("DissolutionChamberRecipe.class", compat)
        self.assertIn("StoneWorkGenerateRecipe.class", compat)
        self.assertIn("CrusherRecipe.class", compat)
        self.assertIn("DissolutionChamberConfig.powerPerTick", compat)
        self.assertIn("MaterialStoneWorkFactoryConfig.powerPerTick", compat)
        self.assertNotIn("FluidExtractorRecipe.class", compat)
        self.assertNotIn("LaserDrillOreRecipe.class", compat)
        self.assertNotIn("LaserDrillFluidRecipe.class", compat)
        self.assertIn('modId="industrialforegoing"', fixture_metadata)
        self.assertIn('versionRange="[1.21-3.6,)"', fixture_metadata)
        self.assertNotIn("1.21-3.6.39", fixture_metadata)
        self.assertIn("representative CI artifact", compatibility_doc)
        self.assertIn("not an exact player dependency pin", compatibility_doc)

    def test_create_compat_is_optional_and_isolated(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("create")
        module = self.read_required(
            "src/compat/create/java/com/swear/autostorage/compat/"
            "create/CreateCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/create/java/com/swear/autostorage/compat/"
            "create/CreateCompat.java"
        )
        fixture_metadata = self.read_required(
            "src/createFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required("docs/create-compatibility.md")

        self.assertRegex(properties, r"(?m)^create_ci_version=[A-Za-z0-9]+$")
        self.assertIn(
            '"maven.modrinth:create:${create_ci_version}"',
            build,
        )
        self.assertIn(
            'createFixtureRuntimeOnly "maven.modrinth:create:${create_ci_version}"',
            build,
        )
        self.assertNotRegex(
            build,
            r'(?m)^\s*runtimeOnly\s+"maven\.modrinth:create:',
        )
        self.assert_descriptor_driven_fixture(
            build, "create", "createFixture", 13
        )
        self.assertNotIn('modId="create"', metadata)
        self.assertEqual(["create"], json.loads(module_index)["requires"])
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn("CreateCompat.register(MACHINES, RECIPES)", module)
        self.assertNotIn("import com.simibubi.create.", module)
        self.assertIn("MillingRecipe.class", compat)
        self.assertIn("CrushingRecipe.class", compat)
        self.assertIn("CuttingRecipe.class", compat)
        self.assertIn("FillingRecipe.class", compat)
        self.assertIn("EmptyingRecipe.class", compat)
        self.assertNotIn("PressingRecipe.class", compat)
        self.assertNotIn("MixingRecipe.class", compat)
        self.assertNotIn("SequencedAssemblyRecipe.class", compat)
        self.assertIn('modId="create"', fixture_metadata)
        self.assertIn('versionRange="[6.0,)"', fixture_metadata)
        self.assertNotIn("6.0.10", fixture_metadata)
        self.assertIn("representative CI artifact", compatibility_doc)
        self.assertIn("not an exact player dependency pin", compatibility_doc)

    def test_actuallyadditions_compat_is_optional_and_isolated(self):
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("actuallyadditions")
        module = self.read_required(
            "src/compat/actuallyadditions/java/com/swear/autostorage/compat/"
            "actuallyadditions/ActuallyadditionsCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/actuallyadditions/java/com/swear/autostorage/compat/"
            "actuallyadditions/ActuallyadditionsCompat.java"
        )
        fixture = self.read_required(
            "src/actuallyadditionsFixture/java/com/swear/autostorage/fixture/"
            "actuallyadditions/ActuallyadditionsIntegrationGameTests.java"
        )
        fixture_metadata = self.read_required(
            "src/actuallyadditionsFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required("docs/actuallyadditions-compatibility.md")
        build = self.read_required("build.gradle")
        contract = json.loads(
            self.read_required("compat/contracts/actuallyadditions.json")
        )
        en_us = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/en_us.json")
        )
        zh_tw = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/zh_tw.json")
        )

        self.assert_descriptor_driven_fixture(
            build, "actuallyadditions", "actuallyadditionsFixture", 14
        )
        self.assertNotIn('modId="actuallyadditions"', metadata)
        self.assertIn('"actuallyadditions"', module_index)
        self.assertIn("maven.modrinth:actually-additions:iNeJmgFj", module_index)
        self.assertIn(
            "072451bb6069025e255a39216edc0f892cda00c12b9905b552e7dd4631d44a41",
            module_index,
        )
        self.assertEqual(
            module_index.count("maven.modrinth:actually-additions:iNeJmgFj"),
            3,
            "auditArtifact, dependencies, and runtimeDependencies once each",
        )
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn("ActuallyadditionsCompat.register(MACHINES, RECIPES)", module)
        self.assertNotIn("import de.ellpeck.actuallyadditions.", module)
        self.assertIn("CrushingRecipe.class", compat)
        self.assertIn("PressingRecipe.class", compat)
        self.assertIn("FermentingRecipe.class", compat)
        self.assertNotIn("EmpowererRecipe.class", compat)
        self.assertNotIn("LaserRecipe.class", compat)
        self.assertNotIn("keysWithoutRegistries", compat)
        self.assertIn("recipe.getFirstChance() != 1.0F", compat)
        self.assertIn("presentableFluid(recipe.getOutput())", compat)
        self.assertIn("crushing_fractional_primary_stays_unsupported", fixture)
        self.assertIn("bucketless_fluid_recipe_stays_unsupported", fixture)
        self.assertIn("crushing_guaranteed_secondary_emits_both_outputs", fixture)
        self.assertIn("crushing_guaranteed_secondary_capacity_is_atomic_noop", fixture)
        self.assertRegex(
            compat,
            r"private static boolean exact\(Ingredient ingredient\) \{\s*"
            r"return ingredient != null\s*"
            r"&& !ingredient\.isEmpty\(\)\s*"
            r"&& ingredient\.isSimple\(\)\s*"
            r"&& Arrays\.stream\(ingredient\.getItems\(\)\)\.anyMatch\("
            r"stack -> !stack\.isEmpty\(\)\);",
        )
        self.assertIn("IsolatedRecipeInventoryEvidence", fixture)
        self.assertIn(
            "IsolatedRecipeInventoryEvidence.assertMatchesDescriptor",
            fixture,
        )
        self.assertIn('modId="actuallyadditions"', fixture_metadata)
        self.assertIn("representative CI", compatibility_doc)
        self.assertIn("Crushing", compatibility_doc)
        self.assertIn("Pressing", compatibility_doc)
        self.assertIn("Fermenting", compatibility_doc)
        self.assertNotIn("peer digest sync", compatibility_doc)
        self.assertIn("isolated recipe-inventory digest", compatibility_doc)
        self.assertEqual(
            "Crushing", en_us["gui.auto_storage.station.actuallyadditions_crushing"]
        )
        self.assertEqual(
            "Pressing", en_us["gui.auto_storage.station.actuallyadditions_pressing"]
        )
        self.assertEqual(
            "Fermenting", en_us["gui.auto_storage.station.actuallyadditions_fermenting"]
        )
        self.assertEqual(
            "粉碎", zh_tw["gui.auto_storage.station.actuallyadditions_crushing"]
        )
        self.assertEqual(
            "壓榨", zh_tw["gui.auto_storage.station.actuallyadditions_pressing"]
        )
        self.assertEqual(
            "發酵", zh_tw["gui.auto_storage.station.actuallyadditions_fermenting"]
        )
        self.assertEqual(
            "Guaranteed Crushing secondary did not emit both exact outputs",
            contract["verification"]["evidence"]["catalyst_tool_remainder_exact"][0]["marker"],
        )

    def test_mysticalagriculture_compat_is_optional_and_isolated(self):
        build = self.read_required("build.gradle")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("mysticalagriculture")
        module = self.read_required(
            "src/compat/mysticalagriculture/java/com/swear/autostorage/compat/"
            "mysticalagriculture/MysticalagricultureCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/mysticalagriculture/java/com/swear/autostorage/compat/"
            "mysticalagriculture/MysticalagricultureCompat.java"
        )
        fixture_metadata = self.read_required(
            "src/mysticalagricultureFixture/resources/META-INF/neoforge.mods.toml"
        )
        fixture_tests = self.read_required(
            "src/mysticalagricultureFixture/java/com/swear/autostorage/fixture/"
            "mysticalagriculture/MysticalagricultureIntegrationGameTests.java"
        )
        compatibility_doc = self.read_required(
            "docs/mysticalagriculture-compatibility.md"
        )
        contract = self.read_required("compat/contracts/mysticalagriculture.json")
        lang_en = self.read_required(
            "src/main/resources/assets/auto_storage/lang/en_us.json"
        )
        lang_zh = self.read_required(
            "src/main/resources/assets/auto_storage/lang/zh_tw.json"
        )

        self.assertIn(
            '"maven.modrinth:mystical-agriculture:izIaJr8V"',
            module_index,
        )
        self.assertIn(
            "d67bb701fbe4ade2efeb0aafd477f569b5e6a5a7c8ac696a8a1f658f8477eb99",
            module_index,
        )
        self.assert_descriptor_driven_fixture(
            build, "mysticalagriculture", "mysticalagricultureFixture", 8
        )
        self.assertNotIn('modId="mysticalagriculture"', metadata)
        self.assertEqual(["mysticalagriculture"], json.loads(module_index)["requires"])
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn("MysticalagricultureCompat.register(MACHINES, RECIPES)", module)
        self.assertNotIn("import com.blakebr0.mysticalagriculture.", module)
        self.assertIn("ReprocessorRecipe.class", compat)
        self.assertIn("ModRecipeTypes.REPROCESSOR", compat)
        self.assertNotIn("InfusionRecipe.class", compat)
        self.assertNotIn("AwakeningRecipe.class", compat)
        self.assertNotIn("EnchanterRecipe.class", compat)
        self.assertNotIn("SoulExtractionRecipe.class", compat)
        self.assertNotIn("SouliumSpawnerRecipe.class", compat)
        self.assertIn('modId="mysticalagriculture"', fixture_metadata)
        self.assertIn('versionRange="[8.0,)"', fixture_metadata)
        self.assertNotIn("8.0.27", fixture_metadata)
        self.assertIn("IsolatedRecipeInventoryEvidence", fixture_tests)
        self.assertIn(
            "IsolatedRecipeInventoryEvidence.assertMatchesDescriptor",
            fixture_tests,
        )
        self.assertIn("COMPAT_KIT_PRESENT_TARGET_LOAD_ONCE", fixture_tests)
        self.assertIn("COMPAT_KIT_INGREDIENT_SHORTAGE_ATOMIC", fixture_tests)
        self.assertIn("COMPAT_KIT_DESTINATION_CAPACITY_ATOMIC", fixture_tests)
        self.assertIn("COMPAT_KIT_REJECTED_FAMILY_FAIL_CLOSED", fixture_tests)
        self.assertIn("COMPAT_KIT_CATALYST_TOOL_REMAINDER_EXACT", fixture_tests)
        self.assertIn("seed/reprocessor/inferium", fixture_tests)
        self.assertRegex(
            fixture_tests,
            r"reprocessor_consumes_seed_fe_and_work[\s\S]*?"
            r"MachineEnergyTable\.isInstalled[\s\S]*?"
            r"COMPAT_KIT_CATALYST_TOOL_REMAINDER_EXACT",
        )
        self.assertNotRegex(
            fixture_tests,
            r"rejected_machine_families_fail_closed[\s\S]*?"
            r"COMPAT_KIT_CATALYST_TOOL_REMAINDER_EXACT",
        )
        contract_json = json.loads(contract)
        self.assertEqual(
            "COMPAT_KIT_CATALYST_TOOL_REMAINDER_EXACT",
            contract_json["verification"]["evidence"][
                "catalyst_tool_remainder_exact"
            ][0]["marker"],
        )
        self.assertIn("reprocessor_consumes_seed_fe_and_work", fixture_tests)
        self.assertIn('"status": "accepted"', contract)
        self.assertIn("ReprocessorRecipe", contract)
        self.assertIn(
            '"gui.auto_storage.station.mysticalagriculture_reprocessor": '
            '"Seed Reprocessing"',
            lang_en,
        )
        self.assertIn(
            '"gui.auto_storage.station.mysticalagriculture_reprocessor": '
            '"種子再處理"',
            lang_zh,
        )
        self.assertIn("representative CI/audit evidence", compatibility_doc)
        self.assertIn("does not impose", compatibility_doc)
        self.assertIn(
            "d67bb701fbe4ade2efeb0aafd477f569b5e6a5a7c8ac696a8a1f658f8477eb99",
            compatibility_doc,
        )
        self.assertIn("outcome **B**", compatibility_doc)
        self.assertIn("isolated recipe-inventory digest", compatibility_doc)
        self.assertNotIn("peer digest sync", compatibility_doc)


    def test_theurgy_compat_is_optional_and_isolated(self):
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("theurgy")
        module = self.read_required(
            "src/compat/theurgy/java/com/swear/autostorage/compat/"
            "theurgy/TheurgyCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/theurgy/java/com/swear/autostorage/compat/"
            "theurgy/TheurgyCompat.java"
        )
        fixture_metadata = self.read_required(
            "src/theurgyFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required("docs/theurgy-compatibility.md")
        build = self.read_required("build.gradle")

        self.assert_descriptor_driven_fixture(
            build, "theurgy", "theurgyFixture", 9
        )
        self.assertNotIn('modId="theurgy"', metadata)
        self.assertIn('"theurgy"', module_index)
        self.assertIn('"requires"', module_index)
        self.assertIn("maven.modrinth:theurgy:KvM1ocNj", module_index)
        self.assertIn(
            "6cbe0abe5fa53ba3d9308c7fe2b9a8f2df4d568f69fdb99a2fe6c6d1e59fdbc5",
            module_index,
        )
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn("TheurgyCompat.register(MACHINES, RECIPES)", module)
        self.assertNotIn("import com.klikli_dev.theurgy.", module)
        self.assertIn("CalcinationRecipe.class", compat)
        self.assertIn("DistillationRecipe.class", compat)
        self.assertIn("LiquefactionRecipe.class", compat)
        self.assertNotIn("IncubationRecipe.class", compat)
        self.assertNotIn("ReformationRecipe.class", compat)
        self.assertNotIn("AccumulationRecipe.class", compat)
        self.assertRegex(
            compat,
            r"Item item = BuiltInRegistries\.ITEM\.get\(itemId\);\s*"
            r"if \(item == Items\.AIR\) \{\s*"
            r"throw new IllegalStateException\(\"Missing Theurgy station item \" \+ itemId\);",
        )
        self.assertIn('modId="theurgy"', fixture_metadata)
        self.assertIn('versionRange="[1.73,)"', fixture_metadata)
        self.assertNotIn("1.73.1", fixture_metadata)
        self.assertIn("representative CI artifact", compatibility_doc)
        self.assertIn("not an exact player dependency pin", compatibility_doc)
        self.assertIn("Calcination", compatibility_doc)
        self.assertIn("Distillation", compatibility_doc)
        self.assertIn("Liquefaction", compatibility_doc)

    def test_hostilenetworks_fail_closed_boundary_is_locked(self):
        fixture = self.read_required(
            "src/hostilenetworksFixture/java/com/swear/autostorage/fixture/"
            "hostilenetworks/HostilenetworksIntegrationGameTests.java"
        )
        coexistence = self.read_required(
            "src/compatibilityMatrixFixture/java/com/swear/autostorage/fixture/"
            "compatibilitymatrix/CompatibilityMatrixGameTests.java"
        )
        docs = self.read_required("docs/hostilenetworks-compatibility.md")
        module = self.read_required(
            "src/compat/hostilenetworks/java/com/swear/autostorage/compat/"
            "hostilenetworks/HostilenetworksCompat.java"
        )
        descriptor = self.read_required("src/compat/hostilenetworks/compat-module.json")
        contract = self.read_required("compat/contracts/hostilenetworks.json")

        self.assertIn("IsolatedRecipeInventoryEvidence", fixture)
        self.assertIn(
            'helper.fail("Hostile Neural Networks mod is not loaded")',
            fixture,
        )
        self.assertIn(
            "Hostile Neural Networks unsafe recipe contract was registered",
            fixture,
        )
        self.assertIn(
            "Hostile Neural Networks living-matter vanilla crafting must stay supported",
            fixture,
        )
        self.assertIn(
            "Hostile Neural Networks simulation and loot fabricator must remain fail closed",
            fixture,
        )
        self.assertIn(
            'id.getNamespace().equals("hostilenetworks")',
            fixture,
        )
        self.assertIn(
            'id.getPath().startsWith("hostilenetworks")',
            fixture,
        )
        self.assertRegex(
            fixture,
            r"id\.getNamespace\(\)\.equals\(\"hostilenetworks\"\)\s*"
            r"\|\|\s*id\.getPath\(\)\.startsWith\(\"hostilenetworks\"\)",
        )
        self.assertIn(
            'manifest.assertCoexistence(helper, "Descriptor matrix coexistence")',
            coexistence,
        )
        self.assertNotIn(
            'id.getNamespace().equals("hostilenetworks")',
            coexistence,
        )
        self.assertNotIn(
            "Hostile Neural Networks fail-closed boundary changed",
            coexistence,
        )
        self.assertIn("outcome **C**", docs)
        self.assertIn("ca855354ff4d4e15f035911436d46a21721df92510463798ed6c5aef6a3038c6", docs)
        self.assertIn("Intentionally empty", module)
        self.assertNotIn("compat-kit scaffold is intentionally RED", module)
        self.assertNotIn("compat-kit scaffold is intentionally RED", fixture)
        self.assertIn('"hostilenetworks"', descriptor)
        self.assertIn(
            "ca855354ff4d4e15f035911436d46a21721df92510463798ed6c5aef6a3038c6",
            descriptor,
        )
        descriptor_data = json.loads(descriptor)
        self.assertEqual(
            [
                "maven.modrinth:hostile-neural-networks:ZbsbtrNE",
                "curse.maven:jade-324717:5884231",
                "maven.modrinth:placebo:1Ypo4tf4",
                "mezz.jei:jei-1.21.1-common-api:19.27.0.340",
            ],
            descriptor_data["dependencies"],
        )
        self.assertEqual(
            [
                "maven.modrinth:hostile-neural-networks:ZbsbtrNE",
                "maven.modrinth:placebo:1Ypo4tf4",
            ],
            descriptor_data["runtimeDependencies"],
        )
        rejected_descriptors = [
            "auto_storage:hostilenetworks_sim_chamber",
            "auto_storage:hostilenetworks_loot_fabricator",
            "auto_storage:hostilenetworks_data_center",
        ]
        self.assertEqual(
            rejected_descriptors,
            descriptor_data["matrix"]["rejectedDescriptors"],
        )
        contract_data = json.loads(contract)
        self.assertEqual(
            rejected_descriptors,
            contract_data["matrix"]["rejectedDescriptors"],
        )
        evidence = contract_data["verification"]["evidence"]
        self.assertEqual(
            "No-energy smelting must not consume cobblestone",
            evidence["ingredient_shortage_atomic"][0]["marker"],
        )
        self.assertEqual(
            "Failed typed family commit partially mutated resources",
            evidence["destination_capacity_atomic"][0]["marker"],
        )
        self.assertEqual(
            "One-item-short Storage capacity must reject the whole batch before mutation",
            evidence["checked_overflow_atomic"][0]["marker"],
        )
        self.assertIn("only_vanilla_crafting_recipes_are_exposed", fixture)
        self.assertIn(
            "Hostile Neural Networks exposed a non-vanilla recipe class",
            fixture,
        )
        self.assertIn(
            "ca855354ff4d4e15f035911436d46a21721df92510463798ed6c5aef6a3038c6",
            contract,
        )
        self.assertIn("Descriptor matrix coexistence", contract)
        self.assertNotIn(
            "Hostile Neural Networks fail-closed boundary changed",
            contract,
        )

    def test_productivemetalworks_registry_checks_cover_namespace_and_path(self):
        fixture = self.read_required(
            "src/productivemetalworksFixture/java/com/swear/autostorage/fixture/"
            "productivemetalworks/ProductivemetalworksIntegrationGameTests.java"
        )
        coexistence = self.read_required(
            "src/compatibilityMatrixFixture/java/com/swear/autostorage/fixture/"
            "compatibilitymatrix/CompatibilityMatrixGameTests.java"
        )

        self.assertIn(
            'helper.fail("Productive Metalworks mod is not loaded")',
            fixture,
        )
        self.assertRegex(
            fixture,
            r'if \(types\.size\(\) != 4\) \{\s*'
            r'helper\.fail\("Expected 4 unique audited Productive Metalworks '
            r'recipe types, but found " \+ types\.size\(\)\);',
        )
        self.assertIn(
            'id.getNamespace().equals("productivemetalworks")',
            fixture,
        )
        self.assertIn(
            'id.getPath().startsWith("productivemetalworks_")',
            fixture,
        )
        self.assertRegex(
            fixture,
            r"id\.getNamespace\(\)\.equals\(\"productivemetalworks\"\)\s*"
            r"\|\|\s*id\.getPath\(\)\.startsWith\(\"productivemetalworks_\"\)",
        )
        self.assertIn(
            'manifest.assertCoexistence(helper, "Descriptor matrix coexistence")',
            coexistence,
        )
        self.assertNotIn(
            'id.getNamespace().equals("productivemetalworks")',
            coexistence,
        )
    def test_createaddition_compat_is_optional_and_isolated(self):
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("createaddition")
        module = self.read_required(
            "src/compat/createaddition/java/com/swear/autostorage/compat/"
            "createaddition/CreateadditionCompatModule.java"
        )
        compat = self.read_required(
            "src/compat/createaddition/java/com/swear/autostorage/compat/"
            "createaddition/CreateadditionCompat.java"
        )
        fixture = self.read_required(
            "src/createadditionFixture/java/com/swear/autostorage/fixture/"
            "createaddition/CreateadditionIntegrationGameTests.java"
        )
        fixture_metadata = self.read_required(
            "src/createadditionFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required("docs/createaddition-compatibility.md")
        build = self.read_required("build.gradle")
        contract = json.loads(
            self.read_required("compat/contracts/createaddition.json")
        )
        audit = json.loads(
            self.read_required("compat/audits/createaddition/1.6.0.json")
        )
        descriptor = json.loads(
            self.read_required("src/compat/createaddition/compat-module.json")
        )
        en_us = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/en_us.json")
        )
        zh_tw = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/zh_tw.json")
        )

        self.assert_descriptor_driven_fixture(
            build, "createaddition", "createadditionFixture", 9
        )
        self.assertNotIn('modId="createaddition"', metadata)
        self.assertIn('"createaddition"', module_index)
        self.assertIn("maven.modrinth:createaddition:qPr8V4G2", module_index)
        self.assertIn(
            "41876c3780b70365a1848994d146a73423cc19fbe86485885795d9e7d855e7e9",
            module_index,
        )
        self.assertEqual(17, audit["scanner_format"])
        self.assertEqual(
            [
                "com.mrh0.createaddition.recipe.charging.ChargingRecipe",
                "com.mrh0.createaddition.recipe.liquid_burning.LiquidBurningRecipe",
                "com.mrh0.createaddition.recipe.rolling.RollingRecipe",
            ],
            sorted(
                candidate["class"]
                for candidate in audit["candidates"]["recipe_classes"]
            ),
        )
        self.assertEqual(contract["matrix"], descriptor["matrix"])
        self.assertEqual(
            "57916d79470225dd3db82f96c7e5c70a87192df1930c8d8efa4768add46fd0a3",
            descriptor["matrix"]["recipeInventory"]["sha256"],
        )
        self.assertEqual(9, contract["verification"]["expected_game_tests"])
        self.assertEqual(9, descriptor["expectedTests"])
        self.assertIn("IsolatedRecipeInventoryEvidence", fixture)
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertIn("CreateadditionCompat.register(MACHINES, RECIPES)", module)
        self.assertIn("RollingRecipe.class", compat)
        self.assertIn("ChargingRecipe.class", compat)
        self.assertNotIn("LiquidBurningRecipe.class", compat)
        self.assertEqual(
            2,
            compat.count("RecipeFamilyFactories.dynamicDeterministicResources("),
        )
        self.assertNotIn(
            "RecipeFamilyFactories.deterministicResources(",
            compat,
        )
        self.assertIn("getFluidIngredients()", compat)
        self.assertIn("getFluidResults()", compat)
        self.assertIn("getRollableResults()", compat)
        self.assertIn("getChance() == 1.0F", compat)
        self.assertIn("rolling_returns_item_remainder_and_consumes_duration", fixture)
        self.assertIn(
            "Create Crafts & Additions rolling remainder transaction was wrong",
            fixture,
        )
        self.assertIn("recipePresent(helper, fixtureRecipe(\"chance_rolling\"))", fixture)
        self.assertIn("recipePresent(helper, fixtureRecipe(\"chance_charging\"))", fixture)
        for recipe_name in (
            "fluid_result_rolling",
            "fluid_ingredient_rolling",
            "fluid_result_charging",
            "fluid_ingredient_charging",
        ):
            self.assertRegex(
                fixture,
                rf'upstreamAndAutoStorageReject\(\s*fixtureRecipe\("{recipe_name}"\)',
            )
            self.assertFalse(
                (ROOT / "src/createadditionFixture/resources/data/"
                 "auto_storage_createaddition_fixture/recipe/"
                 f"{recipe_name}.json").exists()
            )
        self.assertIn("chance_rolling", fixture)
        self.assertIn("fluid_result_rolling", fixture)
        self.assertIn("fluid_ingredient_rolling", fixture)
        self.assertIn("chance_charging", fixture)
        self.assertIn("fluid_result_charging", fixture)
        self.assertIn("fluid_ingredient_charging", fixture)
        self.assertEqual(
            "Create Crafts & Additions rolling remainder transaction was wrong",
            contract["verification"]["evidence"]["catalyst_tool_remainder_exact"][0][
                "marker"
            ],
        )
        self.assertEqual(
            "src/createadditionFixture/java/com/swear/autostorage/fixture/"
            "createaddition/CreateadditionIntegrationGameTests.java",
            contract["verification"]["evidence"]["catalyst_tool_remainder_exact"][0][
                "source"
            ],
        )
        self.assertEqual(
            "Typed family changed the wrong exact resources or output amounts",
            contract["verification"]["evidence"]["multi_output_merge_exact"][0][
                "marker"
            ],
        )
        self.assertEqual(
            "src/recipeAddonFixture/java/com/swear/autostorage/fixture/recipe/"
            "RecipeFamilyIntegrationTests.java",
            contract["verification"]["evidence"]["multi_output_merge_exact"][0][
                "source"
            ],
        )
        self.assertEqual(
            "runRecipeAddonGameTestServer",
            contract["verification"]["evidence"]["multi_output_merge_exact"][0][
                "task"
            ],
        )
        self.assertIn('modId="createaddition"', fixture_metadata)
        self.assertIn("maven.modrinth:create:UjX6dr61", module_index)
        self.assertIn("representative CI", compatibility_doc)
        self.assertIn("scanner format `17`", compatibility_doc)
        self.assertEqual(
            "Rolling",
            en_us["gui.auto_storage.station.createaddition_rolling_mill"],
        )
        self.assertEqual(
            "Charging",
            en_us["gui.auto_storage.station.createaddition_tesla_coil"],
        )
        self.assertEqual(
            "軋制",
            zh_tw["gui.auto_storage.station.createaddition_rolling_mill"],
        )
        self.assertEqual(
            "充電",
            zh_tw["gui.auto_storage.station.createaddition_tesla_coil"],
        )

    def test_createaddition_charging_work_rejects_non_positive_rate(self):
        compat = self.read_required(
            "src/compat/createaddition/java/com/swear/autostorage/compat/"
            "createaddition/CreateadditionCompat.java"
        )
        match = re.search(
            r"private static long chargingWork\(ChargingRecipe recipe\)\s*"
            r"\{(?P<body>.*?)\n    \}",
            compat,
            re.S,
        )
        self.assertIsNotNone(match, "missing CreateadditionCompat.chargingWork")
        body = match.group("body")
        self.assertRegex(
            body,
            r"long rate = chargeRate\(recipe\);",
        )
        self.assertRegex(
            body,
            r"if\s*\(\s*rate\s*<=\s*0L?\s*\)",
        )
        self.assertIn("Math.addExact(energy, rate - 1L) / rate", body)
        self.assertNotIn("Math.max(1L, chargeRate", compat)
        self.assertNotIn("Math.max(1L, rate", body)


    def test_createaddition_reloadable_rates_do_not_change_candidate_eligibility(self):
        compat = self.read_required(
            "src/compat/createaddition/java/com/swear/autostorage/compat/"
            "createaddition/CreateadditionCompat.java"
        )
        rolling = self.java_block(
            compat,
            r"\bprivate\s+static\s+boolean\s+supportsRolling\s*\(",
            "Createaddition rolling eligibility",
        )
        charging = self.java_block(
            compat,
            r"\bprivate\s+static\s+boolean\s+supportsCharging\s*\(",
            "Createaddition charging eligibility",
        )

        self.assertNotIn("rollingDuration()", rolling)
        self.assertNotIn("chargeRate(recipe)", charging)
        self.assertIn("RecipeFamilyCost.stationWork(rollingDuration())", compat)
        self.assertIn("RecipeFamilyCost.stationWork(chargingWork(recipe))", compat)

    def test_createaddition_checked_overflow_fixture_asserts_complete_atomic_no_op(self):
        fixture = self.read_required(
            "src/createadditionFixture/java/com/swear/autostorage/fixture/"
            "createaddition/CreateadditionIntegrationGameTests.java"
        )
        method = self.java_block(
            fixture,
            r"\bpublic\s+static\s+void\s+checked_overflow_rejects_long_max_seed\s*\(",
            "Createaddition checked-overflow GameTest",
        )

        self.assertIn('itemCount(context.core(), Items.IRON_INGOT) != 1', method)
        self.assertIn(
            "context.core().getStationWork(ROLLING_MILL) != work",
            method,
        )

    def test_createaddition_stale_holder_fixture_selects_then_invalidates_real_recipe(self):
        fixture = self.read_required(
            "src/createadditionFixture/java/com/swear/autostorage/fixture/"
            "createaddition/CreateadditionIntegrationGameTests.java"
        )
        method = self.java_block(
            fixture,
            r"\bpublic\s+static\s+void\s+stale_holder_is_atomic\s*\(",
            "Createaddition stale-holder GameTest",
        )

        self.assertNotIn("missing_stale_holder", method)
        self.assertIn("IRON_ROD", method)
        self.assertIn("replaceRecipes", method)
        self.assertIn("CraftingDestination.NONE", method)
        self.assertIn("CraftingDestination.STORAGE", method)

    def test_createaddition_dedupes_ingredient_alternatives_by_canonical_key(self):
        compat = self.read_required(
            "src/compat/createaddition/java/com/swear/autostorage/compat/"
            "createaddition/CreateadditionCompat.java"
        )
        method = self.java_block(
            compat,
            r"\bprivate\s+static\s+TypedRecipeInput\s+consumedWithRemainder\s*\(",
            "Createaddition consumedWithRemainder",
        )
        representatives = self.java_block(
            compat,
            r"\bprivate\s+static\s+List<ItemStack>\s+representatives\s*\(",
            "Createaddition representatives",
        )

        self.assertIn("LinkedHashMap", method)
        self.assertIn("StorageResourceKey.item", method)
        self.assertIn("putIfAbsent", method)
        self.assertNotRegex(
            representatives,
            r"\.map\(\s*stack\s*->\s*stack\.copyWithCount\(1\)\s*\)\s*\.distinct\(\)",
            "ItemStack.distinct cannot normalize duplicate ingredient alternatives",
        )

    def test_createaddition_dynamic_cost_refresh_fails_closed_without_throwing(self):
        family = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeFamily.java"
        )
        resolve = self.java_block(
            family,
            r"\bpublic\s+List<RecipeAdapterMatch\.Contract>\s+resolveVariants\s*\(",
            "RecipeFamily.resolveVariants",
        )
        contract = self.java_block(
            family,
            r"\bpublic\s+RecipeAdapterMatch\.Contract\s+contract\s*\(",
            "RecipeFamily.contract",
        )

        self.assertIn("IllegalArgumentException", resolve)
        self.assertIn("return List.of()", resolve)
        self.assertIn("cacheTypedPlan", contract)
        self.assertIn("Cost.free()", contract)

    def test_createaddition_notes_distinguish_eligibility_from_live_rate_cost(self):
        notes = self.read_required("docs/notes.md")
        compatibility = self.read_required("docs/createaddition-compatibility.md")

        self.assertNotIn("正確作法是eligibility已要求正rate", notes)
        self.assertIn("maxChargeRate", notes)
        self.assertIn("cost", notes.lower())
        self.assertNotIn(
            "cost evaluation fail-closes with\n  `IllegalArgumentException`",
            compatibility,
        )
        self.assertIn("no usable variant", compatibility)

    def test_immersiveengineering_compat_is_optional_fail_closed(self):
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        module_index = self.read_compat_module("immersiveengineering")
        module = self.read_required(
            "src/compat/immersiveengineering/java/com/swear/autostorage/compat/"
            "immersiveengineering/ImmersiveengineeringCompatModule.java"
        )
        fixture = self.read_required(
            "src/immersiveengineeringFixture/java/com/swear/autostorage/fixture/"
            "immersiveengineering/ImmersiveengineeringIntegrationGameTests.java"
        )
        fixture_metadata = self.read_required(
            "src/immersiveengineeringFixture/resources/META-INF/neoforge.mods.toml"
        )
        compatibility_doc = self.read_required(
            "docs/immersiveengineering-compatibility.md"
        )
        contract = self.read_required("compat/contracts/immersiveengineering.json")
        audit = self.read_required(
            "compat/audits/immersiveengineering/12.4.2-194.json"
        )
        build = self.read_required("build.gradle")

        self.assert_descriptor_driven_fixture(
            build, "immersiveengineering", "immersiveengineeringFixture", 8
        )
        self.assertNotIn('modId="immersiveengineering"', metadata)
        self.assertIn('"immersiveengineering"', module_index)
        self.assertIn('"requires"', module_index)
        self.assertIn("maven.modrinth:immersiveengineering:uNRARSH2", module_index)
        self.assertIn(
            "45942985a4a4aebf265b8e22a0c54a96208637471f36f2532ff5d4911322debc",
            module_index,
        )
        descriptor = json.loads(module_index)
        self.assertEqual([], descriptor["matrix"]["descriptors"])
        self.assertEqual([], descriptor["matrix"]["acceptedRecipes"])
        self.assertEqual(
            ["immersiveengineering"],
            descriptor["matrix"]["recipeInventory"]["namespaces"],
        )
        self.assertRegex(
            descriptor["matrix"]["recipeInventory"]["sha256"],
            r"^[0-9a-f]{64}$",
        )
        self.assertNotEqual(
            "0" * 64,
            descriptor["matrix"]["recipeInventory"]["sha256"],
        )
        self.assertIn("implements AutoStorageCompatModule", module)
        self.assertNotIn("import blusunrize.immersiveengineering.", module)
        self.assertIn('modId="immersiveengineering"', fixture_metadata)
        self.assertIn(
            'helper.fail("Immersive Engineering mod is not loaded")',
            fixture,
        )
        self.assertIn("IsolatedRecipeInventoryEvidence", fixture)
        self.assertIn("alloysmelter/electrum", fixture)
        self.assertIn("arcfurnace/dust_iron", fixture)
        self.assertIn(
            'id.getNamespace().equals("immersiveengineering")',
            fixture,
        )
        self.assertIn(
            'id.getPath().startsWith("immersiveengineering_")',
            fixture,
        )
        self.assertRegex(
            fixture,
            r"id\.getNamespace\(\)\.equals\(\"immersiveengineering\"\)\s*"
            r"\|\|\s*id\.getPath\(\)\.startsWith\(\"immersiveengineering_\"\)",
        )
        self.assertIn("outcome **C**", compatibility_doc)
        self.assertIn(
            "45942985a4a4aebf265b8e22a0c54a96208637471f36f2532ff5d4911322debc",
            compatibility_doc,
        )
        self.assertIn("recipeInventory", compatibility_doc)
        self.assertIn('"status": "rejected"', contract)
        self.assertNotIn('"status": "accepted"', contract)
        self.assertIn('"matrix"', contract)
        self.assertIn("immersiveengineering", audit)

    def test_pneumaticcraft_fixture_locks_unsafe_contracts_out(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")
        fixture_metadata = self.read_required(
            "src/pneumaticCraftFixture/resources/META-INF/neoforge.mods.toml"
        )
        fixture_tests = self.read_required(
            "src/pneumaticCraftFixture/java/com/swear/autostorage/fixture/"
            "pneumaticcraft/PneumaticCraftIntegrationGameTests.java"
        )
        compatibility_doc = self.read_required(
            "docs/pneumaticcraft-compatibility.md"
        )

        self.assertRegex(
            properties,
            r"(?m)^pneumaticcraft_ci_version=[A-Za-z0-9]+$",
        )
        self.assertIn(
            'pneumaticCraftFixtureCompileOnly(\n'
            '            "maven.modrinth:pneumaticcraft-repressurized:'
            '${pneumaticcraft_ci_version}")',
            build,
        )
        self.assertIn(
            'pneumaticCraftFixtureRuntimeOnly "maven.modrinth:'
            'pneumaticcraft-repressurized:${pneumaticcraft_ci_version}"',
            build,
        )
        self.assertNotRegex(
            build,
            r'(?m)^\s*runtimeOnly\s+"maven\.modrinth:'
            r'pneumaticcraft-repressurized:',
        )
        self.assertRegex(
            build,
            r"(?s)pneumaticCraftFixture\s*\{.*?"
            r"runtimeClasspath\s*\+=\s*output\s*\+\s*"
            r"sourceSets\.main\.runtimeClasspath.*?\}",
        )
        self.assertIn(
            "tasks.named('runPneumaticCraftGameTestServer').configure",
            build,
        )
        self.assertIn("All 9 required tests passed", build)
        self.assertNotIn('modId="pneumaticcraft"', metadata)
        self.assertIn('modId="pneumaticcraft"', fixture_metadata)
        self.assertIn('versionRange="[8.2,)"', fixture_metadata)
        self.assertNotIn("8.2.22", fixture_metadata)
        self.assertIn("BasicAirHandler", fixture_tests)
        self.assertIn('pnc("air")', fixture_tests)
        self.assertIn("pressure_chamber", fixture_tests)
        self.assertIn("thermo_plant", fixture_tests)
        self.assertIn("fluid_mixer", fixture_tests)
        self.assertIn("assembly", fixture_tests)
        self.assertIn("refinery", fixture_tests)
        self.assertIn("heat_frame_cooling", fixture_tests)
        self.assertIn("explosion_crafting", fixture_tests)
        self.assertIn("zero production recipe families", compatibility_doc)
        self.assertIn("not an exact player dependency pin", compatibility_doc)

    def test_oritech_energy_per_tick_reads_live_config_without_null_fallback(self):
        compat = self.read_required(
            "src/compat/oritech/java/com/swear/autostorage/compat/"
            "oritech/OritechCompat.java"
        )
        fixture = self.read_required(
            "src/oritechFixture/java/com/swear/autostorage/fixture/"
            "oritech/OritechIntegrationGameTests.java"
        )

        self.assertIn("MachineVariant.derived(", compat)
        self.assertRegex(
            compat,
            r"private static int energyPerTick\(\)\s*\{\s*"
            r"return OritechConfig\.processingMachines\.pulverizerData"
            r"\.energyPerTick\.get\(\);\s*\}",
        )
        self.assertNotIn("processingMachines != null", compat)
        self.assertNotIn("pulverizerData != null", compat)
        self.assertNotIn("return 32;", compat)
        self.assertNotIn("value != null ? value : 32", compat)
        self.assertIn(
            "OritechConfig.processingMachines == null",
            fixture,
        )
        self.assertIn(
            "OritechConfig.processingMachines.pulverizerData == null",
            fixture,
        )
        self.assertIn(
            "OritechConfig.processingMachines.pulverizerData.energyPerTick == null",
            fixture,
        )

    def test_oritech_fluid_outputs_use_typed_architectury_api(self):
        compat = self.read_required(
            "src/compat/oritech/java/com/swear/autostorage/compat/"
            "oritech/OritechCompat.java"
        )
        audit = json.loads(self.read_required(
            "compat/audits/oritech/1.2.9.json"
        ))
        descriptor = json.loads(self.read_required(
            "src/compat/oritech/compat-module.json"
        ))
        build = self.read_required("build.gradle")

        self.assertIn("import dev.architectury.fluid.FluidStack;", compat)
        self.assertIn("List<FluidStack> outputs = recipe.getFluidOutputs();", compat)
        self.assertIn("stack == null || !stack.isEmpty()", compat)
        self.assertNotIn("getMethod(\"getFluidOutputs\")", compat)
        self.assertNotIn(".invoke(recipe)", compat)
        self.assertIn(
            {
                "dependency": "maven.modrinth:architectury-api:ZxYGwlk0",
                "sha256": "5ec578f814e8cca87aeffa6e424032e78d9ea5ea6b603dd834c2dc13c31141ee",
                "size": 584004,
            },
            audit["ancestry_dependencies"],
        )
        self.assertEqual(
            [
                "maven.modrinth:oritech:gMBPdWrE",
                "maven.modrinth:architectury-api:ZxYGwlk0",
            ],
            descriptor["dependencies"],
        )
        self.assertNotIn("compatOritechCompileOnly", build)

    def test_prism_gui_support_pack_stages_macfix_and_optional_mods_without_player_dependency_pins(self):
        build = self.read_required("build.gradle")
        properties = self.read_required("gradle.properties")
        metadata = self.read_required("src/main/templates/META-INF/neoforge.mods.toml")

        self.assertRegex(properties, r"(?m)^macfix_gui_version=0\.1\.0$")
        self.assertRegex(
            properties,
            r"(?m)^macfix_gui_sha256=79904d59892c4c5384811a384f3ce88aa5b3d6e8224dbde1b78dc2f80020080c$",
        )
        self.assertIn(
            '../macfix/build/libs/macfix-${macfix_gui_version}.jar',
            build,
        )
        self.assertIn('rename { "macfix-gui-test.jar" }', build)
        self.assertIn("MacFix GUI artifact SHA-256 mismatch", build)
        self.assertRegex(properties, r"(?m)^tmrv_ci_version=pEhG9g9P$")
        self.assertNotRegex(properties, r"(?m)^jei_ci_version=")
        self.assertIn('prismGuiTmrv "maven.modrinth:tmrv:${tmrv_ci_version}"', build)
        self.assertIn(
            'prismGuiMekanism "maven.modrinth:mekanism:${mekanism_ci_version}"',
            build,
        )
        self.assertIn(
            'prismGuiBotania "vazkii.botania:botania-neoforge-1.21.1:${botania_ci_version}"',
            build,
        )
        self.assertIn(
            'prismGuiCurios "top.theillusivec4.curios:curios-neoforge:${botania_curios_ci_version}"',
            build,
        )
        expected_batched_dependencies = [
            'prismGuiModernIndustrialization "maven.modrinth:modern-industrialization:${modern_industrialization_ci_version}"',
            'prismGuiGuideMe "maven.modrinth:guideme:${guideme_ci_version}"',
            'prismGuiArsNouveau "maven.modrinth:ars-nouveau:${ars_nouveau_ci_version}"',
            'prismGuiGeckoLib "maven.modrinth:geckolib:${geckolib_ci_version}"',
            'prismGuiPowah "maven.modrinth:powah:${powah_ci_version}"',
            'prismGuiClothConfig "maven.modrinth:cloth-config:${powah_cloth_config_ci_version}"',
            'prismGuiIndustrialForegoing "maven.modrinth:industrial-foregoing:${industrial_foregoing_ci_version}"',
            'prismGuiTitanium "maven.modrinth:titanium:${titanium_ci_version}"',
            'prismGuiCreate "maven.modrinth:create:${create_ci_version}"',
        ]
        for dependency in expected_batched_dependencies:
            self.assertIn(dependency, build)
        self.assertIn('rename { "tmrv-gui-test.jar" }', build)
        self.assertIn('rename { "mekanism-gui-test.jar" }', build)
        self.assertIn('rename { "botania-gui-test.jar" }', build)
        self.assertIn('rename { "curios-gui-test.jar" }', build)
        for filename in [
            "modern-industrialization-gui-test.jar",
            "guideme-gui-test.jar",
            "ars-nouveau-gui-test.jar",
            "geckolib-gui-test.jar",
            "powah-gui-test.jar",
            "cloth-config-gui-test.jar",
            "industrial-foregoing-gui-test.jar",
            "titanium-gui-test.jar",
            "create-gui-test.jar",
        ]:
            self.assertIn(f'rename {{ "{filename}" }}', build)
        self.assertEqual(1, build.count("prismGuiGuideMe \""))
        self.assertEqual(1, build.count("prismGuiCurios \""))
        self.assertNotIn("prismGuiPneumaticCraft", build)
        self.assertNotIn("pneumaticcraft-gui-test.jar", build)
        self.assertNotIn("prismGuiEvilCraft", build)
        self.assertNotIn("evilcraft-gui-test.jar", build)
        self.assertNotIn("prismGuiCyclopsCore", build)
        self.assertNotIn("cyclops-core-gui-test.jar", build)
        self.assertRegex(
            properties,
            r"(?m)^botania_curios_ci_version=9\.5\.1\+1\.21\.1$",
        )
        self.assertNotIn("prismGuiJei", build)
        self.assertNotIn("jei-gui-test.jar", build)
        self.assertNotRegex(build, r'(?m)^\s*runtimeOnly\s+"maven\.modrinth:tmrv:')
        self.assertNotIn('modId="tmrv"', metadata)
        self.assertNotIn('modId="macfix"', metadata)
        self.assertNotIn('modId="jei"', metadata)
        self.assertNotIn('modId="mekanism"', metadata)

    def test_active_docs_name_the_correct_macfix_project_and_local_pin(self):
        docs = "\n".join(
            self.read_required(path)
            for path in [
                "AGENTS.md",
                "README.md",
                "docs/macos-fullscreen-guide.md",
                "docs/notes.md",
                "docs/overview.md",
                "docs/plan.md",
                "docs/roadmap.md",
            ]
        )
        self.assertNotIn(
            "https://modrinth.com/mod/mac-input-fixes-neoforged",
            docs,
        )
        self.assertNotIn("MacOS Input Fixes (Neoforged)", docs)
        self.assertIn("https://modrinth.com/mod/macfix", docs)
        self.assertIn(
            "79904d59892c4c5384811a384f3ce88aa5b3d6e8224dbde1b78dc2f80020080c",
            docs,
        )

    def test_active_roadmap_does_not_keep_superseded_merge_statuses(self):
        roadmap = self.read_required("docs/roadmap.md")
        self.assertEqual(
            1,
            roadmap.count("GitHub #68 Create Aquatic Ambitions Compat Kit"),
        )
        self.assertEqual(
            1,
            roadmap.count("GitHub #65／PR #73 Advanced AE Compat Kit"),
        )
        self.assertNotIn("GitHub review, remote CI, and merge remain", roadmap)

    def test_items_share_the_universal_live_transaction_ledger(self):
        record = self.read_required(
            "src/main/java/com/swear/autostorage/CoreStorageRecord.java"
        )
        core = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java"
        )
        bridge = self.read_required(
            "src/main/java/com/swear/autostorage/StorageResourceBridge.java"
        )
        self.assertNotIn("Object2LongOpenHashMap<ItemKey>", record)
        self.assertNotIn("Object2LongOpenHashMap<ItemKey>", core)
        self.assertNotIn("private Map<Item,", record)
        self.assertNotRegex(
            core,
            r"private\s+(?:final\s+)?Map<ItemKey,\s*Long>\s+(?:items|storage)\b",
        )
        self.assertIn("private StorageResourceLedger resourceLedger", core)
        self.assertIn("static final ResourceLocation ITEM_KIND", bridge)
        self.assertIn("StorageResourceBridge.itemKey(key", core)
        self.assertIn("resourceLedger.applyExact", core)

    def test_exact_resource_transactions_only_copy_touched_entries(self):
        ledger = self.read_required(
            "src/main/java/com/swear/autostorage/StorageResourceLedger.java"
        )
        apply_exact = self.java_block(
            ledger,
            r"\bboolean\s+applyExact\s*\(",
            "StorageResourceLedger.applyExact",
        )
        self.assertNotIn("new HashMap<>(amounts)", apply_exact)
        self.assertNotIn("amounts.clear()", apply_exact)
        self.assertNotIn("amounts.putAll", apply_exact)

    def test_work_only_transactions_skip_capacity_type_recount(self):
        core = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java"
        )
        transaction = self.java_block(
            core,
            r"\bboolean\s+applyResourceTransaction\s*\(\s*"
            r"Map<StorageResourceKey,\s*Long>\s+deltas",
            "StorageCoreBlockEntity.applyResourceTransaction(Map)",
        )
        self.assertIn("boolean capacityTypesChanged", transaction)
        self.assertIn("if (capacityTypesChanged) refreshTypeCount();", transaction)

    def test_terminal_refresh_does_not_clear_and_rewrite_unchanged_visible_slots(self):
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalMenu.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        storage_refresh = self.java_block(
            storage,
            r"\bpublic\s+void\s+refreshDisplayItemsFiltered\s*\(",
            "StorageTerminalMenu.refreshDisplayItemsFiltered",
        )
        crafting_refresh = self.java_block(
            crafting,
            r"\bpublic\s+void\s+refreshDisplayItemsFiltered\s*\(",
            "CraftingTerminalMenu.refreshDisplayItemsFiltered",
        )
        self.assertNotIn("displayInventory.clearContent()", storage_refresh)
        self.assertNotIn("displayInventory.clearContent()", crafting_refresh)
        self.assertIn("replaceVisibleDisplayStacks", storage_refresh)
        self.assertIn("replaceVisibleDisplayStacks", crafting_refresh)
        crafting_entry = self.java_block(
            crafting,
            r"\bpublic\s+void\s+refreshDisplayItems\s*\(",
            "CraftingTerminalMenu.refreshDisplayItems",
        )
        self.assertIn("if (!page.isItemPage())", crafting_entry)
        self.assertIn("refreshDisplayMetadata(core)", crafting_entry)

    def test_passive_resource_updates_skip_recipe_preview_when_nothing_is_selected(self):
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        for method in [
            "onObservedStorageChanged",
            "onObservedEnergyChanged",
            "onObservedStationWorkChanged",
        ]:
            body = self.java_block(
                crafting,
                rf"\bprotected\s+void\s+{method}\s*\(",
                f"CraftingTerminalMenu.{method}",
            )
            self.assertIn("page.isItemPage() && !selectedOutput.isEmpty()", body)

    def test_recipe_family_registry_freezes_before_selftests(self):
        entrypoint = self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        )
        self.assertRegex(
            entrypoint,
            r"event\.enqueueWork\(\(\) -> \{\s*RecipeAdapters\.snapshot\(\);\s*"
            r"StorageResourceContainerStrategies\.snapshot\(\);\s*"
            r"StorageResourceBlockStrategies\.snapshot\(\);\s*"
            r"SelfTest\.runAll\(\);\s*}\);",
        )

    def test_recipe_renderer_boundary_keeps_emi_out_of_base_screen_and_native_path(self):
        interface = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeDiagramRenderer.java"
        )
        native = self.read_required(
            "src/main/java/com/swear/autostorage/NativeRecipeDiagramRenderer.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        setup = self.read_required(
            "src/main/java/com/swear/autostorage/ClientSetup.java"
        )
        bootstrap = self.read_required(
            "src/main/java/com/swear/autostorage/compat/EmiRecipeDiagramBootstrap.java"
        )

        for relative_path, text in [
            ("RecipeDiagramRenderer.java", interface),
            ("NativeRecipeDiagramRenderer.java", native),
            ("CraftingTerminalScreen.java", screen),
            ("ClientSetup.java", setup),
        ]:
            self.assertNotIn(
                "import dev.emi.", text,
                f"{relative_path} must not link EMI API classes",
            )
        self.assertIn('ModList.get().isLoaded("emi")', setup)
        self.assertIn("EmiRecipeDiagramBootstrap", setup)
        self.assertIn("RecipeDiagramRenderer", bootstrap)
        self.assertIn("EmiRecipeDiagramRenderer", bootstrap)
        self.assertIn("RecipeDiagramRenderer", screen)
        self.assertIn("NativeRecipeDiagramRenderer", screen)

    def test_emi_diagram_adapter_uses_only_public_recipe_widget_contracts(self):
        renderer = self.read_required(
            "src/main/java/com/swear/autostorage/compat/EmiRecipeDiagramRenderer.java"
        )

        for public_api in [
            "dev.emi.emi.api.EmiApi",
            "dev.emi.emi.api.recipe.EmiRecipe",
            "dev.emi.emi.api.widget.Widget",
            "dev.emi.emi.api.widget.WidgetHolder",
        ]:
            self.assertIn(public_api, renderer)
        for internal_api in [
            "dev.emi.emi.screen",
            "WidgetGroup",
            "RecipeScreen",
            "EmiScreenManager",
            "EmiRenderHelper",
        ]:
            self.assertNotIn(internal_api, renderer)
        self.assertRegex(renderer, r"implements\s+WidgetHolder")
        self.assertRegex(renderer, r"List<Widget>")
        self.assertIn("recipe.addWidgets(", renderer)
        self.assertIn("widget.render(", renderer)
        self.assertIn("widget.getTooltip(", renderer)
        self.assertIn("widget.mouseClicked(", renderer)
        self.assertIn("widget.keyPressed(", renderer)

    def test_emi_compat_sources_never_link_internal_packages(self):
        compat_root = ROOT / "src/main/java/com/swear/autostorage/compat"
        sources = "\n".join(path.read_text() for path in sorted(compat_root.glob("*.java")))

        self.assertNotIn("dev.emi.emi.bom", sources)
        self.assertNotIn("dev.emi.emi.screen", sources)
        self.assertNotIn("dev.emi.emi.runtime", sources)

    def test_emi_diagram_selection_is_exact_and_has_only_capability_fallbacks(self):
        setup = self.read_required(
            "src/main/java/com/swear/autostorage/ClientSetup.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        native = self.read_required(
            "src/main/java/com/swear/autostorage/NativeRecipeDiagramRenderer.java"
        )
        renderer = self.read_required(
            "src/main/java/com/swear/autostorage/compat/EmiRecipeDiagramRenderer.java"
        )

        self.assertIn("NativeRecipeDiagramRenderer", setup)
        self.assertIn('ModList.get().isLoaded("emi")', setup)
        self.assertIn("preferredRecipeDiagramRenderer", screen)
        self.assertIn("nativeRecipeDiagramRenderer", screen)
        self.assertRegex(
            screen,
            r"preferredRecipeDiagramRenderer\.supports\([^)]*\)\s*"
            r"\?\s*preferredRecipeDiagramRenderer\s*:\s*nativeRecipeDiagramRenderer",
        )
        self.assertIn("return true;", native)
        self.assertIn("RecipePresentationKind.AXE", renderer)
        self.assertIn("EmiApi.getRecipeManager()", renderer)
        self.assertIn("manager.getRecipe(presentation.recipeId())", renderer)
        self.assertIn("direct.getId()", renderer)
        self.assertIn("direct.getBackingRecipe()", renderer)
        self.assertIn("manager.getRecipesByOutput", renderer)
        self.assertIn("matchesPublicRecipe", renderer)
        self.assertIn("if (match != null && match != candidate) return null", renderer)
        self.assertIn("presentation.recipeId()", renderer)

    def test_emi_diagram_is_bounded_and_does_not_catch_into_native_rendering(self):
        renderer = self.read_required(
            "src/main/java/com/swear/autostorage/compat/EmiRecipeDiagramRenderer.java"
        )

        self.assertIn("graphics.enableScissor(", renderer)
        self.assertIn("graphics.disableScissor()", renderer)
        self.assertIn("widget.getBounds()", renderer)
        self.assertRegex(renderer, r"diagram\.contains\(")
        self.assertNotRegex(
            renderer,
            r"catch\s*\(\s*(?:Throwable|Exception|RuntimeException)",
            "unexpected EMI failures must surface instead of silently selecting native rendering",
        )
        self.assertNotIn("new NativeRecipeDiagramRenderer", renderer)

    def test_crafting_screen_does_not_recompute_recipes_or_read_core_storage_client_side(self):
        text = self.read_required("src/main/java/com/swear/autostorage/CraftingTerminalScreen.java")
        self.assertTrue(
            "menu.getRecipePresentation()" in text,
            "CraftingTerminalScreen must render the menu-synchronized RecipePresentation",
        )
        self.assertNotIn("findRecipesClient", text)
        self.assertNotIn("getCore(minecraft.level)", text)
        self.assertNotIn("RecipeManager mgr = minecraft.level.getRecipeManager()", text)
        self.assertNotIn("import net.minecraft.world.item.crafting.RecipeManager", text)
        self.assertNotIn("import net.minecraft.world.item.crafting.RecipeType", text)
        self.assertNotIn("StorageCoreBlockEntity", text)
        self.assertNotIn("level.getRecipeManager()", text)
        self.assertNotRegex(text, r"\bnew\s+RecipePresentation\b")
        self.assertNotRegex(
            text,
            r"\bRecipePresentation\.(?:build|create|fromRecipe)\s*\(",
        )

    def test_emi_does_not_expose_hidden_selection_slots_as_inputs(self):
        text = self.read_required("src/main/java/com/swear/autostorage/compat/AutoStorageEmiPlugin.java")
        self.assertNotIn("return handler.slots;", text)
        self.assertIn("PLAYER_INVENTORY_SLOTS", text)
        self.assertIn("StorageTerminalMenu.DISPLAY_SLOTS + PLAYER_INVENTORY_SLOTS", text)
        self.assertIn("CraftingTerminalPage.STORAGE", text)
        self.assertGreaterEqual(text.count("handler.getPage()"), 3)
        self.assertIn("handler.isUsePlayerInventory()", text)

    def test_terminal_resize_preserves_search_value_focus_and_debounce(self):
        text = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        self.assertIn("previousSearchValue", text)
        self.assertIn("previousSearchFocused", text)
        self.assertIn("searchBox.setValue(previousSearchValue)", text)
        self.assertIn(
            "configureSearchBoxFocus(searchBoxAutoSelected || previousSearchFocused)",
            text,
        )
        self.assertIn("searchBox.setCanLoseFocus(true)", text)
        self.assertIn("unfocusSearchOnOutsideClick(mouseX, mouseY)", text)
        self.assertNotIn("this.searchTimer = 0;", text)

    def test_terminal_scrollbar_uses_conventional_immediate_interactions_without_animation(self):
        text = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        self.assertIn('"container/creative_inventory/scroller"', text)
        self.assertIn('"container/creative_inventory/scroller_disabled"', text)
        self.assertIn("graphics.blitSprite(", text)
        self.assertIn("scrollbar.press(", text)
        self.assertIn("scrollbar.drag(", text)
        self.assertIn("scrollbar.tick(", text)
        self.assertIn("scrollbar.step(", text)
        self.assertIn("scrollbar.visualState(", text)
        scrollbar = self.java_block(
            text,
            r"\bprotected\s+static\s+void\s+drawScrollbar\s*\(",
            "StorageTerminalScreen.drawScrollbar",
        )
        panels = self.java_block(
            text,
            r"\bprotected\s+void\s+drawPanels\s*\(",
            "StorageTerminalScreen.drawPanels",
        )
        self.assertIn(
            "drawInsetPanel(graphics, x, y, geometry.scrollbar())",
            panels,
        )
        wheel = self.java_block(
            text,
            r"\bpublic\s+boolean\s+mouseScrolled\s*\(",
            "StorageTerminalScreen.mouseScrolled",
        )
        self.assertIn("scrollbar.step(", wheel)
        self.assertNotIn("startSmoothScroll(", text)
        self.assertNotIn("pendingSmoothScrollRows", text)
        self.assertNotIn("TerminalScrollbar.animatedPosition(", text)
        self.assertNotIn("smoothScrollSnapshot", text)
        self.assertNotIn("easedProgress", self.read_required(
            "src/main/java/com/swear/autostorage/TerminalScrollbar.java"
        ))
        self.assertNotIn("g.renderTooltip(font, hoveredSlot.getItem(), mx, my);", text)

    def test_recipe_workspace_uses_one_frame_and_moves_recipe_position_to_header(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        recipe_panel = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderRecipePanel\s*\(",
            "CraftingTerminalScreen.renderRecipePanel",
        )
        labels = self.java_block(
            screen,
            r"\bprotected\s+void\s+renderLabels\s*\(",
            "CraftingTerminalScreen.renderLabels",
        )
        self.assertNotIn("drawInsetPanel(graphics, leftPos, topPos, panel)", recipe_panel)
        self.assertNotIn("recipePosition", recipe_panel)
        self.assertIn("renderRecipePosition", labels)
        self.assertIn("imageWidth - 8", screen)
        self.assertRegex(layout, r"RECIPE_INSET\s*=\s*4\s*;")
        self.assertIn("menu.getCraftableRecipeCount()", screen)
        self.assertIn('\" (\" + recipeCount + \")\"', screen)

    def test_terminal_scale_gate_keeps_10k_30k_and_warm_fifty_millisecond_contract(self):
        fixture = self.read_required(
            "src/compatibilityMatrixFixture/java/com/swear/autostorage/fixture/"
            "compatibilitymatrix/CraftablePerformanceGameTests.java"
        )
        build = self.read_required("build.gradle")

        self.assertIn("MAX_PREFETCH_NANOS = 50_000_000L", fixture)
        self.assertIn("MAX_SWITCH_NANOS = 50_000_000L", fixture)
        self.assertIn("requested != 10_000 && requested != 30_000", fixture)
        self.assertIn('"terminal-scale-" + STORED_TYPE_COUNT + ".json"', fixture)
        self.assertIn("Persistence segment exceeded 63 types", fixture)
        self.assertIn("TERMINAL_SCALE_WARM_INTERACTIONS_BEGIN", fixture)
        self.assertIn("TERMINAL_SCALE_WARM_INTERACTIONS_END", fixture)
        self.assertIn("providers.gradleProperty('terminalScaleTypes').orElse('10000')", build)
        self.assertIn("warmLog.contains(\"Can't keep up!\")", build)
        self.assertLess(
            fixture.index("craftablePreparationNanos = System.nanoTime() - started;"),
            fixture.index("preparationMenu.removed(player);"),
        )

    def test_large_exact_variant_prefilter_avoids_rescanning_equivalent_item_variants(self):
        adapters = self.read_required(
            "src/main/java/com/swear/autostorage/BuiltInRecipeAdapters.java"
        )
        core = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java"
        )
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )

        smithing = self.java_block(
            adapters,
            r"\bprivate\s+static\s+List<RecipeAdapterMatch\.Contract>\s+smithingVariants\s*\(",
            "BuiltInRecipeAdapters.smithingVariants",
        )
        self.assertLess(
            smithing.index("smithingVariantLimitExceeded"),
            smithing.index("uniqueExactStacks"),
        )
        self.assertIn("ingredientAmountByItemSnapshot", core)
        self.assertIn("storedItemAmountsByItem()", core)
        self.assertIn("matchesAllItemVariants()", menu)
        self.assertIn("matchingAllItemVariants", menu)

    def test_only_dynamic_recipe_adapters_receive_the_exact_variant_snapshot(self):
        adapter = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeAdapter.java"
        )
        adapters = self.read_required(
            "src/main/java/com/swear/autostorage/BuiltInRecipeAdapters.java"
        )
        family = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeFamily.java"
        )
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )

        self.assertIn("requiresAvailableStacksForVariants()", adapter)
        self.assertIn("SMITHING_TRANSFORM_ID", adapters)
        self.assertIn("SMITHING_TRIM_ID", adapters)
        self.assertIn("return typedPlanVariants != null", family)
        variant_stacks = self.java_block(
            menu,
            r"\bprivate\s+static\s+List<ItemStack>\s+variantAvailableStacks\s*\(",
            "CraftingTerminalMenu.variantAvailableStacks",
        )
        self.assertIn("requiresAvailableStacksForVariants()", variant_stacks)
        self.assertLess(
            variant_stacks.index("requiresAvailableStacksForVariants()"),
            variant_stacks.index("match.orderedInputs()"),
        )

    def test_stations_can_switch_between_all_and_installed_descriptors(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        en_us = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/en_us.json"
        ))
        zh_tw = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/zh_tw.json"
        ))
        self.assertIn("StationDisplayMode stationDisplayMode", screen)
        self.assertIn("TerminalCycleButton stationDisplayModeBtn", screen)
        self.assertIn("StationDisplayMode.ALL", screen)
        self.assertIn("INSTALLED(\"gui.auto_storage.station_display.installed\")", screen)
        self.assertIn("isStationInstalled(", screen)
        self.assertIn("stationDisplayMode.shows(", screen)
        self.assertIn("rebuildWidgets()", screen)
        self.assertIn("stationDisplayModeBtn", self.java_block(
            screen,
            r"\bprivate\s+void\s+updatePageWidgets\s*\(",
            "CraftingTerminalScreen.updatePageWidgets",
        ))
        for key in [
            "tooltip.auto_storage.station_display",
            "gui.auto_storage.station_display.all",
            "gui.auto_storage.station_display.installed",
        ]:
            self.assertIn(key, en_us)
        self.assertEqual(set(en_us), set(zh_tw))

    def test_mekanism_chemical_terminal_visual_uses_the_exact_emi_stack(self):
        compat = self.read_required(
            "src/compat/mekanism/java/com/swear/autostorage/"
            "MekanismChemicalClientCompat.java"
        )
        self.assertIn("ChemicalEmiStack", compat)
        self.assertIn("EmiIngredient.RENDER_ICON", compat)
        self.assertNotIn("basic_chemical_tank", compat)

        screen = self.read_required(
            "src/main/java/com/swear/autostorage/"
            "StorageTerminalScreen.java"
        )
        self.assertIn("TerminalResourceRendererApi.render(", screen)
        self.assertIn("TerminalResourceDisplay.key(stack)", screen)
        self.assertIn(
            "TerminalDisplayStack.strip(stack).copyWithCount(1)",
            screen,
        )

        crafting_screen = self.read_required(
            "src/main/java/com/swear/autostorage/"
            "CraftingTerminalScreen.java"
        )
        resource_row = self.java_block(
            crafting_screen,
            r"\bprivate\s+void\s+renderResourceRow\s*\(",
            "CraftingTerminalScreen.renderResourceRow",
        )
        self.assertIn("renderTerminalIcon(", resource_row)

        menu = self.read_required(
            "src/main/java/com/swear/autostorage/"
            "CraftingTerminalMenu.java"
        )
        inputs = self.java_block(
            menu,
            r"\bprivate\s+static\s+List<ItemStack>\s+presentationInputs\s*\(",
            "CraftingTerminalMenu.presentationInputs",
        )
        self.assertIn("TerminalResourceDisplay.create(", inputs)
        self.assertIn("typedInput.amount()", inputs)

    def test_terminal_scrollbar_sends_one_server_validated_absolute_packet(self):
        packet = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalScrollPacket.java"
        )
        self.assertIn('"terminal_scroll"', packet)
        self.assertGreaterEqual(packet.count("ByteBufCodecs.VAR_INT"), 2)

        entrypoint = self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        )
        self.assertIn("TerminalScrollPacket.TYPE", entrypoint)
        self.assertIn("menu.scrollTo(packet.offset())", entrypoint)

        screen = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        self.assertIn("new TerminalScrollPacket(menu.containerId, target)", screen)
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalMenu.java"
        )
        self.assertIn("rowAlignedScrollOffset(", menu)
        self.assertNotIn("while (delta < 0)", screen)
        self.assertNotIn("while (delta >= 9)", screen)

    def test_terminal_packets_skip_identical_filter_and_layout_requests(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalMenu.java"
        )
        self.assertIn("boolean applyFilter", menu)
        self.assertIn("boolean applySettings", menu)

        entrypoint = self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        )
        self.assertIn("menu.applyFilter(core, packet.filter())", entrypoint)
        self.assertIn("menu.applySettings(packet, player)", entrypoint)
        self.assertNotIn("menu.refreshDisplayItemsFiltered(core, packet.filter())", entrypoint)

    def test_craftable_refresh_reuses_the_row_aligned_scroll_clamp(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/"
            "CraftingTerminalMenu.java"
        )
        self.assertGreaterEqual(menu.count("scrollTo(scrollOffset);"), 2)
        self.assertNotIn(
            "totalItemTypes - vRows * DISPLAY_COLS",
            menu,
        )

    def test_wrench_recovery_drop_uses_the_post_recovery_escrow(self):
        wrench = self.read_required(
            "src/main/java/com/swear/autostorage/WrenchActions.java"
        )
        self.assertNotIn("spawnIfMissing(level, pos, expected)", wrench)
        self.assertEqual(4, wrench.count("createRecoveryDrop(level.registryAccess())"))

    def test_terminal_open_buffers_use_core_access_remote_contract(self):
        menu = self.read_required("src/main/java/com/swear/autostorage/StorageTerminalMenu.java")
        self.assertGreaterEqual(menu.count("buf.readBlockPos()"), 2)
        self.assertIn("buf.readBoolean()", menu)
        terminal = self.read_required("src/main/java/com/swear/autostorage/TerminalBlock.java")
        self.assertIn("buf.writeBlockPos(core.getBlockPos())", terminal)
        self.assertIn("buf.writeBlockPos(pos)", terminal)
        self.assertIn("buf.writeBoolean(false)", terminal)
        remote = self.read_required("src/main/java/com/swear/autostorage/RemoteTerminalItem.java")
        self.assertIn("buf.writeBoolean(true)", remote)

    def test_buses_and_menus_use_action_actor_storage_contract(self):
        bus_paths = [
            "src/main/java/com/swear/autostorage/ImportBusBlockEntity.java",
            "src/main/java/com/swear/autostorage/ExportBusBlockEntity.java",
        ]
        for relative_path in bus_paths:
            text = self.read_required(relative_path)
            self.assertIn("Action.", text, relative_path)
            self.assertIn("BusActor", text, relative_path)
            self.assertIn("BusTransferGuard.run", text, relative_path)
        for relative_path in [
            "src/main/java/com/swear/autostorage/StorageTerminalMenu.java",
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java",
        ]:
            text = self.read_required(relative_path)
            self.assertIn("Action.", text, relative_path)
            self.assertIn("Actor.", text, relative_path)
        bus_text = self.read_required("src/main/java/com/swear/autostorage/ImportBusBlockEntity.java")
        self.assertNotRegex(bus_text, r"core\.insertItem\([^;]+,\s*true\)")
        self.assertNotRegex(bus_text, r"core\.insertItem\([^;]+,\s*false\)")

    def test_selftest_does_not_reference_client_only_screens(self):
        text = self.read_required("src/main/java/com/swear/autostorage/SelfTest.java")
        self.assertNotIn("Screen", text)
        self.assertNotIn("AbstractContainerScreen", text)

    def test_client_screen_registration_avoids_deprecated_event_bus_subscriber_bus(self):
        client_setup = self.read_required("src/main/java/com/swear/autostorage/ClientSetup.java")
        self.assertNotIn("EventBusSubscriber", client_setup)
        self.assertNotIn("SubscribeEvent", client_setup)
        self.assertIn("import net.neoforged.bus.api.IEventBus;", client_setup)
        self.assertIn("public static void register(IEventBus modEventBus)", client_setup)
        self.assertIn("modEventBus.addListener(ClientSetup::registerScreens)", client_setup)

        auto_storage = self.read_required("src/main/java/com/swear/autostorage/AutoStorage.java")
        self.assertIn("import net.neoforged.api.distmarker.Dist;", auto_storage)
        self.assertIn("import net.neoforged.fml.loading.FMLEnvironment;", auto_storage)
        self.assertIn("FMLEnvironment.dist == Dist.CLIENT", auto_storage)
        self.assertIn("ClientSetup.register(modEventBus)", auto_storage)

    def test_bus_configuration_screen_exposes_the_complete_concise_control_set(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/BusConfigurationScreen.java"
        )
        client_setup = self.read_required(
            "src/main/java/com/swear/autostorage/ClientSetup.java"
        )
        self.assertIn("extends AbstractContainerScreen<BusConfigurationMenu>", screen)
        self.assertIn("AutoStorage.BUS_CONFIGURATION_MENU.get()", client_setup)
        self.assertIn("BusConfigurationScreen::new", client_setup)
        for control in [
            "TOGGLE_MODE_BUTTON",
            "TOGGLE_UNSIDED_BUTTON",
            "TOGGLE_AUTOMATION_BUTTON",
            "TOGGLE_FILTER_MODE_BUTTON",
            "TOGGLE_SIDE_BUTTON_START",
        ]:
            self.assertIn(control, screen)
        self.assertIn("handleInventoryButtonClick", screen)
        self.assertIn("graphics.drawCenteredString", screen)
        self.assertIn(
            'Component.translatable("gui.auto_storage.bus.side.short." + direction.getName())',
            screen,
        )
        self.assertNotIn("directionLabel", screen)
        self.assertNotIn("BFS", screen)
        self.assertNotIn("capability", screen.lower())

        en_us = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/en_us.json"
        ))
        zh_tw = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/zh_tw.json"
        ))
        keys = {
            "container.auto_storage.import_bus",
            "container.auto_storage.export_bus",
            "gui.auto_storage.bus.mode",
            "gui.auto_storage.bus.directional",
            "gui.auto_storage.bus.directionless",
            "gui.auto_storage.bus.front_transfer",
            "gui.auto_storage.bus.external_sides",
            "gui.auto_storage.bus.automation",
            "gui.auto_storage.bus.unsided",
            "gui.auto_storage.bus.allow",
            "gui.auto_storage.bus.deny",
            "gui.auto_storage.bus.filters",
            "gui.auto_storage.bus.read_only",
            *{
                f"gui.auto_storage.bus.side.short.{direction}"
                for direction in ("down", "up", "north", "south", "west", "east")
            },
        }
        self.assertTrue(keys.issubset(en_us))
        self.assertTrue(keys.issubset(zh_tw))
        for key in [
            "gui.auto_storage.bus.directional",
            "gui.auto_storage.bus.directionless",
            "gui.auto_storage.bus.front_transfer",
            "gui.auto_storage.bus.external_sides",
        ]:
            self.assertLessEqual(len(en_us[key]), 12, key)

    def test_directional_bus_mirror_avoids_deprecated_blockstate_rotate(self):
        for source in ["ImportBusBlock.java", "ExportBusBlock.java"]:
            text = self.read_required(f"src/main/java/com/swear/autostorage/{source}")
            self.assertNotIn("state.rotate(", text, source)
            self.assertIn("state.setValue(FACING, mirror.mirror(state.getValue(FACING)))", text, source)

    def test_every_blockstate_model_reference_resolves_to_a_model_file(self):
        assets = ROOT / "src/main/resources/assets"
        missing = []
        for blockstate_path in assets.glob("*/blockstates/*.json"):
            namespace = blockstate_path.parents[1].name
            blockstate = json.loads(blockstate_path.read_text())
            references = []
            for variant in blockstate.get("variants", {}).values():
                references.extend(variant if isinstance(variant, list) else [variant])
            for part in blockstate.get("multipart", []):
                apply = part.get("apply", {})
                references.extend(apply if isinstance(apply, list) else [apply])
            for reference in references:
                model_id = reference.get("model") if isinstance(reference, dict) else None
                if not model_id:
                    continue
                model_namespace, model_path = (
                    model_id.split(":", 1) if ":" in model_id else (namespace, model_id)
                )
                candidate = assets / model_namespace / "models" / f"{model_path}.json"
                if not candidate.is_file():
                    missing.append(
                        f"{blockstate_path.relative_to(ROOT)} -> {model_id}"
                    )
        self.assertEqual([], missing)

    def test_runtime_texture_family_is_complete_native_and_orphan_free(self):
        textures = ROOT / "src/main/resources/assets/auto_storage/textures"
        generation_artifacts = sorted(
            path.relative_to(ROOT).as_posix()
            for path in textures.rglob("*")
            if path.is_file() and (path.suffix == ".json" or path.name.endswith(".preview.png"))
        )
        self.assertEqual([], generation_artifacts)

        models = ROOT / "src/main/resources/assets/auto_storage/models"
        texture_ids = {
            texture_id
            for model_path in models.rglob("*.json")
            for texture_id in json.loads(model_path.read_text()).get("textures", {}).values()
            if isinstance(texture_id, str)
            and texture_id.startswith(("auto_storage:block/", "auto_storage:item/"))
        }
        self.assertTrue(texture_ids, "no gameplay texture references found in block/item models")
        expected_family = self.expected_texture_family()
        expected_connected = self.expected_connected_texture_family()
        expected_runtime = set(expected_family) | {"auto_storage:item/wrench"}
        self.assertEqual(expected_runtime, texture_ids)

        runtime_texture_ids = {
            f"auto_storage:{path.parent.name}/{path.stem}"
            for category in (textures / "block", textures / "item")
            for path in category.glob("*.png")
        }
        self.assertEqual(
            expected_runtime,
            runtime_texture_ids,
            "runtime block/item textures must contain exactly the model-referenced semantic family",
        )

        invalid_textures = []
        for texture_id in sorted(runtime_texture_ids):
            texture_path = textures / f"{texture_id.split(':', 1)[1]}.png"
            relative_path = texture_path.relative_to(ROOT).as_posix()
            if not texture_path.is_file():
                invalid_textures.append(f"missing {relative_path}")
                continue
            dimensions = self.png_dimensions(texture_path)
            expected_dimensions = (80, 16) if texture_id in expected_connected else (16, 16)
            if dimensions != expected_dimensions:
                invalid_textures.append(f"{relative_path} is {dimensions[0]}x{dimensions[1]}")

        self.assertEqual([], invalid_textures)
        overlay_textures = FUSION_PACK / "assets/auto_storage/textures"
        overlay_texture_ids = {
            f"auto_storage:{path.parent.name}/{path.stem}"
            for path in (overlay_textures / "block").glob("*.png")
        }
        self.assertEqual(expected_connected, overlay_texture_ids)
        runtime_gui = {
            path.name for path in (textures / "gui").glob("*.png")
        }
        self.assertEqual({"icons.png", "terminal_controls.png"}, runtime_gui)

    def test_texture_family_manifest_palette_chassis_and_control_atlas_are_reproducible(self):
        art = ROOT / "art/texture-generation/20260714-terminal-family"
        manifest_path = art / "selection.json"
        self.assertTrue(manifest_path.is_file(), f"missing {manifest_path.relative_to(ROOT)}")
        manifest = json.loads(manifest_path.read_text())
        absolute_metadata_paths = []
        for metadata_path in art.rglob("*.json"):
            def visit(value):
                if isinstance(value, dict):
                    for nested in value.values():
                        visit(nested)
                elif isinstance(value, list):
                    for nested in value:
                        visit(nested)
                elif isinstance(value, str) and value.startswith("/"):
                    absolute_metadata_paths.append(
                        f"{metadata_path.relative_to(ROOT)}: {value}"
                    )
            visit(json.loads(metadata_path.read_text()))
        self.assertEqual([], absolute_metadata_paths)
        expected_family = self.expected_texture_family()
        expected_connected = self.expected_connected_texture_family()
        self.assertEqual(2, manifest.get("schema"))
        self.assertEqual("retro-diffusion/rd-fast", manifest.get("model"))
        self.assertEqual([16, 16], manifest.get("runtime_size"))
        self.assertEqual(71421, manifest.get("settings", {}).get("seed"))
        self.assertEqual([71422, 71423, 71424, 71425], manifest.get("settings", {}).get("revision_seeds"))
        self.assertEqual(0.38, manifest.get("settings", {}).get("block_img2img_strength"))
        self.assertEqual(0.68, manifest.get("settings", {}).get("item_img2img_strength"))

        palette = {
            tuple(bytes.fromhex(color.removeprefix("#")))
            for color in manifest.get("palette", [])
        }
        self.assertGreaterEqual(len(palette), 8)
        chassis_source = art / manifest["chassis"]["source"]
        chassis_metadata = art / manifest["chassis"]["metadata"]
        self.assertTrue(chassis_source.is_file())
        self.assertTrue(chassis_metadata.is_file())
        self.assertEqual((16, 16), self.png_dimensions(chassis_source))

        members = manifest.get("members", {})
        self.assertEqual(set(expected_family), set(members))
        revised_seeds = {
            **{f"auto_storage:block/storage_unit_t{tier}": 71422 for tier in range(1, 7)},
            "auto_storage:block/creative_storage_unit": 71425,
            "auto_storage:block/import_bus_top": 71423,
            "auto_storage:block/import_bus_side": 71423,
            "auto_storage:block/export_bus_top": 71424,
            "auto_storage:block/export_bus_side": 71424,
        }
        for texture_id, expected_role in expected_family.items():
            member = members[texture_id]
            self.assertEqual(expected_role, member.get("role"), texture_id)
            runtime = ROOT / member["runtime"]
            source = art / member["source"]
            metadata_path = art / member["metadata"]
            self.assertTrue(runtime.is_file(), texture_id)
            self.assertTrue(source.is_file(), texture_id)
            self.assertTrue(metadata_path.is_file(), texture_id)
            self.assertEqual(hashlib.sha256(runtime.read_bytes()).hexdigest(), member.get("sha256"))
            metadata = json.loads(metadata_path.read_text())
            self.assertEqual("retro-diffusion/rd-fast", metadata.get("model"), texture_id)
            self.assertEqual(16, metadata.get("size"), texture_id)
            self.assertEqual(revised_seeds.get(texture_id, 71421), metadata.get("seed"), texture_id)
            self.assertTrue(metadata.get("img2img"), texture_id)
            self.assertEqual(manifest["chassis"]["source"], metadata.get("reference_image"), texture_id)

            width, height, pixels = self.rgba_png_pixels(runtime)
            self.assertEqual((16, 16), (width, height), texture_id)
            used_colors = {pixel[:3] for pixel in pixels if pixel[3] != 0}
            self.assertLessEqual(used_colors, palette, texture_id)

        _, _, chassis_pixels = self.rgba_png_pixels(chassis_source)
        chassis_points = [tuple(point) for point in manifest.get("chassis", {}).get("points", [])]
        self.assertGreaterEqual(len(chassis_points), 32)
        for texture_id in expected_family:
            if ":block/" not in texture_id:
                continue
            runtime = ROOT / members[texture_id]["runtime"]
            _, _, pixels = self.rgba_png_pixels(runtime)
            for x, y in chassis_points:
                self.assertEqual(chassis_pixels[y * 16 + x], pixels[y * 16 + x],
                                 f"{texture_id} does not share chassis pixel {(x, y)}")

        connected_members = manifest.get("connected_textures", {})
        self.assertEqual(expected_connected, set(connected_members))
        for texture_id, connected in connected_members.items():
            runtime = ROOT / connected["runtime"]
            source = art / connected["source"]
            metadata_path = ROOT / connected["metadata"]
            self.assertTrue(runtime.is_file(), texture_id)
            self.assertTrue(source.is_file(), texture_id)
            self.assertTrue(metadata_path.is_file(), texture_id)
            self.assertEqual((80, 16), self.png_dimensions(runtime), texture_id)
            self.assertEqual(runtime.read_bytes(), source.read_bytes(), texture_id)
            self.assertEqual(hashlib.sha256(runtime.read_bytes()).hexdigest(), connected.get("sha256"))
            self.assertEqual("pieced", connected.get("layout"), texture_id)
            self.assertEqual(5, connected.get("tiles"), texture_id)
            self.assertEqual(
                {"fusion": {"type": "connecting", "layout": "pieced"}},
                json.loads(metadata_path.read_text()),
                texture_id,
            )

        for contact_sheet in manifest.get("contact_sheets", []):
            self.assertTrue((art / contact_sheet).is_file(), contact_sheet)

        semantic_accents = {
            "auto_storage:block/storage_core": {"#3FDCE5", "#9A5CE8"},
            "auto_storage:block/storage_terminal": {"#3FDCE5"},
            "auto_storage:block/crafting_terminal": {"#3FDCE5", "#9A5CE8"},
            "auto_storage:block/creative_storage_unit": {"#3FDCE5", "#C083FF"},
            "auto_storage:block/import_bus_front": {"#2EA8FF"},
            "auto_storage:block/export_bus_front": {"#FF8A24"},
            "auto_storage:item/remote_terminal": {"#3FDCE5", "#9A5CE8"},
        }
        for texture_id, colors in semantic_accents.items():
            runtime = ROOT / members[texture_id]["runtime"]
            _, _, pixels = self.rgba_png_pixels(runtime)
            used = {pixel[:3] for pixel in pixels if pixel[3] != 0}
            required = {tuple(bytes.fromhex(color.removeprefix("#"))) for color in colors}
            self.assertLessEqual(required, used, texture_id)

        remote = ROOT / members["auto_storage:item/remote_terminal"]["runtime"]
        _, _, remote_pixels = self.rgba_png_pixels(remote)
        self.assertTrue(all(remote_pixels[y * 16 + x][3] == 0
                            for x, y in ((0, 0), (15, 0), (0, 15), (15, 15))))

        screen = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        enum = re.search(r"enum TerminalControlIcon\s*\{(?P<body>.*?);", screen, re.DOTALL)
        self.assertIsNotNone(enum)
        icons = [
            {"name": name, "atlas_index": int(index)}
            for name, index in re.findall(r"\b([A-Z][A-Z0-9_]*)\((\d+)\)", enum.group("body"))
        ]
        self.assertEqual(list(range(len(icons))), [icon["atlas_index"] for icon in icons])
        self.assertEqual(icons, manifest.get("control_atlas", {}).get("icons"))
        atlas = ROOT / manifest["control_atlas"]["runtime"]
        self.assertEqual((256, 16), self.png_dimensions(atlas))
        _, _, atlas_pixels = self.rgba_png_pixels(atlas)
        for icon in icons:
            first = icon["atlas_index"] * 16
            alpha = [atlas_pixels[y * 256 + first + x][3] for y in range(16) for x in range(16)]
            self.assertGreaterEqual(sum(value != 0 for value in alpha), 12, icon["name"])
            self.assertTrue(all(
                atlas_pixels[y * 256 + first + x][:3] == (255, 255, 255)
                for y in range(16) for x in range(16)
                if atlas_pixels[y * 256 + first + x][3] != 0
            ), icon["name"])
        self.assertTrue(all(
            atlas_pixels[y * 256 + x][3] == 0
            for y in range(16) for x in range(len(icons) * 16, 256)
        ))

    def test_crafting_terminal_separates_transform_from_station_management(self):
        terminal_screen = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        self.assertNotIn("FuelTable.getFuelValues", terminal_screen)
        self.assertNotIn("pushGuiLayer", terminal_screen)
        self.assertNotIn("FuelSelectionScreen", terminal_screen)
        self.assertNotIn("fuelButtonId", terminal_screen)

        popup = ROOT / "src/main/java/com/swear/autostorage/FuelSelectionScreen.java"
        self.assertFalse(popup.exists(), "transient FuelSelectionScreen must be removed")

        terminal_menu = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalMenu.java"
        )
        self.assertNotIn("FUEL_BUTTON_BASE", terminal_menu)
        self.assertNotIn("fuelButtonId", terminal_menu)
        self.assertNotIn("handleFuelButton", terminal_menu)

        crafting_screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        crafting_menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        self.assertIn("CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON", crafting_screen)
        self.assertIn("CraftingTerminalMenu.STATIONS_PAGE_BUTTON", crafting_screen)
        self.assertNotIn("FUEL_PAGE_BUTTON", crafting_screen + crafting_menu)
        self.assertIn("CraftingTerminalMenu.AUTO_FUEL_TARGET_BUTTON", crafting_screen)
        self.assertNotIn("PREVIOUS_FUEL_TARGET_BUTTON", crafting_screen + crafting_menu)
        self.assertNotIn("NEXT_FUEL_TARGET_BUTTON", crafting_screen + crafting_menu)
        self.assertNotIn("cycleFuelTarget", crafting_menu)
        self.assertIn("TransformProviderApi.targetButtonId", crafting_screen)
        self.assertNotIn("autoFuelBtn", crafting_screen)
        self.assertNotIn("previousFuelTargetBtn", crafting_screen)
        self.assertNotIn("nextFuelTargetBtn", crafting_screen)
        self.assertIn("CraftingTerminalPage.TRANSFORM", crafting_screen)
        self.assertIn("CraftingTerminalPage.STATIONS", crafting_screen)
        self.assertIn("getVisibleTransformUses", crafting_screen)

        lang = self.read_required("src/main/resources/assets/auto_storage/lang/en_us.json")
        self.assertNotIn("container.auto_storage.fuel_selection", lang)
        for key in [
            "gui.auto_storage.page_transform",
            "gui.auto_storage.page_stations",
            "gui.auto_storage.page_storage",
            "gui.auto_storage.page_craftable",
            "gui.auto_storage.fuel_target_auto",
            "gui.auto_storage.fuel_target",
            "gui.auto_storage.fuel_group.timed_stations",
            "gui.auto_storage.fuel_group.instant_stations",
        ]:
            self.assertIn(key, lang)
        self.assertNotIn("gui.auto_storage.page_fuel", lang)
        self.assertNotIn("Consumables", lang)
        self.assertNotIn("gui.auto_storage.previous_fuel_target", lang)
        self.assertNotIn("gui.auto_storage.next_fuel_target", lang)

    def test_transform_target_sidebar_is_persistent_searchable_and_paged(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        en_us = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/en_us.json")
        )
        zh_tw = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/zh_tw.json")
        )

        self.assertIn("record PagedList", layout)
        self.assertIn("Rect transformTargetSearch", layout)
        self.assertIn("PagedList transformTargetList", layout)
        self.assertIn("FuelPageControls transformTargetPageControls", layout)
        self.assertIn("int pageCount()", layout)
        self.assertIn("int firstIndex(int page)", layout)
        self.assertIn("List<Rect> rows(int page)", layout)
        self.assertRegex(screen, r"\bEditBox\s+transformTargetSearchBox\b")
        self.assertRegex(screen, r"\bFuelPageButtons\s+transformTargetPageButtons\b")
        self.assertRegex(screen, r"\bint\s+transformTargetPage\b")
        self.assertIn("filteredTransformTargets()", screen)
        self.assertNotIn("transformTargetSearchBox.setHint", screen)
        self.assertIn("fuelTargetOptions()", screen)
        self.assertIn("displayedPreferences().transformTarget()", screen)
        self.assertIn("CraftingTerminalMenu.AUTO_FUEL_TARGET_BUTTON", screen)
        self.assertIn("TransformProviderApi.targetButtonId", screen)
        self.assertIn("geometry.transformTargetSearch()", screen)
        self.assertIn("geometry.transformTargetList()", screen)
        self.assertIn("geometry.transformTargetPageControls()", screen)
        self.assertNotIn("FuelTargetPopup", screen)
        self.assertNotIn("fuelTargetListBtn", screen)
        target_render = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderTransformTargetList\s*\(",
            "CraftingTerminalScreen.renderTransformTargetList",
        )
        self.assertIn("filteredTransformTargets()", target_render)
        self.assertIn("option.icon()", target_render)
        self.assertIn("option.label()", target_render)
        self.assertRegex(
            target_render,
            r"Objects\.equals\(\s*option\.target\(\),\s*"
            r"displayedPreferences\(\)\.transformTarget\(\)\)",
        )
        click = self.java_block(
            screen,
            r"\bpublic\s+boolean\s+mouseClicked\s*\(",
            "CraftingTerminalScreen.mouseClicked",
        )
        self.assertIn("transformTargetAt(mouseX, mouseY)", click)
        self.assertIn("button == 2", click)
        scroll = self.java_block(
            screen,
            r"^[ ]{4}public\s+boolean\s+mouseScrolled\s*\(",
            "CraftingTerminalScreen.mouseScrolled",
        )
        self.assertIn("geometry.transformTargetList().bounds().contains", scroll)
        self.assertIn("gui.auto_storage.fuel_target_list", en_us)
        self.assertIn("gui.auto_storage.fuel_target_list", zh_tw)

    def test_transform_cards_require_explicit_selection_and_show_their_source_inline(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        en_us = self.read_required(
            "src/main/resources/assets/auto_storage/lang/en_us.json"
        )
        zh_tw = self.read_required(
            "src/main/resources/assets/auto_storage/lang/zh_tw.json"
        )
        click = self.java_block(
            screen,
            r"\bpublic\s+boolean\s+mouseClicked\s*\(",
            "CraftingTerminalScreen.mouseClicked",
        )
        card_hit = self.java_block(
            screen,
            r"\bprivate\s+int\s+transformUseIndexAt\s*\(",
            "CraftingTerminalScreen.transformUseIndexAt",
        )
        card_render = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderTransformCards\s*\(",
            "CraftingTerminalScreen.renderTransformCards",
        )
        source = self.java_block(
            screen,
            r"\bprivate\s+Component\s+transformSource\s*\(",
            "CraftingTerminalScreen.transformSource",
        )
        provider = self.read_required(
            "src/main/java/com/swear/autostorage/TransformProviderApi.java"
        )
        provider_value = self.read_required(
            "src/main/java/com/swear/autostorage/TransformProvider.java"
        )
        powah = self.read_required(
            "src/compat/powah/java/com/swear/autostorage/compat/"
            "powah/PowahCompat.java"
        )
        agents = self.read_required("AGENTS.md")
        api_docs = self.read_required("docs/machine-descriptor-api.md")

        self.assertIn("menu.getVisibleTransformUses()", card_hit)
        self.assertIn("cell.contains", card_hit)
        self.assertIn("transformUseIndexAt((int) mouseX, (int) mouseY)", click)
        self.assertIn("CraftingTerminalMenu.transformUseButtonId", click)
        self.assertIn("menu.getVisibleTransformUses()", card_render)
        self.assertIn("menu.getSelectedTransformUse()", card_render)
        self.assertIn("drawInsetPanel", card_render)
        self.assertIn("cell.contains(mouseX - leftPos, mouseY - topPos)", card_render)
        self.assertIn("graphics.fill", card_render)
        self.assertIn("transformSource(use)", card_render)
        self.assertIn("TransformProviderApi.sourceLabel(use.id())", source)
        self.assertIn("use.stationId()", source)
        self.assertIn("use.stationWorkPerItem()", source)
        self.assertIn("Component sourceLabel", provider_value)
        self.assertIn("provider.sourceLabel()", provider)
        self.assertNotIn("stationLabel", provider)
        self.assertIn("Optional<Component> sourceLabel", provider)
        self.assertIn('"gui.auto_storage.station.powah_furnator"', powah)
        self.assertIn("recipe-viewer category name", agents)
        self.assertIn("every accepted workstation variant", agents)
        self.assertIn("first/representative/installed stack", agents)
        self.assertIn("EMI category display name", api_docs)
        self.assertIn("every accepted variant", api_docs)
        self.assertNotIn("renderTransformPreview", screen)
        self.assertNotIn("gui.auto_storage.transform_select_recipe", en_us)
        self.assertNotIn("gui.auto_storage.transform_select_recipe", zh_tw)
        self.assertIn("return Component.empty()", source)
        self.assertIn("if (!source.getString().isEmpty())", card_render)
        self.assertNotIn("gui.auto_storage.transform_source_direct", en_us)
        self.assertNotIn("gui.auto_storage.transform_source_direct", zh_tw)
        self.assertIn("gui.auto_storage.transform_station_work", en_us)
        self.assertIn("gui.auto_storage.transform_station_work", zh_tw)

    def test_station_stack_counts_overlay_items_and_only_processing_shows_stored_work(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        slot_render = self.java_block(
            screen,
            r"\bprotected\s+void\s+renderSlotContents\s*\(",
            "CraftingTerminalScreen.renderSlotContents",
        )
        category_render = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderMachineCategoryCells\s*\(",
            "CraftingTerminalScreen.renderMachineCategoryCells",
        )

        self.assertNotIn("formatAmount(stack.getCount())", slot_render)
        self.assertIn("ItemStack installed = menu.getSlot(", category_render)
        self.assertIn("ItemStack icon = installed.copyWithCount(1)", category_render)
        self.assertIn("graphics.renderItem(icon", category_render)
        self.assertIn("renderNetworkAmount(", category_render)
        self.assertNotIn("renderItemDecorations", category_render)
        self.assertIn("category == MachineCategory.PROCESS", category_render)
        self.assertIn("machineStoredAmount(entry)", category_render)

    def test_transform_and_empty_station_icons_have_visible_background_rendering(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        transform = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderConsumablesPanel\s*\(",
            "CraftingTerminalScreen.renderConsumablesPanel",
        )
        dimmed = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderDimmedItem\s*\(",
            "CraftingTerminalScreen.renderDimmedItem",
        )
        page_buttons = self.java_block(
            screen,
            r"\bprivate\s+void\s+updateFuelPageButtons\s*\(",
            "CraftingTerminalScreen.updateFuelPageButtons",
        )
        page_widgets = self.java_block(
            screen,
            r"\bprivate\s+void\s+updatePageWidgets\s*\(",
            "CraftingTerminalScreen.updatePageWidgets",
        )

        self.assertIn("ItemStack icon = inputStack.copyWithCount(1)", transform)
        self.assertIn("graphics.renderItem(icon", transform)
        self.assertIn("gui.auto_storage.transform_insert_item", transform)
        self.assertNotIn("graphics.setColor", dimmed)
        self.assertIn("graphics.fill", dimmed)
        self.assertIn("pageCount > 1", page_buttons)
        self.assertIn("repositionFuelSlots()", page_widgets)

    def test_uninstalled_station_icons_are_visibly_dimmed_in_all_station_views(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        dimmed = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderDimmedItem\s*\(",
            "CraftingTerminalScreen.renderDimmedItem",
        )
        category_render = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderMachineCategoryCells\s*\(",
            "CraftingTerminalScreen.renderMachineCategoryCells",
        )
        search_render = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderFuelSearchResults\s*\(",
            "CraftingTerminalScreen.renderFuelSearchResults",
        )
        overlay = re.search(r"0x([0-9A-Fa-f]{8})", dimmed)

        self.assertIsNotNone(overlay, "uninstalled station overlay color is missing")
        self.assertGreaterEqual(
            int(overlay.group(1), 16) >> 24,
            0xA0,
            "uninstalled station icon must retain at most about 37% visibility",
        )
        self.assertIn("renderDimmedItem(graphics", category_render)
        self.assertIn("renderDimmedItem(graphics", search_render)

    def test_transform_cards_use_whole_cell_hit_targets(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        transform_hit = self.java_block(
            screen,
            r"\bprivate\s+int\s+transformUseIndexAt\s*\(",
            "CraftingTerminalScreen.transformUseIndexAt",
        )

        self.assertIn("static Rect fuelSlot(Rect", layout)
        self.assertIn("static Rect fuelIcon(Rect", layout)
        self.assertIn("static Rect fuelAmountBounds(Rect", layout)
        self.assertIn("cell.contains", transform_hit)

    def test_utility_status_stays_fixed_while_station_hitboxes_own_precise_tooltips(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        tooltip = self.java_block(
            screen,
            r"\bprotected\s+void\s+renderTooltip\s*\(",
            "CraftingTerminalScreen.renderTooltip",
        )
        fuel_tooltip = self.java_block(
            screen,
            r"\bprivate\s+boolean\s+renderFuelTooltip\s*\(",
            "CraftingTerminalScreen.renderFuelTooltip",
        )
        station_tooltip = self.java_block(
            screen,
            r"\bprivate\s+boolean\s+renderStationGridTooltip\s*\(",
            "CraftingTerminalScreen.renderStationGridTooltip",
        )
        status = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderUtilityStatus\s*\(",
            "CraftingTerminalScreen.renderUtilityStatus",
        )

        self.assertIn("renderFuelTooltip(graphics, mouseX, mouseY)", tooltip)
        self.assertIn("if (!displayedPreferences().page().isItemPage())", tooltip)
        self.assertIn("CraftingTerminalPage.STATIONS", fuel_tooltip)
        self.assertIn("renderStationGridTooltip", fuel_tooltip)
        self.assertIn("TerminalLayout.fuelSlot(cell)", station_tooltip)
        self.assertIn("graphics.renderTooltip(font, displayStack", station_tooltip)
        self.assertIn("TerminalLayout.fuelAmountBounds(cell)", station_tooltip)
        self.assertIn('"tooltip.auto_storage.machine_rate"', station_tooltip)
        self.assertIn("MachineRateFormatter.format", station_tooltip)
        self.assertIn("energyLabel(descriptor.energyType())", station_tooltip)
        self.assertIn("descriptor.stationLabel()", station_tooltip)
        self.assertNotIn('"gui.auto_storage.resource_view.station_work"', station_tooltip)
        self.assertNotIn('"tooltip.auto_storage.energy_stored"', station_tooltip)
        self.assertNotIn('"tooltip.auto_storage.machine_installed"', station_tooltip)
        self.assertNotIn("transformUseAt(", status)
        self.assertNotIn("machineEnergyIndexAt(", status)
        self.assertNotIn("mouseX", status)
        self.assertNotIn("mouseY", status)
        self.assertIn('"gui.auto_storage.type_capacity"', status)
        self.assertNotIn("super.renderTooltip", fuel_tooltip)

    def test_crafting_terminal_repositions_fuel_slots_without_sticky_checkbox_focus(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        shared_shell = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        self.assertNotIn("Checkbox", screen)
        self.assertIn("MACHINE_SLOT_COUNT", screen)
        self.assertIn("repositionFuelSlots", screen)
        self.assertIn("replaceSlot", screen)
        self.assertIn("geometry.transformCards()", screen)
        self.assertIn("geometry.timedStationsGrid()", screen)
        self.assertIn("geometry.instantStationsGrid()", screen)
        self.assertIn("boolean handled = super.mouseClicked", shared_shell)
        handled = shared_shell.index("boolean handled = super.mouseClicked")
        self.assertGreater(shared_shell.index("setFocused(null)", handled), handled)
        self.assertNotIn("Preview is server-synced", screen)

        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        self.assertIn("MACHINE_SLOT_START", menu)
        self.assertIn("MACHINE_SLOT_COUNT", menu)
        self.assertIn("MachineEnergyTable", menu)

        import json
        lang = json.loads(self.read_required("src/main/resources/assets/auto_storage/lang/en_us.json"))
        self.assertEqual("Brew Energy", lang["gui.auto_storage.energy.blaze_fuel"])

    def test_crafting_pages_fuel_flow_and_craft_actions_match_fullscreen_contract(self):
        page = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalPage.java"
        )
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        machine_table = self.read_required(
            "src/main/java/com/swear/autostorage/MachineEnergyTable.java"
        )
        descriptor = self.read_required(
            "src/main/java/com/swear/autostorage/MachineDescriptor.java"
        )

        for name in ["STORAGE", "CRAFTABLE", "FUEL"]:
            self.assertIn(name, page)
        self.assertNotIn("ITEMS", page)
        self.assertIn("isItemPage", page)
        self.assertNotIn("showOnlyCraftable", menu)
        self.assertIn("MAX_CRAFT_BUTTON", menu)
        self.assertIn("craftMaximum", menu)
        self.assertIn("RAIL_GROUP_GAP", layout)
        self.assertIn("record FlowGrid", layout)
        self.assertIn("CATEGORY_CELL_PREFERRED_WIDTH", layout)
        self.assertIn("int columns = Math.max(1, maxColumns);", layout)
        self.assertNotIn("columnsInRow", layout)
        self.assertIn("(column + 1) * bounds.width() / columns", layout)
        self.assertIn("entries()", machine_table)
        for stale in ["MACHINE_ENERGY_TYPES", "MACHINE_LABEL_KEYS", "STORED_FUEL_TYPES", "FUEL_LABEL_KEYS"]:
            self.assertNotIn(stale, screen)
        self.assertNotIn("machine_rate_hint", screen)
        fuel_rows = screen[
            screen.index("private void renderConsumablesPanel"):
            screen.index("private void drawFlowPageIndicator")
        ]
        self.assertNotIn("drawCenteredString", fuel_rows)
        self.assertIn("drawFlowAmount", fuel_rows)
        self.assertIn("craftMaxBtn", screen)
        self.assertIn("craft1Btn.active = craftable >= 1", screen)
        self.assertIn("craft8Btn.active = craftable >= 8", screen)
        self.assertIn("craft64Btn.active = craftable >= 64", screen)
        self.assertIn("craftMaxBtn.active = craftable >= 1", screen)
        self.assertNotIn("case SEARCH, SEARCH_TAG, SEARCH_MOD", screen)

    def test_terminal_amounts_fit_their_slots_and_fuel_uses_representative_items(self):
        formatter_path = ROOT / "src/main/java/com/swear/autostorage/TerminalAmountFormatter.java"
        self.assertTrue(formatter_path.exists(), "terminal amount formatter must be dist-neutral and testable")

        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        self.assertIn("protected void renderSlotContents", storage)
        self.assertIn("TerminalAmountFormatter.formatCompact", storage)
        self.assertIn("copyWithCount(1)", storage)
        self.assertIn("TerminalDisplayStack.amount(stack)", storage)
        self.assertIn("renderTypedResourceBackground(graphics, stack, slot.x, slot.y)", storage)
        typed_background = self.java_block(
            storage,
            r"\bstatic\s+void\s+renderTypedResourceBackground\s*\(",
            "StorageTerminalScreen.renderTypedResourceBackground",
        )
        self.assertIn("TerminalResourceDisplay.key(stack)", typed_background)
        self.assertIn("StorageResourceKindApi.ITEM_KIND", typed_background)
        self.assertIn("graphics.fill", typed_background)
        colors = {
            name: int(match.group(1), 16)
            for name in [
                "FLUID_RESOURCE_BACKGROUND",
                "FLUID_RESOURCE_BORDER",
                "ENERGY_RESOURCE_BACKGROUND",
                "ENERGY_RESOURCE_BORDER",
                "OTHER_RESOURCE_BACKGROUND",
                "OTHER_RESOURCE_BORDER",
                "STATION_WORK_BACKGROUND",
                "STATION_WORK_BORDER",
            ]
            if (match := re.search(
                rf"\b{name}\s*=\s*0x([0-9A-Fa-f]{{8}})", storage
            ))
        }
        self.assertEqual(8, len(colors))
        self.assertGreaterEqual(colors["FLUID_RESOURCE_BACKGROUND"] >> 24, 0xA0)
        self.assertGreaterEqual(colors["ENERGY_RESOURCE_BACKGROUND"] >> 24, 0xA0)
        self.assertGreaterEqual(colors["OTHER_RESOURCE_BACKGROUND"] >> 24, 0xA0)
        self.assertGreaterEqual(colors["STATION_WORK_BACKGROUND"] >> 24, 0xA0)
        self.assertNotEqual(
            colors["FLUID_RESOURCE_BACKGROUND"],
            colors["ENERGY_RESOURCE_BACKGROUND"],
        )
        self.assertIn("TerminalResourceView.classify(key)", typed_background)
        self.assertIn("if (amount <= 0) return;", storage)
        self.assertIn("getTooltipFromContainerItem", storage)
        self.assertIn("gui.auto_storage.stored_amount", storage)
        self.assertIn("protected void drawTypeCapacity", storage)
        self.assertIn("menu.getTypeCount()", storage)
        self.assertIn("menu.getMaxTypes()", storage)

        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        fuel_panel = crafting[
            crafting.index("private void renderConsumablesPanel"):
            crafting.index("private void drawFlowPageIndicator")
        ]
        self.assertIn("use.representative()", fuel_panel)
        self.assertNotIn("drawEnergyIcon", fuel_panel)
        self.assertNotIn("nextFuelTargetBtn", crafting)
        self.assertIn("displayedPreferences().page() == CraftingTerminalPage.TRANSFORM", crafting)
        self.assertIn("renderUtilityStatus", crafting)
        self.assertIn("geometry.fuelStatus()", crafting)
        flow_amount = self.java_block(
            crafting,
            r"\bprivate\s+void\s+drawFlowAmount\s*\(",
            "CraftingTerminalScreen.drawFlowAmount",
        )
        self.assertIn("TerminalLayout.fuelAmountBounds(cell)", flow_amount)
        self.assertIn("bounds.x() + bounds.width() / 2.0F", flow_amount)
        self.assertIn("graphics.drawString(font, text, -textWidth / 2", flow_amount)
        self.assertNotIn("cell.right()", flow_amount)
        type_capacity = self.java_block(
            crafting,
            r"\bprivate\s+void\s+renderUtilityStatus\s*\(",
            "CraftingTerminalScreen.renderUtilityStatus",
        )
        self.assertIn("geometry.fuelStatus()", type_capacity)
        self.assertIn("drawRaisedPanel(graphics, leftPos, topPos, status)", type_capacity)
        self.assertIn('"gui.auto_storage.type_capacity"', type_capacity)
        self.assertNotIn('"tooltip.auto_storage.energy_stored"', type_capacity)
        self.assertNotIn("machineStoredAmount(descriptor)", type_capacity)
        self.assertNotIn("machineEnergyIndexAt(", type_capacity)
        self.assertNotIn("transformUseAt(", type_capacity)
        self.assertNotIn("drawFlowAmount(graphics, status", type_capacity)
        labels = self.java_block(
            crafting,
            r"\bprotected\s+void\s+renderLabels\s*\(",
            "CraftingTerminalScreen.renderLabels",
        )
        self.assertNotIn("drawTypeCapacity", labels)

    def test_descriptor_station_work_never_reads_a_null_energy_type(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        self.assertIn("core.getStationWork(descriptor.id())", menu)
        self.assertIn("menu.getDescriptorAmount(descriptor.id())", screen)
        self.assertNotIn("menu.getEnergyAmount(descriptor.energyType())", screen)

    def test_typed_craftable_sort_uses_resource_identity_not_proxy_item_id(self):
        comparator = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalEntryComparator.java"
        )
        identity = self.java_block(
            comparator,
            r"\bprivate\s+static\s+ResourceLocation\s+id\s*\(",
            "TerminalEntryComparator.id",
        )
        self.assertIn("TerminalResourceDisplay.key(stack)", identity)
        self.assertIn("StorageResourceKey::resourceId", identity)
        self.assertIn("BuiltInRegistries.ITEM.getKey(stack.getItem())", identity)

    def test_terminal_display_amount_is_exact_server_metadata_not_stack_count(self):
        helper = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalDisplayStack.java"
        )
        key = self.read_required(
            "src/main/java/com/swear/autostorage/ItemKey.java"
        )
        core = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        self.assertIn("putLong(AMOUNT_KEY, amount)", helper)
        self.assertIn("static ItemStack strip", helper)
        self.assertIn("TerminalDisplayStack.strip(stack)", key)
        self.assertIn("TerminalDisplayStack.create(stack, item.amount)", core)
        self.assertIn("TerminalDisplayStack.create(item.key.toStack(1), item.amount)", core)
        self.assertIn("core.getResourceAmount(key)", crafting)
        self.assertIn("TerminalDisplayStack.create(output.icon(), output.storedAmount())", crafting)
        self.assertNotIn("preview.craftable() * output.getCount()", crafting)
        self.assertNotIn("Math.min(count, Integer.MAX_VALUE)", core)

    def test_compact_grid_is_removed_instead_of_hidden(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        lang = self.read_required("src/main/resources/assets/auto_storage/lang/en_us.json")
        for stale in [
            "compactMode",
            "isCompactMode",
            "toggleCompactMode",
            "compactDisplayStacks",
            "compactRailBtn",
            "gui.auto_storage.compact_mode",
        ]:
            self.assertNotIn(stale, menu + screen + lang)

    def test_recipe_presentation_kind_covers_every_supported_native_diagram(self):
        kind = self.read_required(
            "src/main/java/com/swear/autostorage/RecipePresentationKind.java"
        )
        self.assertRegex(kind, r"\bpublic\s+enum\s+RecipePresentationKind\b")
        for family in ["CRAFTING", "COOKING", "STONECUTTING", "SMITHING", "AXE"]:
            self.assertRegex(
                kind,
                rf"\b[A-Z0-9_]*{family}[A-Z0-9_]*\b",
                f"RecipePresentationKind must identify the {family.lower()} diagram",
            )

    def test_recipe_presentation_model_is_exact_immutable_and_bounded(self):
        presentation = self.read_required(
            "src/main/java/com/swear/autostorage/RecipePresentation.java"
        )

        self.assertRegex(presentation, r"\bpublic\s+final\s+class\s+RecipePresentation\b")
        required_fields = {
            "exact recipe id": r"\bprivate\s+final\s+ResourceLocation\s+recipeId\s*;",
            "presentation kind": r"\bprivate\s+final\s+RecipePresentationKind\s+kind\s*;",
            "shaped width": r"\bprivate\s+final\s+int\s+width\s*;",
            "shaped height": r"\bprivate\s+final\s+int\s+height\s*;",
            "shapeless state": r"\bprivate\s+final\s+boolean\s+shapeless\s*;",
            "positioned inputs": r"\bprivate\s+final\s+List<ItemStack>\s+inputs\s*;",
            "exact output stack": r"\bprivate\s+final\s+ItemStack\s+output\s*;",
            "station identity": r"\bprivate\s+final\s+ItemStack\s+station\s*;",
            "typed ledger rows": r"\bprivate\s+final\s+List<Resource>\s+resources\s*;",
        }
        self.assertEqual(
            [],
            [label for label, pattern in required_fields.items()
             if re.search(pattern, presentation) is None],
        )

        self.assertEqual(9, self.java_int_constant(presentation, "MAX_INPUTS"))
        typed_plan = self.read_required(
            "src/main/java/com/swear/autostorage/TypedRecipePlan.java"
        )
        self.assertEqual(81, self.java_int_constant(typed_plan, "MAX_INPUTS"))
        self.assertIn(
            "MAX_ITEM_RESOURCES = TypedRecipePlan.MAX_INPUTS",
            presentation,
        )
        self.assertIn("inputs.size() != MAX_INPUTS", presentation)
        self.assertIn("itemResourceCount > MAX_ITEM_RESOURCES", presentation)
        self.assertIn("toolRows > 1", presentation)
        for resource_kind in ["ITEM", "ENERGY", "TOOL"]:
            self.assertRegex(presentation, rf"\b{resource_kind}\b")
        self.assertIn("record Metadata(", presentation)
        self.assertIn("this.inputs = inputs.stream().map(ItemStack::copy).toList()", presentation)
        self.assertIn("this.output = output.copy()", presentation)
        self.assertIn("this.resources = List.copyOf(resources)", presentation)
        self.assertIn("return output.copy()", presentation)
        self.assertIn("metadataCarrier(Metadata metadata)", presentation)
        self.assertIn("metadataFromCarrier(ItemStack carrier)", presentation)
        self.assertNotIn("output.copyWithCount(1)", presentation)

    def test_recipe_presentation_is_built_server_side_and_uses_bounded_menu_sync(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        self.assertRegex(
            menu,
            r"\bpublic\s+RecipePresentation\s+getRecipePresentation\s*\(\s*\)",
        )
        presentation_getter = self.java_block(
            menu,
            r"\bpublic\s+RecipePresentation\s+getRecipePresentation\s*\(",
            "CraftingTerminalMenu.getRecipePresentation",
        )
        presentation_sync = self.java_block(
            menu,
            r"\bprivate\s+void\s+syncRecipePresentation\s*\(",
            "CraftingTerminalMenu.syncRecipePresentation",
        )
        self.assertIn("RecipePresentation.metadataFromCarrier(", presentation_getter)
        self.assertIn("RecipePresentation.MAX_INPUTS", presentation_getter)
        self.assertIn("metadata.itemResourceCount()", presentation_getter)
        self.assertIn("getEnergyPreview()", presentation_getter)
        self.assertIn("metadata.toolRequired() > 0", presentation_getter)
        self.assertIn("return new RecipePresentation(", presentation_getter)
        self.assertRegex(
            menu,
            r"private\s+void\s+syncRecipePresentation\s*\(\s*RecipeAdapterMatch\s+match",
        )
        self.assertIn("RecipeAdapterMatch.Presentation semantics = match.presentation()", presentation_sync)
        self.assertIn("match.presentationOutput(inputs, core.getLevel())", presentation_sync)
        self.assertIn("match.holder().id()", presentation_sync)
        self.assertIn("output.copy()", presentation_sync)
        self.assertIn("RecipePresentation.metadataCarrier(metadata)", presentation_sync)
        self.assertIn("SELECTION_SLOTS = PRESENTATION_METADATA_SLOT + 1", menu)
        self.assertIn("new SimpleContainer(SELECTION_SLOTS)", menu)
        self.assertIn("new ArrayList<>(2)", menu)

    def test_recipe_resources_keep_explicit_infinity_and_bulk_long_count_commits(self):
        presentation = self.read_required(
            "src/main/java/com/swear/autostorage/RecipePresentation.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        core = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java"
        )
        self.assertRegex(presentation, r"record Resource\([\s\S]*boolean infinite")
        amount_formatter = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeResourceAmountFormatter.java"
        )
        resource_row = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderResourceRow\s*\(",
            "CraftingTerminalScreen.renderResourceRow",
        )
        self.assertIn("resource.infinite()", resource_row)
        self.assertIn('infinite ? "∞"', amount_formatter)
        self.assertIn("amount.available()", resource_row)
        self.assertIn("amount.required()", resource_row)
        self.assertNotIn("plainSubstrByWidth", resource_row)
        self.assertIn("insertItemCount(", core)
        self.assertIn("extractItemCount(", core)
        commit = self.java_block(
            menu,
            r"\bprivate\s+boolean\s+commitCraft\s*\(",
            "CraftingTerminalMenu.commitCraft",
        )
        transaction = self.java_block(
            menu,
            r"\bprivate\s+static\s+boolean\s+applyCoreResourceDeltas\s*\(",
            "CraftingTerminalMenu.applyCoreResourceDeltas",
        )
        self.assertIn("applyCoreResourceDeltas(", commit)
        self.assertIn("StorageResourceTransaction.builder()", transaction)
        self.assertIn("core.applyResourceTransaction(", transaction)
        self.assertNotIn("while (remaining > 0)", commit)
        self.assertNotIn("while (remaining > 0)", transaction)

    def test_smithing_variant_paths_bind_same_id_to_exact_selected_output(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        adapters = self.read_required(
            "src/main/java/com/swear/autostorage/BuiltInRecipeAdapters.java"
        )
        match_contract = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeAdapterMatch.java"
        )
        commit = self.java_block(
            menu,
            r"\bprivate\s+boolean\s+commitCraft\s*\(",
            "CraftingTerminalMenu.commitCraft",
        )
        variant_lookup = self.java_block(
            menu,
            r"\bprivate\s+static\s+RecipeAdapterMatch\s+resolveAvailableRecipeVariant\s*\(",
            "CraftingTerminalMenu.resolveAvailableRecipeVariant",
        )
        variant_resolution = self.java_block(
            match_contract,
            r"\bList<RecipeAdapterMatch>\s+resolveVariants\s*\(",
            "RecipeAdapterMatch.resolveVariants",
        )
        selection_match = self.java_block(
            menu,
            r"\bprivate\s+static\s+boolean\s+matchesSelectionOutput\s*\(",
            "CraftingTerminalMenu.matchesSelectionOutput",
        )
        self.assertIn("resolveAvailableRecipeVariantById(", commit)
        self.assertNotIn("resolveAvailableRecipeMatchById(", menu)
        self.assertIn("selectionDisplay(plannedMatch, level, 0)", commit)
        selection_display = self.java_block(
            menu,
            r"\bprivate\s+static\s+ItemStack\s+selectionDisplay\s*\(",
            "CraftingTerminalMenu.selectionDisplay",
        )
        self.assertIn("match.presentationOutput(List.of(), level)", selection_display)
        self.assertIn("matchesSelectionOutput(variant, requestedOutput, level)", variant_lookup)
        self.assertIn("ItemStack.isSameItemSameComponents", selection_match)
        self.assertNotIn("presentationOutput(List.of(), level)", variant_resolution)
        self.assertNotIn("SMITHING_TRANSFORM_ID", menu)
        self.assertIn("matchesLookupOutput", match_contract)
        self.assertIn("matchesLookupOutput", adapters)
        self.assertRegex(
            adapters,
            r"SMITHING_TRANSFORM_ID[\s\S]*?BuiltInRecipeAdapters::smithingVariants",
        )
        self.assertRegex(
            adapters,
            r"SMITHING_TRIM_ID[\s\S]*?BuiltInRecipeAdapters::smithingVariants",
        )

    def test_recipe_workspace_stacks_diagram_above_ledger_and_footer(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )

        required_layout_regions = [
            "Rect recipeDiagram",
            "Rect recipeLedger",
            "Rect recipeFooter",
            "List<Rect> recipeNavigationButtons",
            "List<Rect> recipeCraftButtons",
            "Rect transformPanel",
            "Rect timedStationsPanel",
            "Rect instantStationsPanel",
            "Rect transformInput",
        ]
        self.assertEqual([], [region for region in required_layout_regions if region not in layout])
        self.assertIn("static final int CONTROL_SIZE = SLOT_SIZE", layout)
        self.assertNotIn("RESOURCE_COUNT = 9", layout)
        self.assertNotIn("recipeResourceCells", layout)
        self.assertRegex(
            layout,
            r"\bdiagram\.bottom\(\)\s*>\s*ledger\.y\(\)"
            r"[\s\S]{0,200}\bledger\.bottom\(\)\s*>\s*footer\.y\(\)",
            "recipe geometry must reject diagram/ledger/footer overlap",
        )
        for usage in [
            "geometry.recipeDiagram()",
            "geometry.recipeLedger()",
            "geometry.recipeFooter()",
            "geometry.recipeNavigationButtons()",
            "geometry.recipeCraftButtons()",
        ]:
            self.assertIn(usage, screen)

    def test_recipe_navigation_uses_two_explicit_previous_next_arrow_buttons(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        init = self.java_block(
            screen,
            r"\bprotected\s+void\s+init\s*\(",
            "CraftingTerminalScreen.init",
        )
        self.assertEqual(1, init.count("TerminalControlIcon.PREVIOUS"))
        self.assertEqual(1, init.count("TerminalControlIcon.NEXT"))
        self.assertIn("navigationButtons.get(0)", init)
        self.assertIn("navigationButtons.get(1)", init)
        self.assertIn("prevRecipeBtn = addRecipeNavigationButton(", init)
        self.assertIn("nextRecipeBtn = addRecipeNavigationButton(", init)
        self.assertNotIn("addTextCycleButton", init[:init.index("List<TerminalLayout.Rect> craftButtons")])

    def test_recipe_output_renders_the_exact_server_synced_stack_count(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        native = self.read_required(
            "src/main/java/com/swear/autostorage/NativeRecipeDiagramRenderer.java"
        )
        recipe_panel = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderRecipePanel\s*\(",
            "CraftingTerminalScreen.renderRecipePanel",
        )
        presentation = re.search(
            r"\bRecipePresentation\s+([A-Za-z_]\w*)\s*=\s*"
            r"menu\.getRecipePresentation\(\)\s*;",
            recipe_panel,
        )
        self.assertIsNotNone(
            presentation,
            "renderRecipePanel must read the server-synced RecipePresentation",
        )
        self.assertIn("activeRecipeDiagramRenderer(", recipe_panel)
        self.assertIn(".render(", recipe_panel)
        output = re.search(
            rf"\bItemStack\s+([A-Za-z_]\w*)\s*=\s*"
            rf"{re.escape(presentation.group(1))}\.output\(\)\s*;",
            native,
        )
        self.assertIsNotNone(output, "recipe output must come from RecipePresentation.output()")
        output_name = output.group(1)
        self.assertRegex(
            native,
            rf"StorageTerminalScreen\.renderTerminalIcon\(\s*graphics\s*,\s*{re.escape(output_name)}\s*,",
        )
        self.assertIn("if (TerminalResourceDisplay.isTyped(output))", native)
        self.assertIn("graphics.renderItemDecorations(font, output", native)
        self.assertIn("TerminalDisplayStack.amount(output)", native)

    def test_active_craftable_page_does_not_retain_a_per_menu_stack_cache(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        refresh = self.java_block(
            menu,
            r"\bpublic\s+void\s+refreshDisplayItemsFiltered\s*\(",
            "CraftingTerminalMenu.refreshDisplayItemsFiltered",
        )
        switch = self.java_block(
            menu,
            r"\bprivate\s+boolean\s+switchPage\s*\(",
            "CraftingTerminalMenu.switchPage",
        )
        self.assertNotIn("CraftableVisibleCache", menu)
        self.assertNotIn("craftableVisibleCache", menu)
        self.assertIn("cacheSharedCraftable(core, displayStacks)", refresh)
        self.assertIn("restoreSharedCraftableCache(core)", switch)

    def test_terminal_semantic_workspaces_use_vanilla_container_grammar(self):
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        native = self.read_required(
            "src/main/java/com/swear/autostorage/NativeRecipeDiagramRenderer.java"
        )

        for source in [storage, screen, native]:
            self.assertNotRegex(source, r"\bTERMINAL_(?:SURFACE|CARD|BORDER|ACCENT|TEXT)")
            self.assertNotIn("drawTerminalSurface", source)
            self.assertNotIn("drawTerminalCard", source)
            self.assertNotIn("drawTerminalControl", source)
            self.assertNotIn("drawTerminalSlot", source)
        for palette_name in [
            "ITEM_ROW_BACKGROUND", "ITEM_ROW_BORDER",
            "ENERGY_ROW_BACKGROUND", "ENERGY_ROW_BORDER",
            "TOOL_ROW_BACKGROUND", "TOOL_ROW_BORDER",
        ]:
            self.assertNotIn(palette_name, screen)
        self.assertIn("drawRaisedPanel(graphics, leftPos, topPos, row)", screen)
        self.assertIn(
            "drawInsetPanel(graphics, leftPos, topPos, geometry.searchBackground())",
            screen,
        )
        self.assertIn("drawInsetPanel(graphics, leftPos, topPos, panel)", screen)
        self.assertIn("drawVanillaSlot(graphics, x, y)", screen)
        self.assertIn("super.renderWidget(graphics, mouseX, mouseY, partialTick)", storage)
        self.assertRegex(screen, r"\.available\(\)\s*>=\s*[A-Za-z_]\w*\.required\(\)")

    def test_recipe_presentation_tolerates_in_flight_slot_sync(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        presentation = self.java_block(
            menu,
            r"\bpublic\s+RecipePresentation\s+getRecipePresentation\s*\(",
            "CraftingTerminalMenu.getRecipePresentation",
        )
        self.assertNotIn("Recipe presentation item resource is missing", presentation)
        self.assertNotIn("Recipe presentation tool resource is missing", presentation)
        self.assertGreaterEqual(
            presentation.count("return RecipePresentation.empty();"),
            3,
            "metadata and dependent hidden slots arrive in separate packets; partial snapshots must not crash rendering",
        )

    def test_recipe_ledger_is_top_aligned_and_never_exceeds_four_columns(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        cells = self.java_block(
            layout,
            r"\bprivate\s+static\s+List<Rect>\s+recipeLedgerCells\s*\(",
            "TerminalLayout.recipeLedgerCells",
        )
        columns = self.java_block(
            layout,
            r"\bprivate\s+static\s+int\s+recipeLedgerColumns\s*\(",
            "TerminalLayout.recipeLedgerColumns",
        )
        self.assertIn("RECIPE_LEDGER_MAX_COLUMNS = 4", layout)
        self.assertIn("recipeLedgerColumns(bounds)", cells)
        self.assertIn("RECIPE_LEDGER_MAX_COLUMNS", columns)
        self.assertRegex(cells, r"int\s+rows\s*=\s*\(resourceCount\s*\+\s*columns\s*-\s*1\)\s*/\s*columns")
        self.assertIn("int top = bounds.y();", cells)
        self.assertNotIn("(bounds.height() - rows * cellHeight) / 2", cells)
        self.assertIn("RECIPE_LEDGER_MAX_HEIGHT = SLOT_SIZE * 3", layout)

    def test_recipe_ledger_reduces_columns_when_the_viewport_is_too_narrow(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        cells = self.java_block(
            layout,
            r"\bprivate\s+static\s+List<Rect>\s+recipeLedgerCells\s*\(",
            "TerminalLayout.recipeLedgerCells",
        )
        columns = self.java_block(
            layout,
            r"\bprivate\s+static\s+int\s+recipeLedgerColumns\s*\(",
            "TerminalLayout.recipeLedgerColumns",
        )
        self.assertIn("RECIPE_LEDGER_MIN_CELL_WIDTH", layout)
        self.assertIn("recipeLedgerColumns(bounds)", cells)
        self.assertRegex(columns, r"bounds\.width\(\)\s*/\s*RECIPE_LEDGER_MIN_CELL_WIDTH")
        minimum = re.search(r"RECIPE_LEDGER_MIN_CELL_WIDTH\s*=\s*(\d+)", layout)
        self.assertIsNotNone(minimum)
        self.assertGreaterEqual(
            int(minimum.group(1)),
            56,
            "full-size /99.9E must fit beside the 16px recipe resource icon",
        )

    def test_available_recipe_amount_uses_high_contrast_dark_green(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        self.assertIn("0xFF176B2C", screen)
        self.assertNotIn("0xFF75D58A", screen)

    def test_recipe_amount_stays_inline_until_it_would_overflow(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        resource_row = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderResourceRow\s*\(",
            "CraftingTerminalScreen.renderResourceRow",
        )
        self.assertIn("amount.inline()", resource_row)
        self.assertRegex(
            resource_row,
            r"font\.width\(amount\.inline\(\)\)\s*<=\s*availableTextWidth",
        )

    def test_emi_diagram_can_match_a_unique_public_recipe_without_backing_metadata(self):
        renderer = self.read_required(
            "src/main/java/com/swear/autostorage/compat/EmiRecipeDiagramRenderer.java"
        )
        compatible = self.java_block(
            renderer,
            r"\bprivate\s+EmiRecipe\s+compatibleRecipe\s*\(",
            "EmiRecipeDiagramRenderer.compatibleRecipe",
        )
        self.assertIn("getRecipesByOutput", compatible)
        self.assertIn("presentation.resources()", renderer)
        self.assertNotIn("toomanyrecipeviewers", renderer.lower())
        self.assertNotIn("dev.nolij", renderer)

    def test_emi_public_recipe_match_preserves_exact_item_components(self):
        renderer = self.read_required(
            "src/main/java/com/swear/autostorage/compat/EmiRecipeDiagramRenderer.java"
        )
        matcher = self.java_block(
            renderer,
            r"\bprivate\s+static\s+boolean\s+matchesPublicRecipe\s*\(",
            "EmiRecipeDiagramRenderer.matchesPublicRecipe",
        )
        output = self.java_block(
            renderer,
            r"\bprivate\s+static\s+boolean\s+matchesOutput\s*\(",
            "EmiRecipeDiagramRenderer.matchesOutput",
        )
        self.assertIn("ItemStack.isSameItemSameComponents", matcher)
        self.assertIn("ItemStack.isSameItemSameComponents", output)
        self.assertNotIn("expected::isEqual", matcher)
        self.assertNotIn("candidate.isEqual(expected)", output)

    def test_emi_diagram_scales_public_widgets_to_the_recipe_panel(self):
        renderer = self.read_required(
            "src/main/java/com/swear/autostorage/compat/EmiRecipeDiagramRenderer.java"
        )
        supports = self.java_block(
            renderer,
            r"\bpublic\s+boolean\s+supports\s*\(",
            "EmiRecipeDiagramRenderer.supports",
        )
        state = self.java_block(
            renderer,
            r"\bprivate\s+WidgetState\s+widgetState\s*\(",
            "EmiRecipeDiagramRenderer.widgetState",
        )
        self.assertNotIn("<= geometry.diagram().width()", supports)
        self.assertNotIn("<= geometry.diagram().height()", supports)
        self.assertIn("Math.min", state)
        self.assertIn("scale", state)

    def test_recipe_presentation_keeps_data_and_container_slot_parity_guarded(self):
        tests = self.read_required(
            "src/main/java/com/swear/autostorage/gametest/TerminalFlowTests.java"
        )
        parity = self.java_block(
            tests,
            r"\bpublic\s+static\s+void\s+"
            r"crafting_menu_data_slot_parity_server_vs_buf_ctor\s*\(",
            "crafting menu data/container slot parity GameTest",
        )
        self.assertIn("serverCount != bufCount", parity)
        self.assertIn("serverMenu.slots.size()", parity)
        self.assertIn("bufMenu.slots.size()", parity)
        self.assertIn("metadataSlots", parity)
        self.assertIn("metadata slot", parity)
        self.assertIn("slot.isActive()", parity)
        self.assertIn("slot.mayPlace", parity)
        self.assertIn("slot.mayPickup", parity)

    def test_terminal_screens_use_shared_adaptive_geometry_and_original_slot_delegates(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )

        self.assertIn("record Geometry", layout)
        for stale_constant in ["SB_X", "SEARCH_X", "GRID_TOP", "SIDE_RAIL_X", "CRAFTING_BOTTOM_HEIGHT"]:
            self.assertNotIn(stale_constant, storage + crafting)

        self.assertIn("List.copyOf(menu.slots)", storage)
        self.assertIn("semanticSlots.get(menuIndex)", storage)
        self.assertIn("delegate.isActive()", storage)
        self.assertIn("delegate.mayPlace(stack)", storage)
        self.assertIn("delegate.mayPickup(player)", storage)
        self.assertNotIn("Slot old = menu.slots.get", storage + crafting)

    def test_emi_registers_adaptive_crafting_terminal_exclusion_areas(self):
        plugin = self.read_required(
            "src/main/java/com/swear/autostorage/compat/AutoStorageEmiPlugin.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        self.assertIn("registry.addExclusionArea(CraftingTerminalScreen.class", plugin)
        self.assertIn("screen.getEmiExclusionAreas()", plugin)
        self.assertIn("new Bounds(", plugin)
        self.assertIn("public List<Rect2i> getEmiExclusionAreas()", screen)

    def test_furnace_fuel_is_named_fuel_and_guide_uses_runtime_recipe_values(self):
        lang = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/en_us.json"
        ))
        self.assertEqual("Fuel", lang["gui.auto_storage.energy.furnace_fuel"])
        self.assertNotIn("gui.auto_storage.fuel_target_furnace", lang)
        self.assertNotIn("gui.auto_storage.fuel.cooking", lang)

        guide_paths = [
            "src/main/resources/assets/auto_storage/patchouli_books/guide/en_us/entries/energy_overview.json",
            "src/main/resources/assets/auto_storage/patchouli_books/guide/en_us/entries/fuel_conversion.json",
            "src/main/resources/assets/auto_storage/patchouli_books/guide/en_us/entries/recipe_costs.json",
        ]
        guide = "\n".join(self.read_required(path) for path in guide_paths)
        self.assertNotIn("Cooking Energy", guide)
        self.assertNotIn("200 Smelting", guide)
        self.assertNotIn("100 Blasting", guide)
        self.assertNotIn("second page button", guide)
        self.assertNotIn("displays all eight totals live", guide)
        self.assertIn("cooking time", guide)
        self.assertIn("runtime burn time", guide)
        self.assertIn("Auto", guide)
        self.assertIn("Neither mode selects or consumes anything", guide)
        self.assertIn("there is no hidden priority", guide)
        self.assertIn("Transform tab", guide)
        self.assertNotIn("Energy Reserves header", guide)
        self.assertNotIn("all currently registered totals", guide)
        self.assertNotIn("Consumables", guide)
        self.assertNotIn("Timed Stations", guide)
        self.assertIn("Processing Stations", guide)
        self.assertIn("Instant Stations", guide)
        self.assertIn("Elven Trade", guide)
        self.assertIn("runtime portal Mana cost", guide)

    def test_remote_access_is_pinned_to_exact_loaded_core_identity(self):
        core = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java"
        )
        record = self.read_required(
            "src/main/java/com/swear/autostorage/CoreStorageRecord.java"
        )
        self.assertIn("UUID.randomUUID()", record)
        self.assertIn("tag.putUUID(TAG_NETWORK_ID, networkId)", record)
        self.assertIn("tag.getUUID(TAG_NETWORK_ID)", record)
        self.assertIn("tag.putUUID(TAG_STORAGE_ID, storageId)", core)
        self.assertNotIn("TAG_NETWORK_ID", core)

        remote = self.read_required(
            "src/main/java/com/swear/autostorage/RemoteTerminalItem.java"
        )
        self.assertIn("TAG_CORE_ID", remote)
        self.assertIn("tag.hasUUID(TAG_CORE_ID)", remote)
        self.assertIn("core.getNetworkId().equals(getBoundCoreId(stack))", remote)

        menu = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalMenu.java"
        )
        self.assertIn("core.getNetworkId().equals(coreId)", menu)
        self.assertIn("level.dimension().equals(coreDimension)", menu)
        self.assertIn("level.hasChunkAt(corePos)", menu)

        entrypoint = self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        )
        self.assertGreaterEqual(entrypoint.count("menu.getCore(player.level())"), 2)

    def test_local_menus_and_buses_validate_a_current_loaded_network_path(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalMenu.java"
        )
        self.assertIn("AutoStorage.hasLoadedNetworkPath", menu)
        self.assertIn("AutoStorage.findLoadedNetworkPath", menu)

        for relative_path in [
            "src/main/java/com/swear/autostorage/ImportBusBlockEntity.java",
            "src/main/java/com/swear/autostorage/ExportBusBlockEntity.java",
        ]:
            text = self.read_required(relative_path)
            self.assertIn("cachedPath", text, relative_path)
            self.assertIn("AutoStorage.hasLoadedNetworkPath", text, relative_path)
            self.assertIn("AutoStorage.findLoadedNetworkPath", text, relative_path)

        entrypoint = self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        )
        self.assertIn("level.hasChunkAt(pos)", entrypoint)
        self.assertIn("isValidNetworkPath", entrypoint)

    def test_public_container_strategies_receive_isolated_stack_copies(self):
        terminal = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalMenu.java"
        )
        bus_menu = self.read_required(
            "src/main/java/com/swear/autostorage/BusConfigurationMenu.java"
        )
        self.assertRegex(
            terminal,
            r"strategy\.planDeposit\(\s*singleContainer\.copy\(\)",
        )
        self.assertRegex(
            bus_menu,
            r"strategy\.planDeposit\(\s*single\.copy\(\)",
        )

    def test_bus_open_payload_redacts_owner_identity(self):
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/BusConfigurationMenu.java"
        )
        self.assertIn(
            "host.getBusConfiguration().withoutOwner().save(root, buffer.registryAccess())",
            menu,
        )

    def test_optional_mod_linkage_failures_are_reported_explicitly(self):
        loader = self.read_required(
            "src/main/java/com/swear/autostorage/CompatibilityModuleLoader.java"
        )
        load_module = self.java_block(
            loader,
            r"\bprivate\s+static\s+void\s+loadModule\s*\(",
            "CompatibilityModuleLoader.loadModule",
        )
        failure = self.java_block(
            loader,
            r"\bprivate\s+static\s+IllegalStateException\s+failure\s*\(",
            "CompatibilityModuleLoader.failure",
        )
        self.assertIn("catch (LinkageError error)", load_module)
        self.assertIn("throw failure(module, targets, error)", load_module)
        self.assertRegex(
            load_module,
            r"catch \(InvocationTargetException exception\) \{\s*"
            r"throw failure\(module, targets, exception\.getCause\(\)\);",
            "reflected failures must preserve their original cause",
        )
        self.assertIn('"Compatibility module " + module.id()', failure)
        self.assertIn('" failed for loaded target mods [" + targets + "]"', failure)
        self.assertIn("cause);", failure)

    def test_storage_unit_guide_matches_self_drop_and_capacity_contract(self):
        for tier in range(1, 7):
            block_id = f"auto_storage:storage_unit_t{tier}"
            loot = json.loads(self.read_required(
                f"src/main/resources/data/auto_storage/loot_table/blocks/storage_unit_t{tier}.json"
            ))
            entries = loot["pools"][0]["entries"]
            self.assertEqual([block_id], [entry["name"] for entry in entries])

        guide = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/patchouli_books/guide/en_us/entries/unit_tiers.json"
        ))
        text = " ".join(page.get("text", "") for page in guide["pages"]).lower()
        self.assertIn("drops itself", text)
        self.assertIn("available type capacity decreases", text)
        self.assertIn("stored items stay in the core", text)

    def test_retired_bottle_energy_has_no_runtime_or_migration_surface(self):
        runtime_without_migration = "\n".join(
            self.read_required(path)
            for path in [
                "src/main/java/com/swear/autostorage/EnergyType.java",
                "src/main/java/com/swear/autostorage/FuelTable.java",
                "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java",
                "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java",
                "src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java",
                "src/main/java/com/swear/autostorage/CoreStorageRecord.java",
                "src/main/java/com/swear/autostorage/CoreStorageRepository.java",
            ]
        )
        self.assertNotIn("BOTTLE_FUEL", runtime_without_migration)
        self.assertNotIn("bottle_fuel", runtime_without_migration)
        self.assertNotIn("legacyBottleFuel", runtime_without_migration)

        player_facing_surfaces = "\n".join(
            self.read_required(path)
            for path in [
                "scripts/prepare_prism_gui_world.py",
                "scripts/run_prism_gui_session.py",
                "src/main/resources/assets/auto_storage/patchouli_books/guide/en_us/entries/energy_overview.json",
                "src/main/resources/assets/auto_storage/patchouli_books/guide/en_us/entries/fuel_conversion.json",
                "src/main/resources/assets/auto_storage/patchouli_books/guide/en_us/entries/crafting_terminal.json",
            ]
        )
        self.assertNotIn("bottle_fuel", player_facing_surfaces)
        self.assertNotIn("Bottle Energy", player_facing_surfaces)
        self.assertNotIn("Coal, Blaze Rod, and Glass Bottle", player_facing_surfaces)

    def test_player_facing_terminal_text_is_concise_localized_and_locale_complete(self):
        agents = self.read_required("AGENTS.md")
        self.assertIn("Simple is better", agents)
        self.assertIn("player-facing", agents)

        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        tooltip = self.java_block(
            storage,
            r"\b(?:protected\s+)?static\s+Tooltip\s+createCycleTooltip\s*\(",
            "concise cycle tooltip",
        )
        self.assertNotIn("cycle_hint", tooltip)
        self.assertNotIn('"\\n"', tooltip)

        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        self.assertNotIn('Component.literal("Previous recipe")', crafting)
        self.assertNotIn('Component.literal("Next recipe")', crafting)
        self.assertIn('Component.translatable("gui.auto_storage.previous_recipe")', crafting)
        self.assertIn('Component.translatable("gui.auto_storage.next_recipe")', crafting)

        en_us = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/en_us.json"
        ))
        zh_tw = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/zh_tw.json"
        ))
        self.assertEqual(set(en_us), set(zh_tw))
        self.assertNotIn("gui.auto_storage.energy.bottle_fuel", en_us)
        self.assertNotIn("tooltip.auto_storage.cycle_hint", en_us)
        self.assertIn("gui.auto_storage.previous_recipe", en_us)
        self.assertIn("gui.auto_storage.next_recipe", en_us)
        self.assertIn("gui.auto_storage.recipe_station", en_us)
        self.assertEqual(zh_tw["gui.auto_storage.energy.blaze_fuel"], "釀造能量")

    def test_recipe_empty_state_station_badge_and_output_icon_match_current_state(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        native = self.read_required(
            "src/main/java/com/swear/autostorage/NativeRecipeDiagramRenderer.java"
        )
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        storage = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )

        recipe_panel = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderRecipePanel\s*\(",
            "recipe panel renderer",
        )
        self.assertIn("menu.getSelectedStack()", recipe_panel)
        self.assertIn('"gui.auto_storage.no_recipe"', recipe_panel)
        clear_presentation = self.java_block(
            menu,
            r"\bprivate\s+void\s+clearRecipePresentation\s*\(",
            "recipe presentation clear",
        )
        self.assertIn("selectedOutput", clear_presentation)
        self.assertIn("PRESENTATION_OUTPUT_SLOT", clear_presentation)

        self.assertIn("renderRecipeStationHint", screen)
        displayed_station = self.java_block(
            screen,
            r"\bprivate\s+ItemStack\s+displayedRecipeStation\s*\(",
            "cycling recipe station badge",
        )
        self.assertIn("stationCycleAnchorMillis", displayed_station)
        self.assertIn("stationCycleRecipeId", displayed_station)
        self.assertIn("stationCycleInstalled", displayed_station)
        self.assertIn("System.currentTimeMillis()", displayed_station)
        self.assertIn("RecipeStationCycle.cycle(now - stationCycleAnchorMillis)", displayed_station)
        self.assertNotIn("getGameTime()", displayed_station)
        self.assertIn("presentation.stationForCycle(cycle)", displayed_station)
        self.assertIn("displayedRecipeStation(presentation)", screen)
        self.assertNotIn("stationForCycle", native)
        recipe_geometry = self.java_block(
            layout,
            r"\bprivate\s+static\s+RecipeGeometry\s+recipeGeometry\s*\(",
            "recipe geometry",
        )
        station_declaration = recipe_geometry[recipe_geometry.index("Rect station"):
                                              recipe_geometry.index("Rect shapelessMarker")]
        self.assertIn("diagram.right()", station_declaration)
        self.assertIn("diagram.bottom()", station_declaration)

        icon_button = self.java_block(
            storage,
            r"\bclass\s+TerminalIconButton\b",
            "mutable terminal item-icon control",
        )
        self.assertIn("setItemIcon", icon_button)
        self.assertNotIn("final ItemStack itemIcon", icon_button)
        self.assertIn("Items.PLAYER_HEAD", screen)
        self.assertIn("AutoStorage.STORAGE_CORE_ITEM", screen)
        self.assertIn("outputDestinationRailBtn.setItemIcon", screen)

    def test_transform_is_separate_and_stations_use_two_category_rows(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        machine_table = self.read_required(
            "src/main/java/com/swear/autostorage/MachineEnergyTable.java"
        )
        descriptor = self.read_required(
            "src/main/java/com/swear/autostorage/MachineDescriptor.java"
        )

        for declaration in [
            "record FuelDescriptorCounts",
            "Rect transformPanel",
            "Rect timedStationsPanel",
            "Rect instantStationsPanel",
            "Rect fuelStatus",
            "Rect transformTargetSearch",
            "PagedList transformTargetList",
            "Rect transformInput",
            "List<Rect> transformAmountButtons",
            "FlowGrid transformCards",
            "FlowGrid timedStationsGrid",
            "FlowGrid instantStationsGrid",
        ]:
            self.assertIn(declaration, layout)
        for legacy in [
            "MACHINE_FLOW_ROWS",
            "Rect machinePanel",
            "Rect fuelPanel",
            "Rect fuelControlPanel",
            "FlowGrid machineGrid",
            "FlowGrid reserveGrid",
        ]:
            self.assertNotIn(legacy, layout)

        self.assertIn("pagedFlowGrid", layout)
        self.assertNotIn("horizontalFlowGrid", layout)
        self.assertIn("renderConsumablesPanel", screen)
        self.assertIn("renderTransformTargetList", screen)
        self.assertIn("renderTransformCards", screen)
        self.assertNotIn("renderTransformPreview", screen)
        self.assertIn("renderTimedStationsPanel", screen)
        self.assertIn("renderInstantStationsPanel", screen)
        self.assertIn("CraftingTerminalPage.TRANSFORM", screen)
        self.assertIn("CraftingTerminalPage.STATIONS", screen)
        self.assertNotIn("renderMachinePanel", screen)
        self.assertNotIn("renderFuelPanel", screen)
        self.assertNotIn("renderFuelControlPanel", screen)
        self.assertNotIn('"gui.auto_storage.installed_machines"', screen)
        self.assertNotIn('"gui.auto_storage.energy_reserves"', screen)
        for category in ["PROCESS", "INSTANT", "TRANSFORM"]:
            self.assertIn(category, machine_table)
        self.assertNotIn("CONSUMABLE", machine_table)
        self.assertNotIn("Consumable", machine_table + descriptor)

    def test_fuel_panels_fill_vertical_space_and_type_capacity_is_inventory_side(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        assembly = self.java_block(
            layout,
            r"\bprivate\s+static\s+Geometry\s+assembleCraftingGeometry\s*\(",
            "TerminalLayout.assembleCraftingGeometry",
        )
        self.assertIn("fuelAreaBottom - TOP_HEIGHT", assembly)
        self.assertIn("playerInventory.right() + CONTROL_GAP", assembly)
        self.assertNotIn("fuelStatus.x() - CONTROL_GAP", assembly)
        status = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderUtilityStatus\s*\(",
            "CraftingTerminalScreen.renderUtilityStatus",
        )
        self.assertIn("drawRaisedPanel", status)
        self.assertNotIn("machineEnergyIndexAt(", status)
        self.assertNotIn("tooltip.auto_storage.machine_installed", status)
        self.assertNotIn("tooltip.auto_storage.machine_rate", status)
        self.assertRegex(
            status,
            r'Component\.translatable\(\s*"gui\.auto_storage\.type_capacity"',
        )

    def test_fuel_descriptor_grids_are_multi_row_and_paged_for_large_integrations(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        grid = self.java_block(
            layout,
            r"\bprivate\s+static\s+FlowGrid\s+pagedFlowGrid\s*\(",
            "TerminalLayout.pagedFlowGrid",
        )
        self.assertIn("bounds.height() / FUEL_CATEGORY_CELL_HEIGHT", grid)
        self.assertIn("columns * rows", grid)

        en_us = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/en_us.json"
        ))
        zh_tw = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/zh_tw.json"
        ))
        self.assertEqual("Transform", en_us["gui.auto_storage.fuel_group.consumables"])
        self.assertEqual("Processing Stations", en_us["gui.auto_storage.fuel_group.timed_stations"])
        self.assertEqual("Instant Stations", en_us["gui.auto_storage.fuel_group.instant_stations"])
        self.assertEqual("Axe Uses", en_us["gui.auto_storage.axe_energy"])
        self.assertEqual("斧頭使用次數", zh_tw["gui.auto_storage.axe_energy"])
        self.assertEqual(set(en_us), set(zh_tw))
        self.assertNotIn("Stations & Axe Energy", en_us.values())

    def test_station_search_reuses_top_search_and_nonempty_query_shows_unified_results(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        model = self.read_required(
            "src/main/java/com/swear/autostorage/FuelSearchModel.java"
        )
        en_us = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/en_us.json"
        ))
        zh_tw = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/zh_tw.json"
        ))

        for declaration in [
            "Rect fuelSearchBox",
            "Rect fuelSearchPanel",
            "FlowGrid fuelSearchGrid",
            "FuelPageControls fuelSearchPageControls",
        ]:
            self.assertIn(declaration, layout)
        assembly = self.java_block(
            layout,
            r"\bprivate\s+static\s+Geometry\s+assembleCraftingGeometry\s*\(",
            "TerminalLayout.assembleCraftingGeometry",
        )
        self.assertIn("Rect fuelSearchBox = searchBox", assembly)
        self.assertIn("fuelSearchPanel", assembly)
        self.assertIn("pagedFlowGrid", assembly)
        self.assertNotIn("fuelSearchButton", layout)

        for declaration in [
            "EditBox fuelSearchBox",
            "FuelPageButtons fuelSearchPageButtons",
            "FuelSearchModel.Index fuelSearchIndex",
            "boolean fuelSearchActive",
            "int fuelSearchPage",
        ]:
            self.assertIn(declaration, screen)
        self.assertIn("FuelSearchModel.search", screen)
        self.assertIn("FuelSearchModel.index", screen)
        self.assertIn("renderFuelSearchResults", screen)
        self.assertNotIn("fuelSearchBtn", screen)
        self.assertIn("fuelSearchActive = !query.isEmpty()", screen)
        self.assertNotIn("fuelSearchActive = !text.isBlank()", screen)
        self.assertIn("focusActiveSearchBox(", screen)
        self.assertIn("fuelSearchBox.setMaxLength(50)", screen)
        self.assertNotIn("fuelSearchBox.setHint", screen)
        self.assertIn("geometry.fuelSearchGrid()", screen)
        self.assertIn("geometry.fuelSearchPanel()", screen)
        self.assertIn("geometry.fuelSearchPageControls()", screen)
        self.assertIn("TerminalSearchQuery.compile", model)
        self.assertIn("record IndexedEntry", model)
        self.assertIn("descriptor.variants()", model)
        self.assertIn("descriptor.acceptedItems().getItems()", model)

        for key in [
            "gui.auto_storage.fuel_search",
            "gui.auto_storage.fuel_search_results",
            "gui.auto_storage.fuel_search_empty",
        ]:
            self.assertIn(key, en_us)
        self.assertEqual(set(en_us), set(zh_tw))

    def test_only_processing_station_slots_overlay_installed_count_and_search_shows_work(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        render_slot_contents = self.java_block(
            screen,
            r"\bprotected\s+void\s+renderSlotContents\s*\(",
            "CraftingTerminalScreen.renderSlotContents",
        )
        category_render = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderMachineCategoryCells\s*\(",
            "CraftingTerminalScreen.renderMachineCategoryCells",
        )
        self.assertIn("CraftingTerminalMenu.MACHINE_SLOT_START", render_slot_contents)
        self.assertNotIn("formatAmount(stack.getCount())", render_slot_contents)
        self.assertIn("ItemStack icon = installed.copyWithCount(1)", category_render)
        self.assertIn("renderNetworkAmount(", category_render)
        self.assertNotIn("renderItemDecorations", category_render)
        self.assertRegex(
            category_render,
            r"if\s*\(\s*category\s*==\s*MachineCategory\.PROCESS\s*\)"
            r"\s*\{[\s\S]*?renderNetworkAmount",
        )

        search_results = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderFuelSearchResults\s*\(",
            "CraftingTerminalScreen.renderFuelSearchResults",
        )
        self.assertIn("ItemStack icon = installed.copyWithCount(1)", search_results)
        self.assertIn("graphics.renderItem(icon", search_results)
        self.assertIn("renderNetworkAmount(", search_results)
        self.assertNotIn("graphics.renderItemDecorations", search_results)
        self.assertIn("descriptor.category() == MachineCategory.PROCESS", search_results)
        self.assertIn("machineStoredAmount(descriptor)", search_results)

    def test_gui_world_has_an_active_executable_scenario_contract(self):
        contract = self.read_required("docs/gui-test-world.md")
        for requirement in [
            "crafting-fuel-page",
            "one repository record",
            "Integer.MAX_VALUE",
            "player inventory",
            "start target",
            "visual assertions",
        ]:
            self.assertIn(requirement, contract)

    def test_fuel_descriptor_rows_have_explicit_previous_next_buttons_and_keep_wheel_paging(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        en_us = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/en_us.json"
        ))
        zh_tw = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/zh_tw.json"
        ))

        self.assertIn("record FuelPageControls", layout)
        for controls in [
            "transformTargetPageControls",
            "transformCardPageControls",
            "timedStationsPageControls",
            "instantStationsPageControls",
            "fuelSearchPageControls",
        ]:
            self.assertIn(f"FuelPageControls {controls}", layout)
            self.assertIn(f"geometry.{controls}()", screen)

        init = self.java_block(
            screen,
            r"\bprotected\s+void\s+init\s*\(",
            "CraftingTerminalScreen.init",
        )
        self.assertEqual(
            5,
            init.count("addFuelPageControls("),
            "Transform targets/cards, Station rows, and unified search need explicit paging controls",
        )
        page_controls = self.java_block(
            screen,
            r"\bprivate\s+\w+\s+addFuelPageControls\s*\(",
            "CraftingTerminalScreen.addFuelPageControls",
        )
        self.assertIn("controls.previous()", page_controls)
        self.assertIn("controls.next()", page_controls)
        self.assertIn('"gui.auto_storage.previous_fuel_page"', page_controls)
        self.assertIn('"gui.auto_storage.next_fuel_page"', page_controls)
        self.assertIn("repositionFuelSlots()", page_controls)
        page_state = self.java_block(
            screen,
            r"\bprivate\s+void\s+updateFuelPageButtons\s*\(",
            "CraftingTerminalScreen.updateFuelPageButtons",
        )
        self.assertIn("boolean visible = fuel && pageCount > 1;", page_state)

        scroll = self.java_block(
            screen,
            r"^[ ]{4}public\s+boolean\s+mouseScrolled\s*\(",
            "CraftingTerminalScreen.mouseScrolled",
        )
        for panel, page in [
            ("timedStationsPanel", "timedStationPage"),
            ("instantStationsPanel", "instantStationPage"),
        ]:
            self.assertIn(f"geometry.{panel}().contains", scroll)
            self.assertIn(f"{page} = Math.clamp", scroll)
        self.assertGreaterEqual(
            scroll.count("repositionFuelSlots()"),
            2,
            "wheel paging must remain available for both Stations rows",
        )
        self.assertGreaterEqual(
            scroll.count("updateFuelPageButtonStates()"),
            3,
            "wheel paging and Transform target cycling must refresh page state",
        )

        self.assertEqual(set(en_us), set(zh_tw))
        self.assertIn("gui.auto_storage.previous_fuel_page", en_us)
        self.assertIn("gui.auto_storage.next_fuel_page", en_us)

    def test_craftable_recipe_hot_path_uses_exact_dispatch_shared_cache_and_typed_candidates(self):
        registry = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeAdapterRegistry.java"
        )
        catalog = self.read_required(
            "src/main/java/com/swear/autostorage/CraftableRecipeCatalog.java"
        )
        adapter = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeAdapter.java"
        )
        family = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeFamily.java"
        )
        entrypoint = self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        )
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )

        self.assertIn("Map<RecipeFamilyKey, RecipeAdapter>", registry)
        self.assertIn("exactAdaptersByKey.get", registry)
        self.assertIn("static final Map<RecipeManager", catalog)
        self.assertIn("WeakHashMap", catalog)
        self.assertIn("static void prewarm", catalog)
        self.assertIn("CraftableRecipeCatalog.prewarm", entrypoint)
        for phase in [
            "candidateSelectionNanos",
            "variantResolutionNanos",
            "previewSimulationNanos",
            "sortNanos",
            "syncNanos",
        ]:
            self.assertIn(phase, menu)
        craftable = self.java_block(
            menu,
            r"\bprivate\s+CraftableBuildResult\s+buildCraftableDisplayStacks\s*\(",
            "CraftingTerminalMenu.buildCraftableDisplayStacks",
        )
        self.assertNotIn("getRecipeManager().byKey", craftable)
        self.assertIn("candidate.match()", craftable)
        self.assertIn("stationAvailability.computeIfAbsent", craftable)
        self.assertLess(
            craftable.index("stationAvailability.computeIfAbsent"),
            craftable.index("candidate.match()"),
        )
        self.assertIn("candidateIndex(RecipeHolder<?> holder, Level level)", adapter)
        self.assertIn("typedCandidateIndex", family)

    def test_craftable_retained_state_releases_transient_classification_graphs(self):
        family = self.read_required(
            "src/main/java/com/swear/autostorage/RecipeFamily.java"
        )
        catalog = self.read_required(
            "src/main/java/com/swear/autostorage/CraftableRecipeCatalog.java"
        )
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        api_docs = self.read_required("docs/recipe-family-api.md")
        matrix = self.read_required(
            "src/compatibilityMatrixFixture/java/com/swear/autostorage/fixture/"
            "compatibilitymatrix/CraftablePerformanceGameTests.java"
        )
        self.assertIn(
            "void clearRuntimeCaches()",
            family,
            "RecipeFamily must expose production cache release for typed plan/contract maps",
        )
        release = self.java_block(
            catalog,
            r"\bstatic\s+void\s+releaseTransientMatches\s*\(",
            "CraftableRecipeCatalog.releaseTransientMatches",
        )
        self.assertIn(
            "RECIPE_FAMILY_REGISTRY",
            release,
            "transient release must clear optional RecipeFamily plan/contract caches",
        )
        self.assertIn(
            "clearRuntimeCaches()",
            release,
            "transient release must clear RecipeFamily runtime caches",
        )
        entry = self.java_block(
            catalog,
            r"\bprivate\s+static\s+final\s+class\s+CatalogEntry\b",
            "CraftableRecipeCatalog.CatalogEntry",
        )
        self.assertIn(
            "releaseTransientMatches",
            entry,
            "CatalogEntry must drop lazy RecipeAdapterMatch after shared listing exists",
        )
        self.assertNotIn(
            "fixedVariants",
            entry,
            "CatalogEntry must not retain a fixedVariants cache across Craftable prepares",
        )
        self.assertIn(
            "match = null",
            entry,
            "CatalogEntry release must drop retained RecipeAdapterMatch graphs",
        )
        shared_cache = self.java_block(
            menu,
            r"\bprivate\s+void\s+cacheSharedCraftable\s*\(",
            "CraftingTerminalMenu.cacheSharedCraftable",
        )
        self.assertIn(
            "CraftableRecipeCatalog.releaseTransientMatches()",
            shared_cache,
            "production shared Craftable cache must release transient catalog/family graphs",
        )
        self.assertIn(
            "server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1,",
            shared_cache,
            "transient release must run on the next server tick after selection and preview "
            "follow-up work completes",
        )
        self.assertNotIn(
            "server.execute(CraftableRecipeCatalog::releaseTransientMatches)",
            shared_cache,
            "same-thread execute may release before selection and preview repopulate caches",
        )
        self.assertNotIn(
            "if (usePlayerInventory) return;",
            shared_cache,
            "player-inventory Craftable builds must also release transient catalog/family graphs",
        )
        self.assertIn(
            "if (!usePlayerInventory && level != null)",
            shared_cache,
            "only the shared-result write may be skipped for player-inventory Craftable builds",
        )
        self.assertRegex(
            api_docs,
            r"until the completed\s+Craftable listing releases them",
            "the public addon contract must document the actual fixed-plan cache lifetime",
        )
        self.assertNotIn(
            "presentation state on every Craftable rebuild",
            api_docs,
            "the public addon contract must not promise world-lifetime fixed-plan caching",
        )
        has_potential = self.java_block(
            menu,
            r"\bprivate\s+boolean\s+hasPotentialRecipeInputs\s*\(",
            "CraftingTerminalMenu.hasPotentialRecipeInputs",
        )
        self.assertIn(
            "representativeItemsExhaustive()",
            has_potential,
            "hasPotentialRecipeInputs must use exhaustive representative ItemKey totals "
            "instead of scanning every stored component variant",
        )
        measure = self.java_block(
            matrix,
            r"\bprivate\s+void\s+measureSharedIndex\s*\(",
            "CraftablePerformanceGameTests.measureSharedIndex",
        )
        self.assertNotIn(
            "releaseTransientMatches",
            measure,
            "shared-index measurement must not call catalog release itself",
        )
        self.assertNotIn(
            "clearRecipeFamilyCaches()",
            measure,
            "shared-index measurement must not clear family caches after production steady state",
        )
        self.assertIn(
            "MAX_BASELINE_INDEX_RETAINED_BYTES = 9L * 1024L * 1024L",
            matrix,
        )

    def test_craftable_catalog_reuses_resolved_stack_independent_match_without_retention(self):
        catalog = self.read_required(
            "src/main/java/com/swear/autostorage/CraftableRecipeCatalog.java"
        )
        entry = self.java_block(
            catalog,
            r"\bprivate\s+static\s+final\s+class\s+CatalogEntry\b",
            "CraftableRecipeCatalog.CatalogEntry",
        )
        resolve = self.java_block(
            entry,
            r"\bprivate\s+List<RecipeAdapterMatch>\s+resolveVariants\s*\(",
            "CraftableRecipeCatalog.CatalogEntry.resolveVariants",
        )
        self.assertRegex(
            resolve,
            r"if\s*\(\s*!adapter\.requiresAvailableStacksForVariants\(\)\s*"
            r"&&\s*baseMatch\.typedRecipePlan\(\)\.isPresent\(\)\s*\)\s*\{\s*"
            r"return\s+List\.of\(baseMatch\);",
            "an already-resolved stack-independent typed match must not rerun adapter "
            "variant resolution during Craftable preparation",
        )
        self.assertNotIn(
            "pendingTypedPlan",
            resolve,
            "legacy and built-in contracts still need adapter-level output validation",
        )
        self.assertLess(
            resolve.index("return List.of(baseMatch)"),
            resolve.index("resolveVariantsFromSnapshot"),
            "the resolved-match fast path must run before adapter variant resolution",
        )
        self.assertNotIn(
            "fixedVariants",
            entry,
            "the fast path must not restore the recipe-keyed fixed-variant retention removed by #79",
        )

    def test_processing_cells_keep_fixed_status_panel_free_of_duplicate_details(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        status = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderUtilityStatus\s*\(",
            "CraftingTerminalScreen.renderUtilityStatus",
        )

        self.assertIn("static Rect fuelAmountBounds(Rect", layout)
        self.assertNotIn("descriptor.representativeStack().getHoverName()", status)
        self.assertNotIn("installed.getHoverName()", status)
        self.assertNotIn("descriptor.rateFor(installed)", status)
        self.assertNotIn('"tooltip.auto_storage.machine_rate"', status)
        self.assertIn('"gui.auto_storage.type_capacity"', status)

    def test_cycle_controls_middle_reset_and_only_boolean_controls_have_status_lights(self):
        direction = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalCycleDirection.java"
        )
        storage_screen = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalScreen.java"
        )
        storage_menu = self.read_required(
            "src/main/java/com/swear/autostorage/StorageTerminalMenu.java"
        )
        crafting_screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        crafting_menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )

        self.assertNotIn("RESET", direction)
        cycle_button = self.java_block(
            storage_screen,
            r"\bclass\s+TerminalCycleButton\b",
            "terminal cycle button",
        )
        self.assertIn("Runnable resetAction", cycle_button)
        self.assertRegex(cycle_button, r"button\s*==\s*2")
        self.assertIn("resetAction.run()", cycle_button)
        for constant in [
            "RESET_SORT_ORDER_BUTTON",
            "RESET_SORT_MODE_BUTTON",
            "RESET_SEARCH_MODE_BUTTON",
        ]:
            self.assertIn(constant, storage_menu)
        self.assertIn("sortOrder = SortOrder.ASCENDING", storage_menu)
        self.assertIn("sortMode = SortMode.NAME", storage_menu)
        self.assertIn("searchMode = SearchMode.OFF", storage_menu)
        self.assertIn("RESET_OUTPUT_DESTINATION_BUTTON", crafting_menu)
        self.assertIn("RESET_PLAYER_INVENTORY_BUTTON", crafting_menu)
        self.assertIn("outputDestination = TerminalOutputDestination.PLAYER", crafting_menu)
        self.assertIn("selectedTransformTarget = null", crafting_menu)
        self.assertIn("usePlayerInventory = false", crafting_menu)

        side_rail = self.java_block(
            crafting_screen,
            r"\bprivate\s+void\s+renderSideRail\s*\(",
            "CraftingTerminalScreen.renderSideRail",
        )
        self.assertIn("preferences.usePlayerInventory()", side_rail)
        self.assertNotIn("menu.getOutputDestination()", side_rail)
        self.assertNotIn("outputDestinationIndex", side_rail)

    def test_recipe_prompt_wraps_and_amount_actions_form_one_segmented_strip(self):
        layout = self.read_required(
            "src/main/java/com/swear/autostorage/TerminalLayout.java"
        )
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        recipe_panel = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderRecipePanel\s*\(",
            "CraftingTerminalScreen.renderRecipePanel",
        )
        empty_start = recipe_panel.index("if (presentation.isEmpty())")
        empty_branch = recipe_panel[empty_start:recipe_panel.index("return;", empty_start)]
        self.assertIn("geometry.recipeContent()", recipe_panel)
        self.assertIn("font.split", empty_branch)
        self.assertNotIn("plainSubstrByWidth", empty_branch)
        self.assertIn("renderWrappedPrompt", empty_branch)
        self.assertIn("content", empty_branch)
        self.assertNotIn("ledger", empty_branch)
        self.assertLess(
            recipe_panel.index("return;", empty_start),
            recipe_panel.index("leftPos + ledger.x()"),
        )

        self.assertIn("class RecipeAmountButton", screen)
        self.assertIn("RecipeAmountSegment", screen)
        self.assertIn("addRecipeAmountButton", screen)
        init = self.java_block(
            screen,
            r"\bprotected\s+void\s+init\s*\(",
            "CraftingTerminalScreen.init",
        )
        amount_controls = init[init.index("List<TerminalLayout.Rect> craftButtons"):]
        self.assertNotIn("Button.builder", amount_controls)
        self.assertEqual(4, amount_controls.count("addRecipeAmountButton("))
        self.assertIn("contiguousSegmentRects", layout)

    def test_transform_sidebar_and_cards_render_in_the_normal_container_pass(self):
        screen = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalScreen.java"
        )
        self.assertNotIn("FuelTargetPopup", screen)
        self.assertNotIn("fuelTargetPopup.render", screen)
        panel = self.java_block(
            screen,
            r"\bprivate\s+void\s+renderConsumablesPanel\s*\(",
            "CraftingTerminalScreen.renderConsumablesPanel",
        )
        self.assertIn("renderTransformTargetList", panel)
        self.assertIn("renderTransformCards", panel)
        self.assertNotIn("renderTransformPreview", panel)

    def test_terminal_control_name_icon_is_even_grid_centered(self):
        atlas = ROOT / "src/main/resources/assets/auto_storage/textures/gui/terminal_controls.png"
        width, height, pixels = self.rgba_png_pixels(atlas)
        self.assertEqual((256, 16), (width, height))
        first = 2 * 16
        points = [
            (x, y)
            for y in range(16)
            for x in range(16)
            if pixels[y * width + first + x][3] != 0
        ]
        self.assertEqual((4, 11), (min(x for x, _ in points), max(x for x, _ in points)))
        self.assertEqual(
            sorted(points),
            sorted((15 - x, y) for x, y in points),
            "the A glyph must be mirrored around the even-grid axis x=7.5",
        )

    def test_machine_descriptors_have_a_public_server_owned_registry_and_fixed_menu_bank(self):
        api = self.read_required(
            "src/main/java/com/swear/autostorage/MachineDescriptorApi.java"
        )
        descriptor = self.read_required(
            "src/main/java/com/swear/autostorage/MachineDescriptor.java"
        )
        table = self.read_required(
            "src/main/java/com/swear/autostorage/MachineEnergyTable.java"
        )
        menu = self.read_required(
            "src/main/java/com/swear/autostorage/CraftingTerminalMenu.java"
        )
        packet = self.read_required(
            "src/main/java/com/swear/autostorage/MachineDescriptorStatePacket.java"
        )
        core = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java"
        )
        record = self.read_required(
            "src/main/java/com/swear/autostorage/CoreStorageRecord.java"
        )

        self.assertIn("REGISTRY_KEY", api)
        self.assertIn("createDeferredRegister", api)
        self.assertRegex(api, r"MAX_DESCRIPTORS\s*=\s*256")
        self.assertIn("Ingredient", descriptor)
        self.assertIn("Component stationLabel", descriptor)
        self.assertIn("stationLabel()", descriptor)
        self.assertIn("TransformValue", descriptor)
        self.assertIn("ComponentSerialization.STREAM_CODEC", table)
        self.assertIn("writeSnapshot", table)
        self.assertIn("readSnapshot", table)
        self.assertIn(
            "MACHINE_SLOT_COUNT = MachineDescriptorApi.MAX_DESCRIPTORS",
            menu,
        )
        self.assertIn("playToClient", self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        ))
        self.assertIn("descriptorId", packet)
        self.assertIn('TAG_MACHINE_DESCRIPTORS = "machineDescriptors"', record)
        self.assertIn("unresolvedMachineEntries", record)
        self.assertNotIn("recoverUnregisteredMachine", core)

    def test_storage_core_breaking_is_tool_independent_creative_safe_and_recoverable(self):
        registration = self.read_required(
            "src/main/java/com/swear/autostorage/AutoStorage.java"
        )
        block = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlock.java"
        )
        item = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlockItem.java"
        )
        repository = self.read_required(
            "src/main/java/com/swear/autostorage/CoreStorageRepository.java"
        )
        wrench = self.read_required(
            "src/main/java/com/swear/autostorage/WrenchActions.java"
        )

        core_registration = self.java_block(
            registration,
            r"\bpublic\s+static\s+final\s+DeferredBlock<Block>\s+STORAGE_CORE\b",
            "Storage Core registration",
        )
        self.assertNotIn("requiresCorrectToolForDrops", core_registration)
        self.assertIn("playerWillDestroy", block)
        self.assertIn("prepareRecoveryDrop", block)
        self.assertIn("onExplosionHit", block)
        self.assertIn("RECOVERY_ID", item)
        self.assertIn("CoreStorageRepository", item)
        self.assertIn("extends SavedData", repository)
        self.assertIn("reissueLatest", repository)
        self.assertIn("claimIntoFresh", repository)
        self.assertIn("RegisterCommandsEvent", registration)
        self.assertIn("prepareRecoveryDrop", wrench)

    def test_remote_terminal_uses_its_own_container_title(self):
        remote = self.read_required(
            "src/main/java/com/swear/autostorage/RemoteTerminalItem.java"
        )
        en_us = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/en_us.json"
        ))
        zh_tw = json.loads(self.read_required(
            "src/main/resources/assets/auto_storage/lang/zh_tw.json"
        ))
        self.assertIn('Component.translatable("container.auto_storage.remote_terminal")', remote)
        self.assertEqual("Remote Terminal", en_us["container.auto_storage.remote_terminal"])
        self.assertEqual("遠端終端機", zh_tw["container.auto_storage.remote_terminal"])

    def test_core_payload_is_owned_by_bounded_world_repository(self):
        repository = self.read_required(
            "src/main/java/com/swear/autostorage/CoreStorageRepository.java"
        )
        record = self.read_required(
            "src/main/java/com/swear/autostorage/CoreStorageRecord.java"
        )
        core = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlockEntity.java"
        )
        block = self.read_required(
            "src/main/java/com/swear/autostorage/StorageCoreBlock.java"
        )

        self.assertIn('DATA_NAME = AutoStorage.MODID + "_core_storages"', repository)
        self.assertIn("extends SavedData", repository)
        self.assertRegex(record, r"MAX_SEGMENT_TYPES\s*=\s*63")
        self.assertIn('TAG_INVENTORY_SEGMENTS = "inventorySegments"', record)
        self.assertIn('TAG_STORAGE_ID = "storageId"', core)
        self.assertIn('TAG_STORAGE_SCHEMA = "storageSchema"', core)
        self.assertIn(".tryCreateFresh(", core)
        self.assertNotIn('tag.put("inventory"', core)
        self.assertNotIn('tag.put("energy"', core)
        self.assertNotIn("BlockItem.setBlockEntityData", block)
        self.assertIn("Unsupported Core storage repository root", repository)
        self.assertIn("Orphan Core storage record", repository)
        self.assertIn("Corrupt Core storage record", repository)
        self.assertIn(
            "Duplicate Core storage record at index {} for storageId={}; "
            "preserving all copies as unavailable data",
            repository,
        )
        self.assertIn("Unresolved Core recovery entry", repository)
        self.assertFalse((ROOT / (
            "src/main/java/com/swear/autostorage/"
            "CoreRecoverySavedData.java"
        )).exists())

    def test_terminal_cold_open_does_not_eagerly_build_unrequested_work(self):
        core = self.read_required(
            "src/main/java/com/swear/autostorage/"
            "StorageCoreBlockEntity.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/"
            "CraftingTerminalMenu.java"
        )
        sorted_method = re.search(
            r"private List<IndexedItem> sortedItems\(SortMode mode\) \{(.*?)\n    \}",
            core,
            re.DOTALL,
        )
        self.assertIsNotNone(sorted_method)
        self.assertNotIn("for (SortMode current : SortMode.values())", sorted_method.group(1))
        self.assertNotIn("craftablePrefetchPending", crafting)
        constructor = re.search(
            r"public CraftingTerminalMenu\(int containerId, Inventory playerInv, "
            r"StorageCoreBlockEntity core, BlockPos accessPos, boolean remoteAccess\) "
            r"\{(.*?)\n    \}",
            crafting,
            re.DOTALL,
        )
        self.assertIsNotNone(constructor)
        self.assertEqual(0, constructor.group(1).count("refreshDisplayItems(core)"))

    def test_prepared_craftable_results_are_shared_and_revision_guarded(self):
        core = self.read_required(
            "src/main/java/com/swear/autostorage/"
            "StorageCoreBlockEntity.java"
        )
        crafting = self.read_required(
            "src/main/java/com/swear/autostorage/"
            "CraftingTerminalMenu.java"
        )
        self.assertIn("getCraftableRevision()", core)
        self.assertIn("craftableRevision++", core)
        self.assertIn("new WeakHashMap<>()", crafting)
        self.assertIn("core.getCraftableRevision()", crafting)
        self.assertIn("core.getMachineRevision()", crafting)
        self.assertIn("core.getTopologyRevision()", crafting)
        self.assertIn("generatedWorkCrossedThreshold", crafting)
        self.assertIn("SHARED_CRAFTABLE_CACHE", crafting)

    def test_texture_family_encodes_distinct_roles_direction_and_declared_symmetry(self):
        art = ROOT / "art/texture-generation/20260714-terminal-family"
        manifest = json.loads((art / "selection.json").read_text())
        members = manifest["members"]

        def member_pixels(texture_id: str):
            path = ROOT / members[texture_id]["runtime"]
            width, height, pixels = self.rgba_png_pixels(path)
            self.assertEqual((16, 16), (width, height), texture_id)
            return pixels

        x_symmetric = [
            "auto_storage:block/storage_core",
            "auto_storage:block/storage_terminal",
            "auto_storage:block/crafting_terminal",
            *[f"auto_storage:block/storage_unit_t{tier}" for tier in range(1, 7)],
            "auto_storage:block/creative_storage_unit",
            "auto_storage:block/import_bus_top",
            "auto_storage:block/import_bus_side",
            "auto_storage:block/import_bus_front",
            "auto_storage:block/export_bus_top",
            "auto_storage:block/export_bus_side",
            "auto_storage:block/export_bus_front",
        ]
        for texture_id in x_symmetric:
            self.assertIn("x", members[texture_id].get("symmetry_axes", []), texture_id)
            pixels = member_pixels(texture_id)
            self.assertTrue(all(
                pixels[y * 16 + x] == pixels[y * 16 + 15 - x]
                for y in range(16) for x in range(8)
            ), texture_id)

        terminal_ids = [
            "auto_storage:block/storage_core",
            "auto_storage:block/storage_terminal",
            "auto_storage:block/crafting_terminal",
        ]
        for left_index, left_id in enumerate(terminal_ids):
            for right_id in terminal_ids[left_index + 1:]:
                changed = sum(
                    left != right
                    for left, right in zip(member_pixels(left_id), member_pixels(right_id))
                )
                self.assertGreaterEqual(changed, 24, f"{left_id} and {right_id} are too similar")

        for tier in range(1, 6):
            left = member_pixels(f"auto_storage:block/storage_unit_t{tier}")
            right = member_pixels(f"auto_storage:block/storage_unit_t{tier + 1}")
            self.assertGreaterEqual(
                sum(a != b for a, b in zip(left, right)),
                8,
                f"adjacent storage tiers {tier}/{tier + 1} need a readable main-face change",
            )

        for face in ("top", "side"):
            imported = member_pixels(f"auto_storage:block/import_bus_{face}")
            exported = member_pixels(f"auto_storage:block/export_bus_{face}")
            self.assertGreaterEqual(
                sum(a != b for a, b in zip(imported, exported)),
                8,
                f"Import and Export Bus {face} faces must remain distinguishable",
            )

    def test_xycraft_exhaustive_scan_seeds_distinct_drain_and_buildings_types(self):
        text = self.read_required(
            "src/xycraftMachinesFixture/java/com/swear/autostorage/fixture/"
            "xycraftmachines/XycraftMachinesIntegrationGameTests.java"
        )
        method = "every_recipe_in_each_audited_machine_type_fails_closed"
        start = text.index(method)
        body_start = text.index("{", start)
        body_end = text.index("private static void assertUnsupported", body_start)
        body = text[body_start:body_end]
        self.assertIn('xycraft("fluid_tank_fill/water_bottle")', body)
        self.assertIn('xycraft("fluid_tank_drain/water_bottle")', body)
        self.assertIn('xycraft("buildings/temp")', body)
        self.assertRegex(
            text,
            r"assertUnsupported\([\s\S]*?"
            r'xycraft\("fluid_tank_drain/water_bottle"\)',
        )
        self.assertRegex(
            text,
            r"assertUnsupported\([\s\S]*?"
            r'xycraft\("buildings/temp"\)',
        )

    def test_integrated_fixtures_use_declarative_runtime_transform(self):
        dependency = "maven.modrinth:integrated-dynamics:tG3ZKTep"
        transform = {
            "sha256": (
                "7c508ebd4048a589812562740132d39802ea0034e11a011fbfd53188b39fdba2"
            ),
            "remove_entries": [
                "org/cyclops/integrateddynamicscompat/modcompat/refinedstorage/"
                "gametest/GameTestsAspectsRefinedStorage.class"
            ],
        }
        for module_id in ("integrateddynamics", "integratedcrafting"):
            contract = json.loads(
                (ROOT / f"compat/contracts/{module_id}.json").read_text()
            )
            descriptor = json.loads(
                (ROOT / f"src/compat/{module_id}/compat-module.json").read_text()
            )
            self.assertEqual(
                {dependency: transform},
                contract["target"]["runtime_artifact_transforms"],
            )
            self.assertEqual(
                [{"dependency": dependency, **transform}],
                descriptor["runtimeArtifactTransforms"],
            )

        build = (ROOT / "build.gradle").read_text()
        for obsolete in (
            "integratedDynamicsPristineArtifact",
            "integratedDynamicsRsGameTestClass",
            "strippedIntegratedDynamicsJar",
            "stripIntegratedDynamicsRsGameTest",
        ):
            self.assertNotIn(obsolete, build)

    def test_integrated_dynamics_overflow_fixture_exercises_fluid_destination(self):
        fixture = self.read_required(
            "src/integratedDynamicsFixture/java/com/swear/autostorage/fixture/"
            "integrateddynamics/IntegrateddynamicsIntegrationGameTests.java"
        )
        method = self.java_block(
            fixture,
            r"\bpublic\s+static\s+void\s+checked_fluid_output_overflow_is_atomic\s*\(",
            "Integrated Dynamics fluid-output overflow GameTest",
        )

        self.assertIn("Long.MAX_VALUE", method)
        self.assertIn("StorageResourceKey.fluid(", method)
        self.assertIn("if (craft(context, SQUEEZER_RECIPE)", method)
        self.assertIn(
            'fluidAmount(context, idFluid("menril_resin")) != Long.MAX_VALUE',
            method,
        )
        self.assertIn(
            "getStationWork(MECHANICAL_SQUEEZER) != 15",
            method,
        )
        self.assertRegex(
            method,
            r"getResourceAmount\(\s*StorageResourceKey\.neoforgeEnergy\(\)\s*\)"
            r"\s*!=\s*energy",
        )

    def test_integrated_dynamics_stations_use_localized_logical_family_labels(self):
        compat = self.read_required(
            "src/compat/integrateddynamics/java/com/swear/autostorage/compat/"
            "integrateddynamics/IntegrateddynamicsCompat.java"
        )
        en_us = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/en_us.json")
        )
        zh_tw = json.loads(
            self.read_required("src/main/resources/assets/auto_storage/lang/zh_tw.json")
        )
        labels = {
            "integrateddynamics_drying_basin": ("Drying Basin", "乾燥盆"),
            "integrateddynamics_mechanical_drying_basin": (
                "Mechanical Drying Basin",
                "機械乾燥盆",
            ),
            "integrateddynamics_mechanical_squeezer": (
                "Mechanical Squeezer",
                "機械壓榨機",
            ),
        }

        self.assertNotIn("getHoverName()", compat)
        for path, (english, traditional_chinese) in labels.items():
            key = f"gui.auto_storage.station.{path}"
            self.assertRegex(
                compat,
                rf'Component\.translatable\(\s*"{re.escape(key)}"\s*\)',
            )
            self.assertEqual(english, en_us[key])
            self.assertEqual(traditional_chinese, zh_tw[key])

    def test_integrated_dynamics_contract_encodes_conditional_fluid_primary_roles(self):
        contract = json.loads(
            self.read_required("compat/contracts/integrateddynamics.json")
        )
        families = {family["id"]: family for family in contract["families"]}
        expected = {
            "recipe_drying_basin": "recipe.outputFluid.amount",
            "recipe_mechanical_drying_basin": "recipe.outputFluid.amount",
            "recipe_mechanical_squeezer": "recipe.outputFluid.amount",
        }
        for family_id, amount_fragment in expected.items():
            fluid_outputs = [
                output
                for output in families[family_id]["outputs"]
                if output["resource_kind"] == "fluid"
                and output["selector"] == "recipe.outputFluid"
            ]
            self.assertEqual(
                {"primary", "remainder"},
                {output["role"] for output in fluid_outputs},
                f"{family_id} must bind fluid-only primary and item-present remainder roles",
            )
            primary = next(
                output for output in fluid_outputs if output["role"] == "primary"
            )
            remainder = next(
                output for output in fluid_outputs if output["role"] == "remainder"
            )
            self.assertIn(amount_fragment, str(primary["amount"]))
            self.assertIn("item", str(primary["amount"]).lower())
            self.assertIn("absent", str(primary["amount"]).lower())
            self.assertIn(amount_fragment, str(remainder["amount"]))
            self.assertIn("item", str(remainder["amount"]).lower())
            self.assertIn("present", str(remainder["amount"]).lower())

    def test_integrated_dynamics_remainder_evidence_executes_exact_fluid_output(self):
        contract = json.loads(
            self.read_required("compat/contracts/integrateddynamics.json")
        )
        marker = contract["verification"]["evidence"][
            "catalyst_tool_remainder_exact"
        ][0]["marker"]
        fixture = self.read_required(
            "src/integratedDynamicsFixture/java/com/swear/autostorage/fixture/"
            "integrateddynamics/IntegrateddynamicsIntegrationGameTests.java"
        )
        method = self.java_block(
            fixture,
            r"\bpublic\s+static\s+void\s+mechanical_squeezer_consumes_item_fe_and_duration\s*\(",
            "Integrated Dynamics exact fluid remainder GameTest",
        )
        self.assertEqual(
            "Integrated Dynamics mechanical squeezer transaction was wrong",
            marker,
        )
        self.assertIn(marker, method)
        self.assertIn('fluidAmount(context, idFluid("menril_resin")) != 250', method)
        self.assertIn('itemCount(context.core(), idItem("crystalized_menril_chunk")) != 1', method)

    def test_integrated_dynamics_fixture_executes_mechanical_drying_basin_transaction(self):
        fixture = self.read_required(
            "src/integratedDynamicsFixture/java/com/swear/autostorage/fixture/"
            "integrateddynamics/IntegrateddynamicsIntegrationGameTests.java"
        )
        method = self.java_block(
            fixture,
            r"\bpublic\s+static\s+void\s+mechanical_drying_basin_consumes_fluid_fe_and_duration\s*\(",
            "Integrated Dynamics Mechanical Drying Basin GameTest",
        )
        self.assertIn("MECHANICAL_DRYING_RECIPE", method)
        self.assertIn('seedFluid(context, idFluid("menril_resin"), 1000)', method)
        self.assertIn("StorageResourceKey.neoforgeEnergy()", method)
        self.assertIn('installStation(context, "mechanical_drying_basin")', method)
        self.assertIn("tick(context.core(), 15)", method)
        self.assertIn('itemCount(context.core(), idItem("crystalized_menril_block")) != 1', method)
        self.assertIn("getStationWork(MECHANICAL_DRYING_BASIN) != 0", method)
        descriptor = json.loads(
            self.read_required("src/compat/integrateddynamics/compat-module.json")
        )
        contract = json.loads(
            self.read_required("compat/contracts/integrateddynamics.json")
        )
        self.assertEqual(9, descriptor["expectedTests"])
        self.assertEqual(9, contract["verification"]["expected_game_tests"])


if __name__ == "__main__":
    unittest.main()
