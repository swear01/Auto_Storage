package com.swearprom.magicstorage.magic_storage;

import net.minecraft.resources.ResourceLocation;

public enum TerminalResourceView {
    ITEM,
    FLUID,
    ENERGY,
    GAS,
    STATION_WORK,
    OTHER;

    public TerminalResourceView next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public TerminalResourceView previous() {
        return values()[(ordinal() - 1 + values().length) % values().length];
    }

    public boolean isAvailable() {
        return switch (this) {
            case ITEM -> StorageResourceKinds.isKindAvailable(StorageResourceKindApi.ITEM_KIND);
            case FLUID -> StorageResourceKinds.isKindAvailable(StorageResourceKindApi.FLUID_KIND);
            case ENERGY -> StorageResourceKinds.isKindAvailable(StorageResourceKindApi.ENERGY_KIND);
            case GAS -> StorageResourceKinds.isChemicalKindAvailable();
            case STATION_WORK ->
                    StorageResourceKinds.isKindAvailable(StorageResourceKindApi.WORK_KIND);
            case OTHER -> StorageResourceKinds.hasOtherKind();
        };
    }

    public TerminalResourceView nextAvailable() {
        TerminalResourceView candidate = this;
        for (int step = 0; step < values().length; step++) {
            candidate = candidate.next();
            if (candidate.isAvailable()) return candidate;
        }
        return ITEM;
    }

    public TerminalResourceView previousAvailable() {
        TerminalResourceView candidate = this;
        for (int step = 0; step < values().length; step++) {
            candidate = candidate.previous();
            if (candidate.isAvailable()) return candidate;
        }
        return ITEM;
    }

    public TerminalResourceView availableOrItem() {
        return isAvailable() ? this : ITEM;
    }

    public boolean matches(StorageResourceKey key) {
        ResourceLocation kind = key.kindId();
        return switch (this) {
            case ITEM -> kind.equals(StorageResourceKindApi.ITEM_KIND);
            case FLUID -> kind.equals(StorageResourceKindApi.FLUID_KIND);
            case ENERGY -> kind.equals(StorageResourceKindApi.ENERGY_KIND)
                    || kind.equals(StorageResourceKindApi.WORK_KIND)
                    && StorageResourceBridge.stationWorkDescriptorId(key).isEmpty();
            case GAS -> StorageResourceKinds.isChemicalKindId(kind);
            case STATION_WORK ->
                    StorageResourceBridge.stationWorkDescriptorId(key).isPresent();
            case OTHER -> !StorageResourceKinds.isBuiltInKindId(kind);
        };
    }

    public static TerminalResourceView byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : ITEM;
    }

    static TerminalResourceView requireById(int id) {
        if (id < 0 || id >= values().length) {
            throw new IllegalArgumentException("Unknown terminal resource view " + id);
        }
        return values()[id];
    }

}
