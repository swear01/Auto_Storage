package com.swearprom.magicstorage.magic_storage;

import java.util.Objects;

public final class RecipeFamilyCost {
    private static final RecipeFamilyCost FREE = new RecipeFamilyCost(null, 0);

    private final EnergyCost energyCost;
    private final long stationWork;

    private RecipeFamilyCost(EnergyCost energyCost, long stationWork) {
        this.energyCost = energyCost;
        this.stationWork = stationWork;
    }

    public static RecipeFamilyCost free() {
        return FREE;
    }

    public static RecipeFamilyCost energy(EnergyCost energyCost) {
        return new RecipeFamilyCost(checkedEnergy(energyCost), 0);
    }

    public static RecipeFamilyCost stationWork(long amountPerCraft) {
        if (amountPerCraft <= 0) {
            throw new IllegalArgumentException("Station work cost must be positive");
        }
        return new RecipeFamilyCost(null, amountPerCraft);
    }

    public static RecipeFamilyCost stationWorkAndEnergy(
            long amountPerCraft,
            EnergyCost energyCost
    ) {
        if (amountPerCraft <= 0) {
            throw new IllegalArgumentException("Station work cost must be positive");
        }
        return new RecipeFamilyCost(checkedEnergy(energyCost), amountPerCraft);
    }

    RecipeAdapterMatch.Cost toInternal(net.minecraft.resources.ResourceLocation stationDescriptorId) {
        Objects.requireNonNull(stationDescriptorId, "stationDescriptorId");
        if (energyCost != null && stationWork > 0) {
            return new RecipeAdapterMatch.Cost(
                    java.util.Optional.of(energyCost),
                    java.util.Optional.empty(),
                    java.util.Optional.of(new RecipeAdapterMatch.StationWorkCost(
                            stationDescriptorId, stationWork)));
        }
        if (energyCost != null) return RecipeAdapterMatch.Cost.energy(energyCost);
        if (stationWork > 0) {
            return RecipeAdapterMatch.Cost.stationWork(
                    new RecipeAdapterMatch.StationWorkCost(stationDescriptorId, stationWork));
        }
        return RecipeAdapterMatch.Cost.free();
    }

    private static EnergyCost checkedEnergy(EnergyCost energyCost) {
        Objects.requireNonNull(energyCost, "energyCost");
        EnergyType processType = Objects.requireNonNull(energyCost.processType(), "processType");
        EnergyType fuelType = Objects.requireNonNull(energyCost.fuelType(), "fuelType");
        if (energyCost.processAmount() < 0 || energyCost.fuelAmount() < 0) {
            throw new IllegalArgumentException("Recipe family energy cost cannot be negative");
        }
        if (energyCost.processAmount() == 0 && energyCost.fuelAmount() == 0) {
            throw new IllegalArgumentException("Use RecipeFamilyCost.free() for a zero energy cost");
        }
        if (processType == fuelType
                && energyCost.processAmount() > 0
                && energyCost.fuelAmount() > 0) {
            throw new IllegalArgumentException("Recipe family energy pools must not overlap");
        }
        return energyCost;
    }
}
