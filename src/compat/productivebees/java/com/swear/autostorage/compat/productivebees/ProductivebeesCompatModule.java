package com.swear.autostorage.compat.productivebees;

import com.swear.autostorage.MachineCategory;
import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MachineVariant;
import com.swear.autostorage.MachineWorkRate;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import cy.jdkdigital.productivebees.ProductiveBeesConfig;
import cy.jdkdigital.productivebees.init.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Objects;
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ProductivebeesCompatModule implements AutoStorageCompatModule {
    private static final DeferredRegister<MachineDescriptor> MACHINES =
            MachineDescriptorApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<RecipeFamily> RECIPES =
            RecipeFamilyApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<TransformProvider> TRANSFORMS =
            TransformProviderApi.createDeferredRegister(AutoStorageApi.MOD_ID);

    @Override
    public void register(AutoStorageCompatContext context) {
        com.swear.autostorage.ConversionScanner.register(HONEY_PATTERN);
        ResourceLocation generatorId = ResourceLocation.fromNamespaceAndPath(
                AutoStorageApi.MOD_ID, "productivebees_honey_generator");
        MACHINES.register(generatorId.getPath(), () ->
                MachineDescriptor.installableVariants(
                        generatorId,
                        Component.translatable(
                                "gui.auto_storage.station.productivebees_honey_generator"),
                        () -> List.of(MachineVariant.of(
                                new ItemStack(requiredItem(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "productivebees", "honey_generator"))),
                                MachineWorkRate.ONE)),
                        MachineCategory.PROCESS,
                        MachineDescriptorApi.MAX_INSTALLED_COUNT,
                        null));
        TRANSFORMS.register(generatorId.getPath(), () ->
                TransformProvider.of(
                        StorageResourceKindApi.ENERGY_KIND,
                        new ItemStack(Items.REDSTONE),
                        Component.translatable("gui.auto_storage.resource_view.energy"),
                        Component.translatable(
                                "gui.auto_storage.station.productivebees_honey_generator"),
                        HONEY_PATTERN::resolve));
        context.register(addon -> addon
                .machineDescriptors(MACHINES)
                .recipeFamilies(RECIPES)
                .transformProviders(TRANSFORMS));
    }
    private static final HoneyPattern HONEY_PATTERN = new HoneyPattern();
    private static final class HoneyPattern
            implements com.swear.autostorage.ConversionPattern {
        @Override
        public ResourceLocation patternId() {
            return ResourceLocation.fromNamespaceAndPath(
                    "productivebees", "honey_generator");
        }

        @Override
        public TransformProviderApi.Result resolve(ItemStack input) {        int mb;
        ItemStack retained = ItemStack.EMPTY;
        if (input.is(Items.HONEY_BOTTLE)) {
            mb = 250;
            retained = new ItemStack(Items.GLASS_BOTTLE);
        } else if (input.is(Items.HONEY_BLOCK)) {
            mb = 1000;
        } else if (input.is(ModTags.Common.HONEY_BUCKETS)) {
            mb = 1000;
            retained = new ItemStack(Items.BUCKET);
        } else {
            return null;
        }
        int honeyUse = ProductiveBeesConfig.GENERAL.generatorHoneyUse.get();
        int powerGen = ProductiveBeesConfig.GENERAL.generatorPowerGen.get();
        if (honeyUse <= 0 || powerGen <= 0) return null;
        long work = mb / honeyUse;
        if (work <= 0) return null;
        try {
            return new TransformProviderApi.Result(
                    StorageResourceKey.neoforgeEnergy(),
                    Math.multiplyExact(work, powerGen),
                    ResourceLocation.fromNamespaceAndPath(
                            AutoStorageApi.MOD_ID, "productivebees_honey_generator"),
                    work,
                    retained.isEmpty()
                            ? List.of()
                            : List.of(retained));
        } catch (ArithmeticException exception) {
            return null;
        }
        }

        @Override
        public String revisionKey() {
            return ProductiveBeesConfig.GENERAL.generatorHoneyUse.get() + "/" + ProductiveBeesConfig.GENERAL.generatorPowerGen.get();
        }
    }
    private static Item requiredItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("Missing Productive Bees item " + id);
        }
        return item;
    }
}
