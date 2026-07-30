package com.swear.autostorage;

import java.util.Objects;

public record StorageTypeCapacity(int finiteTypeSlots, boolean unlimited) {

    private static final StorageTypeCapacity ZERO = new StorageTypeCapacity(0, false);
    private static final StorageTypeCapacity UNLIMITED = new StorageTypeCapacity(0, true);

    public StorageTypeCapacity {
        if (finiteTypeSlots < 0) {
            throw new IllegalArgumentException("Finite type capacity cannot be negative");
        }
    }

    public static StorageTypeCapacity zero() {
        return ZERO;
    }

    public static StorageTypeCapacity finite(int typeSlots) {
        return typeSlots == 0 ? ZERO : new StorageTypeCapacity(typeSlots, false);
    }

    public static StorageTypeCapacity unlimitedCapacity() {
        return UNLIMITED;
    }

    public StorageTypeCapacity plus(StorageTypeCapacity other) {
        Objects.requireNonNull(other);
        return new StorageTypeCapacity(
                Math.addExact(finiteTypeSlots, other.finiteTypeSlots),
                unlimited || other.unlimited);
    }

    public boolean canAcceptNewType(int currentTypeCount) {
        return unlimited || currentTypeCount < finiteTypeSlots;
    }
}
