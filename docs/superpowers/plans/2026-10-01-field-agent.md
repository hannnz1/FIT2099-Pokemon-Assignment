# Original-map field agent implementation plan

> Execute with superpowers:executing-plans, under user's instruction to continue to completion. A fresh review will follow implementation.

**Goal:** Close original-map single-target natural-language capture/defeat flow.
**Architecture:** Extend validated TaskIntent compatibly; isolate field tool handlers; integrate existing room persistence and authoritative UI.
**Tech Stack:** Java8, existing AgentLoop/GameToolRegistry/navigation/native combat, OpenAI Responses, Phaser.
**Spec:** ../specs/2026-10-01-field-agent-design.md

## Constraints and review focus
Preserve old berry schemas/checkpoints; never execute during parsing; route restrictions check every tile; target IDs bound to confirmed goal; completed/captured/defeated semantics distinct; pause/reset/restart invalidate old decisions; no observation/completion turn increment; storage failure freezes actions; no credential output; no unsolicited inventory management.

- [x] Task1: failing schema/type/constraint tests, implement TaskIntent/Codec backward-compatible goal+target.
- [x] Task2: failing native field loop/area/deadline/forbidden attack tests, implement isolated FieldAgentTools and scenario integration.
- [x] Task3: failing room/confirm/no-side-effect/restore tests, persist kind/target and expose progress; compatibility with legacy save.
- [x] Task4: failing presentation tests, task-aware confirmation/result/progress and instructions.
- [x] Task5: full tests+realPG+Java8 build; independent review and repairs; browser/manual/live API acceptance; reports/runtime/source packaging.

## Ledger
Ruling: existing user authorization to finish V1 and the proposed field-AI extension permits inline implementation without another approval stop. No merge/publish performed. Existing local OPENAI_API_KEY reuse remains authorized; no secret files written.

Final ledger 2026-10-02: 280/280 real-PG Java tests; 61/61 client tests; standard Maven BUILD SUCCESS (six default PG skips separately passed); release8 production build, final-JAR real OpenAI three-flow acceptance, browser capture/collection/summon/restart proof, independent review repairs, checked runtime/source archives. See docs/v1-complete.md and packaged verification records.
