package com.swear.autostorage;

public enum RecipePresentationKind {
    NONE,
    CRAFTING,
    COOKING,
    STONECUTTING,
    SMITHING,
    AXE;

    static RecipePresentationKind fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Invalid recipe presentation kind: " + ordinal);
        }
        return values()[ordinal];
    }
}
