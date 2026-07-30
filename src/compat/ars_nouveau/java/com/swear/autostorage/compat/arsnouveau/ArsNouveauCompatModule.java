package com.swear.autostorage.compat.arsnouveau;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.StorageResourceBlockApi;
import com.swear.autostorage.StorageResourceBlockStrategy;
import com.swear.autostorage.StorageResourceHandler;
import com.swear.autostorage.StorageResourceKind;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

public final class ArsNouveauCompatModule implements AutoStorageCompatModule {
    private static final DeferredRegister<MachineDescriptor> MACHINES =
            MachineDescriptorApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<RecipeFamily> RECIPES =
            RecipeFamilyApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<StorageResourceKind> KINDS =
            StorageResourceKindApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<StorageResourceBlockStrategy> BLOCKS =
            StorageResourceBlockApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final ResourceLocation SOURCE_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(AutoStorageApi.MOD_ID, "source");
    private static final ResourceLocation SOURCE_GEM_ID =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_gem");

    static {
        KINDS.register(StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND.getPath(), () ->
                StorageResourceKind.variantless(
                        ArsNouveauCompatModule::sourceRepresentative));
        KINDS.addAlias(
                StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND,
                SOURCE_REGISTRY_ID);
        BLOCKS.register("ars_nouveau_source", SourceBlockStrategy::new);
    }

    @Override
    public void register(AutoStorageCompatContext context) {
        ArsNouveauCompat.register(MACHINES, RECIPES);
        context.register(addon -> addon
                .machineDescriptors(MACHINES)
                .recipeFamilies(RECIPES)
                .resourceKinds(KINDS)
                .blockStrategies(BLOCKS));
    }

    private static ItemStack sourceRepresentative() {
        var item = BuiltInRegistries.ITEM.get(SOURCE_GEM_ID);
        if (item == Items.AIR) {
            throw new IllegalStateException(
                    "Loaded Ars Nouveau did not register " + SOURCE_GEM_ID);
        }
        ItemStack stack = new ItemStack(item);
        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.translatable("gui.auto_storage.resource.source"));
        return stack;
    }

    private static final class SourceBlockStrategy
            implements StorageResourceBlockStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.ARS_NOUVEAU_SOURCE_KIND;
        }

        @Override
        public Optional<StorageResourceHandler> find(
                Level level,
                BlockPos pos,
                Direction side
        ) {
            return ArsNouveauCompat.findSourceBlockHandler(level, pos, side);
        }
    }
}
