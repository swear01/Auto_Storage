package com.swear.autostorage;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FuelSearchModel {
    record Entry(
            EnergyType energyType,
            int machineSlot,
            MachineEnergyTable.Category category
    ) {
        static Entry energy(EnergyType energyType) {
            return new Entry(energyType, -1, MachineEnergyTable.Category.TRANSFORM);
        }

        static Entry machine(int machineSlot, MachineEnergyTable.Category category) {
            return new Entry(null, machineSlot, category);
        }

        boolean isEnergy() {
            return energyType != null;
        }
    }

    record IndexedEntry(Entry entry, List<TerminalSearchEntry> searchEntries) {
        IndexedEntry {
            searchEntries = List.copyOf(searchEntries);
        }
    }

    record Index(List<IndexedEntry> entries) {
        Index {
            entries = List.copyOf(entries);
        }
    }

    private FuelSearchModel() {
    }

    static Index index(
            List<EnergyType> energyTypes,
            List<MachineDescriptor> descriptors
    ) {
        List<IndexedEntry> entries = new ArrayList<>();
        appendMachines(entries, descriptors, MachineEnergyTable.Category.PROCESS);
        appendMachines(entries, descriptors, MachineEnergyTable.Category.INSTANT);
        return new Index(entries);
    }

    static List<Entry> search(
            String text,
            List<EnergyType> energyTypes,
            List<MachineDescriptor> descriptors
    ) {
        return search(text, index(energyTypes, descriptors));
    }

    static List<Entry> search(String text, Index index) {
        return search(TerminalSearchQuery.compile(text), index);
    }

    static List<Entry> search(TerminalSearchQuery query, Index index) {
        List<Entry> result = new ArrayList<>();
        for (IndexedEntry indexed : index.entries()) {
            if (indexed.searchEntries().stream().anyMatch(query::matches)) {
                result.add(indexed.entry());
            }
        }
        return List.copyOf(result);
    }

    private static void appendMachines(
            List<IndexedEntry> result,
            List<MachineDescriptor> descriptors,
            MachineEnergyTable.Category category
    ) {
        for (int machineSlot = 0; machineSlot < descriptors.size(); machineSlot++) {
            MachineDescriptor descriptor = descriptors.get(machineSlot);
            if (descriptor.category() != category) continue;
            result.add(new IndexedEntry(
                    Entry.machine(machineSlot, category),
                    searchEntries(descriptor)));
        }
    }

    private static List<TerminalSearchEntry> searchEntries(MachineDescriptor descriptor) {
        List<TerminalSearchEntry> result = new ArrayList<>();
        Set<net.minecraft.world.item.Item> seen = new HashSet<>();
        appendSearchEntry(result, seen, descriptor.representativeStack());
        if (descriptor.category() != MachineEnergyTable.Category.TRANSFORM) {
            for (MachineVariant variant : descriptor.variants()) {
                appendSearchEntry(result, seen, variant.stack());
            }
        }
        for (ItemStack stack : descriptor.acceptedItems().getItems()) {
            appendSearchEntry(result, seen, stack);
        }
        return List.copyOf(result);
    }

    private static void appendSearchEntry(
            List<TerminalSearchEntry> result,
            Set<net.minecraft.world.item.Item> seen,
            ItemStack stack
    ) {
        if (!stack.isEmpty() && seen.add(stack.getItem())) {
            result.add(TerminalSearchEntry.create(stack));
        }
    }
}
