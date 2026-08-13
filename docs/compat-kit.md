# Auto Storage Compat Kit

Compat Kit is the development-time workflow for adding deterministic cross-mod
support without repeatedly rereading an upstream repository or weakening Auto
Storage's transaction boundary. It collects facts, makes unresolved semantics
explicit, generates reviewed mechanical code or RED boundaries, and runs
declared verification gates. It is never loaded by Minecraft.

This file is authoritative. The GitHub Wiki `Compat Kit` page mirrors it, but
the developer page is intentionally absent from the player-manual Home contents
and sidebar.

## Safety boundary

Compat Kit automates evidence and boilerplate, not recipe semantics.

- No player-runtime jar scanning, reflection fallback, serializer-name
  semantics, machine-name semantics, or EMI/JEI crafting logic.
- `scan` structurally follows class-file inheritance for concrete `Recipe` and
  `RecipeSerializer` implementations. Names are only candidate evidence for
  resource and station surfaces; they never authorize behavior.
- `scan` publishes public surfaces, bounded recipe-data structure, and risk
  evidence only. It also inspects
  bounded private bytecode to detect hidden randomness or world/entity access,
  but never writes that private bytecode into the audit.
- A human or reviewing agent explicitly decides consumed inputs,
  catalysts/tools, remainders, all outputs, typed units, station rates, costs,
  deterministic bounds, and rejections.
- `needs_decision` blocks `scaffold`, `generate`, `conformance`, and `verify`.
- A loaded but incompatible integration fails explicitly. The tool never emits
  a compiling empty adapter or silently skips a required check.
- A representative target version and SHA are reproducible audit evidence, not
  a player-facing exact dependency pin or multi-version support promise.

Use Python 3.11 or newer (`tomllib` is required) and JDK 21 so `javap` matches
the project toolchain:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
python3 --version
```

## Files and ownership

```text
tools/compat-kit/
  compat-kit                         executable wrapper
  compat_kit.py                     standard-library CLI
  schema/*.schema.json              strict machine-readable contracts
  examples/github-actions/          reusable addon CI example
compat/audits/<mod-id>/<version>.json
compat/contracts/<mod-id>.json
compat/generation/<mod-id>.json
build/compat-kit/cache/<scan-identity>/v*/ ignored versioned scan cache
build/compat-kit/report.json         ignored verification report
```

The audit and reviewed contract are committed. Caches, downloaded jars, source
checkouts, and transient reports remain under ignored `build/`.

## End-to-end workflow

### 1. Acquire reproducible evidence

Download one official representative jar and, when available, check out its
matching source tag or commit. Record the artifact URL, SHA-256, and source
revision in the compatibility document. The checkout passed to `scan` or
`diff` must be clean, including untracked files, so the recorded revision fully
describes the inspected source. When the supplied source is a module
subdirectory, Compat Kit discovers the enclosing Git worktree, requires that
whole worktree to be clean, and records its HEAD while keeping evidence paths
relative to the supplied module. A supplied `--source` must belong to a Git
worktree; omit it rather than presenting uncommitted/unversioned contents as
source evidence. When the target jar has classified candidates, at least one
candidate must map to a tracked, non-ignored, non-symlink Java source file under
the supplied module; ignored build output and a clean but unrelated checkout
are rejected instead of borrowing HEAD. Matching is segment-aware: a path such
as `notsamplemod/recipe/CrushingRecipe.java` cannot satisfy the candidate
`samplemod.recipe.CrushingRecipe`. Never silently replace a failed source.
Committed source evidence is also fail-closed: file entries are sorted, unique,
canonical POSIX repository-relative `.java` paths. A null revision requires an
empty file list; a recorded revision plus classified candidates requires at
least one source file. Validation does not reconstruct a class-to-file name
from the binary class name because legal package-private classes may live in a
differently named compilation unit.

### 2. Scan

```bash
tools/compat-kit/compat-kit scan \
  --jar build/compat-kit/artifacts/target.jar \
  --source build/compat-kit/sources/target \
  --classpath build/compat-kit/classpath/dependency.jar \
  --classpath-dependency <dependency-jar-sha256>=group:name:version[:classifier] \
  --data-root build/compat-kit/datapacks/target-override \
  --output compat/audits/target/1.2.3.json
```

If NeoForge metadata declares multiple `[[mods]]`, `scan` requires
`--mod-id <selected-mod-id>`. The selected entry is persisted as the audit
target; omitting `--mod-id` remains valid only when metadata declares exactly
one mod.

`--classpath` is repeatable and supplies the target's complete compile-time
ancestry without turning dependency classes into target candidates. Every
non-JDK superclass and interface reachable from the target jar must resolve;
an incomplete classpath fails instead of silently omitting a structurally
hidden recipe family. This check runs before name-bucket classification, so a
class named like a client viewer, builder, or datagen helper cannot bypass
ancestry validation. Before reading platform classes, Compat Kit requires the
resolved `javap` toolchain's `release` metadata and the actual `javap -version`
process to report major version 21. JDK
ancestry is then resolved from that JDK's `jmods` inventory, not from a
package-prefix allowlist, so platform classes
such as `org.xml.sax.*` are recognized without weakening external dependency
checks. At most 128 jars and 200,000 classes are read,
duplicate/conflicting classes fail, and only artifacts reachable from the exact
structural graph remain in the sorted audit SHA/size set. Repeatable
`--classpath-dependency <sha256>=group:name:version[:classifier]` binds a
supplied jar to the exact Gradle coordinate needed when the target does not
transitively publish that compile API; mappings for unreachable jars are
dropped with those jars. Artifact and coordinate sets both enter the audit and
cache identity. Every classpath jar is rehashed
after inspection just like the target; replacing a dependency during a scan
fails instead of mixing two artifacts in one audit. Class-name constants are
decoded as JVM modified UTF-8, so
unrelated NUL or supplementary string constants cannot abort a valid scan.
`--data-root` is repeatable and follows data-pack precedence: the target jar is
the first layer and later supplied roots override earlier recipes by exact ID.
Roots and recipe/tag files must be real, non-symlink paths. Every bounded
`data/*/tags/**/*.json` resource and bounded `pack.mcmeta` enter each root
digest without being counted as recipes; the file bound applies globally
across ordered roots. Roots declaring top-level data-pack `filter` or
`overlays` metadata fail because removal and overlay-directory activation are
not modeled. Each recipe, tag, and `pack.mcmeta` payload is read once with a
bounded `limit + 1` read per inventory pass. Validation, recipe summaries,
payload hashes, and the ordered root digest all use that same byte snapshot;
tag payloads are hashed incrementally rather than retained. Compat Kit freezes
the ordered root arguments and performs a second complete inventory after
source evidence construction, immediately before caching or returning. A
persistent addition, removal, replacement, or metadata change during the scan
therefore fails explicitly instead of mixing parsed bytes with a later digest.
The deterministic audit
contains NeoForge identity, artifact SHA/size, source revision and exact source
paths, structurally classified concrete recipe and serializer classes,
recipe types, builders, datagen classes, client/viewer wrappers, concrete or
abstract block entities,
resource/station candidates, per-serializer recipe counts, bounded sample IDs,
top-level fields and array cardinalities, NeoForge condition types, override
provenance, and explicit risk flags. Risk
detection uses bounded `javap -c -p` output so non-public implementation risks
are not missed; only compact flags for randomness/chance, world/entity access,
multiblocks, live machine state, generic ingredient surfaces, unbounded
outputs, and capability mutations requiring simulation are persisted.
Structurally discovered recipes inspect every reachable non-JDK target/classpath
superclass and interface implementation, independently from the first path used
to classify the class as a `Recipe`; inherited findings are attributed to each
concrete audited recipe. Duplicate ancestry-class definitions fail closed even
when their hierarchy metadata matches, so bytecode ownership never depends on
classpath order. Each private-bytecode result is reduced to compact
flags before the next class is
read; platform-neutral concurrent pipe readers retain at most the configured
limit plus one byte and terminate `javap` immediately on overflow, so neither
one process nor all classes can bypass the memory bound. `RandomSource`,
`ThreadLocalRandom`, `SplittableRandom`, `SecureRandom`, and `RandomGenerator`
are all randomness evidence. Without `--source`, scans are
cached under `build/compat-kit/cache/` by jar SHA, scanner format, and the
resolved target mod ID; repeating the same identity needs no network access. A
scanner format change uses a new cache namespace instead of trusting stale
evidence.
Every repository-owned audit that backs a complete committed contract is
migrated in the same change; the committed-audit regression rejects legacy
records instead of skipping modules whose scaffold or verification would then
be unusable.
Named nested classes remain inspectable only when their complete owner chain is
present in the same archive; a missing owner fails closed rather than allowing
a descendant of an excluded anonymous, local, or synthetic owner through a
split classpath.
Data-root scans bind the ordered layer digests into their cache identity;
ancestry classpaths bind their exact artifact set. The resolved target mod ID
always enters the cache identity, so implicit and explicit selection of the
same single-mod target reuse one audit while two targets declared by the same
jar cannot reuse each other's audit. Cache metadata also records the selected
JDK 21 installation, release version, module-file identity, and the resolved
`javap` path, reported version, size, and SHA-256.
JDK validation occurs before every cache return, and an identity change forces
a fresh scan rather than reusing evidence from another module inventory. The
current scanner format is `17` with candidate classifier `4`; formats `7`
through `16` remain readable
only as explicit legacy evidence while committed contracts are migrated.
Complete validation, scaffolding, generation, and verification require a
current-format audit plus the exact target jar; only explicit migration paths
may consume legacy audit formats. Each complete consumer rehashes that jar and
independently rebuilds its NeoForge target metadata, complete sorted
class/metadata inventory, and every real candidate's public signature with the
pinned JDK 21 `javap`. It derives named nested `source_class` values from exact
`InnerClasses` metadata and recomputes bounded private-bytecode risk evidence
from the target/ancestry jars. The audit target mod ID selects the same metadata
entry when the exact jar declares multiple mods. Every supplied ancestry jar is
enumerated before graph traversal, so a second inspectable class owner fails
even when that class is not reachable from a target parent. A self-consistent
edited audit target, signature, count, source name, risk list, or digest cannot
replace those artifact bytes. The target jar's recipe source count is independently rebuilt on every
complete use; when `target_jar` is the audit's only recipe-data source, the
entire effective recipe inventory, serializer summary, overrides, and digest
must match the reopened jar exactly.
Scanner format 16 and later retain each candidate's structural classification and source-level
Java type, a separate sorted top-level `structural_hierarchy` inventory, and an
artifact/classpath-bound `structural_candidate_inventory_sha256`. Its
`structural_class_graph` starts from every target class and includes reachable
target/classpath ancestry; the artifact binds that complete target-class count
and graph digest. Validation reconstructs every candidate classification from
this complete graph. Both derived
hierarchy copies and the independent graph must agree, so deleting or rewriting
the hierarchy records cannot silently demote an indirect candidate. Generated Java uses
`source_class`: named nested binary `Outer$Inner` becomes source
`Outer.Inner`, while a legal top-level `$` remains unchanged. Validation also
cross-checks direct `extends`/`implements` parents in the public declaration;
generic type bounds are not treated as direct ancestry. The scanner format is
also stored in every audit and required by its published
schema, so an older structurally valid committed audit cannot authorize current
contract review.
Multi-release `META-INF/versions/` aliases are not treated as binary class names;
the root binary name is scanned once.
Chance, randomness, generic-ingredient, and capability-mutation detection read
both source-like method signatures and the `methodName:(descriptor)` form
emitted by `javap -c -p`.
Named nested classes are audited and mapped back to their top-level Java source,
including legal Java identifiers that themselves contain `$`. The scanner uses
`SourceFile`, `InnerClasses`, and `EnclosingMethod` metadata rather than
splitting the binary name on every `$`; a legal top-level `Recipe$1Variant`
therefore maps to `Recipe$1Variant.java`, while anonymous/local classes and
every class carrying the JVM
`ACC_SYNTHETIC` access flag are excluded. A named class whose ownership chain
passes through any of those excluded classes is also excluded: a binary name
such as `Outer$1$Key` has no truthful source-level owner, so the scanner never
reinterprets it as a top-level `$` identifier or invents a source name. Owner
inspection and named nested `source_class` derivation are both iterative,
memoized once per archive for inspectability, and fail if a chain exceeds
1,024 levels instead of relying on Python recursion or reparsing every ancestor.
If a classified class has no `SourceFile` attribute and `--source` was
supplied, scanning fails with an unavailable source mapping; it never guesses
that a package-private binary class lives in a same-named source file.
Family IDs use normalized simple class names. A legal identifier that normalizes
to an empty string uses `class_<binary-name-hex>` instead, while normalized
collisions append the same collision-free binary-name encoding.

Archive limits, the 1 MiB per-entry NeoForge metadata limit, the 16 MiB
per-entry class limit, candidate counts, signature size, source-file counts,
malformed metadata, unselected or missing multi-mod targets, missing JDK tools,
and `javap` failures all fail closed. Metadata and class sizes are checked before
decompression. The target path is rehashed after inspection; a replaced or
mutated jar cannot combine one artifact SHA with another artifact's metadata
or candidates. Every supplied ancestry jar has the same post-inspection
size/SHA check. Current-format cache entries and committed audits are also fully
validated down through target, artifact, source, candidate, and risk records;
a matching cache path does not authorize malformed or partial evidence. Every
candidate must remain in the bucket computed by the current scanner. Both scan
and committed-audit validation apply the same current priority for client/viewer,
builder, datagen, and structural/name classifications; moving a candidate into
a lower-priority bucket cannot hide it from contract review. Every risk-evidence
owner must also be one of the audited recipe
candidates, so risk cannot be detached from the class under review.

Recipe interfaces that structurally extend `Recipe` classify as `recipe_classes` before station/resource name terms; abstract non-interface bases remain concrete-gated. Otherwise public-signature validation rejects the audit.

Legacy format-7 through format-16 audits must be regenerated from the exact same reviewed artifact rather than edited in place:

```bash
tools/compat-kit/compat-kit migrate-audit \
  compat/audits/target/1.2.3-legacy.json \
  --jar build/compat-kit/artifacts/target.jar \
  --source build/compat-kit/sources/target \
  --output compat/audits/target/1.2.3.json
```

Migration rejects a current audit, a different target identity, or different
artifact bytes. Legacy validation still checks its versioned schema, structural
digests, target identity, and artifact identity, but does not reapply the
current candidate classifier to old buckets: classifier drift is the reason an
explicit migration may be required. Migration selects the legacy audit target
mod ID for the full current scanner pass. The exact artifact is then fully
rescanned with the current classifier, so no stale legacy classification is
preserved or used as semantic authorization.

### 2a. Generate unresolved machine and requirement proposals

```bash
tools/compat-kit/compat-kit propose \
  compat/audits/target/1.2.3.json \
  --output build/compat-kit/target-proposals.json
```

`propose` converts only current-format audit evidence into a compact review
surface. Public numeric methods and fields that mention time/rate/parallelism
are offered as bounded rate-binding candidates; slot counts are deliberately
ignored. This includes public tier fields such as Mekanism
`FactoryTier.processes`, but choosing the corresponding tier/item mapping still
requires review.
Recipe-data fields are classified as transaction-representable,
station-descriptor-representable, or unsupported live/world state. Every
proposal and binding stays `needs_decision`; the output cannot authorize a
contract, generate runtime reflection, or assert that a candidate machine is a
valid station.

### 2b. Generate a dedicated-server runtime probe

```bash
tools/compat-kit/compat-kit probe \
  compat/audits/target/1.2.3.json \
  --plan compat/probes/target.json \
  --game-test-namespace target_auto_storage \
  --output build/compat-kit/target-probe

tools/compat-kit/compat-kit validate-probe \
  build/compat-kit/target-probe/runtime-probe.json \
  --audit compat/audits/target/1.2.3.json \
  --plan compat/probes/target.json
```

The generated GameTest is server-only and evidence-only.
`--game-test-namespace` is mandatory, is recorded in `probe-spec.json`, and
becomes the class's exact `@GameTestHolder`; pass the namespace enabled by the
Gradle run that will execute it. The probe requires an
explicit `compatKitProbeOutput` system property, sorts and records every loaded
`RecipeManager` recipe ID/type/serializer/concrete class plus common public
ingredient/result values, and records target-namespace block, item, and block
entity type identities. The probe fails above 50,000 loaded recipes instead of
truncating silently. It contains no client imports or reflection. `--plan` is
optional, but when present it must match the exact current audit digest and may
add reviewed direct calls for public config values or capability surfaces. The
only accessor shapes are a value-wrapper static field, static method, registry
block method, or enum-constant numeric field. Target config and capability
candidates remain listed as unresolved in `probe-spec.json` until those
bindings are supplied; empty arrays do not authorize those semantics.
`validate-probe` is the mandatory cross-file gate for collected JSON: it loads
the committed audit and optional exact plan instead of relying on the output
schema alone. It validates the exact canonical probe-plan
digest recorded as `source_probe_plan_digest`, and
`compat-runtime-probe.schema.json` before using it as review evidence. Config
records accept only finite JSON numbers and capability records accept only
booleans; validation also requires the output IDs, sources, and capability
surfaces to match the reviewed plan exactly.

### 3. Decide

```bash
tools/compat-kit/compat-kit decide \
  compat/audits/target/1.2.3.json \
  --output build/compat-kit/target-contract-draft.json \
  --next-actions build/compat-kit/target-next-actions.md
```

The draft contains one entry for every recipe-class candidate. Complete each
entry as `accepted` or `rejected`; do not delete inconvenient candidates.
`next-actions.md` is the compact review surface and lists only unresolved work.
The published contract schema applies the same status-dependent boundary:
accepted families require recipe type, station, nonempty inputs/outputs with a
primary output, and a decision; rejected families require their decision. Once
no family remains `needs_decision`, the schema also requires the target
`dependency`, `repositories`, and `runtime_dependencies` scaffold inputs; RED
drafts with unresolved families remain valid. The same completed-contract
branch requires a non-null fixture, positive GameTest count, authoritative task,
nonempty Gradle tasks, all twelve exact checks, and one evidence mapping for
every check.
Recipe JSON `type` values follow Minecraft `ResourceLocation.parse` /
`bySeparator` rules: a namespaced id is kept as-is, a path-only value such as
`crafting_shaped` expands to `minecraft:crafting_shaped`, and a
separator-prefixed empty namespace such as `:crafting_shaped` also expands to
`minecraft:crafting_shaped`. Invalid path characters still fail closed. Digests
continue to hash the raw JSON payload bytes, not the expanded serializer id.

`source_recipe_inventory_sha256` binds the sorted recipe-class inventory;
`source_recipe_data_sha256` separately binds the effective recipe-data and
data-pack override digest. Every complete `generate`/`conformance`/
`resource-scaffold`/`scaffold`/`verify` invocation must also load the committed
source audit and exact target jar. Validation compares the contract's exact family-class set,
per-family scanner risk set, target identity, artifact SHA, and inventory digest
with that separate audit, then reconstructs the jar's class and applicable
recipe inventory; deleting a family, graph record, recipe record, or risk and
recomputing JSON fields still fails. The target JAR may not contain duplicate
recipe ZIP entry names; inventory rejects them before reading payloads so ZIP
name lookup cannot silently select one duplicate.
New contracts use 64 zeroes as the pending matrix recipe-inventory sentinel.
Once no family remains `needs_decision`, the published schema and complete CLI
validation both reject that sentinel; review must replace it with the exact
inventory digest before scaffold or verification.

An accepted family records:

1. exact recipe class and `RecipeType`;
2. station descriptor, category, variants, and rational rates;
3. every consumed input, catalyst, tool/durability, and remainder;
4. every primary and secondary output with exact typed units;
5. station-work and item/fluid/energy/chemical/addon-resource costs; use an
   empty `costs` list only when the reviewed runtime family is genuinely free;
6. deterministic bounds and source/class/method evidence;
7. target Maven repositories as explicit HTTPS URLs;
8. every additional required runtime artifact as an explicit dependency;
9. fixture, exact GameTest count, authoritative GameTest task, Gradle gates,
   and every required check mapped to `{task, source, marker}` evidence.

Process station variants require a positive rational rate. Instant station
variants require a zero numerator. Every numerator and denominator must fit a
signed Java `long`; the published generation-plan schema enforces the same
maximum. These rules match runtime
`MachineDescriptor` validation and fail before scaffolding.

## Transform-page candidates (automatic detection)

`compat-kit transform-candidates <target.jar>` lists one-way conversion-machine
candidates by matching class names and package paths against conversion terms
(`generator`, `generating`, `converter`/`convert`, `burner`, `boiler`,
`combustion`, `turbine`, `magmator`) while excluding datagen/rendering/packet/
client helpers. It is a fast evidence pass, not a review: the reviewer still
applies the Transform-page spec below.

A class belongs on the Transform page only when **all** of these hold:

1. **One-way**: it converts an input item (or a typed resource) into an output
   typed resource (FE, Mana, Source, gas, fluid…); never bidirectional
   charge/discharge, storage, or pure transfer.
2. **Discrete and storable**: one input unit yields one exact, storable output
   amount (continuous passive output such as wind/solar, or per-second output
   without an item input, is excluded).
3. **Deterministic**: the output amount is exact — no chance, no randomness,
   no entity/world dependence. Chance outputs are normalized to exact rational
   EV decimals (the #89 1/10000 basis pattern).
4. **Time-based is allowed**: burn/cooldown conversions belong on the Transform
   page as station-work uses (Botania Thermalily/Endoflame, Powah Furnator);
   the work equals the real-world cycle so the throughput matches the machine.
5. **Retained inputs are declared**: bucket/container/catalyst round-trips use
   the retained-items contract (#95), player-inventory-first with Core
   overflow.

Known rejection classes surfaced by the detector: fluid-input-only machines
(Mekanism Bio/Gas-Burning Generator, IE Diesel, EnderIO Combustion, IF
Biofuel), passive generators (Mekanism Wind/Solar), live multiblocks
(Extreme Reactors, Mekanism Fission/Turbine), and recipe-crafting machines
(covered by recipe families instead).

Generation plans may also declare `transform` families: one-way, exact-input
conversions that either produce immediately or consume station work over time.
The contract family for a transform uses a process/instant station descriptor,
a single `consume` item input with selector `transform.input`, one primary
non-item output, and an empty `costs` list (work lives in the plan bindings).
The plan entry binds exact accepted input items, the output typed key
(`kind`/`resource`), the amount per item, the optional station descriptor and
station work per item (station present iff work is positive), optional
retained items (`item`/`count`, unique, count 1..64) for bucket/container
round-trips, and the target/source label keys. Generated code emits the
`MachineDescriptor` plus a `TransformProvider` whose resolver returns
`null` for non-accepted inputs, so the scaffold RED fixture and the final
addon share the reviewed exact-input contract.

For a bundled module, `verification.game_test_task` is not arbitrary evidence:
it must match the task derived from the fixture's preserved camel case
(`evilCraftFixture` becomes `runEvilCraftGameTestServer`). This prevents a
same-count fixture from supplying another integration's runtime result.
The target mod ID must produce a valid, non-reserved Java package segment;
identifiers such as `class`, `true`, or `null` fail before files are written.
Audited candidate `source_class` values allow already-shipped bytecode under
contextual-keyword package segments (for example Railcraft
`mods.railcraft.world.module.*`). Package segments still reject reserved Java
keywords and literals such as `class`, `true`, and `null`; source-level
top-level and nested type components reject the project's complete reserved
identifier set because generated Java emits those components as class literals.
Generated Auto Storage identifiers also reject reserved words.

Commit the reviewed result as `compat/contracts/<mod-id>.json`.

When a current scan supersedes an older audit of the exact same target and jar,
migrate decisions by exact recipe-class identity instead of manually copying
the contract:

```bash
tools/compat-kit/compat-kit migrate-contract \
  compat/contracts/target.json \
  --old-audit compat/audits/target/1.2.3-old.json \
  --new-audit compat/audits/target/1.2.3.json \
  --output build/compat-kit/target-contract-migrated.json \
  --next-actions build/compat-kit/target-contract-migration.md
```

The command preserves a decision only when the recipe class, its public
signature, its class-owned risk evidence, ancestry artifact SHA/size inventory,
exact ancestry dependency-coordinate mapping, and the recipe-data inventory
digest are all unchanged. New or changed classes
reopen as `needs_decision`. Removing
an accepted family fails; removed rejected legacy false positives are reported.
Target or artifact-SHA drift is rejected. Because this is the explicit companion
to `migrate-audit`, `--old-audit` may retain a legacy classifier's buckets;
migration still validates its versioned schema, persisted structural/target
digests, contract bindings, and exact artifact identity, while `--new-audit`
must pass the current classifier without exceptions.
Format-7 contracts do not contain `source_recipe_data_sha256`, because their
audits predate recipe-data evidence. This omission is accepted only by
`migrate-contract` when `--old-audit` is actually format 7. All common families
then reopen as `needs_decision` because neither recipe-data nor ancestry
evidence can be proven unchanged. Public/current contract validation remains
strict, formats 8 and later may not omit the digest, and a format-7 contract
that supplies an unverifiable digest is rejected rather than trusted. A legacy
format-7 contract may omit `matrix`; if it already contains a reviewed matrix,
migration validates and preserves it instead of replacing or rejecting it.

For a one-issue/one-worktree worker, generate the compact handoff package:

```bash
tools/compat-kit/compat-kit worker-package \
  compat/contracts/target.json \
  --audit compat/audits/target/1.2.3.json \
  --output build/compat-kit/target-worker
```

The seven deterministic files contain hashes, compact class/recipe summaries,
unresolved decisions, commands, issue/worker context, and a PR checklist. They
never embed public-signature bodies or complete upstream source. Existing drift
and symlinked output paths fail before any file is written. `commands.sh` uses
the exact safe repository-relative audit path supplied by `--audit`; it never
refers to a package-local `audit.json` that was not emitted. For a complete
contract it runs every declared `verification.gradle_tasks` entry as its own
Gradle invocation, followed by datagen and diff checks; the package cannot
replace an authoritative target GameTest with only the compatibility matrix.
Directive text uses only the validated target mod ID. Free-form target display
name/version values are retained as escaped JSON explicitly labelled untrusted
data, so target metadata cannot inject worker instructions.
The script preserves the worker's caller-provided JDK environment instead of
hardcoding a macOS/Homebrew `JAVA_HOME`.

### 3a. Generate reviewed mechanical code

After the contract is complete, write a separate generation plan bound to the
exact canonical contract digest and run:

```bash
tools/compat-kit/compat-kit generate \
  compat/contracts/target.json \
  --audit compat/audits/target/1.2.3.json \
  --jar build/compat-kit/artifacts/target.jar \
  --classpath build/compat-kit/classpath/dependency.jar \
  --plan compat/generation/target.json \
  --output build/compat-kit/target-generated
```

The generator emits direct typed registry calls for reviewed safe shapes:
single-item deterministic recipes or a reviewed provider returning an exact
`TypedRecipePlan` and `RecipeFamilyCost`. It can mechanically emit fixed,
config-tick-ratio, public numeric getter, tier multiplier, parallel-lane, and
speed-times-parallel station variants. Config wrapper fields are read with
`.get()`; enum tier fields are read directly, for example
`FactoryTier.BASIC.processes`. Every dynamic value must be positive,
multiplication is checked, and every reviewed dynamic accessor is declared
`integral` and converted through `BigDecimal.longValueExact()` rather than a
lossy cast. Process rates are positive and Instant rates are fixed zero.
Generated code uses `MachineVariant.of`/`derived`, public Auto Storage APIs,
and exact target types; it never uses reflection. Families sharing one station
descriptor must provide byte-identical station and rate definitions, and the
generated register validates the descriptor's reviewed namespace rather than
assuming `auto_storage`.
Plan class and bridge names may not shadow any simple type imported by their
specific renderer; invalid plans fail before materialization instead of
emitting uncompilable Java.

The plan is a reviewed binding, not another inference layer. Unsupported recipe
shapes stay as explicit `red_boundary` entries. A typed provider remains the
small handwritten semantic boundary for N-input/N-output selection,
catalysts/tools/remainders, and mixed resource costs; code generation only owns
registration boilerplate. Every generated family therefore declares an exact
`registration_id`; the audit family ID identifies review evidence and must not
be guessed to be the runtime registry path. The registration namespace must
match the reviewed station descriptor namespace. Family-derived Java variables
and conformance methods use a collision-free `family$` prefix for digit-leading
or reserved IDs plus the shared Java-identifier validator, so those IDs cannot
emit invalid source or collide with another family. Commit plans and generated source, then
keep a byte-for-byte regeneration test so drift is visible.

The built-in `single_item_to_item` template accepts only its canonical contract
shape: one consumed Item selected by `recipe.input` with amount `1`, one primary
Item selected by `recipe.output` with amount `recipe.output.count`, and either
no cost or the single `auto_storage:station_work` cost selected by
`recipe.processing_time`. Its published schema fixes the input binding to an
`ingredient_method` with no arguments, the output binding to an
`item_stack_method` with either no arguments or registries, and the optional
cost binding to a no-argument numeric method. Input/output/cost method members
use the shared Java-keyword-aware validator, so a member such as `class` fails
before source generation. Generation, conformance, resource, and runtime-probe
plan schemas apply the same reserved-identifier rejection to every Java type,
package, class, accessor, provider, and bridge name as their runtime validators.
Any changed amount, selector, extra input/output, or
different cost remains a handwritten/RED boundary rather than being silently
compiled with different semantics.

Contract recipe type IDs, station descriptor IDs, and station variant item IDs
must use normal lowercase resource-location grammar. A descriptor cannot list
the same station variant item twice. Generation plans must bind each station
item exactly once; duplicate rate-item bindings fail before any Java is emitted
instead of being collapsed by set comparison. A `registry_block_method`
accessor also declares its exact `block_id`; the station's representative item
ID is not assumed to be the owning block registry ID.

### 3b. Generate conformance and resource scaffolds

```bash
tools/compat-kit/compat-kit conformance \
  compat/contracts/target.json \
  --audit compat/audits/target/1.2.3.json \
  --jar build/compat-kit/artifacts/target.jar \
  --classpath build/compat-kit/classpath/dependency.jar \
  --plan compat/conformance/target.json \
  --output build/compat-kit/target-conformance

tools/compat-kit/compat-kit resource-scaffold \
  compat/contracts/target.json \
  --audit compat/audits/target/1.2.3.json \
  --jar build/compat-kit/artifacts/target.jar \
  --classpath build/compat-kit/classpath/dependency.jar \
  --plan compat/resources/target.json \
  --output build/compat-kit/target-resource
```

`conformance` emits a reusable assertion harness plus target scenarios for
success/batching, shortage, capacity, overflow, stale holders,
catalyst/tool/remainder preservation, multi-output merging, mixed-resource
rollback, dedicated-server isolation, and all-mod coexistence. The generated
harness performs snapshot/delta/unchanged and success/failure assertions; the
integration supplies only the reviewed scenario setup and operation. Happy,
catalyst/tool/remainder, and multi-output paths carry separate expected deltas,
and zero-valued resource keys are normalized before comparison. Dedicated-
server isolation checks the physical NeoForge distribution instead of trusting
an addon-supplied boolean. Every conformance plan declares the exact
`game_test_namespace`; the generated class uses it in `@GameTestHolder`. Every
conformance plan must cover at least one accepted contract family; an
all-rejected contract cannot emit a successful scaffold with no `@GameTest`
methods. Every
family batch must be at least 2 and at most signed Java `long` maximum, so
`happy_path_and_batching` always proves a repeated operation rather than
duplicating a single-craft assertion. Every
happy-path expected delta multiplied by that batch must fit a signed Java
`long`; every nonzero expected delta itself is schema-bounded to
`[-Long.MAX_VALUE, Long.MAX_VALUE]`, and an overflowing plan fails before source
generation.

`resource-scaffold` emits API-only kind/container/block/renderer boundaries and
real persistence, transfer, rollback, and dedicated-server test scenarios for
an optional custom resource kind. It rejects Item, Fluid, NeoForge Energy, and
Work,
which must reuse Auto Storage's built-in support. Generated common source may
not import Core internals or client classes; the renderer bridge remains
generic and client registration stays isolated. Each resource plan binds a
positive signed-`long` sample amount, unique snapshot key, and exact
`game_test_namespace`; the published schema applies the same amount maximum and the
generated test class uses that namespace in `@GameTestHolder`. The tests own the before/after
snapshot, first assert that reset/seed produced that exact key and amount, then
require `clear()` to remove that key before `load()` restores the exact saved
snapshot. They also own the delta, rollback, and physical-side assertions;
an addon provider exposes operations and bytes, not self-attested persistence
or client-isolation booleans. Resource IDs may begin with digits or have a path
made only of valid punctuation. Punctuation-only paths use a deterministic
`Encoded` plus lower-case code-point-hex suffix; every
constant, registration method, and generated GameTest identifier is derived by
one collision-checked Java-identifier normalizer that prefixes `_` when needed.
Registration, conformance, test, and bridge class names are also checked against
their renderer-specific imported type names before any file is written.

Repository declarations are build inputs, not discovery hints. A target
published through Modrinth Maven, Curse Maven, or its own Maven must list the
required repository in `target.repositories`; an empty list means Maven
Central is sufficient. Generated addons put these reviewed repositories first
in declared order and restrict them to target and explicit runtime dependency
groups. Explicit runtime groups cannot fall back to Maven Central even when
`target.repositories` is empty; the target may fall back only under its exact
SHA gate. Fixed Maven Central, BlameJared,
and release-Ivy filters reserve Patchouli and Auto Storage for their owning
repositories. The generator never guesses, sorts, or otherwise changes target
repository precedence from the dependency coordinate. Required
target-side runtime artifacts belong in `target.runtime_dependencies`; this
additional list must not repeat the primary `target.dependency`. Bundled
descriptors and external addon builds copy the exact reviewed list instead of
depending on transitive metadata or hand edits. Target and explicit runtime
dependencies are non-transitive; every required companion must therefore appear
in the contract. Every bundled descriptor has a non-empty
`runtimeDependencies` list containing its primary compile dependency, so
descriptor-derived isolated and aggregate GameTests cannot schedule a target
mod that is absent at runtime. Repository URLs, dependency coordinates, and derived group
filters are serialized as escaped Groovy literals, so `$`, quotes, and
backslashes cannot alter the reviewed build input. Target and runtime
dependencies must use exact `group:name:version` Maven coordinates; malformed
or control-character-bearing values are rejected before scaffolding.

A bundled fixture may declare `target.runtime_artifact_transforms` only when an
exact reviewed runtime artifact embeds unrelated test-only ZIP entries that the
loader classloads before fixture namespace filtering. The field is an object
keyed by existing runtime dependency; each value binds its pristine SHA-256 and
a non-empty unique list of literal `remove_entries`. Dependency-keyed object
shape plus an exact one-property bound prevents duplicate coordinate or artifact
plans from passing the published schema. Multiple bundled descriptors may still
share one identical transformed artifact.
Absolute/traversal/directory paths, wildcards, control
characters, absent entries, duplicate ZIP entries, unexpected artifacts, and SHA
drift fail closed. Audit, compile, and artifact verification continue to use the
pristine jar; only the descriptor-owned isolated fixture and aggregate matrix use
the deterministic transformed jar. Identical dependency + SHA + entry sets across
multiple descriptors share one cacheable output, while a conflicting SHA or entry
set for the same dependency is rejected before runtime resolution. A transformed
dependency cannot also appear untransformed in another descriptor's matrix
runtime. This field is intentionally rejected for independent-addon scaffolds,
which do not consume the repository's bundled descriptor runtime.

### 4. Scaffold a RED integration

Bundled module:

```bash
tools/compat-kit/compat-kit scaffold \
  --bundled compat/contracts/target.json \
  --audit compat/audits/target/1.2.3.json \
  --jar build/compat-kit/artifacts/target.jar \
  --classpath build/compat-kit/classpath/dependency.jar
```

Independent addon:

```bash
tools/compat-kit/compat-kit scaffold \
  --addon compat/contracts/target.json \
  --audit compat/audits/target/1.2.3.json \
  --jar build/compat-kit/artifacts/target.jar \
  --classpath build/compat-kit/classpath/dependency.jar \
  --output ../target-auto-storage
```

Bundled output owns its `src/compat/<mod-id>/compat-module.json`, isolated source
set, one-call `AutoStorageCompatModule`, present-mod fixture, and GameTest
structure. The fixture must be a Java-safe camel-case identifier ending in
`Fixture`; path-like values fail before any file is created. Gradle discovers
the source set, reviewed HTTPS repositories, target dependencies, fixture mod,
run task, expected test gate, and audited target artifact from that descriptor;
`expectedTests` must be a positive JSON integer representable by Gradle's
`Integer` parser; fractions and wider overflow values are rejected rather than
truncated. The reviewed contract owns the required `matrix` assertion block: required
mods, accepted/rejected evidence, and the exact per-module recipe-inventory
digest. A newly decided contract starts with an explicit pending digest that
blocks complete bundled or addon scaffold/verification until review replaces it;
the same reviewed contract must remain safe to consume in either mode. Bundled
scaffold and verify copy the contract block byte-for-byte into the descriptor,
so a complete bundled module joins CI and the generated compatibility-matrix
manifest without editing shared workflow or matrix Java sources. External addon
output does not publish a repository-owned compatibility descriptor.
Before writing a bundled module, Compat Kit compares its derived module ID and
entrypoint with every existing descriptor. The generated compatibility source
set and fixture share Gradle's source-set namespace, so both are compared with
each other, every existing descriptor source set/fixture, and the repository's
fixed source sets. Different mod IDs that normalize to the same Java/Gradle
identifier, or a target such as `kit_generated_fixture` that would reuse
`compatKitGeneratedFixture`, fail before materialization. The target is on both compile and fixture runtime
classpaths, with both
declarations non-transitive. `build` and the
module GameTest resolve exactly one target jar and reject a SHA different from
the reviewed audit. For an audited descriptor, that same coordinate must be the
primary compile dependency and must also appear unchanged in the explicit
runtime dependencies. No central compatibility switch is added.
If the contract owns a runtime transform, the generated descriptor also owns the
exact `runtimeArtifactTransforms` block. Gradle resolves the pristine artifact
non-transitively, runs `transform-runtime-artifact` with the reviewed SHA and
entries, and wires the single shared output only into the affected fixture and
matrix runtime classpaths. The task has declared inputs/outputs, deterministic
stored-entry ZIP bytes, and Gradle output caching; repeated unchanged runs are
up-to-date rather than rewriting the jar. Global runtime-artifact SHA ownership
is seeded from every descriptor `auditArtifact` before transforms are merged, so
another coordinate cannot claim the same pristine bytes. After dependency
declarations, every fixture runtime configuration is also checked for a direct
external-module declaration of a transformed coordinate. Before each affected
isolated or matrix run, Gradle then hashes the complete resolved source-set
runtime classpath and requires exactly one transformed output with no pristine
SHA. This includes implementation, inherited, transitive, and file dependencies,
so fixed `*FixtureRuntimeOnly` entries are not the only guarded path.

External output is an ordinary NeoForge project with the Gradle wrapper,
compile-only Auto Storage API dependency, reviewed target repositories and
target dependency, one-call
`AutoStorageAddon.register(...)`, dependency metadata, a RED present-target
GameTest, required Patchouli runtime, and reusable GitHub Actions workflow. It
cannot compile against Auto Storage implementation classes. The separate
source audit is copied to `compat/audit.json`; its `build` and
`runGameTestServer` gates also resolve exactly one target jar and verify
`source_audit_sha256`. Every target-derived metadata value, including addon display names and bundled
fixture descriptions, is serialized as a TOML basic string, so quotes,
backslashes, control characters, newlines, and multiline-literal delimiters
cannot break generated metadata. DEL (`U+007F`) is escaped explicitly because
TOML forbids it even though JSON serializers commonly leave it literal.

The `scaffold` adapter and fixture are deliberately RED. Rerunning `scaffold`
is byte-deterministic and refuses to overwrite drift. The manifest binds the
scaffold to the reviewed contract; implementation edits are expected after the
initial RED generation. Verification regenerates the security-sensitive
bundled descriptor or external Gradle execution chain (`build.gradle`,
`settings.gradle`, `gradle.properties`, both launchers, and both wrapper
artifacts) from the reviewed contract and generator, then checks every byte and
manifest entry. Editing a launcher or gate and self-attesting a new manifest
hash therefore cannot bypass artifact or task gates. Before writing anything,
scaffolding preflights every destination for type/content drift and every
required parent as a real directory. Symlinked roots, ancestors, and generated
targets are rejected before the first write; a late-sorting conflict, file
occupying `src`, or `src` symlink cannot leave a partial project or write
outside the requested output root. The lexical output path is normalized
before checking existing ancestors, so `missing/../link/output` cannot hide a
symlink. Existing ancestors above a not-yet-created output root are checked too.
Rerunning an unchanged scaffold restores `0755` on `gradlew` and
`tools/compat-kit/compat-kit`.

Generated independent addons require the current compatible Auto Storage minor
line. A 0.3.1 kit emits `[0.3.1,0.4)`: patches remain compatible, while the
next pre-1.0 minor requires an explicit addon review.

### 5. Implement with strict TDD

Run the generated failing fixture before editing the adapter. Implement only the
reviewed contract through the public SDK. Keep target imports under the isolated
compatibility source set. Do not add target classes to main/common, the API jar,
or dedicated-server paths when the mod is absent.

Behavior evidence covers target-absent classloading, target-present registration,
exact success, shortage, capacity, long overflow, stale holder,
catalyst/tool/remainder behavior, mixed-resource rollback, dedicated-server
client isolation, API-only compilation, and all-mod coexistence.

### 6. Verify

```bash
tools/compat-kit/compat-kit verify \
  compat/contracts/target.json \
  --audit compat/audits/target/1.2.3.json \
  --jar build/compat-kit/artifacts/target.jar \
  --classpath build/compat-kit/classpath/dependency.jar \
  --bundled . \
  --output build/compat-kit/target-report.json
```

For an external addon, use `--addon <directory>`. Addon contracts must declare
exactly `build` and `runGameTestServer`, use `main` as their fixture, and map
evidence only to those actual tasks; the verifier never rewrites bundled task
names into addon task names. It runs both gates and gives
`runGameTestServer` a fresh world. Verification rejects an unresolved
contract, contract/source-audit mismatch, contract/manifest mismatch,
remaining RED marker, forbidden
implementation links, missing evidence source/marker, a source annotation
count different from `expected_game_tests`, GameTest output that does not
report one and only one exact passing summary, an undeclared evidence task, or
any non-zero command. Multiple success summaries are rejected even when one
matches, because editable fixture output cannot outrank the framework result.
`expected_game_tests` is restricted to `1..2147483647`, matching the positive
JSON `Integer` required by the generated Gradle descriptor.
Generated addon builds expose `stageCompatKitTargetArtifact` and
`stageCompatKitAncestryArtifacts`, which stage the exact target and every
audited ancestry jar. Generated and published example workflows pass the target
through `verify --jar` and every staged ancestry jar through repeatable
`--classpath`; copying only `compat/audit.json` is never sufficient.
The generated staging task searches the target's transitive dependencies,
NeoForge additional runtime classpath, and ModDev
`createMinecraftArtifacts` outputs, normalizes their archive layout, and copies
only exact SHA/size matches. The scaffold pins Auto Storage's Parchment baseline
and explicitly sets ModDev `disableRecompilation = true` so local and CI use
the same binary NeoForge/Minecraft development artifact. Before scanning a raw
ModDev platform jar, run
`tools/compat-kit/compat-kit normalize-jar <raw.jar> <canonical.jar>` and pass
the canonical jar through `--classpath`; raw ZIP metadata is not cross-run
stable. The command fixes entry order, timestamps, storage mode, and permissions.
Only ModDev outputs are canonicalized automatically; Maven artifacts remain raw
exact bytes.
Scanner-format-16 and later audits persist exact coordinates for reachable compile APIs
under `ancestry_dependencies`; generated addons emit each as non-transitive
`compileOnly` and `compatKitAncestryArtifacts` dependencies. No post-scaffold
edit is needed or allowed, and unresolved hashes remain a hard failure. Complete consumers independently rebuild the reachable
external class graph from the supplied jars, so classpath-owned metadata cannot
be edited out of an otherwise self-consistent audit. Every unmatched parent
must resolve to the selected JDK 21 modules or a known root; otherwise complete
validation rejects the missing `--classpath` evidence.
A marker assigned to a `run*GameTestServer` task must occur inside an
annotated `@GameTest` method body; a detached constant, helper, or comment does
not prove that the behavior ran. Comments inside a real GameTest are removed
before matching evidence, while markers in executable string arguments remain
valid. Eligible Java Unicode escapes are rejected in evidence sources before
raw marker matching because Java translates them before comment/token parsing;
escaped literal `\\u` text remains allowed. The marker method's own enclosing
class must declare an `@GameTestHolder` that resolves to the exact namespace
enabled by that Gradle run; a decoy holder on another class in the same source
cannot authorize it. Simple and fully qualified `@GameTest` and
`@GameTestHolder` annotations are parsed consistently for evidence and exact
test counts. Literal holders and bounded
`static final String` references are accepted, while missing, unresolved,
ambiguous, or mismatched holders fail closed. Constants are keyed by their
actual innermost declaring class and resolved through the annotation's lexical
enclosing-class scope, explicit static imports, and then package/global
fallback, never the source file
stem. A check is marked passed only
when all of its declared source markers
exist in the correct execution boundary and the associated Gradle task
succeeds. The report records those
per-check evidence links, exact commands, exit codes, output hashes, tool
version, target, and manifest hash. Bundled verification runs every declared
Gradle task as a separate process and removes only `run/world` before each task
so stale GameTest state cannot leak between fixtures. Cleanup rejects a
symlinked lexical verification root, any of its ancestors, `run`, or `world`,
and rejects any resolved path outside the verification root before deletion.
RED and forbidden-link scans are scoped to `src/`; ignored
`build/` outputs and previously extracted scaffolds cannot poison a later
verification. The expected hashes for the bundled descriptor or complete
external Gradle execution chain are recomputed from the reviewed contract and
current generator rather than trusted from the manifest. The target-SHA gate
or launcher therefore cannot be replaced after scaffolding while retaining a
passing report, even if the manifest is edited too. The published report schema
requires a strict target identity, all twelve exact check IDs, and at least one
successful command;
external-addon reports additionally require exactly the authoritative `build`
and `runGameTestServer` command records. GameTest counting and body extraction
share one comment/string-aware Java mask, so `@GameTest` text inside a comment
or literal cannot lend a helper method's markers to a passing runtime count.
The extractor tracks annotation and method parentheses until the actual method
body, so a brace-bearing intermediate annotation cannot become the evidence
body. Escaped triple quotes inside Java text blocks remain literal content and
cannot extend evidence into a detached helper. Each GameTest evidence file must
also be under the fixture source set executed by its declared
`run*GameTestServer` task; a marker in base or another fixture source set cannot
prove behavior for the target run.

The repository compatibility-matrix task also depends directly on every
descriptor-generated audited-artifact verifier and the separately pinned
Botania verifier. Running either the normal 10k matrix or 30k stress command
directly therefore verifies the exact audited target bytes first.

### 7. Review an upstream update

```bash
tools/compat-kit/compat-kit diff \
  compat/audits/target/1.2.3.json \
  build/compat-kit/artifacts/target-1.2.4.jar \
  --source build/compat-kit/sources/target-1.2.4 \
  --output build/compat-kit/target-1.2.3-to-1.2.4.json
```

Read the compact delta first. It reports only added, removed, or changed
recipe/resource/station signatures and risk evidence. Any target jar SHA change
sets `contract_affected=true`, including private implementation-only changes
whose public signatures happen to be stable. The compact surface delta narrows
the review, but it never waives contract review for different artifact bytes.
When the new target is a jar, `diff` validates the old audit first, then selects
that audit's target mod ID for the rescan.

## Distribution

```bash
./gradlew compatKitArchive
```

This produces
`build/distributions/auto-storage-compat-kit-<mod_version>.zip` with the CLI,
schemas, Gradle wrapper, RED templates, complete public-SDK addon example, CI
example, README, and license.
Only the explicit repository-owned addon example template is packaged; local
`build/` or untracked files under `examples/addon` are never traversed. The
ten published JSON schemas are also an explicit allowlist, not a directory
glob: audit, contract, conformance plan, delta, generation plan, proposals,
report, resource plan, runtime-probe plan, and runtime-probe output. Gradle
passes `mod_version` to `publish`, and publication fails unless it
exactly matches the tool version embedded in generated projects.
`assemble`/`build` includes the archive, CI uploads it, and tagged releases add
it beside API, sources, and Javadocs artifacts.

Archive member order, timestamps, permissions, and bytes are reproducible.
The extracted archive is also self-contained: `scaffold --addon` resolves its
Gradle wrapper template beside the extracted CLI instead of requiring an Auto
Storage source checkout.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.test_compat_kit
./gradlew compileCompatKitGeneratedFixtureJava
```

`src/compatKitGeneratedFixture/` is a committed compile-only contract for the
generated conformance and custom-resource outputs. Python regenerates those
sources byte-for-byte from the committed example plans, while Gradle compiles
them against only the public API jar plus NeoForge/Minecraft. The providers
deliberately throw if executed: runtime semantics are supplied and asserted by
each real integration's GameTests, while this fixture permanently guards the
generator's Java/API surface.

## First dogfood: AE2 Inscriber

The committed AE2 19.2.17 scanner-format-17 audit, migrated contract, generation
plan, and generated registration prove this workflow against a real target.
Structural classification retains 13 reachable ancestry jars and seven exact
non-transitive compile coordinates, then finds twelve
actual `Recipe` implementations while inventorying 556 effective recipe JSONs
across 18 serializer groups. The accepted slice remains only
`InscriberRecipe`:

- middle is consumed;
- optional top/bottom are retained for `INSCRIBE` and consumed for `PRESS`;
- output is one exact stack;
- cost is 200 station-work steps and 2,000 configured AE converted through
  AE2's public `PowerUnit` API to a finite positive exact integer FE amount;
- the plain Inscriber contributes 2 work per tick; speed-card state is not
  inferred from its item;
- Charger, Entropy, six custom crafting families, Quartz Cutting, Matter Cannon
  Ammo, and Transform remain rejected.

`Ae2GeneratedCompat.java` owns deterministic descriptor/family registration;
the handwritten `Ae2Compat` methods own exact eligibility, typed transaction
planning, and AE-to-FE cost semantics. A golden test regenerates the source and
requires byte identity before the isolated AE2 source set is compiled.

The bounded rate templates are additionally compiled against source-shaped
fixtures for Iron Furnaces' public config wrapper
`Config.ironFurnaceSpeed.get()` and Mekanism's public factory parallel field
`FactoryTier.BASIC.processes`. Their real isolated GameTest modules remain the
runtime evidence for configured speed and factory parallel throughput; these
representative versions are CI fixtures, not player dependency pins.

Its required-check evidence uses markers inside annotated method braces, not
method names or declarations. The ingredient-shortage check omits the PRESS
middle ingredient and asserts that both remaining items, FE, station work, and
output are unchanged.

See [AE2 compatibility](ae2-compatibility.md).
