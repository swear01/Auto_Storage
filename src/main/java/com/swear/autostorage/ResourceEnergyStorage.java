package com.swear.autostorage;

import net.neoforged.neoforge.energy.IEnergyStorage;

final class ResourceEnergyStorage implements IEnergyStorage {
    private final StorageResourceHandler resources;

    ResourceEnergyStorage(StorageResourceHandler resources) {
        this.resources = resources;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (toReceive <= 0) return 0;
        return (int) resources.insert(StorageResourceBridge.ENERGY_KEY, toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (toExtract <= 0) return 0;
        return (int) resources.extract(StorageResourceBridge.ENERGY_KEY, toExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE,
                resources.getAmount(StorageResourceBridge.ENERGY_KEY));
    }

    @Override
    public int getMaxEnergyStored() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canExtract() {
        return resources.extract(StorageResourceBridge.ENERGY_KEY, 1, true) == 1;
    }

    @Override
    public boolean canReceive() {
        return resources.insert(StorageResourceBridge.ENERGY_KEY, 1, true) == 1;
    }
}
