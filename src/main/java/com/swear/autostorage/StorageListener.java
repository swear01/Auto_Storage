package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;

@FunctionalInterface
public interface StorageListener {
    void onChanged(ItemKey key, long delta, long newAmount, Actor actor);

    default void onEnergyChanged(EnergyType type, long newAmount) {
    }

    default void onStationWorkChanged(
            ResourceLocation descriptorId,
            long delta,
            long newAmount
    ) {
    }

    default void onResourceChanged(
            StorageResourceKey key,
            long delta,
            long newAmount,
            Actor actor
    ) {
    }
}
