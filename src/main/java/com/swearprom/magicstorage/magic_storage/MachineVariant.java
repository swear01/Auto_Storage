package com.swearprom.magicstorage.magic_storage;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Supplier;

public final class MachineVariant {
    private final ItemStack stack;
    private final Supplier<MachineWorkRate> rate;

    private MachineVariant(ItemStack stack, Supplier<MachineWorkRate> rate) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) throw new IllegalArgumentException("Machine variant stack cannot be empty");
        this.stack = stack.copyWithCount(1);
        this.rate = Objects.requireNonNull(rate, "rate");
    }

    public static MachineVariant of(ItemStack stack, MachineWorkRate rate) {
        Objects.requireNonNull(rate, "rate");
        return new MachineVariant(stack, () -> rate);
    }

    public static MachineVariant derived(ItemStack stack, Supplier<MachineWorkRate> rate) {
        return new MachineVariant(stack, rate);
    }

    public ItemStack stack() {
        return stack.copy();
    }

    public MachineWorkRate rate() {
        return Objects.requireNonNull(rate.get(), "machine variant rate");
    }

    boolean matches(ItemStack candidate) {
        return !candidate.isEmpty() && candidate.is(stack.getItem());
    }
}
