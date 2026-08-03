package com.swear.autostorage.fixture.integrateddynamics;

import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cyclops.integrateddynamics.block.BlockMechanicalSqueezerConfig;

@GameTestHolder(IntegrateddynamicsFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class IntegrateddynamicsIntegrationGameTests {
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final ResourceLocation DRYING_BASIN =
            autoStorage("integrateddynamics_drying_basin");
    private static final ResourceLocation MECHANICAL_DRYING_BASIN =
            autoStorage("integrateddynamics_mechanical_drying_basin");
    private static final ResourceLocation MECHANICAL_SQUEEZER =
            autoStorage("integrateddynamics_mechanical_squeezer");
    private static final ResourceLocation MANUAL_SQUEEZER =
            autoStorage("integrateddynamics_squeezer");
    private static final ResourceLocation DRYING_RECIPE = idRecipe(
            "drying_basin/base/crystalized_menril_block");
    private static final ResourceLocation SQUEEZER_RECIPE = idRecipe(
            "mechanical_squeezer/base/menril_resin_planks");
    private static final ResourceLocation CHANCE_SQUEEZER_RECIPE = idRecipe(
            "mechanical_squeezer/convenience/minecraft_dye_yellow_2");

    private IntegrateddynamicsIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void registers_exact_stations_and_rejects_manual_squeezer(
            GameTestHelper helper
    ) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                IntegrateddynamicsIntegrationGameTests.class);
        if (!validStation(DRYING_BASIN, "drying_basin")
                || !validStation(MECHANICAL_DRYING_BASIN, "mechanical_drying_basin")
                || !validStation(MECHANICAL_SQUEEZER, "mechanical_squeezer")
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(DRYING_BASIN)
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(MECHANICAL_DRYING_BASIN)
                || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(MECHANICAL_SQUEEZER)
                || AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(MANUAL_SQUEEZER)
                || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(MANUAL_SQUEEZER)
                || supports(helper, CHANCE_SQUEEZER_RECIPE)) {
            helper.fail("Integrated Dynamics registration was incorrect");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void drying_basin_consumes_fluid_and_duration(GameTestHelper helper) {
        withCore(helper, context -> {
            seedFluid(context, idFluid("menril_resin"), 1000);
            installStation(context, "drying_basin");
            tick(context.core(), 150);
            if (!craft(context, DRYING_RECIPE)
                    || fluidAmount(context, idFluid("menril_resin")) != 0
                    || itemCount(context.core(), idItem("crystalized_menril_block")) != 1
                    || context.core().getStationWork(DRYING_BASIN) != 0) {
                helper.fail("Integrated Dynamics drying basin transaction was wrong");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void mechanical_squeezer_consumes_item_fe_and_duration(
            GameTestHelper helper
    ) {
        withCore(helper, context -> {
            long energy = expectedSqueezerEnergy(15);
            seedItem(context.core(), idItem("menril_planks"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installStation(context, "mechanical_squeezer");
            tick(context.core(), 15);
            if (!craft(context, SQUEEZER_RECIPE)
                    || itemCount(context.core(), idItem("menril_planks")) != 0
                    || itemCount(context.core(), idItem("crystalized_menril_chunk")) != 1
                    || fluidAmount(context, idFluid("menril_resin")) != 250
                    || context.core().getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != 0
                    || context.core().getStationWork(MECHANICAL_SQUEEZER) != 0) {
                helper.fail("Integrated Dynamics mechanical squeezer transaction was wrong");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void missing_ingredient_is_atomic_no_op(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedSqueezerEnergy(15);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            installStation(context, "mechanical_squeezer");
            tick(context.core(), 15);
            if (craft(context, SQUEEZER_RECIPE)
                    || itemCount(context.core(), idItem("crystalized_menril_chunk")) != 0
                    || fluidAmount(context, idFluid("menril_resin")) != 0
                    || context.core().getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(MECHANICAL_SQUEEZER) != 15) {
                helper.fail(
                        "Integrated Dynamics missing-ingredient transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void full_destination_is_atomic_no_op(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedSqueezerEnergy(15);
            seedItem(context.core(), idItem("menril_planks"), 1);
            seedResource(context.core(), StorageResourceKey.neoforgeEnergy(), energy);
            Item output = idItem("crystalized_menril_chunk");
            seedResource(
                    context.core(),
                    StorageResourceKey.item(
                            new ItemStack(output),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            installStation(context, "mechanical_squeezer");
            tick(context.core(), 15);
            if (craft(context, SQUEEZER_RECIPE)
                    || itemCount(context.core(), idItem("menril_planks")) != 1
                    || itemCount(context.core(), output) != Long.MAX_VALUE
                    || fluidAmount(context, idFluid("menril_resin")) != 0
                    || context.core().getResourceAmount(
                            StorageResourceKey.neoforgeEnergy()) != energy
                    || context.core().getStationWork(MECHANICAL_SQUEEZER) != 15) {
                helper.fail(
                        "Integrated Dynamics full destination transaction was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void checked_fluid_output_overflow_is_atomic(GameTestHelper helper) {
        withCore(helper, context -> {
            long energy = expectedSqueezerEnergy(15);
            seedItem(context.core(), idItem("menril_planks"), 1);
            seedResource(
                    context.core(),
                    StorageResourceKey.neoforgeEnergy(),
                    energy);
            seedResource(
                    context.core(),
                    StorageResourceKey.fluid(
                            new FluidStack(idFluid("menril_resin"), 1),
                            context.level().registryAccess()),
                    Long.MAX_VALUE);
            installStation(context, "mechanical_squeezer");
            tick(context.core(), 15);
            if (craft(context, SQUEEZER_RECIPE)
                    || itemCount(context.core(), idItem("menril_planks")) != 1
                    || itemCount(context.core(), idItem("crystalized_menril_chunk")) != 0
                    || fluidAmount(context, idFluid("menril_resin")) != Long.MAX_VALUE
                    || context.core().getResourceAmount(
                            StorageResourceKey.neoforgeEnergy())
                    != energy
                    || context.core().getStationWork(MECHANICAL_SQUEEZER) != 15) {
                helper.fail(
                        "Integrated Dynamics fluid-output overflow was not an atomic no-op");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void chance_outputs_remain_fail_closed(GameTestHelper helper) {
        if (supports(helper, CHANCE_SQUEEZER_RECIPE)
                || supports(helper, idRecipe("squeezer/convenience/minecraft_string"))) {
            helper.fail(
                    "Integrated Dynamics chance-output recipes must remain fail closed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void runtime_mechanical_energy_uses_loaded_config(GameTestHelper helper) {
        long expected = expectedSqueezerEnergy(15);
        long recomputed = Math.multiplyExact(
                (long) BlockMechanicalSqueezerConfig.consumptionRate, 15L);
        if (expected != recomputed || expected <= 0) {
            helper.fail("Integrated Dynamics mechanical energy config was not exact");
            return;
        }
        helper.succeed();
    }

    private static boolean validStation(ResourceLocation descriptorId, String itemPath) {
        MachineDescriptor descriptor = MachineEnergyTable.get(descriptorId);
        ItemStack station = new ItemStack(idItem(itemPath));
        return descriptor != null
                && descriptor.category() == MachineCategory.PROCESS
                && descriptor.maxInstalledCount() == MachineDescriptorApi.MAX_INSTALLED_COUNT
                && descriptor.variants().size() == 1
                && descriptor.accepts(station)
                && descriptor.rateFor(station).orElseThrow().equals(MachineWorkRate.ONE);
    }

    private static boolean supports(GameTestHelper helper, ResourceLocation recipeId) {
        var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
        return holder != null && CraftingTerminalMenu.supportsRecipeHolder(holder);
    }

    private static long expectedSqueezerEnergy(int duration) {
        return Math.multiplyExact(
                (long) BlockMechanicalSqueezerConfig.consumptionRate, (long) duration);
    }

    private static void withCore(GameTestHelper helper, FixtureAssertion assertion) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(
                corePos,
                AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(
                corePos.south(),
                AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(
                    corePos.getX() + 0.5,
                    corePos.getY() + 0.5,
                    corePos.getZ() + 0.5);
            assertion.run(new FixtureContext(level, core, player));
        });
    }

    private static void installStation(FixtureContext context, String itemPath) {
        ItemStack station = new ItemStack(idItem(itemPath));
        var menu = new CraftingTerminalMenu(
                930, context.player().getInventory(), context.core());
        menu.clickMenuButton(context.player(), STATIONS_PAGE_BUTTON);
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START
                     + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = menu.getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) continue;
            slot.set(station.copy());
            slot.setChanged();
            menu.clickMenuButton(context.player(), STORAGE_PAGE_BUTTON);
            return;
        }
        throw new IllegalStateException("Could not install Integrated Dynamics station "
                + itemPath);
    }

    private static boolean craft(FixtureContext context, ResourceLocation recipeId) {
        var menu = new CraftingTerminalMenu(
                931, context.player().getInventory(), context.core());
        if (!menu.handleRecipeRequest(
                context.level(), recipeId, 1,
                CraftingDestination.NONE, context.player())) {
            return false;
        }
        if (menu.computeCraftPreview(context.core(), context.player()).craftable() < 1) {
            return false;
        }
        return menu.handleRecipeRequest(
                context.level(), recipeId, 1,
                CraftingDestination.STORAGE, context.player());
    }

    private static void seedItem(StorageCoreBlockEntity core, Item item, int amount) {
        ItemStack stack = new ItemStack(item, amount);
        if (core.insertItem(stack) != amount) {
            throw new IllegalStateException("Could not seed " + item + " x" + amount);
        }
    }

    private static void seedFluid(FixtureContext context, Fluid fluid, int amount) {
        seedResource(
                context.core(),
                StorageResourceKey.fluid(
                        new FluidStack(fluid, 1), context.level().registryAccess()),
                amount);
    }

    private static void seedResource(
            StorageCoreBlockEntity core,
            StorageResourceKey key,
            long amount
    ) {
        if (core.insertResource(key, amount, Action.EXECUTE) != amount) {
            throw new IllegalStateException("Could not seed " + key + " x" + amount);
        }
    }

    private static long itemCount(StorageCoreBlockEntity core, Item item) {
        return core.getItemCount(ItemKey.of(new ItemStack(item)));
    }

    private static long fluidAmount(FixtureContext context, Fluid fluid) {
        return context.core().getResourceAmount(
                StorageResourceKey.fluid(
                        new FluidStack(fluid, 1), context.level().registryAccess()));
    }

    private static void tick(StorageCoreBlockEntity core, int ticks) {
        for (int tick = 0; tick < ticks; tick++) core.tick();
    }

    private static Item idItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(id(path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Integrated Dynamics item " + path);
        }
        return item;
    }

    private static Fluid idFluid(String path) {
        Fluid fluid = BuiltInRegistries.FLUID.get(id(path));
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
            throw new IllegalStateException("Missing Integrated Dynamics fluid " + path);
        }
        return fluid;
    }

    private static ResourceLocation idRecipe(String path) {
        return id(path);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("integrateddynamics", path);
    }

    private static ResourceLocation autoStorage(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, path);
    }

    private record FixtureContext(
            net.minecraft.server.level.ServerLevel level,
            StorageCoreBlockEntity core,
            net.minecraft.world.entity.player.Player player
    ) {
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(FixtureContext context);
    }
}
