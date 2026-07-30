package com.swear.autostorage;

import java.util.Locale;
import java.util.Optional;

public enum BusFilterMode {
    ALLOW,
    DENY;

    String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<BusFilterMode> parse(String value) {
        for (BusFilterMode mode : values()) {
            if (mode.serializedName().equals(value)) return Optional.of(mode);
        }
        return Optional.empty();
    }
}
