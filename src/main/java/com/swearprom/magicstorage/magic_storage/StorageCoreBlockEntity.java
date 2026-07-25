package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Predicate;

public class StorageCoreBlockEntity extends BlockEntity {

    private static final String TAG_STORAGE_ID = "storageId";
    private static final String TAG_STORAGE_SCHEMA = "storageSchema";
    private static final int STORAGE_SCHEMA = 1;

    private final Set<BlockPos> connectedBlocks = new HashSet<>();
    private final UUID attachmentToken = UUID.randomUUID();
    private UUID storageId;
    private int storageSchema;
    private CoreStorageRecord storageRecord;
    private StorageAvailability storageAvailability = StorageAvailability.UNINITIALIZED;
    private UUID networkId;
    private boolean conflicted = false;
    private long topologyRevision;
    private StorageTypeCapacity typeCapacity = StorageTypeCapacity.zero();
    private int typeCount = 0;
    private long machineRevision;
    private Set<ResourceLocation> infiniteDescriptors = new HashSet<>();
    private Map<ResourceLocation, MachineWorkAccumulator.Remainder> machineWorkRemainders =
            new HashMap<>();
    private UUID preparedRecoveryId;

    private SimpleContainer machines = new SimpleContainer(MachineDescriptorApi.MAX_DESCRIPTORS);

    private StorageResourceLedger resourceLedger = new StorageResourceLedger();
    private final Map<ItemKey, Long> flatCache = new HashMap<>();
    private final Map<ItemKey, TerminalSearchEntry> itemSearchCache = new HashMap<>();
    private final CoreStorageResourceHandler resourceHandler = new CoreStorageResourceHandler(this);
    private final CoreFluidHandler fluidHandler = new CoreFluidHandler(this);
    private final CoreEnergyStorage energyStorage = new CoreEnergyStorage(this);
    private final List<StorageListener> listeners = new ArrayList<>();
    private final List<Runnable> deferredListenerEvents = new ArrayList<>();
    private int mutationBatchDepth;
    private boolean storageChangedInBatch;
    private boolean cacheDirty = true;

    public StorageCoreBlockEntity(BlockPos pos, BlockState state) {
        super(MagicStorage.STORAGE_CORE_BE.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide() || conflicted || !isStorageAvailable()) return;
        boolean remainderChanged = false;
        Map<StorageResourceKey, Long> workDeltas = new HashMap<>();
        List<MachineDescriptor> descriptors = MachineEnergyTable.entries();
        for (int slot = 0; slot < descriptors.size(); slot++) {
            MachineDescriptor entry = MachineEnergyTable.get(slot);
            ItemStack machineStack = machines.getItem(slot);
            if (entry == null || entry.category() != MachineEnergyTable.Category.PROCESS
                    || !entry.accepts(machineStack)) {
                if (entry != null && machineWorkRemainders.remove(entry.id()) != null) {
                    remainderChanged = true;
                }
                continue;
            }
            MachineWorkRate rate = entry.rateFor(machineStack).orElse(null);
            if (rate == null || rate.isZero()) {
                if (machineWorkRemainders.remove(entry.id()) != null) remainderChanged = true;
                continue;
            }
            MachineWorkAccumulator.Remainder previous = machineWorkRemainders.get(entry.id());
            MachineWorkAccumulator.Advance advance = MachineWorkAccumulator.advance(
                    previous,
                    BuiltInRegistries.ITEM.getKey(machineStack.getItem()),
                    rate,
                    machineStack.getCount());
            if (advance.remainder().remainder() == 0) {
                if (machineWorkRemainders.remove(entry.id()) != null) remainderChanged = true;
            } else if (!advance.remainder().equals(previous)) {
                machineWorkRemainders.put(entry.id(), advance.remainder());
                remainderChanged = true;
            }
            if (advance.wholeWork() <= 0) continue;
            StorageResourceKey workKey = entry.energyType() != null
                    ? StorageResourceBridge.energyKey(entry.energyType())
                    : StorageResourceBridge.stationWorkKey(entry.id());
            long current = resourceLedger.amount(workKey);
            long pending = workDeltas.getOrDefault(workKey, 0L);
            long delta = Math.min(advance.wholeWork(), Long.MAX_VALUE - current - pending);
            if (delta > 0) workDeltas.put(workKey, pending + delta);
        }
        boolean workChanged = !workDeltas.isEmpty()
                && applyResourceTransaction(workDeltas, Action.EXECUTE, Actor.EMPTY);
        if (remainderChanged && !workChanged) {
            markStorageChanged();
        }
    }

    Container getMachineContainer() {
        return machines;
    }

    public long getMachineRevision() {
        return machineRevision;
    }

    public long getAxeEnergy() {
        return getDescriptorAmount(MachineEnergyTable.AXE_ID);
    }

    public boolean hasInfiniteAxeEnergy() {
        return hasInfiniteDescriptor(MachineEnergyTable.AXE_ID);
    }

    public boolean hasAxeEnergy(long amount) {
        return hasDescriptorAmount(MachineEnergyTable.AXE_ID, amount);
    }

    public boolean canAddAxeEnergy(ItemStack stack) {
        return canAddDescriptorTransform(MachineEnergyTable.AXE_ID, stack);
    }

    public boolean addAxeEnergy(ItemStack stack) {
        return addDescriptorTransform(MachineEnergyTable.AXE_ID, stack);
    }

    public boolean consumeAxeEnergy(long amount) {
        return consumeDescriptor(MachineEnergyTable.AXE_ID, amount);
    }

    public long getDescriptorAmount(ResourceLocation descriptorId) {
        if (!isStorageAvailable()) return 0;
        return resourceLedger.amount(StorageResourceBridge.descriptorKey(descriptorId));
    }

    public long getStationWork(ResourceLocation descriptorId) {
        if (!isStorageAvailable()) return 0;
        return resourceLedger.amount(StorageResourceBridge.stationWorkKey(descriptorId));
    }

    public boolean isMachineInstalled(ResourceLocation descriptorId) {
        int slot = MachineEnergyTable.findSlot(descriptorId);
        MachineDescriptor descriptor = slot < 0 ? null : MachineEnergyTable.get(slot);
        return descriptor != null
                && descriptor.maxInstalledCount() > 0
                && descriptor.accepts(machines.getItem(slot));
    }

    public boolean consumeStationWork(ResourceLocation descriptorId, long amount) {
        if (amount <= 0 || conflicted || !isStorageAvailable()) return false;
        return applyResourceTransaction(
                Map.of(StorageResourceBridge.stationWorkKey(descriptorId), -amount),
                Action.EXECUTE,
                Actor.EMPTY);
    }

    boolean consumeCraftCosts(RecipeAdapterMatch.Cost cost, long crafts) {
        if (cost == null || crafts <= 0 || conflicted || !isStorageAvailable()) return false;
        EnergyCost energyCost = cost.energyCost().orElse(null);
        RecipeAdapterMatch.StationWorkCost stationCost = cost.stationWorkCost().orElse(null);
        RecipeAdapterMatch.ToolCost toolCost = cost.toolCost().orElse(null);
        long processNeed = 0;
        long fuelNeed = 0;
        long stationNeed = 0;
        long toolNeed = 0;
        try {
            if (energyCost != null) {
                processNeed = Math.multiplyExact(energyCost.processAmount(), crafts);
                fuelNeed = Math.multiplyExact(energyCost.fuelAmount(), crafts);
            }
            if (stationCost != null) {
                stationNeed = Math.multiplyExact(stationCost.amountPerCraft(), crafts);
            }
            if (toolCost != null) {
                toolNeed = Math.multiplyExact(toolCost.amountPerCraft(), crafts);
            }
        } catch (ArithmeticException exception) {
            return false;
        }
        if (energyCost != null
                && (getEnergy(energyCost.processType()) < processNeed
                || getEnergy(energyCost.fuelType()) < fuelNeed)) return false;
        if (stationCost != null
                && getStationWork(stationCost.descriptorId()) < stationNeed) return false;
        if (toolCost != null && !hasDescriptorAmount(toolCost.descriptorId(), toolNeed)) return false;

        Map<StorageResourceKey, Long> deltas = new HashMap<>();
        if (energyCost != null) {
            if (processNeed > 0) {
                deltas.merge(
                        StorageResourceBridge.energyKey(energyCost.processType()),
                        -processNeed,
                        Math::addExact);
            }
            if (fuelNeed > 0) {
                deltas.merge(
                        StorageResourceBridge.energyKey(energyCost.fuelType()),
                        -fuelNeed,
                        Math::addExact);
            }
        }
        if (stationCost != null) {
            deltas.put(
                    StorageResourceBridge.stationWorkKey(stationCost.descriptorId()),
                    -stationNeed);
        }
        if (toolCost != null && !hasInfiniteDescriptor(toolCost.descriptorId())) {
            deltas.put(
                    StorageResourceBridge.descriptorKey(toolCost.descriptorId()),
                    -toolNeed);
        }
        return deltas.isEmpty() || applyResourceTransaction(
                deltas, Action.EXECUTE, Actor.EMPTY);
    }

    public boolean hasInfiniteDescriptor(ResourceLocation descriptorId) {
        return isStorageAvailable() && infiniteDescriptors.contains(descriptorId);
    }

    public boolean hasDescriptorAmount(ResourceLocation descriptorId, long amount) {
        return amount > 0 && (hasInfiniteDescriptor(descriptorId)
                || getDescriptorAmount(descriptorId) >= amount);
    }

    public boolean canAddDescriptorTransform(ResourceLocation descriptorId, ItemStack stack) {
        if (stack.isEmpty() || conflicted || !isStorageAvailable()
                || hasInfiniteDescriptor(descriptorId)) return false;
        MachineDescriptor descriptor = MachineEnergyTable.get(descriptorId);
        if (descriptor == null || descriptor.category() != MachineEnergyTable.Category.TRANSFORM
                || !descriptor.accepts(stack)) return false;
        MachineDescriptor.TransformAmount value = descriptor.valueOf(stack);
        return value.infinite() || value.amount() > 0
                && getDescriptorAmount(descriptorId) <= Long.MAX_VALUE - value.amount();
    }

    public boolean addDescriptorTransform(ResourceLocation descriptorId, ItemStack stack) {
        if (!canAddDescriptorTransform(descriptorId, stack)) return false;
        MachineDescriptor.TransformAmount value = MachineEnergyTable.get(descriptorId).valueOf(stack);
        if (value.infinite()) {
            long current = getDescriptorAmount(descriptorId);
            if (current > 0 && !applyResourceTransaction(
                    Map.of(StorageResourceBridge.descriptorKey(descriptorId), -current),
                    Action.EXECUTE,
                    Actor.EMPTY)) {
                return false;
            }
            infiniteDescriptors.add(descriptorId);
            machineRevision++;
            markStorageChanged();
        } else {
            if (!applyResourceTransaction(
                    Map.of(StorageResourceBridge.descriptorKey(descriptorId), value.amount()),
                    Action.EXECUTE,
                    Actor.EMPTY)) {
                return false;
            }
        }
        stack.setCount(0);
        return true;
    }

    public boolean consumeDescriptor(ResourceLocation descriptorId, long amount) {
        if (!hasDescriptorAmount(descriptorId, amount)) return false;
        if (!hasInfiniteDescriptor(descriptorId)) {
            return applyResourceTransaction(
                    Map.of(StorageResourceBridge.descriptorKey(descriptorId), -amount),
                    Action.EXECUTE,
                    Actor.EMPTY);
        }
        return true;
    }

    public long getEnergy(EnergyType type) {
        return isStorageAvailable()
                ? resourceLedger.amount(StorageResourceBridge.energyKey(type)) : 0;
    }

    public boolean consumeEnergy(EnergyCost cost, long multiplier) {
        if (multiplier <= 0 || !isStorageAvailable() || conflicted) return false;
        long processNeed;
        long fuelNeed;
        try {
            processNeed = Math.multiplyExact(cost.processAmount(), multiplier);
            fuelNeed = Math.multiplyExact(cost.fuelAmount(), multiplier);
        } catch (ArithmeticException e) {
            return false;
        }
        if (getEnergy(cost.processType()) < processNeed) return false;
        if (getEnergy(cost.fuelType()) < fuelNeed) return false;
        Map<StorageResourceKey, Long> deltas = new HashMap<>();
        if (processNeed > 0) {
            deltas.merge(
                    StorageResourceBridge.energyKey(cost.processType()),
                    -processNeed,
                    Math::addExact);
        }
        if (fuelNeed > 0) {
            deltas.merge(
                    StorageResourceBridge.energyKey(cost.fuelType()),
                    -fuelNeed,
                    Math::addExact);
        }
        return deltas.isEmpty() || applyResourceTransaction(
                deltas, Action.EXECUTE, Actor.EMPTY);
    }

    public boolean addFuel(ItemStack stack, EnergyType targetPool) {
        if (stack.isEmpty() || conflicted || !isStorageAvailable()) return false;
        List<FuelValue> values = FuelTable.getFuelValues(stack);
        for (FuelValue fv : values) {
            if (fv.pool() == targetPool) {
                long amount;
                try {
                    amount = Math.multiplyExact(fv.valuePerItem(), (long) stack.getCount());
                } catch (ArithmeticException e) {
                    return false;
                }
                long current = getEnergy(targetPool);
                if (amount <= 0 || current > Long.MAX_VALUE - amount) return false;
                if (!applyResourceTransaction(
                        Map.of(StorageResourceBridge.energyKey(targetPool), amount),
                        Action.EXECUTE,
                        Actor.EMPTY)) {
                    return false;
                }
                stack.setCount(0);
                return true;
            }
        }
        return false;
    }

    public boolean isFuel(ItemStack stack) {
        return FuelTable.isFuel(stack);
    }

    public List<FuelValue> getCompatiblePools(ItemStack stack) {
        return FuelTable.getFuelValues(stack);
    }

    public int getTypeCount() {
        return typeCount;
    }

    public boolean isConflicted() {
        return conflicted || !isStorageAvailable();
    }

    public UUID getNetworkId() {
        if (!isStorageAvailable()) {
            throw new IllegalStateException("Core storage data is unavailable at " + getBlockPos());
        }
        return networkId;
    }

    public long getTotalStoredItemCount() {
        return storageRecord == null ? 0 : storageRecord.itemCount();
    }

    public boolean hasRecoverableContents() {
        return storageRecord != null && !storageRecord.isEmpty();
    }

    UUID prepareRecoveryDrop(ServerLevel serverLevel, UUID owner) {
        if (preparedRecoveryId != null) return preparedRecoveryId;
        if (!isStorageAvailable()) {
            throw new IllegalStateException("Cannot pack unavailable Core storage at " + getBlockPos());
        }
        preparedRecoveryId = CoreStorageRepository.get(serverLevel).prepareRecovery(
                storageId,
                location(serverLevel),
                attachmentToken,
                owner,
                serverLevel.getGameTime()).map(CoreStorageRepository.RecoverySummary::id)
                .orElseThrow(() -> new IllegalStateException(
                        "Core storage attachment is unavailable while packing " + storageId));
        return preparedRecoveryId;
    }

    Optional<UUID> getPreparedRecoveryId() {
        return Optional.ofNullable(preparedRecoveryId);
    }

    public boolean isStorageAvailable() {
        return storageAvailability == StorageAvailability.AVAILABLE && storageRecord != null;
    }

    Optional<UUID> getStorageId() {
        return Optional.ofNullable(storageId);
    }

    CoreStorageRecord storageRecordForTesting() {
        if (storageRecord == null) throw new IllegalStateException("Core storage record is unavailable");
        return storageRecord;
    }

    void initializeFreshStorage(ServerLevel serverLevel) {
        if (isStorageAvailable()) return;
        if (storageId != null) {
            attachExistingStorage(serverLevel);
            return;
        }
        Optional<CoreStorageRecord> created = CoreStorageRepository.get(serverLevel)
                .tryCreateFresh(location(serverLevel), attachmentToken);
        if (created.isEmpty()) {
            updateStorageAvailability(StorageAvailability.UNSUPPORTED_REPOSITORY);
            return;
        }
        CoreStorageRecord fresh = created.get();
        storageId = fresh.storageId();
        storageSchema = STORAGE_SCHEMA;
        attachStorage(fresh);
        super.setChanged();
    }

    boolean claimRecovery(ServerLevel serverLevel, UUID recoveryId) {
        if (!isStorageAvailable() || storageId == null || !storageRecord.isEmpty()) return false;
        CoreStorageRecord temporary = storageRecord;
        CoreStorageRepository repository = CoreStorageRepository.get(serverLevel);
        CoreStorageRepository.ClaimResult result = repository.claimIntoFresh(
                recoveryId, storageId, location(serverLevel), attachmentToken);
        if (!result.success()) {
            temporary.clearMachineMutationCallback();
            if (!repository.removeIfEmpty(storageId, location(serverLevel), attachmentToken)) {
                repository.release(storageId, location(serverLevel), attachmentToken);
            }
            storageRecord = null;
            networkId = null;
            machines = new SimpleContainer(MachineDescriptorApi.MAX_DESCRIPTORS);
            infiniteDescriptors = new HashSet<>();
            machineWorkRemainders = new HashMap<>();
            resourceLedger = new StorageResourceLedger();
            typeCount = 0;
            cacheDirty = true;
            updateStorageAvailability(StorageAvailability.from(result.reason()));
            super.setChanged();
            return false;
        }
        temporary.clearMachineMutationCallback();
        storageId = result.record().storageId();
        storageSchema = STORAGE_SCHEMA;
        attachStorage(result.record());
        super.setChanged();
        return true;
    }

    void removeStorageForBlockRemoval(ServerLevel serverLevel) {
        if (storageId == null || storageRecord == null) return;
        CoreStorageRepository repository = CoreStorageRepository.get(serverLevel);
        if (!repository.removeIfEmpty(storageId, location(serverLevel), attachmentToken)) {
            repository.release(storageId, location(serverLevel), attachmentToken);
        }
        storageRecord.clearMachineMutationCallback();
        storageRecord = null;
        storageAvailability = StorageAvailability.UNINITIALIZED;
    }

    private void attachExistingStorage(ServerLevel serverLevel) {
        if (storageSchema != STORAGE_SCHEMA) {
            updateStorageAvailability(StorageAvailability.UNSUPPORTED_REFERENCE);
            return;
        }
        CoreStorageRepository.AttachResult result = CoreStorageRepository.get(serverLevel)
                .attachExisting(storageId, location(serverLevel), attachmentToken);
        if (!result.success()) {
            updateStorageAvailability(StorageAvailability.from(result.reason()));
            return;
        }
        attachStorage(result.record());
    }

    private void attachStorage(CoreStorageRecord record) {
        if (storageRecord != null && storageRecord != record) {
            storageRecord.clearMachineMutationCallback();
        }
        storageRecord = record;
        networkId = record.networkId();
        machines = record.machines();
        infiniteDescriptors = record.infiniteDescriptors();
        machineWorkRemainders = record.machineWorkRemainders();
        resourceLedger = record.resourceLedger();
        typeCount = record.typeCount();
        cacheDirty = true;
        record.setMachineMutationCallback(this::onMachineChanged);
        updateStorageAvailability(StorageAvailability.AVAILABLE);
    }

    private void onMachineChanged() {
        machineRevision++;
        markStorageChanged();
    }

    private void markStorageChanged() {
        if (storageRecord == null) return;
        if (mutationBatchDepth > 0) {
            storageChangedInBatch = true;
            return;
        }
        storageRecord.markChanged();
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0) return left;
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private CoreStorageRepository.CoreLocation location(ServerLevel serverLevel) {
        return new CoreStorageRepository.CoreLocation(serverLevel.dimension(), getBlockPos());
    }

    private void updateStorageAvailability(StorageAvailability availability) {
        if (storageAvailability == availability) return;
        storageAvailability = availability;
        if (availability != StorageAvailability.AVAILABLE
                && availability != StorageAvailability.UNINITIALIZED) {
            MagicStorage.LOGGER.error(
                    "Core storage data unavailable: reason={}, storageId={}, dimension={}, pos={}",
                    availability,
                    storageId,
                    level == null ? "unassigned" : level.dimension().location(),
                    getBlockPos());
        }
    }

    private void rebuildCache() {
        if (!cacheDirty) return;
        flatCache.clear();
        itemSearchCache.clear();
        if (level == null) return;
        for (StorageResourceKey resourceKey : resourceLedger.keys(StorageResourceBridge.ITEM_KIND)) {
            StorageResourceBridge.itemKey(resourceKey, level.registryAccess())
                    .ifPresent(key -> flatCache.merge(
                            key, resourceLedger.amount(resourceKey), Long::sum));
        }
        for (ItemKey key : flatCache.keySet()) {
            itemSearchCache.put(key, TerminalSearchEntry.create(key));
        }
        cacheDirty = false;
    }

    public void addListener(StorageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StorageListener listener) {
        listeners.remove(listener);
    }

    private void fireChanged(ItemKey key, long delta, long newAmount, Actor actor) {
        if (mutationBatchDepth > 0) {
            deferredListenerEvents.add(() -> notifyChanged(key, delta, newAmount, actor));
            return;
        }
        notifyChanged(key, delta, newAmount, actor);
    }

    private void notifyChanged(ItemKey key, long delta, long newAmount, Actor actor) {
        for (StorageListener listener : List.copyOf(listeners)) {
            listener.onChanged(key, delta, newAmount, actor);
        }
    }

    private void fireResourceChanged(
            StorageResourceKey key,
            long delta,
            long newAmount,
            Actor actor
    ) {
        if (mutationBatchDepth > 0) {
            deferredListenerEvents.add(() -> notifyResourceChanged(key, delta, newAmount, actor));
            return;
        }
        notifyResourceChanged(key, delta, newAmount, actor);
    }

    private void notifyResourceChanged(
            StorageResourceKey key,
            long delta,
            long newAmount,
            Actor actor
    ) {
        for (StorageListener listener : List.copyOf(listeners)) {
            listener.onResourceChanged(key, delta, newAmount, actor);
        }
    }

    private void fireEnergyChanged(EnergyType type, long newAmount) {
        if (mutationBatchDepth > 0) {
            deferredListenerEvents.add(() -> notifyEnergyChanged(type, newAmount));
            return;
        }
        notifyEnergyChanged(type, newAmount);
    }

    private void notifyEnergyChanged(EnergyType type, long newAmount) {
        for (StorageListener listener : List.copyOf(listeners)) {
            listener.onEnergyChanged(type, newAmount);
        }
    }

    private void fireStationWorkChanged(
            ResourceLocation descriptorId,
            long delta,
            long newAmount
    ) {
        if (mutationBatchDepth > 0) {
            deferredListenerEvents.add(() ->
                    notifyStationWorkChanged(descriptorId, delta, newAmount));
            return;
        }
        notifyStationWorkChanged(descriptorId, delta, newAmount);
    }

    private void notifyStationWorkChanged(
            ResourceLocation descriptorId,
            long delta,
            long newAmount
    ) {
        for (StorageListener listener : List.copyOf(listeners)) {
            listener.onStationWorkChanged(descriptorId, delta, newAmount);
        }
    }

    void beginMutationBatch() {
        mutationBatchDepth++;
    }

    void endMutationBatch() {
        if (mutationBatchDepth <= 0) throw new IllegalStateException("No active storage mutation batch");
        mutationBatchDepth--;
        if (mutationBatchDepth > 0) return;
        if (storageChangedInBatch && storageRecord != null) {
            storageChangedInBatch = false;
            storageRecord.markChanged();
        }
        List<Runnable> events = List.copyOf(deferredListenerEvents);
        deferredListenerEvents.clear();
        for (Runnable event : events) event.run();
    }

    public long insertItem(ItemStack stack, Action action, Actor actor) {
        if (stack.isEmpty() || conflicted || !isStorageAvailable()) return 0;
        ItemKey key = ItemKey.of(stack);
        long inserted = insertItemCount(key, stack.getCount(), action, actor);
        if (action == Action.EXECUTE && inserted > 0) stack.shrink((int) inserted);
        return inserted;
    }

    public long insertResource(StorageResourceKey key, long amount, Action action) {
        return insertResource(key, amount, action, Actor.EMPTY);
    }

    long insertResource(StorageResourceKey key, long amount, Action action, Actor actor) {
        if (amount <= 0 || conflicted || !isStorageAvailable()) return 0;
        long existing = resourceLedger.amount(key);
        if (existing == 0
                && !key.kindId().equals(StorageResourceBridge.WORK_KIND)
                && !ledgerCapacity().canAcceptNewType(capacityTypeCount())) return 0;
        long inserted = Math.min(amount, Long.MAX_VALUE - existing);
        if (inserted <= 0 || !applyResourceTransaction(
                Map.of(key, inserted), action, actor)) return 0;
        return inserted;
    }

    public long extractResource(StorageResourceKey key, long amount, Action action) {
        return extractResource(key, amount, action, Actor.EMPTY);
    }

    long extractResource(StorageResourceKey key, long amount, Action action, Actor actor) {
        if (amount <= 0 || conflicted || !isStorageAvailable()) return 0;
        long extracted = Math.min(amount, resourceLedger.amount(key));
        if (extracted <= 0 || !applyResourceTransaction(
                Map.of(key, -extracted), action, actor)) return 0;
        return extracted;
    }

    boolean applyResourceTransaction(
            Map<StorageResourceKey, Long> deltas,
            Action action,
            Actor actor
    ) {
        Objects.requireNonNull(deltas, "deltas");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actor, "actor");
        if (deltas.isEmpty() || conflicted || !isStorageAvailable() || level == null) return false;
        Map<StorageResourceKey, ItemKey> itemKeys = new HashMap<>();
        boolean capacityTypesChanged = false;
        for (StorageResourceKey key : deltas.keySet()) {
            if (!StorageResourceKinds.accepts(key)) return false;
            if (!key.kindId().equals(StorageResourceBridge.WORK_KIND)) {
                capacityTypesChanged = true;
            }
            if (!key.kindId().equals(StorageResourceBridge.ITEM_KIND)) continue;
            var itemKey = StorageResourceBridge.itemKey(key, level.registryAccess());
            if (itemKey.isEmpty()) return false;
            itemKeys.put(key, itemKey.get());
        }
        if (!resourceLedger.applyExact(deltas, ledgerCapacity(deltas), action)) return false;
        if (action == Action.EXECUTE) {
            if (capacityTypesChanged) refreshTypeCount();
            if (!itemKeys.isEmpty()) cacheDirty = true;
            if (deltas.keySet().stream().anyMatch(
                    key -> StorageResourceBridge.descriptorId(key).isPresent())) {
                machineRevision++;
            }
            markStorageChanged();
            for (Map.Entry<StorageResourceKey, ItemKey> entry : itemKeys.entrySet()) {
                fireChanged(
                        entry.getValue(),
                        deltas.get(entry.getKey()),
                        resourceLedger.amount(entry.getKey()),
                        actor);
            }
            for (Map.Entry<StorageResourceKey, Long> entry : deltas.entrySet()) {
                if (itemKeys.containsKey(entry.getKey())) continue;
                fireResourceChanged(
                        entry.getKey(),
                        entry.getValue(),
                        resourceLedger.amount(entry.getKey()),
                        actor);
                StorageResourceBridge.energyType(entry.getKey()).ifPresent(
                        type -> fireEnergyChanged(type, resourceLedger.amount(entry.getKey())));
                StorageResourceBridge.stationWorkDescriptorId(entry.getKey()).ifPresent(
                        descriptorId -> fireStationWorkChanged(
                                descriptorId,
                                entry.getValue(),
                                resourceLedger.amount(entry.getKey())));
            }
        }
        return true;
    }

    public boolean applyResourceTransaction(
            StorageResourceTransaction transaction,
            Action action,
            Actor actor
    ) {
        Objects.requireNonNull(transaction, "transaction");
        return applyResourceTransaction(transaction.deltas(), action, actor);
    }

    public long getResourceAmount(StorageResourceKey key) {
        return isStorageAvailable() ? resourceLedger.amount(key) : 0;
    }

    public List<StorageResourceKey> getResourceKeys(ResourceLocation kindId) {
        return isStorageAvailable() ? resourceLedger.keys(kindId) : List.of();
    }

    public List<StorageResourceKey> getResourceKeys() {
        return isStorageAvailable() ? resourceLedger.snapshot().keySet().stream().toList() : List.of();
    }

    StorageResourceHandler resourceHandler() {
        return resourceHandler;
    }

    CoreFluidHandler fluidHandler() {
        return fluidHandler;
    }

    CoreEnergyStorage energyStorage() {
        return energyStorage;
    }

    private StorageTypeCapacity ledgerCapacity() {
        if (typeCapacity.unlimited()) return StorageTypeCapacity.unlimitedCapacity();
        int unresolvedTypes = storageRecord == null
                ? 0 : storageRecord.unresolvedInventoryEntries().size();
        return StorageTypeCapacity.finite(Math.max(
                0, typeCapacity.finiteTypeSlots() - unresolvedTypes));
    }

    private StorageTypeCapacity ledgerCapacity(Map<StorageResourceKey, Long> deltas) {
        StorageTypeCapacity base = ledgerCapacity();
        if (base.unlimited()) return base;
        int projectedWorkTypes = resourceLedger.typeCount(
                key -> key.kindId().equals(StorageResourceBridge.WORK_KIND));
        for (Map.Entry<StorageResourceKey, Long> entry : deltas.entrySet()) {
            if (!entry.getKey().kindId().equals(StorageResourceBridge.WORK_KIND)) continue;
            long current = resourceLedger.amount(entry.getKey());
            long updated;
            try {
                updated = Math.addExact(current, entry.getValue());
            } catch (ArithmeticException exception) {
                return base;
            }
            if (current == 0 && updated > 0) projectedWorkTypes++;
            else if (current > 0 && updated == 0) projectedWorkTypes--;
        }
        long adjusted = (long) base.finiteTypeSlots() + projectedWorkTypes;
        return StorageTypeCapacity.finite((int) Math.min(Integer.MAX_VALUE, adjusted));
    }

    private int capacityTypeCount() {
        return resourceLedger.typeCount(
                key -> !key.kindId().equals(StorageResourceBridge.WORK_KIND));
    }

    private void refreshTypeCount() {
        typeCount = storageRecord == null
                ? resourceLedger.typeCount() : storageRecord.typeCount();
    }

    public long insertItemCount(ItemKey key, long amount, Action action, Actor actor) {
        if (amount <= 0 || conflicted || !isStorageAvailable()) return 0;
        if (level == null) return 0;
        StorageResourceKey resourceKey = StorageResourceBridge.itemKey(key, level.registryAccess());
        long existing = resourceLedger.amount(resourceKey);
        if (existing == 0 && !ledgerCapacity().canAcceptNewType(capacityTypeCount())) return 0;
        long inserted = Math.min(amount, Long.MAX_VALUE - existing);
        if (inserted <= 0) return 0;
        return applyResourceTransaction(Map.of(resourceKey, inserted), action, actor)
                ? inserted : 0;
    }

    public long insertItem(ItemStack stack, boolean simulate) {
        return insertItem(stack, simulate ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
    }

    public long insertItem(ItemStack stack) {
        return insertItem(stack, Action.EXECUTE, Actor.EMPTY);
    }

    public ItemStack extractItem(ItemKey key, long amount, Action action, Actor actor) {
        long requested = Math.min(amount, Integer.MAX_VALUE);
        long extracted = extractItemCount(key, requested, action, actor);
        if (extracted <= 0) return ItemStack.EMPTY;
        return key.toStack((int) extracted);
    }

    public long extractItemCount(ItemKey key, long amount, Action action, Actor actor) {
        if (amount <= 0 || conflicted || !isStorageAvailable()) return 0;
        if (level == null) return 0;
        StorageResourceKey resourceKey = StorageResourceBridge.itemKey(key, level.registryAccess());
        long existing = resourceLedger.amount(resourceKey);
        if (existing <= 0) return 0;

        long extracted = Math.min(amount, existing);
        return applyResourceTransaction(Map.of(resourceKey, -extracted), action, actor)
                ? extracted : 0;
    }

    public ItemStack extractItem(ItemKey key, long amount, boolean simulate) {
        return extractItem(key, amount, simulate ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
    }

    public ItemStack extractItem(ItemKey key, long amount) {
        return extractItem(key, amount, Action.EXECUTE, Actor.EMPTY);
    }

    public List<ItemStack> getDisplayStacks() {
        return getDisplayStacks("");
    }

    public List<ItemStack> getDisplayStacks(String filter) {
        if (!isStorageAvailable()) return List.of();
        rebuildCache();
        TerminalSearchQuery query = TerminalSearchQuery.compile(filter);
        List<ItemStack> result = new ArrayList<>();
        for (var entry : flatCache.entrySet()) {
            ItemKey key = entry.getKey();
            long count = entry.getValue();
            if (query.matches(itemSearchCache.get(key))) {
                ItemStack stack = key.toStack(1);
                if (!stack.isEmpty()) {
                    result.add(TerminalDisplayStack.create(stack, count));
                }
            }
        }
        return result;
    }

    public List<ItemStack> getDisplayStacks(String filter, SortMode mode, SortOrder order) {
        List<ItemStack> result = getDisplayStacks(filter);
        result.sort(TerminalEntryComparator.forMode(mode, order));
        return result;
    }

    public List<ItemStack> getTerminalDisplayStacks(
            String filter,
            SortMode mode,
            SortOrder order,
            TerminalResourceView resourceView
    ) {
        if (resourceView == TerminalResourceView.ITEM) {
            return getDisplayStacks(filter, mode, order);
        }
        List<ItemStack> result = new ArrayList<>();
        if (level == null) return result;
        TerminalSearchQuery query = TerminalSearchQuery.compile(filter);
        for (Map.Entry<StorageResourceKey, Long> entry : resourceLedger.snapshot().entrySet()) {
            StorageResourceKey key = entry.getKey();
            if (!resourceView.matches(key)) continue;
            if (!StorageResourceKinds.isRegistered(key)) continue;
            ItemStack representative = StorageResourceKinds.representative(
                    key, level.registryAccess());
            if (!matchesResourceFilter(key, representative, query)) continue;
            result.add(TerminalResourceDisplay.create(representative, key, entry.getValue()));
        }
        result.sort(TerminalEntryComparator.forMode(mode, order));
        return result;
    }

    private static boolean matchesResourceFilter(
            StorageResourceKey key,
            ItemStack representative,
            TerminalSearchQuery query
    ) {
        return query.matches(key, representative);
    }

    public long getItemCount(ItemKey key) {
        if (!isStorageAvailable() || level == null) return 0;
        return resourceLedger.amount(StorageResourceBridge.itemKey(key, level.registryAccess()));
    }

    public long countMatching(Predicate<ItemStack> pred) {
        if (!isStorageAvailable()) return 0;
        rebuildCache();
        long total = 0;
        for (var entry : flatCache.entrySet()) {
            if (!pred.test(entry.getKey().toStack(1))) continue;
            long amount = entry.getValue();
            if (total > Long.MAX_VALUE - amount) return Long.MAX_VALUE;
            total += amount;
        }
        return total;
    }

    public long extractMatching(Predicate<ItemStack> pred, long amount, Action action, Actor actor) {
        if (amount <= 0 || conflicted || !isStorageAvailable()) return 0;
        rebuildCache();
        List<ItemKey> matches = new ArrayList<>();
        for (var entry : flatCache.entrySet()) {
            if (pred.test(entry.getKey().toStack(1))) matches.add(entry.getKey());
        }
        long extracted = 0;
        for (ItemKey key : matches) {
            if (extracted >= amount) break;
            extracted += extractItemCount(key, amount - extracted, action, actor);
        }
        return extracted;
    }

    public long extractMatching(Predicate<ItemStack> pred, long amount, boolean simulate) {
        return extractMatching(pred, amount, simulate ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
    }

    static boolean matchesFilter(ItemKey key, String filterText, Level level) {
        return TerminalSearchQuery.compile(filterText).matches(TerminalSearchEntry.create(key));
    }

    // ===== NBT =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (storageId != null) {
            tag.putUUID(TAG_STORAGE_ID, storageId);
        }
        tag.putInt(TAG_STORAGE_SCHEMA, storageSchema);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storageId = tag.hasUUID(TAG_STORAGE_ID) ? tag.getUUID(TAG_STORAGE_ID) : null;
        storageSchema = tag.getInt(TAG_STORAGE_SCHEMA);
        storageRecord = null;
        networkId = null;
        machines = new SimpleContainer(MachineDescriptorApi.MAX_DESCRIPTORS);
        infiniteDescriptors = new HashSet<>();
        machineWorkRemainders = new HashMap<>();
        resourceLedger = new StorageResourceLedger();
        typeCount = 0;
        cacheDirty = true;
        storageAvailability = storageId == null
                ? StorageAvailability.MISSING_REFERENCE
                : StorageAvailability.UNINITIALIZED;
    }

    // ===== Network =====

    public void rebuildNetwork(Level level) {
        Set<BlockPos> previousConnectedBlocks = Set.copyOf(connectedBlocks);
        StorageTypeCapacity previousTypeCapacity = typeCapacity;
        boolean wasConflicted = conflicted;
        connectedBlocks.clear();
        typeCapacity = StorageTypeCapacity.zero();
        conflicted = false;

        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(getBlockPos());
        visited.add(getBlockPos());

        int depth = 0;
        while (!queue.isEmpty() && depth < MagicStorage.NETWORK_SCAN_DEPTH && visited.size() <= MagicStorage.MAX_NETWORK_BLOCKS) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                BlockPos current = queue.poll();

                BlockState state = level.getBlockState(current);
                if (state.getBlock() instanceof IStorageNetworkBlock networkBlock) {
                    if (networkBlock.isStorageCore() && !current.equals(getBlockPos())) {
                        conflicted = true;
                        continue;
                    }
                    connectedBlocks.add(current);
                    typeCapacity = typeCapacity.plus(capacityOf(state));
                }

                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.relative(dir);
                    if (!visited.contains(neighbor) && level.hasChunkAt(neighbor)) {
                        visited.add(neighbor);
                        if (level.getBlockState(neighbor).getBlock() instanceof IStorageNetworkBlock) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
            depth++;
        }
        if (conflicted && !wasConflicted) {
            MagicStorage.LOGGER.warn("Storage network at {} has multiple cores; multi-core is unsupported, network disabled until extra cores removed.", getBlockPos());
        }
        if (wasConflicted != conflicted || !previousTypeCapacity.equals(typeCapacity)
                || !previousConnectedBlocks.equals(connectedBlocks)) {
            topologyRevision++;
        }
    }

    public boolean tryIncrementalAdd(Level level, BlockPos placedPos) {
        if (conflicted) return false;
        if (placedPos.equals(getBlockPos())) return false;
        if (connectedBlocks.contains(placedPos)) return false;

        BlockState state = level.getBlockState(placedPos);
        if (!(state.getBlock() instanceof IStorageNetworkBlock networkBlock)) return false;
        if (networkBlock.isStorageCore()) return false;

        if (!isWithinIncrementalBounds(placedPos)) return false;

        connectedBlocks.add(placedPos);
        typeCapacity = typeCapacity.plus(capacityOf(state));
        topologyRevision++;
        return true;
    }

    private boolean isWithinIncrementalBounds(BlockPos placedPos) {
        if (connectedBlocks.size() >= MagicStorage.MAX_NETWORK_BLOCKS) return false;
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(getBlockPos());
        visited.add(getBlockPos());

        int depth = 0;
        while (!queue.isEmpty() && depth < MagicStorage.NETWORK_SCAN_DEPTH - 1) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                BlockPos current = queue.poll();
                for (Direction dir : Direction.values()) {
                    BlockPos next = current.relative(dir);
                    if (next.equals(placedPos)) return true;
                    if (connectedBlocks.contains(next) && visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
            depth++;
        }
        return false;
    }

    private StorageTypeCapacity capacityOf(BlockState state) {
        if (state.getBlock() instanceof StorageUnitBlock unitBlock) {
            return unitBlock.getTypeCapacityContribution();
        }
        return StorageTypeCapacity.zero();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            if (storageId != null && !isStorageAvailable()) {
                attachExistingStorage(serverLevel);
            }
            rebuildNetwork(serverLevel);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel && storageId != null && storageRecord != null) {
            CoreStorageRepository.get(serverLevel).release(storageId, location(serverLevel), attachmentToken);
            storageRecord.clearMachineMutationCallback();
            storageRecord = null;
            storageAvailability = StorageAvailability.UNINITIALIZED;
        }
        super.setRemoved();
    }

    public void onBreak() {
        if (!connectedBlocks.isEmpty() || !typeCapacity.equals(StorageTypeCapacity.zero())) topologyRevision++;
        connectedBlocks.clear();
        typeCapacity = StorageTypeCapacity.zero();
    }

    public Set<BlockPos> getConnectedBlocks() { return connectedBlocks; }
    public int getTotalTypeSlots() { return typeCapacity.finiteTypeSlots(); }
    public StorageTypeCapacity getTypeCapacity() { return typeCapacity; }
    public long getTopologyRevision() { return topologyRevision; }

    private enum StorageAvailability {
        UNINITIALIZED,
        AVAILABLE,
        MISSING_REFERENCE,
        UNSUPPORTED_REFERENCE,
        MISSING_RECORD,
        CORRUPT_RECORD,
        UNSUPPORTED_REPOSITORY,
        DUPLICATE_ATTACHMENT,
        PACKED,
        RECOVERY_MISSING,
        INVALID_FRESH_RECORD;

        static StorageAvailability from(CoreStorageRepository.AttachFailure failure) {
            return switch (failure) {
                case MISSING_RECORD -> MISSING_RECORD;
                case CORRUPT_RECORD -> CORRUPT_RECORD;
                case UNSUPPORTED_REPOSITORY -> UNSUPPORTED_REPOSITORY;
                case DUPLICATE_ATTACHMENT -> DUPLICATE_ATTACHMENT;
                case PACKED -> PACKED;
                case RECOVERY_MISSING -> RECOVERY_MISSING;
                case INVALID_FRESH_RECORD -> INVALID_FRESH_RECORD;
            };
        }
    }
}
