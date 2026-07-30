package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

interface RecipeAdapter {
    ResourceLocation id();

    int priority();

    boolean supports(RecipeHolder<?> holder);

    RecipeCandidateIndex candidateIndex(RecipeHolder<?> holder);

    default RecipeCandidateIndex candidateIndex(RecipeHolder<?> holder, Level level) {
        return candidateIndex(holder);
    }

    RecipeAdapterMatch.Contract contract(RecipeHolder<?> holder);

    List<RecipeAdapterMatch.Contract> resolveVariants(
            RecipeHolder<?> holder,
            RecipeAdapterMatch.Contract baseContract,
            List<ItemStack> availableStacks,
            Level level
    );

    default boolean requiresAvailableStacksForVariants() {
        return true;
    }

    boolean matchesLookupOutput(
            RecipeHolder<?> holder,
            RecipeAdapterMatch.Contract variantContract,
            ItemStack requestedOutput,
            Level level
    );

    default Optional<RecipeFamilyKey> exactFamilyKey() {
        return Optional.empty();
    }

}
