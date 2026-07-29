from pathlib import Path
import json
import re
import struct
import unittest
import zlib


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources/assets/magic_storage"
FUSION_PACK = ROOT / "src/main/resources/resourcepacks/fusion_connected_casing"
FUSION_RESOURCES = FUSION_PACK / "assets/magic_storage"
NETWORK_BLOCKS = [
    "magic_storage:storage_core",
    *[f"magic_storage:storage_unit_t{tier}" for tier in range(1, 7)],
    "magic_storage:creative_storage_unit",
    "magic_storage:storage_terminal",
    "magic_storage:crafting_terminal",
    "magic_storage:import_bus",
    "magic_storage:export_bus",
]
CUBE_BLOCKS = [
    "storage_core",
    *[f"storage_unit_t{tier}" for tier in range(1, 7)],
    "creative_storage_unit",
    "storage_terminal",
    "crafting_terminal",
]
BUS_BLOCKS = ["import_bus", "export_bus"]
CONNECTED_TEXTURES = [
    *CUBE_BLOCKS,
    *[f"{bus}_{face}" for bus in BUS_BLOCKS for face in ("top", "side")],
]
TIER_ROLES = [
    "copper_bound_cell",
    "iron_braced_double_node",
    "gold_lapis_lattice",
    "diamond_quartz_cross_frame",
    "prismarine_ender_halo",
    "netherite_amethyst_crown_circuit",
]
TIER_ACCENTS = [
    {"#B46A3C"},
    {"#AAB6CC"},
    {"#FFC060", "#2EA8FF"},
    {"#E4E8F2", "#3FDCE5"},
    {"#4BC6A8", "#9A5CE8"},
    {"#5B5264", "#C083FF"},
]


class ConnectedTextureTests(unittest.TestCase):
    def load_json(self, path: Path):
        self.assertTrue(path.is_file(), f"missing {path.relative_to(ROOT)}")
        return json.loads(path.read_text())

    def rgba_png_pixels(self, path: Path):
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

    def test_fusion_is_optional_and_only_used_by_the_client_development_run(self):
        metadata = (ROOT / "src/main/templates/META-INF/neoforge.mods.toml").read_text()
        self.assertNotIn('modId="fusion"', metadata)

        properties = (ROOT / "gradle.properties").read_text()
        self.assertIn("fusion_runtime_version=h2GrA0Ku", properties)
        build = (ROOT / "build.gradle").read_text()
        coordinate = 'maven.modrinth:fusion-connected-textures:${fusion_runtime_version}'
        self.assertIn('url = "https://api.modrinth.com/maven"', build)
        self.assertIn('includeGroup "maven.modrinth"', build)
        self.assertIn('fusionRuntime', build)
        self.assertIn('addModdingDependenciesTo sourceSets.fusionRuntime', build)
        self.assertRegex(
            build,
            re.compile(r'client\s*\{.*?sourceSet\s*=\s*sourceSets\.fusionRuntime', re.DOTALL),
        )
        self.assertRegex(
            build,
            re.compile(r'data\s*\{.*?sourceSet\s*=\s*sourceSets\.fusionRuntime', re.DOTALL),
        )
        self.assertIn(f'fusionRuntimeRuntimeOnly "{coordinate}"', build)
        self.assertNotIn(f'clientAdditionalRuntimeClasspath "{coordinate}"', build)
        self.assertNotIn(f'dataAdditionalRuntimeClasspath "{coordinate}"', build)
        self.assertNotIn(f'serverAdditionalRuntimeClasspath "{coordinate}"', build)
        self.assertNotIn(f'gameTestServerAdditionalRuntimeClasspath "{coordinate}"', build)
        self.assertNotRegex(
            build,
            re.compile(r'^\s*runtimeOnly\s+"maven\.modrinth:fusion-connected-textures:', re.MULTILINE),
        )
        self.assertNotIn("h2GrA0Ku", build)
        self.assertNotIn('implementation "maven.modrinth:fusion-connected-textures:', build)

    def test_vanilla_base_models_do_not_require_fusion(self):
        for block in CUBE_BLOCKS:
            model = self.load_json(RESOURCES / f"models/block/{block}.json")
            self.assertNotIn("loader", model, block)
            self.assertNotIn("type", model, block)
            self.assertEqual("minecraft:block/cube_all", model.get("parent"), block)
            self.assertEqual({"all": f"magic_storage:block/{block}"}, model.get("textures"), block)
        for bus in BUS_BLOCKS:
            model = self.load_json(RESOURCES / f"models/block/{bus}.json")
            self.assertNotIn("loader", model, bus)
            self.assertNotIn("type", model, bus)
            self.assertEqual("minecraft:block/orientable", model.get("parent"), bus)
            self.assertEqual({
                "top": f"magic_storage:block/{bus}_top",
                "front": f"magic_storage:block/{bus}_front",
                "side": f"magic_storage:block/{bus}_side",
            }, model.get("textures"), bus)

    def test_cube_models_connect_across_the_entire_network_family_when_fusion_overlay_is_active(self):
        expected_predicate = [
            {"type": "match_block", "block": block}
            for block in NETWORK_BLOCKS
        ]
        for block in CUBE_BLOCKS:
            model = self.load_json(FUSION_RESOURCES / f"models/block/{block}.json")
            self.assertEqual("fusion:model", model.get("loader"), block)
            self.assertEqual("connecting", model.get("type"), block)
            self.assertEqual("minecraft:block/cube_all", model.get("parent"), block)
            self.assertEqual({"all": f"magic_storage:block/{block}_connected"}, model.get("textures"), block)
            self.assertEqual(expected_predicate, model.get("connections"), block)

    def test_fusion_overlay_bus_models_connect_only_their_casing_and_keep_directional_fronts(self):
        expected_predicate = [
            {"type": "match_block", "block": block}
            for block in NETWORK_BLOCKS
        ]
        for bus in BUS_BLOCKS:
            model = self.load_json(FUSION_RESOURCES / f"models/block/{bus}.json")
            self.assertEqual("fusion:model", model.get("loader"), bus)
            self.assertEqual("connecting", model.get("type"), bus)
            self.assertEqual("minecraft:block/orientable", model.get("parent"), bus)
            self.assertEqual({
                "top": f"magic_storage:block/{bus}_top_connected",
                "front": f"magic_storage:block/{bus}_front",
                "side": f"magic_storage:block/{bus}_side_connected",
            }, model.get("textures"), bus)
            self.assertEqual({
                "top": expected_predicate,
                "side": "#top",
            }, model.get("connections"), bus)
            self.assertFalse((RESOURCES / f"textures/block/{bus}_front.png.mcmeta").exists())

    def test_models_use_only_connection_schema_supported_by_pinned_fusion_1_2_12(self):
        for block in [*CUBE_BLOCKS, *BUS_BLOCKS]:
            model = self.load_json(FUSION_RESOURCES / f"models/block/{block}.json")
            serialized = json.dumps(model)
            self.assertNotIn('"blocks"', serialized, block)
            self.assertNotIn('"type": "false"', serialized, block)
            self.assertNotIn('"type": "true"', serialized, block)

            def inspect(value):
                if isinstance(value, dict):
                    if value.get("type") == "match_block":
                        self.assertEqual({"type", "block"}, set(value), block)
                        self.assertIn(value["block"], NETWORK_BLOCKS, block)
                    for nested in value.values():
                        inspect(nested)
                elif isinstance(value, list):
                    for nested in value:
                        inspect(nested)

            inspect(model.get("connections"))

    def test_connected_sheets_use_five_tile_pieced_layout_and_isolated_tile_matches_item_art(self):
        seam_masks = None
        for texture in CONNECTED_TEXTURES:
            ordinary = RESOURCES / f"textures/block/{texture}.png"
            connected = FUSION_RESOURCES / f"textures/block/{texture}_connected.png"
            self.assertTrue(ordinary.is_file(), f"missing {ordinary.relative_to(ROOT)}")
            self.assertTrue(connected.is_file(), f"missing {connected.relative_to(ROOT)}")
            width, height, ordinary_pixels = self.rgba_png_pixels(ordinary)
            connected_width, connected_height, connected_pixels = self.rgba_png_pixels(connected)
            self.assertEqual((16, 16), (width, height), texture)
            self.assertEqual((80, 16), (connected_width, connected_height), texture)
            first_tile = [
                connected_pixels[y * connected_width + x]
                for y in range(16)
                for x in range(16)
            ]
            self.assertEqual(ordinary_pixels, first_tile, texture)
            tiles = []
            for tile in range(5):
                tile_pixels = [
                    connected_pixels[y * connected_width + tile * 16 + x]
                    for y in range(16)
                    for x in range(16)
                ]
                tiles.append(tile_pixels)
                self.assertGreater(sum(pixel[3] != 0 for pixel in tile_pixels), 192, f"{texture} tile {tile}")

            interior = [y * 16 + x for y in range(2, 14) for x in range(2, 14)]
            for tile in range(1, 5):
                self.assertEqual(
                    [tiles[0][index] for index in interior],
                    [tiles[tile][index] for index in interior],
                    f"{texture} tile {tile} changed the role motif",
                )
            top_bottom = {y * 16 + x for y in (0, 1, 14, 15) for x in range(16)}
            left_right = {y * 16 + x for y in range(16) for x in (0, 1, 14, 15)}
            changed = [
                {index for index, (base, pixel) in enumerate(zip(tiles[0], tile)) if base != pixel}
                for tile in tiles
            ]
            self.assertEqual(set(), changed[0], texture)
            self.assertLessEqual(changed[1], top_bottom | left_right, texture)
            self.assertLessEqual(changed[2], top_bottom, texture)
            self.assertLessEqual(changed[3], left_right, texture)
            for edge in (
                {y * 16 + x for y in (0, 1) for x in range(16)},
                {y * 16 + x for y in (14, 15) for x in range(16)},
                {y * 16 + x for y in range(16) for x in (0, 1)},
                {y * 16 + x for y in range(16) for x in (14, 15)},
            ):
                self.assertTrue(changed[1] & edge, texture)
            self.assertTrue(changed[2], texture)
            self.assertTrue(changed[3], texture)
            self.assertTrue(changed[4], texture)
            corner_indices = {
                y * 16 + x
                for x in (0, 1, 14, 15)
                for y in (0, 1, 14, 15)
                if (x < 2 or x > 13) and (y < 2 or y > 13)
            }
            self.assertFalse(changed[4] & corner_indices, texture)
            current_masks = [frozenset(mask) for mask in changed]
            if seam_masks is None:
                seam_masks = current_masks
            else:
                self.assertEqual(seam_masks, current_masks, f"{texture} has a different casing seam grammar")

            metadata = self.load_json(connected.with_suffix(".png.mcmeta"))
            self.assertEqual({"fusion": {"type": "connecting", "layout": "pieced"}}, metadata, texture)

    def test_fusion_overlay_is_a_required_client_pack_registered_only_when_fusion_is_loaded(self):
        metadata = self.load_json(FUSION_PACK / "pack.mcmeta")
        self.assertIn("pack", metadata)
        client_setup = (ROOT / "src/main/java/com/swearprom/magicstorage/magic_storage/ClientSetup.java").read_text()
        self.assertIn("AddPackFindersEvent", client_setup)
        self.assertIn('ModList.get().isLoaded("fusion")', client_setup)
        self.assertIn('"resourcepacks/fusion_connected_casing"', client_setup)
        self.assertIn("PackType.CLIENT_RESOURCES", client_setup)
        self.assertIn("PackSource.DEFAULT", client_setup)
        self.assertRegex(client_setup, r"true,\s*Pack\.Position\.TOP")

    def test_item_models_are_vanilla_and_never_reference_connected_sheets(self):
        for block in CUBE_BLOCKS:
            model = self.load_json(RESOURCES / f"models/item/{block}.json")
            self.assertNotIn("loader", model, block)
            self.assertNotIn("type", model, block)
            self.assertEqual("minecraft:block/cube_all", model.get("parent"), block)
            self.assertEqual({"all": f"magic_storage:block/{block}"}, model.get("textures"), block)
        for bus in BUS_BLOCKS:
            model = self.load_json(RESOURCES / f"models/item/{bus}.json")
            self.assertNotIn("loader", model, bus)
            self.assertNotIn("type", model, bus)
            self.assertEqual("minecraft:block/orientable", model.get("parent"), bus)
            self.assertEqual({
                "top": f"magic_storage:block/{bus}_top",
                "front": f"magic_storage:block/{bus}_front",
                "side": f"magic_storage:block/{bus}_side",
            }, model.get("textures"), bus)
            self.assertNotIn("_connected", json.dumps(model), bus)

        for path in sorted((RESOURCES / "models/item").glob("*.json")):
            model = self.load_json(path)
            self.assertNotEqual("fusion:model", model.get("loader"), path.name)
            self.assertNotEqual("connecting", model.get("type"), path.name)
            self.assertFalse(str(model.get("parent", "")).startswith("magic_storage:block/"), path.name)
            self.assertNotIn("_connected", json.dumps(model), path.name)

    def test_storage_tiers_declare_increasing_semantic_ornaments_not_capacity_bands(self):
        manifest = self.load_json(ROOT / "art/texture-generation/20260714-terminal-family/selection.json")
        self.assertNotIn("tier_bands", manifest)
        ornaments = manifest.get("tier_ornaments")
        self.assertEqual(6, len(ornaments))
        old_bar_mask = {(x, y) for y in range(5, 11) for x in range(4, 12)}
        detail_counts = []
        for tier, ornament in enumerate(ornaments, 1):
            texture_id = f"magic_storage:block/storage_unit_t{tier}"
            self.assertEqual(tier, ornament.get("tier"))
            self.assertEqual(TIER_ROLES[tier - 1], ornament.get("role"))
            self.assertEqual(TIER_ACCENTS[tier - 1], set(ornament.get("accents", [])))
            points = [tuple(point) for point in ornament.get("detail_points", [])]
            self.assertEqual(len(points), len(set(points)), texture_id)
            self.assertTrue(any(point not in old_bar_mask for point in points), texture_id)
            detail_counts.append(len(points))
            runtime = ROOT / manifest["members"][texture_id]["runtime"]
            width, height, pixels = self.rgba_png_pixels(runtime)
            self.assertEqual((16, 16), (width, height), texture_id)
            self.assertTrue(all(
                pixels[y * width + x] == pixels[y * width + 15 - x]
                for y in range(16)
                for x in range(8)
            ), texture_id)
            used = {"#%02X%02X%02X" % pixel[:3] for pixel in pixels if pixel[3] != 0}
            self.assertLessEqual(TIER_ACCENTS[tier - 1], used, texture_id)
        self.assertEqual(sorted(detail_counts), detail_counts)
        self.assertEqual(len(detail_counts), len(set(detail_counts)))

    def test_adjacent_tiers_change_outside_the_retired_capacity_meter(self):
        retired = {(x, y) for y in range(5, 11) for x in range(4, 12)}
        tiers = []
        for tier in range(1, 7):
            _, _, pixels = self.rgba_png_pixels(RESOURCES / f"textures/block/storage_unit_t{tier}.png")
            tiers.append(pixels)
        for tier in range(5):
            changed = sum(
                tiers[tier][y * 16 + x] != tiers[tier + 1][y * 16 + x]
                for y in range(16)
                for x in range(16)
                if (x, y) not in retired
            )
            self.assertGreaterEqual(changed, 8, f"tiers {tier + 1}/{tier + 2} still differ only as a fill meter")


if __name__ == "__main__":
    unittest.main()
