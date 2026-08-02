package com.swear.autostorage;

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
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class CraftableRecipeCatalog {
    private static final Map<RecipeManager, CatalogIndex> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CatalogIndex index;

    static void prewarm(Level level) {
        new CraftableRecipeCatalog().ensureCurrent(level);
    }

    static void invalidate() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    static void releaseTransientMatches() {
        synchronized (CACHE) {
            for (CatalogIndex cached : CACHE.values()) {
                for (CatalogEntry entry : cached.entries()) {
                    entry.releaseTransientMatches();
                }
            }
        }
        for (RecipeFamily family : AutoStorage.RECIPE_FAMILY_REGISTRY) {
            family.clearRuntimeCaches();
        }
    }

    List<Candidate> getCandidates(
            Level level,
            Collection<Item> availableItems
    ) {
        CatalogIndex current = ensureCurrent(level);
        BitSet candidates = new BitSet(current.entries().size());
        for (int index : current.unindexedRecipeIndices()) candidates.set(index);
        BitSet visitedItems = new BitSet();
        BitSet availableItemIds = new BitSet();
        for (Item item : availableItems) {
            int itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getId(item);
            availableItemIds.set(itemId);
            if (visitedItems.get(itemId)) continue;
            visitedItems.set(itemId);
            for (int candidate : current.recipeIndicesByIngredient()
                    .getOrDefault(item, new int[0])) {
                candidates.set(candidate);
            }
        }
        List<Candidate> result = new ArrayList<>(candidates.cardinality());
        for (int index = candidates.nextSetBit(0);
             index >= 0;
             index = candidates.nextSetBit(index + 1)) {
            CatalogEntry entry = current.entries().get(index);
            if (requirementsMet(entry.requiredItemGroups(), availableItemIds)) {
                result.add(new Candidate(
                        entry.id(),
                        entry.adapter(),
                        entry));
            }
        }
        return List.copyOf(result);
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

        Map<Item, List<Integer>> byIngredient = new HashMap<>();
        List<CatalogEntry> entries = new ArrayList<>(supported.size());
        List<Integer> unindexed = new ArrayList<>();
        for (int index = 0; index < supported.size(); index++) {
            RecipeAdapterMatch match = supported.get(index);
            RecipeHolder<?> holder = match.holder();
            int[][] requirements = requiredItemGroups(match, level);
            boolean fullVariantSnapshot = !match.candidateIndex().isExhaustive();
            entries.add(new CatalogEntry(
                    holder,
                    match.adapter(),
                    match.stationDescriptorId(),
                    match.cost().toolCost().orElse(null),
                    requirements));
            java.util.Set<Item> indexedItems = new java.util.LinkedHashSet<>();
            if (fullVariantSnapshot) unindexed.add(index);
            for (ItemStack candidate : match.candidateIndex().representatives()) {
                if (!candidate.isEmpty()) indexedItems.add(candidate.getItem());
            }
            for (Item item : indexedItems) {
                byIngredient.computeIfAbsent(item, ignored -> new ArrayList<>()).add(index);
            }
        }

        Map<Item, int[]> immutableIndex = new HashMap<>();
        byIngredient.forEach((item, indices) -> immutableIndex.put(
                item, indices.stream().mapToInt(Integer::intValue).toArray()));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        AutoStorage.LOGGER.info(
                "Craftable catalog built in {} ms: total={}, supported={}, unindexed={}, ingredientKeys={}",
                elapsedMillis,
                recipeSnapshot.size(),
                supported.size(),
                unindexed.size(),
                immutableIndex.size());
        return new CatalogIndex(
                recipeSnapshot,
                List.copyOf(entries),
                Map.copyOf(immutableIndex),
                unindexed.stream().mapToInt(Integer::intValue).toArray());
    }

    static int[][] requiredItemGroups(RecipeAdapterMatch match, Level level) {
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

    static boolean requirementsMet(int[][] groups, BitSet availableItems) {
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

    record Candidate(
            ResourceLocation id,
            RecipeAdapter adapter,
            CatalogEntry entry
    ) {
        ResourceLocation stationDescriptorId() {
            return entry.stationDescriptorId();
        }

        RecipeAdapterMatch.ToolCost toolCost() {
            return entry.toolCost();
        }

        RecipeAdapterMatch match() {
            return entry.match();
        }

        List<RecipeAdapterMatch> resolveVariants(
                RecipeAdapterMatch match,
                List<ItemStack> availableStacks,
                Level level
        ) {
            return entry.resolveVariants(match, availableStacks, level);
        }
    }

    private static final class CatalogEntry {
        private final RecipeAdapter adapter;
        private final RecipeHolder<?> holder;
        private final ResourceLocation stationDescriptorId;
        private final RecipeAdapterMatch.ToolCost toolCost;
        private final int[][] requiredItemGroups;
        private RecipeAdapterMatch match;

        private CatalogEntry(
                RecipeHolder<?> holder,
                RecipeAdapter adapter,
                ResourceLocation stationDescriptorId,
                RecipeAdapterMatch.ToolCost toolCost,
                int[][] requiredItemGroups
        ) {
            this.adapter = adapter;
            this.holder = holder;
            this.stationDescriptorId = stationDescriptorId;
            this.toolCost = toolCost;
            this.requiredItemGroups = requiredItemGroups;
        }

        private ResourceLocation id() {
            return holder.id();
        }

        private RecipeAdapter adapter() {
            return adapter;
        }

        private int[][] requiredItemGroups() {
            return requiredItemGroups;
        }

        private ResourceLocation stationDescriptorId() {
            return stationDescriptorId;
        }

        private RecipeAdapterMatch.ToolCost toolCost() {
            return toolCost;
        }

        private RecipeAdapterMatch match() {
            if (match == null) {
                match = new RecipeAdapterMatch(
                        adapter,
                        holder,
                        RecipeCandidateIndex.exhaustive(List.of()));
            }
            return match;
        }

        private void releaseTransientMatches() {
            match = null;
        }

        private List<RecipeAdapterMatch> resolveVariants(
                RecipeAdapterMatch baseMatch,
                List<ItemStack> availableStacks,
                Level level
        ) {
            if (adapter.requiresAvailableStacksForVariants()) {
                return baseMatch.resolveVariantsFromSnapshot(
                        availableStacks, level);
            }
            return baseMatch.resolveVariantsFromSnapshot(List.of(), level);
        }
    }

    private record CatalogIndex(
            Collection<RecipeHolder<?>> recipeSnapshot,
            List<CatalogEntry> entries,
            Map<Item, int[]> recipeIndicesByIngredient,
            int[] unindexedRecipeIndices
    ) {
    }
}
