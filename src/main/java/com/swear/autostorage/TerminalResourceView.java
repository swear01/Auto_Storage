package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;

public enum TerminalResourceView {
    ITEM,
    FLUID,
    ENERGY,
    GAS,
    STATION_WORK,
    OTHER,
    ALL;

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
            case ALL -> true;
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
        return this == ALL || this == classify(key);
    }

    static TerminalResourceView classify(StorageResourceKey key) {
        ResourceLocation kind = key.kindId();
        if (kind.equals(StorageResourceKindApi.ITEM_KIND)) return ITEM;
        if (kind.equals(StorageResourceKindApi.FLUID_KIND)) return FLUID;
        if (StorageResourceKinds.isEnergyKindId(kind)
                || StorageResourceBridge.energyType(key).isPresent()) {
            return ENERGY;
        }
        if (StorageResourceKinds.isChemicalKindId(kind)) return GAS;
        if (StorageResourceBridge.stationWorkDescriptorId(key).isPresent()) {
            return STATION_WORK;
        }
        return OTHER;
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
