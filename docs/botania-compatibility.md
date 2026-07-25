# Botania compatibility

## Status

GitHub [#13](https://github.com/swear01/Magic_Storage/issues/13) has one safe server-side compatibility slice for Botania on NeoForge 1.21.1. Botania remains optional. Magic Storage loads its Botania-linked class only after `ModList` confirms `botania`; the normal absent-mod dedicated server does not link Botania classes.

The player dependency is not pinned to one Botania build. CI currently resolves official `vazkii.botania:botania-neoforge-1.21.1:455-SNAPSHOT` as representative evidence. Because the timestamped build is stored below the mutable `455-SNAPSHOT/` coordinate, `verifyBotaniaFixtureArtifact` requires the resolved jar to match build31 SHA-256:

```text
cfba1589f25d317b2a99b5b5c7b7a3966d5f18999535392d62fcf28b1f2b8908
```

If upstream moves the snapshot, the task fails with a named SHA mismatch instead of silently testing another build. Modrinth still has no Botania 1.21.1 release. The fixture excludes Botania's JEI and Patchouli transitive runtime dependencies, retains Curios, and uses Magic Storage's existing Patchouli. This keeps the server fixture independent from the player GUI support pack's EMI + TMRV contract.

## Mana storage

- `botania:mana` is a conditional, variantless resource kind backed by the Core's existing `long` ledger.
- It appears in the terminal's Other group only while Botania is loaded.
- Held transfer uses public `ManaItem.LOOKUP` on a private one-count copy.
- The first implementation accepts only a finite local Mana Tablet.
- Deposit and withdrawal require the observed post-mutation Mana delta and maximum to match exactly before the Core/cursor transaction may commit.
- Creative/infinite Mana Tablets, bound Mana Mirrors, and any item whose exact local delta cannot be proven are rejected.
- Passive transfer to a `ManaReceiver` is excluded because `receiveMana(int)` has no simulation operation.

## Deterministic recipe families

Each family requires one matching installed instant station:

| Station | Exact recipe family | Transaction details |
|---|---|---|
| Mana Pool | Mana Infusion | Input item, Mana, fixed output, and supported retained block catalyst |
| Runic Altar | Runic Altar | Consumed ingredients and reagent, retained catalysts, exact crafting remainders, Mana, output |
| Terrestrial Agglomeration Plate | Terrestrial Agglomeration | Ingredients, Mana, output |
| Petal Apothecary | Petal Apothecary | Ingredients, reagent, exactly 1000 stored water, output |
| Elven Gateway Core | Elven Trade | Exact inputs, Botania's runtime `MANA_COST`, and every fixed output |

All item, water, Mana, catalyst, remainder, and output deltas are part of one existing typed simulate-then-commit transaction. Elven Trade reads Botania's exposed runtime Mana cost and fails explicitly if that contract is unavailable; it does not assume a local fallback value. Output overflow, missing station, missing input, empty Mana Infusion output, or invalid plan leaves every resource unchanged. Plans above the shared nine-input bound and ingredient/state semantics that cannot be represented exactly fail closed.

## Excluded semantics

- Botanical Brewery is not in this slice. Its public recipe result is empty and `getOutput(ItemStack container)` depends on the runtime container, so one static typed plan cannot prove the output/remainder pair.
- Pure Daisy and Orechid families mutate world state; Orechid output is also random.
- Passive Mana Receiver Bus transfer has no simulation contract.
- Creative/infinite or externally bound Mana containers are not treated as local stored Mana.

## Verification

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew compileBotaniaFixtureJava
./gradlew runBotaniaGameTestServer
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  scripts.test_static_regressions.StaticRegressionTests.test_botania_mana_and_recipe_compat_is_optional_and_isolated
```

The Botania run requires `All 14 required tests passed` plus the current SelfTest summary. It loads Botania, Curios, and Patchouli without JEI. No new Magic Storage player-facing string is introduced in this slice: the existing localized Other group and Botania's own item names provide the UI labels.
