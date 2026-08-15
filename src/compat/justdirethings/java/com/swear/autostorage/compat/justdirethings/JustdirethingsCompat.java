package com.swear.autostorage.compat.justdirethings;

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
import com.direwolf20.justdirethings.setup.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class JustdirethingsCompat {
    private JustdirethingsCompat() {
    }

    public static void register(
            DeferredRegister<MachineDescriptor> machines,
            DeferredRegister<RecipeFamily> recipes,
            DeferredRegister<TransformProvider> transforms
    ) {
        ResourceLocation generatorId = ResourceLocation.fromNamespaceAndPath(
                AutoStorageApi.MOD_ID, "justdirethings_generator_t1");
        machines.register(generatorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        generatorId,
                        Component.translatable(
                                "gui.auto_storage.station.justdirethings_generator_t1"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "justdirethings", "generatort1"))),
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
                                "gui.auto_storage.station.justdirethings_generator_t1"),
                        JustdirethingsCompat::generatorTransform));
    }

    private static TransformProviderApi.Result generatorTransform(ItemStack input) {
        int burnTime = input.getBurnTime(RecipeType.SMELTING);
        if (burnTime <= 0) return null;
        int multiplier = Config.GENERATOR_T1_BURN_SPEED_MULTIPLIER.get();
        int fePerFuelTick = Config.GENERATOR_T1_FE_PER_FUEL_TICK.get();
        if (multiplier <= 0 || fePerFuelTick <= 0) return null;
        long work = burnTime / multiplier;
        if (work <= 0) return null;
        long fe;
        try {
            fe = Math.multiplyExact((long) burnTime, fePerFuelTick);
        } catch (ArithmeticException exception) {
            return null;
        }
        List<ItemStack> retained = input.hasCraftingRemainingItem()
                ? List.of(input.getCraftingRemainingItem())
                : List.of();
        return new TransformProviderApi.Result(
                StorageResourceKey.neoforgeEnergy(),
                fe,
                ResourceLocation.fromNamespaceAndPath(
                        AutoStorageApi.MOD_ID, "justdirethings_generator_t1"),
                work,
                retained);
    }

    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Just Dire Things item " + id);
        }
        return item;
    }
}
