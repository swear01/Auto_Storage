package com.swear.autostorage;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum EnergyType {
    SMELTING_ENERGY("smelting_energy", true, Items.FURNACE),
    BLASTING_ENERGY("blasting_energy", true, Items.BLAST_FURNACE),
    SMOKING_ENERGY("smoking_energy", true, Items.SMOKER),
    CAMPFIRE_ENERGY("campfire_energy", true, Items.CAMPFIRE),
    BREW_ENERGY("brew_energy", true, Items.BREWING_STAND),
    FURNACE_FUEL("furnace_fuel", false, Items.COAL),
    BLAZE_FUEL("blaze_fuel", false, Items.BLAZE_ROD);

    private final String id;
    private final boolean machineGenerated;
    private final Item representativeItem;

    EnergyType(String id, boolean machineGenerated, Item representativeItem) {
        this.id = id;
        this.machineGenerated = machineGenerated;
        this.representativeItem = representativeItem;
    }

    public String getId() { return id; }
    public boolean isMachineGenerated() { return machineGenerated; }
    public ItemStack representativeStack() { return new ItemStack(representativeItem); }
}
