package com.swear.autostorage.compat.rftoolspower;

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
import mcjty.rftoolspower.modules.generator.CoalGeneratorConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class RftoolspowerCompat {
    private RftoolspowerCompat() {
    }

    private static final int COAL_BLOCK_FACTOR = 9;

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes,
            DeferredRegister<TransformProvider> transforms
    ) {
        ResourceLocation generatorId = ResourceLocation.fromNamespaceAndPath(
                AutoStorageApi.MOD_ID, "rftoolspower_coal_generator");
        machines.register(generatorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        generatorId,
                        Component.translatable(
                                "gui.auto_storage.station.rftoolspower_coal_generator"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "rftoolspower", "coalgenerator"))),
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
                                "gui.auto_storage.station.rftoolspower_coal_generator"),
                        RftoolspowerCompat::coalTransform));
    }

    private static TransformProviderApi.Result coalTransform(ItemStack input) {
        int work = CoalGeneratorConfig.TICKSPERCOAL.get();
        int factor = 1;
        if (input.is(Items.COAL) || input.is(Items.CHARCOAL)) {
            // accepted
        } else if (input.is(Items.COAL_BLOCK)) {
            factor = COAL_BLOCK_FACTOR;
        } else {
            return null;
        }
        long scaledWork = Math.multiplyExact((long) work, factor);
        long fe = Math.multiplyExact(
                scaledWork, CoalGeneratorConfig.RFPERTICK.get());
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                fe,
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorageApi.MOD_ID, "rftoolspower_coal_generator"),
                scaledWork);
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing RFTools Power item " + id);
        }
        return item;
    }
}
