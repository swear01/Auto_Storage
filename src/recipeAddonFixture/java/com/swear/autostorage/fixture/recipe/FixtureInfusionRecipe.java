package com.swear.autostorage.fixture.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;

final class FixtureInfusionRecipe extends StonecutterRecipe {
    FixtureInfusionRecipe(Ingredient input, ItemStack output) {
        super("", input, output);
    }

    @Override
    public RecipeType<?> getType() {
        return FixtureMod.INFUSION_TYPE.get();
    }
}
