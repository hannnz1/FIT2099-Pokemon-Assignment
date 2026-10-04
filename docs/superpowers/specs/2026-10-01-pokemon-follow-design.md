# Pokemon Follow Design

Purpose: extend captured Pokemon summon into an optional real-engine companion that follows Mudkip without spending extra turns, API requests or coins. User continues the Java V1 project; no new battle or partner replacement.

Follow defaults off, can be enabled/disabled only by the authenticated owner with collection management control. One legal engine exit per successful world turn, including manual and agent actions. Stay when adjacent (actual engine exits include diagonals), blocked or no permitted route. Use NavigationService shortest routes to a free adjacent tile, respect terrain, occupancy and task area predicate, never teleport or enter Mudkip/NPC tiles. Run after normal map/NPC effects. Pausing without an action does not move followers.

Collection remains ownership/deployment authority. Every new summon gets a deploymentId, preventing an old quest checkpoint for the same captureId being applied to a newer summon. Collection coordinates are deployment spawn anchor only. Quest checkpoint optionally stores matching deploymentId/captureId/current x,y/following, atomically with Mudkip, turn, NPCs and task state. Restore coordinates only when both identities match; stale metadata cannot respawn recalled Pokemon or move a newer summon. Legacy deployment without token uses its captureId as stable fallback, with default stationary behavior. Matching invalid restored placement freezes, preserving collection.

AgentRoom reconciles the actual Actor after cross-room collection reloads using current map position rather than spawn anchor. Public collection rows use actual quest projection, including when training is opened first after restart. Training cannot advance a paused quest. Recall/reset removes Actor and clears follow projection.

Failure: failed/unknown quest write freezes. Recovery loads last durable complete world (including follower location/enable flag); uncertain committed writes reload committed state. Collection summon/recall failures keep existing recovery semantics. No follow position writes to collection, no split world-turn/position commit.

UI: enable/stop follow buttons and plain-language state. Core remains Java8, no dependency or real provider call. Preserve pre-existing uncommitted work and original saves; do not run an old runtime against new deployment saves.
