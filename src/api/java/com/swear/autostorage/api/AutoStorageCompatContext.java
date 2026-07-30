package com.swear.autostorage.api;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;

import java.util.List;
import java.util.function.Consumer;

/**
 * Runtime context supplied to a metadata-gated bundled compatibility module.
 */
public interface AutoStorageCompatContext {
    /**
     * @return stable module ID from the bundled metadata index
     */
    ResourceLocation moduleId();

    /**
     * @return sorted target mod IDs required by the module
     */
    List<String> requiredMods();

    /**
     * @return namespace used by the module's deferred registers
     */
    String registrationNamespace();

    /**
     * @return Auto Storage's mod event bus
     */
    IEventBus modBus();

    /**
     * Wires a module registration plan through the public addon facade.
     *
     * @param registration registration plan callback
     */
    default void register(
            Consumer<AutoStorageAddon.Registration> registration
    ) {
        AutoStorageAddon.register(registrationNamespace(), modBus(), registration);
    }
}
