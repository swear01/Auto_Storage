package com.swear.autostorage.compat.mysticalagriculture;

import com.blakebr0.mysticalagriculture.crafting.recipe.ReprocessorRecipe;
import com.blakebr0.mysticalagriculture.init.ModRecipeTypes;
import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineVariant;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.RecipeFamilyCost;
import com.swear.autostorage.RecipeFamilyFactories;
import com.swear.autostorage.RecipePresentationKind;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.TypedRecipeInput;
import com.swear.autostorage.TypedRecipeOutput;
import com.swear.autostorage.TypedRecipePlan;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class MysticalagricultureCompat {
    private static final ResourceLocation REPROCESSOR_ITEM =
            ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "seed_reprocessor");
    private static final long PROCESSING_STEPS = 200L;
    private static final long FE_PER_STEP = 20L;
    private static final long FE_COST = PROCESSING_STEPS * FE_PER_STEP;

    private MysticalagricultureCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Mystical Agriculture descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Mystical Agriculture family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Mystical Agriculture descriptors and families must share one namespace");
        }

        ResourceLocation descriptorId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "mysticalagriculture_reprocessor");
        machineDescriptors.register(descriptorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        descriptorId,
                        Component.translatable(
                                "gui.auto_storage.station.mysticalagriculture_reprocessor"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(REPROCESSOR_ITEM)),
                                MachineWorkRate.of(1, 1))),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        recipeFamilies.register(descriptorId.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        ReprocessorRecipe.class,
                        ModRecipeTypes.REPROCESSOR::get,
                        descriptorId,
                        MysticalagricultureCompat::supports,
                        MysticalagricultureCompat::plan,
                        recipe -> RecipeFamilyCost.stationWork(PROCESSING_STEPS),
                        RecipePresentationKind.CRAFTING));
    }

    private static boolean supports(ReprocessorRecipe recipe) {
        return exact(input(recipe))
                && !recipe.getResultItem(RegistryAccess.EMPTY).isEmpty();
    }

    private static TypedRecipePlan plan(
            ReprocessorRecipe recipe,
            HolderLookup.Provider registries
    ) {
        Ingredient ingredient = input(recipe);
        ItemStack output = recipe.getResultItem(registries).copy();
        if (output.isEmpty()) {
            throw new IllegalStateException("Mystical Agriculture Reprocessor result is empty");
        }
        int inputCount = 2;
        int width = Math.min(3, inputCount);
        return TypedRecipePlan.builder()
                .input(TypedRecipeInput.consumeAny(keys(ingredient, registries), 1))
                .input(TypedRecipeInput.consume(
                        StorageResourceKey.neoforgeEnergy(), FE_COST))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output.copyWithCount(1), registries),
                        output.getCount()))
                .presentationOutput(output)
                .layout(width, (inputCount + width - 1) / width, true)
                .build();
    }

    private static Ingredient input(ReprocessorRecipe recipe) {
        var ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.size() != 1) {
            return Ingredient.EMPTY;
        }
        return ingredients.getFirst();
    }

    private static boolean exact(Ingredient ingredient) {
        return ingredient != null
                && !ingredient.isEmpty()
                && ingredient.isSimple()
                && !keysWithoutRegistries(ingredient).isEmpty();
    }

    private static List<StorageResourceKey> keys(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> StorageResourceKey.item(
                        stack.copyWithCount(1), registries))
                .distinct()
                .toList();
    }

    private static List<ItemStack> keysWithoutRegistries(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .distinct()
                .toList();
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException(
                    "Missing Mystical Agriculture station item " + id);
        }
        return item;
    }
}
