package com.swear.autostorage;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

final class CoreFluidHandler implements IFluidHandler {
    private final StorageCoreBlockEntity core;

    CoreFluidHandler(StorageCoreBlockEntity core) {
        this.core = core;
    }

    @Override
    public int getTanks() {
        return Math.max(1, availableKeys().size());
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        List<StorageResourceKey> keys = availableKeys();
        if (tank < 0 || tank >= keys.size()) return FluidStack.EMPTY;
        StorageResourceKey key = keys.get(tank);
        int amount = (int) Math.min(Integer.MAX_VALUE, core.getResourceAmount(key));
        return StorageResourceBridge.fluidStack(key, amount, core.getLevel().registryAccess())
                .orElse(FluidStack.EMPTY);
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank >= 0 && tank < getTanks() ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank >= 0 && tank < getTanks() && !stack.isEmpty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || core.getLevel() == null) return 0;
        StorageResourceKey key = StorageResourceBridge.fluidKey(
                resource, core.getLevel().registryAccess());
        return (int) core.insertResource(
                key,
                resource.getAmount(),
                action.simulate() ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || core.getLevel() == null) return FluidStack.EMPTY;
        StorageResourceKey key = StorageResourceBridge.fluidKey(
                resource, core.getLevel().registryAccess());
        long extracted = core.extractResource(
                key,
                resource.getAmount(),
                action.simulate() ? Action.SIMULATE : Action.EXECUTE);
        if (extracted <= 0) return FluidStack.EMPTY;
        return StorageResourceBridge.fluidStack(
                key, (int) extracted, core.getLevel().registryAccess()).orElse(FluidStack.EMPTY);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0 || core.getLevel() == null) return FluidStack.EMPTY;
        List<StorageResourceKey> keys = availableKeys();
        if (keys.isEmpty()) return FluidStack.EMPTY;
        StorageResourceKey key = keys.getFirst();
        long extracted = core.extractResource(
                key,
                maxDrain,
                action.simulate() ? Action.SIMULATE : Action.EXECUTE);
        if (extracted <= 0) return FluidStack.EMPTY;
        return StorageResourceBridge.fluidStack(
                key, (int) extracted, core.getLevel().registryAccess()).orElse(FluidStack.EMPTY);
    }

    private List<StorageResourceKey> availableKeys() {
        if (core.getLevel() == null) return List.of();
        return core.getResourceKeys(StorageResourceBridge.FLUID_KIND).stream()
                .filter(key -> StorageResourceBridge.fluidStack(
                        key, 1, core.getLevel().registryAccess()).isPresent())
                .toList();
    }
}
