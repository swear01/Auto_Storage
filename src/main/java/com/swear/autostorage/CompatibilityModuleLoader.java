package com.swear.autostorage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.swear.autostorage.api.AutoStorageCompatContext;
import com.swear.autostorage.api.AutoStorageCompatModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

final class CompatibilityModuleLoader {
    enum Side {
        CLIENT,
        SERVER
    }

    @FunctionalInterface
    interface ClassResolver {
        Class<?> resolve(String className) throws ClassNotFoundException;
    }

    private static final Set<String> ROOT_KEYS = Set.of("schema", "modules");
    private static final Set<String> MODULE_KEYS =
            Set.of("id", "entrypoint", "requires", "side");

    private CompatibilityModuleLoader() {
    }

    static void loadBundled(IEventBus modBus) {
        String path = "/META-INF/auto_storage/compat-modules.json";
        InputStream stream = CompatibilityModuleLoader.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException(
                    "Missing bundled compatibility module index " + path);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            load(
                    reader,
                    ModList.get()::isLoaded,
                    FMLEnvironment.dist == Dist.CLIENT ? Side.CLIENT : Side.SERVER,
                    modBus,
                    Class::forName);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(
                    "Failed to close bundled compatibility module index " + path,
                    exception);
        }
    }

    static void load(
            Reader metadata,
            Predicate<String> isModLoaded,
            Side physicalSide,
            IEventBus modBus,
            ClassResolver classResolver
    ) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(isModLoaded, "isModLoaded");
        Objects.requireNonNull(physicalSide, "physicalSide");
        Objects.requireNonNull(modBus, "modBus");
        Objects.requireNonNull(classResolver, "classResolver");
        List<ModuleDefinition> modules = parse(metadata);
        for (ModuleDefinition module : modules) {
            if (!module.supports(physicalSide)
                    || module.requires().stream().anyMatch(id -> !isModLoaded.test(id))) {
                continue;
            }
            loadModule(module, modBus, classResolver);
        }
    }

    private static List<ModuleDefinition> parse(Reader metadata) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseReader(metadata);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Malformed Auto Storage compatibility module metadata", exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException(
                    "Compatibility module metadata root must be an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        rejectUnknownKeys(root, ROOT_KEYS, "metadata root");
        if (!root.has("schema") || !root.get("schema").isJsonPrimitive()
                || root.get("schema").getAsInt() != 1) {
            throw new IllegalArgumentException(
                    "Unsupported compatibility module metadata schema");
        }
        if (!root.has("modules") || !root.get("modules").isJsonArray()) {
            throw new IllegalArgumentException(
                    "Compatibility module metadata requires a modules array");
        }
        List<ModuleDefinition> modules = new ArrayList<>();
        Set<ResourceLocation> ids = new HashSet<>();
        Set<String> entrypoints = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray("modules")) {
            ModuleDefinition module = parseModule(element);
            if (!ids.add(module.id())) {
                throw new IllegalArgumentException(
                        "Duplicate compatibility module ID: " + module.id());
            }
            if (!entrypoints.add(module.entrypoint())) {
                throw new IllegalArgumentException(
                        "Duplicate compatibility module entrypoint: "
                                + module.entrypoint());
            }
            modules.add(module);
        }
        modules.sort(Comparator.comparing(ModuleDefinition::id));
        return List.copyOf(modules);
    }

    private static ModuleDefinition parseModule(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(
                    "Compatibility module entry must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        rejectUnknownKeys(object, MODULE_KEYS, "module entry");
        String rawId = requiredString(object, "id");
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            throw new IllegalArgumentException(
                    "Invalid compatibility module ID: " + rawId);
        }
        String entrypoint = requiredString(object, "entrypoint");
        if (!entrypoint.matches(
                "[A-Za-z_$][A-Za-z\\d_$]*(\\.[A-Za-z_$][A-Za-z\\d_$]*)+")) {
            throw new IllegalArgumentException(
                    "Invalid compatibility module entrypoint for " + id);
        }
        if (!object.has("requires") || !object.get("requires").isJsonArray()) {
            throw new IllegalArgumentException(
                    "Compatibility module " + id + " requires a mod ID array");
        }
        List<String> requires = parseRequirements(id, object.getAsJsonArray("requires"));
        ModuleSide side = switch (requiredString(object, "side")) {
            case "both" -> ModuleSide.BOTH;
            case "client" -> ModuleSide.CLIENT;
            case "server" -> ModuleSide.SERVER;
            default -> throw new IllegalArgumentException(
                    "Invalid compatibility module side for " + id);
        };
        return new ModuleDefinition(id, entrypoint, requires, side);
    }

    private static List<String> parseRequirements(
            ResourceLocation moduleId,
            JsonArray values
    ) {
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(
                        "Compatibility module " + moduleId
                                + " has a non-string required mod ID");
            }
            String modId = value.getAsString();
            if (!ResourceLocation.isValidNamespace(modId)) {
                throw new IllegalArgumentException(
                        "Compatibility module " + moduleId
                                + " has invalid required mod ID " + modId);
            }
            if (!unique.add(modId)) {
                throw new IllegalArgumentException(
                        "Compatibility module " + moduleId
                                + " repeats required mod ID " + modId);
            }
            result.add(modId);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "Compatibility module " + moduleId
                            + " requires at least one mod ID");
        }
        result.sort(String::compareTo);
        return List.copyOf(result);
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Compatibility module metadata requires string field " + key);
        }
        String value = object.get(key).getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Compatibility module metadata field " + key + " cannot be blank");
        }
        return value;
    }

    private static void rejectUnknownKeys(
            JsonObject object,
            Set<String> allowed,
            String location
    ) {
        object.keySet().stream()
                .filter(key -> !allowed.contains(key))
                .findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException(
                            "Unknown compatibility metadata field " + key
                                    + " in " + location);
                });
    }

    private static void loadModule(
            ModuleDefinition module,
            IEventBus modBus,
            ClassResolver classResolver
    ) {
        String targets = String.join(", ", module.requires());
        try {
            Class<?> raw = classResolver.resolve(module.entrypoint());
            if (!AutoStorageCompatModule.class.isAssignableFrom(raw)) {
                throw new IllegalStateException(
                        "Entrypoint does not implement AutoStorageCompatModule");
            }
            AutoStorageCompatModule entrypoint =
                    (AutoStorageCompatModule) raw.getDeclaredConstructor().newInstance();
            entrypoint.register(new Context(
                    module.id(),
                    module.requires(),
                    module.id().getNamespace(),
                    modBus));
        } catch (ClassNotFoundException | NoSuchMethodException
                 | InstantiationException | IllegalAccessException exception) {
            throw failure(module, targets, exception);
        } catch (InvocationTargetException exception) {
            throw failure(module, targets, exception.getCause());
        } catch (LinkageError error) {
            throw failure(module, targets, error);
        } catch (RuntimeException exception) {
            throw failure(module, targets, exception);
        }
    }

    private static IllegalStateException failure(
            ModuleDefinition module,
            String targets,
            Throwable cause
    ) {
        return new IllegalStateException(
                "Compatibility module " + module.id()
                        + " failed for loaded target mods [" + targets + "]",
                cause);
    }

    private enum ModuleSide {
        BOTH,
        CLIENT,
        SERVER
    }

    private record ModuleDefinition(
            ResourceLocation id,
            String entrypoint,
            List<String> requires,
            ModuleSide side
    ) {
        private boolean supports(Side physicalSide) {
            return side == ModuleSide.BOTH
                    || side == ModuleSide.CLIENT && physicalSide == Side.CLIENT
                    || side == ModuleSide.SERVER && physicalSide == Side.SERVER;
        }
    }

    private record Context(
            ResourceLocation moduleId,
            List<String> requiredMods,
            String registrationNamespace,
            IEventBus modBus
    ) implements AutoStorageCompatContext {
    }
}
