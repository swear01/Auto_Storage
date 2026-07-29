package com.swearprom.magicstorage.fixture.recipe;

import com.swearprom.magicstorage.magic_storage.StorageResourceBlockStrategy;
import com.swearprom.magicstorage.magic_storage.StorageResourceHandler;
import com.swearprom.magicstorage.magic_storage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

final class FixtureManaBlockStrategy implements StorageResourceBlockStrategy {
    private static final ResourceLocation KIND = ResourceLocation.fromNamespaceAndPath(
            FixtureMod.MODID, "mana");
    private static final Map<Level, Map<BlockPos, Map<StorageResourceKey, Long>>> STORAGE =
            new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Handler>> HANDLERS = new WeakHashMap<>();
    private static final Map<Level, java.util.Set<BlockPos>> ISOLATED_HANDLERS =
            new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Runnable>> REENTRANT_CALLBACKS =
            new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Runnable>> SIMULATION_CALLBACKS =
            new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Runnable>> EXTRACTION_CALLBACKS =
            new WeakHashMap<>();

    static synchronized StorageResourceHandler handler(Level level, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        Behavior behavior = behavior(level, pos);
        Map<BlockPos, Handler> handlers = HANDLERS.computeIfAbsent(
                level, ignored -> new HashMap<>());
        Handler existing = handlers.get(immutablePos);
        if (existing != null && existing.behavior() == behavior) return existing;
        Map<StorageResourceKey, Long> storage = isolated(level, immutablePos)
                ? new HashMap<>()
                : resources(level, immutablePos);
        Handler created = new Handler(level, immutablePos, behavior, storage);
        handlers.put(immutablePos, created);
        return created;
    }

    static synchronized void useIsolatedHandler(
            Level level,
            BlockPos pos,
            StorageResourceKey key,
            long amount
    ) {
        BlockPos immutablePos = pos.immutable();
        ISOLATED_HANDLERS.computeIfAbsent(level, ignored -> new java.util.HashSet<>())
                .add(immutablePos);
        Map<StorageResourceKey, Long> storage = new HashMap<>();
        if (amount > 0) storage.put(key, amount);
        HANDLERS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(immutablePos, new Handler(level, immutablePos, behavior(level, pos), storage));
    }

    static synchronized void set(
            Level level,
            BlockPos pos,
            StorageResourceKey key,
            long amount
    ) {
        if (amount <= 0) resources(level, pos.immutable()).remove(key);
        else resources(level, pos.immutable()).put(key, amount);
    }

    static synchronized void runOnNextTargetInsert(
            Level level,
            BlockPos pos,
            Runnable callback
    ) {
        REENTRANT_CALLBACKS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(pos.immutable(), callback);
    }

    static synchronized void runOnNextSimulation(
            Level level,
            BlockPos pos,
            Runnable callback
    ) {
        SIMULATION_CALLBACKS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(pos.immutable(), callback);
    }

    static synchronized void runOnNextSourceExtract(
            Level level,
            BlockPos pos,
            Runnable callback
    ) {
        EXTRACTION_CALLBACKS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(pos.immutable(), callback);
    }

    static synchronized void replaceHandler(Level level, BlockPos pos) {
        Map<BlockPos, Handler> handlers = HANDLERS.get(level);
        if (handlers != null) handlers.remove(pos.immutable());
    }

    static synchronized void rejectExtraction(Level level, BlockPos pos) {
        Map<BlockPos, Handler> handlers = HANDLERS.get(level);
        Handler handler = handlers == null ? null : handlers.get(pos.immutable());
        if (handler == null) throw new IllegalStateException("Missing fixture resource handler");
        handler.rejectExtraction = true;
    }

    private static synchronized boolean isolated(Level level, BlockPos pos) {
        java.util.Set<BlockPos> positions = ISOLATED_HANDLERS.get(level);
        return positions != null && positions.contains(pos);
    }

    @Override
    public ResourceLocation kindId() {
        return KIND;
    }

    @Override
    public Optional<StorageResourceHandler> find(Level level, BlockPos pos, Direction side) {
        return (level.getBlockState(pos).is(Blocks.AMETHYST_BLOCK)
                || level.getBlockState(pos).is(Blocks.EMERALD_BLOCK)
                || level.getBlockState(pos).is(Blocks.GOLD_BLOCK))
                ? Optional.of(handler(level, pos))
                : Optional.empty();
    }

    private static synchronized Map<StorageResourceKey, Long> resources(Level level, BlockPos pos) {
        return STORAGE.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(pos, ignored -> new HashMap<>());
    }

    private static synchronized Runnable takeReentrantCallback(Level level, BlockPos pos) {
        Map<BlockPos, Runnable> callbacks = REENTRANT_CALLBACKS.get(level);
        return callbacks == null ? null : callbacks.remove(pos);
    }

    private static synchronized Runnable takeSimulationCallback(Level level, BlockPos pos) {
        Map<BlockPos, Runnable> callbacks = SIMULATION_CALLBACKS.get(level);
        return callbacks == null ? null : callbacks.remove(pos);
    }

    private static synchronized Runnable takeExtractionCallback(Level level, BlockPos pos) {
        Map<BlockPos, Runnable> callbacks = EXTRACTION_CALLBACKS.get(level);
        return callbacks == null ? null : callbacks.remove(pos);
    }

    private static Behavior behavior(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.EMERALD_BLOCK)) {
            return Behavior.OVERDRAWING_SOURCE;
        }
        if (level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
            return Behavior.PARTIAL_TARGET;
        }
        return Behavior.NORMAL;
    }

    private enum Behavior {
        NORMAL,
        OVERDRAWING_SOURCE,
        PARTIAL_TARGET
    }

    private static final class Handler implements StorageResourceHandler {
        private final Level level;
        private final BlockPos pos;
        private final Behavior behavior;
        private final Map<StorageResourceKey, Long> storage;
        private boolean rejectExtraction;

        private Handler(
                Level level,
                BlockPos pos,
                Behavior behavior,
                Map<StorageResourceKey, Long> storage
        ) {
            this.level = level;
            this.pos = pos;
            this.behavior = behavior;
            this.storage = storage;
        }

        private Behavior behavior() {
            return behavior;
        }

        @Override
        public List<StorageResourceKey> getStoredResources() {
            return storage.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
        }

        @Override
        public long getAmount(StorageResourceKey key) {
            return key.kindId().equals(KIND) ? storage.getOrDefault(key, 0L) : 0;
        }

        @Override
        public long insert(StorageResourceKey key, long amount, boolean simulate) {
            if (!key.kindId().equals(KIND) || amount <= 0) return 0;
            if (simulate) {
                Runnable callback = takeSimulationCallback(level, pos);
                if (callback != null) callback.run();
            }
            if (behavior == Behavior.OVERDRAWING_SOURCE && !simulate) return 0;
            long stored = getAmount(key);
            long accepted = behavior == Behavior.PARTIAL_TARGET && !simulate
                    ? Math.min(400, Math.min(amount, Long.MAX_VALUE - stored))
                    : Math.min(amount, Long.MAX_VALUE - stored);
            if (!simulate && accepted > 0) storage.put(key, stored + accepted);
            if (!simulate && accepted > 0) {
                Runnable callback = takeReentrantCallback(level, pos);
                if (callback != null) callback.run();
            }
            return accepted;
        }

        @Override
        public long extract(StorageResourceKey key, long amount, boolean simulate) {
            if (!key.kindId().equals(KIND) || amount <= 0) return 0;
            if (simulate) {
                Runnable callback = takeSimulationCallback(level, pos);
                if (callback != null) callback.run();
            }
            if (!simulate && rejectExtraction) return 0;
            long stored = getAmount(key);
            long requested = behavior == Behavior.OVERDRAWING_SOURCE && !simulate
                    ? Math.addExact(amount, 500)
                    : amount;
            long extracted = Math.min(requested, stored);
            if (!simulate && extracted > 0) {
                Runnable callback = takeExtractionCallback(level, pos);
                if (callback != null) callback.run();
                long remaining = stored - extracted;
                if (remaining == 0) storage.remove(key);
                else storage.put(key, remaining);
            }
            return extracted;
        }
    }
}
