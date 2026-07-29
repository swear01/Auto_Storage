package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StorageResourceBlockStrategies {
    private static Map<ResourceLocation, StorageResourceBlockStrategy> byKind;

    private StorageResourceBlockStrategies() {
    }

    static void registerBuiltIns(DeferredRegister<StorageResourceBlockStrategy> strategies) {
        strategies.register("fluid", FluidBlockStrategy::new);
        strategies.register("neoforge_energy", EnergyBlockStrategy::new);
        strategies.register("mekanism_chemical", ChemicalBlockStrategy::new);
        strategies.register("ars_nouveau_source", ArsNouveauSourceBlockStrategy::new);
    }

    static synchronized void snapshot() {
        Map<ResourceLocation, StorageResourceBlockStrategy> result = new LinkedHashMap<>();
        MagicStorage.RESOURCE_BLOCK_STRATEGY_REGISTRY.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    StorageResourceBlockStrategy strategy = entry.getValue();
                    StorageResourceBlockStrategy previous = result.putIfAbsent(
                            strategy.kindId(), strategy);
                    if (previous != null) {
                        throw new IllegalStateException(
                                "Multiple block strategies registered for " + strategy.kindId());
                    }
                });
        byKind = Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    static List<Endpoint> find(Level level, BlockPos pos, Direction side) {
        if (byKind == null) throw new IllegalStateException("Block strategies are not ready");
        StorageResourceHandler generic = level.getCapability(
                StorageResourceCapabilities.BLOCK, pos, side);
        List<Endpoint> result = new ArrayList<>();
        if (generic != null) result.add(new Endpoint(null, generic));
        byKind.values().stream()
                .map(strategy -> strategy.find(level, pos, side)
                        .map(handler -> new Endpoint(strategy.kindId(), handler)))
                .flatMap(Optional::stream)
                .forEach(result::add);
        return List.copyOf(result);
    }

    record Endpoint(ResourceLocation kindId, StorageResourceHandler handler) {
        boolean supports(StorageResourceKey key) {
            return kindId == null || kindId.equals(key.kindId());
        }

        boolean sameIdentity(Endpoint other) {
            return java.util.Objects.equals(kindId, other.kindId)
                    && (handler == other.handler || handler.equals(other.handler));
        }
    }

    private static final class FluidBlockStrategy implements StorageResourceBlockStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.FLUID_KIND;
        }

        @Override
        public Optional<StorageResourceHandler> find(
                Level level,
                BlockPos pos,
                Direction side
        ) {
            IFluidHandler handler = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, pos, side);
            return handler == null
                    ? Optional.empty()
                    : Optional.of(new FluidBlockHandler(handler, level.registryAccess()));
        }
    }

    private static final class EnergyBlockStrategy implements StorageResourceBlockStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.ENERGY_KIND;
        }

        @Override
        public Optional<StorageResourceHandler> find(
                Level level,
                BlockPos pos,
                Direction side
        ) {
            IEnergyStorage handler = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, pos, side);
            return handler == null
                    ? Optional.empty()
                    : Optional.of(new EnergyBlockHandler(handler));
        }
    }

    private static final class ChemicalBlockStrategy implements StorageResourceBlockStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.CHEMICAL_KIND;
        }

        @Override
        public Optional<StorageResourceHandler> find(
                Level level,
                BlockPos pos,
                Direction side
        ) {
            return OptionalModBlockStrategies.findMekanismChemical(level, pos, side);
        }
    }

    private static final class ArsNouveauSourceBlockStrategy
            implements StorageResourceBlockStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND;
        }

        @Override
        public Optional<StorageResourceHandler> find(
                Level level,
                BlockPos pos,
                Direction side
        ) {
            return OptionalModBlockStrategies.findArsNouveauSource(level, pos, side);
        }
    }

    private record FluidBlockHandler(
            IFluidHandler fluids,
            HolderLookup.Provider registries
    ) implements StorageResourceHandler {
        @Override
        public List<StorageResourceKey> getStoredResources() {
            java.util.TreeSet<StorageResourceKey> result = new java.util.TreeSet<>();
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                FluidStack stack = fluids.getFluidInTank(tank);
                if (!stack.isEmpty()) result.add(StorageResourceKey.fluid(stack, registries));
            }
            return List.copyOf(result);
        }

        @Override
        public long getAmount(StorageResourceKey key) {
            if (!key.kindId().equals(StorageResourceKindApi.FLUID_KIND)) return 0;
            long amount = 0;
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                FluidStack stack = fluids.getFluidInTank(tank);
                if (!stack.isEmpty() && StorageResourceKey.fluid(stack, registries).equals(key)) {
                    amount = Math.addExact(amount, stack.getAmount());
                }
            }
            return amount;
        }

        @Override
        public long insert(StorageResourceKey key, long amount, boolean simulate) {
            if (amount <= 0 || !key.kindId().equals(StorageResourceKindApi.FLUID_KIND)) return 0;
            FluidStack stack = key.fluidStack(
                    (int) Math.min(amount, Integer.MAX_VALUE), registries).orElse(null);
            if (stack == null || stack.isEmpty()) return 0;
            return fluids.fill(stack, simulate
                    ? IFluidHandler.FluidAction.SIMULATE
                    : IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public long extract(StorageResourceKey key, long amount, boolean simulate) {
            if (amount <= 0 || !key.kindId().equals(StorageResourceKindApi.FLUID_KIND)) return 0;
            FluidStack requested = key.fluidStack(
                    (int) Math.min(amount, Integer.MAX_VALUE), registries).orElse(null);
            if (requested == null || requested.isEmpty()) return 0;
            FluidStack extracted = fluids.drain(requested, simulate
                    ? IFluidHandler.FluidAction.SIMULATE
                    : IFluidHandler.FluidAction.EXECUTE);
            return !extracted.isEmpty()
                    && StorageResourceKey.fluid(extracted, registries).equals(key)
                    ? extracted.getAmount()
                    : 0;
        }
    }

    private record EnergyBlockHandler(IEnergyStorage energy) implements StorageResourceHandler {
        @Override
        public List<StorageResourceKey> getStoredResources() {
            return energy.getEnergyStored() > 0
                    ? List.of(StorageResourceKey.neoforgeEnergy())
                    : List.of();
        }

        @Override
        public long getAmount(StorageResourceKey key) {
            return key.equals(StorageResourceKey.neoforgeEnergy()) ? energy.getEnergyStored() : 0;
        }

        @Override
        public long insert(StorageResourceKey key, long amount, boolean simulate) {
            return key.equals(StorageResourceKey.neoforgeEnergy()) && amount > 0
                    ? energy.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate)
                    : 0;
        }

        @Override
        public long extract(StorageResourceKey key, long amount, boolean simulate) {
            return key.equals(StorageResourceKey.neoforgeEnergy()) && amount > 0
                    ? energy.extractEnergy((int) Math.min(amount, Integer.MAX_VALUE), simulate)
                    : 0;
        }
    }
}
