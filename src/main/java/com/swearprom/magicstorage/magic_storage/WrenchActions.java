package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

final class WrenchActions {

    private WrenchActions() {
    }

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = tryUse(event.getLevel(), event.getEntity(), event.getHand(), event.getHitVec());
        if (result != InteractionResult.PASS) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    static InteractionResult tryUse(Level level, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()
                || !player.getItemInHand(hand).is(Tags.Items.TOOLS_WRENCH)) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof IStorageNetworkBlock)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.FAIL;
            }
            return dismantle(serverLevel, serverPlayer, pos, state)
                    ? InteractionResult.CONSUME
                    : InteractionResult.FAIL;
        }

        if (isDirectionlessBus(state)) return InteractionResult.PASS;
        BlockState rotated = rotate(state, hit.getDirection());
        if (rotated == null || rotated.equals(state)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BusConfigurationHost host)
                || !BusConfigurationAccess.authorizeMutation(host, player)) {
            return InteractionResult.FAIL;
        }
        return level.setBlock(pos, rotated, Block.UPDATE_ALL)
                ? InteractionResult.CONSUME
                : InteractionResult.FAIL;
    }

    private static boolean isDirectionlessBus(BlockState state) {
        return (state.getBlock() instanceof ImportBusBlock
                && state.getValue(ImportBusBlock.DIRECTIONLESS))
                || (state.getBlock() instanceof ExportBusBlock
                && state.getValue(ExportBusBlock.DIRECTIONLESS));
    }

    private static BlockState rotate(BlockState state, Direction clickedFace) {
        if (state.getBlock() instanceof ImportBusBlock) {
            Direction facing = state.getValue(ImportBusBlock.FACING);
            return state.setValue(ImportBusBlock.FACING, facing.getClockWise(clickedFace.getAxis()));
        }
        if (state.getBlock() instanceof ExportBusBlock) {
            Direction facing = state.getValue(ExportBusBlock.FACING);
            return state.setValue(ExportBusBlock.FACING, facing.getClockWise(clickedFace.getAxis()));
        }
        return null;
    }

    private static boolean dismantle(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        var breakEvent = CommonHooks.fireBlockBreak(
                level, player.gameMode.getGameModeForPlayer(), player, pos, state);
        if (breakEvent.isCanceled()) return false;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof BusConfigurationHost host
                && !BusConfigurationAccess.canConfigure(host, player)) {
            return false;
        }
        boolean preservesBusEscrowDrop = !player.hasInfiniteMaterials()
                && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        if (!preservesBusEscrowDrop
                && blockEntity instanceof ImportBusBlockEntity importer
                && !importer.recoverPendingResources()) {
            return false;
        }
        if (!preservesBusEscrowDrop
                && blockEntity instanceof ExportBusBlockEntity exporter
                && !exporter.recoverPendingResources()) {
            return false;
        }
        if (blockEntity instanceof BusConfigurationHost host
                && !BusConfigurationAccess.authorizeMutation(host, player)) {
            return false;
        }
        if (blockEntity instanceof StorageCoreBlockEntity core) {
            if (!core.isStorageAvailable()) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "msg.magic_storage.core_storage_unavailable"), true);
                return false;
            }
            if (core.hasRecoverableContents()) {
                core.prepareRecoveryDrop(level, player.getUUID());
            }
        }
        ItemStack tool = player.getMainHandItem().copy();
        List<ItemStack> drops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
                ? Block.getDrops(state, level, pos, blockEntity, player, tool)
                : List.of();
        Block block = state.getBlock();
        BlockState destroyState = block.playerWillDestroy(level, pos, state, player);
        boolean removed = destroyState.onDestroyedByPlayer(
                level, pos, player, true, level.getFluidState(pos));
        if (!removed) return false;
        block.destroy(level, pos, destroyState);

        if (player.hasInfiniteMaterials()) return true;

        player.awardStat(Stats.BLOCK_MINED.get(block));
        player.causeFoodExhaustion(0.005F);
        List<ItemEntity> entities = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                entities.add(new ItemEntity(
                        level,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        drop.copy()));
            }
        }

        var dropEvent = new BlockDropsEvent(level, pos, state, blockEntity, entities, player, tool);
        NeoForge.EVENT_BUS.post(dropEvent);
        preserveBusEscrowAfterDropEvent(level, pos, blockEntity, dropEvent);
        if (!dropEvent.isCanceled()) {
            for (ItemEntity entity : dropEvent.getDrops()) {
                ItemStack remaining = entity.getItem().copy();
                player.getInventory().add(remaining);
                if (!remaining.isEmpty()) {
                    entity.setItem(remaining);
                    level.addFreshEntity(entity);
                }
            }
            state.spawnAfterBreak(level, pos, tool, false);
            if (dropEvent.getDroppedExperience() > 0) {
                block.popExperience(level, pos, dropEvent.getDroppedExperience());
            }
            player.inventoryMenu.broadcastChanges();
        }
        return true;
    }

    private static void preserveBusEscrowAfterDropEvent(
            ServerLevel level,
            BlockPos pos,
            BlockEntity blockEntity,
            BlockDropsEvent dropEvent
    ) {
        if (blockEntity instanceof ImportBusBlockEntity importer) {
            ItemStack expected = importer.createRecoveryDrop(level.registryAccess());
            if (!dropEvent.isCanceled()
                    && BusRecoveryDrops.containsMatchingEscrowDrop(dropEvent.getDrops(), expected)) {
                return;
            }
            if (!importer.recoverPendingResources()) {
                BusRecoveryDrops.spawnIfMissing(
                        level, pos, importer.createRecoveryDrop(level.registryAccess()));
            }
            return;
        }
        if (blockEntity instanceof ExportBusBlockEntity exporter) {
            ItemStack expected = exporter.createRecoveryDrop(level.registryAccess());
            if (!dropEvent.isCanceled()
                    && BusRecoveryDrops.containsMatchingEscrowDrop(dropEvent.getDrops(), expected)) {
                return;
            }
            if (!exporter.recoverPendingResources()) {
                BusRecoveryDrops.spawnIfMissing(
                        level, pos, exporter.createRecoveryDrop(level.registryAccess()));
            }
        }
    }
}
