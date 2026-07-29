package com.swearprom.magicstorage.fixture.recipe;

import com.swearprom.magicstorage.magic_storage.StorageResourceContainerStrategy;
import com.swearprom.magicstorage.magic_storage.StorageResourceKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

final class FixtureManaContainer {
    private static final String VARIANT_KEY = "fixtureManaVariant";
    private static final String AMOUNT_KEY = "fixtureManaAmount";
    private static final long CAPACITY = 1_000;
    private static final ResourceLocation KIND_ID = ResourceLocation.fromNamespaceAndPath(
            FixtureMod.MODID, "mana");

    private FixtureManaContainer() {
    }

    static StorageResourceContainerStrategy strategy() {
        return new StorageResourceContainerStrategy() {
            @Override
            public ResourceLocation kindId() {
                return KIND_ID;
            }

            @Override
            public Optional<Transfer> planDeposit(
                    ItemStack singleContainer,
                    HolderLookup.Provider registries
            ) {
                if (!singleContainer.is(FixtureMod.MANA_CELL.get()) || amount(singleContainer) <= 0) {
                    return Optional.empty();
                }
                ItemStack result = singleContainer.copy();
                long transferred = amount(result);
                StorageResourceKey key = manaKey(variant(result));
                clear(result);
                return Optional.of(new Transfer(key, transferred, result));
            }

            @Override
            public Optional<Transfer> planWithdraw(
                    ItemStack singleContainer,
                    StorageResourceKey key,
                    long maxAmount,
                    HolderLookup.Provider registries
            ) {
                if (!singleContainer.is(FixtureMod.MANA_CELL.get())
                        || amount(singleContainer) != 0
                        || !key.kindId().equals(KIND_ID)
                        || maxAmount <= 0) {
                    return Optional.empty();
                }
                long transferred = Math.min(CAPACITY, maxAmount);
                ItemStack result = singleContainer.copy();
                set(result, key.resourceId().getPath(), transferred);
                return Optional.of(new Transfer(key, transferred, result));
            }
        };
    }

    static void set(ItemStack stack, String variant, long amount) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(VARIANT_KEY, variant);
            tag.putLong(AMOUNT_KEY, amount);
        });
    }

    static long amount(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getLong(AMOUNT_KEY);
    }

    static String variant(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString(VARIANT_KEY);
    }

    private static void clear(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(VARIANT_KEY);
            tag.remove(AMOUNT_KEY);
        });
    }

    private static StorageResourceKey manaKey(String variant) {
        return StorageResourceKey.of(
                KIND_ID,
                ResourceLocation.fromNamespaceAndPath(FixtureMod.MODID, variant),
                new CompoundTag());
    }
}
