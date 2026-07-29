from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUIDE_ROOT = ROOT / "src/main/resources/assets/magic_storage/patchouli_books/guide"
RECIPE_ROOT = ROOT / "src/main/resources/data/magic_storage/recipe"
ITEM_MODEL_ROOT = ROOT / "src/main/resources/assets/magic_storage/models/item"

CATEGORIES = {
    "getting_started": (0, "magic_storage:storage_core"),
    "storage_system": (1, "magic_storage:storage_unit_t3"),
    "terminals": (2, "magic_storage:crafting_terminal"),
    "energy": (3, "minecraft:furnace"),
    "automation": (4, "magic_storage:import_bus"),
    "troubleshooting": (5, "minecraft:redstone"),
}

ENTRIES = {
    "first_network": (
        "getting_started",
        0,
        "magic_storage:storage_core",
        ("patchouli:text",) * 4,
    ),
    "recipes": (
        "getting_started",
        1,
        "minecraft:crafting_table",
        ("patchouli:crafting",) * 7,
    ),
    "connecting_blocks": (
        "getting_started",
        2,
        "minecraft:chain",
        (
            "patchouli:text",
            "patchouli:text",
            "patchouli:text",
            "patchouli:text",
            "patchouli:text",
        ),
    ),
    "storage_core": (
        "storage_system",
        0,
        "magic_storage:storage_core",
        ("patchouli:spotlight", "patchouli:text", "patchouli:text", "patchouli:text", "patchouli:text"),
    ),
    "unit_tiers": (
        "storage_system",
        1,
        "magic_storage:storage_unit_t1",
        ("patchouli:spotlight",) + ("patchouli:text",) * 6,
    ),
    "connected_casing": (
        "storage_system",
        2,
        "magic_storage:storage_unit_t3",
        ("patchouli:text",) * 3,
    ),
    "storage_terminal": (
        "terminals",
        0,
        "magic_storage:storage_terminal",
        (
            "patchouli:spotlight",
            "patchouli:text",
            "patchouli:text",
            "patchouli:text",
            "patchouli:text",
        ),
    ),
    "crafting_terminal": (
        "terminals",
        1,
        "magic_storage:crafting_terminal",
        ("patchouli:spotlight",) + ("patchouli:text",) * 7,
    ),
    "terminal_controls": (
        "terminals",
        2,
        "minecraft:redstone",
        ("patchouli:text",) * 5,
    ),
    "energy_overview": (
        "energy",
        0,
        "minecraft:furnace",
        ("patchouli:text",) * 4,
    ),
    "fuel_conversion": (
        "energy",
        1,
        "minecraft:coal",
        ("patchouli:text",) * 6,
    ),
    "recipe_costs": (
        "energy",
        2,
        "minecraft:blast_furnace",
        ("patchouli:text",) * 4,
    ),
    "import_bus": (
        "automation",
        0,
        "magic_storage:import_bus",
        ("patchouli:spotlight", "patchouli:text", "patchouli:text", "patchouli:text"),
    ),
    "export_bus": (
        "automation",
        1,
        "magic_storage:export_bus",
        (
            "patchouli:spotlight",
            "patchouli:text",
            "patchouli:text",
            "patchouli:text",
            "patchouli:text",
        ),
    ),
    "remote_terminal": (
        "automation",
        2,
        "magic_storage:remote_terminal",
        (
            "patchouli:spotlight",
            "patchouli:text",
            "patchouli:text",
            "patchouli:text",
            "patchouli:text",
        ),
    ),
    "troubleshooting": (
        "troubleshooting",
        0,
        "minecraft:redstone",
        ("patchouli:text",) * 6,
    ),
    "progression_reference": (
        "troubleshooting",
        1,
        "minecraft:diamond",
        ("patchouli:text",) * 5,
    ),
}

VANILLA_ICONS = {
    "minecraft:blast_furnace",
    "minecraft:chain",
    "minecraft:coal",
    "minecraft:diamond",
    "minecraft:furnace",
    "minecraft:crafting_table",
    "minecraft:redstone",
}

REQUIRED_TOPICS = {
    "en_us": (
        "starter shopping list",
        "one Core",
        "right-click",
        "sneak-right-click",
        "type capacity",
        "Fusion",
        "Storage tab",
        "Craftable tab",
        "All",
        "bare @",
        "click outside",
        "initial focus",
        "Transform",
        "Stations tab",
        "Name, Quantity, Mod, and ID",
        "middle-click",
        "Processing Stations",
        "Instant Stations",
        "runtime burn time",
        "Auto",
        "Unbreaking",
        "Unbreakable",
        "passive input",
        "filter",
        "same dimension",
        "conflicted",
        "full type capacity",
        "unsupported recipe",
        "whole craft",
        "glossary",
        "T1",
        "T6",
    ),
    "zh_tw": (
        "起步採買清單",
        "一個核心",
        "右鍵",
        "潛行右鍵",
        "種類容量",
        "Fusion",
        "儲存分頁",
        "可合成分頁",
        "全部",
        "只有 @",
        "點擊欄外",
        "初始焦點",
        "轉換",
        "工作站分頁",
        "名稱、數量、模組與 ID",
        "中鍵",
        "處理工作站",
        "即時工作站",
        "燃燒時間",
        "自動",
        "耐久",
        "無法破壞",
        "被動輸入",
        "篩選",
        "相同維度",
        "衝突",
        "種類容量已滿",
        "不支援的配方",
        "整批合成",
        "詞彙",
        "T1",
        "T6",
    ),
}

BANNED_PLAYER_TEXT = (
    "Stations & Axe Energy",
    "Energy Reserves",
    "Bottle Energy",
    "Fuel Workspace",
    "recipes and Fuel",
    "燃料工作區",
    "配方與燃料分頁",
    "two adaptive flow rows",
    "two flow rows",
    "two-row",
    "2-row",
    "BFS",
    "Breadth-First Search",
    "packet",
    "hidden slot",
    "wire parity",
    "test totals",
    "SelfTest",
    "GameTest",
    "ItemKey",
    "NBT",
    "BlockEntity",
    "CraftingTerminalMenu",
    "StorageCoreBlockEntity",
    "工作站與斧頭能量",
    "能量儲備",
    "瓶子能量",
    "兩列",
    "廣度優先",
    "封包",
    "隱藏欄位",
    "線路一致",
    "測試總數",
)


class PatchouliGuideTests(unittest.TestCase):
    def load_tree(self, locale: str, kind: str) -> dict[str, dict]:
        directory = GUIDE_ROOT / locale / kind
        self.assertTrue(directory.is_dir(), f"missing guide directory {directory.relative_to(ROOT)}")
        files = sorted(directory.glob("*.json"))
        self.assertTrue(files, f"no guide JSON files in {directory.relative_to(ROOT)}")
        return {path.stem: json.loads(path.read_text()) for path in files}

    def load_locale(self, locale: str) -> tuple[dict[str, dict], dict[str, dict]]:
        return self.load_tree(locale, "categories"), self.load_tree(locale, "entries")

    def page_structure(self, page: dict) -> dict:
        return {key: value for key, value in page.items() if key not in {"text", "title"}}

    def localized_text(self, categories: dict[str, dict], entries: dict[str, dict]) -> str:
        values = []
        for category in categories.values():
            values.extend((category["name"], category["description"]))
        for entry in entries.values():
            values.append(entry["name"])
            values.extend(page.get("text", "") for page in entry["pages"])
        return "\n".join(values)

    def test_bilingual_structure_icons_and_sort_numbers_match(self):
        en_categories, en_entries = self.load_locale("en_us")
        zh_categories, zh_entries = self.load_locale("zh_tw")

        self.assertEqual(set(CATEGORIES), set(en_categories))
        self.assertEqual(set(CATEGORIES), set(zh_categories))
        self.assertEqual(set(ENTRIES), set(en_entries))
        self.assertEqual(set(ENTRIES), set(zh_entries))

        for category_id, (sortnum, icon) in CATEGORIES.items():
            en_category = en_categories[category_id]
            zh_category = zh_categories[category_id]
            self.assertEqual({"name", "description", "icon", "sortnum"}, set(en_category))
            self.assertEqual({"name", "description", "icon", "sortnum"}, set(zh_category))
            self.assertEqual(icon, en_category["icon"])
            self.assertEqual(sortnum, en_category["sortnum"])
            self.assertEqual(
                {"icon": en_category["icon"], "sortnum": en_category["sortnum"]},
                {"icon": zh_category["icon"], "sortnum": zh_category["sortnum"]},
            )

        for entry_id, (category_id, sortnum, icon, page_types) in ENTRIES.items():
            en_entry = en_entries[entry_id]
            zh_entry = zh_entries[entry_id]
            self.assertEqual({"name", "category", "icon", "sortnum", "pages"}, set(en_entry))
            self.assertEqual({"name", "category", "icon", "sortnum", "pages"}, set(zh_entry))
            expected_header = {
                "category": f"magic_storage:{category_id}",
                "icon": icon,
                "sortnum": sortnum,
            }
            self.assertEqual(expected_header, {key: en_entry[key] for key in expected_header})
            self.assertEqual(expected_header, {key: zh_entry[key] for key in expected_header})
            self.assertEqual(page_types, tuple(page["type"] for page in en_entry["pages"]))
            self.assertEqual(page_types, tuple(page["type"] for page in zh_entry["pages"]))
            self.assertEqual(
                [self.page_structure(page) for page in en_entry["pages"]],
                [self.page_structure(page) for page in zh_entry["pages"]],
                entry_id,
            )

        for category_id in CATEGORIES:
            expected_sortnums = list(range(sum(1 for entry in ENTRIES.values() if entry[0] == category_id)))
            actual_sortnums = sorted(
                entry["sortnum"]
                for entry in en_entries.values()
                if entry["category"] == f"magic_storage:{category_id}"
            )
            self.assertEqual(expected_sortnums, actual_sortnums, category_id)

    def test_internal_links_icons_and_recipe_pages_resolve(self):
        categories, entries = self.load_locale("en_us")
        entry_ids = {f"magic_storage:{entry_id}" for entry_id in entries}
        links = set()
        icons = {category["icon"] for category in categories.values()}
        icons.update(entry["icon"] for entry in entries.values())
        recipe_references = []

        for entry in entries.values():
            for page in entry["pages"]:
                links.update(re.findall(r"\$\(l:([^)#]+)(?:#[^)]+)?\)", page.get("text", "")))
                if page["type"] == "patchouli:spotlight":
                    icons.add(page["item"])
                if page["type"] == "patchouli:crafting":
                    self.assertIn("recipe", page)
                    recipe_references.append(page["recipe"])
                    if "recipe2" in page:
                        recipe_references.append(page["recipe2"])

        self.assertTrue(links, "guide must contain internal entry links")
        self.assertEqual(set(), links - entry_ids, f"broken internal links: {sorted(links - entry_ids)}")

        resource_location = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
        for icon in icons:
            self.assertRegex(icon, resource_location)
            namespace, path = icon.split(":", 1)
            if namespace == "magic_storage":
                self.assertTrue((ITEM_MODEL_ROOT / f"{path}.json").is_file(), f"missing item model for {icon}")
            else:
                self.assertIn(icon, VANILLA_ICONS, f"unverified vanilla guide icon {icon}")

        recipe_ids = {
            f"magic_storage:{path.relative_to(RECIPE_ROOT).with_suffix('').as_posix()}"
            for path in RECIPE_ROOT.rglob("*.json")
        }
        for path in RECIPE_ROOT.rglob("*.json"):
            json.loads(path.read_text())
        self.assertEqual(recipe_ids, set(recipe_references))
        self.assertEqual(len(recipe_ids), len(recipe_references), "each recipe should appear on one guide page")

    def test_recipe_overview_contains_every_mod_recipe_once(self):
        for locale in ("en_us", "zh_tw"):
            _, entries = self.load_locale(locale)
            pages = entries["recipes"]["pages"]
            references = [
                page[key]
                for page in pages
                for key in ("recipe", "recipe2")
                if key in page
            ]
            expected = {
                f"magic_storage:{path.stem}"
                for path in RECIPE_ROOT.glob("*.json")
            }
            self.assertEqual(expected, set(references))
            self.assertEqual(len(expected), len(references))

    def test_guide_book_is_a_craftable_magic_storage_item(self):
        book = json.loads((ROOT / "src/main/resources/data/magic_storage/patchouli_books/guide/book.json").read_text())
        self.assertEqual("magic_storage:guide_book", book.get("custom_book_item"))
        recipe = json.loads((RECIPE_ROOT / "guide_book.json").read_text())
        self.assertEqual(
            {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [
                    {"item": "minecraft:book"},
                    {"item": "minecraft:black_dye"},
                    {"item": "minecraft:copper_ingot"},
                ],
                "result": {"id": "magic_storage:guide_book"},
            },
            recipe,
        )
        magic_storage = (ROOT / "src/main/java/com/swearprom/magicstorage/magic_storage/MagicStorage.java").read_text()
        self.assertIn('ITEMS.register("guide_book"', magic_storage)
        self.assertIn("output.accept(GUIDE_BOOK.get())", magic_storage)
        self.assertTrue((ITEM_MODEL_ROOT / "guide_book.json").is_file())

    def test_required_patchouli_dependency_is_present_in_development_runs(self):
        build = (ROOT / "build.gradle").read_text()
        self.assertIn(
            'runtimeOnly "vazkii.patchouli:Patchouli:${patchouli_version}"',
            build,
        )

    def test_required_player_topics_are_covered_in_both_locales(self):
        for locale, topics in REQUIRED_TOPICS.items():
            categories, entries = self.load_locale(locale)
            text = self.localized_text(categories, entries)
            for topic in topics:
                self.assertIn(topic.casefold(), text.casefold(), f"{locale} missing topic {topic!r}")

    def test_processing_resource_group_uses_player_facing_name_in_both_locales(self):
        expected = {"en_us": "Processing", "zh_tw": "加工"}
        retired = {"en_us": "Station Work", "zh_tw": "工作站作業"}
        for locale in expected:
            _, entries = self.load_locale(locale)
            text = " ".join(
                page.get("text", "")
                for entry_id in ("storage_terminal", "energy_overview")
                for page in entries[entry_id]["pages"]
            )
            self.assertIn(expected[locale], text)
            self.assertNotIn(retired[locale], text)

    def test_traditional_chinese_localizes_every_player_text_field(self):
        en_categories, en_entries = self.load_locale("en_us")
        zh_categories, zh_entries = self.load_locale("zh_tw")
        cjk = re.compile(r"[\u3400-\u9fff]")

        for category_id in CATEGORIES:
            for field in ("name", "description"):
                self.assertNotEqual(en_categories[category_id][field], zh_categories[category_id][field])
                self.assertRegex(zh_categories[category_id][field], cjk)

        for entry_id in ENTRIES:
            self.assertNotEqual(en_entries[entry_id]["name"], zh_entries[entry_id]["name"])
            self.assertRegex(zh_entries[entry_id]["name"], cjk)
            for page_index, (en_page, zh_page) in enumerate(
                zip(en_entries[entry_id]["pages"], zh_entries[entry_id]["pages"])
            ):
                self.assertIn("text", en_page, f"{entry_id} page {page_index} missing English text")
                self.assertIn("text", zh_page, f"{entry_id} page {page_index} missing Traditional Chinese text")
                self.assertNotEqual(en_page["text"], zh_page["text"])
                self.assertRegex(zh_page["text"], cjk)

    def test_stale_and_implementation_language_is_absent(self):
        for locale in ("en_us", "zh_tw"):
            categories, entries = self.load_locale(locale)
            text = self.localized_text(categories, entries)
            for banned in BANNED_PLAYER_TEXT:
                self.assertNotIn(banned.casefold(), text.casefold(), f"{locale} contains banned wording {banned!r}")


if __name__ == "__main__":
    unittest.main()
