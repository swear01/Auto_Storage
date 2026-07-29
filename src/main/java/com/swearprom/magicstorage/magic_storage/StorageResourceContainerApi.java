package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class StorageResourceContainerApi {
    public static final int MAX_STRATEGIES = StorageResourceKindApi.MAX_KINDS;
    public static final ResourceKey<Registry<StorageResourceContainerStrategy>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(
                    MagicStorage.MODID, "resource_container_strategy"));

    private StorageResourceContainerApi() {
    }

    public static DeferredRegister<StorageResourceContainerStrategy> createDeferredRegister(
            String modId
    ) {
        return DeferredRegister.create(REGISTRY_KEY, modId);
    }
}
