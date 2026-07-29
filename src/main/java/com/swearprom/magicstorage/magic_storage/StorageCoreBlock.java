package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public class StorageCoreBlock extends Block implements EntityBlock, IStorageNetworkBlock {

    public StorageCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(state.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core) {
                core.initializeFreshStorage(serverLevel);
            }
            MagicStorage.scheduleNetworkGrowthAfterPlacement(level, pos);
        }
    }

    @Override
    public boolean isStorageCore() { return true; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != MagicStorage.STORAGE_CORE_BE.get()) return null;
        return (l, p, s, be) -> { if (be instanceof StorageCoreBlockEntity core) core.tick(); };
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (!(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof StorageCoreBlockEntity core)) {
            return drops;
        }

        if (params.getLevel() instanceof ServerLevel serverLevel && core.hasRecoverableContents()
                && core.getPreparedRecoveryId().isEmpty()) {
            UUID owner = params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof Player player
                    ? player.getUUID() : null;
            core.prepareRecoveryDrop(serverLevel, owner);
        }

        if (params.getLevel() instanceof ServerLevel serverLevel && core.getPreparedRecoveryId().isPresent()) {
            UUID recoveryId = core.getPreparedRecoveryId().orElseThrow();
            CoreStorageRepository.RecoverySummary summary = CoreStorageRepository.get(serverLevel)
                    .summary(recoveryId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Prepared Core recovery mapping is missing: " + recoveryId));
            for (int index = 0; index < drops.size(); index++) {
                if (drops.get(index).is(MagicStorage.STORAGE_CORE_ITEM.get())) {
                    drops.set(index, StorageCoreBlockItem.createRecoveryStack(summary));
                }
            }
            return drops;
        }
        return drops;
    }

    @Override
    public void playerDestroy(
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            ItemStack tool
    ) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core
                && core.hasRecoverableContents()) {
            UUID recoveryId = core.prepareRecoveryDrop(serverLevel, player.getUUID());
            if (player.isCreative()) {
                CoreStorageRepository.RecoverySummary summary = CoreStorageRepository.get(serverLevel)
                        .summary(recoveryId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Prepared creative Core recovery mapping is missing: " + recoveryId));
                ItemEntity drop = new ItemEntity(
                        level,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        StorageCoreBlockItem.createRecoveryStack(summary));
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onExplosionHit(
            BlockState state,
            Level level,
            BlockPos pos,
            Explosion explosion,
            BiConsumer<ItemStack, BlockPos> dropConsumer
    ) {
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core
                && core.hasRecoverableContents()) {
            UUID owner = explosion.getIndirectSourceEntity() instanceof Player player
                    ? player.getUUID() : null;
            core.prepareRecoveryDrop(serverLevel, owner);
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel)) return;
        var recoveryId = StorageCoreBlockItem.getRecoveryId(stack);
        if (recoveryId.isEmpty()) return;
        if (!(level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core)) {
            throw new IllegalStateException("Placed Storage Core block entity is missing at " + pos);
        }
        if (!core.claimRecovery(serverLevel, recoveryId.get())) {
            throw new IllegalStateException(
                    "Core recovery token was consumed before placement: " + recoveryId.get());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core) {
                if (!moved) {
                    if (level instanceof ServerLevel serverLevel
                            && core.hasRecoverableContents()
                            && core.getPreparedRecoveryId().isEmpty()) {
                        core.prepareRecoveryDrop(serverLevel, null);
                    }
                    core.onBreak();
                }
                if (level instanceof ServerLevel serverLevel) {
                    core.removeStorageForBlockRemoval(serverLevel);
                }
            }
            MagicStorage.scheduleNetworkRebuildAfterRemoval(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide()
                || (!(neighborBlock instanceof IStorageNetworkBlock)
                && (!level.hasChunkAt(neighborPos)
                || !(level.getBlockState(neighborPos).getBlock() instanceof IStorageNetworkBlock)))) return;
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core) {
            core.rebuildNetwork(level);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof StorageCoreBlockEntity core
                && core.isNetworkCapped()) {
            player.displayClientMessage(
                    Component.translatable("msg.magic_storage.network_capped"), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
