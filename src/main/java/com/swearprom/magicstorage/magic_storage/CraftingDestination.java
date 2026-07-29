package com.swearprom.magicstorage.magic_storage;

public enum CraftingDestination {
    NONE,
    CURSOR,
    INVENTORY,
    STORAGE;

    static CraftingDestination byId(int id) {
        if (id < 0 || id >= values().length) {
            throw new IllegalArgumentException("Unknown crafting destination " + id);
        }
        return values()[id];
    }
}
