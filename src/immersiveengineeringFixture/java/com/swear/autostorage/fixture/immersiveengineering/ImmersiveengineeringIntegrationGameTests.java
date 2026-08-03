package com.swear.autostorage.fixture.immersiveengineering;

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

@GameTestHolder(ImmersiveengineeringFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class ImmersiveengineeringIntegrationGameTests {
    private ImmersiveengineeringIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void unsafe_multiblock_contracts_are_not_registered(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                ImmersiveengineeringIntegrationGameTests.class);
        if (!ModList.get().isLoaded("immersiveengineering")) {
            helper.fail("Immersive Engineering mod is not loaded");
            return;
        }
        if (AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("immersiveengineering")
                                || id.getPath().startsWith("immersiveengineering_"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("immersiveengineering")
                                || id.getPath().startsWith("immersiveengineering_"))) {
            helper.fail("Immersive Engineering unsafe multiblock contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void alloy_smelter_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(ie("alloysmelter/electrum")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Immersive Engineering recipe alloysmelter/electrum");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Immersive Engineering recipe was accepted: alloysmelter/electrum");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void coke_oven_recipes_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(ie("cokeoven/charcoal")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Immersive Engineering recipe cokeoven/charcoal");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Immersive Engineering recipe was accepted: cokeoven/charcoal");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void blast_furnace_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, ie("blastfurnace/steel"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void crusher_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, ie("crusher/amethyst"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void cloche_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, ie("cloche/allium"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void metal_press_and_arc_furnace_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, ie("metalpress/blaze_rod"), ie("arcfurnace/dust_iron"));
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_recipe_in_each_audited_machine_type_fails_closed(
            GameTestHelper helper
    ) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                ie("alloysmelter/electrum"),
                ie("cokeoven/charcoal"),
                ie("blastfurnace/steel"),
                ie("crusher/amethyst"),
                ie("cloche/allium"),
                ie("metalpress/blaze_rod"),
                ie("arcfurnace/dust_iron"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited Immersive Engineering recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        if (types.size() != 7) {
            helper.fail("Audited Immersive Engineering recipe type is empty");
            return;
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited Immersive Engineering recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe Immersive Engineering recipe type accepted " + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static void assertUnsupported(GameTestHelper helper, ResourceLocation... recipeIds) {
        for (ResourceLocation recipeId : recipeIds) {
            var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing representative Immersive Engineering recipe " + recipeId);
                return;
            }
            if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Unsafe Immersive Engineering recipe was accepted: " + recipeId);
                return;
            }
        }
        helper.succeed();
    }

    private static ResourceLocation ie(String path) {
        return ResourceLocation.fromNamespaceAndPath("immersiveengineering", path);
    }
}
