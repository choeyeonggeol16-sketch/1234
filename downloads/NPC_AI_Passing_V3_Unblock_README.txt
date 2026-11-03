NPC Passing V3 / Unblock handoff and dead-reservation fix - 2026-11-03

Includes V2 distance-independent forward passing and current-route recovery.
IMPORTANT: the intentional first A* transition >=800ms pause is PRESERVED.
Its source block is identical to V2; regression verifies 800ms first transition
and ordinary sprite interval on subsequent steps.

Additional confirmed source defects addressed:
1. CMoveAroundAction.onEnd(DONE) previously only set THINK. The CIO tick could
   rearm an old walk/retry/rest sleep before its next timer woke AI selection.
   Completion now uses existing rethinkNow to invalidate the old generation,
   release cadence and wake AI. Successor-action ownership guard remains.
2. TileOccupancyNpc reservation lookup previously returned a dead/removed
   owner's ID until TTL expiry, even after physical occupancy became empty.
   Reservation query/admission/commit now clean ghost-owner claims outside
   MapContext's StampedLock and preserve live successor reservations.
   Ordinary live claims and atomic move admission remain exclusive.

Debug: add -Drkern.npc.chase.trace=true to JVM options BEFORE -jar or main class.
Existing trace selects the first body-colliding NPC. New CHASE-PASS-V3 and
CHASE-RESUME-V3 lines report passing/resume decisions. This is opt-in; default
is off. If the symptom persists, retain CHASE-PROBE, CHASE-OWNER, CHASE-SCHED,
CHASE-PASS-V3 and CHASE-RESUME-V3 output for the reproduction.

Validation: all six tests/npc_body_block/test_*.py scripts pass on Java 21.
- body-block and all-distance passing, all headings, reservation retry,
  target/generation races, current route and side entrance recovery.
- complete production TileOccupancyNpc compiled with world/map/collection doubles:
  dead/removed claim cleanup, immediate reclaim, commit, late old-owner cancel,
  live claim preservation and 32-contender exclusivity. Original source fails
  the dead-owner-before-TTL assertion.
- production wander onEnd with action-controller doubles: immediate AI handoff,
  old deadline invalidation and successor guard. Original fails the handoff test.
- production tickInternal: intentional first A* pause retained.

Limits: no full game build, real map/AI scheduler/multi-monster reproduction,
real Agrona storage concurrency test or client load test. These tests establish
specific code defects and corrected contracts, not that every observed server
stop has been identified. No native-server equivalence claim.

Base: user-uploaded RKern_NPC_BodyBlock_AutoHunt_Sight_Unblock_FULL_SRC_20261103.zip.
Changed Java files vs base: SetMoving.java, CMoveAroundAction.java,
TileOccupancyNpc.java. Supplied other auto-hunt/sight Java sources are unchanged.
Rebuild all changed files and restart a test server. Reproduce A ahead of B,
then kill A to open the lane. Keep the intentional initial 800ms pause separate
from repeated stops. Verify B progresses/resumes, and live occupied/reserved
tiles cannot be crossed.
