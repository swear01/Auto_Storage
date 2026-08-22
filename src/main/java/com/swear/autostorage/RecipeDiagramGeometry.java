package com.swear.autostorage;

public final class RecipeDiagramGeometry {
    private RecipeDiagramGeometry() {
    }

    public static int centeredOffset(int containerSize, int contentSize) {
        return Math.max(0, (containerSize - contentSize) / 2);
    }
}
