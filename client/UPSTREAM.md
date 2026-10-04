# UI import record

Source: https://github.com/devshareacademy/monster-tamer
Commit: a964bba7ca0ae1aeeb712065a01b09ce3366f395
License: MIT (see LICENSE). Images/audio have separate provenance.

The original src scenes remain a reference application at reference.html.
The product entry index.html imports src/echo/main.js and never imports the
upstream DataManager or local combat rules. Shared NineSlice is reused directly.
preview.html is an explicitly labelled development fixture, not a Java game.

## Execution ledger

Plan: UI升级与Phaser接入计划.md v1.0.
Ruling: work in a new feature branch in the requested repository; preserve Java files.
Ruling: Java HTTP/WebSocket services do not exist. Implement the complete UI client
and real transport contract; keep fixture preview separate. Real server acceptance
is pending, never substitute fixture results for T01–T38/U04–U10 integration gates.
Pre-flight: tasks 2–6 share Gateway and immutable snapshot view. Use full snapshot
state events initially; delta patches must be negotiated before later adoption.
Ruling: retain upstream single-player scenes behind reference.html, not the product
entry. This prevents existing scene-private local rules from becoming authoritative.

## Delivery state

- Imported upstream code, images/audio and local runtime libraries; reference game
  title and map verified in browser. Assets remain development-only pending full
  publication license review.
- Implemented Chinese Echo UI, same-origin gateway, read-only state, lifecycle
  coordinator and scripted preview. No Java source modification.
- Used one Phaser presentation scene with explicit view routing to avoid duplicating
  network subscriptions across menu scenes. Scene-specific classes from the plan
  are not mechanically copied; original sample scenes remain in reference entry.
- Gateway state events carry full snapshots initially. Delta protocol and production
  map binding need agreement with the not-yet-created Java service.
- TDD red/green recorded for 18 tests, including all review fixes. Full Java/dual
  browser acceptance is pending. See test/ui-checks.md for exact evidence.
- All six original plan tasks retain their real-service gates; this UI delivery
  does not mark the entire game or chapter implementation complete.
