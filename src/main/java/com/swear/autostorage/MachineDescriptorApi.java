package com.swear.autostorage;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.swear.autostorage.api.AutoStorageApi;

public final class MachineDescriptorApi {
    public static final int MAX_DESCRIPTORS = 256;
    public static final int MAX_INSTALLED_COUNT = Integer.MAX_VALUE;
    public static final ResourceKey<Registry<MachineDescriptor>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(
                    AutoStorageApi.MOD_ID, "machine_descriptor"));

    private MachineDescriptorApi() {
    }

    public static DeferredRegister<MachineDescriptor> createDeferredRegister(String modId) {
        return DeferredRegister.create(REGISTRY_KEY, modId);
    }

    public static void registerPersistenceMigration(
            ResourceLocation legacyDescriptorId,
            ResourceLocation variantItemId,
            ResourceLocation currentDescriptorId
    ) {
        MachineEnergyTable.registerPersistenceMigration(
                legacyDescriptorId, variantItemId, currentDescriptorId);
    }
}
