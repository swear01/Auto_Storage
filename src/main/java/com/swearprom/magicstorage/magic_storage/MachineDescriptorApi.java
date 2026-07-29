package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MachineDescriptorApi {
    public static final int MAX_DESCRIPTORS = 256;
    public static final int MAX_INSTALLED_COUNT = Integer.MAX_VALUE;
    public static final ResourceKey<Registry<MachineDescriptor>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(
                    MagicStorage.MODID, "machine_descriptor"));

    private MachineDescriptorApi() {
    }

    public static DeferredRegister<MachineDescriptor> createDeferredRegister(String modId) {
        return DeferredRegister.create(REGISTRY_KEY, modId);
    }
}
