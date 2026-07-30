package com.swear.autostorage;

/**
 * Defines how an Auto Storage machine descriptor participates in crafting.
 */
public enum MachineCategory {
    /**
     * A station that accumulates bounded work before a recipe can finish.
     */
    PROCESS,

    /**
     * A station that unlocks a deterministic recipe without accumulating work.
     */
    INSTANT,

    /**
     * A consumed input that converts directly into a stored resource.
     */
    TRANSFORM
}
