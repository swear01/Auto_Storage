package com.swearprom.magicstorage.magic_storage;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class TerminalProfile {
    enum Capability {
        PAGES,
        RECIPE_WORKSPACE,
        FUEL,
        PLAYER_INVENTORY_SOURCE,
        OUTPUT_DESTINATION
    }

    static final TerminalProfile STORAGE = of();
    static final TerminalProfile CRAFTING = of(
            Capability.PAGES,
            Capability.RECIPE_WORKSPACE,
            Capability.FUEL,
            Capability.PLAYER_INVENTORY_SOURCE,
            Capability.OUTPUT_DESTINATION);

    private static final int PAGE_CONTROL_COUNT = 4;
    private static final int VIEW_CONTROL_COUNT = 5;

    private final Set<Capability> capabilities;

    private TerminalProfile(Set<Capability> capabilities) {
        this.capabilities = Set.copyOf(capabilities);
        if (supports(Capability.FUEL)
                && (!supports(Capability.PAGES) || !supports(Capability.RECIPE_WORKSPACE))) {
            throw new IllegalArgumentException("Fuel requires page and recipe-workspace capabilities");
        }
    }

    static TerminalProfile of(Capability... capabilities) {
        EnumSet<Capability> values = EnumSet.noneOf(Capability.class);
        values.addAll(Arrays.asList(capabilities));
        return new TerminalProfile(values);
    }

    boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    int pageControlCount() {
        return supports(Capability.PAGES) ? PAGE_CONTROL_COUNT : 0;
    }

    int viewControlStartIndex() {
        return pageControlCount();
    }

    int playerInventorySourceIndex() {
        if (!supports(Capability.PLAYER_INVENTORY_SOURCE)) {
            throw new IllegalStateException("Profile has no player-inventory source control");
        }
        return viewControlStartIndex() + VIEW_CONTROL_COUNT;
    }

    int outputDestinationIndex() {
        if (!supports(Capability.OUTPUT_DESTINATION)) {
            throw new IllegalStateException("Profile has no output-destination control");
        }
        return playerInventorySourceIndex() + 1;
    }

    List<Integer> itemRailGroups() {
        if (!supports(Capability.PAGES)) return List.of(VIEW_CONTROL_COUNT);
        if (supports(Capability.OUTPUT_DESTINATION)) {
            return List.of(PAGE_CONTROL_COUNT, VIEW_CONTROL_COUNT, 2);
        }
        if (supports(Capability.PLAYER_INVENTORY_SOURCE)) {
            return List.of(PAGE_CONTROL_COUNT, VIEW_CONTROL_COUNT, 1);
        }
        return List.of(PAGE_CONTROL_COUNT, VIEW_CONTROL_COUNT);
    }

    List<Integer> fuelRailGroups() {
        return supports(Capability.PAGES)
                ? List.of(PAGE_CONTROL_COUNT)
                : itemRailGroups();
    }
}
