package com.swear.autostorage.compat;

import com.swear.autostorage.RecipeDiagramRenderer;

public final class EmiRecipeDiagramBootstrap {
    private static volatile boolean registryReady;

    private EmiRecipeDiagramBootstrap() {
    }

    public static void markRegistryReady() {
        registryReady = true;
    }

    public static RecipeDiagramRenderer create() {
        if (!registryReady) {
            throw new IllegalStateException("EMI recipe registry is not ready");
        }
        return new EmiRecipeDiagramRenderer();
    }
}
