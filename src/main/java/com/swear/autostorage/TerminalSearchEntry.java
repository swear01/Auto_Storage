package com.swear.autostorage;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

record TerminalSearchEntry(Item item, String namespace, String searchableName) {
    static TerminalSearchEntry create(ItemKey key) {
        return create(key.toStack(1));
    }

    static TerminalSearchEntry create(ItemStack stack) {
        ResourceLocation id = stack.getItem().builtInRegistryHolder().key().location();
        return create(stack, id, stack.getHoverName());
    }

    static TerminalSearchEntry create(
            ItemStack stack,
            ResourceLocation identity,
            Component label
    ) {
        String name = label.getString().toLowerCase(Locale.ROOT);
        return new TerminalSearchEntry(
                stack.getItem(),
                identity.getNamespace(),
                name + " " + identity.getPath().toLowerCase(Locale.ROOT));
    }

    boolean is(TagKey<Item> tag) {
        return item.builtInRegistryHolder().is(tag);
    }
}
