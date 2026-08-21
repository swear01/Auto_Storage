package com.swear.autostorage;

import net.minecraft.world.item.ItemStack;

record TerminalRepositoryEntry(
        long serial,
        StorageResourceKey key,
        ItemStack displayStack
) {
    TerminalRepositoryEntry {
        if (serial <= 0) throw new IllegalArgumentException("Repository serial must be positive");
        displayStack = displayStack == null ? ItemStack.EMPTY : displayStack.copy();
        if (displayStack.isEmpty() && key != null) {
            throw new IllegalArgumentException("Removed repository entries cannot carry a key");
        }
    }

    boolean removed() {
        return displayStack.isEmpty();
    }
}
