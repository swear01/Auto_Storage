# Releasing Auto Storage

This is the maintainer runbook for the tag-driven alpha publisher in
`.github/workflows/release.yml`.

## Current release evidence

- Version: `v0.2.0`
- Source commit: `dd2f254707654dca03ec615be5da6bc64dd8479c`
- Main CI: `30467153149`
- Release run: `30468162688`
- GitHub prerelease:
  <https://github.com/swear01/Magic_Storage/releases/tag/v0.2.0>
- Canonical jar SHA-256:
  `64cbe2705f2a1d20f83eb1bf848c1df7b74ffe9dab0e4fb958cfde498457b43c`

The release run accepted the same `build/libs/magic_storage-0.2.0.jar` upload
for CurseForge, Modrinth, and GitHub. Public Modrinth and CurseForge downloads
can remain unavailable until their listing reviews finish.

## Public project metadata

- Name: **Auto Storage**
- Modrinth: <https://modrinth.com/project/auto-storage>
- Modrinth project ID/slug: `auto-storage`
- CurseForge author project: <https://authors.curseforge.com/#/projects/1630575/files>
- CurseForge project ID: `1630575`
- Summary: Server-authoritative storage, terminals, deterministic crafting, typed
  resources, and automation for NeoForge 1.21.1.
- Project type: Mod
- Minecraft: 1.21.1
- Loader: NeoForge
- Environment: Client and server
- License: **MIT**
- Source: <https://github.com/swear01/Magic_Storage>
- Issues: <https://github.com/swear01/Magic_Storage/issues>
- Icon: `art/release/magic-storage-project-icon.png`
- Icon SHA-256:
  `5bbd61d561cf5f6f3f3b87bbfc439c05b1f48b4a650d76e8e3ca665a5847945c`

Use the current README as the long description. EMI is client-required with the
supported range `[1.1.24,2)`; Patchouli is required; Fusion is optional and must
not be declared as required. Optional compatibility fixtures are CI evidence,
not player-facing exact dependency pins.

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
verdict, and `mod_version` equal to the intended tag without the leading `v`.
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

The workflow publishes one alpha jar to GitHub, Modrinth, and CurseForge. A
missing secret, missing variable, tag mismatch, test failure, or platform upload
failure keeps the workflow red.

## Verify

After the external listings become public, download the jar from all three
targets and compare them with the immutable GitHub release asset:

```bash
shasum -a 256 \
  path/to/github-jar \
  path/to/modrinth-jar \
  path/to/curseforge-jar
```

All three SHA-256 values must match. Do not substitute a later local rebuild;
only the uploaded workflow artifact is release evidence. Also verify alpha
status, Minecraft 1.21.1, NeoForge, Java 21, client/server environment,
EMI/Patchouli dependencies, changelog, source/issues links, and the project
icon.

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
