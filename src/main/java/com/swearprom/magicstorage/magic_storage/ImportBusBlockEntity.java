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

public class ImportBusBlockEntity extends BlockEntity implements BusConfigurationHost {

    private static final int COOLDOWN_TICKS = 10;

    private BlockPos corePos;
    private StorageCoreBlockEntity cachedCore;
    private List<BlockPos> cachedPath = List.of();
    private int cooldown = 0;
    private long nextCoreSearchTick = Long.MIN_VALUE;
    private boolean escrowDropWillBePreserved;
    private final UUID recoveryDropId = UUID.randomUUID();
    private BusConfiguration busConfiguration = BusConfiguration.defaults(BusKind.IMPORT);
    private BusResourceEscrow pendingResources = BusResourceEscrow.empty();
    private long operationSequence;
    private final IItemHandler[] passiveItemHandlers = new IItemHandler[7];
    private final StorageResourceHandler[] passiveResourceHandlers = new StorageResourceHandler[7];
    private final IFluidHandler[] passiveFluidHandlers = new IFluidHandler[7];
    private final IEnergyStorage[] passiveEnergyStorages = new IEnergyStorage[7];
    private final IItemHandler passiveItemHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || level == null || level.isClientSide()
                    || !busConfiguration.supported()
                    || !pendingResources.supported()
                    || pendingResources.hasPending()
                    || !busConfiguration.automationEnabled()) return stack.copy();
            BusFilterPolicy policy = BusFilterPolicy.compile(
                    busConfiguration, level.registryAccess());
            if (!policy.allows(ItemKey.of(stack))) return stack.copy();
            StorageCoreBlockEntity core = resolveCore();
            if (core == null || core.isConflicted()) return stack.copy();
            BusActor actor = nextBusActor(core, BusOperationDirection.IMPORT);
            return BusTransferGuard.run(actor, stack.copy(), () -> {
                ItemStack probe = stack.copy();
                long accepted = core.insertItem(probe, Action.SIMULATE, actor);
                if (accepted <= 0) return stack.copy();
                if (!simulate) {
                    if (!busConfiguration.supported()
                            || !pendingResources.supported()
                            || pendingResources.hasPending()
                            || !busConfiguration.automationEnabled()
                            || !BusFilterPolicy.compile(busConfiguration, level.registryAccess())
                            .allows(ItemKey.of(stack))) return stack.copy();
                    ItemStack committed = stack.copyWithCount(
                            (int) Math.min(accepted, stack.getCount()));
                    accepted = core.insertItem(committed, Action.EXECUTE, actor);
                }
                if (accepted >= stack.getCount()) return ItemStack.EMPTY;
                return stack.copyWithCount(stack.getCount() - (int) accepted);
            });
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && !stack.isEmpty();
        }
    };
    private final StorageResourceHandler passiveResourceHandler = new StorageResourceHandler() {
        @Override
        public List<StorageResourceKey> getStoredResources() {
            return List.of();
        }

        @Override
        public long getAmount(StorageResourceKey key) {
            return 0;
        }

        @Override
        public long insert(StorageResourceKey key, long amount, boolean simulate) {
            if (amount <= 0 || !allowsResource(key)) return 0;
            StorageCoreBlockEntity core = resolveCore();
            if (core == null || core.isConflicted()) return 0;
            BusActor actor = nextBusActor(core, BusOperationDirection.IMPORT);
            return BusTransferGuard.run(actor, 0L, () -> core.insertResource(
                    key,
                    amount,
                    simulate ? Action.SIMULATE : Action.EXECUTE,
                    actor));
        }

        @Override
        public long extract(StorageResourceKey key, long amount, boolean simulate) {
            return 0;
        }
    };
    public ImportBusBlockEntity(BlockPos pos, BlockState state) {
        super(MagicStorage.IMPORT_BUS_BE.get(), pos, state);
        for (Direction side : Direction.values()) initializePassiveHandlers(side);
        initializePassiveHandlers(null);
    }

    IItemHandler passiveItemHandler(Direction side) {
        return allowsPassiveCapability(side)
                ? passiveItemHandlers[capabilityIndex(side)]
                : null;
    }

    private IItemHandler createPassiveItemHandler(Direction side) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return passiveItemHandler.getSlots();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return passiveItemHandler.getStackInSlot(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return allowsPassiveCapability(side)
                        ? passiveItemHandler.insertItem(slot, stack, simulate)
                        : stack.copy();
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return passiveItemHandler.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return allowsPassiveCapability(side)
                        && passiveItemHandler.isItemValid(slot, stack);
            }
        };
    }

    StorageResourceHandler passiveResourceHandler(Direction side) {
        return allowsPassiveCapability(side)
                ? passiveResourceHandlers[capabilityIndex(side)]
                : null;
    }

    private StorageResourceHandler createPassiveResourceHandler(Direction side) {
        return new StorageResourceHandler() {
            @Override
            public List<StorageResourceKey> getStoredResources() {
                return allowsPassiveCapability(side)
                        ? passiveResourceHandler.getStoredResources()
                        : List.of();
            }

            @Override
            public long getAmount(StorageResourceKey key) {
                return allowsPassiveCapability(side)
                        ? passiveResourceHandler.getAmount(key)
                        : 0;
            }

            @Override
            public long insert(StorageResourceKey key, long amount, boolean simulate) {
                return allowsPassiveCapability(side)
                        ? passiveResourceHandler.insert(key, amount, simulate)
                        : 0;
            }

            @Override
            public long extract(StorageResourceKey key, long amount, boolean simulate) {
                return 0;
            }
        };
    }

    IFluidHandler passiveFluidHandler(Direction side) {
        return allowsPassiveCapability(side)
                ? passiveFluidHandlers[capabilityIndex(side)]
                : null;
    }

    IEnergyStorage passiveEnergyStorage(Direction side) {
        return allowsPassiveCapability(side)
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

    private boolean allowsPassiveCapability(Direction side) {
        return isLiveServerBus()
                && pendingResources.supported()
                && !pendingResources.hasPending()
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
            BusActor actor = nextBusActor(core, BusOperationDirection.IMPORT);
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

        Direction facing = getBlockState().getValue(ImportBusBlock.FACING);
        BlockPos targetPos = getBlockPos().relative(facing);

        if (!level.hasChunkAt(targetPos)) return;

        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.getBlock() instanceof IStorageNetworkBlock) return;

        Direction targetSide = facing.getOpposite();
        IItemHandler handler = level.getCapability(
                Capabilities.ItemHandler.BLOCK, targetPos, targetSide);
        BusActor actor = nextBusActor(core, BusOperationDirection.IMPORT);
        if (handler != null) {
            BusItemTransferPlan plan = BusItemTransferPlan.capture(
                    level, getBlockPos(), targetPos, targetSide, core,
                    busConfiguration, handler, actor, BusOperationDirection.IMPORT);
            if (plan != null) {
                BusTransferGuard.run(actor, false, () -> transferOneStack(
                        core,
                        handler,
                        BusFilterPolicy.compile(busConfiguration, level.registryAccess()),
                        actor,
                        stack -> BusRecoveryDrops.spawnDirectOrThrow(
                                level, getBlockPos(), stack),
                        plan));
            }
        }
        BusTransferGuard.run(actor, false, () -> BusTypedResourceTransfer.importOne(
                level, getBlockPos(), targetPos, targetSide, core, busConfiguration,
                pendingResources, actor));
        persistPendingResourceChange();
    }

    private void persistPendingResourceChange() {
        if (!pendingResources.takeChanged()) return;
        setChanged();
        if (level != null) level.invalidateCapabilities(getBlockPos());
    }

    static boolean transferOneStack(StorageCoreBlockEntity core, IItemHandler handler, Actor actor,
                                    Consumer<ItemStack> overflow) {
        return transferOneStack(core, handler, null, actor, overflow, null);
    }

    static boolean transferOneStack(
            StorageCoreBlockEntity core,
            IItemHandler handler,
            BusFilterPolicy filterPolicy,
            Actor actor,
            Consumer<ItemStack> overflow
    ) {
        return transferOneStack(core, handler, filterPolicy, actor, overflow, null);
    }

    private static boolean transferOneStack(
            StorageCoreBlockEntity core,
            IItemHandler handler,
            BusFilterPolicy filterPolicy,
            Actor actor,
            Consumer<ItemStack> overflow,
            BusItemTransferPlan plan
    ) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack peek = handler.extractItem(slot, 64, true);
            if (peek.isEmpty()) continue;
            if (filterPolicy != null && !filterPolicy.allows(ItemKey.of(peek))) continue;
            long accepted = core.insertItem(peek, Action.SIMULATE, actor);
            if (accepted <= 0) continue;
            if (plan != null && !plan.stillValid()) return false;
            ItemStack real = handler.extractItem(slot, (int) accepted, false);
            if (real.isEmpty()) continue;
            if (plan != null && !plan.stillValid()) {
                overflow.accept(real.copy());
                return false;
            }
            if (!ItemStack.isSameItemSameComponents(peek, real)) {
                ItemStack unreturned = handler.insertItem(slot, real.copy(), false);
                if (!unreturned.isEmpty()) overflow.accept(unreturned);
                return true;
            }
            core.insertItem(real, Action.EXECUTE, actor);
            if (!real.isEmpty()) {
                ItemStack unreturned = plan == null || plan.stillValid()
                        ? handler.insertItem(slot, real.copy(), false)
                        : real.copy();
                if (!unreturned.isEmpty()) overflow.accept(unreturned);
            }
            return true;
        }
        return false;
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
            BusActor actor = nextBusActor(core, BusOperationDirection.IMPORT);
            boolean drained = BusTransferGuard.run(
                    actor, false, () -> pendingResources.drainToCore(core, actor));
            persistPendingResourceChange();
            if (!drained) return false;
        }
        return true;
    }

    ItemStack createRecoveryDrop(HolderLookup.Provider registries) {
        ItemStack drop = new ItemStack(MagicStorage.IMPORT_BUS_ITEM.get());
        CompoundTag tag = new CompoundTag();
        saveDropData(tag, registries);
        net.minecraft.world.item.BlockItem.setBlockEntityData(
                drop, MagicStorage.IMPORT_BUS_BE.get(), tag);
        return drop;
    }

    void saveDropData(CompoundTag tag, HolderLookup.Provider registries) {
        busConfiguration.withoutOwner().save(tag, registries);
        pendingResources.save(tag);
        BusRecoveryDrops.saveRecoveryDropId(tag, recoveryDropId);
    }

    @Override
    public BusKind busKind() {
        return BusKind.IMPORT;
    }

    @Override
    public void setBusConfiguration(BusConfiguration configuration) {
        BusConfiguration previous = busConfiguration;
        busConfiguration = configuration;
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
        if (state.hasProperty(ImportBusBlock.DIRECTIONLESS)
                && state.getValue(ImportBusBlock.DIRECTIONLESS) != directionless) {
            level.setBlock(getBlockPos(),
                    state.setValue(ImportBusBlock.DIRECTIONLESS, directionless),
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
        busConfiguration = BusConfiguration.load(tag, BusKind.IMPORT, registries);
        pendingResources = BusResourceEscrow.load(tag);
        boolean escrowAvailable = pendingResources.supported()
                && !pendingResources.hasPending();
        if (level != null && (!previous.equals(busConfiguration)
                || previousEscrowAvailable != escrowAvailable)) {
            level.invalidateCapabilities(getBlockPos());
        }
        syncModeBlockState();
    }
}
