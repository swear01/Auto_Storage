# Addon Development

Auto Storage exposes a bounded NeoForge addon SDK for deterministic stations,
recipes, typed resources, transfers, transforms, and station variants. Addons
use the same public registries as Auto Storage's bundled compatibility modules.
Storage, planning, validation, and mutation remain server-authoritative.

This document is the authoritative addon guide. The GitHub Wiki copy must match
it at every SDK release; edit this file first. The Wiki page is intentionally
not listed in the player-manual Home contents or sidebar.

## Start with Compat Kit

Use the [Auto Storage Compat Kit](compat-kit.md) before writing a bundled
integration or independent addon. It scans one reproducible target jar/source
revision, creates an explicit reviewed recipe contract, generates a deliberately
RED SDK-only scaffold, and runs the contract's verification gates. It does not
infer consumption, catalysts, outputs, units, station costs, or determinism.

```bash
tools/compat-kit/compat-kit scan --help
tools/compat-kit/compat-kit decide --help
tools/compat-kit/compat-kit scaffold --help
tools/compat-kit/compat-kit verify --help
```

## Add the SDK

Every GitHub release contains:

- `auto_storage-<version>.jar` — the player/runtime mod;
- `auto_storage-<version>-api.jar` — the compile-only SDK;
- `auto_storage-<version>-api-sources.jar`;
- `auto_storage-<version>-api-javadoc.jar`.

The public GitHub release can be consumed without copying a jar into `libs/`:

```groovy
def autoStorageVersion = "0.3.0"

repositories {
    ivy {
        name = "AutoStorageReleases"
        url = uri(
                "https://github.com/swear01/Auto_Storage/releases/download/"
                        + "v${autoStorageVersion}")
        patternLayout {
            artifact("[artifact]-[revision](-[classifier]).[ext]")
        }
        metadataSources {
            artifact()
        }
    }
}

dependencies {
    compileOnly(
            "com.swear.autostorage:auto_storage:${autoStorageVersion}:api")
    runtimeOnly(
            "com.swear.autostorage:auto_storage:${autoStorageVersion}")
}
```

Use normal NeoForge dependency metadata to require Auto Storage. If the addon
integrates another mod, declare that target dependency in the addon's own
metadata as well. Do not pin player installations to Auto Storage's
representative CI fixture versions.

Compat Kit contracts list every target Maven repository as an explicit HTTPS
URL. The generated addon copies those repositories into `build.gradle`; it
does not infer Modrinth, Curse Maven, or an upstream repository from a
dependency coordinate. Keep this list minimal and reviewable. The generated
build resolves the reviewed target dependency separately and checks its exact
jar SHA against `source_audit_sha256` during both `build` and
`runGameTestServer`; a different artifact fails before compatibility evidence
can pass.

An independent-addon contract uses fixture `main`, declares exactly `build`
and `runGameTestServer`, and maps every evidence record to one of those actual
tasks. Bundled task names are not translated or treated as equivalent.

## Register through one facade

Create addon-owned `DeferredRegister` instances with the focused API classes,
then wire all of them to the addon's mod event bus in one call:

```java
public ExampleAddon(IEventBus modBus) {
    AutoStorageAddon.register(MOD_ID, modBus, addon -> addon
            .machineDescriptors(MACHINES)
            .recipeFamilies(RECIPES)
            .resourceKinds(RESOURCE_KINDS)
            .containerStrategies(CONTAINERS)
            .blockStrategies(BLOCKS)
            .transformProviders(TRANSFORMS)
            .machineVariantContributors(VARIANTS));
}
```

The complete compilable example is
[`examples/addon/src/main/java/example/autostorage/ExampleAddon.java`](../examples/addon/src/main/java/example/autostorage/ExampleAddon.java).
`./gradlew compileAddonExampleJava` compiles it against only the generated API
jar and NeoForge/Minecraft dependencies, not Auto Storage's implementation
output.

The facade verifies:

- every register targets the expected Auto Storage custom registry;
- every `DeferredRegister` uses the addon's namespace;
- the same register is not wired twice;
- at least one Auto Storage registry is contributed;
- reload-hook IDs belong to the addon namespace.

Registry entries and reload hooks are registration-time contracts. Runtime hot
registration is unsupported. Call the facade from the addon's mod constructor:
Auto Storage closes the registration window at NeoForge's first registry event,
and a later call fails immediately instead of attaching listeners that can no
longer run.

## Extension points

### Stations

Use `MachineDescriptorApi.createDeferredRegister(MOD_ID)` and register one
stable descriptor per logical station family. A descriptor can represent one
station item or multiple exact variants with different rational work rates.
Choose `MachineCategory.PROCESS`, `INSTANT`, or `TRANSFORM`; addon code does not
depend on the internal `MachineEnergyTable`.
See [Machine Descriptor API](machine-descriptor-api.md).

### Deterministic recipe families

Use `RecipeFamilyApi.createDeferredRegister(MOD_ID)`. One entry covers one exact
recipe class plus exact `RecipeType`, not individual recipe IDs.

- `singleItemToItem` covers one consumed item and one deterministic item output.
- `deterministicResources` covers bounded exact item/fluid/energy/chemical/addon
  inputs, catalysts, tools, remainders, multiple outputs, and station costs.
- `deterministicResourceVariants` covers a bounded set of complete deterministic
  plans selected from exact available stacks.

See [Recipe Family API](recipe-family-api.md).

### Typed resources

Register a `StorageResourceKind` for a stable kind ID. Add
`StorageResourceContainerStrategy` entries for item-container deposit/withdraw
and `StorageResourceBlockStrategy` entries for sided world capabilities.
Multi-key Core mutation uses one `StorageResourceTransaction`.

If the registered representative item is not enough to render an exact
resource variant, register a client-only icon renderer during client setup:

```java
TerminalResourceRendererApi.register(
        MY_RESOURCE_KIND,
        GuiGraphics.class,
        (graphics, key, amount, x, y, partialTick) -> {
            renderExactResource(graphics, key, amount, x, y, partialTick);
            return true;
        });
```

The generic context keeps `auto_storage-<version>-api.jar` free of
`net.minecraft.client` bytecode references while still giving client modules a
typed `GuiGraphics` lambda. Never call this hook from common or
dedicated-server initialization. IDs are bounded and unique; duplicate
renderers fail explicitly. Client renderer registration closes before menu
screens are registered; later calls fail instead of mutating a live terminal.
Returning `false` asks the terminal to render the resource kind's registered
representative item instead.

See [Typed Resource Storage](typed-resource-storage.md).

### Transform providers

Create a `DeferredRegister<TransformProvider>` through
`TransformProviderApi.createDeferredRegister(MOD_ID)`. Each provider resolves
one exact inserted item into a positive typed output and, optionally, a matching
station-work cost. Resolvers must be deterministic and side-effect free.

Auto mode only discovers matching uses. The server revalidates the exact input,
selected provider, output capacity, and station work before one
simulate-then-commit mutation.

### Existing-station variants

Create a `DeferredRegister<MachineVariantContributor>` through
`MachineVariantContributorApi.createDeferredRegister(MOD_ID)`. A contribution
adds exact station items and rates to an existing logical descriptor without
replacing its ID or recipe family.
The target descriptor must exist when Auto Storage materializes the registry
snapshot. A misspelled or removed target fails with both the contribution ID and
descriptor ID instead of being ignored.

### Capabilities and recipe reload data

The facade also exposes the two bounded lifecycle hooks required by current
integrations:

```java
private static final BlockCapability<MyResourceView, Direction> CAPABILITY =
        BlockCapability.createSided(id("resource_view"), MyResourceView.class);

AutoStorageAddon.register(MOD_ID, modBus, addon -> addon
        .resourceKinds(RESOURCE_KINDS)
        .capabilities(event ->
                AutoStorageCapabilityApi.registerSidedResourceCapability(
                        event,
                        CAPABILITY,
                        (resources, side) -> new MyResourceView(resources)))
        .recipeReload(id("runtime_recipes"), MyRecipes::refresh));
```

`capabilities` receives NeoForge's `RegisterCapabilitiesEvent`.
`AutoStorageCapabilityApi.registerSidedResourceCapability(...)` exposes only a
server-owned `StorageResourceHandler` for the Core, Import Bus, and Export Bus;
it does not expose their implementation block entities. The returned wrapper
must obey the same simulation, exact accepted-amount, and side rules as every
other typed-resource handler.
`recipeReload` runs at server start and after a global datapack reload. Hooks
run in stable ID order and freeze before gameplay; duplicate, foreign, or late
hooks fail explicitly. Auto Storage runs every hook before invalidating and
rebuilding the shared Craftable catalog, so one reload cannot retain a
pre-hook cache. Arbitrary tick, player, world, or mutation callbacks are not
part of the SDK.

## Failure and safety contract

Addon code must provide complete deterministic contracts. Auto Storage does not
infer recipes through reflection, generic `Recipe#getIngredients()`, EMI
widgets, serializer names, or machine names. It does not send resources into an
external machine and wait for world state.

Registration, linkage, or validation failures are startup errors. Auto Storage
does not silently skip a loaded but incompatible integration. Runtime crafting
uses current server recipe holders, checked long arithmetic, joint reservation,
capacity planning, and one atomic storage transaction. Client UI and EMI are
presentation/input surfaces only.

## Bundled compatibility modules

The player still installs one Auto Storage jar. Bundled integrations live in
isolated `src/compat/<mod-id>` source sets. Each module owns one
`src/compat/<mod-id>/compat-module.json` descriptor containing its entrypoint,
required mods, source-set/fixture names, and representative compile
dependencies. Gradle validates every descriptor and deterministically generates
the runtime `META-INF/auto_storage/compat-modules.json` index; there is no second
hand-written central module list. The loader checks every required mod ID before
resolving the module class. A present module uses
`AutoStorageCompatModule` plus the same registration facade available to
external addons. Every bundled entry must declare at least one required target
mod; an empty requirement list is rejected before classloading.

External addons do not add entries to Auto Storage's bundled module index.
They are ordinary NeoForge mods with their own entrypoint and dependency
metadata.

## API stability

The API artifact version equals the Auto Storage mod version.

- Before Auto Storage 1.0, the SDK is alpha. Breaking API changes require a
  minor-version increase and release-note migration section.
- Patch releases must remain source- and binary-compatible with the preceding
  release in the same minor line.
- Registry IDs and persisted resource/descriptor IDs are data contracts and
  must not be reused for different semantics.
- Optional target-mod versions in CI are representative evidence only. Addons
  should declare semantic ranges they actually support and report incompatible
  target changes clearly.

CI compiles a different-package fixture and the example against only the
classified API jar, compares its public `javap` output with
`api/api-surface.txt`, scans API bytecode for client/optional-mod links, builds
each bundled module against the API jar plus only its target dependencies, and
runs isolated plus all-mod GameTest gates. Any intentional API change must
review and update that snapshot in the same change.
