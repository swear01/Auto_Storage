# Advanced AE Compatibility

Auto Storage's Compat Kit review of Advanced AE `1.6.11-1.21.1`
accepts **zero production recipe families**. This is an evidence-backed
fail-closed result (outcome **C**), not an absent-mod fallback and not an empty
recipe adapter.

The present-mod module entrypoint loads only when `advanced_ae` is installed
and registers no stations or recipe families. Vanilla-class recipes that
Advanced AE ships under its namespace remain covered by Auto Storage's
built-in exact crafting/stonecutting/inscriber families without a custom
Advanced AE module. AE2 Inscriber coverage stays in the existing AE2 module.

## Reproducible audit evidence

- target: Advanced AE `1.6.11-1.21.1` (`advanced_ae`);
- Modrinth version ID `lHSZ2gYI` /
  `maven.modrinth:advancedae:lHSZ2gYI`;
- download URL used for local SHA verification:
  `https://cdn.modrinth.com/data/rxYaglEe/versions/lHSZ2gYI/AdvancedAE-1.6.11-1.21.1.jar`;
- CurseForge file `7849217` cross-check (same bytes):
  `https://mediafilez.forgecdn.net/files/7849/217/AdvancedAE-1.6.11-1.21.1.jar`;
- jar SHA-256:
  `891e1f8ee0f3ac1bbce03fc2848b761f9c52bea4533eb3419ae849582e15ced7`;
- jar SHA-1:
  `0af8033f7291b9f5062b229053e16b439a906db9`;
- official source: https://github.com/pedroksl/AdvancedAE
  tag `1.6.11-1.21.1-neoforge` /
  commit `9378212e1fc81930c1fe914c9d8a9a130b34e2b2`;
- jarJar-embedded companions: `ae2addonlib-1.0.3-1.21.1`,
  `ae2wtlib_api-19.2.5`;
- required external runtime for the fixture: AE2 `19.2.17`, GuideME
  `rduAfwb7`, GeckoLib `RVIo5f6E` (`4.8.2`);
- audit: `compat/audits/advanced_ae/1.6.11.json`;
- reviewed contract: `compat/contracts/advanced_ae.json`.

The committed audit uses current scanner format 16. It binds the complete
target class inventory, 104 jar recipe JSON files and their serializer/data
digest, the exact public signature and private-bytecode risks of the runtime
recipe candidate, and ten reachable ancestry artifacts. Nine non-platform
ancestry artifacts also carry exact dependency coordinates; the Minecraft /
NeoForge binary is the same normalized cross-host artifact used by the AE2
audit gate. Complete verification reopens the exact target jar and all ten
ancestry jars instead of trusting self-consistent JSON alone.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Advanced AE version on players and does not claim a multi-version
matrix.

## Audited recipe candidate and rejected surfaces

The legacy format-7 scan classified 12 name-shaped candidates. Current
format-16 structural and recipe-data evidence identifies only
`ReactionChamberRecipe` as an actual recipe candidate; it is rejected in the
committed contract. The other eleven entries are preserved below as reviewed
non-recipe surfaces, but are no longer contract families.

| Family | Result | Reason |
|---|---|---|
| `ReactionChamberRecipe` / `advanced_ae:reaction` | rejected | Live AE-network machine: tick energy uses floor/ceil power ratios, optional Applied Flux recharge, and grid idle-power partial progress; speed cards change requiredTicks from an unconfigured station; `IngredientStack` inputs may be tags; `GenericStack` output may be item or fluid; `isSpecial()` is true |
| `ReactionChamberRecipes` | rejected | Live `Level`/`RecipeManager` lookup helper that mutates temporary item aggregates while matching |
| Builder / serializer / `InitRecipe*` / nested `ToRegister` | rejected | Datagen or registry bootstrap, not independent families |
| EMI / REI category and display wrappers | rejected | Client viewer helpers; Auto Storage never registers third-party EMI workstations |
| `AAERecipeProvider` | rejected | Datagen provider only |

Typed resources were not introduced. Quantum Infusion fluid exists in Advanced
AE, but no deterministic Auto Storage transaction consumes or produces it
through a reviewed family. Chemical storage helpers in the AppMek plugin are
capability bridges, not recipe families.

## Future acceptance boundary

Support can be reconsidered only after a complete simulate-then-commit contract
can express exact integer FE (or AE→FE) and Processing work without depending on
AE network idle power, speed-card machine state, floating-point tick progress,
tag-only `IngredientStack` members without a verified exact-stack expansion, or
viewer/datagen authority.

## Verification

```bash
./gradlew runAdvancedAeGameTestServer
```

Eight present-mod GameTests prove the module registers no Advanced AE
stations/families, representative Reaction Chamber recipes stay unsupported,
and every loaded recipe in the audited `advanced_ae:reaction` type remains fail
closed. The descriptor-owned matrix also locks the SHA-256 inventory of all 85
`advanced_ae` recipes that actually enter the server `RecipeManager`, loads the
representative artifact with its reviewed runtime dependencies, and verifies
the same zero-family boundary without a shared hard-coded mod list.
