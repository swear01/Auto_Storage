package com.swearprom.magicstorage.fixture.compatibilitymatrix;

import com.swearprom.magicstorage.magic_storage.CraftingTerminalMenu;
import com.swearprom.magicstorage.magic_storage.MachineDescriptor;
import com.swearprom.magicstorage.magic_storage.MachineEnergyTable;
import com.swearprom.magicstorage.magic_storage.MagicStorage;
import com.swearprom.magicstorage.magic_storage.StorageCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
    private static final long MAX_PREFETCH_NANOS = 100_000_000L;
    private static final long MAX_SWITCH_NANOS = 50_000_000L;
    private static final long MAX_INDEX_RETAINED_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_MENU_RETAINED_BYTES = 128L * 1024L;
    private static final int WARMUP_COUNT = 5;
    private static final int SAMPLE_COUNT = 20;
    private static final int RETAINED_MENU_COUNT = 16;
    private static final int STORED_TYPE_COUNT = 512;
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

    @GameTest(template = "craftingtests.platform", timeoutTicks = 200)
    public static void full_support_pack_switches_craftable_under_fifty_milliseconds(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(
                corePos,
                MagicStorage.STORAGE_CORE.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(
                corePos.east(),
                MagicStorage.CREATIVE_STORAGE_UNIT.get().defaultBlockState(),
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
            try {
                seedScenario(core);
                SwitchTimes first = measureSwitch(player, core, 700);
                for (int index = 0; index < WARMUP_COUNT; index++) {
                    measureSwitch(player, core, 701 + index);
                }
                long[] prefetchNanos = new long[SAMPLE_COUNT];
                long[] switchNanos = new long[SAMPLE_COUNT];
                long[] warmNanos = new long[SAMPLE_COUNT];
                int outputCount = first.outputCount();
                if (outputCount <= 0) {
                    throw new IllegalStateException(
                            "Craftable benchmark produced no outputs");
                }
                for (int index = 0; index < SAMPLE_COUNT; index++) {
                    SwitchTimes measured =
                            measureSwitch(player, core, 701 + WARMUP_COUNT + index);
                    prefetchNanos[index] = measured.prefetchNanos();
                    switchNanos[index] = measured.switchNanos();
                    warmNanos[index] = measured.warmNanos();
                    if (measured.outputCount() != outputCount) {
                        throw new IllegalStateException(
                                "Craftable benchmark output count changed from "
                                        + outputCount + " to " + measured.outputCount());
                    }
                }

                clearCatalogCache();
                clearBuiltInCaches();
                clearRecipeFamilyCaches();
                long heapWithoutIndex = usedHeapAfterFullGc();
                prewarmCatalog(level);
                prewarmCraftable(player, core);
                long heapWithIndex = usedHeapAfterFullGc();
                long indexRetainedBytes = Math.max(0L, heapWithIndex - heapWithoutIndex);
                long heapBeforeMenus = usedHeapAfterFullGc();
                List<CraftingTerminalMenu> retainedMenus =
                        new ArrayList<>(RETAINED_MENU_COUNT);
                for (int index = 0; index < RETAINED_MENU_COUNT; index++) {
                    CraftingTerminalMenu menu =
                            new CraftingTerminalMenu(800 + index, player.getInventory(), core);
                    menu.broadcastChanges();
                    if (!menu.clickMenuButton(
                            player, CRAFTABLE_PAGE_BUTTON)) {
                        throw new IllegalStateException(
                                "Craftable benchmark could not retain menu " + index);
                    }
                    retainedMenus.add(menu);
                }
                long heapWithMenus = usedHeapAfterFullGc();
                long menusRetainedBytes = Math.max(0L, heapWithMenus - heapBeforeMenus);
                long perMenuRetainedBytes = menusRetainedBytes / RETAINED_MENU_COUNT;
                retainedMenus.forEach(menu -> menu.removed(player));

                Arrays.sort(prefetchNanos);
                Arrays.sort(switchNanos);
                Arrays.sort(warmNanos);
                long prefetchP95Nanos = percentile95(prefetchNanos);
                long switchP95Nanos = percentile95(switchNanos);
                long warmP95Nanos = percentile95(warmNanos);
                writeReport(
                        level.getRecipeManager().getRecipes().size(),
                        core.getTypeCount(),
                        outputCount,
                        first.prefetchNanos(),
                        prefetchP95Nanos,
                        first.switchNanos(),
                        switchP95Nanos,
                        warmP95Nanos,
                        indexRetainedBytes,
                        menusRetainedBytes,
                        perMenuRetainedBytes);
                MagicStorage.LOGGER.info(
                        "CRAFTABLE_BENCHMARK recipes={} storedTypes={} outputs={} "
                                + "firstPrefetchMs={} prefetchP95Ms={} "
                                + "firstSwitchMs={} switchP95Ms={} warmSwitchP95Ms={} "
                                + "indexRetainedBytes={} menusRetainedBytes={} perMenuBytes={}",
                        level.getRecipeManager().getRecipes().size(),
                        core.getTypeCount(),
                        outputCount,
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
                    helper.fail("Craftable prefetch/switch budget exceeded: prefetch p95="
                            + millis(prefetchP95Nanos) + " ms, first switch="
                            + millis(first.switchNanos()) + " ms, switch p95="
                            + millis(switchP95Nanos) + " ms, warm switch p95="
                            + millis(warmP95Nanos) + " ms");
                    return;
                }
                if (indexRetainedBytes >= MAX_INDEX_RETAINED_BYTES) {
                    helper.fail("Craftable shared index retained "
                            + indexRetainedBytes + " bytes; limit is "
                            + MAX_INDEX_RETAINED_BYTES);
                    return;
                }
                if (perMenuRetainedBytes >= MAX_MENU_RETAINED_BYTES) {
                    helper.fail("Craftable menu retained " + perMenuRetainedBytes
                            + " bytes; limit is " + MAX_MENU_RETAINED_BYTES);
                    return;
                }
                helper.succeed();
            } catch (IOException | ReflectiveOperationException exception) {
                helper.fail("Craftable benchmark infrastructure failed: " + exception);
            }
        });
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

    private static void seedScenario(StorageCoreBlockEntity core) {
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
        for (Item item : BuiltInRegistries.ITEM) {
            if (core.getTypeCount() >= STORED_TYPE_COUNT) break;
            ItemStack stack = new ItemStack(item, 64);
            if (!stack.isEmpty()) core.insertItem(stack);
        }
        if (core.getTypeCount() < STORED_TYPE_COUNT) {
            throw new IllegalStateException(
                    "Craftable benchmark seeded only " + core.getTypeCount()
                            + " stored types");
        }
        Container machines = machineContainer(core);
        for (int slot = 0; slot < MachineEnergyTable.entries().size(); slot++) {
            MachineDescriptor descriptor = MachineEnergyTable.get(slot);
            if (descriptor.category() == MachineEnergyTable.Category.TRANSFORM) continue;
            machines.setItem(slot, descriptor.representativeStack());
        }
    }

    private static void clearCatalogCache() throws ReflectiveOperationException {
        Field field = catalogClass().getDeclaredField("CACHE");
        field.setAccessible(true);
        Object value = field.get(null);
        if (!(value instanceof Map<?, ?> cache)) {
            throw new IllegalStateException("Craftable catalog cache has unexpected type");
        }
        cache.clear();
    }

    private static void clearRecipeFamilyCaches() throws ReflectiveOperationException {
        Class<?> adaptersClass = Class.forName(
                "com.swearprom.magicstorage.magic_storage.BuiltInRecipeAdapters");
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
                "com.swearprom.magicstorage.magic_storage.BuiltInRecipeAdapters");
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
                "com.swearprom.magicstorage.magic_storage.CraftableRecipeCatalog");
    }

    private static long usedHeapAfterFullGc() {
        for (int attempt = 0; attempt < 3; attempt++) {
            System.gc();
        }
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
            long perMenuRetainedBytes
    ) throws IOException {
        Path report = Path.of("..", "build", "reports", "craftable-benchmark.json")
                .normalize();
        Files.createDirectories(report.getParent());
        Files.writeString(report, String.format(Locale.ROOT, """
                {
                  "recipes": %d,
                  "stored_types": %d,
                  "craftable_outputs": %d,
                  "first_prefetch_ms": %.3f,
                  "prefetch_p95_ms": %.3f,
                  "first_switch_ms": %.3f,
                  "prefetched_switch_p95_ms": %.3f,
                  "warm_switch_p95_ms": %.3f,
                  "shared_index_retained_bytes": %d,
                  "retained_menu_count": %d,
                  "menus_retained_bytes": %d,
                  "per_menu_retained_bytes": %d
                }
                """,
                recipeCount,
                storedTypes,
                outputCount,
                millis(firstPrefetchNanos),
                millis(prefetchP95Nanos),
                millis(firstNanos),
                millis(switchP95Nanos),
                millis(warmP95Nanos),
                indexRetainedBytes,
                RETAINED_MENU_COUNT,
                menusRetainedBytes,
                perMenuRetainedBytes));
    }

    private record SwitchTimes(
            long prefetchNanos,
            long switchNanos,
            long warmNanos,
            int outputCount
    ) {
    }
}
