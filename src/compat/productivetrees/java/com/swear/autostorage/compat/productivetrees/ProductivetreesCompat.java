package com.swear.autostorage.compat.productivetrees;

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
import cy.jdkdigital.productivetrees.recipe.SawmillRecipe;
import cy.jdkdigital.productivetrees.registry.TreeRegistrator;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProductivetreesCompat {
    public static final int SAWMILL_WORK_TICKS = 20;

    private ProductivetreesCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Productive Trees descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Productive Trees family register targets the wrong registry");
        }
        registerSawmill(machineDescriptors, recipeFamilies);
    }

    private static void registerSawmill(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        ResourceLocation id = descriptorId(machineDescriptors, "sawmill");
        registerStation(machineDescriptors, id, "sawmill");
        recipeFamilies.register(id.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        SawmillRecipe.class,
                        () -> TreeRegistrator.SAW_MILLLING_TYPE.get(),
                        id,
                        ProductivetreesCompat::supportsSawmill,
                        ProductivetreesCompat::sawmillPlan,
                        recipe -> RecipeFamilyCost.stationWork(SAWMILL_WORK_TICKS),
                        RecipePresentationKind.CRAFTING));
    }

    private static ResourceLocation descriptorId(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            String machine
    ) {
        return ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(),
                "productivetrees_" + machine);
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
                                "gui.auto_storage.station.productivetrees_"
                                        + machine),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "productivetrees", machine))),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
    }

    // ---------- Sawmill ----------

    private static boolean supportsSawmill(SawmillRecipe recipe) {
        return recipe != null
                && recipe.input() != null
                && !recipe.input().isEmpty()
                && recipe.output() != null
                && !recipe.output().isEmpty();
    }

    private static TypedRecipePlan sawmillPlan(
            SawmillRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = recipe.output().copy();
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder()
                .input(TypedRecipeInput.consumeAny(
                        keys(recipe.input(), registries), 1))
                .output(primary(output, registries));
        List<ItemStack> extra = new ArrayList<>(2);
        if (recipe.secondary() != null && !recipe.secondary().isEmpty()) {
            extra.add(recipe.secondary().copy());
        }
        if (recipe.tertiary() != null && !recipe.tertiary().isEmpty()) {
            extra.add(recipe.tertiary().copy());
        }
        for (ItemStack stack : extra) {
            builder.output(primary(stack, registries));
        }
        return builder
                .presentationOutput(output)
                .layout(3, 3, true)
                .build();
    }

    // ---------- shared helpers ----------

    private static TypedRecipeOutput primary(
            ItemStack stack,
            HolderLookup.Provider registries
    ) {
        return TypedRecipeOutput.primary(
                StorageResourceKey.item(stack.copyWithCount(1), registries),
                stack.getCount());
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

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Productive Trees item " + id);
        }
        return item;
    }
}
