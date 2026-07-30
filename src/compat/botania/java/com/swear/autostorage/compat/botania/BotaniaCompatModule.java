package com.swear.autostorage.compat.botania;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.StorageResourceContainerApi;
import com.swear.autostorage.StorageResourceContainerStrategy;
import com.swear.autostorage.StorageResourceKey;
import com.swear.autostorage.StorageResourceKind;
import com.swear.autostorage.StorageResourceKindApi;
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

public final class BotaniaCompatModule implements AutoStorageCompatModule {
    private static final DeferredRegister<MachineDescriptor> MACHINES =
            MachineDescriptorApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<RecipeFamily> RECIPES =
            RecipeFamilyApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<StorageResourceKind> KINDS =
            StorageResourceKindApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<StorageResourceContainerStrategy> CONTAINERS =
            StorageResourceContainerApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final ResourceLocation MANA_REGISTRY_ID =
            ResourceLocation.fromNamespaceAndPath(AutoStorageApi.MOD_ID, "mana");
    private static final ResourceLocation MANA_POWDER_ID =
            ResourceLocation.fromNamespaceAndPath("botania", "mana_powder");

    static {
        KINDS.register(StorageResourceKindApi.BOTANIA_MANA_KIND.getPath(), () ->
                StorageResourceKind.variantless(BotaniaCompatModule::manaRepresentative));
        KINDS.addAlias(StorageResourceKindApi.BOTANIA_MANA_KIND, MANA_REGISTRY_ID);
        CONTAINERS.register("botania_mana", ManaContainerStrategy::new);
    }

    @Override
    public void register(AutoStorageCompatContext context) {
        BotaniaCompat.register(MACHINES, RECIPES);
        context.register(addon -> addon
                .machineDescriptors(MACHINES)
                .recipeFamilies(RECIPES)
                .resourceKinds(KINDS)
                .containerStrategies(CONTAINERS));
    }

    private static ItemStack manaRepresentative() {
        var item = BuiltInRegistries.ITEM.get(MANA_POWDER_ID);
        if (item == Items.AIR) {
            throw new IllegalStateException(
                    "Loaded Botania did not register " + MANA_POWDER_ID);
        }
        ItemStack stack = new ItemStack(item);
        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.translatable("gui.auto_storage.resource.mana"));
        return stack;
    }

    private static final class ManaContainerStrategy
            implements StorageResourceContainerStrategy {
        @Override
        public ResourceLocation kindId() {
            return StorageResourceKindApi.BOTANIA_MANA_KIND;
        }

        @Override
        public Optional<Transfer> planDeposit(
                ItemStack singleContainer,
                HolderLookup.Provider registries
        ) {
            return BotaniaCompat.planContainerDeposit(singleContainer, registries);
        }

        @Override
        public Optional<Transfer> planWithdraw(
                ItemStack singleContainer,
                StorageResourceKey key,
                long maxAmount,
                HolderLookup.Provider registries
        ) {
            return BotaniaCompat.planContainerWithdraw(
                    singleContainer, key, maxAmount, registries);
        }
    }
}
