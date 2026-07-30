package com.swear.autostorage.compat.modernindustrialization;

import com.swear.autostorage.MachineCategory;
import aztech.modern_industrialization.machines.init.MIMachineRecipeTypes;
import aztech.modern_industrialization.machines.init.MachineTier;
import aztech.modern_industrialization.machines.recipe.MachineRecipe;
import aztech.modern_industrialization.machines.recipe.MachineRecipeType;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class ModernIndustrializationCompat {
    private static final String MOD_ID = "modern_industrialization";
    private static final MachineWorkRate BRONZE_RATE =
            MachineWorkRate.of(MachineTier.BRONZE.getBaseEu(), 1);
    private static final MachineWorkRate STEEL_RATE =
            MachineWorkRate.of(MachineTier.STEEL.getBaseEu(), 1);
    private static final MachineWorkRate ELECTRIC_RATE =
            MachineWorkRate.of(MachineTier.LV.getBaseEu(), 1);
    private static final List<Family> FAMILIES = List.of(
            electric("assembler", () -> MIMachineRecipeTypes.ASSEMBLER, 9, 2, 3, 0),
            electric("centrifuge", () -> MIMachineRecipeTypes.CENTRIFUGE, 1, 1, 4, 4),
            electric("chemical_reactor", () -> MIMachineRecipeTypes.CHEMICAL_REACTOR, 3, 3, 3, 3),
            threeTier("compressor", () -> MIMachineRecipeTypes.COMPRESSOR, 1, 0, 1, 0),
            threeTier("cutting_machine", () -> MIMachineRecipeTypes.CUTTING_MACHINE, 1, 1, 1, 0),
            electric("distillery", () -> MIMachineRecipeTypes.DISTILLERY, 0, 1, 0, 1),
            electric("electrolyzer", () -> MIMachineRecipeTypes.ELECTROLYZER, 1, 1, 4, 4),
            threeTier("furnace", () -> MIMachineRecipeTypes.FURNACE, 1, 0, 1, 0),
            threeTier("macerator", () -> MIMachineRecipeTypes.MACERATOR, 1, 0, 4, 0),
            threeTier("mixer", () -> MIMachineRecipeTypes.MIXER, 4, 2, 2, 2),
            steelAndElectric("packer", () -> MIMachineRecipeTypes.PACKER, 3, 0, 1, 0),
            electric("polarizer", () -> MIMachineRecipeTypes.POLARIZER, 2, 0, 1, 0),
            steelAndElectric("unpacker", () -> MIMachineRecipeTypes.UNPACKER, 1, 0, 2, 0),
            steelAndElectric("wiremill", () -> MIMachineRecipeTypes.WIREMILL, 1, 0, 1, 0));

    private ModernIndustrializationCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Modern Industrialization descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Modern Industrialization family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Modern Industrialization descriptors and families must share one namespace");
        }

        for (Family family : FAMILIES) {
            String registryPath = "modern_industrialization_" + family.path();
            ResourceLocation descriptorId = ResourceLocation.fromNamespaceAndPath(
                    machineDescriptors.getNamespace(), registryPath);
            machineDescriptors.register(registryPath, () -> MachineDescriptor.installableVariants(
                    descriptorId,
                    net.minecraft.network.chat.Component.translatable(
                            "gui.auto_storage.station." + registryPath),
                    () -> variants(family),
                    MachineCategory.PROCESS,
                    MachineDescriptorApi.MAX_INSTALLED_COUNT,
                    null));
            recipeFamilies.register(registryPath, () ->
                    RecipeFamilyFactories.deterministicResources(
                            MachineRecipe.class,
                            family.recipeType(),
                            descriptorId,
                            recipe -> supports(family, recipe),
                            ModernIndustrializationCompat::plan,
                            recipe -> RecipeFamilyCost.stationWork(coldWork(family, recipe)),
                            RecipePresentationKind.CRAFTING));
        }
    }

    private static List<MachineVariant> variants(Family family) {
        return family.variants().stream()
                .map(variant -> MachineVariant.of(machine(variant.itemPath()), variant.rate()))
                .toList();
    }

    private static boolean supports(Family family, MachineRecipe recipe) {
        int inputCount = recipe.itemInputs.size() + recipe.fluidInputs.size() + 1;
        return recipe.eu > 0
                && recipe.eu <= family.lowestTier().getMaxEu()
                && recipe.duration > 0
                && inputCount > 1
                && inputCount <= 9
                && recipe.itemInputs.size() <= family.maxItemInputs()
                && recipe.fluidInputs.size() <= family.maxFluidInputs()
                && recipe.itemOutputs.size() <= family.maxItemOutputs()
                && recipe.fluidOutputs.size() <= family.maxFluidOutputs()
                && (!recipe.itemOutputs.isEmpty() || !recipe.fluidOutputs.isEmpty())
                && recipe.conditions.isEmpty()
                && recipe.itemInputs.stream().allMatch(input ->
                        input.amount() > 0
                                && isDeterministicInput(input.probability())
                                && hasRepresentatives(input.ingredient()))
                && recipe.fluidInputs.stream().allMatch(input ->
                        input.amount() > 0
                                && isDeterministicInput(input.probability())
                                && Arrays.stream(input.fluid().getStacks())
                                .anyMatch(stack -> !stack.isEmpty()))
                && recipe.itemOutputs.stream().allMatch(output ->
                        output.probability() == 1f
                                && !output.getStack().isEmpty()
                                && output.getStack().getCount() > 0)
                && recipe.fluidOutputs.stream().allMatch(output ->
                        output.probability() == 1f
                                && output.fluid() != null
                                && output.amount() > 0)
                && retainedInputsAreCompatible(recipe);
    }

    private static long coldWork(Family family, MachineRecipe recipe) {
        return Math.multiplyExact(
                (long) recipe.duration,
                Math.min(recipe.eu, family.lowestTier().getBaseEu()));
    }

    private static TypedRecipePlan plan(
            MachineRecipe recipe,
            HolderLookup.Provider registries
    ) {
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        for (MachineRecipe.ItemInput input : recipe.itemInputs) {
            List<StorageResourceKey> alternatives = Arrays.stream(input.ingredient().getItems())
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> StorageResourceKey.item(stack.copyWithCount(1), registries))
                    .distinct()
                    .toList();
            builder.input(input.probability() == 0f
                    ? TypedRecipeInput.catalystAny(alternatives, input.amount())
                    : TypedRecipeInput.consumeAny(alternatives, input.amount()));
        }
        for (MachineRecipe.FluidInput input : recipe.fluidInputs) {
            List<StorageResourceKey> alternatives = Arrays.stream(input.fluid().getStacks())
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> StorageResourceKey.fluid(
                            stack.copyWithAmount(1), registries))
                    .distinct()
                    .toList();
            builder.input(input.probability() == 0f
                    ? TypedRecipeInput.catalystAny(alternatives, input.amount())
                    : TypedRecipeInput.consumeAny(alternatives, input.amount()));
        }
        builder.input(TypedRecipeInput.consume(
                StorageResourceKey.neoforgeEnergy(),
                Math.multiplyExact((long) recipe.eu, recipe.duration)));

        LinkedHashMap<StorageResourceKey, Long> outputs = new LinkedHashMap<>();
        LinkedHashMap<StorageResourceKey, ItemStack> outputStacks = new LinkedHashMap<>();
        for (MachineRecipe.ItemOutput output : recipe.itemOutputs) {
            ItemStack stack = output.getStack();
            StorageResourceKey key = StorageResourceKey.item(stack.copyWithCount(1), registries);
            outputs.merge(key, (long) stack.getCount(), Math::addExact);
            outputStacks.putIfAbsent(key, stack);
        }
        for (MachineRecipe.FluidOutput output : recipe.fluidOutputs) {
            StorageResourceKey key = StorageResourceKey.fluid(
                    new FluidStack(output.fluid(), 1), registries);
            outputs.merge(key, output.amount(), Math::addExact);
            outputStacks.putIfAbsent(key, fluidPresentation(output.fluid()));
        }
        boolean primary = true;
        for (var output : outputs.entrySet()) {
            builder.output(primary
                    ? TypedRecipeOutput.primary(output.getKey(), output.getValue())
                    : TypedRecipeOutput.remainder(output.getKey(), output.getValue()));
            primary = false;
        }
        StorageResourceKey primaryKey = outputs.keySet().iterator().next();
        ItemStack presentationOutput = outputStacks.get(primaryKey).copy();
        if (primaryKey.itemStack(registries).isPresent()) {
            presentationOutput.setCount(Math.toIntExact(outputs.get(primaryKey)));
        }
        int inputCount = recipe.itemInputs.size() + recipe.fluidInputs.size() + 1;
        int width = Math.min(3, inputCount);
        return builder
                .presentationOutput(presentationOutput)
                .layout(width, (inputCount + width - 1) / width, true)
                .build();
    }

    private static boolean hasRepresentatives(Ingredient ingredient) {
        return ingredient != null
                && !ingredient.isEmpty()
                && Arrays.stream(ingredient.getItems()).anyMatch(stack -> !stack.isEmpty());
    }

    private static boolean isDeterministicInput(float probability) {
        return probability == 0f || probability == 1f;
    }

    private static boolean retainedInputsAreCompatible(MachineRecipe recipe) {
        Set<Item> consumedItems = new HashSet<>();
        Set<Item> retainedItems = new HashSet<>();
        for (MachineRecipe.ItemInput input : recipe.itemInputs) {
            Set<Item> target = input.probability() == 0f ? retainedItems : consumedItems;
            for (ItemStack stack : input.ingredient().getItems()) {
                if (!stack.isEmpty() && !target.add(stack.getItem())
                        && input.probability() == 0f) {
                    return false;
                }
            }
        }
        if (retainedItems.stream().anyMatch(consumedItems::contains)) return false;

        Set<Fluid> consumedFluids = new HashSet<>();
        Set<Fluid> retainedFluids = new HashSet<>();
        for (MachineRecipe.FluidInput input : recipe.fluidInputs) {
            Set<Fluid> target = input.probability() == 0f ? retainedFluids : consumedFluids;
            for (FluidStack stack : input.fluid().getStacks()) {
                if (!stack.isEmpty() && !target.add(stack.getFluid())
                        && input.probability() == 0f) {
                    return false;
                }
            }
        }
        return retainedFluids.stream().noneMatch(consumedFluids::contains);
    }

    private static ItemStack machine(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException(
                    "Loaded Modern Industrialization did not register station item " + id);
        }
        return new ItemStack(item);
    }

    private static ItemStack fluidPresentation(Fluid fluid) {
        ItemStack stack = new ItemStack(fluid.getBucket());
        if (!stack.isEmpty()) return stack;
        stack = new ItemStack(Items.BUCKET);
        stack.set(DataComponents.CUSTOM_NAME, new FluidStack(fluid, 1).getHoverName());
        return stack;
    }

    private static Family electric(
            String path,
            Supplier<MachineRecipeType> recipeType,
            int maxItemInputs,
            int maxFluidInputs,
            int maxItemOutputs,
            int maxFluidOutputs
    ) {
        return new Family(
                path,
                recipeType,
                MachineTier.LV,
                maxItemInputs,
                maxFluidInputs,
                maxItemOutputs,
                maxFluidOutputs,
                List.of(new Variant(path, ELECTRIC_RATE)));
    }

    private static Family threeTier(
            String path,
            Supplier<MachineRecipeType> recipeType,
            int maxItemInputs,
            int maxFluidInputs,
            int maxItemOutputs,
            int maxFluidOutputs
    ) {
        return new Family(
                path,
                recipeType,
                MachineTier.BRONZE,
                maxItemInputs,
                maxFluidInputs,
                maxItemOutputs,
                maxFluidOutputs,
                List.of(
                        new Variant("bronze_" + path, BRONZE_RATE),
                        new Variant("steel_" + path, STEEL_RATE),
                        new Variant("electric_" + path, ELECTRIC_RATE)));
    }

    private static Family steelAndElectric(
            String path,
            Supplier<MachineRecipeType> recipeType,
            int maxItemInputs,
            int maxFluidInputs,
            int maxItemOutputs,
            int maxFluidOutputs
    ) {
        return new Family(
                path,
                recipeType,
                MachineTier.STEEL,
                maxItemInputs,
                maxFluidInputs,
                maxItemOutputs,
                maxFluidOutputs,
                List.of(
                        new Variant("steel_" + path, STEEL_RATE),
                        new Variant("electric_" + path, ELECTRIC_RATE)));
    }

    private record Family(
            String path,
            Supplier<MachineRecipeType> recipeType,
            MachineTier lowestTier,
            int maxItemInputs,
            int maxFluidInputs,
            int maxItemOutputs,
            int maxFluidOutputs,
            List<Variant> variants
    ) {
        private Family {
            variants = List.copyOf(variants);
        }
    }

    private record Variant(String itemPath, MachineWorkRate rate) {
    }
}
