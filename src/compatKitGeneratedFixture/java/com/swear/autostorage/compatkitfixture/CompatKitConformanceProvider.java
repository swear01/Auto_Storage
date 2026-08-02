package com.swear.autostorage.compatkitfixture;

import com.swear.autostorage.compatkitfixture.generated.CompatibilityConformanceHarness;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

public final class CompatKitConformanceProvider {
    private CompatKitConformanceProvider() {
    }

    public static CompatibilityConformanceHarness.Scenario create(
            GameTestHelper helper,
            ResourceLocation recipeId
    ) {
        throw new UnsupportedOperationException("Compile-only fixture");
    }
}
