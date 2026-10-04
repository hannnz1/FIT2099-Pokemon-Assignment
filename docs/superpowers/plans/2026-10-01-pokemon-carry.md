# Pokemon Carry Implementation Plan

> Execute inline using test-driven development; preserve uncommitted work, no commit required.

**Goal:** Bring actually captured training Treecko into the original-map player's durable Pokeball backpack.

**Architecture:** One owner-scoped collection checkpoint commits ownership; room state is reconciled from its receipts. Scene reset never resets collection. Existing world executor serializes transfers and model actions.

**Tech Stack:** Java 8, existing WorldStore, HTTP server, browser JavaScript.

**Spec:** ../specs/2026-10-01-pokemon-carry-design.md

## Tasks

- [x] Add failing real HTTP tests in `test/game/agent/PokemonCarryTest.java`: TRANSFER acceptance, bag snapshots, reset/restart, isolated owner and failures. Run them against current code; expect UNKNOWN_COMMAND.
- [x] Add `web/PokemonCollection.java`: trusted capture records, real Player/Pokeball inventory, single checkpoint commit, load/validate, receipts and capacity20. Interface receive(arenaId, Pokeball), snapshot(), receivedIds(), recover(). Failure must not publish memory ownership until durable success/reload.
- [x] Extend `BattleTrainingScenario`: stable arenaId, captured-ball lookup, remove/reconcile transfer, terminal completion history, optional old-schema fields. Persist TRANSFERRED targets without source ball.
- [x] Extend BattleRoom with owner-trusted collection dependency and TRANSFER target validation inside normal command guards/cache. Reconcile immediately before snapshots, actions and recover. Server initializes collection with each session, enriches snapshots and keeps memory sessions isolated.
- [x] Add browser button and inventory presentation; unit-test empty/received/inventory states and guards, run all client tests. Verify in actual browser.
- [x] Full Java suite including PostgreSQL, Java8 production build, reviewer, source/runtime packages, docs and user progress report. Include repeatable no-model acceptance.

## Review focus

- Ambiguous collection commit then thrown error: reload authority before source can move.
- Source write failure after successful collection commit: restart removes stale source ball.
- Repeated transfer after restore/reset: old individual cannot duplicate; new arena can legitimately produce new capture.
- AI, unselected tabs, stale identities, malicious target and another cookie cannot move assets.
- Invalid/full collection never consumes a source ball; fields bounded and preserve original quest capacity.

Verification: Java219/219 with isolated PostgreSQL, client36/36, Java8 production build, independent review clear, packaged browser and restart checked. Runtime/source archives are validated by the final packaging step; no commits, key copying or live model calls.
