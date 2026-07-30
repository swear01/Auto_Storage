package com.swear.autostorage;

public enum SortMode {
    NAME,
    QUANTITY,
    MOD,
    ID;

    public SortMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public SortMode previous() {
        return values()[(ordinal() - 1 + values().length) % values().length];
    }
}
