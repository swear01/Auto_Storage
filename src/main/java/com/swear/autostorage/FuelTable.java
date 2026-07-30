package com.swear.autostorage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

public final class FuelTable {
    private static final Map<Item, List<FuelValue>> TABLE = new HashMap<>();
    private static final Map<EnergyType, Integer> SUPPLIER_COUNTS = new EnumMap<>(EnergyType.class);

    static {
        add(Items.BLAZE_POWDER, new FuelValue(EnergyType.BLAZE_FUEL, 600));
        add(Items.BLAZE_ROD, new FuelValue(EnergyType.BLAZE_FUEL, 1200));
    }

    private static void add(Item item, FuelValue... values) {
        List<FuelValue> fuelValues = List.of(values);
        TABLE.put(item, fuelValues);
        fuelValues.stream().map(FuelValue::pool).distinct()
                .forEach(pool -> SUPPLIER_COUNTS.merge(pool, 1, Integer::sum));
    }

    public static List<FuelValue> getFuelValues(ItemStack stack) {
        List<FuelValue> overlays = TABLE.getOrDefault(stack.getItem(), List.of());
        int burnTime = stack.getBurnTime(null);
        if (burnTime <= 0) return overlays;

        FuelValue runtimeFuel = new FuelValue(EnergyType.FURNACE_FUEL, burnTime);
        if (overlays.isEmpty()) return List.of(runtimeFuel);
        List<FuelValue> values = new ArrayList<>(overlays.size() + 1);
        values.add(runtimeFuel);
        values.addAll(overlays);
        return List.copyOf(values);
    }

    public static boolean isFuel(ItemStack stack) {
        return !getFuelValues(stack).isEmpty();
    }

    public static int getSupplierCount(EnergyType pool) {
        if (pool == EnergyType.FURNACE_FUEL) return getRuntimeFuelSupplierCount();
        return SUPPLIER_COUNTS.getOrDefault(pool, 0);
    }

    public static FuelValue getAutoFuelValue(ItemStack stack, ToLongFunction<EnergyType> currentAmount) {
        return selectAutoFuelValue(getFuelValues(stack), currentAmount);
    }

    static FuelValue selectAutoFuelValue(List<FuelValue> values, ToLongFunction<EnergyType> currentAmount) {
        if (values.isEmpty()) return null;
        if (values.size() == 1) return values.get(0);
        Map<EnergyType, Integer> supplierCounts = new EnumMap<>(EnergyType.class);
        values.stream().map(FuelValue::pool).distinct()
                .forEach(pool -> supplierCounts.put(pool, getSupplierCount(pool)));
        return values.stream().min(Comparator
                .comparingInt((FuelValue value) -> supplierCounts.get(value.pool()))
                .thenComparingLong(value -> currentAmount.applyAsLong(value.pool()))
                .thenComparingInt(value -> value.pool().ordinal()))
                .orElse(null);
    }

    private static int getRuntimeFuelSupplierCount() {
        int count = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getDefaultInstance().getBurnTime(null) > 0) count++;
        }
        return count;
    }
}
