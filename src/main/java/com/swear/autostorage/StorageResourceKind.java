package com.swear.autostorage;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Supplier;

public final class StorageResourceKind {
    private final boolean variantAware;
    private final Supplier<ItemStack> representative;

    private StorageResourceKind(boolean variantAware, Supplier<ItemStack> representative) {
        this.variantAware = variantAware;
        this.representative = Objects.requireNonNull(representative, "representative");
    }

    public static StorageResourceKind variantAware(Supplier<ItemStack> representative) {
        return new StorageResourceKind(true, representative);
    }

    public static StorageResourceKind variantless(Supplier<ItemStack> representative) {
        return new StorageResourceKind(false, representative);
    }

    public boolean supportsVariants() {
        return variantAware;
    }

    public ItemStack representative() {
        ItemStack stack = Objects.requireNonNull(representative.get(), "representative stack").copy();
        if (stack.isEmpty()) {
            throw new IllegalStateException("Storage resource kind representative cannot be empty");
        }
        stack.setCount(1);
        return stack;
    }

    boolean accepts(StorageResourceKey key) {
        return variantAware || key.variantData().isEmpty();
    }
}
