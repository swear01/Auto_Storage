package com.swearprom.magicstorage.fixture.recipe;

import com.swearprom.magicstorage.magic_storage.Action;
import com.swearprom.magicstorage.magic_storage.BusConfiguration;
import com.swearprom.magicstorage.magic_storage.BusFilterMode;
import com.swearprom.magicstorage.magic_storage.BusFilterRule;
import com.swearprom.magicstorage.magic_storage.BusMode;
import com.swearprom.magicstorage.magic_storage.ExportBusBlock;
import com.swearprom.magicstorage.magic_storage.ExportBusBlockEntity;
import com.swearprom.magicstorage.magic_storage.ImportBusBlock;
import com.swearprom.magicstorage.magic_storage.ImportBusBlockEntity;
import com.swearprom.magicstorage.magic_storage.MagicStorage;
import com.swearprom.magicstorage.magic_storage.StorageCoreBlockEntity;
import com.swearprom.magicstorage.magic_storage.StorageResourceCapabilities;
import com.swearprom.magicstorage.magic_storage.StorageResourceHandler;
import com.swearprom.magicstorage.magic_storage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.Optional;

@GameTestHolder(FixtureMod.MODID)
@PrefixGameTestTemplate(false)
public final class BusTypedRollbackIntegrationTests {
    private static final long TRANSFER_BATCH = 1_000;

    private BusTypedRollbackIntegrationTests() {
    }

    @GameTest(template = "craftingtests.platform")
    public static void import_overdraw_is_persisted_in_escrow_and_drained_before_next_source_extract(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(2, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos sourcePos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(sourcePos, Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity importer)) {
                helper.fail("Adversarial typed Import fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            importer.setBusConfiguration(directionalConfiguration());
            StorageResourceKey mana = mana("import_overdraw");
            FixtureManaBlockStrategy.set(level, sourcePos, mana, 2_000);
            var source = FixtureManaBlockStrategy.handler(level, sourcePos);
            StorageResourceHandler cached = level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP);
            if (cached == null) {
                helper.fail("Could not cache the typed Import capability before escrow was created");
                return;
            }

            importer.tick();
            long pending = importer.getPendingResourceAmount(mana);
            if (source.getAmount(mana) != 500
                    || core.getResourceAmount(mana) != TRANSFER_BATCH
                    || pending != 500
                    || source.getAmount(mana) + core.getResourceAmount(mana) + pending != 2_000) {
                helper.fail("Import overdraw was not conserved in Bus escrow: source="
                        + source.getAmount(mana) + ", core=" + core.getResourceAmount(mana)
                        + ", pending=" + pending);
                return;
            }
            if (cached.insert(mana, 1, false) != 0
                    || core.getResourceAmount(mana) != TRANSFER_BATCH
                    || importer.getPendingResourceAmount(mana) != 500
                    || level.getCapability(
                    StorageResourceCapabilities.BLOCK, busPos, Direction.UP) != null) {
                helper.fail("Pending Import escrow did not suspend cached and fresh passive capabilities");
                return;
            }

            ItemStack dropped = Block.getDrops(
                            level.getBlockState(busPos), level, busPos, importer).stream()
                    .filter(stack -> stack.is(MagicStorage.IMPORT_BUS_ITEM.get()))
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
            BlockEntity droppedBus = dropped.has(DataComponents.BLOCK_ENTITY_DATA)
                    ? BlockEntity.loadStatic(
                    busPos,
                    level.getBlockState(busPos),
                    dropped.get(DataComponents.BLOCK_ENTITY_DATA).copyTag(),
                    level.registryAccess())
                    : null;
            if (!(droppedBus instanceof ImportBusBlockEntity droppedImporter)
                    || droppedImporter.getPendingResourceAmount(mana) != 500) {
                helper.fail("Import Bus drop did not preserve typed escrow");
                return;
            }

            CompoundTag saved = importer.saveWithFullMetadata(level.registryAccess());
            level.removeBlockEntity(busPos);
            BlockEntity loaded = BlockEntity.loadStatic(
                    busPos, level.getBlockState(busPos), saved, level.registryAccess());
            if (!(loaded instanceof ImportBusBlockEntity reloaded)) {
                helper.fail("Persisted typed Import Bus escrow could not be reloaded");
                return;
            }
            level.setBlockEntity(reloaded);
            core.rebuildNetwork(level);
            if (reloaded.getPendingResourceAmount(mana) != 500) {
                helper.fail("Typed Import Bus escrow did not survive save/load");
                return;
            }

            reloaded.setBusConfiguration(pausedConfiguration());
            reloaded.tick();
            if (source.getAmount(mana) != 500
                    || core.getResourceAmount(mana) != 1_500
                    || reloaded.getPendingResourceAmount(mana) != 0) {
                helper.fail("Import Bus did not drain escrow before another source extraction: source="
                        + source.getAmount(mana) + ", core=" + core.getResourceAmount(mana)
                        + ", pending=" + reloaded.getPendingResourceAmount(mana));
                return;
            }

            StorageResourceKey staleEndpoint = mana("import_replaced_during_execute");
            level.setBlock(sourcePos, Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            FixtureManaBlockStrategy.useIsolatedHandler(
                    level, sourcePos, staleEndpoint, TRANSFER_BATCH);
            reloaded.setBusConfiguration(allowOnly(staleEndpoint, 3));
            FixtureManaBlockStrategy.runOnNextSourceExtract(
                    level,
                    sourcePos,
                    () -> FixtureManaBlockStrategy.replaceHandler(level, sourcePos));
            reloaded.tick();
            if (FixtureManaBlockStrategy.handler(level, sourcePos).getAmount(staleEndpoint) != 0
                    || core.getResourceAmount(staleEndpoint) != 0
                    || reloaded.getPendingResourceAmount(staleEndpoint) != TRANSFER_BATCH) {
                helper.fail("Import restored a stale execute result into a detached endpoint: live="
                        + FixtureManaBlockStrategy.handler(level, sourcePos).getAmount(staleEndpoint)
                        + ", core=" + core.getResourceAmount(staleEndpoint)
                        + ", pending=" + reloaded.getPendingResourceAmount(staleEndpoint));
                return;
            }
            reloaded.setBusConfiguration(pausedConfiguration(4));
            reloaded.tick();
            if (reloaded.getPendingResourceAmount(staleEndpoint) != 0
                    || core.extractResource(
                    staleEndpoint, TRANSFER_BATCH, Action.EXECUTE) != TRANSFER_BATCH) {
                helper.fail("Import detached-endpoint escrow could not be recovered before continuing");
                return;
            }

            StorageResourceKey detached = mana("import_removed_during_execute");
            level.setBlock(sourcePos, Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            FixtureManaBlockStrategy.useIsolatedHandler(level, sourcePos, detached, 2_000);
            StorageResourceHandler detachedSource = FixtureManaBlockStrategy.handler(
                    level, sourcePos);
            reloaded.setBusConfiguration(allowOnly(detached, 5));
            FixtureManaBlockStrategy.runOnNextSourceExtract(
                    level,
                    sourcePos,
                    () -> level.setBlock(busPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
            boolean previousDrops = level.getGameRules().getBoolean(
                    net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                    .set(false, level.getServer());
            reloaded.tick();
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                    .set(previousDrops, level.getServer());
            ImportBusBlockEntity recovered = droppedImportBus(level, busPos);
            long recoveredAmount = recovered == null ? 0 : recovered.getPendingResourceAmount(detached);
            if (!level.getBlockState(busPos).isAir()
                    || reloaded.getPendingResourceAmount(detached) != 0
                    || detachedSource.getAmount(detached) != 500
                    || core.getResourceAmount(detached) != 0
                    || recoveredAmount != 1_500
                    || detachedSource.getAmount(detached) + core.getResourceAmount(detached)
                    + recoveredAmount != 2_000) {
                helper.fail("Import execute callback removal wrote escrow to a detached Bus or lost resources: "
                        + "source=" + detachedSource.getAmount(detached)
                        + ", core=" + core.getResourceAmount(detached)
                        + ", detached=" + reloaded.getPendingResourceAmount(detached)
                        + ", recovery=" + recoveredAmount);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void export_partial_acceptance_survives_reentrant_core_capacity_exhaustion_in_escrow(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(2, 3, 1));
        BlockPos busPos = corePos.west();
        BlockPos targetPos = busPos.west();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
        level.setBlock(targetPos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity exporter)) {
                helper.fail("Adversarial typed Export fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            exporter.setBusConfiguration(directionalConfiguration());
            StorageResourceKey mana = mana("export_partial");
            FixtureManaBlockStrategy.set(level, targetPos, mana, 0);
            if (core.insertResource(mana, TRANSFER_BATCH, Action.EXECUTE) != TRANSFER_BATCH) {
                helper.fail("Could not seed typed Export source");
                return;
            }
            boolean[] callbackRan = {false};
            FixtureManaBlockStrategy.runOnNextTargetInsert(level, targetPos, () -> {
                callbackRan[0] = true;
                for (int index = 0; index < core.getTotalTypeSlots(); index++) {
                    StorageResourceKey filler = mana("reentrant_filler_" + index);
                    if (core.insertResource(filler, 1, Action.EXECUTE) != 1) {
                        throw new IllegalStateException("Could not fill every Core type slot");
                    }
                }
            });

            exporter.tick();
            var target = FixtureManaBlockStrategy.handler(level, targetPos);
            long pending = exporter.getPendingResourceAmount(mana);
            if (!callbackRan[0]
                    || target.getAmount(mana) != 400
                    || core.getResourceAmount(mana) != 0
                    || core.getResourceKeys().size() != core.getTotalTypeSlots()
                    || pending != 600
                    || target.getAmount(mana) + core.getResourceAmount(mana) + pending
                    != TRANSFER_BATCH) {
                helper.fail("Reentrant typed Export remainder was not conserved in Bus escrow: target="
                        + target.getAmount(mana) + ", core=" + core.getResourceAmount(mana)
                        + ", pending=" + pending + ", types=" + core.getResourceKeys().size());
                return;
            }


            ItemStack dropped = Block.getDrops(
                            level.getBlockState(busPos), level, busPos, exporter).stream()
                    .filter(stack -> stack.is(MagicStorage.EXPORT_BUS_ITEM.get()))
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
            BlockEntity droppedBus = dropped.has(DataComponents.BLOCK_ENTITY_DATA)
                    ? BlockEntity.loadStatic(
                    busPos,
                    level.getBlockState(busPos),
                    dropped.get(DataComponents.BLOCK_ENTITY_DATA).copyTag(),
                    level.registryAccess())
                    : null;
            if (!(droppedBus instanceof ExportBusBlockEntity droppedExporter)
                    || droppedExporter.getPendingResourceAmount(mana) != 600) {
                helper.fail("Export Bus drop did not preserve typed escrow");
                return;
            }

            CompoundTag saved = exporter.saveWithFullMetadata(level.registryAccess());
            level.removeBlockEntity(busPos);
            BlockEntity loaded = BlockEntity.loadStatic(
                    busPos, level.getBlockState(busPos), saved, level.registryAccess());
            if (!(loaded instanceof ExportBusBlockEntity reloaded)
                    || reloaded.getPendingResourceAmount(mana) != 600) {
                helper.fail("Typed Export Bus escrow did not survive save/load");
                return;
            }
            level.setBlockEntity(reloaded);
            core.rebuildNetwork(level);
            StorageResourceKey freed = mana("reentrant_filler_0");
            if (core.extractResource(freed, 1, Action.EXECUTE) != 1) {
                helper.fail("Could not free a Core type slot for Export escrow recovery");
                return;
            }
            reloaded.setBusConfiguration(pausedConfiguration());
            reloaded.tick();
            if (reloaded.getPendingResourceAmount(mana) != 0
                    || core.getResourceAmount(mana) != 600
                    || target.getAmount(mana) != 400) {
                helper.fail("Paused Export Bus did not recover persisted escrow before automation");
                return;
            }

            StorageResourceKey freedForReplacement = mana("reentrant_filler_1");
            if (core.extractResource(freedForReplacement, 1, Action.EXECUTE) != 1) {
                helper.fail("Could not free a Core type slot for detached recovery check");
                return;
            }
            StorageResourceKey detached = mana("export_replaced_during_execute");
            if (core.insertResource(detached, TRANSFER_BATCH, Action.EXECUTE) != TRANSFER_BATCH) {
                helper.fail("Could not seed detached typed Export source");
                return;
            }
            FixtureManaBlockStrategy.set(level, targetPos, detached, 0);
            reloaded.setBusConfiguration(allowOnly(detached, 3));
            ExportBusBlockEntity[] replacement = {null};
            FixtureManaBlockStrategy.runOnNextTargetInsert(level, targetPos, () -> {
                if (core.insertResource(
                        mana("replacement_capacity_filler"), 1, Action.EXECUTE) != 1) {
                    throw new IllegalStateException("Could not consume the rollback type slot");
                }
                BlockState state = level.getBlockState(busPos);
                level.removeBlockEntity(busPos);
                replacement[0] = new ExportBusBlockEntity(busPos, state);
                level.setBlockEntity(replacement[0]);
                replacement[0].setBusConfiguration(directionalConfiguration(4));
            });
            reloaded.tick();
            ExportBusBlockEntity recovered = droppedExportBus(level, busPos);
            long recoveredAmount = recovered == null ? 0 : recovered.getPendingResourceAmount(detached);
            if (replacement[0] == null
                    || level.getBlockEntity(busPos) != replacement[0]
                    || reloaded.getPendingResourceAmount(detached) != 0
                    || replacement[0].getPendingResourceAmount(detached) != 0
                    || target.getAmount(detached) != 0
                    || core.getResourceAmount(detached) != 0
                    || recoveredAmount != TRANSFER_BATCH
                    || target.getAmount(detached) + core.getResourceAmount(detached)
                    + recoveredAmount != TRANSFER_BATCH) {
                helper.fail("Export execute callback replacement wrote escrow to a detached Bus or lost resources: "
                        + "target=" + target.getAmount(detached)
                        + ", core=" + core.getResourceAmount(detached)
                        + ", detached=" + reloaded.getPendingResourceAmount(detached)
                        + ", replacement=" + (replacement[0] == null
                        ? -1 : replacement[0].getPendingResourceAmount(detached))
                        + ", recovery=" + recoveredAmount);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void generic_native_discovery_and_stale_typed_plans_are_safe(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(2, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos sourcePos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(sourcePos, Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState(),
                Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity importer)) {
                helper.fail("Mixed generic/native Import fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            importer.setBusConfiguration(directionalConfiguration());
            StorageResourceKey mana = mana("mixed_source");
            StorageResourceKey water = StorageResourceKey.fluid(
                    new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1),
                    level.registryAccess());
            FixtureManaBlockStrategy.set(level, sourcePos, mana, TRANSFER_BATCH);
            IFluidHandler fluid = FixtureNativeBlockStorage.fluid(level, sourcePos);
            if (fluid.fill(new FluidStack(
                    net.minecraft.world.level.material.Fluids.WATER,
                    (int) TRANSFER_BATCH), IFluidHandler.FluidAction.EXECUTE) != TRANSFER_BATCH) {
                helper.fail("Could not seed mixed native fluid source");
                return;
            }
            for (int tick = 0; tick < 12; tick++) importer.tick();
            if (core.getResourceAmount(mana) != TRANSFER_BATCH
                    || core.getResourceAmount(water) != TRANSFER_BATCH
                    || FixtureManaBlockStrategy.handler(level, sourcePos).getAmount(mana) != 0
                    || fluid.getFluidInTank(0).getAmount() != 0) {
                helper.fail("Generic block capability masked a native resource capability");
                return;
            }

            level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                    .setValue(ExportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
            level.setBlock(sourcePos, Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            if (!(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity exporter)) {
                helper.fail("Stale typed Export fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);

            StorageResourceKey configStale = mana("stale_config");
            if (core.insertResource(configStale, TRANSFER_BATCH, Action.EXECUTE) != TRANSFER_BATCH) {
                helper.fail("Could not seed config-stale typed Export source");
                return;
            }
            FixtureManaBlockStrategy.set(level, sourcePos, configStale, 0);
            exporter.setBusConfiguration(allowOnly(configStale, 10));
            FixtureManaBlockStrategy.runOnNextSimulation(
                    level, sourcePos, () -> exporter.setBusConfiguration(pausedConfiguration(11)));
            exporter.tick();
            if (!typedTransferUntouched(level, sourcePos, core, configStale)) {
                helper.fail("Config changed after simulation but typed Export still committed");
                return;
            }
            core.extractResource(configStale, TRANSFER_BATCH, Action.EXECUTE);

            StorageResourceKey endpointStale = mana("stale_endpoint");
            if (core.insertResource(endpointStale, TRANSFER_BATCH, Action.EXECUTE) != TRANSFER_BATCH) {
                helper.fail("Could not seed endpoint-stale typed Export source");
                return;
            }
            FixtureManaBlockStrategy.set(level, sourcePos, endpointStale, 0);
            exporter.setBusConfiguration(allowOnly(endpointStale, 12));
            FixtureManaBlockStrategy.runOnNextSimulation(
                    level, sourcePos, () -> FixtureManaBlockStrategy.replaceHandler(level, sourcePos));
            exporter.tick();
            if (!typedTransferUntouched(level, sourcePos, core, endpointStale)) {
                helper.fail("Endpoint identity changed after simulation but typed Export still committed");
                return;
            }
            core.extractResource(endpointStale, TRANSFER_BATCH, Action.EXECUTE);

            StorageResourceKey stateStale = mana("stale_target_state");
            if (core.insertResource(stateStale, TRANSFER_BATCH, Action.EXECUTE) != TRANSFER_BATCH) {
                helper.fail("Could not seed state-stale typed Export source");
                return;
            }
            FixtureManaBlockStrategy.set(level, sourcePos, stateStale, 0);
            exporter.setBusConfiguration(allowOnly(stateStale, 13));
            FixtureManaBlockStrategy.runOnNextSimulation(level, sourcePos, () -> level.setBlock(
                    sourcePos, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL));
            exporter.tick();
            if (!typedTransferUntouched(level, sourcePos, core, stateStale)) {
                helper.fail("Target block state changed after simulation but typed Export still committed");
                return;
            }
            core.extractResource(stateStale, TRANSFER_BATCH, Action.EXECUTE);
            level.setBlock(sourcePos, Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            FixtureManaBlockStrategy.replaceHandler(level, sourcePos);

            StorageResourceKey topologyStale = mana("stale_topology");
            if (core.insertResource(topologyStale, TRANSFER_BATCH, Action.EXECUTE) != TRANSFER_BATCH) {
                helper.fail("Could not seed topology-stale typed Export source");
                return;
            }
            FixtureManaBlockStrategy.set(level, sourcePos, topologyStale, 0);
            exporter.setBusConfiguration(allowOnly(topologyStale, 14));
            BlockPos addedUnitPos = corePos.north();
            FixtureManaBlockStrategy.runOnNextSimulation(level, sourcePos, () -> {
                level.setBlock(addedUnitPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                        Block.UPDATE_ALL);
                core.rebuildNetwork(level);
            });
            exporter.tick();
            if (!typedTransferUntouched(level, sourcePos, core, topologyStale)) {
                helper.fail("Core topology changed after simulation but typed Export still committed");
                return;
            }
            core.extractResource(topologyStale, TRANSFER_BATCH, Action.EXECUTE);

            StorageResourceKey executeEndpointStale = mana("stale_target_execute_endpoint");
            if (core.insertResource(
                    executeEndpointStale, TRANSFER_BATCH, Action.EXECUTE) != TRANSFER_BATCH) {
                helper.fail("Could not seed execute-endpoint-stale typed Export source");
                return;
            }
            FixtureManaBlockStrategy.useIsolatedHandler(
                    level, sourcePos, executeEndpointStale, 0);
            exporter.setBusConfiguration(allowOnly(executeEndpointStale, 15));
            FixtureManaBlockStrategy.runOnNextTargetInsert(
                    level,
                    sourcePos,
                    () -> {
                        FixtureManaBlockStrategy.rejectExtraction(level, sourcePos);
                        FixtureManaBlockStrategy.replaceHandler(level, sourcePos);
                    });
            exporter.tick();
            if (core.getResourceAmount(executeEndpointStale) != 0
                    || FixtureManaBlockStrategy.handler(
                    level, sourcePos).getAmount(executeEndpointStale) != 0
                    || exporter.getPendingResourceAmount(
                    executeEndpointStale) != TRANSFER_BATCH) {
                helper.fail("Export lost resources in a detached target after execute: core="
                        + core.getResourceAmount(executeEndpointStale)
                        + ", live=" + FixtureManaBlockStrategy.handler(
                        level, sourcePos).getAmount(executeEndpointStale)
                        + ", pending="
                        + exporter.getPendingResourceAmount(executeEndpointStale));
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "craftingtests.platform")
    public static void creative_bus_removal_recovers_pending_typed_escrow(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(new BlockPos(2, 3, 1));
        BlockPos busPos = corePos.east();
        BlockPos sourcePos = busPos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(sourcePos, Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity importer)) {
                helper.fail("Creative escrow removal fixture is incomplete");
                return;
            }
            core.rebuildNetwork(level);
            importer.setBusConfiguration(directionalConfiguration());
            StorageResourceKey mana = mana("creative_removal");
            FixtureManaBlockStrategy.set(level, sourcePos, mana, 2_000);
            importer.tick();
            if (importer.getPendingResourceAmount(mana) != 500
                    || core.getResourceAmount(mana) != 1_000) {
                helper.fail("Could not seed pending escrow before creative removal");
                return;
            }
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
            BlockState state = level.getBlockState(busPos);
            BlockState destroyState = state.getBlock().playerWillDestroy(
                    level, busPos, state, player);
            boolean removed = destroyState.onDestroyedByPlayer(
                    level, busPos, player, false, level.getFluidState(busPos));
            if (removed) destroyState.getBlock().destroy(level, busPos, destroyState);
            if (!removed
                    || !level.getBlockState(busPos).isAir()
                    || core.getResourceAmount(mana) != 1_500
                    || FixtureManaBlockStrategy.handler(level, sourcePos).getAmount(mana) != 500) {
                helper.fail("Creative Bus removal discarded pending typed escrow");
                return;
            }
            helper.succeed();
        });
    }

    private static BusConfiguration directionalConfiguration() {
        return directionalConfiguration(1);
    }

    private static BusConfiguration directionalConfiguration(long revision) {
        return BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                false,
                true,
                BusFilterMode.DENY,
                List.of(),
                Optional.empty(),
                revision);
    }

    private static BusConfiguration allowOnly(StorageResourceKey key, long revision) {
        return BusConfiguration.current(
                BusMode.DIRECTIONAL,
                BusConfiguration.ALL_SIDES_MASK,
                false,
                true,
                BusFilterMode.ALLOW,
                List.of(BusFilterRule.resource(key)),
                Optional.empty(),
                revision);
    }

    private static BusConfiguration pausedConfiguration() {
        return pausedConfiguration(2);
    }

    private static BusConfiguration pausedConfiguration(long revision) {
        return BusConfiguration.current(
                BusMode.DIRECTIONLESS,
                BusConfiguration.ALL_SIDES_MASK,
                true,
                false,
                BusFilterMode.DENY,
                List.of(),
                Optional.empty(),
                revision);
    }

    private static boolean typedTransferUntouched(
            net.minecraft.server.level.ServerLevel level,
            BlockPos targetPos,
            StorageCoreBlockEntity core,
            StorageResourceKey key
    ) {
        return core.getResourceAmount(key) == TRANSFER_BATCH
                && FixtureManaBlockStrategy.handler(level, targetPos).getAmount(key) == 0;
    }

    private static ImportBusBlockEntity droppedImportBus(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos
    ) {
        BlockEntity recovered = droppedBus(
                level,
                pos,
                MagicStorage.IMPORT_BUS_ITEM.get(),
                MagicStorage.IMPORT_BUS.get().defaultBlockState());
        return recovered instanceof ImportBusBlockEntity importer ? importer : null;
    }

    private static ExportBusBlockEntity droppedExportBus(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos
    ) {
        BlockEntity recovered = droppedBus(
                level,
                pos,
                MagicStorage.EXPORT_BUS_ITEM.get(),
                MagicStorage.EXPORT_BUS.get().defaultBlockState());
        return recovered instanceof ExportBusBlockEntity exporter ? exporter : null;
    }

    private static BlockEntity droppedBus(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            Item expectedItem,
            BlockState state
    ) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0)).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(expectedItem))
                .filter(stack -> stack.has(DataComponents.BLOCK_ENTITY_DATA))
                .map(stack -> BlockEntity.loadStatic(
                        pos,
                        state,
                        stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTag(),
                        level.registryAccess()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static StorageResourceKey mana(String path) {
        return StorageResourceKey.of(
                ResourceLocation.fromNamespaceAndPath(FixtureMod.MODID, "mana"),
                ResourceLocation.fromNamespaceAndPath(FixtureMod.MODID, path),
                new CompoundTag());
    }
}
