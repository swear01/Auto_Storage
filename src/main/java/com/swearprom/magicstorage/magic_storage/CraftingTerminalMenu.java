package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

public class CraftingTerminalMenu extends StorageTerminalMenu {

    public record IngredientPreview(ItemStack stack, long available, long required) {}
    public record EnergyPreview(EnergyType type, long available, long required) {}
    public record CraftPreview(
            int craftable,
            List<ItemStack> missing,
            List<IngredientPreview> ingredients,
            List<EnergyPreview> energies
    ) {}
    private record IngredientNeed(RecipeAdapterMatch.Input ingredient, long count) {}
    private record IngredientAvailability(
            StorageCoreBlockEntity core,
            List<IngredientSource> allSources,
            Map<ItemKey, Long> amountsByKey,
            Map<Item, Long> amountsByItem,
            Map<Item, List<IngredientSource>> sourcesByItem,
            Map<RecipeAdapterMatch.Input, List<IngredientSource>> matchingCache
    ) {
        static IngredientAvailability create(
                StorageCoreBlockEntity core,
                List<IngredientSource> sources,
                boolean includesPlayerSources
        ) {
            if (!includesPlayerSources) {
                return new IngredientAvailability(
                        core,
                        List.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        new IdentityHashMap<>());
            }
            Map<ItemKey, Long> amountsByKey = new HashMap<>();
            Map<Item, Long> amountsByItem = new HashMap<>();
            Map<Item, List<IngredientSource>> sourcesByItem = new HashMap<>();
            for (IngredientSource source : sources) {
                amountsByKey.merge(source.key(), source.amount(), CraftingTerminalMenu::saturatingAdd);
                amountsByItem.merge(
                        source.stack().getItem(),
                        source.amount(),
                        CraftingTerminalMenu::saturatingAdd);
                sourcesByItem.computeIfAbsent(
                        source.stack().getItem(), ignored -> new ArrayList<>()).add(source);
            }
            sourcesByItem.replaceAll((item, matching) -> List.copyOf(matching));
            return new IngredientAvailability(
                    null,
                    sources,
                    Map.copyOf(amountsByKey),
                    Map.copyOf(amountsByItem),
                    Map.copyOf(sourcesByItem),
                    new IdentityHashMap<>());
        }

        List<IngredientSource> sources() {
            return core == null ? allSources : core.storedItemSources();
        }

        Collection<Item> items() {
            return core == null ? sourcesByItem.keySet() : core.storedItems();
        }

        private List<IngredientSource> sources(Item item) {
            return core == null
                    ? sourcesByItem.getOrDefault(item, List.of())
                    : core.storedItemSources(item);
        }

        long amount(ItemKey key) {
            return core == null
                    ? amountsByKey.getOrDefault(key, 0L)
                    : core.getItemCount(key);
        }

        private long amount(Item item) {
            return core == null
                    ? amountsByItem.getOrDefault(item, 0L)
                    : core.storedItemAmount(item);
        }

        List<IngredientSource> matching(RecipeAdapterMatch.Input input) {
            return matchingCache.computeIfAbsent(input, ignored -> {
                List<Item> items = input.representativeItems();
                if (!input.representativeItemsExhaustive() || items.isEmpty()) {
                    return core == null
                            ? allSources.stream().filter(source -> input.test(source.stack())).toList()
                            : core.storedItemSources(input::test);
                }
                if (items.size() == 1) return sources(items.getFirst());
                List<IngredientSource> matching = new ArrayList<>();
                for (Item item : items) matching.addAll(sources(item));
                return List.copyOf(matching);
            });
        }

        long matchingAllItemVariants(RecipeAdapterMatch.Input input) {
            long available = 0;
            for (Item item : input.representativeItems()) {
                available = saturatingAdd(
                        available, amount(item));
            }
            return available;
        }
    }
    private record PlayerReservation(ItemKey key, int count) {}
    private record IngredientPlan(
            Map<ItemKey, Long> coreReservations,
            Map<Integer, PlayerReservation> playerReservations,
            List<Map<ItemKey, Long>> allocations
    ) {}
    private record ToolUsePlan(ResourceLocation descriptorId, long amount) {}
    private record DeliveryPlan(
            List<ItemStack> playerInventory,
            ItemStack carried,
            Map<StorageResourceKey, Long> coreDeltas,
            ToolUsePlan toolUse
    ) {}
    private record TypedConsumption(
            Map<StorageResourceKey, Long> coreConsumed,
            Map<Integer, PlayerReservation> playerReservations,
            Map<StorageResourceKey, Long> remainders
    ) {}
    private record CraftPlan(long crafts, IngredientPlan ingredients, DeliveryPlan delivery) {}
    private record CraftableOutput(StorageResourceKey key, ItemStack icon, long storedAmount) {}
    private record CraftableStatus(boolean craftable, boolean inputsAvailable) {}
    private record CraftableBuildResult(
            List<ItemStack> stacks,
            int candidates,
            int variants,
            long candidateSelectionNanos,
            long variantResolutionNanos,
            long previewSimulationNanos
    ) {}
    private record SharedCraftableCache(
            Object recipeSnapshot,
            long craftableRevision,
            long machineRevision,
            long topologyRevision,
            String filter,
            SortMode sortMode,
            SortOrder sortOrder,
            TerminalResourceView resourceView,
            List<ItemStack> stacks,
            long[] energyThresholds,
            Map<ResourceLocation, Long> stationThresholds
    ) {
        private SharedCraftableCache {
            stacks = stacks.stream().map(ItemStack::copy).toList();
            energyThresholds = energyThresholds.clone();
            stationThresholds = Map.copyOf(stationThresholds);
        }
    }
    private static final Map<StorageCoreBlockEntity, SharedCraftableCache>
            SHARED_CRAFTABLE_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private enum DeliveryTarget {
        CURSOR,
        PLAYER,
        STORAGE;

        private static DeliveryTarget from(CraftingDestination destination) {
            return switch (destination) {
                case CURSOR -> CURSOR;
                case INVENTORY -> PLAYER;
                case STORAGE -> STORAGE;
                case NONE -> throw new IllegalArgumentException("NONE has no delivery target");
            };
        }

        private static DeliveryTarget from(TerminalOutputDestination destination) {
            return switch (destination) {
                case PLAYER -> PLAYER;
                case STORAGE -> STORAGE;
            };
        }
    }

    private static final class FlowEdge {
        private final int to;
        private final int reverseIndex;
        private final long originalCapacity;
        private long capacity;

        private FlowEdge(int to, int reverseIndex, long capacity) {
            this.to = to;
            this.reverseIndex = reverseIndex;
            this.originalCapacity = capacity;
            this.capacity = capacity;
        }
    }

    private static final int MAX_INGREDIENTS = RecipePresentation.MAX_ITEM_RESOURCES;
    private static final int PRESENTATION_OUTPUT_SLOT = 0;
    private static final int ITEM_RESOURCE_SLOT_START = PRESENTATION_OUTPUT_SLOT + 1;
    private static final int PRESENTATION_STATION_SLOT = ITEM_RESOURCE_SLOT_START + MAX_INGREDIENTS;
    private static final int PRESENTATION_INPUT_SLOT_START = PRESENTATION_STATION_SLOT + 1;
    private static final int PRESENTATION_TOOL_SLOT =
            PRESENTATION_INPUT_SLOT_START + RecipePresentation.MAX_INPUTS;
    private static final int PRESENTATION_METADATA_SLOT = PRESENTATION_TOOL_SLOT + 1;
    private static final int SELECTION_SLOTS = PRESENTATION_METADATA_SLOT + 1;
    private static final int PREVIEW_CAP = 9999;
    private static final int MAX_RECIPE_REQUEST = 64;
    public static final int FUEL_INPUT_SLOT = DISPLAY_SLOTS + PLAYER_INVENTORY_SLOTS;
    public static final int MACHINE_SLOT_START = FUEL_INPUT_SLOT + 1;
    public static final int MACHINE_SLOT_COUNT = MachineDescriptorApi.MAX_DESCRIPTORS;
    private static final int MACHINE_SLOT_END = MACHINE_SLOT_START + MACHINE_SLOT_COUNT;
    private static final int MACHINE_SLOT_X = 18;
    private static final int MACHINE_SLOT_Y = 42;
    private static final int MACHINE_SLOT_GAP = 38;
    private static final int FUEL_INPUT_X = 18;
    private static final int FUEL_INPUT_Y = 126;
    static final int MAX_CRAFT_BUTTON = 5;
    static final int CRAFTABLE_PAGE_BUTTON = 6;
    static final int OUTPUT_DESTINATION_BUTTON = 10;
    static final int STORAGE_PAGE_BUTTON = 14;
    static final int TRANSFORM_PAGE_BUTTON = 15;
    static final int AUTO_FUEL_TARGET_BUTTON = 16;
    static final int RESET_OUTPUT_DESTINATION_BUTTON = 24;
    static final int RESET_PLAYER_INVENTORY_BUTTON = 25;
    static final int STATIONS_PAGE_BUTTON = 29;
    private static final int TRANSFORM_USE_BUTTON_BASE = 2_000;
    private static final List<EnergyType> FUEL_TARGETS = List.of(
            EnergyType.FURNACE_FUEL,
            EnergyType.BLAZE_FUEL);
    private static final int SELECTED_TRANSFORM_USE_DATA_SLOT = 8;
    private static final int ENERGY_DATA_START = SELECTED_TRANSFORM_USE_DATA_SLOT + 1;
    private static final int LIVE_ENERGY_DATA_SLOTS = EnergyType.values().length * 4;
    private static final int RETIRED_ENERGY_DATA_SLOTS = 4;
    private static final int ENERGY_DATA_SLOTS = LIVE_ENERGY_DATA_SLOTS + RETIRED_ENERGY_DATA_SLOTS;
    private static final int INGREDIENT_AVAILABLE_DATA_START = ENERGY_DATA_START + ENERGY_DATA_SLOTS;
    private static final int PROCESS_REQUIRED_DATA_START = INGREDIENT_AVAILABLE_DATA_START + MAX_INGREDIENTS * 4;
    private static final int FUEL_REQUIRED_DATA_START = PROCESS_REQUIRED_DATA_START + 4;
    private static final int AXE_ENERGY_DATA_START = FUEL_REQUIRED_DATA_START + 4;
    private static final int AXE_INFINITE_DATA_SLOT = AXE_ENERGY_DATA_START + 4;
    private static final int CRAFTABLE_RECIPE_COUNT_DATA_SLOT = AXE_INFINITE_DATA_SLOT + 1;
    private static final int CRAFTING_DATA_SLOTS = CRAFTABLE_RECIPE_COUNT_DATA_SLOT + 1;
    private static final EnergyType[] ENERGY_SYNC_ORDER = EnergyType.values();

    private ItemStack selectedOutput = ItemStack.EMPTY;
    private ResourceLocation selectedRecipeId;
    private SimpleContainer selectionContainer;
    private SimpleContainer fuelContainer;
    private SimpleContainer consumableInputContainer;
    private Container machineContainer;
    private final List<MachineDescriptor> descriptorSnapshot;
    private final Map<ResourceLocation, MachineDescriptorStatePacket.State> descriptorStates = new HashMap<>();
    private final List<RecipeHolder<?>> currentRecipes = new ArrayList<>();
    private final CraftableRecipeCatalog craftableRecipeCatalog = new CraftableRecipeCatalog();
    private final AxeTransformationCatalog axeTransformationCatalog = new AxeTransformationCatalog();
    private int currentRecipeIndex = 0;
    private int recipeCount = 0;
    private int craftableRecipeCount = 0;
    private int currentRecipeTypeOrder = -1;
    private int craftableCount = 0;
    private boolean usePlayerInventory = false;
    private TerminalOutputDestination outputDestination = TerminalOutputDestination.PLAYER;
    private boolean dirtyRecipes = false;
    private int lastCheckedItem = -1;
    private final Inventory playerInventory;
    private CraftingTerminalPage page = CraftingTerminalPage.STORAGE;
    private ResourceLocation selectedTransformTarget;
    private ResourceLocation selectedTransformUseId;
    private final long[] energyAmounts = new long[EnergyType.values().length];
    private final long[] ingredientAvailable = new long[MAX_INGREDIENTS];
    private long processRequired;
    private long fuelRequired;
    private long axeEnergyAmount;
    private boolean infiniteAxeEnergy;
    private boolean processingFuelInput;
    private boolean processingConsumableInput;
    private int lastPlayerInventoryFingerprint;
    private long lastTopologyRevision;
    private long lastMachineRevision;
    private final long[] nextCraftableEnergyThreshold = new long[EnergyType.values().length];
    private final Map<ResourceLocation, Long> nextCraftableStationThreshold = new HashMap<>();

    public CraftingTerminalMenu(int containerId, Inventory playerInv, StorageCoreBlockEntity core) {
        this(containerId, playerInv, core, core.getBlockPos(), false);
    }

    public CraftingTerminalMenu(int containerId, Inventory playerInv, StorageCoreBlockEntity core, BlockPos accessPos, boolean remoteAccess) {
        super(MagicStorage.CRAFTING_TERMINAL_MENU.get(), containerId, playerInv, core, accessPos, remoteAccess, true);
        this.playerInventory = playerInv;
        this.descriptorSnapshot = MachineEnergyTable.entries();
        initializeStorageMenu(playerInv, core);
        this.lastPlayerInventoryFingerprint = playerInventoryFingerprint();
        this.lastTopologyRevision = core.getTopologyRevision();
        this.lastMachineRevision = core.getMachineRevision();
        Arrays.fill(nextCraftableEnergyThreshold, Long.MAX_VALUE);
        refreshEnergyAmounts(core);
        addContainerData();
    }

    public CraftingTerminalMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        super(MagicStorage.CRAFTING_TERMINAL_MENU.get(), containerId, playerInv, buf, true);
        this.playerInventory = playerInv;
        this.descriptorSnapshot = MachineEnergyTable.readSnapshot(buf);
        initializeStorageMenu(playerInv, null);
        this.lastPlayerInventoryFingerprint = playerInventoryFingerprint();
        addContainerData();
    }

    private void addContainerData() {
        addDataSlots(new ContainerData() {
            @Override
            public int get(int index) {
                if (index == CRAFTABLE_RECIPE_COUNT_DATA_SLOT) {
                    return craftableRecipeCount;
                }
                return switch (index) {
                    case 0 -> currentRecipeIndex;
                    case 1 -> usePlayerInventory ? 1 : 0;
                    case 2 -> craftableCount;
                    case 3 -> recipeCount;
                    case 4 -> currentRecipeTypeOrder;
                    case 5 -> page.ordinal();
                    case 6 -> encodeTransformTarget(selectedTransformTarget);
                    case 7 -> outputDestination.ordinal();
                    case SELECTED_TRANSFORM_USE_DATA_SLOT ->
                            encodeTransformUse(selectedTransformUseId);
                    default -> getPreviewData(index);
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == CRAFTABLE_RECIPE_COUNT_DATA_SLOT) {
                    craftableRecipeCount = value;
                    return;
                }
                switch (index) {
                    case 0 -> currentRecipeIndex = value;
                    case 1 -> usePlayerInventory = value != 0;
                    case 2 -> craftableCount = value;
                    case 3 -> recipeCount = value;
                    case 4 -> currentRecipeTypeOrder = value;
                    case 5 -> page = CraftingTerminalPage.fromOrdinal(value);
                    case 6 -> selectedTransformTarget = decodeTransformTarget(value);
                    case 7 -> outputDestination = TerminalOutputDestination.byId(value);
                    case SELECTED_TRANSFORM_USE_DATA_SLOT ->
                            selectedTransformUseId = decodeTransformUse(value);
                    default -> setPreviewData(index, value);
                }
            }

            @Override
            public int getCount() {
                return CRAFTING_DATA_SLOTS;
            }
        });
    }

    private int getPreviewData(int index) {
        if (index >= ENERGY_DATA_START && index < INGREDIENT_AVAILABLE_DATA_START) {
            int relative = index - ENERGY_DATA_START;
            if (relative >= LIVE_ENERGY_DATA_SLOTS) return 0;
            EnergyType type = ENERGY_SYNC_ORDER[relative / 4];
            return getLongPart(energyAmounts[type.ordinal()], relative % 4);
        }
        if (index >= INGREDIENT_AVAILABLE_DATA_START && index < PROCESS_REQUIRED_DATA_START) {
            int relative = index - INGREDIENT_AVAILABLE_DATA_START;
            return getLongPart(ingredientAvailable[relative / 4], relative % 4);
        }
        if (index >= PROCESS_REQUIRED_DATA_START && index < FUEL_REQUIRED_DATA_START) {
            return getLongPart(processRequired, index - PROCESS_REQUIRED_DATA_START);
        }
        if (index >= FUEL_REQUIRED_DATA_START && index < CRAFTING_DATA_SLOTS) {
            if (index < AXE_ENERGY_DATA_START) {
                return getLongPart(fuelRequired, index - FUEL_REQUIRED_DATA_START);
            }
            if (index < AXE_INFINITE_DATA_SLOT) {
                return getLongPart(axeEnergyAmount, index - AXE_ENERGY_DATA_START);
            }
            return infiniteAxeEnergy ? 1 : 0;
        }
        return 0;
    }

    private void setPreviewData(int index, int value) {
        if (index >= ENERGY_DATA_START && index < INGREDIENT_AVAILABLE_DATA_START) {
            int relative = index - ENERGY_DATA_START;
            if (relative >= LIVE_ENERGY_DATA_SLOTS) return;
            EnergyType type = ENERGY_SYNC_ORDER[relative / 4];
            int energyIndex = type.ordinal();
            energyAmounts[energyIndex] = setLongPart(energyAmounts[energyIndex], relative % 4, value);
            return;
        }
        if (index >= INGREDIENT_AVAILABLE_DATA_START && index < PROCESS_REQUIRED_DATA_START) {
            int relative = index - INGREDIENT_AVAILABLE_DATA_START;
            int ingredient = relative / 4;
            ingredientAvailable[ingredient] = setLongPart(
                    ingredientAvailable[ingredient], relative % 4, value);
            return;
        }
        if (index >= PROCESS_REQUIRED_DATA_START && index < FUEL_REQUIRED_DATA_START) {
            processRequired = setLongPart(processRequired, index - PROCESS_REQUIRED_DATA_START, value);
            return;
        }
        if (index >= FUEL_REQUIRED_DATA_START && index < AXE_ENERGY_DATA_START) {
            fuelRequired = setLongPart(fuelRequired, index - FUEL_REQUIRED_DATA_START, value);
            return;
        }
        if (index >= AXE_ENERGY_DATA_START && index < AXE_INFINITE_DATA_SLOT) {
            axeEnergyAmount = setLongPart(axeEnergyAmount, index - AXE_ENERGY_DATA_START, value);
            return;
        }
        if (index == AXE_INFINITE_DATA_SLOT) {
            infiniteAxeEnergy = value != 0;
        }
    }

    private static int getLongPart(long value, int part) {
        return (int) ((value >>> (part * 16)) & 0xFFFFL);
    }

    private static long setLongPart(long current, int part, int value) {
        int shift = part * 16;
        long mask = 0xFFFFL << shift;
        return (current & ~mask) | (((long) value & 0xFFFFL) << shift);
    }

    private int encodeTransformTarget(ResourceLocation target) {
        if (target == null) return 0;
        int index = TransformProviderApi.targetIds(descriptorSnapshot).indexOf(target);
        return index < 0 ? 0 : index + 1;
    }

    private ResourceLocation decodeTransformTarget(int value) {
        int index = value - 1;
        List<ResourceLocation> targets = TransformProviderApi.targetIds(descriptorSnapshot);
        return index >= 0 && index < targets.size() ? targets.get(index) : null;
    }

    private int encodeTransformUse(ResourceLocation useId) {
        if (useId == null) return 0;
        int index = TransformProviderApi.useIds(descriptorSnapshot).indexOf(useId);
        return index < 0 ? 0 : index + 1;
    }

    private ResourceLocation decodeTransformUse(int value) {
        int index = value - 1;
        List<ResourceLocation> useIds = TransformProviderApi.useIds(descriptorSnapshot);
        return index >= 0 && index < useIds.size() ? useIds.get(index) : null;
    }

    @Override
    protected void setupSlots(Inventory playerInv) {
        for (int row = 0; row < MAX_DISPLAY_ROWS; row++) {
            for (int col = 0; col < DISPLAY_COLS; col++) {
                int slotIndex = col + row * DISPLAY_COLS;
                this.addSlot(new GhostSlot(displayInventory, slotIndex, 7 + col * 18, 17 + row * 18) {
                    @Override
                    public boolean isActive() {
                        return page.isItemPage() && super.isActive();
                    }
                });
            }
        }

        int playerInvTop = 17 + MAX_DISPLAY_ROWS * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(
                        playerInv, col + row * 9 + 9,
                        7 + col * 18, playerInvTop + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(
                    playerInv, col, 7 + col * 18,
                    playerInvTop + 3 * 18 + 4));
        }

        fuelContainer = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                onFuelInputChanged();
            }
        };
        this.addSlot(new Slot(fuelContainer, 0, FUEL_INPUT_X, FUEL_INPUT_Y) {
            @Override
            public boolean isActive() {
                return page == CraftingTerminalPage.TRANSFORM;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return page == CraftingTerminalPage.TRANSFORM && !stack.isEmpty();
            }

            @Override
            public boolean mayPickup(Player player) {
                return page == CraftingTerminalPage.TRANSFORM && super.mayPickup(player);
            }
        });

        StorageCoreBlockEntity core = getCore(playerInv.player.level());
        machineContainer = core != null
                ? core.getMachineContainer()
                : new SimpleContainer(MACHINE_SLOT_COUNT);
        consumableInputContainer = new SimpleContainer(MACHINE_SLOT_COUNT) {
            @Override
            public void setChanged() {
                super.setChanged();
                onConsumableInputsChanged();
            }
        };
        for (int machineSlot = 0; machineSlot < MACHINE_SLOT_COUNT; machineSlot++) {
            int mappedSlot = machineSlot;
            MachineDescriptor descriptor = descriptorAt(mappedSlot);
            boolean consumable = descriptor != null
                    && descriptor.category() == MachineEnergyTable.Category.TRANSFORM;
            Container slotContainer = consumable ? consumableInputContainer : machineContainer;
            int containerSlot = mappedSlot;
            this.addSlot(new Slot(slotContainer, containerSlot,
                    MACHINE_SLOT_X + mappedSlot * MACHINE_SLOT_GAP, MACHINE_SLOT_Y) {
                private boolean hasRetainedLegacyInput() {
                    return consumable && !machineContainer.getItem(mappedSlot).isEmpty();
                }

                @Override
                public ItemStack getItem() {
                    return hasRetainedLegacyInput()
                            ? machineContainer.getItem(mappedSlot).copy()
                            : super.getItem();
                }

                @Override
                public ItemStack remove(int amount) {
                    return hasRetainedLegacyInput()
                            ? machineContainer.removeItem(mappedSlot, amount)
                            : super.remove(amount);
                }

                @Override
                public void set(ItemStack stack) {
                    if (hasRetainedLegacyInput()) {
                        machineContainer.setItem(mappedSlot, stack);
                    } else {
                        super.set(stack);
                    }
                }

                @Override
                public boolean isActive() {
                    MachineDescriptor entry = descriptorAt(mappedSlot);
                    return entry != null
                            && entry.category() != MachineEnergyTable.Category.TRANSFORM
                            && page == CraftingTerminalPage.STATIONS;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (page != CraftingTerminalPage.STATIONS) return false;
                    StorageCoreBlockEntity currentCore = getCore(playerInv.player.level());
                    MachineDescriptor entry = descriptorAt(mappedSlot);
                    if (currentCore == null || currentCore.isConflicted()
                            || entry == null
                            || entry.category() == MachineEnergyTable.Category.TRANSFORM
                            || !entry.accepts(stack)) return false;
                    if (hasRetainedLegacyInput()) return false;
                    return entry.maxInstalledCount() > 0;
                }

                @Override
                public int getMaxStackSize(ItemStack stack) {
                    MachineDescriptor entry = descriptorAt(mappedSlot);
                    if (entry == null) return 0;
                    if (entry.category() == MachineEnergyTable.Category.TRANSFORM) {
                        return Math.min(64, stack.getMaxStackSize());
                    }
                    return entry.maxInstalledCount();
                }

                @Override
                public int getMaxStackSize() {
                    MachineDescriptor entry = descriptorAt(mappedSlot);
                    return entry == null ? 0 : entry.maxInstalledCount();
                }

                @Override
                public boolean mayPickup(Player player) {
                    return page == CraftingTerminalPage.STATIONS && super.mayPickup(player);
                }
            });
        }

        selectionContainer = new SimpleContainer(SELECTION_SLOTS);
        for (int i = 0; i < SELECTION_SLOTS; i++) {
            this.addSlot(new Slot(selectionContainer, i, -9999, -9999) {
                @Override public boolean isActive() { return false; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
    }

    public ItemStack getSelectedStack() {
        return selectionContainer != null
                ? selectionContainer.getItem(PRESENTATION_OUTPUT_SLOT)
                : ItemStack.EMPTY;
    }

    public List<ItemStack> getMissingPreview() {
        List<ItemStack> missing = new ArrayList<>();
        for (IngredientPreview ingredient : getIngredientPreview()) {
            if (ingredient.available() < ingredient.required()) {
                missing.add(TerminalDisplayStack.create(
                        ingredient.stack(), ingredient.required()));
            }
        }
        return missing;
    }

    public List<IngredientPreview> getIngredientPreview() {
        List<IngredientPreview> result = new ArrayList<>();
        if (selectionContainer == null) return result;
        for (int ingredient = 0; ingredient < MAX_INGREDIENTS; ingredient++) {
            ItemStack stack = selectionContainer.getItem(ITEM_RESOURCE_SLOT_START + ingredient);
            if (stack.isEmpty()) continue;
            long required = TerminalDisplayStack.amount(stack);
            result.add(new IngredientPreview(
                    TerminalDisplayStack.strip(stack).copyWithCount(1),
                    ingredientAvailable[ingredient],
                    required));
        }
        return result;
    }

    public RecipePresentation getRecipePresentation() {
        if (selectionContainer == null) return RecipePresentation.empty();
        RecipePresentation.Metadata metadata = RecipePresentation.metadataFromCarrier(
                selectionContainer.getItem(PRESENTATION_METADATA_SLOT));
        if (metadata == null) return RecipePresentation.empty();

        List<ItemStack> inputs = new ArrayList<>(RecipePresentation.MAX_INPUTS);
        for (int input = 0; input < RecipePresentation.MAX_INPUTS; input++) {
            inputs.add(selectionContainer.getItem(PRESENTATION_INPUT_SLOT_START + input).copy());
        }
        List<RecipePresentation.Resource> resources = new ArrayList<>();
        for (int item = 0; item < metadata.itemResourceCount(); item++) {
            ItemStack stack = selectionContainer.getItem(ITEM_RESOURCE_SLOT_START + item);
            if (stack.isEmpty()) {
                return RecipePresentation.empty();
            }
            resources.add(RecipePresentation.Resource.item(
                    TerminalDisplayStack.strip(stack).copyWithCount(1),
                    ingredientAvailable[item],
                    TerminalDisplayStack.amount(stack)));
        }
        for (EnergyPreview energy : getEnergyPreview()) {
            resources.add(RecipePresentation.Resource.energy(
                    energy.type(), energy.available(), energy.required()));
        }
        if (metadata.stationWorkRequired() > 0) {
            resources.add(RecipePresentation.Resource.stationWork(
                    selectionContainer.getItem(PRESENTATION_STATION_SLOT),
                    metadata.stationWorkAvailable(),
                    metadata.stationWorkRequired()));
        }
        if (metadata.toolRequired() > 0) {
            ItemStack tool = selectionContainer.getItem(PRESENTATION_TOOL_SLOT);
            if (tool.isEmpty()) {
                return RecipePresentation.empty();
            }
            resources.add(RecipePresentation.Resource.tool(
                    tool, metadata.toolAvailable(), metadata.toolRequired(), metadata.toolInfinite()));
        }
        return new RecipePresentation(
                metadata,
                inputs,
                selectionContainer.getItem(PRESENTATION_OUTPUT_SLOT),
                selectionContainer.getItem(PRESENTATION_STATION_SLOT),
                presentationStationVariants(metadata.stationDescriptorId()),
                resources);
    }

    private List<ItemStack> presentationStationVariants(ResourceLocation descriptorId) {
        ItemStack installed = selectionContainer.getItem(PRESENTATION_STATION_SLOT);
        MachineDescriptor descriptor = descriptorSnapshot.stream()
                .filter(candidate -> candidate.id().equals(descriptorId))
                .findFirst()
                .orElse(null);
        if (descriptor == null) return List.of(installed);
        List<ItemStack> ordered = new ArrayList<>();
        ordered.add(installed.copyWithCount(1));
        for (MachineVariant variant : descriptor.variants()) {
            ItemStack stack = variant.stack();
            if (!stack.is(installed.getItem())) ordered.add(stack);
        }
        return List.copyOf(ordered);
    }

    public List<EnergyPreview> getEnergyPreview() {
        List<EnergyPreview> result = new ArrayList<>(2);
        EnergyType processType = getCurrentProcessEnergyType();
        if (processType != null && processRequired > 0) {
            result.add(new EnergyPreview(processType, getEnergyAmount(processType), processRequired));
        }
        if (fuelRequired > 0) {
            result.add(new EnergyPreview(
                    EnergyType.FURNACE_FUEL, getEnergyAmount(EnergyType.FURNACE_FUEL), fuelRequired));
        }
        return result;
    }

    private EnergyType getCurrentProcessEnergyType() {
        return switch (currentRecipeTypeOrder) {
            case 1 -> EnergyType.SMELTING_ENERGY;
            case 2 -> EnergyType.BLASTING_ENERGY;
            case 3 -> EnergyType.SMOKING_ENERGY;
            case 4 -> EnergyType.CAMPFIRE_ENERGY;
            default -> null;
        };
    }

    public int getCraftableCount() {
        return craftableCount;
    }

    public int getCurrentRecipeIndex() {
        return currentRecipeIndex;
    }

    public int getRecipeCount() {
        return recipeCount;
    }

    public int getCraftableRecipeCount() {
        return craftableRecipeCount;
    }

    public String getCurrentRecipeTypeLabel() {
        return switch (currentRecipeTypeOrder) {
            case 0 -> "Crafting";
            case 1 -> "Smelting";
            case 2 -> "Blasting";
            case 3 -> "Smoking";
            case 4 -> "Campfire";
            case 5 -> "Stonecutting";
            case 6 -> "Smithing";
            case 7 -> "Axe";
            default -> "No recipe";
        };
    }

    public boolean isUsePlayerInventory() {
        return usePlayerInventory;
    }

    public TerminalOutputDestination getOutputDestination() {
        return outputDestination;
    }

    public boolean isSelectedOutputStorageOnly() {
        StorageResourceKey key = TerminalResourceDisplay.key(getSelectedStack()).orElse(null);
        return key != null && !key.kindId().equals(StorageResourceKindApi.ITEM_KIND);
    }

    public CraftingTerminalPage getPage() {
        return page;
    }

    public EnergyType getSelectedFuelTarget() {
        return TransformProviderApi.energyType(selectedTransformTarget).orElse(null);
    }

    public ResourceLocation getSelectedTransformTarget() {
        return selectedTransformTarget;
    }

    @Override
    public TerminalPreferences getTerminalPreferences() {
        TerminalPreferences common = super.getTerminalPreferences();
        return new TerminalPreferences(
                common.sortMode(),
                common.sortOrder(),
                common.searchMode(),
                common.resourceView(),
                page,
                usePlayerInventory,
                outputDestination,
                getSelectedFuelTarget(),
                selectedTransformTarget);
    }

    public long getEnergyAmount(EnergyType type) {
        return energyAmounts[type.ordinal()];
    }

    public long getAxeEnergyAmount() {
        return getDescriptorAmount(MachineEnergyTable.AXE_ID);
    }

    public boolean hasInfiniteAxeEnergy() {
        return hasInfiniteDescriptor(MachineEnergyTable.AXE_ID);
    }

    public List<MachineDescriptor> getMachineDescriptors() {
        return descriptorSnapshot;
    }

    private MachineDescriptor descriptorAt(int slot) {
        return slot >= 0 && slot < descriptorSnapshot.size() ? descriptorSnapshot.get(slot) : null;
    }

    private int findDescriptorSlot(ItemStack stack) {
        for (int slot = 0; slot < descriptorSnapshot.size(); slot++) {
            if (descriptorSnapshot.get(slot).accepts(stack)) return slot;
        }
        return -1;
    }

    public long getDescriptorAmount(ResourceLocation descriptorId) {
        MachineDescriptorStatePacket.State state = descriptorStates.get(descriptorId);
        if (state != null) return state.amount();
        return descriptorId.equals(MachineEnergyTable.AXE_ID) ? axeEnergyAmount : 0;
    }

    public boolean hasInfiniteDescriptor(ResourceLocation descriptorId) {
        MachineDescriptorStatePacket.State state = descriptorStates.get(descriptorId);
        if (state != null) return state.infinite();
        return descriptorId.equals(MachineEnergyTable.AXE_ID) && infiniteAxeEnergy;
    }

    void applyDescriptorStates(List<MachineDescriptorStatePacket.State> states) {
        descriptorStates.clear();
        for (MachineDescriptorStatePacket.State state : states) {
            if (descriptorSnapshot.stream().anyMatch(descriptor -> descriptor.id().equals(state.descriptorId()))) {
                descriptorStates.put(state.descriptorId(), state);
            }
        }
    }

    static int fuelTargetButtonId(EnergyType target) {
        int index = FUEL_TARGETS.indexOf(target);
        if (index < 0) throw new IllegalArgumentException("Not a fuel target: " + target);
        return TransformProviderApi.LEGACY_FUEL_BUTTON_BASE + index;
    }

    static List<EnergyType> fuelTargets() {
        return FUEL_TARGETS;
    }

    public List<TransformProviderApi.Target> getTransformTargets() {
        return TransformProviderApi.targets(descriptorSnapshot);
    }

    public List<TransformProviderApi.Use> getTransformUses() {
        return TransformProviderApi.uses(
                getSlot(FUEL_INPUT_SLOT).getItem(), descriptorSnapshot);
    }

    public List<TransformProviderApi.Use> getVisibleTransformUses() {
        return getTransformUses().stream()
                .filter(use -> selectedTransformTarget == null
                        || use.targetId().equals(selectedTransformTarget))
                .sorted(TerminalEntryComparator.forMode(
                        getSortMode(),
                        getSortOrder(),
                        TransformProviderApi::sortStack))
                .toList();
    }

    public TransformProviderApi.Use getSelectedTransformUse() {
        if (selectedTransformUseId == null) return null;
        return getVisibleTransformUses().stream()
                .filter(use -> use.id().equals(selectedTransformUseId))
                .findFirst()
                .orElse(null);
    }

    public static int transformUseButtonId(int visibleIndex) {
        if (visibleIndex < 0) {
            throw new IllegalArgumentException("Transform use index must be non-negative");
        }
        return TRANSFORM_USE_BUTTON_BASE + visibleIndex;
    }

    private void onFuelInputChanged() {
        if (processingFuelInput || playerInventory.player.level().isClientSide()) return;
        selectedTransformUseId = null;
        updateTransformPreview(getCore(playerInventory.player.level()));
    }

    private void onConsumableInputsChanged() {
        if (processingConsumableInput) return;
    }

    private boolean transformInput(int requested, Player player) {
        if (requested <= 0 || page != CraftingTerminalPage.TRANSFORM) return false;
        StorageCoreBlockEntity core = getCore(player.level());
        Slot source = getSlot(FUEL_INPUT_SLOT);
        ItemStack stack = source.getItem();
        if (core == null || stack.isEmpty()) return false;
        int available = transformableCount(core, stack);
        int amount = requested == Integer.MAX_VALUE ? available : requested;
        if (amount <= 0 || amount > available || amount > stack.getCount()) return false;
        ItemStack converted = stack.copyWithCount(amount);
        TransformProviderApi.Use use = getSelectedTransformUse();
        if (use == null || !commitTransform(core, use, converted)) return false;

        ItemStack remainder = stack.hasCraftingRemainingItem()
                ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
        processingFuelInput = true;
        try {
            stack.shrink(amount);
            source.setChanged();
            if (!remainder.isEmpty()) {
                returnFuelRemaindersToPlayer(remainder, amount, player);
            }
        } finally {
            processingFuelInput = false;
        }
        refreshEnergyAmounts(core);
        updateTransformPreview(core);
        return true;
    }

    private void updateTransformPreview(@Nullable StorageCoreBlockEntity core) {
        if (page != CraftingTerminalPage.TRANSFORM || core == null) {
            craftableCount = 0;
            return;
        }
        craftableCount = transformableCount(
                core, getSlot(FUEL_INPUT_SLOT).getItem());
    }

    private int transformableCount(StorageCoreBlockEntity core, ItemStack input) {
        if (input.isEmpty()) return 0;
        TransformProviderApi.Use use = getSelectedTransformUse();
        if (use == null) return 0;
        int low = 0;
        int high = input.getCount();
        while (low < high) {
            int candidate = low + (high - low) / 2 + (high - low) % 2;
            if (canCommitTransform(core, use, input.copyWithCount(candidate))) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return low;
    }

    private boolean canCommitTransform(
            StorageCoreBlockEntity core,
            TransformProviderApi.Use use,
            ItemStack input
    ) {
        MachineDescriptor descriptor = transformDescriptor(use);
        if (descriptor != null) {
            return core.canAddDescriptorTransform(descriptor.id(), input);
        }
        StorageResourceTransaction transaction = transformTransaction(core, use, input);
        return transaction != null
                && core.applyResourceTransaction(transaction, Action.SIMULATE, Actor.EMPTY);
    }

    private boolean commitTransform(
            StorageCoreBlockEntity core,
            TransformProviderApi.Use use,
            ItemStack input
    ) {
        MachineDescriptor descriptor = transformDescriptor(use);
        if (descriptor != null) return core.addDescriptorTransform(descriptor.id(), input);
        StorageResourceTransaction transaction = transformTransaction(core, use, input);
        return transaction != null
                && core.applyResourceTransaction(transaction, Action.SIMULATE, Actor.EMPTY)
                && core.applyResourceTransaction(transaction, Action.EXECUTE, Actor.EMPTY);
    }

    @Nullable
    private MachineDescriptor transformDescriptor(TransformProviderApi.Use use) {
        return descriptorSnapshot.stream()
                .filter(candidate -> candidate.id().equals(use.id())
                        && candidate.category() == MachineEnergyTable.Category.TRANSFORM)
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private StorageResourceTransaction transformTransaction(
            StorageCoreBlockEntity core,
            TransformProviderApi.Use use,
            ItemStack input
    ) {
        if (use.stationId() != null && !core.isMachineInstalled(use.stationId())) return null;

        long output;
        long stationWork;
        try {
            output = Math.multiplyExact(use.amountPerItem(), (long) input.getCount());
            stationWork = Math.multiplyExact(use.stationWorkPerItem(), (long) input.getCount());
        } catch (ArithmeticException exception) {
            return null;
        }
        StorageResourceTransaction.Builder transaction =
                StorageResourceTransaction.builder().add(use.output(), output);
        if (stationWork > 0) {
            transaction.add(
                    StorageResourceBridge.stationWorkKey(use.stationId()),
                    -stationWork);
        }
        StorageResourceTransaction exact;
        try {
            exact = transaction.build();
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return null;
        }
        return exact;
    }

    private static void returnFuelRemaindersToPlayer(
            ItemStack template,
            int count,
            Player player
    ) {
        int remaining = count;
        while (remaining > 0) {
            int amount = Math.min(remaining, template.getMaxStackSize());
            ItemStack stack = template.copyWithCount(amount);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
            remaining -= amount;
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        if (slotIndex == FUEL_INPUT_SLOT && page != CraftingTerminalPage.TRANSFORM) return;
        if (slotIndex >= MACHINE_SLOT_START && slotIndex < MACHINE_SLOT_END
                && page != CraftingTerminalPage.STATIONS) return;
        if (slotIndex >= 0 && slotIndex < DISPLAY_SLOTS) {
            if (!page.isItemPage()) return;
            if (clickType == ClickType.QUICK_MOVE && (button == 0 || button == 1)) {
                if (page == CraftingTerminalPage.STORAGE) {
                    super.clicked(slotIndex, button, clickType, player);
                }
                return;
            }
            if (clickType != ClickType.PICKUP || button < 0 || button > 1) return;
            if (!player.level().isClientSide()) {
                Slot slot = getSlot(slotIndex);
                ItemStack displayStack = slot.getItem();
                if (TerminalResourceDisplay.isTyped(displayStack)
                        && page != CraftingTerminalPage.CRAFTABLE) return;
                if (!displayStack.isEmpty() && getCarried().isEmpty()) {
                    selectOutput(player.level(), displayStack);
                    StorageCoreBlockEntity core = getCore(player.level());
                    if (core != null) updatePreview(core, player);
                    broadcastChanges();
                }
            }
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = getSlot(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        if (index == FUEL_INPUT_SLOT) {
            ItemStack original = slot.getItem().copy();
            ItemStack stack = slot.getItem();
            if (!moveItemStackTo(stack, DISPLAY_SLOTS, FUEL_INPUT_SLOT, false)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            return original;
        }

        if (index >= MACHINE_SLOT_START && index < MACHINE_SLOT_END) {
            if (page != CraftingTerminalPage.STATIONS) return ItemStack.EMPTY;
            ItemStack original = slot.getItem().copy();
            ItemStack stack = slot.getItem();
            if (!moveMachineStackToPlayer(stack)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            broadcastChanges();
            return original;
        }

        if (page == CraftingTerminalPage.TRANSFORM) {
            if (index < DISPLAY_SLOTS || index >= FUEL_INPUT_SLOT) return ItemStack.EMPTY;
            ItemStack original = slot.getItem().copy();
            ItemStack stack = slot.getItem();
            if (!moveItemStackTo(
                    stack, FUEL_INPUT_SLOT, FUEL_INPUT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            return original;
        }

        if (page == CraftingTerminalPage.STATIONS) {
            if (index < DISPLAY_SLOTS || index >= FUEL_INPUT_SLOT) return ItemStack.EMPTY;
            ItemStack original = slot.getItem().copy();
            if (!player.level().isClientSide()) {
                StorageCoreBlockEntity core = getCore(player.level());
                int machineSlot = findDescriptorSlot(slot.getItem());
                if (machineSlot >= 0) {
                    if (core == null || core.isConflicted()) return ItemStack.EMPTY;
                    ItemStack stack = slot.getItem();
                    MachineDescriptor entry = descriptorAt(machineSlot);
                    if (entry == null
                            || entry.category() == MachineEnergyTable.Category.TRANSFORM) {
                        return ItemStack.EMPTY;
                    }
                    if (!moveStackToMachineSlot(stack, machineSlot)) return ItemStack.EMPTY;
                    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
                    else slot.setChanged();
                    broadcastChanges();
                    return original;
                }
            }
            return ItemStack.EMPTY;
        }

        if (page == CraftingTerminalPage.CRAFTABLE && index < DISPLAY_SLOTS) {
            return ItemStack.EMPTY;
        }

        return super.quickMoveStack(player, index);
    }

    private boolean moveMachineStackToPlayer(ItemStack source) {
        boolean moved = false;
        while (!source.isEmpty()) {
            int chunkSize = Math.min(source.getCount(), source.getMaxStackSize());
            ItemStack chunk = source.copyWithCount(chunkSize);
            if (!moveItemStackTo(chunk, DISPLAY_SLOTS, FUEL_INPUT_SLOT, false)) break;
            int transferred = chunkSize - chunk.getCount();
            if (transferred <= 0) break;
            source.shrink(transferred);
            moved = true;
        }
        return moved;
    }

    private boolean moveStackToMachineSlot(ItemStack source, int machineSlot) {
        Slot target = getSlot(MACHINE_SLOT_START + machineSlot);
        if (!target.mayPlace(source)) return false;
        ItemStack installed = target.getItem();
        if (!installed.isEmpty() && !ItemStack.isSameItemSameComponents(installed, source)) {
            return false;
        }
        int room = target.getMaxStackSize(source) - installed.getCount();
        if (room <= 0) return false;
        int moved = Math.min(room, source.getCount());
        if (installed.isEmpty()) {
            target.setByPlayer(source.split(moved));
        } else {
            source.shrink(moved);
            installed.grow(moved);
            target.setChanged();
        }
        return true;
    }

    private void selectOutput(Level level, ItemStack stack) {
        ItemStack identity = TerminalDisplayStack.strip(stack);
        selectedRecipeId = null;
        selectedOutput = identity.copyWithCount(1);
        selectionContainer.setItem(PRESENTATION_OUTPUT_SLOT, selectedOutput.copy());
        lookUpRecipes(level, identity);
    }

    public boolean selectRecipe(Level level, int displaySlot, ResourceLocation recipeId, Player player) {
        if (!page.isItemPage() || level.isClientSide()
                || displaySlot < 0 || displaySlot >= DISPLAY_SLOTS) return false;
        StorageCoreBlockEntity core = getCore(level);
        if (core == null) return false;
        ItemStack displayStack = getSlot(displaySlot).getItem();
        if (displayStack.isEmpty()) return false;
        if (page == CraftingTerminalPage.CRAFTABLE) {
            if (!isCraftableOutput(core, displayStack, player)) return false;
        } else if (TerminalResourceDisplay.isTyped(displayStack)
                || core.getItemCount(ItemKey.of(displayStack)) <= 0) {
            return false;
        }

        List<IngredientSource> sources = snapshotIngredientSources(core, player);
        ItemStack requestedOutput = TerminalDisplayStack.strip(displayStack);
        RecipeAdapterMatch match = resolveAvailableRecipeVariantById(
                level, core, recipeId, requestedOutput, sources);
        if (match == null) return false;
        if (!matchesSelectionOutput(match, requestedOutput, level)) return false;
        return selectRecipeById(level, recipeId, requestedOutput, player, core);
    }

    public boolean handleRecipeRequest(
            Level level,
            ResourceLocation recipeId,
            int amount,
            CraftingDestination destination,
            Player player
    ) {
        if (!page.isItemPage() || level.isClientSide()
                || destination == null || amount <= 0 || amount > MAX_RECIPE_REQUEST) return false;
        StorageCoreBlockEntity core = getCore(level);
        if (core == null || core.isConflicted()) return false;
        List<IngredientSource> sources = snapshotIngredientSources(core, player);
        ItemStack requestedOutput = selectedRecipeId != null
                && selectedRecipeId.equals(recipeId)
                && !selectedOutput.isEmpty()
                ? selectedOutput.copy()
                : ItemStack.EMPTY;
        RecipeAdapterMatch match = resolveAvailableRecipeVariantById(
                level, core, recipeId, requestedOutput, sources);
        if (match == null) return false;
        ItemStack result = selectionDisplay(match, level, 0);
        if (result.isEmpty()) return false;
        if (destination == CraftingDestination.NONE) {
            return selectRecipeById(level, recipeId, result, player, core);
        }
        if (isNonItemSelection(match) && destination != CraftingDestination.STORAGE) return false;

        CraftPreview preview = computeCraftPreviewFor(match, core, player);
        int maximumCrafts = Math.min(amount, preview.craftable());
        DeliveryTarget deliveryTarget = DeliveryTarget.from(destination);
        int crafts = 0;
        IngredientPlan ingredientPlan = null;
        DeliveryPlan deliveryPlan = null;
        for (int candidate = maximumCrafts; candidate > 0; candidate--) {
            CraftPlan candidatePlan = planCraft(
                    core, match, candidate, deliveryTarget, player, sources);
            if (candidatePlan != null) {
                crafts = candidate;
                ingredientPlan = candidatePlan.ingredients();
                deliveryPlan = candidatePlan.delivery();
                break;
            }
        }
        if (crafts <= 0 || ingredientPlan == null || deliveryPlan == null) return false;
        if (!commitCraft(
                core, ingredientPlan, deliveryPlan, player, match, crafts)) return false;
        selectRecipeById(level, recipeId, result, player, core);
        refreshDisplayItems(core);
        updatePreview(core, player);
        broadcastChanges();
        return true;
    }

    private boolean selectRecipeById(
            Level level,
            ResourceLocation recipeId,
            ItemStack requestedOutput,
            Player player,
            StorageCoreBlockEntity core
    ) {
        List<IngredientSource> sources = snapshotIngredientSources(core, player);
        RecipeAdapterMatch match = resolveAvailableRecipeVariantById(
                level, core, recipeId, requestedOutput, sources);
        if (match == null) return false;
        ItemStack result = selectionDisplay(match, level, 0);
        if (result.isEmpty()) return false;

        selectOutput(level, result);
        for (int i = 0; i < currentRecipes.size(); i++) {
            if (!currentRecipes.get(i).id().equals(recipeId)) continue;
            selectedRecipeId = recipeId;
            currentRecipeIndex = i;
            syncRecipeMetadata();
            updatePreview(core, player);
            broadcastChanges();
            return true;
        }
        clearSelection();
        return false;
    }

    private void clearSelection() {
        selectedOutput = ItemStack.EMPTY;
        selectedRecipeId = null;
        selectionContainer.clearContent();
        currentRecipes.clear();
        currentRecipeIndex = 0;
        recipeCount = 0;
        craftableRecipeCount = 0;
        currentRecipeTypeOrder = -1;
        craftableCount = 0;
        Arrays.fill(ingredientAvailable, 0);
        processRequired = 0;
        fuelRequired = 0;
    }

    void lookUpRecipes(Level level, ItemStack output) {
        output = TerminalDisplayStack.strip(output);
        selectedRecipeId = null;
        currentRecipes.clear();
        currentRecipeIndex = 0;
        dirtyRecipes = false;
        lastCheckedItem = ItemStack.hashItemAndComponents(output);
        RecipeManager manager = level.getRecipeManager();
        StorageCoreBlockEntity core = getCore(level);
        if (core == null) {
            syncRecipeMetadata();
            return;
        }
        List<IngredientSource> sources = snapshotIngredientSources(
                core, playerInventory == null ? null : playerInventory.player);

        for (RecipeType<?> type : BuiltInRecipeAdapters.discoveryTypes()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Collection<RecipeHolder<?>> holders = (Collection) manager.getAllRecipesFor((RecipeType) type);
            for (RecipeHolder<?> holder : holders) {
                RecipeAdapterMatch match = resolveAvailableRecipeLookupVariant(
                        holder, core, output, sources, level);
                if (match != null) {
                    currentRecipes.add(holder);
                }
            }
        }
        for (RecipeHolder<?> holder : axeTransformationCatalog.recipes(level, core)) {
            RecipeAdapterMatch match = classifyAvailable(holder, core);
            if (match == null) continue;
            ItemStack result = match.presentationOutput(List.of(), level);
            if (!result.isEmpty() && ItemStack.isSameItemSameComponents(result, output)) {
                currentRecipes.add(holder);
            }
        }

        rerankCurrentRecipes(core, playerInventory.player, sources, output, null);
        syncRecipeMetadata();
    }

    private static int getRecipeSortOrder(RecipeHolder<?> holder) {
        return BuiltInRecipeAdapters.registry().classify(holder)
                .map(match -> match.adapter().priority())
                .orElse(99);
    }

    private void rerankCurrentRecipes(
            StorageCoreBlockEntity core,
            Player player,
            List<IngredientSource> sources,
            ItemStack requestedOutput,
            ResourceLocation selectedId
    ) {
        Map<ResourceLocation, Boolean> craftable = new HashMap<>();
        for (int index = 0; index < currentRecipes.size(); index++) {
            RecipeHolder<?> holder = currentRecipes.get(index);
            RecipeAdapterMatch match = resolveAvailableRecipeVariantById(
                    core.getLevel(), core, holder.id(), requestedOutput, sources);
            boolean canCraft = match != null
                    && computeCraftPreviewFor(match, core, sources, 1).craftable() > 0
                    && planCraft(
                    core, match, 1, directDeliveryTarget(match), player, sources) != null;
            craftable.put(holder.id(), canCraft);
            if (match != null) currentRecipes.set(index, match.holder());
        }
        currentRecipes.sort(Comparator
                .comparing((RecipeHolder<?> holder) ->
                        !craftable.getOrDefault(holder.id(), false))
                .thenComparingInt(CraftingTerminalMenu::getRecipeSortOrder)
                .thenComparing(holder -> holder.id().toString()));
        recipeCount = currentRecipes.size();
        craftableRecipeCount = (int) craftable.values().stream()
                .filter(Boolean::booleanValue)
                .count();
        currentRecipeIndex = 0;
        if (selectedId != null) {
            for (int index = 0; index < currentRecipes.size(); index++) {
                if (currentRecipes.get(index).id().equals(selectedId)) {
                    currentRecipeIndex = index;
                    break;
                }
            }
        }
    }

    public List<RecipeHolder<?>> getCurrentRecipes() {
        return currentRecipes;
    }

    public void prevRecipe() {
        if (currentRecipes.isEmpty()) return;
        currentRecipeIndex = (currentRecipeIndex - 1 + currentRecipes.size()) % currentRecipes.size();
        syncRecipeMetadata();
    }

    public void nextRecipe() {
        if (currentRecipes.isEmpty()) return;
        currentRecipeIndex = (currentRecipeIndex + 1) % currentRecipes.size();
        syncRecipeMetadata();
    }

    private void syncRecipeMetadata() {
        recipeCount = currentRecipes.size();
        if (currentRecipes.isEmpty() || currentRecipeIndex >= currentRecipes.size()) {
            currentRecipeTypeOrder = -1;
            clearRecipePresentation();
        } else {
            RecipeHolder<?> cachedHolder = currentRecipes.get(currentRecipeIndex);
            if (selectedRecipeId != null) selectedRecipeId = cachedHolder.id();
            if (!playerInventory.player.level().isClientSide()) {
                StorageCoreBlockEntity core = getCore(playerInventory.player.level());
                List<IngredientSource> sources = core == null
                        ? List.of()
                        : snapshotIngredientSources(core, playerInventory.player);
                ItemStack requestedOutput = selectedOutput.copy();
                RecipeAdapterMatch match = core == null ? null : resolveAvailableRecipeVariantById(
                        playerInventory.player.level(), core, cachedHolder.id(),
                        requestedOutput, sources);
                if (match == null) {
                    clearRecipePresentation();
                } else {
                    currentRecipes.set(currentRecipeIndex, match.holder());
                    currentRecipeTypeOrder = match.adapter().priority();
                    applyPreviewData(emptyCraftPreview());
                    syncRecipePresentation(
                            match,
                            emptyCraftPreview(),
                            snapshotIngredientSources(core, playerInventory.player),
                            core,
                            false);
                }
            }
        }
    }

    public void toggleUsePlayerInventory() {
        usePlayerInventory = !usePlayerInventory;
    }

    public void craftItem(int count, Player player) {
        tryCraftItem(count, player);
    }

    private boolean tryCraftItem(long count, Player player) {
        if (count <= 0 || !page.isItemPage()
                || currentRecipes.isEmpty() || currentRecipeIndex >= currentRecipes.size()) return false;
        if (player.level().isClientSide()) return false;
        StorageCoreBlockEntity core = getCore(player.level());
        if (core == null || core.isConflicted()) return false;

        RecipeAdapterMatch match = resolveCurrentRecipeMatch(player.level());
        if (match == null) return false;
        if (!hasRecipeCostsForCrafts(core, match.cost(), count)) return false;
        List<IngredientSource> sources = snapshotIngredientSources(core, player);
        CraftPlan plan = planCraft(
                core, match, count, directDeliveryTarget(match), player, sources);
        if (plan == null || !commitCraft(
                core,
                plan.ingredients(),
                plan.delivery(),
                player,
                match,
                plan.crafts())) return false;

        refreshDisplayItems(core);
        updatePreview(core, player);
        broadcastChanges();
        return true;
    }

    private void craftMaximum(Player player) {
        if (!page.isItemPage() || player.level().isClientSide()) return;
        StorageCoreBlockEntity core = getCore(player.level());
        if (core == null || core.isConflicted()) return;
        RecipeAdapterMatch match = resolveCurrentRecipeMatch(player.level());
        if (match == null) return;
        List<IngredientSource> sources = snapshotIngredientSources(core, player);
        long resourceMaximum = maximumResourceCrafts(match, core, sources);
        CraftPlan plan = largestDeliverablePlan(
                core, match, resourceMaximum, directDeliveryTarget(match), player, sources);
        if (plan == null || !commitCraft(
                core,
                plan.ingredients(),
                plan.delivery(),
                player,
                match,
                plan.crafts())) return;
        refreshDisplayItems(core);
        updatePreview(core, player);
        broadcastChanges();
    }

    private DeliveryTarget directDeliveryTarget(RecipeAdapterMatch match) {
        return isNonItemSelection(match)
                ? DeliveryTarget.STORAGE
                : DeliveryTarget.from(outputDestination);
    }

    private CraftPlan largestDeliverablePlan(
            StorageCoreBlockEntity core,
            RecipeAdapterMatch match,
            long maximum,
            DeliveryTarget destination,
            Player player,
            List<IngredientSource> sources
    ) {
        if (maximum <= 0) return null;
        CraftPlan maximumPlan = planCraft(core, match, maximum, destination, player, sources);
        if (maximumPlan != null) return maximumPlan;

        long low = 0;
        long high = maximum - 1;
        CraftPlan best = null;
        while (low < high) {
            long distance = high - low;
            long candidate = low + distance / 2 + distance % 2;
            CraftPlan candidatePlan = planCraft(core, match, candidate, destination, player, sources);
            if (candidatePlan != null) {
                low = candidate;
                best = candidatePlan;
            } else {
                high = candidate - 1;
            }
        }
        if (low <= 0) return null;
        return best != null && best.crafts() == low
                ? best
                : planCraft(core, match, low, destination, player, sources);
    }

    private CraftPlan planCraft(
            StorageCoreBlockEntity core,
            RecipeAdapterMatch match,
            long crafts,
            DeliveryTarget destination,
            Player player,
            List<IngredientSource> sources
    ) {
        if (crafts <= 0 || !hasRecipeCostsForCrafts(core, match.cost(), crafts)) return null;
        TypedRecipePlan typedPlan = match.typedRecipePlan().orElse(null);
        TypedConsumption typedConsumption = typedPlan == null
                ? new TypedConsumption(Map.of(), Map.of(), Map.of())
                : planTypedConsumption(core, typedPlan, crafts, sources);
        if (typedPlan != null && (!hasRetainedTypedInputs(core, typedPlan, sources)
                || typedConsumption == null)) return null;
        IngredientPlan ingredients = typedPlan == null
                ? planIngredients(match.orderedInputs(), crafts, sources)
                : new IngredientPlan(
                        Map.of(), typedConsumption.playerReservations(), List.of());
        if (ingredients == null) return null;
        DeliveryPlan delivery = planDelivery(
                core, ingredients, typedConsumption, match, crafts, destination, player);
        return delivery == null ? null : new CraftPlan(crafts, ingredients, delivery);
    }

    private long maximumResourceCrafts(
            RecipeAdapterMatch match,
            StorageCoreBlockEntity core,
            List<IngredientSource> sources
    ) {
        TypedRecipePlan typedPlan = match.typedRecipePlan().orElse(null);
        if (typedPlan != null) {
            long high = maximumTypedCrafts(core, typedPlan, sources, Long.MAX_VALUE);
            RecipeAdapterMatch.ToolCost toolCost = match.cost().toolCost().orElse(null);
            if (toolCost != null) {
                long available = core.hasInfiniteDescriptor(toolCost.descriptorId())
                        ? Long.MAX_VALUE
                        : core.getDescriptorAmount(toolCost.descriptorId());
                high = Math.min(high, available / toolCost.amountPerCraft());
            }
            EnergyCost cost = match.cost().energyCost().orElse(null);
            if (cost != null) {
                if (cost.processAmount() > 0) {
                    high = Math.min(high, core.getEnergy(cost.processType()) / cost.processAmount());
                }
                if (cost.fuelAmount() > 0) {
                    high = Math.min(high, core.getEnergy(cost.fuelType()) / cost.fuelAmount());
                }
            }
            RecipeAdapterMatch.StationWorkCost stationWork =
                    match.cost().stationWorkCost().orElse(null);
            if (stationWork != null) {
                high = Math.min(high,
                        core.getStationWork(stationWork.descriptorId()) / stationWork.amountPerCraft());
            }
            return high;
        }
        List<RecipeAdapterMatch.Input> ingredients = match.orderedInputs();
        long totalAvailable = 0;
        for (IngredientSource source : sources) {
            totalAvailable = saturatingAdd(totalAvailable, source.amount());
        }
        long ingredientsPerCraft = ingredients.stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .mapToLong(RecipeAdapterMatch.Input::multiplicity)
                .sum();
        if (ingredientsPerCraft <= 0) return 0;
        long high = totalAvailable / ingredientsPerCraft;
        for (IngredientNeed need : summarizeIngredients(ingredients)) {
            long available = 0;
            for (IngredientSource source : sources) {
                if (need.ingredient().test(source.stack())) {
                    available = saturatingAdd(available, source.amount());
                }
            }
            high = Math.min(high, available / need.count());
        }
        RecipeAdapterMatch.ToolCost toolCost = match.cost().toolCost().orElse(null);
        if (toolCost != null) {
            long available = core.hasInfiniteDescriptor(toolCost.descriptorId())
                    ? Long.MAX_VALUE
                    : core.getDescriptorAmount(toolCost.descriptorId());
            high = Math.min(high, available / toolCost.amountPerCraft());
        }
        EnergyCost cost = match.cost().energyCost().orElse(null);
        if (cost != null) {
            if (cost.processAmount() > 0) {
                high = Math.min(high, core.getEnergy(cost.processType()) / cost.processAmount());
            }
            if (cost.fuelAmount() > 0) {
                high = Math.min(high, core.getEnergy(cost.fuelType()) / cost.fuelAmount());
            }
        }
        RecipeAdapterMatch.StationWorkCost stationWork =
                match.cost().stationWorkCost().orElse(null);
        if (stationWork != null) {
            high = Math.min(high,
                    core.getStationWork(stationWork.descriptorId()) / stationWork.amountPerCraft());
        }
        if (high <= 0) return 0;

        long low = 0;
        while (low < high) {
            long distance = high - low;
            long candidate = low + distance / 2 + distance % 2;
            if (planIngredients(ingredients, candidate, sources) != null) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        return low;
    }

    private static long maximumTypedCrafts(
            StorageCoreBlockEntity core,
            TypedRecipePlan plan,
            List<IngredientSource> sources,
            long craftLimit
    ) {
        long upperBound = craftLimit;
        boolean hasConsumedInput = false;
        for (TypedRecipeInput input : plan.inputs()) {
            long available = typedInputAvailable(core, input, sources);
            if (input.role() == TypedRecipeInput.Role.CONSUME) {
                hasConsumedInput = true;
                upperBound = Math.min(upperBound, available / input.amount());
            } else if (available < input.amount()) {
                return 0;
            }
        }
        if (!hasConsumedInput || upperBound == 0) return hasConsumedInput ? 0 : Long.MAX_VALUE;
        long low = 0;
        long high = upperBound;
        while (low < high) {
            long difference = high - low;
            long candidate = low + difference / 2 + difference % 2;
            if (planTypedConsumption(core, plan, candidate, sources) != null) low = candidate;
            else high = candidate - 1;
        }
        return low;
    }

    private static boolean hasTypedInputs(
            StorageCoreBlockEntity core,
            TypedRecipePlan plan,
            long crafts,
            List<IngredientSource> sources
    ) {
        if (crafts <= 0) return false;
        return hasRetainedTypedInputs(core, plan, sources)
                && planTypedConsumption(core, plan, crafts, sources) != null;
    }

    private static boolean hasRetainedTypedInputs(
            StorageCoreBlockEntity core,
            TypedRecipePlan plan,
            List<IngredientSource> sources
    ) {
        for (TypedRecipeInput input : plan.inputs()) {
            if (input.role() != TypedRecipeInput.Role.CONSUME
                    && typedInputAvailable(core, input, sources) < input.amount()) return false;
        }
        return true;
    }

    private static long typedInputAvailable(
            StorageCoreBlockEntity core,
            TypedRecipeInput input,
            List<IngredientSource> sources
    ) {
        long available = 0;
        for (StorageResourceKey alternative : input.alternatives()) {
            available = saturatingAdd(
                    available, typedResourceAvailable(core, alternative, sources));
        }
        return available;
    }

    private static StorageResourceKey typedInputRepresentativeKey(
            StorageCoreBlockEntity core,
            TypedRecipeInput input,
            List<IngredientSource> sources
    ) {
        for (StorageResourceKey alternative : input.alternatives()) {
            if (typedResourceAvailable(core, alternative, sources) > 0) return alternative;
        }
        return input.key();
    }

    private static long typedResourceAvailable(
            StorageCoreBlockEntity core,
            StorageResourceKey key,
            List<IngredientSource> sources
    ) {
        if (!key.kindId().equals(StorageResourceKindApi.ITEM_KIND)) {
            return core.getResourceAmount(key);
        }
        Level level = core.getLevel();
        if (level == null) return 0;
        ItemKey itemKey = StorageResourceBridge.itemKey(
                key, level.registryAccess()).orElse(null);
        if (itemKey == null) return 0;
        long available = 0;
        for (IngredientSource source : sources) {
            if (source.key().equals(itemKey)) {
                available = saturatingAdd(available, source.amount());
            }
        }
        return available;
    }

    private static boolean hasEnergyForCrafts(
            StorageCoreBlockEntity core,
            EnergyCost energyCost,
            long crafts
    ) {
        if (crafts <= 0) return false;
        if (energyCost == null) return true;
        try {
            long processNeed = Math.multiplyExact(energyCost.processAmount(), crafts);
            long fuelNeed = Math.multiplyExact(energyCost.fuelAmount(), crafts);
            return core.getEnergy(energyCost.processType()) >= processNeed
                    && core.getEnergy(energyCost.fuelType()) >= fuelNeed;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    private static boolean hasRecipeCostsForCrafts(
            StorageCoreBlockEntity core,
            RecipeAdapterMatch.Cost cost,
            long crafts
    ) {
        if (crafts <= 0 || !hasEnergyForCrafts(
                core, cost.energyCost().orElse(null), crafts)
                || !hasToolForCrafts(core, cost.toolCost().orElse(null), crafts)) return false;
        RecipeAdapterMatch.StationWorkCost stationWork = cost.stationWorkCost().orElse(null);
        if (stationWork == null) return true;
        try {
            return core.getStationWork(stationWork.descriptorId()) >= Math.multiplyExact(
                    stationWork.amountPerCraft(), crafts);
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private RecipeAdapterMatch resolveCurrentRecipeMatch(Level level) {
        if (currentRecipes.isEmpty() || currentRecipeIndex >= currentRecipes.size()) return null;
        RecipeHolder<?> cachedHolder = currentRecipes.get(currentRecipeIndex);
        StorageCoreBlockEntity core = getCore(level);
        if (core == null) return null;
        List<IngredientSource> sources = snapshotIngredientSources(
                core, playerInventory == null ? null : playerInventory.player);
        ItemStack requestedOutput = selectedOutput.copy();
        RecipeAdapterMatch match = resolveAvailableRecipeVariantById(
                level, core, cachedHolder.id(), requestedOutput, sources);
        if (match == null) return null;

        ItemStack result = match.presentationOutput(List.of(), level);
        if (result.isEmpty()) return null;
        ItemStack requestedSelection = selectedOutput.isEmpty() ? result : selectedOutput;
        if (!matchesSelectionOutput(match, requestedSelection, level)) return null;

        currentRecipes.set(currentRecipeIndex, match.holder());
        return match;
    }

    private RecipeHolder<?> resolveRecipeById(
            Level level,
            StorageCoreBlockEntity core,
            ResourceLocation recipeId
    ) {
        RecipeHolder<?> managed = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (managed != null) return managed;
        return core == null ? null : axeTransformationCatalog.byId(level, core, recipeId);
    }

    public CraftPreview computeCraftPreview(StorageCoreBlockEntity core, Player player) {
        if (core == null || currentRecipes.isEmpty() || currentRecipeIndex >= currentRecipes.size())
            return emptyCraftPreview();
        Level level = core.getLevel();
        if (level == null) return emptyCraftPreview();
        RecipeAdapterMatch match = resolveCurrentRecipeMatch(level);
        if (match == null) return emptyCraftPreview();
        return computeCraftPreviewFor(match, core, player);
    }

    private static CraftPreview emptyCraftPreview() {
        return new CraftPreview(0, List.of(), List.of(), List.of());
    }

    private CraftPreview computeCraftPreviewFor(
            RecipeAdapterMatch match,
            StorageCoreBlockEntity core,
            Player player
    ) {
        return computeCraftPreviewFor(match, core, snapshotIngredientSources(core, player));
    }

    private CraftPreview computeCraftPreviewFor(
            RecipeAdapterMatch match,
            StorageCoreBlockEntity core,
            List<IngredientSource> sources
    ) {
        return computeCraftPreviewFor(match, core, sources, PREVIEW_CAP);
    }

    private CraftPreview computeCraftPreviewFor(
            RecipeAdapterMatch match,
            StorageCoreBlockEntity core,
            List<IngredientSource> sources,
            int craftLimit
    ) {
        if (core.isConflicted() || !match.validatesSimulation(match.holder())
                || !isStationAvailable(core, match)) {
            return emptyCraftPreview();
        }
        long max = craftLimit;
        List<ItemStack> missing = new ArrayList<>();
        List<IngredientPreview> ingredientPreviews = new ArrayList<>();
        List<RecipeAdapterMatch.Input> ingredients = match.orderedInputs();
        TypedRecipePlan typedPlan = match.typedRecipePlan().orElse(null);
        List<IngredientNeed> summarizedNeeds =
                typedPlan == null ? summarizeIngredients(ingredients) : List.of();
        if (typedPlan != null) {
            max = Math.min(max, maximumTypedCrafts(core, typedPlan, sources, craftLimit));
            for (TypedRecipeInput input : summarizeTypedInputs(typedPlan.inputs())) {
                long available = typedInputAvailable(core, input, sources);
                StorageResourceKey key = typedInputRepresentativeKey(core, input, sources);
                ItemStack representative = StorageResourceKinds.representative(
                        key, core.getLevel().registryAccess());
                if (!key.kindId().equals(StorageResourceKindApi.ITEM_KIND)) {
                    representative = TerminalResourceDisplay.create(
                            representative, key, input.amount());
                }
                ingredientPreviews.add(new IngredientPreview(
                        representative, available, input.amount()));
                if (available < input.amount() && missing.size() < MAX_INGREDIENTS) {
                    missing.add(representative.copy());
                }
            }
        } else {
            for (IngredientNeed need : summarizedNeeds) {
                long avail = 0;
                for (IngredientSource source : sources) {
                    if (need.ingredient().test(source.stack())) {
                        avail = avail > Long.MAX_VALUE - source.amount()
                                ? Long.MAX_VALUE
                                : avail + source.amount();
                    }
                }
                max = Math.min(max, avail / need.count());
                ItemStack representative = ingredientRepresentative(need.ingredient(), sources);
                if (!representative.isEmpty()) {
                    ingredientPreviews.add(new IngredientPreview(
                            representative.copyWithCount(1), avail, need.count()));
                }
                if (avail < need.count()) {
                    List<ItemStack> items = need.ingredient().representatives();
                    if (!items.isEmpty() && missing.size() < MAX_INGREDIENTS) {
                        missing.add(items.getFirst().copy());
                    }
                }
            }
        }
        RecipeAdapterMatch.ToolCost toolCost = match.cost().toolCost().orElse(null);
        if (toolCost != null) {
            long available = core.hasInfiniteDescriptor(toolCost.descriptorId())
                    ? Long.MAX_VALUE
                    : core.getDescriptorAmount(toolCost.descriptorId());
            max = Math.min(max, available / toolCost.amountPerCraft());
        }

        RecipeAdapterMatch.StationWorkCost stationWork =
                match.cost().stationWorkCost().orElse(null);
        if (stationWork != null) {
            max = Math.min(max,
                    core.getStationWork(stationWork.descriptorId()) / stationWork.amountPerCraft());
        }

        EnergyCost cost = match.cost().energyCost().orElse(null);
        List<EnergyPreview> energyPreviews = new ArrayList<>(2);
        if (cost != null) {
            energyPreviews.add(new EnergyPreview(
                    cost.processType(), core.getEnergy(cost.processType()), cost.processAmount()));
            energyPreviews.add(new EnergyPreview(
                    cost.fuelType(), core.getEnergy(cost.fuelType()), cost.fuelAmount()));
            if (cost.processAmount() > 0)
                max = Math.min(max, core.getEnergy(cost.processType()) / cost.processAmount());
            if (cost.fuelAmount() > 0)
                max = Math.min(max, core.getEnergy(cost.fuelType()) / cost.fuelAmount());
        }

        int upperBound = (int) Math.clamp(max, 0, craftLimit);
        int craftable;
        if (typedPlan != null) {
            craftable = upperBound;
        } else {
            int low = 0;
            int high = upperBound;
            while (low < high) {
                int candidate = (int) (((long) low + high + 1L) / 2L);
                if (canAllocateIngredients(
                        ingredients, summarizedNeeds, candidate, sources)) {
                    low = candidate;
                } else {
                    high = candidate - 1;
                }
            }
            craftable = low;
        }
        if (craftable == 0 && upperBound > 0 && missing.isEmpty()) {
            for (RecipeAdapterMatch.Input ingredient : ingredients) {
                if (!ingredient.isEmpty() && !ingredient.representatives().isEmpty()) {
                    missing.add(ingredient.representatives().getFirst().copy());
                    break;
                }
            }
        }
        if (craftable > 0) missing.clear();
        return new CraftPreview(craftable, missing, ingredientPreviews, energyPreviews);
    }

    private boolean canAllocateIngredients(
            List<RecipeAdapterMatch.Input> ingredients,
            List<IngredientNeed> needs,
            long crafts,
            List<IngredientSource> sources
    ) {
        int[] sourceMatches = new int[sources.size()];
        for (IngredientNeed need : needs) {
            long required;
            try {
                required = Math.multiplyExact(need.count(), crafts);
            } catch (ArithmeticException exception) {
                return false;
            }
            long available = 0;
            for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
                IngredientSource source = sources.get(sourceIndex);
                if (!need.ingredient().test(source.stack())) continue;
                available = saturatingAdd(available, source.amount());
                sourceMatches[sourceIndex]++;
            }
            if (available < required) return false;
        }
        for (int matches : sourceMatches) {
            if (matches > 1) return planIngredients(ingredients, crafts, sources) != null;
        }
        return true;
    }

    private static List<TypedRecipeInput> summarizeTypedInputs(
            List<TypedRecipeInput> inputs
    ) {
        record Identity(
                List<StorageResourceKey> alternatives,
                TypedRecipeInput.Role role,
                Map<StorageResourceKey, TypedRecipeOutput> remainders
        ) {}
        Map<Identity, Long> amounts = new LinkedHashMap<>();
        for (TypedRecipeInput input : inputs) {
            Identity identity = new Identity(
                    input.alternatives(), input.role(), input.alternativeRemainders());
            amounts.merge(identity, input.amount(), Math::addExact);
        }
        List<TypedRecipeInput> result = new ArrayList<>(amounts.size());
        amounts.forEach((identity, amount) -> result.add(new TypedRecipeInput(
                identity.alternatives(), amount, identity.role(), identity.remainders())));
        return result;
    }

    private static ItemStack ingredientRepresentative(
            RecipeAdapterMatch.Input ingredient,
            List<IngredientSource> sources
    ) {
        for (IngredientSource source : sources) {
            if (ingredient.test(source.stack())) return source.stack().copyWithCount(1);
        }
        List<ItemStack> displayItems = ingredient.representatives();
        if (!displayItems.isEmpty()) return displayItems.getFirst().copyWithCount(1);
        return ItemStack.EMPTY;
    }

    private void syncRecipePresentation(
            RecipeAdapterMatch match,
            CraftPreview preview,
            List<IngredientSource> sources,
            StorageCoreBlockEntity core,
            boolean includeResources
    ) {
        RecipeAdapterMatch.Presentation semantics = match.presentation();
        List<ItemStack> inputs = presentationInputs(match, sources, core.getLevel());
        ItemStack output = match.presentationOutput(inputs, core.getLevel());
        ItemStack station = presentationStation(match, core);
        if (output.isEmpty()) throw new IllegalStateException("Selected recipe has no presentation output");
        StorageResourceKey selectionKey = match.selectionOutputKey(core.getLevel()).orElse(null);
        if (selectionKey != null
                && !selectionKey.kindId().equals(StorageResourceKindApi.ITEM_KIND)) {
            TypedRecipePlan plan = match.typedRecipePlan().orElseThrow();
            output = TerminalResourceDisplay.create(
                    output, selectionKey, plan.selectionOutput().amount());
        }

        selectionContainer.clearContent();
        selectionContainer.setItem(PRESENTATION_OUTPUT_SLOT, output.copy());
        selectionContainer.setItem(PRESENTATION_STATION_SLOT, station.copyWithCount(1));
        for (int input = 0; input < RecipePresentation.MAX_INPUTS; input++) {
            selectionContainer.setItem(
                    PRESENTATION_INPUT_SLOT_START + input, inputs.get(input).copy());
        }

        int itemResourceCount = 0;
        if (includeResources) {
            TypedRecipePlan typedPlan = match.typedRecipePlan().orElse(null);
            List<IngredientNeed> needs = typedPlan == null
                    ? summarizeIngredients(match.orderedInputs()) : List.of();
            itemResourceCount = typedPlan == null ? needs.size() : preview.ingredients().size();
            for (int item = 0; item < itemResourceCount; item++) {
                IngredientPreview resource;
                if (item < preview.ingredients().size()) {
                    resource = preview.ingredients().get(item);
                } else {
                    IngredientNeed need = needs.get(item);
                    long available = 0;
                    for (IngredientSource source : sources) {
                        if (need.ingredient().test(source.stack())) {
                            available = saturatingAdd(available, source.amount());
                        }
                    }
                    resource = new IngredientPreview(
                            ingredientRepresentative(need.ingredient(), sources),
                            available,
                            Math.toIntExact(need.count()));
                }
                if (resource.stack().isEmpty()) {
                    throw new IllegalStateException("Recipe presentation item resource has no representative");
                }
                selectionContainer.setItem(
                        ITEM_RESOURCE_SLOT_START + item,
                        TerminalDisplayStack.create(resource.stack(), resource.required()));
                ingredientAvailable[item] = resource.available();
            }
        }

        long toolAvailable = 0;
        long toolRequired = 0;
        boolean toolInfinite = false;
        RecipeAdapterMatch.ToolCost toolCost = match.cost().toolCost().orElse(null);
        if (toolCost != null) {
            toolInfinite = core.hasInfiniteDescriptor(toolCost.descriptorId());
            toolAvailable = toolInfinite
                    ? Long.MAX_VALUE
                    : core.getDescriptorAmount(toolCost.descriptorId());
            if (toolAvailable <= 0) throw new IllegalStateException("Selected recipe has no tool resource");
            toolRequired = toolCost.amountPerCraft();
            MachineDescriptor toolDescriptor = MachineEnergyTable.get(toolCost.descriptorId());
            if (toolDescriptor == null) throw new IllegalStateException("Selected recipe tool is unavailable");
            selectionContainer.setItem(
                    PRESENTATION_TOOL_SLOT, toolDescriptor.representativeStack());
        }

        RecipePresentation.Metadata metadata = new RecipePresentation.Metadata(
                match.holder().id(),
                match.stationDescriptorId(),
                semantics.kind(),
                semantics.width(),
                semantics.height(),
                semantics.shapeless(),
                itemResourceCount,
                toolAvailable,
                toolRequired,
                toolInfinite,
                match.cost().stationWorkCost()
                        .map(cost -> core.getStationWork(cost.descriptorId()))
                        .orElse(0L),
                match.cost().stationWorkCost()
                        .map(RecipeAdapterMatch.StationWorkCost::amountPerCraft)
                        .orElse(0L));
        selectionContainer.setItem(
                PRESENTATION_METADATA_SLOT, RecipePresentation.metadataCarrier(metadata));
    }

    private void clearRecipePresentation() {
        if (selectionContainer != null) selectionContainer.clearContent();
        if (selectionContainer != null && !selectedOutput.isEmpty()) {
            selectionContainer.setItem(PRESENTATION_OUTPUT_SLOT, selectedOutput.copy());
        }
        Arrays.fill(ingredientAvailable, 0);
        processRequired = 0;
        fuelRequired = 0;
        craftableCount = 0;
    }

    private static List<ItemStack> presentationInputs(
            RecipeAdapterMatch match,
            List<IngredientSource> sources,
            Level level
    ) {
        TypedRecipePlan typedPlan = match.typedRecipePlan().orElse(null);
        if (typedPlan != null) {
            List<ItemStack> inputs = new ArrayList<>(
                    Collections.nCopies(RecipePresentation.MAX_INPUTS, ItemStack.EMPTY));
            for (int input = 0;
                 input < Math.min(typedPlan.inputs().size(), RecipePresentation.MAX_INPUTS);
                 input++) {
                TypedRecipeInput typedInput = typedPlan.inputs().get(input);
                StorageResourceKey key = typedInput.key();
                ItemStack representative = StorageResourceKinds.representative(
                        key, level.registryAccess());
                inputs.set(input, key.kindId().equals(StorageResourceKindApi.ITEM_KIND)
                        ? TerminalDisplayStack.create(representative, typedInput.amount())
                        : TerminalResourceDisplay.create(
                                representative, key, typedInput.amount()));
            }
            return List.copyOf(inputs);
        }
        List<RecipeAdapterMatch.Input> ingredients = match.orderedInputs();
        if (ingredients.size() > RecipePresentation.MAX_INPUTS) {
            throw new IllegalArgumentException("Recipe presentation has more than nine inputs");
        }
        List<ItemStack> inputs = new ArrayList<>(
                Collections.nCopies(RecipePresentation.MAX_INPUTS, ItemStack.EMPTY));
        for (int input = 0; input < ingredients.size(); input++) {
            RecipeAdapterMatch.Input ingredient = ingredients.get(input);
            if (!ingredient.isEmpty()) {
                inputs.set(input, ingredientRepresentative(ingredient, sources));
            }
        }
        return List.copyOf(inputs);
    }

    private static ItemStack presentationStation(
            RecipeAdapterMatch match,
            StorageCoreBlockEntity core
    ) {
        MachineDescriptor station = MachineEnergyTable.get(match.stationDescriptorId());
        if (station == null) throw new IllegalStateException("Recipe presentation has no station descriptor");
        if (station.category() == MachineEnergyTable.Category.TRANSFORM) {
            if (!isStationAvailable(core, match)) {
                throw new IllegalStateException("Recipe presentation tool station is unavailable");
            }
            return station.representativeStack();
        }
        int stationSlot = MachineEnergyTable.findSlot(match.stationDescriptorId());
        if (stationSlot < 0) throw new IllegalStateException("Recipe presentation station has no slot");
        ItemStack installed = core.getMachineContainer().getItem(stationSlot);
        if (!station.accepts(installed)) {
            throw new IllegalStateException("Recipe presentation station is not installed");
        }
        return installed.copyWithCount(1);
    }

    private IngredientPlan planIngredients(
            StorageCoreBlockEntity core,
            List<RecipeAdapterMatch.Input> ingredients,
            long crafts,
            Player player
    ) {
        return planIngredients(ingredients, crafts, snapshotIngredientSources(core, player));
    }

    private List<IngredientSource> snapshotIngredientSources(StorageCoreBlockEntity core, Player player) {
        List<IngredientSource> coreSources = core.storedItemSources();
        if (!usePlayerInventory || player == null) return coreSources;
        List<IngredientSource> playerSources = new ArrayList<>();
        if (usePlayerInventory && player != null) {
            for (int slot = 0; slot < PLAYER_INVENTORY_SLOTS; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    playerSources.add(new IngredientSource(
                            ItemKey.of(stack), slot, stack.copyWithCount(1), stack.getCount()));
                }
            }
        }
        Comparator<IngredientSource> order = Comparator
                .comparing((IngredientSource source) ->
                        BuiltInRegistries.ITEM.getKey(source.stack().getItem()).toString())
                .thenComparing(source -> source.key().components().toString())
                .thenComparingInt(IngredientSource::playerSlot);
        playerSources.sort(order);
        List<IngredientSource> sources = new ArrayList<>(
                coreSources.size() + playerSources.size());
        int coreIndex = 0;
        int playerIndex = 0;
        while (coreIndex < coreSources.size() || playerIndex < playerSources.size()) {
            if (playerIndex >= playerSources.size()
                    || coreIndex < coreSources.size()
                    && order.compare(coreSources.get(coreIndex), playerSources.get(playerIndex)) <= 0) {
                sources.add(coreSources.get(coreIndex++));
            } else {
                sources.add(playerSources.get(playerIndex++));
            }
        }
        return List.copyOf(sources);
    }

    private IngredientPlan planIngredients(
            List<RecipeAdapterMatch.Input> ingredients,
            long crafts,
            List<IngredientSource> sources
    ) {
        if (crafts <= 0) return null;
        List<IngredientNeed> needs = aggregateIngredients(ingredients, crafts);
        if (needs == null || needs.isEmpty()) return null;

        int sourceNode = 0;
        int sourceStart = 1;
        int needStart = sourceStart + sources.size();
        int sinkNode = needStart + needs.size();
        List<List<FlowEdge>> graph = new ArrayList<>(sinkNode + 1);
        for (int i = 0; i <= sinkNode; i++) graph.add(new ArrayList<>());

        long totalRequired = 0;
        for (int i = 0; i < sources.size(); i++) {
            addFlowEdge(graph, sourceNode, sourceStart + i, sources.get(i).amount());
        }
        for (int i = 0; i < needs.size(); i++) {
            IngredientNeed need = needs.get(i);
            try {
                totalRequired = Math.addExact(totalRequired, need.count());
            } catch (ArithmeticException e) {
                return null;
            }
            addFlowEdge(graph, needStart + i, sinkNode, need.count());
            for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
                IngredientSource source = sources.get(sourceIndex);
                if (need.ingredient().test(source.stack())) {
                    addFlowEdge(graph, sourceStart + sourceIndex, needStart + i, source.amount());
                }
            }
        }
        if (maximumFlow(graph, sourceNode, sinkNode) != totalRequired) return null;

        Map<ItemKey, Long> coreReservations = new HashMap<>();
        Map<Integer, PlayerReservation> playerReservations = new HashMap<>();
        List<Map<ItemKey, Long>> allocations = new ArrayList<>(needs.size());
        for (int i = 0; i < needs.size(); i++) allocations.add(new LinkedHashMap<>());
        for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
            IngredientSource source = sources.get(sourceIndex);
            long used = 0;
            for (FlowEdge edge : graph.get(sourceStart + sourceIndex)) {
                if (edge.to >= needStart && edge.to < sinkNode) {
                    long allocated = edge.originalCapacity - edge.capacity;
                    used += allocated;
                    if (allocated > 0) {
                        allocations.get(edge.to - needStart).merge(source.key(), allocated, Math::addExact);
                    }
                }
            }
            if (used <= 0) continue;
            if (source.playerSlot() < 0) {
                coreReservations.merge(source.key(), used, Long::sum);
            } else {
                int count = Math.toIntExact(used);
                playerReservations.merge(
                        source.playerSlot(),
                        new PlayerReservation(source.key(), count),
                        (left, right) -> new PlayerReservation(left.key(), Math.addExact(left.count(), right.count()))
                );
            }
        }
        return new IngredientPlan(
                coreReservations,
                playerReservations,
                allocations.stream().map(Map::copyOf).toList()
        );
    }

    private DeliveryPlan planDelivery(
            StorageCoreBlockEntity core,
            IngredientPlan ingredientPlan,
            TypedConsumption typedConsumption,
            RecipeAdapterMatch match,
            long crafts,
            DeliveryTarget destination,
            Player player
    ) {
        if (crafts <= 0) return null;
        RecipeAdapterMatch.ToolCost toolCost = match.cost().toolCost().orElse(null);
        ToolUsePlan toolUse = toolCost == null ? null : planToolUse(core, toolCost, crafts);
        if (toolCost != null && toolUse == null) return null;
        List<ItemStack> inventory = new ArrayList<>(PLAYER_INVENTORY_SLOTS);
        for (int slot = 0; slot < PLAYER_INVENTORY_SLOTS; slot++) {
            inventory.add(player.getInventory().getItem(slot).copy());
        }
        for (Map.Entry<Integer, PlayerReservation> entry : ingredientPlan.playerReservations().entrySet()) {
            ItemStack stack = inventory.get(entry.getKey());
            PlayerReservation reservation = entry.getValue();
            if (stack.isEmpty() || !ItemKey.of(stack).equals(reservation.key())
                    || stack.getCount() < reservation.count()) return null;
            stack.shrink(reservation.count());
        }

        ItemStack carried = getCarried().copy();
        Level level = core.getLevel();
        if (level == null) return null;
        RecipeAdapterMatch.CheckedOutput checkedOutput = match.checkedOutput(
                ingredientPlan.allocations(), crafts, level).orElse(null);
        if (checkedOutput == null) return null;
        Map<ItemKey, Long> primaryOutputs = checkedOutput.primaryOutputs();
        Map<ItemKey, Long> remainders = new LinkedHashMap<>(checkedOutput.remainders());
        Map<StorageResourceKey, Long> resourcePrimaryOutputs = new LinkedHashMap<>(
                checkedOutput.resourcePrimaryOutputs());
        Map<StorageResourceKey, Long> resourceRemainders = new LinkedHashMap<>(
                checkedOutput.resourceRemainders());
        try {
            for (Map.Entry<StorageResourceKey, Long> entry
                    : typedConsumption.remainders().entrySet()) {
                var itemKey = StorageResourceBridge.itemKey(
                        entry.getKey(), level.registryAccess());
                if (itemKey.isPresent()) {
                    remainders.merge(itemKey.orElseThrow(), entry.getValue(), Math::addExact);
                } else {
                    resourceRemainders.merge(entry.getKey(), entry.getValue(), Math::addExact);
                }
            }
        } catch (ArithmeticException exception) {
            return null;
        }
        Map<ItemKey, Long> coreOutputs = new LinkedHashMap<>();
        if (destination == DeliveryTarget.CURSOR) {
            if (primaryOutputs.size() != 1) return null;
            Map.Entry<ItemKey, Long> output = primaryOutputs.entrySet().iterator().next();
            ItemStack result = output.getKey().toStack(1);
            long primaryCount = output.getValue();
            if (primaryCount > result.getMaxStackSize()) return null;
            if (carried.isEmpty()) {
                carried = result.copyWithCount((int) primaryCount);
            } else if (ItemStack.isSameItemSameComponents(carried, result)
                    && primaryCount <= carried.getMaxStackSize() - carried.getCount()) {
                carried.grow((int) primaryCount);
            } else {
                return null;
            }
        } else if (destination == DeliveryTarget.PLAYER) {
            for (Map.Entry<ItemKey, Long> output : primaryOutputs.entrySet()) {
                if (!addOutputToInventoryThenCore(
                        inventory, output.getKey().toStack(1), output.getValue(), coreOutputs)) {
                    return null;
                }
            }
        } else if (destination == DeliveryTarget.STORAGE) {
            for (Map.Entry<ItemKey, Long> output : primaryOutputs.entrySet()) {
                if (!addCoreOutput(coreOutputs, output.getKey(), output.getValue())) return null;
            }
        } else {
            return null;
        }

        for (Map.Entry<ItemKey, Long> entry : remainders.entrySet()) {
            if (destination == DeliveryTarget.STORAGE) {
                if (!addCoreOutput(coreOutputs, entry.getKey(), entry.getValue())) return null;
            } else if (!addOutputToInventoryThenCore(
                    inventory, entry.getKey().toStack(1), entry.getValue(), coreOutputs)) {
                return null;
            }
        }
        Map<StorageResourceKey, Long> resourceOutputs = new LinkedHashMap<>(
                resourcePrimaryOutputs);
        try {
            for (Map.Entry<StorageResourceKey, Long> entry : resourceRemainders.entrySet()) {
                resourceOutputs.merge(entry.getKey(), entry.getValue(), Math::addExact);
            }
        } catch (ArithmeticException exception) {
            return null;
        }
        Map<StorageResourceKey, Long> coreDeltas = coreDeltas(
                core,
                ingredientPlan,
                coreOutputs,
                resourceOutputs,
                typedConsumption.coreConsumed());
        if (coreDeltas == null || !coreDeltas.isEmpty()
                && !applyCoreResourceDeltas(
                core, coreDeltas, Action.SIMULATE)) return null;
        return new DeliveryPlan(
                List.copyOf(inventory), carried, Map.copyOf(coreDeltas), toolUse);
    }

    private static Map<StorageResourceKey, Long> coreDeltas(
            StorageCoreBlockEntity core,
            IngredientPlan ingredientPlan,
            Map<ItemKey, Long> coreOutputs,
            Map<StorageResourceKey, Long> resourceOutputs,
            Map<StorageResourceKey, Long> typedConsumption
    ) {
        Level level = core.getLevel();
        if (level == null) return null;
        Map<StorageResourceKey, Long> deltas = new LinkedHashMap<>();
        try {
            for (Map.Entry<ItemKey, Long> entry : ingredientPlan.coreReservations().entrySet()) {
                mergeResourceDelta(
                        deltas,
                        StorageResourceBridge.itemKey(entry.getKey(), level.registryAccess()),
                        Math.negateExact(entry.getValue()));
            }
            for (Map.Entry<ItemKey, Long> entry : coreOutputs.entrySet()) {
                mergeResourceDelta(
                        deltas,
                        StorageResourceBridge.itemKey(entry.getKey(), level.registryAccess()),
                        entry.getValue());
            }
            for (Map.Entry<StorageResourceKey, Long> entry : resourceOutputs.entrySet()) {
                mergeResourceDelta(deltas, entry.getKey(), entry.getValue());
            }
            for (Map.Entry<StorageResourceKey, Long> entry : typedConsumption.entrySet()) {
                mergeResourceDelta(deltas, entry.getKey(), Math.negateExact(entry.getValue()));
            }
        } catch (ArithmeticException exception) {
            return null;
        }
        return Map.copyOf(deltas);
    }

    private static TypedConsumption planTypedConsumption(
            StorageCoreBlockEntity core,
            TypedRecipePlan plan,
            long crafts,
            List<IngredientSource> sources
    ) {
        if (crafts <= 0) return null;
        List<TypedRecipeInput> inputs = plan.inputs().stream()
                .filter(input -> input.role() == TypedRecipeInput.Role.CONSUME)
                .toList();
        if (inputs.isEmpty()) {
            return new TypedConsumption(Map.of(), Map.of(), Map.of());
        }
        List<StorageResourceKey> resources = inputs.stream()
                .flatMap(input -> input.alternatives().stream())
                .distinct()
                .toList();
        int sourceNode = 0;
        int resourceStart = 1;
        int inputStart = resourceStart + resources.size();
        int sinkNode = inputStart + inputs.size();
        List<List<FlowEdge>> graph = new ArrayList<>(sinkNode + 1);
        for (int index = 0; index <= sinkNode; index++) graph.add(new ArrayList<>());
        for (int index = 0; index < resources.size(); index++) {
            addFlowEdge(
                    graph,
                    sourceNode,
                    resourceStart + index,
                    typedResourceAvailable(core, resources.get(index), sources));
        }
        long totalRequired = 0;
        try {
            for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
                TypedRecipeInput input = inputs.get(inputIndex);
                long required = Math.multiplyExact(input.amount(), crafts);
                totalRequired = Math.addExact(totalRequired, required);
                addFlowEdge(graph, inputStart + inputIndex, sinkNode, required);
                for (int resourceIndex = 0; resourceIndex < resources.size(); resourceIndex++) {
                    StorageResourceKey resource = resources.get(resourceIndex);
                    if (input.alternatives().contains(resource)) {
                        addFlowEdge(
                                graph,
                                resourceStart + resourceIndex,
                                inputStart + inputIndex,
                                typedResourceAvailable(core, resource, sources));
                    }
                }
            }
        } catch (ArithmeticException exception) {
            return null;
        }
        if (maximumFlow(graph, sourceNode, sinkNode) != totalRequired) return null;

        Map<StorageResourceKey, Long> coreConsumed = new LinkedHashMap<>();
        Map<Integer, PlayerReservation> playerReservations = new LinkedHashMap<>();
        Map<StorageResourceKey, Long> remainders = new LinkedHashMap<>();
        try {
            for (int resourceIndex = 0; resourceIndex < resources.size(); resourceIndex++) {
                StorageResourceKey resource = resources.get(resourceIndex);
                long resourceAllocated = 0;
                for (FlowEdge edge : graph.get(resourceStart + resourceIndex)) {
                    if (edge.to < inputStart || edge.to >= sinkNode) continue;
                    long allocated = edge.originalCapacity - edge.capacity;
                    if (allocated <= 0) continue;
                    resourceAllocated = Math.addExact(resourceAllocated, allocated);
                    TypedRecipeInput input = inputs.get(edge.to - inputStart);
                    TypedRecipeOutput remainder = input.remainderFor(resource).orElse(null);
                    if (remainder != null) {
                        remainders.merge(
                                remainder.key(),
                                Math.multiplyExact(remainder.amount(), allocated),
                                Math::addExact);
                    }
                }
                if (resourceAllocated <= 0) continue;
                if (!resource.kindId().equals(StorageResourceKindApi.ITEM_KIND)) {
                    coreConsumed.merge(resource, resourceAllocated, Math::addExact);
                    continue;
                }
                Level level = core.getLevel();
                if (level == null) return null;
                ItemKey itemKey = StorageResourceBridge.itemKey(
                        resource, level.registryAccess()).orElse(null);
                if (itemKey == null) return null;
                long remaining = resourceAllocated;
                for (IngredientSource source : sources) {
                    if (remaining <= 0) break;
                    if (!source.key().equals(itemKey)) continue;
                    long allocated = Math.min(remaining, source.amount());
                    if (source.playerSlot() < 0) {
                        coreConsumed.merge(resource, allocated, Math::addExact);
                    } else {
                        int count = Math.toIntExact(allocated);
                        playerReservations.merge(
                                source.playerSlot(),
                                new PlayerReservation(source.key(), count),
                                (left, right) -> {
                                    if (!left.key().equals(right.key())) {
                                        throw new IllegalStateException(
                                                "Typed recipe reserved different items from one player slot");
                                    }
                                    return new PlayerReservation(
                                            left.key(), Math.addExact(left.count(), right.count()));
                                });
                    }
                    remaining -= allocated;
                }
                if (remaining != 0) return null;
            }
        } catch (ArithmeticException exception) {
            return null;
        }
        return new TypedConsumption(
                Map.copyOf(coreConsumed),
                Map.copyOf(playerReservations),
                Map.copyOf(remainders));
    }

    private static void mergeResourceDelta(
            Map<StorageResourceKey, Long> deltas,
            StorageResourceKey key,
            long delta
    ) {
        if (delta == 0) return;
        long merged = Math.addExact(deltas.getOrDefault(key, 0L), delta);
        if (merged == 0) deltas.remove(key);
        else deltas.put(key, merged);
    }

    private static boolean applyCoreResourceDeltas(
            StorageCoreBlockEntity core,
            Map<StorageResourceKey, Long> deltas,
            Action action
    ) {
        if (deltas.isEmpty()) return true;
        StorageResourceTransaction.Builder transaction = StorageResourceTransaction.builder();
        deltas.forEach(transaction::add);
        return core.applyResourceTransaction(
                transaction.build(), action, Actor.magicCrafting());
    }

    private static ToolUsePlan planToolUse(
            StorageCoreBlockEntity core,
            RecipeAdapterMatch.ToolCost toolCost,
            long crafts
    ) {
        long amount;
        try {
            amount = Math.multiplyExact(toolCost.amountPerCraft(), crafts);
        } catch (ArithmeticException exception) {
            return null;
        }
        return core.hasDescriptorAmount(toolCost.descriptorId(), amount)
                ? new ToolUsePlan(toolCost.descriptorId(), amount)
                : null;
    }

    private static boolean addOutputToInventoryThenCore(
            List<ItemStack> inventory,
            ItemStack template,
            long amount,
            Map<ItemKey, Long> coreOutputs
    ) {
        if (template.isEmpty() || amount <= 0) return false;
        long remaining = amount;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack stack = inventory.get(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) continue;
            int inserted = (int) Math.min(remaining, stack.getMaxStackSize() - stack.getCount());
            if (inserted > 0) {
                stack.grow(inserted);
                remaining -= inserted;
            }
        }
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            if (!inventory.get(slot).isEmpty()) continue;
            int inserted = (int) Math.min(remaining, template.getMaxStackSize());
            inventory.set(slot, template.copyWithCount(inserted));
            remaining -= inserted;
        }
        if (remaining > 0) {
            return addCoreOutput(coreOutputs, ItemKey.of(template), remaining);
        }
        return true;
    }

    private static boolean addCoreOutput(
            Map<ItemKey, Long> coreOutputs,
            ItemKey key,
            long amount
    ) {
        if (amount <= 0) return false;
        long existing = coreOutputs.getOrDefault(key, 0L);
        if (existing > Long.MAX_VALUE - amount) return false;
        coreOutputs.put(key, existing + amount);
        return true;
    }

    private boolean commitCraft(
            StorageCoreBlockEntity core,
            IngredientPlan plan,
            DeliveryPlan delivery,
            Player player,
            RecipeAdapterMatch plannedMatch,
            long crafts
    ) {
        Level level = core.getLevel();
        if (level == null) return false;
        ItemStack plannedOutput = selectionDisplay(plannedMatch, level, 0);
        if (plannedOutput.isEmpty()) return false;
        List<IngredientSource> currentSources = snapshotIngredientSources(core, player);
        RecipeAdapterMatch currentMatch = resolveAvailableRecipeVariantById(
                level, core, plannedMatch.holder().id(), plannedOutput, currentSources);
        if (currentMatch == null
                || !plannedMatch.validatesCommit(currentMatch.holder())
                || !currentMatch.validatesCommit(currentMatch.holder())
                || !plannedMatch.typedRecipePlan().equals(currentMatch.typedRecipePlan())
                || !plannedMatch.cost().equals(currentMatch.cost())
                || !hasRecipeCostsForCrafts(core, currentMatch.cost(), crafts)
                || currentMatch.typedRecipePlan().isPresent()
                && !hasTypedInputs(
                core,
                currentMatch.typedRecipePlan().orElseThrow(),
                crafts,
                currentSources)) return false;
        core.beginMutationBatch();
        try {
            ToolUsePlan toolUse = delivery.toolUse();
            if (toolUse != null
                    && !core.hasDescriptorAmount(toolUse.descriptorId(), toolUse.amount())) return false;
            for (Map.Entry<Integer, PlayerReservation> entry : plan.playerReservations().entrySet()) {
                ItemStack stack = player.getInventory().getItem(entry.getKey());
                PlayerReservation reservation = entry.getValue();
                if (stack.isEmpty() || !ItemKey.of(stack).equals(reservation.key()) || stack.getCount() < reservation.count()) {
                    return false;
                }
            }
            if (!delivery.coreDeltas().isEmpty()
                    && (!applyCoreResourceDeltas(
                    core, delivery.coreDeltas(), Action.SIMULATE)
                    || !applyCoreResourceDeltas(
                    core, delivery.coreDeltas(), Action.EXECUTE))) return false;
            if (!core.consumeCraftCosts(currentMatch.cost(), crafts)) {
                rollbackResourceTransaction(core, delivery.coreDeltas());
                return false;
            }
            for (int slot = 0; slot < delivery.playerInventory().size(); slot++) {
                player.getInventory().setItem(slot, delivery.playerInventory().get(slot).copy());
            }
            player.getInventory().setChanged();
            setCarried(delivery.carried().copy());
            return true;
        } finally {
            core.endMutationBatch();
        }
    }

    private static void rollbackResourceTransaction(
            StorageCoreBlockEntity core,
            Map<StorageResourceKey, Long> committed
    ) {
        if (committed.isEmpty()) return;
        Map<StorageResourceKey, Long> inverse = new LinkedHashMap<>();
        try {
            for (Map.Entry<StorageResourceKey, Long> entry : committed.entrySet()) {
                inverse.put(entry.getKey(), Math.negateExact(entry.getValue()));
            }
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Crafting transaction cannot be inverted", exception);
        }
        if (!applyCoreResourceDeltas(core, inverse, Action.EXECUTE)) {
            throw new IllegalStateException("Failed to roll back crafting resource transaction");
        }
    }

    private static void addFlowEdge(List<List<FlowEdge>> graph, int from, int to, long capacity) {
        FlowEdge forward = new FlowEdge(to, graph.get(to).size(), capacity);
        FlowEdge reverse = new FlowEdge(from, graph.get(from).size(), 0);
        graph.get(from).add(forward);
        graph.get(to).add(reverse);
    }

    private static long maximumFlow(List<List<FlowEdge>> graph, int source, int sink) {
        long flow = 0;
        int[] levels = new int[graph.size()];
        while (buildFlowLevels(graph, source, sink, levels)) {
            int[] nextEdges = new int[graph.size()];
            long pushed;
            while ((pushed = pushFlow(graph, source, sink, Long.MAX_VALUE, levels, nextEdges)) > 0) {
                flow += pushed;
            }
        }
        return flow;
    }

    private static boolean buildFlowLevels(List<List<FlowEdge>> graph, int source, int sink, int[] levels) {
        Arrays.fill(levels, -1);
        Queue<Integer> queue = new ArrayDeque<>();
        levels[source] = 0;
        queue.add(source);
        while (!queue.isEmpty()) {
            int node = queue.remove();
            for (FlowEdge edge : graph.get(node)) {
                if (edge.capacity > 0 && levels[edge.to] < 0) {
                    levels[edge.to] = levels[node] + 1;
                    queue.add(edge.to);
                }
            }
        }
        return levels[sink] >= 0;
    }

    private static long pushFlow(
            List<List<FlowEdge>> graph,
            int node,
            int sink,
            long available,
            int[] levels,
            int[] nextEdges
    ) {
        if (node == sink) return available;
        List<FlowEdge> edges = graph.get(node);
        while (nextEdges[node] < edges.size()) {
            FlowEdge edge = edges.get(nextEdges[node]);
            if (edge.capacity > 0 && levels[edge.to] == levels[node] + 1) {
                long pushed = pushFlow(graph, edge.to, sink, Math.min(available, edge.capacity), levels, nextEdges);
                if (pushed > 0) {
                    edge.capacity -= pushed;
                    graph.get(edge.to).get(edge.reverseIndex).capacity += pushed;
                    return pushed;
                }
            }
            nextEdges[node]++;
        }
        return 0;
    }

    public static boolean supportsRecipeContract(Recipe<?> recipe) {
        RecipeHolder<?> holder = new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "compatibility_probe"),
                recipe);
        return BuiltInRecipeAdapters.registry().classify(holder).isPresent();
    }

    public static boolean supportsRecipeHolder(RecipeHolder<?> holder) {
        if (holder == null) return false;
        RecipeAdapterMatch match = BuiltInRecipeAdapters.registry().classify(holder).orElse(null);
        return match != null && match.validatesSimulation(holder);
    }

    private RecipeAdapterMatch resolveAvailableRecipeVariantById(
            Level level,
            StorageCoreBlockEntity core,
            ResourceLocation recipeId,
            ItemStack requestedOutput,
            List<IngredientSource> sources
    ) {
        RecipeHolder<?> holder = resolveRecipeById(level, core, recipeId);
        return holder == null ? null : resolveAvailableRecipeVariant(
                holder, core, requestedOutput, sources, level);
    }

    private static RecipeAdapterMatch resolveAvailableRecipeVariant(
            RecipeHolder<?> holder,
            StorageCoreBlockEntity core,
            ItemStack requestedOutput,
            List<IngredientSource> sources,
            Level level
    ) {
        RecipeAdapterMatch baseMatch = classifyAvailable(holder, core);
        if (baseMatch == null) return null;
        List<ItemStack> availableStacks = sources.stream()
                .map(IngredientSource::stack)
                .toList();
        List<RecipeAdapterMatch> variants =
                baseMatch.resolveVariantsFromSnapshot(availableStacks, level);
        if (requestedOutput == null || requestedOutput.isEmpty()) {
            return variants.size() == 1 ? variants.getFirst() : null;
        }
        for (RecipeAdapterMatch variant : variants) {
            if (matchesSelectionOutput(variant, requestedOutput, level)) return variant;
        }
        return null;
    }

    private static RecipeAdapterMatch resolveAvailableRecipeLookupVariant(
            RecipeHolder<?> holder,
            StorageCoreBlockEntity core,
            ItemStack requestedOutput,
            List<IngredientSource> sources,
            Level level
    ) {
        RecipeAdapterMatch baseMatch = classifyAvailable(holder, core);
        if (baseMatch == null || requestedOutput == null || requestedOutput.isEmpty()) return null;
        ItemStack requested = TerminalDisplayStack.strip(requestedOutput);
        List<ItemStack> availableStacks = sources.stream().map(IngredientSource::stack).toList();
        List<RecipeAdapterMatch> variants =
                baseMatch.resolveVariantsFromSnapshot(availableStacks, level);
        for (RecipeAdapterMatch variant : variants) {
            if (variant.typedRecipePlan().isPresent()
                    ? matchesSelectionOutput(variant, requested, level)
                    : variant.matchesLookupOutput(requested, level)) return variant;
        }
        return null;
    }

    private static boolean matchesSelectionOutput(
            RecipeAdapterMatch match,
            ItemStack requestedOutput,
            Level level
    ) {
        if (requestedOutput == null || requestedOutput.isEmpty() || level == null) return false;
        StorageResourceKey expected = match.selectionOutputKey(level).orElse(null);
        if (expected == null) return false;
        StorageResourceKey requested = TerminalResourceDisplay.key(requestedOutput).orElse(null);
        if (requested != null) return expected.equals(requested);
        if (!expected.kindId().equals(StorageResourceKindApi.ITEM_KIND)) return false;
        ItemStack presentation = match.presentationOutput(List.of(), level);
        return !presentation.isEmpty() && ItemStack.isSameItemSameComponents(
                presentation, TerminalDisplayStack.strip(requestedOutput));
    }

    private static boolean isNonItemSelection(RecipeAdapterMatch match) {
        return match.typedRecipePlan().map(plan ->
                !plan.selectionOutputKey().kindId().equals(
                        StorageResourceKindApi.ITEM_KIND)).orElse(false);
    }

    private static ItemStack selectionDisplay(
            RecipeAdapterMatch match,
            Level level,
            long amount
    ) {
        if (level == null || amount < 0) return ItemStack.EMPTY;
        StorageResourceKey key = match.selectionOutputKey(level).orElse(null);
        ItemStack presentation = match.presentationOutput(List.of(), level);
        if (key == null || presentation.isEmpty()) return ItemStack.EMPTY;
        return key.kindId().equals(StorageResourceKindApi.ITEM_KIND)
                ? TerminalDisplayStack.create(presentation, amount)
                : TerminalResourceDisplay.create(presentation, key, amount);
    }

    private static RecipeAdapterMatch classifyAvailable(
            RecipeHolder<?> holder,
            StorageCoreBlockEntity core
    ) {
        RecipeAdapterMatch match = BuiltInRecipeAdapters.registry().classify(holder).orElse(null);
        return match != null
                && match.validatesSimulation(holder)
                && isStationAvailable(core, match)
                ? match
                : null;
    }

    private static boolean isStationAvailable(
            StorageCoreBlockEntity core,
            RecipeAdapterMatch match
    ) {
        if (core == null) return false;
        MachineDescriptor station = MachineEnergyTable.get(match.stationDescriptorId());
        if (station == null) return false;
        RecipeAdapterMatch.ToolCost toolCost = match.cost().toolCost().orElse(null);
        if (station.category() == MachineEnergyTable.Category.TRANSFORM) {
            return toolCost != null
                    && toolCost.descriptorId().equals(station.id())
                    && core.hasDescriptorAmount(toolCost.descriptorId(), toolCost.amountPerCraft());
        }
        int stationSlot = MachineEnergyTable.findSlot(station.id());
        return stationSlot >= 0
                && MachineEnergyTable.isInstalled(core, stationSlot)
                && hasToolForCrafts(core, toolCost, 1);
    }

    private static boolean hasToolForCrafts(
            StorageCoreBlockEntity core,
            RecipeAdapterMatch.ToolCost toolCost,
            long crafts
    ) {
        if (crafts <= 0) return false;
        if (toolCost == null) return true;
        try {
            return core.hasDescriptorAmount(
                    toolCost.descriptorId(),
                    Math.multiplyExact(toolCost.amountPerCraft(), crafts));
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private List<IngredientNeed> aggregateIngredients(
            List<RecipeAdapterMatch.Input> ingredients,
            long crafts
    ) {
        List<IngredientNeed> needs = new ArrayList<>();
        for (RecipeAdapterMatch.Input ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            try {
                needs.add(new IngredientNeed(
                        ingredient,
                        Math.multiplyExact(crafts, ingredient.multiplicity())));
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        return needs;
    }

    private List<IngredientNeed> summarizeIngredients(List<RecipeAdapterMatch.Input> ingredients) {
        Map<RecipeAdapterMatch.Input, Integer> counts = new LinkedHashMap<>();
        for (RecipeAdapterMatch.Input ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                counts.merge(ingredient, ingredient.multiplicity(), Math::addExact);
            }
        }
        List<IngredientNeed> needs = new ArrayList<>(counts.size());
        for (Map.Entry<RecipeAdapterMatch.Input, Integer> entry : counts.entrySet()) {
            needs.add(new IngredientNeed(entry.getKey(), entry.getValue()));
        }
        return needs;
    }

    private void updatePreview(StorageCoreBlockEntity core, Player player) {
        if (core == null || core.getLevel() == null) {
            craftableRecipeCount = 0;
            clearRecipePresentation();
            return;
        }
        if (selectedOutput.isEmpty() && currentRecipes.isEmpty()) {
            craftableRecipeCount = 0;
            clearRecipePresentation();
            return;
        }
        ResourceLocation selectedId = currentRecipes.isEmpty()
                || currentRecipeIndex >= currentRecipes.size()
                ? null : currentRecipes.get(currentRecipeIndex).id();
        List<IngredientSource> sources = snapshotIngredientSources(core, player);
        rerankCurrentRecipes(core, player, sources, selectedOutput, selectedId);
        RecipeAdapterMatch match = resolveCurrentRecipeMatch(core.getLevel());
        if (match == null) {
            clearRecipePresentation();
            return;
        }
        if (selectedRecipeId != null) selectedRecipeId = match.holder().id();
        currentRecipeTypeOrder = match.adapter().priority();
        CraftPreview preview = computeCraftPreviewFor(match, core, sources);
        applyPreviewData(preview);
        syncRecipePresentation(match, preview, sources, core, true);
        refreshEnergyAmounts(core);
    }

    private void applyPreviewData(CraftPreview preview) {
        craftableCount = preview.craftable();
        Arrays.fill(ingredientAvailable, 0);
        processRequired = 0;
        fuelRequired = 0;
        for (EnergyPreview energy : preview.energies()) {
            if (energy.type() == EnergyType.FURNACE_FUEL) {
                fuelRequired = energy.required();
            } else {
                processRequired = energy.required();
            }
        }
    }

    @Override
    protected void onObservedStorageChanged(StorageCoreBlockEntity core) {
        if (page == CraftingTerminalPage.TRANSFORM) {
            updateTransformPreview(core);
        } else if (page.isItemPage() && !selectedOutput.isEmpty()) {
            updatePreview(core, playerInventory.player);
        }
    }

    @Override
    protected void onObservedEnergyChanged(StorageCoreBlockEntity core) {
        if (page != CraftingTerminalPage.CRAFTABLE) super.onObservedEnergyChanged(core);
        boolean crossedCraftableThreshold = energyCrossedCraftableThreshold(core);
        refreshEnergyAmounts(core);
        if (crossedCraftableThreshold) {
            if (page == CraftingTerminalPage.CRAFTABLE) refreshDisplayItems(core);
        }
        if (page == CraftingTerminalPage.TRANSFORM) {
            updateTransformPreview(core);
        } else if (page.isItemPage() && !selectedOutput.isEmpty()) {
            updatePreview(core, playerInventory.player);
        }
    }

    @Override
    protected void onObservedStationWorkChanged(
            StorageCoreBlockEntity core,
            Map<ResourceLocation, Long> increases,
            boolean decreased
    ) {
        if (page != CraftingTerminalPage.CRAFTABLE) {
            super.onObservedStationWorkChanged(core, increases, decreased);
        }
        boolean crossedCraftableThreshold =
                decreased || increases.entrySet().stream().anyMatch(entry ->
                entry.getValue() >= nextCraftableStationThreshold.getOrDefault(
                        entry.getKey(), Long.MAX_VALUE));
        if (crossedCraftableThreshold) {
            if (page == CraftingTerminalPage.CRAFTABLE) refreshDisplayItems(core);
        }
        if (page == CraftingTerminalPage.TRANSFORM) {
            updateTransformPreview(core);
        } else if (page.isItemPage() && !selectedOutput.isEmpty()) {
            updatePreview(core, playerInventory.player);
        }
        sendDescriptorStates();
    }

    @Override
    public void broadcastChanges() {
        boolean sendDescriptorState = false;
        if (playerInventory != null && !playerInventory.player.level().isClientSide()) {
            int fingerprint = playerInventoryFingerprint();
            boolean playerInventoryChanged = fingerprint != lastPlayerInventoryFingerprint;
            lastPlayerInventoryFingerprint = fingerprint;

            StorageCoreBlockEntity core = getCore(playerInventory.player.level());
            boolean topologyChanged = core != null && core.getTopologyRevision() != lastTopologyRevision;
            if (topologyChanged) lastTopologyRevision = core.getTopologyRevision();
            boolean machinesChanged = core != null && core.getMachineRevision() != lastMachineRevision;
            if (machinesChanged) lastMachineRevision = core.getMachineRevision();
            sendDescriptorState = machinesChanged;

            if (core != null && (topologyChanged || machinesChanged
                    || playerInventoryChanged && usePlayerInventory)) {
                if (machinesChanged) refreshEnergyAmounts(core);
                if (topologyChanged || machinesChanged || page == CraftingTerminalPage.CRAFTABLE) {
                    refreshDisplayItems(core);
                }
                updatePreview(core, playerInventory.player);
            }
        }
        super.broadcastChanges();
        if (sendDescriptorState) sendDescriptorStates();
    }

    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        sendDescriptorStates();
    }

    protected void sendDescriptorStates() {
        if (!(playerInventory.player instanceof ServerPlayer serverPlayer)) return;
        StorageCoreBlockEntity core = getCore(serverPlayer.level());
        if (core == null) return;
        List<MachineDescriptorStatePacket.State> states = new ArrayList<>();
        for (MachineDescriptor descriptor : descriptorSnapshot) {
            if (!hasDescriptorState(descriptor)) continue;
            states.add(new MachineDescriptorStatePacket.State(
                    descriptor.id(),
                    descriptorStateAmount(core, descriptor),
                    descriptor.category() == MachineEnergyTable.Category.TRANSFORM
                            && core.hasInfiniteDescriptor(descriptor.id())));
        }
        PacketDistributor.sendToPlayer(serverPlayer, new MachineDescriptorStatePacket(containerId, states));
    }

    private static boolean hasDescriptorState(MachineDescriptor descriptor) {
        return descriptor.category() == MachineEnergyTable.Category.TRANSFORM
                || descriptor.category() == MachineEnergyTable.Category.PROCESS
                && descriptor.energyType() == null;
    }

    private static long descriptorStateAmount(StorageCoreBlockEntity core, MachineDescriptor descriptor) {
        return descriptor.category() == MachineEnergyTable.Category.PROCESS
                ? core.getStationWork(descriptor.id())
                : core.getDescriptorAmount(descriptor.id());
    }

    private int playerInventoryFingerprint() {
        if (playerInventory == null) return 0;
        int fingerprint = 1;
        for (int slot = 0; slot < PLAYER_INVENTORY_SLOTS; slot++) {
            ItemStack stack = playerInventory.getItem(slot);
            fingerprint = 31 * fingerprint + ItemStack.hashItemAndComponents(stack);
            fingerprint = 31 * fingerprint + stack.getCount();
        }
        return fingerprint;
    }

    private void refreshEnergyAmounts(StorageCoreBlockEntity core) {
        for (EnergyType type : EnergyType.values()) {
            energyAmounts[type.ordinal()] = core.getEnergy(type);
        }
        axeEnergyAmount = core.getAxeEnergy();
        infiniteAxeEnergy = core.hasInfiniteAxeEnergy();
        descriptorStates.clear();
        for (MachineDescriptor descriptor : descriptorSnapshot) {
            if (!hasDescriptorState(descriptor)) continue;
            descriptorStates.put(descriptor.id(), new MachineDescriptorStatePacket.State(
                    descriptor.id(),
                    descriptorStateAmount(core, descriptor),
                    descriptor.category() == MachineEnergyTable.Category.TRANSFORM
                            && core.hasInfiniteDescriptor(descriptor.id())));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!player.level().isClientSide()) {
            if (buttonId == STORAGE_PAGE_BUTTON) return switchPage(player, CraftingTerminalPage.STORAGE);
            if (buttonId == CRAFTABLE_PAGE_BUTTON) return switchPage(player, CraftingTerminalPage.CRAFTABLE);
            if (buttonId == TRANSFORM_PAGE_BUTTON) {
                return switchPage(player, CraftingTerminalPage.TRANSFORM);
            }
            if (buttonId == STATIONS_PAGE_BUTTON) {
                return switchPage(player, CraftingTerminalPage.STATIONS);
            }
            if (buttonId == AUTO_FUEL_TARGET_BUTTON) {
                if (page != CraftingTerminalPage.TRANSFORM) return false;
                selectedTransformTarget = null;
                selectedTransformUseId = null;
                updateTransformPreview(getCore(player.level()));
                return true;
            }
            List<ResourceLocation> transformTargets =
                    TransformProviderApi.targetIds(descriptorSnapshot);
            if (buttonId >= TransformProviderApi.LEGACY_FUEL_BUTTON_BASE
                    && buttonId < TransformProviderApi.LEGACY_FUEL_BUTTON_BASE
                    + FUEL_TARGETS.size()) {
                if (page != CraftingTerminalPage.TRANSFORM) return false;
                selectedTransformTarget = transformTargets.get(
                        buttonId - TransformProviderApi.LEGACY_FUEL_BUTTON_BASE);
                selectedTransformUseId = null;
                updateTransformPreview(getCore(player.level()));
                return true;
            }
            if (buttonId >= TransformProviderApi.TARGET_BUTTON_BASE
                    && buttonId < TransformProviderApi.TARGET_BUTTON_BASE
                    + transformTargets.size()) {
                if (page != CraftingTerminalPage.TRANSFORM) return false;
                selectedTransformTarget = transformTargets.get(
                        buttonId - TransformProviderApi.TARGET_BUTTON_BASE);
                selectedTransformUseId = null;
                updateTransformPreview(getCore(player.level()));
                return true;
            }
            List<TransformProviderApi.Use> transformUses = getVisibleTransformUses();
            if (buttonId >= TRANSFORM_USE_BUTTON_BASE
                    && buttonId < TRANSFORM_USE_BUTTON_BASE + transformUses.size()) {
                if (page != CraftingTerminalPage.TRANSFORM) return false;
                selectedTransformUseId = transformUses.get(
                        buttonId - TRANSFORM_USE_BUTTON_BASE).id();
                updateTransformPreview(getCore(player.level()));
                return true;
            }
            if (buttonId == OUTPUT_DESTINATION_BUTTON) {
                if (!page.isItemPage() || isSelectedOutputStorageOnly()) return false;
                outputDestination = outputDestination.next();
                StorageCoreBlockEntity core = getCore(player.level());
                if (core != null) updatePreview(core, player);
                return true;
            }
            if (buttonId == RESET_OUTPUT_DESTINATION_BUTTON) {
                if (!page.isItemPage() || isSelectedOutputStorageOnly()) return false;
                outputDestination = TerminalOutputDestination.PLAYER;
                StorageCoreBlockEntity core = getCore(player.level());
                if (core != null) updatePreview(core, player);
                return true;
            }
            if (buttonId == SORT_ORDER_BUTTON
                    || buttonId == NEXT_SORT_MODE_BUTTON
                    || buttonId == PREVIOUS_SORT_MODE_BUTTON
                    || buttonId == RESET_SORT_ORDER_BUTTON
                    || buttonId == RESET_SORT_MODE_BUTTON) {
                StorageCoreBlockEntity core = getCore(player.level());
                return core == null || super.clickMenuButton(player, buttonId);
            }
            if (page == CraftingTerminalPage.TRANSFORM) {
                return switch (buttonId) {
                    case 2 -> transformInput(1, player);
                    case 3 -> transformInput(8, player);
                    case 4 -> transformInput(64, player);
                    case MAX_CRAFT_BUTTON -> transformInput(Integer.MAX_VALUE, player);
                    default -> false;
                };
            }
            if (page == CraftingTerminalPage.STATIONS) return false;
            StorageCoreBlockEntity core = getCore(player.level());
            switch (buttonId) {
                case 0, 1,
                     SORT_ORDER_BUTTON,
                     NEXT_SORT_MODE_BUTTON,
                     NEXT_SEARCH_MODE_BUTTON,
                     PREVIOUS_SORT_MODE_BUTTON,
                     PREVIOUS_SEARCH_MODE_BUTTON,
                     RESET_SORT_ORDER_BUTTON,
                     RESET_SORT_MODE_BUTTON,
                     RESET_SEARCH_MODE_BUTTON,
                     NEXT_RESOURCE_VIEW_BUTTON,
                     PREVIOUS_RESOURCE_VIEW_BUTTON,
                     RESET_RESOURCE_VIEW_BUTTON -> {
                    if (core != null) {
                        return super.clickMenuButton(player, buttonId);
                    }
                    return true;
                }
                case 2 -> craftItem(1, player);
                case 3 -> craftItem(8, player);
                case 4 -> craftItem(64, player);
                case MAX_CRAFT_BUTTON -> craftMaximum(player);
                case 7 -> toggleUsePlayerInventory();
                case RESET_PLAYER_INVENTORY_BUTTON -> usePlayerInventory = false;
                case 8 -> prevRecipe();
                case 9 -> nextRecipe();
                default -> { return false; }
            }
            if (core != null && (buttonId >= 7 && buttonId <= 9
                    || buttonId == RESET_PLAYER_INVENTORY_BUTTON)) {
                refreshDisplayItems(core);
                updatePreview(core, player);
            }
        }
        return true;
    }

    @Override
    public boolean applySettings(TerminalSettingsPacket packet, Player player) {
        boolean changed = super.applySettings(packet, player);
        TerminalPreferences preferences = packet.preferences();
        if (usePlayerInventory != preferences.usePlayerInventory()) {
            usePlayerInventory = preferences.usePlayerInventory();
            changed = true;
        }
        boolean outputDestinationChanged =
                outputDestination != preferences.outputDestination();
        if (outputDestinationChanged) {
            outputDestination = preferences.outputDestination();
            changed = true;
        }
        ResourceLocation requestedTransformTarget = preferences.transformTarget();
        if (requestedTransformTarget != null
                && !TransformProviderApi.targetIds(descriptorSnapshot)
                .contains(requestedTransformTarget)) {
            requestedTransformTarget = null;
        }
        if (!java.util.Objects.equals(
                selectedTransformTarget, requestedTransformTarget)) {
            selectedTransformTarget = requestedTransformTarget;
            selectedTransformUseId = null;
            changed = true;
        }
        if (page != preferences.page()) {
            switchPage(player, preferences.page());
            changed = true;
        }
        if (changed && page == CraftingTerminalPage.TRANSFORM) {
            updateTransformPreview(getCore(player.level()));
        } else if (outputDestinationChanged && page.isItemPage()) {
            updatePreview(getCore(player.level()), player);
        }
        return changed;
    }

    @Override
    protected int minimumVisibleRows() {
        return TerminalLayout.MIN_CRAFTING_ROWS;
    }

    private boolean switchPage(Player player, CraftingTerminalPage nextPage) {
        nextPage = nextPage.normalized();
        if (page == nextPage) return true;
        if (page == CraftingTerminalPage.TRANSFORM) returnTransientInputs(player);
        StorageCoreBlockEntity core = getCore(player.level());
        page = nextPage;
        selectedTransformUseId = null;
        scrollOffset = 0;
        if (core != null && page.isItemPage()) {
            if (page != CraftingTerminalPage.CRAFTABLE
                    || !restoreSharedCraftableCache(core)) {
                refreshDisplayItems(core);
            }
            updatePreview(core, player);
        } else if (page == CraftingTerminalPage.TRANSFORM) {
            updateTransformPreview(core);
        } else {
            craftableCount = 0;
        }
        return true;
    }

    @Override
    public void removed(Player player) {
        if (!player.level().isClientSide()) returnTransientInputs(player);
        super.removed(player);
    }

    private void returnTransientInputs(Player player) {
        returnTransientInput(player, fuelContainer);
        returnTransientInput(player, consumableInputContainer);
    }

    private static void returnTransientInput(Player player, SimpleContainer container) {
        if (container == null) return;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack leftover = container.removeItemNoUpdate(slot);
            if (leftover.isEmpty()) continue;
            if (!player.isAlive() || player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && serverPlayer.hasDisconnected()) {
                player.drop(leftover, false);
            } else {
                player.getInventory().placeItemBackInInventory(leftover);
            }
        }
    }

    @Override
    public void refreshDisplayItems(StorageCoreBlockEntity core) {
        if (!page.isItemPage()) {
            refreshDisplayMetadata(core);
            return;
        }
        refreshDisplayItemsFiltered(core, currentFilter);
    }

    @Override
    public void refreshDisplayItemsFiltered(StorageCoreBlockEntity core, String filter) {
        this.currentFilter = filter != null ? filter : "";
        if (core == null) {
            totalItemTypes = 0;
            replaceVisibleDisplayStacks(List.of(), getVisibleRows());
            return;
        }
        if (page == CraftingTerminalPage.STORAGE) {
            super.refreshDisplayItemsFiltered(core, currentFilter);
            if (!selectedOutput.isEmpty()) {
                if (selectedRecipeId != null) {
                    if (!isSelectedRecipeCurrent(core)) clearSelection();
                } else if (TerminalResourceDisplay.isTyped(selectedOutput)
                        || core.getItemCount(ItemKey.of(selectedOutput)) <= 0) {
                    clearSelection();
                }
            }
            return;
        }
        List<ItemStack> displayStacks;
        CraftableBuildResult craftableBuild = null;
        long sortNanos = 0;
        Player player = playerInventory != null ? playerInventory.player : null;
        craftableBuild = buildCraftableDisplayStacks(core, player);
        displayStacks = craftableBuild.stacks();
        long sortStarted = System.nanoTime();
        sortCraftableDisplayStacks(displayStacks);
        sortNanos = System.nanoTime() - sortStarted;
        cacheSharedCraftable(core, displayStacks);

        totalItemTypes = displayStacks.size();
        refreshDisplayMetadata(core);
        int vRows = getVisibleRows();
        int maxOffset = Math.max(0, totalItemTypes - vRows * DISPLAY_COLS);
        scrollOffset = Math.min(scrollOffset, maxOffset);
        long syncStarted = System.nanoTime();
        replaceVisibleDisplayStacks(displayStacks, vRows);
        long syncNanos = System.nanoTime() - syncStarted;

        if (!selectedOutput.isEmpty()) {
            if (selectedRecipeId != null) {
                if (!isSelectedRecipeCurrent(core)) clearSelection();
            } else if (page == CraftingTerminalPage.CRAFTABLE) {
                if (!isCraftableOutput(core, selectedOutput, player)) clearSelection();
            } else if (TerminalResourceDisplay.isTyped(selectedOutput)
                    || core.getItemCount(ItemKey.of(selectedOutput)) <= 0) {
                clearSelection();
            }
        }
        if (page == CraftingTerminalPage.CRAFTABLE) {
            logCraftableRefresh(craftableBuild, sortNanos, syncNanos);
        }
    }

    private static void logCraftableRefresh(
            CraftableBuildResult build,
            long sortNanos,
            long syncNanos
    ) {
        if (build == null) return;
        long totalNanos = build.candidateSelectionNanos()
                + build.variantResolutionNanos()
                + build.previewSimulationNanos()
                + sortNanos
                + syncNanos;
        if (totalNanos < 50_000_000L) return;
        MagicStorage.LOGGER.info(
                "Craftable refresh took {} ms: candidates={} variants={} outputs={}, "
                        + "candidateSelection={} ms, variantResolution={} ms, "
                        + "previewSimulation={} ms, sort={} ms, sync={} ms",
                totalNanos / 1_000_000L,
                build.candidates(),
                build.variants(),
                build.stacks().size(),
                build.candidateSelectionNanos() / 1_000_000L,
                build.variantResolutionNanos() / 1_000_000L,
                build.previewSimulationNanos() / 1_000_000L,
                sortNanos / 1_000_000L,
                syncNanos / 1_000_000L);
    }

    private void cacheSharedCraftable(
            StorageCoreBlockEntity core,
            List<ItemStack> stacks
    ) {
        if (usePlayerInventory) return;
        Level level = core.getLevel();
        if (level == null) return;
        SHARED_CRAFTABLE_CACHE.put(core, new SharedCraftableCache(
                level.getRecipeManager().getRecipes(),
                core.getCraftableRevision(),
                core.getMachineRevision(),
                core.getTopologyRevision(),
                currentFilter,
                getSortMode(),
                getSortOrder(),
                getResourceView(),
                stacks,
                nextCraftableEnergyThreshold,
                nextCraftableStationThreshold));
    }

    private boolean restoreSharedCraftableCache(StorageCoreBlockEntity core) {
        if (usePlayerInventory) return false;
        Level level = core.getLevel();
        SharedCraftableCache cache = SHARED_CRAFTABLE_CACHE.get(core);
        if (level == null || cache == null
                || cache.recipeSnapshot() != level.getRecipeManager().getRecipes()
                || cache.craftableRevision() != core.getCraftableRevision()
                || cache.machineRevision() != core.getMachineRevision()
                || cache.topologyRevision() != core.getTopologyRevision()
                || !cache.filter().equals(currentFilter)
                || cache.sortMode() != getSortMode()
                || cache.sortOrder() != getSortOrder()
                || cache.resourceView() != getResourceView()
                || generatedWorkCrossedThreshold(core, cache)) {
            return false;
        }
        System.arraycopy(
                cache.energyThresholds(),
                0,
                nextCraftableEnergyThreshold,
                0,
                nextCraftableEnergyThreshold.length);
        nextCraftableStationThreshold.clear();
        nextCraftableStationThreshold.putAll(cache.stationThresholds());
        List<ItemStack> updated = cache.stacks().stream()
                .map(stack -> updateCraftableDisplayAmount(core, stack))
                .toList();
        totalItemTypes = updated.size();
        refreshDisplayMetadata(core);
        int maxOffset = Math.max(
                0, totalItemTypes - getVisibleRows() * DISPLAY_COLS);
        scrollOffset = Math.min(scrollOffset, maxOffset);
        replaceVisibleDisplayStacks(updated, getVisibleRows());
        return true;
    }

    private static boolean generatedWorkCrossedThreshold(
            StorageCoreBlockEntity core,
            SharedCraftableCache cache
    ) {
        for (EnergyType type : EnergyType.values()) {
            long threshold = cache.energyThresholds()[type.ordinal()];
            if (threshold != Long.MAX_VALUE && core.getEnergy(type) >= threshold) return true;
        }
        for (Map.Entry<ResourceLocation, Long> entry
                : cache.stationThresholds().entrySet()) {
            if (core.getStationWork(entry.getKey()) >= entry.getValue()) return true;
        }
        return false;
    }

    private static ItemStack updateCraftableDisplayAmount(
            StorageCoreBlockEntity core,
            ItemStack stack
    ) {
        if (stack.isEmpty() || core.getLevel() == null) return stack;
        StorageResourceKey key = TerminalResourceDisplay.key(stack).orElseGet(() ->
                StorageResourceKey.item(
                        TerminalDisplayStack.strip(stack),
                        core.getLevel().registryAccess()));
        long amount = core.getResourceAmount(key);
        ItemStack icon = TerminalDisplayStack.strip(stack);
        return key.kindId().equals(StorageResourceKindApi.ITEM_KIND)
                ? TerminalDisplayStack.create(icon, amount)
                : TerminalResourceDisplay.create(icon, key, amount);
    }

    private boolean isSelectedRecipeCurrent(StorageCoreBlockEntity core) {
        if (selectedRecipeId == null || selectedOutput.isEmpty()) return false;
        Level level = core.getLevel();
        if (level == null) return false;
        List<IngredientSource> sources = snapshotIngredientSources(
                core, playerInventory == null ? null : playerInventory.player);
        RecipeAdapterMatch match = resolveAvailableRecipeVariantById(
                level, core, selectedRecipeId, selectedOutput, sources);
        if (match == null) return false;
        return matchesSelectionOutput(match, selectedOutput, level);
    }

    private CraftableBuildResult buildCraftableDisplayStacks(
            StorageCoreBlockEntity core,
            Player player
    ) {
        Level level = core.getLevel();
        if (level == null) {
            return new CraftableBuildResult(List.of(), 0, 0, 0, 0, 0);
        }
        if (core.isConflicted()) {
            return new CraftableBuildResult(List.of(), 0, 0, 0, 0, 0);
        }
        Arrays.fill(nextCraftableEnergyThreshold, Long.MAX_VALUE);
        nextCraftableStationThreshold.clear();
        boolean includesPlayerSources = usePlayerInventory && player != null;
        List<IngredientSource> sources = includesPlayerSources
                ? snapshotIngredientSources(core, player) : List.of();
        IngredientAvailability availability = IngredientAvailability.create(
                core,
                sources,
                includesPlayerSources);
        TerminalSearchQuery query = TerminalSearchQuery.compile(currentFilter);
        Map<StorageResourceKey, CraftableOutput> craftableOutputs = new LinkedHashMap<>();
        long candidateSelectionStarted = System.nanoTime();
        List<CraftableRecipeCatalog.Candidate> candidates =
                craftableRecipeCatalog.getCandidates(
                level, availability.items());
        long candidateSelectionNanos = System.nanoTime() - candidateSelectionStarted;
        long variantResolutionNanos = 0;
        long previewSimulationNanos = 0;
        int variants = 0;
        for (CraftableRecipeCatalog.Candidate candidate : candidates) {
            long variantStarted = System.nanoTime();
            RecipeHolder<?> holder =
                    level.getRecipeManager().byKey(candidate.id()).orElse(null);
            RecipeAdapterMatch baseMatch = holder == null ? null : candidate.match(holder);
            if (baseMatch != null && !isStationAvailable(core, baseMatch)) {
                baseMatch = null;
            }
            if (baseMatch != null && !hasPotentialRecipeInputs(
                    baseMatch, core, availability)) {
                baseMatch = null;
            }
            List<RecipeAdapterMatch> resolved = baseMatch == null
                    ? List.of() : candidate.resolveVariants(
                            baseMatch,
                            variantAvailableStacks(baseMatch, availability),
                            level);
            variantResolutionNanos += System.nanoTime() - variantStarted;
            variants += resolved.size();
            for (RecipeAdapterMatch match : resolved) {
                ItemStack output = match.presentationOutput(List.of(), level);
                StorageResourceKey key =
                        match.selectionOutputKey(level, output).orElse(null);
                if (key == null || output.isEmpty() || !getResourceView().matches(key)
                        || !matchesCraftableFilter(key, output, query, level)) continue;
                long previewStarted = System.nanoTime();
                CraftableStatus status = computeCraftableStatus(match, core, availability);
                long previewNanos = System.nanoTime() - previewStarted;
                previewSimulationNanos += previewNanos;
                if (!status.craftable()) {
                    recordNextCraftableThreshold(match, status.inputsAvailable(), core);
                    continue;
                }
                craftableOutputs.putIfAbsent(key, new CraftableOutput(
                        key, output.copyWithCount(1), core.getResourceAmount(key)));
            }
        }
        for (RecipeHolder<?> holder : axeTransformationCatalog.recipes(level, core)) {
            long variantStarted = System.nanoTime();
            RecipeAdapterMatch match = classifyAvailable(holder, core);
            variantResolutionNanos += System.nanoTime() - variantStarted;
            if (match == null) continue;
            variants++;
            ItemStack output = match.presentationOutput(List.of(), level);
            if (output.isEmpty()) continue;
            StorageResourceKey key = StorageResourceKey.item(output, level.registryAccess());
            if (!getResourceView().matches(key)
                    || !matchesCraftableFilter(key, output, query, level)) continue;
            long previewStarted = System.nanoTime();
            CraftableStatus status = computeCraftableStatus(match, core, availability);
            previewSimulationNanos += System.nanoTime() - previewStarted;
            if (!status.craftable()) {
                recordNextCraftableThreshold(match, status.inputsAvailable(), core);
                continue;
            }
            craftableOutputs.putIfAbsent(key, new CraftableOutput(
                    key, output.copyWithCount(1), core.getResourceAmount(key)));
        }
        List<ItemStack> result = new ArrayList<>(craftableOutputs.size());
        for (CraftableOutput output : craftableOutputs.values()) {
            result.add(output.key().kindId().equals(StorageResourceKindApi.ITEM_KIND)
                    ? TerminalDisplayStack.create(output.icon(), output.storedAmount())
                    : TerminalResourceDisplay.create(
                    output.icon(), output.key(), output.storedAmount()));
        }
        return new CraftableBuildResult(
                result,
                candidates.size(),
                variants,
                candidateSelectionNanos,
                variantResolutionNanos,
                previewSimulationNanos);
    }

    private static List<ItemStack> variantAvailableStacks(
            RecipeAdapterMatch match,
            IngredientAvailability availability
    ) {
        if (!match.adapter().requiresAvailableStacksForVariants()) return List.of();
        List<RecipeAdapterMatch.Input> inputs = match.orderedInputs();
        if (inputs.isEmpty()) {
            return availability.sources().stream().map(IngredientSource::stack).toList();
        }
        Set<IngredientSource> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ItemStack> matching = new ArrayList<>();
        for (RecipeAdapterMatch.Input input : inputs) {
            if (input.isEmpty()) continue;
            for (IngredientSource source : availability.matching(input)) {
                if (input.test(source.stack()) && seen.add(source)) {
                    matching.add(source.stack());
                }
            }
        }
        return matching;
    }

    private boolean energyCrossedCraftableThreshold(StorageCoreBlockEntity core) {
        for (EnergyType type : ENERGY_SYNC_ORDER) {
            long current = core.getEnergy(type);
            long previous = energyAmounts[type.ordinal()];
            if (current < previous
                    || current > previous
                    && current >= nextCraftableEnergyThreshold[type.ordinal()]) return true;
        }
        return false;
    }

    private void recordNextCraftableThreshold(
            RecipeAdapterMatch match,
            CraftPreview preview,
            StorageCoreBlockEntity core
    ) {
        recordNextCraftableThreshold(
                match,
                preview.ingredients().stream()
                        .allMatch(input -> input.available() >= input.required()),
                core);
    }

    private void recordNextCraftableThreshold(
            RecipeAdapterMatch match,
            boolean inputsAvailable,
            StorageCoreBlockEntity core
    ) {
        if (!inputsAvailable) return;
        RecipeAdapterMatch.ToolCost tool = match.cost().toolCost().orElse(null);
        if (tool != null && !core.hasDescriptorAmount(tool.descriptorId(), tool.amountPerCraft())) {
            return;
        }
        EnergyCost energy = match.cost().energyCost().orElse(null);
        if (energy != null) {
            recordEnergyThreshold(core, energy.processType(), energy.processAmount());
            recordEnergyThreshold(core, energy.fuelType(), energy.fuelAmount());
        }
        RecipeAdapterMatch.StationWorkCost station =
                match.cost().stationWorkCost().orElse(null);
        if (station != null && core.getStationWork(station.descriptorId())
                < station.amountPerCraft()) {
            nextCraftableStationThreshold.merge(
                    station.descriptorId(), station.amountPerCraft(), Math::min);
        }
    }

    private CraftableStatus computeCraftableStatus(
            RecipeAdapterMatch match,
            StorageCoreBlockEntity core,
            IngredientAvailability availability
    ) {
        boolean inputsAvailable = match.typedRecipePlan()
                .map(plan -> typedInputsAvailableForOne(core, plan, availability))
                .orElseGet(() -> {
                    List<RecipeAdapterMatch.Input> ingredients = match.orderedInputs();
                    return canAllocateIngredientsForListing(
                            ingredients, summarizeIngredients(ingredients), availability);
                });
        return new CraftableStatus(
                inputsAvailable && hasRecipeCostsForCrafts(core, match.cost(), 1),
                inputsAvailable);
    }

    private boolean hasPotentialRecipeInputs(
            RecipeAdapterMatch match,
            StorageCoreBlockEntity core,
            IngredientAvailability availability
    ) {
        TypedRecipePlan plan = match.typedRecipePlan().orElse(null);
        if (plan != null) {
            for (TypedRecipeInput input : plan.inputs()) {
                if (typedInputAvailable(core, input, availability) < input.amount()) return false;
            }
            return true;
        }
        if (match.contract().pendingTypedPlan()) return true;
        for (IngredientNeed need : summarizeIngredients(match.orderedInputs())) {
            if (need.ingredient().matchesAllItemVariants()) {
                if (availability.matchingAllItemVariants(need.ingredient())
                        < need.count()) return false;
                continue;
            }
            long available = 0;
            for (IngredientSource source : availability.matching(need.ingredient())) {
                if (need.ingredient().test(source.stack())) {
                    available = saturatingAdd(available, source.amount());
                }
            }
            if (available < need.count()) return false;
        }
        return true;
    }

    private static boolean typedInputsAvailableForOne(
            StorageCoreBlockEntity core,
            TypedRecipePlan plan,
            IngredientAvailability availability
    ) {
        Map<StorageResourceKey, Integer> consumedBy = new HashMap<>();
        for (TypedRecipeInput input : plan.inputs()) {
            if (typedInputAvailable(core, input, availability) < input.amount()) return false;
            if (input.role() != TypedRecipeInput.Role.CONSUME) continue;
            for (StorageResourceKey alternative : input.alternatives()) {
                consumedBy.merge(alternative, 1, Integer::sum);
            }
        }
        if (consumedBy.values().stream().noneMatch(count -> count > 1)) return true;
        return planTypedConsumption(core, plan, 1, availability.sources()) != null;
    }

    private boolean canAllocateIngredientsForListing(
            List<RecipeAdapterMatch.Input> ingredients,
            List<IngredientNeed> needs,
            IngredientAvailability availability
    ) {
        Map<IngredientSource, Integer> sourceMatches = new IdentityHashMap<>();
        List<IngredientSource> relevantSources = new ArrayList<>();
        for (IngredientNeed need : needs) {
            if (need.ingredient().matchesAllItemVariants()) {
                if (availability.matchingAllItemVariants(need.ingredient())
                        < need.count()) return false;
                continue;
            }
            long available = 0;
            for (IngredientSource source : availability.matching(need.ingredient())) {
                if (!need.ingredient().test(source.stack())) continue;
                available = saturatingAdd(available, source.amount());
                if (sourceMatches.merge(source, 1, Integer::sum) == 1) {
                    relevantSources.add(source);
                }
            }
            if (available < need.count()) return false;
        }
        if (sourceMatches.values().stream().anyMatch(matches -> matches > 1)) {
            return planIngredients(ingredients, 1, relevantSources) != null;
        }
        return true;
    }

    private static long typedInputAvailable(
            StorageCoreBlockEntity core,
            TypedRecipeInput input,
            IngredientAvailability availability
    ) {
        long available = 0;
        for (StorageResourceKey alternative : input.alternatives()) {
            if (!alternative.kindId().equals(StorageResourceKindApi.ITEM_KIND)) {
                available = saturatingAdd(available, core.getResourceAmount(alternative));
                continue;
            }
            Level level = core.getLevel();
            if (level == null) continue;
            ItemKey key = StorageResourceBridge.itemKey(
                    alternative, level.registryAccess()).orElse(null);
            if (key != null) {
                available = saturatingAdd(
                        available, availability.amount(key));
            }
        }
        return available;
    }

    private void recordEnergyThreshold(
            StorageCoreBlockEntity core,
            EnergyType type,
            long required
    ) {
        if (required > 0 && core.getEnergy(type) < required) {
            nextCraftableEnergyThreshold[type.ordinal()] =
                    Math.min(nextCraftableEnergyThreshold[type.ordinal()], required);
        }
    }

    private static boolean matchesCraftableFilter(
            StorageResourceKey key,
            ItemStack representative,
            TerminalSearchQuery query,
            Level level
    ) {
        if (key.kindId().equals(StorageResourceKindApi.ITEM_KIND)) {
            ItemKey item = StorageResourceBridge.itemKey(key, level.registryAccess()).orElse(null);
            return item != null && query.matches(TerminalSearchEntry.create(item));
        }
        return query.matches(key, representative);
    }

    private void sortCraftableDisplayStacks(List<ItemStack> stacks) {
        stacks.sort(TerminalEntryComparator.forMode(getSortMode(), getSortOrder()));
    }

    private boolean isCraftableOutput(StorageCoreBlockEntity core, ItemStack output, Player player) {
        Level level = core.getLevel();
        if (level == null) return false;
        List<IngredientSource> sources = snapshotIngredientSources(core, player);
        for (RecipeHolder<?> holder : findRecipes(level, output)) {
            RecipeAdapterMatch match = resolveAvailableRecipeVariant(
                    holder, core, output, sources, level);
            if (match != null && computeCraftPreviewFor(match, core, sources).craftable() > 0) {
                return true;
            }
        }
        return false;
    }

    private List<RecipeHolder<?>> findRecipes(Level level, ItemStack output) {
        output = TerminalDisplayStack.strip(output);
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        StorageCoreBlockEntity core = getCore(level);
        if (core == null) return recipes;
        List<IngredientSource> sources = snapshotIngredientSources(
                core, playerInventory == null ? null : playerInventory.player);
        for (RecipeType<?> type : BuiltInRecipeAdapters.discoveryTypes()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Collection<RecipeHolder<?>> holders = (Collection) manager.getAllRecipesFor((RecipeType) type);
            for (RecipeHolder<?> holder : holders) {
                RecipeAdapterMatch match = resolveAvailableRecipeVariant(
                        holder, core, output, sources, level);
                if (match != null) {
                    recipes.add(holder);
                }
            }
        }
        for (RecipeHolder<?> holder : axeTransformationCatalog.recipes(level, core)) {
            RecipeAdapterMatch match = classifyAvailable(holder, core);
            if (match == null) continue;
            ItemStack result = match.presentationOutput(List.of(), level);
            if (!result.isEmpty() && ItemStack.isSameItemSameComponents(result, output)) {
                recipes.add(holder);
            }
        }

        recipes.sort(Comparator.comparingInt(CraftingTerminalMenu::getRecipeSortOrder));
        return recipes;
    }
}
