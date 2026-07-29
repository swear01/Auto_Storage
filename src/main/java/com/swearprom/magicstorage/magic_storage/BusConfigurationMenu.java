package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

public final class BusConfigurationMenu extends AbstractContainerMenu {
    public static final int FILTER_SLOT_START = 0;
    public static final int FILTER_SLOT_COUNT = BusConfiguration.MAX_FILTER_RULES;
    public static final int PLAYER_SLOT_START = FILTER_SLOT_START + FILTER_SLOT_COUNT;
    public static final int TOTAL_SLOTS = PLAYER_SLOT_START + 36;
    public static final int DATA_SLOT_COUNT = 9;

    public static final int TOGGLE_MODE_BUTTON = 0;
    public static final int TOGGLE_UNSIDED_BUTTON = 1;
    public static final int TOGGLE_AUTOMATION_BUTTON = 2;
    public static final int TOGGLE_FILTER_MODE_BUTTON = 3;
    public static final int TOGGLE_SIDE_BUTTON_START = 4;

    private final Inventory playerInventory;
    private final net.minecraft.core.BlockPos busPos;
    private final BusKind busKind;
    private final BusConfigurationHost host;
    private final SimpleContainer filterDisplay = new SimpleContainer(FILTER_SLOT_COUNT);
    private long expectedRevision;
    private int mode;
    private int sideMask;
    private int unsidedAccess;
    private int automationEnabled;
    private int filterMode;
    private int canConfigure;
    private int supported;
    private int revisionLow;
    private int revisionHigh;

    public BusConfigurationMenu(
            int containerId,
            Inventory playerInventory,
            ImportBusBlockEntity bus
    ) {
        this(containerId, playerInventory, bus, bus.getBusConfiguration());
    }

    public BusConfigurationMenu(
            int containerId,
            Inventory playerInventory,
            ExportBusBlockEntity bus
    ) {
        this(containerId, playerInventory, bus, bus.getBusConfiguration());
    }

    private BusConfigurationMenu(
            int containerId,
            Inventory playerInventory,
            BusConfigurationHost host,
            BusConfiguration initial
    ) {
        super(MagicStorage.BUS_CONFIGURATION_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.busPos = host.getBlockPos();
        this.busKind = host.busKind();
        this.host = host;
        this.expectedRevision = initial.configRevision();
        initialize(initial);
    }

    public BusConfigurationMenu(
            int containerId,
            Inventory playerInventory,
            RegistryFriendlyByteBuf buffer
    ) {
        super(MagicStorage.BUS_CONFIGURATION_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.busPos = buffer.readBlockPos();
        this.busKind = buffer.readEnum(BusKind.class);
        CompoundTag root = buffer.readNbt();
        BusConfiguration initial = root == null
                ? BusConfiguration.defaults(busKind)
                : BusConfiguration.load(root, busKind, playerInventory.player.registryAccess());
        this.host = null;
        this.expectedRevision = initial.configRevision();
        initialize(initial);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buffer, BusConfigurationHost host) {
        buffer.writeBlockPos(host.getBlockPos());
        buffer.writeEnum(host.busKind());
        CompoundTag root = new CompoundTag();
        host.getBusConfiguration().withoutOwner().save(root, buffer.registryAccess());
        buffer.writeNbt(root);
    }

    static void open(Player player, BusConfigurationHost host, Component title) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, openedBy) -> host instanceof ImportBusBlockEntity importer
                        ? new BusConfigurationMenu(containerId, inventory, importer)
                        : new BusConfigurationMenu(
                        containerId, inventory, (ExportBusBlockEntity) host),
                title), buffer -> writeOpenData(buffer, host));
    }

    private void initialize(BusConfiguration initial) {
        setSnapshot(initial, false);
        for (int index = 0; index < FILTER_SLOT_COUNT; index++) {
            int x = 82 + index % 3 * 18;
            int y = 22 + index / 3 * 18;
            addSlot(new Slot(filterDisplay, index, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        29 + column * 18,
                        90 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 29 + column * 18, 148));
        }
        addDataSlots(createData());
        refreshFilterDisplay(initial);
    }

    private ContainerData createData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                BusConfiguration configuration = host == null
                        ? null : host.getBusConfiguration();
                if (configuration != null) setSnapshot(configuration, true);
                return switch (index) {
                    case 0 -> mode;
                    case 1 -> sideMask;
                    case 2 -> unsidedAccess;
                    case 3 -> automationEnabled;
                    case 4 -> filterMode;
                    case 5 -> canConfigure;
                    case 6 -> supported;
                    case 7 -> revisionLow;
                    case 8 -> revisionHigh;
                    default -> throw new IndexOutOfBoundsException(index);
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> mode = value;
                    case 1 -> sideMask = value;
                    case 2 -> unsidedAccess = value;
                    case 3 -> automationEnabled = value;
                    case 4 -> filterMode = value;
                    case 5 -> canConfigure = value;
                    case 6 -> supported = value;
                    case 7 -> revisionLow = value;
                    case 8 -> revisionHigh = value;
                    default -> throw new IndexOutOfBoundsException(index);
                }
            }

            @Override
            public int getCount() {
                return DATA_SLOT_COUNT;
            }
        };
    }

    private void setSnapshot(BusConfiguration configuration, boolean resolveAccess) {
        mode = configuration.mode().ordinal();
        sideMask = configuration.sideMask();
        unsidedAccess = configuration.unsidedAccess() ? 1 : 0;
        automationEnabled = configuration.automationEnabled() ? 1 : 0;
        filterMode = configuration.filterMode().ordinal();
        canConfigure = resolveAccess && host != null
                && configuration.configRevision() == expectedRevision
                && BusConfigurationAccess.canConfigure(host, playerInventory.player) ? 1 : 0;
        supported = configuration.supported() ? 1 : 0;
        revisionLow = (int) configuration.configRevision();
        revisionHigh = (int) (configuration.configRevision() >>> 32);
    }

    @Override
    public void broadcastChanges() {
        if (host != null) refreshFilterDisplay(host.getBusConfiguration());
        super.broadcastChanges();
    }

    private void refreshFilterDisplay(BusConfiguration configuration) {
        for (int index = 0; index < FILTER_SLOT_COUNT; index++) {
            ItemStack stack = index < configuration.filterRules().size()
                    ? display(configuration.filterRules().get(index))
                    : ItemStack.EMPTY;
            filterDisplay.setItem(index, stack);
        }
    }

    private ItemStack display(BusFilterRule rule) {
        return switch (rule.type()) {
            case EXACT_STACK -> rule.exactKey().orElseThrow().toStack(1);
            case ITEM -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    rule.id().orElseThrow()));
            case RESOURCE -> {
                StorageResourceKey key = rule.resourceKey().orElseThrow();
                ItemStack representative = StorageResourceKinds.representative(
                        key, playerInventory.player.registryAccess());
                yield TerminalResourceDisplay.create(representative, key, 0);
            }
            case TAG, MOD, UNAVAILABLE -> new ItemStack(Items.GRAY_DYE);
        };
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < FILTER_SLOT_START || slotId >= FILTER_SLOT_START + FILTER_SLOT_COUNT) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (host == null || clickType != ClickType.PICKUP) return;
        int index = slotId - FILTER_SLOT_START;
        ItemStack carried = getCarried();
        BusFilterRule replacement = carried.isEmpty() ? null : detectRule(carried);
        if (!carried.isEmpty() && replacement == null) return;
        commit(player, current -> replaceRule(current, index, replacement));
    }

    private BusFilterRule detectRule(ItemStack carried) {
        ItemStack single = carried.copyWithCount(1);
        LinkedHashSet<StorageResourceKey> typed = new LinkedHashSet<>();
        for (StorageResourceContainerStrategy strategy : StorageResourceContainerStrategies.all()) {
            strategy.planDeposit(single.copy(), playerInventory.player.registryAccess())
                    .map(StorageResourceContainerStrategy.Transfer::key)
                    .ifPresent(typed::add);
        }
        if (typed.size() > 1) return null;
        return typed.size() == 1
                ? BusFilterRule.resource(typed.getFirst())
                : BusFilterRule.exact(single);
    }

    private BusConfiguration replaceRule(
            BusConfiguration current,
            int index,
            BusFilterRule replacement
    ) {
        List<BusFilterRule> rules = new ArrayList<>(current.filterRules());
        if (replacement == null) {
            if (index >= rules.size()) return current;
            rules.remove(index);
        } else if (index < rules.size()) {
            rules.set(index, replacement);
        } else if (index == rules.size()) {
            rules.add(replacement);
        } else {
            return current;
        }
        return copy(current, current.mode(), current.sideMask(), current.unsidedAccess(),
                current.automationEnabled(), current.filterMode(), rules);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (host == null) return false;
        if (id == TOGGLE_MODE_BUTTON) {
            return commit(player, current -> copy(
                    current,
                    current.mode() == BusMode.DIRECTIONAL
                            ? BusMode.DIRECTIONLESS : BusMode.DIRECTIONAL,
                    current.sideMask(), current.unsidedAccess(),
                    current.automationEnabled(), current.filterMode(), current.filterRules()));
        }
        if (id == TOGGLE_UNSIDED_BUTTON) {
            return commit(player, current -> copy(
                    current, current.mode(), current.sideMask(), !current.unsidedAccess(),
                    current.automationEnabled(), current.filterMode(), current.filterRules()));
        }
        if (id == TOGGLE_AUTOMATION_BUTTON) {
            return commit(player, current -> copy(
                    current, current.mode(), current.sideMask(), current.unsidedAccess(),
                    !current.automationEnabled(), current.filterMode(), current.filterRules()));
        }
        if (id == TOGGLE_FILTER_MODE_BUTTON) {
            return commit(player, current -> copy(
                    current, current.mode(), current.sideMask(), current.unsidedAccess(),
                    current.automationEnabled(),
                    current.filterMode() == BusFilterMode.ALLOW
                            ? BusFilterMode.DENY : BusFilterMode.ALLOW,
                    current.filterRules()));
        }
        int sideIndex = id - TOGGLE_SIDE_BUTTON_START;
        if (sideIndex >= 0 && sideIndex < Direction.values().length) {
            return commit(player, current -> copy(
                    current, current.mode(), current.sideMask() ^ 1 << sideIndex,
                    current.unsidedAccess(), current.automationEnabled(),
                    current.filterMode(), current.filterRules()));
        }
        return false;
    }

    private boolean commit(Player player, java.util.function.UnaryOperator<BusConfiguration> editor) {
        boolean updated = host.updateBusConfiguration(player, expectedRevision, editor);
        if (!updated) return false;
        expectedRevision = host.getBusConfiguration().configRevision();
        refreshFilterDisplay(host.getBusConfiguration());
        broadcastChanges();
        return true;
    }

    private static BusConfiguration copy(
            BusConfiguration current,
            BusMode mode,
            int sideMask,
            boolean unsidedAccess,
            boolean automationEnabled,
            BusFilterMode filterMode,
            List<BusFilterRule> rules
    ) {
        return BusConfiguration.current(
                mode,
                sideMask,
                unsidedAccess,
                automationEnabled,
                filterMode,
                rules,
                current.owner(),
                current.nextRevision());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < PLAYER_SLOT_START || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        int relative = index - PLAYER_SLOT_START;
        boolean moved = relative < 27
                ? moveItemStackTo(original, PLAYER_SLOT_START + 27, TOTAL_SLOTS, false)
                : moveItemStackTo(original, PLAYER_SLOT_START, PLAYER_SLOT_START + 27, false);
        if (!moved) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (host == null) return true;
        return host.getLevel() != null
                && host.getLevel().getBlockEntity(busPos) == host
                && player.distanceToSqr(
                busPos.getX() + 0.5,
                busPos.getY() + 0.5,
                busPos.getZ() + 0.5) <= 64.0;
    }

    public BusKind getBusKind() {
        return busKind;
    }

    public BusMode getMode() {
        return BusMode.values()[Math.clamp(mode, 0, BusMode.values().length - 1)];
    }

    public int getSideMask() {
        return sideMask;
    }

    public boolean hasUnsidedAccess() {
        return unsidedAccess != 0;
    }

    public boolean isAutomationEnabled() {
        return automationEnabled != 0;
    }

    public BusFilterMode getFilterMode() {
        return BusFilterMode.values()[Math.clamp(
                filterMode, 0, BusFilterMode.values().length - 1)];
    }

    public boolean canConfigure() {
        return canConfigure != 0;
    }

    public boolean isSupported() {
        return supported != 0;
    }

    public long getConfigRevision() {
        return Integer.toUnsignedLong(revisionLow) | (long) revisionHigh << 32;
    }
}
