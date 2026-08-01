# Auto Storage Compat Kit

Compat Kit is a development-time CLI for auditing, contracting, scaffolding,
and verifying deterministic Auto Storage integrations. It does not run inside
Minecraft and never infers consumption, catalyst, output, remainder, cost, or
determinism semantics.

Run `compat-kit --help` for commands. The full maintainer and addon-author
workflow is documented in `docs/compat-kit.md` in the Auto Storage repository.

## Quick start

Use Python 3.11 or newer, JDK 21, and one official representative target jar:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
python3 --version

./compat-kit scan \
  --jar target.jar \
  --source target-source \
  --classpath compile-dependency.jar \
  --data-root optional-datapack \
  --output audit.json
./compat-kit propose audit.json --output proposals.json
./compat-kit probe audit.json --plan probe-plan.json \
  --game-test-namespace target_auto_storage --output runtime-probe
./compat-kit validate-probe runtime-probe.json --audit audit.json \
  --plan probe-plan.json
./compat-kit decide audit.json \
  --output contract-draft.json \
  --next-actions next-actions.md
```

When `--source` points at a Git checkout, tracked and untracked state must be
clean so its HEAD identifies the exact inspected source. A module subdirectory
uses the enclosing Git worktree's status and HEAD; evidence paths remain
relative to the supplied module. Supplying a non-Git source directory fails;
omit `--source` when no versioned source is available. Current-format cache
entries and committed audits carry the scanner format and are fully
schema-validated before reuse. Complete consumers reject legacy scanner
formats; only explicit migration commands may read formats 7 through 11.
Committed source files must be sorted, unique, canonical POSIX
repository-relative `.java` paths. A null revision requires no files, while a
recorded revision with classified candidates requires at least one file. The
validator does not guess a source filename from a binary class name because a
package-private class may legally live in another compilation unit. If the
target has classified candidates, a
supplied source module must contain at least one matching tracked, non-ignored
Java source file; ignored outputs and unrelated clean checkouts are rejected.
Candidate paths match only at a package-path segment boundary, and every
risk-evidence owner must be an audited recipe candidate.
NeoForge metadata entries are limited to 1 MiB and class entries to 16 MiB
before decompression. The target is rehashed after inspection so a path
replacement cannot mix one artifact hash with another artifact's evidence.
Scanner format 12 structurally classifies concrete `Recipe` and
`RecipeSerializer` implementations. Repeatable `--classpath` jars supply the
complete non-JDK ancestry of target classes; every unresolved external base
fails before client/viewer/builder/datagen name classification instead of
letting a structurally hidden recipe disappear. Exact classpath artifact
identities enter the audit/cache, and every ancestry jar is rehashed after
inspection to reject in-flight replacement. A binary class may be owned by
only one ancestry jar; duplicate definitions fail even when their hierarchy
metadata matches. Platform ancestry requires exact
membership in the selected JDK 21 `jmods` inventory. The resolved `javap`
toolchain's `release` metadata must report major version 21 before that
inventory is read or a cached audit is returned. Cache metadata binds the
selected JDK installation, version, and module-file identity; a changed JDK 21
inventory forces a fresh scan. A `java.*` or `javax.*` prefix alone never
authorizes an unresolved class.
JVM modified UTF-8 class constants are handled correctly. Repeatable
`--data-root` inputs use normal
data-pack precedence and add bounded recipe counts, sample IDs, fields,
top-level array sizes, NeoForge conditions, and override provenance to the
audit. Their ordered content digests include every bounded tag JSON and bounded
`pack.mcmeta` bytes; recipe counts remain recipe-only and the evidence-file
bound applies globally across ordered roots. Roots declaring top-level
data-pack `filter` or `overlays` metadata are rejected because the scanner does
not model filter removal or overlay-directory activation semantics. Each
recipe, tag, and metadata file is bounded-read once per inventory pass, and
the same bytes are used for validation, parsing, and hashing. Ordered roots
are inventoried again after source evidence is built and immediately before a
scan can cache or return, so a persistent in-flight change fails instead of
producing mixed evidence. Legacy
formats 7, 8, 9, 10, and 11 remain readable for explicit
migration, but current-only commands reject them. Format 12 stores each
candidate's structural classification and a separate sorted top-level
`structural_hierarchy` inventory. Validation requires both records to agree,
so removing only one indirect hierarchy path cannot demote a structurally
classified recipe to a lower-priority name bucket. Direct `extends`/`implements` parents
in the public declaration are also cross-checked without treating generic type
bounds as direct ancestry.
Risk scanning separately traverses every reachable non-JDK superclass and
interface implementation, rather than only the first path from a concrete
recipe to `Recipe`, so side-superclass and default-interface behavior remains
review evidence.
Use `migrate-audit legacy.json --jar target.jar --output audit.json` to
explicitly rescan the exact format-7, format-8, format-9, format-10, or format-11 artifact;
identity or SHA drift fails.
Use `migrate-contract contract.json --old-audit old.json --new-audit new.json
--output migrated.json --next-actions migration.md` to preserve reviewed
decisions only when class identity, public signature, class-owned risk evidence,
ancestry artifact SHA/size inventory, and recipe-data inventory are unchanged
after that rescan. New or changed
classes remain unresolved; removing an accepted family fails, while removed
rejected false positives are reported.
Format-7 contracts predate recipe-data evidence. Only `migrate-contract`, when
paired with an actual format-7 old audit, accepts that legacy contract's absent
`source_recipe_data_sha256`; every common family reopens because the missing
recipe-data and ancestry evidence cannot preserve a decision. The normal
contract validator remains strict, later audit formats cannot omit the field,
and a format-7 contract claiming an unverifiable recipe-data digest is rejected.

Review every candidate and record exact ingredients, catalysts, remainders,
outputs, typed units, station rates, costs, bounds, target HTTPS Maven
repositories, additional required runtime artifacts, and evidence. Each
required verification check must name the successful Gradle task plus a source
glob and marker; the declared
`game_test_task` must report exactly `expected_game_tests` passing tests.
Both that count and bundled descriptor `expectedTests` are limited to the
positive Gradle/Groovy `Integer` range `1..2147483647`; fractions and wider
values fail validation. Markers assigned to `run*GameTestServer` tasks must
occur inside annotated `@GameTest` method bodies, and that source's
`@GameTestHolder` namespace must equal the namespace enabled by the declared
Gradle run. After
the contract has no `needs_decision` entry, pass the same committed audit to
every later command. Validation compares that audit's exact candidate set with
the contract, including every per-family scanner risk, so recomputing a
contract-only inventory digest cannot hide an omitted recipe family or risk.
The contract separately binds the effective recipe-data/data-pack digest, so a
changed override invalidates a previously complete contract even when the
target jar and recipe classes are unchanged.
Each candidate must remain in the bucket computed by the scanner; moving a
recipe record into a station/resource list is rejected.
Process station rates must be positive and Instant station rates must be zero.
Accepted families keep the `costs` field, but may use an empty list when the
reviewed runtime family is genuinely free.

The scan publishes only public signatures and compact risk evidence. Bounded
private bytecode is inspected for hidden randomness, world/entity access,
multiblocks, live machine state, generic ingredient surfaces, unbounded output,
and capability mutations requiring simulation. Structural recipes also inspect
target/classpath implementation classes along their inheritance path and
attribute inherited findings to the concrete audited recipe. Each class is reduced
immediately and the bytecode is never stored in the audit or retained until the
next class. Platform-neutral concurrent pipe readers retain at most the
configured limit plus one byte and terminate `javap` on overflow.
`RandomSource`, `ThreadLocalRandom`, `SplittableRandom`, `SecureRandom`, and
`RandomGenerator` are recognized.
Named nested classes are included and mapped to their top-level source even
when the Java identifier contains `$`; `InnerClasses`/`EnclosingMethod`
metadata excludes anonymous/local classes. Class files carrying
`ACC_SYNTHETIC` and `META-INF/versions/` aliases are also excluded; the root
binary name is scanned once. Scan and audit validation apply the same current
name-bucket priority.
Family IDs normally use the normalized simple class name. If a legal identifier
contains no alphanumeric characters after normalization, the fallback is
`class_<binary-name-hex>`; normalized collisions also retain a deterministic
binary-name encoding.
Chance, randomness, generic-ingredient, and capability-mutation method calls
accept the descriptor syntax emitted by `javap -c -p`.

`propose` emits evidence-backed station/rate/parallel and recipe-requirement
candidates from public numeric methods and fields. Slot count is never treated
as throughput. All proposal records remain `needs_decision`; transaction,
descriptor, and unsupported live/world classifications are a review aid, not
accepted recipe semantics.

`probe` emits a deterministic, server-only GameTest plus `probe-spec.json`.
`--game-test-namespace` is mandatory and is written to both the spec and the
generated class's `@GameTestHolder`; it must exactly match the namespace enabled
by the Gradle run that executes the probe.
The fixture records the bounded sorted loaded RecipeManager inventory and
target registry identities under an explicit output system property. An
optional exact-audit-digest plan adds reviewed direct config/capability calls;
empty or unresolved candidates remain evidence gaps. It has no
client/reflection path and does not turn unresolved config/capability candidates
into accepted evidence. Runtime output records the exact canonical plan digest;
validation requires the same digest and exact planned ID/source/surface sets,
finite numeric config values, and boolean capability values. Run
`validate-probe` for this cross-file gate; JSON schema validation alone is not
sufficient. `worker-package`
emits seven compact deterministic
issue/worktree/PR handoff files without upstream source or signature bodies;
its gate script points at the exact repository-relative audit path and runs
every Gradle task declared by a complete reviewed contract without hardcoding a
platform-specific JDK path.

For a complete reviewed contract:

```bash
./compat-kit generate contract.json --audit audit.json \
  --plan generation-plan.json --output generated
./compat-kit conformance contract.json --audit audit.json \
  --plan conformance-plan.json --output conformance
./compat-kit resource-scaffold contract.json --audit audit.json \
  --plan resource-plan.json --output resource
```

`generate` owns only direct typed mechanical registration for the bounded safe
shapes and rate templates. Config wrapper fields, public methods/block methods,
and enum constant numeric fields are explicit reviewed accessors; unsupported
shapes remain RED. Dynamic rate accessors are reviewed as integral and converted
exactly, shared descriptors require identical definitions, and every generated
family declares a reviewed runtime `registration_id` distinct from its audit
family key. The `single_item_to_item` template requires the canonical
`recipe.input`/`recipe.output` selectors and exact amount expressions;
`registry_block_method` requires an explicit block ID separate from its station
item. `conformance` requires every family batch to be at least 2 and emits the shared real transaction assertion harness with separate happy,
catalyst/tool/remainder, and multi-output deltas while the integration supplies
scenarios. `resource-scaffold` emits API-only
custom-kind/container/block/renderer and operation-based snapshot tests and
rejects the built-in Item, Fluid, and NeoForge Energy kinds. Both plans require
an exact `game_test_namespace`, and generated test classes emit the matching
`@GameTestHolder`. Generated tests assert the planned sample key/amount was
seeded, require `clear()` to remove that key, and only then prove `load()`
restored the exact saved snapshot. Valid resource IDs with digit-leading segments use one
collision-checked Java-name normalizer that prefixes `_` for generated
constants and methods. The committed
`compatKitGeneratedFixture` source set is regenerated byte-for-byte and compiled
against the public API so generated scaffold Java cannot drift silently.
Recipe types, descriptor IDs, station item IDs, and explicit block IDs must be
lowercase resource locations; duplicate descriptor variant items and duplicate
generation-plan rate bindings are rejected.

```bash
./compat-kit scaffold --addon contract.json --audit audit.json \
  --output target-auto-storage
./compat-kit verify contract.json --audit audit.json --addon target-auto-storage \
  --output report.json
```

Scaffolding preflights every destination before writing the first file. Any
existing path with different content, symlinked root/ancestor/target, or
file-valued parent directory fails without leaving a partial project or writing
outside the requested output root. Lexical `..` segments are normalized before
preflight, and existing ancestors above a not-yet-created root are included.
Byte-identical reruns repair both generated launchers to mode `0755`. Generated
TOML also escapes DEL (`U+007F`).
Generated addon metadata accepts only the current compatible Auto Storage minor
(`[0.3.0,0.4)` for this kit).
Addon contracts use fixture `main` and exactly the `build` and
`runGameTestServer` tasks. Generated builds bind both gates to the exact
reviewed target jar SHA; target compile/runtime and explicit runtime
dependencies are non-transitive, and evidence task names are never remapped.
Reviewed repositories are emitted first and own target/runtime groups.
Explicit runtime groups cannot fall back to Maven Central even with no reviewed
repositories; target fallback is still protected by its exact SHA gate, and
fixed repositories are filtered to their Auto Storage or Patchouli artifacts.
Reviewed repository, dependency, and group values are emitted as escaped
literal Groovy strings; dependency coordinates must use exact
`group:name:version` structure and cannot contain control characters.
Verification regenerates the expected bundled descriptor or external
`build.gradle`, `settings.gradle`, `gradle.properties`, launchers, and wrapper
artifacts from the reviewed contract and current generator, then checks every
byte and manifest entry. Replacing the launcher or SHA gate and self-attesting
a new manifest hash is therefore explicit drift rather than a pass.

Use `scaffold --bundled contract.json --audit audit.json` and
`verify contract.json --audit audit.json --bundled <repo>` inside the Auto
Storage repository. Bundled verification runs each declared Gradle task
separately, removes only `run/world` before each one, validates every evidence
marker, and checks both the source GameTest annotation count and runtime passing
count. Runtime output must contain exactly one matching success summary;
missing, duplicate, or conflicting summaries fail. World cleanup rejects
a symlinked lexical verification root, any of its ancestors, `run`, or `world`,
plus resolved paths outside the verification root. Passing reports
require strict target identity, all twelve exact checks, and nonempty command
evidence; addon reports require exactly `build` and `runGameTestServer`.
Comment/string-aware annotation extraction ignores fake `@GameTest` text when
counting tests and locating marker-bearing method bodies, skips brace-bearing
intermediate annotations, keeps escaped triple quotes inside Java text blocks,
removes comments before marker matching while retaining executable strings, and
requires each GameTest evidence file to belong to the source set executed by its
declared task and its holder namespace to match that task's
`neoforge.enabledGameTestNamespaces` value. Literal namespaces and bounded
`static final String` references are resolved; missing, ambiguous, or mismatched
holders fail closed. Eligible Java Unicode escapes are rejected before marker
matching so pre-lexical escapes cannot manufacture comments, annotations, or
method boundaries.
Bundled descriptors preserve reviewed HTTPS repository order, and fixture names
must be Java-safe identifiers ending in `Fixture`. Their authoritative GameTest
task is derived from the same fixture name (`evilCraftFixture` becomes
`runEvilCraftGameTestServer`) and mismatches fail before materialization. The
target mod ID must also produce a non-reserved Java package segment. An audited
descriptor must use the audited target coordinate as its primary compile
dependency and include that exact coordinate in runtime dependencies. GameTest
evidence markers must occur between the annotated method's braces; a marker
that appears only in its declaration is not execution evidence. The published
archive includes its own Gradle wrapper template, so an extracted copy can
scaffold an addon without an Auto Storage checkout.
Bundled scaffolding also compares derived module IDs, entrypoints, source sets,
and fixtures with existing descriptors before writing. Publication requires
`publish --version <mod_version>` to match the embedded tool version and
includes only the explicit addon example and ten schema allowlists; local
outputs are excluded.

## Review an update

```bash
./compat-kit diff audit.json target-new.jar \
  --source target-new-source \
  --output delta.json
```

The audit, contract, conformance-plan, delta, generation-plan, proposals,
report, resource-plan, runtime-probe-plan, and runtime-probe schemas are under
`schema/`. Once all
family decisions are complete, the contract schema requires dependency and
repository inputs plus the non-null fixture, positive GameTest count,
authoritative task, nonempty task list, all twelve exact checks, and every
evidence mapping; report targets require mod ID, display name, and version. A
complete public-SDK registration example is under `examples/addon/`; its
reusable workflow is under `examples/github-actions/`. Downloaded jars, source
checkouts, caches, and reports are evidence or build products; do not put them
in a Minecraft instance. Any different target jar SHA requires contract review
even when the compact public-signature/risk delta is empty.
