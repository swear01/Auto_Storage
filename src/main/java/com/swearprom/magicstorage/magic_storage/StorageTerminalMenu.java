package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorageTerminalMenu extends AbstractContainerMenu {
    static final int SORT_ORDER_BUTTON = 11;
    static final int NEXT_SORT_MODE_BUTTON = 12;
    static final int NEXT_SEARCH_MODE_BUTTON = 13;
    static final int PREVIOUS_SORT_MODE_BUTTON = 17;
    static final int PREVIOUS_SEARCH_MODE_BUTTON = 18;
    static final int RESET_SORT_ORDER_BUTTON = 21;
    static final int RESET_SORT_MODE_BUTTON = 22;
    static final int RESET_SEARCH_MODE_BUTTON = 23;
    static final int NEXT_RESOURCE_VIEW_BUTTON = 26;
    static final int PREVIOUS_RESOURCE_VIEW_BUTTON = 27;
    static final int RESET_RESOURCE_VIEW_BUTTON = 28;

    public static final int MAX_DISPLAY_ROWS = 9;
    public static final int DISPLAY_COLS = 9;
    public static final int DISPLAY_SLOTS = MAX_DISPLAY_ROWS * DISPLAY_COLS;
    public static final int PLAYER_INVENTORY_SLOTS = 36;
    private BlockPos corePos;
    private BlockPos accessPos;
    private boolean remoteAccess;
    private final UUID coreId;
    private final ResourceKey<Level> coreDimension;
    private List<BlockPos> accessPath = List.of();
    protected final SimpleContainer displayInventory;
    protected int scrollOffset;
    protected int totalItemTypes;
    protected String currentFilter = "";
    protected int displayTypeCount = 0;
    protected int displayMaxTypes = 0;
    protected boolean displayUnlimitedTypeCapacity;
    private int visibleRows = 6;
    private SortMode sortMode = SortMode.NAME;
    private SortOrder sortOrder = SortOrder.ASCENDING;
    private SearchMode searchMode = SearchMode.OFF;
    private TerminalResourceView resourceView = TerminalResourceView.ITEM;
    private StorageCoreBlockEntity observedCore;
    private long observedTopologyRevision;
    private boolean storageDirty;
    private boolean energyDirty;
    private final Map<ResourceLocation, Long> stationWorkIncreases = new HashMap<>();
    private boolean stationWorkDecreased;
    private final StorageListener storageListener = new StorageListener() {
        @Override
        public void onChanged(ItemKey key, long delta, long newAmount, Actor actor) {
            storageDirty = true;
        }

        @Override
        public void onEnergyChanged(EnergyType type, long newAmount) {
            energyDirty = true;
        }

        @Override
        public void onStationWorkChanged(
                ResourceLocation descriptorId,
                long delta,
                long newAmount
        ) {
            if (delta < 0) stationWorkDecreased = true;
            else stationWorkIncreases.merge(descriptorId, newAmount, Math::max);
        }

        @Override
        public void onResourceChanged(
                StorageResourceKey key,
                long delta,
                long newAmount,
                Actor actor
        ) {
            if (StorageResourceBridge.stationWorkDescriptorId(key).isPresent()) return;
            if (key.kindId().equals(StorageResourceBridge.WORK_KIND)) energyDirty = true;
            else storageDirty = true;
        }
    };

    public StorageTerminalMenu(int containerId, Inventory playerInv, StorageCoreBlockEntity core) {
        this(MagicStorage.STORAGE_TERMINAL_MENU.get(), containerId, playerInv, core, core.getBlockPos(), false);
    }

    public StorageTerminalMenu(int containerId, Inventory playerInv, StorageCoreBlockEntity core, BlockPos accessPos, boolean remoteAccess) {
        this(MagicStorage.STORAGE_TERMINAL_MENU.get(), containerId, playerInv, core, accessPos, remoteAccess);
    }

    private void addTypeDataSlots() {
        addDataSlots(new net.minecraft.world.inventory.SimpleContainerData(13) {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> getIntWord(displayTypeCount, 0);
                    case 1 -> getIntWord(displayTypeCount, 1);
                    case 2 -> getIntWord(displayMaxTypes, 0);
                    case 3 -> getIntWord(displayMaxTypes, 1);
                    case 4 -> sortMode.ordinal();
                    case 5 -> sortOrder.ordinal();
                    case 6 -> searchMode.ordinal();
                    case 7 -> getIntWord(totalItemTypes, 0);
                    case 8 -> getIntWord(totalItemTypes, 1);
                    case 9 -> getIntWord(scrollOffset, 0);
                    case 10 -> getIntWord(scrollOffset, 1);
                    case 11 -> displayUnlimitedTypeCapacity ? 1 : 0;
                    default -> resourceView.ordinal();
                };
            }
            @Override public void set(int i, int v) {
                switch (i) {
                    case 0 -> displayTypeCount = setIntWord(displayTypeCount, 0, v);
                    case 1 -> displayTypeCount = setIntWord(displayTypeCount, 1, v);
                    case 2 -> displayMaxTypes = setIntWord(displayMaxTypes, 0, v);
                    case 3 -> displayMaxTypes = setIntWord(displayMaxTypes, 1, v);
                    case 4 -> sortMode = SortMode.values()[v];
                    case 5 -> sortOrder = SortOrder.values()[v];
                    case 6 -> searchMode = SearchMode.values()[v];
                    case 7 -> totalItemTypes = setIntWord(totalItemTypes, 0, v);
                    case 8 -> totalItemTypes = setIntWord(totalItemTypes, 1, v);
                    case 9 -> scrollOffset = setIntWord(scrollOffset, 0, v);
                    case 10 -> scrollOffset = setIntWord(scrollOffset, 1, v);
                    case 11 -> displayUnlimitedTypeCapacity = v != 0;
                    default -> resourceView = TerminalResourceView.byId(v);
                }
            }
            @Override public int getCount() { return 13; }
        });
    }

    private static int getIntWord(int value, int word) {
        return value >>> (word * 16) & 0xFFFF;
    }

    private static int setIntWord(int current, int word, int value) {
        int shift = word * 16;
        int mask = 0xFFFF << shift;
        return current & ~mask | (value & 0xFFFF) << shift;
    }

    public int getTypeCount() { return displayTypeCount; }
    public int getMaxTypes() { return displayMaxTypes; }
    public boolean hasUnlimitedTypeCapacity() { return displayUnlimitedTypeCapacity; }
    public SortMode getSortMode() { return sortMode; }
    public SortOrder getSortOrder() { return sortOrder; }
    public SearchMode getSearchMode() { return searchMode; }
    public TerminalResourceView getResourceView() { return resourceView; }

    public TerminalPreferences getTerminalPreferences() {
        return new TerminalPreferences(
                sortMode,
                sortOrder,
                searchMode,
                resourceView,
                CraftingTerminalPage.STORAGE,
                false,
                TerminalOutputDestination.PLAYER,
                null);
    }

    StorageTerminalMenu(MenuType<?> menuType, int containerId, Inventory playerInv, StorageCoreBlockEntity core) {
        this(menuType, containerId, playerInv, core, core.getBlockPos(), false);
    }

    StorageTerminalMenu(MenuType<?> menuType, int containerId, Inventory playerInv, StorageCoreBlockEntity core, BlockPos accessPos, boolean remoteAccess) {
        this(menuType, containerId, playerInv, core, accessPos, remoteAccess, false);
    }

    protected StorageTerminalMenu(
            MenuType<?> menuType,
            int containerId,
            Inventory playerInv,
            StorageCoreBlockEntity core,
            BlockPos accessPos,
            boolean remoteAccess,
            boolean deferInitialization
    ) {
        super(menuType, containerId);
        if (!core.isStorageAvailable()) {
            throw new IllegalArgumentException("Cannot open a terminal for unavailable Core storage");
        }
        this.corePos = core.getBlockPos();
        this.accessPos = accessPos;
        this.remoteAccess = remoteAccess;
        this.coreId = core.getNetworkId();
        this.coreDimension = core.getLevel() != null ? core.getLevel().dimension() : playerInv.player.level().dimension();
        this.displayInventory = createDisplayInventory();
        this.scrollOffset = 0;
        if (!deferInitialization) initializeStorageMenu(playerInv, core);
    }

    protected StorageTerminalMenu(MenuType<?> menuType, int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(menuType, containerId, playerInv, buf, false);
    }

    protected StorageTerminalMenu(
            MenuType<?> menuType,
            int containerId,
            Inventory playerInv,
            RegistryFriendlyByteBuf buf,
            boolean deferInitialization
    ) {
        super(menuType, containerId);
        this.corePos = buf.readBlockPos();
        this.accessPos = buf.readBlockPos();
        this.remoteAccess = buf.readBoolean();
        this.coreId = null;
        this.coreDimension = playerInv.player.level().dimension();
        this.displayInventory = createDisplayInventory();
        this.scrollOffset = 0;
        if (!deferInitialization) initializeStorageMenu(playerInv, null);
    }

    protected final void initializeStorageMenu(Inventory playerInv, StorageCoreBlockEntity core) {
        setupSlots(playerInv);
        if (core != null) refreshDisplayItems(core);
        addTypeDataSlots();
        if (core != null) {
            observedCore = core;
            observedTopologyRevision = core.getTopologyRevision();
            observedCore.addListener(storageListener);
        }
    }

    private static SimpleContainer createDisplayInventory() {
        return new SimpleContainer(DISPLAY_SLOTS) {
            @Override
            public int getMaxStackSize(ItemStack stack) {
                return Integer.MAX_VALUE;
            }
        };
    }

    protected void setupSlots(Inventory playerInv) {
        int gridTop = 19;
        for (int row = 0; row < MAX_DISPLAY_ROWS; row++) {
            for (int col = 0; col < DISPLAY_COLS; col++) {
                int slotIndex = col + row * DISPLAY_COLS;
                this.addSlot(new GhostSlot(displayInventory, slotIndex, 8 + col * 18, gridTop + row * 18));
            }
        }
        int playerInvTop = gridTop + MAX_DISPLAY_ROWS * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, playerInvTop + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, playerInvTop + 3 * 18 + 4));
        }
    }

    protected StorageCoreBlockEntity getCore(Level level) {
        if (level == null || coreId == null || !level.dimension().equals(coreDimension)
                || !level.hasChunkAt(corePos)) return null;
        if (level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core) {
            return core.isStorageAvailable() && core.getNetworkId().equals(coreId)
                    && hasStorageAccess(level, core) ? core : null;
        }
        return null;
    }

    private boolean hasStorageAccess(Level level, StorageCoreBlockEntity core) {
        if (remoteAccess) return true;
        if (accessPos == null || !core.getConnectedBlocks().contains(accessPos)) return false;
        if (MagicStorage.hasLoadedNetworkPath(level, accessPath, accessPos, corePos)) return true;
        accessPath = MagicStorage.findLoadedNetworkPath(level, accessPos, corePos);
        return !accessPath.isEmpty();
    }

    public void refreshDisplayItems(StorageCoreBlockEntity core) {
        refreshDisplayItemsFiltered(core, currentFilter);
    }

    public void refreshDisplayItemsFiltered(StorageCoreBlockEntity core, String filter) {
        this.currentFilter = filter != null ? filter : "";
        if (core == null) {
            totalItemTypes = 0;
            replaceVisibleDisplayStacks(List.of(), visibleRows);
            return;
        }
        java.util.List<ItemStack> stacks = core.getTerminalDisplayStacks(
                currentFilter, sortMode, sortOrder, resourceView);
        totalItemTypes = stacks.size();
        refreshDisplayMetadata(core);
        int maxOffset = Math.max(0, totalItemTypes - visibleRows * DISPLAY_COLS);
        scrollOffset = Math.min(scrollOffset, maxOffset);
        replaceVisibleDisplayStacks(stacks, visibleRows);
    }

    protected final void refreshDisplayMetadata(StorageCoreBlockEntity core) {
        displayTypeCount = core.getTypeCount();
        displayMaxTypes = core.getTotalTypeSlots();
        displayUnlimitedTypeCapacity = core.getTypeCapacity().unlimited();
    }

    protected final void replaceVisibleDisplayStacks(List<ItemStack> stacks, int rows) {
        for (int i = 0; i < DISPLAY_SLOTS; i++) {
            int idx = scrollOffset + i;
            ItemStack next = idx < stacks.size() && i < rows * DISPLAY_COLS
                    ? stacks.get(idx) : ItemStack.EMPTY;
            ItemStack current = displayInventory.getItem(i);
            if (current.getCount() != next.getCount()
                    || !ItemStack.isSameItemSameComponents(current, next)) {
                displayInventory.setItem(i, next.copy());
            }
        }
    }

    public boolean applyFilter(StorageCoreBlockEntity core, String filter) {
        String normalized = filter != null ? filter : "";
        if (normalized.equals(currentFilter)) return false;
        refreshDisplayItemsFiltered(core, normalized);
        return true;
    }

    public void scrollBy(int delta) {
        int maxOffset = Math.max(0, totalItemTypes - visibleRows * DISPLAY_COLS);
        scrollOffset = Math.clamp(scrollOffset + delta, 0, maxOffset);
    }

    public void scrollTo(int offset) {
        int maxOffset = Math.max(0, totalItemTypes - visibleRows * DISPLAY_COLS);
        scrollOffset = Math.clamp(offset, 0, maxOffset);
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public int getTotalItemTypes() {
        return totalItemTypes;
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        if (slotIndex >= 0 && slotIndex < DISPLAY_SLOTS) {
            boolean pickup = clickType == ClickType.PICKUP && (button == 0 || button == 1);
            boolean quickMove = clickType == ClickType.QUICK_MOVE && (button == 0 || button == 1);
            if (!pickup && !quickMove) return;
            if (!player.level().isClientSide()) {
                Slot slot = getSlot(slotIndex);
                ItemStack displayStack = slot.getItem();
                if (TerminalResourceDisplay.isTyped(displayStack)) return;
                if (!displayStack.isEmpty() && getCarried().isEmpty()) {
                    StorageCoreBlockEntity core = getCore(player.level());
                    if (core != null) {
                        var key = ItemKey.of(displayStack);
                        long actualCount = core.getItemCount(key);
                        long maxStack = displayStack.getMaxStackSize();
                        long amount = quickMove
                                ? Math.min(actualCount, maxStack * 36)
                                : button == 0
                                        ? Math.min(actualCount, maxStack)
                                        : Math.max(1, (Math.min(actualCount, maxStack) + 1) / 2);
                        if (amount <= 0) amount = 1;
                        if (quickMove) {
                            if (extractToPlayer(core, key, amount, player) > 0) {
                                refreshDisplayItemsFiltered(core, currentFilter);
                                broadcastChanges();
                            }
                        } else {
                            ItemStack extracted = core.extractItem(key, amount, Action.EXECUTE, Actor.player(player));
                            if (!extracted.isEmpty()) {
                                setCarried(extracted);
                                refreshDisplayItemsFiltered(core, currentFilter);
                                broadcastChanges();
                            }
                        }
                    }
                }
            }
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    public boolean handleHeldContainerTransfer(
            TerminalHeldContainerTransferPacket packet,
            Player player
    ) {
        if (player.level().isClientSide()
                || player.isSpectator()
                || !stillValid(player)
                || packet.containerId() != containerId
                || packet.stateId() != getStateId()
                || packet.expectedView() != resourceView
                || resourceView == TerminalResourceView.ITEM
                || packet.slotIndex() < 0
                || packet.slotIndex() >= visibleRows * DISPLAY_COLS
                || packet.slotIndex() >= DISPLAY_SLOTS
                || getCarried().isEmpty()
                || this instanceof CraftingTerminalMenu crafting
                && crafting.getPage() != CraftingTerminalPage.STORAGE) return false;
        if (packet.direction() == TerminalContainerTransferDirection.DEPOSIT) {
            return depositHeldResourceContainer(player);
        }
        ItemStack displayStack = getSlot(packet.slotIndex()).getItem();
        return fillHeldResourceContainer(displayStack, player);
    }

    private boolean fillHeldResourceContainer(ItemStack displayStack, Player player) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) return false;
        StorageResourceKey key = TerminalResourceDisplay.key(displayStack).orElse(null);
        if (key == null || !resourceView.matches(key)) return false;
        StorageCoreBlockEntity core = getCore(player.level());
        if (core == null) return false;

        long stored = core.getResourceAmount(key);
        if (stored <= 0) return false;
        var transfer = StorageResourceContainerStrategies.find(key.kindId())
                .flatMap(strategy -> strategy.planWithdraw(
                        carried.copyWithCount(1), key, stored, player.level().registryAccess()))
                .filter(candidate -> candidate.key().equals(key))
                .orElse(null);
        return transfer != null && commitContainerTransfer(core, transfer, false, player);
    }

    private boolean depositHeldResourceContainer(Player player) {
        if (resourceView == TerminalResourceView.ITEM) return false;
        ItemStack carried = getCarried();
        if (carried.isEmpty()) return false;
        StorageCoreBlockEntity core = getCore(player.level());
        if (core == null) return false;
        ItemStack singleContainer = carried.copyWithCount(1);
        for (StorageResourceContainerStrategy strategy : StorageResourceContainerStrategies.all()) {
            var transfer = strategy.planDeposit(
                    singleContainer.copy(), player.level().registryAccess()).orElse(null);
            if (transfer == null || !transfer.key().kindId().equals(strategy.kindId())
                    || !resourceView.matches(transfer.key())) continue;
            return commitContainerTransfer(core, transfer, true, player);
        }
        return false;
    }

    private boolean commitContainerTransfer(
            StorageCoreBlockEntity core,
            StorageResourceContainerStrategy.Transfer transfer,
            boolean deposit,
            Player player
    ) {
        if (!StorageResourceKinds.accepts(transfer.key())) return false;
        ContainerResultPlacement placement = planContainerResultPlacement(
                getCarried(), transfer.resultContainer(), player);
        if (placement == null) return false;
        long delta = deposit ? transfer.amount() : -transfer.amount();
        StorageResourceTransaction transaction = StorageResourceTransaction.builder()
                .add(transfer.key(), delta)
                .build();
        Actor actor = Actor.player(player);
        if (!core.applyResourceTransaction(transaction, Action.SIMULATE, actor)) return false;

        core.beginMutationBatch();
        try {
            if (!core.applyResourceTransaction(transaction, Action.EXECUTE, actor)) return false;
            placement.apply(this, player);
        } finally {
            core.endMutationBatch();
        }
        refreshDisplayItemsFiltered(core, currentFilter);
        broadcastChanges();
        return true;
    }

    private ContainerResultPlacement planContainerResultPlacement(
            ItemStack carried,
            ItemStack result,
            Player player
    ) {
        if (carried.isEmpty()) return null;
        if (carried.getCount() == 1) {
            return new ContainerResultPlacement(result.copy(), -1, ItemStack.EMPTY);
        }
        ItemStack remaining = carried.copyWithCount(carried.getCount() - 1);
        if (result.isEmpty()) return new ContainerResultPlacement(remaining, -1, ItemStack.EMPTY);
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack existing = player.getInventory().items.get(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, result)
                    && existing.getCount() + result.getCount() <= existing.getMaxStackSize()) {
                ItemStack updated = existing.copyWithCount(existing.getCount() + result.getCount());
                return new ContainerResultPlacement(remaining, slot, updated);
            }
        }
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            if (player.getInventory().items.get(slot).isEmpty()) {
                return new ContainerResultPlacement(remaining, slot, result.copy());
            }
        }
        return null;
    }

    private record ContainerResultPlacement(
            ItemStack carried,
            int inventorySlot,
            ItemStack inventoryResult
    ) {
        private void apply(StorageTerminalMenu menu, Player player) {
            menu.setCarried(carried.copy());
            if (inventorySlot >= 0) {
                player.getInventory().setItem(inventorySlot, inventoryResult.copy());
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stackInSlot = slot.getItem();

        if (index < DISPLAY_SLOTS) {
            if (TerminalResourceDisplay.isTyped(stackInSlot)) return ItemStack.EMPTY;
            if (!player.level().isClientSide()) {
                StorageCoreBlockEntity core = getCore(player.level());
                if (core != null) {
                    var key = ItemKey.of(stackInSlot);
                    long actualCount = core.getItemCount(key);
                    long amount = Math.min(actualCount, stackInSlot.getMaxStackSize() * 36);
                    if (extractToPlayer(core, key, amount, player) > 0) {
                        refreshDisplayItems(core);
                        broadcastChanges();
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        if (!player.level().isClientSide()) {
            StorageCoreBlockEntity core = getCore(player.level());
            if (core != null) {
                ItemStack toInsert = stackInSlot.copy();
                long inserted = core.insertItem(toInsert, Action.EXECUTE, Actor.player(player));
                if (inserted > 0) {
                    stackInSlot.shrink((int) inserted);
                    slot.setChanged();
                    refreshDisplayItems(core);
                    broadcastChanges();
                }
            }
        }

        return ItemStack.EMPTY;
    }

    private long extractToPlayer(StorageCoreBlockEntity core, ItemKey key, long amount, Player player) {
        long moved = 0;
        int maxStackSize = key.toStack(1).getMaxStackSize();
        while (moved < amount) {
            int request = (int) Math.min(amount - moved, maxStackSize);
            ItemStack extracted = core.extractItem(key, request, Action.EXECUTE, Actor.player(player));
            if (extracted.isEmpty()) break;
            int extractedCount = extracted.getCount();
            if (!player.getInventory().add(extracted) && !extracted.isEmpty()) {
                player.drop(extracted, false);
            }
            moved += extractedCount;
        }
        return moved;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != 0 && buttonId != 1
                && buttonId != SORT_ORDER_BUTTON
                && buttonId != NEXT_SORT_MODE_BUTTON
                && buttonId != NEXT_SEARCH_MODE_BUTTON
                && buttonId != PREVIOUS_SORT_MODE_BUTTON
                && buttonId != PREVIOUS_SEARCH_MODE_BUTTON
                && buttonId != RESET_SORT_ORDER_BUTTON
                && buttonId != RESET_SORT_MODE_BUTTON
                && buttonId != RESET_SEARCH_MODE_BUTTON
                && buttonId != NEXT_RESOURCE_VIEW_BUTTON
                && buttonId != PREVIOUS_RESOURCE_VIEW_BUTTON
                && buttonId != RESET_RESOURCE_VIEW_BUTTON) {
            return false;
        }
        if (!player.level().isClientSide()) {
            StorageCoreBlockEntity core = getCore(player.level());
            if (core != null) {
                switch (buttonId) {
                    case 0 -> scrollBy(-DISPLAY_COLS);
                    case 1 -> scrollBy(DISPLAY_COLS);
                    case SORT_ORDER_BUTTON -> sortOrder = SortOrder.toggle(sortOrder);
                    case NEXT_SORT_MODE_BUTTON -> sortMode = sortMode.next();
                    case NEXT_SEARCH_MODE_BUTTON -> searchMode = searchMode.next();
                    case PREVIOUS_SORT_MODE_BUTTON -> sortMode = sortMode.previous();
                    case PREVIOUS_SEARCH_MODE_BUTTON -> searchMode = searchMode.previous();
                    case RESET_SORT_ORDER_BUTTON -> sortOrder = SortOrder.ASCENDING;
                    case RESET_SORT_MODE_BUTTON -> sortMode = SortMode.NAME;
                    case RESET_SEARCH_MODE_BUTTON -> searchMode = SearchMode.OFF;
                    case NEXT_RESOURCE_VIEW_BUTTON -> resourceView = resourceView.nextAvailable();
                    case PREVIOUS_RESOURCE_VIEW_BUTTON -> resourceView = resourceView.previousAvailable();
                    case RESET_RESOURCE_VIEW_BUTTON -> resourceView = TerminalResourceView.ITEM;
                }
                refreshDisplayItems(core);
            }
        }
        return true;
    }

    public boolean applySettings(TerminalSettingsPacket packet, Player player) {
        int rows = Math.clamp(packet.visibleRows(), 3, MAX_DISPLAY_ROWS);
        TerminalPreferences preferences = packet.preferences();
        TerminalResourceView requestedView = preferences.resourceView().availableOrItem();
        boolean changed = rows != visibleRows
                || sortMode != preferences.sortMode()
                || sortOrder != preferences.sortOrder()
                || searchMode != preferences.searchMode()
                || resourceView != requestedView;
        visibleRows = rows;
        sortMode = preferences.sortMode();
        sortOrder = preferences.sortOrder();
        searchMode = preferences.searchMode();
        resourceView = requestedView;
        return changed;
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    @Override
    public boolean stillValid(Player player) {
        if (corePos == null) return false;
        StorageCoreBlockEntity core = getCore(player.level());
        if (core == null) return false;
        if (remoteAccess) return true;
        if (accessPos == null) return false;
        var accessBlock = player.level().getBlockState(accessPos).getBlock();
        boolean accessExists = accessPos.equals(corePos)
                ? accessBlock instanceof StorageCoreBlock
                : accessBlock instanceof TerminalBlock;
        return accessExists
                && player.distanceToSqr(accessPos.getX() + 0.5, accessPos.getY() + 0.5, accessPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void broadcastChanges() {
        boolean observedCoreValid = observedCore != null && getCore(observedCore.getLevel()) == observedCore;
        boolean topologyDirty = observedCoreValid
                && observedCore.getTopologyRevision() != observedTopologyRevision;
        if ((storageDirty || topologyDirty) && observedCoreValid) {
            storageDirty = false;
            observedTopologyRevision = observedCore.getTopologyRevision();
            refreshDisplayItems(observedCore);
            onObservedStorageChanged(observedCore);
        }
        if (energyDirty && observedCoreValid) {
            energyDirty = false;
            onObservedEnergyChanged(observedCore);
        }
        if ((stationWorkDecreased || !stationWorkIncreases.isEmpty()) && observedCoreValid) {
            boolean decreased = stationWorkDecreased;
            Map<ResourceLocation, Long> increases = Map.copyOf(stationWorkIncreases);
            stationWorkDecreased = false;
            stationWorkIncreases.clear();
            onObservedStationWorkChanged(observedCore, increases, decreased);
        }
        super.broadcastChanges();
    }

    protected void onObservedStorageChanged(StorageCoreBlockEntity core) {
    }

    protected void onObservedEnergyChanged(StorageCoreBlockEntity core) {
        if (resourceView == TerminalResourceView.ENERGY) refreshDisplayItems(core);
    }

    protected void onObservedStationWorkChanged(
            StorageCoreBlockEntity core,
            Map<ResourceLocation, Long> increases,
            boolean decreased
    ) {
        if (resourceView == TerminalResourceView.STATION_WORK) refreshDisplayItems(core);
    }

    @Override
    public void removed(Player player) {
        if (observedCore != null) {
            observedCore.removeListener(storageListener);
            observedCore = null;
        }
        super.removed(player);
        if (!player.level().isClientSide()) {
            displayInventory.clearContent();
        }
    }
}
