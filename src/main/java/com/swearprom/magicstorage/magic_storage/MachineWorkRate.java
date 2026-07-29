package com.swearprom.magicstorage.magic_storage;

public record MachineWorkRate(long numerator, long denominator) {
    public static final MachineWorkRate ZERO = new MachineWorkRate(0, 1);
    public static final MachineWorkRate ONE = new MachineWorkRate(1, 1);

    public MachineWorkRate {
        if (numerator < 0 || denominator <= 0) {
            throw new IllegalArgumentException("Machine work rates require a non-negative numerator and positive denominator");
        }
        if (numerator == 0) {
            denominator = 1;
        } else {
            long divisor = greatestCommonDivisor(numerator, denominator);
            numerator /= divisor;
            denominator /= divisor;
        }
    }

    public static MachineWorkRate of(long numerator, long denominator) {
        return new MachineWorkRate(numerator, denominator);
    }

    public boolean isZero() {
        return numerator == 0;
    }

    private static long greatestCommonDivisor(long left, long right) {
        while (right != 0) {
            long remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }
}
