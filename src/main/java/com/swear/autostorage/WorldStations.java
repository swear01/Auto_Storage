package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class WorldStations {
    private static final String SAVED_DATA_ID = "auto_storage_world_stations";
    private static final Map<ResourceLocation, BasePredicate> DEFINITIONS =
            new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, BooleanSupplier> ENABLED =
            new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<ResourceLocation, Set<BlockPos>>> PLACED =
            new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<ResourceLocation, Set<BlockPos>>> VALID =
            new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, Set<ResourceLocation>>> BASE_WATCHERS =
            new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<ResourceLocation, Integer>> VALID_COUNTS =
            new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Long> REVISIONS =
            new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<ResourceLocation, Boolean>> ENABLED_STATES =
            new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface BasePredicate {
        boolean test(Level level, BlockPos pos);
    }

    private WorldStations() {
    }

    public static void define(ResourceLocation blockId, BasePredicate base) {
        define(blockId, () -> true, base);
    }

    public static void define(
            ResourceLocation blockId,
            BooleanSupplier enabled,
            BasePredicate base
    ) {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(base, "base");
        if (DEFINITIONS.putIfAbsent(blockId, base) != null
                || ENABLED.putIfAbsent(blockId, enabled) != null) {
            throw new IllegalArgumentException("World station already defined: " + blockId);
        }
    }

    public static boolean isDefined(ResourceLocation blockId) {
        return DEFINITIONS.containsKey(blockId);
    }

    public static long revision(Level level) {
        if (level == null) return 0;
        syncEnabled(level);
        return REVISIONS.getOrDefault(level.dimension(), 0L);
    }

    static void place(ServerLevel level, ResourceLocation blockId, BlockPos pos) {
        if (!isDefined(blockId)) return;
        BlockPos immutable = pos.immutable();
        PLACED.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(blockId, ignored -> ConcurrentHashMap.newKeySet())
                .add(immutable);
        BASE_WATCHERS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(immutable.below(2), ignored -> ConcurrentHashMap.newKeySet())
                .add(blockId);
        StationSavedData saved = data(level);
        saved.add(level.dimension(), blockId, immutable);
        saved.setDirty();
        bumpRevision(level.dimension());
        refresh(level, blockId, immutable);
    }

    static void remove(ServerLevel level, ResourceLocation blockId, BlockPos pos) {
        Map<ResourceLocation, Set<BlockPos>> byBlock = PLACED.get(level.dimension());
        if (byBlock == null) return;
        BlockPos immutable = pos.immutable();
        Set<BlockPos> positions = byBlock.get(blockId);
        if (positions == null || !positions.remove(immutable)) return;
        if (positions.isEmpty()) byBlock.remove(blockId);
        if (byBlock.isEmpty()) PLACED.remove(level.dimension());
        Map<BlockPos, Set<ResourceLocation>> watchers = BASE_WATCHERS.get(level.dimension());
        if (watchers != null) {
            Set<ResourceLocation> ids = watchers.get(immutable.below(2));
            if (ids != null) {
                ids.remove(blockId);
                if (ids.isEmpty()) watchers.remove(immutable.below(2));
            }
            if (watchers.isEmpty()) BASE_WATCHERS.remove(level.dimension());
        }
        StationSavedData saved = data(level);
        saved.remove(level.dimension(), blockId, immutable);
        saved.setDirty();
        removeValid(level, blockId, immutable, saved);
        bumpRevision(level.dimension());
    }

    public static boolean isPresent(Level level, ResourceLocation blockId) {
        if (level == null || !isDefined(blockId)) return false;
        syncEnabled(level);
        if (!ENABLED.get(blockId).getAsBoolean()) return false;
        return VALID_COUNTS
                .getOrDefault(level.dimension(), Map.of())
                .getOrDefault(blockId, 0) > 0;
    }

    public static boolean isPresentAt(Level level, ResourceLocation blockId, BlockPos pos) {
        if (level == null || pos == null || !isDefined(blockId)) return false;
        syncEnabled(level);
        if (!ENABLED.get(blockId).getAsBoolean()) return false;
        return VALID
                .getOrDefault(level.dimension(), Map.of())
                .getOrDefault(blockId, Set.of())
                .contains(pos.immutable());
    }

    static void onNeighborNotify(ServerLevel level, BlockPos changedPos) {
        Map<BlockPos, Set<ResourceLocation>> watchers = BASE_WATCHERS.get(level.dimension());
        if (watchers == null) return;
        refreshWatchers(level, watchers, changedPos.immutable());
        refreshWatchers(level, watchers, changedPos.below());
        refreshWatchers(level, watchers, changedPos.above());
    }

    private static void refreshWatchers(
            ServerLevel level,
            Map<BlockPos, Set<ResourceLocation>> watchers,
            BlockPos base
    ) {
        Set<ResourceLocation> ids = watchers.get(base);
        if (ids == null) return;
        for (ResourceLocation blockId : ids) {
            refresh(level, blockId, base.above(2));
        }
    }

    static void onChunkLoad(ServerLevel level, ChunkPos chunkPos) {
        Map<ResourceLocation, Set<BlockPos>> byBlock = PLACED.get(level.dimension());
        if (byBlock == null) return;
        for (Map.Entry<ResourceLocation, Set<BlockPos>> entry : byBlock.entrySet()) {
            for (BlockPos pos : Set.copyOf(entry.getValue())) {
                if (new ChunkPos(pos).equals(chunkPos)) {
                    refresh(level, entry.getKey(), pos);
                }
            }
        }
    }

    static void onChunkUnload(ServerLevel level, ChunkPos chunkPos) {
        Map<ResourceLocation, Set<BlockPos>> byBlock = VALID.get(level.dimension());
        if (byBlock == null) return;
        StationSavedData saved = data(level);
        for (Map.Entry<ResourceLocation, Set<BlockPos>> entry : byBlock.entrySet()) {
            for (BlockPos pos : Set.copyOf(entry.getValue())) {
                if (new ChunkPos(pos).equals(chunkPos)) {
                    removeValid(level, entry.getKey(), pos, saved);
                }
            }
        }
    }

    public static void load(ServerLevel level) {
        StationSavedData saved = data(level);
        Set<ResourceKey<Level>> dimensions = new HashSet<>(REVISIONS.keySet());
        dimensions.addAll(saved.positions.keySet());
        dimensions.addAll(saved.validPositions.keySet());
        PLACED.clear();
        VALID.clear();
        BASE_WATCHERS.clear();
        VALID_COUNTS.clear();
        ENABLED_STATES.clear();
        for (ResourceKey<Level> dimension : dimensions) {
            REVISIONS.put(dimension, REVISIONS.getOrDefault(dimension, 0L) + 1L);
        }
        saved.validPositions.clear();
        Map<ResourceKey<Level>, ServerLevel> loadedLevels = new HashMap<>();
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            loadedLevels.put(loadedLevel.dimension(), loadedLevel);
        }
        saved.positions.forEach((dimension, byBlock) -> byBlock.forEach((blockId, positions) -> {
            if (!isDefined(blockId)) return;
            Set<BlockPos> placed = PLACED.computeIfAbsent(
                    dimension, ignored -> new ConcurrentHashMap<>())
                    .computeIfAbsent(blockId, ignored -> ConcurrentHashMap.newKeySet());
            placed.addAll(positions);
            for (BlockPos pos : positions) {
                BASE_WATCHERS.computeIfAbsent(dimension, ignored -> new ConcurrentHashMap<>())
                        .computeIfAbsent(pos.below(2), ignored -> ConcurrentHashMap.newKeySet())
                        .add(blockId);
                ServerLevel loadedLevel = loadedLevels.get(dimension);
                if (loadedLevel != null) {
                    refresh(loadedLevel, blockId, pos);
                }
            }
        }));
        saved.setDirty();
    }

    private static void refresh(ServerLevel level, ResourceLocation blockId, BlockPos pos) {
        Set<BlockPos> positions = PLACED
                .getOrDefault(level.dimension(), Map.of())
                .getOrDefault(blockId, Set.of());
        BasePredicate definition = DEFINITIONS.get(blockId);
        if (definition == null || !positions.contains(pos.immutable()) || !level.isLoaded(pos)) return;
        boolean valid = level.getBlockState(pos).is(BuiltInRegistries.BLOCK.get(blockId))
                && definition.test(level, pos);
        StationSavedData saved = data(level);
        if (valid) {
            addValid(level, blockId, pos, saved);
        } else {
            removeValid(level, blockId, pos, saved);
        }
    }

    private static void addValid(
            ServerLevel level,
            ResourceLocation blockId,
            BlockPos pos,
            StationSavedData saved
    ) {
        Set<BlockPos> valid = VALID.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(blockId, ignored -> ConcurrentHashMap.newKeySet());
        if (!valid.add(pos.immutable())) return;
        VALID_COUNTS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                .put(blockId, valid.size());
        saved.setValid(level.dimension(), blockId, pos, true);
        saved.setDirty();
        bumpRevision(level.dimension());
    }

    private static void removeValid(
            ServerLevel level,
            ResourceLocation blockId,
            BlockPos pos,
            StationSavedData saved
    ) {
        Map<ResourceLocation, Set<BlockPos>> byBlock = VALID.get(level.dimension());
        Set<BlockPos> valid = byBlock == null ? null : byBlock.get(blockId);
        if (valid == null || !valid.remove(pos.immutable())) return;
        VALID_COUNTS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                .put(blockId, valid.size());
        if (valid.isEmpty()) byBlock.remove(blockId);
        if (byBlock.isEmpty()) VALID.remove(level.dimension());
        saved.setValid(level.dimension(), blockId, pos, false);
        saved.setDirty();
        bumpRevision(level.dimension());
    }

    private static void bumpRevision(ResourceKey<Level> dimension) {
        REVISIONS.merge(dimension, 1L, Long::sum);
    }

    private static void syncEnabled(Level level) {
        ResourceKey<Level> dimension = level.dimension();
        Map<ResourceLocation, Boolean> states = ENABLED_STATES.computeIfAbsent(
                dimension, ignored -> new ConcurrentHashMap<>());
        for (Map.Entry<ResourceLocation, BooleanSupplier> entry : ENABLED.entrySet()) {
            boolean current = entry.getValue().getAsBoolean();
            Boolean previous = states.put(entry.getKey(), current);
            if (previous != null && previous != current) {
                bumpRevision(dimension);
                if (level instanceof ServerLevel serverLevel) {
                    refreshAll(serverLevel, entry.getKey());
                }
            }
        }
    }

    private static void refreshAll(ServerLevel level, ResourceLocation blockId) {
        Set<BlockPos> positions = PLACED
                .getOrDefault(level.dimension(), Map.of())
                .getOrDefault(blockId, Set.of());
        for (BlockPos pos : Set.copyOf(positions)) {
            refresh(level, blockId, pos);
        }
    }

    private static StationSavedData data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StationSavedData::new, StationSavedData::load),
                SAVED_DATA_ID);
    }

    private static final class StationSavedData extends SavedData {
        private final Map<ResourceKey<Level>, Map<ResourceLocation, Set<BlockPos>>> positions =
                new ConcurrentHashMap<>();
        private final Map<ResourceKey<Level>, Map<ResourceLocation, Set<BlockPos>>> validPositions =
                new ConcurrentHashMap<>();

        static StationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
            StationSavedData data = new StationSavedData();
            ListTag dimensions = tag.getList("dimensions", 10);
            for (int dimensionIndex = 0; dimensionIndex < dimensions.size(); dimensionIndex++) {
                CompoundTag dimension = dimensions.getCompound(dimensionIndex);
                ResourceKey<Level> dimensionKey = ResourceKey.create(
                        Registries.DIMENSION,
                        ResourceLocation.parse(dimension.getString("dimension")));
                ListTag stations = dimension.getList("stations", 10);
                Map<ResourceLocation, Set<BlockPos>> byBlock = new ConcurrentHashMap<>();
                Map<ResourceLocation, Set<BlockPos>> validByBlock = new ConcurrentHashMap<>();
                for (int stationIndex = 0; stationIndex < stations.size(); stationIndex++) {
                    CompoundTag station = stations.getCompound(stationIndex);
                    ResourceLocation blockId = ResourceLocation.parse(station.getString("block"));
                    Set<BlockPos> positions = readPositions(station.getList("positions", 10));
                    Set<BlockPos> valid = readPositions(station.getList("valid", 10));
                    if (!positions.isEmpty()) byBlock.put(blockId, positions);
                    if (!valid.isEmpty()) validByBlock.put(blockId, valid);
                }
                if (!byBlock.isEmpty()) data.positions.put(dimensionKey, byBlock);
                if (!validByBlock.isEmpty()) data.validPositions.put(dimensionKey, validByBlock);
            }
            return data;
        }

        private static Set<BlockPos> readPositions(ListTag positionTags) {
            Set<BlockPos> positions = ConcurrentHashMap.newKeySet();
            for (int positionIndex = 0; positionIndex < positionTags.size(); positionIndex++) {
                CompoundTag position = positionTags.getCompound(positionIndex);
                positions.add(new BlockPos(
                        position.getInt("x"),
                        position.getInt("y"),
                        position.getInt("z")));
            }
            return positions;
        }

        void add(ResourceKey<Level> dimension, ResourceLocation blockId, BlockPos pos) {
            positions.computeIfAbsent(dimension, ignored -> new ConcurrentHashMap<>())
                    .computeIfAbsent(blockId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(pos.immutable());
        }

        void remove(ResourceKey<Level> dimension, ResourceLocation blockId, BlockPos pos) {
            Map<ResourceLocation, Set<BlockPos>> byBlock = positions.get(dimension);
            if (byBlock != null) {
                Set<BlockPos> stationPositions = byBlock.get(blockId);
                if (stationPositions != null) {
                    stationPositions.remove(pos.immutable());
                    if (stationPositions.isEmpty()) byBlock.remove(blockId);
                }
                if (byBlock.isEmpty()) positions.remove(dimension);
            }
            setValid(dimension, blockId, pos, false);
        }

        void setValid(ResourceKey<Level> dimension, ResourceLocation blockId, BlockPos pos, boolean valid) {
            if (valid) {
                validPositions.computeIfAbsent(dimension, ignored -> new ConcurrentHashMap<>())
                        .computeIfAbsent(blockId, ignored -> ConcurrentHashMap.newKeySet())
                        .add(pos.immutable());
                return;
            }
            Map<ResourceLocation, Set<BlockPos>> entries = validPositions.get(dimension);
            if (entries == null) return;
            Set<BlockPos> positions = entries.get(blockId);
            if (positions == null) return;
            positions.remove(pos.immutable());
            if (positions.isEmpty()) entries.remove(blockId);
            if (entries.isEmpty()) validPositions.remove(dimension);
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag dimensions = new ListTag();
            Set<ResourceKey<Level>> dimensionKeys = new HashSet<>(positions.keySet());
            dimensionKeys.addAll(validPositions.keySet());
            for (ResourceKey<Level> dimensionKey : dimensionKeys) {
                CompoundTag dimension = new CompoundTag();
                dimension.putString("dimension", dimensionKey.location().toString());
                ListTag stations = new ListTag();
                Map<ResourceLocation, Set<BlockPos>> byBlock = positions.getOrDefault(dimensionKey, Map.of());
                Map<ResourceLocation, Set<BlockPos>> validByBlock = validPositions.getOrDefault(dimensionKey, Map.of());
                Set<ResourceLocation> blockIds = new HashSet<>(byBlock.keySet());
                blockIds.addAll(validByBlock.keySet());
                for (ResourceLocation blockId : blockIds) {
                    CompoundTag station = new CompoundTag();
                    station.putString("block", blockId.toString());
                    station.put("positions", writePositions(byBlock.getOrDefault(blockId, Set.of())));
                    station.put("valid", writePositions(validByBlock.getOrDefault(blockId, Set.of())));
                    stations.add(station);
                }
                dimension.put("stations", stations);
                dimensions.add(dimension);
            }
            tag.put("dimensions", dimensions);
            return tag;
        }

        private static ListTag writePositions(Set<BlockPos> positions) {
            ListTag tags = new ListTag();
            for (BlockPos pos : positions) {
                CompoundTag position = new CompoundTag();
                position.putInt("x", pos.getX());
                position.putInt("y", pos.getY());
                position.putInt("z", pos.getZ());
                tags.add(position);
            }
            return tags;
        }
    }
}
