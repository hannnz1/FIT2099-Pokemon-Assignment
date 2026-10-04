# Pokémon V1 Completion Implementation Plan

> **For agentic workers:** Use executing-plans with independent focused parallel agents and final independent review. Existing checkout is reused because it holds prior authorized untracked development.

**Goal:** Complete and verify all remaining V1 game, persistence, constraints and trace features.

**Architecture:** Extend the existing authoritative scene adapters; keep provider workers immutable and serialize world actions. Persistence stores bounded trusted checkpoints, never executable model content; restoration invalidates work and approvals.

**Tech Stack:** Java 8, original FIT2099 engine, JDK HTTP/JDBC, PostgreSQL JDBC runtime driver, browser modules, JUnit/Node.

**Spec:** ../specs/2026-09-30-v1-completion-design.md and the original pokemon-agent-demo-v1-spec-gemini-free-tier.md.

## Global Constraints

- Default gpt-6-luna; reuse existing server key; no credential or approval-token exposure.
- Java 8, legacy gameplay intact, no arbitrary shell/code/tool execution.
- Real engine state is authoritative; no fixed route imposed on live models.
- Bound operations, memories, snapshots and retries; stale work cannot commit after takeover/restart.
- Preserve existing untracked changes; no automatic commits of previous work or destructive checkout cleanup.

## Review Focus

- Restriction path crosses forbidden intermediate tiles: test route predicate at every step.
- Deadline expires during movement or after pause: test absolute active world turns.
- Restoration replays approval/late decision: fresh lifecycle and clear token tests.
- Persistence writes fail after world action: fail closed until reload, test storage outage state.
- NPC/trace data includes token or private context: public and saved checkpoint redaction tests.

### Task 1: Constraints

Files: TaskIntent/TaskIntentCodec, new ConstraintPolicy, BerryQuestSession; new focused tests. Do not edit scenario/room/UI.
Interfaces: TaskIntent.getAreaId(): String nullable; getDeadlineTurn(): Long nullable. ConstraintPolicy(TaskIntent, Map<String,Set<Location>>, long questDeadline), allows(Location), deadlineTurn(). BerryQuestSession.configureDeadline(long) before start; exportState()/restoreState(Map<String,Object>) for trusted checkpoint, restoring through validated game fields and invalidating approvals (coordinate with root).
- [x] Write failing mapping, malformed params, intermediate tile restriction, late deliver tests; run targeted runner.
- [x] Implement typed schema/backward decoder and server checks.
- [x] Verify targeted suite; report interfaces and evidence.

### Task 2: Trace and evaluation

Files: AgentLoop, trace package, gateway usage handling if needed; new tests. Do not edit scene/room.
Interfaces: AgentLoop.configureTrace(String agentId,String provider,String model); getStepTrace(): List<Map<String,Object>>; getMetrics(): Map<String,Object>. Existing tick API intact.
- [x] Failing tests for executed tool arguments/name, rejected/stale/provider errors, redacted tokens, nonnegative latency, aggregate denominator.
- [x] Add bounded structured trace at actual world commit and provider completion; available usage only.
- [x] Verify targeted tests and report contract.

### Task 3: Persistence repository

Files: persistence package, SQL/docs for store; isolated tests. Do not edit room/scene/session/loop/pom.
Interfaces: WorldStore extends AutoCloseable: save(String owner, Map<String,Object> checkpoint), load(String owner) returns nullable Map, close(). FileWorldStore(Path directory); JdbcWorldStore(connection factory), StoreException finite code. Reject oversized/invalid checkpoint, parameterized SQL, transaction/rollback, owner isolation. Schema only prefixed pokemon_agent_* tables. Driver loaded externally; do not modify user DB or expose secrets.
- [x] Failing tests for bounded atomic roundtrip, owner isolation, malformed file, transaction rollback.
- [x] Implement file/JDBC stores and deployment instructions.
- [x] Verify tests and isolated real DB when root supplies connection.

### Task 4: World, manual control and integration (root)

Files: GeminiQuestScenario, new V1WorldFactory/WorldClock, AgentRoom/WebServer, Application opt-in, client UI, checkpoint adapters.
- [x] Add failing manual ownership, true world tick, original terrain and NIGHT, checkpoint restore tests.
- [x] Integrate task 1 restrictions and task 2 trace; implement manual movement/pickup/deliver/wait through same trusted rules.
- [x] Add per-world clock and engine map tick/NPC wandering; provider and approval wait freeze clock.
- [x] Export/restore bounded actual world checkpoint with immutable owner scope; restart task paused/fresh identity and no replay; persist command/step outcomes before exposing success.
- [x] Connect task 3 store with stable opaque cookie identity and environment configuration; expose saved/recovered/storage-error state safely.
- [x] Add Chinese manual/constraint/time/metrics UI; use arbitrary map dimensions from server, retain compact test scene.

### Task 5: Acceptance and deliverables

- [x] Run full Java and client tests plus Java 8 compilation.
- [x] Real isolated PostgreSQL roundtrip/restart/isolation; real model and browser manual takeover/restore proof.
- [x] Independent code review, fix important issues with regression evidence.
- [x] AC-01–08 matrix and actual full demo evidence; update progress honestly, package jar/source/licenses and screenshots.

## Ledger

2026-09-30: design and plan recorded; execute under user's explicit instruction to continue through V1 completion without repeated approval gates.

2026-09-30: Completed implementation and independent review; see ../../v1-acceptance.md. Original-map peaceful V1 uses a per-world 60-turn day and training HP 1000; legacy gameplay remains separate. OpenAI default follows user override; Gemini live testing and local Maven remain unverified. File default and real PostgreSQL are both verified.
