package com.swearprom.magicstorage.magic_storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum SearchMode {
    OFF(false, false),
    EMI(true, false),
    EMI_TWO_WAY(true, true);

    private final boolean synchronizesToEmi;
    private final boolean synchronizesFromEmi;

    SearchMode(boolean synchronizesToEmi, boolean synchronizesFromEmi) {
        this.synchronizesToEmi = synchronizesToEmi;
        this.synchronizesFromEmi = synchronizesFromEmi;
    }

    public SearchMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public SearchMode previous() {
        return values()[(ordinal() - 1 + values().length) % values().length];
    }

    public boolean synchronizesToEmi() {
        return synchronizesToEmi;
    }

    public boolean synchronizesFromEmi() {
        return synchronizesFromEmi;
    }

    String configValue() {
        return name();
    }

    static SearchMode fromConfigValue(String value) {
        if (value == null) throw new IllegalArgumentException("Search mode config is missing");
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "OFF" -> OFF;
            case "EMI" -> EMI;
            case "EMI_TWO_WAY" -> EMI_TWO_WAY;
            case "AUTO", "NORMAL", "TAG", "MOD" -> OFF;
            default -> throw new IllegalArgumentException(
                    "Unknown search synchronization mode " + value);
        };
    }

    static List<String> configValues() {
        return new ArrayList<>(List.of(
                OFF.name(),
                EMI.name(),
                EMI_TWO_WAY.name(),
                "AUTO",
                "NORMAL",
                "TAG",
                "MOD"));
    }
}
