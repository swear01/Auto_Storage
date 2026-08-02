package com.swear.autostorage.fixture.draconicevolution;

import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.List;

@GameTestHolder(DraconicevolutionFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class DraconicevolutionIntegrationGameTests {
    private DraconicevolutionIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void present_mod_registers_no_unsafe_families(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(helper.getLevel().getRecipeManager(), DraconicevolutionIntegrationGameTests.class);
        if (!ModList.get().isLoaded("draconicevolution")
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("draconicevolution")
                                || id.getPath().startsWith("draconicevolution_"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("draconicevolution")
                                || id.getPath().startsWith("draconicevolution_"))) {
            helper.fail("Draconic Evolution unsafe recipe contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void vanilla_crafting_under_namespace_stays_supported(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = de("infused_obsidian");
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Draconic Evolution vanilla crafting must stay supported");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void fusion_awakened_draconium_block_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(de("awakened_draconium_block")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Draconic Evolution recipe awakened_draconium_block");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Draconic Evolution recipe was accepted: awakened_draconium_block");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void fusion_awakened_core_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(de("components/awakened_core")).orElse(null);
        if (holder == null) {
            helper.fail("Missing representative Draconic Evolution recipe components/awakened_core");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Unsafe Draconic Evolution recipe was accepted: components/awakened_core");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void fusion_crafting_injector_fail_closed(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(de("machines/wyvern_crafting_injector")).orElse(null);
        if (holder == null) {
            helper.fail(
                    "Missing representative Draconic Evolution recipe machines/wyvern_crafting_injector");
            return;
        }
        if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail(
                    "Unsafe Draconic Evolution recipe was accepted: machines/wyvern_crafting_injector");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_fusion_crafting_recipe_fails_closed(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                de("awakened_draconium_block"),
                de("components/awakened_core"),
                de("machines/wyvern_crafting_injector"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited Draconic Evolution recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        if (types.size() != 1) {
            helper.fail(
                    "Expected 1 unique audited Draconic Evolution fusion recipe type, but found "
                            + types.size());
            return;
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited Draconic Evolution recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe Draconic Evolution fusion recipe type accepted " + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static ResourceLocation de(String path) {
        return ResourceLocation.fromNamespaceAndPath("draconicevolution", path);
    }
}
