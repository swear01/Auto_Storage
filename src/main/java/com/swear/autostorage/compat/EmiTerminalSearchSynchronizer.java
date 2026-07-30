package com.swear.autostorage.compat;

import com.swear.autostorage.SearchMode;
import com.swear.autostorage.TerminalSearchSynchronizer;
import dev.emi.emi.api.EmiApi;

public final class EmiTerminalSearchSynchronizer implements TerminalSearchSynchronizer {
    @Override
    public void synchronizeFromTerminal(SearchMode mode, String text) {
        if (mode.synchronizesToEmi()) {
            EmiApi.setSearchText(text);
        }
    }

    @Override
    public String textToSynchronizeToTerminal(SearchMode mode) {
        return mode.synchronizesFromEmi() ? EmiApi.getSearchText() : null;
    }
}
