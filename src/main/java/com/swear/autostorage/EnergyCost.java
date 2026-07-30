package com.swear.autostorage;

public record EnergyCost(EnergyType processType, long processAmount,
                         EnergyType fuelType, long fuelAmount) {}
