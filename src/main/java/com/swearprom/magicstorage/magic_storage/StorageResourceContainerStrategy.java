package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

public interface StorageResourceContainerStrategy {
    ResourceLocation kindId();

    Optional<Transfer> planDeposit(
            ItemStack singleContainer,
            HolderLookup.Provider registries
    );

    Optional<Transfer> planWithdraw(
            ItemStack singleContainer,
            StorageResourceKey key,
            long maxAmount,
            HolderLookup.Provider registries
    );

    record Transfer(
            StorageResourceKey key,
            long amount,
            ItemStack resultContainer
    ) {
        public Transfer {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(resultContainer, "resultContainer");
            if (amount <= 0) throw new IllegalArgumentException("Container transfer amount must be positive");
            if (resultContainer.getCount() > 1) {
                throw new IllegalArgumentException("One container cannot produce multiple result containers");
            }
            resultContainer = resultContainer.copy();
        }

        @Override
        public ItemStack resultContainer() {
            return resultContainer.copy();
        }
    }
}
