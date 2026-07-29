package com.swearprom.magicstorage.magic_storage;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.reflect.InvocationTargetException;

final class OptionalModCapabilities {
    private static final String MEKANISM_MOD_ID = "mekanism";
    private static final String MEKANISM_COMPAT_CLASS =
            "com.swearprom.magicstorage.magic_storage.MekanismChemicalCompat";

    private OptionalModCapabilities() {
    }

    static void register(RegisterCapabilitiesEvent event) {
        if (!ModList.get().isLoaded(MEKANISM_MOD_ID)) return;
        try {
            Class.forName(MEKANISM_COMPAT_CLASS)
                    .getDeclaredMethod("register", RegisterCapabilitiesEvent.class)
                    .invoke(null, event);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Failed to load Mekanism chemical compatibility", exception);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof LinkageError error) {
                throw new IllegalStateException(
                        "Mekanism chemical compatibility is binary-incompatible", error);
            }
            throw new IllegalStateException(
                    "Failed to register Mekanism chemical compatibility", exception.getCause());
        } catch (LinkageError error) {
            throw new IllegalStateException(
                    "Mekanism chemical compatibility is binary-incompatible", error);
        }
    }
}
