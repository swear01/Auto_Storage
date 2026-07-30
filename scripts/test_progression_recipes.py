from collections import Counter
import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
RECIPE_DIR = ROOT / "src/main/resources/data/auto_storage/recipe"

EXPECTED_RECIPES = {
    "storage_core": {
        "type": "minecraft:crafting_shaped",
        "pattern": ["OCO", "RDR", "OCO"],
        "key": {
            "C": {"item": "minecraft:copper_ingot"},
            "O": {"item": "minecraft:obsidian"},
            "R": {"item": "minecraft:redstone"},
            "D": {"item": "minecraft:diamond"},
        },
        "result": {"id": "auto_storage:storage_core"},
    },
    "storage_terminal": {
        "type": "minecraft:crafting_shaped",
        "pattern": ["GPG", "RTR", "CHC"],
        "key": {
            "G": {"item": "minecraft:glass_pane"},
            "P": {"item": "minecraft:black_concrete_powder"},
            "R": {"item": "minecraft:redstone"},
            "T": {"item": "minecraft:redstone_torch"},
            "H": {"item": "minecraft:chest"},
            "C": {"item": "minecraft:copper_ingot"},
        },
        "result": {"id": "auto_storage:storage_terminal"},
    },
    "crafting_terminal": {
        "type": "minecraft:crafting_shaped",
        "pattern": ["RGR", "CSC", "RTR"],
        "key": {
            "R": {"item": "minecraft:redstone"},
            "G": {"item": "minecraft:gold_ingot"},
            "C": {"item": "minecraft:copper_ingot"},
            "S": {"item": "auto_storage:storage_terminal"},
            "T": {"item": "minecraft:crafting_table"},
        },
        "result": {"id": "auto_storage:crafting_terminal"},
    },
    "remote_terminal": {
        "type": "minecraft:crafting_shaped",
        "pattern": ["OEO", "RTR", " C "],
        "key": {
            "O": {"item": "minecraft:obsidian"},
            "E": {"item": "minecraft:ender_pearl"},
            "R": {"item": "minecraft:redstone"},
            "T": {"item": "auto_storage:crafting_terminal"},
            "C": {"item": "minecraft:compass"},
        },
        "result": {"id": "auto_storage:remote_terminal"},
    },
    "storage_unit_t1": {
        "type": "minecraft:crafting_shaped",
        "pattern": ["PCP", "RBR", "PCP"],
        "key": {
            "C": {"item": "minecraft:copper_ingot"},
            "P": {"item": "minecraft:black_concrete_powder"},
            "R": {"item": "minecraft:redstone"},
            "B": {"item": "minecraft:barrel"},
        },
        "result": {"id": "auto_storage:storage_unit_t1", "count": 2},
    },
    "storage_unit_t2": {
        "type": "minecraft:crafting_shaped",
        "pattern": ["BIB", "RUR", "BIB"],
        "key": {
            "I": {"item": "minecraft:iron_ingot"},
            "B": {"item": "minecraft:basalt"},
            "R": {"item": "minecraft:redstone"},
            "U": {"item": "auto_storage:storage_unit_t1"},
        },
        "result": {"id": "auto_storage:storage_unit_t2"},
    },
    "storage_unit_t3": {
        "type": "minecraft:crafting_shaped",
        "pattern": ["OGO", "LUL", "OGO"],
        "key": {
            "G": {"item": "minecraft:gold_ingot"},
            "L": {"item": "minecraft:lapis_lazuli"},
            "O": {"item": "minecraft:obsidian"},
            "U": {"item": "auto_storage:storage_unit_t2"},
        },
        "result": {"id": "auto_storage:storage_unit_t3"},
    },
    "storage_unit_t4": {
        "type": "minecraft:crafting_shaped",
        "pattern": ["QDQ", "SUS", "QDQ"],
        "key": {
            "Q": {"item": "minecraft:quartz"},
            "D": {"item": "minecraft:diamond"},
            "S": {"item": "minecraft:slime_ball"},
            "U": {"item": "auto_storage:storage_unit_t3"},
        },
        "result": {"id": "auto_storage:storage_unit_t4"},
    },
    "storage_unit_t5": {
        "type": "minecraft:crafting_shaped",
        "pattern": ["PHP", "EUE", "PHP"],
        "key": {
            "P": {"item": "minecraft:prismarine_crystals"},
            "H": {"item": "minecraft:honeycomb"},
            "E": {"item": "minecraft:ender_pearl"},
            "U": {"item": "auto_storage:storage_unit_t4"},
        },
        "result": {"id": "auto_storage:storage_unit_t5"},
    },
    "storage_unit_t6": {
        "type": "minecraft:crafting_shaped",
        "pattern": ["ANA", "EUE", "ANA"],
        "key": {
            "A": {"item": "minecraft:amethyst_block"},
            "N": {"item": "minecraft:netherite_scrap"},
            "E": {"item": "minecraft:ender_eye"},
            "U": {"item": "auto_storage:storage_unit_t5"},
        },
        "result": {"id": "auto_storage:storage_unit_t6"},
    },
}


class ProgressionRecipeTests(unittest.TestCase):
    def read_recipe(self, recipe_id: str) -> dict:
        path = RECIPE_DIR / f"{recipe_id}.json"
        self.assertTrue(path.is_file(), f"missing {path.relative_to(ROOT)}")
        return json.loads(path.read_text())

    def ingredient_counts(self, recipe: dict) -> Counter:
        return Counter(
            recipe["key"][symbol]["item"]
            for row in recipe["pattern"]
            for symbol in row
            if symbol != " "
        )

    def test_recipes_match_exact_shaped_matrices_and_output_counts(self):
        for recipe_id, expected in EXPECTED_RECIPES.items():
            with self.subTest(recipe_id=recipe_id):
                self.assertEqual(expected, self.read_recipe(recipe_id))

        output_counts = {
            recipe_id: self.read_recipe(recipe_id)["result"].get("count", 1)
            for recipe_id in EXPECTED_RECIPES
        }
        self.assertEqual(
            {
                "storage_core": 1,
                "storage_terminal": 1,
                "crafting_terminal": 1,
                "remote_terminal": 1,
                "storage_unit_t1": 2,
                "storage_unit_t2": 1,
                "storage_unit_t3": 1,
                "storage_unit_t4": 1,
                "storage_unit_t5": 1,
                "storage_unit_t6": 1,
            },
            output_counts,
        )

    def test_starter_bundle_uses_dark_overworld_materials_without_amethyst(self):
        starter = Counter()
        for recipe_id in ["storage_core", "storage_terminal", "storage_unit_t1"]:
            starter.update(self.ingredient_counts(self.read_recipe(recipe_id)))

        self.assertEqual(
            Counter(
                {
                    "minecraft:copper_ingot": 6,
                    "minecraft:obsidian": 4,
                    "minecraft:black_concrete_powder": 5,
                    "minecraft:redstone": 6,
                    "minecraft:redstone_torch": 1,
                    "minecraft:diamond": 1,
                    "minecraft:glass_pane": 2,
                    "minecraft:chest": 1,
                    "minecraft:barrel": 1,
                }
            ),
            starter,
        )
        self.assertNotIn("minecraft:amethyst_shard", starter)

    def test_storage_unit_t2_through_t6_form_continuous_upgrade_chain(self):
        for tier in range(2, 7):
            recipe_id = f"storage_unit_t{tier}"
            recipe = self.read_recipe(recipe_id)
            with self.subTest(recipe_id=recipe_id):
                self.assertEqual(
                    {"item": f"auto_storage:storage_unit_t{tier - 1}"},
                    recipe["key"]["U"],
                )
                self.assertEqual(1, "".join(recipe["pattern"]).count("U"))
                self.assertEqual(
                    f"auto_storage:storage_unit_t{tier}", recipe["result"]["id"]
                )

    def test_functional_midgame_recipes_have_no_expensive_or_completion_gates(self):
        forbidden = {
            "minecraft:ancient_debris",
            "minecraft:breeze_rod",
            "minecraft:diamond_block",
            "minecraft:echo_shard",
            "minecraft:ender_eye",
            "minecraft:heavy_core",
            "minecraft:netherite_ingot",
            "minecraft:netherite_scrap",
            "minecraft:netherite_upgrade_smithing_template",
            "minecraft:ominous_trial_key",
            "minecraft:trial_key",
        }
        for recipe_id in [
            "storage_core",
            "storage_terminal",
            "storage_unit_t1",
            "storage_unit_t2",
            "storage_unit_t3",
        ]:
            with self.subTest(recipe_id=recipe_id):
                ingredients = set(self.ingredient_counts(self.read_recipe(recipe_id)))
                self.assertTrue(forbidden.isdisjoint(ingredients))

    def test_storage_units_never_use_diamond_blocks_or_netherite_ingots(self):
        forbidden = {"minecraft:diamond_block", "minecraft:netherite_ingot"}
        for tier in range(1, 7):
            recipe_id = f"storage_unit_t{tier}"
            with self.subTest(recipe_id=recipe_id):
                ingredients = set(self.ingredient_counts(self.read_recipe(recipe_id)))
                self.assertTrue(forbidden.isdisjoint(ingredients))

    def test_all_referenced_item_ids_are_exact_valid_resource_locations(self):
        ingredient_ids = {
            ingredient["item"]
            for recipe_id in EXPECTED_RECIPES
            for recipe in [self.read_recipe(recipe_id)]
            for ingredient in (
                recipe["key"].values()
                if "key" in recipe
                else recipe["ingredients"]
            )
        }
        self.assertEqual(
            {
                "auto_storage:storage_unit_t1",
                "auto_storage:storage_unit_t2",
                "auto_storage:storage_unit_t3",
                "auto_storage:storage_unit_t4",
                "auto_storage:storage_unit_t5",
                "minecraft:amethyst_block",
                "minecraft:barrel",
                "minecraft:basalt",
                "minecraft:black_concrete_powder",
                "minecraft:chest",
                "minecraft:compass",
                "minecraft:copper_ingot",
                "minecraft:diamond",
                "minecraft:ender_eye",
                "minecraft:ender_pearl",
                "minecraft:glass_pane",
                "minecraft:gold_ingot",
                "minecraft:honeycomb",
                "minecraft:iron_ingot",
                "minecraft:lapis_lazuli",
                "minecraft:netherite_scrap",
                "minecraft:obsidian",
                "minecraft:prismarine_crystals",
                "minecraft:quartz",
                "minecraft:redstone",
                "minecraft:redstone_torch",
                "minecraft:slime_ball",
                "minecraft:crafting_table",
                "auto_storage:storage_terminal",
                "auto_storage:crafting_terminal",
            },
            ingredient_ids,
        )
        resource_location = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
        referenced_ids = ingredient_ids | {
            self.read_recipe(recipe_id)["result"]["id"]
            for recipe_id in EXPECTED_RECIPES
        }
        for item_id in referenced_ids:
            with self.subTest(item_id=item_id):
                self.assertRegex(item_id, resource_location)


if __name__ == "__main__":
    unittest.main()
