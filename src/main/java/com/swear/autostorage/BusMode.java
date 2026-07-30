package com.swear.autostorage;

import java.util.Locale;
import java.util.Optional;

public enum BusMode {
    DIRECTIONAL,
    DIRECTIONLESS;

    String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<BusMode> parse(String value) {
        for (BusMode mode : values()) {
            if (mode.serializedName().equals(value)) return Optional.of(mode);
        }
        return Optional.empty();
    }
}
