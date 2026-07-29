package com.swearprom.magicstorage.magic_storage;

public interface TerminalSearchSynchronizer {
    TerminalSearchSynchronizer NONE = new TerminalSearchSynchronizer() {
        @Override
        public void synchronizeFromTerminal(SearchMode mode, String text) {
        }

        @Override
        public String textToSynchronizeToTerminal(SearchMode mode) {
            return null;
        }
    };

    void synchronizeFromTerminal(SearchMode mode, String text);

    String textToSynchronizeToTerminal(SearchMode mode);
}
