package com.swear.autostorage.compat.createenchantmentindustry;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.RecipeFamily;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CreateEnchantmentIndustryCompat {
    private CreateEnchantmentIndustryCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes
    ) {
        // Intentionally empty: Create Enchantment Industry has no accepted recipe family.
        // Mechanical Grindstone duration depends on live kinetics and tank free space, and also
        // disenchants enchanted items outside GrindingRecipe JSON. Printer execution is owned by
        // live template filter behaviours with enchantment NBT and config multipliers. Blaze
        // Enchanter/Forger are not RecipeType families and use RandomSource. Optional Apothic
        // Infuser/Salvaging require live machine or fan/world state. Vanilla crafting/smithing and
        // Create filling/emptying/cutting JSON under the mod namespace remain covered by built-ins
        // or the Create module.
    }
}
