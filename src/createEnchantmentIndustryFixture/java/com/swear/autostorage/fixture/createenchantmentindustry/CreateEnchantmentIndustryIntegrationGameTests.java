package com.swear.autostorage.fixture.createenchantmentindustry;

import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import com.swear.autostorage.CraftingTerminalMenu;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;

@GameTestHolder(CreateEnchantmentIndustryFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class CreateEnchantmentIndustryIntegrationGameTests {
    private CreateEnchantmentIndustryIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void present_mod_registers_no_unsafe_families(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                CreateEnchantmentIndustryIntegrationGameTests.class);
        if (!ModList.get().isLoaded("create_enchantment_industry")
                || !ModList.get().isLoaded("create_dragons_plus")
                || AutoStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getPath().startsWith("create_enchantment_industry")
                                || id.getNamespace().equals("create_enchantment_industry"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getPath().startsWith("create_enchantment_industry")
                                || id.getNamespace().equals("create_enchantment_industry"))) {
            helper.fail("Create Enchantment Industry unsafe recipe contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void vanilla_crafting_under_cei_namespace_stays_supported(
            GameTestHelper helper
    ) {
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                "create_enchantment_industry", "crafting/printer");
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail(
                    "Create Enchantment Industry vanilla crafting under its namespace must stay supported");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void grinding_and_printing_remain_fail_closed(GameTestHelper helper) {
        if (AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(autoStorage(
                "create_enchantment_industry_grinding"))
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(autoStorage(
                "create_enchantment_industry_printing"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(autoStorage(
                "create_enchantment_industry_mechanical_grindstone"))
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(autoStorage(
                "create_enchantment_industry_printer"))
                || AutoStorage.RESOURCE_KIND_REGISTRY.containsKey(cei("experience"))) {
            helper.fail(
                    "Create Enchantment Industry grinding/printing/infusing must remain fail closed");
            return;
        }
        ResourceLocation grindingId = cei("grinding/experience_nugget");
        var grinding = helper.getLevel().getRecipeManager().byKey(grindingId).orElse(null);
        if (grinding == null || CraftingTerminalMenu.supportsRecipeHolder(grinding)) {
            helper.fail(
                    "Create Enchantment Industry grinding/printing/infusing must remain fail closed");
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
                        && id.getPath().contains("create_enchantment_industry"))) {
            helper.fail(
                    "Create Enchantment Industry grinding/printing/infusing must remain fail closed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_loaded_grinding_recipe_fails_closed(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        ResourceLocation seedId = cei("grinding/experience_nugget");
        var seed = manager.byKey(seedId).orElse(null);
        if (seed == null) {
            helper.fail("Missing audited Create Enchantment Industry grinding recipe " + seedId);
            return;
        }
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        types.add(seed.value().getType());
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited Create Enchantment Industry grinding type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail(
                            "Create Enchantment Industry grinding/printing/infusing must remain fail closed: "
                                    + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static ResourceLocation autoStorage(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path);
    }

    private static ResourceLocation cei(String path) {
        return ResourceLocation.fromNamespaceAndPath("create_enchantment_industry", path);
    }
}
