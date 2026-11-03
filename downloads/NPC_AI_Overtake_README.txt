NPC far-chase overtaking fix - 2026-11-03

Base: supplied RKern_NPC_BodyBlock_AutoHunt_Sight_Unblock_FULL_SRC_20261103.zip.
Includes the preceding current-route chase-resume fix. Changed Java files
relative to that upload: SetMoving.java and CMoveAroundAction.java only.
Supplied sight and auto-hunt source changes are preserved.

Far-chase change:
- A body-blocked monster chasing a target more than 8 tiles away may try the
  two forward diagonals around the intended heading.
- Bypass is only attempted for an NPC-body block, not a PC block.
- The chosen tile must be statically legal, pass live collision probing and
  ordinary movement commit, and strictly reduce squared target distance.
- Actor-stable preferred side avoids selecting a side by changing blocker ID.
- No pure lateral/backward fallback. No speed/cadence increase or new route scan.
- At <=8 tiles or when both forward sides fail, retain blocked -> wander.
- Revalidate target position/identity, health, map and action generation after
  probing. Do not write successor movement state after generation changes.

Prior recovery change:
- Nearby melee recovery uses current bounded occupancy route + attack-slot claim
  instead of vetoing recovery on a historically blocked tile.
- Long/ranged lane recovery probes the first step of a fresh current static route.

Validation: Java 21 extracted production movement/resume methods, with
deterministic tile, collision, action and route doubles.
PASS test_body_block.py; PASS test_current_lane.py; PASS test_overtaking.py.
Cases: far/relative overtaking, preferred/opposite side, no bypass, near crowd,
wall, wrong-direction progress, target death/movement/retarget, action generation
change, failed movement commit, stable side preference, pet/wander, reservation
retry, side entrance reopening and invalid-target recovery.

Limits: no full server build, live map/scheduler/multi-NPC simulation or load
test. This is a requested L1 behavior adaptation, not verified native equivalence.
Actual overtaking depends on geometry and normal monster movement speed.

Rebuild and test: on an open field, make two monsters chase a player more than
8 tiles away; verify rear monster uses a free forward side. Then block both
sides and verify wander; check <=8-tile crowds, other reopened entrances and
moving targets. Run each tests/npc_body_block/test_*.py with Python and Java 21.
