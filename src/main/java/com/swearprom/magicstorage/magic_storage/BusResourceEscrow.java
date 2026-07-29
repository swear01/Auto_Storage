package com.swearprom.magicstorage.magic_storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class BusResourceEscrow {
    private static final String TAG_PENDING_RESOURCES = "pendingResources";

    private final StorageResourceLedger ledger;
    private final Tag unsupportedRaw;
    private boolean changed;

    private BusResourceEscrow(StorageResourceLedger ledger, Tag unsupportedRaw) {
        this.ledger = ledger;
        this.unsupportedRaw = unsupportedRaw == null ? null : unsupportedRaw.copy();
    }

    static BusResourceEscrow empty() {
        return new BusResourceEscrow(new StorageResourceLedger(), null);
    }

    static BusResourceEscrow load(CompoundTag root) {
        Tag raw = root.get(TAG_PENDING_RESOURCES);
        if (raw == null) return empty();
        if (!(raw instanceof CompoundTag tag)) {
            MagicStorage.LOGGER.error("Typed Bus escrow is not a compound; preserving raw NBT");
            return new BusResourceEscrow(new StorageResourceLedger(), raw);
        }
        try {
            return new BusResourceEscrow(StorageResourceLedger.load(tag), null);
        } catch (IllegalArgumentException exception) {
            MagicStorage.LOGGER.error("Typed Bus escrow is invalid; preserving raw NBT: {}",
                    exception.getMessage());
            return new BusResourceEscrow(new StorageResourceLedger(), tag);
        }
    }

    void save(CompoundTag root) {
        if (unsupportedRaw != null) {
            root.put(TAG_PENDING_RESOURCES, unsupportedRaw.copy());
        } else if (!ledger.isEmpty()) {
            root.put(TAG_PENDING_RESOURCES, ledger.save());
        }
    }

    boolean supported() {
        return unsupportedRaw == null;
    }

    boolean hasPending() {
        return unsupportedRaw != null || !ledger.isEmpty();
    }

    long amount(StorageResourceKey key) {
        return unsupportedRaw == null ? ledger.amount(key) : 0;
    }

    void add(StorageResourceKey key, long amount) {
        if (amount <= 0) return;
        if (unsupportedRaw != null) {
            throw new IllegalStateException("Cannot add to unsupported typed Bus escrow");
        }
        StorageTypeCapacity capacity = StorageTypeCapacity.unlimitedCapacity();
        if (ledger.insert(key, amount, capacity, Action.SIMULATE) != amount) {
            throw new IllegalStateException("Typed Bus escrow amount overflow");
        }
        long inserted = ledger.insert(key, amount, capacity, Action.EXECUTE);
        if (inserted != amount) throw new IllegalStateException("Typed Bus escrow commit diverged");
        changed = true;
    }

    boolean drainToCore(StorageCoreBlockEntity core, Actor actor) {
        if (unsupportedRaw != null || ledger.isEmpty()) return false;
        for (var entry : ledger.snapshot().entrySet()) {
            long simulated = core.insertResource(
                    entry.getKey(), entry.getValue(), Action.SIMULATE, actor);
            if (simulated <= 0) continue;
            long inserted = core.insertResource(entry.getKey(), simulated, Action.EXECUTE, actor);
            if (inserted <= 0) continue;
            long removed = ledger.extract(entry.getKey(), inserted, Action.EXECUTE);
            if (removed != inserted) {
                throw new IllegalStateException("Typed Bus escrow drain diverged");
            }
            changed = true;
            return true;
        }
        return false;
    }

    boolean takeChanged() {
        boolean result = changed;
        changed = false;
        return result;
    }
}
