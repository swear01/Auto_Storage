package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

public class ImportBusBlock extends Block implements EntityBlock, IStorageNetworkBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty DIRECTIONLESS = BooleanProperty.create("directionless");

    public ImportBusBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(DIRECTIONLESS, false));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            MagicStorage.scheduleNetworkGrowthAfterPlacement(level, pos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DIRECTIONLESS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof ImportBusBlockEntity bus) {
            bus.assignOwnerOnPlacement(player.getUUID());
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ImportBusBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != MagicStorage.IMPORT_BUS_BE.get()) return null;
        return (l, p, s, be) -> {
            if (be instanceof ImportBusBlockEntity bus) {
                bus.tick();
            }
        };
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
                && level.getBlockEntity(pos) instanceof ImportBusBlockEntity bus) {
            BusConfigurationMenu.open(
                    player, bus, Component.translatable("container.magic_storage.import_bus"));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            net.minecraft.world.InteractionHand hand,
            BlockHitResult hitResult
    ) {
        useWithoutItem(state, level, pos, player, hitResult);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (!(params.getOptionalParameter(
                LootContextParams.BLOCK_ENTITY) instanceof ImportBusBlockEntity bus)) {
            return drops;
        }
        CompoundTag tag = new CompoundTag();
        bus.saveDropData(tag, params.getLevel().registryAccess());
        for (ItemStack drop : drops) {
            if (drop.is(MagicStorage.IMPORT_BUS_ITEM.get())) {
                BlockItem.setBlockEntityData(
                        drop, MagicStorage.IMPORT_BUS_BE.get(), tag.copy());
            }
        }
        return drops;
    }

    @Override
    public boolean onDestroyedByPlayer(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            boolean willHarvest,
            FluidState fluid
    ) {
        boolean preservesEscrowDrop = !player.hasInfiniteMaterials()
                && willHarvest
                && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        ImportBusBlockEntity bus = !level.isClientSide()
                && level.getBlockEntity(pos) instanceof ImportBusBlockEntity found
                ? found : null;
        if (bus != null) {
            if (preservesEscrowDrop) {
                bus.markEscrowDropWillBePreserved();
            } else if (!bus.recoverPendingResources()) {
                return false;
            }
        }
        boolean removed = super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        if (!removed && bus != null && preservesEscrowDrop) {
            bus.consumeEscrowDropWillBePreserved();
        }
        return removed;
    }

    @Override
    protected void onExplosionHit(
            BlockState state,
            Level level,
            BlockPos pos,
            Explosion explosion,
            BiConsumer<ItemStack, BlockPos> dropConsumer
    ) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof ImportBusBlockEntity bus
                && !bus.recoverPendingResources()) {
            return;
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()
                    && level.getBlockEntity(pos) instanceof ImportBusBlockEntity bus
                    && !bus.consumeEscrowDropWillBePreserved()
                    && !bus.recoverPendingResources()) {
                BusRecoveryDrops.spawnIfMissing(
                        level, pos, bus.createRecoveryDrop(level.registryAccess()));
            }
            MagicStorage.scheduleNetworkRebuildAfterRemoval(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
