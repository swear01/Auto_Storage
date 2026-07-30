package com.swear.autostorage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class StorageResourceTransaction {
    private final Map<StorageResourceKey, Long> deltas;

    private StorageResourceTransaction(Map<StorageResourceKey, Long> deltas) {
        if (deltas.isEmpty()) {
            throw new IllegalArgumentException("Storage resource transaction cannot be empty");
        }
        this.deltas = Map.copyOf(deltas);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<StorageResourceKey, Long> deltas() {
        return deltas;
    }

    public static final class Builder {
        private final Map<StorageResourceKey, Long> deltas = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder add(StorageResourceKey key, long delta) {
            Objects.requireNonNull(key, "key");
            if (delta == 0) {
                throw new IllegalArgumentException("Storage resource delta cannot be zero");
            }
            long merged = Math.addExact(deltas.getOrDefault(key, 0L), delta);
            if (merged == 0) deltas.remove(key);
            else deltas.put(key, merged);
            return this;
        }

        public StorageResourceTransaction build() {
            return new StorageResourceTransaction(deltas);
        }
    }
}
