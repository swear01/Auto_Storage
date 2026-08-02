package com.swear.autostorage.fixture.xycraftmachines;

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

@GameTestHolder(XycraftMachinesFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class XycraftMachinesIntegrationGameTests {
    private XycraftMachinesIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void unsafe_xycraft_contracts_are_not_registered(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                XycraftMachinesIntegrationGameTests.class);
        if (!ModList.get().isLoaded("xycraft_machines")
                || !ModList.get().isLoaded("xycraft_core")
                || !ModList.get().isLoaded("xycraft_world")
                || AutoStorage.RESOURCE_KIND_REGISTRY.containsKey(xycraft("xynergy"))
                || AutoStorage.RESOURCE_KIND_REGISTRY.containsKey(
                        machines("xynergy"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream().anyMatch(
                        id -> id.getPath().startsWith("xycraft"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream().anyMatch(
                        id -> id.getPath().startsWith("xycraft"))) {
            helper.fail("XyCraft Machines unsafe contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void squasher_and_blender_recipes_fail_closed(
            GameTestHelper helper
    ) {
        assertUnsupported(
                helper,
                "Unsafe XyCraft Machines recipe was accepted",
                xycraft("squasher/aluminum_sheet"),
                xycraft("blender/mud"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void chance_crusher_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(
                helper,
                "Unsafe XyCraft Machines recipe was accepted",
                xycraft("crusher/cobbles"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void world_extractor_and_ore_tap_fail_closed(
            GameTestHelper helper
    ) {
        assertUnsupported(
                helper,
                "Unsafe XyCraft Machines recipe was accepted",
                xycraft("extractor/cobblestone"),
                xycraft("ore_tap/steam"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void multiblock_fluid_tank_recipes_fail_closed(
            GameTestHelper helper
    ) {
        assertUnsupported(
                helper,
                "Unsafe XyCraft Machines recipe was accepted",
                xycraft("fluid_tank_fill/water_bottle"),
                xycraft("fluid_tank_drain/water_bottle"),
                xycraft("buildings/temp"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void entropy_and_biome_producers_fail_closed(
            GameTestHelper helper
    ) {
        assertUnsupported(
                helper,
                "Unsafe XyCraft Machines recipe was accepted",
                xycraft("ark_melter/cobble"),
                xycraft("cry_chamber/cobble_lava"),
                xycraft("atmospheric_vacuum/nitrogen"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void centrifuge_refinery_and_solidifier_fail_closed(
            GameTestHelper helper
    ) {
        assertUnsupported(
                helper,
                "Unsafe XyCraft Machines recipe was accepted",
                xycraft("centrifuge/sand_dirt"),
                xycraft("refinery/fuel"),
                xycraft("solidifier/lava"));
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_recipe_in_each_audited_machine_type_fails_closed(
            GameTestHelper helper
    ) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                xycraft("squasher/aluminum_sheet"),
                xycraft("blender/mud"),
                xycraft("crusher/cobbles"),
                xycraft("extractor/cobblestone"),
                xycraft("ore_tap/steam"),
                xycraft("fluid_tank_fill/water_bottle"),
                xycraft("fluid_tank_drain/water_bottle"),
                xycraft("buildings/temp"),
                xycraft("ark_melter/cobble"),
                xycraft("cry_chamber/cobble_lava"),
                xycraft("atmospheric_vacuum/nitrogen"),
                xycraft("centrifuge/sand_dirt"),
                xycraft("refinery/fuel"),
                xycraft("solidifier/lava"),
                xycraft("isolator/magma_block"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited XyCraft Machines recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited XyCraft Machines recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe XyCraft Machines recipe type accepted "
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
                helper.fail("Missing representative XyCraft Machines recipe " + recipeId);
                return;
            }
            if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail(acceptedMarker + ": " + recipeId);
                return;
            }
        }
        helper.succeed();
    }

    private static ResourceLocation xycraft(String path) {
        return ResourceLocation.fromNamespaceAndPath("xycraft", path);
    }

    private static ResourceLocation machines(String path) {
        return ResourceLocation.fromNamespaceAndPath("xycraft_machines", path);
    }
}
