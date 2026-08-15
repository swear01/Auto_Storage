package com.swear.autostorage;

import com.swear.autostorage.ConversionPattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-owned runtime conversion scanner.
 *
 * <p>Patterns registered through {@link #register} read their values live
 * from the recipe manager / datamaps / config (or revision-tracked caches
 * rebuilt on datapack reload). The scanner resolves input items against
 * every pattern and reports the exact per-pattern revision digest so the
 * shared Transform result can invalidate when any scanned value changes.
 * All resolutions are side-effect-free; commit still runs through the
 * exact simulate-then-commit transaction.</p>
 */
public final class ConversionScanner {
    private static final Map<ResourceLocation, ConversionPattern> PATTERNS = new HashMap<>();

    private ConversionScanner() {
    }

    public static void register(ConversionPattern pattern) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(pattern.patternId(), "pattern id");
        synchronized (PATTERNS) {
            ConversionPattern previous = PATTERNS.putIfAbsent(
                    pattern.patternId(), pattern);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate conversion pattern " + pattern.patternId());
            }
        }
    }

    public static ConversionPattern pattern(ResourceLocation patternId) {
        synchronized (PATTERNS) {
            return PATTERNS.get(patternId);
        }
    }

    /**
     * @return combined revision digest of every registered pattern, used
     *         to invalidate shared Transform results on datapack reload.
     */
    public static String revisionKey() {
        List<ConversionPattern> snapshot;
        synchronized (PATTERNS) {
            snapshot = new ArrayList<>(PATTERNS.values());
        }
        snapshot.sort(Comparator.comparing(
                pattern -> pattern.patternId().toString()));
        StringBuilder digest = new StringBuilder();
        for (ConversionPattern pattern : snapshot) {
            digest.append(pattern.patternId()).append('=')
                    .append(pattern.revisionKey()).append('\n');
        }
        return com.swear.autostorage.IsolatedRecipeInventoryEvidence
                .recipeInventorySha256(List.of(digest.toString()));
    }
}
