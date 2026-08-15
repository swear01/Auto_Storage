package com.swear.autostorage;

import com.swear.autostorage.compat.EmiRecipeDiagramBootstrap;
import com.swear.autostorage.compat.EmiTerminalSearchSynchronizer;
import com.swear.autostorage.compat.JeiRecipeDiagramBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ClientSetup {

    enum ViewerBinding {
        EMI,
        JEI,
        NONE
    }

    public static void register(IEventBus modEventBus) {
        if (!ModList.get().isLoaded("emi") && !ModList.get().isLoaded("jei")) {
            throw new IllegalStateException("Auto Storage requires EMI or JEI on clients");
        }
        registerEnergyGlyph();
        modEventBus.addListener(ClientSetup::registerScreens);
        modEventBus.addListener(
                EventPriority.LOWEST,
                RegisterMenuScreensEvent.class,
                ignored -> TerminalResourceRendererApi.freeze());
        modEventBus.addListener(ClientSetup::addFusionConnectedCasingPack);
    }

    static ViewerBinding selectedViewerBinding() {
        if (ModList.get().isLoaded("emi")) {
            return ViewerBinding.EMI;
        }
        if (ModList.get().isLoaded("jei") && JeiRecipeDiagramBootstrap.isRuntimeReady()) {
            return ViewerBinding.JEI;
        }
        return ViewerBinding.NONE;
    }

    static long viewerGeneration() {
        return selectedViewerBinding() == ViewerBinding.JEI
                ? JeiRecipeDiagramBootstrap.runtimeGeneration()
                : 0L;
    }

    static RecipeDiagramRenderer createRecipeDiagramRenderer() {
        return switch (selectedViewerBinding()) {
            case EMI -> EmiRecipeDiagramBootstrap.create();
            case JEI -> JeiRecipeDiagramBootstrap.createRenderer();
            case NONE -> new NativeRecipeDiagramRenderer();
        };
    }

    static TerminalSearchSynchronizer createTerminalSearchSynchronizer() {
        return switch (selectedViewerBinding()) {
            case EMI -> new EmiTerminalSearchSynchronizer();
            case JEI -> JeiRecipeDiagramBootstrap.createSearchSynchronizer();
            case NONE -> TerminalSearchSynchronizer.NONE;
        };
    }

    private static void registerEnergyGlyph() {
        TerminalResourceRendererApi.register(
                StorageResourceKindApi.ENERGY_KIND,
                net.minecraft.client.gui.GuiGraphics.class,
                ClientSetup::renderEnergyGlyph);
    }

    private static boolean renderEnergyGlyph(
            Object graphics,
            StorageResourceKey key,
            long amount,
            int x,
            int y,
            float partialTick
    ) {
        if (!(graphics instanceof net.minecraft.client.gui.GuiGraphics guiGraphics)
                || !key.kindId().equals(StorageResourceKindApi.ENERGY_KIND)) {
            return false;
        }
        guiGraphics.blit(
                ResourceLocation.fromNamespaceAndPath(
                        com.swear.autostorage.api.AutoStorageApi.MOD_ID,
                        "textures/gui/energy_icon.png"),
                x, y, 0, 0, 16, 16, 16, 16);
        return true;
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.<StorageTerminalMenu, StorageTerminalScreen<StorageTerminalMenu>>register(
                AutoStorage.STORAGE_TERMINAL_MENU.get(),
                (menu, inv, title) -> new StorageTerminalScreen<>(menu, inv, title));
        event.<CraftingTerminalMenu, CraftingTerminalScreen>register(
                AutoStorage.CRAFTING_TERMINAL_MENU.get(),
                (menu, inv, title) -> new CraftingTerminalScreen(menu, inv, title));
        event.register(AutoStorage.BUS_CONFIGURATION_MENU.get(), BusConfigurationScreen::new);
    }

    private static void addFusionConnectedCasingPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !ModList.get().isLoaded("fusion")) return;
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(AutoStorage.MODID, "resourcepacks/fusion_connected_casing"),
                PackType.CLIENT_RESOURCES,
                Component.literal("Auto Storage: Fusion connected casing"),
                PackSource.DEFAULT,
                true,
                Pack.Position.TOP);
    }
}
