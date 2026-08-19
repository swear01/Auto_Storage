package com.swear.autostorage.fixture.fluxnetworks;

import com.swear.autostorage.Action;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingDestination;
import com.swear.autostorage.CraftingTerminalMenu;
import com.swear.autostorage.IsolatedRecipeInventoryEvidence;
import com.swear.autostorage.ItemKey;
import com.swear.autostorage.MachineEnergyTable;
import com.swear.autostorage.StorageCoreBlockEntity;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.SyntheticRecipeCatalogs;
import com.swear.autostorage.WorldStations;
import com.swear.autostorage.compat.fluxnetworks.FluxnetworksCompatModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import sonar.fluxnetworks.FluxConfig;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

@GameTestHolder(FluxnetworksFixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class FluxnetworksIntegrationGameTests {
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(
            AutoStorage.MODID, "flux_station/redstone_to_flux_dust");
    private static final int STATIONS_PAGE_BUTTON = 29;
    private static final int STORAGE_PAGE_BUTTON = 14;

    private FluxnetworksIntegrationGameTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void flux_station_module_registers(GameTestHelper helper) {
        IsolatedRecipeInventoryEvidence.assertMatchesDescriptor(
                helper.getLevel().getRecipeManager(),
                FluxnetworksIntegrationGameTests.class);
        if (!ModList.get().isLoaded("fluxnetworks")) {
            helper.fail("Flux Networks mod is not loaded");
            return;
        }
        if (!AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(FluxnetworksCompatModule.FLUX_STATION_ID)
                || BuiltInRegistries.BLOCK.get(FluxnetworksCompatModule.FLUX_STATION_ID) == Blocks.AIR
                || BuiltInRegistries.ITEM.get(FluxnetworksCompatModule.FLUX_STATION_ID) == Items.AIR) {
            helper.fail("Flux Station block, item, or descriptor was not registered");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void station_drops_its_registered_item(GameTestHelper helper) {
        BlockPos station = helper.absolutePos(new BlockPos(8, 3, 2));
        Block stationBlock = BuiltInRegistries.BLOCK.get(FluxnetworksCompatModule.FLUX_STATION_ID);
        helper.getLevel().setBlock(station, stationBlock.defaultBlockState(), Block.UPDATE_ALL);
        boolean dropped = Block.getDrops(
                        helper.getLevel().getBlockState(station),
                        helper.getLevel(),
                        station,
                        null)
                .stream()
                .anyMatch(stack -> stack.is(
                        BuiltInRegistries.ITEM.get(FluxnetworksCompatModule.FLUX_STATION_ID)));
        if (!dropped) {
            helper.fail("Flux Station did not drop its registered item");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void redstone_conversion_is_exposed(GameTestHelper helper) {
        var holder = SyntheticRecipeCatalogs.recipes(helper.getLevel()).stream()
                .filter(candidate -> candidate.id().equals(RECIPE_ID))
                .findFirst()
                .orElse(null);
        if (holder == null || !CraftingTerminalMenu.supportsRecipeHolder(holder)) {
            helper.fail("Flux redstone conversion was not exposed");
            return;
        }
        helper.succeed();
    }

    @GameTest(
            template = "craftingtests.platform",
            batch = "flux_config")
    public static void disabled_flux_recipe_fails_closed(GameTestHelper helper) {
        boolean previous = FluxConfig.enableFluxRecipe;
        FluxConfig.enableFluxRecipe = false;
        try {
            BlockPos station = helper.absolutePos(new BlockPos(8, 3, 2));
            helper.getLevel().setBlock(
                    station.below(2),
                    Blocks.BEDROCK.defaultBlockState(),
                    Block.UPDATE_ALL);
            placeStation(helper, station);
            if (WorldStations.isPresent(
                    helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID)) {
                helper.fail("Disabled Flux station remained available");
                return;
            }
            if (SyntheticRecipeCatalogs.byId(helper.getLevel(), RECIPE_ID) != null) {
                helper.fail("Disabled Flux recipe remained exposed");
                return;
            }
        } finally {
            FluxConfig.enableFluxRecipe = previous;
        }
        helper.succeed();
    }

    @GameTest(
            template = "craftingtests.platform",
            batch = "flux_config")
    public static void disabled_flux_terminal_craft_fails_closed(GameTestHelper helper) {
        boolean previous = FluxConfig.enableFluxRecipe;
        FluxConfig.enableFluxRecipe = false;
        withCore(helper, (level, core, player) -> {
            try {
                BlockPos station = helper.absolutePos(new BlockPos(10, 3, 2));
                level.setBlock(station.below(2), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
                placeStation(helper, station);
                seedItem(core, Items.REDSTONE, 1);
                Item fluxDust = BuiltInRegistries.ITEM.get(FluxnetworksCompatModule.FLUX_DUST_ID);
                var menu = new CraftingTerminalMenu(705, player.getInventory(), core);
                menu.lookUpRecipes(level, new ItemStack(fluxDust));
                boolean committed = menu.handleRecipeRequest(
                        level, RECIPE_ID, 1, CraftingDestination.STORAGE, player);
                if (committed
                        || core.getItemCount(ItemKey.of(new ItemStack(Items.REDSTONE))) != 1
                        || core.getItemCount(ItemKey.of(new ItemStack(fluxDust))) != 0) {
                    helper.fail("Disabled Flux terminal craft did not fail closed");
                    return;
                }
                helper.succeed();
            } finally {
                FluxConfig.enableFluxRecipe = previous;
            }
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void wrong_base_fails_closed(GameTestHelper helper) {
        BlockPos station = helper.absolutePos(new BlockPos(2, 3, 2));
        helper.getLevel().setBlock(station.below(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeStation(helper, station);
        if (WorldStations.isPresentAt(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID, station)) {
            helper.fail("Flux Station accepted a non-bedrock/non-flux base");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void air_base_fails_closed(GameTestHelper helper) {
        BlockPos station = helper.absolutePos(new BlockPos(11, 3, 2));
        helper.getLevel().setBlock(station.below(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        placeStation(helper, station);
        if (WorldStations.isPresentAt(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID, station)) {
            helper.fail("Flux Station accepted air as its base");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void bedrock_base_is_available(GameTestHelper helper) {
        BlockPos station = helper.absolutePos(new BlockPos(3, 3, 2));
        helper.getLevel().setBlock(station.below(2), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
        placeStation(helper, station);
        if (!WorldStations.isPresentAt(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID, station)) {
            helper.fail("Flux Station did not accept a bedrock base");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void flux_block_base_is_available(GameTestHelper helper) {
        Block fluxBlock = BuiltInRegistries.BLOCK.get(FluxnetworksCompatModule.FLUX_BLOCK_ID);
        if (fluxBlock == Blocks.AIR) {
            helper.fail("Flux Networks flux_block is missing");
            return;
        }
        BlockPos station = helper.absolutePos(new BlockPos(4, 3, 2));
        helper.getLevel().setBlock(station.below(2), fluxBlock.defaultBlockState(), Block.UPDATE_ALL);
        placeStation(helper, station);
        if (!WorldStations.isPresentAt(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID, station)) {
            helper.fail("Flux Station did not accept a flux_block base");
            return;
        }
        helper.succeed();
    }

    @GameTest(
            template = "craftingtests.platform",
            batch = "flux_station_cache")
    public static void chunk_unload_fails_closed_until_reload(GameTestHelper helper) {
        BlockPos station = helper.absolutePos(new BlockPos(9, 3, 2));
        helper.getLevel().setBlock(station.below(2), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
        placeStation(helper, station);
        if (!WorldStations.isPresent(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID)) {
            helper.fail("Valid Flux Station was not cached before chunk unload");
            return;
        }
        try {
            invokeWorldStationLifecycle(
                    "onChunkUnload", helper.getLevel(), new ChunkPos(station));
        } catch (ReflectiveOperationException exception) {
            helper.fail("WorldStations has no chunk-unload invalidation: " + exception);
            return;
        }
        if (WorldStations.isPresent(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID)) {
            helper.fail("Flux Station remained available after chunk unload");
            return;
        }
        try {
            invokeWorldStationLifecycle(
                    "onChunkLoad", helper.getLevel(), new ChunkPos(station));
        } catch (ReflectiveOperationException exception) {
            helper.fail("WorldStations chunk-load refresh failed: " + exception);
            return;
        }
        if (!WorldStations.isPresent(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID)) {
            helper.fail("Flux Station did not become available after chunk reload");
            return;
        }
        helper.succeed();
    }

    @GameTest(
            template = "craftingtests.platform",
            batch = "flux_station_cache")
    public static void base_change_updates_cached_availability(GameTestHelper helper) {
        BlockPos station = helper.absolutePos(new BlockPos(8, 3, 2));
        helper.getLevel().setBlock(station.below(2), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
        placeStation(helper, station);
        if (!WorldStations.isPresent(helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID)) {
            helper.fail("Valid Flux Station was not cached as available");
            return;
        }
        long validRevision = WorldStations.revision(helper.getLevel());
        helper.getLevel().setBlock(station.below(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        if (WorldStations.isPresent(helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID)
                || WorldStations.revision(helper.getLevel()) == validRevision) {
            helper.fail("Flux Station cache did not invalidate after its base was removed");
            return;
        }
        helper.getLevel().setBlock(station.below(2), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
        if (!WorldStations.isPresent(helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID)) {
            helper.fail("Flux Station did not become available after its base was restored");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "craftingtests.platform")
    public static void conversion_is_one_to_one(GameTestHelper helper) {
        withCore(helper, (level, core, player) -> {
            BlockPos station = helper.absolutePos(new BlockPos(5, 3, 2));
            level.setBlock(station.below(2), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
            placeStation(helper, station);
            ItemEntity groundRedstone = new ItemEntity(
                    level,
                    station.getX() + 0.5,
                    station.getY() - 0.5,
                    station.getZ() + 0.5,
                    new ItemStack(Items.REDSTONE, 2));
            level.addFreshEntity(groundRedstone);
            seedItem(core, Items.REDSTONE, 3);
            var menu = new CraftingTerminalMenu(701, player.getInventory(), core);
            Item fluxDust = BuiltInRegistries.ITEM.get(FluxnetworksCompatModule.FLUX_DUST_ID);
            menu.lookUpRecipes(level, new ItemStack(fluxDust));
            boolean committed = menu.handleRecipeRequest(
                    level, RECIPE_ID, 3, CraftingDestination.STORAGE, player);
            long input = core.getItemCount(ItemKey.of(new ItemStack(Items.REDSTONE)));
            long output = core.getItemCount(ItemKey.of(new ItemStack(fluxDust)));
            if (!committed || input != 0 || output != 3
                    || !groundRedstone.isAlive()
                    || groundRedstone.getItem().getCount() != 2
                    || !level.getBlockState(station).is(
                            BuiltInRegistries.BLOCK.get(FluxnetworksCompatModule.FLUX_STATION_ID))
                    || !level.getBlockState(station.below(2)).is(Blocks.BEDROCK)) {
                helper.fail("Flux conversion was not one-to-one: committed=" + committed
                        + " input=" + input + " output=" + output
                        + " recipes=" + menu.getRecipeCount()
                        + " craftable=" + menu.getCraftableCount());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void conversion_batch_is_exact(GameTestHelper helper) {
        withCore(helper, (level, core, player) -> {
            BlockPos station = helper.absolutePos(new BlockPos(6, 3, 2));
            level.setBlock(station.below(2), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
            placeStation(helper, station);
            seedItem(core, Items.REDSTONE, 64);
            var menu = new CraftingTerminalMenu(702, player.getInventory(), core);
            Item fluxDust = BuiltInRegistries.ITEM.get(FluxnetworksCompatModule.FLUX_DUST_ID);
            menu.lookUpRecipes(level, new ItemStack(fluxDust));
            boolean committed = menu.handleRecipeRequest(
                    level, RECIPE_ID, 64, CraftingDestination.STORAGE, player);
            long input = core.getItemCount(ItemKey.of(new ItemStack(Items.REDSTONE)));
            long output = core.getItemCount(ItemKey.of(new ItemStack(fluxDust)));
            if (!committed || input != 0 || output != 64) {
                helper.fail("Flux conversion batch changed the wrong amount: committed="
                        + committed + " input=" + input + " output=" + output
                        + " recipes=" + menu.getRecipeCount()
                        + " craftable=" + menu.getCraftableCount());
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void installed_station_is_second_availability_source(GameTestHelper helper) {
        withCore(helper, (level, core, player) -> {
            Item stationItem = BuiltInRegistries.ITEM.get(FluxnetworksCompatModule.FLUX_STATION_ID);
            int slot = MachineEnergyTable.findSlot(FluxnetworksCompatModule.FLUX_STATION_ID);
            if (stationItem == Items.AIR || slot < 0) {
                helper.fail("Flux Station descriptor slot is missing");
                return;
            }
            installStation(core, player, stationItem);
            seedItem(core, Items.REDSTONE, 1);
            var menu = new CraftingTerminalMenu(703, player.getInventory(), core);
            Item fluxDust = BuiltInRegistries.ITEM.get(FluxnetworksCompatModule.FLUX_DUST_ID);
            menu.lookUpRecipes(level, new ItemStack(fluxDust));
            boolean committed = menu.handleRecipeRequest(
                    level, RECIPE_ID, 1, CraftingDestination.STORAGE, player);
            if (!committed || core.getItemCount(ItemKey.of(new ItemStack(fluxDust))) != 1) {
                helper.fail("Installed Flux Station did not provide availability");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(
            template = "craftingtests.platform",
            batch = "flux_station_persistence")
    public static void station_registry_persists_and_break_removes(GameTestHelper helper) {
        BlockPos station = helper.absolutePos(new BlockPos(7, 3, 2));
        helper.getLevel().setBlock(station.below(2), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
        placeStation(helper, station);
        WorldStations.load(helper.getLevel());
        if (!WorldStations.isPresentAt(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID, station)) {
            helper.fail("Flux Station registry did not reload its saved position");
            return;
        }
        helper.getLevel().setBlock(station, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (WorldStations.isPresentAt(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID, station)) {
            helper.fail("Broken Flux Station remained available");
            return;
        }
        helper.succeed();
    }

    @GameTest(
            template = "craftingtests.platform",
            batch = "flux_station_persistence")
    public static void stale_persisted_station_fails_closed_after_reload(GameTestHelper helper) {
        BlockPos station = helper.absolutePos(new BlockPos(8, 3, 2));
        helper.getLevel().setBlock(station.below(2), Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
        placeStation(helper, station);
        if (!WorldStations.isPresentAt(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID, station)) {
            helper.fail("Flux Station was not available before stale-state setup");
            return;
        }

        helper.getLevel().setBlock(station, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        try {
            addStaleSavedPosition(
                    helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID, station);
        } catch (ReflectiveOperationException exception) {
            helper.fail("Could not create stale saved station state: " + exception);
            return;
        }
        WorldStations.load(helper.getLevel());
        if (WorldStations.isPresentAt(
                helper.getLevel(), FluxnetworksCompatModule.FLUX_STATION_ID, station)) {
            helper.fail("Stale persisted Flux Station remained available after reload");
            return;
        }
        helper.succeed();
    }

    private static void invokeWorldStationLifecycle(
            String methodName,
            ServerLevel level,
            ChunkPos chunkPos
    ) throws ReflectiveOperationException {
        Method method = WorldStations.class.getDeclaredMethod(
                methodName, ServerLevel.class, ChunkPos.class);
        method.setAccessible(true);
        method.invoke(null, level, chunkPos);
    }

    private static void addStaleSavedPosition(
            ServerLevel level,
            ResourceLocation blockId,
            BlockPos pos
    ) throws ReflectiveOperationException {
        Method dataMethod = WorldStations.class.getDeclaredMethod("data", ServerLevel.class);
        dataMethod.setAccessible(true);
        SavedData saved = (SavedData) dataMethod.invoke(null, level);
        Field positionsField = saved.getClass().getDeclaredField("positions");
        Field validPositionsField = saved.getClass().getDeclaredField("validPositions");
        positionsField.setAccessible(true);
        validPositionsField.setAccessible(true);
        addSavedPosition((Map<?, ?>) positionsField.get(saved), level, blockId, pos);
        addSavedPosition((Map<?, ?>) validPositionsField.get(saved), level, blockId, pos);
        saved.setDirty();
    }

    @SuppressWarnings("unchecked")
    private static void addSavedPosition(
            Map<?, ?> dimensions,
            ServerLevel level,
            ResourceLocation blockId,
            BlockPos pos
    ) {
        Map<Object, Map<Object, Set<BlockPos>>> mutableDimensions =
                (Map<Object, Map<Object, Set<BlockPos>>>) dimensions;
        Map<Object, Set<BlockPos>> byBlock = mutableDimensions.computeIfAbsent(
                level.dimension(), ignored -> new java.util.concurrent.ConcurrentHashMap<>());
        byBlock.computeIfAbsent(blockId, ignored -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(pos.immutable());
    }

    private static void placeStation(GameTestHelper helper, BlockPos pos) {
        Block station = BuiltInRegistries.BLOCK.get(FluxnetworksCompatModule.FLUX_STATION_ID);
        helper.getLevel().setBlock(pos, station.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void withCore(GameTestHelper helper, FixtureAssertion assertion) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, AutoStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), AutoStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Core not found");
                return;
            }
            core.rebuildNetwork(level);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() + 0.5);
            assertion.run(level, core, player);
        });
    }

    private static void installStation(
            StorageCoreBlockEntity core,
            Player player,
            Item stationItem
    ) {
        ItemStack station = new ItemStack(stationItem);
        var menu = new CraftingTerminalMenu(704, player.getInventory(), core);
        menu.clickMenuButton(player, STATIONS_PAGE_BUTTON);
        for (int index = CraftingTerminalMenu.MACHINE_SLOT_START;
             index < CraftingTerminalMenu.MACHINE_SLOT_START
                     + CraftingTerminalMenu.MACHINE_SLOT_COUNT;
             index++) {
            var slot = menu.getSlot(index);
            if (!slot.isActive() || !slot.mayPlace(station)) continue;
            slot.set(station.copy());
            slot.setChanged();
            menu.clickMenuButton(player, STORAGE_PAGE_BUTTON);
            return;
        }
        throw new IllegalStateException("Could not install Flux Station");
    }

    private static void seedItem(StorageCoreBlockEntity core, Item item, int count) {
        if (core.insertResource(
                StorageResourceKey.item(new ItemStack(item), core.getLevel().registryAccess()),
                count,
                Action.EXECUTE) != count) {
            throw new IllegalStateException("Could not seed " + item);
        }
    }

    @FunctionalInterface
    private interface FixtureAssertion {
        void run(net.minecraft.server.level.ServerLevel level, StorageCoreBlockEntity core, Player player);
    }
}
