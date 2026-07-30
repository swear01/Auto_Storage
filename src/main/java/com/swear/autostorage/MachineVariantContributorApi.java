package com.swear.autostorage;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.swear.autostorage.api.AutoStorageApi;

/**
 * Public registry API for contributing variants to existing station
 * descriptors.
 */
public final class MachineVariantContributorApi {
    /**
     * Maximum number of variant-contributor entries.
     */
    public static final int MAX_CONTRIBUTORS = 256;
    /**
     * Registry key for machine variant contributors.
     */
    public static final ResourceKey<Registry<MachineVariantContributor>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(
                    AutoStorageApi.MOD_ID, "machine_variant_contributor"));

    private MachineVariantContributorApi() {
    }

    /**
     * Creates an addon-owned deferred register.
     *
     * @param modId addon namespace
     * @return deferred register targeting the contributor registry
     */
    public static DeferredRegister<MachineVariantContributor> createDeferredRegister(
            String modId
    ) {
        return DeferredRegister.create(REGISTRY_KEY, modId);
    }
}
