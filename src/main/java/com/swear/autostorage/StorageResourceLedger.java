package com.swear.autostorage;

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
    private static final int SCHEMA = 2;
    private static final int LEGACY_SCHEMA = 1;
    private static final String TAG_SCHEMA = "schema";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_KIND = "kind";
    private static final String TAG_RESOURCE = "resource";
    private static final String TAG_VARIANT = "variant";
    private static final String TAG_AMOUNT = "amount";
    private static final String TAG_PENDING_NUMERATOR = "pending_n";
    private static final String TAG_PENDING_DENOMINATOR = "pending_d";

    private final Map<StorageResourceKey, Entry> entries = new HashMap<>();
    private final Map<ResourceLocation, Integer> typeCountsByKind = new HashMap<>();

    long amount(StorageResourceKey key) {
        Entry entry = entries.get(Objects.requireNonNull(key, "key"));
        return entry == null ? 0L : entry.whole;
    }

    ExactRational pending(StorageResourceKey key) {
        Entry entry = entries.get(Objects.requireNonNull(key, "key"));
        return entry == null ? ExactRational.ZERO : entry.pending;
    }

    boolean occupies(StorageResourceKey key) {
        return entries.containsKey(Objects.requireNonNull(key, "key"));
    }

    int typeCount() {
        return entries.size();
    }

    int typeCount(Predicate<StorageResourceKey> include) {
        return (int) entries.keySet().stream().filter(include).count();
    }

    int typeCount(ResourceLocation kindId) {
        return typeCountsByKind.getOrDefault(Objects.requireNonNull(kindId, "kindId"), 0);
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    List<StorageResourceKey> keys(ResourceLocation kindId) {
        Objects.requireNonNull(kindId, "kindId");
        return entries.keySet().stream()
                .filter(key -> key.kindId().equals(kindId))
                .sorted()
                .toList();
    }

    Map<StorageResourceKey, Long> snapshot() {
        Map<StorageResourceKey, Long> snapshot = new LinkedHashMap<>();
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue().whole));
        return Map.copyOf(snapshot);
    }

    Map<StorageResourceKey, ExactRational> pendingSnapshot() {
        Map<StorageResourceKey, ExactRational> snapshot = new LinkedHashMap<>();
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !entry.getValue().pending.isZero())
                .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue().pending));
        return Map.copyOf(snapshot);
    }

    void forEach(BiConsumer<StorageResourceKey, Long> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        entries.forEach((key, entry) -> consumer.accept(key, entry.whole));
    }

    long totalAmount(ResourceLocation kindId) {
        long total = 0;
        for (Map.Entry<StorageResourceKey, Entry> entry : entries.entrySet()) {
            if (!entry.getKey().kindId().equals(kindId)) continue;
            total = total > Long.MAX_VALUE - entry.getValue().whole
                    ? Long.MAX_VALUE : total + entry.getValue().whole;
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
        if (!occupies(key) && !capacity.canAcceptNewType(typeCount())) return 0;
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
        return apply(deltas, Map.of(), capacity, action);
    }

    boolean applyExpectedCredits(
            Map<StorageResourceKey, ExactRational> credits,
            StorageTypeCapacity capacity,
            Action action
    ) {
        return apply(Map.of(), credits, Map.of(), capacity, action);
    }

    boolean applyExpectedDebits(
            Map<StorageResourceKey, ExactRational> debits,
            StorageTypeCapacity capacity,
            Action action
    ) {
        return apply(Map.of(), Map.of(), debits, capacity, action);
    }

    boolean apply(
            Map<StorageResourceKey, Long> deltas,
            Map<StorageResourceKey, ExactRational> expectedCredits,
            StorageTypeCapacity capacity,
            Action action
    ) {
        return apply(deltas, expectedCredits, Map.of(), capacity, action);
    }

    boolean apply(
            Map<StorageResourceKey, Long> deltas,
            Map<StorageResourceKey, ExactRational> expectedCredits,
            Map<StorageResourceKey, ExactRational> expectedDebits,
            StorageTypeCapacity capacity,
            Action action
    ) {
        Objects.requireNonNull(deltas, "deltas");
        Objects.requireNonNull(expectedCredits, "expectedCredits");
        Objects.requireNonNull(expectedDebits, "expectedDebits");
        Objects.requireNonNull(capacity, "capacity");
        Objects.requireNonNull(action, "action");
        if (deltas.isEmpty() && expectedCredits.isEmpty() && expectedDebits.isEmpty()) return false;
        Map<StorageResourceKey, Entry> updates = new HashMap<>();
        int projectedTypeCount = entries.size();
        for (Map.Entry<StorageResourceKey, Long> entry : deltas.entrySet()) {
            StorageResourceKey key = Objects.requireNonNull(entry.getKey(), "delta key");
            Long boxedDelta = Objects.requireNonNull(entry.getValue(), "delta amount");
            long delta = boxedDelta;
            if (delta == 0) return false;
            Entry existing = updates.containsKey(key)
                    ? updates.get(key)
                    : entries.getOrDefault(key, Entry.empty());
            long updatedWhole;
            try {
                updatedWhole = Math.addExact(existing.whole, delta);
            } catch (ArithmeticException exception) {
                return false;
            }
            if (updatedWhole < 0) return false;
            Entry projected = existing.withWhole(updatedWhole);
            ExactRational credit = expectedCredits.get(key);
            ExactRational debit = expectedDebits.get(key);
            if (credit != null) {
                projected = projected.credit(credit);
                if (projected == null) return false;
            }
            if (debit != null) {
                projected = projected.debit(debit);
                if (projected == null) return false;
            }
            if (!existing.occupies() && projected.occupies()) projectedTypeCount++;
            if (existing.occupies() && !projected.occupies()) projectedTypeCount--;
            updates.put(key, projected);
        }
        for (Map.Entry<StorageResourceKey, ExactRational> entry : expectedCredits.entrySet()) {
            StorageResourceKey key = Objects.requireNonNull(entry.getKey(), "credit key");
            ExactRational credit = Objects.requireNonNull(entry.getValue(), "credit amount");
            if (credit.isZero()) return false;
            if (updates.containsKey(key)) continue;
            Entry existing = entries.getOrDefault(key, Entry.empty());
            Entry projected = existing.credit(credit);
            if (projected == null) return false;
            ExactRational debit = expectedDebits.get(key);
            if (debit != null) {
                projected = projected.debit(debit);
                if (projected == null) return false;
            }
            if (!existing.occupies() && projected.occupies()) projectedTypeCount++;
            if (existing.occupies() && !projected.occupies()) projectedTypeCount--;
            updates.put(key, projected);
        }
        for (Map.Entry<StorageResourceKey, ExactRational> entry : expectedDebits.entrySet()) {
            StorageResourceKey key = Objects.requireNonNull(entry.getKey(), "debit key");
            ExactRational debit = Objects.requireNonNull(entry.getValue(), "debit amount");
            if (debit.isZero()) return false;
            if (updates.containsKey(key)) continue;
            Entry existing = entries.getOrDefault(key, Entry.empty());
            Entry projected = existing.debit(debit);
            if (projected == null) return false;
            if (!existing.occupies() && projected.occupies()) projectedTypeCount++;
            if (existing.occupies() && !projected.occupies()) projectedTypeCount--;
            updates.put(key, projected);
        }
        if (!capacity.unlimited() && projectedTypeCount > capacity.finiteTypeSlots()) return false;
        if (action == Action.EXECUTE) {
            for (Map.Entry<StorageResourceKey, Entry> update : updates.entrySet()) {
                StorageResourceKey key = update.getKey();
                Entry existing = entries.getOrDefault(key, Entry.empty());
                Entry projected = update.getValue();
                if (!projected.occupies()) {
                    entries.remove(key);
                    if (existing.occupies()) {
                        typeCountsByKind.computeIfPresent(key.kindId(), (ignored, count) ->
                                count == 1 ? null : count - 1);
                    }
                } else {
                    entries.put(key, projected);
                    if (!existing.occupies()) {
                        typeCountsByKind.merge(key.kindId(), 1, Integer::sum);
                    }
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
        ListTag list = new ListTag();
        entries.entrySet().stream()
                .filter(entry -> include.test(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag encoded = new CompoundTag();
                    encoded.putString(TAG_KIND, entry.getKey().kindId().toString());
                    encoded.putString(TAG_RESOURCE, entry.getKey().resourceId().toString());
                    encoded.put(TAG_VARIANT, entry.getKey().variantData());
                    encoded.putLong(TAG_AMOUNT, entry.getValue().whole);
                    if (!entry.getValue().pending.isZero()) {
                        encoded.putLong(TAG_PENDING_NUMERATOR, entry.getValue().pending.numerator());
                        encoded.putLong(
                                TAG_PENDING_DENOMINATOR, entry.getValue().pending.denominator());
                    }
                    list.add(encoded);
                });
        root.put(TAG_ENTRIES, list);
        return root;
    }

    static StorageResourceLedger load(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        if (!root.contains(TAG_SCHEMA, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Unsupported typed resource ledger schema");
        }
        int schema = root.getInt(TAG_SCHEMA);
        if (schema != SCHEMA && schema != LEGACY_SCHEMA) {
            throw new IllegalArgumentException("Unsupported typed resource ledger schema");
        }
        Tag rawEntries = root.get(TAG_ENTRIES);
        if (!(rawEntries instanceof ListTag list)
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Typed resource ledger entries are not compounds");
        }
        StorageResourceLedger ledger = new StorageResourceLedger();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.contains(TAG_KIND, Tag.TAG_STRING)
                    || !entry.contains(TAG_RESOURCE, Tag.TAG_STRING)
                    || !entry.contains(TAG_VARIANT, Tag.TAG_COMPOUND)
                    || !entry.contains(TAG_AMOUNT, Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Typed resource ledger entry is incomplete");
            }
            ResourceLocation kindId = ResourceLocation.tryParse(entry.getString(TAG_KIND));
            ResourceLocation resourceId = ResourceLocation.tryParse(entry.getString(TAG_RESOURCE));
            long amount = entry.getLong(TAG_AMOUNT);
            ExactRational pending = ExactRational.ZERO;
            if (schema == SCHEMA && entry.contains(TAG_PENDING_NUMERATOR, Tag.TAG_LONG)) {
                if (!entry.contains(TAG_PENDING_DENOMINATOR, Tag.TAG_LONG)) {
                    throw new IllegalArgumentException("Typed resource ledger pending is incomplete");
                }
                long pendingNumerator = entry.getLong(TAG_PENDING_NUMERATOR);
                long pendingDenominator = entry.getLong(TAG_PENDING_DENOMINATOR);
                if (pendingNumerator < 0
                        || pendingDenominator <= 0
                        || pendingNumerator >= pendingDenominator) {
                    throw new IllegalArgumentException("Typed resource ledger pending is invalid");
                }
                pending = ExactRational.of(pendingNumerator, pendingDenominator);
            } else if (schema == SCHEMA
                    && entry.contains(TAG_PENDING_DENOMINATOR, Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Typed resource ledger pending is incomplete");
            }
            if (kindId == null || resourceId == null || amount < 0) {
                throw new IllegalArgumentException("Typed resource ledger entry is invalid");
            }
            if (amount == 0 && pending.isZero()) {
                throw new IllegalArgumentException("Typed resource ledger entry is empty");
            }
            StorageResourceKey key = StorageResourceKey.of(
                    kindId, resourceId, entry.getCompound(TAG_VARIANT));
            if (ledger.entries.putIfAbsent(key, new Entry(amount, pending)) != null) {
                throw new IllegalArgumentException("Duplicate typed resource ledger key " + key);
            }
            ledger.typeCountsByKind.merge(kindId, 1, Integer::sum);
        }
        return ledger;
    }

    private record Entry(long whole, ExactRational pending) {
        private Entry {
            Objects.requireNonNull(pending, "pending");
            if (whole < 0) {
                throw new IllegalArgumentException("Ledger whole amount cannot be negative");
            }
            if (pending.numerator() >= pending.denominator() && !pending.isZero()) {
                throw new IllegalArgumentException("Ledger pending must be in [0, 1)");
            }
            if (whole == 0 && pending.isZero()) {
                // empty sentinel allowed only before put
            }
        }

        static Entry empty() {
            return new Entry(0, ExactRational.ZERO);
        }

        boolean occupies() {
            return whole > 0 || !pending.isZero();
        }

        Entry withWhole(long updatedWhole) {
            if (updatedWhole == 0 && pending.isZero()) {
                return empty();
            }
            return new Entry(updatedWhole, pending);
        }

        Entry credit(ExactRational credit) {
            Objects.requireNonNull(credit, "credit");
            if (credit.isZero()) return null;
            ExactRational total;
            try {
                total = ExactRational.whole(whole).add(pending).add(credit);
            } catch (ArithmeticException exception) {
                return null;
            }
            long updatedWhole = total.floor();
            ExactRational updatedPending = total.fractionalPart();
            if (updatedWhole == 0 && updatedPending.isZero()) {
                return empty();
            }
            return new Entry(updatedWhole, updatedPending);
        }

        Entry debit(ExactRational debit) {
            Objects.requireNonNull(debit, "debit");
            if (debit.isZero()) return null;
            ExactRational total;
            try {
                total = ExactRational.whole(whole).add(pending).subtract(debit);
            } catch (ArithmeticException exception) {
                return null;
            }
            long updatedWhole = total.floor();
            ExactRational updatedPending = total.fractionalPart();
            if (updatedWhole == 0 && updatedPending.isZero()) {
                return empty();
            }
            return new Entry(updatedWhole, updatedPending);
        }
    }
}
