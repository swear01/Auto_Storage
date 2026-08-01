package com.swear.autostorage.fixture.compatibilitymatrix;

import com.blakebr0.extendedcrafting.crafting.recipe.UltimateSingularityRecipe;
import com.blakebr0.extendedcrafting.singularity.SingularityRegistry;
import com.swear.autostorage.MachineEnergyTable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CompatibilityMatrixFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class CompatibilityMatrixGameTests {
    private CompatibilityMatrixGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void optional_compatibility_registrations_coexist(
            GameTestHelper helper
    ) {
        CompatibilityMatrixManifest manifest = CompatibilityMatrixManifest.load();
        if (!manifest.assertCoexistence(helper)) {
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
        CompatibilityMatrixManifest manifest = CompatibilityMatrixManifest.load();
        if (!manifest.assertAcceptedRecipes(helper)) {
            return;
        }
        helper.succeed();
    }
}
