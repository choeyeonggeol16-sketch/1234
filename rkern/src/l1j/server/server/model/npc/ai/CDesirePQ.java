package l1j.server.server.model.npc.ai;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.ai.core.WorldCollisionHash;

/**
 * Verified retail behavior used here:
 * - default DesirePqSize = 50
 * - warrior NO_DESIRE -> AddMoveAroundDesire(5, 5)
 * - warrior Attack_DecayRatio = 6.6
 * - warrior Attack_BoostValue = 300
 * - warrior UseSkill_DecayRatio = 66000
 * - warrior UseSkill_BoostValue = 100000
 * - desire decay cadence = 5000ms
 * - AddValue accumulates value for the same key
 * - RemoveKey removes the exact desire key
 * - BoostUp/BoostDown adds/subtracts the state's boost value
 * - RandomizeAttackDesire permutes existing ATTACK desire values only
 *
 * No per-tick allocation.
 * Storage is lazy and grows only when a monster actually acquires desires.
 */
public final class CDesirePQ {

    private static final WorldCollisionHash COLLISION =
            WorldCollisionHash.getInstance();

    public static final byte PS_IDLE              = 0;
    public static final byte PS_MOVE_AROUND       = 1;
    public static final byte PS_DO_NOTHING        = 2;
    public static final byte PS_ATTACK            = 3;
    public static final byte PS_CHASE             = 4;
    public static final byte PS_FLEE              = 5;
    public static final byte PS_GET_ITEM          = 6;
    public static final byte PS_FOLLOW            = 7;
    public static final byte PS_DECAYING          = 8;
    public static final byte PS_MOVE_TO_WAYPOINT  = 9;
    public static final byte PS_USE_SKILL         = 10;
    public static final byte PS_MOVE_TO           = 11;
    public static final byte PS_EFFECT_ACTION     = 12;
    public static final byte PS_EFFECT_ACTION2    = 13;
    public static final byte PS_PET_DEFAULT       = 14;
    public static final byte PS_MOVE_TO_TARGET    = 15;
    public static final byte PS_MOVE_SUPER_PT     = 16;
    public static final byte PS_MOVE_FREEWAY      = 17;
    public static final byte PS_MOVE_FORMATION    = 18;

    public static final byte BLOCKED_ATTACK_NONE = 0;
    public static final byte BLOCKED_ATTACK_LANE = 1;
    public static final byte BLOCKED_ATTACK_RING = 2;

    /** reference server DesirePqSize is a warning threshold, not a hard eviction limit. */
    /** reference server value saturation at 0x005A5D70 in CDesirePQ::AddValue. */
    private static final float MAX_DESIRE_VALUE = 3.3e30f;
    private static final float IMMEDIATE_VALUE = -1.0f;
    private static final int IMMEDIATE_INDEX = -2;
    private static final int INITIAL_SIZE = 4;

    private static final float MOVE_AROUND_VALUE = 5.0f;
    private static final float ATTACK_DECAY = 6.6f;
    private static final float ATTACK_BOOST = 300.0f;
    private static final float USE_SKILL_DECAY = 66000.0f;
    private static final float USE_SKILL_BOOST = 100000.0f;

    private static final long DECAY_INTERVAL_MS = 5_000L;

    /** warrior ATTACKED: damage/max_hp/0.05*100 = damage/max_hp*2000 */
    private static final float ATTACKED_SCALE = 2000.0f;

    /** warrior CLAN_ATTACKED: damage/max_hp/0.05*30 = damage/max_hp*600 */
    private static final float CLAN_ATTACKED_SCALE = 600.0f;

    /** warrior_aggressive SEE_CREATURE */
    private static final float SIGHT_ATTACK_VALUE = 200.0f;

    private byte[] states = new byte[INITIAL_SIZE];
    private int[] targetIds = new int[INITIAL_SIZE];
    private float[] values = new float[INITIAL_SIZE];

    // MakeAttack (+0x58, +0x80, +0x74, +0x1c). One lazily allocated,
    // flat primitive array rather than four independent per-NPC arrays.
    // Packed as moveType, flag, auxiliary float bits, auxiliary integer.
    private int[] attackPayload;
    private int immediateAttackMoveType;
    private int immediateAttackFlag;
    private int immediateAttackEffectPointBits;
    private int immediateAttackOffset;

    // Skill payload is allocated only when an NPC actually receives a
    // PS_USE_SKILL desire. Indexes follow the ordinary desire max heap.
    private int[] skillIds;
    private int[] skillMoveTypes;
    private int[] skillFlags;
    private int[] skillExtras;
    private float[] skillEffectPoints;

    private int size;

    // reference server CDesirePQ keeps its ordinary CDesire[] as a max-heap ordered by
    // CDesire::value (+0xA0).  Heap rebuilding is deferred until the next
    // selection so that several changes in the same AI tick cost one pass.
    private boolean heapDirty;

    // Native CDesirePQ::AddValue(-1.0f) copies a desire to the dedicated
    // member at +0x50 and sets +0x48. _GetTopDesire returns that member
    // before consulting the ordinary max heap. Only its key is represented
    // here until the remaining native desire payloads are integrated.
    private boolean immediatePresent;
    private byte immediateState;
    private int immediateTargetId;
    private int immediateSkillId;
    private int immediateSkillMoveType;
    private int immediateSkillFlags;
    private int immediateSkillExtra;
    private float immediateSkillEffectPoint;
    private boolean activeImmediate;

    /** Current desire identity includes skill id for PS_USE_SKILL. */
    private byte activeState = -1;
    private int activeTargetId;
    private int activeSkillId;

    private long lastDecayAt;

    private CDesirePQ(final long nowMs) {
        lastDecayAt = nowMs;

        // warrior::NO_DESIRE -> AddMoveAroundDesire(5, 5)
        states[0] = PS_MOVE_AROUND;
        targetIds[0] = 0;
        values[0] = MOVE_AROUND_VALUE;
        size = 1;
    }

    public static boolean isActive(final L1MonsterInstance npc) {
        return npc != null && npc._desirePQ != null;
    }

    /**
     * warrior::NO_DESIRE -> AddMoveAroundDesire(5, 5).
     *
     * The old adapter only created CDesirePQ after SEE/ATTACKED, so a monster
     * with no prior hate event never entered PS_MOVE_AROUND at all.  The AI
     * dispatcher calls this when it is about to choose a desire.
     */
    public static void onNoDesire(
            final L1MonsterInstance npc,
            final long nowMs) {

        if (npc == null) {
            return;
        }

        final CDesirePQ pq =
                ensure(npc, nowMs);

        if (pq.size == 0) {
            pq.addMoveAround();
        }
    }

    private static CDesirePQ ensure(
            final L1MonsterInstance npc,
            final long nowMs) {

        CDesirePQ pq = npc._desirePQ;
        if (pq == null) {
            pq = new CDesirePQ(nowMs);
            npc._desirePQ = pq;
        }
        return pq;
    }

    // ------------------------------------------------------------------
    // Retail script event adapters
    // ------------------------------------------------------------------

    public static void addSightAttack(
            final L1MonsterInstance npc,
            final L1Character target,
            final long nowMs) {

        if (!validActorTarget(npc, target)) {
            return;
        }

        addAttackDesireEx(npc, target, SIGHT_ATTACK_VALUE, nowMs);
    }

    public static void addAttacked(
            final L1MonsterInstance npc,
            final L1Character attacker,
            final int damage,
            final long nowMs) {

        // warrior::ATTACKED calls AddAttackDesire even for 0 damage.
        // A zero-valued PS_ATTACK is a real desire key (and may receive
        // Attack_BoostValue on activation); skipping it loses a hit event.
        if (!validActorTarget(npc, attacker) || damage < 0) {
            return;
        }

        final int maxHp = npc.getMaxHp();
        if (maxHp <= 0) {
            return;
        }

        final float value =
                ((float) damage / (float) maxHp)
                        * ATTACKED_SCALE;

        addAttackDesireEx(npc, attacker, value, nowMs);
    }

    public static boolean addClanAttacked(
            final L1MonsterInstance npc,
            final L1Character attacker,
            final int damage,
            final long nowMs) {

        if (!validActorTarget(npc, attacker)
                || damage <= 0) {
            return false;
        }

        // warrior::CLAN_ATTACKED checks GetLifeTime() > 7.
        if (nowMs - npc._desireCreatedAtMs <= 7_000L) {
            return false;
        }

        final int maxHp = npc.getMaxHp();
        if (maxHp <= 0) {
            return false;
        }

        final float value =
                ((float) damage / (float) maxHp)
                        * CLAN_ATTACKED_SCALE;

        if (value <= 0.0f) {
            return false;
        }

        return addAttackDesireEx(npc, attacker, value, nowMs);
    }

    // ------------------------------------------------------------------
    // L1 body-block -> MOVE_AROUND bridge
    // ------------------------------------------------------------------

    /**
     * Keep the ATTACK desire/value intact, but temporarily make this exact
     * target non-runnable. Another ATTACK desire may take over first; when
     * none exists, ordinary PS_MOVE_AROUND becomes the runnable fallback.
     */
    public static void blockAttackForMoveAround(
            final L1MonsterInstance npc,
            final L1Character target,
            final int blockedX,
            final int blockedY) {

        if (!validActorTarget(npc, target)) {
            return;
        }

        npc._desireBlockedAttackTargetId =
                target.getId();
        npc._desireBlockedAttackMode =
                BLOCKED_ATTACK_LANE;

        npc._desireBlockedLanePos =
                ((blockedX & 0xffff) << 16)
                        | (blockedY & 0xffff);

        clearResumeReservation(npc);
    }

    public static void blockAttackForMoveAroundRing(
            final L1MonsterInstance npc,
            final L1Character target) {

        if (!validActorTarget(npc, target)) {
            return;
        }

        npc._desireBlockedAttackTargetId =
                target.getId();
        npc._desireBlockedAttackMode =
                BLOCKED_ATTACK_RING;
        npc._desireBlockedLanePos = 0;
        clearResumeReservation(npc);
    }

    public static byte blockedAttackMode(
            final L1MonsterInstance npc) {

        return npc != null
                ? npc._desireBlockedAttackMode
                : BLOCKED_ATTACK_NONE;
    }

    public static boolean hasBlockedAttack(
            final L1MonsterInstance npc) {

        return npc != null
                && npc._desireBlockedAttackTargetId != 0;
    }

    public static L1Character blockedAttackTarget(
            final L1MonsterInstance npc) {

        if (npc == null
                || npc._desireBlockedAttackTargetId == 0) {
            return null;
        }

        return resolveTarget(
                npc,
                npc._desireBlockedAttackTargetId);
    }

    /**
     * _desireResumeAttackPos is not just bookkeeping: it owns an actual
     * TileOccupancyNpc reservation. Never zero the fields without cancelling
     * that reservation first, otherwise a dead/retargeted NPC leaves a ghost
     * melee slot until the reservation TTL expires.
     */
    private static void clearResumeReservation(
            final L1MonsterInstance npc) {

        if (npc == null) {
            return;
        }

        final int packed =
                npc._desireResumeAttackPos;

        if (packed != 0) {
            COLLISION.cancelNpcMove(
                    npc._desireResumeAttackMapId,
                    (packed >>> 16) & 0xffff,
                    packed & 0xffff,
                    npc.getId());
        }

        npc._desireResumeAttackTargetId = 0;
        npc._desireResumeAttackTargetPos = 0;
        npc._desireResumeAttackPos = 0;
        npc._desireResumeAttackMapId = 0;
        npc._desireResumeAttackBlockedRetries = 0;
    }

    /**
     * Release only the temporary runnable gate. The ATTACK desire itself was
     * never removed, so the next topState() immediately re-evaluates it.
     */
    public static void releaseBlockedAttack(
            final L1MonsterInstance npc) {

        if (npc != null) {
            clearResumeReservation(npc);
            npc._desireBlockedAttackTargetId = 0;
            npc._desireBlockedLanePos = 0;
            npc._desireBlockedAttackMode = BLOCKED_ATTACK_NONE;
        }
    }

    public static void releaseBlockedAttack(
            final L1MonsterInstance npc,
            final L1Character target) {

        if (npc == null || target == null) {
            return;
        }

        if (npc._desireBlockedAttackTargetId
                == target.getId()) {
            clearResumeReservation(npc);
            npc._desireBlockedAttackTargetId = 0;
            npc._desireBlockedLanePos = 0;
            npc._desireBlockedAttackMode = BLOCKED_ATTACK_NONE;
        }
    }

    // ------------------------------------------------------------------
    // CDesirePQ-like operations
    // ------------------------------------------------------------------

    /** Special CDesirePQ::AddValue(-1.0f) branch, independent of heap size. */
    public static void addImmediateDesire(
            final L1MonsterInstance npc,
            final byte state,
            final int targetId,
            final long nowMs) {

        if (npc != null) {
            ensure(npc, nowMs).addValue(
                    state, targetId, IMMEDIATE_VALUE, nowMs);
        }
    }

    /**
     * CDesireFactory::MakeAttack (0x0041B520) stores its incoming desire
     * value without taking an absolute value. The -1.0f case must reach
     * CDesirePQ::AddValue's dedicated immediate slot unchanged.
     */
    public static boolean addAttackDesire(
            final L1MonsterInstance npc,
            final L1Character target,
            final float desireValue,
            final long nowMs) {
        return addAttackDesire(npc, target, 0, false,
                desireValue, 0.0f, 0, nowMs);
    }

    public static boolean addAttackDesire(
            final L1MonsterInstance npc,
            final L1Character target,
            final int moveType,
            final boolean flag,
            final float desireValue,
            final float auxiliaryFloat,
            final int auxiliaryInt,
            final long nowMs) {

        if (npc == null || npc.isDead() || npc.getCurrentHp() <= 0
                || !validActorTarget(npc, target)
                || !Float.isFinite(desireValue)
                || !Float.isFinite(auxiliaryFloat)) {
            return false;
        }

        final CDesirePQ pq = ensure(npc, nowMs);
        final int id = target.getId();
        // reference server AddValue merges keys by identity and copies only its flag
        // (+0x80) and cumulative value (+0xA0) on duplicate registration.
        pq.decay(nowMs);
        final int previous = pq.findKey(PS_ATTACK, id);
        pq.addValue(PS_ATTACK, id, desireValue, nowMs);
        if (desireValue == IMMEDIATE_VALUE) {
            pq.immediateAttackMoveType = moveType;
            pq.immediateAttackFlag = flag ? 1 : 0;
            pq.immediateAttackEffectPointBits = Float.floatToRawIntBits(auxiliaryFloat);
            pq.immediateAttackOffset = auxiliaryInt;
            return true;
        }
        final int index = pq.findKey(PS_ATTACK, id);
        if (index >= 0) {
            if (previous < 0) {
                pq.putAttackPayload(index, moveType, flag, auxiliaryFloat, auxiliaryInt);
            } else if (pq.attackPayload != null) {
                pq.attackPayload[(index << 2) + 1] = flag ? 1 : 0;
            } else if (flag) {
                // Duplicate CDesire updates +0x80 even if the initial
                // MakeAttack had no nonzero payload to allocate.
                pq.putAttackPayload(index, 0, true, 0.0f, 0);
            }
        }
        return true;
    }

    /**
     * CNPC::AddAttackDesireEx (0x00485A04), confirmed master branch.
     * A creature with a resolved GetMaster() receives a separate owner
     * desire of +5 for nonnegative input or -5 for negative input.
     * The original function separately tests other NPC conditions; only
     * its verified master and MakeAttack paths are adapted here.
     */
    public static boolean addAttackDesireEx(
            final L1MonsterInstance npc,
            final L1Character target,
            final float desireValue,
            final long nowMs) {
        if (npc == null || npc.isDead() || npc.getCurrentHp() <= 0
                || !validActorTarget(npc, target)
                || !Float.isFinite(desireValue)) {
            return false;
        }
        // Native AddAttackDesireEx adds a secondary master desire *before*
        // adding the original target desire (0x00485D9D / 0x00485DCC).
        if (target instanceof L1NpcInstance) {
            final L1Character master;
            if (target instanceof L1CompanionInstance) {
                master = ((L1CompanionInstance) target).get_master();
            } else {
                master = ((L1NpcInstance) target).getMaster();
            }
            if (master != null && validActorTarget(npc, master)) {
                // reference server compares the *signed* original input with 0.0f.
                CDesireFactory.MakeAttack(npc, master,
                        desireValue >= 0.0f ? 5.0f : -5.0f, nowMs);
            }
        }
        return CDesireFactory.MakeAttack(npc, target, desireValue, nowMs);
    }

    private void putAttackPayload(
            final int index, final int moveType, final boolean flag,
            final float auxiliaryFloat, final int auxiliaryInt) {
        if (attackPayload == null) {
            if (moveType == 0 && !flag && auxiliaryFloat == 0.0f && auxiliaryInt == 0) {
                return;
            }
            attackPayload = new int[states.length << 2];
        }
        final int pos = index << 2;
        attackPayload[pos] = moveType;
        attackPayload[pos + 1] = flag ? 1 : 0;
        attackPayload[pos + 2] = Float.floatToRawIntBits(auxiliaryFloat);
        attackPayload[pos + 3] = auxiliaryInt;
    }

    /** Read fields from the active CDesireFactory::MakeAttack payload. */
    public static int activeAttackMoveType(final L1MonsterInstance npc) {
        final CDesirePQ pq = npc != null ? npc._desirePQ : null;
        if (pq == null || pq.activeState != PS_ATTACK) return 0;
        if (pq.activeImmediate) return pq.immediateAttackMoveType;
        final int i = pq.findKey(PS_ATTACK, pq.activeTargetId);
        return i >= 0 && pq.attackPayload != null
                ? pq.attackPayload[i << 2] : 0;
    }

    public static boolean activeAttackFlag(final L1MonsterInstance npc) {
        final CDesirePQ pq = npc != null ? npc._desirePQ : null;
        if (pq == null || pq.activeState != PS_ATTACK) return false;
        if (pq.activeImmediate) return pq.immediateAttackFlag != 0;
        final int i = pq.findKey(PS_ATTACK, pq.activeTargetId);
        return i >= 0 && pq.attackPayload != null
                && pq.attackPayload[(i << 2) + 1] != 0;
    }

    public static float activeAttackAuxFloat(final L1MonsterInstance npc) {
        final CDesirePQ pq = npc != null ? npc._desirePQ : null;
        if (pq == null || pq.activeState != PS_ATTACK) return 0;
        final int v;
        if (pq.activeImmediate) v = pq.immediateAttackEffectPointBits;
        else {
            final int i = pq.findKey(PS_ATTACK, pq.activeTargetId);
            v = i >= 0 && pq.attackPayload != null
                    ? pq.attackPayload[(i << 2) + 2] : 0;
        }
        return Float.intBitsToFloat(v);
    }

    public static int activeAttackAuxInt(final L1MonsterInstance npc) {
        final CDesirePQ pq = npc != null ? npc._desirePQ : null;
        if (pq == null || pq.activeState != PS_ATTACK) return 0;
        if (pq.activeImmediate) return pq.immediateAttackOffset;
        final int i = pq.findKey(PS_ATTACK, pq.activeTargetId);
        return i >= 0 && pq.attackPayload != null
                ? pq.attackPayload[(i << 2) + 3] : 0;
    }

    /** reference server CDesireFactory::MakeUseSkill -> CDesirePQ::AddValue bridge. */
    public static boolean addUseSkillDesire(
            final L1MonsterInstance npc,
            final int targetId,
            final int skillId,
            final int moveType,
            final int flags,
            final float value,
            final float effectPoint,
            final int extra,
            final long nowMs) {

        if (npc == null || skillId <= 0 || targetId < 0
                || Float.isNaN(value) || Float.isInfinite(value)) {
            return false;
        }
        ensure(npc, nowMs).addSkillValue(
                targetId, skillId, moveType, flags, value,
                effectPoint, extra, nowMs);
        return true;
    }

    /** Snapshot of the currently selected skill desire; never scans the heap. */
    public static int activeSkillId(final L1MonsterInstance npc) {
        final CDesirePQ pq = npc != null ? npc._desirePQ : null;
        return pq != null && pq.activeState == PS_USE_SKILL
                ? pq.activeSkillId : 0;
    }

    public static int activeSkillTargetId(final L1MonsterInstance npc) {
        final CDesirePQ pq = npc != null ? npc._desirePQ : null;
        return pq != null && pq.activeState == PS_USE_SKILL
                ? pq.activeTargetId : 0;
    }

    public static L1Character activeSkillTarget(final L1MonsterInstance npc) {
        final int targetId = activeSkillTargetId(npc);
        if (targetId == 0) {
            return npc; // self-cast desire
        }
        final L1Character target = resolveTarget(npc, targetId);
        return validActorTarget(npc, target) ? target : null;
    }

    /** Consumes only the executed skill key, leaving ATTACK and other skills. */
    public static boolean removeUseSkillDesire(
            final L1MonsterInstance npc,
            final int targetId,
            final int skillId) {
        final CDesirePQ pq = npc != null ? npc._desirePQ : null;
        if (pq == null) {
            return false;
        }
        if (pq.immediatePresent && pq.immediateState == PS_USE_SKILL
                && pq.immediateTargetId == targetId
                && pq.immediateSkillId == skillId) {
            pq.removeImmediate();
            return true;
        }
        final int index = pq.findSkillKey(targetId, skillId);
        if (index < 0) {
            return false;
        }
        pq.removeAt(index);
        if (pq.size == 0) {
            pq.addMoveAround();
        }
        return true;
    }

    public static byte topState(
            final L1MonsterInstance npc,
            final long nowMs) {

        final CDesirePQ pq = npc != null ? npc._desirePQ : null;
        if (pq == null) {
            return -1;
        }

        return pq.activateTop(npc, nowMs);
    }

    public static boolean isMoveAroundTop(
            final L1MonsterInstance npc,
            final long nowMs) {

        return topState(npc, nowMs) == PS_MOVE_AROUND;
    }

    public static L1Character topAttackTarget(
            final L1MonsterInstance npc,
            final long nowMs) {

        final CDesirePQ pq = npc != null ? npc._desirePQ : null;
        if (pq == null) {
            return null;
        }

        for (int guard = pq.size; guard >= 0; guard--) {
            if (pq.activateTop(npc, nowMs) != PS_ATTACK) {
                return null;
            }

            final int index = pq.activeImmediate
                    ? IMMEDIATE_INDEX
                    : pq.findKey(
                            pq.activeState,
                            pq.activeTargetId);

            if (index < 0 && index != IMMEDIATE_INDEX) {
                return null;
            }
            if (index >= 0 && pq.states[index] != PS_ATTACK) {
                return null;
            }

            final int targetId = index == IMMEDIATE_INDEX
                    ? pq.immediateTargetId
                    : pq.targetIds[index];
            final L1Character target =
                    resolveTarget(npc, targetId);

            if (target == null) {
                pq.removeKey(PS_ATTACK, targetId);
                continue;
            }

            if (!isAttackRunnable(npc, target)) {
                return null;
            }

            return target;
        }

        return null;
    }

    /**
     * Add one ATTACK candidate only when that exact key does not already exist.
     * BLOCKED retarget must not keep stacking +200 every retry tick.
     */
    public static boolean ensureAttackCandidate(
            final L1MonsterInstance npc,
            final L1Character target,
            final long nowMs) {

        if (!validActorTarget(npc, target)) {
            return false;
        }

        final CDesirePQ pq =
                ensure(npc, nowMs);

        pq.decay(nowMs);

        final int index =
                pq.findKey(
                        PS_ATTACK,
                        target.getId());

        if (index >= 0) {
            /*
             * BLOCKED retarget priority:
             * an ATTACKED event may already have created this key with a very
             * small value. While the current target is physically blocked,
             * a recognized alternate target must beat MOVE_AROUND(5) instead
             * of being ignored behind random-walk.
             *
             * Floor only; never stack +200 every retry tick.
             */
            if (pq.values[index]
                    < SIGHT_ATTACK_VALUE) {

                pq.values[index] =
                        SIGHT_ATTACK_VALUE;
                pq.heapDirty = true;
            }

            return true;
        }

        pq.addValue(
                PS_ATTACK,
                target.getId(),
                SIGHT_ATTACK_VALUE,
                nowMs);

        return true;
    }

    public static boolean prioritizeMoveAroundAttacker(
            final L1MonsterInstance npc,
            final L1Character attacker,
            final long nowMs) {

        if (!ensureAttackCandidate(npc, attacker, nowMs)) {
            return false;
        }

        final CDesirePQ pq = npc._desirePQ;
        final int index = pq.findKey(PS_ATTACK, attacker.getId());
        if (index < 0) {
            return false;
        }

        float priority = pq.values[index];
        for (int i = 0; i < pq.size; i++) {
            if (i == index || pq.states[i] != PS_ATTACK) {
                continue;
            }
            final L1Character other = resolveTarget(npc, pq.targetIds[i]);
            if (validActorTarget(npc, other) && pq.values[i] >= priority) {
                priority = pq.values[i] + 1.0f;
            }
        }
        pq.values[index] = priority;
        pq.heapDirty = true;
        return true;
    }

    /**
     * A creature physically blocking the current lane is an explicit exception:
     * make that exact ATTACK desire win the next PQ arbitration.
     *
     * This is only used for a real occupancy blocker (PC/Pet/Summon/Companion),
     * never for ordinary alternate-player retargeting.
     */
    public static boolean promotePhysicalBlocker(
            final L1MonsterInstance npc,
            final L1Character target,
            final long nowMs) {

        if (!ensureAttackCandidate(
                npc,
                target,
                nowMs)) {

            return false;
        }

        final CDesirePQ pq =
                npc._desirePQ;

        if (pq == null) {
            return false;
        }

        pq.decay(nowMs);

        final int index =
                pq.findKey(
                        PS_ATTACK,
                        target.getId());

        if (index < 0) {
            return false;
        }

        float top =
                -Float.MAX_VALUE;

        for (int i = 0; i < pq.size; i++) {
            if (i == index
                    || pq.states[i] != PS_ATTACK) {
                continue;
            }

            final L1Character other =
                    resolveTarget(
                            npc,
                            pq.targetIds[i]);

            if (!validActorTarget(
                    npc,
                    other)) {
                continue;
            }

            if (pq.values[i] > top) {
                top = pq.values[i];
            }
        }

        if (top == -Float.MAX_VALUE) {
            top = 0.0f;
        }

        if (pq.values[index] <= top) {
            pq.values[index] = top + 1.0f;
            pq.heapDirty = true;
        }

        return true;
    }

    /**
     * NC CDesirePQ::GetTopDesire, L1 blocked-target exclusion variant.
     * Return the highest-valued EXISTING runnable attack desire without
     * manufacturing hate for a bystander or changing a captured action target.
     * The AI owner performs the actual target hand-off after blocking old A.
     */
    public static L1Character GetTopDesire(
            final L1MonsterInstance npc,
            final int excludedTargetId) {

        final CDesirePQ pq = npc != null ? npc._desirePQ : null;
        if (pq == null) {
            return null;
        }

        if (pq.immediatePresent
                && pq.immediateState == PS_ATTACK
                && pq.immediateTargetId != excludedTargetId) {
            final L1Character immediate =
                    resolveTarget(npc, pq.immediateTargetId);
            if (isAttackRunnable(npc, immediate)) {
                return immediate;
            }
        }

        L1Character best = null;
        float bestValue = -Float.MAX_VALUE;
        for (int i = 0; i < pq.size; i++) {
            if (pq.states[i] != PS_ATTACK
                    || pq.targetIds[i] == excludedTargetId
                    || pq.values[i] < bestValue) {
                continue;
            }

            final L1Character candidate =
                    resolveTarget(npc, pq.targetIds[i]);
            if (isAttackRunnable(npc, candidate)) {
                best = candidate;
                bestValue = pq.values[i];
            }
        }
        return best;
    }

    public static boolean hasOtherAttack(
            final L1MonsterInstance npc,
            final int excludedTargetId) {
        return GetTopDesire(npc, excludedTargetId) != null;
    }

    public static boolean removeAttack(
            final L1MonsterInstance npc,
            final L1Character target) {

        if (npc == null || target == null || npc._desirePQ == null) {
            return false;
        }

        final boolean removed =
                npc._desirePQ.removeKey(
                        PS_ATTACK,
                        target.getId());

        if (npc._desireResumeAttackTargetId == target.getId()) {
            clearResumeReservation(npc);
        }
        if (npc._desireBlockedAttackTargetId == target.getId()) {
            releaseBlockedAttack(npc, target);
        }

        return removed;
    }

    public static boolean removeAttack(
            final L1MonsterInstance npc,
            final int targetId) {

        if (npc == null || npc._desirePQ == null || targetId == 0) {
            return false;
        }

        final boolean removed =
                npc._desirePQ.removeKey(
                        PS_ATTACK,
                        targetId);

        if (npc._desireResumeAttackTargetId == targetId) {
            clearResumeReservation(npc);
        }
        if (npc._desireBlockedAttackTargetId == targetId) {
            clearResumeReservation(npc);
            npc._desireBlockedAttackTargetId = 0;
            npc._desireBlockedLanePos = 0;
            npc._desireBlockedAttackMode = BLOCKED_ATTACK_NONE;
        }

        return removed;
    }

    public static void removeAllAttack(
            final L1MonsterInstance npc) {

        if (npc == null || npc._desirePQ == null) {
            return;
        }

        npc._desirePQ.removeAll(PS_ATTACK);
        clearResumeReservation(npc);
        npc._desireBlockedAttackTargetId = 0;
        npc._desireBlockedLanePos = 0;
        npc._desireBlockedAttackMode = BLOCKED_ATTACK_NONE;
    }

    public static void randomizeAttack(
            final L1MonsterInstance npc) {

        if (npc == null || npc._desirePQ == null) {
            return;
        }

        npc._desirePQ.randomize(PS_ATTACK);
    }

    public static void clear(final L1MonsterInstance npc) {
        if (npc == null) {
            return;
        }

        npc._desirePQ = null;
        npc._desireMoveAroundCounter = -1;
        npc._desireMoveAroundDecisionAtMs = 0L;
        npc._randomMoveDistance = 0;
        npc._randomMoveDirection = 0;
        npc._desireMoveAroundTargetX = 0;
        npc._desireMoveAroundTargetY = 0;
        clearResumeReservation(npc);
        npc._desireBlockedAttackTargetId = 0;
        npc._desireBlockedLanePos = 0;
        npc._desireBlockedAttackMode = BLOCKED_ATTACK_NONE;
        npc._desireMoveAroundWaitKind = 0;
    }

    public static void leaveMoveAround(final L1MonsterInstance npc) {
        if (npc == null) {
            return;
        }

        /*
         * Do not reset the native dMoveAround counter merely because another
         * desire became active. The counter belongs to CNPC state and is reset
         * by dMoveAround itself on accepted movement; only L1 adapter runtime
         * is discarded here.
         */
        npc._desireMoveAroundDecisionAtMs = 0L;
        npc._desireMoveAroundWaitKind = 0;
        npc._randomMoveDistance = 0;
        npc._randomMoveDirection = 0;
        npc._desireMoveAroundTargetX = 0;
        npc._desireMoveAroundTargetY = 0;
    }

    private void addValue(
            final byte state,
            final int targetId,
            final float value,
            final long nowMs) {

        decay(nowMs);

        // reference server 0x0041EE63..0x0041EE96: -1.0f replaces the separate
        // immediate desire; it neither enters nor modifies the ordinary heap.
        if (value == IMMEDIATE_VALUE) {
            immediateAttackMoveType = immediateAttackFlag =
                    immediateAttackEffectPointBits = immediateAttackOffset = 0;
            immediateState = state;
            immediateTargetId = targetId;
            immediatePresent = true;
            if (activeImmediate) {
                activeState = -1;
                activeTargetId = 0;
            }
            activeImmediate = false;
            return;
        }

        final int existing = findKey(state, targetId);

        if (existing >= 0) {
            // CDesirePQ::AddValue (0x0041EF5E..0x0041EF99):
            // accumulate the exact key and saturate to the native float limit.
            final float accumulated = values[existing] + value;
            values[existing] = accumulated > MAX_DESIRE_VALUE
                    ? MAX_DESIRE_VALUE : accumulated;
            heapDirty = true;
            return;
        }

        // The reference server implementation does not replace the lowest-value desire at
        // DesirePqSize (50). It inserts the new key and may issue a warning
        // when the configured threshold is exceeded. Keep every distinct key.
        ensureCapacity(size + 1);

        states[size] = state;
        targetIds[size] = targetId;
        values[size] = value;
        if (attackPayload != null) {
            final int pos = size << 2;
            attackPayload[pos] = attackPayload[pos + 1] =
                    attackPayload[pos + 2] = attackPayload[pos + 3] = 0;
        }
        size++;
        heapDirty = true;
    }

    private void addSkillValue(
            final int targetId,
            final int skillId,
            final int moveType,
            final int flags,
            final float value,
            final float effectPoint,
            final int extra,
            final long nowMs) {

        decay(nowMs);
        if (value == IMMEDIATE_VALUE) {
            immediateAttackMoveType = immediateAttackFlag =
                    immediateAttackEffectPointBits = immediateAttackOffset = 0;
            immediatePresent = true;
            immediateState = PS_USE_SKILL;
            immediateTargetId = targetId;
            immediateSkillId = skillId;
            immediateSkillMoveType = moveType;
            immediateSkillFlags = flags;
            immediateSkillEffectPoint = effectPoint;
            immediateSkillExtra = extra;
            // Keep an active *ordinary* desire's identity until the next
            // selection. activateTop() must BoostDown that exact ATTACK or
            // USE_SKILL key before switching to the immediate skill.
            // Dropping activeState here leaked the previous +300/+100000 boost.
            if (activeImmediate) {
                activeState = -1;
                activeTargetId = 0;
                activeSkillId = 0;
            }
            activeImmediate = false;
            return;
        }
        int index = findSkillKey(targetId, skillId);
        if (index >= 0) {
            final float sum = values[index] + value;
            values[index] = sum > MAX_DESIRE_VALUE ? MAX_DESIRE_VALUE : sum;
        } else {
            ensureCapacity(size + 1);
            ensureSkillPayload();
            index = size++;
            states[index] = PS_USE_SKILL;
            targetIds[index] = targetId;
            values[index] = value;
            skillIds[index] = skillId;
            // NC CDesirePQ::_AddValue updates only the accumulated value
            // and force on a duplicate key. Preserve the first skill's
            // move type, flags, boost, and action payload.
            skillMoveTypes[index] = moveType;
            skillFlags[index] = flags;
            skillEffectPoints[index] = effectPoint;
            skillExtras[index] = extra;
            if (attackPayload != null) {
                final int pos = index << 2;
                attackPayload[pos] = attackPayload[pos + 1] =
                        attackPayload[pos + 2] = attackPayload[pos + 3] = 0;
            }
        }
        heapDirty = true;
    }

    private int findSkillKey(final int targetId, final int skillId) {
        if (skillIds == null) {
            return -1;
        }
        for (int i = 0; i < size; i++) {
            if (states[i] == PS_USE_SKILL
                    && targetIds[i] == targetId
                    && skillIds[i] == skillId) {
                return i;
            }
        }
        return -1;
    }

    private void ensureSkillPayload() {
        if (skillIds != null) {
            return;
        }
        final int n = states.length;
        skillIds = new int[n];
        skillMoveTypes = new int[n];
        skillFlags = new int[n];
        skillExtras = new int[n];
        skillEffectPoints = new float[n];
    }

    private byte activateTop(
            final L1MonsterInstance npc,
            final long nowMs) {

        decay(nowMs);
        pruneHardInvalidAttackTargets(npc);

        int top = findTopRunnableIndex(npc);

        if (top == -1) {
            addMoveAround();
            top = findTopRunnableIndex(npc);

            if (top == -1) {
                return PS_IDLE;
            }
        }

        final byte topState = top == IMMEDIATE_INDEX
                ? immediateState : states[top];
        final int topTargetId = top == IMMEDIATE_INDEX
                ? immediateTargetId : targetIds[top];
        final int topSkill = topState != PS_USE_SKILL ? 0
                : top == IMMEDIATE_INDEX ? immediateSkillId
                : skillIds != null ? skillIds[top] : 0;

        if (isActiveKey(topState, topTargetId)
                && activeSkillId == topSkill
                && activeImmediate == (top == IMMEDIATE_INDEX)) {
            return topState;
        }

        if (!activeImmediate) {
            // Different skills may share the same target. An active skill's
            // BoostDown must find the exact skill, not the first skill for
            // that target (or another skill permanently loses its value).
            final int active = activeState == PS_USE_SKILL
                    ? findSkillKey(activeTargetId, activeSkillId)
                    : findKey(activeState, activeTargetId);
            if (active >= 0) {
                values[active] -= boostFor(states[active]);
                heapDirty = true;
            }
        }

        activeState = -1;
        activeTargetId = 0;
        activeSkillId = 0;
        activeImmediate = false;

        top = findTopRunnableIndex(npc);
        if (top == IMMEDIATE_INDEX) {
            activeState = immediateState;
            activeTargetId = immediateTargetId;
            activeSkillId = immediateState == PS_USE_SKILL
                    ? immediateSkillId : 0;
            activeImmediate = true;
            return activeState;
        }

        if (top < 0) {
            addMoveAround();
            top = findTopRunnableIndex(npc);
        }

        if (top < 0) {
            return PS_IDLE;
        }

        final float boost = boostFor(states[top]);
        if (boost != 0.0f) {
            values[top] += boost;
            heapDirty = true;
        }

        activeState = states[top];
        activeTargetId = targetIds[top];
        activeSkillId = activeState == PS_USE_SKILL
                ? skillIds[top] : 0;
        return activeState;
    }

    private int findTopRunnableIndex(
            final L1MonsterInstance npc) {

        // reference server _GetTopDesire: the dedicated immediate desire normally wins.
        // The L1 body-occupancy adapter must still suppress an ATTACK whose
        // target is temporarily blocked, or immediate A would endlessly
        // override the fallback heap without a runnable action.
        if (immediatePresent
                && (immediateState != PS_ATTACK
                        || isAttackRunnable(npc, resolveTarget(
                                npc, immediateTargetId)))) {
            return IMMEDIATE_INDEX;
        }
        makeHeapIfDirty();
        if (size == 0) {
            return -1;
        }

        if (states[0] != PS_ATTACK
                || isAttackRunnable(
                        npc,
                        resolveTarget(npc, targetIds[0]))) {
            return 0;
        }

        // L1-specific body-block adapter: a temporarily unrunnable ATTACK
        // remains in the reference design heap. Search the rest only in this case;
        // do not remove, change, or demote its stored desire value.
        int top = -1;
        float topValue = -Float.MAX_VALUE;
        for (int i = 1; i < size; i++) {
            if (states[i] == PS_ATTACK
                    && !isAttackRunnable(
                            npc, resolveTarget(npc, targetIds[i]))) {
                continue;
            }
            if (top < 0 || values[i] > topValue) {
                top = i;
                topValue = values[i];
            }
        }
        return top;
    }

    /**
     * CDesirePQ::AddValue in reference server calls 0x0041CC0C after adding or modifying
     * ordinary desires. That routine constructs a max heap using comparator
     * 0x0041ABC8, which compares the +0xA0 float desire value. Reconstruct
     * the same ordering with parallel primitive arrays (no CDesire objects).
     */
    private void makeHeapIfDirty() {
        if (!heapDirty) {
            return;
        }
        for (int i = (size >>> 1) - 1; i >= 0; i--) {
            siftDown(i);
        }
        heapDirty = false;
    }

    private void siftDown(int parent) {
        final int half = size >>> 1;
        while (parent < half) {
            int child = (parent << 1) + 1;
            final int right = child + 1;
            if (right < size && values[right] > values[child]) {
                child = right;
            }
            if (!(values[child] > values[parent])) {
                return;
            }
            final byte state = states[parent];
            states[parent] = states[child];
            states[child] = state;
            final int target = targetIds[parent];
            targetIds[parent] = targetIds[child];
            targetIds[child] = target;
            final float value = values[parent];
            values[parent] = values[child];
            values[child] = value;
            if (skillIds != null) {
                swapSkillPayload(parent, child);
            }
            if (attackPayload != null) {
                final int a = parent << 2, b = child << 2;
                for (int j = 0; j < 4; j++) {
                    final int p = attackPayload[a + j];
                    attackPayload[a + j] = attackPayload[b + j];
                    attackPayload[b + j] = p;
                }
            }
            parent = child;
        }
    }

    private void swapSkillPayload(final int a, final int b) {
        int v = skillIds[a]; skillIds[a] = skillIds[b]; skillIds[b] = v;
        v = skillMoveTypes[a]; skillMoveTypes[a] = skillMoveTypes[b]; skillMoveTypes[b] = v;
        v = skillFlags[a]; skillFlags[a] = skillFlags[b]; skillFlags[b] = v;
        v = skillExtras[a]; skillExtras[a] = skillExtras[b]; skillExtras[b] = v;
        final float f = skillEffectPoints[a];
        skillEffectPoints[a] = skillEffectPoints[b];
        skillEffectPoints[b] = f;
    }

    private void pruneHardInvalidAttackTargets(
            final L1MonsterInstance npc) {

        if (immediatePresent && immediateState == PS_ATTACK) {
            final L1Character target = resolveTarget(npc, immediateTargetId);
            if (!validActorTarget(npc, target)) {
                removeImmediate();
            }
        }

        for (int i = size - 1; i >= 0; i--) {
            if (states[i] != PS_ATTACK) {
                continue;
            }

            final L1Character target =
                    resolveTarget(
                            npc,
                            targetIds[i]);

            if (target == null
                    || !validActorTarget(
                            npc,
                            target)) {

                if (target != null
                        && target.isInvisble()
                        && !npc.getNpcTemplate().is_agrocoi()) {
                    releaseBlockedAttack(npc, target);
                }
                removeAt(i);
            }
        }

        if (npc != null && immediatePresent
                && immediateState == PS_USE_SKILL
                && immediateTargetId != 0
                && !validActorTarget(npc,
                        resolveTarget(npc, immediateTargetId))) {
            removeImmediate();
        }
        for (int i = size - 1; i >= 0; i--) {
            if (npc != null && states[i] == PS_USE_SKILL
                    && targetIds[i] != 0
                    && !validActorTarget(npc,
                            resolveTarget(npc, targetIds[i]))) {
                removeAt(i);
            }
        }
        if (size == 0) {
            addMoveAround();
        }
    }

    private static L1Character resolveTarget(
            final L1MonsterInstance npc,
            final int targetId) {

        if (npc == null || targetId == 0) {
            return null;
        }

        final L1Character current =
                npc.getTarget();

        if (current != null
                && current.getId() == targetId) {
            return current;
        }

        final L1Object obj =
                L1World.getInstance()
                        .findObject(targetId);

        return obj instanceof L1Character
                ? (L1Character) obj
                : null;
    }

    private static boolean isAttackRunnable(
            final L1MonsterInstance npc,
            final L1Character target) {

        /*
         * Native ATTACK desire runnable filtering is hard target validity.
         * Off-screen zero-hate suppression was an L1 adapter and could hide
         * the active ATTACK desire while the native action still owned it.
         */
        return validActorTarget(npc, target)
                && npc._desireBlockedAttackTargetId
                        != target.getId();
    }


    private void decay(final long nowMs) {
        if (nowMs - lastDecayAt < DECAY_INTERVAL_MS) {
            return;
        }

        // Retail checks the 5000ms gate and performs one Decay pass.
        lastDecayAt = nowMs;

        for (int i = size - 1; i >= 0; i--) {
            final float decay = decayFor(states[i]);

            if (decay == 0.0f) {
                continue;
            }

            values[i] -= decay;
            heapDirty = true;

            if (values[i] <= 0.0f) {
                removeAt(i);
            }
        }

        if (size == 0) {
            addMoveAround();
        }
    }

    private void addMoveAround() {
        if (findKey(PS_MOVE_AROUND, 0) >= 0) {
            return;
        }

        ensureCapacity(size + 1);
        states[size] = PS_MOVE_AROUND;
        targetIds[size] = 0;
        values[size] = MOVE_AROUND_VALUE;
        if (attackPayload != null) {
            final int pos = size << 2;
            attackPayload[pos] = attackPayload[pos + 1] =
                    attackPayload[pos + 2] = attackPayload[pos + 3] = 0;
        }
        size++;
        heapDirty = true;
    }

    private boolean removeKey(
            final byte state,
            final int targetId) {

        if (immediatePresent
                && immediateState == state
                && immediateTargetId == targetId) {
            removeImmediate();
            return true;
        }

        final int index = findKey(state, targetId);
        if (index < 0) {
            return false;
        }

        removeAt(index);

        if (size == 0) {
            addMoveAround();
        }

        return true;
    }

    private void removeAll(final byte state) {
        if (immediatePresent && immediateState == state) {
            removeImmediate();
        }
        for (int i = size - 1; i >= 0; i--) {
            if (states[i] == state) {
                removeAt(i);
            }
        }

        if (size == 0) {
            addMoveAround();
        }
    }

    /**
     * Retail RandomizeDesire behavior: existing values are integer-truncated
     * and permuted among desires of the same state. No target scan/addition.
     */
    private void randomize(final byte state) {
        int count = 0;

        for (int i = 0; i < size; i++) {
            if (states[i] == state) {
                count++;
            }
        }

        if (count <= 1) {
            return;
        }

        final ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // Allocation-free Fisher-Yates over same-state slots.
        for (int logical = count - 1; logical > 0; logical--) {
            final int pick = rnd.nextInt(logical + 1);

            final int a = nthIndex(state, logical);
            final int b = nthIndex(state, pick);

            final float av = (float) ((int) values[a]);
            final float bv = (float) ((int) values[b]);

            values[a] = bv;
            values[b] = av;
        }
        heapDirty = true;
    }

    private int nthIndex(final byte state, final int nth) {
        int n = 0;

        for (int i = 0; i < size; i++) {
            if (states[i] != state) {
                continue;
            }

            if (n++ == nth) {
                return i;
            }
        }

        return -1;
    }

    private void removeImmediate() {
        immediatePresent = false;
        immediateState = 0;
        immediateTargetId = 0;
        immediateSkillId = 0;
        immediateSkillMoveType = 0;
        immediateSkillFlags = 0;
        immediateSkillExtra = 0;
        immediateSkillEffectPoint = 0;
        immediateAttackMoveType = immediateAttackFlag =
                immediateAttackEffectPointBits = immediateAttackOffset = 0;
        if (activeImmediate) {
            activeImmediate = false;
            activeState = -1;
            activeTargetId = 0;
            activeSkillId = 0;
        }
    }

    private void removeAt(final int index) {
        if (index < 0 || index >= size) {
            return;
        }

        if (!activeImmediate && isActiveKey(states[index], targetIds[index])
                && (states[index] != PS_USE_SKILL
                        || activeSkillId == skillIds[index])) {
            activeState = -1;
            activeTargetId = 0;
            activeSkillId = 0;
        }

        final int last = --size;
        heapDirty = true;

        if (index != last) {
            states[index] = states[last];
            targetIds[index] = targetIds[last];
            values[index] = values[last];
            if (skillIds != null) {
                skillIds[index] = skillIds[last];
                skillMoveTypes[index] = skillMoveTypes[last];
                skillFlags[index] = skillFlags[last];
                skillExtras[index] = skillExtras[last];
                skillEffectPoints[index] = skillEffectPoints[last];
            }
            if (attackPayload != null) {
                final int a = index << 2, b = last << 2;
                for (int j = 0; j < 4; j++) {
                    attackPayload[a + j] = attackPayload[b + j];
                }
            }
        }
        if (attackPayload != null) {
            final int pos = last << 2;
            attackPayload[pos] = attackPayload[pos + 1] =
                    attackPayload[pos + 2] = attackPayload[pos + 3] = 0;
        }
        if (skillIds != null) {
            skillIds[last] = 0;
            skillMoveTypes[last] = 0;
            skillFlags[last] = 0;
            skillExtras[last] = 0;
            skillEffectPoints[last] = 0.0f;
        }

        states[last] = 0;
        targetIds[last] = 0;
        values[last] = 0.0f;
    }

    private int findKey(
            final byte state,
            final int targetId) {

        for (int i = 0; i < size; i++) {
            if (states[i] == state
                    && targetIds[i] == targetId) {
                return i;
            }
        }

        return -1;
    }

    private boolean isActiveKey(
            final byte state,
            final int targetId) {

        return state == activeState
                && targetId == activeTargetId;
    }

    private void ensureCapacity(final int wanted) {
        if (wanted <= states.length) {
            return;
        }

        int next = states.length << 1;
        if (next < wanted) {
            next = wanted;
        }
        // Array-backed L1 representation of reference server's growable desire sequence.
        // Retaining >50 entries is essential: DesirePqSize is not a cap.

        final byte[] newStates = new byte[next];
        final int[] newTargets = new int[next];
        final float[] newValues = new float[next];

        System.arraycopy(states, 0, newStates, 0, size);
        System.arraycopy(targetIds, 0, newTargets, 0, size);
        System.arraycopy(values, 0, newValues, 0, size);

        states = newStates;
        targetIds = newTargets;
        values = newValues;
        if (skillIds != null) {
            skillIds = java.util.Arrays.copyOf(skillIds, next);
            skillMoveTypes = java.util.Arrays.copyOf(skillMoveTypes, next);
            skillFlags = java.util.Arrays.copyOf(skillFlags, next);
            skillExtras = java.util.Arrays.copyOf(skillExtras, next);
            skillEffectPoints = java.util.Arrays.copyOf(skillEffectPoints, next);
        }
        if (attackPayload != null) {
            attackPayload = java.util.Arrays.copyOf(attackPayload, next << 2);
        }
    }

    private static float decayFor(final byte state) {
        if (state == PS_ATTACK) return ATTACK_DECAY;
        if (state == PS_USE_SKILL) return USE_SKILL_DECAY;
        return 0.0f;
    }

    private static float boostFor(final byte state) {
        if (state == PS_ATTACK) return ATTACK_BOOST;
        if (state == PS_USE_SKILL) return USE_SKILL_BOOST;
        return 0.0f;
    }

    private static boolean validActorTarget(
            final L1MonsterInstance npc,
            final L1Character target) {

        if (npc == null
                || target == null
                || target.getId() == npc.getId()
                || target.isDead()
                || target.getCurrentHp() <= 0
                || target.getMapId() != npc.getMapId()
                || (target.isInvisble() && !npc.getNpcTemplate().is_agrocoi())) {

            return false;
        }

//        if (target instanceof L1PcInstance) {
//            final L1PcInstance pc =
//                    (L1PcInstance) target;
//
//            if (pc.isGm()
//                    || pc.isGmInvis()
//                    || pc.isGhost()) {
//
//                return false;
//            }
//        }

        return true;
    }
}
