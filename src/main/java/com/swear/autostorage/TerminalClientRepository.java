package com.swear.autostorage;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TerminalClientRepository {
    private final Map<Long, TerminalRepositoryEntry> entries = new HashMap<>();
    private List<TerminalRepositoryEntry> visible = List.of();
    private long completedRevision = -1;
    private long fullRevision = -1;
    private int expectedFullChunks;
    private int nextFullChunk;
    private boolean fullInProgress;
    private long deltaRevision = -1;
    private int expectedDeltaChunks;
    private int nextDeltaChunk;
    private boolean deltaInProgress;
    private boolean recoveryRequired;
    private String filter = "";
    private SortMode sortMode = SortMode.NAME;
    private SortOrder sortOrder = SortOrder.ASCENDING;
    private TerminalResourceView resourceView = TerminalResourceView.ITEM;
    private boolean dirty = true;
    private int scrollOffset;

    boolean apply(TerminalRepositoryUpdatePacket packet) {
        if (recoveryRequired
                && !(packet.full() && packet.chunkIndex() == 0)) return true;
        if (packet.full()) return applyFull(packet);
        if (fullInProgress) return failRecovery();
        if (!deltaInProgress && packet.revision() <= completedRevision) return true;
        if (!deltaInProgress) {
            if (packet.revision() != completedRevision + 1 || packet.chunkIndex() != 0) {
                return failRecovery();
            }
            deltaRevision = packet.revision();
            expectedDeltaChunks = packet.chunkCount();
            nextDeltaChunk = 0;
            deltaInProgress = true;
        }
        if (packet.revision() != deltaRevision
                || packet.chunkCount() != expectedDeltaChunks
                || packet.chunkIndex() != nextDeltaChunk) return failRecovery();
        if (!applyEntries(packet.entries())) return failRecovery();
        nextDeltaChunk++;
        if (nextDeltaChunk == expectedDeltaChunks) {
            deltaInProgress = false;
            completedRevision = deltaRevision;
        }
        dirty = true;
        return true;
    }

    void setView(
            String filter,
            SortMode sortMode,
            SortOrder sortOrder,
            TerminalResourceView resourceView
    ) {
        String normalized = filter == null ? "" : filter;
        if (this.filter.equals(normalized)
                && this.sortMode == sortMode
                && this.sortOrder == sortOrder
                && this.resourceView == resourceView) return;
        this.filter = normalized;
        this.sortMode = sortMode;
        this.sortOrder = sortOrder;
        this.resourceView = resourceView;
        dirty = true;
        scrollOffset = 0;
    }

    void setScrollOffset(int requested, int visibleRows) {
        int max = maxScrollOffset(visibleRows);
        int clamped = Math.clamp(requested, 0, max);
        int aligned = (int) (((long) clamped + StorageTerminalMenu.DISPLAY_COLS / 2)
                / StorageTerminalMenu.DISPLAY_COLS * StorageTerminalMenu.DISPLAY_COLS);
        scrollOffset = Math.min(aligned, max);
    }

    int getScrollOffset(int visibleRows) {
        setScrollOffset(scrollOffset, visibleRows);
        return scrollOffset;
    }

    int maxScrollOffset(int visibleRows) {
        int count = visibleEntries().size();
        long rows = ((long) count + StorageTerminalMenu.DISPLAY_COLS - 1)
                / StorageTerminalMenu.DISPLAY_COLS;
        long max = Math.max(0L, rows - visibleRows) * StorageTerminalMenu.DISPLAY_COLS;
        int largestAlignedInt = Integer.MAX_VALUE
                - Integer.MAX_VALUE % StorageTerminalMenu.DISPLAY_COLS;
        return (int) Math.min(max, largestAlignedInt);
    }

    int totalEntries() {
        return visibleEntries().size();
    }

    long serialAt(int visibleIndex, int visibleRows) {
        long index = (long) scrollOffset + visibleIndex;
        if (visibleIndex < 0
                || visibleIndex >= (long) visibleRows * StorageTerminalMenu.DISPLAY_COLS
                || index < 0 || index >= visibleEntries().size()) return -1;
        return visibleEntries().get((int) index).serial();
    }

    ItemStack stackAt(int visibleIndex, int visibleRows) {
        long index = (long) scrollOffset + visibleIndex;
        if (visibleIndex < 0
                || visibleIndex >= (long) visibleRows * StorageTerminalMenu.DISPLAY_COLS
                || index < 0 || index >= visibleEntries().size()) return ItemStack.EMPTY;
        return visibleEntries().get((int) index).displayStack().copy();
    }

    long revision() {
        return completedRevision;
    }

    private boolean applyFull(TerminalRepositoryUpdatePacket packet) {
        if (deltaInProgress) return failRecovery();
        if (packet.revision() < completedRevision) return true;
        if (packet.chunkIndex() == 0) {
            recoveryRequired = false;
            entries.clear();
            scrollOffset = 0;
            fullRevision = packet.revision();
            expectedFullChunks = packet.chunkCount();
            nextFullChunk = 0;
            fullInProgress = true;
        }
        if (!fullInProgress
                || packet.revision() != fullRevision
                || packet.chunkCount() != expectedFullChunks
                || packet.chunkIndex() != nextFullChunk) return failRecovery();
        if (!applyEntries(packet.entries())) return failRecovery();
        nextFullChunk++;
        if (nextFullChunk == expectedFullChunks) {
            fullInProgress = false;
            completedRevision = fullRevision;
        }
        dirty = true;
        return true;
    }

    private boolean failRecovery() {
        entries.clear();
        visible = List.of();
        fullInProgress = false;
        deltaInProgress = false;
        recoveryRequired = true;
        dirty = true;
        return false;
    }

    private boolean applyEntries(List<TerminalRepositoryEntry> updates) {
        for (TerminalRepositoryEntry update : updates) {
            TerminalRepositoryEntry previous = entries.get(update.serial());
            if (update.removed()) {
                if (previous == null) return false;
                entries.remove(update.serial());
                continue;
            }
            if (update.key() == null && previous == null) return false;
            if (update.key() != null && previous != null
                    && !update.key().equals(previous.key())) return false;
            StorageResourceKey key = update.key() != null
                    ? update.key() : previous.key();
            if (key == null) return false;
            entries.put(update.serial(), new TerminalRepositoryEntry(
                    update.serial(), key, update.displayStack()));
        }
        return true;
    }

    private List<TerminalRepositoryEntry> visibleEntries() {
        if (!dirty) return visible;
        TerminalSearchQuery query = TerminalSearchQuery.compile(filter);
        List<TerminalRepositoryEntry> filtered = new ArrayList<>();
        for (TerminalRepositoryEntry entry : entries.values()) {
            ItemStack display = entry.displayStack();
            StorageResourceKey key = entry.key();
            if (!resourceView.matches(key)
                    || !query.matches(key, TerminalDisplayStack.strip(display))) continue;
            filtered.add(entry);
        }
        Comparator<TerminalRepositoryEntry> comparator = TerminalEntryComparator.forMode(
                sortMode, sortOrder, TerminalRepositoryEntry::displayStack);
        filtered.sort(comparator.thenComparingLong(TerminalRepositoryEntry::serial));
        visible = List.copyOf(filtered);
        dirty = false;
        return visible;
    }
}
