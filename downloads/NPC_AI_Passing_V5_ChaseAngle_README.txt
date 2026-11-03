NPC Passing V5 / angle-based chase - 2026-11-03

Specific cause of the reported 4-o'clock approach:
coalescedSegmentHeading consumed the dominant cardinal axis first and selected
a diagonal only when the remaining X/Y magnitudes became equal. For (-2,-1),
that selected west first, then northwest, even on an open field.

Change: ordinary/relative MONSTER chase now quantizes the remaining persistent
waypoint vector to its nearest 45-degree heading, using the same 22.5-degree
integer threshold already used by PathFinder. Both initial CalcMidDest heading
and subsequent segment headings use this rule. No random route styles added.
Pure cardinal/shallow approaches remain cardinal when that is the nearest angle.
Other NPC movement and random walking retain their existing grouped steering.
Static legality and existing raster GEO fallback remain in place; the static
route/waypoints are not replaced. Walls can still require a real right-angle path.

Includes V4 persistent body-clear passing route, V3 dead-owner reservation cleanup
and immediate wander-to-AI handoff. The intentional first A* >=800ms pause is
unchanged and tested. No movement speed increase or extra worker/queue added.

Eight tests/npc_body_block/test_*.py scripts PASS (Java 21). New angle regression
extracts actual initial/current heading helpers, checks old vs new (-2,-1)
selection, quadrants, relative chase, non-chase scope, 625 goals with shortest
8-way step count/no reversals, and GEO fallback for a blocked preferred diagonal.
Prior persistent BFS/claim passing, body-block, reopened-route, dead reservation,
AI handoff and first-pause tests also pass.

Limits: extracted-method/complete occupancy-class harnesses with deterministic
map/actor/occupancy/collection/scheduler doubles; no full game build, actual
server movement capture, native equivalence or client load test.

Base: user-uploaded RKern_NPC_BodyBlock_AutoHunt_Sight_Unblock_FULL_SRC_20261103.zip.
Changed Java files vs base: SetMoving.java, CMoveAroundAction.java,
CMoveToTargetAction.java and TileOccupancyNpc.java. Other supplied auto-hunt
and sight Java code is preserved. Rebuild and restart a test server.
Test oblique open-field approaches, real walls, front/rear passing, all-eight
ring blocking, leader death/opening and the intentional initial pause.
Debug JVM option: -Drkern.npc.chase.trace=true before -jar/main class.
