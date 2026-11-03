NPC AI body-block fix

Changed: SetMoving.java, CMoveToTargetAction.java, CMoveAroundAction.java.

A monster chasing a target now returns DYNAMIC_BLOCKED when its intended step
is blocked by a body, before attempting lateral collision-response movement.
Enterable side contact, reservation-only retry, pets and MOVE_AROUND retain
their existing movement policy. Normal chase no longer switches to an attack
slot route just because a previous step contacted a body.
A lane-blocked random walk does not reclaim another melee slot while its
original blocked lane remains occupied. Full-ring blocks retain their separate
recovery policy. Open-lane, valid attack position and invalid-target handling
remain covered by the regression harness.

Validation: extracted production moveCreature and tryResumeBlockedAttack
methods compiled/run with Java 21 and deterministic collision/action doubles.
Fixed source passes; original source fails the body-blocked chase assertion.
Checks include repeated/relative chase, side contact, reservation retry, pet,
wander, open/closed lane, ring recovery and invalid target.

Limits: no full game build (dependency libraries not included in upload),
no live map or NPC scheduler test, no load/10,000-client test. Native EXE RTTI
identifies movement action classes; no claim of binary-identical behavior.
The native EXE is not included or modified.

Apply: rebuild your existing server with the three changed Java files, restart
a test server and reproduce a three-entrance choke occupied by monsters. Verify
rear monsters wander, do not alternate left/right, and resume when the blocked
lane clears. Also check pets, ranged mobs, open corridors and dead targets.

Run: python tests/npc_body_block/test_body_block.py (Java 21 javac on PATH).
