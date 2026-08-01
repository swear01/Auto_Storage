package com.swear.autostorage.compat.draconicevolution;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.RecipeFamily;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DraconicevolutionCompat {
    private DraconicevolutionCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes
    ) {
        // Intentionally empty: Fusion Crafting depends on live multiblock injectors,
        // injector-local energy charging, DEConfig craft/charge times, and generic
        // Ingredient/StackIngredient payloads. Vanilla crafting/smelting under the
        // draconicevolution namespace remains covered by Auto Storage built-ins.
    }
}
