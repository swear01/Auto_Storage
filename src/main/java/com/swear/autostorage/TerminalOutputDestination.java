package com.swear.autostorage;

public enum TerminalOutputDestination {
    PLAYER,
    STORAGE;

    TerminalOutputDestination next() {
        return this == PLAYER ? STORAGE : PLAYER;
    }

    static TerminalOutputDestination byId(int id) {
        if (id < 0 || id >= values().length) {
            throw new IllegalArgumentException("Unknown terminal output destination " + id);
        }
        return values()[id];
    }
}
