# Pokemon Follow Implementation Plan

**Goal:** Optional automatic following for summoned Treecko, with complete atomic world restore.
**Architecture:** Collection deployment generations own Actor existence; world checkpoints own matching current placement and follow state.
**Tech:** Java8, existing engine/navigation, JS browser modules; inline execution, no commit requested.
**Spec:** ../specs/2026-10-01-pokemon-follow-design.md

- [x] HTTP tests first: FOLLOW unsupported, opt-in turn/resource invariants, actual one-exit occupancy movement, stationary stop, repeated commands.
- [x] Collection deployment generation and validation. Scene route-follow and state APIs. AgentRoom projection restore/reconcile and guarded FOLLOW command; checkpoint optional projection; server actual public collection positions.
- [x] Persistence/fault/security/legacy/blocked route tests, including resummon generation and cross-room Actor reload. Real PostgreSQL opt-in.
- [x] Client tests first, follow controls/detail. Full Java/PostgreSQL/client tests and fresh review.
- [x] Java8 runtime build, packaged browser follow/stop/restart; offline acceptance script, usage/progress, runtime/source archives and archive checks.

Ledger: continuous authorization carries from user; implement inline in existing checkout to preserve all current changes. No API calls required. Position ownership intentionally moves from static collection anchor to generation-matched quest projection to keep follower position, Mudkip/NPC positions and turn in one atomic checkpoint.

Ledger outcome: baseline FOLLOW state test failed (missing state); implemented real follow. Client three new tests first failed then passed. Area restriction test initially lacked AREA_RESTRICTED flag, fixed setup against TaskIntent contract; no product change. Reviewer found no important defect; recommended detour test added and passed. Full Java+PostgreSQL 243/243; client42/42; Java8 build; packaged browser restart+continued follow; offline HTTP script passed. No real API calls or secrets in deliverables.
