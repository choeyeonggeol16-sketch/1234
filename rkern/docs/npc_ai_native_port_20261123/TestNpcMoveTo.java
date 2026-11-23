package l1j.server.server.model.npc.ai.action;

import java.lang.reflect.Field;
import java.util.Arrays;
import sun.misc.Unsafe;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;

/**
 * Production NC dMoveTo/dMoveToTarget action regression on Java 21.
 * No game world boot, MySQL, network or synthetic item transactions.
 */
public final class TestNpcMoveTo {
    private TestNpcMoveTo() {
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static L1MonsterInstance fixture(final Unsafe unsafe,
            final Field loc, final Field hp,
            final int id, final int x, final int y, final L1Map map)
            throws Exception {
        final L1MonsterInstance npc = (L1MonsterInstance)
                unsafe.allocateInstance(L1MonsterInstance.class);
        npc.setId(id);
        loc.set(npc, new L1Location(x, y, map));
        hp.setInt(npc, 100);
        return npc;
    }

    public static void main(final String[] args) throws Exception {
        final Field u = Unsafe.class.getDeclaredField("theUnsafe");
        u.setAccessible(true);
        final Unsafe unsafe = (Unsafe) u.get(null);
        final Field loc = L1Object.class.getDeclaredField("_loc");
        loc.setAccessible(true);
        final Field hp = L1Character.class.getDeclaredField("_currentHp");
        hp.setAccessible(true);

        final byte[][] open = new byte[128][128];
        for (byte[] column : open) Arrays.fill(column, (byte) 0x03);
        final L1Map map = new L1V1Map(996, open, 0, 0,
                false, true, true, true, true, true,
                true, true, true, true, true, false);
        final L1MonsterInstance npc =
                fixture(unsafe, loc, hp, 975101, 10, 10, map);
        final L1MonsterInstance target =
                fixture(unsafe, loc, hp, 975102, 12, 10, map);

        // NC dMoveTo: sqrt(1000) world units maps to exact L1 tile.
        check(dMoveTo.dMoveTo(npc, 10, 10)
                == CCreatureController.RESULT_DONE,
                "dMoveTo exact-tile arrival must finish");
        check(dMoveTo.isLongMove(0, 0, 82, 0),
                "8192 world units maps to >81 L1 cells");
        check(!dMoveTo.isLongMove(0, 0, 81, 0),
                "below native long-jump threshold must follow the route");
        check(dMoveTo.isLongMove(0, 0, 58, 58),
                "NC long-jump threshold uses Euclidean squared distance");
        check(!dMoveTo.validDestination(npc, 130, 10),
                "out-of-bounds L1 static MoveTo destination rejected");

        npc._scriptMoveMapId = npc.getMapId();
        npc._scriptMoveGoalXY = MoveIntentCache.pack(10, 10);
        npc._aiAction = CCreatureController.ACTION_MOVE_TO_POINT;
        check(CMoveAction.onTick(npc, CCreatureController.ACTION_MOVE_TO_POINT)
                == CCreatureController.RESULT_DONE,
                "CIO ACTION_MOVE_TO_POINT executes real NC dMoveTo");
        npc._scriptMoveMapId = 123;
        check(CMoveAction.onTick(npc, CCreatureController.ACTION_MOVE_TO_POINT)
                == CCreatureController.RESULT_INVALID,
                "teleport/map-change invalidates old static MoveTo request");
        npc._scriptMoveMapId = npc.getMapId();

        // NC dMoveToTarget: 4000-world-unit leash is >40, not >=40.
        check(!dMoveToTarget.outsideLeash(40L, 0L),
                "exactly 40 tiles remains within native MOVE_TO_TARGET leash");
        check(dMoveToTarget.outsideLeash(41L, 0L),
                "beyond 40 tiles must retire the native MOVE_TO_TARGET");
        check(dMoveToTarget.outsideLeash(29L, 29L),
                "native MOVE_TO_TARGET uses Euclidean range, not Chebyshev");
        check(dMoveToTarget.reached(2L, 0L, 2)
                && !dMoveToTarget.reached(2L, 0L, 1),
                "caller stopDistance plus character occupancy radius");

        // A character owns its occupied tile: native radius maps to 1 L1
        // tile. Explicit stopDistance 1 therefore completes at 2 tiles.
        check(dMoveToTarget.dMoveToTarget(npc, target, 1)
                == CCreatureController.RESULT_DONE,
                "scripted target move stops 2 tiles out with radius +1");
        check(dMoveToTarget.dMoveToTarget(npc, target, 2)
                == CCreatureController.RESULT_DONE,
                "larger scripted stopDistance completes without moving");
        check(dMoveToTarget.dMoveToTarget(npc, target, Integer.MAX_VALUE)
                == CCreatureController.RESULT_DONE,
                "extended script stopDistance cannot overflow radius addition");

        target.getLocation().set(51, 10, map);
        check(dMoveToTarget.dMoveToTarget(npc, target, 1)
                == CCreatureController.RESULT_INVALID,
                "target beyond native leash is rejected before pathfinding");
        target.getLocation().set(50, 10, map);
        check(dMoveToTarget.dMoveToTarget(npc, target, 39)
                == CCreatureController.RESULT_DONE,
                "exact 40-tile boundary does not fail the leash");
        target.getLocation().set(12, 10, map);
        target.setDead(true);
        check(dMoveToTarget.dMoveToTarget(npc, target, 1)
                == CCreatureController.RESULT_INVALID,
                "dead scripted target must never move the NPC");
        target.setDead(false);

        // Completed native MOVE_TO only retires its own destination fields.
        // It must not drop another NPC's combat target or stop all AI.
        npc._target = target;
        npc._aiAction = CCreatureController.ACTION_MOVE_TO_POINT;
        npc._scriptMoveMapId = npc.getMapId();
        npc._scriptMoveGoalXY = MoveIntentCache.pack(10, 10);
        CMoveAction.onEnd(npc, CCreatureController.ACTION_MOVE_TO_POINT,
                CCreatureController.RESULT_DONE);
        check(npc._scriptMoveMapId == -1 && npc._scriptMoveGoalXY == 0L,
                "scripted destination retired after completion");
        check(npc._target == target,
                "noncombat movement must preserve unrelated combat target");
        check(npc._aiAction == CCreatureController.ACTION_THINK,
                "completed scripted move asks CIO to arbitrate next desire");

        npc._aiAction = CCreatureController.ACTION_MOVE_TO_OBJECT;
        npc._scriptMoveTargetId = target.getId();
        npc._scriptMoveMapId = npc.getMapId();
        npc._pathFindFailCount = 15;
        CMoveAction.onEnd(npc, CCreatureController.ACTION_MOVE_TO_OBJECT,
                CCreatureController.RESULT_INVALID);
        check(npc._aiAction == CCreatureController.ACTION_THINK,
                "failed NC scripted movement must not unregister NPC AI");
        check(npc._scriptMoveTargetId == 0 && npc._scriptMoveMapId == -1,
                "failed target move must retire only captured script state");
        check(npc._target == target,
                "failed scripted movement must not erase combat target");

        System.out.println("PASS NC dMoveTo/dMoveToTarget: exact arrival, "
                + "82-tile long move, 40-tile leash, 2D stop radius, "
                + "dead/map-invalid target, CIO action dispatch, "
                + "completion/failure preserving combat owner");
    }
}
