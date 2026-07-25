package com.swearprom.magicstorage.magic_storage;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class CraftingTerminalScreen extends StorageTerminalScreen<CraftingTerminalMenu> {

    private final NativeRecipeDiagramRenderer nativeRecipeDiagramRenderer;
    private final RecipeDiagramRenderer preferredRecipeDiagramRenderer;
    private Button prevRecipeBtn;
    private Button nextRecipeBtn;
    private Button craft1Btn;
    private Button craft8Btn;
    private Button craft64Btn;
    private Button craftMaxBtn;
    private TerminalIconButton storagePageBtn;
    private TerminalIconButton craftablePageBtn;
    private TerminalIconButton transformPageBtn;
    private TerminalIconButton stationsPageBtn;
    private TerminalCycleButton playerInventoryRailBtn;
    private TerminalCycleButton outputDestinationRailBtn;
    private TerminalCycleButton fuelTargetSelector;
    private TerminalIconButton fuelTargetListBtn;
    private TerminalIconButton fuelSearchBtn;
    private EditBox fuelSearchBox;
    private FuelPageButtons consumablesPageButtons;
    private FuelPageButtons timedStationsPageButtons;
    private FuelPageButtons instantStationsPageButtons;
    private FuelPageButtons fuelSearchPageButtons;
    private FuelTargetPopup fuelTargetPopup;
    private CraftingTerminalPage lastPage;
    private ResourceLocation lastTransformTarget;
    private int consumablePage;
    private int timedStationPage;
    private int instantStationPage;
    private int fuelSearchPage;
    private boolean fuelSearchActive;
    private FuelSearchModel.Index fuelSearchIndex;
    private List<FuelSearchModel.Entry> filteredFuelEntries = List.of();
    private RecipeDiagramRenderer.Geometry recipeDiagramGeometry;
    private int lastRecipeMouseX;
    private int lastRecipeMouseY;
    private ResourceLocation stationCycleRecipeId;
    private ItemStack stationCycleInstalled = ItemStack.EMPTY;
    private long stationCycleAnchorMillis;

    public CraftingTerminalScreen(CraftingTerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        nativeRecipeDiagramRenderer = new NativeRecipeDiagramRenderer();
        preferredRecipeDiagramRenderer = ClientSetup.createRecipeDiagramRenderer();
    }

    @Override
    protected TerminalProfile terminalProfile() {
        return TerminalProfile.CRAFTING;
    }

    @Override
    protected TerminalLayout.FuelDescriptorCounts fuelDescriptorCounts() {
        int timedStations = machineSlotsForCategory(MachineEnergyTable.Category.PROCESS).size();
        int instantStations = machineSlotsForCategory(MachineEnergyTable.Category.INSTANT).size();
        int consumables = 1 + CraftingTerminalMenu.fuelTargets().size()
                + machineSlotsForCategory(MachineEnergyTable.Category.TRANSFORM).size();
        return new TerminalLayout.FuelDescriptorCounts(
                consumables,
                timedStations,
                instantStations,
                fuelTargetOptions().size());
    }

    @Override
    protected void addTerminalProfileControls() {
        storagePageBtn = addItemButton(
                MagicStorage.STORAGE_TERMINAL_ITEM.get().getDefaultInstance(),
                Component.translatable("gui.magic_storage.page_storage"),
                geometry.railButtons().get(0),
                button -> clickMenuButton(CraftingTerminalMenu.STORAGE_PAGE_BUTTON));
        craftablePageBtn = addItemButton(
                Items.CRAFTING_TABLE.getDefaultInstance(),
                Component.translatable("gui.magic_storage.page_craftable"),
                geometry.railButtons().get(1),
                button -> clickMenuButton(CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON));
        transformPageBtn = addItemButton(
                Items.COAL.getDefaultInstance(),
                Component.translatable("gui.magic_storage.page_transform"),
                geometry.railButtons().get(2),
                button -> clickMenuButton(CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON));
        stationsPageBtn = addItemButton(
                Items.FURNACE.getDefaultInstance(),
                Component.translatable("gui.magic_storage.page_stations"),
                geometry.railButtons().get(3),
                button -> clickMenuButton(CraftingTerminalMenu.STATIONS_PAGE_BUTTON));
        playerInventoryRailBtn = addItemCycleButton(
                Items.BUNDLE.getDefaultInstance(),
                Component.translatable("gui.magic_storage.use_player_inv"),
                geometry.railButtons().get(terminalProfile().playerInventorySourceIndex()),
                direction -> clickMenuButton(7),
                () -> clickMenuButton(CraftingTerminalMenu.RESET_PLAYER_INVENTORY_BUTTON));
        outputDestinationRailBtn = addItemCycleButton(
                Items.PLAYER_HEAD.getDefaultInstance(),
                Component.translatable("gui.magic_storage.output_destination"),
                geometry.railButtons().get(terminalProfile().outputDestinationIndex()),
                direction -> clickMenuButton(CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON),
                () -> clickMenuButton(CraftingTerminalMenu.RESET_OUTPUT_DESTINATION_BUTTON));
    }

    @Override
    protected boolean isItemViewActive() {
        return displayedPreferences().page().isItemPage();
    }

    @Override
    protected boolean isResourceViewControlActive() {
        return displayedPreferences().page().isItemPage();
    }

    @Override
    protected void init() {
        String previousFuelSearch = fuelSearchBox == null ? "" : fuelSearchBox.getValue();
        boolean previousFuelSearchFocused = fuelSearchBox != null && fuelSearchBox.isFocused();
        super.init();
        recipeDiagramGeometry = createRecipeDiagramGeometry();
        List<TerminalLayout.Rect> navigationButtons = geometry.recipeNavigationButtons();
        prevRecipeBtn = addRecipeNavigationButton(
                TerminalControlIcon.PREVIOUS,
                Component.translatable("gui.magic_storage.previous_recipe"),
                navigationButtons.get(0),
                button -> clickMenuButton(8));
        nextRecipeBtn = addRecipeNavigationButton(
                TerminalControlIcon.NEXT,
                Component.translatable("gui.magic_storage.next_recipe"),
                navigationButtons.get(1),
                button -> clickMenuButton(9));

        List<TerminalLayout.Rect> craftButtons = geometry.recipeCraftButtons();
        craft1Btn = addRecipeAmountButton(
                Component.translatable("gui.magic_storage.craft_amount", 1), craftButtons.get(0),
                button -> clickMenuButton(2), RecipeAmountSegment.FIRST);
        craft8Btn = addRecipeAmountButton(
                Component.translatable("gui.magic_storage.craft_amount", 8), craftButtons.get(1),
                button -> clickMenuButton(3), RecipeAmountSegment.MIDDLE);
        craft64Btn = addRecipeAmountButton(
                Component.translatable("gui.magic_storage.craft_amount", 64), craftButtons.get(2),
                button -> clickMenuButton(4), RecipeAmountSegment.MIDDLE);
        craftMaxBtn = addRecipeAmountButton(
                Component.translatable("gui.magic_storage.craft_max"), craftButtons.get(3),
                button -> clickMenuButton(CraftingTerminalMenu.MAX_CRAFT_BUTTON),
                RecipeAmountSegment.LAST);

        TerminalLayout.Rect targetBounds = geometry.fuelTargetSelector();
        FuelTargetOption initialTarget = fuelTargetOption(
                displayedPreferences().transformTarget());
        fuelTargetSelector = addTextCycleButton(
                initialTarget.label(), targetBounds, this::selectAdjacentFuelTarget,
                () -> clickMenuButton(CraftingTerminalMenu.AUTO_FUEL_TARGET_BUTTON));
        fuelTargetListBtn = addItemButton(
                Items.BOOK.getDefaultInstance(),
                Component.translatable("gui.magic_storage.fuel_target_list"),
                geometry.fuelTargetListButton(),
                button -> toggleFuelTargetPopup());
        TerminalLayout.PopupList popup = geometry.fuelTargetPopup();
        fuelTargetPopup = new FuelTargetPopup(
                leftPos + popup.bounds().x(), topPos + popup.bounds().y(), popup);
        fuelTargetPopup.visible = false;
        fuelTargetPopup.active = false;
        addWidget(fuelTargetPopup);
        TerminalLayout.Rect fuelSearch = geometry.fuelSearchBox();
        fuelSearchBox = new EditBox(
                font,
                leftPos + fuelSearch.x(),
                topPos + fuelSearch.y(),
                fuelSearch.width(),
                font.lineHeight,
                Component.translatable("gui.magic_storage.fuel_search"));
        fuelSearchBox.setBordered(false);
        fuelSearchBox.setTextColor(0x404040);
        fuelSearchBox.setMaxLength(50);
        fuelSearchBox.setTooltip(Tooltip.create(
                Component.translatable("tooltip.magic_storage.search_help")));
        fuelSearchBox.setValue(previousFuelSearch);
        fuelSearchBox.setResponder(text -> refreshFuelSearchResults());
        addRenderableWidget(fuelSearchBox);
        fuelSearchBtn = addItemButton(
                Items.COMPASS.getDefaultInstance(),
                Component.translatable("gui.magic_storage.fuel_search"),
                geometry.fuelSearchButton(),
                button -> setFuelSearchActive(!fuelSearchActive));
        consumablesPageButtons = addFuelPageControls(
                geometry.consumablesPageControls(),
                () -> consumablePage,
                page -> consumablePage = page,
                this::transformPageCount);
        timedStationsPageButtons = addFuelPageControls(
                geometry.timedStationsPageControls(),
                () -> timedStationPage,
                page -> timedStationPage = page,
                () -> geometry.timedStationsGrid().pageCount());
        instantStationsPageButtons = addFuelPageControls(
                geometry.instantStationsPageControls(),
                () -> instantStationPage,
                page -> instantStationPage = page,
                () -> geometry.instantStationsGrid().pageCount());
        fuelSearchPageButtons = addFuelPageControls(
                geometry.fuelSearchPageControls(),
                () -> fuelSearchPage,
                page -> fuelSearchPage = page,
                this::fuelSearchPageCount);

        fuelSearchIndex = FuelSearchModel.index(
                CraftingTerminalMenu.fuelTargets(),
                menu.getMachineDescriptors());
        refreshFuelSearchResults();
        if (fuelSearchActive && previousFuelSearchFocused) {
            fuelSearchBox.setFocused(true);
            setFocused(fuelSearchBox);
        }
        updatePageWidgets();
        updateSidebarState();
    }

    private RecipeDiagramRenderer.Geometry createRecipeDiagramGeometry() {
        return new RecipeDiagramRenderer.Geometry(
                recipeRect(geometry.recipeDiagram()),
                geometry.recipeInputSlots().stream().map(CraftingTerminalScreen::recipeRect).toList(),
                recipeRect(geometry.recipeArrow()),
                recipeRect(geometry.recipeOutput()),
                recipeRect(geometry.recipeStation()),
                recipeRect(geometry.recipeShapelessMarker()));
    }

    private static RecipeDiagramRenderer.Rect recipeRect(TerminalLayout.Rect bounds) {
        return new RecipeDiagramRenderer.Rect(
                bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    private RecipeDiagramRenderer activeRecipeDiagramRenderer(RecipePresentation presentation) {
        return preferredRecipeDiagramRenderer.supports(presentation, recipeDiagramGeometry)
                ? preferredRecipeDiagramRenderer : nativeRecipeDiagramRenderer;
    }

    private Button addRecipeAmountButton(
            Component message,
            TerminalLayout.Rect bounds,
            Button.OnPress action,
            RecipeAmountSegment segment
    ) {
        Button button = new RecipeAmountButton(
                leftPos + bounds.x(), topPos + bounds.y(), bounds.width(), bounds.height(),
                message, action, segment);
        addRenderableWidget(button);
        return button;
    }

    private enum RecipeAmountSegment {
        FIRST,
        MIDDLE,
        LAST
    }

    private final class RecipeAmountButton extends Button {
        private final RecipeAmountSegment segment;

        private RecipeAmountButton(
                int x,
                int y,
                int width,
                int height,
                Component message,
                OnPress action,
                RecipeAmountSegment segment
        ) {
            super(x, y, width, height, message, action, DEFAULT_NARRATION);
            this.segment = segment;
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }

    private Button addRecipeNavigationButton(
            TerminalControlIcon icon,
            Component narration,
            TerminalLayout.Rect bounds,
            Button.OnPress action
    ) {
        return addIconButton(icon, narration, bounds, action);
    }

    private FuelPageButtons addFuelPageControls(
            TerminalLayout.FuelPageControls controls,
            IntSupplier currentPage,
            IntConsumer setPage,
            IntSupplier pageCount
    ) {
        Component previousLabel = Component.translatable(
                "gui.magic_storage.previous_fuel_page");
        Component nextLabel = Component.translatable(
                "gui.magic_storage.next_fuel_page");
        TerminalIconButton previous = addIconButton(
                TerminalControlIcon.PREVIOUS,
                previousLabel,
                controls.previous(),
                button -> {
                    setPage.accept(Math.max(0, currentPage.getAsInt() - 1));
                    repositionFuelSlots();
                    updateFuelPageButtonStates();
                });
        TerminalIconButton next = addIconButton(
                TerminalControlIcon.NEXT,
                nextLabel,
                controls.next(),
                button -> {
                    setPage.accept(Math.min(
                            pageCount.getAsInt() - 1, currentPage.getAsInt() + 1));
                    repositionFuelSlots();
                    updateFuelPageButtonStates();
                });
        previous.setTooltip(Tooltip.create(previousLabel));
        next.setTooltip(Tooltip.create(nextLabel));
        return new FuelPageButtons(previous, next);
    }

    private int fuelSearchPageCount() {
        return geometry.fuelSearchGrid().pageCount(filteredFuelEntries.size());
    }

    private int transformPageCount() {
        return geometry.consumablesGrid().pageCount(
                1 + menu.getTransformUses().size());
    }

    private void refreshFuelSearchResults() {
        String query = fuelSearchBox == null ? "" : fuelSearchBox.getValue();
        filteredFuelEntries = FuelSearchModel.search(query, fuelSearchIndex).stream()
                .filter(entry -> !entry.isEnergy()
                        && entry.category() != MachineEnergyTable.Category.TRANSFORM)
                .toList();
        fuelSearchPage = Math.clamp(
                fuelSearchPage, 0, fuelSearchPageCount() - 1);
        repositionFuelSlots();
        updateFuelPageButtonStates();
    }

    private void setFuelSearchActive(boolean active) {
        fuelSearchActive = active;
        fuelSearchPage = 0;
        closeFuelTargetPopup();
        refreshFuelSearchResults();
        updatePageWidgets();
        if (active) {
            fuelSearchBox.setFocused(true);
            setFocused(fuelSearchBox);
        }
    }

    private void repositionFuelSlots() {
        consumablePage = Math.clamp(
                consumablePage, 0, transformPageCount() - 1);
        timedStationPage = Math.clamp(
                timedStationPage, 0, geometry.timedStationsGrid().pageCount() - 1);
        instantStationPage = Math.clamp(
                instantStationPage, 0, geometry.instantStationsGrid().pageCount() - 1);

        replaceSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT, -9999, -9999);
        for (int machineSlot = 0; machineSlot < CraftingTerminalMenu.MACHINE_SLOT_COUNT; machineSlot++) {
            replaceSlot(CraftingTerminalMenu.MACHINE_SLOT_START + machineSlot, -9999, -9999);
        }

        CraftingTerminalPage page = displayedPreferences().page();
        if (page == CraftingTerminalPage.TRANSFORM) {
            TerminalLayout.Rect fuelInput = geometry.fuelInput();
            replaceSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT, fuelInput.x(), fuelInput.y());
            return;
        }
        if (page != CraftingTerminalPage.STATIONS) return;

        if (fuelSearchActive) {
            List<TerminalLayout.Rect> cells = geometry.fuelSearchGrid().cells(
                    fuelSearchPage, filteredFuelEntries.size());
            int first = fuelSearchPage * geometry.fuelSearchGrid().capacity();
            for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
                FuelSearchModel.Entry result = filteredFuelEntries.get(first + visibleIndex);
                if (result.isEnergy()) continue;
                TerminalLayout.Rect slot = TerminalLayout.fuelSlot(cells.get(visibleIndex));
                replaceSlot(
                        CraftingTerminalMenu.MACHINE_SLOT_START + result.machineSlot(),
                        slot.x(),
                        slot.y());
            }
            return;
        }

        positionMachineCategory(
                MachineEnergyTable.Category.PROCESS,
                geometry.timedStationsGrid(),
                timedStationPage,
                0);
        positionMachineCategory(
                MachineEnergyTable.Category.INSTANT,
                geometry.instantStationsGrid(),
                instantStationPage,
                0);
    }

    private void positionMachineCategory(
            MachineEnergyTable.Category category,
            TerminalLayout.FlowGrid grid,
            int page,
            int descriptorOffset
    ) {
        List<Integer> machineSlots = machineSlotsForCategory(category);
        List<TerminalLayout.Rect> cells = grid.cells(page);
        int firstDescriptor = page * grid.capacity();
        for (int categoryIndex = 0; categoryIndex < machineSlots.size(); categoryIndex++) {
            int visibleIndex = descriptorOffset + categoryIndex - firstDescriptor;
            if (visibleIndex < 0 || visibleIndex >= cells.size()) continue;
            TerminalLayout.Rect slot = TerminalLayout.fuelSlot(cells.get(visibleIndex));
            replaceSlot(
                    CraftingTerminalMenu.MACHINE_SLOT_START + machineSlots.get(categoryIndex),
                    slot.x(),
                    slot.y());
        }
    }

    private void clickMenuButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void selectAdjacentFuelTarget(TerminalCycleDirection direction) {
        List<FuelTargetOption> options = fuelTargetOptions();
        int current = indexOfFuelTarget(
                options, displayedPreferences().transformTarget());
        if (current < 0) throw new IllegalStateException("Current Fuel target is not server-approved");
        int offset = direction == TerminalCycleDirection.NEXT ? 1 : -1;
        FuelTargetOption option = options.get(Math.floorMod(current + offset, options.size()));
        clickMenuButton(option.target() == null
                ? CraftingTerminalMenu.AUTO_FUEL_TARGET_BUTTON
                : TransformProviderApi.targetButtonId(
                        option.target(), menu.getMachineDescriptors()));
    }

    private void toggleFuelTargetPopup() {
        if (fuelTargetPopup.visible) {
            closeFuelTargetPopup();
            return;
        }
        int selected = indexOfFuelTarget(
                fuelTargetOptions(), displayedPreferences().transformTarget());
        if (selected < 0) throw new IllegalStateException("Current Fuel target is not server-approved");
        fuelTargetPopup.reveal(selected);
        fuelTargetPopup.visible = true;
        fuelTargetPopup.active = true;
        setFocused(null);
    }

    private void closeFuelTargetPopup() {
        if (fuelTargetPopup == null) return;
        fuelTargetPopup.visible = false;
        fuelTargetPopup.active = false;
        setFocused(null);
    }

    private void selectFuelTarget(FuelTargetOption option) {
        clickMenuButton(option.target() == null
                ? CraftingTerminalMenu.AUTO_FUEL_TARGET_BUTTON
                : TransformProviderApi.targetButtonId(
                        option.target(), menu.getMachineDescriptors()));
        closeFuelTargetPopup();
    }

    private void updatePageWidgets() {
        TerminalPreferences preferences = displayedPreferences();
        CraftingTerminalPage page = preferences.page();
        ResourceLocation target = preferences.transformTarget();
        boolean itemPage = page.isItemPage();
        setItemViewControlsVisible(itemPage);

        setWidgetVisible(prevRecipeBtn, itemPage);
        setWidgetVisible(nextRecipeBtn, itemPage);
        boolean amountActions = itemPage || page == CraftingTerminalPage.TRANSFORM;
        setWidgetVisible(craft1Btn, amountActions);
        setWidgetVisible(craft8Btn, amountActions);
        setWidgetVisible(craft64Btn, amountActions);
        setWidgetVisible(craftMaxBtn, amountActions);
        setWidgetVisible(playerInventoryRailBtn, itemPage);
        setWidgetVisible(outputDestinationRailBtn, itemPage);

        storagePageBtn.active = page != CraftingTerminalPage.STORAGE;
        craftablePageBtn.active = page != CraftingTerminalPage.CRAFTABLE;
        transformPageBtn.active = page != CraftingTerminalPage.TRANSFORM;
        stationsPageBtn.active = page != CraftingTerminalPage.STATIONS;

        boolean transform = page == CraftingTerminalPage.TRANSFORM;
        boolean stations = page == CraftingTerminalPage.STATIONS;
        setWidgetVisible(fuelTargetSelector, transform);
        setWidgetVisible(fuelTargetListBtn, transform);
        setWidgetVisible(fuelSearchBtn, stations);
        setWidgetVisible(fuelSearchBox, stations && fuelSearchActive);
        if (!transform) closeFuelTargetPopup();
        if (transform) {
            fuelTargetSelector.active = fuelTargetOptions().size() > 1;
            FuelTargetOption option = fuelTargetOption(target);
            fuelTargetSelector.setMessage(option.label());
            fuelTargetSelector.setTooltip(createCycleTooltip(
                    "gui.magic_storage.fuel_target", option.label()));
        }
        updateFuelPageButtonStates();
        updateCraftButtonState();
        lastPage = page;
        lastTransformTarget = target;
    }

    private void updateSidebarState() {
        TerminalPreferences preferences = displayedPreferences();
        storagePageBtn.setTooltip(Tooltip.create(Component.translatable("gui.magic_storage.page_storage")));
        craftablePageBtn.setTooltip(Tooltip.create(Component.translatable("gui.magic_storage.page_craftable")));
        transformPageBtn.setTooltip(Tooltip.create(Component.translatable("gui.magic_storage.page_transform")));
        stationsPageBtn.setTooltip(Tooltip.create(Component.translatable("gui.magic_storage.page_stations")));
        fuelTargetListBtn.setTooltip(Tooltip.create(Component.translatable("gui.magic_storage.fuel_target_list")));
        fuelSearchBtn.setTooltip(Tooltip.create(Component.translatable(
                "gui.magic_storage.fuel_search")));

        updateToggleButton(playerInventoryRailBtn, "gui.magic_storage.use_player_inv",
                preferences.usePlayerInventory());
        boolean storageOnly = menu.isSelectedOutputStorageOnly();
        TerminalOutputDestination effectiveDestination = storageOnly
                ? TerminalOutputDestination.STORAGE
                : preferences.outputDestination();
        Component outputDestination = switch (effectiveDestination) {
            case PLAYER -> Component.translatable("gui.magic_storage.output_destination.player");
            case STORAGE -> Component.translatable("gui.magic_storage.output_destination.storage");
        };
        outputDestinationRailBtn.setItemIcon(switch (effectiveDestination) {
            case PLAYER -> Items.PLAYER_HEAD.getDefaultInstance();
            case STORAGE -> MagicStorage.STORAGE_CORE_ITEM.get().getDefaultInstance();
        });
        outputDestinationRailBtn.active = outputDestinationRailBtn.visible && !storageOnly;
        updateCycleTooltip(outputDestinationRailBtn, "gui.magic_storage.output_destination",
                outputDestination);
    }

    private void updateCraftButtonState() {
        if (craft1Btn == null) return;
        if (displayedPreferences().page() == CraftingTerminalPage.TRANSFORM) {
            int transformable = menu.getCraftableCount();
            craft1Btn.active = transformable >= 1;
            craft8Btn.active = transformable >= 8;
            craft64Btn.active = transformable >= 64;
            craftMaxBtn.active = transformable >= 1;
            return;
        }
        int craftable = menu.getCraftableCount();
        craft1Btn.active = craftable >= 1;
        craft8Btn.active = craftable >= 8;
        craft64Btn.active = craftable >= 64;
        craftMaxBtn.active = craftable >= 1;
        prevRecipeBtn.active = menu.getRecipeCount() > 1;
        nextRecipeBtn.active = menu.getRecipeCount() > 1;
    }

    private void updateFuelPageButtonStates() {
        boolean stations = displayedPreferences().page() == CraftingTerminalPage.STATIONS;
        updateFuelPageButtons(
                consumablesPageButtons,
                consumablePage,
                transformPageCount(),
                displayedPreferences().page() == CraftingTerminalPage.TRANSFORM);
        updateFuelPageButtons(
                timedStationsPageButtons,
                timedStationPage,
                geometry.timedStationsGrid().pageCount(),
                stations && !fuelSearchActive);
        updateFuelPageButtons(
                instantStationsPageButtons,
                instantStationPage,
                geometry.instantStationsGrid().pageCount(),
                stations && !fuelSearchActive);
        updateFuelPageButtons(
                fuelSearchPageButtons,
                fuelSearchPage,
                fuelSearchPageCount(),
                stations && fuelSearchActive);
    }

    private void updateFuelPageButtons(
            FuelPageButtons buttons,
            int page,
            int pageCount,
            boolean fuel
    ) {
        if (buttons == null) return;
        boolean visible = fuel;
        setWidgetVisible(buttons.previous(), visible);
        setWidgetVisible(buttons.next(), visible);
        buttons.previous().active = visible && pageCount > 1 && page > 0;
        buttons.next().active = visible && pageCount > 1 && page + 1 < pageCount;
    }

    private void updateToggleButton(TerminalCycleButton button, String key, boolean enabled) {
        updateCycleTooltip(button, key, Component.translatable(enabled
                ? "gui.magic_storage.state_on"
                : "gui.magic_storage.state_off"));
    }

    private void setWidgetVisible(AbstractWidget widget, boolean visible) {
        if (!visible && getFocused() == widget) setFocused(null);
        widget.visible = visible;
        widget.active = visible;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        TerminalPreferences preferences = displayedPreferences();
        if (lastPage != preferences.page()
                || !Objects.equals(
                lastTransformTarget, preferences.transformTarget())) {
            updatePageWidgets();
        }
        updateCraftButtonState();
        updateSidebarState();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        CraftingTerminalPage page = displayedPreferences().page();
        if (page == CraftingTerminalPage.TRANSFORM
                || page == CraftingTerminalPage.STATIONS) {
            renderUtilityPage(graphics, page);
        } else {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            renderRecipePanel(graphics, partialTick, mouseX, mouseY);
        }
        renderSideRail(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (fuelTargetPopup == null || !fuelTargetPopup.visible) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0.0F, 0.0F, 400.0F);
            fuelTargetPopup.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderSideRail(GuiGraphics graphics) {
        TerminalPreferences preferences = displayedPreferences();
        boolean itemPage = preferences.page().isItemPage();
        if (!itemPage) {
            drawRaisedPanel(graphics, leftPos, topPos, geometry.fuelRailPanel());
        }
        if (itemPage && preferences.usePlayerInventory()) {
            drawRailMarker(graphics, terminalProfile().playerInventorySourceIndex(), false, 0xFF2E7D32);
        }
    }

    private void drawRailMarker(GuiGraphics graphics, int railIndex, boolean fuelLayout, int color) {
        List<TerminalLayout.Rect> buttons = fuelLayout
                ? geometry.fuelRailButtons() : geometry.railButtons();
        if (railIndex < 0 || railIndex >= buttons.size()) return;
        TerminalLayout.Rect button = buttons.get(railIndex);
        int x = leftPos + button.x() - 2;
        int y = topPos + button.y() + 3;
        graphics.fill(x, y, x + 2, y + button.height() - 6, color);
    }

    private void renderUtilityPage(
            GuiGraphics graphics,
            CraftingTerminalPage page
    ) {
        drawRaisedPanel(graphics, leftPos, topPos, geometry.frame());
        drawInsetPanel(graphics, leftPos, topPos, geometry.playerInventory());
        if (page == CraftingTerminalPage.TRANSFORM) {
            renderConsumablesPanel(graphics);
        } else if (fuelSearchActive) {
            renderFuelSearchResults(graphics);
        } else {
            renderTimedStationsPanel(graphics);
            renderInstantStationsPanel(graphics);
        }
        renderFuelTypeCapacity(graphics);
        if (page == CraftingTerminalPage.STATIONS && fuelSearchActive) {
            TerminalLayout.Rect search = geometry.fuelSearchBox();
            drawInsetPanel(
                    graphics,
                    leftPos,
                    topPos,
                    new TerminalLayout.Rect(
                            search.x() - 2,
                            search.y() - 2,
                            search.width() + 4,
                            search.height() + 4));
        }
    }

    private void renderFuelSearchResults(GuiGraphics graphics) {
        TerminalLayout.Rect panel = geometry.fuelSearchPanel();
        TerminalLayout.FlowGrid grid = geometry.fuelSearchGrid();
        drawInsetPanel(graphics, leftPos, topPos, panel);
        Component heading = Component.translatable("gui.magic_storage.fuel_search_results");
        int controlsLeft = geometry.fuelSearchPageControls().previous().x();
        int headingWidth = Math.max(1, controlsLeft - panel.x() - 32);
        String visibleHeading = font.plainSubstrByWidth(heading.getString(), headingWidth);
        int headerY = panel.y() + (TerminalLayout.CONTROL_SIZE - font.lineHeight) / 2 + 2;
        graphics.drawString(
                font,
                visibleHeading,
                leftPos + panel.x() + 4,
                topPos + headerY,
                0xFF404040,
                false);
        if (fuelSearchPageCount() > 1) {
            String page = (fuelSearchPage + 1) + "/" + fuelSearchPageCount();
            graphics.drawString(
                    font,
                    page,
                    leftPos + controlsLeft - font.width(page) - 3,
                    topPos + headerY,
                    0xFF404040,
                    false);
        }
        if (filteredFuelEntries.isEmpty()) {
            Component empty = Component.translatable("gui.magic_storage.fuel_search_empty");
            graphics.drawString(
                    font,
                    empty,
                    leftPos + panel.x() + (panel.width() - font.width(empty)) / 2,
                    topPos + panel.y() + (panel.height() - font.lineHeight) / 2,
                    0xFF606060,
                    false);
            return;
        }

        int first = fuelSearchPage * grid.capacity();
        List<TerminalLayout.Rect> cells = grid.cells(
                fuelSearchPage, filteredFuelEntries.size());
        for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
            FuelSearchModel.Entry result = filteredFuelEntries.get(first + visibleIndex);
            TerminalLayout.Rect cell = cells.get(visibleIndex);
            MachineDescriptor descriptor = descriptorAt(result.machineSlot());
            if (descriptor == null) continue;
            TerminalLayout.Rect slot = TerminalLayout.fuelSlot(cell);
            drawSlotFrame(graphics, leftPos + slot.x(), topPos + slot.y());
            ItemStack installed = menu.getSlot(
                    CraftingTerminalMenu.MACHINE_SLOT_START
                            + result.machineSlot()).getItem();
            if (installed.isEmpty()) {
                renderDimmedItem(graphics, descriptor.representativeStack(), slot);
            }
            drawFlowAmount(graphics, cell, formatAmount(installed.getCount()));
        }
    }

    private void renderConsumablesPanel(GuiGraphics graphics) {
        TerminalLayout.Rect panel = geometry.consumablesPanel();
        TerminalLayout.FlowGrid grid = geometry.consumablesGrid();
        renderFuelCategoryHeading(
                graphics,
                panel,
                grid,
                geometry.consumablesPageControls(),
                Component.translatable("gui.magic_storage.page_transform"),
                consumablePage,
                transformPageCount());
        renderFuelTargetBar(graphics, panel);

        List<TransformProviderApi.Use> uses = menu.getTransformUses();
        int entryCount = 1 + uses.size();
        int first = consumablePage * grid.capacity();
        List<TerminalLayout.Rect> cells = grid.cells(consumablePage, entryCount);
        for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
            int descriptorIndex = first + visibleIndex;
            TerminalLayout.Rect cell = cells.get(visibleIndex);
            if (descriptorIndex == 0) {
                TerminalLayout.Rect slot = TerminalLayout.fuelSlot(cell);
                drawSlotFrame(graphics, leftPos + slot.x(), topPos + slot.y());
                continue;
            }
            TransformProviderApi.Use use = uses.get(descriptorIndex - 1);
            TerminalLayout.Rect icon = TerminalLayout.fuelIcon(cell);
            graphics.renderItem(
                    use.representative(), leftPos + icon.x(), topPos + icon.y());
            drawFlowAmount(
                    graphics,
                    cell,
                    use.infinite() ? "∞" : formatAmount(use.amountPerItem()));
        }
        if (!menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().isEmpty()
                && uses.isEmpty()) {
            Component empty = Component.translatable(
                    "gui.magic_storage.no_transformations");
            graphics.drawString(
                    font,
                    empty,
                    leftPos + panel.x() + (panel.width() - font.width(empty)) / 2,
                    topPos + panel.y() + panel.height() / 2,
                    0xFF606060,
                    false);
        }
    }

    private void renderFuelTargetBar(GuiGraphics graphics, TerminalLayout.Rect panel) {
        TerminalLayout.Rect bar = TerminalLayout.fuelTargetBar(panel);
        TerminalLayout.Rect heading = TerminalLayout.fuelCategoryLabel(
                panel, geometry.consumablesGrid());
        if (bar.equals(heading)) return;
        drawRaisedPanel(graphics, leftPos, topPos, bar);
        Component label = Component.translatable("gui.magic_storage.fuel_target");
        int availableWidth = Math.max(0, geometry.fuelTargetSelector().x() - bar.x() - 6);
        String visible = font.plainSubstrByWidth(label.getString(), availableWidth);
        graphics.drawString(
                font,
                visible,
                leftPos + bar.x() + 4,
                topPos + bar.y() + (bar.height() - font.lineHeight) / 2,
                0xFF404040,
                false);
    }

    private void renderTimedStationsPanel(GuiGraphics graphics) {
        TerminalLayout.Rect panel = geometry.timedStationsPanel();
        TerminalLayout.FlowGrid grid = geometry.timedStationsGrid();
        renderFuelCategoryHeading(
                graphics,
                panel,
                grid,
                geometry.timedStationsPageControls(),
                Component.translatable("gui.magic_storage.fuel_group.timed_stations"),
                timedStationPage,
                grid.pageCount());
        renderMachineCategoryCells(
                graphics,
                MachineEnergyTable.Category.PROCESS,
                grid,
                timedStationPage);
    }

    private void renderInstantStationsPanel(GuiGraphics graphics) {
        TerminalLayout.Rect panel = geometry.instantStationsPanel();
        TerminalLayout.FlowGrid grid = geometry.instantStationsGrid();
        renderFuelCategoryHeading(
                graphics,
                panel,
                grid,
                geometry.instantStationsPageControls(),
                Component.translatable("gui.magic_storage.fuel_group.instant_stations"),
                instantStationPage,
                grid.pageCount());
        renderMachineCategoryCells(
                graphics,
                MachineEnergyTable.Category.INSTANT,
                grid,
                instantStationPage);
    }

    private void renderFuelCategoryHeading(
            GuiGraphics graphics,
            TerminalLayout.Rect panel,
            TerminalLayout.FlowGrid grid,
            TerminalLayout.FuelPageControls controls,
            Component heading,
            int page,
            int pageCount
    ) {
        drawInsetPanel(graphics, leftPos, topPos, panel);
        TerminalLayout.Rect label = TerminalLayout.fuelCategoryLabel(panel, grid);
        drawRaisedPanel(graphics, leftPos, topPos, label);
        if (grid.bounds().x() == panel.x() + 2) {
            int pageWidth = pageCount > 1
                    ? font.width((page + 1) + "/" + pageCount) + 4 : 0;
            int availableWidth = Math.max(
                    1, controls.previous().x() - pageWidth - label.x() - 8);
            String visible = font.plainSubstrByWidth(
                    heading.getString(), availableWidth);
            graphics.drawString(
                    font,
                    visible,
                    leftPos + label.x() + 4,
                    topPos + label.y()
                            + (label.height() - font.lineHeight) / 2,
                    0xFF404040,
                    false);
            if (pageCount > 1) {
                String pageText = (page + 1) + "/" + pageCount;
                graphics.drawString(
                        font,
                        pageText,
                        leftPos + controls.previous().x()
                                - font.width(pageText) - 3,
                        topPos + label.y()
                                + (label.height() - font.lineHeight) / 2,
                        0xFF404040,
                        false);
            }
            return;
        }
        List<FormattedCharSequence> lines = font.split(
                heading, Math.max(1, label.width() - 4));
        if (pageCount > 1) {
            FormattedCharSequence line = lines.getFirst();
            graphics.drawString(
                    font,
                    line,
                    leftPos + label.x() + (label.width() - font.width(line)) / 2,
                    topPos + label.y(),
                    0xFF404040,
                    false);
            return;
        }
        int visibleLines = Math.min(2, lines.size());
        int lineCount = visibleLines + (pageCount > 1 ? 1 : 0);
        int y = label.y() + (label.height() - lineCount * font.lineHeight) / 2;
        for (int lineIndex = 0; lineIndex < visibleLines; lineIndex++) {
            FormattedCharSequence line = lines.get(lineIndex);
            graphics.drawString(
                    font,
                    line,
                    leftPos + label.x() + (label.width() - font.width(line)) / 2,
                    topPos + y,
                    0xFF404040,
                    false);
            y += font.lineHeight;
        }
        drawFlowPageIndicator(graphics, label, page, pageCount, y);
    }

    private void renderMachineCategoryCells(
            GuiGraphics graphics,
            MachineEnergyTable.Category category,
            TerminalLayout.FlowGrid grid,
            int page
    ) {
        List<Integer> machineSlots = machineSlotsForCategory(category);
        int first = page * grid.capacity();
        List<TerminalLayout.Rect> cells = grid.cells(page);
        for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
            int categoryIndex = first + visibleIndex;
            if (categoryIndex >= machineSlots.size()) break;
            int machineSlot = machineSlots.get(categoryIndex);
            MachineDescriptor entry = descriptorAt(machineSlot);
            TerminalLayout.Rect cell = cells.get(visibleIndex);
            TerminalLayout.Rect slot = TerminalLayout.fuelSlot(cell);
            drawSlotFrame(graphics, leftPos + slot.x(), topPos + slot.y());
            if (entry != null && menu.getSlot(
                    CraftingTerminalMenu.MACHINE_SLOT_START + machineSlot).getItem().isEmpty()) {
                renderDimmedItem(graphics, entry.representativeStack(), slot);
            }
            if (entry != null) {
                ItemStack installed = menu.getSlot(
                        CraftingTerminalMenu.MACHINE_SLOT_START + machineSlot).getItem();
                drawFlowAmount(
                        graphics,
                        cell,
                        formatAmount(installed.getCount()));
            }
        }
    }

    @Override
    protected void renderSlotContents(
            GuiGraphics graphics,
            ItemStack stack,
            net.minecraft.world.inventory.Slot slot,
            String countString
    ) {
        if (slot.index >= CraftingTerminalMenu.MACHINE_SLOT_START
                && slot.index < CraftingTerminalMenu.MACHINE_SLOT_START
                + CraftingTerminalMenu.MACHINE_SLOT_COUNT) {
            if (!stack.isEmpty()) graphics.renderItem(stack.copyWithCount(1), slot.x, slot.y);
            return;
        }
        super.renderSlotContents(graphics, stack, slot, countString);
    }

    private void renderDimmedItem(
            GuiGraphics graphics,
            ItemStack stack,
            TerminalLayout.Rect bounds
    ) {
        graphics.setColor(0.65F, 0.72F, 0.75F, 0.38F);
        graphics.renderItem(stack, leftPos + bounds.x(), topPos + bounds.y());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawFlowAmount(
            GuiGraphics graphics,
            TerminalLayout.Rect cell,
            String text
    ) {
        TerminalLayout.Rect bounds = TerminalLayout.fuelAmountBounds(cell);
        int textWidth = font.width(text);
        float scale = Math.min(1.0F,
                (float) Math.max(1, bounds.width()) / Math.max(1, textWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(
                leftPos + bounds.x() + bounds.width() / 2.0F,
                topPos + bounds.y(),
                0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -textWidth / 2, 0, 0xFF404040, false);
        graphics.pose().popPose();
    }

    private void renderFuelTypeCapacity(GuiGraphics graphics) {
        TerminalLayout.Rect status = geometry.fuelStatus();
        drawRaisedPanel(graphics, leftPos, topPos, status);
        int capacityHeight = Math.max(
                TerminalLayout.CONTROL_SIZE,
                status.height() - TerminalLayout.CONTROL_SIZE - 2);
        Component label = menu.hasUnlimitedTypeCapacity()
                ? Component.translatable(
                        "gui.magic_storage.type_capacity_unlimited",
                        formatAmount(menu.getTypeCount()))
                : Component.translatable(
                        "gui.magic_storage.type_capacity",
                        formatAmount(menu.getTypeCount()),
                        formatAmount(menu.getMaxTypes()));
        int textWidth = font.width(label);
        int iconSpace = status.width() >= 48 ? 20 : 0;
        int availableTextWidth = Math.max(1, status.width() - iconSpace - 8);
        float scale = Math.min(1.0F, (float) availableTextWidth / Math.max(1, textWidth));
        int contentWidth = iconSpace + Math.round(textWidth * scale);
        int contentX = status.x() + Math.max(2, (status.width() - contentWidth) / 2);
        int contentY = status.y()
                + (capacityHeight - Math.round(font.lineHeight * scale)) / 2;
        if (iconSpace > 0) {
            ItemStack capacityIcon = menu.hasUnlimitedTypeCapacity()
                    ? MagicStorage.CREATIVE_STORAGE_UNIT_ITEM.get().getDefaultInstance()
                    : MagicStorage.STORAGE_UNIT_T1_ITEM.get().getDefaultInstance();
            graphics.renderItem(
                    capacityIcon,
                    leftPos + contentX,
                    topPos + status.y() + (capacityHeight - 16) / 2);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(
                leftPos + contentX + iconSpace,
                topPos + contentY,
                0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, label, 0, 0, 0xFF404040, false);
        graphics.pose().popPose();
    }

    private void drawFlowPageIndicator(
            GuiGraphics graphics,
            TerminalLayout.Rect label,
            int page,
            int pageCount,
            int y
    ) {
        if (pageCount <= 1) return;
        String text = (Math.clamp(page, 0, pageCount - 1) + 1) + "/" + pageCount;
        graphics.drawString(font, text,
                leftPos + label.x() + (label.width() - font.width(text)) / 2,
                topPos + y,
                0xFF404040, false);
    }

    private static void drawSlotFrame(GuiGraphics graphics, int x, int y) {
        drawVanillaSlot(graphics, x, y);
    }

    private void renderRecipePanel(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
        TerminalLayout.Rect panel = geometry.workspace();
        drawInsetPanel(graphics, leftPos, topPos, panel);
        TerminalLayout.Rect diagram = geometry.recipeDiagram();
        TerminalLayout.Rect ledger = geometry.recipeLedger();
        TerminalLayout.Rect footer = geometry.recipeFooter();
        TerminalLayout.Rect content = geometry.recipeContent();
        drawRaisedPanel(graphics, leftPos, topPos, content);
        drawRaisedPanel(graphics, leftPos, topPos, footer);

        RecipePresentation presentation = menu.getRecipePresentation();
        if (presentation.isEmpty()) {
            Component prompt = Component.translatable(menu.getSelectedStack().isEmpty()
                    ? "gui.magic_storage.select_recipe_item"
                    : "gui.magic_storage.no_recipe");
            List<FormattedCharSequence> promptLines = font.split(
                    prompt, Math.max(1, content.width() - 8));
            renderWrappedPrompt(graphics, promptLines, content);
            return;
        }

        graphics.fill(
                leftPos + ledger.x(), topPos + ledger.y(),
                leftPos + ledger.right(), topPos + ledger.y() + 1,
                0xFF8B8B8B);

        lastRecipeMouseX = mouseX;
        lastRecipeMouseY = mouseY;
        activeRecipeDiagramRenderer(presentation).render(
                graphics,
                font,
                presentation,
                recipeDiagramGeometry,
                leftPos,
                topPos,
                mouseX,
                mouseY,
                partialTick);
        renderRecipeStationHint(graphics, presentation);

        int recipeCount = menu.getRecipeCount();
        String recipePosition = recipeCount <= 0 ? "" :
                (Math.clamp(menu.getCurrentRecipeIndex(), 0, recipeCount - 1) + 1) + "/" + recipeCount;
        if (!recipePosition.isEmpty()) {
            graphics.drawString(font, recipePosition,
                    leftPos + diagram.right() - font.width(recipePosition),
                    topPos + diagram.y() + 1, 0xFF606060, false);
        }

        List<RecipePresentation.Resource> resources = presentation.resources();
        List<TerminalLayout.Rect> cells = geometry.recipeLedgerCells(resources.size());
        for (int index = 0; index < resources.size(); index++) {
            renderResourceRow(graphics, cells.get(index), resources.get(index));
        }
    }

    private void renderWrappedPrompt(
            GuiGraphics graphics,
            List<FormattedCharSequence> lines,
            TerminalLayout.Rect bounds
    ) {
        int totalHeight = lines.size() * font.lineHeight;
        int y = topPos + bounds.y() + Math.max(0, (bounds.height() - totalHeight) / 2);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(
                    font,
                    line,
                    leftPos + bounds.x() + Math.max(0, (bounds.width() - font.width(line)) / 2),
                    y,
                    0xFF606060,
                    false);
            y += font.lineHeight;
        }
    }

    private void renderRecipeStationHint(GuiGraphics graphics, RecipePresentation presentation) {
        TerminalLayout.Rect station = geometry.recipeStation();
        int x = leftPos + station.x() + (station.width() - 16) / 2;
        int y = topPos + station.y() + (station.height() - 16) / 2;
        graphics.renderItem(displayedRecipeStation(presentation), x, y);
        graphics.fill(x, y, x + 16, y + 16, 0x58000000);
    }

    private ItemStack displayedRecipeStation(RecipePresentation presentation) {
        long now = System.currentTimeMillis();
        ItemStack installed = presentation.station();
        if (!Objects.equals(stationCycleRecipeId, presentation.recipeId())
                || !ItemStack.isSameItemSameComponents(stationCycleInstalled, installed)
                || now < stationCycleAnchorMillis) {
            stationCycleRecipeId = presentation.recipeId();
            stationCycleInstalled = installed.copyWithCount(1);
            stationCycleAnchorMillis = now;
        }
        long cycle = RecipeStationCycle.cycle(now - stationCycleAnchorMillis);
        return presentation.stationForCycle(cycle);
    }

    private void renderResourceRow(
            GuiGraphics graphics,
            TerminalLayout.Rect cell,
            RecipePresentation.Resource resource
    ) {
        int x = leftPos + cell.x();
        int y = topPos + cell.y();
        int bottom = y + cell.height();
        drawRaisedPanel(graphics, x, y,
                new TerminalLayout.Rect(0, 0, cell.width(), cell.height()));
        int iconY = y + Math.max(0, (cell.height() - 16) / 2);
        ItemStack icon = resource.kind() == RecipePresentation.ResourceKind.ENERGY
                ? resource.energyType().representativeStack() : resource.stack();
        graphics.renderItem(icon, x + 1, iconY);
        RecipeResourceAmountFormatter amount = RecipeResourceAmountFormatter.format(
                resource.available(), resource.required(), resource.infinite());
        int textX = x + 18;
        int textY = y + Math.max(0, (cell.height() - font.lineHeight * 2) / 2);
        int color = resource.infinite() || resource.available() >= resource.required()
                ? 0xFF176B2C : 0xFFFF7777;
        graphics.drawString(font, amount.available(), textX, textY, color, false);
        graphics.drawString(font, amount.required(), textX, textY + font.lineHeight, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        graphics.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, 0xFF404040, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (fuelTargetPopup != null && fuelTargetPopup.visible
                && fuelTargetPopup.isMouseOver(mouseX, mouseY)) return;
        if (!displayedPreferences().page().isItemPage()
                && renderFuelTooltip(graphics, mouseX, mouseY)) return;
        super.renderTooltip(graphics, mouseX, mouseY);
        if (!displayedPreferences().page().isItemPage()) return;
        RecipePresentation presentation = menu.getRecipePresentation();
        if (presentation.isEmpty()) return;
        TerminalLayout.Rect station = geometry.recipeStation();
        if (station.contains(mouseX - leftPos, mouseY - topPos)) {
            graphics.renderTooltip(font, Component.translatable(
                    "gui.magic_storage.recipe_station",
                    displayedRecipeStation(presentation).getHoverName()),
                    mouseX, mouseY);
            return;
        }
        if (activeRecipeDiagramRenderer(presentation).renderTooltip(
                graphics,
                font,
                presentation,
                recipeDiagramGeometry,
                leftPos,
                topPos,
                mouseX,
                mouseY)) {
            return;
        }
        RecipePresentation.Resource resource = recipeResourceAt(
                presentation, mouseX, mouseY);
        if (resource != null) {
            Component name = resource.kind() == RecipePresentation.ResourceKind.ENERGY
                    ? energyLabel(resource.energyType()) : resource.stack().getHoverName();
            graphics.renderComponentTooltip(font, List.of(
                    name,
                    Component.translatable("gui.magic_storage.available_amount",
                            formatAmount(resource.available())),
                    Component.translatable("gui.magic_storage.required_for_one",
                            formatAmount(resource.required()))
            ), mouseX, mouseY);
        }
    }

    private boolean renderFuelTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        TerminalLayout.Rect fuelStatus = geometry.fuelStatus();
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        if (fuelStatus.contains(localX, localY)
                && !geometry.fuelSearchButton().contains(localX, localY)
                && !(fuelSearchActive && geometry.fuelSearchBox().contains(localX, localY))) {
            Component capacity = menu.hasUnlimitedTypeCapacity()
                    ? Component.translatable(
                            "gui.magic_storage.type_capacity_unlimited", menu.getTypeCount())
                    : Component.translatable(
                            "gui.magic_storage.type_capacity",
                            menu.getTypeCount(),
                            menu.getMaxTypes());
            graphics.renderTooltip(font, capacity, mouseX, mouseY);
            return true;
        }

        if (displayedPreferences().page() == CraftingTerminalPage.TRANSFORM) {
            TransformProviderApi.Use use = transformUseAt(mouseX, mouseY);
            if (use != null) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(use.representative().getHoverName());
                tooltip.add(Component.translatable(
                        "tooltip.magic_storage.transform_output",
                        use.infinite() ? "∞" : formatAmount(use.amountPerItem())));
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                return true;
            }
        }

        int machineIndex = machineEnergyIndexAt(mouseX, mouseY);
        if (machineIndex >= 0) {
            ItemStack installed = menu.getSlot(
                    CraftingTerminalMenu.MACHINE_SLOT_START + machineIndex).getItem();
            MachineDescriptor entry = descriptorAt(machineIndex);
            if (entry == null) return false;
            Component machineName = installed.isEmpty()
                    ? entry.representativeStack().getHoverName()
                    : installed.getHoverName();
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(machineName);
            tooltip.add(Component.translatable(
                    "tooltip.magic_storage.machine_installed",
                    installed.getCount()));
            if (entry.generatesEnergy()) {
                MachineWorkRate rate = installed.isEmpty()
                        ? MachineWorkRate.ZERO
                        : entry.rateFor(installed).orElse(MachineWorkRate.ZERO);
                tooltip.add(Component.translatable(
                        "tooltip.magic_storage.machine_rate",
                        MachineRateFormatter.format(rate, installed.getCount())));
                tooltip.add(Component.translatable("tooltip.magic_storage.energy_stored",
                        machineStoredAmount(entry)));
            } else if (entry.category() == MachineEnergyTable.Category.TRANSFORM) {
                if (entry.id().equals(MachineEnergyTable.AXE_ID)) {
                    tooltip.set(0, Component.translatable("gui.magic_storage.axe_energy"));
                }
                Object amount = menu.hasInfiniteDescriptor(entry.id())
                        ? "∞"
                        : menu.getDescriptorAmount(entry.id());
                tooltip.add(Component.translatable("tooltip.magic_storage.energy_stored", amount));
            }
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return true;
        }

        int fuelIndex = storedFuelIndexAt(mouseX, mouseY);
        if (fuelIndex >= 0) {
            EnergyType type = CraftingTerminalMenu.fuelTargets().get(fuelIndex);
            graphics.renderComponentTooltip(font, List.of(
                    energyLabel(type),
                    Component.translatable("tooltip.magic_storage.energy_stored",
                            menu.getEnergyAmount(type))
            ), mouseX, mouseY);
            return true;
        }

        TerminalLayout.Rect input = geometry.fuelInput();
        if (menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().isEmpty()
                && input.contains(mouseX - leftPos, mouseY - topPos)) {
            graphics.renderTooltip(font, Component.translatable("gui.magic_storage.fuel_input"), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private long machineStoredAmount(MachineDescriptor descriptor) {
        EnergyType energyType = descriptor.energyType();
        return energyType == null
                ? menu.getDescriptorAmount(descriptor.id())
                : menu.getEnergyAmount(energyType);
    }

    private TransformProviderApi.Use transformUseAt(int mouseX, int mouseY) {
        List<TransformProviderApi.Use> uses = menu.getTransformUses();
        TerminalLayout.FlowGrid grid = geometry.consumablesGrid();
        int first = consumablePage * grid.capacity();
        List<TerminalLayout.Rect> cells = grid.cells(
                consumablePage, 1 + uses.size());
        for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
            int entryIndex = first + visibleIndex;
            if (entryIndex <= 0) continue;
            if (TerminalLayout.fuelIcon(cells.get(visibleIndex)).contains(
                    mouseX - leftPos, mouseY - topPos)
                    || TerminalLayout.fuelAmountBounds(cells.get(visibleIndex)).contains(
                    mouseX - leftPos, mouseY - topPos)) {
                return uses.get(entryIndex - 1);
            }
        }
        return null;
    }

    private RecipePresentation.Resource recipeResourceAt(
            RecipePresentation presentation,
            int mouseX,
            int mouseY
    ) {
        List<RecipePresentation.Resource> resources = presentation.resources();
        List<TerminalLayout.Rect> cells = geometry.recipeLedgerCells(resources.size());
        for (int index = 0; index < resources.size(); index++) {
            TerminalLayout.Rect cell = cells.get(index);
            if (cell.contains(mouseX - leftPos, mouseY - topPos)) return resources.get(index);
        }
        return null;
    }

    private int machineEnergyIndexAt(int mouseX, int mouseY) {
        if (fuelSearchActive) {
            TerminalLayout.FlowGrid grid = geometry.fuelSearchGrid();
            int first = fuelSearchPage * grid.capacity();
            List<TerminalLayout.Rect> cells = grid.cells(
                    fuelSearchPage, filteredFuelEntries.size());
            for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
                FuelSearchModel.Entry result = filteredFuelEntries.get(first + visibleIndex);
                if (result.isEnergy()) continue;
                TerminalLayout.Rect cell = cells.get(visibleIndex);
                if (TerminalLayout.fuelSlot(cell).contains(
                        mouseX - leftPos, mouseY - topPos)
                        || result.category() != MachineEnergyTable.Category.INSTANT
                        && TerminalLayout.fuelAmountBounds(cell).contains(
                        mouseX - leftPos, mouseY - topPos)) {
                    return result.machineSlot();
                }
            }
            return -1;
        }
        int process = machineEnergyIndexAt(
                MachineEnergyTable.Category.PROCESS,
                geometry.timedStationsGrid(),
                timedStationPage,
                0,
                mouseX,
                mouseY);
        if (process >= 0) return process;
        int instant = machineEnergyIndexAt(
                MachineEnergyTable.Category.INSTANT,
                geometry.instantStationsGrid(),
                instantStationPage,
                0,
                mouseX,
                mouseY);
        if (instant >= 0) return instant;
        return -1;
    }

    private int machineEnergyIndexAt(
            MachineEnergyTable.Category category,
            TerminalLayout.FlowGrid grid,
            int page,
            int descriptorOffset,
            int mouseX,
            int mouseY
    ) {
        List<Integer> machineSlots = machineSlotsForCategory(category);
        int firstDescriptor = page * grid.capacity();
        List<TerminalLayout.Rect> cells = grid.cells(page);
        for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
            int categoryIndex = firstDescriptor + visibleIndex - descriptorOffset;
            if (categoryIndex < 0 || categoryIndex >= machineSlots.size()) continue;
            if (TerminalLayout.fuelSlot(cells.get(visibleIndex)).contains(
                    mouseX - leftPos, mouseY - topPos)
                    || category == MachineEnergyTable.Category.PROCESS
                    && TerminalLayout.fuelAmountBounds(cells.get(visibleIndex)).contains(
                    mouseX - leftPos, mouseY - topPos)) {
                return machineSlots.get(categoryIndex);
            }
        }
        return -1;
    }

    private int storedFuelIndexAt(int mouseX, int mouseY) {
        if (fuelSearchActive) {
            TerminalLayout.FlowGrid grid = geometry.fuelSearchGrid();
            int first = fuelSearchPage * grid.capacity();
            List<TerminalLayout.Rect> cells = grid.cells(
                    fuelSearchPage, filteredFuelEntries.size());
            for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
                FuelSearchModel.Entry result = filteredFuelEntries.get(first + visibleIndex);
                if (!result.isEnergy()) continue;
                if (TerminalLayout.fuelIcon(cells.get(visibleIndex)).contains(
                        mouseX - leftPos, mouseY - topPos)) {
                    return CraftingTerminalMenu.fuelTargets().indexOf(result.energyType());
                }
            }
            return -1;
        }
        TerminalLayout.FlowGrid grid = geometry.consumablesGrid();
        int firstDescriptor = consumablePage * grid.capacity();
        List<TerminalLayout.Rect> cells = grid.cells(consumablePage);
        int fuelCount = CraftingTerminalMenu.fuelTargets().size();
        for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
            int descriptorIndex = firstDescriptor + visibleIndex;
            if (descriptorIndex <= 0 || descriptorIndex > fuelCount) continue;
            if (TerminalLayout.fuelIcon(cells.get(visibleIndex)).contains(
                    mouseX - leftPos, mouseY - topPos)) return descriptorIndex - 1;
        }
        return -1;
    }

    private static int consumableMachineOffset() {
        return 1 + CraftingTerminalMenu.fuelTargets().size();
    }

    private MachineDescriptor descriptorAt(int slot) {
        List<MachineDescriptor> descriptors = menu.getMachineDescriptors();
        return slot >= 0 && slot < descriptors.size() ? descriptors.get(slot) : null;
    }

    private List<Integer> machineSlotsForCategory(MachineEnergyTable.Category category) {
        List<Integer> result = new ArrayList<>();
        List<MachineDescriptor> entries = menu.getMachineDescriptors();
        for (int slot = 0; slot < entries.size(); slot++) {
            if (entries.get(slot).category() == category) result.add(slot);
        }
        return List.copyOf(result);
    }

    private List<FuelTargetOption> fuelTargetOptions() {
        List<FuelTargetOption> options = new ArrayList<>();
        options.add(new FuelTargetOption(
                null,
                Items.COMPARATOR.getDefaultInstance(),
                Component.translatable("gui.magic_storage.fuel_target_auto")));
        for (TransformProviderApi.Target target : menu.getTransformTargets()) {
            EnergyType energy = TransformProviderApi.energyType(target.id()).orElse(null);
            options.add(new FuelTargetOption(
                    target.id(),
                    target.representative(),
                    energy == null
                            ? target.representative().getHoverName()
                            : energyLabel(energy)));
        }
        return List.copyOf(options);
    }

    private FuelTargetOption fuelTargetOption(ResourceLocation targetId) {
        List<FuelTargetOption> options = fuelTargetOptions();
        int index = indexOfFuelTarget(options, targetId);
        if (index >= 0) return options.get(index);
        return new FuelTargetOption(
                targetId,
                Items.BARRIER.getDefaultInstance(),
                Component.literal(targetId.toString()));
    }

    private static int indexOfFuelTarget(
            List<FuelTargetOption> options,
            ResourceLocation targetId
    ) {
        for (int index = 0; index < options.size(); index++) {
            if (Objects.equals(options.get(index).target(), targetId)) return index;
        }
        return -1;
    }

    private final class FuelTargetPopup extends Button {
        private final TerminalLayout.PopupList popupGeometry;
        private int scrollOffset;

        private FuelTargetPopup(
                int x,
                int y,
                TerminalLayout.PopupList popupGeometry
        ) {
            super(x, y, popupGeometry.bounds().width(), popupGeometry.bounds().height(),
                    Component.translatable("gui.magic_storage.fuel_target_list"),
                    button -> { }, DEFAULT_NARRATION);
            this.popupGeometry = popupGeometry;
        }

        private void reveal(int optionIndex) {
            if (optionIndex < scrollOffset) {
                scrollOffset = optionIndex;
            } else if (optionIndex >= scrollOffset + popupGeometry.capacity()) {
                scrollOffset = optionIndex - popupGeometry.capacity() + 1;
            }
            scrollOffset = popupGeometry.clampScrollOffset(scrollOffset);
        }

        @Override
        protected boolean isValidClickButton(int button) {
            return button == 0;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            int localX = (int) mouseX - leftPos;
            int localY = (int) mouseY - topPos;
            TerminalLayout.Rect bounds = popupGeometry.bounds();
            if (popupGeometry.maxScrollOffset() > 0
                    && localX >= bounds.right() - TerminalScrollbar.THUMB_WIDTH - 2) {
                int trackTop = topPos + bounds.y() + 2;
                int trackHeight = bounds.height() - 4;
                int thumbTop = TerminalScrollbar.thumbTop(
                        trackTop,
                        trackHeight,
                        scrollOffset,
                        popupGeometry.maxScrollOffset());
                scrollOffset = TerminalScrollbar.pageTarget(
                        mouseY,
                        thumbTop,
                        scrollOffset,
                        popupGeometry.capacity(),
                        popupGeometry.maxScrollOffset());
                return;
            }
            List<TerminalLayout.Rect> rows = popupGeometry.rows(scrollOffset);
            for (int row = 0; row < rows.size(); row++) {
                if (rows.get(row).contains(localX, localY)) {
                    selectFuelTarget(fuelTargetOptions().get(scrollOffset + row));
                    return;
                }
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
            int direction = scrollY < 0 ? 1 : -1;
            scrollOffset = popupGeometry.clampScrollOffset(scrollOffset + direction);
            return true;
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            drawRaisedPanel(graphics, leftPos, topPos, popupGeometry.bounds());
            List<FuelTargetOption> options = fuelTargetOptions();
            List<TerminalLayout.Rect> rows = popupGeometry.rows(scrollOffset);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                FuelTargetOption option = options.get(scrollOffset + rowIndex);
                TerminalLayout.Rect row = rows.get(rowIndex);
                boolean selected = Objects.equals(
                        option.target(), displayedPreferences().transformTarget());
                int left = leftPos + row.x();
                int top = topPos + row.y();
                int right = left + row.width();
                int bottom = top + row.height();
                TerminalLayout.Rect rowBounds = new TerminalLayout.Rect(
                        row.x(), row.y(), row.width(), row.height());
                if (selected) {
                    drawInsetPanel(graphics, leftPos, topPos, rowBounds);
                } else {
                    drawRaisedPanel(graphics, leftPos, topPos, rowBounds);
                }
                ItemStack icon = option.icon();
                graphics.renderItem(icon, left + 2, top + (row.height() - 16) / 2);
                String label = font.plainSubstrByWidth(
                        option.label().getString(), Math.max(0, row.width() - 38));
                graphics.drawString(font, label, left + 21,
                        top + Math.max(0, (row.height() - font.lineHeight) / 2),
                        0xFF404040, false);
            }

            TerminalLayout.Rect bounds = popupGeometry.bounds();
            drawScrollbar(
                    graphics,
                    leftPos + bounds.right() - TerminalScrollbar.THUMB_WIDTH - 2,
                    topPos + bounds.y() + 2,
                    bounds.height() - 4,
                    scrollOffset,
                    popupGeometry.maxScrollOffset());
        }
    }

    private static Component energyLabel(EnergyType type) {
        return Component.translatable("gui.magic_storage.energy." + type.getId());
    }

    private static String formatAmount(long amount) {
        return TerminalAmountFormatter.formatCompact(amount);
    }

    private record FuelTargetOption(
            ResourceLocation target,
            ItemStack iconStack,
            Component labelText
    ) {
        private FuelTargetOption {
            iconStack = iconStack.copyWithCount(1);
        }

        private ItemStack icon() {
            return iconStack.copy();
        }

        private Component label() {
            return labelText;
        }
    }

    private record FuelPageButtons(TerminalIconButton previous, TerminalIconButton next) {
    }

    public List<Rect2i> getEmiExclusionAreas() {
        List<Rect2i> result = terminalExclusionAreas();
        if (fuelTargetPopup != null && fuelTargetPopup.visible) {
            TerminalLayout.Rect popup = geometry.fuelTargetPopup().bounds();
            result.add(new Rect2i(
                    leftPos + popup.x(), topPos + popup.y(), popup.width(), popup.height()));
        }
        return List.copyOf(result);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (fuelTargetPopup != null && fuelTargetPopup.visible) {
            if (fuelTargetPopup.isMouseOver(mouseX, mouseY)) {
                if (button == 0) fuelTargetPopup.onClick(mouseX, mouseY);
                return true;
            }
            if (!fuelTargetListBtn.isMouseOver(mouseX, mouseY)) {
                closeFuelTargetPopup();
                return true;
            }
        }
        if (button == 2 && displayedPreferences().page() == CraftingTerminalPage.STATIONS) {
            if (timedStationsPageButtons.previous().isMouseOver(mouseX, mouseY)
                    || timedStationsPageButtons.next().isMouseOver(mouseX, mouseY)) {
                timedStationPage = 0;
                repositionFuelSlots();
                updateFuelPageButtonStates();
                return true;
            }
            if (instantStationsPageButtons.previous().isMouseOver(mouseX, mouseY)
                    || instantStationsPageButtons.next().isMouseOver(mouseX, mouseY)) {
                instantStationPage = 0;
                repositionFuelSlots();
                updateFuelPageButtonStates();
                return true;
            }
        }
        if (displayedPreferences().page().isItemPage()) {
            RecipePresentation presentation = menu.getRecipePresentation();
            if (!presentation.isEmpty() && geometry.recipeStation().contains(
                    (int) mouseX - leftPos, (int) mouseY - topPos)) {
                return true;
            }
            if (!presentation.isEmpty()
                    && activeRecipeDiagramRenderer(presentation).mouseClicked(
                    presentation,
                    recipeDiagramGeometry,
                    leftPos,
                    topPos,
                    mouseX,
                    mouseY,
                    button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (fuelTargetPopup != null && fuelTargetPopup.visible && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeFuelTargetPopup();
            return true;
        }
        if (displayedPreferences().page() == CraftingTerminalPage.STATIONS && fuelSearchActive) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setFuelSearchActive(false);
                return true;
            }
            if (fuelSearchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (displayedPreferences().page().isItemPage()) {
            RecipePresentation presentation = menu.getRecipePresentation();
            if (!presentation.isEmpty()
                    && activeRecipeDiagramRenderer(presentation).keyPressed(
                    presentation,
                    recipeDiagramGeometry,
                    leftPos,
                    topPos,
                    lastRecipeMouseX,
                    lastRecipeMouseY,
                    keyCode,
                    scanCode,
                    modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (displayedPreferences().page() == CraftingTerminalPage.STATIONS
                && fuelSearchActive
                && fuelSearchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (displayedPreferences().page() == CraftingTerminalPage.TRANSFORM && scrollY != 0) {
            if (fuelTargetPopup.visible && fuelTargetPopup.isMouseOver(mouseX, mouseY)) {
                return fuelTargetPopup.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
            if (fuelTargetSelector.visible && fuelTargetSelector.active
                    && fuelTargetSelector.isMouseOver(mouseX, mouseY)) {
                return fuelTargetSelector.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
        }
        if (displayedPreferences().page() == CraftingTerminalPage.STATIONS && scrollY != 0) {
            int localX = (int) mouseX - leftPos;
            int localY = (int) mouseY - topPos;
            int direction = scrollY < 0 ? 1 : -1;
            if (fuelSearchActive && geometry.fuelSearchPanel().contains(localX, localY)) {
                if (fuelSearchPageCount() > 1) {
                    fuelSearchPage = Math.clamp(
                            fuelSearchPage + direction,
                            0,
                            fuelSearchPageCount() - 1);
                    repositionFuelSlots();
                    updateFuelPageButtonStates();
                }
                return true;
            }
            if (geometry.timedStationsPanel().contains(localX, localY)
                    && geometry.timedStationsGrid().pageCount() > 1) {
                timedStationPage = Math.clamp(
                        timedStationPage + direction,
                        0,
                        geometry.timedStationsGrid().pageCount() - 1);
                repositionFuelSlots();
                updateFuelPageButtonStates();
                return true;
            }
            if (geometry.instantStationsPanel().contains(localX, localY)
                    && geometry.instantStationsGrid().pageCount() > 1) {
                instantStationPage = Math.clamp(
                        instantStationPage + direction,
                        0,
                        geometry.instantStationsGrid().pageCount() - 1);
                repositionFuelSlots();
                updateFuelPageButtonStates();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
