# V1 completion design

User intent: finish the existing Pokémon Agent V1, preserving Java 8, real engine authority, cheap default OpenAI gpt-6-luna and optional Gemini. The original spec remains binding except the user's provider override. No fake live model success, no broad AI battle or multi-agent features.

## Components

1. Structured AREA_RESTRICTED and DEADLINE parameters. Areas are server-owned catalogs of allowed tiles, not player code. Deadline is an absolute world action turn no later than quest NIGHT. Each movement step checks the area and each mutating quest action checks time. Existing two-constraint outputs remain readable, extended model output includes nullable areaId/deadlineTurn.
2. World/manual integration. Reuse the original Application map layout and original engine actors/grounds in an opt-in V1 world. A per-world DAY/NIGHT clock prevents unrelated browser sessions sharing a singleton. Seed real Berry resources near the laboratory with a real stale Treecko observation. Only actual actions advance the world; waiting on provider, pause and approvals do not. A trusted manual controller uses the same game rules and is rejected while AI owns control. Legacy Application gets an explicit V1 entry option; legacy game remains playable.
3. Complete step trace/evaluation. Each submitted tool step records IDs, name, sanitized arguments, validation/action results, before/after state, provider/model, epoch time and model latency/available usage. Never store approval tokens/keys or hidden reasoning. Aggregate completion, step, invalid-call, replan and approval statistics.
4. Transactional PostgreSQL storage and recovery. Store versioned world checkpoints (actual tiles, actors, inventory, quest/economy/time, NPC memories, bounded trace) scoped by opaque browser owner. One transaction persists checkpoint and related memory/trace state. Active tasks restore PAUSED with fresh task/action identities; approvals are invalidated; completed quests remain completed. A local atomic-file implementation supports development fallback, clearly distinguished from PostgreSQL. Database failure prevents further mutations and exposes an actionable finite error. No credentials in saved state or UI.

## Acceptance

All existing tests remain; add constrained route/time, manual takeover/provider unavailable, world ticks/NIGHT, roundtrip restore, invalidated stale approval/action, DB rollback/isolation, redacted trace/metrics and AC-01–08 tests. Run a real PostgreSQL checkpoint roundtrip using isolated temporary local DB if available, plus packaged browser/manual/model verification. Document any externally blocked check without marking it complete.
