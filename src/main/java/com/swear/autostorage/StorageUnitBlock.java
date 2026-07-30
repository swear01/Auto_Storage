package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class StorageUnitBlock extends Block implements IStorageNetworkBlock {

    private final StorageTypeCapacity typeCapacityContribution;

    public StorageUnitBlock(Properties properties, int typeContribution) {
        this(properties, StorageTypeCapacity.finite(typeContribution));
    }

    protected StorageUnitBlock(Properties properties, StorageTypeCapacity typeCapacityContribution) {
        super(properties);
        this.typeCapacityContribution = typeCapacityContribution;
    }

    public int getTypeContribution() {
        return typeCapacityContribution.finiteTypeSlots();
    }

    public StorageTypeCapacity getTypeCapacityContribution() {
        return typeCapacityContribution;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            AutoStorage.scheduleNetworkGrowthAfterPlacement(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            AutoStorage.scheduleNetworkRebuildAfterRemoval(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
