package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

public class WorldStationBlock extends Block {
    private final ResourceLocation blockId;

    public WorldStationBlock(Properties properties, ResourceLocation blockId) {
        super(properties);
        this.blockId = blockId;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Item item = BuiltInRegistries.ITEM.get(blockId);
        return item == net.minecraft.world.item.Items.AIR
                ? List.of()
                : List.of(new ItemStack(item));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(state.getBlock()) && level instanceof ServerLevel serverLevel) {
            WorldStations.place(serverLevel, blockId, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WorldStations.remove(serverLevel, blockId, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
