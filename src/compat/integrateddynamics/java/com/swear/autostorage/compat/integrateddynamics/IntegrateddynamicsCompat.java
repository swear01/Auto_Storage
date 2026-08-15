package com.swear.autostorage.compat.integrateddynamics;

import com.mojang.datafixers.util.Either;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.cyclops.cyclopscore.recipe.ItemStackFromIngredient;
import org.cyclops.integrateddynamics.RegistryEntries;
import org.cyclops.integrateddynamics.blockentity.BlockEntityCoalGeneratorConfig;
import org.cyclops.integrateddynamics.block.BlockMechanicalDryingBasinConfig;
import org.cyclops.integrateddynamics.block.BlockMechanicalSqueezerConfig;
import org.cyclops.integrateddynamics.core.recipe.type.RecipeDryingBasin;
import org.cyclops.integrateddynamics.core.recipe.type.RecipeMechanicalDryingBasin;
import org.cyclops.integrateddynamics.core.recipe.type.RecipeMechanicalSqueezer;
import org.cyclops.integrateddynamics.core.recipe.type.RecipeSqueezer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class IntegrateddynamicsCompat {
    private static final String MOD_ID = "integrateddynamics";

    private IntegrateddynamicsCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            DeferredRegister<TransformProvider> transformProviders
    ) {
        requireRegisters(machineDescriptors, recipeFamilies, transformProviders);
        String namespace = machineDescriptors.getNamespace();
        ResourceLocation coalGeneratorId = id(
                namespace, "integrateddynamics_coal_generator");
        registerStation(
                machineDescriptors,
                coalGeneratorId,
                "coal_generator",
                Component.translatable(
                        "gui.auto_storage.station.integrateddynamics_coal_generator"));
                com.swear.autostorage.ConversionScanner.register(COAL_GENERATOR_PATTERN);
transformProviders.register(coalGeneratorId.getPath(), () ->                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.station.integrateddynamics_coal_generator"),
                        COAL_GENERATOR_PATTERN::resolve));
        ResourceLocation dryingBasin = id(namespace, "integrateddynamics_drying_basin");
        ResourceLocation mechanicalDryingBasin = id(
                namespace, "integrateddynamics_mechanical_drying_basin");
        ResourceLocation mechanicalSqueezer = id(
                namespace, "integrateddynamics_mechanical_squeezer");

        registerStation(
                machineDescriptors,
                dryingBasin,
                "drying_basin",
                Component.translatable(
                        "gui.auto_storage.station.integrateddynamics_drying_basin"));
        registerStation(
                machineDescriptors,
                mechanicalDryingBasin,
                "mechanical_drying_basin",
                Component.translatable(
                        "gui.auto_storage.station.integrateddynamics_mechanical_drying_basin"));
        registerStation(
                machineDescriptors,
                mechanicalSqueezer,
                "mechanical_squeezer",
                Component.translatable(
                        "gui.auto_storage.station.integrateddynamics_mechanical_squeezer"));

        recipeFamilies.register(dryingBasin.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        RecipeDryingBasin.class,
                        RegistryEntries.RECIPETYPE_DRYING_BASIN,
                        dryingBasin,
                        IntegrateddynamicsCompat::supportsDryingBasin,
                        IntegrateddynamicsCompat::dryingBasinPlan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.getDuration()),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(mechanicalDryingBasin.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        RecipeMechanicalDryingBasin.class,
                        RegistryEntries.RECIPETYPE_MECHANICAL_DRYING_BASIN,
                        mechanicalDryingBasin,
                        IntegrateddynamicsCompat::supportsMechanicalDryingBasin,
                        IntegrateddynamicsCompat::mechanicalDryingBasinPlan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.getDuration()),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register(mechanicalSqueezer.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        RecipeMechanicalSqueezer.class,
                        RegistryEntries.RECIPETYPE_MECHANICAL_SQUEEZER,
                        mechanicalSqueezer,
                        IntegrateddynamicsCompat::supportsMechanicalSqueezer,
                        IntegrateddynamicsCompat::mechanicalSqueezerPlan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.getDuration()),
                        RecipePresentationKind.CRAFTING));
    }

    private static void requireRegisters(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            DeferredRegister<TransformProvider> transformProviders
    ) {
        Objects.requireNonNull(transformProviders, "transformProviders");
        if (!transformProviders.getRegistryKey().equals(TransformProviderApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Integrated Dynamics transform provider register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(transformProviders.getNamespace())) {
            throw new IllegalArgumentException(
                    "Integrated Dynamics descriptors and transform providers must share one namespace");
        }
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Integrated Dynamics descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Integrated Dynamics family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Integrated Dynamics descriptors and families must share one namespace");
        }
    }

    private static void registerStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            ResourceLocation descriptorId,
            String itemPath,
            Component label
    ) {
        machineDescriptors.register(descriptorId.getPath(), () -> {
            Item item = requiredItem(itemPath);
            return MachineDescriptor.installableVariants(
                    descriptorId,
                    label,
                    () -> List.of(MachineVariant.of(new ItemStack(item), MachineWorkRate.ONE)),
                    MachineCategory.PROCESS,
                    MachineDescriptorApi.MAX_INSTALLED_COUNT,
                    null);
        });
    }

    private static boolean supportsDryingBasin(RecipeDryingBasin recipe) {
        return recipe.getDuration() > 0
                && hasDryingInputs(recipe)
                && exactDryingOutput(recipe)
                && energyCost(0, recipe.getDuration()).isPresent();
    }

    private static boolean supportsMechanicalDryingBasin(RecipeMechanicalDryingBasin recipe) {
        return recipe.getDuration() > 0
                && hasDryingInputs(recipe)
                && exactDryingOutput(recipe)
                && energyCost(
                        BlockMechanicalDryingBasinConfig.consumptionRate,
                        recipe.getDuration()).isPresent();
    }

    private static boolean supportsMechanicalSqueezer(RecipeMechanicalSqueezer recipe) {
        return recipe.getDuration() > 0
                && exact(recipe.getInputIngredient())
                && deterministicSqueezerOutputs(recipe)
                && energyCost(
                        BlockMechanicalSqueezerConfig.consumptionRate,
                        recipe.getDuration()).isPresent();
    }

    private static TypedRecipePlan dryingBasinPlan(
            RecipeDryingBasin recipe,
            HolderLookup.Provider registries
    ) {
        return dryingPlan(recipe, 0, registries);
    }

    private static TypedRecipePlan mechanicalDryingBasinPlan(
            RecipeMechanicalDryingBasin recipe,
            HolderLookup.Provider registries
    ) {
        return dryingPlan(
                recipe,
                BlockMechanicalDryingBasinConfig.consumptionRate,
                registries);
    }

    private static TypedRecipePlan dryingPlan(
            RecipeDryingBasin recipe,
            int energyPerTick,
            HolderLookup.Provider registries
    ) {
        long energy = energyCost(energyPerTick, recipe.getDuration()).orElseThrow();
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        recipe.getInputIngredient().filter(IntegrateddynamicsCompat::exact).ifPresent(ingredient ->
                builder.input(consumed(ingredient, registries)));
        recipe.getInputFluid().filter(IntegrateddynamicsCompat::exact).ifPresent(fluid ->
                builder.input(TypedRecipeInput.consume(
                        StorageResourceKey.fluid(fluid.copyWithAmount(1), registries),
                        fluid.getAmount())));
        if (energy > 0) {
            builder.input(TypedRecipeInput.consume(
                    StorageResourceKey.neoforgeEnergy(), energy));
        }
        ItemStack itemOutput = dryingItemOutput(recipe).orElse(ItemStack.EMPTY);
        Optional<FluidStack> fluidOutput = recipe.getOutputFluid().filter(IntegrateddynamicsCompat::exact);
        if (!itemOutput.isEmpty()) {
            builder.output(TypedRecipeOutput.primary(
                    StorageResourceKey.item(itemOutput.copyWithCount(1), registries),
                    itemOutput.getCount()));
            fluidOutput.ifPresent(fluid -> builder.output(TypedRecipeOutput.remainder(
                    StorageResourceKey.fluid(fluid.copyWithAmount(1), registries),
                    fluid.getAmount())));
            return builder
                    .presentationOutput(itemOutput.copy())
                    .layout(Math.min(3, Math.max(1, builderInputCount(recipe, energy))), 1, true)
                    .build();
        }
        FluidStack fluid = fluidOutput.orElseThrow();
        return builder
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.fluid(fluid.copyWithAmount(1), registries),
                        fluid.getAmount()))
                .presentationOutput(new ItemStack(Items.BUCKET))
                .layout(Math.min(3, Math.max(1, builderInputCount(recipe, energy))), 1, true)
                .build();
    }

    private static TypedRecipePlan mechanicalSqueezerPlan(
            RecipeMechanicalSqueezer recipe,
            HolderLookup.Provider registries
    ) {
        long energy = energyCost(
                BlockMechanicalSqueezerConfig.consumptionRate,
                recipe.getDuration()).orElseThrow();
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder()
                .input(consumed(recipe.getInputIngredient(), registries));
        if (energy > 0) {
            builder.input(TypedRecipeInput.consume(StorageResourceKey.neoforgeEnergy(), energy));
        }
        List<ItemStack> itemOutputs = guaranteedSqueezerItems(recipe);
        Optional<FluidStack> fluidOutput = recipe.getOutputFluid().filter(IntegrateddynamicsCompat::exact);
        ItemStack presentation = ItemStack.EMPTY;
        boolean primaryAssigned = false;
        Map<StorageResourceKey, Long> mergedItems = new LinkedHashMap<>();
        for (ItemStack output : itemOutputs) {
            StorageResourceKey key = StorageResourceKey.item(
                    output.copyWithCount(1), registries);
            mergedItems.merge(key, (long) output.getCount(), Math::addExact);
            if (presentation.isEmpty()) {
                presentation = output.copy();
            }
        }
        for (Map.Entry<StorageResourceKey, Long> entry : mergedItems.entrySet()) {
            builder.output(primaryAssigned
                    ? TypedRecipeOutput.remainder(entry.getKey(), entry.getValue())
                    : TypedRecipeOutput.primary(entry.getKey(), entry.getValue()));
            primaryAssigned = true;
        }
        if (fluidOutput.isPresent()) {
            FluidStack fluid = fluidOutput.get();
            StorageResourceKey key = StorageResourceKey.fluid(
                    fluid.copyWithAmount(1), registries);
            builder.output(primaryAssigned
                    ? TypedRecipeOutput.remainder(key, fluid.getAmount())
                    : TypedRecipeOutput.primary(key, fluid.getAmount()));
            primaryAssigned = true;
            if (presentation.isEmpty()) {
                presentation = new ItemStack(Items.BUCKET);
            }
        }
        if (!primaryAssigned || presentation.isEmpty()) {
            throw new IllegalStateException("Mechanical Squeezer plan has no outputs");
        }
        return builder
                .presentationOutput(presentation)
                .layout(energy > 0 ? 2 : 1, 1, true)
                .build();
    }

    private static int builderInputCount(RecipeDryingBasin recipe, long energy) {
        int count = 0;
        if (recipe.getInputIngredient().filter(IntegrateddynamicsCompat::exact).isPresent()) count++;
        if (recipe.getInputFluid().filter(IntegrateddynamicsCompat::exact).isPresent()) count++;
        if (energy > 0) count++;
        return count;
    }

    private static boolean hasDryingInputs(RecipeDryingBasin recipe) {
        Optional<Ingredient> item = recipe.getInputIngredient();
        Optional<FluidStack> fluid = recipe.getInputFluid();
        if (item.isPresent() && !exact(item.get())) {
            return false;
        }
        if (fluid.isPresent() && !exact(fluid.get())) {
            return false;
        }
        return item.isPresent() || fluid.isPresent();
    }

    private static boolean exactDryingOutput(RecipeDryingBasin recipe) {
        Optional<Either<ItemStack, ItemStackFromIngredient>> declaredItem =
                recipe.getOutputItem();
        Optional<ItemStack> item = dryingItemOutput(recipe);
        if (declaredItem.isPresent() && item.isEmpty()) {
            return false;
        }
        Optional<FluidStack> fluid = recipe.getOutputFluid().filter(IntegrateddynamicsCompat::exact);
        return item.isPresent() || fluid.isPresent();
    }

    private static Optional<ItemStack> dryingItemOutput(RecipeDryingBasin recipe) {
        Optional<Either<ItemStack, ItemStackFromIngredient>> output = recipe.getOutputItem();
        if (output.isEmpty()) {
            return Optional.empty();
        }
        return output.flatMap(either -> either.left())
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy);
    }

    private static boolean deterministicSqueezerOutputs(RecipeMechanicalSqueezer recipe) {
        List<ItemStack> items = guaranteedSqueezerItems(recipe);
        Optional<FluidStack> fluid = recipe.getOutputFluid().filter(IntegrateddynamicsCompat::exact);
        if (items.isEmpty() && fluid.isEmpty()) {
            return false;
        }
        for (RecipeSqueezer.IngredientChance chance : recipe.getOutputItems()) {
            if (chance.getChance() != 1.0F) {
                return false;
            }
            if (!exactSqueezerItem(chance)) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> guaranteedSqueezerItems(RecipeMechanicalSqueezer recipe) {
        List<ItemStack> outputs = new ArrayList<>();
        for (RecipeSqueezer.IngredientChance chance : recipe.getOutputItems()) {
            if (chance.getChance() != 1.0F) {
                continue;
            }
            ItemStack stack = exactSqueezerStack(chance);
            if (stack != null && !stack.isEmpty()) {
                outputs.add(stack.copy());
            }
        }
        return outputs;
    }

    private static boolean exactSqueezerItem(RecipeSqueezer.IngredientChance chance) {
        return exactSqueezerStack(chance) != null;
    }

    private static ItemStack exactSqueezerStack(RecipeSqueezer.IngredientChance chance) {
        return chance.getIngredient().left().filter(stack -> !stack.isEmpty()).orElse(null);
    }

    private static OptionalLong energyCost(int energyPerTick, int duration) {
        if (duration <= 0) {
            return OptionalLong.empty();
        }
        if (energyPerTick < 0) {
            return OptionalLong.empty();
        }
        if (energyPerTick == 0) {
            return OptionalLong.of(0L);
        }
        try {
            long total = Math.multiplyExact((long) energyPerTick, (long) duration);
            return total > 0 ? OptionalLong.of(total) : OptionalLong.empty();
        } catch (ArithmeticException exception) {
            return OptionalLong.empty();
        }
    }

    private static TypedRecipeInput consumed(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        return TypedRecipeInput.consumeAny(
                itemKeys(representatives(ingredient), registries), 1);
    }

    private static boolean exact(Ingredient ingredient) {
        return ingredient != null
                && !ingredient.isEmpty()
                && ingredient.isSimple()
                && !representatives(ingredient).isEmpty();
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
    private static final CoalGeneratorPattern COAL_GENERATOR_PATTERN = new CoalGeneratorPattern();
    private static final class CoalGeneratorPattern
            implements com.swear.autostorage.ConversionPattern {
        @Override
        public ResourceLocation patternId() {
            return ResourceLocation.fromNamespaceAndPath(
                    "integrateddynamics", "coal_generator");
        }

        @Override
        public TransformProviderApi.Result resolve(ItemStack input) {        int burnTime = input.getBurnTime(RecipeType.SMELTING);
        int energyPerTick = BlockEntityCoalGeneratorConfig.energyPerTick;
        if (burnTime <= 0 || energyPerTick <= 0) return null;
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                Math.multiplyExact((long) burnTime, energyPerTick),
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorageApi.MOD_ID, "integrateddynamics_coal_generator"),
                burnTime);
        }

        @Override
        public String revisionKey() {
            return String.valueOf(BlockEntityCoalGeneratorConfig.energyPerTick);
        }
    }
    private static Item requiredItem(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException(
                    "Loaded Integrated Dynamics did not register station item " + id);
        }
        return item;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
