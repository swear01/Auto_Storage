package com.swear.autostorage;

import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.function.Supplier;

final class ResourceFluidHandler implements IFluidHandler {
    private final StorageResourceHandler resources;
    private final Supplier<HolderLookup.Provider> registries;

    ResourceFluidHandler(
            StorageResourceHandler resources,
            Supplier<HolderLookup.Provider> registries
    ) {
        this.resources = resources;
        this.registries = registries;
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
        return StorageResourceBridge.fluidStack(
                key,
                (int) Math.min(Integer.MAX_VALUE, resources.getAmount(key)),
                registries.get()).orElse(FluidStack.EMPTY);
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
        if (resource.isEmpty()) return 0;
        StorageResourceKey key = StorageResourceBridge.fluidKey(resource, registries.get());
        return (int) resources.insert(key, resource.getAmount(), action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        StorageResourceKey key = StorageResourceBridge.fluidKey(resource, registries.get());
        long extracted = resources.extract(key, resource.getAmount(), action.simulate());
        return extracted <= 0 ? FluidStack.EMPTY : StorageResourceBridge.fluidStack(
                key, (int) extracted, registries.get()).orElse(FluidStack.EMPTY);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        List<StorageResourceKey> keys = availableKeys();
        if (keys.isEmpty()) return FluidStack.EMPTY;
        StorageResourceKey key = keys.getFirst();
        long extracted = resources.extract(key, maxDrain, action.simulate());
        return extracted <= 0 ? FluidStack.EMPTY : StorageResourceBridge.fluidStack(
                key, (int) extracted, registries.get()).orElse(FluidStack.EMPTY);
    }

    private List<StorageResourceKey> availableKeys() {
        HolderLookup.Provider provider = registries.get();
        return resources.getStoredResources().stream()
                .filter(key -> key.kindId().equals(StorageResourceKindApi.FLUID_KIND))
                .filter(key -> StorageResourceBridge.fluidStack(key, 1, provider).isPresent())
                .sorted()
                .toList();
    }
}
