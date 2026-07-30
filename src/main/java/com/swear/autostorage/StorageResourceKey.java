package com.swear.autostorage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Objects;
import java.util.Optional;

public final class StorageResourceKey implements Comparable<StorageResourceKey> {
    private final ResourceLocation kindId;
    private final ResourceLocation resourceId;
    private final CompoundTag variantData;

    private StorageResourceKey(
            ResourceLocation kindId,
            ResourceLocation resourceId,
            CompoundTag variantData
    ) {
        this.kindId = Objects.requireNonNull(kindId, "kindId");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.variantData = Objects.requireNonNull(variantData, "variantData").copy();
    }

    public static StorageResourceKey of(
            ResourceLocation kindId,
            ResourceLocation resourceId,
            CompoundTag variantData
    ) {
        return new StorageResourceKey(kindId, resourceId, variantData);
    }

    public static StorageResourceKey item(
            ItemStack stack,
            HolderLookup.Provider registries
    ) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Cannot key an empty item stack");
        return StorageResourceBridge.itemKey(ItemKey.of(stack), registries);
    }

    public static StorageResourceKey fluid(
            FluidStack stack,
            HolderLookup.Provider registries
    ) {
        return StorageResourceBridge.fluidKey(stack, registries);
    }

    public static StorageResourceKey neoforgeEnergy() {
        return StorageResourceBridge.ENERGY_KEY;
    }

    public Optional<ItemStack> itemStack(HolderLookup.Provider registries) {
        return StorageResourceBridge.itemKey(this, registries)
                .map(key -> key.toStack(1));
    }

    public Optional<FluidStack> fluidStack(
            int amount,
            HolderLookup.Provider registries
    ) {
        return StorageResourceBridge.fluidStack(this, amount, registries);
    }

    public ResourceLocation kindId() {
        return kindId;
    }

    public ResourceLocation resourceId() {
        return resourceId;
    }

    public CompoundTag variantData() {
        return variantData.copy();
    }

    @Override
    public int compareTo(StorageResourceKey other) {
        int kindOrder = kindId.toString().compareTo(other.kindId.toString());
        if (kindOrder != 0) return kindOrder;
        int resourceOrder = resourceId.toString().compareTo(other.resourceId.toString());
        if (resourceOrder != 0) return resourceOrder;
        return variantData.toString().compareTo(other.variantData.toString());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StorageResourceKey key
                && kindId.equals(key.kindId)
                && resourceId.equals(key.resourceId)
                && variantData.equals(key.variantData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kindId, resourceId, variantData);
    }

    @Override
    public String toString() {
        return kindId + "/" + resourceId + variantData;
    }
}
