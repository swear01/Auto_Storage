package com.swear.autostorage.api;

/**
 * Entrypoint implemented by one bundled, metadata-gated compatibility module.
 */
public interface AutoStorageCompatModule {
    /**
     * Registers the module after every declared target mod is known to be
     * loaded.
     *
     * @param context module identity and registration context
     */
    void register(AutoStorageCompatContext context);
}
