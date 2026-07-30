package com.swear.autostorage.compat.create;

import com.swear.autostorage.MachineCategory;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CreateCompat {
    private static final String MOD_ID = "create";

    private CreateCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        requireRegisters(machineDescriptors, recipeFamilies);
        String namespace = machineDescriptors.getNamespace();
        ResourceLocation milling = id(namespace, "create_milling");
        ResourceLocation crushing = id(namespace, "create_crushing");
        ResourceLocation cutting = id(namespace, "create_cutting");
        ResourceLocation filling = id(namespace, "create_filling");
        ResourceLocation emptying = id(namespace, "create_emptying");

        registerStation(machineDescriptors, milling, "millstone");
        registerStation(machineDescriptors, crushing, "crushing_wheel");
        registerStation(machineDescriptors, cutting, "mechanical_saw");
        registerStation(machineDescriptors, filling, "spout");
        registerStation(machineDescriptors, emptying, "item_drain");

        recipeFamilies.register(milling.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        MillingRecipe.class,
                        AllRecipeTypes.MILLING::getType,
                        milling,
                        CreateCompat::supportsTimedItems,
                        CreateCompat::timedItemPlan,
                        recipe -> RecipeFamilyCost.stationWork(
                                recipe.getProcessingDuration()),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(crushing.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        CrushingRecipe.class,
                        AllRecipeTypes.CRUSHING::getType,
                        crushing,
                        CreateCompat::supportsTimedItems,
                        CreateCompat::timedItemPlan,
                        recipe -> RecipeFamilyCost.stationWork(
                                recipe.getProcessingDuration()),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(cutting.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        CuttingRecipe.class,
                        AllRecipeTypes.CUTTING::getType,
                        cutting,
                        CreateCompat::supportsTimedItems,
                        CreateCompat::timedItemPlan,
                        recipe -> RecipeFamilyCost.stationWork(
                                recipe.getProcessingDuration()),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(filling.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        FillingRecipe.class,
                        AllRecipeTypes.FILLING::getType,
                        filling,
                        CreateCompat::supportsFilling,
                        CreateCompat::fillingPlan,
                        recipe -> RecipeFamilyCost.stationWork(
                                SpoutBlockEntity.FILLING_TIME),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(emptying.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        EmptyingRecipe.class,
                        AllRecipeTypes.EMPTYING::getType,
                        emptying,
                        CreateCompat::supportsEmptying,
                        CreateCompat::emptyingPlan,
                        recipe -> RecipeFamilyCost.stationWork(
                                ItemDrainBlockEntity.FILLING_TIME),
                        RecipePresentationKind.CRAFTING));
    }

    private static void requireRegisters(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Create descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Create family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Create descriptors and families must share one namespace");
        }
    }

    private static void registerStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            ResourceLocation descriptorId,
            String itemPath
    ) {
        machineDescriptors.register(descriptorId.getPath(), () -> {
            Item item = requiredItem(itemPath);
            return MachineDescriptor.installableVariants(
                    descriptorId,
                    new ItemStack(item).getHoverName(),
                    () -> List.of(MachineVariant.of(new ItemStack(item), MachineWorkRate.ONE)),
                    MachineCategory.PROCESS,
                    MachineDescriptorApi.MAX_INSTALLED_COUNT,
                    null);
        });
    }

    private static boolean supportsTimedItems(ProcessingRecipe<?, ?> recipe) {
        return recipe.getProcessingDuration() > 0
                && recipe.getRequiredHeat() == HeatCondition.NONE
                && recipe.getIngredients().size() == 1
                && exact(recipe.getIngredients().getFirst())
                && recipe.getFluidIngredients().isEmpty()
                && recipe.getFluidResults().isEmpty()
                && deterministic(recipe.getRollableResults());
    }

    private static TypedRecipePlan timedItemPlan(
            ProcessingRecipe<?, ?> recipe,
            HolderLookup.Provider registries
    ) {
        return itemPlan(
                consumedWithRemainder(recipe.getIngredients().getFirst(), registries),
                recipe.getRollableResults(),
                registries);
    }

    private static boolean supportsFilling(FillingRecipe recipe) {
        return recipe.getRequiredHeat() == HeatCondition.NONE
                && recipe.getProcessingDuration() == 0
                && recipe.getIngredients().size() == 1
                && exact(recipe.getIngredients().getFirst())
                && recipe.getFluidIngredients().size() == 1
                && exact(recipe.getFluidIngredients().getFirst())
                && recipe.getFluidResults().isEmpty()
                && recipe.getRollableResults().size() == 1
                && deterministic(recipe.getRollableResults());
    }

    private static TypedRecipePlan fillingPlan(
            FillingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = recipe.getRollableResults().getFirst().getStack();
        return TypedRecipePlan.builder()
                .input(consumed(recipe.getIngredients().getFirst(), registries))
                .input(consumed(recipe.getRequiredFluid(), registries))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output.copyWithCount(1), registries),
                        output.getCount()))
                .presentationOutput(output)
                .layout(2, 1, true)
                .build();
    }

    private static boolean supportsEmptying(EmptyingRecipe recipe) {
        return recipe.getRequiredHeat() == HeatCondition.NONE
                && recipe.getProcessingDuration() == 0
                && recipe.getIngredients().size() == 1
                && exact(recipe.getIngredients().getFirst())
                && recipe.getFluidIngredients().isEmpty()
                && recipe.getRollableResults().size() == 1
                && deterministic(recipe.getRollableResults())
                && recipe.getFluidResults().size() == 1
                && exact(recipe.getFluidResults().getFirst());
    }

    private static TypedRecipePlan emptyingPlan(
            EmptyingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack itemOutput = recipe.getRollableResults().getFirst().getStack();
        FluidStack fluidOutput = recipe.getResultingFluid();
        return TypedRecipePlan.builder()
                .input(consumed(recipe.getIngredients().getFirst(), registries))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(
                                itemOutput.copyWithCount(1), registries),
                        itemOutput.getCount()))
                .output(TypedRecipeOutput.remainder(
                        StorageResourceKey.fluid(
                                fluidOutput.copyWithAmount(1), registries),
                        fluidOutput.getAmount()))
                .presentationOutput(itemOutput)
                .layout(1, 1, true)
                .build();
    }

    private static TypedRecipePlan itemPlan(
            TypedRecipeInput input,
            List<ProcessingOutput> recipeOutputs,
            HolderLookup.Provider registries
    ) {
        LinkedHashMap<StorageResourceKey, Long> outputs = new LinkedHashMap<>();
        ItemStack presentation = recipeOutputs.getFirst().getStack();
        for (ProcessingOutput recipeOutput : recipeOutputs) {
            ItemStack output = recipeOutput.getStack();
            outputs.merge(
                    StorageResourceKey.item(output.copyWithCount(1), registries),
                    (long) output.getCount(),
                    Math::addExact);
        }
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder().input(input);
        boolean primary = true;
        for (Map.Entry<StorageResourceKey, Long> output : outputs.entrySet()) {
            builder.output(primary
                    ? TypedRecipeOutput.primary(output.getKey(), output.getValue())
                    : TypedRecipeOutput.remainder(output.getKey(), output.getValue()));
            primary = false;
        }
        return builder
                .presentationOutput(presentation)
                .layout(1, 1, true)
                .build();
    }

    private static TypedRecipeInput consumedWithRemainder(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        List<ItemStack> representatives = representatives(ingredient);
        List<StorageResourceKey> alternatives = itemKeys(representatives, registries);
        Map<StorageResourceKey, TypedRecipeOutput> remainders = new LinkedHashMap<>();
        for (int index = 0; index < representatives.size(); index++) {
            ItemStack stack = representatives.get(index);
            if (!stack.hasCraftingRemainingItem()) continue;
            ItemStack remainder = stack.getCraftingRemainingItem();
            if (remainder.isEmpty()) continue;
            remainders.put(
                    alternatives.get(index),
                    TypedRecipeOutput.remainder(
                            StorageResourceKey.item(remainder, registries),
                            remainder.getCount()));
        }
        return remainders.isEmpty()
                ? TypedRecipeInput.consumeAny(alternatives, 1)
                : TypedRecipeInput.consumeAnyWithRemainders(
                        alternatives, 1, remainders);
    }

    private static TypedRecipeInput consumed(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        return TypedRecipeInput.consumeAny(
                itemKeys(representatives(ingredient), registries), 1);
    }

    private static TypedRecipeInput consumed(
            SizedFluidIngredient ingredient,
            HolderLookup.Provider registries
    ) {
        List<StorageResourceKey> alternatives = Arrays.stream(ingredient.getFluids())
                .map(stack -> StorageResourceKey.fluid(
                        stack.copyWithAmount(1), registries))
                .distinct()
                .toList();
        return TypedRecipeInput.consumeAny(alternatives, ingredient.amount());
    }

    private static boolean deterministic(List<ProcessingOutput> outputs) {
        return !outputs.isEmpty()
                && outputs.stream().allMatch(output ->
                output.getChance() == 1.0F && !output.getStack().isEmpty());
    }

    private static boolean exact(Ingredient ingredient) {
        return ingredient != null
                && !ingredient.isEmpty()
                && ingredient.isSimple()
                && !representatives(ingredient).isEmpty();
    }

    private static boolean exact(SizedFluidIngredient ingredient) {
        return ingredient != null
                && ingredient.amount() > 0
                && Arrays.stream(ingredient.getFluids())
                .anyMatch(stack -> !stack.isEmpty());
    }

    private static boolean exact(FluidStack stack) {
        return stack != null && !stack.isEmpty() && stack.getAmount() > 0;
    }

    private static List<ItemStack> representatives(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .distinct()
                .toList();
    }

    private static List<StorageResourceKey> itemKeys(
            List<ItemStack> representatives,
            HolderLookup.Provider registries
    ) {
        return representatives.stream()
                .map(stack -> StorageResourceKey.item(stack, registries))
                .distinct()
                .toList();
    }

    private static Item requiredItem(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalStateException("Missing Create station item " + id);
        return item;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
