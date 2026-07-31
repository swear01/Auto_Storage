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

import com.google.gson.JsonParser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
        if (!CompatibilityMatrixManifest.recipeInventorySha256(List.of()).equals(
                "37517e5f3dc66819f61f5a7bb8ace1921282415f10551d2defa5c3eb0985b570")) {
            helper.fail("Empty recipe inventory canonicalization disagreed with the generator");
            return;
        }
        if (!missingRecipeInventoryFailsClearly()) {
            helper.fail("Missing recipeInventory did not fail with a descriptive error");
            return;
        }
        CompatibilityMatrixManifest manifest = CompatibilityMatrixManifest.load();
        if (!manifest.assertCoexistence(helper, "Descriptor matrix coexistence")) {
            return;
        }
        if (AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getPath().startsWith("productivemetalworks_"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getPath().startsWith("productivemetalworks_"))) {
            helper.fail("Productive Metalworks fail-closed boundary changed");
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

    private static boolean missingRecipeInventoryFailsClearly() {
        try {
            Method parseGroup = CompatibilityMatrixManifest.class.getDeclaredMethod(
                    "parseGroup", com.google.gson.JsonObject.class, boolean.class);
            parseGroup.setAccessible(true);
            parseGroup.invoke(
                    null,
                    JsonParser.parseString("{\"id\":\"sample\",\"mods\":[],"
                            + "\"descriptors\":[],\"resourceKinds\":[],"
                            + "\"acceptedRecipes\":[],\"rejectedDescriptors\":[],"
                            + "\"rejectedResourceKinds\":[]}").getAsJsonObject(),
                    true);
            return false;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            return cause instanceof IllegalStateException
                    && cause.getMessage() != null
                    && cause.getMessage().contains("missing recipeInventory");
        } catch (ReflectiveOperationException exception) {
            return false;
        }
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
