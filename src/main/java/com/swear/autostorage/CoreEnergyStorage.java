package com.swear.autostorage;

import net.neoforged.neoforge.energy.IEnergyStorage;

final class CoreEnergyStorage implements IEnergyStorage {
    private final StorageCoreBlockEntity core;

    CoreEnergyStorage(StorageCoreBlockEntity core) {
        this.core = core;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (toReceive <= 0) return 0;
        return (int) core.insertResource(
                StorageResourceBridge.ENERGY_KEY,
                toReceive,
                simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (toExtract <= 0) return 0;
        return (int) core.extractResource(
                StorageResourceBridge.ENERGY_KEY,
                toExtract,
                simulate ? Action.SIMULATE : Action.EXECUTE);
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE,
                core.getResourceAmount(StorageResourceBridge.ENERGY_KEY));
    }

    @Override
    public int getMaxEnergyStored() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canExtract() {
        return getEnergyStored() > 0;
    }

    @Override
    public boolean canReceive() {
        return core.insertResource(
                StorageResourceBridge.ENERGY_KEY, 1, Action.SIMULATE) == 1;
    }
}
