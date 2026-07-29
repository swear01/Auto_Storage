package com.swearprom.magicstorage.magic_storage;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

final class TerminalClientPreferences {
    private static final String AUTO_FUEL_TARGET = "auto";
    private static final Values VALUES;
    static final ModConfigSpec SPEC;

    static {
        Pair<Values, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Values::new);
        VALUES = pair.getLeft();
        SPEC = pair.getRight();
    }

    private TerminalClientPreferences() {
    }

    static TerminalPreferences load() {
        String configuredSearchMode = VALUES.searchMode.get();
        SearchMode searchMode = SearchMode.fromConfigValue(configuredSearchMode);
        if (!configuredSearchMode.equals(searchMode.configValue())) {
            VALUES.searchMode.set(searchMode.configValue());
            SPEC.save();
        }
        return new TerminalPreferences(
                VALUES.sortMode.get(),
                VALUES.sortOrder.get(),
                searchMode,
                VALUES.resourceView.get().availableOrItem(),
                VALUES.page.get(),
                VALUES.usePlayerInventory.get(),
                VALUES.outputDestination.get(),
                fuelTarget(VALUES.fuelTarget.get()),
                transformTarget(VALUES.fuelTarget.get()));
    }

    static void save(TerminalPreferences preferences) {
        boolean changed = setIfChanged(VALUES.sortMode, preferences.sortMode());
        changed |= setIfChanged(VALUES.sortOrder, preferences.sortOrder());
        changed |= setIfChanged(VALUES.searchMode, preferences.searchMode().configValue());
        changed |= setIfChanged(VALUES.resourceView, preferences.resourceView());
        changed |= setIfChanged(VALUES.page, preferences.page());
        changed |= setIfChanged(VALUES.usePlayerInventory, preferences.usePlayerInventory());
        changed |= setIfChanged(VALUES.outputDestination, preferences.outputDestination());
        changed |= setIfChanged(VALUES.fuelTarget,
                preferences.transformTarget() == null
                        ? AUTO_FUEL_TARGET
                        : preferences.transformTarget().toString());
        if (changed) SPEC.save();
    }

    static boolean searchBoxAutoSelected() {
        return VALUES.searchBoxAutoSelected.get();
    }

    static void saveSearchBoxAutoSelected(boolean autoSelected) {
        if (setIfChanged(VALUES.searchBoxAutoSelected, autoSelected)) SPEC.save();
    }

    private static EnergyType fuelTarget(String id) {
        return TransformProviderApi.energyType(transformTarget(id)).orElse(null);
    }

    private static ResourceLocation transformTarget(String id) {
        if (AUTO_FUEL_TARGET.equals(id)) return null;
        for (EnergyType type : EnergyType.values()) {
            if (!type.isMachineGenerated() && type.getId().equals(id)) {
                return TransformProviderApi.energyTargetId(type);
            }
        }
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) throw new IllegalStateException("Invalid configured transform target " + id);
        return parsed;
    }

    private static <T> boolean setIfChanged(ModConfigSpec.ConfigValue<T> value, T next) {
        if (Objects.equals(value.get(), next)) return false;
        value.set(next);
        return true;
    }

    private static boolean validTransformTarget(Object value) {
        return value instanceof String id
                && (AUTO_FUEL_TARGET.equals(id)
                || ResourceLocation.tryParse(id) != null);
    }

    private static final class Values {
        private final ModConfigSpec.EnumValue<SortMode> sortMode;
        private final ModConfigSpec.EnumValue<SortOrder> sortOrder;
        private final ModConfigSpec.ConfigValue<String> searchMode;
        private final ModConfigSpec.BooleanValue searchBoxAutoSelected;
        private final ModConfigSpec.EnumValue<TerminalResourceView> resourceView;
        private final ModConfigSpec.EnumValue<CraftingTerminalPage> page;
        private final ModConfigSpec.BooleanValue usePlayerInventory;
        private final ModConfigSpec.EnumValue<TerminalOutputDestination> outputDestination;
        private final ModConfigSpec.ConfigValue<String> fuelTarget;

        private Values(ModConfigSpec.Builder builder) {
            builder.push("terminal");
            sortMode = builder.defineEnum("sortMode", SortMode.NAME);
            sortOrder = builder.defineEnum("sortOrder", SortOrder.ASCENDING);
            searchMode = builder.defineInList(
                    "searchMode", SearchMode.OFF.configValue(), SearchMode.configValues());
            searchBoxAutoSelected = builder.define("searchBoxAutoSelected", true);
            resourceView = builder.defineEnum("resourceView", TerminalResourceView.ITEM);
            page = builder.defineEnum("craftingPage", CraftingTerminalPage.STORAGE);
            usePlayerInventory = builder.define("usePlayerInventory", false);
            outputDestination = builder.defineEnum(
                    "outputDestination", TerminalOutputDestination.PLAYER);
            fuelTarget = builder.define(
                    "fuelTarget", AUTO_FUEL_TARGET, TerminalClientPreferences::validTransformTarget);
            builder.pop();
        }
    }
}
