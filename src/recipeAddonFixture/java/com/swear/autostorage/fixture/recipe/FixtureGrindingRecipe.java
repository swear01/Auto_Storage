package com.swear.autostorage.fixture.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;

final class FixtureGrindingRecipe extends StonecutterRecipe {
    FixtureGrindingRecipe(Ingredient input, ItemStack output) {
        super("", input, output);
    }

    @Override
    public RecipeType<?> getType() {
        return FixtureMod.GRINDING_TYPE.get();
    }
}
