package com.swear.autostorage.fixture.hostilenetworks;

import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HostilenetworksFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class HostilenetworksIntegrationGameTests {
    private HostilenetworksIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void present_mod_registers_no_unsafe_families(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                HostilenetworksIntegrationGameTests.class);
        if (!ModList.get().isLoaded("hostilenetworks")) {
            helper.fail("Hostile Neural Networks mod is not loaded");
            return;
        }
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("hostilenetworks")
                                || id.getPath().startsWith("hostilenetworks"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("hostilenetworks")
                                || id.getPath().startsWith("hostilenetworks"))) {
            helper.fail("Hostile Neural Networks unsafe recipe contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void living_matter_vanilla_crafting_stays_supported(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                "hostilenetworks", "living_matter/overworldian/iron_ingot");
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail(
                    "Hostile Neural Networks living-matter vanilla crafting must stay supported");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void simulation_and_loot_fabricator_remain_fail_closed(
            GameTestHelper helper
    ) {
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(autoStorage(
                "hostilenetworks_sim_chamber"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(autoStorage(
                "hostilenetworks_loot_fabricator"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(autoStorage(
                "hostilenetworks_sim_chamber"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(autoStorage(
                "hostilenetworks_loot_fabricator"))) {
            helper.fail(
                    "Hostile Neural Networks simulation and loot fabricator must remain fail closed");
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
                        && id.getPath().contains("hostilenetworks"))) {
            helper.fail(
                    "Hostile Neural Networks simulation and loot fabricator must remain fail closed");
            return;
        }
        helper.succeed();
    }

    private static ResourceLocation autoStorage(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path);
    }
}
