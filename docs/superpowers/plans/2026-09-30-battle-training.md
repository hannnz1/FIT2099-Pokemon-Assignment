# Battle training implementation plan

> Use executing-plans in this session, then verify completion. Existing checkout contains authorized untracked development and is reused.

**Goal:** A playable Java combat/capture arena with trusted tools and safe recovery.
**Architecture:** CombatSession adapts original engine actions; CombatTools registers strict tools; BattleTrainingScenario owns the scene/loop/checkpoints; BattleTrainingConsole exposes manual, explicit offline demo and live modes.
**Tech:** Java8, existing AgentLoop/WorldStore/OpenAiGateway, JUnit.
**Spec:** ../specs/2026-09-30-battle-training-design.md

## Global constraints
Actual engine state determines captures; no generated code execution. Legacy and V1 peaceful scene unchanged. Captures use original allowableActions and unlimited Pokeballs; no invented HP threshold. Live mode never falls back to fixtures.

## Review focus
Removed targets cannot capture twice; duplicate attacks cannot damage twice; paused/late model cannot commit; target and actor outside allowed area cannot fight; inconsistent captured/defeated checkpoint rejected.

- [x] Test first: CombatTrainingTest actual capture/ball, no-battle, adjacency/type, attack retaliation/idempotency, takeover and checkpoint roundtrip/rejection.
- [x] Implement CombatSession and CombatTools, BattleTrainingScenario with restore paused and actual-ball goal.
- [x] Implement playable BattleTrainingConsole modes and store save/load; explicit offline demo.
- [x] Run full Java tests and Java8 compilation; packaged offline/manual smoke; review guards/restore.
- [x] Update stage progress/run instructions and deliver training jar without replacing V1 artifact.
Ledger: 182/182 full Java including PostgreSQL; Java8 production compile; packaged offline demo plus real OpenAI capture and manual restart smoke passed. Independent review found constraint/lifecycle recovery bugs, fixed with RED/GREEN regressions and scoped review clear. --new supports explicit new episodes. V1 browser remains unchanged; this stage deliberately ships a standalone arena before browser/task-parser integration. No commits made for prior untracked work.
