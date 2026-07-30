package com.swear.autostorage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class TerminalSearchQuery {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final TerminalSearchQuery EMPTY = new TerminalSearchQuery(List.of());

    private final List<Term> terms;

    private TerminalSearchQuery(List<Term> terms) {
        this.terms = List.copyOf(terms);
    }

    static TerminalSearchQuery compile(String text) {
        if (text == null || text.isBlank()) return EMPTY;
        List<Term> terms = new ArrayList<>();
        for (String token : WHITESPACE.split(text.toLowerCase(Locale.ROOT).trim())) {
            if (token.isEmpty()) continue;
            char prefix = token.charAt(0);
            String value = prefix == '@' || prefix == '#' || prefix == '$'
                    ? token.substring(1)
                    : token;
            if (value.isEmpty()) {
                continue;
            } else if (prefix == '@') {
                terms.add(new ModTerm(value));
            } else if (prefix == '#') {
                ResourceLocation id = ResourceLocation.tryParse(value);
                terms.add(id == null
                        ? InvalidTerm.INSTANCE
                        : new TagTerm(TagKey.create(Registries.ITEM, id)));
            } else {
                terms.add(new NameTerm(value));
            }
        }
        return terms.isEmpty() ? EMPTY : new TerminalSearchQuery(terms);
    }

    boolean matches(TerminalSearchEntry entry) {
        for (Term term : terms) {
            if (!term.matches(entry)) return false;
        }
        return true;
    }

    boolean isEmpty() {
        return terms.isEmpty();
    }

    boolean matches(StorageResourceKey key, ItemStack representative) {
        if (terms.isEmpty()) return true;
        String identity = (key.kindId() + " " + key.resourceId() + " "
                + representative.getHoverName().getString()).toLowerCase(Locale.ROOT);
        for (Term term : terms) {
            if (!term.matches(key, representative, identity)) return false;
        }
        return true;
    }

    private sealed interface Term permits NameTerm, ModTerm, TagTerm, InvalidTerm {
        boolean matches(TerminalSearchEntry entry);

        boolean matches(StorageResourceKey key, ItemStack representative, String identity);
    }

    private record NameTerm(String value) implements Term {
        @Override
        public boolean matches(TerminalSearchEntry entry) {
            return entry.searchableName().contains(value);
        }

        @Override
        public boolean matches(
                StorageResourceKey key,
                ItemStack representative,
                String identity
        ) {
            return identity.contains(value);
        }
    }

    private record ModTerm(String value) implements Term {
        @Override
        public boolean matches(TerminalSearchEntry entry) {
            return entry.namespace().contains(value);
        }

        @Override
        public boolean matches(
                StorageResourceKey key,
                ItemStack representative,
                String identity
        ) {
            if (StorageResourceBridge.stationWorkDescriptorId(key).isPresent()) {
                return representative.getItem().builtInRegistryHolder().key().location()
                        .getNamespace().contains(value);
            }
            return key.resourceId().getNamespace().contains(value);
        }
    }

    private record TagTerm(TagKey<Item> tag) implements Term {
        @Override
        public boolean matches(TerminalSearchEntry entry) {
            return entry.is(tag);
        }

        @Override
        public boolean matches(
                StorageResourceKey key,
                ItemStack representative,
                String identity
        ) {
            return key.kindId().equals(StorageResourceKindApi.ITEM_KIND)
                    && representative.is(tag);
        }
    }

    private enum InvalidTerm implements Term {
        INSTANCE;

        @Override
        public boolean matches(TerminalSearchEntry entry) {
            return false;
        }

        @Override
        public boolean matches(
                StorageResourceKey key,
                ItemStack representative,
                String identity
        ) {
            return false;
        }
    }
}
