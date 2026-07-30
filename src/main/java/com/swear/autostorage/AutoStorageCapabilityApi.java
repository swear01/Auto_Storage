package com.swear.autostorage;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Bounded capability bridge for exposing a typed-resource view from Auto
 * Storage's Core and transfer buses.
 */
public final class AutoStorageCapabilityApi {
    private AutoStorageCapabilityApi() {
    }

    /**
     * Registers one sided capability on the Storage Core, Import Bus, and
     * Export Bus. The factory receives only the public typed-resource handler
     * and queried side; internal block entities are never exposed.
     *
     * @param event NeoForge capability registration event
     * @param capability target capability
     * @param factory wrapper for one server-owned typed-resource handler
     * @param <T> exposed capability type
     */
    public static <T> void registerSidedResourceCapability(
            RegisterCapabilitiesEvent event,
            BlockCapability<T, Direction> capability,
            BiFunction<StorageResourceHandler, Direction, T> factory
    ) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(factory, "factory");
        event.registerBlockEntity(
                capability,
                AutoStorage.STORAGE_CORE_BE.get(),
                (core, side) -> core.getLevel() == null || core.getLevel().isClientSide()
                        ? null : factory.apply(core.resourceHandler(), side));
        event.registerBlockEntity(
                capability,
                AutoStorage.IMPORT_BUS_BE.get(),
                (bus, side) -> {
                    StorageResourceHandler resources = bus.passiveResourceHandler(side);
                    return bus.getLevel() == null || bus.getLevel().isClientSide()
                            || resources == null ? null : factory.apply(resources, side);
                });
        event.registerBlockEntity(
                capability,
                AutoStorage.EXPORT_BUS_BE.get(),
                (bus, side) -> {
                    StorageResourceHandler resources = bus.passiveResourceHandler(side);
                    return bus.getLevel() == null || bus.getLevel().isClientSide()
                            || resources == null ? null : factory.apply(resources, side);
                });
    }
}
