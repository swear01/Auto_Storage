package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class StorageResourceContainerStrategies {
    private static Map<ResourceLocation, StorageResourceContainerStrategy> byKind;

    private StorageResourceContainerStrategies() {
    }

    static void registerBuiltIns(
            DeferredRegister<StorageResourceContainerStrategy> strategies
    ) {
        strategies.register("fluid", FluidContainerStrategy::new);
        strategies.register("neoforge_energy", EnergyContainerStrategy::new);
        strategies.register("mekanism_chemical", ChemicalContainerStrategy::new);
        strategies.register("botania_mana", BotaniaManaContainerStrategy::new);
    }

    static synchronized void snapshot() {
        Map<ResourceLocation, StorageResourceContainerStrategy> result = new LinkedHashMap<>();
        MagicStorage.RESOURCE_CONTAINER_STRATEGY_REGISTRY.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    StorageResourceContainerStrategy strategy = entry.getValue();
                    StorageResourceContainerStrategy previous = result.putIfAbsent(
                            strategy.kindId(), strategy);
                    if (previous != null) {
                        throw new IllegalStateException(
                                "Multiple container strategies registered for " + strategy.kindId());
                    }
                });
        byKind = Map.copyOf(result);
    }

    static Optional<StorageResourceContainerStrategy> find(ResourceLocation kindId) {
        if (byKind == null) throw new IllegalStateException("Container strategies are not ready");
        return Optional.ofNullable(byKind.get(kindId));
    }

    static java.util.List<StorageResourceContainerStrategy> all() {
        if (byKind == null) throw new IllegalStateException("Container strategies are not ready");
        return byKind.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private static final class FluidContainerStrategy implements StorageResourceContainerStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.FLUID_KIND;
        }

        @Override
        public Optional<Transfer> planDeposit(
                ItemStack singleContainer,
                HolderLookup.Provider registries
        ) {
            if (singleContainer.isEmpty() || singleContainer.getCount() != 1) return Optional.empty();
            ItemStack copy = singleContainer.copy();
            var handler = copy.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler == null) return Optional.empty();
            FluidStack simulated = handler.drain(
                    Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty()) return Optional.empty();
            FluidStack executed = handler.drain(simulated, IFluidHandler.FluidAction.EXECUTE);
            if (executed.getAmount() != simulated.getAmount()
                    || !FluidStack.isSameFluidSameComponents(executed, simulated)) return Optional.empty();
            return Optional.of(new Transfer(
                    StorageResourceKey.fluid(executed, registries),
                    executed.getAmount(),
                    handler.getContainer()));
        }

        @Override
        public Optional<Transfer> planWithdraw(
                ItemStack singleContainer,
                StorageResourceKey key,
                long maxAmount,
                HolderLookup.Provider registries
        ) {
            if (singleContainer.isEmpty() || singleContainer.getCount() != 1
                    || !key.kindId().equals(kindId()) || maxAmount <= 0) return Optional.empty();
            FluidStack resource = key.fluidStack(
                    (int) Math.min(maxAmount, Integer.MAX_VALUE), registries).orElse(null);
            if (resource == null || resource.isEmpty()) return Optional.empty();
            ItemStack copy = singleContainer.copy();
            var handler = copy.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler == null) return Optional.empty();
            int simulated = handler.fill(resource, IFluidHandler.FluidAction.SIMULATE);
            if (simulated <= 0) return Optional.empty();
            FluidStack exact = resource.copyWithAmount(simulated);
            if (handler.fill(exact, IFluidHandler.FluidAction.EXECUTE) != simulated) {
                return Optional.empty();
            }
            return Optional.of(new Transfer(key, simulated, handler.getContainer()));
        }
    }

    private static final class EnergyContainerStrategy implements StorageResourceContainerStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.ENERGY_KIND;
        }

        @Override
        public Optional<Transfer> planDeposit(
                ItemStack singleContainer,
                HolderLookup.Provider registries
        ) {
            if (singleContainer.isEmpty() || singleContainer.getCount() != 1) return Optional.empty();
            ItemStack copy = singleContainer.copy();
            var handler = copy.getCapability(Capabilities.EnergyStorage.ITEM);
            if (handler == null || !handler.canExtract()) return Optional.empty();
            int simulated = handler.extractEnergy(Integer.MAX_VALUE, true);
            if (simulated <= 0 || handler.extractEnergy(simulated, false) != simulated) {
                return Optional.empty();
            }
            return Optional.of(new Transfer(
                    StorageResourceKey.neoforgeEnergy(), simulated, copy));
        }

        @Override
        public Optional<Transfer> planWithdraw(
                ItemStack singleContainer,
                StorageResourceKey key,
                long maxAmount,
                HolderLookup.Provider registries
        ) {
            if (singleContainer.isEmpty() || singleContainer.getCount() != 1
                    || !key.equals(StorageResourceKey.neoforgeEnergy()) || maxAmount <= 0) {
                return Optional.empty();
            }
            ItemStack copy = singleContainer.copy();
            var handler = copy.getCapability(Capabilities.EnergyStorage.ITEM);
            if (handler == null || !handler.canReceive()) return Optional.empty();
            int request = (int) Math.min(maxAmount, Integer.MAX_VALUE);
            int simulated = handler.receiveEnergy(request, true);
            if (simulated <= 0 || handler.receiveEnergy(simulated, false) != simulated) {
                return Optional.empty();
            }
            return Optional.of(new Transfer(key, simulated, copy));
        }
    }

    private static final class ChemicalContainerStrategy implements StorageResourceContainerStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.CHEMICAL_KIND;
        }

        @Override
        public Optional<Transfer> planDeposit(
                ItemStack singleContainer,
                HolderLookup.Provider registries
        ) {
            return OptionalModContainerStrategies.planMekanismChemicalDeposit(
                    singleContainer, registries);
        }

        @Override
        public Optional<Transfer> planWithdraw(
                ItemStack singleContainer,
                StorageResourceKey key,
                long maxAmount,
                HolderLookup.Provider registries
        ) {
            return OptionalModContainerStrategies.planMekanismChemicalWithdraw(
                    singleContainer, key, maxAmount, registries);
        }
    }

    private static final class BotaniaManaContainerStrategy implements StorageResourceContainerStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.BOTANIA_MANA_KIND;
        }

        @Override
        public Optional<Transfer> planDeposit(
                ItemStack singleContainer,
                HolderLookup.Provider registries
        ) {
            return OptionalModContainerStrategies.planBotaniaManaDeposit(
                    singleContainer, registries);
        }

        @Override
        public Optional<Transfer> planWithdraw(
                ItemStack singleContainer,
                StorageResourceKey key,
                long maxAmount,
                HolderLookup.Provider registries
        ) {
            return OptionalModContainerStrategies.planBotaniaManaWithdraw(
                    singleContainer, key, maxAmount, registries);
        }
    }
}
