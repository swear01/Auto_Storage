package com.swear.autostorage.compat;

import com.swear.autostorage.RecipeDiagramRenderer;
import com.swear.autostorage.TerminalSearchSynchronizer;
import mezz.jei.api.runtime.IJeiRuntime;

public final class JeiRecipeDiagramBootstrap {
    private static volatile IJeiRuntime runtime;

    private JeiRecipeDiagramBootstrap() {
    }

    public static void markRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    public static void markRuntimeUnavailable() {
        runtime = null;
    }

    public static boolean isRuntimeReady() {
        return runtime != null;
    }

    static IJeiRuntime runtime() {
        IJeiRuntime current = runtime;
        if (current == null) {
            throw new IllegalStateException("JEI runtime is not ready");
        }
        return current;
    }

    public static RecipeDiagramRenderer createRenderer() {
        return new JeiRecipeDiagramRenderer(runtime());
    }

    public static TerminalSearchSynchronizer createSearchSynchronizer() {
        return new JeiTerminalSearchSynchronizer(runtime());
    }
}
