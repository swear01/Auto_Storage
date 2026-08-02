package com.swear.autostorage.fixture.productivebees;

import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.AutoStorage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.List;

@GameTestHolder(ProductivebeesFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ProductivebeesIntegrationGameTests {
    private ProductivebeesIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void unsafe_machine_contracts_are_not_registered(GameTestHelper helper) {
        if (!ModList.get().isLoaded("productivebees")) {
            helper.fail("Productive Bees mod is not loaded");
            return;
        }
        if (AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("productivebees")
                                || id.getPath().startsWith("productivebees_"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("productivebees")
                                || id.getPath().startsWith("productivebees_"))) {
            helper.fail("Productive Bees unsafe machine contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void centrifuge_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("centrifuge/honeycomb_breeze")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe centrifuge/honeycomb_breeze");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: centrifuge/honeycomb_breeze");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void bottler_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("bottler/honey_bottle")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe bottler/honey_bottle");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: bottler/honey_bottle");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void advanced_beehive_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("bee_produce/coal_bee")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe bee_produce/coal_bee");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: bee_produce/coal_bee");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void bee_breeding_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("bee_breeding/quarry_bee")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe bee_breeding/quarry_bee");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: bee_breeding/quarry_bee");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void bee_conversion_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("bee_conversion/hoarder_bee")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe bee_conversion/hoarder_bee");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: bee_conversion/hoarder_bee");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void block_conversion_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pb("block_conversion/anvil_repair")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Bees recipe block_conversion/anvil_repair");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Bees recipe was accepted: block_conversion/anvil_repair");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_recipe_in_each_audited_machine_type_fails_closed(
            GameTestHelper helper
    ) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                pb("centrifuge/honeycomb_breeze"),
                pb("bottler/honey_bottle"),
                pb("bee_produce/coal_bee"),
                pb("bee_breeding/quarry_bee"),
                pb("bee_conversion/hoarder_bee"),
                pb("block_conversion/anvil_repair"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited Productive Bees recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        if (types.size() != 6) {
            helper.fail("Expected 6 unique audited Productive Bees recipe types, but found " + types.size());
            return;
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited Productive Bees recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe Productive Bees recipe type accepted " + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static ResourceLocation pb(String path) {
        return ResourceLocation.fromNamespaceAndPath("productivebees", path);
    }
}
