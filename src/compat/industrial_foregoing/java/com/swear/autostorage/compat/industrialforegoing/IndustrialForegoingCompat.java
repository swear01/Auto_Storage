package com.swear.autostorage.compat.industrialforegoing;

import com.swear.autostorage.MachineCategory;
import com.buuz135.industrial.config.machine.generator.PitifulGeneratorConfig;
import com.buuz135.industrial.config.machine.core.DissolutionChamberConfig;
import com.buuz135.industrial.config.machine.resourceproduction.MaterialStoneWorkFactoryConfig;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.recipe.CrusherRecipe;
import com.buuz135.industrial.recipe.DissolutionChamberRecipe;
import com.buuz135.industrial.recipe.StoneWorkGenerateRecipe;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IndustrialForegoingCompat {
    private static final String MOD_ID = "industrialforegoing";

    private IndustrialForegoingCompat() {
    }

    private static final long MYCELIAL_FURNACE_FE_PER_TICK = 80;

    public static void register(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            DeferredRegister<RecipeFamily> recipeFamilies,
            DeferredRegister<TransformProvider> transformProviders
    ) {
        requireRegisters(machineDescriptors, recipeFamilies, transformProviders);
        ResourceLocation pitifulId = id(
                machineDescriptors.getNamespace(), "industrial_foregoing_pitiful_generator");
        registerStation(
                machineDescriptors,
                pitifulId,
                "pitiful_generator");
        transformProviders.register(pitifulId.getPath(), () ->
                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.station.industrial_foregoing_pitiful_generator"),
                        IndustrialForegoingCompat::pitifulGeneratorTransform));
        ResourceLocation furnaceId = id(
                machineDescriptors.getNamespace(),
                "industrial_foregoing_mycelial_furnace");
        registerStation(machineDescriptors, furnaceId, "mycelial_furnace");
        transformProviders.register(furnaceId.getPath(), () ->
                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.station.industrial_foregoing_mycelial_furnace"),
                        IndustrialForegoingCompat::mycelialFurnaceTransform));
        String namespace = machineDescriptors.getNamespace();
        ResourceLocation dissolution = id(namespace, "industrial_foregoing_dissolution_chamber");
        ResourceLocation stonework = id(
                namespace, "industrial_foregoing_material_stonework_factory");

        registerStation(machineDescriptors, dissolution, "dissolution_chamber");
        registerStation(machineDescriptors, stonework, "material_stonework_factory");
        recipeFamilies.register(dissolution.getPath(), () ->
                RecipeFamilyFactories.deterministicResources(
                        DissolutionChamberRecipe.class,
                        ModuleCore.DISSOLUTION_TYPE::get,
                        dissolution,
                        IndustrialForegoingCompat::supportsDissolution,
                        IndustrialForegoingCompat::dissolutionPlan,
                        recipe -> RecipeFamilyCost.stationWork(recipe.processingTime),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register("industrial_foregoing_stonework_generate", () ->
                RecipeFamilyFactories.deterministicResources(
                        StoneWorkGenerateRecipe.class,
                        ModuleCore.STONEWORK_GENERATE_TYPE::get,
                        stonework,
                        IndustrialForegoingCompat::supportsStonework,
                        IndustrialForegoingCompat::stoneworkPlan,
                        recipe -> RecipeFamilyCost.stationWork(
                                MaterialStoneWorkFactoryConfig.maxProgress),
                        RecipePresentationKind.CRAFTING));
        recipeFamilies.register("industrial_foregoing_crusher", () ->
                RecipeFamilyFactories.deterministicResources(
                        CrusherRecipe.class,
                        ModuleCore.CRUSHER_TYPE::get,
                        stonework,
                        IndustrialForegoingCompat::supportsCrusher,
                        IndustrialForegoingCompat::crusherPlan,
                        recipe -> RecipeFamilyCost.stationWork(
                                MaterialStoneWorkFactoryConfig.maxProgress),
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
                    "Industrial Foregoing transform provider register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(transformProviders.getNamespace())) {
            throw new IllegalArgumentException(
                    "Industrial Foregoing descriptors and transform providers must share one namespace");
        }
        Objects.requireNonNull(machineDescriptors, "machineDescriptors");
        Objects.requireNonNull(recipeFamilies, "recipeFamilies");
        if (!machineDescriptors.getRegistryKey().equals(MachineDescriptorApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Industrial Foregoing descriptor register targets the wrong registry");
        }
        if (!recipeFamilies.getRegistryKey().equals(RecipeFamilyApi.REGISTRY_KEY)) {
            throw new IllegalArgumentException(
                    "Industrial Foregoing family register targets the wrong registry");
        }
        if (!machineDescriptors.getNamespace().equals(recipeFamilies.getNamespace())) {
            throw new IllegalArgumentException(
                    "Industrial Foregoing descriptors and families must share one namespace");
        }
    }

    private static void registerStation(
            DeferredRegister<MachineDescriptor> machineDescriptors,
            ResourceLocation descriptorId,
            String itemPath
    ) {
        machineDescriptors.register(descriptorId.getPath(), () -> {
            Item item = requiredItem(itemPath);
            return MachineDescriptor.installableVariants(
                    descriptorId,
                    new ItemStack(item).getHoverName(),
                    () -> List.of(MachineVariant.of(new ItemStack(item), MachineWorkRate.ONE)),
                    MachineCategory.PROCESS,
                    MachineDescriptorApi.MAX_INSTALLED_COUNT,
                    null);
        });
    }

    private static boolean supportsDissolution(DissolutionChamberRecipe recipe) {
        ItemStack output = recipe.output.orElse(ItemStack.EMPTY);
        return recipe.processingTime > 0
                && DissolutionChamberConfig.powerPerTick > 0
                && !output.isEmpty()
                && hasPassiveCraftHook(output)
                && recipe.input != null
                && recipe.input.stream().allMatch(IndustrialForegoingCompat::exact)
                && exact(recipe.inputFluid)
                && recipe.outputFluid.map(IndustrialForegoingCompat::exact).orElse(true)
                && groupedItemInputCount(recipe.input) + 2 <= 9;
    }

    private static TypedRecipePlan dissolutionPlan(
            DissolutionChamberRecipe recipe,
            HolderLookup.Provider registries
    ) {
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        groupedItemInputs(recipe.input, registries).forEach(builder::input);
        builder.input(consumedFluid(recipe.inputFluid, registries));
        builder.input(TypedRecipeInput.consume(
                StorageResourceKey.neoforgeEnergy(),
                (long) recipe.processingTime * DissolutionChamberConfig.powerPerTick));

        ItemStack output = recipe.output.orElseThrow().copy();
        builder.output(TypedRecipeOutput.primary(
                StorageResourceKey.item(output.copyWithCount(1), registries),
                output.getCount()));
        recipe.outputFluid.filter(stack -> !stack.isEmpty()).ifPresent(stack ->
                builder.output(TypedRecipeOutput.remainder(
                        StorageResourceKey.fluid(stack.copyWithAmount(1), registries),
                        stack.getAmount())));
        int inputs = groupedItemInputCount(recipe.input) + 2;
        int width = Math.min(3, inputs);
        return builder
                .presentationOutput(output)
                .layout(width, (inputs + width - 1) / width, true)
                .build();
    }

    private static boolean supportsStonework(StoneWorkGenerateRecipe recipe) {
        return recipe.output != null
                && !recipe.output.isEmpty()
                && MaterialStoneWorkFactoryConfig.maxProgress > 0
                && MaterialStoneWorkFactoryConfig.powerPerTick > 0
                && validThreshold(recipe.waterNeed, recipe.waterConsume)
                && validThreshold(recipe.lavaNeed, recipe.lavaConsume);
    }

    private static TypedRecipePlan stoneworkPlan(
            StoneWorkGenerateRecipe recipe,
            HolderLookup.Provider registries
    ) {
        TypedRecipePlan.Builder builder = TypedRecipePlan.builder();
        addThreshold(builder, Fluids.WATER, recipe.waterNeed, recipe.waterConsume, registries);
        addThreshold(builder, Fluids.LAVA, recipe.lavaNeed, recipe.lavaConsume, registries);
        builder.input(TypedRecipeInput.consume(
                StorageResourceKey.neoforgeEnergy(),
                stoneworkEnergy()));
        ItemStack output = recipe.output.copy();
        int inputs = (recipe.waterNeed > 0 ? 1 : 0)
                + (recipe.lavaNeed > 0 ? 1 : 0)
                + 1;
        int width = Math.min(3, inputs);
        return builder
                .presentationOutput(output)
                .layout(width, (inputs + width - 1) / width, true)
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output.copyWithCount(1), registries),
                        output.getCount()))
                .build();
    }

    private static boolean supportsCrusher(CrusherRecipe recipe) {
        return exact(recipe.input)
                && exact(recipe.output)
                && representatives(recipe.output).size() == 1
                && MaterialStoneWorkFactoryConfig.maxProgress > 0
                && MaterialStoneWorkFactoryConfig.powerPerTick > 0;
    }

    private static TypedRecipePlan crusherPlan(
            CrusherRecipe recipe,
            HolderLookup.Provider registries
    ) {
        List<StorageResourceKey> inputs = itemKeys(recipe.input, registries);
        ItemStack output = representatives(recipe.output).getFirst().copy();
        return TypedRecipePlan.builder()
                .input(TypedRecipeInput.consumeAny(inputs, 1))
                .input(TypedRecipeInput.consume(
                        StorageResourceKey.neoforgeEnergy(), stoneworkEnergy()))
                .presentationOutput(output)
                .layout(2, 1, true)
                .output(TypedRecipeOutput.primary(
                        StorageResourceKey.item(output.copyWithCount(1), registries),
                        output.getCount()))
                .build();
    }

    private static List<TypedRecipeInput> groupedItemInputs(
            List<Ingredient> ingredients,
            HolderLookup.Provider registries
    ) {
        LinkedHashMap<List<StorageResourceKey>, Long> grouped = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            grouped.merge(itemKeys(ingredient, registries), 1L, Math::addExact);
        }
        return grouped.entrySet().stream()
                .map(entry -> TypedRecipeInput.consumeAny(
                        entry.getKey(), entry.getValue()))
                .toList();
    }

    private static int groupedItemInputCount(List<Ingredient> ingredients) {
        return Math.toIntExact(ingredients.stream()
                .map(IndustrialForegoingCompat::representatives)
                .map(stacks -> stacks.stream().map(ItemStackKey::new).toList())
                .distinct()
                .count());
    }

    private static TypedRecipeInput consumedFluid(
            SizedFluidIngredient ingredient,
            HolderLookup.Provider registries
    ) {
        List<StorageResourceKey> alternatives = Arrays.stream(ingredient.getFluids())
                .map(stack -> StorageResourceKey.fluid(
                        stack.copyWithAmount(1), registries))
                .distinct()
                .toList();
        return TypedRecipeInput.consumeAny(alternatives, ingredient.amount());
    }

    private static void addThreshold(
            TypedRecipePlan.Builder builder,
            Fluid fluid,
            int need,
            int consume,
            HolderLookup.Provider registries
    ) {
        if (need == 0) return;
        StorageResourceKey key = StorageResourceKey.fluid(
                new FluidStack(fluid, 1), registries);
        builder.input(consume == 0
                ? TypedRecipeInput.catalyst(key, need)
                : TypedRecipeInput.consume(key, consume));
    }

    private static boolean validThreshold(int need, int consume) {
        return need >= 0
                && consume >= 0
                && ((need == 0 && consume == 0)
                || (need > 0 && (consume == 0 || consume == need)));
    }

    private static long stoneworkEnergy() {
        return (long) MaterialStoneWorkFactoryConfig.maxProgress
                * MaterialStoneWorkFactoryConfig.powerPerTick;
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

    private static boolean exact(FluidStack stack) {
        return stack != null && !stack.isEmpty() && stack.getAmount() > 0;
    }

    private static List<StorageResourceKey> itemKeys(
            Ingredient ingredient,
            HolderLookup.Provider registries
    ) {
        return representatives(ingredient).stream()
                .map(stack -> StorageResourceKey.item(stack, registries))
                .distinct()
                .toList();
    }

    private static List<ItemStack> representatives(Ingredient ingredient) {
        LinkedHashMap<ItemStackKey, ItemStack> unique = new LinkedHashMap<>();
        if (ingredient != null) {
            Arrays.stream(ingredient.getItems())
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> stack.copyWithCount(1))
                    .forEach(stack -> unique.putIfAbsent(new ItemStackKey(stack), stack));
        }
        return List.copyOf(unique.values());
    }

    private static boolean hasPassiveCraftHook(ItemStack output) {
        try {
            Method method = output.getItem().getClass().getMethod(
                    "onCraftedBy", ItemStack.class, Level.class, Player.class);
            return method.getDeclaringClass() == Item.class;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Minecraft Item.onCraftedBy signature changed", exception);
        }
    }


    private static TransformProviderApi.Result mycelialFurnaceTransform(ItemStack input) {
        if (input == null || input.isEmpty()) return null;
        int burnTime = input.getBurnTime(RecipeType.SMELTING);
        if (burnTime <= 0) return null;
        long fe = Math.multiplyExact((long) burnTime, MYCELIAL_FURNACE_FE_PER_TICK);
        List<ItemStack> retained = input.hasCraftingRemainingItem()
                ? List.of(input.getCraftingRemainingItem())
                : List.of();
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                fe,
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorageApi.MOD_ID, "industrial_foregoing_mycelial_furnace"),
                burnTime,
                retained);
    }

    private static TransformProviderApi.Result pitifulGeneratorTransform(ItemStack input) {
        int burnTime = input.getBurnTime(RecipeType.SMELTING);
        int energyPerTick = PitifulGeneratorConfig.powerPerTick;
        if (burnTime <= 0 || energyPerTick <= 0 || input.hasCraftingRemainingItem()) {
            return null;
        }
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                Math.multiplyExact((long) burnTime, energyPerTick),
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorageApi.MOD_ID, "industrial_foregoing_pitiful_generator"),
                burnTime);
    }

    private static Item requiredItem(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Industrial Foregoing station item " + id);
        }
        return item;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private record ItemStackKey(ItemStack stack) {
        private ItemStackKey {
            stack = stack.copyWithCount(1);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ItemStackKey key
                    && ItemStack.isSameItemSameComponents(stack, key.stack);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack);
        }
    }
}
