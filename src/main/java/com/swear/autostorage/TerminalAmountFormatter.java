package com.swear.autostorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

final class TerminalAmountFormatter {
    private static final long[] DIVISORS = {
            1_000L,
            1_000_000L,
            1_000_000_000L,
            1_000_000_000_000L,
            1_000_000_000_000_000L,
            1_000_000_000_000_000_000L
    };
    private static final String[] UNITS = {"K", "M", "G", "T", "P", "E"};
    private static final List<String> SCALE_SAMPLES = scaleSamples();

    private TerminalAmountFormatter() {
    }

    static String formatCompact(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be non-negative");
        if (amount < DIVISORS[0]) return Long.toString(amount);

        int unit = 0;
        while (unit + 1 < DIVISORS.length && amount >= DIVISORS[unit + 1]) unit++;
        long divisor = DIVISORS[unit];
        long whole = amount / divisor;
        if (whole >= 100) return whole + UNITS[unit];

        long tenths = amount / (divisor / 10);
        long fraction = tenths % 10;
        return fraction == 0
                ? (tenths / 10) + UNITS[unit]
                : (tenths / 10) + "." + fraction + UNITS[unit];
    }

    static float scaleForSlot(ToIntFunction<String> width, int slotBound) {
        Objects.requireNonNull(width, "width");
        if (slotBound <= 0) throw new IllegalArgumentException("Slot bound must be positive");
        int widest = SCALE_SAMPLES.stream().mapToInt(width).max().orElseThrow();
        if (widest <= 0) throw new IllegalArgumentException("Measured text width must be positive");
        return Math.min(1.0F, (float) slotBound / widest);
    }

    private static List<String> scaleSamples() {
        List<String> samples = new ArrayList<>();
        samples.add("999");
        for (String unit : UNITS) {
            samples.add("999" + unit);
            samples.add("99.9" + unit);
        }
        return List.copyOf(samples);
    }
}
