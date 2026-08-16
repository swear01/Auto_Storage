package com.swear.autostorage.compat.productivemetalworks;

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
import cy.jdkdigital.productivemetalworks.recipe.BlockCastingRecipe;
import cy.jdkdigital.productivemetalworks.recipe.ItemCastingRecipe;
import cy.jdkdigital.productivemetalworks.recipe.ItemMeltingRecipe;
import cy.jdkdigital.productivemetalworks.registry.MetalworksRegistrator;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProductivemetalworksCompat {
    public static final int CASTING_WORK_TICKS = 1000;

    private ProductivemetalworksCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Productive Metalworks descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Productive Metalworks family register targets the wrong registry");
        }
        registerMelting(machineDescriptors, recipeFamilies);
        registerCasting(machineDescriptors, recipeFamilies);
    }

    private static void registerMelting(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        ResourceLocation id = descriptorId(machineDescriptors, "foundry");
        registerFoundryStation(machineDescriptors, id);
        recipeFamilies.register(id.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        ItemMeltingRecipe.class,
                        () -> MetalworksRegistrator.ITEM_MELTING_TYPE.get(),
                        id,
                        ProductivemetalworksCompat::supportsMelting,
                        ProductivemetalworksCompat::meltingPlan,
                        ProductivemetalworksCompat::meltingCost,
                        RecipePresentationKind.CRAFTING));
    }

    private static void registerCasting(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        ResourceLocation id = descriptorId(machineDescriptors, "casting_table");
        machineDescriptors.register(id.getPath(), () ->
                MachineDescriptor.installableVariants(
                        id,
                        Component.translatable(
                                "gui.auto_storage.station.productivemetalworks_"
                                        + "casting_table"),
                        () -> List.of(
                                MachineVariant.of(
                                        new ItemStack(requiredItem(
                                                ResourceLocation.fromNamespaceAndPath(
                                                        "productivemetalworks",
                                                        "casting_table"))),
                                        MachineWorkRate.ONE),
                                MachineVariant.of(
                                        new ItemStack(requiredItem(
                                                ResourceLocation.fromNamespaceAndPath(
                                                        "productivemetalworks",
                                                        "casting_basin"))),
                                        MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        recipeFamilies.register(id.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        ItemCastingRecipe.class,
                        () -> MetalworksRegistrator.ITEM_CASTING_TYPE.get(),
                        id,
                        ProductivemetalworksCompat::supportsCasting,
                        ProductivemetalworksCompat::castingPlan,
                        recipe -> RecipeFamilyCost.stationWork(CASTING_WORK_TICKS),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(id.getPath() + "_basin", () ->
                RecipeFamilyFactories.deterministicResources(
                        BlockCastingRecipe.class,
                        () -> MetalworksRegistrator.BLOCK_CASTING_TYPE.get(),
                        id,
                        ProductivemetalworksCompat::supportsCasting,
                        ProductivemetalworksCompat::castingPlan,
                        recipe -> RecipeFamilyCost.stationWork(CASTING_WORK_TICKS),
                        RecipePresentationKind.CRAFTING));
    }

    private static ResourceLocation descriptorId(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            String machine
    ) {
        return ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(),
                "productivemetalworks_" + machine);
    }

    private static void registerFoundryStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            ResourceLocation id
    ) {
        machineDescriptors.register(id.getPath(), () ->
                MachineDescriptor.installableVariants(
                        id,
                        Component.translatable(
                                "gui.auto_storage.station.productivemetalworks_"
                                        + "foundry"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "productivemetalworks",
                                                "gray_foundry_controller"))),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
    }

    // ---------- Melting ----------

    private static boolean supportsMelting(ItemMeltingRecipe recipe) {
        return recipe != null
                && recipe.item != null
                && !recipe.item.isEmpty()
                && recipe.result != null
                && !recipe.result.isEmpty()
                && recipe.result.stream().allMatch(
                        stack -> stack != null && !stack.isEmpty());
    }

    private static TypedRecipePlan meltingPlan(
            ItemMeltingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder()
                .input(TypedRecipeInput.consumeAny(
                        keys(recipe.item, registries), 1));
        for (FluidStack stack : recipe.result) {
            builder.output(TypedRecipeOutput.primary(
                    StorageResourceKey.fluid(
                            stack.copyWithAmount(1), registries),
                    stack.getAmount()));
        }
        return builder
                .presentationOutput(new ItemStack(Items.BUCKET))
                .layout(3, 3, true)
                .build();
    }

    private static RecipeFamilyCost meltingCost(ItemMeltingRecipe recipe) {
        int work = 0;
        for (FluidStack stack : recipe.result) {
            work += stack.getAmount();
        }
        return RecipeFamilyCost.stationWork(Math.max(1, work));
    }

    // ---------- Casting ----------

    private static boolean supportsCasting(ItemCastingRecipe recipe) {
        return recipe != null
                && recipe.consumeCast
                && recipe.cast != null
                && !recipe.cast.isEmpty()
                && recipe.fluid != null
                && recipe.fluid.amount() > 0
                && recipe.result != null
                && !recipe.result.isEmpty();
    }

    private static TypedRecipePlan castingPlan(
            ItemCastingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = recipe.result.copy();
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder()
                .input(TypedRecipeInput.consumeAny(
                        keys(recipe.cast, registries), 1))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(
                                output.copyWithCount(1), registries),
                        output.getCount()));
        addFluidInput(builder, recipe.fluid, registries);
        return builder
                .presentationOutput(output)
                .layout(3, 3, true)
                .build();
    }

    // ---------- shared helpers ----------

    private static void addFluidInput(
            TypedRecipePlan.Builder builder,
            SizedFluidIngredient fluidInput,
            HolderLookup.Provider registries
    ) {
        for (var stack : fluidInput.getFluids()) {
            builder.input(TypedRecipeInput.consume(
                    StorageResourceKey.fluid(
                            stack.copyWithAmount(1), registries),
                    fluidInput.amount()));
            break;
        }
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
            throw new IllegalStateException(
                    "Missing Productive Metalworks item " + id);
        }
        return item;
    }
}
