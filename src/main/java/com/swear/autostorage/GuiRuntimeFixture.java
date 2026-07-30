package com.swear.autostorage;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class GuiRuntimeFixture {
    static final String MARKER_FILE = ".auto_storage_runtime_fixture_pending";
    private static final long WORK_RESERVE = 1_000_000L;

    private GuiRuntimeFixture() {
    }

    static int run(CommandSourceStack source, BlockPos corePos, int itemsPerType) {
        Path marker = source.getServer().getWorldPath(LevelResource.ROOT).resolve(MARKER_FILE);
        if (!Files.isRegularFile(marker)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "GUI runtime fixture marker is missing"));
            return 0;
        }
        if (!(source.getLevel().getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "GUI runtime fixture Core is missing"));
            return 0;
        }
        core.rebuildNetwork(source.getLevel());
        SeedSummary summary = seedCore(core, itemsPerType);
        AutoStorage.LOGGER.info(
                "AS_GUI_RUNTIME_FIXTURE_SEEDED itemTypes={} itemsPerType={} stations={}",
                summary.itemTypes(),
                itemsPerType,
                summary.stations());
        return summary.itemTypes();
    }

    static int warmCraftable(CommandSourceStack source, BlockPos corePos) {
        Path marker = source.getServer().getWorldPath(LevelResource.ROOT).resolve(MARKER_FILE);
        if (!Files.isRegularFile(marker)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "GUI runtime fixture marker is missing"));
            return 0;
        }
        if (!(source.getLevel().getBlockEntity(corePos) instanceof StorageCoreBlockEntity core)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "GUI runtime fixture Core is missing"));
            return 0;
        }
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "GUI runtime fixture player is missing"));
            return 0;
        }
        long started = System.nanoTime();
        int craftableOutputs;
        CraftingTerminalMenu menu =
                new CraftingTerminalMenu(0, player.getInventory(), core);
        try {
            if (!menu.clickMenuButton(
                    player, CraftingTerminalMenu.CRAFTABLE_PAGE_BUTTON)) {
                throw new IllegalStateException("Could not prewarm Craftable page");
            }
            craftableOutputs = menu.getTotalItemTypes();
        } finally {
            menu.removed(player);
        }
        try {
            Files.delete(marker);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(
                    "Could not consume GUI runtime fixture marker", exception);
        }
        AutoStorage.LOGGER.info(
                "AS_GUI_RUNTIME_FIXTURE_READY itemTypes={} craftableOutputs={} prepareMs={}",
                core.getTypeCount(),
                craftableOutputs,
                (System.nanoTime() - started) / 1_000_000.0);
        return craftableOutputs;
    }

    static SeedSummary seedCore(StorageCoreBlockEntity core, int itemsPerType) {
        if (itemsPerType <= 0) {
            throw new IllegalArgumentException("itemsPerType must be positive");
        }
        if (core.getLevel() == null || !core.isStorageAvailable()) {
            throw new IllegalStateException("GUI runtime fixture Core is unavailable");
        }
        Map<StorageResourceKey, Long> deltas = new LinkedHashMap<>();
        int itemTypes = 0;
        for (Map.Entry<net.minecraft.resources.ResourceKey<net.minecraft.world.item.Item>,
                net.minecraft.world.item.Item> entry : BuiltInRegistries.ITEM.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            ItemStack stack = entry.getValue().getDefaultInstance();
            if (stack.isEmpty()) continue;
            StorageResourceKey key = StorageResourceKey.item(
                    stack, core.getLevel().registryAccess());
            long missing = itemsPerType - core.getResourceAmount(key);
            if (missing > 0) deltas.put(key, missing);
            itemTypes++;
        }
        for (EnergyType type : EnergyType.values()) {
            addReserve(deltas, core, StorageResourceBridge.energyKey(type));
        }
        int stations = 0;
        for (int slot = 0; slot < MachineEnergyTable.entries().size(); slot++) {
            MachineDescriptor descriptor = MachineEnergyTable.get(slot);
            if (descriptor.category() == MachineEnergyTable.Category.TRANSFORM) continue;
            int count = descriptor.category() == MachineEnergyTable.Category.PROCESS
                    ? Math.min(130, descriptor.maxInstalledCount()) : 1;
            core.getMachineContainer().setItem(
                    slot, descriptor.representativeStack().copyWithCount(count));
            if (descriptor.category() == MachineEnergyTable.Category.PROCESS
                    && descriptor.energyType() == null) {
                addReserve(
                        deltas,
                        core,
                        StorageResourceBridge.stationWorkKey(descriptor.id()));
            }
            stations++;
        }
        if (!deltas.isEmpty()
                && !core.applyResourceTransaction(deltas, Action.EXECUTE, Actor.EMPTY)) {
            throw new IllegalStateException("GUI runtime fixture transaction was rejected");
        }
        core.prewarmTerminalIndexes();
        return new SeedSummary(itemTypes, stations);
    }

    private static void addReserve(
            Map<StorageResourceKey, Long> deltas,
            StorageCoreBlockEntity core,
            StorageResourceKey key
    ) {
        long missing = WORK_RESERVE - core.getResourceAmount(key);
        if (missing > 0) deltas.put(key, missing);
    }

    record SeedSummary(int itemTypes, int stations) {
    }
}
