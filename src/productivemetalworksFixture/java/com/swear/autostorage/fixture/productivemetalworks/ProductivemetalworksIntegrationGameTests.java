package com.swear.autostorage.fixture.productivemetalworks;

import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.List;

@GameTestHolder(ProductivemetalworksFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ProductivemetalworksIntegrationGameTests {
    private ProductivemetalworksIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void unsafe_foundry_contracts_are_not_registered(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), ProductivemetalworksIntegrationGameTests.class);
        if (!ModList.get().isLoaded("productivemetalworks")) {
            helper.fail("Productive Metalworks mod is not loaded");
            return;
        }
        if (AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("productivemetalworks")
                                || id.getPath().startsWith("productivemetalworks_"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("productivemetalworks")
                                || id.getPath().startsWith("productivemetalworks_"))) {
            helper.fail("Productive Metalworks unsafe foundry contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void item_melting_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(pmw("melting/ancient_debris")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Metalworks recipe melting/ancient_debris");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Metalworks recipe was accepted: melting/ancient_debris");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void fluid_alloying_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(pmw("alloying/molten_obsidian")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Metalworks recipe alloying/molten_obsidian");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Metalworks recipe was accepted: alloying/molten_obsidian");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void item_casting_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(pmw("casting/blaze_rod")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Metalworks recipe casting/blaze_rod");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Metalworks recipe was accepted: casting/blaze_rod");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void block_casting_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(pmw("casting/black_foundry_capacitor")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Metalworks recipe casting/black_foundry_capacitor");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Metalworks recipe was accepted: casting/black_foundry_capacitor");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void casting_mold_recipe_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(pmw("casting/cast/ingot")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Productive Metalworks recipe casting/cast/ingot");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Productive Metalworks recipe was accepted: casting/cast/ingot");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_entity_melting_recipe_stays_absent(GameTestHelper helper) {
        if (helper.getLevel().getRecipeManager().byKey(pmw("entity_melting/cow")).isPresent()
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getPath().contains("entity_melting"))) {
            helper.fail("Productive Metalworks entity melting boundary changed");
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
                pmw("melting/ancient_debris"),
                pmw("alloying/molten_obsidian"),
                pmw("casting/blaze_rod"),
                pmw("casting/black_foundry_capacitor"),
                pmw("casting/cast/ingot"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited Productive Metalworks recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        if (types.size() != 4) {
            helper.fail("Expected 4 unique audited Productive Metalworks recipe types, but found " + types.size());
            return;
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited Productive Metalworks recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe Productive Metalworks recipe type accepted " + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static ResourceLocation pmw(String path) {
        return ResourceLocation.fromNamespaceAndPath("productivemetalworks", path);
    }
}
