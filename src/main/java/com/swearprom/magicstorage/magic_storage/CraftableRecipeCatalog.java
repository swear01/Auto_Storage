package com.swearprom.magicstorage.magic_storage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class CraftableRecipeCatalog {
    private static final Map<RecipeManager, CatalogIndex> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CatalogIndex index;

    static void prewarm(Level level) {
        new CraftableRecipeCatalog().ensureCurrent(level);
    }

    List<ResourceLocation> getCandidateRecipeIds(
            Level level,
            Collection<ItemStack> availableStacks
    ) {
        CatalogIndex current = ensureCurrent(level);
        Set<ResourceLocation> candidates = new LinkedHashSet<>();
        candidates.addAll(current.unindexedRecipeIds());
        for (ItemStack stack : availableStacks) {
            if (stack.isEmpty()) continue;
            candidates.addAll(current.recipeIdsByIngredient()
                    .getOrDefault(stack.getItem(), List.of()));
        }
        List<ResourceLocation> result = new ArrayList<>(candidates);
        result.sort(Comparator.comparingInt(
                id -> current.recipeOrder().getOrDefault(id, Integer.MAX_VALUE)));
        return result;
    }

    private CatalogIndex ensureCurrent(Level level) {
        RecipeManager manager = level.getRecipeManager();
        Collection<RecipeHolder<?>> currentSnapshot = manager.getRecipes();
        if (index != null && currentSnapshot == index.recipeSnapshot()) return index;
        synchronized (CACHE) {
            CatalogIndex cached = CACHE.get(manager);
            if (cached == null || cached.recipeSnapshot() != currentSnapshot) {
                cached = buildIndex(level, currentSnapshot);
                CACHE.put(manager, cached);
            }
            index = cached;
        }
        return index;
    }

    private static CatalogIndex buildIndex(
            Level level,
            Collection<RecipeHolder<?>> recipeSnapshot
    ) {
        long started = System.nanoTime();
        RecipeManager manager = level.getRecipeManager();
        RecipeAdapterRegistry adapterRegistry = BuiltInRecipeAdapters.registry();
        List<RecipeAdapterMatch> supported = new ArrayList<>();
        for (RecipeType<?> type : BuiltInRecipeAdapters.discoveryTypes()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            List<RecipeHolder<?>> holders = (List) manager.getAllRecipesFor((RecipeType) type);
            holders.stream()
                    .sorted(Comparator.comparing(holder -> holder.id().toString()))
                    .map(holder -> adapterRegistry.classify(holder, level))
                    .flatMap(java.util.Optional::stream)
                    .forEach(supported::add);
        }

        Map<Item, List<ResourceLocation>> byIngredient = new HashMap<>();
        Map<ResourceLocation, Integer> order = new HashMap<>();
        List<ResourceLocation> unindexed = new ArrayList<>();
        for (int index = 0; index < supported.size(); index++) {
            RecipeAdapterMatch match = supported.get(index);
            RecipeHolder<?> holder = match.holder();
            order.put(holder.id(), index);
            Set<Item> indexedItems = new LinkedHashSet<>();
            if (!match.candidateIndex().isExhaustive()) unindexed.add(holder.id());
            for (ItemStack candidate : match.candidateIndex().representatives()) {
                if (!candidate.isEmpty()) indexedItems.add(candidate.getItem());
            }
            for (Item item : indexedItems) {
                byIngredient.computeIfAbsent(item, ignored -> new ArrayList<>()).add(holder.id());
            }
        }

        Map<Item, List<ResourceLocation>> immutableIndex = new HashMap<>();
        byIngredient.forEach((item, ids) -> immutableIndex.put(item, List.copyOf(ids)));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        MagicStorage.LOGGER.info(
                "Craftable catalog built in {} ms: total={}, supported={}, unindexed={}, ingredientKeys={}",
                elapsedMillis,
                recipeSnapshot.size(),
                supported.size(),
                unindexed.size(),
                immutableIndex.size());
        return new CatalogIndex(
                recipeSnapshot,
                Map.copyOf(immutableIndex),
                Map.copyOf(order),
                List.copyOf(unindexed));
    }

    private record CatalogIndex(
            Collection<RecipeHolder<?>> recipeSnapshot,
            Map<Item, List<ResourceLocation>> recipeIdsByIngredient,
            Map<ResourceLocation, Integer> recipeOrder,
            List<ResourceLocation> unindexedRecipeIds
    ) {
    }
}
