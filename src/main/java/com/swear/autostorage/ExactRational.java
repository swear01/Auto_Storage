package com.swear.autostorage;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Non-negative exact rational used for chance expected-value credits and pending stock.
 * Values are always reduced; zero is canonical {@code 0/1}.
 */
public record ExactRational(long numerator, long denominator) {
    public static final ExactRational ZERO = new ExactRational(0, 1);
    public static final ExactRational ONE = new ExactRational(1, 1);
    public static final int CHANCE_BASIS = 10_000;

    public ExactRational {
        if (numerator < 0 || denominator <= 0) {
            throw new IllegalArgumentException(
                    "Exact rationals require a non-negative numerator and positive denominator");
        }
        if (numerator == 0) {
            denominator = 1;
        } else {
            long divisor = greatestCommonDivisor(numerator, denominator);
            numerator /= divisor;
            denominator /= divisor;
        }
    }

    public static ExactRational of(long numerator, long denominator) {
        return new ExactRational(numerator, denominator);
    }

    public static ExactRational whole(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Whole exact rationals cannot be negative");
        }
        return amount == 0 ? ZERO : of(amount, 1);
    }

    /**
     * Converts a unit-interval chance into a reduced rational on {@link #CHANCE_BASIS}.
     * Exact {@code 1.0F} becomes {@link #ONE}. Values outside {@code (0, 1]} or non-finite fail closed.
     */
    public static ExactRational fromUnitInterval(float chance) {
        if (!Float.isFinite(chance) || chance <= 0.0F || chance > 1.0F) {
            throw new IllegalArgumentException("Chance must be a finite value in (0, 1]");
        }
        if (chance == 1.0F) {
            return ONE;
        }
        long scaled = Math.round((double) chance * CHANCE_BASIS);
        if (scaled <= 0 || scaled > CHANCE_BASIS) {
            throw new IllegalArgumentException("Chance could not be represented on basis " + CHANCE_BASIS);
        }
        if (Math.abs((double) chance * CHANCE_BASIS - scaled) > 0.01) {
            throw new IllegalArgumentException("Chance could not be represented on basis " + CHANCE_BASIS);
        }
        return of(scaled, CHANCE_BASIS);
    }

    public boolean isZero() {
        return numerator == 0;
    }

    public boolean isWhole() {
        return denominator == 1;
    }

    public long floor() {
        return numerator / denominator;
    }

    public ExactRational fractionalPart() {
        long remainder = numerator % denominator;
        return remainder == 0 ? ZERO : of(remainder, denominator);
    }

    public ExactRational add(ExactRational other) {
        Objects.requireNonNull(other, "other");
        if (isZero()) return other;
        if (other.isZero()) return this;
        try {
            long left = Math.multiplyExact(numerator, other.denominator);
            long right = Math.multiplyExact(other.numerator, denominator);
            long sum = Math.addExact(left, right);
            long common = Math.multiplyExact(denominator, other.denominator);
            return of(sum, common);
        } catch (ArithmeticException exception) {
            BigInteger sum = BigInteger.valueOf(numerator)
                    .multiply(BigInteger.valueOf(other.denominator))
                    .add(BigInteger.valueOf(other.numerator)
                            .multiply(BigInteger.valueOf(denominator)));
            BigInteger common = BigInteger.valueOf(denominator)
                    .multiply(BigInteger.valueOf(other.denominator));
            return ofExact(sum, common);
        }
    }

    public ExactRational multiply(ExactRational other) {
        Objects.requireNonNull(other, "other");
        if (isZero() || other.isZero()) return ZERO;
        try {
            return of(
                    Math.multiplyExact(numerator, other.numerator),
                    Math.multiplyExact(denominator, other.denominator));
        } catch (ArithmeticException exception) {
            return ofExact(
                    BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(other.numerator)),
                    BigInteger.valueOf(denominator).multiply(BigInteger.valueOf(other.denominator)));
        }
    }

    public ExactRational multiply(long factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Exact rational multiply factor cannot be negative");
        }
        if (factor == 0 || isZero()) return ZERO;
        if (factor == 1) return this;
        try {
            return of(Math.multiplyExact(numerator, factor), denominator);
        } catch (ArithmeticException exception) {
            return ofExact(
                    BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(factor)),
                    BigInteger.valueOf(denominator));
        }
    }

    public int compareTo(ExactRational other) {
        Objects.requireNonNull(other, "other");
        try {
            return Long.compare(
                    Math.multiplyExact(numerator, other.denominator),
                    Math.multiplyExact(other.numerator, denominator));
        } catch (ArithmeticException exception) {
            return BigInteger.valueOf(numerator)
                    .multiply(BigInteger.valueOf(other.denominator))
                    .compareTo(BigInteger.valueOf(other.numerator)
                            .multiply(BigInteger.valueOf(denominator)));
        }
    }

    public ExactRational subtract(ExactRational other) {
        Objects.requireNonNull(other, "other");
        if (other.isZero()) return this;
        if (compareTo(other) < 0) {
            throw new ArithmeticException("Exact rational subtraction underflow");
        }
        if (equals(other)) return ZERO;
        try {
            long left = Math.multiplyExact(numerator, other.denominator);
            long right = Math.multiplyExact(other.numerator, denominator);
            long difference = Math.subtractExact(left, right);
            long common = Math.multiplyExact(denominator, other.denominator);
            return of(difference, common);
        } catch (ArithmeticException exception) {
            BigInteger difference = BigInteger.valueOf(numerator)
                    .multiply(BigInteger.valueOf(other.denominator))
                    .subtract(BigInteger.valueOf(other.numerator)
                            .multiply(BigInteger.valueOf(denominator)));
            BigInteger common = BigInteger.valueOf(denominator)
                    .multiply(BigInteger.valueOf(other.denominator));
            return ofExact(difference, common);
        }
    }

    private static ExactRational ofExact(BigInteger numerator, BigInteger denominator) {
        BigInteger gcd = numerator.gcd(denominator);
        BigInteger reducedNumerator = numerator.divide(gcd);
        BigInteger reducedDenominator = denominator.divide(gcd);
        if (reducedNumerator.bitLength() > 63 || reducedDenominator.bitLength() > 63) {
            throw new ArithmeticException("Exact rational overflowed signed long");
        }
        return of(reducedNumerator.longValueExact(), reducedDenominator.longValueExact());
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
