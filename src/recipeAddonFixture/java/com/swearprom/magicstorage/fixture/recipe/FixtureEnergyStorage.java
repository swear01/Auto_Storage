package com.swearprom.magicstorage.fixture.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.energy.IEnergyStorage;

final class FixtureEnergyStorage implements IEnergyStorage {
    private static final String ENERGY_KEY = "fixtureEnergy";
    private static final int CAPACITY = 10_000;
    private final ItemStack stack;

    FixtureEnergyStorage(ItemStack stack) {
        this.stack = stack;
    }

    static void setEnergy(ItemStack stack, int amount) {
        int clamped = Math.clamp(amount, 0, CAPACITY);
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(ENERGY_KEY, clamped));
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = Math.min(Math.max(0, maxReceive), CAPACITY - getEnergyStored());
        if (!simulate && received > 0) setEnergy(stack, getEnergyStored() + received);
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = Math.min(Math.max(0, maxExtract), getEnergyStored());
        if (!simulate && extracted > 0) setEnergy(stack, getEnergyStored() - extracted);
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getInt(ENERGY_KEY);
    }

    @Override
    public int getMaxEnergyStored() {
        return CAPACITY;
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
