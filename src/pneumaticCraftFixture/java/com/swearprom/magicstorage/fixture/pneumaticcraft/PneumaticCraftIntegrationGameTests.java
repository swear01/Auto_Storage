package com.swearprom.magicstorage.fixture.pneumaticcraft;

import com.swearprom.magicstorage.magic_storage.CraftingTerminalMenu;
import com.swearprom.magicstorage.magic_storage.MagicStorage;
import me.desht.pneumaticcraft.common.capabilities.BasicAirHandler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.List;

@GameTestHolder(PneumaticCraftFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class PneumaticCraftIntegrationGameTests {
    private PneumaticCraftIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void unsafe_air_and_machine_contracts_are_not_registered(
            GameTestHelper helper
    ) {
        if (!ModList.get().isLoaded("pneumaticcraft")
                || MagicStorage.RESOURCE_KIND_REGISTRY.containsKey(pnc("air"))
                || MagicStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(
                        magicStorage("pneumaticcraft_pressure_chamber"))
                || MagicStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(
                        magicStorage("pneumaticcraft_thermo_plant"))
                || MagicStorage.RECIPE_FAMILY_REGISTRY.keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals("pneumaticcraft"))) {
            helper.fail("PneumaticCraft unsafe pressure/air contract was registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void pressure_chamber_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper,
                pnc("pressure_chamber/coal_to_diamond"),
                pnc("pressure_chamber/milk_to_slime_balls"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void air_handler_semantics_are_not_a_transaction_contract(
            GameTestHelper helper
    ) {
        BasicAirHandler handler = new BasicAirHandler(1_000);
        handler.addAir(-1_500);
        if (handler.getAir() != -1_000
                || handler.getPressure() != -1.0F
                || MagicStorage.RESOURCE_KIND_REGISTRY.containsKey(pnc("air"))) {
            helper.fail("PNC air audit no longer matches the fail-closed resource boundary");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void thermo_plant_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, pnc("thermo_plant/upgrade_matrix"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void fluid_mixer_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, pnc("fluid_mixer/mix_obsidian"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void assembly_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper,
                pnc("assembly/solar_cell"),
                pnc("assembly/unassembled_pcb"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void refinery_recipes_fail_closed(GameTestHelper helper) {
        assertUnsupported(helper, pnc("refinery/oil_2"));
    }

    @GameTest(template = "craftingtests.platform")
    public static void world_heat_and_explosion_recipes_fail_closed(
            GameTestHelper helper
    ) {
        assertUnsupported(helper,
                pnc("heat_frame_cooling/ice"),
                pnc("explosion_crafting/compressed_iron_ingot"));
    }

    @GameTest(template = "craftingtests.platform")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void every_recipe_in_each_audited_machine_type_fails_closed(
            GameTestHelper helper
    ) {
        var manager = helper.getLevel().getRecipeManager();
        var types = new LinkedHashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (ResourceLocation recipeId : List.of(
                pnc("pressure_chamber/coal_to_diamond"),
                pnc("thermo_plant/upgrade_matrix"),
                pnc("fluid_mixer/mix_obsidian"),
                pnc("assembly/solar_cell"),
                pnc("refinery/oil_2"),
                pnc("heat_frame_cooling/ice"),
                pnc("explosion_crafting/compressed_iron_ingot"))) {
            var holder = manager.byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing audited PneumaticCraft recipe " + recipeId);
                return;
            }
            types.add(holder.value().getType());
        }
        for (var type : types) {
            var holders = manager.getAllRecipesFor(
                    (net.minecraft.world.item.crafting.RecipeType) type);
            if (holders.isEmpty()) {
                helper.fail("Audited PneumaticCraft recipe type is empty: " + type);
                return;
            }
            for (Object raw : holders) {
                var holder = (net.minecraft.world.item.crafting.RecipeHolder<?>) raw;
                if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Unsafe PneumaticCraft recipe type accepted " + holder.id());
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static void assertUnsupported(
            GameTestHelper helper,
            ResourceLocation... recipeIds
    ) {
        for (ResourceLocation recipeId : recipeIds) {
            var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null) {
                helper.fail("Missing representative PneumaticCraft recipe " + recipeId);
                return;
            }
            if (CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                helper.fail("Unsafe PneumaticCraft recipe was accepted: " + recipeId);
                return;
            }
        }
        helper.succeed();
    }

    private static ResourceLocation pnc(String path) {
        return ResourceLocation.fromNamespaceAndPath("pneumaticcraft", path);
    }

    private static ResourceLocation magicStorage(String path) {
        return ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, path);
    }
}
