# BEGIN agents_rule-base
# Agent Rules

## Core Rules

### Stay On Task
Execute ONLY what was requested. If unclear, STOP and ASK. Do NOT assume.
One task at a time. After completing the task, STOP.

### Search First, Never Guess
NEVER fabricate code, file paths, function names, or API behavior from memory.
Do NOT implement, edit, or answer from assumptions. Do NOT proceed with a "reasonable
default" when authoritative guidance is missing.

Before any action, discover what already exists:

1. **Local** — Read target files; Grep/Glob the repo; check `docs/`, README,
   `AGENTS.md`, scoped `AGENTS.md`, and relevant skills for project guidance.
2. **External** — For libraries, APIs, tools, or time-sensitive facts (versions,
   pricing, compatibility, recent changes), search the web or official docs.
   Use Context7 MCP when available. Never rely on training data alone.

First tool calls in every task MUST be discovery (Read, Grep, Glob, SemanticSearch,
WebSearch, or doc MCP) — not edits and not invented answers.

If search finds nothing authoritative, STOP and report what you searched, what you
expected, and what decision you need from the user. Do NOT guess or fill gaps yourself.

### Code Quality
Match existing code style, naming, and patterns.
No new libraries unless asked. No comments unless asked.
Keep changes minimal.

### GitHub PR Review
When the user explicitly requests GitHub PR code review, invoke the
`github-pr-review-loop` skill. A local agent review is supplementary evidence,
not a substitute for a GitHub review bot.

### No-Useless Options
When changing behavior, change it — do not keep the old behavior as an option.
Never add flags, parameters, or config options that were not explicitly requested.
If you are about to add an "option to preserve old behavior," stop: just change the behavior.

## No Silent Fallback

### Banned Behaviors
- Silently replacing a failing API/model/library/tool with another
- Returning dummy/mock/empty/default results as if valid
- Broad catch-and-continue (`except Exception`, `catch (error)`, etc.)
- Skipping tests, linters, type checks, or verifiers
- Downgrading implementation scope just to finish
- Hiding failures behind "best effort"

### Allowed Behaviors
- Retry the exact same operation once if transient
- Propose a fallback, but STOP before implementing it
- Use fallback only when explicitly approved by the user

### When Blocked, Report
1. What failed
2. Exact command/tool/API that failed
3. Relevant error output
4. Fallback considered but NOT implemented
5. Decision needed from user

## Learn From Mistakes

When you discover that your own incorrect assumption, decision, or action caused
an error, persist the lesson during the same task if it is verified and reusable.

- Record project-specific facts and gotchas in `docs/notes.md`.
- Update the relevant active doc when the correction changes documented behavior,
  commands, APIs, configuration, or workflow.
- Change `AGENTS.md` or its managed template only when the lesson is a durable rule
  that should govern future agent behavior.
- State what was updated in the final response.
- Do not record transient failures, guesses, or secrets.

## Docs Lifecycle

- Active docs live under `docs/`.
- Historical docs live under `archive/` (mirrors original path).
- Every behavior/API/CLI/config change must update the relevant active doc
  immediately, as part of the same change — never deferred to "later".
- Obsolete docs must be archived, not left active.
- Archived docs must not be treated as current truth.
- Active docs must not link to archived docs as active references.

Before every commit, scan every doc that references or describes the changed
code/behavior and confirm it is current — fix or archive stale content. No exceptions.
Scope the scan to what the change touches; full-tree sweeps only when explicitly requested.

If no docs update is needed, explicitly report:

    Docs checked; no documentation update required.

## Archive Policy

**Archive vs Delete:**
- Archive: doc has historical value (old API, past decision, superseded design)
- Delete: doc is simply wrong, redundant, or never useful — `git rm` it directly

Do not archive to avoid decisions. Archiving inflates repo size; delete what has no value.

Use `agents_rule archive <file>` to archive docs. Do NOT manually move files.

Archive header prepended automatically:

    > Archived: YYYY-MM-DD
    > Reason: <reason>
    > Replacement: <replacement-or-none>
    > Status: historical only; do not use as active truth.

Archives live under `archive/` at project root, preserving original path:

    docs/api.md  →  archive/docs/api.md

The `archive/` tree is excluded from ripgrep by default.

When searching, prefer `rg` over `grep` — it respects `.rgignore` automatically.
If `grep` must be used, always exclude archive/:

    grep -r --exclude-dir=archive ...

## Verification Policy

- Run the smallest relevant verification command before declaring done.
- Never claim tests passed unless they actually ran and passed.
- If verification cannot run, explain exactly why.

Final response must include:
- Files changed
- Docs updated, or: `Docs checked; no documentation update required.`
- Verification command run and result
- Remaining risks

## Git-Safe Move Policy

All tracked file moves MUST use `git mv`. Direct `mv`/`rename` on tracked files is forbidden.

For docs archiving: always use `agents_rule archive`. This ensures the move is recorded as a rename in Git, not delete+add.

Expected `git status` after archiving:

    R  docs/old.md -> archive/docs/old.md
# END agents_rule-base

## Player-Facing UX

Simple is better for player-facing text and controls.
Show only what the player needs for the current action; omit implementation details,
redundant instructions, and input hints that are discoverable through interaction.
Recipe/source labels start from the owning recipe-viewer category name and must be
true for every accepted workstation variant. Use the shortest shared localized
category/family name; never name it from the first/representative/installed stack
or retain tier/speed suffixes. If no truthful shared name exists, split the family.
`station_work` is an internal persistence/resource/transaction key only. The
player-facing resource group is `Processing`, and each Processing value tooltip
uses its descriptor's localized logical family name rather than `Station Work`.

## Project Docs

- Overview: docs/overview.md
- Structure: docs/structure.md
- Notes: docs/notes.md
- Plan: docs/plan.md
- Roadmap: docs/roadmap.md

## GitHub / CI / GUI Release Gates

- Public repo: https://github.com/swear01/Auto_Storage
- A PR may be declared reviewed or merged only after the shared `github-pr-review-loop` Skill obtains inspectable **GitHub-triggered PR review** evidence for the latest head. Each iteration selects exactly one available bot in the Skill's order; do not trigger multiple providers for the same head. Local CLI/Antigravity/Cursor runs, copied summaries, reactions without a completed review, and `.cursor/skills/full-repo-audit/` do not satisfy this gate. Automatic review for participating providers must be disabled before the manual loop. Provider disabled/quota/auth failures remain explicit availability evidence for selecting the next bot, never a silent local fallback. The consumer Gemini Code Assist GitHub reviewer is sunset and unavailable.
- CI lives in `.github/workflows/ci.yml` and must keep `./gradlew build`, minimum/latest-compatible EMI release compilation, `./gradlew runGameTestServer`, `./gradlew runRecipeAddonGameTestServer`, `./gradlew runAe2GameTestServer`, `./gradlew runMekanismGameTestServer`, `./gradlew runBotaniaGameTestServer`, `./gradlew runIronFurnacesGameTestServer`, `./gradlew runFarmersDelightGameTestServer`, `./gradlew runModernIndustrializationGameTestServer`, `./gradlew runArsNouveauGameTestServer`, `./gradlew runEvilCraftGameTestServer`, `./gradlew runPowahGameTestServer`, `./gradlew runIndustrialForegoingGameTestServer`, `./gradlew runCreateGameTestServer`, `./gradlew runPneumaticCraftGameTestServer`, `./gradlew runExtendedCraftingGameTestServer`, `./gradlew runCompatibilityMatrixGameTestServer`, `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts`, and the `./gradlew runData` datagen drift check green. The addon run independently loads the repository-owned `auto_storage_recipe_fixture`; the thirteen optional-mod runs each load one representative CI artifact and execute real behavior assertions, then the compatibility-matrix run loads all thirteen artifacts together to catch registration/classpath conflicts. The matrix is fixed at three tests: two coexistence boundaries plus one 11,657-recipe/10,000-exact-type Terminal benchmark; `-PterminalScaleTypes=30000` is the issue-closing stress run. Truly cold default-Name Storage opening, first Craftable construction, and actual shared Craftable first/p95/warm switches must stay below 50 ms; one-time alternate sort/search interaction p95 stays below 250 ms, the warm marker interval contains no server keep-up warning, per-menu retained heap stays below 128 KiB, and shared retained cache stays below 8 MiB at both 10k and 30k. Both runs write `build/reports/terminal-scale-<types>.json`, round-trip one production repository record exactly, and keep every inventory segment at no more than 63 types. Do not add optional-mod multi-version matrices: fixture versions are CI evidence, not player-facing exact dependency pins, and compatibility fixes for other versions are driven by user reports. The Botania fixture resolves official timestamped build `455-20260723.172746-31` from the upstream `455-SNAPSHOT` series, excludes its JEI runtime dependency, retains Curios, reuses the project's Patchouli, and verifies the exact jar SHA-256 before compile/run. Do not point CI back at the mutable `455-SNAPSHOT` coordinate: a newer upstream build must not silently replace the representative fixture. Every EMI compile/client/data artifact is resolved from Modrinth by exact version ID; `emi_version` remains the human-readable compatibility coordinate paired with that immutable runtime ID. CI and release upload jar, API artifacts, reproducible Compat Kit archive, logs, and reports.
- Compat Kit is development-only. `docs/compat-kit.md` is authoritative and the GitHub Wiki `Compat Kit` page mirrors it, but developer pages must stay out of the player-manual Home contents and sidebar. The tool may automate bounded audit evidence, explicit-contract scaffolding, and declared verification; it must never infer recipe semantics or become runtime reflection fallback.
- Compat Kit complete contracts must declare target HTTPS Maven repositories, every additional required runtime artifact, one authoritative GameTest task/count, require descriptor `expectedTests` to be a positive JSON `Integer` without fractional/overflow truncation, and `{task, source, marker}` evidence for every required check. A source checkout passed to `scan` or `diff` must belong to Git, discover its enclosing worktree, require that whole worktree to be clean including untracked files, and contain at least one tracked, non-ignored mapped Java source whenever the target has classified candidates; candidate-source suffixes must match at a path-segment boundary; current-format cached audits and committed audits must record the current scanner format and pass the complete nested target/artifact/source/candidate/risk schema before reuse, every candidate must remain in the bucket computed by the current scanner, and every risk-evidence owner must be an audited recipe candidate. NeoForge mod metadata entries are limited to 1 MiB and class entries to 16 MiB before decompression; the target path must still have the same size and SHA after inspection as the bytes hashed before inspection. Every complete validation/scaffold/verify must load the separate committed source audit and compare its exact recipe-candidate set, per-family risk set, target identity, artifact SHA, and inventory digest with the contract; a recomputed contract-only `source_recipe_inventory_sha256`, moved candidate, or deleted risk cannot authorize incomplete review. Process station variants require positive rates and Instant variants require zero rates, matching runtime `MachineDescriptor` validation. Accepted families require an explicit `costs` field but may use an empty list for a reviewed runtime family that is genuinely free. Verification must confirm the exact source annotation count, exactly one non-conflicting runtime success summary with the exact passing count, marker presence, successful associated task, and resolved target jar SHA before reporting a check passed; a marker assigned to a `run*GameTestServer` task must occur between an annotated `@GameTest` method's braces, not only in its annotation or declaration; commented/string annotations are not GameTests, brace-bearing annotations between `@GameTest` and the method cannot become the evidence body, every GameTest evidence source must belong to the source set executed by its declared task, and evidence task names are never remapped. Published passing-report schemas must require all twelve exact checks and nonempty command evidence; addon reports require exactly `build` plus `runGameTestServer`. External addon contracts use fixture `main` and exactly those two tasks; generated addon GameTest runtimes must include Auto Storage's required Patchouli dependency, every target-derived TOML value must be TOML basic-string escaped including U+007F, and every target-derived Gradle string must be emitted as an escaped literal. Every scaffold must preflight all destination drift and reject file-valued or symlinked ancestors/targets, including existing ancestors above a not-yet-created output root, before its first write; byte-identical reruns must restore both generated launchers to mode `0755`. Published contract schemas must enforce accepted/rejected family status requirements consistently with CLI validation. Generated independent addons must constrain Auto Storage to the current compatible pre-1.0 minor line. Bundled fixture names must match `[a-z][A-Za-z0-9]*Fixture` before materialization, their `game_test_task` must match the run task derived from that preserved camel-case fixture name, target IDs must produce non-reserved Java package segments, and derived module IDs/entrypoints/source sets/fixtures must not collide with existing descriptors before any file is written. Bundled descriptors must retain the contract's reviewed repository order and runtime dependencies; audited descriptors must use the audited coordinate as their primary compile dependency and include it unchanged in runtime dependencies. Generated target compile/runtime and explicit runtime dependency declarations are non-transitive so undeclared artifacts cannot enter verification. Verification-sensitive bundled descriptors and the complete external Gradle execution chain (`build.gradle`, `settings.gradle`, `gradle.properties`, both launchers, and wrapper jar/properties) must be regenerated from the reviewed contract and generator before comparison; a manifest cannot self-attest an edited gate or launcher. Fresh GameTest world cleanup must reject symlinked `run`/`world` parents and resolved paths outside the verification root before deletion. Generated addons put reviewed repositories first and restrict them to target/runtime groups; explicit runtime groups cannot fall back to Maven Central, while the target may fall back only under its exact SHA gate. Fixed Maven Central/BlameJared/Ivy entries exclude or include the Patchouli/Auto Storage groups they own. The compatibility-matrix task must directly depend on every descriptor-generated audited-artifact verifier plus the separately pinned Botania verifier, so direct 10k/30k invocation cannot bypass SHA evidence. Named nested classes are audited and mapped to their top-level source while anonymous/local names, JVM `ACC_SYNTHETIC` classes, and `META-INF/versions/` archive aliases are excluded; duplicate normalized family names use a collision-free encoded binary-name suffix; `javap` chance, randomness, generic-ingredient, and capability-mutation detection must accept invocation descriptors such as `getChance:(...)`, `random:(...)`, `getIngredients:(...)`, and `insertItem:(...)`; every changed target artifact SHA forces contract review even when public signatures are stable. Descriptor-generated run names come from the fixture's preserved camel case (`evilCraftFixture` → `runEvilCraftGameTestServer`), never from a lowercased directory ID. Published archives must scaffold independently with their adjacent wrapper template, include only explicit repository-owned addon-example files rather than recursive local outputs, and fail publication unless Gradle `mod_version` equals the embedded Compat Kit tool version.
- Optional client boot/resource smoke lives in `.github/workflows/client-smoke.yml` and is `workflow_dispatch` only; it stages required Patchouli, pinned Fusion, and the exact NeoForge 1.21.1 Modrinth runtime matching the latest EMI accepted by `emi_version_range`. The HeadlessMC step has a 10-minute hard timeout and is not GUI layout approval.
- CD lives in `.github/workflows/release.yml`: push tag `v<mod_version>` only after `gradle.properties` has the matching `mod_version`; the workflow rejects mismatched tags, regenerates release notes from git history, reruns all CI gates, uploads jar + logs/reports, and publishes one alpha to GitHub, Modrinth, and CurseForge. Every release Action is pinned to a full commit SHA and the Gradle 9.2.1 wrapper distribution is checksum-verified. GitHub environment `publishing` must define secrets `MODRINTH_TOKEN`/`CURSEFORGE_TOKEN` and variables `MODRINTH_PROJECT_ID`/`CURSEFORGE_PROJECT_ID`; missing values fail closed before publishing.
- GUI/Patchouli/visual changes require `python3 scripts/run_prism_gui_session.py --scenario <scenario>` plus the fixed Prism dev / manual handoff checklist in `docs/notes.md` and `docs/macos-fullscreen-guide.md`. `deploy_prism_dev.py` treats Auto Storage, every current `auto_storage-*.jar`, every pre-0.3 legacy-basename jar, pinned Fusion, and the 18-jar representative GUI support pack, including hash-pinned MacFix 0.1.0 plus Iron Furnaces, Farmer's Delight, TMRV, Mekanism, Botania, Modern Industrialization, Ars Nouveau, Powah, Industrial Foregoing, Create, Extended Crafting, and their shared runtime dependencies as one rollback transaction. The GUI runner rejects any remaining legacy-basename jar before launch; never let a 0.2.x and 0.3.x jar coexist because their different mod IDs can both load. Until https://modrinth.com/mod/macfix is public, MacFix must be staged only from `../macfix/build/libs/macfix-0.1.0.jar` with SHA-256 `79904d59892c4c5384811a384f3ce88aa5b3d6e8224dbde1b78dc2f80020080c`; missing or changed artifacts fail closed, and it remains a GUI-test utility rather than a player dependency. Replace this local source with an immutable Modrinth version ID after approval. TMRV and JEI are mutually exclusive, so deployment removes JEI and `crafting-fuel-page` verifies the deployed support jars against `build/prism-gui-mods` before changing the world or launching. EvilCraft remains isolated GameTest coverage and is deliberately removed from this combined client pack with Cyclops Core: TMRV 0.9.0's JEI stub triggers EvilCraft 1.2.91's Spirit Furnace packet before its JEI registrar exists. Do not whitelist that FATAL. The runner clears the Computer Use wrapper, disables Prism's per-instance error-console pop-up, and requires an already-running, fully initialized normal-root Prism process before using Prism's documented CLI against the configured `dev` instance: `.../prismlauncher -l dev -w AutoStorageGuiTest -o AutoStorageBot`. It must fail before launch when Prism is not warm because Prism cold start refreshes Microsoft/Xbox ownership even with `-o`. Never create a fabricated one-account `-d` root: Prism still requires an owning account to authorize the full game, so an Offline-only root falls into demo/account-selection flow. The runner must not modify `accounts.json`; it reads only the current launcher-log segment and fails before handoff on real Microsoft/Xbox/XSTS/Minecraft-services auth steps or endpoints. Generic Offline `AuthFlow(...)` task and `RefreshSchedule` bookkeeping lines are not sufficient evidence of network authentication. After `AS_GUI_TEST_READY`, it also verifies that the captured macOS desktop display mode is unchanged, then stops automation and hands control to the user. On macOS, `MacOsWindowMixin` makes F11 a borderless Cocoa window and must never attach GLFW to a monitor or select a display mode. macOS native fullscreen (green button or Control-Command-F) and combined native+Minecraft fullscreen are forbidden. Closing is also gated: press F11 once, wait for the normal bordered window, then press Command-Q; never press Command-Q directly from F11 fullscreen. Each visual run starts an exact-PID-and-command shutdown watchdog that terminates only its test Java process if `Stopping!` is followed by a five-second GLFW swap stall, writes `shutdown.json`, and the next run precisely clears any stale process from the same dev test instance. It still scans `latest.log` and fails on every non-whitelisted current-run error; the only extra exact single-line allowances are Botania 455-SNAPSHOT's extensionless Patchouli README and Industrial Foregoing 3.6.39's Curios `example`/`feet` references. Visual verification owner: user; the user must confirm the fullscreen gate before any GUI action. `boot-smoke` does not require visual approval; visual scenarios do. Do not claim GUI verified from GameTest/client-smoke alone.
- Prism GUI worlds, block layouts, navigation functions, and player kits must be scenario-scoped. Include only what the current checklist uses and start already aimed at its first target. Before handoff, automate every repeatable setup step: preload the scenario-owned Core with all required stored resources, stations, Transform reserves/inputs, processing work, and typed resources, and leave the player inventory empty unless the visual check itself requires a held item. The user should only perform fullscreen approval, page/recipe selection, the minimum visual-only input action, and visual inspection; installation, resource loading, waiting, crafting mutation, and other behavior setup belong in GameTests or fixture tests. A preloaded persistent Core must be generated as one matching repository record plus BE storage reference, never as a duplicate client state, and destructive hotbar reset must be omitted for that scenario. Datapack player-kit slots must stay inside Minecraft's actual `/item replace` domains (`hotbar.0..8`, `inventory.0..26`), be unique, and be regression-tested before launch. The on-demand `terminal-scale` scenario accepts only `--scale-types 10000` or `30000`, creates exact component-bearing variants in that one production record, then uses a marker-gated server command to add every actual runtime registered item at configurable `--items-per-type`, all Processing/Instant descriptors, and required work reserves before `AS_GUI_RUNTIME_FIXTURE_READY`; it leaves the player inventory empty, provides both Storage/Crafting Terminal navigation, and is the required F11 gate for prepared large-grid open, scrollbar, Craftable preview, and recipe-ordering changes. True cold-open latency belongs to the compatibility-matrix benchmark and must not be claimed from the prewarmed visual world.

## Mod-Specific Essentials

- `auto_storage` — NeoForge 1.21.1 storage+crafting mod. Build: `./gradlew build`.
- EMI is a required **client-only** dependency with release range `[1.1.24,2)`; `emi_version=1.1.24+1.21.1` plus its matching `emi_runtime_version` Modrinth ID are only the reproducible minimum development baseline. Integration code may use only EMI public API packages; dedicated servers must not require EMI.
- Auto Storage must not register third-party recipe workstations into EMI. The owning mod registers its JEI/EMI catalyst; the Prism GUI support pack uses TMRV to expose JEI-plugin metadata to EMI without installing JEI.
- Canonical Item resource variant data must encode the reconstructed `ItemStack`'s `DataComponentPatch`, never its full effective `DataComponentMap`. Third-party item prototypes may contain default components that are valid in memory but intentionally cannot be serialized as explicit values.
- When stuck on storage/network/grid/resource code, **check Refined Storage 2 source first** (patterns only, never copy verbatim — license differs). Full reference table + workflow in `docs/notes.md`.
- Keep all network/storage logic **server-side**; sync to client via packets. Never store storage state client-side.
