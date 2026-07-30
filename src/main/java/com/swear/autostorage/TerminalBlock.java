package com.swear.autostorage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class TerminalBlock extends Block implements IStorageNetworkBlock {

    private final boolean crafting;

    public TerminalBlock(Properties properties, boolean crafting) {
        super(properties);
        this.crafting = crafting;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            AutoStorage.scheduleNetworkGrowthAfterPlacement(level, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            StorageCoreBlockEntity core = AutoStorage.bfsFindCore(level, pos);
            if (core != null) {
                if (!core.isStorageAvailable()) {
                    player.displayClientMessage(
                            Component.translatable("msg.auto_storage.core_storage_unavailable"), true);
                    return InteractionResult.CONSUME;
                }
                if (crafting) {
                    player.openMenu(new SimpleMenuProvider(
                            (containerId, inv, p) -> new CraftingTerminalMenu(containerId, inv, core, pos, false),
                            Component.translatable("container.auto_storage.crafting_terminal")
                    ), buf -> {
                        buf.writeBlockPos(core.getBlockPos());
                        buf.writeBlockPos(pos);
                        buf.writeBoolean(false);
                        MachineEnergyTable.writeSnapshot(buf, MachineEnergyTable.entries());
                    });
                } else {
                    player.openMenu(new SimpleMenuProvider(
                            (containerId, inv, p) -> new StorageTerminalMenu(containerId, inv, core, pos, false),
                            Component.translatable("container.auto_storage.storage_terminal")
                    ), buf -> {
                        buf.writeBlockPos(core.getBlockPos());
                        buf.writeBlockPos(pos);
                        buf.writeBoolean(false);
                    });
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            AutoStorage.scheduleNetworkRebuildAfterRemoval(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
