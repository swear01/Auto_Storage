package com.swear.autostorage.fixture.compatibilitymatrix;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.swear.autostorage.AutoStorage;
import com.swear.autostorage.CraftingTerminalMenu;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CompatibilityMatrixManifest {
    private static final String RESOURCE_PATH =
            "/META-INF/auto_storage/compatibility-matrix-manifest.json";
    private static final Set<String> ROOT_KEYS = Set.of(
            "schema", "modules", "companions", "unclaimedRecipeInventory");

    private final List<AssertionGroup> modules;
    private final List<AssertionGroup> companions;
    private final String unclaimedRecipeInventorySha256;

    private CompatibilityMatrixManifest(
            List<AssertionGroup> modules,
            List<AssertionGroup> companions,
            String unclaimedRecipeInventorySha256
    ) {
        this.modules = List.copyOf(modules);
        this.companions = List.copyOf(companions);
        this.unclaimedRecipeInventorySha256 = unclaimedRecipeInventorySha256;
    }

    static CompatibilityMatrixManifest load() {
        InputStream stream = CompatibilityMatrixManifest.class.getResourceAsStream(
                RESOURCE_PATH);
        if (stream == null) {
            throw new IllegalStateException(
                    "Missing compatibility matrix manifest " + RESOURCE_PATH);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(
                    "Failed to close compatibility matrix manifest", exception);
        }
    }

    static CompatibilityMatrixManifest parse(Reader metadata) {
        JsonElement parsed = JsonParser.parseReader(metadata);
        if (!parsed.isJsonObject()) {
            throw new IllegalStateException("compatibility matrix manifest root must be object");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!root.keySet().equals(ROOT_KEYS)) {
            throw new IllegalStateException(
                    "compatibility matrix manifest has unexpected keys: " + root.keySet());
        }
        if (root.get("schema").getAsInt() != 1) {
            throw new IllegalStateException("compatibility matrix manifest schema must be 1");
        }
        List<AssertionGroup> modules = parseGroups(root.getAsJsonArray("modules"), true);
        List<AssertionGroup> companions = parseGroups(root.getAsJsonArray("companions"), true);
        JsonObject unclaimed = root.getAsJsonObject("unclaimedRecipeInventory");
        if (unclaimed == null || unclaimed.keySet().size() != 1
                || !unclaimed.has("sha256")) {
            throw new IllegalStateException(
                    "unclaimedRecipeInventory must declare only sha256");
        }
        return new CompatibilityMatrixManifest(
                modules,
                companions,
                unclaimed.get("sha256").getAsString());
    }

    boolean assertCoexistence(GameTestHelper helper, String evidence) {
        Objects.requireNonNull(evidence, "evidence");
        for (AssertionGroup group : allGroups()) {
            for (String modId : group.mods()) {
                if (!ModList.get().isLoaded(modId)) {
                    helper.fail(evidence + ": did not load " + modId);
                    return false;
                }
            }
            for (ResourceLocation id : group.descriptors()) {
                if (!AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(id)
                        || !AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(id)) {
                    helper.fail(evidence + ": registry is missing " + id);
                    return false;
                }
            }
            for (ResourceLocation kindId : group.resourceKinds()) {
                if (AutoStorage.RESOURCE_KIND_REGISTRY.get(kindId) == null) {
                    helper.fail(evidence + ": registry is missing resource kind " + kindId);
                    return false;
                }
            }
            for (ResourceLocation id : group.rejectedDescriptors()) {
                if (AutoStorage.RECIPE_FAMILY_REGISTRY.containsKey(id)
                        || AutoStorage.MACHINE_DESCRIPTOR_REGISTRY.containsKey(id)) {
                    helper.fail(evidence + ": fail-closed boundary changed for " + id);
                    return false;
                }
            }
            for (ResourceLocation kindId : group.rejectedResourceKinds()) {
                if (AutoStorage.RESOURCE_KIND_REGISTRY.containsKey(kindId)) {
                    helper.fail(
                            evidence + ": fail-closed boundary changed for resource kind "
                                    + kindId);
                    return false;
                }
            }
        }
        return true;
    }

    boolean assertAcceptedRecipes(GameTestHelper helper) {
        for (AssertionGroup group : allGroups()) {
            for (ResourceLocation recipeId : group.acceptedRecipes()) {
                var holder = helper.getLevel().getRecipeManager().byKey(recipeId).orElse(null);
                if (holder == null) {
                    helper.fail("Combined compatibility recipe is missing " + recipeId);
                    return false;
                }
                if (!CraftingTerminalMenu.supportsRecipeHolder(holder)) {
                    helper.fail("Combined compatibility rejected accepted recipe " + recipeId);
                    return false;
                }
            }
        }
        return true;
    }

    void assertRecipeInventories(RecipeManager recipeManager) {
        Map<String, List<String>> byNamespace = new HashMap<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            ResourceLocation id = holder.id();
            byNamespace
                    .computeIfAbsent(id.getNamespace(), ignored -> new ArrayList<>())
                    .add(id.toString());
        }
        Set<String> claimed = new HashSet<>();
        List<String> mismatches = new ArrayList<>();
        Map<String, String> actualDigests = new HashMap<>();
        for (AssertionGroup group : allGroups()) {
            List<String> ids = new ArrayList<>();
            for (String namespace : group.recipeNamespaces()) {
                if (!claimed.add(namespace)) {
                    throw new IllegalStateException(
                            "Duplicate recipe namespace claim: " + namespace);
                }
                ids.addAll(byNamespace.getOrDefault(namespace, List.of()));
            }
            String actual = recipeInventorySha256(ids);
            actualDigests.put(group.id(), actual + " count=" + ids.size());
            if (!actual.equals(group.recipeInventorySha256())) {
                mismatches.add(
                        group.id()
                                + ": expected "
                                + group.recipeInventorySha256()
                                + ", got "
                                + actual
                                + " count="
                                + ids.size());
            }
        }
        List<String> unclaimed = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : byNamespace.entrySet()) {
            if (!claimed.contains(entry.getKey())) {
                unclaimed.addAll(entry.getValue());
            }
        }
        String actualUnclaimed = recipeInventorySha256(unclaimed);
        actualDigests.put("unclaimed", actualUnclaimed + " count=" + unclaimed.size());
        if (!actualUnclaimed.equals(unclaimedRecipeInventorySha256)) {
            mismatches.add(
                    "unclaimed: expected "
                            + unclaimedRecipeInventorySha256
                            + ", got "
                            + actualUnclaimed
                            + " count="
                            + unclaimed.size());
        }
        if (!mismatches.isEmpty()) {
            throw new IllegalStateException(
                    "Compatibility recipe inventory drifted: "
                            + String.join("; ", mismatches)
                            + " | actual="
                            + actualDigests);
        }
    }

    private List<AssertionGroup> allGroups() {
        List<AssertionGroup> groups = new ArrayList<>(modules.size() + companions.size());
        groups.addAll(modules);
        groups.addAll(companions);
        return groups;
    }

    private static List<AssertionGroup> parseGroups(JsonArray array, boolean requireId) {
        if (array == null) {
            throw new IllegalStateException("compatibility matrix groups must be an array");
        }
        List<AssertionGroup> groups = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : array) {
            AssertionGroup group = parseGroup(element.getAsJsonObject(), requireId);
            if (!seen.add(group.id())) {
                throw new IllegalStateException("Duplicate matrix group id: " + group.id());
            }
            groups.add(group);
        }
        groups.sort(Comparator.comparing(AssertionGroup::id));
        return groups;
    }

    private static AssertionGroup parseGroup(JsonObject object, boolean requireId) {
        String id = requiredString(object, "id");
        JsonObject recipeInventory = object.getAsJsonObject("recipeInventory");
        if (recipeInventory == null) {
            throw new IllegalStateException("missing recipeInventory in group " + id);
        }
        return new AssertionGroup(
                id,
                stringList(object, "mods"),
                resourceList(object, "descriptors"),
                resourceList(object, "resourceKinds"),
                resourceList(object, "acceptedRecipes"),
                resourceList(object, "rejectedDescriptors"),
                resourceList(object, "rejectedResourceKinds"),
                stringList(recipeInventory, "namespaces"),
                requiredString(recipeInventory, "sha256"));
    }

    private static List<String> stringList(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        if (array == null) {
            throw new IllegalStateException("missing matrix list " + key);
        }
        List<String> values = new ArrayList<>(array.size());
        Set<String> seen = new HashSet<>();
        for (JsonElement element : array) {
            String value = element.getAsString();
            if (!seen.add(value)) {
                throw new IllegalStateException("duplicate matrix value in " + key + ": " + value);
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static List<ResourceLocation> resourceList(JsonObject object, String key) {
        List<ResourceLocation> values = new ArrayList<>();
        for (String value : stringList(object, key)) {
            values.add(ResourceLocation.parse(value));
        }
        return List.copyOf(values);
    }

    private static String requiredString(JsonObject object, String key) {
        Objects.requireNonNull(object, "object");
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalStateException("missing matrix string " + key);
        }
        return object.get(key).getAsString();
    }

    static String recipeInventorySha256(List<String> recipeIds) {
        List<String> sorted = new ArrayList<>(recipeIds);
        sorted.sort(Comparator.naturalOrder());
        String canonical = canonicalJsonStringArray(sorted);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String canonicalJsonStringArray(List<String> values) {
        if (values.isEmpty()) {
            return "[]\n";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[\n");
        for (int index = 0; index < values.size(); index++) {
            builder.append("  \"");
            builder.append(escapeJson(values.get(index)));
            builder.append('"');
            if (index + 1 < values.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("]\n");
        return builder.toString();
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\', '"' -> builder.append('\\').append(character);
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }

    private record AssertionGroup(
            String id,
            List<String> mods,
            List<ResourceLocation> descriptors,
            List<ResourceLocation> resourceKinds,
            List<ResourceLocation> acceptedRecipes,
            List<ResourceLocation> rejectedDescriptors,
            List<ResourceLocation> rejectedResourceKinds,
            List<String> recipeNamespaces,
            String recipeInventorySha256
    ) {
    }
}
