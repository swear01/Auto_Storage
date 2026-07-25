package com.swearprom.magicstorage.magic_storage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.BitSet;
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

    List<Candidate> getCandidates(
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
        BitSet availableItems = new BitSet();
        for (ItemStack stack : availableStacks) {
            if (!stack.isEmpty()) {
                availableItems.set(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getId(stack.getItem()));
            }
        }
        result.removeIf(id -> !requirementsMet(
                current.requiredItemGroups().get(id), availableItems));
        result.sort(Comparator.comparingInt(
                id -> current.recipeOrder().getOrDefault(id, Integer.MAX_VALUE)));
        return result.stream()
                .map(id -> new Candidate(id, current.adaptersByRecipeId().get(id)))
                .filter(candidate -> candidate.adapter() != null)
                .toList();
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
        Map<ResourceLocation, RecipeAdapter> adaptersByRecipeId = new HashMap<>();
        Map<ResourceLocation, int[][]> requiredItemGroups = new HashMap<>();
        List<ResourceLocation> unindexed = new ArrayList<>();
        for (int index = 0; index < supported.size(); index++) {
            RecipeAdapterMatch match = supported.get(index);
            RecipeHolder<?> holder = match.holder();
            order.put(holder.id(), index);
            adaptersByRecipeId.put(holder.id(), match.adapter());
            int[][] requirements = requiredItemGroups(match, level);
            if (requirements.length > 0) {
                requiredItemGroups.put(holder.id(), requirements);
            }
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
                Map.copyOf(adaptersByRecipeId),
                Map.copyOf(requiredItemGroups),
                List.copyOf(unindexed));
    }

    private static int[][] requiredItemGroups(RecipeAdapterMatch match, Level level) {
        List<int[]> groups = new ArrayList<>();
        TypedRecipePlan plan = match.typedRecipePlan().orElse(null);
        if (plan != null) {
            for (TypedRecipeInput input : plan.inputs()) {
                if (input.alternatives().stream().anyMatch(key ->
                        !key.kindId().equals(StorageResourceKindApi.ITEM_KIND))) continue;
                List<ItemKey> itemKeys = input.alternatives().stream()
                        .map(key -> StorageResourceBridge.itemKey(
                                key, level.registryAccess()).orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList();
                if (itemKeys.size() != input.alternatives().size()) continue;
                int[] itemIds = itemKeys.stream()
                        .mapToInt(key -> net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getId(key.item()))
                        .distinct()
                        .toArray();
                if (itemIds.length > 0) groups.add(itemIds);
            }
        } else if (match.candidateIndex().isExhaustive()) {
            for (RecipeAdapterMatch.Input input : match.orderedInputs()) {
                if (input.isEmpty()) continue;
                int[] itemIds = input.representatives().stream()
                        .filter(stack -> !stack.isEmpty())
                        .mapToInt(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getId(stack.getItem()))
                        .distinct()
                        .toArray();
                if (itemIds.length > 0) groups.add(itemIds);
            }
        }
        return groups.toArray(int[][]::new);
    }

    private static boolean requirementsMet(int[][] groups, BitSet availableItems) {
        if (groups == null) return true;
        for (int[] group : groups) {
            boolean matched = false;
            for (int itemId : group) {
                if (availableItems.get(itemId)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    record Candidate(ResourceLocation id, RecipeAdapter adapter) {
        RecipeAdapterMatch match(RecipeHolder<?> holder) {
            return new RecipeAdapterMatch(
                    adapter, holder, RecipeCandidateIndex.exhaustive(List.of()));
        }
    }

    private record CatalogIndex(
            Collection<RecipeHolder<?>> recipeSnapshot,
            Map<Item, List<ResourceLocation>> recipeIdsByIngredient,
            Map<ResourceLocation, Integer> recipeOrder,
            Map<ResourceLocation, RecipeAdapter> adaptersByRecipeId,
            Map<ResourceLocation, int[][]> requiredItemGroups,
            List<ResourceLocation> unindexedRecipeIds
    ) {
    }
}
