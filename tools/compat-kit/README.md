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
  --output audit.json
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
schema-validated before reuse. If the target has classified candidates, a
supplied source module must contain at least one matching tracked, non-ignored
Java source file; ignored outputs and unrelated clean checkouts are rejected.
Candidate paths match only at a package-path segment boundary, and every
risk-evidence owner must be an audited recipe candidate.
NeoForge metadata entries are limited to 1 MiB and class entries to 16 MiB
before decompression. The target is rehashed after inspection so a path
replacement cannot mix one artifact hash with another artifact's evidence.

Review every candidate and record exact ingredients, catalysts, remainders,
outputs, typed units, station rates, costs, bounds, target HTTPS Maven
repositories, additional required runtime artifacts, and evidence. Each
required verification check must name the successful Gradle task plus a source
glob and marker; the declared
`game_test_task` must report exactly `expected_game_tests` passing tests.
Bundled descriptor `expectedTests` is emitted and accepted only as a positive
JSON integer; fractions and wider overflow values fail validation.
Markers assigned to `run*GameTestServer` tasks must occur inside annotated
`@GameTest` method bodies. After
the contract has no `needs_decision` entry, pass the same committed audit to
every later command. Validation compares that audit's exact candidate set with
the contract, including every per-family scanner risk, so recomputing a
contract-only inventory digest cannot hide an omitted recipe family or risk.
Each candidate must remain in the bucket computed by the scanner; moving a
recipe record into a station/resource list is rejected.
Process station rates must be positive and Instant station rates must be zero.
Accepted families keep the `costs` field, but may use an empty list when the
reviewed runtime family is genuinely free.

The scan publishes only public signatures and compact risk evidence. Bounded
private bytecode is inspected for hidden randomness, world/entity access,
multiblocks, live machine state, generic ingredient surfaces, unbounded output,
and capability mutations requiring simulation, but is never stored in the
audit. Named nested classes are included and mapped to their top-level source;
anonymous/local classes, class files carrying `ACC_SYNTHETIC`, and
`META-INF/versions/` aliases are excluded; the root binary name is scanned once.
Chance, randomness, generic-ingredient, and capability-mutation method calls
accept the descriptor syntax emitted by `javap -c -p`.

```bash
./compat-kit scaffold --addon contract.json --audit audit.json \
  --output target-auto-storage
./compat-kit verify contract.json --audit audit.json --addon target-auto-storage \
  --output report.json
```

Scaffolding preflights every destination before writing the first file. Any
existing path with different content, symlinked root/ancestor/target, or
file-valued parent directory fails without leaving a partial project or writing
outside the requested output root. Existing ancestors above a not-yet-created
root are included. Byte-identical reruns repair both generated launchers to
mode `0755`. Generated TOML also escapes DEL (`U+007F`).
Generated addon metadata accepts only the current compatible Auto Storage minor
(`[0.3.0,0.4)` for this kit).
Addon contracts use fixture `main` and exactly the `build` and
`runGameTestServer` tasks. Generated builds bind both gates to the exact
reviewed target jar SHA; target compile/runtime and explicit runtime
dependencies are non-transitive, and evidence task names are never remapped.
Reviewed repositories are emitted first and own target/runtime groups.
Explicit runtime groups cannot fall back to Maven Central; target fallback is
still protected by its exact SHA gate, and fixed repositories are filtered to
their Auto Storage or Patchouli artifacts. Reviewed repository, dependency, and
group values are emitted as escaped literal Groovy strings.
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
symlinked parents and paths outside the verification root. Passing reports
require all twelve exact checks and nonempty command
evidence; addon reports require exactly `build` and `runGameTestServer`.
Comment/string-aware annotation extraction ignores fake `@GameTest` text when
counting tests and locating marker-bearing method bodies, skips brace-bearing
intermediate annotations, and requires each GameTest evidence file to belong to
the source set executed by its declared task.
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
includes only the explicit addon example and four schema allowlists; local
outputs are excluded.

## Review an update

```bash
./compat-kit diff audit.json target-new.jar \
  --source target-new-source \
  --output delta.json
```

The audit, contract, delta, and report schemas are under `schema/`. A complete
public-SDK registration example is under `examples/addon/`; its reusable
workflow is under `examples/github-actions/`. Downloaded jars, source checkouts,
caches, and reports are evidence or build products; do not put them in a
Minecraft instance. Any different target jar SHA requires contract review even
when the compact public-signature/risk delta is empty.
