NPC chase resume fix - 2026-11-03

Base: RKern_NPC_BodyBlock_AutoHunt_Sight_Unblock_FULL_SRC_20261103.zip
Changed Java source: CMoveAroundAction.java only.

Root cause: the previous fix required the original blocked tile to become empty
before checking present reachability. That historical tile could stay occupied
after a different entrance opened or the monster/target changed position.
The same stale-tile check was also used for long-range/ranged recovery.

Fix:
- Nearby melee recovery (within existing 12-tile bounded router) uses a current
  occupancy route and atomic attack-slot claim, without a historical-tile veto.
- No fallback through an empty historical tile when that melee route fails.
- Long-range/ranged lane recovery checks the first physical step of a freshly
  computed current static route against live occupancy. This first-step check
  does not prove the entire long-range route will remain clear.
- Body-blocked chase still yields to existing random-walk behavior.
- Preserve supplied auto-hunt and sight changes byte-for-byte.

Validation (Java 21 extracted production methods with deterministic doubles):
- test_body_block.py: PASS repeated/relative blocked chase, side contact, pet,
  reservation retry, wander, side entrance recovery, no route, 9..12 tile range,
  long-range first step, full ring and invalid target.
- test_current_lane.py: PASS stale occupied/empty tile, actual first-step
  occupancy, absent route, zero-progress step and static wall.
- Unmodified uploaded lane helper FAILS the stale occupied tile test.

Limits: no full game build, real map/NPC scheduler simulation or concurrent
client load test. The melee route/claim helper itself is existing code; the
resume-decision test uses a deterministic route/claim double.

Apply: rebuild with the updated source and restart a test server. Block several
entrances using monsters, allow rear monsters to wander, then open a DIFFERENT
entrance while leaving the original blocked tile occupied. Check resumed chase,
blocked routes, ranged monsters, moving targets and repeated open/close cycles.
