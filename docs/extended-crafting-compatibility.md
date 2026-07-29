# Extended Crafting Compatibility

Auto Storage conditionally loads this adapter only when Extended Crafting is present.

## Supported

- Exact `ShapedTableRecipe` values from the `extendedcrafting:table` recipe type.
- Exact `UltimateSingularityRecipe` values, including every component-bearing Singularity input
  currently enabled by Extended Crafting and loaded material tags.
- Table sizes from 3×3 through the Ultimate Table's 9×9 boundary.
- One installed Ultimate Crafting Table unlocks the family as an Instant Station.
- Up to 81 occupied positions or distinct exact input groups are preserved. The native summary
  remains 3×3 while EMI/TMRV owns the full diagram; the Available/Required ledger pages through
  every exact input without compressing rows.
- Alternative items, exact counts, crafting remainders, output components, destination capacity,
  and rollback use the normal server-owned simulate-then-commit transaction.
- EMI/TMRV remains responsible for the full visual 9×9 recipe diagram and workstation metadata.
- Before the server Craftable catalog is prewarmed, Auto Storage reloads Extended Crafting's
  public Singularity registry after item tags are available. This prevents tag-backed
  Singularities from remaining cached as empty on the server while EMI sees them on the client.

## Fail-closed boundaries

- Custom Extended Crafting recipe transformers are rejected because their runtime remainder
  mutation is not represented by the current exact plan API.
- More than 81 exact input groups are rejected.
- Combination Crafting, Ender Crafting, Flux Crafting, non-Ultimate Singularities, and external-machine
  execution are not inferred from generic recipe data.

## Verification

CI uses Extended Crafting 7.0.8 (`Pb2OHQ8E`) with Cucumber 8.0.16 (`8421rqFF`) as one
reproducible representative fixture. These artifacts do not pin player dependency versions;
other compatible versions are accepted and incompatibilities are handled from user reports.

`runExtendedCraftingGameTestServer` runs four isolated tests covering station gating, the
full 9×9/81-position exact commit, one-short atomic no-op, full-destination rollback, and the
real Ultimate Singularity's component-exact preview/commit. The fixture supplies test-only
values for the ten default common-ingot tags so this case must expose more than nine inputs,
all using one item ID with distinct `singularity_id` components.
`runCompatibilityMatrixGameTestServer` also loads Extended Crafting with the other eleven
representative optional mods and verifies that the recipe preserves every Singularity enabled by
that combined tag set.

The `crafting-fuel-page` Prism scenario preloads an Ultimate Crafting Table and one of each
of Extended Crafting's 19 default Singularity variants in the production repository format.
Its scenario-owned datapack supplies the same test-only common-ingot tag values; these values are
not shipped as Auto Storage gameplay data.
Fullscreen validation selects the real Ultimate Singularity, requires the complete EMI/TMRV 9×9
public widget instead of the native 3×3 summary, checks inline `1/1` for every material, and
wheels through the complete readable ledger.
