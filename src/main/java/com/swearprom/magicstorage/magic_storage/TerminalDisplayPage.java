package com.swearprom.magicstorage.magic_storage;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record TerminalDisplayPage(int totalTypes, int offset, List<ItemStack> stacks) {
    public TerminalDisplayPage {
        if (totalTypes < 0 || offset < 0) throw new IllegalArgumentException();
        stacks = List.copyOf(stacks);
    }
}
