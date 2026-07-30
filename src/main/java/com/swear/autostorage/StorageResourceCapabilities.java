package com.swear.autostorage;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import com.swear.autostorage.api.AutoStorageApi;

public final class StorageResourceCapabilities {
    public static final BlockCapability<StorageResourceHandler, Direction> BLOCK =
            BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath(
                            AutoStorageApi.MOD_ID, "storage_resource_handler"),
                    StorageResourceHandler.class);

    private StorageResourceCapabilities() {
    }
}
