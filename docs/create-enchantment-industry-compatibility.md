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
- required runtime companions: Create `6.0.10` (`maven.modrinth:create:UjX6dr61`)
  and Create Dragons Plus `1.11.3`
  (`maven.modrinth:create-dragons-plus:LxesD770`);
- official source branch `1.21.1/6.0.0-dev`, commit
  `37ec6810531895292d51d878ef8e7f7e8f83e992` (`2.5.0`);
- audit: `compat/audits/create_enchantment_industry/2.5.0-preview-alpha1.json`;
- reviewed contract: `compat/contracts/create_enchantment_industry.json`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Create Enchantment Industry version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

Compat Kit enumerated 28 recipe-class candidates. Every candidate is rejected in
the committed contract. The important server families and false positives are:

| Candidate | Result | Reason |
|---|---|---|
| `GrindingRecipe` | rejected | Mechanical Grindstone+Drain is kinetic; duration uses stack-size modifiers and live tank free space; also disenchants enchanted items outside JSON recipes |
| `PrintingRecipe` | rejected | Printer execution is owned by live template `PrintingBehaviour` state, enchantment NBT, config multipliers, and random sound sampling |
| `RecipePrintingBehaviour` | rejected | Live block-entity behaviour, not a `RecipeType` |
| `PrintingRecipe$Builder` / `$Serializer` / `PrintingRecipeParams` | rejected | Builder/serializer/params helpers |
| `CEIRecipes` | rejected | Registry bootstrap only |
| `CEIRecipeProvider` | rejected | Datagen only |
| `InfusingRecipe` (+ builder/serializer/params) | rejected | Optional Apothic Enchanting Infuser mutates live Infuser/Basin inventories |
| `CEIARecipes` / `CEIARecipeProvider` | rejected | Optional Apothic registry/datagen |
| `SalvagingRecipe` / `CEIAXRecipes` / `CEIAXRecipeProvider` | rejected | Optional Apotheosis fan/world salvaging and helpers |
| All `*PrintingRecipeJEI*` / `PrintingRecipeJEI` / `$Type` | rejected | Client JEI display helpers; never server authority |
| `CreateRecipeCategoryAccessor` | rejected | JEI mixin accessor |

Blaze Enchanter, Classic Blaze Enchanter, and Blaze Forger are not recipe-class
candidates. Their runtime contracts use scroll-level settings, templates, and
`RandomSource` enchantment selection, so they remain outside Auto Storage
installed-station scope.

Typed resources: experience fluid (`create_enchantment_industry:experience`) is
present as a NeoForge fluid and is **not** registered as an Auto Storage resource
kind. Absence of a custom CEI kind is intentional until a simulate-then-commit
XP contract exists.

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
```

Five present-mod GameTests prove the module registers no CEI stations/families
or experience kind, representative vanilla crafting under the CEI namespace stays
supported, grinding recipes remain unsupported, and every loaded grinding-type
recipe stays fail closed. The all-mod compatibility matrix also loads the
representative CEI artifact plus Create Dragons Plus and locks the same
zero-family boundary.
