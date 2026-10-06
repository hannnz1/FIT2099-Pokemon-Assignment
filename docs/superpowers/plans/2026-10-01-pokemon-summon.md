# Pokemon Summon Implementation Plan

**Goal:** Summon and recall actual captured Treecko on the original quest map.
**Architecture:** Collection deployment metadata is the single owner authority; AgentRoom materializes its actual Actor on GeminiQuestScenario map. Management does not advance world time. Resets recall before replacing map.
**Spec:** ../specs/2026-10-01-pokemon-summon-design.md
**Tech:** Java8, existing WorldStore and GameMap, browser modules. Inline execution, preserve all existing work, no commit requested.

- [x] Add failing real HTTP tests in `PokemonSummonTest.java`, using existing carry helpers, show SUMMON unsupported before implementation.
- [x] Extend `PokemonCollection`: optional deployment validation/encode, getBall, summon/recall single commit, actual trainer inventory projection, stable old collection compatibility.
- [x] Extend `GeminiQuestScenario`: validate adjacent summon point, reconcile actual Actor and expose actual position, preserve independent NPC. Extend AgentRoom commands SUMMON(captureId,direction), RECALL(captureId), guards, trace, reset and recovery; server passes session collection and synchronizes before mutation/snapshot/tick.
- [x] Add client presentation tests first, select individual and direction, summon/recall controls, original map overlay and deployed/in-ball labels.
- [x] Full Java/PG/client tests, Java8 build, independent review; offline HTTP and browser capture/carry/summon/recall/restart. Package runtime/source, usage and progress docs.

Review focus: ambiguous commit, ghost Actor after another room reloads collection, occupied restore, reset/save partial failure, double inventory representation, late model path into occupied summon, stale/wrong-owner commands.

