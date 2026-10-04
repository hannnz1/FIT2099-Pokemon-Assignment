# UI implementation verification — 2026-09-27

## Automated

`node --test test/unit/*.test.mjs`: 18 tests passing.
Coverage: immutable views, version rejection, stale/duplicate/gap messages,
preferences-only persistence, stable entity IDs, quest projection, identical command
retry payloads, HTTP rejection, connection-close race, same-room pending retry,
old-room resync race, buffering during resync, disabled/frozen actions, dialog
transitions, and cancelled/duplicate presentation events.

Review: an independent reviewer identified five issues; regression tests reproduced
and verified fixes. Follow-up review found definitive HTTP rejection trapping pending
commands; a failing 403 test reproduced it and passed after correction.

## Browser checks actually performed

- Original reference.html loaded title and entered its map; no captured error logs.
- preview.html loaded all selected images and UI panels; no captured error/warn logs.
- Preview flow: enter → talk → borrow → three attacks → capture → accept → four
  evidence interactions → tools → vines → stones → latch → water → three rescues
  → report. Journal showed four evidence entries, 3/3 and explicit preview completion.
- Verified party shows Treecko placeholder and the borrowed companion is absent;
  inventory shows no personal ball and separate public repair tools.
- Verified Chinese dialog wrapping and visible manual text reveal control.
- Settings selected fast text and showed persisted-preference notification.
- Formal entry returned visible Java-service-unavailable message; did not enter a
  fake room, fabricate success, or silently switch to preview.
- Desktop DOM measured canvas 1200×675; page scrollWidth equalled clientWidth
  (1351 at one measurement, 1265 at another), so no horizontal overflow observed.
  Exact requested 1024/1366 viewport overrides were not reliably reflected by the
  embedded browser; those precise breakpoint checks remain pending.

## Not yet passed

These checks are not Java gameplay or multiplayer acceptance. T01–T38 and U10 remain
pending real server implementation. U01–U03 have only the above baseline evidence;
full Chinese/resize/keyboard coverage and item-by-item asset licenses remain open.
U04–U09 have isolated client tests, not full two-client Java integration evidence.

No Java source changed. Maven was not on PATH; Java tests were not run this turn.
The installed npm shim was broken in the review environment, so verification used
Node directly. No npm dependencies were installed. No publication or push performed.
