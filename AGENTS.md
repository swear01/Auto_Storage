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

## Project Docs

- Overview: docs/overview.md
- Structure: docs/structure.md
- Notes: docs/notes.md
- Plan: docs/plan.md
- Roadmap: docs/roadmap.md

## GitHub / CI / GUI Release Gates

- Public repo: https://github.com/swear01/Magic_Storage
- CI lives in `.github/workflows/ci.yml` and must keep `./gradlew build`, minimum/latest-compatible EMI release compilation, `./gradlew runGameTestServer`, `./gradlew runRecipeAddonGameTestServer`, `./gradlew runMekanismGameTestServer`, `./gradlew runBotaniaGameTestServer`, `./gradlew runIronFurnacesGameTestServer`, `./gradlew runFarmersDelightGameTestServer`, `./gradlew runModernIndustrializationGameTestServer`, `./gradlew runArsNouveauGameTestServer`, `./gradlew runEvilCraftGameTestServer`, `./gradlew runPowahGameTestServer`, `./gradlew runIndustrialForegoingGameTestServer`, `./gradlew runCreateGameTestServer`, `./gradlew runPneumaticCraftGameTestServer`, `./gradlew runCompatibilityMatrixGameTestServer`, `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover scripts`, and the `./gradlew runData` datagen drift check green. The addon run independently loads the repository-owned `magic_storage_recipe_fixture`; the eleven optional-mod runs each load one representative CI artifact and execute real behavior assertions, then the compatibility-matrix run loads all eleven artifacts together to catch registration/classpath conflicts. The matrix is fixed at three tests: two coexistence boundaries plus one 10,890-recipe/512-stored-type Craftable benchmark; actual first/p95/warm page switches must stay below 50 ms, steady prefetch p95 below 100 ms, shared retained cache below 8 MiB, and per-menu retained heap below 128 KiB. Do not add optional-mod multi-version matrices: fixture versions are CI evidence, not player-facing exact dependency pins, and compatibility fixes for other versions are driven by user reports. The Botania fixture resolves official `455-SNAPSHOT`, excludes its JEI runtime dependency, retains Curios, reuses the project's Patchouli, and verifies the exact resolved Botania jar SHA-256 before compile/run so a moved snapshot fails explicitly. Every EMI compile/client/data artifact is resolved from Modrinth by exact version ID; `emi_version` remains the human-readable compatibility coordinate paired with that immutable runtime ID. It uploads jar + logs/reports artifacts.
- Optional client boot/resource smoke lives in `.github/workflows/client-smoke.yml` and is `workflow_dispatch` only; it stages required Patchouli, pinned Fusion, and the exact NeoForge 1.21.1 Modrinth runtime matching the latest EMI accepted by `emi_version_range`. The HeadlessMC step has a 10-minute hard timeout and is not GUI layout approval.
- CD lives in `.github/workflows/release.yml`: push tag `v<mod_version>` only after `gradle.properties` has the matching `mod_version`; the workflow rejects mismatched tags, regenerates release notes from git history, reruns all CI gates, and uploads jar + logs/reports.
- GUI/Patchouli/visual changes require `python3 scripts/run_prism_gui_session.py --scenario <scenario>` plus the fixed Prism dev / manual handoff checklist in `docs/notes.md` and `docs/macos-fullscreen-guide.md`. `deploy_prism_dev.py` treats Magic Storage, pinned Fusion, and the 15-jar representative GUI support pack for Iron Furnaces, Farmer's Delight, TMRV, Mekanism, Botania, Modern Industrialization, Ars Nouveau, Powah, Industrial Foregoing, Create, and their shared runtime dependencies as one rollback transaction; TMRV and JEI are mutually exclusive, so deployment removes JEI and `crafting-fuel-page` verifies the deployed support jars against `build/prism-gui-mods` before changing the world or launching. EvilCraft remains isolated GameTest coverage and is deliberately removed from this combined client pack with Cyclops Core: TMRV 0.9.0's JEI stub triggers EvilCraft 1.2.91's Spirit Furnace packet before its JEI registrar exists. Do not whitelist that FATAL. The runner clears the Computer Use wrapper, disables Prism's per-instance error-console pop-up, and requires an already-running, fully initialized normal-root Prism process before using Prism's documented CLI against the configured `dev` instance: `.../prismlauncher -l dev -w MagicStorageGuiTest -o MagicStorageBot`. It must fail before launch when Prism is not warm because Prism cold start refreshes Microsoft/Xbox ownership even with `-o`. Never create a fabricated one-account `-d` root: Prism still requires an owning account to authorize the full game, so an Offline-only root falls into demo/account-selection flow. The runner must not modify `accounts.json`; it reads only the current launcher-log segment and fails before handoff on real Microsoft/Xbox/XSTS/Minecraft-services auth steps or endpoints. Generic Offline `AuthFlow(...)` task and `RefreshSchedule` bookkeeping lines are not sufficient evidence of network authentication. After `MS_GUI_TEST_READY`, it also verifies that the captured macOS desktop display mode is unchanged, then stops automation and hands control to the user. On macOS, `MacOsWindowMixin` makes F11 a borderless Cocoa window and must never attach GLFW to a monitor or select a display mode. macOS native fullscreen (green button or Control-Command-F) and combined native+Minecraft fullscreen are forbidden. Closing is also gated: press F11 once, wait for the normal bordered window, then press Command-Q; never press Command-Q directly from F11 fullscreen. Each visual run starts an exact-PID-and-command shutdown watchdog that terminates only its test Java process if `Stopping!` is followed by a five-second GLFW swap stall, writes `shutdown.json`, and the next run precisely clears any stale process from the same dev test instance. It still scans `latest.log` and fails on every non-whitelisted current-run error; the only extra exact single-line allowances are Botania 455-SNAPSHOT's extensionless Patchouli README and Industrial Foregoing 3.6.39's Curios `example`/`feet` references. Visual verification owner: user; the user must confirm the fullscreen gate before any GUI action. `boot-smoke` does not require visual approval; visual scenarios do. Do not claim GUI verified from GameTest/client-smoke alone.
- Prism GUI worlds, block layouts, navigation functions, and player kits must be scenario-scoped. Include only what the current checklist uses and start already aimed at its first target. Before handoff, automate every repeatable setup step: preload the scenario-owned Core with all required stored resources, stations, Transform reserves/inputs, processing work, and typed resources, and leave the player inventory empty unless the visual check itself requires a held item. The user should only perform fullscreen approval, page/recipe selection, the minimum visual-only input action, and visual inspection; installation, resource loading, waiting, crafting mutation, and other behavior setup belong in GameTests or fixture tests. A preloaded persistent Core must be generated as one matching repository record plus BE storage reference, never as a duplicate client state, and destructive hotbar reset must be omitted for that scenario. Datapack player-kit slots must stay inside Minecraft's actual `/item replace` domains (`hotbar.0..8`, `inventory.0..26`), be unique, and be regression-tested before launch.

## Mod-Specific Essentials

- `magic_storage` — NeoForge 1.21.1 storage+crafting mod. Build: `./gradlew build`.
- EMI is a required **client-only** dependency with release range `[1.1.24,2)`; `emi_version=1.1.24+1.21.1` plus its matching `emi_runtime_version` Modrinth ID are only the reproducible minimum development baseline. Integration code may use only EMI public API packages; dedicated servers must not require EMI.
- Magic Storage must not register third-party recipe workstations into EMI. The owning mod registers its JEI/EMI catalyst; the Prism GUI support pack uses TMRV to expose JEI-plugin metadata to EMI without installing JEI.
- Canonical Item resource variant data must encode the reconstructed `ItemStack`'s `DataComponentPatch`, never its full effective `DataComponentMap`. Third-party item prototypes may contain default components that are valid in memory but intentionally cannot be serialized as explicit values.
- When stuck on storage/network/grid/resource code, **check Refined Storage 2 source first** (patterns only, never copy verbatim — license differs). Full reference table + workflow in `docs/notes.md`.
- Keep all network/storage logic **server-side**; sync to client via packets. Never store storage state client-side.
