package com.swear.autostorage.compat;

import com.swear.autostorage.SearchMode;
import com.swear.autostorage.TerminalSearchSynchronizer;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.Objects;

public final class JeiTerminalSearchSynchronizer implements TerminalSearchSynchronizer {
    private final IJeiRuntime runtime;

    JeiTerminalSearchSynchronizer(IJeiRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public void synchronizeFromTerminal(SearchMode mode, String text) {
        if (mode.synchronizesToEmi()) {
            runtime.getIngredientFilter().setFilterText(text);
        }
    }

    @Override
    public String textToSynchronizeToTerminal(SearchMode mode) {
        return mode.synchronizesFromEmi() ? runtime.getIngredientFilter().getFilterText() : null;
    }
}
