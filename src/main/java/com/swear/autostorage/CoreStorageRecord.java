package com.swear.autostorage;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

final class CoreStorageRecord {
    static final int MAX_SEGMENT_TYPES = 63;

    static final String TAG_STORAGE_ID = "storageId";
    static final String TAG_NETWORK_ID = "networkId";
    static final String TAG_ENERGY = "energy";
    static final String TAG_DESCRIPTOR_CONSUMABLES = "descriptorConsumables";
    static final String TAG_MACHINE_DESCRIPTORS = "machineDescriptors";
    static final String TAG_MACHINE_WORK = "machineWork";
    static final String TAG_INVENTORY_SEGMENTS = "inventorySegments";
    static final String TAG_RESOURCE_LEDGER = "resourceLedger";
    static final String TAG_ENTRIES = "entries";
    static final String TAG_DESCRIPTOR_ID = "descriptorId";
    static final String TAG_ITEM = "item";
    static final String TAG_COUNT = "count";
    static final String TAG_AMOUNT = "amount";
    static final String TAG_INFINITE = "infinite";
    static final String TAG_VARIANT_ITEM_ID = "variantItemId";
    static final String TAG_RATE_NUMERATOR = "rateNumerator";
    static final String TAG_RATE_DENOMINATOR = "rateDenominator";
    static final String TAG_REMAINDER = "remainder";

    private final UUID storageId;
    private final UUID networkId;
    private final Set<ResourceLocation> infiniteDescriptors = new java.util.HashSet<>();
    private final Map<ResourceLocation, MachineWorkAccumulator.Remainder> machineWorkRemainders =
            new java.util.HashMap<>();
    private final List<CompoundTag> unresolvedDescriptorEntries = new ArrayList<>();
    private final List<CompoundTag> unresolvedMachineEntries = new ArrayList<>();
    private final List<CompoundTag> unresolvedMachineWorkEntries = new ArrayList<>();
    private StorageResourceLedger resourceLedger = new StorageResourceLedger();
    private final List<CompoundTag> unresolvedInventoryEntries = new ArrayList<>();
    private final SimpleContainer machines;

    private Runnable dirtyCallback = () -> {
    };
    private Runnable machineMutationCallback = this::markChanged;
    private boolean suppressMachineCallback;

    private CoreStorageRecord(UUID storageId, UUID networkId) {
        this.storageId = java.util.Objects.requireNonNull(storageId, "storageId");
        this.networkId = java.util.Objects.requireNonNull(networkId, "networkId");
        machines = new SimpleContainer(MachineDescriptorApi.MAX_DESCRIPTORS) {
            @Override
            public int getMaxStackSize() {
                return MachineDescriptorApi.MAX_INSTALLED_COUNT;
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return MachineDescriptorApi.MAX_INSTALLED_COUNT;
            }

            @Override
            public void setItem(int slot, ItemStack stack) {
                MachineDescriptor descriptor = MachineEnergyTable.get(slot);
                if (descriptor != null && descriptor.category() != MachineEnergyTable.Category.TRANSFORM) {
                    stack.limitSize(descriptor.maxInstalledCount());
                }
                super.setItem(slot, stack);
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                MachineDescriptor descriptor = MachineEnergyTable.get(slot);
                return descriptor != null
                        && descriptor.category() != MachineEnergyTable.Category.TRANSFORM
                        && descriptor.maxInstalledCount() > 0
                        && descriptor.accepts(stack);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (!suppressMachineCallback) {
                    machineMutationCallback.run();
                }
            }
        };
    }

    static CoreStorageRecord fresh(UUID storageId) {
        return new CoreStorageRecord(storageId, UUID.randomUUID());
    }

    static LoadResult load(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag raw = tag.copy();
        UUID storageId = tag.hasUUID(TAG_STORAGE_ID) ? tag.getUUID(TAG_STORAGE_ID) : null;
        if (storageId == null) {
            return LoadResult.failure(null, raw, "missing storageId");
        }
        if (!tag.hasUUID(TAG_NETWORK_ID)) {
            return LoadResult.failure(storageId, raw, "missing networkId");
        }
        if (!tag.contains(TAG_ENERGY, Tag.TAG_COMPOUND)
                || !tag.contains(TAG_DESCRIPTOR_CONSUMABLES, Tag.TAG_LIST)
                || !tag.contains(TAG_MACHINE_DESCRIPTORS, Tag.TAG_LIST)
                || !tag.contains(TAG_INVENTORY_SEGMENTS, Tag.TAG_LIST)) {
            return LoadResult.failure(storageId, raw, "missing mandatory record payload");
        }
        ListTag descriptorEntries = compoundList(tag, TAG_DESCRIPTOR_CONSUMABLES);
        ListTag machineEntries = compoundList(tag, TAG_MACHINE_DESCRIPTORS);
        ListTag inventorySegments = compoundList(tag, TAG_INVENTORY_SEGMENTS);
        if (descriptorEntries == null || machineEntries == null || inventorySegments == null) {
            return LoadResult.failure(storageId, raw, "mandatory record list has non-compound elements");
        }

        CoreStorageRecord record = new CoreStorageRecord(storageId, tag.getUUID(TAG_NETWORK_ID));
        try {
            if (tag.contains(TAG_RESOURCE_LEDGER)) {
                if (!tag.contains(TAG_RESOURCE_LEDGER, Tag.TAG_COMPOUND)) {
                    return LoadResult.failure(storageId, raw, "typed resource ledger is not a compound");
                }
                record.resourceLedger = StorageResourceLedger.load(
                        tag.getCompound(TAG_RESOURCE_LEDGER));
            }
            String energyError = record.loadEnergy(tag.getCompound(TAG_ENERGY));
            if (energyError != null) {
                return LoadResult.failure(storageId, raw, energyError);
            }
            record.loadDescriptorEntries(descriptorEntries);
            record.loadMachineEntries(machineEntries, registries);
            if (tag.contains(TAG_MACHINE_WORK)) {
                if (!tag.contains(TAG_MACHINE_WORK, Tag.TAG_LIST)) {
                    return LoadResult.failure(storageId, raw, "machine work payload is not a list");
                }
                ListTag workEntries = compoundList(tag, TAG_MACHINE_WORK);
                if (workEntries == null) {
                    return LoadResult.failure(storageId, raw,
                            "machine work list has non-compound elements");
                }
                record.loadMachineWorkEntries(workEntries);
            }
            String inventoryError = record.loadInventorySegments(inventorySegments, registries);
            if (inventoryError != null) {
                return LoadResult.failure(storageId, raw, inventoryError);
            }
            return LoadResult.success(record);
        } catch (RuntimeException exception) {
            return LoadResult.failure(storageId, raw,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_STORAGE_ID, storageId);
        tag.putUUID(TAG_NETWORK_ID, networkId);

        CompoundTag energyTag = new CompoundTag();
        for (EnergyType type : EnergyType.values()) {
            energyTag.putLong(type.getId(), 0);
        }
        tag.put(TAG_ENERGY, energyTag);

        ListTag descriptorTags = new ListTag();
        Set<ResourceLocation> descriptorIds = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        descriptorIds.addAll(infiniteDescriptors);
        for (ResourceLocation descriptorId : descriptorIds) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_DESCRIPTOR_ID, descriptorId.toString());
            entry.putLong(TAG_AMOUNT, 0);
            entry.putBoolean(TAG_INFINITE, infiniteDescriptors.contains(descriptorId));
            descriptorTags.add(entry);
        }
        unresolvedDescriptorEntries.forEach(entry -> descriptorTags.add(entry.copy()));
        tag.put(TAG_DESCRIPTOR_CONSUMABLES, descriptorTags);

        ListTag machineTags = new ListTag();
        List<MachineDescriptor> descriptors = MachineEnergyTable.entries();
        for (int slot = 0; slot < descriptors.size(); slot++) {
            ItemStack stack = machines.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_DESCRIPTOR_ID, descriptors.get(slot).id().toString());
            entry.put(TAG_ITEM, stack.copyWithCount(1).save(registries));
            entry.putLong(TAG_COUNT, stack.getCount());
            machineTags.add(entry);
        }
        unresolvedMachineEntries.forEach(entry -> machineTags.add(entry.copy()));
        tag.put(TAG_MACHINE_DESCRIPTORS, machineTags);

        ListTag machineWorkTags = new ListTag();
        Set<ResourceLocation> workIds = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        workIds.addAll(machineWorkRemainders.keySet());
        for (ResourceLocation descriptorId : workIds) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_DESCRIPTOR_ID, descriptorId.toString());
            entry.putLong(TAG_AMOUNT, 0);
            MachineWorkAccumulator.Remainder remainder = machineWorkRemainders.get(descriptorId);
            if (remainder != null) {
                entry.putString(TAG_VARIANT_ITEM_ID, remainder.variantItemId().toString());
                entry.putLong(TAG_RATE_NUMERATOR, remainder.rate().numerator());
                entry.putLong(TAG_RATE_DENOMINATOR, remainder.rate().denominator());
                entry.putLong(TAG_REMAINDER, remainder.remainder());
            }
            machineWorkTags.add(entry);
        }
        unresolvedMachineWorkEntries.forEach(entry -> machineWorkTags.add(entry.copy()));
        tag.put(TAG_MACHINE_WORK, machineWorkTags);

        List<CompoundTag> inventoryEntries = new ArrayList<>();
        Set<StorageResourceKey> persistedAsInventory = new HashSet<>();
        resourceLedger.forEach((resourceKey, amount) -> {
            if (!resourceKey.kindId().equals(StorageResourceBridge.ITEM_KIND)) return;
            var itemKey = StorageResourceBridge.itemKey(resourceKey, registries);
            if (itemKey.isEmpty()) return;
            CompoundTag inventoryEntry = new CompoundTag();
            inventoryEntry.put(TAG_ITEM, itemKey.get().toStack(1).save(registries));
            inventoryEntry.putLong(TAG_COUNT, amount);
            inventoryEntries.add(inventoryEntry);
            persistedAsInventory.add(resourceKey);
        });
        inventoryEntries.sort(Comparator
                .comparing((CompoundTag entry) -> entry.getCompound(TAG_ITEM).getString("id"))
                .thenComparing(entry -> entry.getCompound(TAG_ITEM).toString())
                .thenComparingLong(entry -> entry.getLong(TAG_COUNT)));
        unresolvedInventoryEntries.forEach(entry -> inventoryEntries.add(entry.copy()));

        ListTag segments = new ListTag();
        for (int start = 0; start < inventoryEntries.size(); start += MAX_SEGMENT_TYPES) {
            ListTag entries = new ListTag();
            int end = Math.min(inventoryEntries.size(), start + MAX_SEGMENT_TYPES);
            for (int index = start; index < end; index++) {
                entries.add(inventoryEntries.get(index));
            }
            CompoundTag segment = new CompoundTag();
            segment.put(TAG_ENTRIES, entries);
            segments.add(segment);
        }
        tag.put(TAG_INVENTORY_SEGMENTS, segments);
        tag.put(TAG_RESOURCE_LEDGER, resourceLedger.save(
                key -> !persistedAsInventory.contains(key)));
        return tag;
    }

    private String loadEnergy(CompoundTag tag) {
        for (EnergyType type : EnergyType.values()) {
            if (!tag.contains(type.getId(), Tag.TAG_LONG) || tag.getLong(type.getId()) < 0) {
                return "invalid energy field " + type.getId();
            }
            long amount = tag.getLong(type.getId());
            if (amount > 0 && !migrateLegacyAmount(
                    StorageResourceBridge.energyKey(type), amount)) {
                return "energy exists in both legacy and typed storage for " + type.getId();
            }
        }
        return null;
    }

    private void loadDescriptorEntries(ListTag entries) {
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            ResourceLocation descriptorId = ResourceLocation.tryParse(entry.getString(TAG_DESCRIPTOR_ID));
            MachineDescriptor descriptor = descriptorId == null ? null : MachineEnergyTable.get(descriptorId);
            if (descriptor == null || descriptor.category() != MachineEnergyTable.Category.TRANSFORM
                    || !entry.contains(TAG_AMOUNT, Tag.TAG_LONG)
                    || !entry.contains(TAG_INFINITE, Tag.TAG_BYTE)
                    || entry.getLong(TAG_AMOUNT) < 0
                    || entry.getByte(TAG_INFINITE) != 0 && entry.getByte(TAG_INFINITE) != 1) {
                unresolvedDescriptorEntries.add(entry.copy());
                continue;
            }
            if (entry.getBoolean(TAG_INFINITE)) {
                infiniteDescriptors.add(descriptorId);
                continue;
            }
            long amount = entry.getLong(TAG_AMOUNT);
            if (amount <= 0) {
                continue;
            }
            if (!migrateLegacyAmount(StorageResourceBridge.descriptorKey(descriptorId), amount)) {
                throw new IllegalArgumentException(
                        "descriptor exists in both legacy and typed storage for " + descriptorId);
            }
        }
    }

    private void loadMachineEntries(ListTag entries, HolderLookup.Provider registries) {
        suppressMachineCallback = true;
        try {
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entry = entries.getCompound(index);
                if (!entry.contains(TAG_ITEM, Tag.TAG_COMPOUND)) {
                    unresolvedMachineEntries.add(entry.copy());
                    continue;
                }
                ResourceLocation descriptorId = ResourceLocation.tryParse(entry.getString(TAG_DESCRIPTOR_ID));
                MachineDescriptor descriptor = descriptorId == null ? null : MachineEnergyTable.get(descriptorId);
                ItemStack stack = parsePersistedItem(entry.getCompound(TAG_ITEM), registries);
                long persistedCount = entry.contains(TAG_COUNT, Tag.TAG_LONG)
                        ? entry.getLong(TAG_COUNT) : stack.getCount();
                int slot = descriptorId == null ? -1 : MachineEnergyTable.findSlot(descriptorId);
                if (descriptor == null || descriptor.category() == MachineEnergyTable.Category.TRANSFORM
                        || stack.isEmpty() || slot < 0 || !descriptor.accepts(stack)
                        || persistedCount <= 0 || persistedCount > Integer.MAX_VALUE) {
                    unresolvedMachineEntries.add(entry.copy());
                    continue;
                }
                stack.setCount((int) persistedCount);

                ItemStack existing = machines.getItem(slot);
                int room = Math.max(0, descriptor.maxInstalledCount() - existing.getCount());
                int accepted = Math.min(room, stack.getCount());
                if (accepted > 0) {
                    machines.setItem(slot, stack.copyWithCount(existing.getCount() + accepted));
                }
                if (accepted < stack.getCount()) {
                    CompoundTag remainder = entry.copy();
                    remainder.put(TAG_ITEM, stack.copyWithCount(1).save(registries));
                    remainder.putLong(TAG_COUNT, stack.getCount() - accepted);
                    unresolvedMachineEntries.add(remainder);
                }
            }
        } finally {
            suppressMachineCallback = false;
        }
    }

    private void loadMachineWorkEntries(ListTag entries) {
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            ResourceLocation descriptorId = ResourceLocation.tryParse(entry.getString(TAG_DESCRIPTOR_ID));
            MachineDescriptor descriptor = descriptorId == null ? null : MachineEnergyTable.get(descriptorId);
            if (descriptor == null || descriptor.category() != MachineEnergyTable.Category.PROCESS
                    || !entry.contains(TAG_AMOUNT, Tag.TAG_LONG)
                    || entry.getLong(TAG_AMOUNT) < 0) {
                unresolvedMachineWorkEntries.add(entry.copy());
                continue;
            }
            long amount = entry.getLong(TAG_AMOUNT);
            if (descriptor.energyType() != null && amount > 0) {
                unresolvedMachineWorkEntries.add(entry.copy());
                continue;
            }
            boolean hasRemainder = entry.contains(TAG_VARIANT_ITEM_ID, Tag.TAG_STRING)
                    || entry.contains(TAG_RATE_NUMERATOR, Tag.TAG_LONG)
                    || entry.contains(TAG_RATE_DENOMINATOR, Tag.TAG_LONG)
                    || entry.contains(TAG_REMAINDER, Tag.TAG_LONG);
            MachineWorkAccumulator.Remainder remainder = null;
            if (hasRemainder) {
                ResourceLocation variantId = ResourceLocation.tryParse(
                        entry.getString(TAG_VARIANT_ITEM_ID));
                try {
                    remainder = new MachineWorkAccumulator.Remainder(
                            variantId,
                            MachineWorkRate.of(
                                    entry.getLong(TAG_RATE_NUMERATOR),
                                    entry.getLong(TAG_RATE_DENOMINATOR)),
                            entry.getLong(TAG_REMAINDER));
                } catch (RuntimeException exception) {
                    unresolvedMachineWorkEntries.add(entry.copy());
                    continue;
                }
            }
            if (amount > 0 && !migrateLegacyAmount(
                    StorageResourceBridge.stationWorkKey(descriptorId), amount)) {
                throw new IllegalArgumentException(
                        "station work exists in both legacy and typed storage for " + descriptorId);
            }
            if (remainder != null) machineWorkRemainders.put(descriptorId, remainder);
        }
    }

    private boolean migrateLegacyAmount(StorageResourceKey key, long amount) {
        if (resourceLedger.amount(key) > 0) return false;
        return resourceLedger.insert(
                key,
                amount,
                StorageTypeCapacity.unlimitedCapacity(),
                Action.EXECUTE) == amount;
    }

    private String loadInventorySegments(ListTag segments, HolderLookup.Provider registries) {
        Set<StorageResourceKey> loadedLegacyKeys = new HashSet<>();
        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            CompoundTag segment = segments.getCompound(segmentIndex);
            if (!segment.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
                return "inventory segment " + segmentIndex + " has no entries list";
            }
            ListTag entries = compoundList(segment, TAG_ENTRIES);
            if (entries == null) {
                return "inventory segment " + segmentIndex + " entries have non-compound elements";
            }
            if (entries.size() > MAX_SEGMENT_TYPES) {
                return "inventory segment " + segmentIndex + " exceeds " + MAX_SEGMENT_TYPES + " types";
            }
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                CompoundTag entry = entries.getCompound(entryIndex);
                if (!entry.contains(TAG_ITEM, Tag.TAG_COMPOUND)) {
                    return "inventory entry has no item";
                }
                if (!entry.contains(TAG_COUNT, Tag.TAG_LONG)) {
                    return "inventory entry count is not a long";
                }
                long count = entry.getLong(TAG_COUNT);
                if (count <= 0) {
                    return "inventory entry has non-positive count";
                }
                ItemStack stack = parsePersistedItem(entry.getCompound(TAG_ITEM), registries);
                if (stack.isEmpty()) {
                    unresolvedInventoryEntries.add(entry.copy());
                    continue;
                }
                StorageResourceKey key = StorageResourceBridge.itemKey(
                        ItemKey.of(stack), registries);
                if (resourceLedger.amount(key) > 0 && !loadedLegacyKeys.contains(key)) {
                    return "item exists in both inventory segments and typed resource ledger";
                }
                putItemInternal(key, count);
                loadedLegacyKeys.add(key);
            }
        }
        return null;
    }

    private static ListTag compoundList(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list)) {
            return null;
        }
        return list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND ? list : null;
    }

    private static ItemStack parsePersistedItem(
            CompoundTag itemTag,
            HolderLookup.Provider registries
    ) {
        ResourceLocation itemId = ResourceLocation.tryParse(itemTag.getString("id"));
        if (itemId == null || registries.lookupOrThrow(Registries.ITEM)
                .get(ResourceKey.create(Registries.ITEM, itemId)).isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY);
    }

    UUID storageId() {
        return storageId;
    }

    UUID networkId() {
        return networkId;
    }

    long energyAmount(EnergyType type) {
        return resourceLedger.amount(StorageResourceBridge.energyKey(type));
    }

    void setEnergyAmount(EnergyType type, long amount) {
        setResourceAmount(StorageResourceBridge.energyKey(type), amount);
    }

    SimpleContainer machines() {
        return machines;
    }

    long descriptorAmount(ResourceLocation descriptorId) {
        return resourceLedger.amount(StorageResourceBridge.descriptorKey(descriptorId));
    }

    void setDescriptorAmount(ResourceLocation descriptorId, long amount) {
        setResourceAmount(StorageResourceBridge.descriptorKey(descriptorId), amount);
    }

    Set<ResourceLocation> infiniteDescriptors() {
        return infiniteDescriptors;
    }

    boolean hasStationWork() {
        return resourceLedger.keys(StorageResourceBridge.WORK_KIND).stream()
                .anyMatch(key -> StorageResourceBridge.stationWorkDescriptorId(key).isPresent());
    }

    Map<ResourceLocation, MachineWorkAccumulator.Remainder> machineWorkRemainders() {
        return machineWorkRemainders;
    }

    List<CompoundTag> unresolvedDescriptorEntries() {
        return unresolvedDescriptorEntries;
    }

    List<CompoundTag> unresolvedMachineEntries() {
        return unresolvedMachineEntries;
    }

    List<CompoundTag> unresolvedMachineWorkEntries() {
        return unresolvedMachineWorkEntries;
    }

    StorageResourceLedger resourceLedger() {
        return resourceLedger;
    }

    List<CompoundTag> unresolvedInventoryEntries() {
        return unresolvedInventoryEntries;
    }

    int typeCount() {
        long count = unresolvedInventoryEntries.size()
                + (long) resourceLedger.typeCount()
                - resourceLedger.typeCount(StorageResourceBridge.WORK_KIND);
        if (count >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) count;
    }

    long itemCount() {
        long total = resourceLedger.totalAmount(StorageResourceBridge.ITEM_KIND);
        for (CompoundTag unresolved : unresolvedInventoryEntries) {
            total = saturatingAdd(total, Math.max(0, unresolved.getLong(TAG_COUNT)));
        }
        return total;
    }

    long getItemCount(ItemKey key, HolderLookup.Provider registries) {
        return resourceLedger.amount(StorageResourceBridge.itemKey(key, registries));
    }

    void putItem(ItemKey key, long amount, HolderLookup.Provider registries) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Core storage amount must be positive");
        }
        putItemInternal(StorageResourceBridge.itemKey(key, registries), amount);
        markChanged();
    }

    private void putItemInternal(StorageResourceKey key, long amount) {
        resourceLedger.insert(
                key, amount, StorageTypeCapacity.unlimitedCapacity(), Action.EXECUTE);
    }

    private void setResourceAmount(StorageResourceKey key, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Core storage amount cannot be negative");
        }
        long current = resourceLedger.amount(key);
        if (current == amount) return;
        if (!resourceLedger.applyExact(
                Map.of(key, amount - current),
                StorageTypeCapacity.unlimitedCapacity(),
                Action.EXECUTE)) {
            throw new IllegalArgumentException("Core storage amount cannot be represented");
        }
        markChanged();
    }

    boolean isEmpty() {
        if (!resourceLedger.isEmpty()
                || !machines.isEmpty()
                || !infiniteDescriptors.isEmpty() || !unresolvedDescriptorEntries.isEmpty()
                || !machineWorkRemainders.isEmpty()
                || !unresolvedMachineEntries.isEmpty() || !unresolvedMachineWorkEntries.isEmpty()
                || !unresolvedInventoryEntries.isEmpty()) {
            return false;
        }
        return true;
    }

    void setDirtyCallback(Runnable callback) {
        dirtyCallback = java.util.Objects.requireNonNull(callback, "callback");
    }

    void setMachineMutationCallback(Runnable callback) {
        machineMutationCallback = java.util.Objects.requireNonNull(callback, "callback");
    }

    void clearMachineMutationCallback() {
        machineMutationCallback = this::markChanged;
    }

    void markChanged() {
        dirtyCallback.run();
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0) {
            return left;
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    record LoadResult(CoreStorageRecord record, UUID storageId, CompoundTag raw, String error) {
        static LoadResult success(CoreStorageRecord record) {
            return new LoadResult(record, record.storageId(), null, null);
        }

        static LoadResult failure(UUID storageId, CompoundTag raw, String error) {
            return new LoadResult(null, storageId, raw.copy(), error);
        }

        boolean success() {
            return record != null;
        }
    }
}
