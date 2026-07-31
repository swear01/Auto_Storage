package com.swear.autostorage.compat.integratedcrafting;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.RecipeFamily;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class IntegratedcraftingCompat {
    private IntegratedcraftingCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes
    ) {
        // Intentionally empty: Integrated Crafting is network automation over vanilla
        // recipe types plus an unsafe DeadBush special recipe. Zero families are accepted.
    }
}
