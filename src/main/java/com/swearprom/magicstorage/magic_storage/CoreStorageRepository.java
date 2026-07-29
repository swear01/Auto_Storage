package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class CoreStorageRepository extends SavedData {
    static final String DATA_NAME = MagicStorage.MODID + "_core_storages";

    private static final int SCHEMA_VERSION = 1;
    private static final String TAG_SCHEMA_VERSION = "schemaVersion";
    private static final String TAG_STORAGES = "storages";
    private static final String TAG_RECOVERIES = "recoveries";
    private static final String TAG_ID = "id";
    private static final String TAG_STORAGE_ID = "storageId";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_TYPE_COUNT = "typeCount";
    private static final String TAG_ITEM_COUNT = "itemCount";
    private static final String TAG_CREATED_AT = "createdAt";
    private static final String TAG_ORIGIN_DIMENSION = "originDimension";
    private static final String TAG_ORIGIN_POS = "originPos";

    private final Map<UUID, CoreStorageRecord> records = new LinkedHashMap<>();
    private final Map<UUID, List<CompoundTag>> corruptRecords = new LinkedHashMap<>();
    private final List<CompoundTag> orphanRecords = new ArrayList<>();
    private final Map<UUID, Recovery> recoveries = new LinkedHashMap<>();
    private final Map<UUID, UUID> recoveryByStorage = new HashMap<>();
    private final List<CompoundTag> unresolvedRecoveries = new ArrayList<>();
    private final Map<UUID, Attachment> attachments = new HashMap<>();
    private CompoundTag unsupportedRoot;

    CoreStorageRepository() {
    }

    static CoreStorageRepository get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld is unavailable for Core storage repository");
        }
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(CoreStorageRepository::new, CoreStorageRepository::load),
                DATA_NAME);
    }

    static CoreStorageRepository load(CompoundTag tag, HolderLookup.Provider registries) {
        CoreStorageRepository repository = new CoreStorageRepository();
        Map<UUID, CompoundTag> decodedSources = new HashMap<>();
        ListTag storages = compoundList(tag, TAG_STORAGES);
        ListTag recoveryTags = compoundList(tag, TAG_RECOVERIES);
        if (!tag.contains(TAG_SCHEMA_VERSION, Tag.TAG_INT)
                || tag.getInt(TAG_SCHEMA_VERSION) != SCHEMA_VERSION
                || storages == null
                || recoveryTags == null) {
            repository.unsupportedRoot = tag.copy();
            MagicStorage.LOGGER.error(
                    "Unsupported Core storage repository root; preserving raw NBT");
            return repository;
        }

        for (int index = 0; index < storages.size(); index++) {
            CompoundTag raw = storages.getCompound(index).copy();
            CoreStorageRecord.LoadResult result = CoreStorageRecord.load(raw, registries);
            UUID storageId = result.storageId();
            if (storageId == null) {
                repository.orphanRecords.add(raw);
                MagicStorage.LOGGER.error(
                        "Orphan Core storage record at index {}: {}",
                        index,
                        result.error());
                continue;
            }
            if (repository.records.containsKey(storageId)
                    || repository.corruptRecords.containsKey(storageId)) {
                MagicStorage.LOGGER.error(
                        "Duplicate Core storage record at index {} for storageId={}; preserving all copies as unavailable data",
                        index,
                        storageId);
                repository.moveDuplicateToCorrupt(
                        storageId, raw, decodedSources.remove(storageId));
                continue;
            }
            if (!result.success()) {
                repository.corruptRecords
                        .computeIfAbsent(storageId, ignored -> new ArrayList<>())
                        .add(raw);
                MagicStorage.LOGGER.error(
                        "Corrupt Core storage record at index {} for storageId={}: {}",
                        index,
                        storageId,
                        result.error());
                continue;
            }
            CoreStorageRecord record = result.record();
            record.setDirtyCallback(repository::setDirty);
            repository.records.put(storageId, record);
            decodedSources.put(storageId, raw);
        }

        for (int index = 0; index < recoveryTags.size(); index++) {
            CompoundTag raw = recoveryTags.getCompound(index).copy();
            Recovery recovery = decodeRecovery(raw);
            if (recovery == null
                    || repository.recoveries.containsKey(recovery.id())
                    || repository.recoveryByStorage.containsKey(recovery.storageId())) {
                repository.unresolvedRecoveries.add(raw);
                MagicStorage.LOGGER.error(
                        "Unresolved Core recovery entry at index {}; preserving raw NBT",
                        index);
                continue;
            }
            repository.recoveries.put(recovery.id(), recovery);
            repository.recoveryByStorage.put(recovery.storageId(), recovery.id());
        }
        return repository;
    }

    private static ListTag compoundList(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list)) {
            return null;
        }
        return list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND ? list : null;
    }

    private void moveDuplicateToCorrupt(
            UUID storageId,
            CompoundTag duplicate,
            CompoundTag decodedSource
    ) {
        List<CompoundTag> entries = corruptRecords.computeIfAbsent(
                storageId, ignored -> new ArrayList<>());
        CoreStorageRecord decoded = records.remove(storageId);
        if (decoded != null) {
            entries.add(java.util.Objects.requireNonNull(decodedSource, "decodedSource").copy());
        }
        entries.add(duplicate.copy());
    }

    CoreStorageRecord createFresh(CoreLocation location, UUID ownerToken) {
        return tryCreateFresh(location, ownerToken).orElseThrow(
                () -> new IllegalStateException("Core storage repository schema is unavailable"));
    }

    Optional<CoreStorageRecord> tryCreateFresh(CoreLocation location, UUID ownerToken) {
        if (unsupportedRoot != null) {
            return Optional.empty();
        }
        UUID storageId;
        do {
            storageId = UUID.randomUUID();
        } while (records.containsKey(storageId) || corruptRecords.containsKey(storageId));
        CoreStorageRecord record = CoreStorageRecord.fresh(storageId);
        record.setDirtyCallback(this::setDirty);
        records.put(storageId, record);
        attachments.put(storageId, new Attachment(location, ownerToken));
        setDirty();
        return Optional.of(record);
    }

    AttachResult attachExisting(UUID storageId, CoreLocation location, UUID ownerToken) {
        if (unsupportedRoot != null) {
            return AttachResult.failure(AttachFailure.UNSUPPORTED_REPOSITORY);
        }
        if (corruptRecords.containsKey(storageId)) {
            return AttachResult.failure(AttachFailure.CORRUPT_RECORD);
        }
        CoreStorageRecord record = records.get(storageId);
        if (record == null) {
            return AttachResult.failure(AttachFailure.MISSING_RECORD);
        }

        UUID recoveryId = recoveryByStorage.get(storageId);
        if (recoveryId != null) {
            Recovery recovery = recoveries.get(recoveryId);
            if (recovery == null || !recovery.origin().equals(location)) {
                return AttachResult.failure(AttachFailure.PACKED);
            }
            Attachment attachment = attachments.get(storageId);
            if (attachment != null && !attachment.matches(location, ownerToken)) {
                return AttachResult.failure(AttachFailure.DUPLICATE_ATTACHMENT);
            }
            removeRecovery(recovery);
            setDirty();
        }

        Attachment current = attachments.get(storageId);
        if (current != null) {
            return current.matches(location, ownerToken)
                    ? AttachResult.success(record)
                    : AttachResult.failure(AttachFailure.DUPLICATE_ATTACHMENT);
        }
        attachments.put(storageId, new Attachment(location, ownerToken));
        return AttachResult.success(record);
    }

    void release(UUID storageId, CoreLocation location, UUID ownerToken) {
        Attachment attachment = attachments.get(storageId);
        if (attachment != null && attachment.matches(location, ownerToken)) {
            attachments.remove(storageId);
            CoreStorageRecord record = records.get(storageId);
            if (record != null) {
                record.clearMachineMutationCallback();
            }
        }
    }

    boolean removeIfEmpty(UUID storageId, CoreLocation location, UUID ownerToken) {
        CoreStorageRecord record = records.get(storageId);
        Attachment attachment = attachments.get(storageId);
        if (record == null || attachment == null || !attachment.matches(location, ownerToken)
                || recoveryByStorage.containsKey(storageId) || !record.isEmpty()) {
            return false;
        }
        attachments.remove(storageId);
        records.remove(storageId);
        setDirty();
        return true;
    }

    Optional<RecoverySummary> prepareRecovery(
            UUID storageId,
            CoreLocation location,
            UUID ownerToken,
            UUID owner,
            long createdAt
    ) {
        CoreStorageRecord record = records.get(storageId);
        Attachment attachment = attachments.get(storageId);
        if (record == null || attachment == null || !attachment.matches(location, ownerToken)) {
            return Optional.empty();
        }
        UUID existingId = recoveryByStorage.get(storageId);
        if (existingId != null) {
            Recovery existing = recoveries.get(existingId);
            return existing == null ? Optional.empty() : Optional.of(existing.summary());
        }

        UUID recoveryId;
        do {
            recoveryId = UUID.randomUUID();
        } while (recoveries.containsKey(recoveryId));
        Recovery recovery = new Recovery(
                recoveryId,
                storageId,
                owner,
                record.typeCount(),
                record.itemCount(),
                createdAt,
                location);
        recoveries.put(recoveryId, recovery);
        recoveryByStorage.put(storageId, recoveryId);
        setDirty();
        return Optional.of(recovery.summary());
    }

    Optional<RecoverySummary> summary(UUID recoveryId) {
        Recovery recovery = recoveries.get(recoveryId);
        return recovery == null ? Optional.empty() : Optional.of(recovery.summary());
    }

    Optional<RecoverySummary> reissueLatest(UUID owner) {
        Recovery latest = null;
        for (Recovery recovery : recoveries.values()) {
            if (recovery.owner() == null || !recovery.owner().equals(owner)) {
                continue;
            }
            if (latest == null || recovery.createdAt() >= latest.createdAt()) {
                latest = recovery;
            }
        }
        return latest == null ? Optional.empty() : Optional.of(latest.summary());
    }

    Optional<RecoverySummary> reissueLatestOwnerless() {
        Recovery latest = null;
        for (Recovery recovery : recoveries.values()) {
            if (recovery.owner() != null) {
                continue;
            }
            if (latest == null || recovery.createdAt() >= latest.createdAt()) {
                latest = recovery;
            }
        }
        return latest == null ? Optional.empty() : Optional.of(latest.summary());
    }

    boolean canClaim(UUID recoveryId) {
        Recovery recovery = recoveries.get(recoveryId);
        return recovery != null
                && records.containsKey(recovery.storageId())
                && !corruptRecords.containsKey(recovery.storageId())
                && !attachments.containsKey(recovery.storageId());
    }

    ClaimResult claimIntoFresh(
            UUID recoveryId,
            UUID freshStorageId,
            CoreLocation location,
            UUID ownerToken
    ) {
        Recovery recovery = recoveries.get(recoveryId);
        if (recovery == null) {
            return ClaimResult.failure(AttachFailure.RECOVERY_MISSING);
        }
        if (corruptRecords.containsKey(recovery.storageId())) {
            return ClaimResult.failure(AttachFailure.CORRUPT_RECORD);
        }
        CoreStorageRecord packed = records.get(recovery.storageId());
        if (packed == null) {
            return ClaimResult.failure(AttachFailure.MISSING_RECORD);
        }
        if (attachments.containsKey(recovery.storageId())) {
            return ClaimResult.failure(AttachFailure.DUPLICATE_ATTACHMENT);
        }

        CoreStorageRecord fresh = records.get(freshStorageId);
        Attachment freshAttachment = attachments.get(freshStorageId);
        if (fresh == null || freshAttachment == null
                || !freshAttachment.matches(location, ownerToken) || !fresh.isEmpty()
                || recoveryByStorage.containsKey(freshStorageId)) {
            return ClaimResult.failure(AttachFailure.INVALID_FRESH_RECORD);
        }

        records.remove(freshStorageId);
        attachments.remove(freshStorageId);
        removeRecovery(recovery);
        attachments.put(packed.storageId(), new Attachment(location, ownerToken));
        setDirty();
        return ClaimResult.success(packed);
    }

    boolean hasRecovery(UUID recoveryId) {
        return recoveries.containsKey(recoveryId);
    }

    boolean hasRecord(UUID storageId) {
        return records.containsKey(storageId);
    }

    Optional<CoreStorageRecord> getRecord(UUID storageId) {
        return Optional.ofNullable(records.get(storageId));
    }

    int recordCountForTesting() {
        return records.size() + corruptRecords.size();
    }

    void markChanged() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unsupportedRoot != null) {
            return tag.merge(unsupportedRoot.copy());
        }

        tag.putInt(TAG_SCHEMA_VERSION, SCHEMA_VERSION);
        ListTag storages = new ListTag();
        records.values().stream()
                .sorted(Comparator.comparing(record -> record.storageId().toString()))
                .map(record -> record.save(registries))
                .forEach(storages::add);
        corruptRecords.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> entry.getValue().forEach(raw -> storages.add(raw.copy())));
        orphanRecords.forEach(raw -> storages.add(raw.copy()));
        tag.put(TAG_STORAGES, storages);

        ListTag recoveryTags = new ListTag();
        recoveries.values().forEach(recovery -> recoveryTags.add(encodeRecovery(recovery)));
        unresolvedRecoveries.forEach(raw -> recoveryTags.add(raw.copy()));
        tag.put(TAG_RECOVERIES, recoveryTags);
        return tag;
    }

    private static CompoundTag encodeRecovery(Recovery recovery) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, recovery.id());
        tag.putUUID(TAG_STORAGE_ID, recovery.storageId());
        if (recovery.owner() != null) {
            tag.putUUID(TAG_OWNER, recovery.owner());
        }
        tag.putInt(TAG_TYPE_COUNT, recovery.typeCount());
        tag.putLong(TAG_ITEM_COUNT, recovery.itemCount());
        tag.putLong(TAG_CREATED_AT, recovery.createdAt());
        tag.putString(TAG_ORIGIN_DIMENSION, recovery.origin().dimension().location().toString());
        tag.putLong(TAG_ORIGIN_POS, recovery.origin().pos().asLong());
        return tag;
    }

    private static Recovery decodeRecovery(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ID) || !tag.hasUUID(TAG_STORAGE_ID)
                || tag.contains(TAG_OWNER) && !tag.hasUUID(TAG_OWNER)
                || !tag.contains(TAG_TYPE_COUNT, Tag.TAG_INT)
                || !tag.contains(TAG_ITEM_COUNT, Tag.TAG_LONG)
                || !tag.contains(TAG_CREATED_AT, Tag.TAG_LONG)
                || !tag.contains(TAG_ORIGIN_DIMENSION, Tag.TAG_STRING)
                || !tag.contains(TAG_ORIGIN_POS, Tag.TAG_LONG)
                || tag.getInt(TAG_TYPE_COUNT) < 0
                || tag.getLong(TAG_ITEM_COUNT) < 0
                || tag.getLong(TAG_CREATED_AT) < 0) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(TAG_ORIGIN_DIMENSION));
        if (dimensionId == null) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return new Recovery(
                tag.getUUID(TAG_ID),
                tag.getUUID(TAG_STORAGE_ID),
                tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null,
                tag.getInt(TAG_TYPE_COUNT),
                tag.getLong(TAG_ITEM_COUNT),
                tag.getLong(TAG_CREATED_AT),
                new CoreLocation(dimension, BlockPos.of(tag.getLong(TAG_ORIGIN_POS))));
    }

    private void removeRecovery(Recovery recovery) {
        recoveries.remove(recovery.id());
        recoveryByStorage.remove(recovery.storageId(), recovery.id());
    }

    enum AttachFailure {
        MISSING_RECORD,
        CORRUPT_RECORD,
        UNSUPPORTED_REPOSITORY,
        DUPLICATE_ATTACHMENT,
        PACKED,
        RECOVERY_MISSING,
        INVALID_FRESH_RECORD
    }

    record CoreLocation(ResourceKey<Level> dimension, BlockPos pos) {
        CoreLocation {
            java.util.Objects.requireNonNull(dimension, "dimension");
            java.util.Objects.requireNonNull(pos, "pos");
            pos = pos.immutable();
        }
    }

    record AttachResult(CoreStorageRecord record, AttachFailure reason) {
        static AttachResult success(CoreStorageRecord record) {
            return new AttachResult(record, null);
        }

        static AttachResult failure(AttachFailure reason) {
            return new AttachResult(null, reason);
        }

        boolean success() {
            return record != null;
        }
    }

    record ClaimResult(CoreStorageRecord record, AttachFailure reason) {
        static ClaimResult success(CoreStorageRecord record) {
            return new ClaimResult(record, null);
        }

        static ClaimResult failure(AttachFailure reason) {
            return new ClaimResult(null, reason);
        }

        boolean success() {
            return record != null;
        }
    }

    record RecoverySummary(UUID id, int typeCount, long itemCount, long createdAt) {
    }

    private record Recovery(
            UUID id,
            UUID storageId,
            UUID owner,
            int typeCount,
            long itemCount,
            long createdAt,
            CoreLocation origin
    ) {
        RecoverySummary summary() {
            return new RecoverySummary(id, typeCount, itemCount, createdAt);
        }
    }

    private record Attachment(CoreLocation location, UUID ownerToken) {
        Attachment {
            java.util.Objects.requireNonNull(location, "location");
            java.util.Objects.requireNonNull(ownerToken, "ownerToken");
        }

        boolean matches(CoreLocation expectedLocation, UUID expectedOwnerToken) {
            return location.equals(expectedLocation) && ownerToken.equals(expectedOwnerToken);
        }
    }
}
