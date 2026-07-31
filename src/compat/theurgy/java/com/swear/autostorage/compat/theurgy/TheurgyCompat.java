package com.swear.autostorage.compat.theurgy;

import com.klikli_dev.theurgy.content.recipe.CalcinationRecipe;
import com.klikli_dev.theurgy.content.recipe.DistillationRecipe;
import com.klikli_dev.theurgy.content.recipe.LiquefactionRecipe;
import com.klikli_dev.theurgy.registry.RecipeTypeRegistry;
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
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class TheurgyCompat {
    private static final String MOD_ID = "theurgy";

    private TheurgyCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Theurgy descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Theurgy family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Theurgy descriptors and families must share one namespace");
        }

        String namespace = machineDescriptors.getNamespace();
        ResourceLocation calcination = id(namespace, "theurgy_calcination_oven");
        ResourceLocation distillation = id(namespace, "theurgy_distiller");
        ResourceLocation liquefaction = id(namespace, "theurgy_liquefaction_cauldron");

        registerStation(
                machineDescriptors,
                calcination,
                "calcination_oven",
                "gui.auto_storage.station.theurgy_calcination");
        registerStation(
                machineDescriptors,
                distillation,
                "distiller",
                "gui.auto_storage.station.theurgy_distillation");
        registerStation(
                machineDescriptors,
                liquefaction,
                "liquefaction_cauldron",
                "gui.auto_storage.station.theurgy_liquefaction");

        recipeFamilies.register(calcination.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        CalcinationRecipe.class,
                        RecipeTypeRegistry.CALCINATION,
                        calcination,
                        TheurgyCompat::supportsCalcination,
                        TheurgyCompat::calcinationPlan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.getTime()),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(distillation.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        DistillationRecipe.class,
                        RecipeTypeRegistry.DISTILLATION,
                        distillation,
                        TheurgyCompat::supportsDistillation,
                        TheurgyCompat::distillationPlan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.getTime()),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(liquefaction.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        LiquefactionRecipe.class,
                        RecipeTypeRegistry.LIQUEFACTION,
                        liquefaction,
                        TheurgyCompat::supportsLiquefaction,
                        TheurgyCompat::liquefactionPlan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.getTime()),
                        RecipePresentationKind.CRAFTING));
    }

    private static void registerStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            ResourceLocation descriptorId,
            String itemPath,
            String labelKey
    ) {
        machineDescriptors.register(descriptorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        descriptorId,
                        Component.translatable(labelKey),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(itemPath)),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
    }

    private static boolean supportsCalcination(CalcinationRecipe recipe) {
        return exact(recipe.sizedIngredient())
                && !recipe.getResultItem(RegistryAccess.EMPTY).isEmpty()
                && recipe.getTime() > 0;
    }

    private static boolean supportsDistillation(DistillationRecipe recipe) {
        return exact(recipe.getIngredient())
                && !recipe.getResultItem(RegistryAccess.EMPTY).isEmpty()
                && recipe.getTime() > 0;
    }

    private static boolean supportsLiquefaction(LiquefactionRecipe recipe) {
        return exact(recipe.getIngredient())
                && exact(recipe.getSolvent())
                && !recipe.getResultItem(RegistryAccess.EMPTY).isEmpty()
                && recipe.getTime() > 0;
    }

    private static TypedRecipePlan calcinationPlan(
            CalcinationRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = recipe.getResultItem(registries).copy();
        return TypedRecipePlan.builder()
                .input(consumed(recipe.sizedIngredient(), registries))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(
                                output.copyWithCount(1), registries),
                        output.getCount()))
                .presentationOutput(output)
                .layout(1, 1, true)
                .build();
    }

    private static TypedRecipePlan distillationPlan(
            DistillationRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = recipe.getResultItem(registries).copy();
        return TypedRecipePlan.builder()
                .input(consumed(recipe.getIngredient(), registries))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(
                                output.copyWithCount(1), registries),
                        output.getCount()))
                .presentationOutput(output)
                .layout(1, 1, true)
                .build();
    }

    private static TypedRecipePlan liquefactionPlan(
            LiquefactionRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = recipe.getResultItem(registries).copy();
        return TypedRecipePlan.builder()
                .input(consumed(recipe.getIngredient(), registries))
                .input(consumed(recipe.getSolvent(), registries))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(
                                output.copyWithCount(1), registries),
                        output.getCount()))
                .presentationOutput(output)
                .layout(2, 1, true)
                .build();
    }

    private static TypedRecipeInput consumed(
            SizedIngredient ingredient,
            HolderLookup.Provider registries
    ) {
        return TypedRecipeInput.consumeAny(
                itemKeys(representatives(ingredient.ingredient()), registries),
                ingredient.count());
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
                .filter(stack -> !stack.isEmpty())
                .map(stack -> StorageResourceKey.fluid(
                        stack.copyWithAmount(1), registries))
                .distinct()
                .toList();
        return TypedRecipeInput.consumeAny(alternatives, ingredient.amount());
    }

    private static boolean exact(SizedIngredient ingredient) {
        return ingredient != null
                && ingredient.count() > 0
                && exact(ingredient.ingredient());
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
        ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Theurgy station item " + itemId);
        }
        return item;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
