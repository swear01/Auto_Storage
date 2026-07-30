package com.swear.autostorage;

import java.util.Optional;

final class TerminalPreferenceSession {
    enum Scope {
        STORAGE,
        CRAFTING
    }

    private TerminalPreferences persisted;
    private final Scope scope;
    private boolean acknowledged;

    TerminalPreferenceSession(TerminalPreferences persisted, Scope scope) {
        this.persisted = persisted;
        this.scope = scope;
    }

    TerminalPreferences preferences() {
        return persisted;
    }

    TerminalPreferences presentation(TerminalPreferences actual) {
        return acknowledged ? actual : persisted;
    }

    Optional<TerminalPreferences> observe(TerminalPreferences actual) {
        if (!acknowledged) {
            boolean matches = scope == Scope.CRAFTING
                    ? persisted.equals(actual)
                    : persisted.matchesCommon(actual);
            if (matches) acknowledged = true;
            return Optional.empty();
        }
        TerminalPreferences next = scope == Scope.CRAFTING
                ? actual
                : persisted.mergeCommon(actual);
        if (next.equals(persisted)) return Optional.empty();
        persisted = next;
        return Optional.of(next);
    }
}
