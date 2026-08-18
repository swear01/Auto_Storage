package com.swear.autostorage.gametest;

import com.swear.autostorage.EnergyType;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.SortMode;
import com.swear.autostorage.SortOrder;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.TerminalDisplayStack;
import com.swear.autostorage.TerminalResourceView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AutoStorage.MODID)
@PrefixGameTestTemplate(false)
public final class TypedResourceTests {
    private TypedResourceTests() {
    }

    @GameTest(template = "behavioraltests.platform")
    public static void core_fluid_capability_preserves_exact_variants_and_simulation(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            IFluidHandler handler = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, corePos, null);
            if (handler == null) {
                helper.fail("Storage Core did not expose NeoForge fluid capability");
                return;
            }

            FluidStack water = new FluidStack(Fluids.WATER, 1_000);
            int simulated = handler.fill(water, IFluidHandler.FluidAction.SIMULATE);
            if (simulated != 1_000 || core.getTypeCount() != 0 || water.getAmount() != 1_000) {
                helper.fail("Fluid simulation mutated Core or caller stack");
                return;
            }
            if (handler.fill(water, IFluidHandler.FluidAction.EXECUTE) != 1_000
                    || core.getTypeCount() != 1 || water.getAmount() != 1_000) {
                helper.fail("Fluid execute did not store exact amount without mutating caller stack");
                return;
            }

            FluidStack namedWater = new FluidStack(Fluids.WATER, 250);
            namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("Exact Variant"));
            if (handler.fill(namedWater, IFluidHandler.FluidAction.EXECUTE) != 250
                    || core.getTypeCount() != 2) {
                helper.fail("Fluid components did not create an exact second resource type");
                return;
            }
            if (handler.getTanks() != 2) {
                helper.fail("Expected two exact fluid tanks, got " + handler.getTanks());
                return;
            }

            FluidStack simulatedDrain = handler.drain(
                    new FluidStack(Fluids.WATER, 600), IFluidHandler.FluidAction.SIMULATE);
            if (simulatedDrain.getAmount() != 600 || core.getTypeCount() != 2) {
                helper.fail("Fluid drain simulation changed Core");
                return;
            }
            FluidStack drained = handler.drain(
                    new FluidStack(Fluids.WATER, 600), IFluidHandler.FluidAction.EXECUTE);
            if (drained.getAmount() != 600 || core.getTypeCount() != 2) {
                helper.fail("Fluid drain execute returned the wrong amount or removed a live type");
                return;
            }
            FluidStack remainingPlain = handler.drain(
                    new FluidStack(Fluids.WATER, Integer.MAX_VALUE),
                    IFluidHandler.FluidAction.SIMULATE);
            if (remainingPlain.getAmount() != 400) {
                helper.fail("Plain water amount should be 400, got " + remainingPlain.getAmount());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "behavioraltests.platform")
    public static void core_energy_capability_is_atomic_and_separate_from_crafting_energy(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, corePos, null);
            if (energy == null) {
                helper.fail("Storage Core did not expose NeoForge Energy capability");
                return;
            }
            if (energy.receiveEnergy(1_200, true) != 1_200
                    || energy.getEnergyStored() != 0 || core.getTypeCount() != 0) {
                helper.fail("Energy simulation mutated Core");
                return;
            }
            if (energy.receiveEnergy(1_200, false) != 1_200
                    || energy.getEnergyStored() != 1_200 || core.getTypeCount() != 1) {
                helper.fail("Energy execute did not store the exact amount");
                return;
            }
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != 0
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != 0) {
                helper.fail("NeoForge Energy leaked into crafting energy pools");
                return;
            }
            var entries = core.getTerminalDisplayStacks(
                    "", SortMode.NAME, SortOrder.ASCENDING, TerminalResourceView.ENERGY);
            ItemStack entry = entries.size() == 1 ? entries.getFirst() : ItemStack.EMPTY;
            if (!entry.is(Items.LIGHTNING_ROD)
                    || TerminalDisplayStack.amount(entry) != 1_200
                    || !Component.translatable("gui.auto_storage.resource.neoforge_energy")
                    .equals(entry.get(DataComponents.CUSTOM_NAME))) {
                helper.fail("NeoForge Energy terminal entry used the wrong lightning-rod representative");
                return;
            }
            if (energy.extractEnergy(350, true) != 350 || energy.getEnergyStored() != 1_200) {
                helper.fail("Energy extraction simulation mutated Core");
                return;
            }
            if (energy.extractEnergy(350, false) != 350 || energy.getEnergyStored() != 850) {
                helper.fail("Energy extraction execute returned the wrong amount");
                return;
            }
            helper.succeed();
        });
    }
}
