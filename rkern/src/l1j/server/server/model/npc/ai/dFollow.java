package l1j.server.server.model.npc.ai;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.npc.ai.action.SetMoving;

public final class dFollow {

    private static final int FOLLOW_RADIUS = 2;

    private static final int[] DX =
            { 0, 1, 1, 1, 0, -1, -1, -1 };

    private static final int[] DY =
            { -1, -1, 0, 1, 1, 1, 0, -1 };

    private static final WorldCollisionHash COLLISION =
            WorldCollisionHash.getInstance();

    private dFollow() {
    }

    public static boolean follow(
            final L1NpcInstance follower,
            final L1Character master) {

        if (follower == null || master == null) {
            return true;
        }

        if (follower.getMapId() != master.getMapId()
                || follower.getMap() == null) {
            SetMoving.clear(follower);
            return true;
        }

        final int distance =
                follower.getLocation()
                        .getTileLineDistance(master.getLocation());

        if (distance <= FOLLOW_RADIUS) {
            SetMoving.clear(follower);
            return false;
        }

        final int packed =
                getFollowPosition(
                        follower,
                        master);

        if (packed == 0) {
            SetMoving.clear(follower);
            follower.setSleepTime(300L);
            return false;
        }

        final int targetX =
                unpackX(packed);

        final int targetY =
                unpackY(packed);

        final byte result =
                SetMoving.tickToPoint(
                        follower,
                        targetX,
                        targetY);

        if (result == SetMoving.INVALID) {
            SetMoving.clear(follower);
        }

        return false;
    }

 
    private static int getFollowPosition(
            final L1NpcInstance follower,
            final L1Character master) {

        final int start =
                ThreadLocalRandom.current()
                        .nextInt(8);

        final int masterX =
                master.getX();

        final int masterY =
                master.getY();

        final short mapId =
                (short) master.getMapId();

        long bestDistanceSq =
                Long.MAX_VALUE;

        int bestPacked = 0;

        for (int i = 0; i < 8; i++) {
            final int dir =
                    (start + i) & 7;

            final int x =
                    masterX
                    + DX[dir] * FOLLOW_RADIUS;

            final int y =
                    masterY
                    + DY[dir] * FOLLOW_RADIUS;

            if (!master.getMap().isInMap(x, y)
                    || !master.getMap().isPassable(x, y)) {
                continue;
            }

            if (!COLLISION.canEnter(
                    mapId,
                    x,
                    y,
                    follower.getId())) {
                continue;
            }

            final long dx =
                    (long) x - follower.getX();

            final long dy =
                    (long) y - follower.getY();

            final long distanceSq =
                    dx * dx + dy * dy;

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq =
                        distanceSq;

                bestPacked =
                        pack(x, y);
            }
        }

        return bestPacked;
    }

    private static int pack(
            final int x,
            final int y) {

        return (x << 16)
                | (y & 0xffff);
    }

    private static int unpackX(
            final int packed) {

        return (packed >>> 16) & 0xffff;
    }

    private static int unpackY(
            final int packed) {

        return packed & 0xffff;
    }
}
