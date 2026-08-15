package com.swear.autostorage.compat.generatorgalore;

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineVariant;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.api.AutoStorageApi;
import com.mojang.datafixers.util.Pair;
import cy.jdkdigital.generatorgalore.GeneratorGalore;
import cy.jdkdigital.generatorgalore.common.datamap.SolidFuelMap;
import cy.jdkdigital.generatorgalore.registry.GeneratorRegistry;
import cy.jdkdigital.generatorgalore.util.GeneratorObject;
import cy.jdkdigital.generatorgalore.util.GeneratorUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Locale;

public final class GeneratorgaloreCompat {
    private GeneratorgaloreCompat() {
    }

    private static final List<String> GENERATORS = List.of(
            "copper", "gold", "iron", "diamond", "emerald", "netherite",
            "obsidian", "netherstar", "halitosis", "culinary",
            "enchantment", "ender");

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes,
            DeferredRegister<TransformProvider> transforms
    ) {
        for (String generator : GENERATORS) {
            registerGenerator(machines, transforms, generator);
        }
    }

    private static void registerGenerator(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<TransformProvider> transforms,
            String generator
    ) {
        ResourceLocation generatorId = ResourceLocation.fromNamespaceAndPath(
                AutoStorageApi.MOD_ID, "generatorgalore_" + generator + "_generator");
        ResourceLocation stationItem = ResourceLocation.fromNamespaceAndPath(
                "generatorgalore", generator + "_generator");
        machines.register(generatorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        generatorId,
                        Component.translatable(
                                "gui.auto_storage.station.generatorgalore_"
                                        + generator + "_generator"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(stationItem)),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        transforms.register(generatorId.getPath(), () ->
                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.station.generatorgalore_"
                                        + generator + "_generator"),
                        input -> resolve(
                                ResourceLocation.fromNamespaceAndPath(
                                        "generatorgalore", generator),
                                input)));
    }

    private static TransformProviderApi.Result resolve(
            ResourceLocation generatorId,
            ItemStack input
    ) {
        if (input == null || input.isEmpty()) return null;
        GeneratorObject generator = GeneratorRegistry.generators.get(generatorId);
        if (generator == null) return null;
        GeneratorUtil.FuelType fuelType = generator.getFuelType();
        if (fuelType == GeneratorUtil.FuelType.FLUID
                || fuelType == GeneratorUtil.FuelType.POTION) {
            return null;
        }
        long rate;
        long work;
        SolidFuelMap map = null;
        Block block = generator.getBlockSupplier().get();
        if (block != null) {
            map = block.builtInRegistryHolder().getData(
                    GeneratorGalore.SOLID_FUEL_MAP);
        }
        SolidFuelMap.SolidFuel fuel = null;
        if (map != null) {
            for (SolidFuelMap.SolidFuel candidate : map.fuels()) {
                if (candidate.item().test(input)) {
                    fuel = candidate;
                    break;
                }
            }
        }
        if (fuel != null) {
            rate = fuel.generationRate();
            work = Math.multiplyExact(
                    (long) fuel.burnTime(), (long) fuel.consumptionRate());
        } else if (fuelType == GeneratorUtil.FuelType.ENCHANTMENT) {
            Pair<Float, Integer> pair =
                    GeneratorUtil.calculateEnchantmentGenerationRate(
                            generator, input);
            if (pair == null) return null;
            rate = Math.round(pair.getFirst());
            work = pair.getSecond();
            return new TransformProviderApi.Result(
                    StorageResourceKey.neoforgeEnergy(),
                    safeMultiply(rate, work),
                    ResourceLocation.fromNamespaceAndPath(
                            AutoStorageApi.MOD_ID,
                            "generatorgalore_"
                                    + generatorId.getPath() + "_generator"),
                    work,
                    List.of(new ItemStack(Items.BOOK)));
        } else if (fuelType == GeneratorUtil.FuelType.FOOD) {
            Pair<Float, Integer> pair = GeneratorUtil.calculateFoodGenerationRate(
                    generator, input);
            if (pair == null) return null;
            rate = Math.round(pair.getFirst());
            work = pair.getSecond();
        } else {
            int burnTime = input.getBurnTime(RecipeType.SMELTING);
            if (burnTime <= 0) return null;
            rate = Math.round(generator.getGenerationRate());
            work = Math.round(burnTime * generator.getConsumptionRate());
        }
        if (rate <= 0 || work <= 0) return null;
        long fe = safeMultiply(rate, work);
        List<ItemStack> retained = input.hasCraftingRemainingItem()
                ? List.of(input.getCraftingRemainingItem())
                : List.of();
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                fe,
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorageApi.MOD_ID,
                        "generatorgalore_"
                                + generatorId.getPath() + "_generator"),
                work,
                retained);
    }

    private static long safeMultiply(long rate, long work) {
        try {
            return Math.multiplyExact(rate, work);
        } catch (ArithmeticException exception) {
            return -1;
        }
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException(
                    "Missing Generator Galore item " + id);
        }
        return item;
    }
}
