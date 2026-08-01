package com.swear.autostorage.fixture.advancedae;

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

@GameTestHolder(AdvancedAeFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class AdvancedAeIntegrationGameTests {
    private AdvancedAeIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void unsafe_machine_contracts_are_not_registered(GameTestHelper helper) {
        if (!ModList.get().isLoaded("advanced_ae")) {
            helper.fail("Advanced AE mod is not loaded");
            return;
        }
        if (AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("advanced_ae")
                                || id.getPath().startsWith("advanced_ae_"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("advanced_ae")
                                || id.getPath().startsWith("advanced_ae_"))) {
            helper.fail("Advanced AE unsafe machine contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void quantum_alloy_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(aae("quantum_alloy")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Advanced AE recipe quantum_alloy");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Advanced AE recipe was accepted: quantum_alloy");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void shattered_singularity_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(aae("shatteredsingularity")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Advanced AE recipe shatteredsingularity");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Advanced AE recipe was accepted: shatteredsingularity");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void quantum_infusion_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(aae("quantum_infusion")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Advanced AE recipe quantum_infusion");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Advanced AE recipe was accepted: quantum_infusion");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void singularity_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(aae("singularity")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Advanced AE recipe singularity");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Advanced AE recipe was accepted: singularity");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void fluix_crystal_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(aae("fluixcrystals")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Advanced AE recipe fluixcrystals");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Advanced AE recipe was accepted: fluixcrystals");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void quartz_crystal_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(aae("quartzcrystal")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Advanced AE recipe quartzcrystal");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Advanced AE recipe was accepted: quartzcrystal");
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
                aae("quantum_alloy"),
                aae("shatteredsingularity"),
                aae("quantum_infusion"),
                aae("singularity"),
                aae("fluixcrystals"),
                aae("quartzcrystal"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited Advanced AE recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        if (types.size() != 1) {
            helper.fail("Expected 1 audited Advanced AE reaction recipe type, but found "
                    + types.size());
            return;
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited Advanced AE recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe Advanced AE recipe type accepted " + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static ResourceLocation aae(String path) {
        return ResourceLocation.fromNamespaceAndPath("advanced_ae", path);
    }
}
