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

Review every candidate and record exact ingredients, catalysts, remainders,
outputs, typed units, station rates, costs, bounds, target HTTPS Maven
repositories, additional required runtime artifacts, and evidence. Each
required verification check must name the successful Gradle task plus a source
glob and marker; the declared
`game_test_task` must report exactly `expected_game_tests` passing tests. After
the contract has no `needs_decision` entry, pass the same committed audit to
every later command. Validation compares that audit's exact candidate set with
the contract, so recomputing a contract-only inventory digest cannot hide an
omitted recipe family:

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

Addon contracts use fixture `main` and exactly the `build` and
`runGameTestServer` tasks. Generated builds bind both gates to the exact
reviewed target jar SHA; evidence task names are never remapped. Verification
also checks the manifest hash of the generated `build.gradle` (or bundled
descriptor), so removing that SHA gate is explicit drift rather than a pass.

Use `scaffold --bundled contract.json --audit audit.json` and
`verify contract.json --audit audit.json --bundled <repo>` inside the Auto
Storage repository. Bundled verification runs each declared Gradle task
separately, removes only `run/world` before each one, validates every evidence
marker, and checks both the source GameTest annotation count and runtime passing
count. Bundled descriptors preserve reviewed HTTPS repository order, and fixture
names must be Java-safe identifiers ending in `Fixture`. The published archive
includes its own Gradle wrapper template, so an extracted copy can scaffold an
addon without an Auto Storage checkout.

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
