package com.swear.autostorage.compat.immersiveengineering;

import blusunrize.immersiveengineering.api.crafting.ArcFurnaceRecipe;
import blusunrize.immersiveengineering.api.crafting.BottlingMachineRecipe;
import blusunrize.immersiveengineering.api.crafting.IERecipeTypes;
import blusunrize.immersiveengineering.api.crafting.MultiblockRecipe;
import blusunrize.immersiveengineering.api.crafting.TagOutput;
import com.swear.autostorage.EnergyCost;
import com.swear.autostorage.EnergyType;
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
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TypedRecipeInput;
import com.swear.autostorage.TypedRecipeOutput;
import com.swear.autostorage.TypedRecipePlan;
import com.swear.autostorage.api.AutoStorageApi;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Objects;

public final class ImmersiveengineeringCompat {
    private ImmersiveengineeringCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Immersive Engineering descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Immersive Engineering family register targets the wrong registry");
        }
        registerArcFurnace(machineDescriptors, recipeFamilies);
        registerBottling(machineDescriptors, recipeFamilies);
    }

    private static void registerArcFurnace(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        ResourceLocation id = descriptorId(machineDescriptors, "arc_furnace");
        registerStation(machineDescriptors, id, "arc_furnace");
        recipeFamilies.register(id.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        ArcFurnaceRecipe.class,
                        () -> IERecipeTypes.ARC_FURNACE.get(),
                        id,
                        ImmersiveengineeringCompat::supportsArcFurnace,
                        ImmersiveengineeringCompat::arcFurnacePlan,
                        recipe -> RecipeFamilyCost.stationWorkAndTool(
                                recipe.getBaseTime(),
                                ResourceLocation.fromNamespaceAndPath(
                                "auto_storage", "engineers_hammer"), 1),
                        RecipePresentationKind.CRAFTING));
    }

    private static void registerBottling(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        ResourceLocation id = descriptorId(machineDescriptors, "bottling_machine");
        registerStation(machineDescriptors, id, "bottling_machine");
        recipeFamilies.register(id.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        BottlingMachineRecipe.class,
                        () -> IERecipeTypes.BOTTLING_MACHINE.get(),
                        id,
                        ImmersiveengineeringCompat::supportsBottling,
                        ImmersiveengineeringCompat::bottlingPlan,
                        recipe -> RecipeFamilyCost.stationWorkAndTool(
                                recipe.getBaseTime(),
                                ResourceLocation.fromNamespaceAndPath(
                                "auto_storage", "engineers_hammer"), 1),
                        RecipePresentationKind.CRAFTING));
    }

    private static ResourceLocation descriptorId(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            String machine
    ) {
        return ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(),
                "immersiveengineering_" + machine);
    }

    private static void registerStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            ResourceLocation id,
            String machine
    ) {
        machineDescriptors.register(id.getPath(), () ->
                MachineDescriptor.installableVariants(
                        id,
                        Component.translatable(
                                "gui.auto_storage.station.immersiveengineering_"
                                        + machine),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "immersiveengineering", machine))),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
    }

    // ---------- Arc Furnace ----------

    private static boolean supportsArcFurnace(ArcFurnaceRecipe recipe) {
        try {
            boolean ok = recipe != null
                    && recipe.input != null
                    && !recipe.input.getBaseIngredient().isEmpty()
                    && recipe.output != null
                    && recipe.output.getLazyList().size() == 1
                    && !recipe.output.getLazyList().get(0).get().isEmpty()
                    && (recipe.secondaryOutputs == null || recipe.secondaryOutputs.isEmpty())
                    && recipe.slag != null && recipe.slag.get().isEmpty()
                    && recipe.getBaseTime() > 0;
            return ok;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static TypedRecipePlan arcFurnacePlan(
            ArcFurnaceRecipe recipe,
            HolderLookup.Provider registries
    ) {
        try {
            return multiItemPlan(
                    recipe,
                    recipe.input,
                    recipe.additives,
                    recipe.output.getLazyList().get(0),
                    recipe.getBaseEnergy(),
                    registries,
                    List.of(TypedRecipeInput.consume(
                            descriptorResourceKey(ResourceLocation.fromNamespaceAndPath(
                                    "auto_storage", "graphite_electrode")),
                            1)));
        } catch (RuntimeException failure) {
            throw failure;
        }
    }

    // ---------- Bottling Machine ----------

    private static boolean supportsBottling(BottlingMachineRecipe recipe) {
        return recipe != null
                && recipe.inputs != null && recipe.inputs.size() == 1
                && recipe.fluidInput != null
                && recipe.fluidInput.amount() > 0
                && recipe.fluidInput.getFluids().length > 0
                && recipe.output != null && recipe.output.getLazyList().size() == 1
                && !recipe.output.getLazyList().get(0).get().isEmpty()
                && recipe.getBaseTime() > 0;
    }

    private static TypedRecipePlan bottlingPlan(
            BottlingMachineRecipe recipe,
            HolderLookup.Provider registries
    ) {
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder()
                .input(TypedRecipeInput.consumeAny(
                        keys(recipe.inputs.get(0).getBaseIngredient(), registries),
                        recipe.inputs.get(0).getCount()))
                .output(primary(recipe.output.getLazyList().get(0), registries));
        addFluidInput(builder, recipe.fluidInput, registries);
        return finish(builder, recipe.output.getLazyList().get(0), recipe.getBaseEnergy());
    }

    // ---------- shared helpers ----------

    private static TypedRecipePlan multiItemPlan(
            Object recipe,
            blusunrize.immersiveengineering.api.crafting.IngredientWithSize primary,
            List<blusunrize.immersiveengineering.api.crafting.IngredientWithSize> additives,
            TagOutput output,
            int energy,
            HolderLookup.Provider registries
    ) {
        return multiItemPlan(
                recipe, primary, additives, output, energy, registries, List.of());
    }

    private static TypedRecipePlan multiItemPlan(
            Object recipe,
            blusunrize.immersiveengineering.api.crafting.IngredientWithSize primary,
            List<blusunrize.immersiveengineering.api.crafting.IngredientWithSize> additives,
            TagOutput output,
            int energy,
            HolderLookup.Provider registries,
            List<TypedRecipeInput> extraInputs
    ) {
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        if (primary != null) {
            builder.input(TypedRecipeInput.consumeAny(
                    keys(primary.getBaseIngredient(), registries),
                    primary.getCount()));
        }
        for (blusunrize.immersiveengineering.api.crafting.IngredientWithSize additive : additives) {
            builder.input(TypedRecipeInput.consumeAny(
                    keys(additive.getBaseIngredient(), registries),
                    additive.getCount()));
        }
        for (TypedRecipeInput extraInput : extraInputs) builder.input(extraInput);
        builder.output(primaryOutput(output, registries));
        return finish(builder, output, energy);
    }

    private static TypedRecipePlan finish(
            TypedRecipePlan.Builder builder,
            TagOutput output,
            int energy
    ) {
        ItemStack stack = output.get();
        if (energy > 0) {
            builder.input(TypedRecipeInput.consume(
                    StorageResourceKey.neoforgeEnergy(), energy));
        }
        return builder
                .presentationOutput(stack.copy())
                .layout(3, 3, true)
                .build();
    }

    private static void addFluidInput(
            TypedRecipePlan.Builder builder,
            SizedFluidIngredient fluidInput,
            HolderLookup.Provider registries
    ) {
        List<StorageResourceKey> alternatives = java.util.Arrays.stream(
                fluidInput.getFluids())
                .map(stack -> StorageResourceKey.fluid(
                        stack.copyWithAmount(1), registries))
                .toList();
        builder.input(TypedRecipeInput.consumeAny(alternatives, fluidInput.amount()));
    }

    private static TypedRecipeOutput primaryOutput(
            TagOutput output,
            HolderLookup.Provider registries
    ) {
        ItemStack stack = output.get().copy();
        return TypedRecipeOutput.primary(
                StorageResourceKey.item(stack.copyWithCount(1), registries),
                stack.getCount());
    }

    private static TypedRecipeOutput primary(
            TagOutput output,
            HolderLookup.Provider registries
    ) {
        return primaryOutput(output, registries);
    }

    private static List<StorageResourceKey> keys(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        return java.util.Arrays.stream(ingredient.getItems())
                .filter(stack -> !stack.isEmpty())
                .map(stack -> StorageResourceKey.item(
                        stack.copyWithCount(1), registries))
                .distinct()
                .toList();
    }

    private static StorageResourceKey descriptorResourceKey(ResourceLocation descriptorId) {
        CompoundTag variant = new CompoundTag();
        variant.putString("descriptorId", descriptorId.toString());
        return StorageResourceKey.of(
                StorageResourceKindApi.WORK_KIND,
                ResourceLocation.fromNamespaceAndPath("auto_storage", "descriptor"),
                variant);
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Immersive Engineering item " + id);
        }
        return item;
    }
}
