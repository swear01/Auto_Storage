# Create Enchantment Industry Compatibility

Auto Storage's Compat Kit review of Create Enchantment Industry
`2.5.0-preview-alpha1` accepts **zero production recipe families**. This is an
evidence-backed fail-closed result (outcome **C**), not an absent-mod fallback
and not an empty adapter claiming Grindstone, Printer, Blaze Enchanter, or
Forger support.

The present-mod module entrypoint loads only when `create_enchantment_industry`
is installed and registers no stations or recipe families. Vanilla crafting and
smithing JSON under the mod namespace remain covered by Auto Storage built-ins.
Create filling/emptying/cutting/crushing JSON that satisfies the existing Create
module contract continues to use that module.

## Reproducible audit evidence

- target: Create Enchantment Industry `2.5.0-preview-alpha1`
  (`create_enchantment_industry`);
- Modrinth version `8XedJhwv` /
  `maven.modrinth:create-enchantment-industry:8XedJhwv`;
- CurseForge file URL used by the ATM10 representative listing:
  `https://www.curseforge.com/minecraft/mc-mods/create-enchantment-industry/files/8241357`;
- download URL used for local SHA verification:
  `https://cdn.modrinth.com/data/JWGBpFUP/versions/8XedJhwv/create-enchantment-industry-2.5.0-preview-alpha1.jar`;
- jar SHA-256:
  `b25cc57696e6a26fef16437d88f3fe5a2690090e6f9728b6c72ca3e945cb07eb`;
- required runtime companions: Create via `${create_ci_version}` (`UjX6dr61`)
  and Create Dragons Plus `1.11.3`
  (`maven.modrinth:create-dragons-plus:LxesD770`);
- reviewed HTTPS repositories for audited compile ancestry resolution:
  Modrinth, `https://maven.createmod.net`,
  `https://maven.dragons.plus/releases`,
  `https://maven.shadowsoffire.dev/releases`, and
  `https://maven.blamejared.com` (discovered from upstream CEI
  `build.gradle`);
- official source branch `1.21.1/6.0.0-dev`, commit
  `37ec6810531895292d51d878ef8e7f7e8f83e992` (`2.5.0`);
- scanner format `17`: 378 target classes,
  519 target/reachable ancestry graph records,
  and 129 classified candidates across all buckets;
- 7 reachable ancestry artifacts: the normalized
  binary-pipeline NeoForge/Minecraft artifact plus
  6 exact Maven coordinates written into the bundled descriptor compile
  dependencies:
  `com.simibubi.create:create-1.21.1:6.0.10-280`,
  `dev.engine-room.flywheel:flywheel-neoforge-api-1.21.1:1.0.6`,
  `dev.shadowsoffire:ApothicEnchanting:1.21.1-1.5.3`,
  `mezz.jei:jei-1.21.1-common-api:19.21.2.313`,
  `net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1`,
  `plus.dragons.createdragonsplus:create-dragons-plus-1.21.1:1.11.3`;
- normalized NeoForge/Minecraft SHA-256/size:
  `2382ea29e50ff9deb46fa393d1e49c3a54b5d6273c252d0208d3fed903e8eb5f`
  / `56,279,815` bytes, matching the cross-host binary pattern used by the
  current AE2 audit rather than a host-recompiled jar;
- exact target-jar recipe-data inventory digest:
  `9f290f16714ff7c7cb4b5f020ecdab3375c73be2ea2ac3574f84126ddb3c91fc`;
- audit: `compat/audits/create_enchantment_industry/2.5.0-preview-alpha1.json`;
- reviewed contract: `compat/contracts/create_enchantment_industry.json`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Create Enchantment Industry version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

The legacy scanner-format-7 audit classified 28 name-shaped entries as recipe
families. Exact format-17 structural scanning removed 24 non-recipe entries and
left exactly four actual `Recipe` candidates. All four were reopened by
`migrate-contract`, reviewed again, and rejected in the committed contract:

| Candidate | Result | Reason |
|---|---|---|
| `GrindingRecipe` | rejected | Mechanical Grindstone+Drain is kinetic; duration uses stack-size modifiers and live tank free space; also disenchants enchanted items outside JSON recipes; format-17 risk includes randomness |
| `PrintingRecipe` | rejected | Printer execution is owned by live template `PrintingBehaviour` state, enchantment NBT, config, and random sound sampling |
| `InfusingRecipe` | rejected | Optional Apothic Enchanting Infuser mutates live Infuser/Basin inventories; live_machine_state / simulation_required |
| `SalvagingRecipe` | rejected | Optional Apotheosis fan/world salvaging outside installed-station scope |

Blaze Enchanter, Classic Blaze Enchanter, and Blaze Forger are not recipe-class
candidates. Their runtime contracts use scroll-level settings, templates, and
`RandomSource` enchantment selection, so they remain outside Auto Storage
installed-station scope.

Typed resources: experience fluid (`create_enchantment_industry:experience`) is
present as a NeoForge fluid and is **not** registered as an Auto Storage resource
kind. Absence of a custom CEI kind is intentional until a simulate-then-commit
XP contract exists.

## Declarative matrix evidence

The module descriptor and reviewed contract both declare the target mod present
with zero descriptors, resource kinds, accepted recipes, rejected registry IDs,
or CEI-owned recipe families. The isolated CEI fixture locks the 22 successfully
loaded `create_enchantment_industry:*` recipes by SHA-256
`967c0ea0c4be92858443d12f2e60a02dd7b00dbf4905e4b3634e2bf83c8cdbdf`.

No shared workflow or matrix Java list is extended for this module.

## Future acceptance boundary

Support can be reconsidered only after a generic contract can express:

- kinetic Grindstone duration without live RPM/tank approximation;
- exact disenchant-versus-recipe paths;
- Printer template behaviours without retained filter/tank state;
- non-random Enchanter/Forger outputs with exact experience fluid costs.

Until then, no Create Enchantment Industry-only approximation is allowed.

## Verification

```bash
./gradlew runCreateEnchantmentIndustryGameTestServer
tools/compat-kit/compat-kit verify \
  compat/contracts/create_enchantment_industry.json \
  --bundled \
  --audit compat/audits/create_enchantment_industry/2.5.0-preview-alpha1.json \
  --jar build/compat-kit/artifacts/create-enchantment-industry-2.5.0-preview-alpha1.jar \
  --classpath <each of the 7 reachable ancestry jars>
```

Five present-mod GameTests prove the module registers no CEI stations/families
or experience kind, representative vanilla crafting under the CEI namespace stays
supported, grinding recipes remain unsupported, every loaded grinding-type
recipe stays fail closed, and the isolated recipe-inventory digest matches the
descriptor. Full bundled Compat Kit verification must pass all twelve checks
across `build`, base GameTests, recipe-addon GameTests, the CEI fixture, and
the compatibility matrix.
