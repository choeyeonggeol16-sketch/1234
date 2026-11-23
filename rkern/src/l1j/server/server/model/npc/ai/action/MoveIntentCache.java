package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.L1Character;

/**
 * NPC movement intent cache.
 *
 * AI owner thread별 primitive direct-map 하나만 유지한다.
 * NPC 객체에는 추가 상태를 넣지 않고 target/follow destination,
 * wait state, no-progress watchdog 상태를 재사용한다.
 *
 * direct-map 충돌은 key miss로만 동작하며 잘못된 상태를 재사용하지 않는다.
 * per-tick allocation 없음.
 */
final class MoveIntentCache {

    static final long NO_POS = Long.MIN_VALUE;

    static final byte KIND_NONE   = 0;
    static final byte KIND_COMBAT = 1;
    static final byte KIND_FOLLOW = 2;

    private static final int CACHE_SIZE = 4096;
    private static final int CACHE_MASK = CACHE_SIZE - 1;

    private static final ThreadLocal<State> LOCAL =
            ThreadLocal.withInitial(State::new);

    private MoveIntentCache() {
    }

    static long getGoal(
            final L1NpcInstance npc,
            final byte kind,
            final int anchorId,
            final int anchorX,
            final int anchorY,
            final int maxAnchorDrift) {

        if (npc == null || npc.getId() == 0) {
            return NO_POS;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        final long key = key(npc);

        if (s.key[slot] != key
                || s.kind[slot] != kind
                || s.anchorId[slot] != anchorId) {
            return NO_POS;
        }

        final long anchor = s.anchorPos[slot];
        if (anchor == NO_POS
                || chebyshev(
                        anchorX,
                        anchorY,
                        unpackX(anchor),
                        unpackY(anchor)) > maxAnchorDrift) {
            clearSlot(s, slot);
            return NO_POS;
        }

        return s.goalPos[slot];
    }

    static void setGoal(
            final L1NpcInstance npc,
            final byte kind,
            final int anchorId,
            final int anchorX,
            final int anchorY,
            final int goalX,
            final int goalY) {

        if (npc == null || npc.getId() == 0) {
            return;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());

        initSlot(s, slot, npc);
        s.kind[slot] = kind;
        s.anchorId[slot] = anchorId;
        s.anchorPos[slot] = pack(anchorX, anchorY);
        s.goalPos[slot] = pack(goalX, goalY);
    }

    static void clearGoal(final L1NpcInstance npc) {
        clearGoalAndReport(npc);
    }

    /**
     * Release owner-local movement goal and report whether an actual intent
     * existed. Used by NC dFollow to invalidate PathFinder only once on
     * arrival, rather than on every idle companion/summon/doll/pet tick.
     */
    static boolean clearGoalAndReport(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) {
            return false;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());

        if (s.key[slot] != key(npc)) {
            return false;
        }
        final boolean hadGoal =
                s.kind[slot] != KIND_NONE
                || s.goalPos[slot] != NO_POS;
        s.kind[slot] = KIND_NONE;
        s.anchorId[slot] = 0;
        s.anchorPos[slot] = NO_POS;
        s.goalPos[slot] = NO_POS;
        return hadGoal;
    }

    static int reportMove(
            final L1NpcInstance npc,
            final int beforeX,
            final int beforeY,
            final int afterX,
            final int afterY,
            final int goalX,
            final int goalY) {

        if (npc == null || npc.getId() == 0) {
            return 0;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        initSlot(s, slot, npc);

        final int beforeH = chebyshev(beforeX, beforeY, goalX, goalY);
        final int afterH = chebyshev(afterX, afterY, goalX, goalY);
        final long previousOrigin = s.previousOrigin[slot];
        final long after = pack(afterX, afterY);

        int count = s.noProgress[slot] & 0xff;
        final int beforeCount = count;

        if (afterH < beforeH) {
            count = 0;
        } else if (previousOrigin != NO_POS && after == previousOrigin) {
            count += 2;
        } else {
            count++;
        }

        /* threshold를 +2 cycle increment가 건너뛰지 않게 crossing을 정규화한다. */
        if (beforeCount < 4 && count > 4) {
            count = 4;
        } else if (beforeCount < 8 && count > 8) {
            count = 8;
        }

        if (count > 127) {
            count = 127;
        }

        s.noProgress[slot] = (byte) count;
        s.previousOrigin[slot] = pack(beforeX, beforeY);
        return count;
    }

    /**
     * L1 NC-style chase continuity: report only physical reverse steps
     * against an unchanged target. A normal curved detour does not count.
     * Uses the existing owner-local primitive movement cache.
     */
    static boolean reportChaseStep(final L1NpcInstance npc,
            final L1Character target, final int beforeX, final int beforeY,
            final int afterX, final int afterY) {
        if (npc == null || target == null || npc.getId() == 0
                || (beforeX == afterX && beforeY == afterY)) {
            return false;
        }
        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        initSlot(s, slot, npc);
        final long targetPos = pack(target.getX(), target.getY());
        if (s.kind[slot] != KIND_COMBAT
                || s.anchorId[slot] != target.getId()
                || s.anchorPos[slot] != targetPos) {
            s.kind[slot] = KIND_COMBAT;
            s.anchorId[slot] = target.getId();
            s.anchorPos[slot] = targetPos;
            s.goalPos[slot] = NO_POS;
            s.passingUntil[slot] = 0L;
            s.rejectedMeleeMask[slot] = 0;
            s.waitUntil[slot] = 0L;
            s.waitReason[slot] = 0;
            s.previousOrigin[slot] = NO_POS;
            s.noProgress[slot] = 0;
        }
        final boolean reversed = s.previousOrigin[slot] != NO_POS
                && s.previousOrigin[slot] == pack(afterX, afterY);
        final int reversals = reversed
                ? Math.min(2, (s.noProgress[slot] & 0xff) + 1) : 0;
        s.previousOrigin[slot] = pack(beforeX, beforeY);
        s.noProgress[slot] = (byte) reversals;
        if (reversals < 2) return false;
        // The step is already committed; only the next timer may move again.
        s.previousOrigin[slot] = NO_POS;
        s.noProgress[slot] = 0;
        return true;
    }

    /**
     * Reject multiple failed melee tiles around one target for a bounded
     * interval, using one primitive mask per owner-local NPC cache slot.
     * A single last-rejected tile let dense groups alternate A and B forever.
     */
    static void rejectMeleeAttackPosition(final L1NpcInstance npc,
            final L1Character target, final int x, final int y,
            final long untilMs) {
        if (npc == null || target == null || npc.getId() == 0) return;
        final int bit = meleePositionBit(target.getX(), target.getY(), x, y);
        if (bit == 0) return;

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        initSlot(s, slot, npc);
        final long targetPos = pack(target.getX(), target.getY());
        final long nowMs = System.currentTimeMillis();
        if (s.kind[slot] != KIND_COMBAT
                || s.anchorId[slot] != target.getId()
                || s.anchorPos[slot] != targetPos
                || s.waitReason[slot] != 1
                || nowMs >= s.waitUntil[slot]) {
            s.rejectedMeleeMask[slot] = 0;
            s.waitUntil[slot] = 0L;
        }
        s.kind[slot] = KIND_COMBAT;
        s.anchorId[slot] = target.getId();
        s.anchorPos[slot] = targetPos;
        s.rejectedMeleeMask[slot] |= (byte) bit;
        s.waitReason[slot] = 1;
        s.waitUntil[slot] = Math.max(s.waitUntil[slot], untilMs);
        s.previousOrigin[slot] = NO_POS;
        s.noProgress[slot] = 0;
    }

    static boolean rejectedMeleeAttackPosition(final L1NpcInstance npc,
            final L1Character target, final int x, final int y,
            final long nowMs) {
        if (npc == null || target == null || npc.getId() == 0) return false;
        final int bit = meleePositionBit(target.getX(), target.getY(), x, y);
        if (bit == 0) return false;
        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        return s.key[slot] == key(npc)
                && s.kind[slot] == KIND_COMBAT
                && s.anchorId[slot] == target.getId()
                && s.anchorPos[slot] == pack(target.getX(), target.getY())
                && s.waitReason[slot] == 1
                && nowMs < s.waitUntil[slot]
                && ((s.rejectedMeleeMask[slot] & 0xff) & bit) != 0;
    }

    private static int meleePositionBit(final int tx, final int ty,
            final int x, final int y) {
        final int dx = x - tx;
        final int dy = y - ty;
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1
                || (dx | dy) == 0) return 0;
        final int heading;
        if (dy < 0) {
            heading = dx < 0 ? 7 : (dx > 0 ? 1 : 0);
        } else if (dy > 0) {
            heading = dx < 0 ? 5 : (dx > 0 ? 3 : 4);
        } else {
            heading = dx > 0 ? 2 : 6;
        }
        return 1 << heading;
    }

    /** Bounded owner-local NC passing waypoint; never crosses a retarget. */
    static void setPassingDestination(final L1NpcInstance npc,
            final L1Character target, final int x, final int y,
            final long untilMs) {
        if (npc == null || target == null || npc.getId() == 0) return;
        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        initSlot(s, slot, npc);
        s.kind[slot] = KIND_COMBAT;
        s.anchorId[slot] = target.getId();
        s.anchorPos[slot] = pack(target.getX(), target.getY());
        s.goalPos[slot] = pack(x, y);
        s.passingUntil[slot] = untilMs;
    }

    static long getPassingDestination(final L1NpcInstance npc,
            final L1Character target, final long nowMs) {
        if (npc == null || target == null || npc.getId() == 0) return NO_POS;
        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        if (s.key[slot] != key(npc)
                || s.kind[slot] != KIND_COMBAT
                || s.anchorId[slot] != target.getId()
                || s.anchorPos[slot] != pack(target.getX(), target.getY())
                || nowMs >= s.passingUntil[slot]) {
            if (s.key[slot] == key(npc)) {
                s.goalPos[slot] = NO_POS;
                s.passingUntil[slot] = 0L;
            }
            return NO_POS;
        }
        return s.goalPos[slot];
    }

    static void clearPassingDestination(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) return;
        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        if (s.key[slot] == key(npc) && s.kind[slot] == KIND_COMBAT) {
            s.goalPos[slot] = NO_POS;
            s.passingUntil[slot] = 0L;
        }
    }

    static void clearProgress(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) {
            return;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());

        if (s.key[slot] == key(npc)) {
            s.noProgress[slot] = 0;
            s.previousOrigin[slot] = NO_POS;
        }
    }

    static void beginWait(
            final L1NpcInstance npc,
            final long untilMs,
            final byte reason) {

        if (npc == null || npc.getId() == 0) {
            return;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        initSlot(s, slot, npc);

        s.waitUntil[slot] = untilMs;
        s.waitReason[slot] = reason;
    }

    static long waitUntil(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) {
            return 0L;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        return s.key[slot] == key(npc) ? s.waitUntil[slot] : 0L;
    }

    static byte waitReason(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) {
            return 0;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        return s.key[slot] == key(npc) ? s.waitReason[slot] : 0;
    }

    static void clearWait(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) {
            return;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());

        if (s.key[slot] == key(npc)) {
            s.waitUntil[slot] = 0L;
            s.waitReason[slot] = 0;
        }
    }

    static void clearAll(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) {
            return;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());
        if (s.key[slot] == key(npc)) {
            clearSlot(s, slot);
        }
    }

    static long pack(final int x, final int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    static int unpackX(final long packed) {
        return (int) (packed >> 32);
    }

    static int unpackY(final long packed) {
        return (int) packed;
    }

    private static void initSlot(
            final State s,
            final int slot,
            final L1NpcInstance npc) {

        final long key = key(npc);
        if (s.key[slot] == key) {
            return;
        }

        clearSlot(s, slot);
        s.key[slot] = key;
    }

    private static void clearSlot(final State s, final int slot) {
        s.key[slot] = 0L;
        s.kind[slot] = KIND_NONE;
        s.anchorId[slot] = 0;
        s.anchorPos[slot] = NO_POS;
        s.goalPos[slot] = NO_POS;
        s.passingUntil[slot] = 0L;
        s.previousOrigin[slot] = NO_POS;
        s.noProgress[slot] = 0;
        s.rejectedMeleeMask[slot] = 0;
        s.waitUntil[slot] = 0L;
        s.waitReason[slot] = 0;
    }

    private static long key(final L1NpcInstance npc) {
        return ((long) npc.getId() << 32)
                ^ (npc.getMapId() & 0xffffffffL);
    }

    private static int slot(final int actorId) {
        return actorId & CACHE_MASK;
    }

    private static int chebyshev(
            final int x0,
            final int y0,
            final int x1,
            final int y1) {
        return Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    }

    private static final class State {
        final long[] key = new long[CACHE_SIZE];
        final byte[] kind = new byte[CACHE_SIZE];
        final int[] anchorId = new int[CACHE_SIZE];
        final long[] anchorPos = new long[CACHE_SIZE];
        final long[] goalPos = new long[CACHE_SIZE];
        final long[] passingUntil = new long[CACHE_SIZE];
        final long[] previousOrigin = new long[CACHE_SIZE];
        final byte[] noProgress = new byte[CACHE_SIZE];
        final byte[] rejectedMeleeMask = new byte[CACHE_SIZE];
        final long[] waitUntil = new long[CACHE_SIZE];
        final byte[] waitReason = new byte[CACHE_SIZE];

        State() {
            for (int i = 0; i < CACHE_SIZE; i++) {
                anchorPos[i] = NO_POS;
                goalPos[i] = NO_POS;
                previousOrigin[i] = NO_POS;
            }
        }
    }
}
