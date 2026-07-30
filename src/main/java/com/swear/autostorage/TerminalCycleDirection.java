package com.swear.autostorage;

enum TerminalCycleDirection {
    NEXT,
    PREVIOUS;

    static TerminalCycleDirection fromMouseButton(int button) {
        return switch (button) {
            case 0 -> NEXT;
            case 1 -> PREVIOUS;
            default -> throw new IllegalArgumentException("Unsupported cycle mouse button: " + button);
        };
    }

    static TerminalCycleDirection fromScroll(double delta) {
        if (delta == 0.0) throw new IllegalArgumentException("Cycle scroll delta must be non-zero");
        return delta < 0.0 ? NEXT : PREVIOUS;
    }
}
