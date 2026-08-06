package com.swear.autostorage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Objects;

public final class TerminalDisplayStack {
    private static final String AMOUNT_KEY = "auto_storage:terminal_display_amount";
    private static final String PENDING_NUMERATOR_KEY = "auto_storage:terminal_display_pending_n";
    private static final String PENDING_DENOMINATOR_KEY = "auto_storage:terminal_display_pending_d";

    private TerminalDisplayStack() {
    }

    static ItemStack create(ItemStack stack, long amount) {
        return create(stack, amount, ExactRational.ZERO);
    }

    static ItemStack create(ItemStack stack, long amount, ExactRational pending) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Display stack cannot be empty");
        if (amount < 0) throw new IllegalArgumentException("Display amount cannot be negative");
        Objects.requireNonNull(pending, "pending");
        if (pending.numerator() >= pending.denominator() && !pending.isZero()) {
            throw new IllegalArgumentException("Display pending must be in [0, 1)");
        }
        ItemStack display = strip(stack);
        display.setCount(1);
        CustomData.update(DataComponents.CUSTOM_DATA, display, tag -> {
            tag.putLong(AMOUNT_KEY, amount);
            if (pending.isZero()) {
                tag.remove(PENDING_NUMERATOR_KEY);
                tag.remove(PENDING_DENOMINATOR_KEY);
            } else {
                tag.putLong(PENDING_NUMERATOR_KEY, pending.numerator());
                tag.putLong(PENDING_DENOMINATOR_KEY, pending.denominator());
            }
        });
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

    public static ExactRational pending(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return ExactRational.ZERO;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(PENDING_NUMERATOR_KEY, Tag.TAG_LONG)) {
            if (tag.contains(PENDING_DENOMINATOR_KEY, Tag.TAG_LONG)) {
                throw new IllegalStateException("Terminal display pending is incomplete");
            }
            return ExactRational.ZERO;
        }
        if (!tag.contains(PENDING_DENOMINATOR_KEY, Tag.TAG_LONG)) {
            throw new IllegalStateException("Terminal display pending is incomplete");
        }
        long numerator = tag.getLong(PENDING_NUMERATOR_KEY);
        long denominator = tag.getLong(PENDING_DENOMINATOR_KEY);
        if (numerator < 0 || denominator <= 0 || numerator >= denominator) {
            throw new IllegalStateException("Terminal display pending is invalid");
        }
        return ExactRational.of(numerator, denominator);
    }

    public static ItemStack strip(ItemStack stack) {
        ItemStack stripped = stack.copy();
        CustomData data = stripped.get(DataComponents.CUSTOM_DATA);
        if (data == null) return stripped;
        CompoundTag tag = data.copyTag();
        boolean changed = false;
        if (tag.contains(AMOUNT_KEY)) {
            if (!tag.contains(AMOUNT_KEY, Tag.TAG_LONG)) {
                throw new IllegalStateException("Terminal display amount is not a long");
            }
            tag.remove(AMOUNT_KEY);
            changed = true;
        }
        if (tag.contains(PENDING_NUMERATOR_KEY) || tag.contains(PENDING_DENOMINATOR_KEY)) {
            tag.remove(PENDING_NUMERATOR_KEY);
            tag.remove(PENDING_DENOMINATOR_KEY);
            changed = true;
        }
        if (!changed) return stripped;
        if (tag.isEmpty()) {
            stripped.remove(DataComponents.CUSTOM_DATA);
        } else {
            stripped.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stripped;
    }
}
