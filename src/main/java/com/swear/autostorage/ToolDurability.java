package com.swear.autostorage;

import net.minecraft.world.item.ItemStack;

/**
 * Converts a damageable tool item's remaining durability into a stored
 * tool-resource amount, following the Axe Energy pattern (durability x
 * (unbreaking+1) x count; infinite when unbreakable).
 */
final class ToolDurability {
    private ToolDurability() {
    }

    static long finiteValue(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxDamage() <= 0) return 0;
        if (stack.has(net.minecraft.core.component.DataComponents.UNBREAKABLE)) {
            return 0;
        }
        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        int unbreakingLevel = 0;
        net.minecraft.world.item.enchantment.ItemEnchantments enchantments =
                stack.getOrDefault(
                        net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING)) {
                unbreakingLevel = Math.max(unbreakingLevel, entry.getIntValue());
            }
        }
        if (remaining <= 0) return 0;
        try {
            return Math.multiplyExact(
                    Math.multiplyExact((long) remaining, (long) unbreakingLevel + 1L),
                    stack.getCount());
        } catch (ArithmeticException exception) {
            return 0;
        }
    }
}
