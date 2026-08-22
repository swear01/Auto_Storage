# Terminal Repository Synchronization Design

## Goal

Remove terminal scroll-induced server work while preserving server-authoritative storage, typed resources, crafting validation, and existing GUI behavior. The client may keep and optimistically update a disposable read-only view cache, but no client value authorizes a storage mutation.

## Architecture

`StorageTerminalMenu` owns an authoritative server-side repository mapping. Each visible resource has a stable per-menu serial mapped to its exact `StorageResourceKey`. The server sends an initial full repository and subsequent deltas for amount, pending amount, display stack, craftable state, and removal. Full updates replace the client repository; deltas refer to an existing serial or carry the key for a new serial.

The client menu creates a `TerminalClientRepository` before the first update can arrive. It stores only presentation entries and local ordering state. Client filtering, sorting, visible-row calculation, and scrolling operate on this repository. Scroll wheel, track, and thumb interaction never send a server scroll packet and never invoke recipe catalog work.

Display slots become client virtual slots backed by repository rows. They retain the existing menu indices so player, fuel, machine, and presentation slots keep their wire parity. Existing terminal rendering, amount formatting, typed-resource frames, tooltips, scissoring, and layout geometry continue to consume `Slot#getItem`.

## Authoritative actions and optimistic state

Client actions identify a repository serial, not a slot index or client `ItemStack`. The server validates container identity, access, current page, serial ownership, exact resource key, current amount, resource view, and action type before re-resolving the current Core state and applying the existing transaction. Typed display representatives are never treated as extractable items.

An optional optimistic client update may change only the local presentation entry while a request is in flight. The server response is the authority: accepted mutations arrive as repository deltas, rejected or stale actions trigger a full repository refresh or restore the last server entry. Repository sequence/epoch checks reject stale deltas and request a full resync rather than applying an ambiguous update.

## View and invalidation rules

Storage view synchronizes all eligible registered resource keys and performs search/resource-view/sort locally. Craftable view synchronizes the server-validated craftable output set without the current search/resource-view restriction; the client applies those filters locally. Craftable catalog construction remains server-only and is invalidated only by storage, machine, station, energy-threshold, recipe, or player-source changes. Page changes and `usePlayerInventory` changes request the appropriate full view; scroll does not.

The old absolute `TerminalScrollPacket` path is removed from normal terminal scrolling. Server `scrollOffset` and visible display inventory are no longer used as the source of terminal grid state. Existing server menu and data-slot parity for non-grid controls remains unchanged.

## Failure handling

- A packet with an unknown container, wrong menu type, invalid sequence, malformed key, or excessive entry count is rejected.
- A delta for an unknown serial requests a full snapshot instead of guessing its key.
- A stale serial action is rejected without storage mutation.
- A disconnected or invalid Core clears the client repository and disables grid actions.
- Server mutations remain simulate-then-commit and can correct any optimistic presentation on the next delta.

## Tests

Add focused tests for repository full replacement, delta amount/removal, serial identity across local sort/scroll, stale serial rejection, malformed/unknown delta resync, and the invariant that scroll changes do not call `refreshDisplayItemsFiltered` or send `TerminalScrollPacket`. Extend terminal GameTests/static regressions for local scroll, typed-resource action validation, Craftable cache reuse, and full repository recovery after a server-side change.

## Non-goals

This change does not move Core reads, recipe classification, crafting planning, or mutation authority to the client; does not add a third-party dependency; does not change storage persistence; and does not redesign the recipe diagram or utility pages.
