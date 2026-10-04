# Phaser Integration Implementation Plan

Goal: deliver existing RPG assets as the primary real Java quest/training map with existing controls and persistence.
Execution: inline, preserve all uncommitted work. User authorized connection; skill workflow informs implementation without extra approval gates.

- [x] Red tests: projection uses only server state, native positions/HP, captured targets disappear, safe keyboard and selected targets.
- [x] Projection and shared Phaser scene renderer; use existing asset art, atlas samples and reusable runtime.
- [x] Wire quest/training render, keyboard and pointer through current UI commands; responsive canvas and accessible map alternative.
- [x] Red HTTP tests for packaged vendor/art/CSP/MIME, implement allowlisted resource serving. Add one shared resource packer and Maven resources.
- [x] Fresh Java/client/PostgreSQL/Java8 tests and packaged browser gameplay/restart/asset loading verification.
- [x] Update docs and final Phaser runtime/source archives; save actual screenshots and report scope faithfully.
