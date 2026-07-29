package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ExportBusBlockEntity extends BlockEntity implements BusConfigurationHost {

    private static final int COOLDOWN_TICKS = 10;

    private BlockPos corePos;
    private StorageCoreBlockEntity cachedCore;
    private List<BlockPos> cachedPath = List.of();
    private int cooldown = 0;
    private long nextCoreSearchTick = Long.MIN_VALUE;
    private boolean escrowDropWillBePreserved;
    private final UUID recoveryDropId = UUID.randomUUID();
    private ItemKey filterItem = null;
    private BusConfiguration busConfiguration = BusConfiguration.defaults(BusKind.EXPORT);
    private BusResourceEscrow pendingResources = BusResourceEscrow.empty();
    private long operationSequence;
    private StorageCoreBlockEntity itemCandidateCore;
    private long itemCandidateCoreRevision = Long.MIN_VALUE;
    private long itemCandidateConfigRevision = Long.MIN_VALUE;
    private long itemCandidateDatapackRevision = Long.MIN_VALUE;
    private List<ItemKey> cachedItemCandidates = List.of();
    private final IItemHandler[] passiveItemHandlers = new IItemHandler[7];
    private final StorageResourceHandler[] passiveResourceHandlers = new StorageResourceHandler[7];
    private final IFluidHandler[] passiveFluidHandlers = new IFluidHandler[7];
    private final IEnergyStorage[] passiveEnergyStorages = new IEnergyStorage[7];
    private final StorageResourceHandler passiveResourceHandler = new StorageResourceHandler() {
        @Override
        public List<StorageResourceKey> getStoredResources() {
            StorageCoreBlockEntity core = resolveCore();
            if (core == null || core.isConflicted()) return List.of();
            return core.getResourceKeys().stream()
                    .filter(ExportBusBlockEntity.this::allowsResource)
                    .toList();
        }

        @Override
        public long getAmount(StorageResourceKey key) {
            if (!allowsResource(key)) return 0;
            StorageCoreBlockEntity core = resolveCore();
            return core == null || core.isConflicted() ? 0 : core.getResourceAmount(key);
        }

        @Override
        public long insert(StorageResourceKey key, long amount, boolean simulate) {
            return 0;
        }

        @Override
        public long extract(StorageResourceKey key, long amount, boolean simulate) {
            if (amount <= 0 || !allowsResource(key)) return 0;
            StorageCoreBlockEntity core = resolveCore();
            if (core == null || core.isConflicted()) return 0;
            BusActor actor = nextBusActor(core, BusOperationDirection.EXPORT);
            return BusTransferGuard.run(actor, 0L, () -> core.extractResource(
                    key,
                    amount,
                    simulate ? Action.SIMULATE : Action.EXECUTE,
                    actor));
        }
    };
    public ExportBusBlockEntity(BlockPos pos, BlockState state) {
        super(MagicStorage.EXPORT_BUS_BE.get(), pos, state);
        for (Direction side : Direction.values()) initializePassiveHandlers(side);
        initializePassiveHandlers(null);
    }

    IItemHandler passiveItemHandler(Direction side) {
        return exposesPassiveCapability(side)
                ? passiveItemHandlers[capabilityIndex(side)]
                : null;
    }

    private IItemHandler createPassiveItemHandler(Direction side) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 1;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                if (slot != 0 || !exposesPassiveCapability(side)) return ItemStack.EMPTY;
                ItemKey candidate = firstPassiveItemCandidate();
                if (candidate == null) return ItemStack.EMPTY;
                StorageCoreBlockEntity core = resolveCore();
                if (core == null || core.isConflicted()) return ItemStack.EMPTY;
                ItemStack representative = candidate.toStack(1);
                long available = core.getItemCount(candidate);
                return available <= 0
                        ? ItemStack.EMPTY
                        : candidate.toStack((int) Math.min(available, representative.getMaxStackSize()));
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack.copy();
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot != 0 || amount <= 0 || !exposesPassiveCapability(side)) {
                    return ItemStack.EMPTY;
                }
                ItemKey candidate = firstPassiveItemCandidate();
                if (candidate == null) return ItemStack.EMPTY;
                StorageCoreBlockEntity core = resolveCore();
                if (core == null || core.isConflicted()) return ItemStack.EMPTY;
                int limit = Math.min(amount, candidate.toStack(1).getMaxStackSize());
                BusActor actor = nextBusActor(core, BusOperationDirection.EXPORT);
                return BusTransferGuard.run(actor, ItemStack.EMPTY, () -> core.extractItem(
                        candidate,
                        limit,
                        simulate ? Action.SIMULATE : Action.EXECUTE,
                        actor));
            }

            @Override
            public int getSlotLimit(int slot) {
                return slot == 0 ? 64 : 0;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;
            }
        };
    }

    private ItemKey firstPassiveItemCandidate() {
        if (!isLiveServerBus()) return null;
        StorageCoreBlockEntity core = resolveCore();
        if (core == null || core.isConflicted()) return null;
        List<ItemKey> candidates = itemCandidates(core);
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private List<ItemKey> itemCandidates(StorageCoreBlockEntity core) {
        long coreRevision = core.getCraftableRevision();
        long configRevision = busConfiguration.configRevision();
        long datapackRevision = MagicStorage.datapackRevision();
        if (itemCandidateCore == core
                && itemCandidateCoreRevision == coreRevision
                && itemCandidateConfigRevision == configRevision
                && itemCandidateDatapackRevision == datapackRevision) {
            return cachedItemCandidates;
        }
        List<ItemKey> candidates = BusFilterPolicy.compile(
                busConfiguration, level.registryAccess()).orderedCandidates(
                core.getDisplayStacks().stream().map(ItemKey::of).toList());
        itemCandidateCore = core;
        itemCandidateCoreRevision = coreRevision;
        itemCandidateConfigRevision = configRevision;
        itemCandidateDatapackRevision = datapackRevision;
        cachedItemCandidates = candidates;
        return cachedItemCandidates;
    }

    StorageResourceHandler passiveResourceHandler(Direction side) {
        return exposesPassiveCapability(side)
                ? passiveResourceHandlers[capabilityIndex(side)]
                : null;
    }

    private StorageResourceHandler createPassiveResourceHandler(Direction side) {
        return new StorageResourceHandler() {
            @Override
            public List<StorageResourceKey> getStoredResources() {
                return exposesPassiveCapability(side)
                        ? passiveResourceHandler.getStoredResources()
                        : List.of();
            }

            @Override
            public long getAmount(StorageResourceKey key) {
                return exposesPassiveCapability(side)
                        ? passiveResourceHandler.getAmount(key)
                        : 0;
            }

            @Override
            public long insert(StorageResourceKey key, long amount, boolean simulate) {
                return 0;
            }

            @Override
            public long extract(StorageResourceKey key, long amount, boolean simulate) {
                return exposesPassiveCapability(side)
                        ? passiveResourceHandler.extract(key, amount, simulate)
                        : 0;
            }
        };
    }

    IFluidHandler passiveFluidHandler(Direction side) {
        return exposesPassiveCapability(side)
                ? passiveFluidHandlers[capabilityIndex(side)]
                : null;
    }

    IEnergyStorage passiveEnergyStorage(Direction side) {
        return exposesPassiveCapability(side)
                ? passiveEnergyStorages[capabilityIndex(side)]
                : null;
    }

    private void initializePassiveHandlers(Direction side) {
        int index = capabilityIndex(side);
        passiveItemHandlers[index] = createPassiveItemHandler(side);
        passiveResourceHandlers[index] = createPassiveResourceHandler(side);
        passiveFluidHandlers[index] = new ResourceFluidHandler(
                passiveResourceHandlers[index], () -> level.registryAccess());
        passiveEnergyStorages[index] = new ResourceEnergyStorage(passiveResourceHandlers[index]);
    }

    private static int capabilityIndex(Direction side) {
        return side == null ? Direction.values().length : side.ordinal();
    }

    private boolean exposesPassiveCapability(Direction side) {
        return isLiveServerBus()
                && pendingResources.supported()
                && !pendingResources.hasPending()
                && busConfiguration.mode() == BusMode.DIRECTIONLESS
                && busConfiguration.allowsCapability(side);
    }

    private boolean allowsResource(StorageResourceKey key) {
        if (!isLiveServerBus()
                || !busConfiguration.supported()
                || !pendingResources.supported()
                || pendingResources.hasPending()
                || !busConfiguration.automationEnabled()
                || key.kindId().equals(StorageResourceKindApi.ITEM_KIND)) return false;
        return BusFilterPolicy.compile(
                busConfiguration, level.registryAccess()).allows(key);
    }

    public void tick() {
        if (!isLiveServerBus()
                || !busConfiguration.supported()
                || !pendingResources.supported()) return;

        if (pendingResources.hasPending()) {
            StorageCoreBlockEntity core = resolveCore();
            if (core == null || core.isConflicted()) return;
            BusActor actor = nextBusActor(core, BusOperationDirection.EXPORT);
            BusTransferGuard.run(actor, false, () -> pendingResources.drainToCore(core, actor));
            persistPendingResourceChange();
            return;
        }
        if (!busConfiguration.automationEnabled()
                || busConfiguration.mode() != BusMode.DIRECTIONAL) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = COOLDOWN_TICKS;

        StorageCoreBlockEntity core = resolveCore();
        if (core == null) {
            setChanged();
            return;
        }
        if (core.isConflicted()) return;

        Direction facing = getBlockState().getValue(ExportBusBlock.FACING);
        BlockPos targetPos = getBlockPos().relative(facing);

        if (!level.hasChunkAt(targetPos)) return;

        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.getBlock() instanceof IStorageNetworkBlock) return;

        Direction targetSide = facing.getOpposite();
        IItemHandler handler = level.getCapability(
                Capabilities.ItemHandler.BLOCK, targetPos, targetSide);

        if (handler != null) {
            BusActor actor = nextBusActor(core, BusOperationDirection.EXPORT);
            BusItemTransferPlan plan = BusItemTransferPlan.capture(
                    level, getBlockPos(), targetPos, targetSide, core,
                    busConfiguration, handler, actor, BusOperationDirection.EXPORT);
            if (plan != null) {
                for (ItemKey candidate : itemCandidates(core)) {
                    if (BusTransferGuard.run(actor, false, () -> transferOneStack(
                            core, candidate, handler, actor,
                            stack -> BusRecoveryDrops.spawnDirectOrThrow(
                                    level, getBlockPos(), stack),
                            plan))) break;
                }
            }
        }
        BusActor actor = nextBusActor(core, BusOperationDirection.EXPORT);
        BusTransferGuard.run(actor, false, () -> BusTypedResourceTransfer.exportOne(
                level, getBlockPos(), targetPos, targetSide, core, busConfiguration,
                pendingResources, actor));
        persistPendingResourceChange();
    }

    private void persistPendingResourceChange() {
        if (!pendingResources.takeChanged()) return;
        setChanged();
        if (level != null) level.invalidateCapabilities(getBlockPos());
    }

    static boolean transferOneStack(StorageCoreBlockEntity core, ItemKey filterItem, IItemHandler handler,
                                    Actor actor, Consumer<ItemStack> overflow) {
        return transferOneStack(core, filterItem, handler, actor, overflow, null);
    }

    private static boolean transferOneStack(
            StorageCoreBlockEntity core,
            ItemKey filterItem,
            IItemHandler handler,
            Actor actor,
            Consumer<ItemStack> overflow,
            BusItemTransferPlan plan
    ) {
        ItemStack toExport = core.extractItem(filterItem, 64, Action.SIMULATE, actor);
        if (toExport.isEmpty()) return false;

        ItemStack leftoverSim = toExport.copy();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            leftoverSim = handler.insertItem(slot, leftoverSim, true);
            if (leftoverSim.isEmpty()) break;
        }
        int fits = toExport.getCount() - leftoverSim.getCount();
        if (fits <= 0) return false;
        if (plan != null && !plan.stillValid()) return false;

        ItemStack extracted = core.extractItem(filterItem, fits, Action.EXECUTE, actor);
        if (extracted.isEmpty()) return false;
        if (plan != null && !plan.stillValid()) {
            restoreToCoreOrOverflow(core, extracted, actor, overflow, plan);
            return false;
        }
        java.util.List<AcceptedSlot> acceptedSlots = new java.util.ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack offered = extracted.copy();
            extracted = handler.insertItem(slot, offered, false);
            int accepted = acceptedCount(offered, extracted);
            if (accepted > 0) acceptedSlots.add(new AcceptedSlot(slot, accepted));
            if (plan != null && !plan.stillValid()) {
                ReclaimResult reclaim = reclaimAccepted(
                        handler, filterItem, acceptedSlots, overflow);
                if (!reclaim.reclaimed().isEmpty()) {
                    if (extracted.isEmpty()) extracted = reclaim.reclaimed();
                    else extracted.grow(reclaim.reclaimed().getCount());
                }
                if (reclaim.unreclaimed() > 0 && !plan.sameEndpointIdentity()) {
                    overflow.accept(filterItem.toStack(reclaim.unreclaimed()));
                }
                restoreToCoreOrOverflow(core, extracted, actor, overflow, plan);
                return false;
            }
            if (extracted.isEmpty()) break;
        }
        restoreToCoreOrOverflow(core, extracted, actor, overflow, plan);
        return true;
    }

    private static int acceptedCount(ItemStack offered, ItemStack remainder) {
        if (remainder.isEmpty()) return offered.getCount();
        if (!ItemStack.isSameItemSameComponents(offered, remainder)
                || remainder.getCount() > offered.getCount()) {
            throw new IllegalStateException("Item handler returned an invalid insertion remainder");
        }
        return offered.getCount() - remainder.getCount();
    }

    private static ReclaimResult reclaimAccepted(
            IItemHandler handler,
            ItemKey expected,
            java.util.List<AcceptedSlot> acceptedSlots,
            Consumer<ItemStack> overflow
    ) {
        int reclaimedCount = 0;
        int unreclaimedCount = 0;
        for (int index = acceptedSlots.size() - 1; index >= 0; index--) {
            AcceptedSlot accepted = acceptedSlots.get(index);
            ItemStack reclaimed = handler.extractItem(accepted.slot(), accepted.count(), false);
            if (reclaimed.isEmpty()) {
                unreclaimedCount = Math.addExact(unreclaimedCount, accepted.count());
                continue;
            }
            if (reclaimed.getCount() > accepted.count()) {
                throw new IllegalStateException("Item handler returned an invalid reclaim amount");
            }
            if (ItemKey.of(reclaimed).equals(expected)) {
                reclaimedCount = Math.addExact(reclaimedCount, reclaimed.getCount());
                unreclaimedCount = Math.addExact(
                        unreclaimedCount, accepted.count() - reclaimed.getCount());
            } else {
                ItemStack unreturned = handler.insertItem(
                        accepted.slot(), reclaimed.copy(), false);
                if (!unreturned.isEmpty()) overflow.accept(unreturned);
                unreclaimedCount = Math.addExact(unreclaimedCount, accepted.count());
            }
        }
        return new ReclaimResult(
                reclaimedCount == 0 ? ItemStack.EMPTY : expected.toStack(reclaimedCount),
                unreclaimedCount);
    }

    private static void restoreToCoreOrOverflow(
            StorageCoreBlockEntity core,
            ItemStack stack,
            Actor actor,
            Consumer<ItemStack> overflow,
            BusItemTransferPlan plan
    ) {
        if (stack.isEmpty()) return;
        if (plan == null || plan.sameCoreIdentity()) {
            core.insertItem(stack, Action.EXECUTE, actor);
        }
        if (!stack.isEmpty()) overflow.accept(stack.copy());
    }

    private record AcceptedSlot(int slot, int count) {
    }

    private record ReclaimResult(ItemStack reclaimed, int unreclaimed) {
    }

    private StorageCoreBlockEntity resolveCore() {
        if (!isLiveServerBus()) return null;
        if (cachedCore != null && !cachedCore.isRemoved()
                && cachedCore.getConnectedBlocks().contains(getBlockPos())) {
            if (MagicStorage.hasLoadedNetworkPath(level, cachedPath, getBlockPos(), cachedCore.getBlockPos())) {
                return cachedCore;
            }
            cachedPath = MagicStorage.findLoadedNetworkPath(level, getBlockPos(), cachedCore.getBlockPos());
            if (!cachedPath.isEmpty()) return cachedCore;
        }
        cachedCore = null;
        cachedPath = List.of();
        corePos = null;
        long gameTime = level.getGameTime();
        if (gameTime < nextCoreSearchTick) return null;
        cachedCore = MagicStorage.bfsFindCore(level, getBlockPos());
        if (cachedCore != null && !cachedCore.getConnectedBlocks().contains(getBlockPos())) {
            cachedCore = null;
        }
        cachedPath = cachedCore != null
                ? MagicStorage.findLoadedNetworkPath(level, getBlockPos(), cachedCore.getBlockPos())
                : List.of();
        if (cachedCore != null && cachedPath.isEmpty()) cachedCore = null;
        corePos = cachedCore != null ? cachedCore.getBlockPos() : null;
        nextCoreSearchTick = cachedCore == null ? gameTime + COOLDOWN_TICKS : Long.MIN_VALUE;
        return cachedCore;
    }

    private boolean isLiveServerBus() {
        return level != null
                && !level.isClientSide()
                && !isRemoved()
                && level.hasChunkAt(getBlockPos())
                && level.getBlockEntity(getBlockPos()) == this;
    }

    private BusActor nextBusActor(
            StorageCoreBlockEntity core,
            BusOperationDirection direction
    ) {
        long sequence = operationSequence;
        operationSequence = operationSequence == Long.MAX_VALUE ? 0 : operationSequence + 1;
        return new BusActor(
                busConfiguration.owner(),
                level.dimension(),
                getBlockPos(),
                core.getNetworkId(),
                direction,
                new BusOperationId(level.getGameTime(), sequence));
    }

    public void setFilter(ItemStack stack) {
        if (!busConfiguration.supported()) return;
        if (stack.isEmpty()) {
            filterItem = null;
        } else {
            filterItem = ItemKey.of(stack);
        }
        busConfiguration = busConfiguration.withSingleExactFilter(stack);
        setChanged();
    }

    public ItemKey getFilter() {
        return filterItem;
    }

    public void assignOwnerOnPlacement(UUID owner) {
        BusConfiguration assigned = busConfiguration.assignInitialOwner(owner);
        if (assigned.equals(busConfiguration)) return;
        busConfiguration = assigned;
        setChanged();
    }

    public BusConfiguration getBusConfiguration() {
        return busConfiguration;
    }

    public long getPendingResourceAmount(StorageResourceKey key) {
        return pendingResources.amount(key);
    }

    void markEscrowDropWillBePreserved() {
        escrowDropWillBePreserved = true;
    }

    boolean consumeEscrowDropWillBePreserved() {
        boolean result = escrowDropWillBePreserved;
        escrowDropWillBePreserved = false;
        return result;
    }

    boolean recoverPendingResources() {
        if (!pendingResources.supported()) return !pendingResources.hasPending();
        while (pendingResources.hasPending()) {
            StorageCoreBlockEntity core = resolveCore();
            if (core == null || core.isConflicted()) return false;
            BusActor actor = nextBusActor(core, BusOperationDirection.EXPORT);
            boolean drained = BusTransferGuard.run(
                    actor, false, () -> pendingResources.drainToCore(core, actor));
            persistPendingResourceChange();
            if (!drained) return false;
        }
        return true;
    }

    ItemStack createRecoveryDrop(HolderLookup.Provider registries) {
        ItemStack drop = new ItemStack(MagicStorage.EXPORT_BUS_ITEM.get());
        CompoundTag tag = new CompoundTag();
        saveDropData(tag, registries);
        net.minecraft.world.item.BlockItem.setBlockEntityData(
                drop, MagicStorage.EXPORT_BUS_BE.get(), tag);
        return drop;
    }

    void saveDropData(CompoundTag tag, HolderLookup.Provider registries) {
        busConfiguration.withoutOwner().save(tag, registries);
        pendingResources.save(tag);
        BusRecoveryDrops.saveRecoveryDropId(tag, recoveryDropId);
    }

    @Override
    public BusKind busKind() {
        return BusKind.EXPORT;
    }

    @Override
    public void setBusConfiguration(BusConfiguration configuration) {
        BusConfiguration previous = busConfiguration;
        busConfiguration = configuration;
        filterItem = configuration.filterRules().stream()
                .filter(rule -> rule.type() == BusFilterRule.Type.EXACT_STACK)
                .findFirst()
                .flatMap(BusFilterRule::exactKey)
                .orElse(null);
        cooldown = 0;
        setChanged();
        if (level != null && !previous.equals(configuration)) {
            level.invalidateCapabilities(getBlockPos());
        }
        syncModeBlockState();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) level.invalidateCapabilities(getBlockPos());
        syncModeBlockState();
    }

    private void syncModeBlockState() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        boolean directionless = busConfiguration.mode() == BusMode.DIRECTIONLESS;
        if (state.hasProperty(ExportBusBlock.DIRECTIONLESS)
                && state.getValue(ExportBusBlock.DIRECTIONLESS) != directionless) {
            level.setBlock(getBlockPos(),
                    state.setValue(ExportBusBlock.DIRECTIONLESS, directionless),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (corePos != null) {
            tag.putInt("coreX", corePos.getX());
            tag.putInt("coreY", corePos.getY());
            tag.putInt("coreZ", corePos.getZ());
        }
        if (filterItem != null) {
            tag.put("filter", filterItem.toStack(1).save(registries));
        }
        busConfiguration.save(tag, registries);
        pendingResources.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cooldown = 0;
        escrowDropWillBePreserved = false;
        if (tag.contains("coreX")) {
            corePos = new BlockPos(tag.getInt("coreX"), tag.getInt("coreY"), tag.getInt("coreZ"));
        }
        BusConfiguration previous = busConfiguration;
        boolean previousEscrowAvailable = pendingResources.supported()
                && !pendingResources.hasPending();
        busConfiguration = BusConfiguration.load(tag, BusKind.EXPORT, registries);
        pendingResources = BusResourceEscrow.load(tag);
        filterItem = busConfiguration.filterRules().stream()
                .filter(rule -> rule.type() == BusFilterRule.Type.EXACT_STACK)
                .map(BusFilterRule::exactKey)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElse(null);
        boolean escrowAvailable = pendingResources.supported()
                && !pendingResources.hasPending();
        if (level != null && (!previous.equals(busConfiguration)
                || previousEscrowAvailable != escrowAvailable)) {
            level.invalidateCapabilities(getBlockPos());
        }
        syncModeBlockState();
    }
}
