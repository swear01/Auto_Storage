package com.swear.autostorage;

public enum TerminalContainerTransferDirection {
    DEPOSIT,
    WITHDRAW;

    static TerminalContainerTransferDirection byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }
}
