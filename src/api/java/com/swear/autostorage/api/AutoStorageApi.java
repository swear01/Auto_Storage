package com.swear.autostorage.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Stable identifiers shared by the Auto Storage runtime and addon SDK.
 */
public final class AutoStorageApi {
    /**
     * Auto Storage's NeoForge mod ID and registry namespace.
     */
    public static final String MOD_ID = "auto_storage";

    private AutoStorageApi() {
    }

    /**
     * Creates an identifier in the Auto Storage namespace.
     *
     * @param path identifier path
     * @return the namespaced identifier
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
