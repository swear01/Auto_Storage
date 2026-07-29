package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.function.Function;

final class TerminalEntryComparator {
    private TerminalEntryComparator() {
    }

    static Comparator<ItemStack> forMode(SortMode mode, SortOrder order) {
        Comparator<ItemStack> comparator = switch (mode) {
            case NAME -> Comparator.comparing(TerminalEntryComparator::displayName)
                    .thenComparing(TerminalEntryComparator::fullId)
                    .thenComparing(TerminalEntryComparator::components);
            case QUANTITY -> Comparator.comparingLong(TerminalDisplayStack::amount)
                    .thenComparing(TerminalEntryComparator::fullId)
                    .thenComparing(TerminalEntryComparator::components);
            case MOD -> Comparator.comparing(TerminalEntryComparator::namespace)
                    .thenComparing(TerminalEntryComparator::displayName)
                    .thenComparing(TerminalEntryComparator::path)
                    .thenComparing(TerminalEntryComparator::components);
            case ID -> Comparator.comparing(TerminalEntryComparator::fullId)
                    .thenComparing(TerminalEntryComparator::components);
        };
        return order == SortOrder.DESCENDING ? comparator.reversed() : comparator;
    }

    static <T> Comparator<T> forMode(
            SortMode mode,
            SortOrder order,
            Function<T, ItemStack> displayStack
    ) {
        return Comparator.comparing(displayStack, forMode(mode, order));
    }

    private static String displayName(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    private static String namespace(ItemStack stack) {
        return id(stack).getNamespace();
    }

    private static String path(ItemStack stack) {
        return id(stack).getPath();
    }

    private static String fullId(ItemStack stack) {
        return id(stack).toString();
    }

    private static String components(ItemStack stack) {
        return TerminalDisplayStack.strip(stack).getComponents().toString();
    }

    private static ResourceLocation id(ItemStack stack) {
        return TerminalResourceDisplay.key(stack)
                .map(StorageResourceKey::resourceId)
                .orElseGet(() -> BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
