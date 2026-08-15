package com.swear.autostorage.compat.enderio;

import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.config.machines.common.EnergyConfig;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
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
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.StorageResourceKey;
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
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class EnderioCompat {
    private static final ResourceLocation ALLOY_SMELTER_ITEM =
            ResourceLocation.fromNamespaceAndPath("enderio", "alloy_smelter");
    private static final ResourceLocation ALLOY_SMELTING_TYPE =
            ResourceLocation.fromNamespaceAndPath("enderio", "alloy_smelting");

    private EnderioCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            DeferredRegister<TransformProvider> transformProviders
    ) {
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        Objects.requireNonNull(transformProviders, "transformProviders");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Ender IO descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Ender IO family register targets the wrong registry");
        }
        if (!transformProviders.getRegistryKey().equals(TransformProviderApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Ender IO transform provider register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())
                || !machineDescriptors.getNamespace().equals(
                        transformProviders.getNamespace())) {
            throw new IllegalArgumentException(
                    "Ender IO descriptors, families, and transform providers "
                            + "must share one namespace");
        }

        ResourceLocation stirlingId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "enderio_stirling_generator");
        machineDescriptors.register(stirlingId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        stirlingId,
                        Component.translatable(
                                "gui.auto_storage.station.enderio_stirling_generator"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "enderio", "stirling_generator"))),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        StirlingPattern stirlingPattern = new StirlingPattern();
        com.swear.autostorage.ConversionScanner.register(stirlingPattern);
        transformProviders.register(stirlingId.getPath(), () ->
                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.LIGHTNING_ROD),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.station.enderio_stirling_generator"),
                        stirlingPattern::resolve));

        ResourceLocation descriptorId = ResourceLocation.fromNamespaceAndPath(
                machineDescriptors.getNamespace(), "enderio_alloy_smelting");
        machineDescriptors.register(descriptorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        descriptorId,
                        Component.translatable(
                                "gui.auto_storage.station.enderio_alloy_smelting"),
                        () -> List.of(MachineVariant.derived(
                                new ItemStack(requiredItem(ALLOY_SMELTER_ITEM)),
                                () -> MachineWorkRate.of(baseAlloyUsage(), 1))),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        recipeFamilies.register(descriptorId.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        AlloySmeltingRecipe.class,
                        EnderioCompat::alloySmeltingType,
                        descriptorId,
                        EnderioCompat::supports,
                        EnderioCompat::plan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.energy()),
                        RecipePresentationKind.CRAFTING));
    }

    private static boolean supports(AlloySmeltingRecipe recipe) {
        return recipe != null
                && !recipe.isSmelting()
                && recipe.energy() > 0
                && !recipe.output().isEmpty()
                && !recipe.inputs().isEmpty()
                && recipe.inputs().size() <= 3
                && recipe.inputs().stream().allMatch(EnderioCompat::exactSized);
    }

    private static TypedRecipePlan plan(
            AlloySmeltingRecipe recipe,
            HolderLookup.Provider registries
    ) {
        if (!supports(recipe)) {
            throw new IllegalStateException(
                    "Ender IO Alloy Smelting recipe is outside the accepted contract");
        }
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        for (SizedIngredient input : recipe.inputs()) {
            builder.input(TypedRecipeInput.consumeAny(
                    keys(input.ingredient(), registries), input.count()));
        }
        builder.input(TypedRecipeInput.consume(
                StorageResourceKey.neoforgeEnergy(), recipe.energy()));

        ItemStack output = recipe.output().copy();
        int inputCount = recipe.inputs().size() + 1;
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

    private static boolean exactSized(SizedIngredient sized) {
        return sized != null
                && sized.count() > 0
                && exact(sized.ingredient());
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

    @SuppressWarnings("unchecked")
    private static RecipeType<AlloySmeltingRecipe> alloySmeltingType() {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(ALLOY_SMELTING_TYPE);
        if (type == null) {
            throw new IllegalStateException(
                    "Missing Ender IO recipe type " + ALLOY_SMELTING_TYPE);
        }
        return (RecipeType<AlloySmeltingRecipe>) type;
    }

    private static final class StirlingPattern
            implements com.swear.autostorage.ConversionPattern {
        @Override
        public ResourceLocation patternId() {
            return ResourceLocation.fromNamespaceAndPath(
                    "enderio", "stirling_generator");
        }

        @Override
        public TransformProviderApi.Result resolve(ItemStack input) {
            int burnTime = input.getBurnTime(RecipeType.SMELTING);
            if (burnTime <= 0 || input.hasCraftingRemainingItem()) return null;
            EnergyConfig energy = MachinesConfig.COMMON.ENERGY;
            double speed = energy.STIRLING_GENERATOR_BURN_SPEED.get();
            double efficiency = energy.STIRLING_GENERATOR_FUEL_EFFICIENCY_BASE.get();
            int production = energy.STIRLING_GENERATOR_PRODUCTION.get();
            long duration = (long) Math.floor(
                    burnTime * speed * (efficiency / 100.0));
            if (duration <= 0 || production <= 0) return null;
            try {
                return new TransformProviderApi.Result(
                        StorageResourceKey.neoforgeEnergy(),
                        Math.multiplyExact(duration, production),
                        ResourceLocation.fromNamespaceAndPath(
                                AutoStorageApi.MOD_ID,
                                "enderio_stirling_generator"),
                        duration);
            } catch (ArithmeticException exception) {
                return null;
            }
        }

        @Override
        public String revisionKey() {
            EnergyConfig energy = MachinesConfig.COMMON.ENERGY;
            return energy.STIRLING_GENERATOR_BURN_SPEED.get() + "/"
                    + energy.STIRLING_GENERATOR_FUEL_EFFICIENCY_BASE.get()
                    + "/" + energy.STIRLING_GENERATOR_PRODUCTION.get();
        }
    }

    private static int baseAlloyUsage() {
        int usage = MachinesConfig.COMMON.ENERGY.ALLOY_SMELTER_USAGE.get();
        if (usage <= 0) {
            throw new IllegalStateException(
                    "Ender IO Alloy Smelter base usage must be positive");
        }
        return usage;
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Ender IO station item " + id);
        }
        return item;
    }
}
