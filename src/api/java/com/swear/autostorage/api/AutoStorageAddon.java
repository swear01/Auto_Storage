package com.swear.autostorage.api;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One-call facade for wiring addon-owned Auto Storage custom registries and
 * bounded lifecycle hooks.
 */
public final class AutoStorageAddon {
    private static final ResourceLocation MACHINE_DESCRIPTOR_REGISTRY =
            AutoStorageApi.id("machine_descriptor");
    private static final ResourceLocation RECIPE_FAMILY_REGISTRY =
            AutoStorageApi.id("recipe_family");
    private static final ResourceLocation RESOURCE_KIND_REGISTRY =
            AutoStorageApi.id("resource_kind");
    private static final ResourceLocation CONTAINER_STRATEGY_REGISTRY =
            AutoStorageApi.id("resource_container_strategy");
    private static final ResourceLocation BLOCK_STRATEGY_REGISTRY =
            AutoStorageApi.id("resource_block_strategy");
    private static final ResourceLocation TRANSFORM_PROVIDER_REGISTRY =
            AutoStorageApi.id("transform_provider");
    private static final ResourceLocation MACHINE_VARIANT_CONTRIBUTOR_REGISTRY =
            AutoStorageApi.id("machine_variant_contributor");

    private AutoStorageAddon() {
    }

    /**
     * Builds and wires one addon's registration plan.
     *
     * @param modId addon's NeoForge mod ID and registry namespace
     * @param modBus addon's mod event bus
     * @param registration registration plan callback
     */
    public static void register(
            String modId,
            IEventBus modBus,
            Consumer<Registration> registration
    ) {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(modBus, "modBus");
        Objects.requireNonNull(registration, "registration");
        if (!ResourceLocation.isValidNamespace(modId)) {
            throw new IllegalArgumentException("Invalid addon mod ID: " + modId);
        }
        AutoStorageAddonLifecycle.ensureRegistrationOpen(modId);
        Registration plan = new Registration(modId, modBus);
        registration.accept(plan);
        plan.finish();
    }

    /**
     * Mutable registration plan valid only during the enclosing
     * {@link AutoStorageAddon#register} callback.
     */
    public static final class Registration {
        private final String modId;
        private final IEventBus modBus;
        private final List<DeferredRegister<?>> registers = new ArrayList<>();
        private final Set<Object> identities = new HashSet<>();
        private boolean finished;

        private Registration(String modId, IEventBus modBus) {
            this.modId = modId;
            this.modBus = modBus;
        }

        /**
         * Adds a machine-descriptor deferred register.
         *
         * @param register addon-owned register
         * @return this plan
         */
        public Registration machineDescriptors(DeferredRegister<?> register) {
            return add(register, MACHINE_DESCRIPTOR_REGISTRY);
        }

        /**
         * Adds a deterministic recipe-family deferred register.
         *
         * @param register addon-owned register
         * @return this plan
         */
        public Registration recipeFamilies(DeferredRegister<?> register) {
            return add(register, RECIPE_FAMILY_REGISTRY);
        }

        /**
         * Adds a typed-resource-kind deferred register.
         *
         * @param register addon-owned register
         * @return this plan
         */
        public Registration resourceKinds(DeferredRegister<?> register) {
            return add(register, RESOURCE_KIND_REGISTRY);
        }

        /**
         * Adds an item-container transfer-strategy deferred register.
         *
         * @param register addon-owned register
         * @return this plan
         */
        public Registration containerStrategies(DeferredRegister<?> register) {
            return add(register, CONTAINER_STRATEGY_REGISTRY);
        }

        /**
         * Adds a sided block transfer-strategy deferred register.
         *
         * @param register addon-owned register
         * @return this plan
         */
        public Registration blockStrategies(DeferredRegister<?> register) {
            return add(register, BLOCK_STRATEGY_REGISTRY);
        }

        /**
         * Adds a deterministic Transform-provider deferred register.
         *
         * @param register addon-owned register
         * @return this plan
         */
        public Registration transformProviders(DeferredRegister<?> register) {
            return add(register, TRANSFORM_PROVIDER_REGISTRY);
        }

        /**
         * Adds an existing-station variant-contributor deferred register.
         *
         * @param register addon-owned register
         * @return this plan
         */
        public Registration machineVariantContributors(DeferredRegister<?> register) {
            return add(register, MACHINE_VARIANT_CONTRIBUTOR_REGISTRY);
        }

        /**
         * Adds a NeoForge capability-registration callback.
         *
         * @param callback capability registrar
         * @return this plan
         */
        public Registration capabilities(
                Consumer<RegisterCapabilitiesEvent> callback
        ) {
            ensureOpen();
            modBus.addListener(Objects.requireNonNull(callback, "callback"));
            return this;
        }

        /**
         * Adds a server-start and global-datapack-reload callback.
         *
         * @param id stable callback ID in the addon namespace
         * @param callback deterministic runtime-data refresh
         * @return this plan
         */
        public Registration recipeReload(
                ResourceLocation id,
                Runnable callback
        ) {
            ensureOpen();
            AutoStorageAddonLifecycle.registerRecipeReload(
                    modId,
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(callback, "callback"));
            return this;
        }

        private Registration add(
                DeferredRegister<?> register,
                ResourceLocation expectedRegistry
        ) {
            ensureOpen();
            Objects.requireNonNull(register, "register");
            if (!register.getRegistryKey().location().equals(expectedRegistry)) {
                throw new IllegalArgumentException(
                        "DeferredRegister targets " + register.getRegistryKey().location()
                                + " instead of " + expectedRegistry);
            }
            if (!register.getNamespace().equals(modId)) {
                throw new IllegalArgumentException(
                        "DeferredRegister namespace " + register.getNamespace()
                                + " does not match addon " + modId);
            }
            if (!identities.add(register)) {
                throw new IllegalArgumentException(
                        "DeferredRegister added twice for addon " + modId);
            }
            registers.add(register);
            return this;
        }

        private void finish() {
            ensureOpen();
            AutoStorageAddonLifecycle.ensureRegistrationOpen(modId);
            if (registers.isEmpty()) {
                throw new IllegalArgumentException(
                        "Addon registration contains no Auto Storage registries: " + modId);
            }
            finished = true;
            registers.forEach(register -> register.register(modBus));
        }

        private void ensureOpen() {
            if (finished) {
                throw new IllegalStateException(
                        "Addon registration is already finished: " + modId);
            }
        }
    }
}
