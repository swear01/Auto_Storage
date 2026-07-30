package com.swear.autostorage;

import java.util.Objects;

public record TypedRecipeOutput(StorageResourceKey key, long amount, Role role) {
    public enum Role {
        PRIMARY,
        REMAINDER
    }

    public TypedRecipeOutput {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(role, "role");
        if (amount <= 0) throw new IllegalArgumentException("Typed recipe output amount must be positive");
    }

    public static TypedRecipeOutput primary(StorageResourceKey key, long amount) {
        return new TypedRecipeOutput(key, amount, Role.PRIMARY);
    }

    public static TypedRecipeOutput remainder(StorageResourceKey key, long amount) {
        return new TypedRecipeOutput(key, amount, Role.REMAINDER);
    }
}
