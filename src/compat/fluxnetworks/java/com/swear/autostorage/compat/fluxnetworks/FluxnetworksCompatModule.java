package com.swear.autostorage.compat.fluxnetworks;

import com.swear.autostorage.MachineDescriptor;
import com.swear.autostorage.MachineDescriptorApi;
import com.swear.autostorage.RecipeFamily;
import com.swear.autostorage.RecipeFamilyApi;
import com.swear.autostorage.RecipeFamilyCost;
import com.swear.autostorage.RecipeFamilyFactories;
import com.swear.autostorage.RecipePresentationKind;
import com.swear.autostorage.SyntheticRecipeCatalogs;
import com.swear.autostorage.WorldStationBlock;
import com.swear.autostorage.WorldStations;
import com.swear.autostorage.api.AutoStorageApi;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import sonar.fluxnetworks.FluxConfig;

public final class FluxnetworksCompatModule implements AutoStorageCompatModule {
    public static final ResourceLocation FLUX_STATION_ID =
            AutoStorageApi.id("flux_station");
    public static final ResourceLocation FLUX_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath("fluxnetworks", "flux_block");
    public static final ResourceLocation FLUX_DUST_ID =
            ResourceLocation.fromNamespaceAndPath("fluxnetworks", "flux_dust");

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(AutoStorageApi.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<MachineDescriptor> MACHINES =
            MachineDescriptorApi.createDeferredRegister(AutoStorageApi.MOD_ID);
    private static final DeferredRegister<RecipeFamily> RECIPES =
            RecipeFamilyApi.createDeferredRegister(AutoStorageApi.MOD_ID);

    private static final DeferredBlock<Block> FLUX_STATION = BLOCKS.register(
            FLUX_STATION_ID.getPath(),
            () -> new WorldStationBlock(
                    BlockBehaviour.Properties.of().strength(3.0F, 6.0F),
                    FLUX_STATION_ID));
    private static final DeferredItem<BlockItem> FLUX_STATION_ITEM = ITEMS.register(
            FLUX_STATION_ID.getPath(),
            () -> new BlockItem(FLUX_STATION.get(), new Item.Properties()));

    @Override
    public void register(AutoStorageCompatContext context) {
        WorldStations.define(
                FLUX_STATION_ID,
                () -> FluxConfig.enableFluxRecipe,
                FluxnetworksCompatModule::hasBase);
        SyntheticRecipeCatalogs.register(new FluxConversionCatalog(
                FLUX_STATION_ID,
                FLUX_DUST_ID));
        RECIPES.register(FLUX_STATION_ID.getPath(), () ->
                RecipeFamilyFactories.singleItemToItem(
                        com.swear.autostorage.WorldStationConversionRecipe.class,
                        () -> com.swear.autostorage.WorldStationConversionRecipe.TYPE,
                        FLUX_STATION_ID,
                        com.swear.autostorage.WorldStationConversionRecipe::input,
                        (recipe, registries) -> recipe.result(),
                        recipe -> RecipeFamilyCost.free(),
                        RecipePresentationKind.WORLD_STATION));
        BLOCKS.register(context.modBus());
        ITEMS.register(context.modBus());
        context.modBus().addListener(FluxnetworksCompatModule::addCreativeItem);
        MACHINES.register(
                FLUX_STATION_ID.getPath(),
                () -> MachineDescriptor.worldStation(
                        FLUX_STATION_ID,
                        Component.translatable("gui.auto_storage.station.flux_station"),
                        FLUX_STATION_ITEM.get().getDefaultInstance(),
                        FLUX_STATION_ID));
        context.register(addon -> addon
                .machineDescriptors(MACHINES)
                .recipeFamilies(RECIPES));
    }

    private static void addCreativeItem(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(
                AutoStorageApi.id("auto_storage"))) {
            event.accept(FLUX_STATION_ITEM.get());
        }
    }

    private static boolean hasBase(net.minecraft.world.level.Level level,
                                   net.minecraft.core.BlockPos pos) {
        var below = level.getBlockState(pos.below(2));
        Block fluxBlock = BuiltInRegistries.BLOCK.get(FLUX_BLOCK_ID);
        return FluxConfig.enableFluxRecipe
                && (below.is(Blocks.BEDROCK)
                        || fluxBlock != Blocks.AIR && below.is(fluxBlock));
    }
}
