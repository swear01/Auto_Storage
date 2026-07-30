package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Client-lifecycle registration for custom typed-resource terminal icons.
 *
 * <p>The generic render context keeps the common addon API free of client-only
 * class references. Addons must call this API only from their client setup.</p>
 */
public final class TerminalResourceRendererApi {
    private static final int MAX_RENDERERS = 256;
    private static final Map<ResourceLocation, Registration<?>> RENDERERS =
            new LinkedHashMap<>();
    private static boolean frozen;

    private TerminalResourceRendererApi() {
    }

    /**
     * Registers one client renderer for a typed-resource kind.
     *
     * @param kindId resource-kind ID
     * @param contextType exact client render-context class
     * @param renderer renderer invoked for that context
     * @param <C> client render-context type
     */
    public static synchronized <C> void register(
            ResourceLocation kindId,
            Class<C> contextType,
            Renderer<C> renderer
    ) {
        Objects.requireNonNull(kindId, "kindId");
        Objects.requireNonNull(contextType, "contextType");
        Objects.requireNonNull(renderer, "renderer");
        if (frozen) {
            throw new IllegalStateException(
                    "Terminal resource renderer registration is closed: " + kindId);
        }
        if (RENDERERS.size() >= MAX_RENDERERS) {
            throw new IllegalStateException(
                    "Too many terminal resource renderers: " + MAX_RENDERERS);
        }
        if (RENDERERS.putIfAbsent(
                kindId,
                new Registration<>(contextType, renderer)) != null) {
            throw new IllegalArgumentException(
                    "Duplicate terminal resource renderer: " + kindId);
        }
    }

    static synchronized void freeze() {
        frozen = true;
    }

    static boolean render(
            Object context,
            StorageResourceKey key,
            long amount,
            int x,
            int y,
            float partialTick
    ) {
        Registration<?> registration;
        synchronized (TerminalResourceRendererApi.class) {
            registration = RENDERERS.get(key.kindId());
        }
        return registration != null
                && registration.render(context, key, amount, x, y, partialTick);
    }

    /**
     * Renders one exact typed-resource key in a client-owned context.
     *
     * @param <C> client render-context type
     */
    @FunctionalInterface
    public interface Renderer<C> {
        /**
         * @return {@code true} when the icon was rendered
         */
        boolean render(
                C context,
                StorageResourceKey key,
                long amount,
                int x,
                int y,
                float partialTick
        );
    }

    private record Registration<C>(
            Class<C> contextType,
            Renderer<C> renderer
    ) {
        boolean render(
                Object context,
                StorageResourceKey key,
                long amount,
                int x,
                int y,
                float partialTick
        ) {
            return contextType.isInstance(context)
                    && renderer.render(
                    contextType.cast(context), key, amount, x, y, partialTick);
        }
    }
}
