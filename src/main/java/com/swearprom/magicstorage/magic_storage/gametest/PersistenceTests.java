package com.swearprom.magicstorage.magic_storage;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(MagicStorage.MODID)
public class PersistenceTests {

    @GameTest(template = "platform")
    public static void processing_tick_commits_all_station_work_once(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !core.isStorageAvailable()) {
                helper.fail("Fresh Core did not attach to world storage");
                return;
            }
            core.getMachineContainer().setItem(
                    MachineEnergyTable.FURNACE_SLOT, new ItemStack(Items.FURNACE));
            core.getMachineContainer().setItem(
                    MachineEnergyTable.SMOKER_SLOT, new ItemStack(Items.SMOKER));
            AtomicInteger dirtyCalls = new AtomicInteger();
            core.storageRecordForTesting().setDirtyCallback(dirtyCalls::incrementAndGet);

            core.tick();

            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != 1
                    || core.getEnergy(EnergyType.SMOKING_ENERGY) != 1
                    || dirtyCalls.get() != 1) {
                helper.fail("One processing tick must atomically persist every station once: "
                        + "smelt=" + core.getEnergy(EnergyType.SMELTING_ENERGY)
                        + ", smoke=" + core.getEnergy(EnergyType.SMOKING_ENERGY)
                        + ", dirty=" + dirtyCalls.get());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void core_persistence_contract_is_repository_owned_and_segmented(GameTestHelper helper) {
        if (!CoreStorageRepository.DATA_NAME.equals("magic_storage_core_storages")) {
            helper.fail("Core storage repository must use the approved world data name");
            return;
        }
        if (CoreStorageRecord.MAX_SEGMENT_TYPES != 63) {
            helper.fail("Core storage records must use 63-type persistence segments");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void core_chunk_nbt_stays_fixed_for_785_types(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !core.isStorageAvailable()) {
                helper.fail("Fresh Core did not attach to world storage");
                return;
            }
            CoreStorageRecord record = core.storageRecordForTesting();
            for (int index = 0; index < 785; index++) {
                ItemStack variant = new ItemStack(Items.PAPER);
                variant.set(DataComponents.CUSTOM_NAME, Component.literal("chunk-boundary-" + index));
                record.putItem(ItemKey.of(variant), index + 1L, level.registryAccess());
            }
            CompoundTag tag = new CompoundTag();
            core.saveAdditional(tag, level.registryAccess());
            if (!tag.hasUUID("storageId") || tag.getInt("storageSchema") != 1
                    || tag.getAllKeys().size() != 2
                    || tag.contains("networkId") || tag.contains("inventory")
                    || tag.contains("energy") || tag.contains("machineDescriptors")
                    || tag.contains("descriptorConsumables")) {
                helper.fail("Core chunk NBT grew with repository payload: " + tag.getAllKeys());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void missing_core_repository_reference_fails_closed_without_replacement(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        UUID missingStorageId = UUID.randomUUID();
        CoreStorageRepository repository = CoreStorageRepository.get(level);
        int before = repository.recordCountForTesting();
        StorageCoreBlockEntity detached = new StorageCoreBlockEntity(
                helper.absolutePos(new BlockPos(1, 3, 1)),
                MagicStorage.STORAGE_CORE.get().defaultBlockState());
        CompoundTag reference = new CompoundTag();
        reference.putUUID("storageId", missingStorageId);
        reference.putInt("storageSchema", 1);
        detached.loadAdditional(reference, level.registryAccess());
        detached.setLevel(level);
        detached.onLoad();
        ItemStack rejected = new ItemStack(Items.DIAMOND, 4);
        if (detached.isStorageAvailable()
                || detached.insertItem(rejected, Action.EXECUTE, Actor.EMPTY) != 0
                || rejected.getCount() != 4
                || repository.recordCountForTesting() != before
                || repository.hasRecord(missingStorageId)) {
            helper.fail("Missing repository reference became a writable empty Core");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void core_storage_record_round_trip_preserves_all_state(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        UUID storageId = UUID.randomUUID();
        CoreStorageRecord source = CoreStorageRecord.fresh(storageId);
        UUID networkId = source.networkId();
        for (EnergyType type : EnergyType.values()) {
            source.setEnergyAmount(type, 101L + type.ordinal());
        }
        ItemStack namedDiamond = new ItemStack(Items.DIAMOND);
        namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Repository Diamond"));
        source.putItem(ItemKey.of(namedDiamond), Long.MAX_VALUE - 7, registries);
        source.machines().setItem(MachineEnergyTable.FURNACE_SLOT, new ItemStack(Items.FURNACE, 3));
        source.setDescriptorAmount(MachineEnergyTable.AXE_ID, 37L);
        source.machineWorkRemainders().put(
                MachineEnergyTable.FURNACE_ID,
                new MachineWorkAccumulator.Remainder(
                        ResourceLocation.withDefaultNamespace("furnace"),
                        MachineWorkRate.of(5, 18),
                        7));
        source.infiniteDescriptors().add(ResourceLocation.fromNamespaceAndPath(
                MagicStorage.MODID, "round_trip_infinite"));

        CompoundTag unresolvedInventory = unresolvedInventoryEntry(
                registries, "removed_addon:storage_item", 73);
        CompoundTag unresolvedMachine = unresolvedMachineEntry(
                registries, "removed_addon:machine", "removed_addon:machine_item", 5);
        CompoundTag unresolvedDescriptor = new CompoundTag();
        unresolvedDescriptor.putString("descriptorId", "removed_addon:consumable");
        unresolvedDescriptor.putLong("amount", 19);
        unresolvedDescriptor.putBoolean("infinite", false);
        source.unresolvedInventoryEntries().add(unresolvedInventory.copy());
        source.unresolvedMachineEntries().add(unresolvedMachine.copy());
        source.unresolvedDescriptorEntries().add(unresolvedDescriptor.copy());

        CompoundTag encoded = source.save(registries);
        CoreStorageRecord.LoadResult decoded = CoreStorageRecord.load(encoded, registries);
        if (!decoded.success()) {
            helper.fail("Core storage record did not decode: " + decoded.error());
            return;
        }
        CoreStorageRecord restored = decoded.record();
        if (!restored.storageId().equals(storageId) || !restored.networkId().equals(networkId)) {
            helper.fail("Storage or network UUID changed during repository round trip");
            return;
        }
        for (EnergyType type : EnergyType.values()) {
            if (restored.energyAmount(type) != 101L + type.ordinal()) {
                helper.fail("Energy did not round trip for " + type);
                return;
            }
        }
        if (restored.getItemCount(ItemKey.of(namedDiamond), registries) != Long.MAX_VALUE - 7
                || restored.machines().getItem(MachineEnergyTable.FURNACE_SLOT).getCount() != 3
                || restored.descriptorAmount(MachineEnergyTable.AXE_ID) != 37
                || restored.machineWorkRemainders().get(MachineEnergyTable.FURNACE_ID).remainder() != 7
                || restored.unresolvedInventoryEntries().size() != 1
                || restored.unresolvedMachineEntries().size() != 1
                || restored.unresolvedDescriptorEntries().size() != 2) {
            helper.fail("Core storage record lost durable state during round trip");
            return;
        }
        if (!restored.unresolvedInventoryEntries().getFirst().equals(unresolvedInventory)
                || !restored.unresolvedMachineEntries().getFirst().equals(unresolvedMachine)) {
            helper.fail("Unavailable addon NBT changed during repository round trip");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void legacy_record_without_machine_work_payload_still_loads(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        CoreStorageRecord source = CoreStorageRecord.fresh(UUID.randomUUID());
        CompoundTag encoded = source.save(registries);
        encoded.remove(CoreStorageRecord.TAG_MACHINE_WORK);
        CoreStorageRecord.LoadResult decoded = CoreStorageRecord.load(encoded, registries);
        if (!decoded.success()
                || decoded.record().hasStationWork()
                || !decoded.record().machineWorkRemainders().isEmpty()) {
            helper.fail("Legacy record without optional machine work payload did not load empty");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void core_storage_record_segments_never_exceed_63_types(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        int[] cases = {0, 1, 63, 64, 785, 786};
        int[] expectedSegments = {0, 1, 1, 2, 13, 13};
        for (int caseIndex = 0; caseIndex < cases.length; caseIndex++) {
            CoreStorageRecord record = CoreStorageRecord.fresh(UUID.randomUUID());
            for (int index = 0; index < cases[caseIndex]; index++) {
                ItemStack variant = new ItemStack(Items.PAPER);
                variant.set(DataComponents.CUSTOM_NAME, Component.literal("segment-" + index));
                record.putItem(ItemKey.of(variant), index + 1L, registries);
            }
            CompoundTag encoded = record.save(registries);
            ListTag segments = encoded.getList("inventorySegments", Tag.TAG_COMPOUND);
            if (segments.size() != expectedSegments[caseIndex]) {
                helper.fail(cases[caseIndex] + " types used " + segments.size()
                        + " segments instead of " + expectedSegments[caseIndex]);
                return;
            }
            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                int entries = segments.getCompound(segmentIndex)
                        .getList("entries", Tag.TAG_COMPOUND).size();
                if (entries > CoreStorageRecord.MAX_SEGMENT_TYPES) {
                    helper.fail("Segment exceeded 63 types: " + entries);
                    return;
                }
            }
            CoreStorageRecord.LoadResult decoded = CoreStorageRecord.load(encoded, registries);
            if (!decoded.success() || decoded.record().typeCount() != cases[caseIndex]) {
                helper.fail("Segmented record did not restore " + cases[caseIndex] + " types");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void core_storage_repository_enforces_exact_runtime_attachment(GameTestHelper helper) {
        CoreStorageRepository repository = new CoreStorageRepository();
        CoreStorageRepository.CoreLocation firstLocation = new CoreStorageRepository.CoreLocation(
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 3, 1)));
        CoreStorageRepository.CoreLocation secondLocation = new CoreStorageRepository.CoreLocation(
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(4, 3, 1)));
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        CoreStorageRecord record = repository.createFresh(firstLocation, firstOwner);

        CoreStorageRepository.AttachResult duplicate = repository.attachExisting(
                record.storageId(), secondLocation, secondOwner);
        if (duplicate.success()
                || duplicate.reason() != CoreStorageRepository.AttachFailure.DUPLICATE_ATTACHMENT) {
            helper.fail("A second loaded Core attached to the same storage UUID");
            return;
        }
        repository.release(record.storageId(), firstLocation, secondOwner);
        if (repository.attachExisting(record.storageId(), secondLocation, secondOwner).success()) {
            helper.fail("A non-owner token released another Core attachment");
            return;
        }
        repository.release(record.storageId(), firstLocation, firstOwner);
        CoreStorageRepository.AttachResult transferred = repository.attachExisting(
                record.storageId(), secondLocation, secondOwner);
        if (!transferred.success() || transferred.record() != record) {
            helper.fail("Released repository record did not attach at the new location");
            return;
        }
        int recordsBeforeMissingLookup = repository.recordCountForTesting();
        CoreStorageRepository.AttachResult missing = repository.attachExisting(
                UUID.randomUUID(), firstLocation, UUID.randomUUID());
        if (missing.success() || missing.reason() != CoreStorageRepository.AttachFailure.MISSING_RECORD
                || repository.recordCountForTesting() != recordsBeforeMissingLookup) {
            helper.fail("Missing storage lookup manufactured a blank record");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void core_storage_recovery_reuses_one_record_without_payload_snapshot(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        CoreStorageRepository repository = new CoreStorageRepository();
        CoreStorageRepository.CoreLocation origin = new CoreStorageRepository.CoreLocation(
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 3, 1)));
        CoreStorageRepository.CoreLocation target = new CoreStorageRepository.CoreLocation(
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(4, 3, 1)));
        UUID originalOwnerToken = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        CoreStorageRecord stored = repository.createFresh(origin, originalOwnerToken);
        UUID networkId = stored.networkId();
        stored.putItem(ItemKey.of(new ItemStack(Items.DIAMOND)), 81, registries);

        CoreStorageRepository.RecoverySummary summary = repository.prepareRecovery(
                stored.storageId(), origin, originalOwnerToken, playerId, 1234L).orElseThrow();
        if (!repository.reissueLatest(playerId).orElseThrow().id().equals(summary.id())) {
            helper.fail("Recovery reissue created a different capability UUID");
            return;
        }
        CompoundTag repositoryTag = repository.save(new CompoundTag(), registries);
        ListTag recoveries = repositoryTag.getList("recoveries", Tag.TAG_COMPOUND);
        if (recoveries.size() != 1
                || !recoveries.getCompound(0).hasUUID("storageId")
                || recoveries.getCompound(0).contains("contents")) {
            helper.fail("Recovery entry copied Core payload instead of referencing storage UUID");
            return;
        }

        repository.release(stored.storageId(), origin, originalOwnerToken);
        UUID targetOwnerToken = UUID.randomUUID();
        CoreStorageRecord temporaryFresh = repository.createFresh(target, targetOwnerToken);
        CoreStorageRepository.ClaimResult claimed = repository.claimIntoFresh(
                summary.id(), temporaryFresh.storageId(), target, targetOwnerToken);
        if (!claimed.success() || claimed.record() != stored
                || !claimed.record().networkId().equals(networkId)
                || claimed.record().getItemCount(
                        ItemKey.of(new ItemStack(Items.DIAMOND)), registries) != 81
                || repository.hasRecovery(summary.id())
                || repository.hasRecord(temporaryFresh.storageId())) {
            helper.fail("Recovery claim copied, reset, or failed to consume the repository mapping");
            return;
        }
        CoreStorageRepository.ClaimResult duplicateClaim = repository.claimIntoFresh(
                summary.id(), stored.storageId(), target, targetOwnerToken);
        if (duplicateClaim.success()
                || duplicateClaim.reason() != CoreStorageRepository.AttachFailure.RECOVERY_MISSING) {
            helper.fail("A copied recovery token claimed the same storage twice");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void packed_record_only_allows_exact_origin_interruption_recovery(GameTestHelper helper) {
        CoreStorageRepository repository = new CoreStorageRepository();
        CoreStorageRepository.CoreLocation origin = new CoreStorageRepository.CoreLocation(
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 3, 1)));
        CoreStorageRepository.CoreLocation copy = new CoreStorageRepository.CoreLocation(
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(4, 3, 1)));
        UUID originalToken = UUID.randomUUID();
        CoreStorageRecord record = repository.createFresh(origin, originalToken);
        UUID recoveryId = repository.prepareRecovery(
                record.storageId(), origin, originalToken, UUID.randomUUID(), 99L).orElseThrow().id();
        repository.release(record.storageId(), origin, originalToken);

        CoreStorageRepository.AttachResult copiedAttach = repository.attachExisting(
                record.storageId(), copy, UUID.randomUUID());
        if (copiedAttach.success()
                || copiedAttach.reason() != CoreStorageRepository.AttachFailure.PACKED) {
            helper.fail("A stale copied Core attached a packed storage record");
            return;
        }
        CoreStorageRepository.AttachResult originAttach = repository.attachExisting(
                record.storageId(), origin, UUID.randomUUID());
        if (!originAttach.success() || repository.hasRecovery(recoveryId)) {
            helper.fail("Exact origin did not cancel an interrupted pack");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void corrupt_repository_records_are_preserved_and_fail_closed(GameTestHelper helper) {
        UUID corruptId = UUID.randomUUID();
        CompoundTag corrupt = new CompoundTag();
        corrupt.putUUID("storageId", corruptId);
        corrupt.putString("sentinel", "retain-me");
        CompoundTag orphan = new CompoundTag();
        orphan.putString("sentinel", "orphan-retain-me");
        ListTag storages = new ListTag();
        storages.add(corrupt.copy());
        storages.add(orphan.copy());
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", 1);
        root.put("storages", storages);
        root.put("recoveries", new ListTag());

        CoreStorageRepository repository = CoreStorageRepository.load(
                root, helper.getLevel().registryAccess());
        CoreStorageRepository.AttachResult attached = repository.attachExisting(
                corruptId,
                new CoreStorageRepository.CoreLocation(
                        helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 3, 1))),
                UUID.randomUUID());
        if (attached.success()
                || attached.reason() != CoreStorageRepository.AttachFailure.CORRUPT_RECORD) {
            helper.fail("Corrupt repository record did not fail closed");
            return;
        }
        CompoundTag resaved = repository.save(new CompoundTag(), helper.getLevel().registryAccess());
        ListTag retained = resaved.getList("storages", Tag.TAG_COMPOUND);
        if (retained.size() != 2 || !retained.contains(corrupt) || !retained.contains(orphan)) {
            helper.fail("Corrupt or orphan repository NBT was not retained exactly: " + retained);
            return;
        }

        CoreStorageRecord duplicateSource = CoreStorageRecord.fresh(UUID.randomUUID());
        duplicateSource.putItem(
                ItemKey.of(new ItemStack(Items.DIAMOND)), 17,
                helper.getLevel().registryAccess());
        CompoundTag firstCopy = duplicateSource.save(helper.getLevel().registryAccess());
        firstCopy.putString("sentinel", "first-copy");
        CompoundTag secondCopy = firstCopy.copy();
        secondCopy.putString("sentinel", "second-copy");
        ListTag duplicateStorages = new ListTag();
        duplicateStorages.add(firstCopy.copy());
        duplicateStorages.add(secondCopy.copy());
        CompoundTag duplicateRoot = new CompoundTag();
        duplicateRoot.putInt("schemaVersion", 1);
        duplicateRoot.put("storages", duplicateStorages);
        duplicateRoot.put("recoveries", new ListTag());
        CoreStorageRepository duplicateRepository = CoreStorageRepository.load(
                duplicateRoot, helper.getLevel().registryAccess());
        CoreStorageRepository.AttachResult duplicateAttach = duplicateRepository.attachExisting(
                duplicateSource.storageId(),
                new CoreStorageRepository.CoreLocation(
                        helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 3, 2))),
                UUID.randomUUID());
        ListTag duplicateResaved = duplicateRepository
                .save(new CompoundTag(), helper.getLevel().registryAccess())
                .getList("storages", Tag.TAG_COMPOUND);
        if (duplicateAttach.success()
                || duplicateAttach.reason() != CoreStorageRepository.AttachFailure.CORRUPT_RECORD
                || duplicateResaved.size() != 2
                || !duplicateResaved.contains(firstCopy)
                || !duplicateResaved.contains(secondCopy)) {
            helper.fail("Duplicate storage records were not retained byte-equivalent and unavailable");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void malformed_repository_list_shapes_fail_closed_without_data_loss(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag validRecord = CoreStorageRecord.fresh(UUID.randomUUID()).save(registries);
        for (EnergyType type : EnergyType.values()) {
            CompoundTag malformedEnergy = validRecord.copy();
            malformedEnergy.getCompound("energy").putString(type.getId(), "retain-invalid-energy");
            CoreStorageRecord.LoadResult loaded = CoreStorageRecord.load(malformedEnergy, registries);
            if (loaded.success() || !malformedEnergy.equals(loaded.raw())) {
                helper.fail("Malformed energy field was accepted or changed for " + type);
                return;
            }

            CompoundTag missingEnergy = validRecord.copy();
            missingEnergy.getCompound("energy").remove(type.getId());
            loaded = CoreStorageRecord.load(missingEnergy, registries);
            if (loaded.success() || !missingEnergy.equals(loaded.raw())) {
                helper.fail("Missing energy field was accepted or changed for " + type);
                return;
            }
        }

        CompoundTag malformedDescriptor = validRecord.copy();
        CompoundTag descriptorEntry = new CompoundTag();
        descriptorEntry.putString("descriptorId", MachineEnergyTable.AXE_ID.toString());
        descriptorEntry.putString("amount", "retain-invalid-descriptor-amount");
        descriptorEntry.putBoolean("infinite", false);
        malformedDescriptor.getList("descriptorConsumables", Tag.TAG_COMPOUND).add(descriptorEntry);
        CoreStorageRecord.LoadResult loadedDescriptor = CoreStorageRecord.load(
                malformedDescriptor, registries);
        if (!loadedDescriptor.success()
                || !malformedDescriptor.equals(loadedDescriptor.record().save(registries))) {
            helper.fail("Malformed descriptor metadata was not retained raw");
            return;
        }

        CompoundTag malformedDescriptorBoolean = validRecord.copy();
        CompoundTag descriptorBooleanEntry = new CompoundTag();
        descriptorBooleanEntry.putString("descriptorId", MachineEnergyTable.AXE_ID.toString());
        descriptorBooleanEntry.putLong("amount", 0);
        descriptorBooleanEntry.putByte("infinite", (byte) 2);
        malformedDescriptorBoolean.getList("descriptorConsumables", Tag.TAG_COMPOUND)
                .add(descriptorBooleanEntry);
        loadedDescriptor = CoreStorageRecord.load(malformedDescriptorBoolean, registries);
        if (!loadedDescriptor.success()
                || !malformedDescriptorBoolean.equals(loadedDescriptor.record().save(registries))) {
            helper.fail("Malformed descriptor boolean was not retained raw");
            return;
        }

        CoreStorageRecord countSource = CoreStorageRecord.fresh(UUID.randomUUID());
        countSource.putItem(ItemKey.of(new ItemStack(Items.STONE)), 1, registries);
        CompoundTag malformedInventoryCount = countSource.save(registries);
        malformedInventoryCount.getList("inventorySegments", Tag.TAG_COMPOUND)
                .getCompound(0).getList("entries", Tag.TAG_COMPOUND)
                .getCompound(0).putInt("count", 1);
        CoreStorageRecord.LoadResult loadedInventoryCount = CoreStorageRecord.load(
                malformedInventoryCount, registries);
        if (loadedInventoryCount.success()
                || !malformedInventoryCount.equals(loadedInventoryCount.raw())) {
            helper.fail("Non-long inventory count was accepted or changed");
            return;
        }

        for (String key : new String[]{
                "descriptorConsumables", "machineDescriptors", "inventorySegments"
        }) {
            CompoundTag malformed = validRecord.copy();
            ListTag wrongShape = new ListTag();
            wrongShape.add(StringTag.valueOf("retain-" + key));
            malformed.put(key, wrongShape);
            CoreStorageRecord.LoadResult loaded = CoreStorageRecord.load(malformed, registries);
            if (loaded.success() || !malformed.equals(loaded.raw())) {
                helper.fail("Malformed " + key + " list was accepted or changed");
                return;
            }
        }

        CoreStorageRecord populated = CoreStorageRecord.fresh(UUID.randomUUID());
        populated.putItem(ItemKey.of(new ItemStack(Items.STONE)), 1, registries);
        CompoundTag malformedEntries = populated.save(registries);
        ListTag segments = malformedEntries.getList("inventorySegments", Tag.TAG_COMPOUND);
        ListTag wrongEntries = new ListTag();
        wrongEntries.add(StringTag.valueOf("retain-entry"));
        segments.getCompound(0).put("entries", wrongEntries);
        CoreStorageRecord.LoadResult loadedEntries = CoreStorageRecord.load(malformedEntries, registries);
        if (loadedEntries.success() || !malformedEntries.equals(loadedEntries.raw())) {
            helper.fail("Malformed inventory entries list was accepted or changed");
            return;
        }

        for (String key : new String[]{"storages", "recoveries"}) {
            CompoundTag root = new CompoundTag();
            root.putInt("schemaVersion", 1);
            root.put("storages", new ListTag());
            root.put("recoveries", new ListTag());
            ListTag wrongShape = new ListTag();
            wrongShape.add(StringTag.valueOf("retain-" + key));
            root.put(key, wrongShape);
            CoreStorageRepository repository = CoreStorageRepository.load(root, registries);
            if (repository.tryCreateFresh(
                    new CoreStorageRepository.CoreLocation(
                            helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 3, 2))),
                    UUID.randomUUID()).isPresent()) {
                helper.fail("Malformed repository root allowed fresh storage creation");
                return;
            }
            CompoundTag resaved = repository.save(new CompoundTag(), registries);
            if (!root.equals(resaved)) {
                helper.fail("Malformed repository " + key + " list was not preserved exactly");
                return;
            }
        }

        CompoundTag malformedRecovery = new CompoundTag();
        malformedRecovery.putUUID("id", UUID.randomUUID());
        malformedRecovery.putUUID("storageId", UUID.randomUUID());
        malformedRecovery.putString("owner", "retain-invalid-owner");
        malformedRecovery.putString("typeCount", "retain-invalid-count");
        malformedRecovery.putString("originDimension", helper.getLevel().dimension().location().toString());
        malformedRecovery.putLong("originPos", helper.absolutePos(new BlockPos(3, 3, 3)).asLong());
        ListTag malformedRecoveries = new ListTag();
        malformedRecoveries.add(malformedRecovery.copy());
        CompoundTag recoveryRoot = new CompoundTag();
        recoveryRoot.putInt("schemaVersion", 1);
        recoveryRoot.put("storages", new ListTag());
        recoveryRoot.put("recoveries", malformedRecoveries);
        CompoundTag recoveryResaved = CoreStorageRepository.load(recoveryRoot, registries)
                .save(new CompoundTag(), registries);
        if (!recoveryRoot.equals(recoveryResaved)) {
            helper.fail("Malformed recovery metadata was normalized instead of retained raw");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void repository_deletes_only_empty_attached_records(GameTestHelper helper) {
        CoreStorageRepository repository = new CoreStorageRepository();
        CoreStorageRepository.CoreLocation location = new CoreStorageRepository.CoreLocation(
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 3, 1)));
        UUID ownerToken = UUID.randomUUID();
        CoreStorageRecord empty = repository.createFresh(location, ownerToken);
        if (!repository.removeIfEmpty(empty.storageId(), location, ownerToken)
                || repository.hasRecord(empty.storageId())) {
            helper.fail("Empty Core record was not deleted on removal");
            return;
        }

        CoreStorageRecord populated = repository.createFresh(location, ownerToken);
        populated.putItem(
                ItemKey.of(new ItemStack(Items.STONE)), 1,
                helper.getLevel().registryAccess());
        if (repository.removeIfEmpty(populated.storageId(), location, ownerToken)
                || !repository.hasRecord(populated.storageId())) {
            helper.fail("Non-empty Core record was deleted on removal");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void storage_core_can_always_be_harvested_without_losing_its_drop(GameTestHelper helper) {
        if (MagicStorage.STORAGE_CORE.get().defaultBlockState().requiresCorrectToolForDrops()) {
            helper.fail("Storage Core must not delete contents when broken without the preferred tool");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void creative_break_drops_one_time_recoverable_packed_core(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var restoredPos = helper.absolutePos(new BlockPos(4, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not found");
                return;
            }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 64));
            core.insertItem(new ItemStack(Items.DIRT, 17));

            var creativePlayer = helper.makeMockPlayer(GameType.CREATIVE);
            var state = level.getBlockState(corePos);
            state.getBlock().playerWillDestroy(level, corePos, state, creativePlayer);

            ItemStack packedCore = level.getEntitiesOfClass(
                            ItemEntity.class, new AABB(corePos).inflate(2.0)).stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(MagicStorage.STORAGE_CORE_ITEM.get()))
                    .findFirst()
                    .map(ItemStack::copy)
                    .orElse(ItemStack.EMPTY);
            if (packedCore.isEmpty()) {
                helper.fail("Creative break did not create a packed Core");
                return;
            }
            var recoveryId = StorageCoreBlockItem.getRecoveryId(packedCore);
            if (recoveryId.isEmpty()) {
                helper.fail("Packed Core is missing its recovery token");
                return;
            }
            if (StorageCoreBlockItem.getStoredTypeCount(packedCore) != 2) {
                helper.fail("Packed Core summary should report two stored types");
                return;
            }

            level.removeBlock(corePos, false);
            level.setBlock(restoredPos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            MagicStorage.STORAGE_CORE.get().setPlacedBy(
                    level, restoredPos, level.getBlockState(restoredPos), creativePlayer, packedCore);
            if (!(level.getBlockEntity(restoredPos) instanceof StorageCoreBlockEntity restored)) {
                helper.fail("Restored Core BE not found");
                return;
            }
            if (restored.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 64
                    || restored.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 17) {
                helper.fail("Creative packed Core did not restore its exact contents");
                return;
            }
            if (CoreStorageRepository.get(level).hasRecovery(recoveryId.get())) {
                helper.fail("Recovery token must be one-time after successful placement");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void blank_core_after_break_does_not_clone_previous_inventory(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var unitPos = corePos.east();

        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(unitPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core BE not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 64));
            core.getMachineContainer().setItem(0, new ItemStack(Items.FURNACE, 2));
            if (core.getTypeCount() != 1) { helper.fail("Should have 1 type"); return; }

            level.destroyBlock(corePos, false);
            level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

            helper.runAfterDelay(1, () -> {
                var blankBe = level.getBlockEntity(corePos);
                if (!(blankBe instanceof StorageCoreBlockEntity blankCore)) { helper.fail("Blank core BE not found"); return; }
                blankCore.onLoad();
                if (blankCore.getTypeCount() != 0) {
                    helper.fail("Blank core cloned previous inventory after break");
                    return;
                }
                if (!blankCore.getMachineContainer().isEmpty()) {
                    helper.fail("Blank core cloned previously installed machines after break");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "platform")
    public static void failed_packed_core_placement_keeps_recovery_mapping(GameTestHelper helper) {
        var level = helper.getLevel();
        var repository = CoreStorageRepository.get(level);
        var origin = new CoreStorageRepository.CoreLocation(
                level.dimension(), helper.absolutePos(new BlockPos(1, 3, 1)));
        UUID attachmentToken = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        CoreStorageRecord record = repository.createFresh(origin, attachmentToken);
        record.putItem(
                ItemKey.of(new ItemStack(Items.STONE)), 9, level.registryAccess());
        CoreStorageRepository.RecoverySummary summary = repository.prepareRecovery(
                record.storageId(), origin, attachmentToken, owner, level.getGameTime()).orElseThrow();
        repository.release(record.storageId(), origin, attachmentToken);
        ItemStack packedCore = StorageCoreBlockItem.createRecoveryStack(summary);

        try {
            MagicStorage.STORAGE_CORE.get().setPlacedBy(
                    level,
                    helper.absolutePos(new BlockPos(4, 3, 1)),
                    MagicStorage.STORAGE_CORE.get().defaultBlockState(),
                    helper.makeMockPlayer(GameType.SURVIVAL),
                    packedCore);
            helper.fail("Placement without a Core block entity must fail closed");
            return;
        } catch (IllegalStateException expected) {
            if (!repository.hasRecovery(summary.id()) || !repository.hasRecord(record.storageId())) {
                helper.fail("Failed placement consumed or deleted repository state");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void failed_recovery_claim_discards_temporary_fresh_record(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !core.isStorageAvailable()) {
                helper.fail("Fresh Core did not attach before failed recovery claim");
                return;
            }
            CoreStorageRepository repository = CoreStorageRepository.get(level);
            UUID temporaryStorageId = core.getStorageId().orElseThrow();
            int recordsBeforeClaim = repository.recordCountForTesting();
            if (core.claimRecovery(level, UUID.randomUUID())) {
                helper.fail("Missing recovery mapping unexpectedly claimed a record");
                return;
            }
            if (repository.hasRecord(temporaryStorageId)
                    || repository.recordCountForTesting() != recordsBeforeClaim - 1) {
                helper.fail("Failed recovery claim leaked its temporary fresh record");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void command_loaded_core_reference_replaces_temporary_storage_without_leak(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var commandPos = helper.absolutePos(new BlockPos(1, 3, 1));
        var sourcePos = helper.absolutePos(new BlockPos(4, 3, 1));
        level.setBlock(commandPos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(sourcePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(commandPos) instanceof StorageCoreBlockEntity commandCore)
                    || !(level.getBlockEntity(sourcePos) instanceof StorageCoreBlockEntity sourceCore)) {
                helper.fail("Core block entities not found");
                return;
            }
            CoreStorageRepository repository = CoreStorageRepository.get(level);
            UUID temporaryStorageId = commandCore.getStorageId().orElseThrow();
            UUID sourceStorageId = sourceCore.getStorageId().orElseThrow();
            int recordsBeforeLoad = repository.recordCountForTesting();
            sourceCore.getMachineContainer().setItem(
                    MachineEnergyTable.FURNACE_SLOT, new ItemStack(Items.FURNACE, 7));
            CompoundTag sourceReference = new CompoundTag();
            sourceCore.saveAdditional(sourceReference, level.registryAccess());
            sourceCore.removeStorageForBlockRemoval(level);
            level.removeBlock(sourcePos, false);

            commandCore.loadAdditional(sourceReference, level.registryAccess());
            if (!commandCore.isStorageAvailable()
                    || !commandCore.getStorageId().orElseThrow().equals(sourceStorageId)
                    || commandCore.getMachineContainer().getItem(
                    MachineEnergyTable.FURNACE_SLOT).getCount() != 7
                    || repository.hasRecord(temporaryStorageId)
                    || repository.recordCountForTesting() != recordsBeforeLoad - 1) {
                helper.fail("Command-loaded Core reference leaked its temporary record or stayed detached");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void latest_recovery_prefers_last_mapping_created_in_same_tick(GameTestHelper helper) {
        var level = helper.getLevel();
        var repository = CoreStorageRepository.get(level);
        UUID owner = UUID.randomUUID();
        long createdAt = level.getGameTime();
        var firstLocation = new CoreStorageRepository.CoreLocation(
                level.dimension(), helper.absolutePos(new BlockPos(1, 3, 1)));
        var secondLocation = new CoreStorageRepository.CoreLocation(
                level.dimension(), helper.absolutePos(new BlockPos(4, 3, 1)));
        UUID firstToken = UUID.randomUUID();
        UUID secondToken = UUID.randomUUID();
        CoreStorageRecord first = repository.createFresh(firstLocation, firstToken);
        CoreStorageRecord second = repository.createFresh(secondLocation, secondToken);
        UUID firstRecovery = repository.prepareRecovery(
                first.storageId(), firstLocation, firstToken, owner, createdAt).orElseThrow().id();
        UUID secondRecovery = repository.prepareRecovery(
                second.storageId(), secondLocation, secondToken, owner, createdAt).orElseThrow().id();
        var latest = repository.reissueLatest(owner);
        if (latest.isEmpty() || !latest.get().id().equals(secondRecovery)
                || latest.get().id().equals(firstRecovery)) {
            helper.fail("Same-tick recovery reissue did not select the last mapping");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void recovery_commands_keep_owned_and_ownerless_records_separate(GameTestHelper helper) {
        var level = helper.getLevel();
        var repository = CoreStorageRepository.get(level);
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.randomUUID(), "magic-storage-recovery-test"));
        var ownedLocation = new CoreStorageRepository.CoreLocation(
                level.dimension(), helper.absolutePos(new BlockPos(1, 3, 1)));
        var ownerlessLocation = new CoreStorageRepository.CoreLocation(
                level.dimension(), helper.absolutePos(new BlockPos(4, 3, 1)));
        UUID ownedToken = UUID.randomUUID();
        UUID ownerlessToken = UUID.randomUUID();
        CoreStorageRecord owned = repository.createFresh(ownedLocation, ownedToken);
        CoreStorageRecord ownerless = repository.createFresh(ownerlessLocation, ownerlessToken);
        UUID ownedRecovery = repository.prepareRecovery(
                owned.storageId(), ownedLocation, ownedToken, player.getUUID(), Long.MAX_VALUE - 1)
                .orElseThrow().id();
        UUID ownerlessRecovery = repository.prepareRecovery(
                ownerless.storageId(), ownerlessLocation, ownerlessToken, null, Long.MAX_VALUE)
                .orElseThrow().id();

        level.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(0),
                "magic_storage recover_core");
        if (!inventoryContainsRecovery(player, ownedRecovery)
                || inventoryContainsRecovery(player, ownerlessRecovery)) {
            helper.fail("Public recovery command crossed the owner boundary");
            return;
        }

        level.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(0),
                "magic_storage recover_core ownerless");
        if (inventoryContainsRecovery(player, ownerlessRecovery)) {
            helper.fail("Non-operator recovered an ownerless Core record");
            return;
        }

        level.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(2),
                "magic_storage recover_core ownerless");
        if (!inventoryContainsRecovery(player, ownerlessRecovery)) {
            helper.fail("Operator could not recover the latest ownerless Core record");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ownerless_non_player_removals_preserve_core_recovery_records(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos commandCorePos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos explosionCorePos = helper.absolutePos(new BlockPos(4, 3, 1));
        level.setBlock(commandCorePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(explosionCorePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(commandCorePos) instanceof StorageCoreBlockEntity commandCore)
                    || !(level.getBlockEntity(explosionCorePos) instanceof StorageCoreBlockEntity explosionCore)) {
                helper.fail("Core missing before non-player removal recovery test");
                return;
            }
            commandCore.storageRecordForTesting().putItem(
                    ItemKey.of(new ItemStack(Items.DIAMOND)), 1, level.registryAccess());
            explosionCore.storageRecordForTesting().putItem(
                    ItemKey.of(new ItemStack(Items.EMERALD)), 2, level.registryAccess());
            UUID explosionOwner = UUID.randomUUID();
            UUID explosionRecoveryId = explosionCore.prepareRecoveryDrop(level, explosionOwner);

            level.setBlock(commandCorePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.explode(
                    null,
                    explosionCorePos.getX() + 0.5,
                    explosionCorePos.getY() + 0.5,
                    explosionCorePos.getZ() + 0.5,
                    8.0F,
                    Level.ExplosionInteraction.BLOCK);

            ListTag recoveries = CoreStorageRepository.get(level)
                    .save(new CompoundTag(), level.registryAccess())
                    .getList("recoveries", Tag.TAG_COMPOUND);
            int commandRecoveries = 0;
            int explosionRecoveries = 0;
            for (int index = 0; index < recoveries.size(); index++) {
                CompoundTag recovery = recoveries.getCompound(index);
                if (recovery.getLong("originPos") == commandCorePos.asLong()
                        && recovery.getLong("itemCount") == 1L
                        && !recovery.contains("owner")) {
                    commandRecoveries++;
                }
                if (recovery.getLong("originPos") == explosionCorePos.asLong()
                        && recovery.getLong("itemCount") == 2L
                        && recovery.hasUUID("owner")
                        && recovery.getUUID("owner").equals(explosionOwner)
                        && recovery.getUUID("id").equals(explosionRecoveryId)) {
                    explosionRecoveries++;
                }
            }
            if (commandRecoveries != 1 || explosionRecoveries != 1) {
                helper.fail("Non-player removal recovery mappings were missing, duplicated, or reassigned: "
                        + "command=" + commandRecoveries + ", explosion=" + explosionRecoveries);
                return;
            }
            helper.succeed();
        });
    }

    private static boolean inventoryContainsRecovery(
            net.minecraft.world.entity.player.Player player,
            UUID recoveryId
    ) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (StorageCoreBlockItem.getRecoveryId(player.getInventory().getItem(slot))
                    .filter(recoveryId::equals)
                    .isPresent()) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = "platform")
    public static void core_reference_round_trip_resolves_repository_identity(GameTestHelper helper) {
        var level = helper.getLevel();
        var firstPos = helper.absolutePos(new BlockPos(1, 3, 1));
        var secondPos = helper.absolutePos(new BlockPos(4, 3, 1));
        level.setBlock(firstPos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(secondPos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(firstPos) instanceof StorageCoreBlockEntity first)
                    || !(level.getBlockEntity(secondPos) instanceof StorageCoreBlockEntity second)) {
                helper.fail("Core block entities not found");
                return;
            }
            UUID firstStorageId = first.getStorageId().orElseThrow();
            UUID secondStorageId = second.getStorageId().orElseThrow();
            if (firstStorageId.equals(secondStorageId)
                    || first.getNetworkId().equals(second.getNetworkId())) {
                helper.fail("Fresh Cores must have distinct storage and network identities");
                return;
            }
            CompoundTag reference = new CompoundTag();
            first.saveAdditional(reference, level.registryAccess());
            UUID referencedStorageId = reference.getUUID("storageId");
            CoreStorageRecord referenced = CoreStorageRepository.get(level)
                    .getRecord(referencedStorageId).orElseThrow();
            if (!referencedStorageId.equals(firstStorageId)
                    || !referenced.networkId().equals(first.getNetworkId())) {
                helper.fail("Chunk reference did not resolve the original repository identity");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void packed_core_token_moves_record_without_block_entity_payload(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var restoredPos = helper.absolutePos(new BlockPos(4, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core BE not found");
                return;
            }
            core.rebuildNetwork(level);
            core.insertItemCount(ItemKey.of(new ItemStack(Items.STONE)), 128, Action.EXECUTE, Actor.EMPTY);
            core.insertItemCount(ItemKey.of(new ItemStack(Items.DIRT)), 50, Action.EXECUTE, Actor.EMPTY);
            core.getMachineContainer().setItem(MachineEnergyTable.FURNACE_SLOT, new ItemStack(Items.FURNACE, 4));
            UUID storageId = core.getStorageId().orElseThrow();
            UUID networkId = core.getNetworkId();

            ItemStack packedCore = Block.getDrops(level.getBlockState(corePos), level, corePos, core)
                    .stream()
                    .filter(stack -> stack.is(MagicStorage.STORAGE_CORE_ITEM.get()))
                    .findFirst().map(ItemStack::copy).orElse(ItemStack.EMPTY);
            if (packedCore.isEmpty() || StorageCoreBlockItem.getRecoveryId(packedCore).isEmpty()
                    || packedCore.has(DataComponents.BLOCK_ENTITY_DATA)) {
                helper.fail("Packed Core must carry only a recovery token and summary");
                return;
            }
            level.removeBlock(corePos, false);
            level.setBlock(restoredPos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(restoredPos.east(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
            MagicStorage.STORAGE_CORE.get().setPlacedBy(
                    level, restoredPos, level.getBlockState(restoredPos), null, packedCore);
            if (!(level.getBlockEntity(restoredPos) instanceof StorageCoreBlockEntity restored)
                    || !restored.getStorageId().orElseThrow().equals(storageId)
                    || !restored.getNetworkId().equals(networkId)
                    || restored.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 128
                    || restored.getItemCount(ItemKey.of(new ItemStack(Items.DIRT))) != 50
                    || restored.getMachineContainer().getItem(MachineEnergyTable.FURNACE_SLOT).getCount() != 4) {
                helper.fail("Packed Core did not reattach the exact repository record");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void runtime_long_counts_saturate_without_nbt_injection(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            ItemKey stone = ItemKey.of(new ItemStack(Items.STONE));
            ItemStack namedStone = new ItemStack(Items.STONE);
            namedStone.set(DataComponents.CUSTOM_NAME, Component.literal("Second Variant"));
            ItemKey secondVariant = ItemKey.of(namedStone);
            if (core.insertItemCount(stone, Long.MAX_VALUE, Action.EXECUTE, Actor.EMPTY) != Long.MAX_VALUE
                    || core.insertItemCount(stone, 1, Action.EXECUTE, Actor.EMPTY) != 0
                    || core.insertItemCount(secondVariant, 1, Action.EXECUTE, Actor.EMPTY) != 1
                    || core.countMatching(stack -> stack.is(Items.STONE)) != Long.MAX_VALUE) {
                helper.fail("Long-count storage wrapped, accepted overflow, or failed to saturate fuzzy totals");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void record_machine_components_and_descriptor_state_round_trip(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        CoreStorageRecord record = CoreStorageRecord.fresh(UUID.randomUUID());
        ItemStack namedFurnaces = new ItemStack(Items.FURNACE, Integer.MAX_VALUE);
        namedFurnaces.set(DataComponents.CUSTOM_NAME, Component.literal("Arc Furnaces"));
        record.machines().setItem(MachineEnergyTable.FURNACE_SLOT, namedFurnaces.copy());
        record.setDescriptorAmount(MachineEnergyTable.AXE_ID, 74L);
        record.infiniteDescriptors().add(ResourceLocation.fromNamespaceAndPath(
                MagicStorage.MODID, "test_infinite_descriptor"));
        CoreStorageRecord.LoadResult loaded = CoreStorageRecord.load(record.save(registries), registries);
        if (!loaded.success()) {
            helper.fail("Record failed to reload: " + loaded.error());
            return;
        }
        ItemStack restored = loaded.record().machines().getItem(MachineEnergyTable.FURNACE_SLOT);
        if (!ItemStack.isSameItemSameComponents(restored, namedFurnaces)
                || restored.getCount() != Integer.MAX_VALUE
                || loaded.record().descriptorAmount(MachineEnergyTable.AXE_ID) != 74
                || loaded.record().unresolvedDescriptorEntries().size() != 1) {
            helper.fail("Record lost machine components or descriptor state");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void energy_at_long_max_rejects_generation_and_fuel(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            CoreStorageRecord record = core.storageRecordForTesting();
            record.setEnergyAmount(EnergyType.SMELTING_ENERGY, Long.MAX_VALUE);
            record.setEnergyAmount(EnergyType.FURNACE_FUEL, Long.MAX_VALUE);
            record.machines().setItem(MachineEnergyTable.FURNACE_SLOT, new ItemStack(Items.FURNACE));
            core.tick();
            ItemStack coal = new ItemStack(Items.COAL);
            if (core.getEnergy(EnergyType.SMELTING_ENERGY) != Long.MAX_VALUE
                    || core.addFuel(coal, EnergyType.FURNACE_FUEL)
                    || coal.getCount() != 1
                    || core.getEnergy(EnergyType.FURNACE_FUEL) != Long.MAX_VALUE) {
                helper.fail("Long.MAX_VALUE energy wrapped or consumed rejected fuel");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void export_bus_filter_nbt_preserves_components(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.runAfterDelay(1, () -> {
            var registries = level.registryAccess();
            var busPos = helper.absolutePos(new BlockPos(1, 3, 1));
            var busState = MagicStorage.EXPORT_BUS.get().defaultBlockState();

            var sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(
                registries.registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SHARPNESS),
                1
            );

            var bus = new ExportBusBlockEntity(busPos, busState);
            bus.setFilter(sword.copy());

            ItemKey filterBefore = bus.getFilter();
            if (filterBefore == null) { helper.fail("Filter not set"); return; }

            var tag = new CompoundTag();
            bus.saveAdditional(tag, registries);

            var bus2 = new ExportBusBlockEntity(busPos, busState);
            bus2.loadAdditional(tag, registries);

            ItemKey filterAfter = bus2.getFilter();
            if (filterAfter == null) { helper.fail("Filter lost after NBT load"); return; }
            if (!filterAfter.toStack(1).isEnchanted()) {
                helper.fail("Filter components (enchantment) were lost after NBT round-trip");
                return;
            }
            if (!filterAfter.equals(filterBefore)) {
                helper.fail("Filter ItemKey changed after NBT round-trip");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void advanced_search_at_mod(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));
            // @minecraft should match stone, @magic_storage should not
            var byMinecraft = core.getDisplayStacks("@minecraft");
            if (byMinecraft.size() < 1) helper.fail("@minecraft should match vanilla items");
            var byMod = core.getDisplayStacks("@magic_storage");
            if (!byMod.isEmpty()) helper.fail("@magic_storage should not match vanilla items");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void advanced_search_empty_filter(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 1));
            core.insertItem(new ItemStack(Items.DIRT, 1));
            // null and blank filter should show all
            if (core.getDisplayStacks(null).size() != 2) helper.fail("null filter should show all");
            if (core.getDisplayStacks("").size() != 2) helper.fail("empty filter should show all");
            if (core.getDisplayStacks("   ").size() != 2) helper.fail("blank filter should show all");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void fuzzy_match_across_variants(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            var be = level.getBlockEntity(corePos);
            if (!(be instanceof StorageCoreBlockEntity core)) { helper.fail("Core not found"); return; }
            core.rebuildNetwork(level);
            var enchanted = new ItemStack(Items.DIAMOND_SWORD);
            enchanted.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.SHARPNESS), 1);
            core.insertItem(enchanted);
            core.insertItem(new ItemStack(Items.DIAMOND_SWORD));
            if (core.getTypeCount() != 2) { helper.fail("expected 2 variants, got " + core.getTypeCount()); return; }
            long fuzzy = core.countMatching(s -> s.is(Items.DIAMOND_SWORD));
            if (fuzzy != 2) { helper.fail("fuzzy count should be 2, got " + fuzzy); return; }
            long exact = core.countMatching(s -> ItemStack.isSameItemSameComponents(s, new ItemStack(Items.DIAMOND_SWORD)));
            if (exact != 1) { helper.fail("exact count should be 1, got " + exact); return; }
            long got = core.extractMatching(s -> s.is(Items.DIAMOND_SWORD), 1, false);
            if (got != 1) { helper.fail("should extract 1, got " + got); return; }
            if (core.countMatching(s -> s.is(Items.DIAMOND_SWORD)) != 1) { helper.fail("1 should remain"); return; }
            helper.succeed();
        });
    }

    private static CompoundTag unresolvedInventoryEntry(
            net.minecraft.core.HolderLookup.Provider registries,
            String itemId,
            long count
    ) {
        CompoundTag rawItem = (CompoundTag) new ItemStack(Items.PAPER).save(registries);
        rawItem.putString("id", itemId);
        CompoundTag entry = new CompoundTag();
        entry.put("item", rawItem);
        entry.putLong("count", count);
        return entry;
    }

    private static CompoundTag unresolvedMachineEntry(
            net.minecraft.core.HolderLookup.Provider registries,
            String descriptorId,
            String itemId,
            int count
    ) {
        CompoundTag rawItem = (CompoundTag) new ItemStack(Items.FURNACE, count).save(registries);
        rawItem.putString("id", itemId);
        CompoundTag entry = new CompoundTag();
        entry.putString("descriptorId", descriptorId);
        entry.put("item", rawItem);
        return entry;
    }
}
