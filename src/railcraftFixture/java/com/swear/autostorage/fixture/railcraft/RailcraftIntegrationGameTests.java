package com.swear.autostorage.fixture.railcraft;

import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingTerminalMenu;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.List;

@GameTestHolder(RailcraftFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class RailcraftIntegrationGameTests {
    private RailcraftIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void unsafe_railcraft_contracts_are_not_registered(
            GameTestHelper helper
    ) {
        if (!ModList.get().isLoaded("railcraft")) {
            helper.fail("Railcraft Reborn mod is not loaded");
            return;
        }
        if (AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("railcraft")
                                || id.getPath().startsWith("railcraft_"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("railcraft")
                                || id.getPath().startsWith("railcraft_"))) {
            helper.fail("Railcraft Reborn unsafe contract was registered");
            return;
        }
        for (ResourceLocation rejected : List.of(
                autoStorage("railcraft_crusher"),
                autoStorage("railcraft_blast_furnace"),
                autoStorage("railcraft_coke_oven"),
                autoStorage("railcraft_rolling"))) {
            if (AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(rejected)
                    || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(rejected)) {
                helper.fail("Railcraft Reborn unsafe contract was registered");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void chance_crusher_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(
                helper,
                "Unsafe Railcraft Reborn recipe was accepted",
                railcraft("crusher/crushing_cobblestone"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void blast_furnace_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(
                helper,
                "Unsafe Railcraft Reborn recipe was accepted",
                railcraft("blast_furnace/blasting_iron_ingot"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void coke_oven_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(
                helper,
                "Unsafe Railcraft Reborn recipe was accepted",
                railcraft("coke_oven/coal_coke"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void rolling_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(
                helper,
                "Unsafe Railcraft Reborn recipe was accepted",
                railcraft("rolling/rebar_iron"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void fluid_tie_and_rotor_repair_fail_closed(GameTestHelper helper) {
        assertUnsupported(
                helper,
                "Unsafe Railcraft Reborn recipe was accepted",
                railcraft("wooden_tie"),
                railcraft("stone_tie"),
                railcraft("rotor_repair"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void special_crafting_helpers_fail_closed(GameTestHelper helper) {
        assertUnsupported(
                helper,
                "Unsafe Railcraft Reborn recipe was accepted",
                railcraft("ticket"),
                railcraft("chest_minecart_disassembly"),
                railcraft("patchouli_book_crafting"));
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_recipe_in_each_audited_machine_type_fails_closed(
            GameTestHelper helper
    ) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                railcraft("crusher/crushing_cobblestone"),
                railcraft("blast_furnace/blasting_iron_ingot"),
                railcraft("coke_oven/coal_coke"),
                railcraft("rolling/rebar_iron"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited Railcraft Reborn recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        if (types.size() != 4) {
            helper.fail("Expected 4 unique audited Railcraft Reborn recipe types, but found "
                    + types.size());
            return;
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited Railcraft Reborn recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe Railcraft Reborn recipe type accepted "
                            + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static void assertUnsupported(
            GameTestHelper helper,
            String acceptedMarker,
            ResourceLocation... recipeIds
    ) {
        for (ResourceLocation recipeId : recipeIds) {
            var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing representative Railcraft Reborn recipe " + recipeId);
                return;
            }
            if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail(acceptedMarker + ": " + recipeId);
                return;
            }
        }
        helper.succeed();
    }

    private static ResourceLocation railcraft(String path) {
        return ResourceLocation.fromNamespaceAndPath("railcraft", path);
    }

    private static ResourceLocation autoStorage(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path);
    }
}
