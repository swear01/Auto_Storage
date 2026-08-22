package com.swear.autostorage;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

final class TerminalRepositorySlot extends Slot {
    private final Slot delegate;
    private final TerminalClientRepository repository;
    private final int visibleIndex;
    private final int visibleRows;
    private final BooleanSupplier active;

    TerminalRepositorySlot(
            Slot delegate,
            TerminalClientRepository repository,
            int visibleIndex,
            int visibleRows,
            int x,
            int y,
            BooleanSupplier active
    ) {
        super(delegate.container, delegate.getContainerSlot(), x, y);
        this.delegate = delegate;
        this.repository = repository;
        this.visibleIndex = visibleIndex;
        this.visibleRows = visibleRows;
        this.active = active;
    }

    long serial() {
        return repository.serialAt(visibleIndex, visibleRows);
    }

    @Override
    public ItemStack getItem() {
        return repository.stackAt(visibleIndex, visibleRows);
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
    }

    @Override
    public void set(ItemStack stack) {
    }

    @Override
    public ItemStack remove(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return getItem().getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.getMaxStackSize();
    }

    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return delegate.getNoItemIcon();
    }

    @Override
    public boolean allowModification(Player player) {
        return false;
    }

    @Override
    public boolean isHighlightable() {
        return delegate.isHighlightable();
    }

    @Override
    public boolean isFake() {
        return true;
    }

    @Override
    public void onQuickCraft(ItemStack oldStack, ItemStack newStack) {
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
    }
}
