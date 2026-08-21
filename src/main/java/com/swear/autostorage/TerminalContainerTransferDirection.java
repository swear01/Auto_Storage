package com.swear.autostorage;

public enum TerminalContainerTransferDirection {
    DEPOSIT(0),
    WITHDRAW(1);

    private final int wireId;

    TerminalContainerTransferDirection(int wireId) {
        this.wireId = wireId;
    }

    int wireId() {
        return wireId;
    }

    static TerminalContainerTransferDirection byWireId(int id) {
        for (TerminalContainerTransferDirection direction : values()) {
            if (direction.wireId == id) return direction;
        }
        return null;
    }
}
