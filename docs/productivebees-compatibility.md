# Productive Bees Compatibility

Auto Storage's Compat Kit review of Productive Bees `1.21.1-13.13.5`
accepts **zero production recipe families**. This is an evidence-backed
fail-closed result (outcome **C**), not an absent-mod fallback and not an empty
recipe adapter.

The present-mod module entrypoint loads only when `productivebees` is installed
and registers no stations or recipe families. Vanilla-class recipes that
Productive Bees ships under its namespace remain covered by Auto Storage's
built-in exact crafting/smelting families without a custom module.

## Reproducible audit evidence

- target: Productive Bees `1.21.1-13.13.5` (`productivebees`);
- CurseForge file `8022994` / Curse Maven
  `curse.maven:productivebees-377897:8022994`;
- download URL used for local SHA verification:
  `https://mediafilez.forgecdn.net/files/8022/994/productivebees-1.21.1-13.13.5.jar`;
- jar SHA-256:
  `9d48d198bc6eacf3b7729f4d60b91e661cfa15d105264ba225dee87b1d547ba1`;
- ATM10 modlist SHA-1 cross-check:
  `31f7bb1567fdbb508db133f5b094d5d0d293030d`;
- official source: https://github.com/JDKDigital/productive-bees
  commit `3c818315d67abc16801626ce292bb207a7383f06` (`mod_version=1.21.1-13.13.5`);
- required companion `productivelib` is jarJar-embedded in the representative
  artifact (`META-INF/jarjar/productivelib-1.21.1-0.2.0.jar`);
- audit: `compat/audits/productivebees/13.13.5.json`;
- reviewed contract: `compat/contracts/productivebees.json`.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Productive Bees version on players and does not claim a multi-version
matrix. Modrinth currently lists `1.21.1-13.13.0` for NeoForge; ATM10 and
CurseForge carry `13.13.5`, which is the audited fixture.

## Audited recipe candidates

Compat Kit enumerated 71 recipe-class candidates. Every candidate is rejected
in the committed contract. The runtime families behind that inventory are:

| Family | Result | Reason |
|---|---|---|
| Centrifuge / heated centrifuge | rejected | `ChancedOutput` rolls via `RandomSource`; upgrades change productivity/stability and energy drain |
| Advanced beehive produce | rejected | bee identity plus chanced outputs and live hive upgrade modifiers |
| Bottler | rejected | fluid fill entangled with piston+bee kill gene-bottle path, `FluidUtil` capability fill fallback, and `HAS_BOTTLE` block-state mutation; no recipe-declared work/energy |
| Incubation | rejected | hard-coded egg/cage/gene paths with purity `RandomSource` rolls, energy, upgrades, and live `BeeCage` capture |
| Bee breeding / conversion / fishing / spawning / NBT changer | rejected | live bee/entity/world mutation and/or chance |
| Block / item conversion | rejected | chance-gated bee pollination that mutates world/items |
| Gene treat / combine gene / configurable comb recipes | rejected | special crafting over gene/bee_type NBT rather than installed-station transactions |
| EMI / JEI / datagen / helpers / serializers | rejected | not independent deterministic Auto Storage recipe families |

Typed resources were not introduced. Chance outputs and entity/world/live-machine
paths cannot be reduced to a simulate-then-commit plan without approximating
those conditions away.

## Future acceptance boundary

Support can be reconsidered only after a generic contract can express guaranteed
deterministic output subsets without `RandomSource`, isolate bottler fluid/item
fills from entity-kill and capability-fill side paths, and model incubator
gene/cage semantics without live entity capture or purity rolls.

## Verification

```bash
./gradlew runProductivebeesGameTestServer
```

Eight present-mod GameTests prove the module registers no Productive Bees
stations/families, representative centrifuge/bottler/beehive/breeding/conversion/
block-conversion recipes stay unsupported, and every loaded recipe in each
audited custom recipe type remains fail closed. The all-mod compatibility
matrix also loads the representative artifact and locks the same zero-family
boundary.
