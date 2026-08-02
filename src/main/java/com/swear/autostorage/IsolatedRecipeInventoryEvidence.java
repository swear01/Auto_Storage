package com.swear.autostorage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class IsolatedRecipeInventoryEvidence {
    public static final String RESOURCE_PATH =
            "/META-INF/auto_storage/isolated-recipe-inventory.json";

    private IsolatedRecipeInventoryEvidence() {
    }

    public static void assertMatchesDescriptor(
            RecipeManager recipeManager,
            Class<?> resourceOwner
    ) {
        Objects.requireNonNull(recipeManager, "recipeManager");
        Objects.requireNonNull(resourceOwner, "resourceOwner");
        JsonObject root = loadEvidence(resourceOwner);
        List<String> namespaces = stringList(root, "namespaces");
        String expectedSha256 = requiredString(root, "sha256");
        Set<String> claimed = new HashSet<>(namespaces);
        List<String> matched = new ArrayList<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (claimed.contains(holder.id().getNamespace())) {
                matched.add(holder.id().toString());
            }
        }
        String actual = recipeInventorySha256(matched);
        if (!actual.equals(expectedSha256)) {
            throw new IllegalStateException(
                    "Isolated recipe inventory drifted: expected "
                            + expectedSha256
                            + ", got "
                            + actual
                            + " namespaces="
                            + namespaces
                            + " count="
                            + matched.size());
        }
    }

    public static String recipeInventorySha256(List<String> recipeIds) {
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

    private static JsonObject loadEvidence(Class<?> resourceOwner) {
        InputStream stream = resourceOwner.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            throw new IllegalStateException(
                    "Missing isolated recipe inventory evidence " + RESOURCE_PATH);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException(
                        "isolated recipe inventory evidence must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.keySet().equals(Set.of("namespaces", "sha256"))) {
                throw new IllegalStateException(
                        "isolated recipe inventory evidence has unexpected keys: "
                                + root.keySet());
            }
            return root;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(
                    "Failed to close isolated recipe inventory evidence", exception);
        }
    }

    private static List<String> stringList(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        if (array == null) {
            throw new IllegalStateException("missing isolated inventory list " + key);
        }
        List<String> values = new ArrayList<>(array.size());
        Set<String> seen = new HashSet<>();
        for (JsonElement element : array) {
            String value = element.getAsString();
            if (!seen.add(value)) {
                throw new IllegalStateException(
                        "duplicate isolated inventory value in " + key + ": " + value);
            }
            values.add(value);
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("isolated inventory namespaces must not be empty");
        }
        return List.copyOf(values);
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalStateException("missing isolated inventory string " + key);
        }
        return object.get(key).getAsString();
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
}
