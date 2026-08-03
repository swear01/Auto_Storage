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
revision, creates an explicit reviewed recipe contract, generates either a
deliberately RED SDK-only scaffold or reviewed mechanical registration, and
runs the contract's verification gates. It does not infer consumption,
catalysts, outputs, units, station costs, or determinism.
Every scaffold/verify command also loads the committed source audit. The exact
audited recipe-candidate set, target identity, artifact SHA, and inventory
digest must match the contract, so recomputing a contract-only field cannot
hide an omitted candidate. Candidate records must also remain in the bucket
computed by the current scanner; changing a recipe into a station/resource
record is rejected. When source is supplied, at least one classified candidate
must map to a tracked, non-ignored Java file in that Git module; ignored build
output or an unrelated clean checkout cannot contribute its revision as target
evidence. Candidate-source suffixes match only at path-segment boundaries, and
risk evidence must remain owned by an audited recipe candidate. The scanner
also rehashes the target after inspection, so an
atomically replaced jar cannot mix two artifacts in one audit.

```bash
tools/compat-kit/compat-kit scan --help
tools/compat-kit/compat-kit decide --help
tools/compat-kit/compat-kit migrate-contract --help
tools/compat-kit/compat-kit validate-probe --help
tools/compat-kit/compat-kit generate --help
tools/compat-kit/compat-kit conformance --help
tools/compat-kit/compat-kit resource-scaffold --help
tools/compat-kit/compat-kit scaffold --help
tools/compat-kit/compat-kit verify --help
```

`generate` accepts only a completed contract plus a reviewed plan bound to its
exact digest. It emits direct typed station/family registration for bounded
safe shapes and rate bindings; a handwritten provider still owns exact typed
selection, catalysts/tools/remainders, and costs. Unsupported semantics remain
an explicit RED boundary. Dynamic rate accessors are reviewed as integral and
converted exactly; families sharing a descriptor must share the same reviewed
definition. Each generated family also declares its runtime `registration_id`;
the audit family key is evidence identity and is not assumed to be a registry
path. Family-derived Java names use a collision-free `family$` prefix for
digit-leading or reserved IDs and shared identifier validation. `conformance`
requires a batch in `2..Long.MAX_VALUE`, rejects happy-delta × batch signed-long overflow, and
generates repeated snapshot/delta/rollback assertions
around addon-supplied operations rather than trusting booleans returned by the
addon. `resource-scaffold` requires a positive signed-long sample amount and generates only public-API custom-resource boundaries,
sample-keyed persistence/transfer assertions, and rejects built-in Item, Fluid,
and NeoForge Energy support. Valid digit-leading resource IDs are normalized
through one collision-checked Java-name derivation that prefixes `_` where
required. None of these commands add runtime reflection or
make the client authoritative. Repository tests regenerate and compile the
committed generated-scaffold fixture against the public API jar.

Supply the target's complete non-JDK compile ancestry through repeatable
`--classpath` for `scan` and every complete consumer; every unresolved external superclass or interface fails
instead of silently hiding a structural recipe family, including classes whose
names look like client viewers, builders, or datagen helpers. The audit records
only structurally reachable classpath artifact SHA/size evidence. For each
reachable compile API that is not transitively available from the target, pass
`--classpath-dependency <sha256>=group:name:version[:classifier]`; the audit
persists that exact coordinate and each ancestry jar is rehashed after all
inherited-bytecode inspection so an in-flight replacement fails. A structurally discovered recipe inspects implementation classes along
its target/classpath hierarchy and attributes compact inherited risks to the
concrete recipe. Completed
contracts separately bind the
recipe-class inventory and effective recipe-data/data-pack digest. Runtime
probe JSON must pass `validate-probe` with the same audit and optional plan;
schema validation alone does not prove those cross-file identities.
Every complete generation, conformance, resource, scaffold, and verification
command also receives the exact target jar. Compat Kit rehashes it and rebuilds
the complete sorted class/metadata inventory instead of trusting an edited
audit's self-consistent count or digest. It also rebuilds the target jar recipe
source count and, when no external data root contributed recipes, requires the
entire recorded recipe inventory to match those jar bytes. Generated addon CI
uses `stageCompatKitTargetArtifact` plus `stageCompatKitAncestryArtifacts`, then
passes `build/compat-kit/target.jar` to `verify --jar` and every staged ancestry
jar through repeatable `--classpath`.
The generated ancestry task searches the target's transitive dependency graph,
NeoForge's additional runtime classpath, and the ModDev
`createMinecraftArtifacts` outputs, normalizes their archive layout, then
stages only SHA/size matches from the audit. The scaffold uses the same pinned
Parchment mapping baseline as Auto Storage so the generated NeoForge/Minecraft
development artifact reproduces the scanner's canonical archive. Build that
scanner input with `compat-kit normalize-jar <raw.jar> <canonical.jar>` because
raw ModDev ZIP metadata is not cross-run stable; generated staging repeats the
sorted, fixed-timestamp, stored-entry normalization only for ModDev outputs.
Maven artifacts remain raw exact bytes. Scanner-format-16 and later
`ancestry_dependencies` are emitted
automatically as non-transitive `compileOnly` and
`compatKitAncestryArtifacts` declarations in independent addons, and as
descriptor-owned compile dependencies in bundled modules for every reachable
audited ancestry coordinate; hand-editing root Gradle is not a supported
workaround, and an unresolved hash remains a hard failure.
Complete consumers reopen the target and every supplied ancestry jar,
reconstruct the reachable external metadata graph, reject duplicate
inspectable classes across the complete supplied set, rebuild exact NeoForge
target metadata and candidate public signatures, derive named nested source
types from exact `InnerClasses` metadata, and recompute bounded
private-bytecode risk evidence, so editing target/classpath-owned records
cannot remove structural recipe candidates while preserving a self-consistent
JSON audit. If any reconstructed parent is neither present in those exact jars
nor a selected JDK 21 class or known root, validation fails for missing
ancestry instead of accepting a truncated graph.

The scanner first verifies that resolved `javap` belongs to a JDK whose
`release` metadata and actual `javap -version` both report major version 21,
then accepts platform ancestry only
when the class is present in that JDK's module inventory. Package prefixes such as `javax.*` do not
bypass missing classpath evidence. It uses class-file `SourceFile`,
`InnerClasses`, and `EnclosingMethod` metadata for source ownership and nested
classes. Thus a top-level or source-addressable named class whose identifier
contains `$` remains auditable while local, anonymous, and synthetic classes
stay excluded. A named class under an excluded owner is also excluded rather
than assigned a source-level owner derived from its binary name. Owner-chain
inspection and named nested `source_class` derivation are both iterative,
archive-memoized for inspectability, and limited to 1,024 levels; a
nested owner missing from the same archive fails closed. When
`SourceFile` is absent, a supplied source checkout fails as unavailable rather
than guessing a same-named compilation unit. Format 15 stores binary and
source-level Java names, each candidate's structural
classification, a separate sorted top-level `structural_hierarchy` inventory,
an artifact/classpath-bound structural candidate digest, and direct metadata for
every target class plus reachable ancestry. The artifact also binds the complete
target-class count and graph digest. Validation independently reconstructs each
candidate from that graph, so removing both derived indirect-path copies cannot
bypass review. Generated Java uses
the source-level name for source-addressable nested classes and preserves legal
top-level `$` identifiers. Direct public
`extends`/`implements` declarations provide a second bucket cross-check;
generic type bounds do not count as direct ancestry. Legacy formats 7 through 16
must be rescanned with `migrate-audit`. A class name that normalizes to no
alphanumeric family ID uses deterministic `class_<binary-name-hex>` evidence.
Explicit `--data-root` evidence binds all bounded tag JSON and bounded
`pack.mcmeta` bytes while keeping recipe counts recipe-only; a root with a
top-level data-pack `filter` or `overlays` field is rejected rather than
misreporting filtered or overlay recipes as effective. Any ancestry artifact
SHA/size or `ancestry_dependencies` coordinate change affects `diff` and
reopens migrated family decisions even when target public signatures are stable.
Each ancestry class must have exactly one jar owner. The scanner rejects
duplicate definitions even when their hierarchy declarations match, validates
JDK 21 before a cache hit, and invalidates the cache when the selected JDK module
identity changes. Recipe risks traverse the complete non-JDK superclass and
interface graph, not only the hierarchy path that established `Recipe`
classification.

Complete contracts use lowercase resource locations for recipe types, station
descriptor IDs, and station variant items. Generated rate bindings are
one-to-one with those station items; duplicate descriptor variants and duplicate
rate bindings are rejected. `registry_block_method` bindings name their block ID
separately from the representative station item. The generated
`single_item_to_item` path accepts only the documented one-input/one-primary
output selectors and exact amount expressions, and method bindings reject Java
keywords; all other shapes need a reviewed provider. Generated custom-resource tests verify that their sample key and
amount were seeded, that `clear()` removes that key, and that `load()` restores
the exact snapshot before exercising the remaining persistence boundaries. `worker-package`
runs every declared Gradle task without replacing the worker's JDK environment,
so the target's authoritative GameTest remains part of a delegated worker gate.
Worker instructions identify targets only by validated mod ID; free-form display
metadata is retained as escaped, explicitly untrusted JSON evidence.

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
dependency coordinate. The same reviewed list must resolve any persisted
`ancestry_dependencies` coordinate. Keep this list minimal, ordered, and reviewable; declaration order is preserved
because it can change which repository serves a coordinate. Reviewed
repositories are emitted before defaults and restricted to target/runtime
groups. Explicit runtime groups cannot fall back to Maven Central even when
the reviewed repository list is empty; the target can fall back only under its
exact SHA check. Fixed repository filters reserve
Patchouli and Auto Storage for BlameJared and the release Ivy repository. The generated
build also copies every explicit `target.runtime_dependencies` entry, so
required libraries such as GuideME do not depend on transitive metadata or an
undocumented post-scaffold edit. Target and explicit runtime dependencies are
non-transitive on both target compile and runtime classpaths, so every required
companion must be listed exactly once; this additional list must not repeat the
primary `target.dependency`. The generated build resolves the reviewed
target dependency separately and checks its exact
jar SHA against `source_audit_sha256` during both `build` and
`runGameTestServer`; it also copies the reviewed audit to `compat/audit.json`.
Reviewed repository URLs, dependency coordinates, and group filters are
serialized as literal Groovy strings rather than interpolated text. Maven
coordinates must use exact `group:name:version` structure and cannot contain
control characters.
Repository-owned bundled fixtures may additionally declare exact
`target.runtime_artifact_transforms` for reviewed removal of unrelated test-only
ZIP entries. This contract field is an object keyed by exact runtime dependency,
which makes duplicate dependency plans unrepresentable to schema-only consumers.
One bundled contract may own exactly one transform, so it also cannot alias one
artifact SHA through multiple coordinates; separate descriptors may share the
same identical plan.
The pristine coordinate and SHA remain the audit, compile, and
artifact-gate input; only isolated/matrix test runtimes receive the deterministic
transformed output. Identical transforms shared by multiple bundled descriptors
deduplicate to one artifact, and conflicting declarations fail closed. Independent
addon scaffolds reject this bundled-descriptor-only field rather than silently
running a different artifact contract.
Because Auto Storage requires Patchouli on both sides, the generated
GameTest runtime includes the matching Patchouli artifact and its repository.
A different target artifact or source audit fails before compatibility
evidence can pass. Verification regenerates the expected `build.gradle`,
`settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, and Gradle
wrapper jar/properties from the reviewed contract and generator before
comparing each with both the file and manifest. An addon therefore cannot
replace the launcher or artifact gate and authorize that edit by updating its
own manifest hash. Scaffold generation preflights all destinations and parent
directories before its first write. Existing conflicting files, symlinked
roots/ancestors/targets, or a file-valued `src` ancestor fail without leaving
workflow, manifest, wrapper fragments, or writes outside the output root. This
includes existing symlink ancestors above a not-yet-created output root, and
lexical `..` segments are normalized before that preflight.
Byte-identical reruns repair both generated launchers to mode `0755`.

An independent-addon contract uses fixture `main`, declares exactly `build`
and `runGameTestServer`, and maps every evidence record to one of those actual
tasks. Published completed-contract schemas require that non-null fixture,
GameTest count in `1..2147483647`, authoritative task, nonempty task list, all twelve
exact checks, and every evidence mapping; unresolved RED drafts may keep those
fields empty. A `runGameTestServer` evidence marker must live inside the annotated
GameTest method that executes the assertion; file-level constants, comments,
and detached helpers are rejected. Runtime probes require an explicit
`--game-test-namespace`; conformance and resource plans require the same exact
`game_test_namespace`, and every generated class emits it in
`@GameTestHolder`. An addon's holder must resolve to the
addon's enabled `<target_mod_id>_auto_storage` namespace; a test compiled in
another namespace is not evidence. Referenced holder constants are resolved by
their actual declaring class, not the source file stem. Comments inside the method do not count,
while executable string arguments may carry assertion markers. Eligible Java
Unicode escapes are rejected before evidence parsing so they cannot create
comments or method boundaries before tokenization. Its fresh-world cleanup
rejects a symlinked lexical addon root, every ancestor of that root, `run`, or
`world`, plus resolved paths outside the addon root before deletion. Bundled
task names are not translated or treated as equivalent.
GameTest output must contain
exactly one success summary with the reviewed count; conflicting summaries fail
even when one count matches. A published passing report must carry the strict
target identity, all twelve mandated check records, and exactly the addon
`build` and `runGameTestServer` command records. Commented or string-literal
`@GameTest` text is ignored by both source counting and method-body evidence
extraction; intermediate annotation initializers are skipped until the real
method body, escaped triple quotes stay inside Java text blocks, and every
GameTest evidence file must belong to the fixture source set executed by its
declared task.

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
- `dynamicDeterministicResources` covers the same one-plan contract when loaded
  server configuration can change its exact plan or cost after registration;
  it re-resolves those values without dropping the exact candidate index. Its
  required side-effect-free `dynamicStateToken` must change whenever those
  values change so shared Craftable results cannot survive a config reload.
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

The reviewed contract must preserve every scanner risk attached to each recipe
family. Process station variants use positive work rates; Instant variants use
zero rates. Every rational numerator and denominator must fit signed Java
`long`, including documents checked only through the published generation-plan
schema. Accepted families keep an explicit `costs` list, which may be empty
only for a reviewed runtime family that is genuinely free. A bundled contract's
GameTest task must be the task derived from its fixture name; another fixture
with the same expected count cannot provide its runtime evidence. Compat Kit
rejects drift before generating code that runtime descriptor validation would
reject.

Registration, linkage, or validation failures are startup errors. Auto Storage
does not silently skip a loaded but incompatible integration. Runtime crafting
uses current server recipe holders, checked long arithmetic, joint reservation,
capacity planning, and one atomic storage transaction. Client UI and EMI are
presentation/input surfaces only.

## Bundled compatibility modules

The player still installs one Auto Storage jar. Bundled integrations live in
isolated `src/compat/<mod-id>` source sets. Each module owns one
`src/compat/<mod-id>/compat-module.json` descriptor containing its entrypoint,
required mods, source-set/fixture names, representative compile dependencies,
and matrix assertion metadata (required mods, accepted/rejected evidence, and
per-module isolated recipe-inventory digest). Full multi-module coexistence and
unclaimed inventories are computed by the matrix GameTest and recorded in
`build/reports/terminal-scale-*.json`; they are not committed expected digests.
Gradle validates every descriptor and deterministically generates the runtime
index, matrix assertion manifest, and CI/release
`build/reports/compatibility-modules.md`.
Descriptor `expectedTests` is a positive JSON integer;
fractional or wider numeric values fail instead of being truncated.
The generated `META-INF/auto_storage/compat-modules.json` has no second
hand-written central module list, and CI consumes descriptor-derived
`runCompatFixtureGameTestServers` rather than a hand-maintained per-module
workflow list. Routine module PRs must not hand-edit aggregate README/overview
tables or regenerate a tracked summary. The loader checks every required mod ID before
resolving the module class. A present module uses
`AutoStorageCompatModule` plus the same registration facade available to
external addons. Every bundled entry must declare at least one required target
mod; an empty requirement list is rejected before classloading. Compat Kit
descriptors also carry the reviewed target repositories and artifact SHA, so a
non-central Maven target resolves through contract-owned configuration rather
than a root-build guess. Compat Kit rejects target IDs that would become Java
reserved package segments instead of emitting uncompilable source.
When present, descriptor `runtimeArtifactTransforms` is generated from the
reviewed dependency-keyed contract object as a sorted descriptor record list.
Exact dependency/SHA/entry validation happens before Gradle
wires a transformed jar, and the pristine jar never shares an isolated or matrix
runtime classpath with that output. Global SHA ownership includes every audited
artifact as well as every transform. Direct runtime-only declarations are
checked after dependency setup, and each affected run verifies the complete
resolved source-set runtime classpath by artifact SHA, including implementation,
inherited, transitive, and file dependencies, before Minecraft starts.
Before scaffold writes, the generated module ID, entrypoint, source set, and
fixture are compared with all existing descriptors so normalized Java/Gradle
identifier collisions fail closed.

External addons do not add entries to Auto Storage's bundled module index.
They are ordinary NeoForge mods with their own entrypoint and dependency
metadata.

## API stability

The API artifact version equals the Auto Storage mod version.

- Before Auto Storage 1.0, the SDK is alpha. Breaking API changes require a
  minor-version increase and release-note migration section.
- Patch releases must remain source- and binary-compatible with the preceding
  release in the same minor line.
- Compat Kit therefore generates a current-minor dependency range; version
  0.3.0 produces `[0.3.0,0.4)`, not an open-ended pre-1.0 range.
- Compat Kit publication receives the authoritative Gradle `mod_version` and
  fails if it differs from the tool version. Its addon example and ten
  machine-readable schemas use explicit tracked allowlists, never recursive or
  globbed local-output scans.
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
