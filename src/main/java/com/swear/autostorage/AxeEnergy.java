package com.swear.autostorage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.ItemAbilities;

final class AxeEnergy {
    private AxeEnergy() {
    }

    static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (stack.canPerformAction(ItemAbilities.AXE_STRIP)
                || stack.canPerformAction(ItemAbilities.AXE_SCRAPE)
                || stack.canPerformAction(ItemAbilities.AXE_WAX_OFF));
    }

    static boolean isInfinite(ItemStack stack) {
        return accepts(stack) && stack.has(DataComponents.UNBREAKABLE);
    }

    static int remainingDurability(ItemStack stack) {
        if (!accepts(stack) || stack.getMaxDamage() <= 0) return 0;
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    static long finiteValue(ItemStack stack) {
        if (!accepts(stack) || isInfinite(stack)) return 0;
        int unbreakingLevel = 0;
        ItemEnchantments enchantments = stack.getOrDefault(
                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(Enchantments.UNBREAKING)) {
                unbreakingLevel = Math.max(unbreakingLevel, entry.getIntValue());
            }
        }
        return scaledFiniteValue(remainingDurability(stack), unbreakingLevel, stack.getCount());
    }

    static long scaledFiniteValue(int remainingDurability, int unbreakingLevel, int count) {
        if (remainingDurability <= 0 || unbreakingLevel < 0 || count <= 0) return 0;
        return Math.multiplyExact(
                Math.multiplyExact((long) remainingDurability, (long) unbreakingLevel + 1L),
                count);
    }

    static ItemStack representativeStack() {
        return new ItemStack(Items.IRON_AXE);
    }
}
