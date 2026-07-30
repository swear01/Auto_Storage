package com.swear.autostorage;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StorageTerminalScreen<T extends StorageTerminalMenu> extends AbstractContainerScreen<T> {

    protected static final ResourceLocation ICONS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "textures/gui/icons.png");
    protected static final ResourceLocation TERMINAL_CONTROLS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    AutoStorage.MODID, "textures/gui/terminal_controls.png");

    private static final int SCROLL_TRACK_INSET = 1;
    private static final int SEARCH_DEBOUNCE_TICKS = 2;
    private static final int FLUID_RESOURCE_BACKGROUND = 0xB0245966;
    private static final int FLUID_RESOURCE_BORDER = 0xFF65E6FF;
    private static final int ENERGY_RESOURCE_BACKGROUND = 0xB04F3F78;
    private static final int ENERGY_RESOURCE_BORDER = 0xFFD69BFF;
    private static final int OTHER_RESOURCE_BACKGROUND = 0xB03F7692;
    private static final int OTHER_RESOURCE_BORDER = 0xFF7BD7FF;
    private static final int STATION_WORK_BACKGROUND = 0xB0785128;
    private static final int STATION_WORK_BORDER = 0xFFFFC86A;
    private static final ResourceLocation SCROLLER_SPRITE =
            ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE =
            ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    private final List<Slot> semanticSlots;
    private final TerminalPreferenceSession preferenceSession;
    private final TerminalSearchSynchronizer searchSynchronizer;
    private EditBox searchBox;
    private final TerminalScrollbar scrollbar = new TerminalScrollbar();
    private int lastRequestedScroll = Integer.MIN_VALUE;
    private int lastObservedScroll = Integer.MIN_VALUE;
    private int searchTimer;
    private String lastSentSearch = "";
    private boolean searchBoxAutoSelected;
    private float networkAmountScale = 1.0F;
    protected int visibleRows;
    protected TerminalLayout.Geometry geometry;
    private SearchMode lastSeenSearchMode = SearchMode.OFF;
    private SortMode lastSeenSortMode = SortMode.NAME;
    private SortOrder lastSeenSortOrder = SortOrder.ASCENDING;
    private TerminalResourceView lastSeenResourceView = TerminalResourceView.ITEM;

    private TerminalCycleButton sortOrderBtn;
    private TerminalCycleButton sortModeBtn;
    private TerminalIconButton autoFocusBtn;
    private TerminalCycleButton searchModeBtn;
    private TerminalCycleButton resourceViewBtn;

    public StorageTerminalScreen(T menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.semanticSlots = List.copyOf(menu.slots);
        TerminalPreferences preferences = TerminalClientPreferences.load();
        this.preferenceSession = new TerminalPreferenceSession(
                preferences,
                menu instanceof CraftingTerminalMenu
                        ? TerminalPreferenceSession.Scope.CRAFTING
                        : TerminalPreferenceSession.Scope.STORAGE);
        this.searchSynchronizer = ClientSetup.createTerminalSearchSynchronizer();
    }

    protected TerminalLayout.Geometry createGeometry() {
        return TerminalLayout.forProfile(
                terminalProfile(), this.width, this.height, fuelDescriptorCounts());
    }

    protected TerminalProfile terminalProfile() {
        return TerminalProfile.STORAGE;
    }

    protected TerminalLayout.FuelDescriptorCounts fuelDescriptorCounts() {
        return TerminalLayout.FuelDescriptorCounts.none();
    }

    protected void addTerminalProfileControls() {
    }

    protected boolean isItemViewActive() {
        return true;
    }

    protected final TerminalPreferences displayedPreferences() {
        return preferenceSession.presentation(menu.getTerminalPreferences());
    }

    protected int playerInvLocalTop() {
        return geometry.playerInventory().y();
    }

    protected int playerInvLocalLeft() {
        return geometry.playerInventory().x();
    }

    protected TerminalLayout.Rect railButton(int index) {
        return geometry.railButtons().get(index);
    }

    @Override
    protected void init() {
        String previousSearchValue = searchBox != null ? searchBox.getValue() : "";
        boolean previousSearchFocused = searchBox != null && searchBox.isFocused();
        this.geometry = createGeometry();
        this.visibleRows = geometry.visibleRows();
        this.imageWidth = geometry.imageWidth();
        this.imageHeight = geometry.imageHeight();
        this.inventoryLabelX = geometry.playerInventory().x();
        this.inventoryLabelY = geometry.playerInventory().y() - 12;
        super.init();
        this.leftPos = geometry.centeredFrameLeft(this.width);
        this.networkAmountScale = TerminalAmountFormatter.scaleForSlot(
                font::width, TerminalLayout.ICON_CANVAS_SIZE);

        TerminalLayout.Rect search = geometry.searchBox();
        this.searchBox = new EditBox(font, leftPos + search.x(), topPos + search.y(),
                search.width(), font.lineHeight, Component.translatable("gui.auto_storage.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setMaxLength(50);
        this.searchBox.setTooltip(Tooltip.create(
                Component.translatable("tooltip.auto_storage.search_help")));
        this.searchBox.setValue(previousSearchValue);
        this.searchBox.setResponder(this::scheduleSearch);
        this.searchBoxAutoSelected = TerminalClientPreferences.searchBoxAutoSelected();
        this.addRenderableWidget(searchBox);
        configureSearchBoxFocus(searchBoxAutoSelected || previousSearchFocused);

        addTerminalProfileControls();
        int viewStart = terminalProfile().viewControlStartIndex();
        sortOrderBtn = addCycleButton(
                TerminalControlIcon.SORT_ASCENDING,
                Component.translatable("tooltip.auto_storage.sort_order"),
                railButton(viewStart),
                direction -> sendButton(StorageTerminalMenu.SORT_ORDER_BUTTON),
                () -> sendButton(StorageTerminalMenu.RESET_SORT_ORDER_BUTTON));
        sortModeBtn = addCycleButton(
                TerminalControlIcon.SORT_NAME,
                Component.translatable("tooltip.auto_storage.sort_mode"),
                railButton(viewStart + 1),
                direction -> sendButton(direction == TerminalCycleDirection.NEXT
                        ? StorageTerminalMenu.NEXT_SORT_MODE_BUTTON
                        : StorageTerminalMenu.PREVIOUS_SORT_MODE_BUTTON),
                () -> sendButton(StorageTerminalMenu.RESET_SORT_MODE_BUTTON));
        autoFocusBtn = addItemButton(
                Items.TARGET.getDefaultInstance(),
                Component.translatable("tooltip.auto_storage.search_auto_focus"),
                railButton(viewStart + 2),
                button -> toggleSearchBoxAutoSelected());
        searchModeBtn = addCycleButton(
                TerminalControlIcon.SEARCH_OFF,
                Component.translatable("tooltip.auto_storage.search_sync"),
                railButton(viewStart + 3),
                direction -> sendButton(direction == TerminalCycleDirection.NEXT
                        ? StorageTerminalMenu.NEXT_SEARCH_MODE_BUTTON
                        : StorageTerminalMenu.PREVIOUS_SEARCH_MODE_BUTTON),
                () -> sendButton(StorageTerminalMenu.RESET_SEARCH_MODE_BUTTON));
        resourceViewBtn = addItemCycleButton(
                Items.CHEST.getDefaultInstance(),
                Component.translatable("tooltip.auto_storage.resource_view"),
                railButton(viewStart + 4),
                direction -> sendButton(direction == TerminalCycleDirection.NEXT
                        ? StorageTerminalMenu.NEXT_RESOURCE_VIEW_BUTTON
                        : StorageTerminalMenu.PREVIOUS_RESOURCE_VIEW_BUTTON),
                () -> sendButton(StorageTerminalMenu.RESET_RESOURCE_VIEW_BUTTON));

        updateViewSettingButtons();
        setItemViewControlsVisible(isItemViewActive());
        repositionSlots();
        sendSettings();
    }

    protected void setItemViewControlsVisible(boolean visible) {
        setSearchControlVisible(visible);
        setViewButtonsVisible(visible);
    }

    protected void setSearchControlVisible(boolean visible) {
        if (searchBox != null) {
            boolean wasActive = searchBox.active;
            searchBox.visible = visible;
            searchBox.active = visible;
            if (!visible) {
                searchBox.setCanLoseFocus(true);
                searchBox.setFocused(false);
                searchTimer = 0;
                setFocused(null);
            } else if (!wasActive) {
                configureSearchBoxFocus(searchBoxAutoSelected);
            }
        }
    }

    protected void setViewButtonsVisible(boolean visible) {
        setSortControlsVisible(visible);
        setWidgetVisible(autoFocusBtn, visible);
        setWidgetVisible(searchModeBtn, visible);
        setWidgetVisible(resourceViewBtn, visible && isResourceViewControlActive());
    }

    protected void setSortControlsVisible(boolean visible) {
        setWidgetVisible(sortOrderBtn, visible);
        setWidgetVisible(sortModeBtn, visible);
    }

    protected boolean isResourceViewControlActive() {
        return isItemViewActive();
    }

    private static void setWidgetVisible(Button button, boolean visible) {
        if (button == null) return;
        button.visible = visible;
        button.active = visible;
    }

    protected void repositionSlots() {
        TerminalLayout.Rect grid = geometry.itemGrid();
        for (int row = 0; row < StorageTerminalMenu.MAX_DISPLAY_ROWS; row++) {
            for (int column = 0; column < StorageTerminalMenu.DISPLAY_COLS; column++) {
                int menuIndex = column + row * StorageTerminalMenu.DISPLAY_COLS;
                replaceSlot(menuIndex,
                        grid.x() + column * TerminalLayout.SLOT_SIZE,
                        grid.y() + row * TerminalLayout.SLOT_SIZE);
            }
        }

        TerminalLayout.Rect playerInventory = geometry.playerInventory();
        int start = StorageTerminalMenu.DISPLAY_SLOTS;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                replaceSlot(start + column + row * 9,
                        playerInventory.x() + column * TerminalLayout.SLOT_SIZE,
                        playerInventory.y() + row * TerminalLayout.SLOT_SIZE);
            }
        }
        for (int column = 0; column < 9; column++) {
            replaceSlot(start + 27 + column,
                    playerInventory.x() + column * TerminalLayout.SLOT_SIZE,
                    playerInventory.y() + 3 * TerminalLayout.SLOT_SIZE + 4);
        }
    }

    protected void replaceSlot(int menuIndex, int x, int y) {
        Slot delegate = semanticSlots.get(menuIndex);
        Slot moved = new Slot(delegate.container, delegate.getContainerSlot(), x, y) {
            @Override
            public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {
                delegate.onQuickCraft(oldStack, newStack);
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                delegate.onTake(player, stack);
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return delegate.mayPlace(stack);
            }

            @Override
            public int getMaxStackSize() {
                return delegate.getMaxStackSize();
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return delegate.getMaxStackSize(stack);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return delegate.getNoItemIcon();
            }

            @Override
            public boolean mayPickup(Player player) {
                return delegate.mayPickup(player);
            }

            @Override
            public boolean isActive() {
                return (menuIndex >= StorageTerminalMenu.DISPLAY_SLOTS
                        || menuIndex < visibleRows * StorageTerminalMenu.DISPLAY_COLS)
                        && delegate.isActive();
            }

            @Override
            public boolean allowModification(Player player) {
                return delegate.allowModification(player);
            }

            @Override
            public boolean isHighlightable() {
                return delegate.isHighlightable();
            }

            @Override
            public boolean isFake() {
                return delegate.isFake();
            }
        };
        moved.index = menuIndex;
        menu.slots.set(menuIndex, moved);
    }

    protected TerminalIconButton addIconButton(
            TerminalControlIcon icon,
            Component narration,
            TerminalLayout.Rect bounds,
            Button.OnPress action
    ) {
        TerminalIconButton button = new TerminalIconButton(
                leftPos + bounds.x(), topPos + bounds.y(), bounds.width(), bounds.height(),
                narration, action, icon, ItemStack.EMPTY);
        addRenderableWidget(button);
        return button;
    }

    protected TerminalIconButton addItemButton(
            ItemStack icon,
            Component narration,
            TerminalLayout.Rect bounds,
            Button.OnPress action
    ) {
        TerminalIconButton button = new TerminalIconButton(
                leftPos + bounds.x(), topPos + bounds.y(), bounds.width(), bounds.height(),
                narration, action, null, icon.copyWithCount(1));
        addRenderableWidget(button);
        return button;
    }

    protected TerminalCycleButton addCycleButton(
            TerminalControlIcon icon,
            Component narration,
            TerminalLayout.Rect bounds,
            Consumer<TerminalCycleDirection> action,
            Runnable resetAction
    ) {
        TerminalCycleButton button = new TerminalCycleButton(
                leftPos + bounds.x(), topPos + bounds.y(), bounds.width(), bounds.height(),
                narration, action, resetAction, icon, ItemStack.EMPTY);
        addRenderableWidget(button);
        return button;
    }

    protected TerminalCycleButton addItemCycleButton(
            ItemStack icon,
            Component narration,
            TerminalLayout.Rect bounds,
            Consumer<TerminalCycleDirection> action,
            Runnable resetAction
    ) {
        TerminalCycleButton button = new TerminalCycleButton(
                leftPos + bounds.x(), topPos + bounds.y(), bounds.width(), bounds.height(),
                narration, action, resetAction, null, icon.copyWithCount(1));
        addRenderableWidget(button);
        return button;
    }

    protected TerminalCycleButton addTextCycleButton(
            Component message,
            TerminalLayout.Rect bounds,
            Consumer<TerminalCycleDirection> action,
            Runnable resetAction
    ) {
        TerminalCycleButton button = new TerminalCycleButton(
                leftPos + bounds.x(), topPos + bounds.y(), bounds.width(), bounds.height(),
                message, action, resetAction, null, ItemStack.EMPTY);
        addRenderableWidget(button);
        return button;
    }

    private Component searchModeLabel() {
        return switch (displayedPreferences().searchMode()) {
            case OFF -> Component.translatable("gui.auto_storage.search_mode.off");
            case EMI -> Component.translatable("gui.auto_storage.search_mode.emi");
            case EMI_TWO_WAY ->
                    Component.translatable("gui.auto_storage.search_mode.emi_two_way");
        };
    }

    private Component sortModeLabel() {
        return switch (displayedPreferences().sortMode()) {
            case NAME -> Component.translatable("gui.auto_storage.sort_mode.name");
            case QUANTITY -> Component.translatable("gui.auto_storage.sort_mode.quantity");
            case MOD -> Component.translatable("gui.auto_storage.sort_mode.mod");
            case ID -> Component.translatable("gui.auto_storage.sort_mode.id");
        };
    }

    private Component sortOrderLabel() {
        return displayedPreferences().sortOrder() == SortOrder.ASCENDING
                ? Component.translatable("gui.auto_storage.sort_order.ascending")
                : Component.translatable("gui.auto_storage.sort_order.descending");
    }

    private Component resourceViewLabel() {
        return Component.translatable("gui.auto_storage.resource_view."
                + displayedPreferences().resourceView().name().toLowerCase(java.util.Locale.ROOT));
    }

    private ItemStack resourceViewIcon() {
        return switch (displayedPreferences().resourceView()) {
            case ITEM -> Items.CHEST.getDefaultInstance();
            case FLUID -> Items.WATER_BUCKET.getDefaultInstance();
            case ENERGY -> Items.REDSTONE.getDefaultInstance();
            case GAS -> TerminalResourceDisplay.create(
                    StorageResourceKinds.kindRepresentative(
                            StorageResourceKindApi.CHEMICAL_KIND),
                    StorageResourceKey.of(
                            StorageResourceKindApi.CHEMICAL_KIND,
                            ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"),
                            new net.minecraft.nbt.CompoundTag()),
                    1);
            case STATION_WORK -> Items.CLOCK.getDefaultInstance();
            case OTHER -> Items.AMETHYST_SHARD.getDefaultInstance();
            case ALL -> AutoStorage.STORAGE_CORE_ITEM.get().getDefaultInstance();
        };
    }

    private void updateViewSettingButtons() {
        if (sortOrderBtn == null || sortModeBtn == null || autoFocusBtn == null
                || searchModeBtn == null
                || resourceViewBtn == null) return;
        TerminalPreferences preferences = displayedPreferences();
        lastSeenSortOrder = preferences.sortOrder();
        lastSeenSortMode = preferences.sortMode();
        lastSeenSearchMode = preferences.searchMode();
        lastSeenResourceView = preferences.resourceView();
        sortOrderBtn.setIcon(preferences.sortOrder() == SortOrder.ASCENDING
                ? TerminalControlIcon.SORT_ASCENDING : TerminalControlIcon.SORT_DESCENDING);
        sortModeBtn.setIcon(switch (preferences.sortMode()) {
            case NAME -> TerminalControlIcon.SORT_NAME;
            case QUANTITY -> TerminalControlIcon.SORT_QUANTITY;
            case MOD -> TerminalControlIcon.SORT_MOD;
            case ID -> TerminalControlIcon.SORT_ID;
        });
        searchModeBtn.setIcon(switch (preferences.searchMode()) {
            case OFF -> TerminalControlIcon.SEARCH_OFF;
            case EMI -> TerminalControlIcon.SEARCH_EMI;
            case EMI_TWO_WAY -> TerminalControlIcon.SEARCH_EMI_TWO_WAY;
        });
        resourceViewBtn.setItemIcon(resourceViewIcon());
        updateCycleTooltip(
                autoFocusBtn,
                "tooltip.auto_storage.search_auto_focus",
                Component.translatable(searchBoxAutoSelected
                        ? "gui.auto_storage.state_on"
                        : "gui.auto_storage.state_off"));
        updateCycleTooltip(sortOrderBtn, "tooltip.auto_storage.sort_order", sortOrderLabel());
        updateCycleTooltip(sortModeBtn, "tooltip.auto_storage.sort_mode", sortModeLabel());
        updateCycleTooltip(searchModeBtn, "tooltip.auto_storage.search_sync", searchModeLabel());
        updateCycleTooltip(resourceViewBtn, "tooltip.auto_storage.resource_view", resourceViewLabel());
    }

    private void toggleSearchBoxAutoSelected() {
        searchBoxAutoSelected = !searchBoxAutoSelected;
        TerminalClientPreferences.saveSearchBoxAutoSelected(searchBoxAutoSelected);
        focusActiveSearchBox(searchBoxAutoSelected);
        updateViewSettingButtons();
    }

    protected final boolean searchBoxAutoSelected() {
        return searchBoxAutoSelected;
    }

    protected final void focusActiveSearchBox(boolean focused) {
        EditBox active = activeSearchBox();
        if (active == null) return;
        active.setCanLoseFocus(true);
        active.setFocused(focused);
        if (focused) {
            setFocused(active);
        } else if (getFocused() == active) {
            setFocused(null);
        }
    }

    private void configureSearchBoxFocus(boolean focused) {
        searchBox.setCanLoseFocus(true);
        searchBox.setFocused(focused);
        if (focused) {
            setFocused(searchBox);
        } else if (getFocused() == searchBox) {
            setFocused(null);
        }
    }

    protected static void updateCycleTooltip(
            Button button,
            String controlKey,
            Component currentValue
    ) {
        Component message = Component.translatable(controlKey).append(": ").append(currentValue);
        button.setMessage(message);
        button.setTooltip(createCycleTooltip(controlKey, currentValue));
    }

    protected static Tooltip createCycleTooltip(String controlKey, Component currentValue) {
        Component message = Component.translatable(controlKey).append(": ").append(currentValue);
        return Tooltip.create(message);
    }

    private void sendSettings() {
        if (minecraft != null && minecraft.player != null && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new TerminalSettingsPacket(
                    menu.containerId, visibleRows, preferenceSession.preferences()));
        }
    }

    public void refreshSearch(String text) {
        searchBox.setValue(text);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanels(graphics, leftPos, topPos);
        if (!isItemViewActive()) return;
        if (searchBoxAutoSelected) drawAutoFocusMarker(graphics);
        int totalItems = menu.getTotalItemTypes();
        int maxOffset = Math.max(0,
                totalItems - visibleRows * StorageTerminalMenu.DISPLAY_COLS);
        int position = scrollbar.synchronize(menu.getScrollOffset(), maxOffset);
        drawScrollbar(
                graphics,
                leftPos + geometry.scrollbar().x() + SCROLL_TRACK_INSET,
                scrollTrackTop(),
                scrollTrackHeight(),
                position,
                maxOffset,
                scrollbar.visualState(maxOffset));
    }

    private int scrollTrackTop() {
        return topPos + geometry.scrollbar().y() + SCROLL_TRACK_INSET;
    }

    private void drawAutoFocusMarker(GuiGraphics graphics) {
        TerminalLayout.Rect button = railButton(terminalProfile().viewControlStartIndex() + 2);
        int x = leftPos + button.x() - 2;
        int y = topPos + button.y() + 3;
        graphics.fill(x, y, x + 2, y + button.height() - 6, 0xFF2E7D32);
    }

    private int scrollTrackHeight() {
        return geometry.scrollbar().height() - 2 * SCROLL_TRACK_INSET;
    }

    protected void drawPanels(GuiGraphics graphics, int x, int y) {
        drawRaisedPanel(graphics, x, y, new TerminalLayout.Rect(0, 0, imageWidth, imageHeight));
        drawRaisedPanel(graphics, x, y, geometry.railPanel());
        drawInsetPanel(graphics, x, y, geometry.itemGrid());
        drawInsetPanel(graphics, x, y, geometry.scrollbar());
        drawInsetPanel(graphics, x, y, geometry.playerInventory());
        drawInsetPanel(graphics, x, y, geometry.searchBackground());
    }

    protected static void drawRaisedPanel(
            GuiGraphics graphics,
            int originX,
            int originY,
            TerminalLayout.Rect rectangle
    ) {
        int left = originX + rectangle.x();
        int top = originY + rectangle.y();
        int right = left + rectangle.width();
        int bottom = top + rectangle.height();
        graphics.fill(left, top, right, bottom, 0xFFC6C6C6);
        graphics.fill(left, top, right, top + 1, 0xFFFFFFFF);
        graphics.fill(left, top, left + 1, bottom, 0xFFFFFFFF);
        graphics.fill(left, bottom - 1, right, bottom, 0xFF555555);
        graphics.fill(right - 1, top, right, bottom, 0xFF555555);
    }

    protected static void drawInsetPanel(
            GuiGraphics graphics,
            int originX,
            int originY,
            TerminalLayout.Rect rectangle
    ) {
        int left = originX + rectangle.x();
        int top = originY + rectangle.y();
        int right = left + rectangle.width();
        int bottom = top + rectangle.height();
        graphics.fill(left, top, right, bottom, 0xFF8B8B8B);
        graphics.fill(left, top, right, top + 1, 0xFF373737);
        graphics.fill(left, top, left + 1, bottom, 0xFF373737);
        graphics.fill(left, bottom - 1, right, bottom, 0xFFFFFFFF);
        graphics.fill(right - 1, top, right, bottom, 0xFFFFFFFF);
    }

    protected static void drawVanillaSlot(GuiGraphics graphics, int x, int y) {
        drawInsetPanel(graphics, x - 1, y - 1, new TerminalLayout.Rect(0, 0, 18, 18));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        graphics.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, 0xFF404040, false);
        drawTypeCapacity(graphics);
    }

    protected void drawTypeCapacity(GuiGraphics graphics) {
        Component text = menu.hasUnlimitedTypeCapacity()
                ? Component.translatable(
                        "gui.auto_storage.type_capacity_unlimited", menu.getTypeCount())
                : Component.translatable(
                        "gui.auto_storage.type_capacity", menu.getTypeCount(), menu.getMaxTypes());
        int textWidth = font.width(text);
        int right = imageWidth - 8;
        int left = inventoryLabelX + font.width(playerInventoryTitle) + 8;
        float scale = Math.min(1.0F, (float) Math.max(1, right - left) / Math.max(1, textWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(right, inventoryLabelY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -textWidth, 0, 0xFF404040, false);
        graphics.pose().popPose();
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index >= StorageTerminalMenu.DISPLAY_SLOTS) {
            super.renderSlot(graphics, slot);
            return;
        }
        if (!isItemViewActive() || slot.index >= visibleRows * StorageTerminalMenu.DISPLAY_COLS) return;
        super.renderSlot(graphics, slot);
    }

    @Override
    protected void renderSlotContents(
            GuiGraphics graphics,
            ItemStack stack,
            Slot slot,
            String countString
    ) {
        if (slot.index >= StorageTerminalMenu.DISPLAY_SLOTS) {
            super.renderSlotContents(graphics, stack, slot, countString);
            return;
        }
        if (stack.isEmpty()) return;

        renderTypedResourceBackground(graphics, stack, slot.x, slot.y);
        ItemStack icon = TerminalDisplayStack.strip(stack).copyWithCount(1);
        if (!renderTerminalIcon(graphics, stack, slot.x, slot.y, 0.0F)) {
            graphics.renderItemDecorations(font, icon, slot.x, slot.y);
        }
        renderNetworkAmount(graphics, slot.x, slot.y, TerminalDisplayStack.amount(stack));
    }

    static void renderTypedResourceBackground(
            GuiGraphics graphics,
            ItemStack stack,
            int x,
            int y
    ) {
        StorageResourceKey key = TerminalResourceDisplay.key(stack).orElse(null);
        if (key == null || key.kindId().equals(StorageResourceKindApi.ITEM_KIND)) return;
        int background;
        int border;
        switch (TerminalResourceView.classify(key)) {
            case FLUID -> {
                background = FLUID_RESOURCE_BACKGROUND;
                border = FLUID_RESOURCE_BORDER;
            }
            case ENERGY -> {
                background = ENERGY_RESOURCE_BACKGROUND;
                border = ENERGY_RESOURCE_BORDER;
            }
            case STATION_WORK -> {
                background = STATION_WORK_BACKGROUND;
                border = STATION_WORK_BORDER;
            }
            default -> {
                background = OTHER_RESOURCE_BACKGROUND;
                border = OTHER_RESOURCE_BORDER;
            }
        }
        graphics.fill(x, y, x + TerminalLayout.ICON_CANVAS_SIZE,
                y + TerminalLayout.ICON_CANVAS_SIZE, background);
        graphics.fill(x, y, x + TerminalLayout.ICON_CANVAS_SIZE, y + 1, border);
        graphics.fill(x, y, x + 1, y + TerminalLayout.ICON_CANVAS_SIZE, border);
        graphics.fill(x, y + TerminalLayout.ICON_CANVAS_SIZE - 1,
                x + TerminalLayout.ICON_CANVAS_SIZE, y + TerminalLayout.ICON_CANVAS_SIZE, border);
        graphics.fill(x + TerminalLayout.ICON_CANVAS_SIZE - 1, y,
                x + TerminalLayout.ICON_CANVAS_SIZE, y + TerminalLayout.ICON_CANVAS_SIZE, border);
    }

    static boolean renderTerminalIcon(
            GuiGraphics graphics,
            ItemStack stack,
            int x,
            int y,
            float partialTick
    ) {
        StorageResourceKey key = TerminalResourceDisplay.key(stack).orElse(null);
        if (key != null && !key.kindId().equals(StorageResourceKindApi.ITEM_KIND)) {
            if (TerminalResourceRendererApi.render(
                    graphics, key, TerminalDisplayStack.amount(stack), x, y, partialTick)) {
                return true;
            }
        }
        graphics.renderItem(TerminalDisplayStack.strip(stack).copyWithCount(1), x, y);
        return false;
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (TerminalDisplayStack.isDisplay(stack)) {
            tooltip.add(Component.translatable(
                    "gui.auto_storage.stored_amount", TerminalDisplayStack.amount(stack)));
        }
        return tooltip;
    }

    protected void renderNetworkAmount(GuiGraphics graphics, int x, int y, long amount) {
        if (amount <= 0) return;
        String text = TerminalAmountFormatter.formatCompact(amount);
        float scaledWidth = font.width(text) * networkAmountScale;
        float scaledHeight = font.lineHeight * networkAmountScale;
        graphics.pose().pushPose();
        graphics.pose().translate(
                x + TerminalLayout.ICON_CANVAS_SIZE - scaledWidth,
                y + TerminalLayout.ICON_CANVAS_SIZE - scaledHeight,
                200.0F);
        graphics.pose().scale(networkAmountScale, networkAmountScale, 1.0F);
        graphics.drawString(font, text, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        if (isItemViewActive() && searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) return false;
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        unfocusSearchOnOutsideClick(mouseX, mouseY);
        TerminalResourceView visibleResourceView = displayedPreferences().resourceView();
        if (isItemViewActive()
                && (button == 0 || button == 1)
                && hoveredSlot != null
                && hoveredSlot.index >= 0
                && hoveredSlot.index < visibleRows * StorageTerminalMenu.DISPLAY_COLS
                && visibleResourceView != TerminalResourceView.ITEM
                && !menu.getCarried().isEmpty()
                && (button == 1 || TerminalResourceDisplay.key(hoveredSlot.getItem())
                .map(key -> !key.kindId().equals(StorageResourceKindApi.ITEM_KIND))
                .orElse(false))) {
            if (minecraft != null && minecraft.getConnection() != null) {
                minecraft.getConnection().send(new TerminalHeldContainerTransferPacket(
                        menu.containerId,
                        menu.getStateId(),
                        hoveredSlot.index,
                        visibleResourceView,
                        button == 0
                                ? TerminalContainerTransferDirection.WITHDRAW
                                : TerminalContainerTransferDirection.DEPOSIT));
            }
            return true;
        }
        if (isItemViewActive() && isOverScrollBar(mouseX, mouseY) && button == 0) {
            lastRequestedScroll = Integer.MIN_VALUE;
            int maxOffset = maxScrollOffset();
            int position = scrollbar.synchronize(menu.getScrollOffset(), maxOffset);
            sendScrollTarget(scrollbar.press(
                    mouseY,
                    scrollTrackTop(),
                    scrollTrackHeight(),
                    position,
                    maxOffset,
                    scrollPageStep()));
            return true;
        }
        if (isItemViewActive() && hoveredSlot != null
                && hoveredSlot.index >= visibleRows * StorageTerminalMenu.DISPLAY_COLS
                && hoveredSlot.index < StorageTerminalMenu.DISPLAY_SLOTS) {
            return false;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && getFocused() instanceof AbstractButton) {
            setFocused(null);
            setDragging(false);
        }
        return handled;
    }

    protected final void unfocusSearchOnOutsideClick(double mouseX, double mouseY) {
        EditBox active = activeSearchBox();
        if (active == null || !active.isFocused() || active.isMouseOver(mouseX, mouseY)) return;
        active.setFocused(false);
        if (getFocused() == active) setFocused(null);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) scrollbar.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isItemViewActive() && scrollbar.isDragging()) {
            sendScrollTarget(scrollbar.drag(
                    mouseY,
                    scrollTrackTop(),
                    scrollTrackHeight(),
                    maxScrollOffset()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isItemViewActive() && (isOverGrid(mouseX, mouseY) || isOverScrollBar(mouseX, mouseY))) {
            double direction = scrollY != 0.0 ? scrollY : scrollX;
            int maxOffset = maxScrollOffset();
            int position = scrollbar.synchronize(menu.getScrollOffset(), maxOffset);
            if (direction != 0.0) sendScrollTarget(scrollbar.step(
                    position,
                    direction > 0
                            ? -StorageTerminalMenu.DISPLAY_COLS
                            : StorageTerminalMenu.DISPLAY_COLS,
                    maxOffset));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        EditBox active = activeSearchBox();
        if (active != null && (active.keyPressed(keyCode, scanCode, modifiers)
                || active.isFocused()
                && keyCode >= GLFW.GLFW_KEY_SPACE
                && keyCode <= GLFW.GLFW_KEY_WORLD_2)) return true;
        return keyPressedOutsideSearch(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        EditBox active = activeSearchBox();
        if (active != null && active.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    protected EditBox activeSearchBox() {
        return isItemViewActive() ? searchBox : null;
    }

    protected boolean keyPressedOutsideSearch(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void scheduleSearch(String ignored) {
        searchSynchronizer.synchronizeFromTerminal(
                displayedPreferences().searchMode(), searchBox.getValue());
        searchTimer = SEARCH_DEBOUNCE_TICKS;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int serverScroll = menu.getScrollOffset();
        if (serverScroll != lastObservedScroll) {
            lastObservedScroll = serverScroll;
            lastRequestedScroll = Integer.MIN_VALUE;
        }
        if (isItemViewActive()) {
            scrollbar.synchronize(serverScroll, maxScrollOffset());
            scrollbar.tick(maxScrollOffset(), scrollPageStep())
                    .ifPresent(this::sendScrollTarget);
        } else {
            scrollbar.release();
        }
        preferenceSession.observe(menu.getTerminalPreferences()).ifPresent(preferences -> {
            TerminalClientPreferences.save(preferences);
        });
        TerminalPreferences preferences = displayedPreferences();
        if (preferences.sortOrder() != lastSeenSortOrder
                || preferences.sortMode() != lastSeenSortMode
                || preferences.searchMode() != lastSeenSearchMode
                || preferences.resourceView() != lastSeenResourceView) {
            boolean searchModeChanged = preferences.searchMode() != lastSeenSearchMode;
            updateViewSettingButtons();
            if (searchModeChanged) {
                searchSynchronizer.synchronizeFromTerminal(
                        preferences.searchMode(), searchBox.getValue());
            }
        }
        synchronizeSearchFromEmi();
        if (searchTimer > 0 && --searchTimer == 0) sendSearchPacket();
    }

    private void synchronizeSearchFromEmi() {
        String text = searchSynchronizer.textToSynchronizeToTerminal(
                displayedPreferences().searchMode());
        if (text == null || text.equals(searchBox.getValue())) return;
        searchBox.setValue(text);
        searchTimer = 0;
        sendSearchPacket();
    }

    private void sendSearchPacket() {
        String text = searchBox.getValue();
        if (text.equals(lastSentSearch)) return;
        lastSentSearch = text;
        if (minecraft != null && minecraft.player != null && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new SearchFilterPacket(menu.containerId, text));
        }
    }

    void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    boolean isOverGrid(double mouseX, double mouseY) {
        TerminalLayout.Rect grid = geometry.itemGrid();
        return isItemViewActive()
                && mouseX >= leftPos + grid.x() && mouseX < leftPos + grid.right()
                && mouseY >= topPos + grid.y() && mouseY < topPos + grid.bottom();
    }

    boolean isOverScrollBar(double mouseX, double mouseY) {
        TerminalLayout.Rect scrollbar = geometry.scrollbar();
        return isItemViewActive()
                && mouseX >= leftPos + scrollbar.x() && mouseX < leftPos + scrollbar.right()
                && mouseY >= topPos + scrollbar.y() && mouseY < topPos + scrollbar.bottom();
    }

    private int maxScrollOffset() {
        return menu.getMaxScrollOffset();
    }

    private int scrollPageStep() {
        return Math.max(1, visibleRows / 6) * StorageTerminalMenu.DISPLAY_COLS;
    }

    private void sendScrollTarget(int target) {
        if (minecraft == null || minecraft.getConnection() == null) return;
        target = Math.clamp(target, 0, maxScrollOffset());
        if (target == lastRequestedScroll) return;
        lastRequestedScroll = target;
        minecraft.getConnection().send(new TerminalScrollPacket(menu.containerId, target));
    }

    protected static void drawScrollbar(
            GuiGraphics graphics,
            int x,
            int trackTop,
            int trackHeight,
            int position,
            int maxPosition,
            TerminalScrollbar.VisualState state
    ) {
        int thumbTop = TerminalScrollbar.thumbTop(
                trackTop, trackHeight, position, maxPosition);
        if (state == TerminalScrollbar.VisualState.CLICKED) {
            graphics.setColor(0.75F, 0.75F, 0.75F, 1.0F);
        }
        graphics.blitSprite(
                state == TerminalScrollbar.VisualState.DISABLED
                        ? SCROLLER_DISABLED_SPRITE : SCROLLER_SPRITE,
                x,
                thumbTop,
                TerminalScrollbar.THUMB_WIDTH,
                TerminalScrollbar.THUMB_HEIGHT);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected static void blitControlIcon(
            GuiGraphics graphics,
            int x,
            int y,
            TerminalControlIcon icon,
            int color
    ) {
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = (color >>> 24) / 255.0F;
        graphics.setColor(red, green, blue, alpha);
        graphics.blit(
                TERMINAL_CONTROLS_TEXTURE,
                x,
                y,
                icon.atlasIndex() * TerminalLayout.ICON_CANVAS_SIZE,
                0,
                TerminalLayout.ICON_CANVAS_SIZE,
                TerminalLayout.ICON_CANVAS_SIZE,
                256,
                TerminalLayout.ICON_CANVAS_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected enum TerminalControlIcon {
        SORT_ASCENDING(0),
        SORT_DESCENDING(1),
        SORT_NAME(2),
        SORT_QUANTITY(3),
        SORT_MOD(4),
        SORT_ID(5),
        SEARCH_OFF(6),
        SEARCH_EMI(7),
        SEARCH_EMI_TWO_WAY(8),
        PREVIOUS(9),
        NEXT(10);

        private final int atlasIndex;

        TerminalControlIcon(int atlasIndex) {
            this.atlasIndex = atlasIndex;
        }

        int atlasIndex() {
            return atlasIndex;
        }
    }

    protected static class TerminalIconButton extends Button {
        private TerminalControlIcon icon;
        private ItemStack itemIcon;

        protected TerminalIconButton(
                int x,
                int y,
                int width,
                int height,
                Component narration,
                OnPress onPress,
                TerminalControlIcon icon,
                ItemStack itemIcon
        ) {
            super(x, y, width, height, narration, onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.itemIcon = itemIcon;
            if (icon != null && !itemIcon.isEmpty()) {
                throw new IllegalArgumentException("Terminal control cannot have two icons");
            }
        }

        protected void setIcon(TerminalControlIcon icon) {
            if (!itemIcon.isEmpty()) {
                throw new IllegalStateException("Cannot replace an item control with an atlas icon");
            }
            this.icon = icon;
        }

        protected void setItemIcon(ItemStack itemIcon) {
            if (icon != null && !itemIcon.isEmpty()) {
                throw new IllegalStateException("Cannot replace an atlas control with an item icon");
            }
            this.itemIcon = itemIcon.isEmpty() ? ItemStack.EMPTY : itemIcon.copyWithCount(1);
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            if (icon == null && itemIcon.isEmpty()) {
                super.renderString(graphics, font, color);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            int iconX = getX() + (getWidth() - TerminalLayout.ICON_CANVAS_SIZE) / 2;
            int iconY = getY() + (getHeight() - TerminalLayout.ICON_CANVAS_SIZE) / 2;
            if (!itemIcon.isEmpty()) {
                renderTerminalIcon(graphics, itemIcon, iconX, iconY, partialTick);
                return;
            }
            if (icon == null) return;
            blitControlIcon(graphics, iconX, iconY, icon,
                    active ? 0xFFFFFFFF : 0xFF777777);
        }
    }

    protected static final class TerminalCycleButton extends TerminalIconButton {
        private final Consumer<TerminalCycleDirection> action;
        private final Runnable resetAction;

        private TerminalCycleButton(
                int x,
                int y,
                int width,
                int height,
                Component narration,
                Consumer<TerminalCycleDirection> action,
                Runnable resetAction,
                TerminalControlIcon icon,
                ItemStack itemIcon
        ) {
            super(x, y, width, height, narration,
                    button -> action.accept(TerminalCycleDirection.NEXT), icon, itemIcon);
            this.action = action;
            this.resetAction = resetAction;
        }

        @Override
        protected boolean isValidClickButton(int button) {
            return button == 0 || button == 1 || button == 2;
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            if (button == 2) {
                resetAction.run();
            } else {
                action.accept(TerminalCycleDirection.fromMouseButton(button));
            }
        }

        @Override
        public boolean mouseScrolled(
                double mouseX,
                double mouseY,
                double scrollX,
                double scrollY
        ) {
            if (!active || !visible || !isMouseOver(mouseX, mouseY) || scrollY == 0.0) return false;
            action.accept(TerminalCycleDirection.fromScroll(scrollY));
            return true;
        }
    }

    protected List<Rect2i> terminalExclusionAreas() {
        List<Rect2i> result = new ArrayList<>(geometry.exclusionRects().size());
        for (TerminalLayout.Rect rectangle : geometry.exclusionRects()) {
            result.add(new Rect2i(
                    leftPos + rectangle.x(),
                    topPos + rectangle.y(),
                    rectangle.width(),
                    rectangle.height()));
        }
        return result;
    }
}
