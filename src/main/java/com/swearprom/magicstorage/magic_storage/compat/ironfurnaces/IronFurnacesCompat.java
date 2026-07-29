package com.swearprom.magicstorage.magic_storage.compat.ironfurnaces;

import com.swearprom.magicstorage.magic_storage.MachineVariant;
import com.swearprom.magicstorage.magic_storage.MachineWorkRate;
import ironfurnaces.Config;
import ironfurnaces.blocks.furnaces.BlockIronFurnaceBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public final class IronFurnacesCompat {
    private static final List<ResourceLocation> SUPPORTED_FURNACES = List.of(
            id("copper_furnace"),
            id("iron_furnace"),
            id("silver_furnace"),
            id("gold_furnace"),
            id("diamond_furnace"),
            id("emerald_furnace"),
            id("crystal_furnace"),
            id("obsidian_furnace"),
            id("netherite_furnace"),
            id("million_furnace"));

    private IronFurnacesCompat() {
    }

    public static List<MachineVariant> furnaceVariants() {
        List<MachineVariant> variants = new ArrayList<>(SUPPORTED_FURNACES.size());
        for (ResourceLocation id : SUPPORTED_FURNACES) variants.add(createVariant(id));
        return List.copyOf(variants);
    }

    private static MachineVariant createVariant(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (!(block instanceof BlockIronFurnaceBase furnace)) {
            throw new IllegalStateException("Iron Furnaces block is unavailable or incompatible: " + id);
        }
        ModConfigSpec.IntValue cookTime = cookTimeConfig(id);
        if (cookTime == null) {
            throw new IllegalStateException("Iron Furnaces block has no cook-time config: " + id);
        }
        return MachineVariant.derived(
                new ItemStack(furnace),
                () -> rateFor(id, cookTime.get()));
    }

    private static ModConfigSpec.IntValue cookTimeConfig(ResourceLocation id) {
        return switch (id.getPath()) {
            case "copper_furnace" -> Config.copperFurnaceSpeed;
            case "iron_furnace" -> Config.ironFurnaceSpeed;
            case "silver_furnace" -> Config.silverFurnaceSpeed;
            case "gold_furnace" -> Config.goldFurnaceSpeed;
            case "diamond_furnace" -> Config.diamondFurnaceSpeed;
            case "emerald_furnace" -> Config.emeraldFurnaceSpeed;
            case "crystal_furnace" -> Config.crystalFurnaceSpeed;
            case "obsidian_furnace" -> Config.obsidianFurnaceSpeed;
            case "netherite_furnace" -> Config.netheriteFurnaceSpeed;
            case "million_furnace" -> Config.millionFurnaceSpeed;
            default -> throw new IllegalStateException("Unsupported Iron Furnaces variant: " + id);
        };
    }

    private static MachineWorkRate rateFor(ResourceLocation id, int configuredTicks) {
        if (configuredTicks <= 0) {
            throw new IllegalStateException(
                    "Iron Furnaces cook time must be positive for " + id + ": " + configuredTicks);
        }
        return MachineWorkRate.of(200, configuredTicks);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ironfurnaces", path);
    }
}
