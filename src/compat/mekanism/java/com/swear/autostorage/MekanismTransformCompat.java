package com.swear.autostorage;

import com.swear.autostorage.api.AutoStorageApi;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.chemical.attribute.ChemicalFuel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class MekanismTransformCompat {
    private MekanismTransformCompat() {
    }

    private static final long MAX_GAS_BURN_RATE = 256;
    private static final long REDSTONE_DUST_FE = 10_000;
    private static final long REDSTONE_BLOCK_FE = 90_000;
    private static final TagKey<Item> REDSTONE_DUST_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "dusts/redstone"));
    private static final TagKey<Item> REDSTONE_BLOCK_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "storage_blocks/redstone"));

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<TransformProvider> transforms
    ) {
        transforms.register("mekanism_energy_conversion", () ->
                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.source.mekanism_energy_conversion"),
                        MekanismTransformCompat::energyConversionTransform));
        ResourceLocation generatorId = ResourceLocation.fromNamespaceAndPath(
                AutoStorageApi.MOD_ID, "mekanism_gas_generator");
        machines.register(generatorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        generatorId,
                        Component.translatable(
                                "gui.auto_storage.station.mekanism_gas_generator"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "mekanismgenerators",
                                                "gas_burning_generator"))),
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
                                "gui.auto_storage.station.mekanism_gas_generator"),
                        MekanismTransformCompat::gasTransform));
    }

    private static TransformProviderApi.Result energyConversionTransform(
            ItemStack input
    ) {
        if (input == null || input.isEmpty()) return null;
        long energy = 0;
        if (input.is(REDSTONE_DUST_TAG)) {
            energy = REDSTONE_DUST_FE;
        } else if (input.is(REDSTONE_BLOCK_TAG)) {
            energy = REDSTONE_BLOCK_FE;
        } else {
            return null;
        }
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                energy,
                null,
                0);
    }

    private static TransformProviderApi.Result gasTransform(ItemStack input) {
        if (input == null || input.isEmpty()) return null;
        IChemicalHandler handler = input.getCapability(
                MekanismChemicalCompat.CHEMICAL_ITEM_CAPABILITY);
        if (handler == null || handler.getChemicalTanks() <= 0) return null;
        ChemicalStack contents = handler.getChemicalInTank(0);
        if (contents == null || contents.isEmpty() || contents.getAmount() <= 0) {
            return null;
        }
        ChemicalFuel fuel = contents.getData(
                IMekanismDataMapTypes.INSTANCE.chemicalFuel());
        if (fuel == null || fuel.burnTicks() <= 0 || fuel.energyPerTick() <= 0) {
            return null;
        }
        long amount = contents.getAmount();
        long work = (amount + MAX_GAS_BURN_RATE - 1) / MAX_GAS_BURN_RATE;
        long fe;
        try {
            fe = Math.multiplyExact(amount, fuel.energyDensity());
        } catch (ArithmeticException exception) {
            return null;
        }
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                fe,
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorageApi.MOD_ID, "mekanism_gas_generator"),
                work,
                List.of(new ItemStack(input.getItem())));
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException(
                    "Loaded Mekanism Generators did not register " + id);
        }
        return item;
    }
}
