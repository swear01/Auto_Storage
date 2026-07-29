package com.swearprom.magicstorage.magic_storage;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class MachineRateFormatter {
    private MachineRateFormatter() {
    }

    static String format(MachineWorkRate rate, int installedCount) {
        if (rate.isZero() || installedCount <= 0) return "0.00";
        BigDecimal value = BigDecimal.valueOf(rate.numerator())
                .multiply(BigDecimal.valueOf(installedCount))
                .divide(BigDecimal.valueOf(rate.denominator()), 8, RoundingMode.HALF_UP);
        if (value.compareTo(new BigDecimal("0.01")) < 0) return "<0.01";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
