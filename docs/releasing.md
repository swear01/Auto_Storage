# Releasing Auto Storage

This is the maintainer runbook for the tag-driven alpha publisher in
`.github/workflows/release.yml`.

## Next release target

- Version: `v0.3.0`
- Mod ID and registry namespace: `auto_storage`
- Java package: `com.swear.autostorage`
- Artifact: `build/libs/auto_storage-0.3.0.jar`
- Tracking issue: <https://github.com/swear01/Auto_Storage/issues/31>

This is an intentional breaking identity release. It does not migrate or alias
0.2.x worlds, registries, configs, commands, addon IDs, or saved data. Release
notes and both external listings must tell players to start a new world and
must not present 0.3.0 as a drop-in replacement for 0.2.x.

## Current release evidence

- Version: `v0.2.0`
- Source commit: `dd2f254707654dca03ec615be5da6bc64dd8479c`
- Main CI: `30467153149`
- Release run: `30468162688`
- GitHub prerelease:
  <https://github.com/swear01/Auto_Storage/releases/tag/v0.2.0>
- Canonical jar SHA-256:
  `64cbe2705f2a1d20f83eb1bf848c1df7b74ffe9dab0e4fb958cfde498457b43c`

The release run accepted the same `build/libs/magic_storage-0.2.0.jar` upload
for CurseForge, Modrinth, and GitHub. Public Modrinth and CurseForge downloads
can remain unavailable until their listing reviews finish.

The 2026-07-30 listing audit completed the missing platform metadata. Modrinth
now has the long description, MIT license, categories and featured tags,
canonical source/issues/wiki links, and corrected `0.2.0` Minecraft 1.21.1 +
NeoForge metadata; the project is submitted for moderation. CurseForge now has
the same long description, canonical public GitHub source and Wiki links,
comments enabled, MIT/open-distribution settings, and its `0.2.0` file remains
under review.

## Public project metadata

- Name: **Auto Storage**
- Modrinth: <https://modrinth.com/mod/auto-storage>
- Modrinth project ID/slug: `auto-storage`
- CurseForge author project: <https://authors.curseforge.com/#/projects/1630575/files>
- CurseForge project ID: `1630575`
- Wiki: <https://github.com/swear01/Auto_Storage/wiki>
- Summary: Server-authoritative storage, terminals, deterministic crafting, typed
  resources, and automation for NeoForge 1.21.1.
- Project type: Mod
- Minecraft: 1.21.1
- Loader: NeoForge
- Environment: Client and server
- License: **MIT**
- Source: <https://github.com/swear01/Auto_Storage>
- Issues: <https://github.com/swear01/Auto_Storage/issues>
- Modrinth categories: Storage, Technology, Utility, Management
- Modrinth featured tags: Storage, Technology, Utility
- CurseForge categories: Storage; Utility & QoL; Processing; Automation
- CurseForge comments: enabled
- Icon: `art/release/auto-storage-project-icon.png`
- Icon SHA-256:
  `5bbd61d561cf5f6f3f3b87bbfc439c05b1f48b4a650d76e8e3ca665a5847945c`

Keep both platform long descriptions aligned with the current README feature and
requirement facts. EMI is client-required with the supported range
`[1.1.24,2)`; Patchouli is required; Fusion is optional and must not be declared
as required. Optional compatibility fixtures are CI evidence, not player-facing
exact dependency pins.

The Wiki `Addon Development` and `Compat Kit` pages mirror
`docs/addon-development.md` and `docs/compat-kit.md` for developers. Both are
intentionally excluded from the player-manual Home contents and
sidebar.

Do not feature an old development capture merely to clear a platform suggestion.
The Modrinth gallery and CurseForge media remain empty until a clean current
fullscreen release screenshot exists.

## One-time platform setup

1. The Modrinth and CurseForge projects above must remain public, MIT-licensed,
   and allow third-party distribution.
2. Generate a Modrinth personal access token using its
   [API documentation](https://docs.modrinth.com/api/), and generate an author
   upload token from CurseForge's
   [API Tokens page](https://legacy.curseforge.com/account/api-tokens).
3. In the GitHub `publishing` environment, set variables
   `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID`.
4. In the same environment, set secrets `MODRINTH_TOKEN` and
   `CURSEFORGE_TOKEN`. Enter tokens only through GitHub's secret prompt; never
   paste them into chat, commits, logs, or shell arguments.

The CLI equivalents are:

```bash
gh variable set MODRINTH_PROJECT_ID --env publishing
gh variable set CURSEFORGE_PROJECT_ID --env publishing
gh secret set MODRINTH_TOKEN --env publishing
gh secret set CURSEFORGE_TOKEN --env publishing
```

## Pre-release validation

The release workflow and its non-publishing metadata contract are checked by:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts.test_github_workflows
```

Before tagging, require a clean `main`, green CI, the version-specific visual
verdict, GitHub-triggered bot review evidence for every merged release PR, and
`mod_version` equal to the intended tag without the leading `v`. Local CLI
summaries and manual full-repository audits are not PR review evidence. Run the
shared `github-pr-review-loop`: each iteration selects exactly one available bot,
and the selected bot must report no actionable finding for the latest head. For
the 0.3.0 SDK work merged by PR #35, retrospective PR #36 retains its exact
`297b7a2b74..0d26517a88` base/head evidence and completed Codex result; Cursor's
separately observed routing failure is availability evidence, not a second
required clean review.
The matching `docs/release-notes/<mod_version>.md` file is mandatory; the
workflow prepends it to generated commit history and fails before publishing if
it is missing. Breaking and migration warnings belong in that versioned file so
GitHub, Modrinth, and CurseForge receive the same player-facing notice.
For an SDK or Compat Kit release, clone
`https://github.com/swear01/Auto_Storage.wiki.git`, copy the authoritative
`docs/addon-development.md` and `docs/compat-kit.md` content to
`Addon-Development.md` and `Compat-Kit.md` (rewriting repository-relative links
to canonical `swear01/Auto_Storage` links), and push that Wiki commit before
creating the release tag. Do not add either developer page to the player-manual
Home contents or sidebar. The Wiki pages must not contain an older API or tool
surface than the tagged guides.
The workflow reruns build, minimum/latest EMI compilation, every isolated and
combined GameTest gate, Python tests, and datagen drift before publishing.

## Publish

Create an annotated immutable tag from the verified `main` commit:

```bash
git switch main
git pull --ff-only
version="$(sed -n 's/^mod_version=//p' gradle.properties)"
git tag -a "v${version}" -m "Auto Storage v${version}"
git push origin main "v${version}"
gh run list --workflow Release --limit 1
```

The workflow publishes the player alpha jar to GitHub, Modrinth, and
CurseForge. After the three-platform upload succeeds, it attaches the
compile-only API jar, API sources, API Javadocs, and reproducible
`auto-storage-compat-kit-<version>.zip` to the same GitHub Release. Those
developer assets are not sent to Modrinth or CurseForge as player files. A
missing secret, missing variable, tag mismatch, test failure, or
platform/developer-asset upload failure keeps the workflow red.

## Verify

After the external listings become public, download the jar from all three
targets and compare them with the immutable GitHub release asset:

```bash
shasum -a 256 \
  path/to/github-jar \
  path/to/modrinth-jar \
  path/to/curseforge-jar
```

All three player-jar SHA-256 values must match. Do not substitute a later local
rebuild; only the uploaded workflow artifact is release evidence. Also verify
the GitHub Release contains the matching
`auto_storage-<version>-api.jar`,
`auto_storage-<version>-api-sources.jar`, and
`auto_storage-<version>-api-javadoc.jar`, plus
`auto-storage-compat-kit-<version>.zip`; run an addon compile through the public
dependency in `docs/addon-development.md`, and run
`compat-kit --help` from the extracted archive. Finally verify alpha status,
Minecraft 1.21.1, NeoForge, Java 21, client/server environment, EMI/Patchouli
dependencies, changelog, source/issues links, and the project icon.

This dashboard verification is mandatory even when `mc-publish` is green. The
initial `v0.2.0` Modrinth upload was accepted while its dashboard still showed
no game version and no loader; both fields were corrected manually on
2026-07-30 before moderation submission.

## Retry and rollback

- If the run failed before `Publish alpha`, fix the cause on `main`, use a new
  patch version, and create a new tag. For a confirmed transient runner failure
  with no publication attempt, `gh run rerun <run-id> --failed` reruns the same
  commit and ref.
- If `Publish alpha` started, inspect GitHub, Modrinth, and CurseForge before any
  retry. Do not blindly rerun a partially published version or move/force-push
  its tag.
- A partial release is incomplete even if one platform succeeded. Keep the
  workflow red, mark any bad platform file unavailable through its dashboard,
  correct the problem on `main`, and publish a new patch version.
- Never replace an already public jar under the same version. Roll forward with
  a new version so hashes and changelogs remain auditable.

## Secret rotation

Create replacement tokens first, update the two GitHub environment secrets
through `gh secret set`, verify the next release, then revoke the old tokens in
Modrinth and CurseForge. Rotate immediately after suspected exposure. Project
IDs are non-secret GitHub environment variables and change only if a project is
recreated.
