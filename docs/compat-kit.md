# Auto Storage Compat Kit

Compat Kit is the development-time workflow for adding deterministic cross-mod
support without repeatedly rereading an upstream repository or weakening Auto
Storage's transaction boundary. It collects facts, makes unresolved semantics
explicit, generates RED scaffolds, and runs declared verification gates. It is
never loaded by Minecraft.

This file is authoritative. The GitHub Wiki `Compat Kit` page mirrors it, but
the developer page is intentionally absent from the player-manual Home contents
and sidebar.

## Safety boundary

Compat Kit automates evidence and boilerplate, not recipe semantics.

- No runtime jar scanning, reflection fallback, serializer-name inference,
  machine-name inference, or EMI/JEI crafting logic.
- `scan` publishes public surfaces and risk evidence only. It also inspects
  bounded private bytecode to detect hidden randomness or world/entity access,
  but never writes that private bytecode into the audit.
- A human or reviewing agent explicitly decides consumed inputs,
  catalysts/tools, remainders, all outputs, typed units, station rates, costs,
  deterministic bounds, and rejections.
- `needs_decision` blocks `scaffold` and `verify`.
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
build/compat-kit/cache/<jar-sha>/v*/ ignored versioned scan cache
build/compat-kit/report.json         ignored verification report
```

The audit and reviewed contract are committed. Caches, downloaded jars, source
checkouts, and transient reports remain under ignored `build/`.

## End-to-end workflow

### 1. Acquire reproducible evidence

Download one official representative jar and, when available, check out its
matching source tag or commit. Record the artifact URL, SHA-256, and source
revision in the compatibility document. Never silently replace a failed source.

### 2. Scan

```bash
tools/compat-kit/compat-kit scan \
  --jar build/compat-kit/artifacts/target.jar \
  --source build/compat-kit/sources/target \
  --output compat/audits/target/1.2.3.json
```

The deterministic audit contains NeoForge identity, artifact SHA/size, source
revision and exact source paths, recipe-class/public-signature evidence,
resource API candidates, station candidates, and explicit risk flags. Risk
detection uses bounded `javap -c -p` output so non-public implementation risks
are not missed; only compact flags for randomness/chance, world/entity access,
multiblocks, live machine state, generic ingredient surfaces, unbounded
outputs, and capability mutations requiring simulation are persisted. Without
`--source`, scans are cached by jar SHA under `build/compat-kit/cache/`;
repeating the same SHA and scanner format needs no network access. A scanner
format change uses a new cache namespace instead of trusting stale evidence.
Named nested classes are audited and mapped back to their top-level Java source;
anonymous, local, and synthetic `$<number>` classes are excluded.

Archive limits, candidate counts, signature size, source-file counts, malformed
metadata, ambiguous multi-mod jars, missing JDK tools, and `javap` failures all
fail closed.

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

An accepted family records:

1. exact recipe class and `RecipeType`;
2. station descriptor, category, variants, and rational rates;
3. every consumed input, catalyst, tool/durability, and remainder;
4. every primary and secondary output with exact typed units;
5. station-work and item/fluid/energy/chemical/addon-resource costs;
6. deterministic bounds and source/class/method evidence;
7. target Maven repositories as explicit HTTPS URLs;
8. fixture, exact GameTest count, authoritative GameTest task, Gradle gates,
   and every required check mapped to `{task, source, marker}` evidence.

Commit the reviewed result as `compat/contracts/<mod-id>.json`.

Repository declarations are build inputs, not discovery hints. A target
published through Modrinth Maven, Curse Maven, or its own Maven must list the
required repository in `target.repositories`; an empty list means Maven
Central is sufficient. Generated addons copy only these reviewed repositories
and never guess a repository from the dependency coordinate.

### 4. Scaffold a RED integration

Bundled module:

```bash
tools/compat-kit/compat-kit scaffold \
  --bundled compat/contracts/target.json
```

Independent addon:

```bash
tools/compat-kit/compat-kit scaffold \
  --addon compat/contracts/target.json \
  --output ../target-auto-storage
```

Bundled output owns its `src/compat/<mod-id>/compat-module.json`, isolated source
set, one-call `AutoStorageCompatModule`, present-mod fixture, and GameTest
structure. Gradle discovers the source set, target dependencies, fixture mod,
run task, expected test gate, and audited target artifact from that descriptor;
the target is on both compile and fixture runtime classpaths. `build` and the
module GameTest resolve exactly one target jar and reject a SHA different from
the reviewed audit. No central compatibility switch is added.

External output is an ordinary NeoForge project with the Gradle wrapper,
compile-only Auto Storage API dependency, reviewed target repositories and
target dependency, one-call
`AutoStorageAddon.register(...)`, dependency metadata, a RED present-target
GameTest, and reusable GitHub Actions workflow. It cannot compile against Auto
Storage implementation classes. Its `build` and `runGameTestServer` gates also
resolve exactly one target jar and verify `source_audit_sha256`.

The generated adapter and fixture are deliberately RED. Rerunning `scaffold`
is byte-deterministic and refuses to overwrite drift. The manifest binds the
scaffold to the reviewed contract; implementation edits are expected after the
initial RED generation.

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
  --bundled . \
  --output build/compat-kit/target-report.json
```

For an external addon, use `--addon <directory>`. Addon contracts must declare
exactly `build` and `runGameTestServer`, use `main` as their fixture, and map
evidence only to those actual tasks; the verifier never rewrites bundled task
names into addon task names. It runs both gates and gives
`runGameTestServer` a fresh world. Verification rejects an unresolved
contract, contract/manifest mismatch, remaining RED marker, forbidden
implementation links, missing evidence source/marker, a source annotation
count different from `expected_game_tests`, GameTest output that does not
report that exact passing count, an undeclared evidence task, or any non-zero
command. A check is marked passed only when all of its declared source markers
exist and the associated Gradle task succeeds. The report records those
per-check evidence links, exact commands, exit codes, output hashes, tool
version, target, and manifest hash. Bundled verification runs every declared
Gradle task as a separate process and removes only `run/world` before each task
so stale GameTest state cannot leak between fixtures. RED and forbidden-link
scans are scoped to `src/`; ignored `build/` outputs and previously extracted
scaffolds cannot poison a later verification. The manifest also hashes the
bundled descriptor or external `build.gradle`, so the target-SHA gate cannot be
removed after scaffolding while retaining a passing report.

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

## Distribution

```bash
./gradlew compatKitArchive
```

This produces
`build/distributions/auto-storage-compat-kit-<mod_version>.zip` with the CLI,
schemas, Gradle wrapper, RED templates, complete public-SDK addon example, CI
example, README, and license.
`assemble`/`build` includes the archive, CI uploads it, and tagged releases add
it beside API, sources, and Javadocs artifacts.

Archive member order, timestamps, permissions, and bytes are reproducible.
The extracted archive is also self-contained: `scaffold --addon` resolves its
Gradle wrapper template beside the extracted CLI instead of requiring an Auto
Storage source checkout.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.test_compat_kit
```

## First dogfood: AE2 Inscriber

The committed AE2 19.2.17 audit and contract prove this workflow against a real
target. The accepted slice is only `InscriberRecipe`:

- middle is consumed;
- optional top/bottom are retained for `INSCRIBE` and consumed for `PRESS`;
- output is one exact stack;
- cost is 200 station-work steps and 2,000 configured AE converted through
  AE2's public `PowerUnit` API to a finite positive exact integer FE amount;
- the plain Inscriber contributes 2 work per tick; speed-card state is not
  inferred from its item;
- Charger and every unaudited AE2 family remain rejected.

See [AE2 compatibility](ae2-compatibility.md).
