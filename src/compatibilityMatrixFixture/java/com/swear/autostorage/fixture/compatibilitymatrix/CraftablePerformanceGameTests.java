package com.swear.autostorage.fixture.compatibilitymatrix;

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.Action;
import com.swear.autostorage.Actor;
import com.swear.autostorage.CraftingTerminalPage;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.SearchMode;
import com.swear.autostorage.SortMode;
import com.swear.autostorage.SortOrder;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageTerminalMenu;
import com.swear.autostorage.TerminalOutputDestination;
import com.swear.autostorage.TerminalPreferences;
import com.swear.autostorage.TerminalResourceView;
import com.swear.autostorage.TerminalSettingsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@GameTestHolder(CompatibilityMatrixFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class CraftablePerformanceGameTests {
    private static final int CRAFTABLE_PAGE_BUTTON = 6;
    private static final int STORAGE_PAGE_BUTTON = 14;
    private static final long MAX_PREFETCH_NANOS = 50_000_000L;
    private static final long MAX_SWITCH_NANOS = 50_000_000L;
    private static final long MAX_STORAGE_INTERACTION_NANOS = 250_000_000L;
    private static final long MAX_BASELINE_INDEX_RETAINED_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_MENU_RETAINED_BYTES = 128L * 1024L;
    private static final int WARMUP_COUNT = 10;
    private static final int SAMPLE_COUNT = 20;
    private static final int RETAINED_MENU_COUNT = 16;
    private static final int SEED_BATCH_TYPES = 1_000;
    private static final int STORED_TYPE_COUNT = terminalScaleTypes();
    private static final List<String> STORED_ITEM_IDS = List.of(
            "minecraft:oak_log",
            "minecraft:cobblestone",
            "minecraft:brown_mushroom",
            "minecraft:red_mushroom",
            "minecraft:bowl",
            "minecraft:netherite_upgrade_smithing_template",
            "minecraft:diamond_sword",
            "minecraft:netherite_ingot",
            "minecraft:iron_ingot",
            "minecraft:bone_meal",
            "minecraft:sugar_cane",
            "minecraft:fishing_rod",
            "minecraft:wheat_seeds",
            "minecraft:fermented_spider_eye",
            "minecraft:sugar",
            "minecraft:milk_bucket",
            "minecraft:amethyst_shard",
            "minecraft:mossy_cobblestone",
            "minecraft:glass_pane",
            "minecraft:glass_bottle",
            "minecraft:honey_bottle",
            "modern_industrialization:aluminum_blade",
            "ars_nouveau:source_gem",
            "create:andesite_alloy",
            "botania:mana_powder",
            "botania:manasteel_ingot",
            "botania:livingrock",
            "botania:mana_pearl",
            "botania:mana_diamond",
            "botania:white_mystical_petal");

    private CraftablePerformanceGameTests() {
    }

    private static int terminalScaleTypes() {
        int requested = Integer.getInteger(
                "auto_storage.terminalScaleTypes", 10_000);
        if (requested != 10_000 && requested != 30_000) {
            throw new IllegalArgumentException(
                    "auto_storage.terminalScaleTypes must be 10000 or 30000");
        }
        return requested;
    }

    @GameTest(template = "craftingtests.platform", timeoutTicks = 2_000)
    public static void full_support_pack_switches_craftable_under_fifty_milliseconds(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(
                corePos,
                AutoStorage.STORAGE_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(
                corePos.east(),
                AutoStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(),
                Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Craftable benchmark Core not found");
                return;
            }
            core.rebuildNetwork(level);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(
                    corePos.getX() + 0.5,
                    corePos.getY() + 0.5,
                    corePos.getZ() + 0.5);
            new BenchmarkRun(helper, player, core).start();
        });
    }

    private static final class BenchmarkRun {
        private final GameTestHelper helper;
        private final net.minecraft.world.entity.player.Player player;
        private final StorageCoreBlockEntity core;
        private final long[] prefetchNanos = new long[SAMPLE_COUNT];
        private final long[] switchNanos = new long[SAMPLE_COUNT];
        private final long[] warmNanos = new long[SAMPLE_COUNT];
        private SwitchTimes first;
        private int step;
        private long heapWithoutIndex;
        private long indexRetainedBytes;
        private long heapBeforeMenus;
        private long terminalPreparationNanos;
        private long craftablePreparationNanos;
        private StorageMetrics storageMetrics;
        private PersistenceMetrics persistenceMetrics;
        private List<Item> variantItems;
        private int variantIndex;

        private BenchmarkRun(
                GameTestHelper helper,
                net.minecraft.world.entity.player.Player player,
                StorageCoreBlockEntity core
        ) {
            this.helper = helper;
            this.player = player;
            this.core = core;
        }

        private void start() {
            run(() -> {
                variantItems = seedBaseScenario(core);
                schedule(this::seedBatch);
            });
        }

        private void seedBatch() {
            run(() -> {
                int inserted = 0;
                while (core.getTypeCount() < STORED_TYPE_COUNT
                        && inserted++ < SEED_BATCH_TYPES) {
                    ItemStack stack = new ItemStack(
                            variantItems.get(variantIndex % variantItems.size()),
                            variantIndex % 64 + 1);
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                            String.format(
                                    Locale.ROOT,
                                    "Terminal Scale %05d",
                                    variantIndex)));
                    int expected = stack.getCount();
                    if (core.insertItem(stack) != expected) {
                        throw new IllegalStateException(
                                "Could not seed exact terminal-scale variant "
                                        + variantIndex);
                    }
                    variantIndex++;
                }
                if (core.getTypeCount() < STORED_TYPE_COUNT) {
                    schedule(this::seedBatch);
                } else {
                    installInstantStations(core);
                    terminalPreparationNanos = 0L;
                    schedule(this::measureStorage);
                }
            });
        }

        private void measureStorage() {
            run(() -> {
                storageMetrics = measureStorageInteractions(player, core);
                CraftingTerminalMenu preparationMenu =
                        new CraftingTerminalMenu(
                                596, player.getInventory(), core);
                long started = System.nanoTime();
                if (!preparationMenu.clickMenuButton(
                        player, CRAFTABLE_PAGE_BUTTON)) {
                    throw new IllegalStateException(
                            "Could not prepare Craftable benchmark");
                }
                craftablePreparationNanos = System.nanoTime() - started;
                preparationMenu.removed(player);
                helper.runAfterDelay(40, this::measureFirst);
            });
        }

        private void measureFirst() {
            run(() -> {
                AutoStorage.LOGGER.info("TERMINAL_SCALE_WARM_INTERACTIONS_BEGIN");
                first = measureSwitch(player, core, 700);
                if (first.outputCount() <= 0) {
                    throw new IllegalStateException(
                            "Craftable benchmark produced no outputs");
                }
                step = 0;
                schedule(this::warmup);
            });
        }

        private void warmup() {
            run(() -> {
                measureSwitch(player, core, 701 + step++);
                if (step < WARMUP_COUNT) schedule(this::warmup);
                else {
                    step = 0;
                    schedule(this::sample);
                }
            });
        }

        private void sample() {
            run(() -> {
                SwitchTimes measured =
                        measureSwitch(player, core, 701 + WARMUP_COUNT + step);
                if (measured.outputCount() != first.outputCount()) {
                    throw new IllegalStateException(
                            "Craftable benchmark output count changed from "
                                    + first.outputCount() + " to " + measured.outputCount());
                }
                prefetchNanos[step] = measured.prefetchNanos();
                switchNanos[step] = measured.switchNanos();
                warmNanos[step] = measured.warmNanos();
                if (++step < SAMPLE_COUNT) schedule(this::sample);
                else {
                    AutoStorage.LOGGER.info("TERMINAL_SCALE_WARM_INTERACTIONS_END");
                    schedule(this::measurePersistence);
                }
            });
        }

        private void measurePersistence() {
            run(() -> {
                persistenceMetrics = measurePersistenceRoundTrip(core);
                helper.runAfterDelay(40, this::measureHeapBaseline);
            });
        }

        private void measureHeapBaseline() {
            run(() -> {
                clearCatalogCache();
                clearBuiltInCaches();
                clearRecipeFamilyCaches();
                heapWithoutIndex = usedHeapAfterFullGc();
                schedule(this::measureSharedIndex);
            });
        }

        private void measureSharedIndex() {
            run(() -> {
                prewarmCatalog(helper.getLevel());
                prewarmCraftable(player, core);
                indexRetainedBytes = Math.max(
                        0L, usedHeapAfterFullGc() - heapWithoutIndex);
                heapBeforeMenus = usedHeapAfterFullGc();
                schedule(this::measureMenus);
            });
        }

        private void measureMenus() {
            run(() -> {
                List<CraftingTerminalMenu> retainedMenus =
                        new ArrayList<>(RETAINED_MENU_COUNT);
                for (int index = 0; index < RETAINED_MENU_COUNT; index++) {
                    CraftingTerminalMenu menu =
                            new CraftingTerminalMenu(800 + index, player.getInventory(), core);
                    menu.broadcastChanges();
                    if (!menu.clickMenuButton(player, CRAFTABLE_PAGE_BUTTON)) {
                        throw new IllegalStateException(
                                "Craftable benchmark could not retain menu " + index);
                    }
                    retainedMenus.add(menu);
                }
                long menusRetainedBytes = Math.max(
                        0L, usedHeapAfterFullGc() - heapBeforeMenus);
                long perMenuRetainedBytes = menusRetainedBytes / RETAINED_MENU_COUNT;
                retainedMenus.forEach(menu -> menu.removed(player));
                finish(menusRetainedBytes, perMenuRetainedBytes);
            });
        }

        private void finish(long menusRetainedBytes, long perMenuRetainedBytes)
                throws IOException {
            Arrays.sort(prefetchNanos);
            Arrays.sort(switchNanos);
            Arrays.sort(warmNanos);
            long prefetchP95Nanos = percentile95(prefetchNanos);
            long switchP95Nanos = percentile95(switchNanos);
            long warmP95Nanos = percentile95(warmNanos);
            writeReport(
                    helper.getLevel().getRecipeManager().getRecipes().size(),
                    core.getTypeCount(),
                    first.outputCount(),
                    first.prefetchNanos(),
                    prefetchP95Nanos,
                    first.switchNanos(),
                    switchP95Nanos,
                    warmP95Nanos,
                    indexRetainedBytes,
                    menusRetainedBytes,
                    perMenuRetainedBytes,
                    storageMetrics,
                    persistenceMetrics,
                    terminalPreparationNanos,
                    craftablePreparationNanos);
            AutoStorage.LOGGER.info(
                    "CRAFTABLE_BENCHMARK recipes={} storedTypes={} outputs={} "
                            + "firstPrefetchMs={} prefetchP95Ms={} "
                            + "firstSwitchMs={} switchP95Ms={} warmSwitchP95Ms={} "
                            + "indexRetainedBytes={} menusRetainedBytes={} perMenuBytes={}",
                    helper.getLevel().getRecipeManager().getRecipes().size(),
                    core.getTypeCount(),
                    first.outputCount(),
                    millis(first.prefetchNanos()),
                    millis(prefetchP95Nanos),
                    millis(first.switchNanos()),
                    millis(switchP95Nanos),
                    millis(warmP95Nanos),
                    indexRetainedBytes,
                    menusRetainedBytes,
                    perMenuRetainedBytes);
            if (prefetchP95Nanos >= MAX_PREFETCH_NANOS
                    || first.switchNanos() >= MAX_SWITCH_NANOS
                    || switchP95Nanos >= MAX_SWITCH_NANOS
                    || warmP95Nanos >= MAX_SWITCH_NANOS) {
                throw new IllegalStateException(
                        "Craftable prefetch/switch budget exceeded");
            }
            if (craftablePreparationNanos >= MAX_SWITCH_NANOS) {
                throw new IllegalStateException(
                        "craftable_prepare_ms must be < 50: "
                                + millis(craftablePreparationNanos));
            }
            if (STORED_TYPE_COUNT == 10_000
                    && indexRetainedBytes >= MAX_BASELINE_INDEX_RETAINED_BYTES) {
                throw new IllegalStateException(
                        "Craftable shared index retained " + indexRetainedBytes + " bytes");
            }
            if (perMenuRetainedBytes >= MAX_MENU_RETAINED_BYTES) {
                throw new IllegalStateException(
                        "Craftable menu retained " + perMenuRetainedBytes + " bytes");
            }
            helper.succeed();
        }

        private void schedule(Runnable action) {
            helper.runAfterDelay(1, action);
        }

        private void run(ThrowingRunnable action) {
            try {
                action.run();
            } catch (Exception exception) {
                helper.fail("Craftable benchmark failed: " + exception);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static SwitchTimes measureSwitch(
            net.minecraft.world.entity.player.Player player,
            StorageCoreBlockEntity core,
            int containerId
    ) {
        CraftingTerminalMenu menu =
                new CraftingTerminalMenu(containerId, player.getInventory(), core);
        long prefetchStarted = System.nanoTime();
        menu.broadcastChanges();
        long prefetchNanos = System.nanoTime() - prefetchStarted;
        long switchStarted = System.nanoTime();
        if (!menu.clickMenuButton(player, CRAFTABLE_PAGE_BUTTON)) {
            throw new IllegalStateException("Craftable benchmark prefetched switch was rejected");
        }
        long switchNanos = System.nanoTime() - switchStarted;
        int outputCount = menu.getTotalItemTypes();
        if (!menu.clickMenuButton(player, STORAGE_PAGE_BUTTON)) {
            throw new IllegalStateException("Craftable benchmark Storage switch was rejected");
        }
        long warmStarted = System.nanoTime();
        if (!menu.clickMenuButton(player, CRAFTABLE_PAGE_BUTTON)) {
            throw new IllegalStateException("Craftable benchmark warm switch was rejected");
        }
        long warmNanos = System.nanoTime() - warmStarted;
        menu.removed(player);
        return new SwitchTimes(prefetchNanos, switchNanos, warmNanos, outputCount);
    }

    private static List<Item> seedBaseScenario(StorageCoreBlockEntity core) {
        for (String itemId : STORED_ITEM_IDS) {
            ResourceLocation id = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null || BuiltInRegistries.ITEM.getKey(item).equals(
                    BuiltInRegistries.ITEM.getDefaultKey())) {
                throw new IllegalStateException("Craftable benchmark item is missing: " + id);
            }
            ItemStack stack = new ItemStack(item, 64);
            if (core.insertItem(stack) != 64) {
                throw new IllegalStateException("Could not seed Craftable benchmark item " + id);
            }
        }
        List<Item> variantItems = STORED_ITEM_IDS.stream()
                .map(ResourceLocation::parse)
                .map(BuiltInRegistries.ITEM::get)
                .toList();
        return variantItems;
    }

    private static void installInstantStations(StorageCoreBlockEntity core) {
        Container machines = machineContainer(core);
        for (int slot = 0; slot < MachineEnergyTable.entries().size(); slot++) {
            MachineDescriptor descriptor = MachineEnergyTable.get(slot);
            if (descriptor.category() != MachineCategory.INSTANT) continue;
            machines.setItem(slot, descriptor.representativeStack());
        }
    }

    private static StorageMetrics measureStorageInteractions(
            net.minecraft.world.entity.player.Player player,
            StorageCoreBlockEntity core
    ) {
        long started = System.nanoTime();
        StorageTerminalMenu first = new StorageTerminalMenu(
                600, player.getInventory(), core);
        long firstOpenNanos = System.nanoTime() - started;
        int initialPacketBytes = contentPacketBytes(first, core);
        first.removed(player);

        started = System.nanoTime();
        StorageTerminalMenu menu = new StorageTerminalMenu(
                601, player.getInventory(), core);
        long warmOpenNanos = System.nanoTime() - started;
        Map<String, Long> interactions = new java.util.LinkedHashMap<>();
        for (SortMode mode : SortMode.values()) {
            for (SortOrder order : SortOrder.values()) {
                TerminalPreferences preferences = new TerminalPreferences(
                        mode,
                        order,
                        SearchMode.OFF,
                        TerminalResourceView.ITEM,
                        CraftingTerminalPage.STORAGE,
                        false,
                        TerminalOutputDestination.PLAYER,
                        null);
                started = System.nanoTime();
                menu.applySettings(new TerminalSettingsPacket(
                        menu.containerId,
                        StorageTerminalMenu.INITIAL_DISPLAY_ROWS,
                        preferences), player);
                menu.refreshDisplayItems(core);
                interactions.put(
                        "sort_" + mode.name().toLowerCase(Locale.ROOT)
                                + "_" + order.name().toLowerCase(Locale.ROOT),
                        System.nanoTime() - started);
            }
        }
        for (Map.Entry<String, String> query : Map.of(
                "plain", "terminal scale 099",
                "mod", "@minecraft",
                "tag", "#minecraft:logs").entrySet()) {
            started = System.nanoTime();
            menu.applyFilter(core, query.getValue());
            interactions.put(
                    "search_" + query.getKey(), System.nanoTime() - started);
            menu.applyFilter(core, "");
        }

        menu.scrollTo(StorageTerminalMenu.DISPLAY_COLS);
        started = System.nanoTime();
        menu.refreshDisplayItems(core);
        interactions.put("scroll_first", System.nanoTime() - started);
        List<ItemStack> beforeFar = displayStacks(menu);
        int totalRows = (menu.getTotalItemTypes()
                + StorageTerminalMenu.DISPLAY_COLS - 1)
                / StorageTerminalMenu.DISPLAY_COLS;
        int farOffset = Math.max(
                0,
                totalRows - menu.getVisibleRows())
                * StorageTerminalMenu.DISPLAY_COLS;
        menu.scrollTo(farOffset);
        started = System.nanoTime();
        menu.refreshDisplayItems(core);
        interactions.put("scroll_far", System.nanoTime() - started);
        PacketMetrics packetMetrics = slotPacketBytes(beforeFar, menu, core);

        menu.scrollTo(0);
        menu.refreshDisplayItems(core);
        ItemKey oak = ItemKey.of(new ItemStack(
                BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("oak_log"))));
        started = System.nanoTime();
        if (core.insertItemCount(oak, 1, Action.EXECUTE, Actor.EMPTY) != 1) {
            throw new IllegalStateException("Could not mutate visible benchmark item");
        }
        menu.broadcastChanges();
        interactions.put("mutation_visible_update", System.nanoTime() - started);
        menu.removed(player);

        long[] warm = new long[interactions.size() + 1];
        warm[0] = warmOpenNanos;
        int index = 1;
        for (long nanos : interactions.values()) warm[index++] = nanos;
        Arrays.sort(warm);
        long p95 = percentile95(warm);
        AutoStorage.LOGGER.info(
                "STORAGE_BENCHMARK firstOpenMs={} warmOpenMs={} interactionP95Ms={} interactions={}",
                millis(firstOpenNanos),
                millis(warmOpenNanos),
                millis(p95),
                interactions.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> millis(entry.getValue()),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new)));
        if (firstOpenNanos >= MAX_SWITCH_NANOS) {
            throw new IllegalStateException(
                    "Cold Storage first open exceeded 50 ms: "
                            + millis(firstOpenNanos));
        }
        if (p95 >= MAX_STORAGE_INTERACTION_NANOS) {
            throw new IllegalStateException(
                    "Storage interaction p95 exceeded 250 ms: " + millis(p95));
        }
        return new StorageMetrics(
                firstOpenNanos,
                warmOpenNanos,
                Map.copyOf(interactions),
                p95,
                initialPacketBytes,
                first.slots.size(),
                packetMetrics.changedSlots(),
                packetMetrics.totalBytes(),
                packetMetrics.largestBytes());
    }

    private static List<ItemStack> displayStacks(StorageTerminalMenu menu) {
        List<ItemStack> stacks = new ArrayList<>(StorageTerminalMenu.DISPLAY_SLOTS);
        for (int slot = 0; slot < StorageTerminalMenu.DISPLAY_SLOTS; slot++) {
            stacks.add(menu.getSlot(slot).getItem().copy());
        }
        return List.copyOf(stacks);
    }

    private static int contentPacketBytes(
            StorageTerminalMenu menu,
            StorageCoreBlockEntity core
    ) {
        net.minecraft.core.NonNullList<ItemStack> items =
                net.minecraft.core.NonNullList.withSize(
                        menu.slots.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < menu.slots.size(); slot++) {
            items.set(slot, menu.getSlot(slot).getItem().copy());
        }
        var packet = new net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket(
                menu.containerId, 0, items, menu.getCarried().copy());
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(),
                java.util.Objects.requireNonNull(core.getLevel()).registryAccess());
        net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
                .STREAM_CODEC.encode(buffer, packet);
        return buffer.readableBytes();
    }

    private static PacketMetrics slotPacketBytes(
            List<ItemStack> before,
            StorageTerminalMenu menu,
            StorageCoreBlockEntity core
    ) {
        int changed = 0;
        int total = 0;
        int largest = 0;
        for (int slot = 0; slot < StorageTerminalMenu.DISPLAY_SLOTS; slot++) {
            ItemStack after = menu.getSlot(slot).getItem();
            if (before.get(slot).getCount() == after.getCount()
                    && ItemStack.isSameItemSameComponents(before.get(slot), after)) continue;
            changed++;
            var packet = new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                    menu.containerId, 0, slot, after.copy());
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(),
                    java.util.Objects.requireNonNull(core.getLevel()).registryAccess());
            net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
                    .STREAM_CODEC.encode(buffer, packet);
            int bytes = buffer.readableBytes();
            total += bytes;
            largest = Math.max(largest, bytes);
        }
        if (changed > StorageTerminalMenu.DISPLAY_SLOTS) {
            throw new IllegalStateException("Visible packet update was not bounded");
        }
        return new PacketMetrics(changed, total, largest);
    }

    private static PersistenceMetrics measurePersistenceRoundTrip(
            StorageCoreBlockEntity core
    ) throws ReflectiveOperationException {
        Object record = invokeNoArgs(core, "storageRecordForTesting");
        Method save = record.getClass().getDeclaredMethod(
                "save", net.minecraft.core.HolderLookup.Provider.class);
        save.setAccessible(true);
        long started = System.nanoTime();
        net.minecraft.nbt.CompoundTag encoded = (net.minecraft.nbt.CompoundTag) save.invoke(
                record,
                java.util.Objects.requireNonNull(core.getLevel()).registryAccess());
        long saveNanos = System.nanoTime() - started;

        net.minecraft.nbt.ListTag segments =
                encoded.getList("inventorySegments", net.minecraft.nbt.Tag.TAG_COMPOUND);
        int encodedTypes = 0;
        for (int index = 0; index < segments.size(); index++) {
            int size = segments.getCompound(index)
                    .getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
            if (size > 63) {
                throw new IllegalStateException("Persistence segment exceeded 63 types");
            }
            encodedTypes += size;
        }
        int expectedSegments = (STORED_TYPE_COUNT + 62) / 63;
        if (segments.size() != expectedSegments || encodedTypes != STORED_TYPE_COUNT) {
            throw new IllegalStateException(
                    "Persistence segmentation mismatch: segments=" + segments.size()
                            + " types=" + encodedTypes);
        }

        net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
        root.putInt("schemaVersion", 1);
        net.minecraft.nbt.ListTag storages = new net.minecraft.nbt.ListTag();
        storages.add(encoded.copy());
        root.put("storages", storages);
        root.put("recoveries", new net.minecraft.nbt.ListTag());
        Class<?> repositoryClass = Class.forName(
                "com.swear.autostorage.CoreStorageRepository");
        Method load = repositoryClass.getDeclaredMethod(
                "load",
                net.minecraft.nbt.CompoundTag.class,
                net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        started = System.nanoTime();
        Object loadedRepository = load.invoke(
                null,
                root,
                java.util.Objects.requireNonNull(core.getLevel()).registryAccess());
        long loadNanos = System.nanoTime() - started;
        Field recordsField = repositoryClass.getDeclaredField("records");
        recordsField.setAccessible(true);
        Map<?, ?> records = (Map<?, ?>) recordsField.get(loadedRepository);
        if (records.size() != 1) {
            throw new IllegalStateException(
                    "Round-trip repository did not contain exactly one record");
        }
        Object decodedRecord = records.values().iterator().next();
        Map<?, ?> original = ledgerSnapshot(record);
        Map<?, ?> decoded = ledgerSnapshot(decodedRecord);
        if (original.size() != STORED_TYPE_COUNT
                || !original.equals(decoded)) {
            throw new IllegalStateException(
                    "Persistence round-trip changed exact keys or amounts");
        }
        return new PersistenceMetrics(
                saveNanos, loadNanos, segments.size(), decoded.size());
    }

    private static Object invokeNoArgs(Object target, String methodName)
            throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Map<?, ?> ledgerSnapshot(Object record)
            throws ReflectiveOperationException {
        Object ledger = invokeNoArgs(record, "resourceLedger");
        return (Map<?, ?>) invokeNoArgs(ledger, "snapshot");
    }

    private static void clearCatalogCache() throws ReflectiveOperationException {
        Field field = catalogClass().getDeclaredField("CACHE");
        field.setAccessible(true);
        Object value = field.get(null);
        if (!(value instanceof Map<?, ?> cache)) {
            throw new IllegalStateException("Craftable catalog cache has unexpected type");
        }
        cache.clear();
        Field shared = CraftingTerminalMenu.class.getDeclaredField(
                "SHARED_CRAFTABLE_CACHE");
        shared.setAccessible(true);
        ((Map<?, ?>) shared.get(null)).clear();
    }

    private static void clearRecipeFamilyCaches() throws ReflectiveOperationException {
        Class<?> adaptersClass = Class.forName(
                "com.swear.autostorage.BuiltInRecipeAdapters");
        Method registryMethod = adaptersClass.getDeclaredMethod("registry");
        registryMethod.setAccessible(true);
        Object registry = registryMethod.invoke(null);
        Method adaptersMethod = registry.getClass().getDeclaredMethod("adapters");
        adaptersMethod.setAccessible(true);
        for (Object adapter : (List<?>) adaptersMethod.invoke(registry)) {
            Field outer = Arrays.stream(adapter.getClass().getDeclaredFields())
                    .filter(Field::isSynthetic)
                    .filter(field -> field.getType().getName().endsWith(".RecipeFamily"))
                    .findFirst()
                    .orElse(null);
            if (outer == null) continue;
            outer.setAccessible(true);
            Object family = outer.get(adapter);
            clearMapField(family, "typedPlanCache");
            clearMapField(family, "typedContractCache");
        }
    }

    private static void clearBuiltInCaches() throws ReflectiveOperationException {
        Class<?> adaptersClass = Class.forName(
                "com.swear.autostorage.BuiltInRecipeAdapters");
        for (String name : List.of("SMITHING_INPUT_CACHE", "COMPONENT_IDENTITY_CACHE")) {
            Field field = adaptersClass.getDeclaredField(name);
            field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).clear();
        }
    }

    private static void clearMapField(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        ((Map<?, ?>) field.get(target)).clear();
    }

    private static void prewarmCatalog(net.minecraft.world.level.Level level)
            throws ReflectiveOperationException {
        Method method = catalogClass().getDeclaredMethod(
                "prewarm", net.minecraft.world.level.Level.class);
        method.setAccessible(true);
        method.invoke(null, level);
    }

    private static void prewarmCraftable(
            net.minecraft.world.entity.player.Player player,
            StorageCoreBlockEntity core
    ) {
        CraftingTerminalMenu menu = new CraftingTerminalMenu(799, player.getInventory(), core);
        menu.broadcastChanges();
        if (!menu.clickMenuButton(player, CRAFTABLE_PAGE_BUTTON)) {
            throw new IllegalStateException("Could not prewarm shared Craftable results");
        }
        menu.removed(player);
    }

    private static Container machineContainer(StorageCoreBlockEntity core) {
        try {
            Method method = StorageCoreBlockEntity.class.getDeclaredMethod("getMachineContainer");
            method.setAccessible(true);
            return (Container) method.invoke(core);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access benchmark machine container", exception);
        }
    }

    private static Class<?> catalogClass() throws ClassNotFoundException {
        return Class.forName(
                "com.swear.autostorage.CraftableRecipeCatalog");
    }

    private static long usedHeapAfterFullGc() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static long percentile95(long[] sorted) {
        return sorted[Math.max(0, (int) Math.ceil(sorted.length * 0.95) - 1)];
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static void writeReport(
            int recipeCount,
            int storedTypes,
            int outputCount,
            long firstPrefetchNanos,
            long prefetchP95Nanos,
            long firstNanos,
            long switchP95Nanos,
            long warmP95Nanos,
            long indexRetainedBytes,
            long menusRetainedBytes,
            long perMenuRetainedBytes,
            StorageMetrics storage,
            PersistenceMetrics persistence,
            long terminalPreparationNanos,
            long craftablePreparationNanos
    ) throws IOException {
        StringBuilder interactionJson = new StringBuilder();
        int interactionIndex = 0;
        for (Map.Entry<String, Long> entry : storage.interactionNanos().entrySet()) {
            if (interactionIndex++ > 0) interactionJson.append(",\n");
            interactionJson.append(String.format(
                    Locale.ROOT,
                    "    %s: %.3f",
                    jsonString(entry.getKey()),
                    millis(entry.getValue())));
        }
        String json = String.format(Locale.ROOT, """
                {
                  "scale_types": %d,
                  "recipes": %d,
                  "stored_types": %d,
                  "craftable_outputs": %d,
                  "repository_load_ms": %.3f,
                  "repository_save_ms": %.3f,
                  "terminal_index_prepare_ms": %.3f,
                  "craftable_prepare_ms": %.3f,
                  "persistence_segments": %d,
                  "round_trip_exact_types": %d,
                  "storage_first_open_ms": %.3f,
                  "storage_warm_open_ms": %.3f,
                  "storage_interaction_p95_ms": %.3f,
                  "storage_interactions_ms": {
                %s
                  },
                  "first_prefetch_ms": %.3f,
                  "prefetch_p95_ms": %.3f,
                  "first_switch_ms": %.3f,
                  "prefetched_switch_p95_ms": %.3f,
                  "warm_switch_p95_ms": %.3f,
                  "shared_index_retained_bytes": %d,
                  "retained_menu_count": %d,
                  "menus_retained_bytes": %d,
                  "per_menu_retained_bytes": %d,
                  "initial_content_slots": %d,
                  "initial_content_packet_bytes": %d,
                  "far_page_changed_slots": %d,
                  "far_page_slot_packets_total_bytes": %d,
                  "largest_slot_packet_bytes": %d
                }
                """,
                STORED_TYPE_COUNT,
                recipeCount,
                storedTypes,
                outputCount,
                millis(persistence.loadNanos()),
                millis(persistence.saveNanos()),
                millis(terminalPreparationNanos),
                millis(craftablePreparationNanos),
                persistence.segments(),
                persistence.exactTypes(),
                millis(storage.firstOpenNanos()),
                millis(storage.warmOpenNanos()),
                millis(storage.p95Nanos()),
                interactionJson,
                millis(firstPrefetchNanos),
                millis(prefetchP95Nanos),
                millis(firstNanos),
                millis(switchP95Nanos),
                millis(warmP95Nanos),
                indexRetainedBytes,
                RETAINED_MENU_COUNT,
                menusRetainedBytes,
                perMenuRetainedBytes,
                storage.initialSlots(),
                storage.initialPacketBytes(),
                storage.farChangedSlots(),
                storage.farPacketBytes(),
                storage.largestSlotPacketBytes());
        Path reports = Path.of("..", "build", "reports").normalize();
        Files.createDirectories(reports);
        Files.writeString(
                reports.resolve("terminal-scale-" + STORED_TYPE_COUNT + ".json"), json);
        Files.writeString(reports.resolve("craftable-benchmark.json"), json);
    }

    private static String jsonString(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private record SwitchTimes(
            long prefetchNanos,
            long switchNanos,
            long warmNanos,
            int outputCount
    ) {
    }

    private record StorageMetrics(
            long firstOpenNanos,
            long warmOpenNanos,
            Map<String, Long> interactionNanos,
            long p95Nanos,
            int initialPacketBytes,
            int initialSlots,
            int farChangedSlots,
            int farPacketBytes,
            int largestSlotPacketBytes
    ) {
    }

    private record PacketMetrics(int changedSlots, int totalBytes, int largestBytes) {
    }

    private record PersistenceMetrics(
            long saveNanos,
            long loadNanos,
            int segments,
            int exactTypes
    ) {
    }
}
