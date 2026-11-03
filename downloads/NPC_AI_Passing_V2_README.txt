NPC passing fix V2 - 2026-11-03

Supersedes NPC_AI_Overtake_FULL_SRC_FIX.zip: REMOVE its 8-tile distance rule.

A rear chasing monster B, blocked by another NPC A or an NPC move reservation,
tries the two forward diagonals around its intended heading BEFORE yielding
to wander/retry. This is independent of target distance. The ordinary direct
step is still preferred when clear. Actor-stable side preference is retained.
Each bypass must be statically legal, pass live collision checking and ordinary
atomic movement admission, and strictly reduce squared target distance.
No pure lateral/backward fallback, speed increase, queue or route scan added.
If no bypass is available: body block yields to wander, reservation-only block
keeps its existing short retry. Walls, PCs and occupied tiles are not bypassed
by ignoring collision. Retarget, target motion/death and action-generation
checks remain in place.

Includes prior current-route chase-resume fix in CMoveAroundAction.java.
Changed Java files vs latest user upload: SetMoving.java, CMoveAroundAction.java.
Other uploaded auto-hunt/sight Java sources are preserved byte-for-byte.

Validation: Java 21, extracted production movement/resume methods with
deterministic collision, action and route doubles. Three regression scripts
pass: test_body_block.py, test_overtaking.py, test_current_lane.py.
Passing tests cover distances 2/3/8/9/20, all eight movement headings, both
sides, occupied/reserved/no-bypass/wall/near-crowd cases, strict progress,
target/death/generation/commit races and stable preferred side. Prior blocked
wander and reopened-route recovery tests still pass.

Limits: no full game build or live multi-monster map/scheduler test; no claim
of exact native-server equivalence or guaranteed overtaking at identical
monster speeds. This permits legal passing steps without altering walk speed.

Rebuild and restart a test server. Test A ahead of B on an open field at short
and long target distances, then test blocked side tiles and narrow corridors.
Confirm B can choose a free forward-side tile and does not repeatedly wait
behind A when that step is available. Recheck blocked wander and route reopening.
