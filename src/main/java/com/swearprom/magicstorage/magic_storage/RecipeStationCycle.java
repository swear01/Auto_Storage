package com.swearprom.magicstorage.magic_storage;

final class RecipeStationCycle {
    private static final long CYCLE_TIME_MILLIS = 1_000L;

    private RecipeStationCycle() {
    }

    static long cycle(long elapsedMillis) {
        return Math.max(0L, elapsedMillis) / CYCLE_TIME_MILLIS;
    }
}
