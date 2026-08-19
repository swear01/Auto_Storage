# Productive Metalworks Compatibility

Auto Storage's Compat Kit review of Productive Metalworks `1.21.1-1.15.0`
accepts the deterministic **Foundry melting** and **casting** families and
keeps the fluid-alloying, entity-melting, and mold-reuse boundaries
fail-closed.

## Reproducible audit evidence

- target: Productive Metalworks `1.21.1-1.15.0` (`productivemetalworks`);
- CurseForge file `7884786` / Curse Maven
  `curse.maven:productivemetalworks-1184570:7884786`;
- download URL used for local SHA verification:
  `https://mediafilez.forgecdn.net/files/7884/786/productivemetalworks-1.21.1-1.15.0.jar`;
- jar SHA-256:
  `1dcf9e10fc457c92d9ed466336104927169817cd509ca9ca69dec734f994d124`;
- official source: https://github.com/JDKDigital/productivemetalworks
  commit `7c6483c51e1a9def633a939ea75e0018dd079ffa` (`mod_version=1.21.1-1.15.0`);
- audit: `compat/audits/productivemetalworks/1.15.0.json`;
- reviewed contract: `compat/contracts/productivemetalworks.json`.

The scanner-format-17 audit binds 103 target classes, the exact 1,118-recipe
data inventory, and nine reachable non-JDK ancestry artifacts.

This version is representative CI/audit evidence. Auto Storage does not impose
an exact Productive Metalworks version on players and does not claim a
multi-version matrix.

## Audited recipe candidates

| Family | Result | Reason |
|---|---|---|
| Item melting (`productivemetalworks:item_melting`, 46 recipes) | accepted | one item melts into one or more exact fluid results (39 single, 6 double, 1 triple); work = sum of result mb (the block entity ticks `getTimeInSlot` = total result amount); temperature is a fuel/coil condition abstracted by the installed foundry controller station |
| Item casting (`productivemetalworks:item_casting`) | accepted | consumed cast + exact fluid → exact item; `consume_cast=true` only (molds that are reused per craft are not modeled); cooling abstracted as 1000 ticks station work |
| Block casting (`productivemetalworks:block_casting`) | accepted | same contract as item casting for the basin; 18/19 recipes qualify (`consume_cast=true`) |
| Fluid alloying | rejected | fluid-tag inputs depend on external mods (c:molten_*), the fixture world has none live, and alloying executes against live tank contents with `speed` modifiers |
| Entity melting | rejected | consumes live entities; entity/world mutation |
| `ICastingRecipe` | rejected | abstract shared interface; the concrete families above are the runtime surface |

## Stations

- `auto_storage:productivemetalworks_foundry` — PROCESS station installable with
  the `productivemetalworks:gray_foundry_controller` block item (built-in
  rendering, one work/tick); melting runs here.
- `auto_storage:productivemetalworks_casting_table` — PROCESS station
  installable only with the `casting_table` block item; item casting runs here.
- `auto_storage:productivemetalworks_casting_basin` — PROCESS station
  installable only with the `casting_basin` block item; block casting runs here.

## CI policy

`runProductivemetalworksGameTestServer` runs eight isolated tests covering the
descriptor inventory digest, conditional registration, melting support and
multi-fluid execution (clock → 360 mb molten gold + 100 mb molten redstone),
basin casting support and execution (fluid + cast → capacitor), mold/alloying/
entity fail-closed boundaries, and per-type contract coverage of every live
recipe. The isolated inventory digest locks the 581 unconditional
`productivemetalworks:*` recipes that load without the conditional mods
(Create, Mekanism, AllTheOres, etc.).
