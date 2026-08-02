package com.swear.autostorage.compatkitfixture.generated;

import com.swear.autostorage.StorageResourceBlockStrategy;
import com.swear.autostorage.StorageResourceContainerStrategy;
import com.swear.autostorage.StorageResourceHandler;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKind;
import com.swear.autostorage.TerminalResourceRendererApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

public final class GeneratedSteamResource {
    private GeneratedSteamResource() {
    }

    public static final ResourceLocation COMPAT_KIT_FIXTURE_STEAM =
            id("compat_kit_fixture", "steam");

    public static StorageResourceKind SteamKind() {
        return StorageResourceKind.variantless(() ->
                new ItemStack(requiredItem(id("minecraft", "water_bucket"))));
    }

    public static <C> StorageResourceContainerStrategy SteamContainers(
            GeneratedSteamBridge<C> bridge
    ) {
        Objects.requireNonNull(bridge, "bridge");
        return new StorageResourceContainerStrategy() {
            @Override
            public ResourceLocation kindId() {
                return id("compat_kit_fixture", "steam");
            }

            @Override
            public Optional<Transfer> planDeposit(
                    ItemStack singleContainer,
                    HolderLookup.Provider registries
            ) {
                return bridge.planDeposit(singleContainer, registries);
            }

            @Override
            public Optional<Transfer> planWithdraw(
                    ItemStack singleContainer,
                    StorageResourceKey key,
                    long maxAmount,
                    HolderLookup.Provider registries
            ) {
                return bridge.planWithdraw(singleContainer, key, maxAmount, registries);
            }
        };
    }

    public static <C> StorageResourceBlockStrategy SteamBlocks(
            GeneratedSteamBridge<C> bridge
    ) {
        Objects.requireNonNull(bridge, "bridge");
        return new StorageResourceBlockStrategy() {
            @Override
            public ResourceLocation kindId() {
                return id("compat_kit_fixture", "steam");
            }

            @Override
            public Optional<StorageResourceHandler> find(
                    Level level,
                    BlockPos pos,
                    Direction side
            ) {
                return bridge.find(level, pos, side);
            }
        };
    }

    public static <C> void registerSteamRenderer(
            Class<C> contextType,
            GeneratedSteamBridge<C> bridge
    ) {
        Objects.requireNonNull(bridge, "bridge");
        TerminalResourceRendererApi.register(
                id("compat_kit_fixture", "steam"), contextType, bridge::render);
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalStateException("Missing resource representative " + id);
        return item;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
