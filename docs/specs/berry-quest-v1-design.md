# Berry quest / approval implementation

Intent: continue the approved all-Java Agent V1 using the existing engine. Implement
real berry pickup, merchant purchase, professor delivery and trusted player approval
before provider/API/UI integration. Keep the original Candy economy and entry point.

Architecture: one BerryQuestSession owns one actor/map/task/quest and a scoped demo
wallet/merchant offer. Rules specify required count, deadline turn, capacity and
approval TTL. Berry is a real Item; quantity checks count actual objects. Tools bind
the session, use strict schemas and the existing per-session idempotency registry.
Completion changes both quest and task only after physical delivery to the professor.

Approval: separate bounded PurchaseApprovalService holds one proposal and one grant.
The server computes quantity/price/expiry. The owner responds through a trusted API;
the model can only request and use the scoped capability. Grants bind lifecycle,
quantity and cost; expire, are single-use, and are invalidated by lifecycle changes.
Every critical purchase requires approval; NO_SPENDING overrides are exact grants.

Execution: calls are world-thread-only. Each tool validates before mutations. Approval
freezes the session turn API; a later room scheduler must also freeze map/time ticks.
The clock for authorization remains independent. Pause does not freeze quest deadlines.
No provider, auth server, database, full-game save or global resource service is added.

Implementation / verification ledger:
1. Pickup/delivery tests written before session/tools; missing types observed RED.
   Corrected capacity fixture to use feasible capacity 3 with two carried objects.
2. Approval tests written before manager/methods; missing methods observed RED.
3. Engine scenarios written before BerryQuestDemo; missing demo observed RED.
4. Added approval outcome observation regression: expected WAITING, actual null RED;
   manager now exposes WAITING/DENIED/APPROVED/CONSUMED/EXPIRED/INVALIDATED GREEN.
5. Full Java suite 72/72; Java 8 production compile; client suite 18/18; demo both
   branches complete with three real deliveries, balances 4 (approve) and 5 (deny).
6. Independent review: no high/medium findings within documented single-session scope.

Remaining product work: Gemini/natural-language constraint mapping, authenticated
HTTP/WS and room scheduler, actual UI controls, database/full-game persistence and
end-to-end tests against the complete application.
