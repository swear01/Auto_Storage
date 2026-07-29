package com.swearprom.magicstorage.magic_storage.compat.farmersdelight;

import com.swearprom.magicstorage.magic_storage.EnergyCost;
import com.swearprom.magicstorage.magic_storage.EnergyType;
import com.swearprom.magicstorage.magic_storage.MachineDescriptor;
import com.swearprom.magicstorage.magic_storage.MachineDescriptorApi;
import com.swearprom.magicstorage.magic_storage.MachineEnergyTable;
import com.swearprom.magicstorage.magic_storage.MachineVariant;
import com.swearprom.magicstorage.magic_storage.MachineWorkRate;
import com.swearprom.magicstorage.magic_storage.RecipeFamily;
import com.swearprom.magicstorage.magic_storage.RecipeFamilyApi;
import com.swearprom.magicstorage.magic_storage.RecipeFamilyCost;
import com.swearprom.magicstorage.magic_storage.RecipeFamilyFactories;
import com.swearprom.magicstorage.magic_storage.RecipePresentationKind;
import com.swearprom.magicstorage.magic_storage.StorageResourceKey;
import com.swearprom.magicstorage.magic_storage.TypedRecipeInput;
import com.swearprom.magicstorage.magic_storage.TypedRecipeOutput;
import com.swearprom.magicstorage.magic_storage.TypedRecipePlan;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class FarmersDelightCookingPotCompat {
    public static final String REGISTRY_PATH = "farmers_delight_cooking_pot";

    private FarmersDelightCookingPotCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("Cooking Pot descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("Cooking Pot family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Cooking Pot descriptor and recipe family must share one namespace");
        }

        ResourceLocation descriptorId = descriptorId(machineDescriptors.getNamespace());
        machineDescriptors.register(REGISTRY_PATH, () -> MachineDescriptor.installableVariants(
                descriptorId,
                new ItemStack(ModItems.COOKING_POT.get()).getHoverName(),
                () -> List.of(MachineVariant.of(
                        new ItemStack(ModItems.COOKING_POT.get()),
                        MachineWorkRate.ONE)),
                MachineEnergyTable.Category.PROCESS,
                MachineDescriptorApi.MAX_INSTALLED_COUNT,
                null));
        recipeFamilies.register(REGISTRY_PATH, () -> RecipeFamilyFactories.deterministicResources(
                CookingPotRecipe.class,
                ModRecipeTypes.COOKING,
                descriptorId,
                FarmersDelightCookingPotCompat::plan,
                FarmersDelightCookingPotCompat::cost,
                RecipePresentationKind.COOKING));
    }

    public static ResourceLocation descriptorId(String namespace) {
        return ResourceLocation.fromNamespaceAndPath(
                Objects.requireNonNull(namespace, "namespace"), REGISTRY_PATH);
    }

    private static RecipeFamilyCost cost(CookingPotRecipe recipe) {
        int cookTime = recipe.getCookTime();
        return RecipeFamilyCost.stationWorkAndEnergy(
                cookTime,
                new EnergyCost(
                        EnergyType.SMELTING_ENERGY,
                        0,
                        EnergyType.FURNACE_FUEL,
                        cookTime));
    }

    private static TypedRecipePlan plan(
            CookingPotRecipe recipe,
            HolderLookup.Provider registries
    ) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(registries, "registries");
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty() || ingredients.size() > CookingPotRecipe.INPUT_SLOTS) {
            throw new IllegalArgumentException(
                    "Cooking Pot recipe requires one to six ingredients");
        }

        LinkedHashMap<List<StorageResourceKey>, InputGroup> inputs = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            List<ItemStack> representatives = representatives(ingredient);
            List<StorageResourceKey> alternatives = representatives.stream()
                    .map(stack -> StorageResourceKey.item(stack, registries))
                    .toList();
            LinkedHashMap<StorageResourceKey, TypedRecipeOutput> alternativeRemainders =
                    new LinkedHashMap<>();
            for (int index = 0; index < representatives.size(); index++) {
                ItemStack remainder = remainderFor(representatives.get(index));
                if (!remainder.isEmpty()) {
                    alternativeRemainders.put(
                            alternatives.get(index),
                            TypedRecipeOutput.remainder(
                                    StorageResourceKey.item(remainder, registries),
                                    remainder.getCount()));
                }
            }
            inputs.merge(
                    alternatives,
                    new InputGroup(1, alternativeRemainders),
                    InputGroup::merge);
        }

        ItemStack servingContainer = recipe.getOutputContainer();
        if (!servingContainer.isEmpty()) {
            StorageResourceKey containerKey = StorageResourceKey.item(
                    servingContainer.copyWithCount(1), registries);
            inputs.merge(
                    List.of(containerKey),
                    new InputGroup(1, java.util.Map.of()),
                    InputGroup::merge);
        }
        ItemStack output = recipe.getResultItem(registries).copy();
        if (output.isEmpty()) {
            throw new IllegalArgumentException("Cooking Pot recipe output cannot be empty");
        }
        StorageResourceKey outputKey = StorageResourceKey.item(output, registries);

        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        inputs.forEach((alternatives, group) -> builder.input(
                group.remainders().isEmpty()
                        ? TypedRecipeInput.consumeAny(alternatives, group.amount())
                        : TypedRecipeInput.consumeAnyWithRemainders(
                                alternatives, group.amount(), group.remainders())));
        builder.output(TypedRecipeOutput.primary(outputKey, output.getCount()));

        int inputCount = inputs.size();
        int width = Math.min(3, inputCount);
        int height = (inputCount + width - 1) / width;
        return builder
                .presentationOutput(output)
                .layout(width, height, true)
                .build();
    }

    private static List<ItemStack> representatives(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            throw new IllegalArgumentException("Cooking Pot ingredient cannot be empty");
        }
        LinkedHashMap<ItemStackKey, ItemStack> unique = new LinkedHashMap<>();
        Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .forEach(stack -> unique.putIfAbsent(new ItemStackKey(stack), stack));
        if (unique.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cooking Pot ingredient has no exact item representatives");
        }
        return List.copyOf(unique.values());
    }

    private static ItemStack remainderFor(ItemStack stack) {
        if (stack.hasCraftingRemainingItem()) return stack.getCraftingRemainingItem().copy();
        Item override = CookingPotBlockEntity.INGREDIENT_REMAINDER_OVERRIDES.get(stack.getItem());
        return override == null ? ItemStack.EMPTY : override.getDefaultInstance().copy();
    }

    private record ItemStackKey(ItemStack stack) {
        private ItemStackKey {
            stack = stack.copyWithCount(1);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ItemStackKey key
                    && ItemStack.isSameItemSameComponents(stack, key.stack);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack);
        }
    }

    private record InputGroup(
            long amount,
            java.util.Map<StorageResourceKey, TypedRecipeOutput> remainders
    ) {
        private InputGroup {
            if (amount <= 0) throw new IllegalArgumentException("Cooking Pot input amount must be positive");
            remainders = java.util.Map.copyOf(remainders);
        }

        private InputGroup merge(InputGroup other) {
            if (!remainders.equals(other.remainders)) {
                throw new IllegalArgumentException(
                        "Repeated Cooking Pot ingredients have incompatible remainders");
            }
            return new InputGroup(Math.addExact(amount, other.amount), remainders);
        }
    }
}
