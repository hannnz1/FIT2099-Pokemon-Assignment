# SDD ledger — plan: C:/Users/Administrator/Documents/Codex/2026-09-29/docs-specs-pokemon-agent-demo-v1/docs/superpowers/plans/2026-10-06-animation-fluidity.md

## Scope and evidence

User requested all four releases of the unified roadmap: A–H, not merely animation subtasks 1–4. Current baseline frontend: 195/195 passing, 2026-10-06. Source snapshot isolated in work/optimization-four-batches, branch optimization-four-batches. Production remains unchanged until a verified release.

Ruling: use an isolated Git snapshot of the current non-Git Desktop source — the older release checkout is clean but predates the current implementation; preserving the latest source avoids regression — final verified changes must be copied back to the named project.
Ruling: the plan's concluding “only update plan, no development” sentence describes its original creation request; the latest explicit request authorizes implementation — genuine external evidence (human participants, device tests, actual model calls) still cannot be fabricated.

Pre-flight: A1 entity IDs feed A2 movement and A4 effects; map rebuild identity must exclude turns/selection. A3 command settlement must await actual server acknowledgement, not a synchronous UI callback. E history cleanup must retain paid quota and unknown usage settlement. F identity migration must preserve owner isolation and E quotas. C failure controls must match server states. B/G must reuse existing individual-ID journey and persisted outcomes.

## Release tracking

- Release 1: A0–3 and E implemented; automated acceptance passed.
- Release 2: B/C/A4–5 implemented; automated three-partner journeys passed; human observations remain.
- Release 3: A6–7 automated acceptance passed; D repaired real matrix recorded, stability acceptance incomplete.
- Release 4: F/G/H implemented and local/isolated checks passed; public identity rollout, daily offsite and external alerts remain.

Human newcomer observations and actual mobile hardware performance require real participants/devices; automation evidence is recorded separately.

## Verified increment: releases 1 and 2 (partial)

- Frontend suite: 205 passed, 0 failed. Selected Java suites EvaluationHistoryTest, EvaluationPlatformTest, GrowthRoomTest, DuelRoomTest, PublicEvaluationTest passed on 2026-10-06. Deliberate store-failure fixtures emit GROWTH_SAVE_FAILED; these are expected tests, not production failures.
- E history: archive frees active slot; terminal owned delete; restart and replay; unknown in-flight usage retained; no quota refund; compact index; configurable retention. RED logs history-red.log and history-index-red.log, GREEN history-final.log.
- A1–3: retained entity groups, per-entity movement, bounded hold/touch controller. Baseline and first-pass headless Windows measurements frame P95 both 16.8ms; no FPS improvement claim. Browser map rebuild count remains 2 across 40 lab movements. This does not substitute for mobile or long-run acceptance.
- B: actual first wild attack leads to first camp recovery before normal capture; persisted stage survives reload. Early capture and legacy saves remain supported.
- C: failure categories and actual delivered/carried/level progress; website budget exhaustion removes misleading resume. Evaluation provider reason persisted and exposed.
- A4: explicit native combat events added without extra random draws; miss and status-prevention tests first failed for missing API then passed. Growth/duel integration still under verification.
- Remaining: browser hold/modal/weak-network verification, adventure effects, 18 paid cases, identity migration, post-adventure goals, operational backup/drill/load, final review and source integration. No production release yet.

## F identity design ruling (before integration)

Ruling: opt-in invited stable owner UUID, SHA-256 invite/recovery/device hashes, one-use invite, at most 8 lifetime device records per owner and 32 owners; all canonical sessions are keyed by stable owner so two devices share the same authoritative room and quota. Cookies remain HttpOnly/SameSite/Secure. A recovery credential is shown once and never persisted plaintext. Cross-device recovery previews cloud save and requires explicit useCloud; current anonymous save remains untouched. Conflicting local save is not auto-merged or allowed to overwrite cloud; selecting local instead means cancel recovery and redeem a distinct invite. Activation is last, after checkpoint copies, so failed migration retains old owner and unspent invite. Running rooms/evaluations and outstanding usage block migration. Feature defaults disabled until configured with hashed invites; when enabled anonymous users keep free play and baseline but paid providers require identity, preventing cookie reset from multiplying paid allowance. Existing per-user paid evaluation ledger is copied, never reset or refunded. Global provider fee ledger never migrates.

Cost of this choice: no email/login federation, cloud/local merge, recovery-key rotation or unlimited devices. Device revocation currently logs out this device; remote device-management UI remains to verify.

## First real model matrix, preserved failures

Frozen code 2d7a837, DeepSeek flash, 30 decisions/180 seconds, matched baseline fixtures, Seeds 11/23/37. 18 baseline completed; paid: 6 completed, 5 model/planning failures, 7 stopped by the private budget (some partially executed). Actual protected transport: 84 successful HTTP calls, 232614 input + 3358 output = 235972 known tokens, private ceiling 250000; next request reserves its maximum before dispatch, so dispatch stops below the numeric ceiling. Cost unknown because no price version configured. Individual original reports retained, never overwritten. Additional up to 300000 tokens requested from user; no further paid dispatch until approval.

Real evidence: shared/competition repeated observe after reaching visible stock; another pickup used noncanonical itemId. Add deterministic local pickup priority based only on current visible stock and outstanding shared need, preserving engine checks and explicit ENGINE_LOCAL_PICKUP_PRIORITY trace. Policy version changes to berry-local-pickup-recovery-20261006; subsequent comparisons must rerun matched baseline with same policy. This is bounded native coordination, not a claim that the model alone solved the case.

H: game-only cron backup and five-minute local monitor installed on Tencent. First consistent backup game-20261005T162047Z.tar.gz, HTTP200, unpaused, no alerts. Offsite copy and restore drill still pending. One/five/ten connection and sixty-minute soak started; current capacity admits 8 and rejects 2 with SESSION_LIMIT, no claim of ten-player support.

## Browser, identity and restore evidence

- Browser input checks passed: key hold/release, touch hold/release, modal interruption, arrows do not scroll, delayed 400ms acknowledgement does not duplicate a released move. Growth and training page errors empty. Screenshots benchmarks/animation/new-growth-ui.png and new-training-ui.png.
- Identity HTTP regression first exposed public owner UUID accepted as cookie; RED expected false/actual true, fixed by disallowing registered owner IDs in legacy cookie fallback. New anonymous session after registered player also tested (null-owner guard). Identity activation now blocks NPC and evaluation workers and evicts the old idle cache while retaining checkpoint files.
- F legacy/paid migration is opt-in; implementation still requires multi-device browser checks, invitation provisioning and production rollout verification. Do not claim feature enabled publicly yet.
- B new stories now go RETURN -> TRAINER -> COMPLETE only after actual original-individual rookie victory. Legacy COMPLETE remains compatible; skipped legacy saves use reachable evolution/survey/standard-trainer goals and do not invent an original ID or past victory.
- H actual restore drill: production archive SHA256 OK, isolated read-only/no-network container validated 257 checkpoint envelopes/native saved battles. An initial nested read-only mount could not create its mountpoint; remounted the program and jar as separate directories. No production save or fee rollback. Private offsite archive downloaded under ignored benchmarks/operations/backup-offsite-20261006.tar.gz, never publish it. Daily offsite transport is not configured; on-server daily backups and five-minute journal monitor are configured.

## Native recovery regression ruling

Full suite initially found eight failures after eager pickup changed normal provider/approval/race ordering. The correction restores normal delivery-only dispatch; a local pickup is allowed only at a shared task's fourth repeated decision boundary, never before an outstanding model decision or a normal first pickup. Solo quests keep their model ordering. All native mutations still validate tools/quest/current stock, bounded step/failure limits and canonical BERRY. Targeted 52 tests passed (one optional PostgreSQL skipped), including asynchronous player-wins race and pending approval. New stall test verifies three actual observations precede recovery and ENGINE_LOCAL_PICKUP_PRIORITY is disclosed. Full suite recheck pending.

## Final whole-branch review and fix pass

One fresh-context reviewer found two Important issues: legacy journals without billable could lose unknown fees; lost redemption responses could strand an identity. RED evidence legacy-fee-red.log (journal null) and identity-unknown-red.log (second owner created after unknown committed save). GREEN review-verified-green.log: 15 tests passed. Unknown legacy classification is conservatively retained unless explicitly baseline; bounded purge backlog may require manual fee reconciliation. Client creates requestId/device/strong recovery before dispatch, retains pending operation only in sessionStorage, server replay requires matching invite/device/recovery hashes and requestId, and rereads committed identity index after an unknown save. HTTP same-request replay, bad credentials, restart, revoked devices, copy failure and two devices sharing one room covered. No plaintext credential in server checkpoints/trace.

Final package Java638 tests,0 failures,21 optional-environment skipped; frontend209 passed,0 failures. Three initial partners' actual browser journey and rookie victories completed using native authority (5/5/4 rounds), same individual and no paid calls. These are automated journeys, not observations of three human newcomers. Identity browser dropped successful redemption response then retried and restored same room on second device. Boundary overlay thirty moves remains bounded and OS reduced-motion snaps; lost confirmed movement response retries same requestId and world advances once.

H fresh isolated restore: backup checksum OK,257 envelopes/native restored states checked, checksum+extract+audit about1 second. This measures isolated validation, not full public-service RTO or every account migration. Production game/save/fee unchanged. Ops injected checks (3) passed including corrupt status, paused container, disk/backup/storage/queue alerts. New healthz-aware monitor is prepared; existing live monitor continues until a game release provides healthz.

Outstanding evidence: actual mobile hardware performance, real human newcomer observation, repair paid retest (additional budget pending), daily offsite synchronization and external alert delivery. Sixty-minute free load still running; update report on completion. No claim of ten-player capacity:8 admitted,2 rejected SESSION_LIMIT. Gameplay production image remains unchanged.


## Final delivery evidence (2026-10-06)

Integrated actual Desktop source: Java640 total,619 passed,21 optional skipped,0 failed; frontend213 passed; operations5 passed. Final jar packaged, native four-map/goal links/mobile390/health checks and input400ms/release-new-direction passed. Fixed untranslated growth NO_PATH notice without changing rules.

Sixty-minute free soak complete,28702 requests,0 errors,P95~20.8ms;8 admitted2 SESSION_LIMIT. Resource sampling began late,174 samples,~28.9min,CPU3.61s in sampled interval,peak working set309.4MiB. Isolated restored service health and growth HTTP200 passed with no network/keys/public ports; production image unchanged.

User approved additional max300000 tokens. Repaired matrix18 baseline completed; DeepSeek8 completed,3 failed(1 wall timeout,1 invalid response,1 no-progress),7 budget-stopped. Actual protected HTTP ledger283151 tokens/105 successful calls/0 HTTP failures,ceiling honored. Remaining reservation could not accommodate another request; no further paid dispatch. Competition3/3,information3/3; shared1/3 and forest1/3. Fault/battle new matrix were blocked by budget, not accepted as passed. First-round original failures preserved separately; model seed fixtures do not imply deterministic model output. Retrospective pricing snapshot is an audit estimate, never the billed amount.

Full delivery report docs/optimization-four-batches-report.md states code vs acceptance boundaries. Real-model reliability, actual mobile/human observations, daily offsite and external notifications remain unaccepted. No production gameplay deployment or public identity enablement.

Provider-label RED: native DeepSeek evaluation expected deepseek but got openai; GREEN three native provider identities passed. Final source full suite640 total,619 passed,21 optional skipped,0 failures. Frozen paid reports remain unchanged; this post-experiment correction changes trace attribution only.
