package com.swearprom.magicstorage.magic_storage;

import com.swearprom.magicstorage.magic_storage.compat.EmiRecipeDiagramBootstrap;
import com.swearprom.magicstorage.magic_storage.compat.EmiTerminalSearchSynchronizer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ClientSetup {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientSetup::registerScreens);
        modEventBus.addListener(ClientSetup::addFusionConnectedCasingPack);
    }

    static RecipeDiagramRenderer createRecipeDiagramRenderer() {
        if (ModList.get().isLoaded("emi")) {
            return EmiRecipeDiagramBootstrap.create();
        }
        return new NativeRecipeDiagramRenderer();
    }

    static TerminalSearchSynchronizer createTerminalSearchSynchronizer() {
        if (ModList.get().isLoaded("emi")) {
            return new EmiTerminalSearchSynchronizer();
        }
        return TerminalSearchSynchronizer.NONE;
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.<StorageTerminalMenu, StorageTerminalScreen<StorageTerminalMenu>>register(
                MagicStorage.STORAGE_TERMINAL_MENU.get(),
                (menu, inv, title) -> new StorageTerminalScreen<>(menu, inv, title));
        event.<CraftingTerminalMenu, CraftingTerminalScreen>register(
                MagicStorage.CRAFTING_TERMINAL_MENU.get(),
                (menu, inv, title) -> new CraftingTerminalScreen(menu, inv, title));
        event.register(MagicStorage.BUS_CONFIGURATION_MENU.get(), BusConfigurationScreen::new);
    }

    private static void addFusionConnectedCasingPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !ModList.get().isLoaded("fusion")) return;
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(MagicStorage.MODID, "resourcepacks/fusion_connected_casing"),
                PackType.CLIENT_RESOURCES,
                Component.literal("Magic Storage: Fusion connected casing"),
                PackSource.DEFAULT,
                true,
                Pack.Position.TOP);
    }
}
