NPC Passing V4 - persistent chase route - 2026-11-03

Includes V3 dead-reservation cleanup and immediate wander->AI handoff.
PRESERVES the intentional initial A* 800ms pause.

Added: when ordinary chase is body-blocked and cheap passing cannot move,
try the existing live-occupancy melee-ring router BEFORE gating the target
and switching to wander. A reachable attack slot is atomically claimed and
the existing reserved-slot action immediately performs the first step.
Subsequent ticks retain that goal and recompute the route with live occupancy.
This allows a valid sideways-first detour that the previous strict distance-
decreasing two-diagonal shortcut rejected. No new actor fields, workspaces,
threads or queues. Existing router is bounded to 25x25; this is not an unlimited
whole-map crowd path search. Cheap forward passing remains distance-independent.
Full/actually unreachable attack rings still fall through to blocked wander.
Other admitted attack desires retain their prior selection order.

Seven tests/npc_body_block/test_*.py scripts PASS with Java 21.
New test extracts REAL production bounded BFS, ring selection/claim, blocked-
chase handoff, reserved-slot action and reserved physical movement methods.
A synthetic A/B scene blocks both original forward diagonals; the test checks
a sideways first step, movement on each subsequent tick, the same retained
attack-slot goal, arrival, leader removal/opening, full-ring fallback and
stale-generation rejection. Map, occupancy services and physical commit are
deterministic doubles, not a live game world.
Prior movement, reopened-route, dead-reservation, timer handoff and intentional
first-pause tests also pass. No full game build/live server reproduction or
load test. This verifies specific code paths, not every reported stall.

Base: uploaded RKern_NPC_BodyBlock_AutoHunt_Sight_Unblock_FULL_SRC_20261103.zip.
Changed Java files vs base: SetMoving.java, CMoveAroundAction.java,
CMoveToTargetAction.java, TileOccupancyNpc.java. Other auto-hunt/sight Java
sources are preserved byte-for-byte. Rebuild all changed files and restart.

Diagnostics: JVM option -Drkern.npc.chase.trace=true before -jar/main class.
Trace selects first body-colliding NPC; CHASE-ROUTE-V4 confirms the new chase
handoff is executing. Capture CHASE-PROBE/OWNER/SCHED and PASS/RESUME/ROUTE
lines if a stall persists. Confirm V4 is rebuilt rather than only copying
Java source files into the running server.
