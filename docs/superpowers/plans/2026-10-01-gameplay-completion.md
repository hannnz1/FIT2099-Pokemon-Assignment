# Gameplay Completion Implementation Plan

**Goal:** Finish the three remaining single-player gameplay extensions and combined delivery.
**Architecture:** WildEncounter owns native map/trainer target Actors, PokemonSpecies encodes portable catalog; AgentRoom gates and journals world changes. Collection remains receipt/ownership authority. Deployed health is generation-matched world state.
**Tech:** Java8, existing WorldStore/GameMap/CombatSession/NavigationService and browser modules.
**Spec:** ../specs/2026-10-01-gameplay-completion-design.md

- [x] Red HTTP tests for EXPLORE, native wild Mudkip capture/store, selectable owned battle partner.
- [x] PokemonSpecies registry and collection generic species/heal; preserve legacy validation.
- [x] WildEncounter native target spawning/actions/checkpoints/stable capture receipts; optional protected CombatSession constructor.
- [x] Scene wild/partner/rest APIs and deployed HP restore. AgentRoom commands, reconciliation, guards and complete checkpoint. Public actual owned species/HP and field states.
- [x] Failure/restart/HP/native battle/legacy/constraint/security tests; client tests and complete Chinese UI.
- [x] Full Java/PostgreSQL/client, Java8 build, final review and packaged browser gameplay/restart; repeatable offline script, docs and final runtime/source archives.

Ledger: user explicitly requests continue until complete; execute inline continuously in existing checkout preserving all uncommitted work. Scope derived from preceding three-item list; optional clarification sent, no response yet, proceeding with stated scope. No real API change/calls. Battle partner switches combat controls only, quest still uses Mudkip. Do not declare all V2 complete.

Final evidence: Java259/259 PostgreSQL, client46/46, release8 build, independent final review, packaged browser capture/store/summon/switch/move/battle/restart/rest and HTTP script passed. Archives prepared and verified in outputs.
