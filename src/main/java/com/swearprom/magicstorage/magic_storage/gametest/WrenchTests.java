package com.swearprom.magicstorage.magic_storage;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

@GameTestHolder(MagicStorage.MODID)
@PrefixGameTestTemplate(false)
public class WrenchTests {

    @GameTest(template = "registrationtests.empty")
    public static void own_wrench_is_registered_and_uses_common_tag(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ITEM);
        var wrench = registry.get(ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "wrench"));
        if (wrench == null || wrench != MagicStorage.WRENCH.get()) {
            helper.fail("Auto Storage wrench is not registered");
            return;
        }
        if (!new ItemStack(wrench).is(Tags.Items.TOOLS_WRENCH)) {
            helper.fail("Auto Storage wrench is not in c:tools/wrench");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "persistencetests.platform")
    public static void wrench_rotates_both_directional_buses(GameTestHelper helper) {
        var level = helper.getLevel();
        var importPos = helper.absolutePos(new BlockPos(1, 3, 1));
        var exportPos = helper.absolutePos(new BlockPos(3, 3, 1));
        level.setBlock(importPos,
                MagicStorage.IMPORT_BUS.get().defaultBlockState().setValue(ImportBusBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);
        level.setBlock(exportPos,
                MagicStorage.EXPORT_BUS.get().defaultBlockState().setValue(ExportBusBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(importPos.getX() + 0.5, importPos.getY() + 0.5, importPos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
        InteractionResult noOpImport = WrenchActions.tryUse(
                level, player, InteractionHand.MAIN_HAND, hit(importPos, Direction.NORTH));
        InteractionResult noOpExport = WrenchActions.tryUse(
                level, player, InteractionHand.MAIN_HAND, hit(exportPos, Direction.NORTH));
        if (noOpImport != InteractionResult.PASS || noOpExport != InteractionResult.PASS
                || !(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity untouchedImporter)
                || !(level.getBlockEntity(exportPos) instanceof ExportBusBlockEntity untouchedExporter)
                || untouchedImporter.getBusConfiguration().owner().isPresent()
                || untouchedExporter.getBusConfiguration().owner().isPresent()
                || untouchedImporter.getBusConfiguration().configRevision() != 0
                || untouchedExporter.getBusConfiguration().configRevision() != 0
                || level.getBlockState(importPos).getValue(ImportBusBlock.FACING) != Direction.NORTH
                || level.getBlockState(exportPos).getValue(ExportBusBlock.FACING) != Direction.NORTH) {
            helper.fail("Same-axis no-op wrench rotation claimed or mutated a legacy Bus");
            return;
        }
        InteractionResult importResult = WrenchActions.tryUse(
                level, player, InteractionHand.MAIN_HAND, hit(importPos, Direction.UP));
        InteractionResult exportResult = WrenchActions.tryUse(
                level, player, InteractionHand.MAIN_HAND, hit(exportPos, Direction.UP));
        if (!importResult.consumesAction() || !exportResult.consumesAction()) {
            helper.fail("Directional wrench rotation must consume the interaction");
            return;
        }
        if (level.getBlockState(importPos).getValue(ImportBusBlock.FACING) != Direction.EAST) {
            helper.fail("Import Bus did not rotate NORTH -> EAST around the clicked top face");
            return;
        }
        if (level.getBlockState(exportPos).getValue(ExportBusBlock.FACING) != Direction.EAST) {
            helper.fail("Export Bus did not rotate NORTH -> EAST around the clicked top face");
            return;
        }
        if (!(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity importer)
                || !(level.getBlockEntity(exportPos) instanceof ExportBusBlockEntity exporter)
                || !importer.getBusConfiguration().owner().equals(Optional.of(player.getUUID()))
                || !exporter.getBusConfiguration().owner().equals(Optional.of(player.getUUID()))
                || importer.getBusConfiguration().configRevision() != 1
                || exporter.getBusConfiguration().configRevision() != 1) {
            helper.fail("First valid wrench rotations did not atomically claim both legacy Buses");
            return;
        }

        var importState = MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.NORTH);
        if (importState.rotate(level, importPos, Rotation.CLOCKWISE_90).getValue(ImportBusBlock.FACING) != Direction.EAST
                || importState.mirror(Mirror.LEFT_RIGHT).getValue(ImportBusBlock.FACING) != Direction.SOUTH) {
            helper.fail("Import Bus rotate/mirror structure transforms are incomplete");
            return;
        }
        var exportState = MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.NORTH);
        if (exportState.rotate(level, exportPos, Rotation.CLOCKWISE_90).getValue(ExportBusBlock.FACING) != Direction.EAST
                || exportState.mirror(Mirror.LEFT_RIGHT).getValue(ExportBusBlock.FACING) != Direction.SOUTH) {
            helper.fail("Export Bus rotate/mirror structure transforms are incomplete");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "persistencetests.platform")
    public static void directionless_bus_normal_wrench_passes_without_rotation(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        var importPos = helper.absolutePos(new BlockPos(1, 3, 1));
        var exportPos = helper.absolutePos(new BlockPos(3, 3, 1));
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        level.setBlock(exportPos, MagicStorage.EXPORT_BUS.get().defaultBlockState()
                .setValue(ExportBusBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity importer)
                || !(level.getBlockEntity(exportPos) instanceof ExportBusBlockEntity exporter)) {
            helper.fail("Directionless wrench test Buses are missing");
            return;
        }
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(importPos.getX() + 0.5, importPos.getY() + 0.5, importPos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
        BusConfiguration directionlessImport = BusConfiguration.current(
                BusMode.DIRECTIONLESS,
                BusConfiguration.ALL_SIDES_MASK,
                true,
                true,
                BusFilterMode.DENY,
                List.of(),
                Optional.of(player.getUUID()),
                1);
        BusConfiguration directionlessExport = BusConfiguration.current(
                BusMode.DIRECTIONLESS,
                BusConfiguration.ALL_SIDES_MASK,
                false,
                true,
                BusFilterMode.ALLOW,
                List.of(),
                Optional.of(player.getUUID()),
                1);
        importer.setBusConfiguration(directionlessImport);
        exporter.setBusConfiguration(directionlessExport);

        InteractionResult importResult = WrenchActions.tryUse(
                level, player, InteractionHand.MAIN_HAND, hit(importPos, Direction.UP));
        InteractionResult exportResult = WrenchActions.tryUse(
                level, player, InteractionHand.MAIN_HAND, hit(exportPos, Direction.UP));
        if (importResult != InteractionResult.PASS
                || exportResult != InteractionResult.PASS
                || level.getBlockState(importPos).getValue(ImportBusBlock.FACING) != Direction.NORTH
                || level.getBlockState(exportPos).getValue(ExportBusBlock.FACING) != Direction.NORTH) {
            helper.fail("Normal wrench use rotated or consumed a directionless Bus interaction");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "persistencetests.platform")
    public static void bus_wrench_rotation_and_dismantle_require_owner(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 2));
        var busPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState()
                .setValue(ImportBusBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)) {
                helper.fail("Owned wrench test Core or Import Bus is missing");
                return;
            }
            core.rebuildNetwork(level);
            var owner = FakePlayerFactory.get(
                    level, new GameProfile(UUID.randomUUID(), "bus-wrench-owner"));
            var intruder = FakePlayerFactory.get(
                    level, new GameProfile(UUID.randomUUID(), "bus-wrench-intruder"));
            owner.setGameMode(GameType.SURVIVAL);
            intruder.setGameMode(GameType.SURVIVAL);
            owner.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
            intruder.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
            intruder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
            bus.assignOwnerOnPlacement(owner.getUUID());

            if (WrenchActions.tryUse(
                    level, intruder, InteractionHand.MAIN_HAND, hit(busPos, Direction.UP))
                    != InteractionResult.FAIL
                    || level.getBlockState(busPos).getValue(ImportBusBlock.FACING) != Direction.NORTH) {
                helper.fail("Non-owner rotated an owned Bus");
                return;
            }
            if (!WrenchActions.tryUse(
                    level, owner, InteractionHand.MAIN_HAND, hit(busPos, Direction.UP)).consumesAction()
                    || level.getBlockState(busPos).getValue(ImportBusBlock.FACING) != Direction.EAST) {
                helper.fail("Owner could not rotate an owned Bus");
                return;
            }

            CompoundTag escrowTag = bus.saveWithoutMetadata(level.registryAccess());
            BusResourceEscrow escrow = BusResourceEscrow.empty();
            escrow.add(StorageResourceKey.neoforgeEnergy(), 47);
            escrow.save(escrowTag);
            bus.loadAdditional(escrowTag, level.registryAccess());
            BusConfiguration before = bus.getBusConfiguration();
            intruder.setGameMode(GameType.CREATIVE);
            intruder.setShiftKeyDown(true);
            if (WrenchActions.tryUse(
                    level, intruder, InteractionHand.MAIN_HAND, hit(busPos, Direction.UP))
                    != InteractionResult.FAIL
                    || level.getBlockState(busPos).isAir()
                    || core.getResourceAmount(StorageResourceKey.neoforgeEnergy()) != 0
                    || bus.getPendingResourceAmount(StorageResourceKey.neoforgeEnergy()) != 47
                    || !bus.getBusConfiguration().equals(before)) {
                helper.fail("Non-owner Creative wrench mutated Bus escrow, Core storage, owner, or revision");
                return;
            }
            owner.setShiftKeyDown(true);
            if (!WrenchActions.tryUse(
                    level, owner, InteractionHand.MAIN_HAND, hit(busPos, Direction.UP)).consumesAction()
                    || !level.getBlockState(busPos).isAir()) {
                helper.fail("Owner could not dismantle an owned Bus");
                return;
            }

            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                if (owner.getInventory().getItem(slot).is(MagicStorage.IMPORT_BUS_ITEM.get())) {
                    owner.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
            for (ItemEntity entity : level.getEntitiesOfClass(
                    ItemEntity.class, new AABB(busPos).inflate(3.0))) {
                if (entity.getItem().is(MagicStorage.IMPORT_BUS_ITEM.get())) entity.discard();
            }

            BlockPos canceledBusPos = corePos.north();
            level.setBlock(
                    canceledBusPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
            if (!(level.getBlockEntity(canceledBusPos) instanceof ImportBusBlockEntity canceledBus)) {
                helper.fail("Canceled-drop Import Bus is missing");
                return;
            }
            BusResourceEscrow canceledEscrow = BusResourceEscrow.empty();
            canceledEscrow.add(StorageResourceKey.neoforgeEnergy(), 53);
            CompoundTag canceledTag = canceledBus.saveWithoutMetadata(level.registryAccess());
            canceledEscrow.save(canceledTag);
            canceledBus.loadAdditional(canceledTag, level.registryAccess());
            canceledBus.assignOwnerOnPlacement(owner.getUUID());
            core.rebuildNetwork(level);

            Consumer<BlockDropsEvent> cancelDrop = event -> {
                if (event.getLevel() == level && event.getPos().equals(canceledBusPos)) {
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(BlockDropsEvent.class, cancelDrop);
            InteractionResult canceledResult;
            try {
                canceledResult = WrenchActions.tryUse(
                        level, owner, InteractionHand.MAIN_HAND, hit(canceledBusPos, Direction.UP));
            } finally {
                NeoForge.EVENT_BUS.unregister(cancelDrop);
            }
            if (!canceledResult.consumesAction() || !level.getBlockState(canceledBusPos).isAir()) {
                helper.fail("Canceled BlockDropsEvent prevented Bus dismantle without preserving it");
                return;
            }

            long conserved = core.getResourceAmount(StorageResourceKey.neoforgeEnergy());
            for (ItemStack stack : owner.getInventory().items) {
                if (stack.is(MagicStorage.IMPORT_BUS_ITEM.get())) conserved += stackEscrow(stack);
            }
            for (ItemEntity entity : level.getEntitiesOfClass(
                    ItemEntity.class, new AABB(canceledBusPos).inflate(2.0))) {
                if (entity.getItem().is(MagicStorage.IMPORT_BUS_ITEM.get())) {
                    conserved += stackEscrow(entity.getItem());
                }
            }
            if (conserved != 53) {
                helper.fail("Canceled BlockDropsEvent lost or duplicated Bus escrow: " + conserved);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "persistencetests.platform")
    public static void replaced_wrench_drop_event_does_not_restore_plain_bus_without_escrow(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos busPos = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)) {
            helper.fail("Plain replaced-drop Import Bus is missing");
            return;
        }
        var player = FakePlayerFactory.get(
                level, new GameProfile(UUID.randomUUID(), "plain-replaced-wrench"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
        player.setShiftKeyDown(true);
        bus.assignOwnerOnPlacement(player.getUUID());

        Consumer<BlockDropsEvent> replaceDrops = event -> {
            if (event.getLevel() == level && event.getPos().equals(busPos)) {
                event.getDrops().clear();
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockDropsEvent.class, replaceDrops);
        InteractionResult result;
        try {
            result = WrenchActions.tryUse(
                    level, player, InteractionHand.MAIN_HAND, hit(busPos, Direction.UP));
        } finally {
            NeoForge.EVENT_BUS.unregister(replaceDrops);
        }
        int busItems = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(MagicStorage.IMPORT_BUS_ITEM.get())) busItems += stack.getCount();
        }
        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(busPos).inflate(2.0))) {
            if (entity.getItem().is(MagicStorage.IMPORT_BUS_ITEM.get())) {
                busItems += entity.getItem().getCount();
            }
        }
        if (!result.consumesAction() || !level.getBlockState(busPos).isAir() || busItems != 0) {
            helper.fail("Replaced BlockDropsEvent restored a plain Wrench Bus without escrow: "
                    + busItems);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "persistencetests.platform")
    public static void ordinary_wrench_passes_non_directional_blocks_and_rejects_wrong_context(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var busPos = helper.absolutePos(new BlockPos(3, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos,
                MagicStorage.IMPORT_BUS.get().defaultBlockState().setValue(ImportBusBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
        if (WrenchActions.tryUse(level, player, InteractionHand.MAIN_HAND, hit(corePos, Direction.UP)) != InteractionResult.PASS) {
            helper.fail("Ordinary wrench use on a non-directional network block must pass");
            return;
        }
        if (WrenchActions.tryUse(level, player, InteractionHand.OFF_HAND, hit(busPos, Direction.UP)) != InteractionResult.PASS) {
            helper.fail("Off-hand wrench use must pass");
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        if (WrenchActions.tryUse(level, player, InteractionHand.MAIN_HAND, hit(busPos, Direction.UP)) != InteractionResult.PASS) {
            helper.fail("An untagged item must not act as a wrench");
            return;
        }
        var spectator = helper.makeMockPlayer(GameType.SPECTATOR);
        spectator.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
        if (WrenchActions.tryUse(level, spectator, InteractionHand.MAIN_HAND, hit(busPos, Direction.UP)) != InteractionResult.PASS) {
            helper.fail("Spectator wrench use must pass without mutation");
            return;
        }
        if (level.getBlockState(busPos).getValue(ImportBusBlock.FACING) != Direction.NORTH) {
            helper.fail("Rejected wrench contexts mutated the bus");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "persistencetests.platform")
    public static void wrench_event_cancels_export_filter_assignment_before_rotation(GameTestHelper helper) {
        var level = helper.getLevel();
        var busPos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(busPos,
                MagicStorage.EXPORT_BUS.get().defaultBlockState().setValue(ExportBusBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
            helper.fail("Export Bus block entity missing");
            return;
        }
        bus.setFilter(new ItemStack(Items.DIAMOND));
        ItemKey before = bus.getFilter();

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
        var event = new PlayerInteractEvent.RightClickBlock(
                player, InteractionHand.MAIN_HAND, busPos, hit(busPos, Direction.UP));
        WrenchActions.onRightClickBlock(event);
        if (!event.isCanceled() || !event.getCancellationResult().consumesAction()) {
            helper.fail("Handled wrench event must cancel later block/item use");
            return;
        }
        if (!before.equals(bus.getFilter())) {
            helper.fail("Wrench rotation replaced the Export Bus filter");
            return;
        }
        if (level.getBlockState(busPos).getValue(ExportBusBlock.FACING) != Direction.EAST) {
            helper.fail("Canceled Export Bus wrench event did not rotate the bus");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "persistencetests.platform")
    public static void sneak_wrench_dismantle_preserves_core_inventory_energy_and_identity(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.east(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
                helper.fail("Storage Core block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            core.insertItem(new ItemStack(Items.STONE, 73));
            if (!core.addFuel(new ItemStack(Items.COAL), EnergyType.FURNACE_FUEL)) {
                helper.fail("Could not seed fuel before dismantle");
                return;
            }
            UUID networkId = core.getNetworkId();
            long fuel = core.getEnergy(EnergyType.FURNACE_FUEL);

            var player = FakePlayerFactory.get(
                    level, new GameProfile(UUID.randomUUID(), "wrench-core-test"));
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
            player.setShiftKeyDown(true);
            InteractionResult result = WrenchActions.tryUse(
                    level, player, InteractionHand.MAIN_HAND, hit(corePos, Direction.UP));
            if (!result.consumesAction() || !level.getBlockState(corePos).isAir()) {
                helper.fail("Sneak wrench did not dismantle the Storage Core: result=" + result
                        + ", shifted=" + player.isShiftKeyDown()
                        + ", tagged=" + player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH)
                        + ", state=" + level.getBlockState(corePos));
                return;
            }

            ItemStack droppedCore = findInventoryStack(player.getInventory(), MagicStorage.STORAGE_CORE_ITEM.get());
            if (droppedCore.isEmpty() || StorageCoreBlockItem.getRecoveryId(droppedCore).isEmpty()) {
                helper.fail("Dismantled Core did not enter inventory with a server recovery token");
                return;
            }
            var restoredPos = helper.absolutePos(new BlockPos(5, 3, 1));
            level.setBlock(restoredPos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(restoredPos.east(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
            MagicStorage.STORAGE_CORE.get().setPlacedBy(
                    level, restoredPos, level.getBlockState(restoredPos), player, droppedCore);
            if (!(level.getBlockEntity(restoredPos) instanceof StorageCoreBlockEntity restored)) {
                helper.fail("Restored Core block entity missing");
                return;
            }
            if (!networkId.equals(restored.getNetworkId())) {
                helper.fail("Wrench dismantle changed the Core identity");
                return;
            }
            if (restored.getItemCount(ItemKey.of(new ItemStack(Items.STONE))) != 73) {
                helper.fail("Wrench dismantle lost Core inventory");
                return;
            }
            if (restored.getEnergy(EnergyType.FURNACE_FUEL) != fuel) {
                helper.fail("Wrench dismantle lost Core fuel energy");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "persistencetests.platform")
    public static void full_inventory_dismantle_spawns_exact_world_overflow(GameTestHelper helper) {
        var level = helper.getLevel();
        var unitPos = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlock(unitPos, MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        var player = FakePlayerFactory.get(
                level, new GameProfile(UUID.randomUUID(), "wrench-overflow-test"));
        player.setGameMode(GameType.SURVIVAL);
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
        player.setShiftKeyDown(true);

        InteractionResult result = WrenchActions.tryUse(
                level, player, InteractionHand.MAIN_HAND, hit(unitPos, Direction.UP));
        if (!result.consumesAction() || !level.getBlockState(unitPos).isAir()) {
            helper.fail("Full-inventory wrench dismantle did not remove the Storage Unit: result=" + result
                    + ", shifted=" + player.isShiftKeyDown()
                    + ", tagged=" + player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH)
                    + ", state=" + level.getBlockState(unitPos));
            return;
        }
        if (!findInventoryStack(player.getInventory(), MagicStorage.STORAGE_UNIT_T1_ITEM.get()).isEmpty()) {
            helper.fail("Full inventory unexpectedly accepted the dismantled Storage Unit");
            return;
        }
        int worldCount = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(unitPos).inflate(2.0))) {
            if (entity.getItem().is(MagicStorage.STORAGE_UNIT_T1_ITEM.get())) {
                worldCount += entity.getItem().getCount();
            }
        }
        if (worldCount != 1) {
            helper.fail("Expected one exact Storage Unit world overflow, got " + worldCount);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "persistencetests.platform")
    public static void export_bus_drop_preserves_only_component_rich_filter(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = helper.absolutePos(new BlockPos(1, 3, 1));
        var busPos = corePos.east();
        level.setBlock(corePos, MagicStorage.STORAGE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(corePos.south(), MagicStorage.STORAGE_UNIT_T1.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(busPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(2, () -> {
            if (!(level.getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)
                    || !(level.getBlockEntity(busPos) instanceof ExportBusBlockEntity bus)) {
                helper.fail("Core or Export Bus block entity missing");
                return;
            }
            core.rebuildNetwork(level);
            var filter = new ItemStack(Items.DIAMOND_SWORD);
            filter.enchant(
                    level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                            .getHolderOrThrow(Enchantments.SHARPNESS),
                    3);
            bus.setFilter(filter);
            bus.assignOwnerOnPlacement(UUID.randomUUID());
            bus.tick();

            var drops = Block.getDrops(level.getBlockState(busPos), level, busPos, bus);
            ItemStack busDrop = ItemStack.EMPTY;
            for (ItemStack drop : drops) {
                if (drop.is(MagicStorage.EXPORT_BUS_ITEM.get())) {
                    busDrop = drop;
                    break;
                }
            }
            if (busDrop.isEmpty() || !busDrop.has(DataComponents.BLOCK_ENTITY_DATA)) {
                helper.fail("Export Bus drop did not preserve its filter");
                return;
            }
            var data = busDrop.get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
            if (!data.contains("filter")
                    || !data.contains(BusConfiguration.TAG_BUS_CONFIG)
                    || data.contains("coreX") || data.contains("coreY") || data.contains("coreZ")) {
                helper.fail("Export Bus drop must preserve configuration without runtime Core hints, got " + data);
                return;
            }
            var droppedConfig = BusConfiguration.load(
                    data,
                    BusKind.EXPORT,
                    level.registryAccess());
            if (!droppedConfig.supported() || droppedConfig.owner().isPresent()) {
                helper.fail("Export Bus drop retained an owner or unsupported configuration");
                return;
            }

            var restoredPos = helper.absolutePos(new BlockPos(5, 3, 1));
            level.setBlock(restoredPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
            if (!BlockItem.updateCustomBlockEntityTag(level, null, restoredPos, busDrop)) {
                helper.fail("Export Bus filter data could not be restored");
                return;
            }
            if (!(level.getBlockEntity(restoredPos) instanceof ExportBusBlockEntity restored)
                    || restored.getFilter() == null
                    || !ItemStack.isSameItemSameComponents(filter, restored.getFilter().toStack(1))) {
                helper.fail("Export Bus restored filter lost item components");
                return;
            }
            helper.succeed();
        });
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
    }

    private static ItemStack findInventoryStack(net.minecraft.world.entity.player.Inventory inventory,
                                                net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static long stackEscrow(ItemStack stack) {
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data == null ? 0 : BusResourceEscrow.load(data.copyTag())
                .amount(StorageResourceKey.neoforgeEnergy());
    }
}
