package com.swearprom.magicstorage.magic_storage;

public enum CraftingTerminalPage {
    STORAGE,
    CRAFTABLE,
    TRANSFORM,
    STATIONS,
    FUEL;

    public boolean isItemPage() {
        return this == STORAGE || this == CRAFTABLE;
    }

    public CraftingTerminalPage normalized() {
        return this == FUEL ? TRANSFORM : this;
    }

    static CraftingTerminalPage fromOrdinal(int ordinal) {
        return (ordinal >= 0 && ordinal < values().length ? values()[ordinal] : STORAGE)
                .normalized();
    }
}
