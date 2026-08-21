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

    static TerminalContainerTransferDirection requireWireId(int id) {
        for (TerminalContainerTransferDirection direction : values()) {
            if (direction.wireId == id) return direction;
        }
        throw new IllegalArgumentException(
                "Unknown terminal container transfer direction wire id " + id);
    }
}
