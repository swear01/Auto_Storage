package com.swear.autostorage.api;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AutoStorageAddonLifecycle {
    private static final Map<ResourceLocation, Runnable> RECIPE_RELOADS =
            new LinkedHashMap<>();
    private static boolean registrationClosed;

    private AutoStorageAddonLifecycle() {
    }

    public static synchronized void ensureRegistrationOpen(String owner) {
        if (registrationClosed) {
            throw new IllegalStateException(
                    "Auto Storage addon registration is closed: " + owner);
        }
    }

    public static synchronized void closeRegistration() {
        registrationClosed = true;
    }

    static synchronized void registerRecipeReload(
            String modId,
            ResourceLocation id,
            Runnable callback
    ) {
        ensureRegistrationOpen(id.toString());
        if (!id.getNamespace().equals(modId)) {
            throw new IllegalArgumentException(
                    "Recipe reload hook " + id + " is not owned by " + modId);
        }
        if (RECIPE_RELOADS.putIfAbsent(id, callback) != null) {
            throw new IllegalArgumentException("Duplicate recipe reload hook: " + id);
        }
    }

    public static void runRecipeReloads() {
        Map<ResourceLocation, Runnable> snapshot;
        synchronized (AutoStorageAddonLifecycle.class) {
            registrationClosed = true;
            snapshot = Map.copyOf(RECIPE_RELOADS);
        }
        snapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entry.getValue().run());
    }
}
