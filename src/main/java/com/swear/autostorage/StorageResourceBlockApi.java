package com.swear.autostorage;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.swear.autostorage.api.AutoStorageApi;

public final class StorageResourceBlockApi {
    public static final int MAX_STRATEGIES = StorageResourceKindApi.MAX_KINDS;
    public static final ResourceKey<Registry<StorageResourceBlockStrategy>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(
                    AutoStorageApi.MOD_ID, "resource_block_strategy"));

    private StorageResourceBlockApi() {
    }

    public static DeferredRegister<StorageResourceBlockStrategy> createDeferredRegister(
            String modId
    ) {
        return DeferredRegister.create(REGISTRY_KEY, modId);
    }
}
