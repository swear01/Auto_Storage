package com.swear.autostorage.compat.actuallyadditions;

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
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.TypedRecipeInput;
import com.swear.autostorage.TypedRecipeOutput;
import com.swear.autostorage.TypedRecipePlan;
import de.ellpeck.actuallyadditions.mod.crafting.ActuallyRecipes;
import de.ellpeck.actuallyadditions.mod.crafting.CrushingRecipe;
import de.ellpeck.actuallyadditions.mod.crafting.FermentingRecipe;
import de.ellpeck.actuallyadditions.mod.crafting.PressingRecipe;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityCanolaPress;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityCrusher;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ActuallyadditionsCompat {
    private static final ResourceLocation CRUSHER_ITEM =
            ResourceLocation.fromNamespaceAndPath("actuallyadditions", "crusher");
    private static final ResourceLocation CANOLA_PRESS_ITEM =
            ResourceLocation.fromNamespaceAndPath("actuallyadditions", "canola_press");
    private static final ResourceLocation FERMENTING_BARREL_ITEM =
            ResourceLocation.fromNamespaceAndPath("actuallyadditions", "fermenting_barrel");

    private static final int CRUSHER_TIME = 100;
    private static final long CRUSHER_ENERGY =
            Math.multiplyExact((long) TileEntityCrusher.ENERGY_USE, CRUSHER_TIME);
    private static final long PRESSING_ENERGY = Math.multiplyExact(
            (long) TileEntityCanolaPress.ENERGY_USE, 30L);

    private ActuallyadditionsCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            DeferredRegister<TransformProvider> transformProviders
    ) {
        Objects.requireNonNull(transformProviders, "transformProviders");
        if (!transformProviders.getRegistryKey().equals(TransformProviderApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Actually Additions transform provider register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(transformProviders.getNamespace())) {
            throw new IllegalArgumentException(
                    "Actually Additions descriptors and transform providers must share one namespace");
        }
        ResourceLocation coalGeneratorId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "actuallyadditions_coal_generator");
        machineDescriptors.register(coalGeneratorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        coalGeneratorId,
                        Component.translatable(
                                "gui.auto_storage.station.actuallyadditions_coal_generator"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "actuallyadditions", "coal_generator"))),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        transformProviders.register(coalGeneratorId.getPath(), () ->
                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.station.actuallyadditions_coal_generator"),
                        ActuallyadditionsCompat::coalGeneratorTransform));
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Actually Additions descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Actually Additions family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Actually Additions descriptors and families must share one namespace");
        }

        String namespace = machineDescriptors.getNamespace();
        ResourceLocation crushing = ResourceLocation.fromNamespaceAndPath(
                namespace, "actuallyadditions_crushing");
        ResourceLocation pressing = ResourceLocation.fromNamespaceAndPath(
                namespace, "actuallyadditions_pressing");
        ResourceLocation fermenting = ResourceLocation.fromNamespaceAndPath(
                namespace, "actuallyadditions_fermenting");

        machineDescriptors.register(crushing.getPath(), () ->
                MachineDescriptor.installableVariants(
                        crushing,
                        Component.translatable("gui.auto_storage.station.actuallyadditions_crushing"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(CRUSHER_ITEM)),
                                MachineWorkRate.of(TileEntityCrusher.ENERGY_USE, 1))),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        machineDescriptors.register(pressing.getPath(), () ->
                MachineDescriptor.installableVariants(
                        pressing,
                        Component.translatable("gui.auto_storage.station.actuallyadditions_pressing"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(CANOLA_PRESS_ITEM)),
                                MachineWorkRate.of(TileEntityCanolaPress.ENERGY_USE, 1))),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        machineDescriptors.register(fermenting.getPath(), () ->
                MachineDescriptor.installableVariants(
                        fermenting,
                        Component.translatable(
                                "gui.auto_storage.station.actuallyadditions_fermenting"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(FERMENTING_BARREL_ITEM)),
                                MachineWorkRate.of(1, 1))),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));

        recipeFamilies.register(crushing.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        CrushingRecipe.class,
                        ActuallyRecipes.Types.CRUSHING,
                        crushing,
                        ActuallyadditionsCompat::supportsCrushing,
                        ActuallyadditionsCompat::crushingPlan,
                        recipe -> RecipeFamilyCost.stationWork(CRUSHER_ENERGY),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(pressing.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        PressingRecipe.class,
                        ActuallyRecipes.Types.PRESSING,
                        pressing,
                        ActuallyadditionsCompat::supportsPressing,
                        ActuallyadditionsCompat::pressingPlan,
                        recipe -> RecipeFamilyCost.stationWork(PRESSING_ENERGY),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(fermenting.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        FermentingRecipe.class,
                        ActuallyRecipes.Types.FERMENTING,
                        fermenting,
                        ActuallyadditionsCompat::supportsFermenting,
                        ActuallyadditionsCompat::fermentingPlan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.getTime()),
                        RecipePresentationKind.CRAFTING));
    }

    private static boolean supportsCrushing(CrushingRecipe recipe) {
        if (recipe == null
                || !exact(recipe.getInput())
                || !exactOutput(recipe.getOutputOne())
                || recipe.getFirstChance() != 1.0F) {
            return false;
        }
        ItemStack secondary = recipe.getOutputTwo();
        if (secondary == null || secondary.isEmpty()) {
            return true;
        }
        return exactOutput(secondary) && recipe.getSecondChance() == 1.0F;
    }

    private static TypedRecipePlan crushingPlan(
            CrushingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        if (!supportsCrushing(recipe)) {
            throw new IllegalStateException(
                    "Actually Additions Crushing recipe is outside the accepted contract");
        }
        ItemStack primary = recipe.getOutputOne().copy();
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder()
                .input(TypedRecipeInput.consumeAny(keys(recipe.getInput(), registries), 1))
                .input(TypedRecipeInput.consume(
                        StorageResourceKey.neoforgeEnergy(), CRUSHER_ENERGY))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(primary.copyWithCount(1), registries),
                        primary.getCount()));
        ItemStack secondary = recipe.getOutputTwo();
        if (secondary != null && !secondary.isEmpty()) {
            ItemStack copy = secondary.copy();
            builder.output(TypedRecipeOutput.remainder(
                    StorageResourceKey.item(copy.copyWithCount(1), registries),
                    copy.getCount()));
        }
        return builder
                .presentationOutput(primary)
                .layout(2, 1, true)
                .build();
    }

    private static boolean supportsPressing(PressingRecipe recipe) {
        return recipe != null
                && exact(recipe.getInput())
                && presentableFluid(recipe.getOutput());
    }

    private static TypedRecipePlan pressingPlan(
            PressingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        if (!supportsPressing(recipe)) {
            throw new IllegalStateException(
                    "Actually Additions Pressing recipe is outside the accepted contract");
        }
        FluidStack output = recipe.getOutput().copy();
        return TypedRecipePlan.builder()
                .input(TypedRecipeInput.consumeAny(keys(recipe.getInput(), registries), 1))
                .input(TypedRecipeInput.consume(
                        StorageResourceKey.neoforgeEnergy(), PRESSING_ENERGY))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.fluid(output.copyWithAmount(1), registries),
                        output.getAmount()))
                .presentationOutput(fluidPresentation(output.getFluid()))
                .layout(2, 1, true)
                .build();
    }

    private static boolean supportsFermenting(FermentingRecipe recipe) {
        return recipe != null
                && recipe.getTime() > 0
                && exactFluid(recipe.getInput())
                && presentableFluid(recipe.getOutput());
    }

    private static TypedRecipePlan fermentingPlan(
            FermentingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        if (!supportsFermenting(recipe)) {
            throw new IllegalStateException(
                    "Actually Additions Fermenting recipe is outside the accepted contract");
        }
        FluidStack input = recipe.getInput().copy();
        FluidStack output = recipe.getOutput().copy();
        return TypedRecipePlan.builder()
                .input(TypedRecipeInput.consume(
                        StorageResourceKey.fluid(input.copyWithAmount(1), registries),
                        input.getAmount()))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.fluid(output.copyWithAmount(1), registries),
                        output.getAmount()))
                .presentationOutput(fluidPresentation(output.getFluid()))
                .layout(1, 1, true)
                .build();
    }

    private static boolean exact(Ingredient ingredient) {
        return ingredient != null
                && !ingredient.isEmpty()
                && ingredient.isSimple()
                && Arrays.stream(ingredient.getItems()).anyMatch(stack -> !stack.isEmpty());
    }

    private static boolean exactOutput(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() > 0;
    }

    private static boolean exactFluid(FluidStack stack) {
        return stack != null && !stack.isEmpty() && stack.getAmount() > 0;
    }

    private static boolean presentableFluid(FluidStack stack) {
        return exactFluid(stack) && stack.getFluid().getBucket() != Items.AIR;
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

    private static ItemStack fluidPresentation(Fluid fluid) {
        ItemStack stack = new ItemStack(fluid.getBucket());
        if (stack.isEmpty()) {
            throw new IllegalStateException(
                    "Actually Additions fluid has no bucket presentation: "
                            + BuiltInRegistries.FLUID.getKey(fluid));
        }
        return stack;
    }

    private static TransformProviderApi.Result coalGeneratorTransform(ItemStack input) {
        int burnTime = input.getBurnTime(RecipeType.SMELTING);
        if (burnTime <= 0) return null;
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                Math.multiplyExact((long) burnTime, 20L),
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorageApi.MOD_ID, "actuallyadditions_coal_generator"),
                burnTime);
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Actually Additions station item " + id);
        }
        return item;
    }
}
