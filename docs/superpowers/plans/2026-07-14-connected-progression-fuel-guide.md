# Connected Storage Family, Fuel Workspace, Progression, and Guide Implementation Plan

> Current replacement: GitHub #23 removes the historical Fuel target cycle/popup and fixed station-row presentation. Use the persistent Transform target sidebar/use-card and height-adaptive Stations contract instead.

> **Execution rule:** Follow strict RED -> GREEN TDD. Do not edit production code or runtime assets for a task until its focused test fails for the expected reason.

**Goal:** Replace the failed 0.1.17 visual baseline with a connected, progressively ornate storage family; a three-row Fuel workspace; interoperable wrench actions; coherent recipes; and a complete bilingual Patchouli guide.

**Design:** `docs/superpowers/specs/2026-07-14-connected-progression-fuel-guide-design.md`

## Task 1: Freeze the 0.1.17 failure and contracts

**Status:** completed. The four screenshots remain under the failed 0.1.17 run artifact, active docs preserve that verdict, and the replacement contracts below are now durable project rules.

- Preserve the four user screenshots in the current GUI run artifact.
- Record the fullscreen verdict as failed in active planning/notes.
- Record the Fusion pin, wrench tag/interaction grammar, three Fuel categories, cycle reset defaults, status-light grammar, recipe budgets, and guide outline.
- Confirm no 0.1.17 Minecraft process remains; do not close user-owned Terminal windows.

**Verification:** docs links resolve, `git diff --check`, and the artifact hashes are recorded locally.

## Task 2: Fuel geometry and terminal interactions

**Status:** completed. Focused RED contracts reproduced the mixed layout, truncated prompt, detached amount buttons, missing middle-reset dispatch, and incorrect value-selector light; production and focused checks are green.

**0.1.19 verdict and replacement:** the 0.1.19 user-owned fullscreen verdict failed. Vertical cells alone did not establish hierarchy: Fuel Target still competed with the Consumables heading, the final row entered the Inventory label band, and a valid one-resource recipe still left a large white ledger area. The following compact target bar, three-category Fuel geometry, and dark-palette corrections are historical intermediate states. GitHub #23 now owns the current persistent Transform target sidebar, exact-input cards, two-group height-adaptive Stations layout, independent type-capacity panel, and vanilla light container styling. The recipe correction still uses top-aligned resources, at most four columns, and three rows for more than eight resources. The historical 0.1.20 automated GREEN was compileJava/build, Python 155/155, SelfTest 210828/210828, GameTest 269/269, and runData with no new drift. Current visual changes require a fresh user-owned checklist and may not reuse the earlier 0.1.22 verdict.

**RED first:** Add focused SelfTest/Python tests for three full-width `Consumables`/`Timed Stations`/`Instant Stations` panels, descriptor-category filtering, independent paging, sparse edge coverage, slot placement, no overlap at all supported layout breakpoints, unchanged menu parity, wrapped prompt, segmented footer, middle-reset dispatch, and status-marker rules. Run the smallest focused commands and confirm failures reference the old mixed two-row layout, truncation, missing reset action, standalone buttons, and output marker.

**GREEN:** Refactor `TerminalLayout` geometry into category panels/flow grids; rebuild client slots from category mappings; render each row and its exact reserves; replace the recipe footer buttons with one segmented control; wrap empty prompt text; add explicit middle-reset actions to shared cycle controls and server menu handlers; remove value-selector lights.

**Docs:** update `docs/overview.md`, `docs/structure.md`, `docs/notes.md`, `PLAN.md`.

**Focused verification:** `compileJava`, SelfTest, terminal/fuel Python contracts, Fuel/terminal GameTests.

## Task 3: Wrench interoperability and lossless dismantling

**Status:** completed. Seven GameTests cover rotation/dismantling/authority/NBT/overflow/filter isolation, and five resource tests cover registration, tag, recipe, model, and localization.

**RED first:** Add GameTests for own-wrench registration/recipe/tag, foreign `c:tools/wrench` acceptance, Import/Export rotation, ordinary non-directional PASS, sneak dismantle, Core NBT preservation, full-inventory overflow, Export filter isolation, main-hand/server-only execution, and no duplicate mutation.

**GREEN:** Register the Wrench item; add `c:tools/wrench`; implement one shared NeoForge right-click hook; use normal loot generation and inventory-first/world-overflow delivery; rotate only blocks with supported directional state.

**Assets/docs:** add 16x16 wrench texture/model, lang keys, recipe, creative-tab entry, guide content, and structure/notes updates.

**Focused verification:** `compileJava`, wrench GameTests, resource JSON parse.

## Task 4: Fusion connected casing and ornate tiers

**Status:** completed for production, automated contracts, and the current macOS fullscreen delivery. Eight connected-resource tests, ten transactional deploy tests, runner preflight tests, and the fixed-world tests are green; later visual changes still require their own current-run checklist.

**RED first:** Add Python tests for optional Fusion metadata, exact 1.2.12 artifact/hash in deployment config, a vanilla no-Fusion fallback, conditional built-in overlay registration, connecting overlay models/mcmeta, non-connecting item models, bus functional-face preservation, cross-network connection predicates, and tier ornament progression/symmetry outside the old bar mask.

**GREEN:** Keep Fusion out of required metadata; keep vanilla base models and conditionally register a forced built-in client resource-pack overlay when Fusion is loaded; use an isolated `fusionRuntime` source set for client/data development runs while keeping server/GameTest runs Fusion-free; teach Prism deployment to fetch/hash/install the official jar; generate five-tile `pieced` connected casing sheets; update block models using the pinned 1.2.12 array-of-single-`block` predicate schema; redesign T1-T6 motifs in the deterministic family assembly script and regenerate the contact sheet/runtime 16x16 textures. The Prism runner rejects a missing, duplicate, stale, or hash-mismatched Fusion before launch.

**Focused verification:** deployment unittests, texture/model tests, `build`, dedicated-server startup, `runClient --dry-run`.

## Task 5: Progression recipes

**Status:** completed. Six recipe tests enforce exact matrices, valid IDs, starter budget, forbidden midgame gates, upgrade continuity, and the absence of Diamond Block/Netherite Ingot walls.

**RED first:** Add resource tests for the exact recipe table, starter one-diamond budget, functional-midgame forbidden items, valid IDs, upgrade continuity, and no Diamond Blocks/Netherite Ingots.

**GREEN:** Replace Storage Core, Storage Terminal, T1-T6 recipes and add the Wrench recipe exactly as specified. Keep established inexpensive bus/remote recipes unless the tests expose a milestone conflict.

**Docs:** update progression tables in `README.md`, `PLAN.md`, `docs/overview.md`, `docs/notes.md`, and the guide source.

**Focused verification:** recipe tests, `runData`, JSON parse, recipe-manager GameTest lookup.

## Task 6: Complete bilingual Patchouli rewrite

**Status:** completed. Five guide tests enforce six-category/22-JSON en_us/zh_tw structural parity, valid recipe references, required player topics, and banned implementation language.

**RED first:** Add tests requiring identical en_us/zh_tw category, entry, page, recipe-reference, icon, and sort-number structure; all referenced recipes must exist; all required topics must appear; stale labels and implementation language must not appear.

**GREEN:** Replace the current guide with the six-category player manual from the design. Use recipe pages wherever possible and keep prose detailed but player-facing.

**Focused verification:** Patchouli static tests, language parity, `runData`, client boot showing a nonzero exact preloaded-content count.

## Task 7: Full gates, versioned deployment, and fullscreen handoff

**Status:** completed for the current macOS fullscreen delivery. The dark-workspace replacement passed its 0.1.20 automated gates; 0.1.22 then replaced monitor-backed F11 with borderless Cocoa F11, removed `fullscreenResolution`, and rejects handoff if READY-time desktop point/pixel/refresh/depth differs from the captured mode. The user confirmed the current fullscreen result. The old `20260714-224250-crafting-fuel-page` READY artifact remains historical failed evidence; each later visual change must still launch its own current-run scenario and receive a fresh checklist verdict.

- Scan every active doc that describes changed behavior; archive completed/superseded design plans with `agents_rule archive` only after replacement links are current.
- Run `compileJava`, `build`, `runGameTestServer`, all Python tests, `runData`, datagen drift, resource/model/texture checks, `runClient --dry-run`, and `git diff --check`.
- Stop the Gradle daemon after verification.
- Run the transactional patch-version deployment; verify exactly one Auto Storage jar and the exact Fusion jar in Prism dev.
- Rebuild the fixed void world with an updated connected-texture/wrench/Fuel gallery.
- Launch the current visual scenario offline in automatic Minecraft F11 fullscreen, wait for READY and unchanged-desktop-mode verification, then leave only the Minecraft client open for the user. On macOS this is borderless Cocoa F11, never monitor-attached GLFW fullscreen; never use macOS native fullscreen or combine the two modes.
- The user must pass fullscreen and inspect all dynamic checklist items. Close only F11 → visible titled windowed window → Command-Q, then inspect `shutdown.json`; do not claim GUI verification from automated tests.

**Commit/push:** not part of this plan unless the user asks after review.
