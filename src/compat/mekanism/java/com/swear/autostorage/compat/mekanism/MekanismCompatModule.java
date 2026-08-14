package com.swear.autostorage.compat.mekanism;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.MekanismChemicalCompat;
import com.swear.autostorage.MekanismChemicalClientCompat;
import com.swear.autostorage.MekanismRecipeCompat;
import com.swear.autostorage.MekanismTransformCompat;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.StorageResourceBlockApi;
import com.swear.autostorage.StorageResourceBlockStrategy;
import com.swear.autostorage.StorageResourceContainerApi;
import com.swear.autostorage.StorageResourceContainerStrategy;
import com.swear.autostorage.StorageResourceHandler;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKind;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.TransformProvider;
import com.swear.autostorage.TransformProviderApi;
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Optional;

public final class MekanismCompatModule implements AutoStorageCompatModule {
    private static final DeferredRegister<MachineDescriptor> MACHINES =
            MachineDescriptorApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<RecipeFamily> RECIPES =
            RecipeFamilyApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<TransformProvider> TRANSFORMS =
            TransformProviderApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<StorageResourceKind> KINDS =
            StorageResourceKindApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<StorageResourceContainerStrategy> CONTAINERS =
            StorageResourceContainerApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<StorageResourceBlockStrategy> BLOCKS =
            StorageResourceBlockApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final ResourceLocation CHEMICAL_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(AutoStorageApi.MOD_ID, "chemical");
    private static final ResourceLocation CHEMICAL_TANK_ID =
            ResourceLocation.fromNamespaceAndPath("mekanism", "basic_chemical_tank");

    static {
        KINDS.register(StorageResourceKindApi.CHEMICAL_KIND.getPath(), () ->
                StorageResourceKind.variantless(
                        MekanismCompatModule::chemicalRepresentative));
        KINDS.addAlias(StorageResourceKindApi.CHEMICAL_KIND, CHEMICAL_REGISTRY_ID);
        CONTAINERS.register("mekanism_chemical", ChemicalContainerStrategy::new);
        BLOCKS.register("mekanism_chemical", ChemicalBlockStrategy::new);
    }

    @Override
    public void register(AutoStorageCompatContext context) {
        MekanismRecipeCompat.register(MACHINES, RECIPES);
        MekanismTransformCompat.register(MACHINES, TRANSFORMS);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MekanismChemicalClientCompat.register();
        }
        context.register(addon -> addon
                .machineDescriptors(MACHINES)
                .recipeFamilies(RECIPES)
                .transformProviders(TRANSFORMS)
                .resourceKinds(KINDS)
                .containerStrategies(CONTAINERS)
                .blockStrategies(BLOCKS)
                .capabilities(MekanismChemicalCompat::register));
    }

    private static ItemStack chemicalRepresentative() {
        var item = BuiltInRegistries.ITEM.get(CHEMICAL_TANK_ID);
        if (item == Items.AIR) {
            throw new IllegalStateException(
                    "Loaded Mekanism did not register " + CHEMICAL_TANK_ID);
        }
        return new ItemStack(item);
    }

    private static final class ChemicalContainerStrategy
            implements StorageResourceContainerStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.CHEMICAL_KIND;
        }

        @Override
        public Optional<Transfer> planDeposit(
                ItemStack singleContainer,
                HolderLookup.Provider registries
        ) {
            return MekanismChemicalCompat.planContainerDeposit(
                    singleContainer, registries);
        }

        @Override
        public Optional<Transfer> planWithdraw(
                ItemStack singleContainer,
                StorageResourceKey key,
                long maxAmount,
                HolderLookup.Provider registries
        ) {
            return MekanismChemicalCompat.planContainerWithdraw(
                    singleContainer, key, maxAmount, registries);
        }
    }

    private static final class ChemicalBlockStrategy
            implements StorageResourceBlockStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.CHEMICAL_KIND;
        }

        @Override
        public Optional<StorageResourceHandler> find(
                Level level,
                BlockPos pos,
                Direction side
        ) {
            return MekanismChemicalCompat.findBlockHandler(level, pos, side);
        }
    }
}
