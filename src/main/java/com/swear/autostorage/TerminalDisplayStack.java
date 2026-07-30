package com.swear.autostorage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class TerminalDisplayStack {
    private static final String AMOUNT_KEY = "auto_storage:terminal_display_amount";

    private TerminalDisplayStack() {
    }

    static ItemStack create(ItemStack stack, long amount) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Display stack cannot be empty");
        if (amount < 0) throw new IllegalArgumentException("Display amount cannot be negative");
        ItemStack display = strip(stack);
        display.setCount(1);
        CustomData.update(DataComponents.CUSTOM_DATA, display, tag -> tag.putLong(AMOUNT_KEY, amount));
        return display;
    }

    static boolean isDisplay(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(AMOUNT_KEY);
    }

    public static long amount(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return stack.getCount();
        CompoundTag tag = data.copyTag();
        if (!tag.contains(AMOUNT_KEY)) return stack.getCount();
        if (!tag.contains(AMOUNT_KEY, Tag.TAG_LONG)) {
            throw new IllegalStateException("Terminal display amount is not a long");
        }
        long amount = tag.getLong(AMOUNT_KEY);
        if (amount < 0) throw new IllegalStateException("Terminal display amount is negative");
        return amount;
    }

    public static ItemStack strip(ItemStack stack) {
        ItemStack stripped = stack.copy();
        CustomData data = stripped.get(DataComponents.CUSTOM_DATA);
        if (data == null) return stripped;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(AMOUNT_KEY)) return stripped;
        if (!tag.contains(AMOUNT_KEY, Tag.TAG_LONG)) {
            throw new IllegalStateException("Terminal display amount is not a long");
        }
        tag.remove(AMOUNT_KEY);
        if (tag.isEmpty()) {
            stripped.remove(DataComponents.CUSTOM_DATA);
        } else {
            stripped.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stripped;
    }
}
