package com.swear.autostorage;

import java.util.Objects;

public record TypedRecipeOutput(StorageResourceKey key, ExactRational expected, Role role) {
    public enum Role {
        PRIMARY,
        REMAINDER
    }

    public TypedRecipeOutput {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(role, "role");
        if (expected.isZero()) {
            throw new IllegalArgumentException("Typed recipe output expected amount must be positive");
        }
    }

    public static TypedRecipeOutput primary(StorageResourceKey key, long amount) {
        return new TypedRecipeOutput(key, ExactRational.whole(amount), Role.PRIMARY);
    }

    public static TypedRecipeOutput primary(StorageResourceKey key, ExactRational expected) {
        return new TypedRecipeOutput(key, expected, Role.PRIMARY);
    }

    public static TypedRecipeOutput remainder(StorageResourceKey key, long amount) {
        return new TypedRecipeOutput(key, ExactRational.whole(amount), Role.REMAINDER);
    }

    public static TypedRecipeOutput remainder(StorageResourceKey key, ExactRational expected) {
        return new TypedRecipeOutput(key, expected, Role.REMAINDER);
    }

    /**
     * Whole per-craft amount for callers that require an integer output.
     * Fractional expected-value outputs must use {@link #expected()} instead.
     */
    public long amount() {
        if (!expected.isWhole()) {
            throw new IllegalStateException(
                    "Typed recipe output amount requires a whole expected value");
        }
        return expected.numerator();
    }

    public long displayAmount() {
        return expected.floor();
    }
}
