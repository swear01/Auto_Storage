package com.swearprom.magicstorage.fixture.mekanism;

import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class FixtureChemicalBlockStorage {
    private static final Map<Level, Map<BlockPos, IChemicalHandler>> HANDLERS =
            new WeakHashMap<>();

    private FixtureChemicalBlockStorage() {
    }

    static synchronized IChemicalHandler handler(Level level, BlockPos pos) {
        return HANDLERS.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(pos.immutable(), ignored ->
                        (IChemicalHandler) BasicChemicalTank.createAllValid(10_000, () -> {}));
    }
}
