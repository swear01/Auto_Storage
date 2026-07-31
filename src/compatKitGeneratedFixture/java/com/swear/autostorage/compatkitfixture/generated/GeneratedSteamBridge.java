package com.swear.autostorage.compatkitfixture.generated;

import com.swear.autostorage.StorageResourceContainerStrategy;
import com.swear.autostorage.StorageResourceHandler;
import com.swear.autostorage.StorageResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

public interface GeneratedSteamBridge<C> {
    Optional<StorageResourceContainerStrategy.Transfer> planDeposit(
            ItemStack singleContainer,
            HolderLookup.Provider registries
    );

    Optional<StorageResourceContainerStrategy.Transfer> planWithdraw(
            ItemStack singleContainer,
            StorageResourceKey key,
            long maxAmount,
            HolderLookup.Provider registries
    );

    Optional<StorageResourceHandler> find(
            Level level,
            BlockPos pos,
            Direction side
    );

    boolean render(
            C context,
            StorageResourceKey key,
            long amount,
            int x,
            int y,
            float partialTick
    );
}
