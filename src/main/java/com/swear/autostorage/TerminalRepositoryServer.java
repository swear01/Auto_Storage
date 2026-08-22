package com.swear.autostorage;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TerminalRepositoryServer {
    private final Map<StorageResourceKey, Long> serials = new HashMap<>();
    private Map<StorageResourceKey, ItemStack> previous = Map.of();
    private long nextSerial = 1;
    private long revision;
    private boolean fullPending = true;

    void requestFull() {
        fullPending = true;
    }

    List<TerminalRepositoryUpdatePacket> update(
            int containerId,
            List<ItemStack> displayStacks,
            RegistryAccess registries
    ) {
        Map<StorageResourceKey, ItemStack> current = index(displayStacks, registries);
        boolean full = fullPending;
        List<TerminalRepositoryEntry> changes = full
                ? fullEntries(current)
                : changedEntries(previous, current);
        if (!full && changes.isEmpty()) return List.of();

        revision++;
        previous = copy(current);
        pruneSerials(current.keySet());
        fullPending = false;
        return chunk(containerId, revision, full, changes, registries);
    }

    List<TerminalRepositoryUpdatePacket> updateChanges(
            int containerId,
            Map<StorageResourceKey, ItemStack> changed,
            java.util.Set<StorageResourceKey> removed,
            RegistryAccess registries
    ) {
        if (changed.isEmpty() && removed.isEmpty()) return List.of();
        Map<StorageResourceKey, ItemStack> next = new LinkedHashMap<>();
        previous.forEach((key, stack) -> next.put(key, stack.copy()));
        List<TerminalRepositoryEntry> entries = new ArrayList<>();
        for (Map.Entry<StorageResourceKey, ItemStack> entry : changed.entrySet()) {
            StorageResourceKey key = entry.getKey();
            ItemStack display = entry.getValue();
            ItemStack old = next.get(key);
            if (display.isEmpty()) {
                if (old != null) {
                    entries.add(new TerminalRepositoryEntry(
                            serialFor(key), null, ItemStack.EMPTY));
                    next.remove(key);
                }
                continue;
            }
            next.put(key, display.copy());
            if (old == null || !ItemStack.isSameItemSameComponents(old, display)) {
                entries.add(new TerminalRepositoryEntry(
                        serialFor(key), old == null ? key : null, display));
            }
        }
        for (StorageResourceKey key : removed) {
            if (changed.containsKey(key)) continue;
            if (next.remove(key) != null) {
                entries.add(new TerminalRepositoryEntry(
                        serialFor(key), null, ItemStack.EMPTY));
            }
        }
        if (entries.isEmpty()) return List.of();
        revision++;
        previous = copy(next);
        pruneSerials(next.keySet());
        return chunk(containerId, revision, false, entries, registries);
    }

    List<TerminalRepositoryUpdatePacket> fullSnapshot(
            int containerId,
            RegistryAccess registries
    ) {
        revision++;
        fullPending = false;
        pruneSerials(previous.keySet());
        return chunk(containerId, revision, true, fullEntries(previous), registries);
    }

    private void pruneSerials(java.util.Set<StorageResourceKey> present) {
        serials.keySet().removeIf(key -> !present.contains(key));
    }

    StorageResourceKey keyFor(long serial) {
        for (Map.Entry<StorageResourceKey, Long> entry : serials.entrySet()) {
            if (entry.getValue() == serial) return entry.getKey();
        }
        return null;
    }

    ItemStack displayFor(long serial) {
        StorageResourceKey key = keyFor(serial);
        if (key == null) return ItemStack.EMPTY;
        ItemStack display = previous.get(key);
        return display == null ? ItemStack.EMPTY : display.copy();
    }

    private List<TerminalRepositoryEntry> fullEntries(
            Map<StorageResourceKey, ItemStack> current
    ) {
        List<TerminalRepositoryEntry> entries = new ArrayList<>(current.size());
        for (Map.Entry<StorageResourceKey, ItemStack> entry : current.entrySet()) {
            entries.add(new TerminalRepositoryEntry(
                    serialFor(entry.getKey()), entry.getKey(), entry.getValue()));
        }
        return entries;
    }

    private List<TerminalRepositoryEntry> changedEntries(
            Map<StorageResourceKey, ItemStack> oldEntries,
            Map<StorageResourceKey, ItemStack> current
    ) {
        List<TerminalRepositoryEntry> changes = new ArrayList<>();
        for (Map.Entry<StorageResourceKey, ItemStack> entry : current.entrySet()) {
            long serial = serialFor(entry.getKey());
            ItemStack previousStack = oldEntries.get(entry.getKey());
            if (previousStack == null
                    || !ItemStack.isSameItemSameComponents(previousStack, entry.getValue())) {
                changes.add(new TerminalRepositoryEntry(
                        serial,
                        previousStack == null ? entry.getKey() : null,
                        entry.getValue()));
            }
        }
        for (Map.Entry<StorageResourceKey, ItemStack> entry : oldEntries.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                changes.add(new TerminalRepositoryEntry(
                        serialFor(entry.getKey()), null, ItemStack.EMPTY));
            }
        }
        return changes;
    }

    private long serialFor(StorageResourceKey key) {
        return serials.computeIfAbsent(key, ignored -> nextSerial++);
    }

    private static Map<StorageResourceKey, ItemStack> index(
            List<ItemStack> displayStacks,
            RegistryAccess registries
    ) {
        Map<StorageResourceKey, ItemStack> indexed = new LinkedHashMap<>();
        for (ItemStack display : displayStacks) {
            if (display.isEmpty()) continue;
            StorageResourceKey key = TerminalResourceDisplay.key(display).orElseGet(() ->
                    StorageResourceKey.item(TerminalDisplayStack.strip(display), registries));
            if (indexed.put(key, display.copy()) != null) {
                throw new IllegalStateException("Duplicate terminal repository resource " + key);
            }
        }
        return indexed;
    }

    private static Map<StorageResourceKey, ItemStack> copy(
            Map<StorageResourceKey, ItemStack> source
    ) {
        Map<StorageResourceKey, ItemStack> copy = new LinkedHashMap<>();
        source.forEach((key, stack) -> copy.put(key, stack.copy()));
        return Map.copyOf(copy);
    }

    private static List<TerminalRepositoryUpdatePacket> chunk(
            int containerId,
            long revision,
            boolean full,
            List<TerminalRepositoryEntry> entries,
            RegistryAccess registries
    ) {
        RegistryFriendlyByteBuf scratch = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), registries);
        try {
            List<List<TerminalRepositoryEntry>> chunks = new ArrayList<>();
            List<TerminalRepositoryEntry> current = new ArrayList<>(
                    TerminalRepositoryUpdatePacket.MAX_ENTRIES_PER_PACKET);
            int currentBytes = TerminalRepositoryUpdatePacket.MAX_PACKET_OVERHEAD_BYTES;
            for (TerminalRepositoryEntry entry : entries) {
                int entryBytes = TerminalRepositoryUpdatePacket.encodedEntrySize(scratch, entry);
                if (entryBytes + TerminalRepositoryUpdatePacket.MAX_PACKET_OVERHEAD_BYTES
                        > TerminalRepositoryUpdatePacket.MAX_SERIALIZED_BYTES) {
                    throw new IllegalArgumentException(
                            "Terminal repository entry exceeds packet size limit");
                }
                if (!current.isEmpty()
                        && (current.size() >= TerminalRepositoryUpdatePacket.MAX_ENTRIES_PER_PACKET
                        || currentBytes + entryBytes
                        > TerminalRepositoryUpdatePacket.MAX_SERIALIZED_BYTES)) {
                    chunks.add(List.copyOf(current));
                    current = new ArrayList<>(TerminalRepositoryUpdatePacket.MAX_ENTRIES_PER_PACKET);
                    currentBytes = TerminalRepositoryUpdatePacket.MAX_PACKET_OVERHEAD_BYTES;
                }
                current.add(entry);
                currentBytes += entryBytes;
            }
            if (!current.isEmpty() || chunks.isEmpty()) chunks.add(List.copyOf(current));

            int chunkCount = chunks.size();
            List<TerminalRepositoryUpdatePacket> packets = new ArrayList<>(chunkCount);
            for (int chunk = 0; chunk < chunkCount; chunk++) {
                packets.add(new TerminalRepositoryUpdatePacket(
                        containerId,
                        revision,
                        full,
                        chunk,
                        chunkCount,
                        chunks.get(chunk)));
            }
            return packets;
        } finally {
            scratch.release();
        }
    }
}
