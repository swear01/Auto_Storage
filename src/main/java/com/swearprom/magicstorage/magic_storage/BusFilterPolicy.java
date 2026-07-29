package com.swearprom.magicstorage.magic_storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BusFilterPolicy {

    public enum RuleState {
        ACTIVE,
        DUPLICATE,
        UNAVAILABLE
    }

    public record RuleSlot(int index, BusFilterRule rule, RuleState state) {
        public RuleSlot {
            if (index < 0) throw new IllegalArgumentException("Bus filter slot index must be non-negative");
            rule = Objects.requireNonNull(rule, "rule");
            state = Objects.requireNonNull(state, "state");
        }
    }

    private final boolean supported;
    private final BusFilterMode mode;
    private final List<RuleSlot> ruleSlots;
    private final HolderLookup.Provider registries;

    private BusFilterPolicy(
            boolean supported,
            BusFilterMode mode,
            List<RuleSlot> ruleSlots,
            HolderLookup.Provider registries
    ) {
        this.supported = supported;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.ruleSlots = List.copyOf(ruleSlots);
        this.registries = Objects.requireNonNull(registries, "registries");
    }

    public static BusFilterPolicy compile(
            BusConfiguration configuration,
            HolderLookup.Provider registries
    ) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(registries, "registries");
        Set<BusFilterRule> seen = new HashSet<>();
        List<RuleSlot> slots = new ArrayList<>(configuration.filterRules().size());
        for (int index = 0; index < configuration.filterRules().size(); index++) {
            BusFilterRule rule = configuration.filterRules().get(index);
            RuleState state;
            if (!seen.add(rule)) {
                state = RuleState.DUPLICATE;
            } else if (!rule.available()) {
                state = RuleState.UNAVAILABLE;
            } else {
                state = RuleState.ACTIVE;
            }
            slots.add(new RuleSlot(index, rule, state));
        }
        return new BusFilterPolicy(
                configuration.supported(),
                configuration.filterMode(),
                slots,
                registries
        );
    }

    public boolean allows(ItemKey key) {
        Objects.requireNonNull(key, "key");
        if (!supported || denyHasUnavailableRule()) return false;
        boolean matched = ruleSlots.stream()
                .filter(slot -> slot.state() == RuleState.ACTIVE)
                .anyMatch(slot -> matches(slot.rule(), key));
        return mode == BusFilterMode.ALLOW ? matched : !matched;
    }

    public boolean allows(StorageResourceKey key) {
        Objects.requireNonNull(key, "key");
        if (!supported || denyHasUnavailableRule()) return false;
        if (key.kindId().equals(StorageResourceKindApi.ITEM_KIND)) {
            return StorageResourceBridge.itemKey(key, registries)
                    .map(this::allows)
                    .orElse(false);
        }
        boolean matched = ruleSlots.stream()
                .filter(slot -> slot.state() == RuleState.ACTIVE)
                .anyMatch(slot -> matches(slot.rule(), key));
        return mode == BusFilterMode.ALLOW ? matched : !matched;
    }

    public List<ItemKey> orderedCandidates(Collection<ItemKey> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (!supported || denyHasUnavailableRule() || candidates.isEmpty()) return List.of();
        List<ItemKey> ordered = new ArrayList<>(new LinkedHashSet<>(candidates));
        Map<ItemKey, String> componentIdentities = new HashMap<>();
        ordered.sort(Comparator
                .comparing((ItemKey key) -> BuiltInRegistries.ITEM.getKey(key.item()).toString())
                .thenComparing(key -> componentIdentities.computeIfAbsent(
                        key, value -> canonicalComponents(value.components()))));
        if (mode == BusFilterMode.DENY) {
            return ordered.stream().filter(this::allows).toList();
        }
        LinkedHashSet<ItemKey> result = new LinkedHashSet<>();
        for (RuleSlot slot : ruleSlots) {
            if (slot.state() != RuleState.ACTIVE) continue;
            for (ItemKey key : ordered) {
                if (matches(slot.rule(), key)) result.add(key);
            }
        }
        return List.copyOf(result);
    }

    public List<StorageResourceKey> orderedResourceCandidates(
            Collection<StorageResourceKey> candidates
    ) {
        Objects.requireNonNull(candidates, "candidates");
        if (!supported || denyHasUnavailableRule() || candidates.isEmpty()) return List.of();
        List<StorageResourceKey> ordered = new ArrayList<>(new LinkedHashSet<>(candidates));
        ordered.sort(Comparator.naturalOrder());
        if (mode == BusFilterMode.DENY) {
            return ordered.stream().filter(this::allows).toList();
        }
        LinkedHashSet<StorageResourceKey> result = new LinkedHashSet<>();
        for (RuleSlot slot : ruleSlots) {
            if (slot.state() != RuleState.ACTIVE) continue;
            for (StorageResourceKey key : ordered) {
                if (matches(slot.rule(), key)) result.add(key);
            }
        }
        return List.copyOf(result);
    }

    public List<RuleSlot> ruleSlots() {
        return ruleSlots;
    }

    private boolean denyHasUnavailableRule() {
        return mode == BusFilterMode.DENY && ruleSlots.stream()
                .anyMatch(slot -> slot.state() == RuleState.UNAVAILABLE);
    }

    private boolean matches(BusFilterRule rule, ItemKey key) {
        return switch (rule.type()) {
            case EXACT_STACK -> rule.exactKey().orElseThrow().equals(key);
            case ITEM -> BuiltInRegistries.ITEM.getKey(key.item()).equals(rule.id().orElseThrow());
            case TAG -> key.toStack(1).is(TagKey.create(Registries.ITEM, rule.id().orElseThrow()));
            case MOD -> BuiltInRegistries.ITEM.getKey(key.item()).getNamespace()
                    .equals(rule.namespace().orElseThrow());
            case RESOURCE, UNAVAILABLE -> false;
        };
    }

    private boolean matches(BusFilterRule rule, StorageResourceKey key) {
        return rule.type() == BusFilterRule.Type.RESOURCE
                && rule.resourceKey().orElseThrow().equals(key);
    }

    private String canonicalComponents(DataComponentMap components) {
        Tag encoded = DataComponentMap.CODEC.encodeStart(
                RegistryOps.create(NbtOps.INSTANCE, registries), components).getOrThrow();
        return canonicalTag(encoded);
    }

    private static String canonicalTag(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            StringBuilder result = new StringBuilder("C{");
            compound.getAllKeys().stream().sorted().forEach(key -> {
                String value = canonicalTag(compound.get(key));
                result.append(key.length()).append(':').append(key)
                        .append(value.length()).append(':').append(value);
            });
            return result.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder result = new StringBuilder("L[");
            for (Tag entry : list) {
                String value = canonicalTag(entry);
                result.append(value.length()).append(':').append(value);
            }
            return result.append(']').toString();
        }
        String value = tag.toString();
        return tag.getId() + ":" + value.length() + ":" + value;
    }
}
