package com.swearprom.magicstorage.magic_storage;

import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ElectrolysisRecipe.ElectrolysisRecipeOutput;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.PressurizedReactionRecipe.PressurizedReactionRecipeOutput;
import mekanism.api.recipes.basic.BasicCentrifugingRecipe;
import mekanism.api.recipes.basic.BasicChemicalCrystallizerRecipe;
import mekanism.api.recipes.basic.BasicChemicalDissolutionRecipe;
import mekanism.api.recipes.basic.BasicChemicalInfuserRecipe;
import mekanism.api.recipes.basic.BasicChemicalOxidizerRecipe;
import mekanism.api.recipes.basic.BasicCombinerRecipe;
import mekanism.api.recipes.basic.BasicCompressingRecipe;
import mekanism.api.recipes.basic.BasicCrushingRecipe;
import mekanism.api.recipes.basic.BasicElectrolysisRecipe;
import mekanism.api.recipes.basic.BasicEnrichingRecipe;
import mekanism.api.recipes.basic.BasicInjectingRecipe;
import mekanism.api.recipes.basic.BasicItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.basic.BasicMetallurgicInfuserRecipe;
import mekanism.api.recipes.basic.BasicNucleosynthesizingRecipe;
import mekanism.api.recipes.basic.BasicPaintingRecipe;
import mekanism.api.recipes.basic.BasicPigmentExtractingRecipe;
import mekanism.api.recipes.basic.BasicPigmentMixingRecipe;
import mekanism.api.recipes.basic.BasicPressurizedReactionRecipe;
import mekanism.api.recipes.basic.BasicPurifyingRecipe;
import mekanism.api.recipes.basic.BasicRotaryRecipe;
import mekanism.api.recipes.basic.BasicSawmillRecipe;
import mekanism.api.recipes.basic.BasicSmeltingRecipe;
import mekanism.api.recipes.basic.BasicWashingRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tier.FactoryTier;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class MekanismRecipeCompat {
    private static final String MOD_ID = "mekanism";
    private static final long BASIC_MACHINE_WORK = 10L * SharedConstants.TICKS_PER_SECOND;
    private static final long FIVE_SECOND_MACHINE_WORK =
            5L * SharedConstants.TICKS_PER_SECOND;

    private MekanismRecipeCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("Mekanism descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException("Mekanism family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Mekanism descriptor and recipe family must share one namespace");
        }

        String namespace = machineDescriptors.getNamespace();
        registerSingleInput(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "crusher",
                FactoryType.CRUSHING,
                BasicCrushingRecipe.class,
                MekanismRecipeTypes.TYPE_CRUSHING,
                BasicCrushingRecipe::getInput,
                BasicCrushingRecipe::getOutputDefinition);
        registerSingleInput(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "enrichment_chamber",
                FactoryType.ENRICHING,
                BasicEnrichingRecipe.class,
                MekanismRecipeTypes.TYPE_ENRICHING,
                BasicEnrichingRecipe::getInput,
                BasicEnrichingRecipe::getOutputDefinition);
        registerSingleInput(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "energized_smelter",
                FactoryType.SMELTING,
                BasicSmeltingRecipe.class,
                MekanismRecipeTypes.TYPE_SMELTING,
                BasicSmeltingRecipe::getInput,
                BasicSmeltingRecipe::getOutputDefinition);
        registerItemChemical(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "osmium_compressor",
                FactoryType.COMPRESSING,
                BasicCompressingRecipe.class,
                MekanismRecipeTypes.TYPE_COMPRESSING);

        String combinerPath = registryPath("combiner");
        ResourceLocation combinerDescriptorId = descriptorId(namespace, "combiner");
        registerStation(
                machineDescriptors,
                combinerPath,
                combinerDescriptorId,
                FactoryType.COMBINING);
        recipeFamilies.register(combinerPath, () -> RecipeFamilyFactories.deterministicResources(
                BasicCombinerRecipe.class,
                MekanismRecipeTypes.TYPE_COMBINING,
                combinerDescriptorId,
                recipe -> true,
                MekanismRecipeCompat::combiningPlan,
                recipe -> RecipeFamilyCost.stationWork(BASIC_MACHINE_WORK),
                RecipePresentationKind.CRAFTING));
        registerItemChemical(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "purification_chamber",
                FactoryType.PURIFYING,
                BasicPurifyingRecipe.class,
                MekanismRecipeTypes.TYPE_PURIFYING);
        registerItemChemical(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "chemical_injection_chamber",
                FactoryType.INJECTING,
                BasicInjectingRecipe.class,
                MekanismRecipeTypes.TYPE_INJECTING);
        registerItemChemical(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "metallurgic_infuser",
                FactoryType.INFUSING,
                BasicMetallurgicInfuserRecipe.class,
                MekanismRecipeTypes.TYPE_METALLURGIC_INFUSING);

        String sawmillPath = registryPath("precision_sawmill");
        ResourceLocation sawmillDescriptorId = descriptorId(namespace, "precision_sawmill");
        registerStation(
                machineDescriptors,
                sawmillPath,
                sawmillDescriptorId,
                FactoryType.SAWING);
        recipeFamilies.register(sawmillPath, () -> RecipeFamilyFactories.deterministicResources(
                BasicSawmillRecipe.class,
                MekanismRecipeTypes.TYPE_SAWING,
                sawmillDescriptorId,
                MekanismRecipeCompat::isDeterministicSawing,
                MekanismRecipeCompat::sawingPlan,
                recipe -> RecipeFamilyCost.stationWork(BASIC_MACHINE_WORK),
                RecipePresentationKind.CRAFTING));

        String reactionPath = registryPath("pressurized_reaction_chamber");
        ResourceLocation reactionDescriptorId = descriptorId(
                namespace, "pressurized_reaction_chamber");
        registerStation(
                machineDescriptors,
                reactionPath,
                reactionDescriptorId,
                "pressurized_reaction_chamber",
                MachineWorkRate.ONE);
        recipeFamilies.register(reactionPath, () -> RecipeFamilyFactories.deterministicResources(
                BasicPressurizedReactionRecipe.class,
                MekanismRecipeTypes.TYPE_REACTION,
                reactionDescriptorId,
                MekanismRecipeCompat::hasSupportedOutput,
                MekanismRecipeCompat::reactionPlan,
                recipe -> RecipeFamilyCost.stationWork(recipe.getDuration()),
                RecipePresentationKind.CRAFTING));

        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "rotary_condensentrator",
                BasicRotaryRecipe.class,
                MekanismRecipeTypes.TYPE_ROTARY,
                MekanismRecipeCompat::hasOneRotaryDirection,
                MekanismRecipeCompat::rotaryPlan,
                recipe -> RecipeFamilyCost.stationWork(1));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "chemical_oxidizer",
                BasicChemicalOxidizerRecipe.class,
                MekanismRecipeTypes.TYPE_OXIDIZING,
                recipe -> true,
                MekanismRecipeCompat::itemToChemicalPlan,
                recipe -> RecipeFamilyCost.stationWork(FIVE_SECOND_MACHINE_WORK));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "chemical_infuser",
                BasicChemicalInfuserRecipe.class,
                MekanismRecipeTypes.TYPE_CHEMICAL_INFUSING,
                recipe -> true,
                MekanismRecipeCompat::chemicalChemicalPlan,
                recipe -> RecipeFamilyCost.stationWork(1));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "electrolytic_separator",
                BasicElectrolysisRecipe.class,
                MekanismRecipeTypes.TYPE_SEPARATING,
                MekanismRecipeCompat::hasSupportedElectrolysisOutput,
                MekanismRecipeCompat::electrolysisPlan,
                recipe -> RecipeFamilyCost.stationWork(recipe.getEnergyMultiplier()));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "chemical_dissolution_chamber",
                BasicChemicalDissolutionRecipe.class,
                MekanismRecipeTypes.TYPE_DISSOLUTION,
                recipe -> hasSupportedPerTickUsage(
                        recipe.getChemicalInput(),
                        recipe.perTickUsage(),
                        FIVE_SECOND_MACHINE_WORK),
                (recipe, registries) -> itemChemicalToChemicalPlan(
                        recipe, registries, FIVE_SECOND_MACHINE_WORK),
                recipe -> RecipeFamilyCost.stationWork(FIVE_SECOND_MACHINE_WORK));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "chemical_washer",
                BasicWashingRecipe.class,
                MekanismRecipeTypes.TYPE_WASHING,
                recipe -> true,
                MekanismRecipeCompat::fluidChemicalPlan,
                recipe -> RecipeFamilyCost.stationWork(1));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "chemical_crystallizer",
                BasicChemicalCrystallizerRecipe.class,
                MekanismRecipeTypes.TYPE_CRYSTALLIZING,
                recipe -> true,
                MekanismRecipeCompat::chemicalToItemPlan,
                recipe -> RecipeFamilyCost.stationWork(BASIC_MACHINE_WORK));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "isotopic_centrifuge",
                BasicCentrifugingRecipe.class,
                MekanismRecipeTypes.TYPE_CENTRIFUGING,
                recipe -> true,
                MekanismRecipeCompat::chemicalToChemicalPlan,
                recipe -> RecipeFamilyCost.stationWork(1));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "antiprotonic_nucleosynthesizer",
                BasicNucleosynthesizingRecipe.class,
                MekanismRecipeTypes.TYPE_NUCLEOSYNTHESIZING,
                recipe -> hasSupportedPerTickUsage(
                        recipe.getChemicalInput(),
                        recipe.perTickUsage(),
                        recipe.getDuration()),
                MekanismRecipeCompat::nucleosynthesizingPlan,
                recipe -> RecipeFamilyCost.stationWork(recipe.getDuration()));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "pigment_extractor",
                BasicPigmentExtractingRecipe.class,
                MekanismRecipeTypes.TYPE_PIGMENT_EXTRACTING,
                recipe -> true,
                MekanismRecipeCompat::itemToChemicalPlan,
                recipe -> RecipeFamilyCost.stationWork(FIVE_SECOND_MACHINE_WORK));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "pigment_mixer",
                BasicPigmentMixingRecipe.class,
                MekanismRecipeTypes.TYPE_PIGMENT_MIXING,
                recipe -> true,
                MekanismRecipeCompat::chemicalChemicalPlan,
                recipe -> RecipeFamilyCost.stationWork(1));
        registerTypedStation(
                machineDescriptors,
                recipeFamilies,
                namespace,
                "painting_machine",
                BasicPaintingRecipe.class,
                MekanismRecipeTypes.TYPE_PAINTING,
                recipe -> hasSupportedPerTickUsage(
                        recipe.getChemicalInput(),
                        recipe.perTickUsage(),
                        BASIC_MACHINE_WORK),
                (recipe, registries) -> itemChemicalToItemPlan(
                        recipe, registries, BASIC_MACHINE_WORK),
                recipe -> RecipeFamilyCost.stationWork(BASIC_MACHINE_WORK));
    }

    public static ResourceLocation descriptorId(String namespace, String stationPath) {
        return ResourceLocation.fromNamespaceAndPath(
                Objects.requireNonNull(namespace, "namespace"), registryPath(stationPath));
    }

    private static <R extends ItemStackToItemStackRecipe> void registerSingleInput(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            String namespace,
            String stationPath,
            FactoryType factoryType,
            Class<R> recipeClass,
            Supplier<? extends net.minecraft.world.item.crafting.RecipeType<?>> recipeType,
            java.util.function.Function<R, ItemStackIngredient> input,
            java.util.function.Function<R, List<ItemStack>> output
    ) {
        String path = registryPath(stationPath);
        ResourceLocation descriptorId = descriptorId(namespace, stationPath);
        registerStation(machineDescriptors, path, descriptorId, factoryType);
        recipeFamilies.register(path, () -> RecipeFamilyFactories.deterministicResources(
                recipeClass,
                recipeType,
                descriptorId,
                recipe -> true,
                (recipe, registries) -> singleInputPlan(
                        input.apply(recipe), output.apply(recipe), registries),
                recipe -> RecipeFamilyCost.stationWork(BASIC_MACHINE_WORK),
                RecipePresentationKind.CRAFTING));
    }

    private static <R extends BasicItemStackChemicalToItemStackRecipe> void registerItemChemical(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            String namespace,
            String stationPath,
            FactoryType factoryType,
            Class<R> recipeClass,
            Supplier<? extends net.minecraft.world.item.crafting.RecipeType<?>> recipeType
    ) {
        String path = registryPath(stationPath);
        ResourceLocation descriptorId = descriptorId(namespace, stationPath);
        registerStation(machineDescriptors, path, descriptorId, factoryType);
        recipeFamilies.register(path, () -> RecipeFamilyFactories.deterministicResources(
                recipeClass,
                recipeType,
                descriptorId,
                MekanismRecipeCompat::hasSupportedItemChemicalUsage,
                MekanismRecipeCompat::itemChemicalPlan,
                recipe -> RecipeFamilyCost.stationWork(BASIC_MACHINE_WORK),
                RecipePresentationKind.CRAFTING));
    }

    private static <R extends Recipe<?>> void registerTypedStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            String namespace,
            String stationPath,
            Class<R> recipeClass,
            Supplier<? extends net.minecraft.world.item.crafting.RecipeType<?>> recipeType,
            Predicate<? super R> eligibility,
            BiFunction<? super R, HolderLookup.Provider, TypedRecipePlan> plan,
            Function<? super R, RecipeFamilyCost> cost
    ) {
        String path = registryPath(stationPath);
        ResourceLocation descriptorId = descriptorId(namespace, stationPath);
        registerStation(
                machineDescriptors,
                path,
                descriptorId,
                stationPath,
                MachineWorkRate.ONE);
        recipeFamilies.register(path, () -> RecipeFamilyFactories.deterministicResources(
                recipeClass,
                recipeType,
                descriptorId,
                eligibility,
                plan,
                cost,
                RecipePresentationKind.CRAFTING));
    }

    private static void registerStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            String registryPath,
            ResourceLocation descriptorId,
            FactoryType factoryType
    ) {
        machineDescriptors.register(registryPath, () -> MachineDescriptor.installableVariants(
                descriptorId,
                new ItemStack(factoryType.getBaseBlock().asItem()).getHoverName(),
                () -> factoryVariants(factoryType),
                MachineEnergyTable.Category.PROCESS,
                MachineDescriptorApi.MAX_INSTALLED_COUNT,
                null));
    }

    private static void registerStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            String registryPath,
            ResourceLocation descriptorId,
            String stationPath,
            MachineWorkRate rate
    ) {
        machineDescriptors.register(registryPath, () -> MachineDescriptor.installableVariants(
                descriptorId,
                stationStack(stationPath).getHoverName(),
                () -> List.of(MachineVariant.of(stationStack(stationPath), rate)),
                MachineEnergyTable.Category.PROCESS,
                MachineDescriptorApi.MAX_INSTALLED_COUNT,
                null));
    }

    private static List<MachineVariant> factoryVariants(FactoryType factoryType) {
        List<MachineVariant> variants = new ArrayList<>(FactoryTier.values().length + 1);
        variants.add(MachineVariant.of(
                new ItemStack(factoryType.getBaseBlock().asItem()),
                MachineWorkRate.ONE));
        for (FactoryTier tier : FactoryTier.values()) {
            variants.add(MachineVariant.of(
                    new ItemStack(MekanismBlocks.getFactory(tier, factoryType).asItem()),
                    MachineWorkRate.of(tier.processes, 1)));
        }
        return List.copyOf(variants);
    }

    private static TypedRecipePlan singleInputPlan(
            ItemStackIngredient input,
            List<ItemStack> outputDefinition,
            HolderLookup.Provider registries
    ) {
        ItemStack output = fixedItemOutput(outputDefinition);
        return TypedRecipePlan.builder()
                .input(itemInput(input, registries))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output, registries), output.getCount()))
                .presentationOutput(output)
                .layout(1, 1, false)
                .build();
    }

    private static TypedRecipePlan combiningPlan(
            BasicCombinerRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = fixedItemOutput(recipe.getOutputDefinition());
        return TypedRecipePlan.builder()
                .input(itemInput(recipe.getMainInput(), registries))
                .input(itemInput(recipe.getExtraInput(), registries))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output, registries), output.getCount()))
                .presentationOutput(output)
                .layout(2, 1, false)
                .build();
    }

    private static boolean hasSupportedItemChemicalUsage(
            BasicItemStackChemicalToItemStackRecipe recipe
    ) {
        long multiplier = recipe.perTickUsage() ? BASIC_MACHINE_WORK : 1;
        try {
            for (ChemicalStack representation : recipe.getChemicalInput().getRepresentations()) {
                if (!representation.isEmpty()) {
                    Math.multiplyExact(
                            recipe.getChemicalInput().getNeededAmount(representation),
                            multiplier);
                }
            }
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static TypedRecipePlan itemChemicalPlan(
            BasicItemStackChemicalToItemStackRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = fixedItemOutput(recipe.getOutputDefinition());
        long multiplier = recipe.perTickUsage() ? BASIC_MACHINE_WORK : 1;
        return TypedRecipePlan.builder()
                .input(itemInput(recipe.getItemInput(), registries))
                .input(chemicalInput(recipe.getChemicalInput(), multiplier))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output, registries), output.getCount()))
                .presentationOutput(output)
                .layout(2, 1, false)
                .build();
    }

    private static boolean hasOneRotaryDirection(BasicRotaryRecipe recipe) {
        return recipe.hasFluidToChemical() != recipe.hasChemicalToFluid();
    }

    private static TypedRecipePlan rotaryPlan(
            BasicRotaryRecipe recipe,
            HolderLookup.Provider registries
    ) {
        if (!hasOneRotaryDirection(recipe)) {
            throw new IllegalArgumentException(
                    "Rotary compatibility requires exactly one conversion direction");
        }
        if (recipe.hasFluidToChemical()) {
            ChemicalStack output = fixedChemicalOutput(recipe.getChemicalOutputDefinition());
            return TypedRecipePlan.builder()
                    .input(fluidInput(recipe.getFluidInput(), registries))
                    .output(TypedRecipeOutput.primary(
                            MekanismChemicalCompat.key(output), output.getAmount()))
                    .presentationOutput(chemicalPresentation(output))
                    .layout(1, 1, false)
                    .build();
        }
        FluidStack output = fixedFluidOutput(recipe.getFluidOutputDefinition());
        return TypedRecipePlan.builder()
                .input(chemicalInput(recipe.getChemicalInput()))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.fluid(
                                output.copyWithAmount(1), registries),
                        output.getAmount()))
                .presentationOutput(fluidPresentation())
                .layout(1, 1, false)
                .build();
    }

    private static TypedRecipePlan itemToChemicalPlan(
            BasicChemicalOxidizerRecipe recipe,
            HolderLookup.Provider registries
    ) {
        return itemToChemicalPlan(
                recipe.getInput(), recipe.getOutputDefinition(), registries);
    }

    private static TypedRecipePlan itemToChemicalPlan(
            BasicPigmentExtractingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        return itemToChemicalPlan(
                recipe.getInput(), recipe.getOutputDefinition(), registries);
    }

    private static TypedRecipePlan itemToChemicalPlan(
            ItemStackIngredient input,
            List<ChemicalStack> outputDefinition,
            HolderLookup.Provider registries
    ) {
        ChemicalStack output = fixedChemicalOutput(outputDefinition);
        return TypedRecipePlan.builder()
                .input(itemInput(input, registries))
                .output(TypedRecipeOutput.primary(
                        MekanismChemicalCompat.key(output), output.getAmount()))
                .presentationOutput(chemicalPresentation(output))
                .layout(1, 1, false)
                .build();
    }

    private static TypedRecipePlan chemicalChemicalPlan(
            BasicChemicalInfuserRecipe recipe,
            HolderLookup.Provider registries
    ) {
        return chemicalChemicalPlan(
                recipe.getLeftInput(),
                recipe.getRightInput(),
                recipe.getOutputDefinition());
    }

    private static TypedRecipePlan chemicalChemicalPlan(
            BasicPigmentMixingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        return chemicalChemicalPlan(
                recipe.getLeftInput(),
                recipe.getRightInput(),
                recipe.getOutputDefinition());
    }

    private static TypedRecipePlan chemicalChemicalPlan(
            ChemicalStackIngredient left,
            ChemicalStackIngredient right,
            List<ChemicalStack> outputDefinition
    ) {
        ChemicalStack output = fixedChemicalOutput(outputDefinition);
        return TypedRecipePlan.builder()
                .input(chemicalInput(left))
                .input(chemicalInput(right))
                .output(TypedRecipeOutput.primary(
                        MekanismChemicalCompat.key(output), output.getAmount()))
                .presentationOutput(chemicalPresentation(output))
                .layout(2, 1, false)
                .build();
    }

    private static boolean hasSupportedElectrolysisOutput(BasicElectrolysisRecipe recipe) {
        List<ElectrolysisRecipeOutput> outputs = recipe.getOutputDefinition();
        if (outputs.size() != 1) return false;
        ElectrolysisRecipeOutput output = outputs.getFirst();
        try {
            if (MekanismChemicalCompat.key(output.left()).equals(
                    MekanismChemicalCompat.key(output.right()))) {
                Math.addExact(output.left().getAmount(), output.right().getAmount());
            }
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static TypedRecipePlan electrolysisPlan(
            BasicElectrolysisRecipe recipe,
            HolderLookup.Provider registries
    ) {
        List<ElectrolysisRecipeOutput> definitions = recipe.getOutputDefinition();
        if (definitions.size() != 1) {
            throw new IllegalArgumentException(
                    "Electrolysis compatibility requires one deterministic output pair");
        }
        ElectrolysisRecipeOutput definition = definitions.getFirst();
        ChemicalStack left = definition.left();
        ChemicalStack right = definition.right();
        StorageResourceKey leftKey = MekanismChemicalCompat.key(left);
        StorageResourceKey rightKey = MekanismChemicalCompat.key(right);
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder()
                .input(fluidInput(recipe.getInput(), registries))
                .presentationOutput(chemicalPresentation(left))
                .layout(1, 1, false);
        if (leftKey.equals(rightKey)) {
            builder.output(TypedRecipeOutput.primary(
                    leftKey, Math.addExact(left.getAmount(), right.getAmount())));
        } else {
            builder.output(TypedRecipeOutput.primary(leftKey, left.getAmount()));
            builder.output(TypedRecipeOutput.remainder(rightKey, right.getAmount()));
        }
        return builder.build();
    }

    private static TypedRecipePlan itemChemicalToChemicalPlan(
            BasicChemicalDissolutionRecipe recipe,
            HolderLookup.Provider registries,
            long work
    ) {
        ChemicalStack output = fixedChemicalOutput(recipe.getOutputDefinition());
        long multiplier = recipe.perTickUsage() ? work : 1;
        return TypedRecipePlan.builder()
                .input(itemInput(recipe.getItemInput(), registries))
                .input(chemicalInput(recipe.getChemicalInput(), multiplier))
                .output(TypedRecipeOutput.primary(
                        MekanismChemicalCompat.key(output), output.getAmount()))
                .presentationOutput(chemicalPresentation(output))
                .layout(2, 1, false)
                .build();
    }

    private static TypedRecipePlan fluidChemicalPlan(
            BasicWashingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ChemicalStack output = fixedChemicalOutput(recipe.getOutputDefinition());
        return TypedRecipePlan.builder()
                .input(fluidInput(recipe.getFluidInput(), registries))
                .input(chemicalInput(recipe.getChemicalInput()))
                .output(TypedRecipeOutput.primary(
                        MekanismChemicalCompat.key(output), output.getAmount()))
                .presentationOutput(chemicalPresentation(output))
                .layout(2, 1, false)
                .build();
    }

    private static TypedRecipePlan chemicalToItemPlan(
            BasicChemicalCrystallizerRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = fixedItemOutput(recipe.getOutputDefinition());
        return TypedRecipePlan.builder()
                .input(chemicalInput(recipe.getInput()))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output, registries), output.getCount()))
                .presentationOutput(output)
                .layout(1, 1, false)
                .build();
    }

    private static TypedRecipePlan chemicalToChemicalPlan(
            BasicCentrifugingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ChemicalStack output = fixedChemicalOutput(recipe.getOutputDefinition());
        return TypedRecipePlan.builder()
                .input(chemicalInput(recipe.getInput()))
                .output(TypedRecipeOutput.primary(
                        MekanismChemicalCompat.key(output), output.getAmount()))
                .presentationOutput(chemicalPresentation(output))
                .layout(1, 1, false)
                .build();
    }

    private static TypedRecipePlan nucleosynthesizingPlan(
            BasicNucleosynthesizingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack output = fixedItemOutput(recipe.getOutputDefinition());
        long multiplier = recipe.perTickUsage() ? recipe.getDuration() : 1;
        return TypedRecipePlan.builder()
                .input(itemInput(recipe.getItemInput(), registries))
                .input(chemicalInput(recipe.getChemicalInput(), multiplier))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output, registries), output.getCount()))
                .presentationOutput(output)
                .layout(2, 1, false)
                .build();
    }

    private static TypedRecipePlan itemChemicalToItemPlan(
            BasicPaintingRecipe recipe,
            HolderLookup.Provider registries,
            long work
    ) {
        ItemStack output = fixedItemOutput(recipe.getOutputDefinition());
        long multiplier = recipe.perTickUsage() ? work : 1;
        return TypedRecipePlan.builder()
                .input(itemInput(recipe.getItemInput(), registries))
                .input(chemicalInput(recipe.getChemicalInput(), multiplier))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output, registries), output.getCount()))
                .presentationOutput(output)
                .layout(2, 1, false)
                .build();
    }

    private static boolean hasSupportedPerTickUsage(
            ChemicalStackIngredient ingredient,
            boolean perTickUsage,
            long work
    ) {
        long multiplier = perTickUsage ? work : 1;
        try {
            for (ChemicalStack representation : ingredient.getRepresentations()) {
                if (!representation.isEmpty()) {
                    Math.multiplyExact(
                            ingredient.getNeededAmount(representation), multiplier);
                }
            }
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static boolean isDeterministicSawing(BasicSawmillRecipe recipe) {
        return recipe.getSecondaryChance() == 0 || recipe.getSecondaryChance() == 1;
    }

    private static TypedRecipePlan sawingPlan(
            BasicSawmillRecipe recipe,
            HolderLookup.Provider registries
    ) {
        ItemStack main = fixedItemOutput(recipe.getMainOutputDefinition());
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder()
                .input(itemInput(recipe.getInput(), registries))
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(main, registries), main.getCount()))
                .presentationOutput(main)
                .layout(1, 1, false);
        if (recipe.getSecondaryChance() == 1) {
            ItemStack secondary = fixedItemOutput(recipe.getSecondaryOutputDefinition());
            builder.output(TypedRecipeOutput.remainder(
                    StorageResourceKey.item(secondary, registries),
                    secondary.getCount()));
        }
        return builder.build();
    }

    private static boolean hasSupportedOutput(BasicPressurizedReactionRecipe recipe) {
        List<PressurizedReactionRecipeOutput> outputs = recipe.getOutputDefinition();
        if (outputs.size() != 1
                || outputs.getFirst().item().isEmpty()
                && outputs.getFirst().chemical().isEmpty()) return false;
        try {
            Math.multiplyExact(recipe.getEnergyRequired(), (long) recipe.getDuration());
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static TypedRecipePlan reactionPlan(
            BasicPressurizedReactionRecipe recipe,
            HolderLookup.Provider registries
    ) {
        List<PressurizedReactionRecipeOutput> definitions = recipe.getOutputDefinition();
        if (definitions.size() != 1 || definitions.getFirst().item().isEmpty()
                && definitions.getFirst().chemical().isEmpty()) {
            throw new IllegalArgumentException(
                    "Pressurized Reaction compatibility requires one deterministic output");
        }
        PressurizedReactionRecipeOutput definition = definitions.getFirst();
        ItemStack itemOutput = definition.item().copy();
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder()
                .input(itemInput(recipe.getInputSolid(), registries))
                .input(fluidInput(recipe.getInputFluid(), registries))
                .input(chemicalInput(recipe.getInputChemical()));
        if (!itemOutput.isEmpty()) {
            builder.output(TypedRecipeOutput.primary(
                    StorageResourceKey.item(itemOutput, registries), itemOutput.getCount()));
        }
        if (recipe.getEnergyRequired() > 0) {
            builder.input(TypedRecipeInput.consume(
                    StorageResourceKey.neoforgeEnergy(),
                    Math.multiplyExact(
                            recipe.getEnergyRequired(), (long) recipe.getDuration())));
        }
        ChemicalStack chemicalOutput = definition.chemical();
        if (!chemicalOutput.isEmpty()) {
            builder.output(TypedRecipeOutput.primary(
                    MekanismChemicalCompat.key(chemicalOutput), chemicalOutput.getAmount()));
        }
        ItemStack presentationOutput = itemOutput;
        if (presentationOutput.isEmpty()) {
            presentationOutput = chemicalPresentation(chemicalOutput);
        }
        return builder
                .presentationOutput(presentationOutput)
                .layout(2, 2, false)
                .build();
    }

    private static TypedRecipeInput itemInput(
            ItemStackIngredient ingredient,
            HolderLookup.Provider registries
    ) {
        LinkedHashMap<StorageResourceKey, Long> alternatives = new LinkedHashMap<>();
        for (ItemStack representation : ingredient.getRepresentations()) {
            if (representation.isEmpty()) continue;
            long amount = ingredient.getNeededAmount(representation);
            if (amount <= 0) continue;
            StorageResourceKey key = StorageResourceKey.item(
                    representation.copyWithCount(1), registries);
            alternatives.merge(key, amount, MekanismRecipeCompat::requireSameAmount);
        }
        return consumeAlternatives(alternatives, "item");
    }

    private static TypedRecipeInput fluidInput(
            FluidStackIngredient ingredient,
            HolderLookup.Provider registries
    ) {
        LinkedHashMap<StorageResourceKey, Long> alternatives = new LinkedHashMap<>();
        for (FluidStack representation : ingredient.getRepresentations()) {
            if (representation.isEmpty()) continue;
            long amount = ingredient.getNeededAmount(representation);
            if (amount <= 0) continue;
            StorageResourceKey key = StorageResourceKey.fluid(
                    representation.copyWithAmount(1), registries);
            alternatives.merge(key, amount, MekanismRecipeCompat::requireSameAmount);
        }
        return consumeAlternatives(alternatives, "fluid");
    }

    private static TypedRecipeInput chemicalInput(ChemicalStackIngredient ingredient) {
        return chemicalInput(ingredient, 1);
    }

    private static TypedRecipeInput chemicalInput(
            ChemicalStackIngredient ingredient,
            long multiplier
    ) {
        LinkedHashMap<StorageResourceKey, Long> alternatives = new LinkedHashMap<>();
        for (ChemicalStack representation : ingredient.getRepresentations()) {
            if (representation.isEmpty()) continue;
            long amount = Math.multiplyExact(
                    ingredient.getNeededAmount(representation), multiplier);
            if (amount <= 0) continue;
            alternatives.merge(
                    MekanismChemicalCompat.key(representation),
                    amount,
                    MekanismRecipeCompat::requireSameAmount);
        }
        return consumeAlternatives(alternatives, "chemical");
    }

    private static TypedRecipeInput consumeAlternatives(
            LinkedHashMap<StorageResourceKey, Long> alternatives,
            String kind
    ) {
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("Mekanism " + kind + " ingredient has no representatives");
        }
        long amount = alternatives.values().iterator().next();
        if (alternatives.values().stream().anyMatch(value -> value != amount)) {
            throw new IllegalArgumentException(
                    "Mekanism " + kind + " ingredient alternatives require different amounts");
        }
        return TypedRecipeInput.consumeAny(List.copyOf(alternatives.keySet()), amount);
    }

    private static long requireSameAmount(long left, long right) {
        if (left != right) {
            throw new IllegalArgumentException(
                    "Duplicate Mekanism ingredient representation changed its required amount");
        }
        return left;
    }

    private static ItemStack fixedItemOutput(List<ItemStack> definitions) {
        if (definitions.size() != 1 || definitions.getFirst().isEmpty()) {
            throw new IllegalArgumentException(
                    "Mekanism recipe compatibility requires one deterministic item output");
        }
        return definitions.getFirst().copy();
    }

    private static ChemicalStack fixedChemicalOutput(List<ChemicalStack> definitions) {
        if (definitions.size() != 1 || definitions.getFirst().isEmpty()) {
            throw new IllegalArgumentException(
                    "Mekanism recipe compatibility requires one deterministic chemical output");
        }
        return definitions.getFirst().copy();
    }

    private static FluidStack fixedFluidOutput(List<FluidStack> definitions) {
        if (definitions.size() != 1 || definitions.getFirst().isEmpty()) {
            throw new IllegalArgumentException(
                    "Mekanism recipe compatibility requires one deterministic fluid output");
        }
        return definitions.getFirst().copy();
    }

    private static ItemStack chemicalPresentation(ChemicalStack output) {
        ItemStack presentation = stationStack("basic_chemical_tank");
        presentation.set(DataComponents.CUSTOM_NAME, output.getTextComponent());
        return presentation;
    }

    private static ItemStack fluidPresentation() {
        return new ItemStack(Items.BUCKET);
    }

    private static ItemStack stationStack(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            throw new IllegalStateException("Loaded Mekanism did not register station item " + id);
        }
        return new ItemStack(item);
    }

    private static String registryPath(String stationPath) {
        return "mekanism_" + Objects.requireNonNull(stationPath, "stationPath");
    }
}
