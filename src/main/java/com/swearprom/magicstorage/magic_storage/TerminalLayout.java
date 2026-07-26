package com.swearprom.magicstorage.magic_storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class TerminalLayout {
    static final int SLOT_SIZE = 18;
    static final int CONTROL_SIZE = SLOT_SIZE;
    static final int ICON_CANVAS_SIZE = 16;
    static final int TOP_HEIGHT = 19;
    static final int MIN_STORAGE_ROWS = 3;
    static final int MIN_CRAFTING_ROWS = 1;
    static final int MAX_ROWS = 9;
    static final int RAIL_GROUP_GAP = 6;

    private static final int NARROW_WIDTH = 210;
    private static final int SIDE_BY_SIDE_MIN_WIDTH = 367;
    private static final int SIDE_BY_SIDE_MAX_WIDTH = 390;
    private static final int SIDE_BY_SIDE_WORKSPACE_X = 187;
    private static final int SIDE_BY_SIDE_RIGHT_MARGIN = 4;
    private static final int STORAGE_BOTTOM_HEIGHT = 99;
    private static final int PLAYER_INVENTORY_HEIGHT = 4 * SLOT_SIZE + 4;
    private static final int OUTER_MARGIN = 16;
    private static final int PREFERRED_VERTICAL_MARGIN = 24;
    private static final int NARROW_MIN_VERTICAL_MARGIN = 4;
    private static final int NARROW_WORKSPACE_HEIGHT = 104;
    private static final int WORKSPACE_PLAYER_GAP = 2;
    private static final int INVENTORY_LABEL_BAND_HEIGHT = 13;
    private static final int FUEL_PANEL_INSET = 2;
    private static final int FUEL_TARGET_BAR_HEIGHT = CONTROL_SIZE;
    private static final int FUEL_TARGET_BAR_GAP = 1;
    private static final int FUEL_CATEGORY_LABEL_WIDTH = 64;
    private static final int FUEL_CATEGORY_HEADER_GAP = 1;
    private static final int FUEL_CATEGORY_CELL_HEIGHT = 20;
    private static final int FUEL_CONSUMABLE_PANEL_HEIGHT = FUEL_PANEL_INSET * 2
            + FUEL_TARGET_BAR_HEIGHT + FUEL_TARGET_BAR_GAP + FUEL_CATEGORY_CELL_HEIGHT;
    private static final int FUEL_STATION_PANEL_HEIGHT = FUEL_PANEL_INSET * 2
            + FUEL_CATEGORY_CELL_HEIGHT;
    private static final int FUEL_STATION_TALL_PANEL_HEIGHT = FUEL_PANEL_INSET * 2
            + CONTROL_SIZE + FUEL_CATEGORY_HEADER_GAP + FUEL_CATEGORY_CELL_HEIGHT;
    private static final int FUEL_CATEGORY_GAP = 2;
    private static final int FUEL_AMOUNT_HEIGHT = 9;
    private static final int RAIL_BUTTON_SIZE = CONTROL_SIZE;
    private static final int RAIL_SPACING = 2;
    private static final int RAIL_TOP = 6;
    private static final int RAIL_FRAME_GAP = 1;
    private static final int CATEGORY_CELL_MIN_WIDTH = SLOT_SIZE * 2;
    private static final int CATEGORY_CELL_PREFERRED_WIDTH = SLOT_SIZE * 4;
    private static final int RECIPE_INSET = 4;
    private static final int CONTROL_GAP = 2;
    private static final int RECIPE_DIAGRAM_MIN_HEIGHT = SLOT_SIZE * 3;
    private static final int RECIPE_DIAGRAM_MAX_HEIGHT = SLOT_SIZE * 4;
    private static final int RECIPE_LEDGER_MIN_ROW_HEIGHT = 16;
    private static final int RECIPE_LEDGER_MAX_COLUMNS = 4;
    private static final int RECIPE_LEDGER_MIN_CELL_WIDTH = 56;
    private static final int RECIPE_LEDGER_MAX_HEIGHT = SLOT_SIZE * 3;
    private static final int RECIPE_BODY_MAX_HEIGHT = RECIPE_DIAGRAM_MAX_HEIGHT
            + RECIPE_LEDGER_MAX_HEIGHT;
    private static final int RECIPE_BODY_FOOTER_GAP = 4;
    private static final int MAX_RECIPE_LEDGER_ROWS = RecipePresentation.MAX_ITEM_RESOURCES + 3;
    private static final int TRANSFORM_TARGET_ROW_HEIGHT = 20;
    private static final int TRANSFORM_TARGET_COLUMN_MIN_WIDTH = 96;
    private static final int TRANSFORM_TARGET_COLUMN_MAX_WIDTH = 116;
    private static final int TRANSFORM_CARD_HEIGHT = 32;
    private static final int PROCESSING_STATION_MAX_COLUMNS = 3;
    private static final int PROCESSING_STATION_CELL_WIDTH = 68;
    private static final int INSTANT_STATION_CELL_SIZE = 20;

    record Rect(int x, int y, int width, int height) {
        Rect {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Rectangle dimensions must be non-negative");
            }
        }

        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(int pointX, int pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }

        boolean overlaps(Rect other) {
            return x < other.right() && right() > other.x
                    && y < other.bottom() && bottom() > other.y;
        }
    }

    record FuelDescriptorCounts(
            int consumableCount,
            int timedStationCount,
            int instantStationCount,
            int fuelTargetCount
    ) {
        FuelDescriptorCounts {
            if (consumableCount < 0 || timedStationCount < 0
                    || instantStationCount < 0 || fuelTargetCount < 0) {
                throw new IllegalArgumentException("Fuel descriptor counts must be non-negative");
            }
        }

        static FuelDescriptorCounts none() {
            return new FuelDescriptorCounts(0, 0, 0, 0);
        }

        boolean isEmpty() {
            return consumableCount == 0 && timedStationCount == 0
                    && instantStationCount == 0 && fuelTargetCount == 0;
        }
    }

    record FlowGrid(
            Rect bounds,
            List<Rect> cells,
            int columns,
            int rows,
            int preferredCellWidth,
            int capacity,
            int itemCount,
            boolean compact
    ) {
        FlowGrid {
            cells = List.copyOf(cells);
        }

        int pageCount() {
            return pageCount(itemCount);
        }

        int pageCount(int dynamicItemCount) {
            return Math.max(1, (dynamicItemCount + capacity - 1) / capacity);
        }

        int visibleCount(int page) {
            int first = Math.clamp(page, 0, pageCount() - 1) * capacity;
            return Math.max(0, Math.min(capacity, itemCount - first));
        }

        List<Rect> cells(int page) {
            return flowCells(
                    bounds, visibleCount(page), columns, rows, preferredCellWidth, compact);
        }

        List<Rect> cells(int page, int dynamicItemCount) {
            int first = Math.clamp(page, 0, pageCount(dynamicItemCount) - 1) * capacity;
            int visible = Math.max(0, Math.min(capacity, dynamicItemCount - first));
            return flowCells(
                    bounds, visible, columns, rows, preferredCellWidth, compact);
        }
    }

    record FuelPageControls(Rect previous, Rect next) {
    }

    record PagedList(
            Rect bounds,
            int rowHeight,
            int capacity,
            int itemCount
    ) {
        PagedList {
            if (rowHeight <= 0 || capacity <= 0 || itemCount < 0) {
                throw new IllegalArgumentException("Paged list dimensions are out of bounds");
            }
        }

        int pageCount() {
            return pageCount(itemCount);
        }

        int pageCount(int dynamicItemCount) {
            return Math.max(1, (dynamicItemCount + capacity - 1) / capacity);
        }

        int firstIndex(int page) {
            return Math.clamp(page, 0, pageCount() - 1) * capacity;
        }

        List<Rect> rows(int page) {
            return rows(page, itemCount);
        }

        List<Rect> rows(int page, int dynamicItemCount) {
            int first = Math.clamp(page, 0, pageCount(dynamicItemCount) - 1) * capacity;
            int visible = Math.max(0, Math.min(capacity, dynamicItemCount - first));
            List<Rect> result = new ArrayList<>(visible);
            for (int row = 0; row < visible; row++) {
                result.add(new Rect(
                        bounds.x(),
                        bounds.y() + row * rowHeight,
                        bounds.width(),
                        rowHeight));
            }
            return List.copyOf(result);
        }
    }

    record Geometry(
            TerminalProfile profile,
            boolean wide,
            int imageWidth,
            int imageHeight,
            int visibleRows,
            Rect itemGrid,
            Rect searchBox,
            Rect searchBackground,
            Rect scrollbar,
            Rect playerInventory,
            Rect workspace,
            Rect recipeDiagram,
            Rect recipeLedger,
            Rect recipeFooter,
            List<Rect> recipeInputSlots,
            Rect recipeArrow,
            Rect recipeOutput,
            Rect recipeStation,
            Rect recipeShapelessMarker,
            List<Rect> recipeNavigationButtons,
            List<Rect> recipeCraftButtons,
            Rect transformPanel,
            Rect timedStationsPanel,
            Rect instantStationsPanel,
            Rect transformTargetSearch,
            PagedList transformTargetList,
            FuelPageControls transformTargetPageControls,
            Rect transformInput,
            List<Rect> transformAmountButtons,
            Rect fuelStatus,
            Rect fuelSearchButton,
            Rect fuelSearchBox,
            Rect fuelSearchPanel,
            FlowGrid transformCards,
            FlowGrid timedStationsGrid,
            FlowGrid instantStationsGrid,
            FlowGrid fuelSearchGrid,
            FuelPageControls transformCardPageControls,
            FuelPageControls timedStationsPageControls,
            FuelPageControls instantStationsPageControls,
            FuelPageControls fuelSearchPageControls,
            List<Rect> railButtons,
            List<Rect> fuelRailButtons,
            Rect railBounds,
            Rect railPanel,
            Rect fuelRailPanel,
            List<Rect> exclusionRects
    ) {
        Geometry {
            recipeInputSlots = List.copyOf(recipeInputSlots);
            recipeNavigationButtons = List.copyOf(recipeNavigationButtons);
            recipeCraftButtons = List.copyOf(recipeCraftButtons);
            transformAmountButtons = List.copyOf(transformAmountButtons);
            railButtons = List.copyOf(railButtons);
            fuelRailButtons = List.copyOf(fuelRailButtons);
            exclusionRects = List.copyOf(exclusionRects);
        }

        List<Rect> recipeLedgerCells(int resourceCount) {
            return TerminalLayout.recipeLedgerCells(recipeLedger, resourceCount);
        }

        Rect recipeContent() {
            return new Rect(
                    recipeDiagram.x(),
                    recipeDiagram.y(),
                    recipeDiagram.width(),
                    recipeLedger.bottom() - recipeDiagram.y());
        }

        int centeredFrameLeft(int screenWidth) {
            int groupLeft = Math.min(0, railPanel.x());
            int groupRight = Math.max(imageWidth, railPanel.right());
            return (screenWidth - (groupRight - groupLeft)) / 2 - groupLeft;
        }

        Rect frame() {
            return exclusionRects.getFirst();
        }
    }

    private TerminalLayout() {
    }

    static Geometry forProfile(
            TerminalProfile profile,
            int screenWidth,
            int screenHeight,
            FuelDescriptorCounts fuelDescriptors
    ) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(fuelDescriptors, "fuelDescriptors");
        if (profile.supports(TerminalProfile.Capability.RECIPE_WORKSPACE)) {
            if (fuelDescriptors.fuelTargetCount() <= 0) {
                throw new IllegalArgumentException("Crafting terminals require at least one Fuel target");
            }
            return craftingGeometry(profile, screenWidth, screenHeight, fuelDescriptors);
        }
        if (!fuelDescriptors.isEmpty()) {
            throw new IllegalArgumentException("Reduced terminal profiles cannot contain flow items");
        }
        return storageGeometry(profile, screenHeight);
    }

    private static Geometry storageGeometry(TerminalProfile profile, int screenHeight) {
        int rows = fixedBandRows(screenHeight, STORAGE_BOTTOM_HEIGHT, MIN_STORAGE_ROWS);
        int imageHeight = TOP_HEIGHT + STORAGE_BOTTOM_HEIGHT + rows * SLOT_SIZE;
        Rect itemGrid = new Rect(8, TOP_HEIGHT, 9 * SLOT_SIZE, rows * SLOT_SIZE);
        Rect playerInventory = new Rect(
                8, itemGrid.bottom() + 14, 9 * SLOT_SIZE, PLAYER_INVENTORY_HEIGHT);
        RailGeometry rails = profileRails(profile, imageHeight);
        Rect empty = new Rect(0, 0, 0, 0);
        FlowGrid emptyFlow = emptyFlow();
        FuelPageControls emptyPageControls = new FuelPageControls(empty, empty);
        PagedList emptyList = new PagedList(empty, TRANSFORM_TARGET_ROW_HEIGHT, 1, 0);
        return new Geometry(
                profile,
                false,
                NARROW_WIDTH,
                imageHeight,
                rows,
                itemGrid,
                new Rect(102, 6, 70, 9),
                new Rect(100, 4, 72, 12),
                new Rect(174, TOP_HEIGHT, 12, rows * SLOT_SIZE),
                playerInventory,
                empty,
                empty,
                empty,
                empty,
                List.of(),
                empty,
                empty,
                empty,
                empty,
                List.of(),
                List.of(),
                empty,
                empty,
                empty,
                empty,
                emptyList,
                emptyPageControls,
                empty,
                List.of(),
                empty,
                empty,
                empty,
                empty,
                emptyFlow,
                emptyFlow,
                emptyFlow,
                emptyFlow,
                emptyPageControls,
                emptyPageControls,
                emptyPageControls,
                emptyPageControls,
                rails.itemButtons(),
                rails.fuelButtons(),
                rails.bounds(),
                rails.panel(),
                rails.fuelRailPanel(),
                List.of(new Rect(0, 0, NARROW_WIDTH, imageHeight), rails.panel())
        );
    }

    private static Geometry craftingGeometry(
            TerminalProfile profile,
            int screenWidth,
            int screenHeight,
            FuelDescriptorCounts fuelDescriptors
    ) {
        boolean horizontalFuelHeaders = supportsHorizontalFuelHeaders(screenHeight);
        int fuelGroupHeight = fuelGroupHeight(horizontalFuelHeaders);
        int wideRows = fixedBandRows(screenHeight, STORAGE_BOTTOM_HEIGHT, MIN_STORAGE_ROWS);
        int wideItemBottom = TOP_HEIGHT + wideRows * SLOT_SIZE;
        int widePlayerY = Math.max(
                wideItemBottom + 14,
                TOP_HEIGHT + fuelGroupHeight + INVENTORY_LABEL_BAND_HEIGHT);
        int wideHeight = widePlayerY + PLAYER_INVENTORY_HEIGHT + 8;
        RailGeometry wideRails = profileRails(profile, wideHeight);
        int availableSideBySideWidth = screenWidth - 2 * OUTER_MARGIN
                - railReservedWidth(wideRails.panel());
        boolean wide = availableSideBySideWidth >= SIDE_BY_SIDE_MIN_WIDTH;
        int imageWidth = wide
                ? Math.min(SIDE_BY_SIDE_MAX_WIDTH, availableSideBySideWidth)
                : NARROW_WIDTH;
        int rows = wide ? wideRows : narrowRows(screenHeight);

        Rect itemGrid = new Rect(8, TOP_HEIGHT, 9 * SLOT_SIZE, rows * SLOT_SIZE);
        Rect scrollbar = new Rect(174, TOP_HEIGHT, 12, rows * SLOT_SIZE);
        Rect playerInventory;
        Rect workspace;
        int imageHeight;
        if (wide) {
            int basePlayerY = Math.max(
                    itemGrid.bottom() + 14,
                    TOP_HEIGHT + fuelGroupHeight + INVENTORY_LABEL_BAND_HEIGHT);
            int unusedHeight = Math.max(
                    0,
                    screenHeight - (basePlayerY + PLAYER_INVENTORY_HEIGHT + 8)
                            - 2 * PREFERRED_VERTICAL_MARGIN);
            int playerY = basePlayerY + unusedHeight / 4;
            playerInventory = new Rect(
                    8, playerY, 9 * SLOT_SIZE, PLAYER_INVENTORY_HEIGHT);
            imageHeight = playerInventory.bottom() + 8;
            workspace = new Rect(
                    SIDE_BY_SIDE_WORKSPACE_X,
                    TOP_HEIGHT,
                    imageWidth - SIDE_BY_SIDE_WORKSPACE_X - SIDE_BY_SIDE_RIGHT_MARGIN,
                    imageHeight - TOP_HEIGHT - 8);
        } else {
            int workspaceY = itemGrid.bottom() + 4;
            int playerY = Math.max(
                    workspaceY + NARROW_WORKSPACE_HEIGHT + WORKSPACE_PLAYER_GAP,
                    TOP_HEIGHT + fuelGroupHeight + INVENTORY_LABEL_BAND_HEIGHT);
            playerInventory = new Rect(8, playerY, 9 * SLOT_SIZE, PLAYER_INVENTORY_HEIGHT);
            imageHeight = playerInventory.bottom() + 8;
            workspace = new Rect(8, workspaceY, imageWidth - 16, NARROW_WORKSPACE_HEIGHT);
        }

        RailGeometry rails = profileRails(profile, imageHeight);
        if (wide) {
            int exactAvailableWidth = screenWidth - 2 * OUTER_MARGIN
                    - railReservedWidth(rails.panel());
            if (exactAvailableWidth < SIDE_BY_SIDE_MIN_WIDTH) {
                return craftingNarrow(
                        profile, screenHeight, fuelDescriptors, horizontalFuelHeaders);
            }
            imageWidth = Math.min(SIDE_BY_SIDE_MAX_WIDTH, exactAvailableWidth);
            workspace = new Rect(
                    SIDE_BY_SIDE_WORKSPACE_X,
                    TOP_HEIGHT,
                    imageWidth - SIDE_BY_SIDE_WORKSPACE_X - SIDE_BY_SIDE_RIGHT_MARGIN,
                    imageHeight - TOP_HEIGHT - 8);
        }

        return assembleCraftingGeometry(profile, wide, imageWidth, imageHeight, rows, itemGrid, scrollbar,
                playerInventory, workspace, rails, fuelDescriptors, horizontalFuelHeaders);
    }

    private static Geometry craftingNarrow(
            TerminalProfile profile,
            int screenHeight,
            FuelDescriptorCounts fuelDescriptors,
            boolean horizontalFuelHeaders
    ) {
        int rows = narrowRows(screenHeight);
        Rect itemGrid = new Rect(8, TOP_HEIGHT, 9 * SLOT_SIZE, rows * SLOT_SIZE);
        int workspaceY = itemGrid.bottom() + 4;
        int playerY = Math.max(
                workspaceY + NARROW_WORKSPACE_HEIGHT + WORKSPACE_PLAYER_GAP,
                TOP_HEIGHT + fuelGroupHeight(horizontalFuelHeaders)
                        + INVENTORY_LABEL_BAND_HEIGHT);
        Rect playerInventory = new Rect(8, playerY, 9 * SLOT_SIZE, PLAYER_INVENTORY_HEIGHT);
        int imageHeight = playerInventory.bottom() + 8;
        Rect workspace = new Rect(8, workspaceY, NARROW_WIDTH - 16, NARROW_WORKSPACE_HEIGHT);
        return assembleCraftingGeometry(profile, false, NARROW_WIDTH, imageHeight, rows, itemGrid,
                new Rect(174, TOP_HEIGHT, 12, rows * SLOT_SIZE), playerInventory, workspace,
                profileRails(profile, imageHeight), fuelDescriptors, horizontalFuelHeaders);
    }

    private static Geometry assembleCraftingGeometry(
            TerminalProfile profile,
            boolean wide,
            int imageWidth,
            int imageHeight,
            int rows,
            Rect itemGrid,
            Rect scrollbar,
            Rect playerInventory,
            Rect workspace,
            RailGeometry rails,
            FuelDescriptorCounts fuelDescriptors,
            boolean horizontalFuelHeaders
    ) {
        int fuelAreaBottom = playerInventory.y() - INVENTORY_LABEL_BAND_HEIGHT;
        int availablePanelHeight = fuelAreaBottom - TOP_HEIGHT;
        Rect transformPanel = new Rect(
                8, TOP_HEIGHT, imageWidth - 16, availablePanelHeight);
        int stationAreaWidth = imageWidth - 16 - FUEL_CATEGORY_GAP;
        int timedStationsWidth = stationAreaWidth * 5 / 8;
        Rect timedStationsPanel = new Rect(
                8, TOP_HEIGHT, timedStationsWidth, availablePanelHeight);
        Rect instantStationsPanel = new Rect(
                timedStationsPanel.right() + FUEL_CATEGORY_GAP,
                TOP_HEIGHT,
                stationAreaWidth - timedStationsWidth,
                availablePanelHeight);

        int targetColumnWidth = Math.clamp(
                transformPanel.width() / 3,
                TRANSFORM_TARGET_COLUMN_MIN_WIDTH,
                TRANSFORM_TARGET_COLUMN_MAX_WIDTH);
        Rect transformTargetSearch = new Rect(
                transformPanel.x() + FUEL_PANEL_INSET,
                transformPanel.y() + FUEL_PANEL_INSET,
                targetColumnWidth,
                CONTROL_SIZE);
        FuelPageControls transformTargetPageControls = new FuelPageControls(
                new Rect(
                        transformTargetSearch.x(),
                        transformPanel.bottom() - FUEL_PANEL_INSET - CONTROL_SIZE,
                        CONTROL_SIZE,
                        CONTROL_SIZE),
                new Rect(
                        transformTargetSearch.right() - CONTROL_SIZE,
                        transformPanel.bottom() - FUEL_PANEL_INSET - CONTROL_SIZE,
                        CONTROL_SIZE,
                        CONTROL_SIZE));
        int fuelTargetCount = fuelDescriptors.fuelTargetCount();
        int targetListY = transformTargetSearch.bottom() + CONTROL_GAP;
        int targetListHeight = Math.max(
                TRANSFORM_TARGET_ROW_HEIGHT,
                transformTargetPageControls.previous().y() - CONTROL_GAP - targetListY);
        int targetCapacity = Math.max(1, targetListHeight / TRANSFORM_TARGET_ROW_HEIGHT);
        PagedList transformTargetList = new PagedList(
                new Rect(
                        transformTargetSearch.x(),
                        targetListY,
                        targetColumnWidth,
                        targetCapacity * TRANSFORM_TARGET_ROW_HEIGHT),
                TRANSFORM_TARGET_ROW_HEIGHT,
                targetCapacity,
                fuelTargetCount);
        int transformContentX = transformTargetSearch.right() + CONTROL_GAP;
        int transformContentWidth = transformPanel.right() - FUEL_PANEL_INSET - transformContentX;
        Rect transformInput = new Rect(
                transformContentX + 1,
                transformPanel.y() + FUEL_PANEL_INSET,
                SLOT_SIZE,
                SLOT_SIZE);
        int minimumAmountStripWidth = CONTROL_SIZE * 4;
        boolean singleTransformActionRow = transformContentWidth
                >= SLOT_SIZE + CONTROL_GAP + minimumAmountStripWidth;
        int amountStripAvailable = singleTransformActionRow
                ? transformContentWidth - SLOT_SIZE - CONTROL_GAP
                : transformContentWidth;
        int amountStripWidth = Math.max(
                minimumAmountStripWidth,
                Math.min(216, amountStripAvailable));
        amountStripWidth -= amountStripWidth % 4;
        Rect transformAmountStrip = new Rect(
                singleTransformActionRow
                        ? transformPanel.right() - FUEL_PANEL_INSET - amountStripWidth
                        : transformContentX,
                singleTransformActionRow
                        ? transformInput.y()
                        : transformInput.bottom() + CONTROL_GAP,
                amountStripWidth,
                CONTROL_SIZE);
        List<Rect> transformAmountButtons =
                contiguousSegmentRects(transformAmountStrip, 4);
        int transformCardControlsY = Math.max(
                transformInput.bottom(), transformAmountStrip.bottom()) + CONTROL_GAP;
        FuelPageControls transformCardPageControls = new FuelPageControls(
                new Rect(
                        transformPanel.right() - FUEL_PANEL_INSET
                                - CONTROL_SIZE * 2 - CONTROL_GAP,
                        transformCardControlsY,
                        CONTROL_SIZE,
                        CONTROL_SIZE),
                new Rect(
                        transformPanel.right() - FUEL_PANEL_INSET - CONTROL_SIZE,
                        transformCardControlsY,
                        CONTROL_SIZE,
                        CONTROL_SIZE));
        int transformCardsY = transformCardPageControls.previous().bottom() + CONTROL_GAP;
        Rect transformCardBounds = new Rect(
                transformContentX,
                transformCardsY,
                transformContentWidth,
                Math.max(TRANSFORM_CARD_HEIGHT,
                        transformPanel.bottom() - FUEL_PANEL_INSET - transformCardsY));
        FlowGrid transformCards = pagedSingleColumnGrid(
                transformCardBounds,
                fuelDescriptors.consumableCount(),
                TRANSFORM_CARD_HEIGHT);
        FlowGrid timedStationsGrid = pagedFlowGrid(
                categoryFlowBounds(timedStationsPanel, false, true),
                fuelDescriptors.timedStationCount(),
                PROCESSING_STATION_MAX_COLUMNS,
                PROCESSING_STATION_CELL_WIDTH);
        Rect instantFlow = categoryFlowBounds(
                instantStationsPanel, false, true);
        int statusX = playerInventory.right() + CONTROL_GAP;
        Rect fuelStatus = new Rect(
                statusX,
                playerInventory.y(),
                imageWidth - 8 - statusX,
                playerInventory.height());
        Rect fuelSearchButton = new Rect(
                fuelStatus.right() - CONTROL_SIZE,
                fuelStatus.bottom() - CONTROL_SIZE,
                CONTROL_SIZE,
                CONTROL_SIZE);
        Rect fuelSearchBox = fuelStatus.width() >= CONTROL_SIZE * 4
                ? new Rect(
                        fuelStatus.x() + 3,
                        fuelSearchButton.y() + 4,
                        fuelSearchButton.x() - CONTROL_GAP - fuelStatus.x() - 6,
                        9)
                : new Rect(102, 6, 70, 9);
        Rect fuelSearchPanel = new Rect(
                transformPanel.x(),
                transformPanel.y(),
                transformPanel.width(),
                transformPanel.height());
        Rect fuelSearchFlowBounds = new Rect(
                fuelSearchPanel.x() + FUEL_PANEL_INSET,
                fuelSearchPanel.y() + FUEL_PANEL_INSET + CONTROL_SIZE + CONTROL_GAP,
                fuelSearchPanel.width() - FUEL_PANEL_INSET * 2,
                fuelSearchPanel.height() - FUEL_PANEL_INSET * 2 - CONTROL_SIZE - CONTROL_GAP);
        int fuelSearchItemCount = fuelDescriptors.timedStationCount()
                + fuelDescriptors.instantStationCount();
        FlowGrid fuelSearchGrid = pagedFlowGrid(
                fuelSearchFlowBounds,
                fuelSearchItemCount,
                PROCESSING_STATION_MAX_COLUMNS,
                PROCESSING_STATION_CELL_WIDTH);
        FuelPageControls fuelSearchPageControls = new FuelPageControls(
                new Rect(
                        fuelSearchPanel.right() - FUEL_PANEL_INSET
                                - CONTROL_SIZE * 2 - CONTROL_GAP,
                        fuelSearchPanel.y() + FUEL_PANEL_INSET,
                        CONTROL_SIZE,
                        CONTROL_SIZE),
                new Rect(
                        fuelSearchPanel.right() - FUEL_PANEL_INSET - CONTROL_SIZE,
                        fuelSearchPanel.y() + FUEL_PANEL_INSET,
                        CONTROL_SIZE,
                        CONTROL_SIZE));
        FlowGrid instantStationsGrid = pagedCompactGrid(instantFlow,
                fuelDescriptors.instantStationCount());
        FuelPageControls timedStationsPageControls = fuelPageControls(
                fuelCategoryLabel(timedStationsPanel, timedStationsGrid),
                timedStationsPanel.right() - FUEL_PANEL_INSET);
        FuelPageControls instantStationsPageControls = fuelPageControls(
                fuelCategoryLabel(instantStationsPanel, instantStationsGrid),
                instantStationsPanel.right() - FUEL_PANEL_INSET);
        RecipeGeometry recipe = recipeGeometry(workspace);
        return new Geometry(
                profile,
                wide,
                imageWidth,
                imageHeight,
                rows,
                itemGrid,
                new Rect(102, 6, 70, 9),
                new Rect(100, 4, 72, 12),
                scrollbar,
                playerInventory,
                workspace,
                recipe.diagram(),
                recipe.ledger(),
                recipe.footer(),
                recipe.inputSlots(),
                recipe.arrow(),
                recipe.output(),
                recipe.station(),
                recipe.shapelessMarker(),
                recipe.navigationButtons(),
                recipe.craftButtons(),
                transformPanel,
                timedStationsPanel,
                instantStationsPanel,
                transformTargetSearch,
                transformTargetList,
                transformTargetPageControls,
                transformInput,
                transformAmountButtons,
                fuelStatus,
                fuelSearchButton,
                fuelSearchBox,
                fuelSearchPanel,
                transformCards,
                timedStationsGrid,
                instantStationsGrid,
                fuelSearchGrid,
                transformCardPageControls,
                timedStationsPageControls,
                instantStationsPageControls,
                fuelSearchPageControls,
                rails.itemButtons(),
                rails.fuelButtons(),
                rails.bounds(),
                rails.panel(),
                rails.fuelRailPanel(),
                List.of(new Rect(0, 0, imageWidth, imageHeight), rails.panel())
        );
    }

    private static int fixedBandRows(int screenHeight, int bottomHeight, int minimum) {
        int preferredHeight = Math.max(0, screenHeight - 2 * PREFERRED_VERTICAL_MARGIN);
        int rows = (preferredHeight - TOP_HEIGHT - bottomHeight) / SLOT_SIZE;
        return Math.clamp(rows, minimum, MAX_ROWS);
    }

    static Rect fuelSlot(Rect cell) {
        return leftCenteredSquare(cell, SLOT_SIZE, 1);
    }

    static Rect fuelIcon(Rect cell) {
        return leftCenteredSquare(cell, ICON_CANVAS_SIZE, 2);
    }

    static Rect fuelAmountBounds(Rect cell) {
        return new Rect(
                cell.x() + 22,
                cell.y() + Math.max(0, (cell.height() - FUEL_AMOUNT_HEIGHT) / 2),
                Math.max(0, cell.width() - 23),
                FUEL_AMOUNT_HEIGHT);
    }

    private static Rect leftCenteredSquare(Rect cell, int size, int inset) {
        return new Rect(
                cell.x() + inset,
                cell.y() + Math.max(0, (cell.height() - size) / 2),
                size,
                size);
    }

    private static int narrowRows(int screenHeight) {
        int maximumHeight = screenHeight - 2 * NARROW_MIN_VERTICAL_MARGIN;
        boolean horizontalFuelHeaders = supportsHorizontalFuelHeaders(screenHeight);
        for (int rows = MAX_ROWS; rows >= MIN_CRAFTING_ROWS; rows--) {
            if (narrowImageHeight(rows, horizontalFuelHeaders) <= maximumHeight) return rows;
        }
        return MIN_CRAFTING_ROWS;
    }

    private static int narrowImageHeight(int rows, boolean horizontalFuelHeaders) {
        int itemBottom = TOP_HEIGHT + rows * SLOT_SIZE;
        int workspaceBottom = itemBottom + 4 + NARROW_WORKSPACE_HEIGHT;
        int fuelGroupBottom = TOP_HEIGHT + fuelGroupHeight(horizontalFuelHeaders)
                + INVENTORY_LABEL_BAND_HEIGHT;
        int playerY = Math.max(workspaceBottom + WORKSPACE_PLAYER_GAP, fuelGroupBottom);
        return playerY + PLAYER_INVENTORY_HEIGHT + 8;
    }

    private static boolean supportsHorizontalFuelHeaders(int screenHeight) {
        return screenHeight >= TOP_HEIGHT
                + FUEL_CONSUMABLE_PANEL_HEIGHT + FUEL_STATION_TALL_PANEL_HEIGHT * 2
                + FUEL_CATEGORY_GAP * 2 + INVENTORY_LABEL_BAND_HEIGHT
                + PLAYER_INVENTORY_HEIGHT + 8;
    }

    private static int fuelGroupHeight(boolean horizontalHeaders) {
        int stationPanelHeight = horizontalHeaders
                ? FUEL_STATION_TALL_PANEL_HEIGHT : FUEL_STATION_PANEL_HEIGHT;
        return FUEL_CONSUMABLE_PANEL_HEIGHT + stationPanelHeight * 2
                + FUEL_CATEGORY_GAP * 2;
    }

    private static RecipeGeometry recipeGeometry(Rect workspace) {
        Rect footer = new Rect(
                workspace.x() + RECIPE_INSET,
                workspace.bottom() - RECIPE_INSET - CONTROL_SIZE,
                workspace.width() - RECIPE_INSET * 2,
                CONTROL_SIZE);
        int bodyTopLimit = workspace.y() + RECIPE_INSET;
        int bodyBottomLimit = footer.y() - RECIPE_BODY_FOOTER_GAP;
        int availableBodyHeight = bodyBottomLimit - bodyTopLimit;
        int bodyHeight = Math.min(RECIPE_BODY_MAX_HEIGHT, availableBodyHeight);
        if (bodyHeight < RECIPE_DIAGRAM_MIN_HEIGHT + RECIPE_LEDGER_MIN_ROW_HEIGHT) {
            throw new IllegalArgumentException("Recipe workspace is too short for its compact body");
        }
        int ledgerHeight = Math.min(
                RECIPE_LEDGER_MAX_HEIGHT,
                Math.max(RECIPE_LEDGER_MIN_ROW_HEIGHT,
                        bodyHeight - RECIPE_DIAGRAM_MIN_HEIGHT));
        int diagramHeight = bodyHeight - ledgerHeight;
        int bodyY = bodyTopLimit + (availableBodyHeight - bodyHeight) / 2;
        Rect diagram = new Rect(
                workspace.x() + RECIPE_INSET,
                bodyY,
                workspace.width() - RECIPE_INSET * 2,
                diagramHeight);
        Rect ledger = new Rect(
                diagram.x(),
                diagram.bottom(),
                diagram.width(),
                ledgerHeight);
        if (diagram.bottom() > ledger.y()
                || ledger.bottom() > footer.y()) {
            throw new IllegalArgumentException("Recipe diagram, ledger, and footer overlap");
        }

        int inputGridSize = SLOT_SIZE * 3;
        int outputSize = SLOT_SIZE + 10;
        int chainWidth = inputGridSize + CONTROL_GAP + CONTROL_SIZE
                + CONTROL_GAP + outputSize;
        int chainX = diagram.x() + Math.max(0, (diagram.width() - chainWidth) / 2);
        int inputY = diagram.y() + Math.max(0, (diagram.height() - inputGridSize) / 2);
        List<Rect> inputSlots = new ArrayList<>(RecipePresentation.MAX_INPUTS);
        for (int input = 0; input < RecipePresentation.MAX_INPUTS; input++) {
            inputSlots.add(new Rect(
                    chainX + input % 3 * SLOT_SIZE,
                    inputY + input / 3 * SLOT_SIZE,
                    SLOT_SIZE,
                    SLOT_SIZE));
        }
        Rect arrow = new Rect(
                chainX + inputGridSize + CONTROL_GAP,
                diagram.y() + (diagram.height() - CONTROL_SIZE) / 2,
                CONTROL_SIZE,
                CONTROL_SIZE);
        Rect output = new Rect(
                arrow.right() + CONTROL_GAP,
                diagram.y() + (diagram.height() - outputSize) / 2,
                outputSize,
                outputSize);
        Rect station = new Rect(
                diagram.right() - SLOT_SIZE - 2,
                diagram.bottom() - SLOT_SIZE - 2,
                SLOT_SIZE,
                SLOT_SIZE);
        Rect shapelessMarker = new Rect(
                diagram.x(),
                diagram.y(),
                10,
                10);

        List<Rect> navigationButtons = List.of(
                new Rect(footer.x(), footer.y(), CONTROL_SIZE, CONTROL_SIZE),
                new Rect(footer.x() + CONTROL_SIZE + CONTROL_GAP,
                        footer.y(), CONTROL_SIZE, CONTROL_SIZE));
        int availableCraftWidth = footer.right()
                - navigationButtons.getLast().right() - CONTROL_GAP;
        int segmentWidth = Math.clamp(availableCraftWidth / 4, CONTROL_SIZE, 32);
        Rect craftStrip = new Rect(
                footer.right() - segmentWidth * 4,
                footer.y(),
                segmentWidth * 4,
                CONTROL_SIZE);
        List<Rect> craftButtons = contiguousSegmentRects(craftStrip, 4);
        if (navigationButtons.getLast().right() + CONTROL_GAP > craftButtons.getFirst().x()) {
            throw new IllegalArgumentException("Recipe footer controls do not fit workspace");
        }
        return new RecipeGeometry(
                diagram,
                ledger,
                footer,
                inputSlots,
                arrow,
                output,
                station,
                shapelessMarker,
                navigationButtons,
                craftButtons);
    }

    private static List<Rect> recipeLedgerCells(Rect bounds, int resourceCount) {
        if (resourceCount < 0 || resourceCount > MAX_RECIPE_LEDGER_ROWS) {
            throw new IllegalArgumentException("Recipe ledger resource count is out of bounds");
        }
        if (resourceCount == 0) return List.of();
        int availableColumns = Math.max(1, bounds.width() / RECIPE_LEDGER_MIN_CELL_WIDTH);
        int columns = Math.min(Math.min(RECIPE_LEDGER_MAX_COLUMNS, availableColumns), resourceCount);
        int rows = (resourceCount + columns - 1) / columns;
        int cellHeight = Math.min(SLOT_SIZE, bounds.height() / rows);
        int top = bounds.y();
        List<Rect> result = new ArrayList<>(resourceCount);
        for (int index = 0; index < resourceCount; index++) {
            int column = index % columns;
            int row = index / columns;
            int left = bounds.x() + column * bounds.width() / columns;
            int right = bounds.x() + (column + 1) * bounds.width() / columns;
            result.add(new Rect(
                    left,
                    top + row * cellHeight,
                    right - left,
                    cellHeight));
        }
        return List.copyOf(result);
    }

    private static List<Rect> contiguousSegmentRects(Rect bounds, int segmentCount) {
        if (segmentCount <= 0 || bounds.width() % segmentCount != 0) {
            throw new IllegalArgumentException("Segment strip must divide into equal positive widths");
        }
        int segmentWidth = bounds.width() / segmentCount;
        List<Rect> result = new ArrayList<>(segmentCount);
        for (int index = 0; index < segmentCount; index++) {
            result.add(new Rect(
                    bounds.x() + index * segmentWidth,
                    bounds.y(),
                    segmentWidth,
                    bounds.height()));
        }
        return List.copyOf(result);
    }

    static Rect fuelCategoryLabel(Rect panel, FlowGrid grid) {
        if (grid.bounds().x() == panel.x() + FUEL_PANEL_INSET) {
            return new Rect(
                    panel.x() + FUEL_PANEL_INSET,
                    panel.y() + FUEL_PANEL_INSET,
                    panel.width() - FUEL_PANEL_INSET * 2,
                    CONTROL_SIZE);
        }
        return new Rect(
                panel.x() + FUEL_PANEL_INSET,
                grid.bounds().y(),
                FUEL_CATEGORY_LABEL_WIDTH,
                grid.bounds().height());
    }

    private static FuelPageControls fuelPageControls(Rect label, int rightLimit) {
        int width = CONTROL_SIZE * 2 + CONTROL_GAP;
        boolean horizontal = label.height() == CONTROL_SIZE
                && label.width() > FUEL_CATEGORY_LABEL_WIDTH;
        int left = horizontal
                ? Math.max(label.x(), Math.min(label.right(), rightLimit) - width)
                : label.x() + Math.max(0, (label.width() - width) / 2);
        int top = horizontal ? label.y() : label.bottom() - CONTROL_SIZE;
        return new FuelPageControls(
                new Rect(left, top, CONTROL_SIZE, CONTROL_SIZE),
                new Rect(left + CONTROL_SIZE + CONTROL_GAP,
                        top, CONTROL_SIZE, CONTROL_SIZE));
    }

    private static Rect categoryFlowBounds(
            Rect panel,
            boolean belowTargetBar,
            boolean horizontalHeader
    ) {
        int rowY = panel.y() + FUEL_PANEL_INSET;
        if (belowTargetBar || horizontalHeader) {
            rowY += FUEL_TARGET_BAR_HEIGHT + FUEL_TARGET_BAR_GAP;
        }
        int leftInset = horizontalHeader
                ? FUEL_PANEL_INSET
                : FUEL_PANEL_INSET + FUEL_CATEGORY_LABEL_WIDTH + CONTROL_GAP;
        return new Rect(
                panel.x() + leftInset,
                rowY,
                panel.width() - leftInset - FUEL_PANEL_INSET,
                panel.bottom() - FUEL_PANEL_INSET - rowY);
    }

    private static FlowGrid pagedFlowGrid(
            Rect bounds,
            int itemCount,
            int maxColumns,
            int preferredCellWidth
    ) {
        int columns = Math.max(1, Math.min(
                maxColumns, bounds.width() / preferredCellWidth));
        int rows = Math.max(1, bounds.height() / FUEL_CATEGORY_CELL_HEIGHT);
        int capacity = columns * rows;
        int visible = Math.min(itemCount, capacity);
        List<Rect> cells = flowCells(
                bounds,
                visible,
                columns,
                rows,
                preferredCellWidth,
                false);
        return new FlowGrid(
                bounds,
                cells,
                columns,
                rows,
                preferredCellWidth,
                capacity,
                itemCount,
                false);
    }

    private static FlowGrid pagedCompactGrid(Rect bounds, int itemCount) {
        int columns = Math.max(1, bounds.width() / INSTANT_STATION_CELL_SIZE);
        int rows = Math.max(1, bounds.height() / INSTANT_STATION_CELL_SIZE);
        int capacity = columns * rows;
        int visible = Math.min(itemCount, capacity);
        List<Rect> cells = flowCells(
                bounds,
                visible,
                columns,
                rows,
                INSTANT_STATION_CELL_SIZE,
                true);
        return new FlowGrid(
                bounds,
                cells,
                columns,
                rows,
                INSTANT_STATION_CELL_SIZE,
                capacity,
                itemCount,
                true);
    }

    private static FlowGrid pagedSingleColumnGrid(
            Rect bounds,
            int itemCount,
            int cellHeight
    ) {
        int rows = Math.max(1, bounds.height() / cellHeight);
        int visible = Math.min(itemCount, rows);
        List<Rect> cells = flowCells(
                bounds,
                visible,
                1,
                rows,
                bounds.width(),
                false);
        return new FlowGrid(
                bounds,
                cells,
                1,
                rows,
                bounds.width(),
                rows,
                itemCount,
                false);
    }

    private static List<Rect> flowCells(
            Rect bounds,
            int visible,
            int maxColumns,
            int maxRows,
            int preferredCellWidth,
            boolean compact
    ) {
        if (visible <= 0) return List.of();
        int largestColumns = Math.min(maxColumns, visible);
        int minimumColumns = (visible + maxRows - 1) / maxRows;
        int preferredColumns = Math.max(1, bounds.width() / preferredCellWidth);
        int columns = Math.clamp(preferredColumns, minimumColumns, largestColumns);
        int rows = (visible + columns - 1) / columns;
        List<Rect> cells = new ArrayList<>(visible);
        for (int index = 0; index < visible; index++) {
            int column = index % columns;
            int row = index / columns;
            int columnsInRow = Math.min(columns, visible - row * columns);
            int left = compact
                    ? bounds.x() + column * preferredCellWidth
                    : bounds.x() + column * bounds.width() / columnsInRow;
            int right = compact
                    ? Math.min(bounds.right(), left + preferredCellWidth)
                    : bounds.x() + (column + 1) * bounds.width() / columnsInRow;
            int top = compact
                    ? bounds.y() + row * preferredCellWidth
                    : bounds.y() + row * bounds.height() / maxRows;
            int bottom = compact
                    ? Math.min(bounds.bottom(), top + preferredCellWidth)
                    : bounds.y() + (row + 1) * bounds.height() / maxRows;
            cells.add(new Rect(
                    left,
                    top,
                    right - left,
                    bottom - top));
        }
        return cells;
    }

    private static FlowGrid emptyFlow() {
        return new FlowGrid(
                new Rect(0, 0, 0, 0), List.of(), 1, 1, 1, 1, 0, false);
    }

    private static RailGeometry profileRails(TerminalProfile profile, int imageHeight) {
        List<Rect> itemButtons = railButtons(imageHeight, profile.itemRailGroups());
        List<Rect> fuelButtons = railButtons(imageHeight, profile.fuelRailGroups());
        List<Rect> allButtons = new ArrayList<>(itemButtons.size() + fuelButtons.size());
        allButtons.addAll(itemButtons);
        allButtons.addAll(fuelButtons);
        Rect bounds = boundsOf(allButtons);
        return new RailGeometry(
                itemButtons,
                fuelButtons,
                bounds,
                inflate(bounds, 3),
                inflate(boundsOf(fuelButtons), 3));
    }

    private static List<Rect> railButtons(int imageHeight, List<Integer> groupSizes) {
        List<Rect> result = new ArrayList<>();
        int column = 0;
        int y = RAIL_TOP;
        boolean firstGroup = true;
        for (int groupSize : groupSizes) {
            if (groupSize <= 0) continue;
            if (!firstGroup) y += RAIL_GROUP_GAP;
            firstGroup = false;
            for (int index = 0; index < groupSize; index++) {
                if (y + RAIL_BUTTON_SIZE > imageHeight - RAIL_TOP) {
                    column++;
                    y = RAIL_TOP;
                }
                result.add(new Rect(
                        -(column + 1) * (RAIL_BUTTON_SIZE + RAIL_SPACING),
                        y,
                        RAIL_BUTTON_SIZE,
                        RAIL_BUTTON_SIZE));
                y += RAIL_BUTTON_SIZE + RAIL_SPACING;
            }
        }
        return result;
    }

    private static int railReservedWidth(Rect railPanel) {
        return Math.max(0, -railPanel.x()) + RAIL_FRAME_GAP;
    }

    private static Rect boundsOf(List<Rect> rectangles) {
        if (rectangles.isEmpty()) return new Rect(0, 0, 0, 0);
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (Rect rectangle : rectangles) {
            left = Math.min(left, rectangle.x());
            top = Math.min(top, rectangle.y());
            right = Math.max(right, rectangle.right());
            bottom = Math.max(bottom, rectangle.bottom());
        }
        return new Rect(left, top, right - left, bottom - top);
    }

    private static Rect inflate(Rect rectangle, int amount) {
        if (rectangle.width() == 0 && rectangle.height() == 0) return rectangle;
        return new Rect(
                rectangle.x() - amount,
                rectangle.y() - amount,
                rectangle.width() + amount * 2,
                rectangle.height() + amount * 2);
    }

    private record RecipeGeometry(
            Rect diagram,
            Rect ledger,
            Rect footer,
            List<Rect> inputSlots,
            Rect arrow,
            Rect output,
            Rect station,
            Rect shapelessMarker,
            List<Rect> navigationButtons,
            List<Rect> craftButtons
    ) {
        RecipeGeometry {
            inputSlots = List.copyOf(inputSlots);
            navigationButtons = List.copyOf(navigationButtons);
            craftButtons = List.copyOf(craftButtons);
        }
    }

    private record RailGeometry(
            List<Rect> itemButtons,
            List<Rect> fuelButtons,
            Rect bounds,
            Rect panel,
            Rect fuelRailPanel
    ) {
        RailGeometry {
            itemButtons = List.copyOf(itemButtons);
            fuelButtons = List.copyOf(fuelButtons);
        }
    }
}
