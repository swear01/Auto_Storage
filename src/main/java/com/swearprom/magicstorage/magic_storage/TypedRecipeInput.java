package com.swearprom.magicstorage.magic_storage;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record TypedRecipeInput(
        List<StorageResourceKey> alternatives,
        long amount,
        Role role,
        Map<StorageResourceKey, TypedRecipeOutput> alternativeRemainders
) {
    public enum Role {
        CONSUME,
        CATALYST,
        TOOL
    }

    public TypedRecipeInput {
        Objects.requireNonNull(alternatives, "alternatives");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(alternativeRemainders, "alternativeRemainders");
        alternatives = List.copyOf(alternatives);
        if (alternatives.isEmpty() || alternatives.stream().anyMatch(Objects::isNull)
                || new HashSet<>(alternatives).size() != alternatives.size()) {
            throw new IllegalArgumentException(
                    "Typed recipe input requires unique resource alternatives");
        }
        if (amount <= 0) throw new IllegalArgumentException("Typed recipe input amount must be positive");
        alternativeRemainders = java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(alternativeRemainders));
        if (!alternatives.containsAll(alternativeRemainders.keySet())) {
            throw new IllegalArgumentException(
                    "Typed recipe input remainders require a matching resource alternative");
        }
        if (role != Role.CONSUME && !alternativeRemainders.isEmpty()) {
            throw new IllegalArgumentException(
                    "Only consumed typed recipe inputs can return remainders");
        }
        for (TypedRecipeOutput remainder : alternativeRemainders.values()) {
            if (remainder == null || remainder.role() != TypedRecipeOutput.Role.REMAINDER) {
                throw new IllegalArgumentException(
                        "Typed recipe input alternatives require remainder outputs");
            }
        }
    }

    public TypedRecipeInput(
            List<StorageResourceKey> alternatives,
            long amount,
            Role role
    ) {
        this(alternatives, amount, role, Map.of());
    }

    public TypedRecipeInput(StorageResourceKey key, long amount, Role role) {
        this(List.of(Objects.requireNonNull(key, "key")), amount, role, Map.of());
    }

    public StorageResourceKey key() {
        return alternatives.getFirst();
    }

    public static TypedRecipeInput consume(StorageResourceKey key, long amount) {
        return new TypedRecipeInput(key, amount, Role.CONSUME);
    }

    public static TypedRecipeInput consumeAny(List<StorageResourceKey> alternatives, long amount) {
        return new TypedRecipeInput(alternatives, amount, Role.CONSUME);
    }

    public static TypedRecipeInput consumeAnyWithRemainders(
            List<StorageResourceKey> alternatives,
            long amount,
            Map<StorageResourceKey, TypedRecipeOutput> alternativeRemainders
    ) {
        return new TypedRecipeInput(
                alternatives, amount, Role.CONSUME, alternativeRemainders);
    }

    public Optional<TypedRecipeOutput> remainderFor(StorageResourceKey alternative) {
        return Optional.ofNullable(alternativeRemainders.get(
                Objects.requireNonNull(alternative, "alternative")));
    }

    public static TypedRecipeInput catalyst(StorageResourceKey key, long amount) {
        return new TypedRecipeInput(key, amount, Role.CATALYST);
    }

    public static TypedRecipeInput catalystAny(List<StorageResourceKey> alternatives, long amount) {
        return new TypedRecipeInput(alternatives, amount, Role.CATALYST);
    }

    public static TypedRecipeInput tool(StorageResourceKey key, long amount) {
        return new TypedRecipeInput(key, amount, Role.TOOL);
    }

    public static TypedRecipeInput toolAny(List<StorageResourceKey> alternatives, long amount) {
        return new TypedRecipeInput(alternatives, amount, Role.TOOL);
    }
}
