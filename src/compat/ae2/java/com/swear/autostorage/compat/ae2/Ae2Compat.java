package com.swear.autostorage.compat.ae2;

import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.recipes.AERecipeTypes;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
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
import java.util.OptionalLong;

public final class Ae2Compat {
    private static final ResourceLocation INSCRIBER_ITEM =
            ResourceLocation.fromNamespaceAndPath("ae2", "inscriber");
    private static final long PROCESSING_STEPS = 200;
    private static final double AE_PER_STEP = 10.0D;

    private Ae2Compat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("AE2 descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("AE2 family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "AE2 descriptors and families must share one namespace");
        }

        ResourceLocation descriptorId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "ae2_inscriber");
        machineDescriptors.register(descriptorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        descriptorId,
                        Component.translatable("gui.auto_storage.station.ae2_inscriber"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(INSCRIBER_ITEM)),
                                MachineWorkRate.of(2, 1))),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        recipeFamilies.register(descriptorId.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        InscriberRecipe.class,
                        () -> AERecipeTypes.INSCRIBER,
                        descriptorId,
                        Ae2Compat::supports,
                        Ae2Compat::plan,
                        recipe -> RecipeFamilyCost.stationWork(PROCESSING_STEPS),
                        RecipePresentationKind.CRAFTING));
    }

    private static boolean supports(InscriberRecipe recipe) {
        return exact(recipe.getMiddleInput())
                && optionalExact(recipe.getTopOptional())
                && optionalExact(recipe.getBottomOptional())
                && !recipe.getResultItem().isEmpty()
                && requiredFe().isPresent();
    }

    private static TypedRecipePlan plan(
            InscriberRecipe recipe,
            HolderLookup.Provider registries
    ) {
        OptionalLong energy = requiredFe();
        if (energy.isEmpty()) {
            throw new IllegalStateException(
                    "AE2 Inscriber power cost cannot be represented as exact FE");
        }
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        addOptional(
                builder,
                recipe.getTopOptional(),
                recipe.getProcessType(),
                registries);
        builder.input(consumed(recipe.getMiddleInput(), registries));
        addOptional(
                builder,
                recipe.getBottomOptional(),
                recipe.getProcessType(),
                registries);
        builder.input(TypedRecipeInput.consume(
                StorageResourceKey.neoforgeEnergy(), energy.getAsLong()));

        ItemStack output = recipe.getResultItem().copy();
        int inputCount = 2
                + (recipe.getTopOptional().isEmpty() ? 0 : 1)
                + (recipe.getBottomOptional().isEmpty() ? 0 : 1);
        int width = Math.min(3, inputCount);
        return builder
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(
                                output.copyWithCount(1), registries),
                        output.getCount()))
                .presentationOutput(output)
                .layout(width, (inputCount + width - 1) / width, true)
                .build();
    }

    private static void addOptional(
            TypedRecipePlan.Builder builder,
            Ingredient ingredient,
            InscriberProcessType processType,
            HolderLookup.Provider registries
    ) {
        if (ingredient.isEmpty()) return;
        List<StorageResourceKey> alternatives = keys(ingredient, registries);
        builder.input(processType == InscriberProcessType.PRESS
                ? TypedRecipeInput.consumeAny(alternatives, 1)
                : TypedRecipeInput.catalystAny(alternatives, 1));
    }

    private static TypedRecipeInput consumed(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        return TypedRecipeInput.consumeAny(keys(ingredient, registries), 1);
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

    private static boolean exact(Ingredient ingredient) {
        return ingredient != null
                && !ingredient.isEmpty()
                && ingredient.isSimple()
                && !keysWithoutRegistries(ingredient).isEmpty();
    }

    private static boolean optionalExact(Ingredient ingredient) {
        return ingredient != null && (ingredient.isEmpty() || exact(ingredient));
    }

    private static List<ItemStack> keysWithoutRegistries(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .distinct()
                .toList();
    }

    private static OptionalLong requiredFe() {
        double configuredAe = PowerMultiplier.CONFIG.multiply(
                Math.multiplyExact(PROCESSING_STEPS, (long) AE_PER_STEP));
        double converted = PowerUnit.AE.convertTo(PowerUnit.FE, configuredAe);
        if (!Double.isFinite(converted)
                || converted <= 0
                || converted > Long.MAX_VALUE
                || converted != Math.rint(converted)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of((long) converted);
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing AE2 station item " + id);
        }
        return item;
    }
}
