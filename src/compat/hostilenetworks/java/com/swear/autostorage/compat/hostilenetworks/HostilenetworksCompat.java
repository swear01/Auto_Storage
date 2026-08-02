package com.swear.autostorage.compat.hostilenetworks;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.RecipeFamily;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HostilenetworksCompat {
    private HostilenetworksCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes
    ) {
        // Intentionally empty: Hostile Neural Networks has no accepted recipe family.
        // Simulation Chamber prediction rolls use RandomSource; Loot Fabricator depends on
        // live FabSelection block-entity state. Vanilla crafting under the mod namespace
        // remains covered by Auto Storage built-ins.
    }
}
