package com.swear.autostorage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class RecipeAdapterRegistry {
    private static final Comparator<RecipeAdapter> ORDER = Comparator
            .comparingInt(RecipeAdapter::priority)
            .thenComparing(adapter -> adapter.id().toString());

    private final List<RecipeAdapter> adapters;
    private final Map<ResourceLocation, RecipeAdapter> adaptersById;
    private final Map<RecipeFamilyKey, RecipeAdapter> exactAdaptersByKey;
    private final List<RecipeAdapter> fallbackAdapters;

    RecipeAdapterRegistry(List<? extends RecipeAdapter> adapters) {
        Objects.requireNonNull(adapters, "adapters");
        List<RecipeAdapter> ordered = new ArrayList<>(adapters.size());
        Map<ResourceLocation, RecipeAdapter> byId = new HashMap<>();
        Map<RecipeFamilyKey, RecipeAdapter> byExactKey = new HashMap<>();
        List<RecipeAdapter> fallbacks = new ArrayList<>();
        for (RecipeAdapter adapter : adapters) {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(adapter.id(), "adapter.id");
            if (byId.putIfAbsent(adapter.id(), adapter) != null) {
                throw new IllegalArgumentException("Duplicate recipe adapter ID: " + adapter.id());
            }
            Optional<RecipeFamilyKey> exactKey = adapter.exactFamilyKey();
            if (exactKey.isPresent()) {
                if (byExactKey.putIfAbsent(exactKey.get(), adapter) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate exact recipe family: " + exactKey.get());
                }
            } else {
                fallbacks.add(adapter);
            }
            ordered.add(adapter);
        }
        ordered.sort(ORDER);
        fallbacks.sort(ORDER);
        this.adapters = List.copyOf(ordered);
        this.adaptersById = Map.copyOf(byId);
        this.exactAdaptersByKey = Map.copyOf(byExactKey);
        this.fallbackAdapters = List.copyOf(fallbacks);
    }

    List<RecipeAdapter> adapters() {
        return adapters;
    }

    Optional<RecipeAdapter> get(ResourceLocation id) {
        return Optional.ofNullable(adaptersById.get(Objects.requireNonNull(id, "id")));
    }

    Optional<RecipeAdapterMatch> classify(RecipeHolder<?> holder) {
        return classify(holder, null);
    }

    Optional<RecipeAdapterMatch> classify(RecipeHolder<?> holder, Level level) {
        Objects.requireNonNull(holder, "holder");
        RecipeAdapter exact = exactAdaptersByKey.get(new RecipeFamilyKey(
                recipeClass(holder), holder.value().getType()));
        if (exact == null) return classifyFallback(holder, level, null, false);
        Optional<RecipeAdapterMatch> earlierFallback =
                classifyFallback(holder, level, exact, true);
        if (earlierFallback.isPresent()) return earlierFallback;
        if (exact.supports(holder)) {
            return Optional.of(match(exact, holder, level));
        }
        return classifyFallback(holder, level, exact, false);
    }

    private Optional<RecipeAdapterMatch> classifyFallback(
            RecipeHolder<?> holder,
            Level level,
            RecipeAdapter exact,
            boolean beforeExact
    ) {
        for (RecipeAdapter fallback : fallbackAdapters) {
            if (exact != null && (ORDER.compare(fallback, exact) < 0) != beforeExact) continue;
            if (fallback.supports(holder)) {
                return Optional.of(match(fallback, holder, level));
            }
        }
        return Optional.empty();
    }

    private static RecipeAdapterMatch match(
            RecipeAdapter adapter,
            RecipeHolder<?> holder,
            Level level
    ) {
        return new RecipeAdapterMatch(
                adapter, holder, adapter.candidateIndex(holder, level));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Recipe<?>> recipeClass(RecipeHolder<?> holder) {
        return (Class<? extends Recipe<?>>) (Class<?>) holder.value().getClass();
    }
}
