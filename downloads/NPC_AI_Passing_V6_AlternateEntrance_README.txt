NPC Passing V6 / alternate entrance search - 2026-11-03

Requested case: monster approaches from south, south entry is blocked, but
a real route goes around the obstacle to the target's open north entrance.

Confirmed prior limitation: all eight melee slots were considered, but routes
were confined to an actor-centered 25x25 window. A separate 12-tile target
cutoff could prevent trying the ring at all. An open opposite entrance or
the detour required to reach it could fall outside the search window.

Change:
- Keep the existing radius-12 local occupancy BFS as the first pass.
- If no candidate attack slot is reached, retry in a radius-32 (65x65) window
  centered between actor and destination. Both ring-claim scans and movement
  toward the reserved slot use this same two-stage router.
- Remove the old 12-tile claim veto. Blocked melee recovery always asks the
  route/claim helper rather than checking only the straight first step.
- Preserve a claimed reachable slot across movement ticks; if all eight slots
  are occupied, skip scanning and retain blocked-wander behavior.

Fixed primitive arrays are reused on the existing PathFinder owner. No new
per-query nodes/queues or worker threads. Workspace grows from 625 to 4225
cells: about 47KB extra primitive storage per PathFinder, plus array headers.
The expanded search is conditional on local failure and can cost more at
congested chokes. It remains BOUNDED: failure is not proof that no route exists
anywhere on the full map. No 10,000-client load capacity claim.

Validation: nine tests/npc_body_block/test_*.py scripts PASS on Java 21.
New test uses actual production BFS, live ring selection/claim, blocked chase
and reserved movement methods, with deterministic map/occupancy/actor doubles.
South-to-north scene: seven entrances occupied; only north open; a barrier
can only be crossed through a gap outside the old local window. V5 FAILS the
arrival-start assertion. V6 starts moving and reaches the exact north slot
without idle retry on the path. Also covers a completely disconnected north
slot (reject), distance beyond old cutoff, local fast path, and full-ring skip.
Prior angle, persistent passing, death reservation, handoff and 800ms tests
also pass. The intentional initial A* 800ms pause is PRESERVED.

Limits: no full game build, actual server map/scheduler reproduction or load
benchmark. Native-server equivalence is not established.

Base: supplied RKern_NPC_BodyBlock_AutoHunt_Sight_Unblock_FULL_SRC_20261103.zip.
Changed Java vs base: PathFinder.java, SetMoving.java, CMoveAroundAction.java,
CMoveToTargetAction.java, TileOccupancyNpc.java. Other supplied auto-hunt/sight
Java sources are preserved byte-for-byte. V6 includes V3/V4/V5 fixes.
Rebuild changed files and restart a test server. Reproduce south blockage
with an open north route, then close that route and test all-eight occupancy.
Debug option: -Drkern.npc.chase.trace=true before -jar/main class.
