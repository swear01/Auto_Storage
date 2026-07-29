package com.swearprom.magicstorage.magic_storage;

import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.Objects;

final class MachineWorkAccumulator {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private MachineWorkAccumulator() {
    }

    static Advance advance(
            Remainder previous,
            ResourceLocation variantItemId,
            MachineWorkRate rate,
            int installedCount
    ) {
        Objects.requireNonNull(variantItemId, "variantItemId");
        Objects.requireNonNull(rate, "rate");
        if (rate.isZero() || installedCount <= 0) {
            throw new IllegalArgumentException("Machine work generation requires a positive rate and count");
        }
        long carried = previous != null
                && previous.variantItemId().equals(variantItemId)
                && previous.rate().equals(rate)
                ? previous.remainder() : 0;
        try {
            long total = Math.addExact(
                    Math.multiplyExact(rate.numerator(), installedCount), carried);
            long whole = total / rate.denominator();
            long remainder = total % rate.denominator();
            return new Advance(whole, new Remainder(variantItemId, rate, remainder));
        } catch (ArithmeticException exception) {
            BigInteger total = BigInteger.valueOf(rate.numerator())
                    .multiply(BigInteger.valueOf(installedCount))
                    .add(BigInteger.valueOf(carried));
            BigInteger[] division = total.divideAndRemainder(BigInteger.valueOf(rate.denominator()));
            if (division[0].compareTo(LONG_MAX) > 0) {
                return new Advance(Long.MAX_VALUE, new Remainder(variantItemId, rate, 0));
            }
            return new Advance(
                    division[0].longValueExact(),
                    new Remainder(variantItemId, rate, division[1].longValueExact()));
        }
    }

    record Remainder(ResourceLocation variantItemId, MachineWorkRate rate, long remainder) {
        Remainder {
            Objects.requireNonNull(variantItemId, "variantItemId");
            Objects.requireNonNull(rate, "rate");
            if (rate.isZero() || remainder < 0 || remainder >= rate.denominator()) {
                throw new IllegalArgumentException("Invalid machine work remainder");
            }
        }
    }

    record Advance(long wholeWork, Remainder remainder) {
        Advance {
            if (wholeWork < 0) throw new IllegalArgumentException("Machine work cannot be negative");
            Objects.requireNonNull(remainder, "remainder");
        }
    }
}
