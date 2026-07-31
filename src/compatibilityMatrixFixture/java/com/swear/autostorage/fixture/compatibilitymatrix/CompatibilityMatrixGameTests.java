package com.swear.autostorage.fixture.compatibilitymatrix;

import com.blakebr0.extendedcrafting.crafting.recipe.UltimateSingularityRecipe;
import com.blakebr0.extendedcrafting.singularity.SingularityRegistry;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.StorageResourceKindApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(CompatibilityMatrixFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class CompatibilityMatrixGameTests {
    private CompatibilityMatrixGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void optional_compatibility_registrations_coexist(
            GameTestHelper helper
    ) {
        for (String modId : List.of(
                "ae2",
                "mekanism",
                "botania",
                "ironfurnaces",
                "farmersdelight",
                "modern_industrialization",
                "ars_nouveau",
                "evilcraft",
                "powah",
                "industrialforegoing",
                "create",
                "pneumaticcraft",
                "extendedcrafting",
                "theurgy")) {
            if (!ModList.get().isLoaded(modId)) {
                helper.fail("Compatibility matrix did not load " + modId);
                return;
            }
        }
        for (String path : List.of(
                "ae2_inscriber",
                "mekanism_energized_smelter",
                "botania_mana_pool",
                "farmers_delight_cooking_pot",
                "modern_industrialization_macerator",
                "ars_nouveau_imbuement_chamber",
                "evilcraft_blood_infuser",
                "powah_energizing",
                "industrial_foregoing_dissolution_chamber",
                "create_cutting",
                "extended_crafting_table",
                "theurgy_calcination_oven",
                "theurgy_distiller",
                "theurgy_liquefaction_cauldron")) {
            ResourceLocation id = autoStorage(path);
            if (!AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(id)
                    || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(id)) {
                helper.fail("Combined compatibility registry is missing " + id);
                return;
            }
        }
        for (ResourceLocation kindId : List.of(
                StorageResourceKindApi.CHEMICAL_KIND,
                StorageResourceKindApi.BOTANIA_MANA_KIND,
                StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND)) {
            if (AutoStorage.RESOURCE_KIND_REGISTRY.get(kindId) == null) {
                helper.fail("Combined compatibility registry is missing resource kind " + kindId);
                return;
            }
        }
        if (AutoStorage.RESOURCE_KIND_REGISTRY.containsKey(pnc("air"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(
                        autoStorage("pneumaticcraft_pressure_chamber"))) {
            helper.fail("PneumaticCraft fail-closed boundary changed");
            return;
        }
        var furnace = MachineEnergyTable.get(MachineEnergyTable.FURNACE_ID);
        ItemStack ironFurnace = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("ironfurnaces", "iron_furnace")));
        if (furnace == null || ironFurnace.is(Items.AIR) || !furnace.accepts(ironFurnace)) {
            helper.fail("Iron Furnaces variant did not coexist with the shared Furnace descriptor");
            return;
        }
        var ultimate = helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.fromNamespaceAndPath(
                        "extendedcrafting", "ultimate_singularity")).orElse(null);
        int expectedSingularities = SingularityRegistry.getInstance().getSingularities().stream()
                .filter(singularity -> singularity.isInUltimateSingularity()
                        && !singularity.getIngredient().isEmpty())
                .toList().size();
        int actualIngredients = ultimate != null
                && ultimate.value() instanceof UltimateSingularityRecipe recipe
                ? recipe.getIngredients().size()
                : -1;
        if (expectedSingularities <= 0 || actualIngredients != expectedSingularities) {
            helper.fail("Combined Ultimate Singularity inputs: expected "
                    + expectedSingularities + ", actual " + actualIngredients);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void accepted_recipe_families_classify_together(
            GameTestHelper helper
    ) {
        for (ResourceLocation recipeId : List.of(
                ResourceLocation.fromNamespaceAndPath(
                        "ae2", "inscriber/logic_processor"),
                ResourceLocation.fromNamespaceAndPath(
                        "botania", "mana_infusion/biscuit_of_totality"),
                ResourceLocation.fromNamespaceAndPath(
                        "modern_industrialization", "materials/aluminum/macerator/blade"),
                ResourceLocation.fromNamespaceAndPath(
                        "ars_nouveau", "imbuement_amethyst"),
                ResourceLocation.fromNamespaceAndPath(
                        "evilcraft", "blood_infuser/base/bloody_cobblestone"),
                ResourceLocation.fromNamespaceAndPath(
                        "powah", "energizing/energized_steel"),
                ResourceLocation.fromNamespaceAndPath(
                        "industrialforegoing", "dissolution_chamber/pink_slime_ball"),
                ResourceLocation.fromNamespaceAndPath(
                        "create", "cutting/andesite_alloy"),
                ResourceLocation.fromNamespaceAndPath(
                        "theurgy", "distillation/bread"))) {
            var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Combined compatibility recipe is missing " + recipeId);
                return;
            }
            if (!CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Combined compatibility rejected accepted recipe " + recipeId);
                return;
            }
        }
        helper.succeed();
    }

    private static ResourceLocation autoStorage(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path);
    }

    private static ResourceLocation pnc(String path) {
        return ResourceLocation.fromNamespaceAndPath("pneumaticcraft", path);
    }
}
