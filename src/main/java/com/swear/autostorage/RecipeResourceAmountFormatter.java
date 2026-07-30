package com.swear.autostorage;

record RecipeResourceAmountFormatter(String available, String required) {
    static RecipeResourceAmountFormatter format(long available, long required, boolean infinite) {
        return new RecipeResourceAmountFormatter(
                infinite ? "∞" : TerminalAmountFormatter.formatCompact(available),
                "/" + TerminalAmountFormatter.formatCompact(required));
    }

    String inline() {
        return available + required;
    }
}
