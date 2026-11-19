package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1NpcInstance;

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
        if (npc == null || npc.getId() == 0) {
            return;
        }

        final State s = LOCAL.get();
        final int slot = slot(npc.getId());

        if (s.key[slot] == key(npc)) {
            s.kind[slot] = KIND_NONE;
            s.anchorId[slot] = 0;
            s.anchorPos[slot] = NO_POS;
            s.goalPos[slot] = NO_POS;
        }
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
        s.previousOrigin[slot] = NO_POS;
        s.noProgress[slot] = 0;
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
        final long[] previousOrigin = new long[CACHE_SIZE];
        final byte[] noProgress = new byte[CACHE_SIZE];
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
