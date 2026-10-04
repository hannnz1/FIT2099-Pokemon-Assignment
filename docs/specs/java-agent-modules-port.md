# Java agent module port

Scope approved in the chat: port the discussed Mindcraft, AI Town and Generative
Agents mechanisms into the existing Java game. The selected module ports include
bounded automatic execution, engine-turn integration and event-memory save/load.
This does not mean completion of the separate Gemini Agent V1 product spec.

## Design

- Keep Java 8 source compatibility and the course engine API.
- Tools use a whitelist, strict scalar schemas, an execution policy and structured
  results. Each registry belongs to one actor/session; do not share it across users.
- Tasks use operation IDs plus generations to reject late asynchronous results.
  Pause/cancel invalidate pending work. World-changing commits and task transitions
  share a lock; callers must serialize all world access on their world executor.
- Navigation follows actual same-map exits using a shortest-path search. Every
  movement step rechecks the map and restrictions. No implicit teleportation,
  partial destination success or advancement of the global clock inside tools.
- NPC perception filters world/area/radius/time before recording events. Memories
  are immutable, isolated by world and NPC, deduplicated by event ID and retrieved
  by subject and occurrence time. No embeddings or reflection.
- Store upstream commits and license texts under third_party. Files explain their
  source and intentional deviations. Existing client and gameplay stay intact.

## Implementation plan

1. Write behavioral tests for strict tool validation, policy enforcement, duplicate
   action IDs, immutable results and real map mutations. Implement registry/results.
2. Test cancellation during pending decisions, timeout, pause/resume and duplicate
   commits. Implement task lifecycle and asynchronous runner.
3. Test detours, unreachable/occupied destinations, area restrictions and one-step
   movement. Implement navigation and tools adapted to the engine.
4. Test memory isolation, visibility, future events, deduplication and bounded
   historical retrieval. Implement event memory and perception.
5. Add a deterministic executable example, source/license manifest and usage docs.
   Run all Java tests, Java 8 API compilation and the existing client tests.
6. Complete Mindcraft's retained-action and self-prompt loop adaptation: immutable
   decision snapshots, nonblocking world ticks, one tool per tick, bounded model
   calls and continuation steps, bounded failure retries, lifecycle invalidation.
7. Add event-only, versioned atomic memory snapshots and an NPC-bound recall tool;
   validate reload, provenance, isolation and damaged-file rejection.
8. Bridge the loop into engine Action/playTurn with opt-in AgentMudkip, preserving
   terrain weapon rules. Verify no legacy wandering or duplicate turn execution.
9. Run final whole-module review, regression tests, production compilation and
   executable packaged demo; refresh the guide and source overlay.

## Continuation decisions and results

- The automatic scheduler replaces endless self-prompting with configured decision,
  action-step and consecutive-failure budgets. A successful tool cannot complete a
  task: only the injected authoritative completion rule can do that.
- Every tick executes at most one tool. Decisions run on a caller-owned executor;
  results wait until a later world tick. Blocking provider work may outlive timeout,
  but cannot mutate the world. Lifecycle revisions also invalidate retained actions.
- Memory snapshots preserve only event memories. Active operations, game state,
  approvals and receipt caches are not restored; restarting requires fresh sessions.
- AgentMudkip is opt-in to avoid two controllers. The original entry point and
  client are preserved. The executable example exercises its actual playTurn action.
- Reviewer identified lost WATER terrain weapon updates; a failing regression test
  reproduced the issue and the adapter now preserves the original equip/remove rule.

## Deliberate limits

No provider calls, database, HTTP server, approval-token service or full quest
engine are added in this port. In-memory idempotency lasts for the registry's
lifetime; crash-safe game persistence must transact it with game mutations later.
The example decision provider is explicitly scripted, not an LLM. Memory snapshots
require atomic replacement support and do not claim transactional full-game saves.
