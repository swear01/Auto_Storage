package com.swearprom.magicstorage.fixture.recipe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class FixtureNativeBlockStorage {
    private static final Map<Level, Map<BlockPos, FluidTank>> FLUIDS = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, EnergyStorage>> ENERGY = new WeakHashMap<>();

    private FixtureNativeBlockStorage() {
    }

    static synchronized FluidTank fluid(Level level, BlockPos pos) {
        return FLUIDS.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(pos.immutable(), ignored -> new FluidTank(10_000));
    }

    static synchronized EnergyStorage energy(Level level, BlockPos pos) {
        return ENERGY.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(pos.immutable(), ignored -> new EnergyStorage(10_000));
    }
}
