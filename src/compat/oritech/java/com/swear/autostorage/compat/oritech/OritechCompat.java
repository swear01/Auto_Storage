package com.swear.autostorage.compat.oritech;

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
import dev.architectury.fluid.FluidStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.DeferredRegister;
import rearth.oritech.init.OritechConfig;
import rearth.oritech.init.recipes.OritechRecipe;
import rearth.oritech.init.recipes.RecipeContent;
import rearth.oritech.util.FluidIngredient;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OritechCompat {
    private static final ResourceLocation PULVERIZER_ITEM =
            ResourceLocation.fromNamespaceAndPath("oritech", "pulverizer_block");
    private static final int MAX_PLAN_INPUTS = 9;

    private OritechCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            DeferredRegister<TransformProvider> transformProviders
    ) {
        Objects.requireNonNull(transformProviders, "transformProviders");
        if (!transformProviders.getRegistryKey().equals(TransformProviderApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Oritech transform provider register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(transformProviders.getNamespace())) {
            throw new IllegalArgumentException(
                    "Oritech descriptors and transform providers must share one namespace");
        }
        ResourceLocation fuelGeneratorId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "oritech_fuel_generator");
        machineDescriptors.register(fuelGeneratorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        fuelGeneratorId,
                        Component.translatable(
                                "gui.auto_storage.station.oritech_fuel_generator"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "oritech", "fuel_generator_block"))),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
                com.swear.autostorage.ConversionScanner.register(FUEL_GENERATOR_PATTERN);
transformProviders.register(fuelGeneratorId.getPath(), () ->                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.station.oritech_fuel_generator"),
                        FUEL_GENERATOR_PATTERN::resolve));
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Oritech descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Oritech family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Oritech descriptors and families must share one namespace");
        }

        ResourceLocation descriptorId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "oritech_pulverizer");
        machineDescriptors.register(descriptorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        descriptorId,
                        Component.translatable("gui.auto_storage.station.oritech_pulverizer"),
                        () -> List.of(MachineVariant.derived(
                                new ItemStack(requiredItem(PULVERIZER_ITEM)),
                                () -> MachineWorkRate.of(1, 1))),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        recipeFamilies.register(descriptorId.getPath(), () ->
                RecipeFamilyFactories.dynamicDeterministicResources(
                        OritechRecipe.class,
                        () -> RecipeContent.PULVERIZER,
                        descriptorId,
                        OritechCompat::supports,
                        OritechCompat::plan,
                        recipe -> RecipeFamilyCost.stationWork(requiredWork(recipe)),
                        OritechCompat::energyPerTick,
                        RecipePresentationKind.CRAFTING));
    }

    private static boolean supports(OritechRecipe recipe) {
        if (recipe == null
                || recipe.getTime() <= 0
                || recipe.getInputs() == null
                || recipe.getInputs().isEmpty()
                || recipe.getInputs().size() + 1 > MAX_PLAN_INPUTS
                || recipe.getResults() == null
                || recipe.getResults().isEmpty()
                || !emptyFluid(recipe.getFluidInput())
                || !emptyFluidOutputs(recipe)) {
            return false;
        }
        for (Ingredient ingredient : recipe.getInputs()) {
            if (!exact(ingredient)) {
                return false;
            }
        }
        for (ItemStack result : recipe.getResults()) {
            if (result == null || result.isEmpty() || result.getCount() <= 0) {
                return false;
            }
        }
        try {
            requiredWork(recipe);
            return true;
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    private static TypedRecipePlan plan(
            OritechRecipe recipe,
            HolderLookup.Provider registries
    ) {
        long energy = requiredEnergy(recipe);
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        for (Ingredient ingredient : recipe.getInputs()) {
            builder.input(TypedRecipeInput.consumeAny(keys(ingredient, registries), 1));
        }
        if (energy > 0) {
            builder.input(TypedRecipeInput.consume(
                    StorageResourceKey.neoforgeEnergy(), energy));
        }

        LinkedHashMap<StorageResourceKey, Long> outputs = new LinkedHashMap<>();
        ItemStack presentation = recipe.getResults().getFirst().copy();
        for (ItemStack result : recipe.getResults()) {
            ItemStack stack = result.copy();
            outputs.merge(
                    StorageResourceKey.item(stack.copyWithCount(1), registries),
                    (long) stack.getCount(),
                    Math::addExact);
        }
        boolean primary = true;
        for (Map.Entry<StorageResourceKey, Long> output : outputs.entrySet()) {
            builder.output(primary
                    ? TypedRecipeOutput.primary(output.getKey(), output.getValue())
                    : TypedRecipeOutput.remainder(output.getKey(), output.getValue()));
            primary = false;
        }

        int inputCount = recipe.getInputs().size() + energyInputCount();
        int width = Math.min(3, inputCount);
        return builder
                .presentationOutput(presentation)
                .layout(width, (inputCount + width - 1) / width, true)
                .build();
    }

    private static long requiredWork(OritechRecipe recipe) {
        return recipe.getTime();
    }

    private static long requiredEnergy(OritechRecipe recipe) {
        return Math.multiplyExact((long) energyPerTick(), recipe.getTime());
    }

    private static int energyInputCount() {
        return energyPerTick() > 0 ? 1 : 0;
    }

    private static int energyPerTick() {
        return OritechConfig.processingMachines.pulverizerData.energyPerTick.get();
    }

    private static boolean emptyFluid(FluidIngredient fluidInput) {
        if (fluidInput == null || fluidInput.isEmpty() || fluidInput.amount() <= 0) {
            return true;
        }
        return !fluidInput.hasTag() && fluidInput.getFluid() == Fluids.EMPTY;
    }

    private static boolean emptyFluidOutputs(OritechRecipe recipe) {
        List<FluidStack> outputs = recipe.getFluidOutputs();
        if (outputs == null) return false;
        for (FluidStack stack : outputs) {
            if (stack == null || !stack.isEmpty()) return false;
        }
        return true;
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
    private static final FuelGeneratorPattern FUEL_GENERATOR_PATTERN = new FuelGeneratorPattern();
    private static final class FuelGeneratorPattern
            implements com.swear.autostorage.ConversionPattern {
        @Override
        public ResourceLocation patternId() {
            return ResourceLocation.fromNamespaceAndPath(
                    "oritech", "fuel_generator");
        }

        @Override
        public TransformProviderApi.Result resolve(ItemStack input) {        int burnTime = input.getBurnTime(RecipeType.SMELTING);
        if (burnTime <= 0) return null;
        int energyPerTick = OritechConfig.generators.fuelGeneratorData.energyPerTick.get();
        if (energyPerTick <= 0) return null;
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                Math.multiplyExact((long) burnTime, energyPerTick),
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorageApi.MOD_ID, "oritech_fuel_generator"),
                burnTime);
        }

        @Override
        public String revisionKey() {
            return String.valueOf(OritechConfig.generators.fuelGeneratorData.energyPerTick);
        }
    }
    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Oritech station item " + id);
        }
        return item;
    }
}
