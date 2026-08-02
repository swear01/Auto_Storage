package com.swear.autostorage.fixture.createaquaticambitions;

import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.AutoStorage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateAquaticAmbitionsFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class CreateAquaticAmbitionsIntegrationGameTests {
    private CreateAquaticAmbitionsIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void unsafe_machine_contracts_are_not_registered(GameTestHelper helper) {
        if (!ModList.get().isLoaded("create_aquatic_ambitions")) {
            helper.fail("Create Aquatic Ambitions mod is not loaded");
            return;
        }
        if (!ModList.get().isLoaded("create")) {
            helper.fail("Create dependency is not loaded for Create Aquatic Ambitions fixture");
            return;
        }
        if (AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("create_aquatic_ambitions")
                                || id.getPath().startsWith("create_aquatic_ambitions_"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("create_aquatic_ambitions")
                                || id.getPath().startsWith("create_aquatic_ambitions_"))) {
            helper.fail("Create Aquatic Ambitions unsafe machine contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void channeling_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(caa("channeling/brain_coral_block")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Create Aquatic Ambitions recipe channeling/brain_coral_block");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Create Aquatic Ambitions recipe was accepted: channeling/brain_coral_block");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void chanced_channeling_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(caa("channeling/brain_coral_fan_revival")).orElse(null);
        if (holder == null) {
            helper.fail(
                    "Missing representative Create Aquatic Ambitions recipe channeling/brain_coral_fan_revival");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail(
                    "Unsafe Create Aquatic Ambitions recipe was accepted: channeling/brain_coral_fan_revival");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_channeling_recipe_fails_closed(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var representative = manager.byKey(caa("channeling/brain_coral_block")).orElse(null);
        if (representative == null) {
            helper.fail("Missing representative Create Aquatic Ambitions recipe channeling/brain_coral_block");
            return;
        }
        var type = representative.value().getType();
        var holders = manager.getAllRecipesFor(
                (net.minecraft.world.item.crafting.RecipeType) type);
        if (holders.isEmpty()) {
            helper.fail("Audited Create Aquatic Ambitions recipe type is empty");
            return;
        }
        for (Object raw : holders) {
            var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
            if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Unsafe Create Aquatic Ambitions recipe type accepted " + holder.id());
                return;
            }
        }
        helper.succeed();
    }

    private static ResourceLocation caa(String path) {
        return ResourceLocation.fromNamespaceAndPath("create_aquatic_ambitions", path);
    }
}
