package com.swear.autostorage;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GhostSlot extends Slot {

    public int activeLimit = Integer.MAX_VALUE;

    public GhostSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean isActive() {
        return getContainerSlot() < activeLimit;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return true;
    }
}
