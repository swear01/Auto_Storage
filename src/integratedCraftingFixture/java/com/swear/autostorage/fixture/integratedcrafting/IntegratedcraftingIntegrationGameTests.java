package com.swear.autostorage.fixture.integratedcrafting;

import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(IntegratedcraftingFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class IntegratedcraftingIntegrationGameTests {
    private IntegratedcraftingIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void present_mod_registers_no_unsafe_families(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                IntegratedcraftingIntegrationGameTests.class);
        if (!ModList.get().isLoaded("integratedcrafting")
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getPath().startsWith("integratedcrafting"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getPath().startsWith("integratedcrafting"))) {
            helper.fail("Integrated Crafting unsafe recipe contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void dead_bush_special_recipe_remains_fail_closed(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                "integratedcrafting", "special/minecraft_dead_bush");
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder != null && CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail(
                    "Integrated Crafting DeadBush special recipe must remain fail closed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void vanilla_network_automation_recipes_are_not_new_families(
            GameTestHelper helper
    ) {
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(autoStorage(
                "integratedcrafting_crafting_job"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(autoStorage(
                "integratedcrafting_recipe_index"))) {
            helper.fail("Integrated Crafting recipes must remain fail closed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void overflow_and_shortage_checks_stay_fail_closed(
            GameTestHelper helper
    ) {
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                .anyMatch(id -> id.getNamespace().equals("auto_storage")
                        && id.getPath().contains("integratedcrafting"))) {
            helper.fail("Integrated Crafting recipes must remain fail closed");
            return;
        }
        helper.succeed();
    }

    private static ResourceLocation autoStorage(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path);
    }
}
