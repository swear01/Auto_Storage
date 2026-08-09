package com.swear.autostorage;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class CraftingTerminalScreen extends StorageTerminalScreen<CraftingTerminalMenu> {

    private final NativeRecipeDiagramRenderer nativeRecipeDiagramRenderer;
    private ClientSetup.ViewerBinding preferredViewerBinding;
    private long preferredViewerGeneration;
    private RecipeDiagramRenderer preferredRecipeDiagramRenderer;
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
    private TerminalCycleButton stationDisplayModeBtn;
    private EditBox transformTargetSearchBox;
    private EditBox fuelSearchBox;
    private FuelPageButtons transformTargetPageButtons;
    private FuelPageButtons transformCardPageButtons;
    private FuelPageButtons timedStationsPageButtons;
    private FuelPageButtons instantStationsPageButtons;
    private FuelPageButtons fuelSearchPageButtons;
    private CraftingTerminalPage lastPage;
    private ResourceLocation lastTransformTarget;
    private int transformUsePage;
    private int transformTargetPage;
    private int timedStationPage;
    private int instantStationPage;
    private int fuelSearchPage;
    private boolean fuelSearchActive;
    private FuelSearchModel.Index fuelSearchIndex;
    private List<FuelTargetOption> filteredTransformTargets = List.of();
    private List<FuelSearchModel.Entry> filteredFuelEntries = List.of();
    private RecipeDiagramRenderer.Geometry recipeDiagramGeometry;
    private int lastRecipeMouseX;
    private int lastRecipeMouseY;
    private ResourceLocation stationCycleRecipeId;
    private ItemStack stationCycleInstalled = ItemStack.EMPTY;
    private long stationCycleAnchorMillis;
    private ResourceLocation recipeLedgerRecipeId;
    private int recipeLedgerOffset;
    private SortMode lastUtilitySortMode;
    private SortOrder lastUtilitySortOrder;
    private StationDisplayMode stationDisplayMode = StationDisplayMode.ALL;

    public CraftingTerminalScreen(CraftingTerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        nativeRecipeDiagramRenderer = new NativeRecipeDiagramRenderer();
        preferredViewerBinding = ClientSetup.selectedViewerBinding();
        preferredViewerGeneration = ClientSetup.viewerGeneration();
        preferredRecipeDiagramRenderer = ClientSetup.createRecipeDiagramRenderer();
    }

    private RecipeDiagramRenderer preferredRecipeDiagramRenderer() {
        ClientSetup.ViewerBinding binding = ClientSetup.selectedViewerBinding();
        long generation = ClientSetup.viewerGeneration();
        if (binding != preferredViewerBinding || generation != preferredViewerGeneration) {
            preferredViewerBinding = binding;
            preferredViewerGeneration = generation;
            preferredRecipeDiagramRenderer = ClientSetup.createRecipeDiagramRenderer();
        }
        return preferredRecipeDiagramRenderer;
    }

    @Override
    protected TerminalProfile terminalProfile() {
        return TerminalProfile.CRAFTING;
    }

    @Override
    protected TerminalLayout.FuelDescriptorCounts fuelDescriptorCounts() {
        int timedStations = machineSlotsForCategory(MachineCategory.PROCESS).size();
        int instantStations = machineSlotsForCategory(MachineCategory.INSTANT).size();
        int transformUses = Math.max(1, menu.getVisibleTransformUses().size());
        return new TerminalLayout.FuelDescriptorCounts(
                transformUses,
                timedStations,
                instantStations,
                fuelTargetOptions().size());
    }

    @Override
    protected void addTerminalProfileControls() {
        storagePageBtn = addItemButton(
                AutoStorage.STORAGE_TERMINAL_ITEM.get().getDefaultInstance(),
                Component.translatable("gui.auto_storage.page_storage"),
                geometry.railButtons().get(0),
                button -> clickMenuButton(CraftingTerminalMenu.STORAGE_PAGE_BUTTON));
        craftablePageBtn = addItemButton(
                Items.CRAFTING_TABLE.getDefaultInstance(),
                Component.translatable("gui.auto_storage.page_craftable"),
                geometry.railButtons().get(1),
                button -> clickMenuButton(CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON));
        transformPageBtn = addItemButton(
                Items.COAL.getDefaultInstance(),
                Component.translatable("gui.auto_storage.page_transform"),
                geometry.railButtons().get(2),
                button -> clickMenuButton(CraftingTerminalMenu.TRANSFORM_PAGE_BUTTON));
        stationsPageBtn = addItemButton(
                Items.FURNACE.getDefaultInstance(),
                Component.translatable("gui.auto_storage.page_stations"),
                geometry.railButtons().get(3),
                button -> clickMenuButton(CraftingTerminalMenu.STATIONS_PAGE_BUTTON));
        playerInventoryRailBtn = addItemCycleButton(
                Items.BUNDLE.getDefaultInstance(),
                Component.translatable("gui.auto_storage.use_player_inv"),
                geometry.railButtons().get(terminalProfile().playerInventorySourceIndex()),
                direction -> clickMenuButton(7),
                () -> clickMenuButton(CraftingTerminalMenu.RESET_PLAYER_INVENTORY_BUTTON));
        outputDestinationRailBtn = addItemCycleButton(
                Items.PLAYER_HEAD.getDefaultInstance(),
                Component.translatable("gui.auto_storage.output_destination"),
                geometry.railButtons().get(terminalProfile().outputDestinationIndex()),
                direction -> clickMenuButton(CraftingTerminalMenu.OUTPUT_DESTINATION_BUTTON),
                () -> clickMenuButton(CraftingTerminalMenu.RESET_OUTPUT_DESTINATION_BUTTON));
        stationDisplayModeBtn = addItemCycleButton(
                Items.CHEST.getDefaultInstance(),
                Component.translatable("tooltip.auto_storage.station_display"),
                geometry.fuelRailButtons().get(terminalProfile().viewControlStartIndex() + 2),
                direction -> setStationDisplayMode(stationDisplayMode.next()),
                () -> setStationDisplayMode(StationDisplayMode.ALL));
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
        String previousTransformSearch = transformTargetSearchBox == null
                ? "" : transformTargetSearchBox.getValue();
        boolean previousTransformSearchFocused = transformTargetSearchBox != null
                && transformTargetSearchBox.isFocused();
        String previousFuelSearch = fuelSearchBox == null ? "" : fuelSearchBox.getValue();
        boolean previousFuelSearchFocused = fuelSearchBox != null && fuelSearchBox.isFocused();
        fuelSearchActive = !TerminalSearchQuery.compile(previousFuelSearch).isEmpty();
        super.init();
        recipeDiagramGeometry = createRecipeDiagramGeometry();
        List<TerminalLayout.Rect> navigationButtons = geometry.recipeNavigationButtons();
        prevRecipeBtn = addRecipeNavigationButton(
                TerminalControlIcon.PREVIOUS,
                Component.translatable("gui.auto_storage.previous_recipe"),
                navigationButtons.get(0),
                button -> clickMenuButton(8));
        nextRecipeBtn = addRecipeNavigationButton(
                TerminalControlIcon.NEXT,
                Component.translatable("gui.auto_storage.next_recipe"),
                navigationButtons.get(1),
                button -> clickMenuButton(9));

        List<TerminalLayout.Rect> craftButtons = geometry.recipeCraftButtons();
        craft1Btn = addRecipeAmountButton(
                Component.translatable("gui.auto_storage.craft_amount", 1), craftButtons.get(0),
                button -> clickMenuButton(2), RecipeAmountSegment.FIRST);
        craft8Btn = addRecipeAmountButton(
                Component.translatable("gui.auto_storage.craft_amount", 8), craftButtons.get(1),
                button -> clickMenuButton(3), RecipeAmountSegment.MIDDLE);
        craft64Btn = addRecipeAmountButton(
                Component.translatable("gui.auto_storage.craft_amount", 64), craftButtons.get(2),
                button -> clickMenuButton(4), RecipeAmountSegment.MIDDLE);
        craftMaxBtn = addRecipeAmountButton(
                Component.translatable("gui.auto_storage.craft_max"), craftButtons.get(3),
                button -> clickMenuButton(CraftingTerminalMenu.MAX_CRAFT_BUTTON),
                RecipeAmountSegment.LAST);

        TerminalLayout.Rect targetSearch = geometry.transformTargetSearch();
        transformTargetSearchBox = new EditBox(
                font,
                leftPos + targetSearch.x(),
                topPos + targetSearch.y(),
                targetSearch.width(),
                font.lineHeight,
                Component.translatable("gui.auto_storage.transform_search"));
        transformTargetSearchBox.setBordered(false);
        transformTargetSearchBox.setTextColor(0xFFFFFF);
        transformTargetSearchBox.setMaxLength(50);
        transformTargetSearchBox.setValue(previousTransformSearch);
        transformTargetSearchBox.setResponder(text -> refreshTransformTargets());
        addRenderableWidget(transformTargetSearchBox);
        TerminalLayout.Rect fuelSearch = geometry.fuelSearchBox();
        fuelSearchBox = new EditBox(
                font,
                leftPos + fuelSearch.x(),
                topPos + fuelSearch.y(),
                fuelSearch.width(),
                font.lineHeight,
                Component.translatable("gui.auto_storage.fuel_search"));
        fuelSearchBox.setBordered(false);
        fuelSearchBox.setTextColor(0xFFFFFF);
        fuelSearchBox.setMaxLength(50);
        fuelSearchBox.setTooltip(Tooltip.create(
                Component.translatable("tooltip.auto_storage.search_help")));
        fuelSearchBox.setValue(previousFuelSearch);
        fuelSearchBox.setResponder(this::setFuelSearchQuery);
        addRenderableWidget(fuelSearchBox);
        transformTargetPageButtons = addFuelPageControls(
                geometry.transformTargetPageControls(),
                () -> transformTargetPage,
                page -> transformTargetPage = page,
                this::transformTargetPageCount);
        transformCardPageButtons = addFuelPageControls(
                geometry.transformCardPageControls(),
                () -> transformUsePage,
                page -> transformUsePage = page,
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

        refreshTransformTargets();
        fuelSearchIndex = FuelSearchModel.index(
                CraftingTerminalMenu.fuelTargets(),
                menu.getMachineDescriptors());
        refreshFuelSearchResults();
        updatePageWidgets();
        updateSidebarState();
        boolean previousActiveSearchFocused = switch (displayedPreferences().page()) {
            case TRANSFORM -> previousTransformSearchFocused;
            case STATIONS -> previousFuelSearchFocused;
            default -> false;
        };
        focusActiveSearchBox(searchBoxAutoSelected() || previousActiveSearchFocused);
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
        RecipeDiagramRenderer preferred = preferredRecipeDiagramRenderer();
        return preferred.supports(presentation, recipeDiagramGeometry)
                ? preferred : nativeRecipeDiagramRenderer;
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
                "gui.auto_storage.previous_fuel_page");
        Component nextLabel = Component.translatable(
                "gui.auto_storage.next_fuel_page");
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
        return geometry.transformCards().pageCount(
                menu.getVisibleTransformUses().size());
    }

    private int transformTargetPageCount() {
        return geometry.transformTargetList().pageCount(filteredTransformTargets.size());
    }

    private void refreshTransformTargets() {
        TerminalSearchQuery query = TerminalSearchQuery.compile(
                transformTargetSearchBox == null ? "" : transformTargetSearchBox.getValue());
        filteredTransformTargets = fuelTargetOptions().stream()
                .filter(option -> query.matches(option.searchEntry()))
                .sorted(TerminalEntryComparator.forMode(
                        displayedPreferences().sortMode(),
                        displayedPreferences().sortOrder(),
                        FuelTargetOption::sortStack))
                .toList();
        transformTargetPage = Math.clamp(
                transformTargetPage, 0, transformTargetPageCount() - 1);
        updateFuelPageButtonStates();
    }

    private void refreshFuelSearchResults() {
        refreshFuelSearchResults(TerminalSearchQuery.compile(
                fuelSearchBox == null ? "" : fuelSearchBox.getValue()));
    }

    private void refreshFuelSearchResults(TerminalSearchQuery query) {
        filteredFuelEntries = FuelSearchModel.search(query, fuelSearchIndex).stream()
                .filter(entry -> !entry.isEnergy()
                        && entry.category() != MachineCategory.TRANSFORM)
                .filter(entry -> stationDisplayMode.shows(
                        isStationInstalled(entry.machineSlot())))
                .sorted(TerminalEntryComparator.forMode(
                        displayedPreferences().sortMode(),
                        displayedPreferences().sortOrder(),
                        entry -> machineSortStack(entry.machineSlot())))
                .toList();
        fuelSearchPage = Math.clamp(
                fuelSearchPage, 0, fuelSearchPageCount() - 1);
        repositionFuelSlots();
        updateFuelPageButtonStates();
    }

    private void setFuelSearchQuery(String text) {
        TerminalSearchQuery query = TerminalSearchQuery.compile(text);
        boolean previousActive = fuelSearchActive;
        fuelSearchActive = !query.isEmpty();
        fuelSearchPage = 0;
        refreshFuelSearchResults(query);
        if (fuelSearchActive != previousActive) updatePageWidgets();
    }

    private void repositionFuelSlots() {
        transformUsePage = Math.clamp(
                transformUsePage, 0, transformPageCount() - 1);
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
            TerminalLayout.Rect fuelInput = geometry.transformInput();
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
                MachineCategory.PROCESS,
                geometry.timedStationsGrid(),
                timedStationPage,
                0);
        positionMachineCategory(
                MachineCategory.INSTANT,
                geometry.instantStationsGrid(),
                instantStationPage,
                0);
    }

    private void positionMachineCategory(
            MachineCategory category,
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

    private void selectFuelTarget(FuelTargetOption option) {
        clickMenuButton(option.target() == null
                ? CraftingTerminalMenu.AUTO_FUEL_TARGET_BUTTON
                : TransformProviderApi.targetButtonId(
                        option.target(), menu.getMachineDescriptors()));
    }

    private void updatePageWidgets() {
        TerminalPreferences preferences = displayedPreferences();
        CraftingTerminalPage page = preferences.page();
        ResourceLocation target = preferences.transformTarget();
        boolean itemPage = page.isItemPage();
        setItemViewControlsVisible(itemPage);
        if (!itemPage) setSortControlsVisible(true);
        repositionFuelSlots();

        setWidgetVisible(prevRecipeBtn, itemPage);
        setWidgetVisible(nextRecipeBtn, itemPage);
        boolean amountActions = itemPage || page == CraftingTerminalPage.TRANSFORM;
        positionAmountButtons(page == CraftingTerminalPage.TRANSFORM
                ? geometry.transformAmountButtons()
                : geometry.recipeCraftButtons());
        setWidgetVisible(craft1Btn, amountActions);
        setWidgetVisible(craft8Btn, amountActions);
        setWidgetVisible(craft64Btn, amountActions);
        setWidgetVisible(craftMaxBtn, amountActions);
        setWidgetVisible(playerInventoryRailBtn, itemPage);
        setWidgetVisible(outputDestinationRailBtn, itemPage);
        setWidgetVisible(stationDisplayModeBtn,
                page == CraftingTerminalPage.STATIONS);

        storagePageBtn.active = page != CraftingTerminalPage.STORAGE;
        craftablePageBtn.active = page != CraftingTerminalPage.CRAFTABLE;
        transformPageBtn.active = page != CraftingTerminalPage.TRANSFORM;
        stationsPageBtn.active = page != CraftingTerminalPage.STATIONS;

        boolean transform = page == CraftingTerminalPage.TRANSFORM;
        boolean stations = page == CraftingTerminalPage.STATIONS;
        setWidgetVisible(transformTargetSearchBox, transform);
        setWidgetVisible(fuelSearchBox, stations);
        updateFuelPageButtonStates();
        updateCraftButtonState();
        lastPage = page;
        lastTransformTarget = target;
    }

    private void positionAmountButtons(List<TerminalLayout.Rect> bounds) {
        List<Button> buttons = List.of(craft1Btn, craft8Btn, craft64Btn, craftMaxBtn);
        for (int index = 0; index < buttons.size(); index++) {
            Button button = buttons.get(index);
            TerminalLayout.Rect rectangle = bounds.get(index);
            button.setX(leftPos + rectangle.x());
            button.setY(topPos + rectangle.y());
            button.setWidth(rectangle.width());
        }
    }

    private void updateSidebarState() {
        TerminalPreferences preferences = displayedPreferences();
        storagePageBtn.setTooltip(Tooltip.create(Component.translatable("gui.auto_storage.page_storage")));
        craftablePageBtn.setTooltip(Tooltip.create(Component.translatable("gui.auto_storage.page_craftable")));
        transformPageBtn.setTooltip(Tooltip.create(Component.translatable("gui.auto_storage.page_transform")));
        stationsPageBtn.setTooltip(Tooltip.create(Component.translatable("gui.auto_storage.page_stations")));
        updateToggleButton(playerInventoryRailBtn, "gui.auto_storage.use_player_inv",
                preferences.usePlayerInventory());
        boolean storageOnly = menu.isSelectedOutputStorageOnly();
        TerminalOutputDestination effectiveDestination = storageOnly
                ? TerminalOutputDestination.STORAGE
                : preferences.outputDestination();
        Component outputDestination = switch (effectiveDestination) {
            case PLAYER -> Component.translatable("gui.auto_storage.output_destination.player");
            case STORAGE -> Component.translatable("gui.auto_storage.output_destination.storage");
        };
        outputDestinationRailBtn.setItemIcon(switch (effectiveDestination) {
            case PLAYER -> Items.PLAYER_HEAD.getDefaultInstance();
            case STORAGE -> AutoStorage.STORAGE_CORE_ITEM.get().getDefaultInstance();
        });
        outputDestinationRailBtn.active = outputDestinationRailBtn.visible && !storageOnly;
        updateCycleTooltip(outputDestinationRailBtn, "gui.auto_storage.output_destination",
                outputDestination);
        stationDisplayModeBtn.setItemIcon(stationDisplayMode == StationDisplayMode.ALL
                ? Items.CHEST.getDefaultInstance()
                : Items.FURNACE.getDefaultInstance());
        updateCycleTooltip(
                stationDisplayModeBtn,
                "tooltip.auto_storage.station_display",
                Component.translatable(stationDisplayMode.translationKey()));
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
        boolean transform = displayedPreferences().page() == CraftingTerminalPage.TRANSFORM;
        boolean stations = displayedPreferences().page() == CraftingTerminalPage.STATIONS;
        updateFuelPageButtons(
                transformTargetPageButtons,
                transformTargetPage,
                transformTargetPageCount(),
                transform);
        updateFuelPageButtons(
                transformCardPageButtons,
                transformUsePage,
                transformPageCount(),
                transform);
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
        boolean visible = fuel && pageCount > 1;
        setWidgetVisible(buttons.previous(), visible);
        setWidgetVisible(buttons.next(), visible);
        buttons.previous().active = visible && page > 0;
        buttons.next().active = visible && page + 1 < pageCount;
    }

    private void updateToggleButton(TerminalCycleButton button, String key, boolean enabled) {
        updateCycleTooltip(button, key, Component.translatable(enabled
                ? "gui.auto_storage.state_on"
                : "gui.auto_storage.state_off"));
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
        if (lastUtilitySortMode != preferences.sortMode()
                || lastUtilitySortOrder != preferences.sortOrder()) {
            lastUtilitySortMode = preferences.sortMode();
            lastUtilitySortOrder = preferences.sortOrder();
            transformTargetPage = 0;
            transformUsePage = 0;
            timedStationPage = 0;
            instantStationPage = 0;
            fuelSearchPage = 0;
            refreshTransformTargets();
            refreshFuelSearchResults();
        }
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
            renderUtilityPage(graphics, page, mouseX, mouseY);
        } else {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            renderRecipePanel(graphics, partialTick, mouseX, mouseY);
        }
        renderSideRail(graphics);
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
            CraftingTerminalPage page,
            int mouseX,
            int mouseY
    ) {
        drawRaisedPanel(graphics, leftPos, topPos, geometry.frame());
        drawInsetPanel(graphics, leftPos, topPos, geometry.playerInventory());
        drawInsetPanel(graphics, leftPos, topPos, geometry.searchBackground());
        if (page == CraftingTerminalPage.TRANSFORM) {
            renderConsumablesPanel(graphics, mouseX, mouseY);
        } else if (fuelSearchActive) {
            renderFuelSearchResults(graphics);
        } else {
            renderTimedStationsPanel(graphics);
            renderInstantStationsPanel(graphics);
        }
        renderUtilityStatus(graphics);
    }

    private void renderFuelSearchResults(GuiGraphics graphics) {
        TerminalLayout.Rect panel = geometry.fuelSearchPanel();
        TerminalLayout.FlowGrid grid = geometry.fuelSearchGrid();
        drawInsetPanel(graphics, leftPos, topPos, panel);
        Component heading = Component.translatable("gui.auto_storage.fuel_search_results");
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
            Component empty = Component.translatable("gui.auto_storage.fuel_search_empty");
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
            if (!installed.isEmpty()) {
                ItemStack icon = installed.copyWithCount(1);
                graphics.renderItem(icon, leftPos + slot.x(), topPos + slot.y());
                if (descriptor.category() == MachineCategory.PROCESS) {
                    renderNetworkAmount(
                            graphics,
                            leftPos + slot.x(),
                            topPos + slot.y(),
                            installed.getCount());
                }
            } else {
                renderDimmedItem(graphics, descriptor.representativeStack(), slot);
            }
            if (descriptor.category() == MachineCategory.PROCESS) {
                drawFlowAmount(
                        graphics,
                        cell,
                        formatAmount(machineStoredAmount(descriptor)));
            }
        }
    }

    private void renderConsumablesPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        drawInsetPanel(graphics, leftPos, topPos, geometry.transformPanel());
        renderTransformTargetList(graphics);
        TerminalLayout.Rect input = geometry.transformInput();
        drawSlotFrame(graphics, leftPos + input.x(), topPos + input.y());
        ItemStack inputStack = menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem();
        if (!inputStack.isEmpty()) {
            ItemStack icon = inputStack.copyWithCount(1);
            graphics.renderItem(icon, leftPos + input.x(), topPos + input.y());
            graphics.renderItemDecorations(
                    font,
                    icon,
                    leftPos + input.x(),
                    topPos + input.y(),
                    formatAmount(inputStack.getCount()));
        }
        Component inputLabel = inputStack.isEmpty()
                ? Component.translatable("gui.auto_storage.fuel_input")
                : inputStack.getHoverName();
        int labelRight = geometry.transformAmountButtons().getFirst().x() - 3;
        if (labelRight > input.right()) {
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(
                            inputLabel.getString(), labelRight - input.right() - 3),
                    leftPos + input.right() + 3,
                    topPos + input.y() + (input.height() - font.lineHeight) / 2,
                    0xFF404040,
                    false);
        }
        if (inputStack.isEmpty()) {
            TerminalLayout.Rect cards = geometry.transformCards().bounds();
            Component prompt = Component.translatable(
                    "gui.auto_storage.transform_insert_item");
            String visible = font.plainSubstrByWidth(
                    prompt.getString(), Math.max(1, cards.width() - 8));
            graphics.drawString(
                    font,
                    visible,
                    leftPos + cards.x() + (cards.width() - font.width(visible)) / 2,
                    topPos + cards.y() + 4,
                    0xFF606060,
                    false);
        }
        renderTransformCards(graphics, mouseX, mouseY);
    }

    private void renderTransformTargetList(GuiGraphics graphics) {
        TerminalLayout.PagedList list = geometry.transformTargetList();
        List<FuelTargetOption> options = filteredTransformTargets();
        List<TerminalLayout.Rect> rows = list.rows(transformTargetPage, options.size());
        int first = transformTargetPage * list.capacity();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            FuelTargetOption option = options.get(first + rowIndex);
            TerminalLayout.Rect row = rows.get(rowIndex);
            boolean selected = Objects.equals(
                    option.target(), displayedPreferences().transformTarget());
            if (selected) {
                drawInsetPanel(graphics, leftPos, topPos, row);
            } else {
                drawRaisedPanel(graphics, leftPos, topPos, row);
            }
            graphics.renderItem(
                    option.icon(),
                    leftPos + row.x() + 2,
                    topPos + row.y() + (row.height() - 16) / 2);
            String label = font.plainSubstrByWidth(
                    option.label().getString(), Math.max(1, row.width() - 22));
            graphics.drawString(
                    font,
                    label,
                    leftPos + row.x() + 21,
                    topPos + row.y() + (row.height() - font.lineHeight) / 2,
                    selected ? 0xFF163C54 : 0xFF404040,
                    false);
        }
        drawFlowPageIndicator(
                graphics,
                new TerminalLayout.Rect(
                        list.bounds().x(),
                        geometry.transformTargetPageControls().previous().y(),
                        list.bounds().width(),
                        TerminalLayout.CONTROL_SIZE),
                transformTargetPage,
                transformTargetPageCount(),
                geometry.transformTargetPageControls().previous().y()
                        + (TerminalLayout.CONTROL_SIZE - font.lineHeight) / 2);
    }

    private void renderTransformCards(GuiGraphics graphics, int mouseX, int mouseY) {
        List<TransformProviderApi.Use> uses = menu.getVisibleTransformUses();
        TransformProviderApi.Use selected = menu.getSelectedTransformUse();
        TerminalLayout.FlowGrid grid = geometry.transformCards();
        int first = transformUsePage * grid.capacity();
        List<TerminalLayout.Rect> cells = grid.cells(transformUsePage, uses.size());
        ItemStack input = menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem();
        for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
            TransformProviderApi.Use use = uses.get(first + visibleIndex);
            TerminalLayout.Rect cell = cells.get(visibleIndex);
            if (selected != null && selected.id().equals(use.id())) {
                drawInsetPanel(graphics, leftPos, topPos, cell);
            } else {
                drawRaisedPanel(graphics, leftPos, topPos, cell);
                if (cell.contains(mouseX - leftPos, mouseY - topPos)) {
                    graphics.fill(
                            leftPos + cell.x() + 1,
                            topPos + cell.y() + 1,
                            leftPos + cell.right() - 1,
                            topPos + cell.bottom() - 1,
                            0x28FFFFFF);
                }
            }
            if (!input.isEmpty()) {
                graphics.renderItem(input.copyWithCount(1),
                        leftPos + cell.x() + 3,
                        topPos + cell.y() + (cell.height() - 16) / 2);
            }
            graphics.drawString(
                    font,
                    "→",
                    leftPos + cell.x() + 22,
                    topPos + cell.y() + (cell.height() - font.lineHeight) / 2,
                    0xFF606060,
                    false);
            graphics.renderItem(
                    use.representative(),
                    leftPos + cell.x() + 33,
                    topPos + cell.y() + (cell.height() - 16) / 2);
            Component label = fuelTargetOption(use.targetId()).label();
            String text = label.getString() + "  "
                    + (use.infinite() ? "∞" : formatAmount(use.amountPerItem()));
            Component source = transformSource(use);
            int textY = source.getString().isEmpty()
                    ? cell.y() + (cell.height() - font.lineHeight) / 2
                    : cell.y() + 4;
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(text, Math.max(1, cell.width() - 54)),
                    leftPos + cell.x() + 52,
                    topPos + textY,
                    0xFF404040,
                    false);
            if (!source.getString().isEmpty()) {
                graphics.drawString(
                        font,
                        font.plainSubstrByWidth(
                                source.getString(), Math.max(1, cell.width() - 54)),
                        leftPos + cell.x() + 52,
                        topPos + cell.bottom() - font.lineHeight - 4,
                        0xFF606060,
                        false);
            }
        }
        if (!input.isEmpty() && uses.isEmpty()) {
            Component empty = Component.translatable("gui.auto_storage.no_transformations");
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(empty.getString(), grid.bounds().width() - 8),
                    leftPos + grid.bounds().x() + 4,
                    topPos + grid.bounds().y() + 4,
                    0xFF606060,
                    false);
        }
    }

    private Component transformSource(TransformProviderApi.Use use) {
        if (use.stationId() == null) {
            return Component.empty();
        }
        Component station = TransformProviderApi.sourceLabel(use.id())
                .orElseGet(() -> Component.literal(use.stationId().toString()));
        return use.stationWorkPerItem() > 0
                ? Component.translatable(
                        "gui.auto_storage.transform_station_work",
                        station,
                        formatAmount(use.stationWorkPerItem()))
                : station;
    }

    private void renderTimedStationsPanel(GuiGraphics graphics) {
        TerminalLayout.Rect panel = geometry.timedStationsPanel();
        TerminalLayout.FlowGrid grid = geometry.timedStationsGrid();
        renderFuelCategoryHeading(
                graphics,
                panel,
                grid,
                geometry.timedStationsPageControls(),
                Component.translatable("gui.auto_storage.fuel_group.timed_stations"),
                timedStationPage,
                grid.pageCount());
        renderMachineCategoryCells(
                graphics,
                MachineCategory.PROCESS,
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
                Component.translatable("gui.auto_storage.fuel_group.instant_stations"),
                instantStationPage,
                grid.pageCount());
        renderMachineCategoryCells(
                graphics,
                MachineCategory.INSTANT,
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
            MachineCategory category,
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
            ItemStack installed = menu.getSlot(
                    CraftingTerminalMenu.MACHINE_SLOT_START + machineSlot).getItem();
            if (!installed.isEmpty()) {
                ItemStack icon = installed.copyWithCount(1);
                graphics.renderItem(icon, leftPos + slot.x(), topPos + slot.y());
                if (category == MachineCategory.PROCESS) {
                    renderNetworkAmount(
                            graphics,
                            leftPos + slot.x(),
                            topPos + slot.y(),
                            installed.getCount());
                }
            } else if (entry != null) {
                renderDimmedItem(graphics, entry.representativeStack(), slot);
            }
            if (entry != null) {
                if (category == MachineCategory.PROCESS) {
                    drawFlowAmount(
                            graphics,
                            cell,
                            formatAmount(machineStoredAmount(entry)));
                }
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
        if (slot.index == CraftingTerminalMenu.FUEL_INPUT_SLOT
                || slot.index >= CraftingTerminalMenu.MACHINE_SLOT_START
                && slot.index < CraftingTerminalMenu.MACHINE_SLOT_START
                + CraftingTerminalMenu.MACHINE_SLOT_COUNT) {
            return;
        }
        super.renderSlotContents(graphics, stack, slot, countString);
    }

    private void renderDimmedItem(
            GuiGraphics graphics,
            ItemStack stack,
            TerminalLayout.Rect bounds
    ) {
        graphics.renderItem(stack, leftPos + bounds.x(), topPos + bounds.y());
        graphics.fill(
                leftPos + bounds.x(),
                topPos + bounds.y(),
                leftPos + bounds.right(),
                topPos + bounds.bottom(),
                0xA05A6870);
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

    private void renderUtilityStatus(GuiGraphics graphics) {
        TerminalLayout.Rect status = geometry.fuelStatus();
        drawRaisedPanel(graphics, leftPos, topPos, status);
        int capacityHeight = Math.max(
                TerminalLayout.CONTROL_SIZE,
                status.height() - TerminalLayout.CONTROL_SIZE - 2);
        Component label = menu.hasUnlimitedTypeCapacity()
                ? Component.translatable(
                        "gui.auto_storage.type_capacity_unlimited",
                        formatAmount(menu.getTypeCount()))
                : Component.translatable(
                        "gui.auto_storage.type_capacity",
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
                    ? AutoStorage.CREATIVE_STORAGE_UNIT_ITEM.get().getDefaultInstance()
                    : AutoStorage.STORAGE_UNIT_T1_ITEM.get().getDefaultInstance();
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
        TerminalLayout.Rect diagram = geometry.recipeDiagram();
        TerminalLayout.Rect ledger = geometry.recipeLedger();
        TerminalLayout.Rect footer = geometry.recipeFooter();
        TerminalLayout.Rect content = geometry.recipeContent();
        drawRaisedPanel(graphics, leftPos, topPos, content);
        drawRaisedPanel(graphics, leftPos, topPos, footer);

        RecipePresentation presentation = menu.getRecipePresentation();
        if (presentation.isEmpty()) {
            Component prompt = Component.translatable(menu.getSelectedStack().isEmpty()
                    ? "gui.auto_storage.select_recipe_item"
                    : "gui.auto_storage.no_recipe");
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

        List<RecipePresentation.Resource> resources = visibleRecipeResources(presentation);
        List<TerminalLayout.Rect> cells = geometry.recipeLedgerCells(resources.size());
        for (int index = 0; index < resources.size(); index++) {
            renderResourceRow(graphics, cells.get(index), resources.get(index));
        }
        int maxOffset = recipeLedgerMaxOffset(presentation);
        if (maxOffset > 0) {
            TerminalLayout.Rect scrollbar = new TerminalLayout.Rect(
                    ledger.right() - 14, ledger.y(), 14, ledger.height());
            drawInsetPanel(graphics, leftPos, topPos, scrollbar);
            drawScrollbar(
                    graphics,
                    leftPos + scrollbar.x() + 1,
                    topPos + scrollbar.y() + 1,
                    scrollbar.height() - 2,
                    recipeLedgerOffset,
                    maxOffset,
                    maxOffset > 0
                            ? TerminalScrollbar.VisualState.ENABLED
                            : TerminalScrollbar.VisualState.DISABLED);
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
        renderTypedResourceBackground(graphics, icon, x + 1, iconY);
        renderTerminalIcon(graphics, icon, x + 1, iconY, 0.0F);
        RecipeResourceAmountFormatter amount = RecipeResourceAmountFormatter.format(
                resource.available(), resource.required(), resource.infinite());
        int textX = x + 18;
        int availableTextWidth = Math.max(0, cell.width() - 20);
        int color = resource.infinite() || resource.available() >= resource.required()
                ? 0xFF176B2C : 0xFFFF7777;
        if (font.width(amount.inline()) <= availableTextWidth) {
            int textY = y + Math.max(0, (cell.height() - font.lineHeight) / 2);
            graphics.drawString(font, amount.inline(), textX, textY, color, false);
            return;
        }
        int textY = y + Math.max(0, (cell.height() - font.lineHeight * 2) / 2);
        graphics.drawString(font, amount.available(), textX, textY, color, false);
        graphics.drawString(font, amount.required(), textX, textY + font.lineHeight, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        graphics.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, 0xFF404040, false);
        renderRecipePosition(graphics);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!displayedPreferences().page().isItemPage()) {
            renderFuelTooltip(graphics, mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
        RecipePresentation presentation = menu.getRecipePresentation();
        if (presentation.isEmpty()) return;
        TerminalLayout.Rect station = geometry.recipeStation();
        if (station.contains(mouseX - leftPos, mouseY - topPos)) {
            graphics.renderTooltip(font, Component.translatable(
                    "gui.auto_storage.recipe_station",
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
                    Component.translatable("gui.auto_storage.available_amount",
                            formatAmount(resource.available())),
                    Component.translatable("gui.auto_storage.required_for_one",
                            formatAmount(resource.required()))
            ), mouseX, mouseY);
        }
    }

    private boolean renderFuelTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        CraftingTerminalPage page = displayedPreferences().page();
        if (page == CraftingTerminalPage.TRANSFORM) {
            TerminalLayout.Rect input = geometry.transformInput();
            if (menu.getSlot(CraftingTerminalMenu.FUEL_INPUT_SLOT).getItem().isEmpty()
                    && input.contains(mouseX - leftPos, mouseY - topPos)) {
                graphics.renderTooltip(
                        font,
                        Component.translatable("gui.auto_storage.fuel_input"),
                        mouseX,
                        mouseY);
                return true;
            }
            return false;
        }
        if (page != CraftingTerminalPage.STATIONS) return false;
        if (fuelSearchActive) {
            return renderStationGridTooltip(
                    graphics,
                    mouseX,
                    mouseY,
                    filteredFuelEntries.stream()
                            .map(FuelSearchModel.Entry::machineSlot)
                            .toList(),
                    geometry.fuelSearchGrid(),
                    fuelSearchPage);
        }
        return renderStationGridTooltip(
                graphics,
                mouseX,
                mouseY,
                machineSlotsForCategory(MachineCategory.PROCESS),
                geometry.timedStationsGrid(),
                timedStationPage)
                || renderStationGridTooltip(
                        graphics,
                        mouseX,
                        mouseY,
                        machineSlotsForCategory(MachineCategory.INSTANT),
                        geometry.instantStationsGrid(),
                        instantStationPage);
    }

    private boolean renderStationGridTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            List<Integer> machineSlots,
            TerminalLayout.FlowGrid grid,
            int page
    ) {
        int first = page * grid.capacity();
        List<TerminalLayout.Rect> cells = grid.cells(page, machineSlots.size());
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
            int machineSlot = machineSlots.get(first + visibleIndex);
            MachineDescriptor descriptor = descriptorAt(machineSlot);
            if (descriptor == null) continue;
            TerminalLayout.Rect cell = cells.get(visibleIndex);
            ItemStack installed = menu.getSlot(
                    CraftingTerminalMenu.MACHINE_SLOT_START + machineSlot).getItem();
            ItemStack displayStack = installed.isEmpty()
                    ? descriptor.representativeStack()
                    : installed.copyWithCount(1);
            if (TerminalLayout.fuelSlot(cell).contains(localX, localY)) {
                graphics.renderTooltip(font, displayStack, mouseX, mouseY);
                return true;
            }
            if (descriptor.category() != MachineCategory.PROCESS
                    || !TerminalLayout.fuelAmountBounds(cell).contains(localX, localY)) {
                continue;
            }
            Component valueName = descriptor.energyType() == null
                    ? descriptor.stationLabel()
                    : energyLabel(descriptor.energyType());
            MachineWorkRate rate = installed.isEmpty()
                    ? MachineWorkRate.ZERO
                    : descriptor.rateFor(installed).orElse(MachineWorkRate.ZERO);
            graphics.renderComponentTooltip(font, List.of(
                    valueName,
                    Component.translatable(
                            "tooltip.auto_storage.machine_rate",
                            MachineRateFormatter.format(rate, installed.getCount()))
            ), mouseX, mouseY);
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

    private int transformUseIndexAt(int mouseX, int mouseY) {
        List<TransformProviderApi.Use> uses = menu.getVisibleTransformUses();
        TerminalLayout.FlowGrid grid = geometry.transformCards();
        int first = transformUsePage * grid.capacity();
        List<TerminalLayout.Rect> cells = grid.cells(
                transformUsePage, uses.size());
        for (int visibleIndex = 0; visibleIndex < cells.size(); visibleIndex++) {
            TerminalLayout.Rect cell = cells.get(visibleIndex);
            if (cell.contains(mouseX - leftPos, mouseY - topPos)) return first + visibleIndex;
        }
        return -1;
    }

    private RecipePresentation.Resource recipeResourceAt(
            RecipePresentation presentation,
            int mouseX,
            int mouseY
    ) {
        List<RecipePresentation.Resource> resources = visibleRecipeResources(presentation);
        List<TerminalLayout.Rect> cells = geometry.recipeLedgerCells(resources.size());
        for (int index = 0; index < resources.size(); index++) {
            TerminalLayout.Rect cell = cells.get(index);
            if (cell.contains(mouseX - leftPos, mouseY - topPos)) return resources.get(index);
        }
        return null;
    }

    private List<RecipePresentation.Resource> visibleRecipeResources(
            RecipePresentation presentation
    ) {
        if (!Objects.equals(recipeLedgerRecipeId, presentation.recipeId())) {
            recipeLedgerRecipeId = presentation.recipeId();
            recipeLedgerOffset = 0;
        }
        List<RecipePresentation.Resource> resources = presentation.resources();
        recipeLedgerOffset = Math.clamp(recipeLedgerOffset, 0, recipeLedgerMaxOffset(presentation));
        int end = Math.min(resources.size(), recipeLedgerOffset + geometry.recipeLedgerCapacity());
        return resources.subList(recipeLedgerOffset, end);
    }

    private int recipeLedgerMaxOffset(RecipePresentation presentation) {
        int overflow = presentation.resources().size() - geometry.recipeLedgerCapacity();
        if (overflow <= 0) return 0;
        int columns = geometry.recipeLedgerColumns();
        return (overflow + columns - 1) / columns * columns;
    }

    private MachineDescriptor descriptorAt(int slot) {
        List<MachineDescriptor> descriptors = menu.getMachineDescriptors();
        return slot >= 0 && slot < descriptors.size() ? descriptors.get(slot) : null;
    }

    private List<Integer> machineSlotsForCategory(MachineCategory category) {
        List<Integer> result = new ArrayList<>();
        List<MachineDescriptor> entries = menu.getMachineDescriptors();
        for (int slot = 0; slot < entries.size(); slot++) {
            if (entries.get(slot).category() == category
                    && stationDisplayMode.shows(isStationInstalled(slot))) {
                result.add(slot);
            }
        }
        result.sort(TerminalEntryComparator.forMode(
                displayedPreferences().sortMode(),
                displayedPreferences().sortOrder(),
                this::machineSortStack));
        return List.copyOf(result);
    }

    private boolean isStationInstalled(int machineSlot) {
        return machineSlot >= 0
                && machineSlot < menu.getMachineDescriptors().size()
                && menu.getSlot(CraftingTerminalMenu.MACHINE_SLOT_START + machineSlot)
                .hasItem();
    }

    private void setStationDisplayMode(StationDisplayMode mode) {
        if (stationDisplayMode == mode) return;
        stationDisplayMode = mode;
        timedStationPage = 0;
        instantStationPage = 0;
        fuelSearchPage = 0;
        rebuildWidgets();
    }

    private ItemStack machineSortStack(int machineSlot) {
        MachineDescriptor descriptor = descriptorAt(machineSlot);
        if (descriptor == null) return ItemStack.EMPTY;
        ItemStack stack = descriptor.representativeStack();
        stack.set(DataComponents.CUSTOM_NAME, descriptor.stationLabel());
        return TerminalDisplayStack.create(
                stack,
                menu.getSlot(CraftingTerminalMenu.MACHINE_SLOT_START + machineSlot)
                        .getItem().getCount());
    }

    private List<FuelTargetOption> fuelTargetOptions() {
        List<FuelTargetOption> options = new ArrayList<>();
        options.add(new FuelTargetOption(
                null,
                Items.COMPARATOR.getDefaultInstance(),
                Component.translatable("gui.auto_storage.fuel_target_auto")));
        for (TransformProviderApi.Target target : menu.getTransformTargets()) {
            options.add(new FuelTargetOption(
                    target.id(),
                    target.representative(),
                    target.label()));
        }
        return List.copyOf(options);
    }

    private List<FuelTargetOption> filteredTransformTargets() {
        return filteredTransformTargets;
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

    private FuelTargetOption transformTargetAt(double mouseX, double mouseY) {
        List<FuelTargetOption> options = filteredTransformTargets();
        TerminalLayout.PagedList list = geometry.transformTargetList();
        int first = transformTargetPage * list.capacity();
        List<TerminalLayout.Rect> rows = list.rows(transformTargetPage, options.size());
        for (int row = 0; row < rows.size(); row++) {
            if (rows.get(row).contains(
                    (int) mouseX - leftPos,
                    (int) mouseY - topPos)) {
                return options.get(first + row);
            }
        }
        return null;
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

    private static Component energyLabel(EnergyType type) {
        return Component.translatable("gui.auto_storage.energy." + type.getId());
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

        private TerminalSearchEntry searchEntry() {
            ResourceLocation identity = target == null
                    ? ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "auto")
                    : target;
            return TerminalSearchEntry.create(iconStack, identity, labelText);
        }

        private ItemStack sortStack() {
            ItemStack stack = iconStack.copy();
            stack.set(DataComponents.CUSTOM_NAME, labelText);
            return TerminalDisplayStack.create(stack, 0);
        }
    }

    private record FuelPageButtons(TerminalIconButton previous, TerminalIconButton next) {
    }

    private enum StationDisplayMode {
        ALL("gui.auto_storage.station_display.all"),
        INSTALLED("gui.auto_storage.station_display.installed");

        private final String translationKey;

        StationDisplayMode(String translationKey) {
            this.translationKey = translationKey;
        }

        private boolean shows(boolean installed) {
            return this == ALL || installed;
        }

        private StationDisplayMode next() {
            return this == ALL ? INSTALLED : ALL;
        }

        private String translationKey() {
            return translationKey;
        }
    }

    private void renderRecipePosition(GuiGraphics graphics) {
        if (!displayedPreferences().page().isItemPage()) return;
        int recipeCount = menu.getRecipeCount();
        if (recipeCount <= 0) return;
        int craftableRecipeCount = menu.getCraftableRecipeCount();
        String recipePosition = (Math.clamp(
                menu.getCurrentRecipeIndex(), 0, recipeCount - 1) + 1)
                + " / " + craftableRecipeCount + " (" + recipeCount + ")";
        graphics.drawString(
                font,
                recipePosition,
                imageWidth - 8 - font.width(recipePosition),
                titleLabelY,
                0xFF606060,
                false);
    }

    public List<Rect2i> getEmiExclusionAreas() {
        return List.copyOf(terminalExclusionAreas());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        unfocusSearchOnOutsideClick(mouseX, mouseY);
        if (displayedPreferences().page() == CraftingTerminalPage.TRANSFORM) {
            FuelTargetOption target = transformTargetAt(mouseX, mouseY);
            if (button == 2 && (target != null
                    || transformTargetPageButtons.previous().isMouseOver(mouseX, mouseY)
                    || transformTargetPageButtons.next().isMouseOver(mouseX, mouseY))) {
                transformTargetSearchBox.setValue("");
                transformTargetPage = 0;
                clickMenuButton(CraftingTerminalMenu.AUTO_FUEL_TARGET_BUTTON);
                updateFuelPageButtonStates();
                return true;
            }
            if (button == 0 && target != null) {
                selectFuelTarget(target);
                return true;
            }
            int useIndex = transformUseIndexAt((int) mouseX, (int) mouseY);
            if (button == 0 && useIndex >= 0) {
                clickMenuButton(CraftingTerminalMenu.transformUseButtonId(useIndex));
                return true;
            }
            if (button == 2 && (transformCardPageButtons.previous().isMouseOver(mouseX, mouseY)
                    || transformCardPageButtons.next().isMouseOver(mouseX, mouseY))) {
                transformUsePage = 0;
                updateFuelPageButtonStates();
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
    protected EditBox activeSearchBox() {
        return switch (displayedPreferences().page()) {
            case TRANSFORM -> transformTargetSearchBox;
            case STATIONS -> fuelSearchBox;
            default -> super.activeSearchBox();
        };
    }

    @Override
    protected boolean keyPressedOutsideSearch(int keyCode, int scanCode, int modifiers) {
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
        return super.keyPressedOutsideSearch(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (displayedPreferences().page().isItemPage() && scrollY != 0
                && geometry.recipeLedger().contains(
                (int) mouseX - leftPos, (int) mouseY - topPos)) {
            RecipePresentation presentation = menu.getRecipePresentation();
            int maxOffset = presentation.isEmpty() ? 0 : recipeLedgerMaxOffset(presentation);
            if (maxOffset > 0) {
                int direction = scrollY < 0 ? 1 : -1;
                recipeLedgerOffset = Math.clamp(
                        recipeLedgerOffset + direction * geometry.recipeLedgerColumns(),
                        0,
                        maxOffset);
            }
            return true;
        }
        if (displayedPreferences().page() == CraftingTerminalPage.TRANSFORM && scrollY != 0) {
            int localX = (int) mouseX - leftPos;
            int localY = (int) mouseY - topPos;
            int direction = scrollY < 0 ? 1 : -1;
            if (geometry.transformTargetList().bounds().contains(localX, localY)) {
                transformTargetPage = Math.clamp(
                        transformTargetPage + direction,
                        0,
                        transformTargetPageCount() - 1);
                updateFuelPageButtonStates();
                return true;
            }
            if (geometry.transformCards().bounds().contains(localX, localY)) {
                transformUsePage = Math.clamp(
                        transformUsePage + direction,
                        0,
                        transformPageCount() - 1);
                updateFuelPageButtonStates();
                return true;
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
