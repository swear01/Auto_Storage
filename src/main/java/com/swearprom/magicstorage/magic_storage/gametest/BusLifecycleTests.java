package com.swearprom.magicstorage.magic_storage;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(MagicStorage.MODID)
@PrefixGameTestTemplate(false)
public final class BusLifecycleTests {
    private static final StorageResourceKey ENERGY = StorageResourceKey.neoforgeEnergy();

    private BusLifecycleTests() {
    }

    @GameTest(template = "persistencetests.platform")
    public static void direct_air_replacement_preserves_import_and_export_escrow(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos importPos = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos exportPos = helper.absolutePos(new BlockPos(4, 3, 1));
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(exportPos, MagicStorage.EXPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity importer)
                || !(level.getBlockEntity(exportPos) instanceof ExportBusBlockEntity exporter)) {
            helper.fail("Escrow lifecycle Buses are missing");
            return;
        }
        seedEscrow(importer, 17, level.registryAccess());
        seedEscrow(exporter, 23, level.registryAccess());

        boolean previousDrops = level.getGameRules().getBoolean(
                net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                .set(false, level.getServer());
        level.setBlock(importPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(exportPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                .set(previousDrops, level.getServer());

        long imported = droppedEscrow(level, importPos, MagicStorage.IMPORT_BUS_ITEM.get());
        long exported = droppedEscrow(level, exportPos, MagicStorage.EXPORT_BUS_ITEM.get());
        if (imported != 17 || exported != 23) {
            helper.fail("Direct air replacement lost Bus escrow: import="
                    + imported + ", export=" + exported);
            return;
        }

        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(importPos).inflate(4.0))) {
            entity.discard();
        }
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity lootImporter)) {
            helper.fail("Loot-first escrow Import Bus is missing");
            return;
        }
        seedEscrow(lootImporter, 29, level.registryAccess());
        if (!level.destroyBlock(importPos, true)) {
            helper.fail("Loot-first escrow Import Bus was not destroyed");
            return;
        }
        int busDrops = 0;
        long escrow = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(importPos).inflate(2.0))) {
            ItemStack stack = entity.getItem();
            if (!stack.is(MagicStorage.IMPORT_BUS_ITEM.get())) continue;
            busDrops += stack.getCount();
            escrow += stackEscrow(stack);
        }
        if (busDrops != 1 || escrow != 29) {
            helper.fail("Loot-first destruction duplicated or lost escrow: drops="
                    + busDrops + ", escrow=" + escrow);
            return;
        }

        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(importPos).inflate(4.0))) {
            entity.discard();
        }
        BlockPos adjacentPos = importPos.east();
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(adjacentPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity first)
                || !(level.getBlockEntity(adjacentPos) instanceof ImportBusBlockEntity second)) {
            helper.fail("Adjacent escrow Import Buses are missing");
            return;
        }
        seedEscrow(first, 37, level.registryAccess());
        seedEscrow(second, 37, level.registryAccess());
        previousDrops = level.getGameRules().getBoolean(
                net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                .set(false, level.getServer());
        level.setBlock(importPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(adjacentPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                .set(previousDrops, level.getServer());

        busDrops = 0;
        escrow = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(importPos).inflate(2.0))) {
            ItemStack stack = entity.getItem();
            if (!stack.is(MagicStorage.IMPORT_BUS_ITEM.get())) continue;
            busDrops += stack.getCount();
            escrow += stackEscrow(stack);
        }
        if (busDrops != 2 || escrow != 74) {
            helper.fail("Adjacent same-tick Bus recovery deduped another source: drops="
                    + busDrops + ", escrow=" + escrow);
            return;
        }

        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(importPos).inflate(4.0))) {
            entity.discard();
        }
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity firstAtSamePos)) {
            helper.fail("First same-position escrow Import Bus is missing");
            return;
        }
        seedEscrow(firstAtSamePos, 41, level.registryAccess());
        level.setBlock(importPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(importPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(importPos) instanceof ImportBusBlockEntity secondAtSamePos)) {
            helper.fail("Second same-position escrow Import Bus is missing");
            return;
        }
        seedEscrow(secondAtSamePos, 41, level.registryAccess());
        level.setBlock(importPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        busDrops = 0;
        escrow = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(importPos).inflate(2.0))) {
            ItemStack stack = entity.getItem();
            if (!stack.is(MagicStorage.IMPORT_BUS_ITEM.get())) continue;
            busDrops += stack.getCount();
            escrow += stackEscrow(stack);
        }
        if (busDrops != 2 || escrow != 82) {
            helper.fail("Same-position Bus recoveries shared a dedupe identity: drops="
                    + busDrops + ", escrow=" + escrow);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "persistencetests.platform")
    public static void block_drop_event_protects_escrow_before_later_listeners(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos busPos = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)) {
            helper.fail("Protected BlockDropsEvent Import Bus is missing");
            return;
        }
        seedEscrow(bus, 59, level.registryAccess());
        ArrayList<ItemEntity> drops = new ArrayList<>();
        drops.add(new ItemEntity(
                level,
                busPos.getX() + 0.5,
                busPos.getY() + 0.5,
                busPos.getZ() + 0.5,
                bus.createRecoveryDrop(level.registryAccess())));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockDropsEvent event = new BlockDropsEvent(
                level,
                busPos,
                level.getBlockState(busPos),
                bus,
                drops,
                player,
                ItemStack.EMPTY);
        ArrayList<ItemStack> intercepted = new ArrayList<>();
        Consumer<BlockDropsEvent> cancelAndConsume = candidate -> {
            if (candidate != event) return;
            for (ItemEntity entity : List.copyOf(candidate.getDrops())) {
                intercepted.add(entity.getItem().copy());
                candidate.getDrops().remove(entity);
            }
            candidate.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(BlockDropsEvent.class, cancelAndConsume);
        try {
            NeoForge.EVENT_BUS.post(event);
        } finally {
            NeoForge.EVENT_BUS.unregister(cancelAndConsume);
        }

        int protectedDrops = 0;
        long protectedEscrow = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(busPos).inflate(2.0))) {
            if (!entity.getItem().is(MagicStorage.IMPORT_BUS_ITEM.get())) continue;
            protectedDrops += entity.getItem().getCount();
            protectedEscrow += stackEscrow(entity.getItem());
        }
        if (!intercepted.isEmpty() || protectedDrops != 1 || protectedEscrow != 59) {
            helper.fail("A later canceled BlockDropsEvent consumed or duplicated Bus escrow: intercepted="
                    + intercepted.size() + ", drops=" + protectedDrops
                    + ", escrow=" + protectedEscrow);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "persistencetests.platform")
    public static void failed_creative_wrench_dismantle_does_not_claim_legacy_bus(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos busPos = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)) {
            helper.fail("Legacy escrow Import Bus is missing");
            return;
        }
        seedEscrow(bus, 31, level.registryAccess());
        if (bus.getBusConfiguration().owner().isPresent()
                || bus.getBusConfiguration().configRevision() != 0) {
            helper.fail("Legacy Bus did not start unclaimed");
            return;
        }

        var player = FakePlayerFactory.get(
                level, new GameProfile(UUID.randomUUID(), "failed-wrench-claim"));
        player.setGameMode(GameType.CREATIVE);
        player.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
        player.setShiftKeyDown(true);

        InteractionResult result = WrenchActions.tryUse(
                level,
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(busPos), net.minecraft.core.Direction.UP, busPos, false));
        if (result != InteractionResult.FAIL || level.getBlockState(busPos).isAir()) {
            helper.fail("Unrecoverable Creative dismantle did not fail closed");
            return;
        }
        if (bus.getBusConfiguration().owner().isPresent()
                || bus.getBusConfiguration().configRevision() != 0) {
            helper.fail("Failed dismantle claimed the legacy Bus: "
                    + bus.getBusConfiguration().owner());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "persistencetests.platform")
    public static void survival_wrench_dismantle_preserves_one_escrow_bus_drop(
            GameTestHelper helper
    ) {
        var level = helper.getLevel();
        BlockPos busPos = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlock(busPos, MagicStorage.IMPORT_BUS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(busPos) instanceof ImportBusBlockEntity bus)) {
            helper.fail("Escrow Import Bus is missing");
            return;
        }
        seedEscrow(bus, 47, level.registryAccess());

        var player = FakePlayerFactory.get(
                level, new GameProfile(UUID.randomUUID(), "survival-wrench-escrow"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MagicStorage.WRENCH.get()));
        player.setShiftKeyDown(true);

        InteractionResult result = WrenchActions.tryUse(
                level,
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(busPos), net.minecraft.core.Direction.UP, busPos, false));
        if (result != InteractionResult.CONSUME || !level.getBlockState(busPos).isAir()) {
            helper.fail("Survival wrench did not dismantle the escrow Import Bus");
            return;
        }

        long escrow = 0;
        int busDrops = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.is(MagicStorage.IMPORT_BUS_ITEM.get())) continue;
            busDrops += stack.getCount();
            escrow += stackEscrow(stack);
        }
        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(busPos).inflate(2.0))) {
            ItemStack stack = entity.getItem();
            if (!stack.is(MagicStorage.IMPORT_BUS_ITEM.get())) continue;
            busDrops += stack.getCount();
            escrow += stackEscrow(stack);
        }
        if (busDrops != 1 || escrow != 47) {
            helper.fail("Survival dismantle duplicated or lost escrow: drops="
                    + busDrops + ", escrow=" + escrow);
            return;
        }
        helper.succeed();
    }

    private static void seedEscrow(
            ImportBusBlockEntity bus,
            long amount,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        CompoundTag tag = escrowTag(amount);
        bus.loadAdditional(tag, registries);
    }

    private static void seedEscrow(
            ExportBusBlockEntity bus,
            long amount,
            net.minecraft.core.HolderLookup.Provider registries
    ) {
        CompoundTag tag = escrowTag(amount);
        bus.loadAdditional(tag, registries);
    }

    private static CompoundTag escrowTag(long amount) {
        BusResourceEscrow escrow = BusResourceEscrow.empty();
        escrow.add(ENERGY, amount);
        CompoundTag tag = new CompoundTag();
        escrow.save(tag);
        return tag;
    }

    private static long droppedEscrow(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            Item expectedItem
    ) {
        long amount = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, new AABB(pos).inflate(2.0))) {
            ItemStack stack = entity.getItem();
            if (!stack.is(expectedItem)) continue;
            var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (data == null) continue;
            amount += BusResourceEscrow.load(data.copyTag()).amount(ENERGY);
        }
        return amount;
    }

    private static long stackEscrow(ItemStack stack) {
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data == null ? 0 : BusResourceEscrow.load(data.copyTag()).amount(ENERGY);
    }
}
