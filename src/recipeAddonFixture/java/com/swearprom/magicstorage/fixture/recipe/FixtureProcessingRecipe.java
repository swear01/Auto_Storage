package com.swearprom.magicstorage.fixture.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;

final class FixtureProcessingRecipe extends StonecutterRecipe {
    FixtureProcessingRecipe(Ingredient input, ItemStack output) {
        super("", input, output);
    }

    @Override
    public RecipeType<?> getType() {
        return FixtureMod.PROCESSING_TYPE.get();
    }
}
