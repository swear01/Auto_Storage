package com.swearprom.magicstorage.magic_storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.BiConsumer;

final class StorageResourceLedger {
    private static final int SCHEMA = 1;
    private static final String TAG_SCHEMA = "schema";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_KIND = "kind";
    private static final String TAG_RESOURCE = "resource";
    private static final String TAG_VARIANT = "variant";
    private static final String TAG_AMOUNT = "amount";

    private final Map<StorageResourceKey, Long> amounts = new HashMap<>();
    private final Map<ResourceLocation, Integer> typeCountsByKind = new HashMap<>();

    long amount(StorageResourceKey key) {
        return amounts.getOrDefault(Objects.requireNonNull(key, "key"), 0L);
    }

    int typeCount() {
        return amounts.size();
    }

    int typeCount(Predicate<StorageResourceKey> include) {
        return (int) amounts.keySet().stream().filter(include).count();
    }

    int typeCount(ResourceLocation kindId) {
        return typeCountsByKind.getOrDefault(Objects.requireNonNull(kindId, "kindId"), 0);
    }

    boolean isEmpty() {
        return amounts.isEmpty();
    }

    List<StorageResourceKey> keys(ResourceLocation kindId) {
        Objects.requireNonNull(kindId, "kindId");
        return amounts.keySet().stream()
                .filter(key -> key.kindId().equals(kindId))
                .sorted()
                .toList();
    }

    Map<StorageResourceKey, Long> snapshot() {
        Map<StorageResourceKey, Long> snapshot = new LinkedHashMap<>();
        amounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(snapshot);
    }

    void forEach(BiConsumer<StorageResourceKey, Long> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        amounts.forEach(consumer);
    }

    long totalAmount(ResourceLocation kindId) {
        long total = 0;
        for (Map.Entry<StorageResourceKey, Long> entry : amounts.entrySet()) {
            if (!entry.getKey().kindId().equals(kindId)) continue;
            total = total > Long.MAX_VALUE - entry.getValue()
                    ? Long.MAX_VALUE : total + entry.getValue();
        }
        return total;
    }

    long insert(
            StorageResourceKey key,
            long requested,
            StorageTypeCapacity capacity,
            Action action
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(action, "action");
        if (requested <= 0) {
            throw new IllegalArgumentException("Requested insertion must be positive");
        }
        long existing = amount(key);
        if (existing == 0 && !capacity.canAcceptNewType(typeCount())) return 0;
        long accepted = Math.min(requested, Long.MAX_VALUE - existing);
        if (accepted <= 0) return 0;
        if (!applyExact(Map.of(key, accepted), capacity, action)) {
            throw new IllegalStateException("Bounded resource insertion failed validation");
        }
        return accepted;
    }

    long extract(StorageResourceKey key, long requested, Action action) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");
        if (requested <= 0) {
            throw new IllegalArgumentException("Requested extraction must be positive");
        }
        long extracted = Math.min(requested, amount(key));
        if (extracted <= 0) return 0;
        if (!applyExact(Map.of(key, -extracted), StorageTypeCapacity.unlimitedCapacity(), action)) {
            throw new IllegalStateException("Bounded resource extraction failed validation");
        }
        return extracted;
    }

    boolean applyExact(
            Map<StorageResourceKey, Long> deltas,
            StorageTypeCapacity capacity,
            Action action
    ) {
        Objects.requireNonNull(deltas, "deltas");
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(action, "action");
        Map<StorageResourceKey, Long> updates = new HashMap<>();
        int projectedTypeCount = amounts.size();
        for (Map.Entry<StorageResourceKey, Long> entry : deltas.entrySet()) {
            StorageResourceKey key = Objects.requireNonNull(entry.getKey(), "delta key");
            Long boxedDelta = Objects.requireNonNull(entry.getValue(), "delta amount");
            long delta = boxedDelta;
            if (delta == 0) return false;
            long existing = amounts.getOrDefault(key, 0L);
            long updated;
            try {
                updated = Math.addExact(existing, delta);
            } catch (ArithmeticException exception) {
                return false;
            }
            if (updated < 0) return false;
            if (existing == 0 && updated > 0) projectedTypeCount++;
            if (existing > 0 && updated == 0) projectedTypeCount--;
            updates.put(key, updated);
        }
        if (!capacity.unlimited() && projectedTypeCount > capacity.finiteTypeSlots()) return false;
        if (action == Action.EXECUTE) {
            for (Map.Entry<StorageResourceKey, Long> update : updates.entrySet()) {
                StorageResourceKey key = update.getKey();
                long existing = amounts.getOrDefault(key, 0L);
                if (update.getValue() == 0) {
                    amounts.remove(key);
                    typeCountsByKind.computeIfPresent(key.kindId(), (ignored, count) ->
                            count == 1 ? null : count - 1);
                } else {
                    amounts.put(key, update.getValue());
                    if (existing == 0) typeCountsByKind.merge(key.kindId(), 1, Integer::sum);
                }
            }
        }
        return true;
    }

    CompoundTag save() {
        return save(key -> true);
    }

    CompoundTag save(Predicate<StorageResourceKey> include) {
        Objects.requireNonNull(include, "include");
        CompoundTag root = new CompoundTag();
        root.putInt(TAG_SCHEMA, SCHEMA);
        ListTag entries = new ListTag();
        amounts.entrySet().stream()
                .filter(entry -> include.test(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag encoded = new CompoundTag();
                    encoded.putString(TAG_KIND, entry.getKey().kindId().toString());
                    encoded.putString(TAG_RESOURCE, entry.getKey().resourceId().toString());
                    encoded.put(TAG_VARIANT, entry.getKey().variantData());
                    encoded.putLong(TAG_AMOUNT, entry.getValue());
                    entries.add(encoded);
                });
        root.put(TAG_ENTRIES, entries);
        return root;
    }

    static StorageResourceLedger load(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        if (!root.contains(TAG_SCHEMA, Tag.TAG_INT) || root.getInt(TAG_SCHEMA) != SCHEMA) {
            throw new IllegalArgumentException("Unsupported typed resource ledger schema");
        }
        Tag rawEntries = root.get(TAG_ENTRIES);
        if (!(rawEntries instanceof ListTag entries)
                || !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Typed resource ledger entries are not compounds");
        }
        StorageResourceLedger ledger = new StorageResourceLedger();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!entry.contains(TAG_KIND, Tag.TAG_STRING)
                    || !entry.contains(TAG_RESOURCE, Tag.TAG_STRING)
                    || !entry.contains(TAG_VARIANT, Tag.TAG_COMPOUND)
                    || !entry.contains(TAG_AMOUNT, Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Typed resource ledger entry is incomplete");
            }
            ResourceLocation kindId = ResourceLocation.tryParse(entry.getString(TAG_KIND));
            ResourceLocation resourceId = ResourceLocation.tryParse(entry.getString(TAG_RESOURCE));
            long amount = entry.getLong(TAG_AMOUNT);
            if (kindId == null || resourceId == null || amount <= 0) {
                throw new IllegalArgumentException("Typed resource ledger entry is invalid");
            }
            StorageResourceKey key = StorageResourceKey.of(
                    kindId, resourceId, entry.getCompound(TAG_VARIANT));
            if (ledger.amounts.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Duplicate typed resource ledger key " + key);
            }
            ledger.typeCountsByKind.merge(kindId, 1, Integer::sum);
        }
        return ledger;
    }
}
