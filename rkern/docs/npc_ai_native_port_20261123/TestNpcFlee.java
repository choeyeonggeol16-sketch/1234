package l1j.server.server.model.npc.ai.action;

import java.lang.reflect.Field;
import java.util.Arrays;
import sun.misc.Unsafe;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1HateList;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;

/** Production dFlee/dFleeEx policy and exact-target action handoff regression. */
public final class TestNpcFlee {
    private TestNpcFlee() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static L1MonsterInstance fixture(
            final Unsafe unsafe,
            final Field location,
            final Field health,
            final int id,
            final int x,
            final int y,
            final L1Map map) throws Exception {
        final L1MonsterInstance npc =
                (L1MonsterInstance) unsafe.allocateInstance(L1MonsterInstance.class);
        npc.setId(id);
        location.set(npc, new L1Location(x, y, map));
        health.setInt(npc, 100);
        npc._hateList = new L1HateList();
        return npc;
    }

    public static void main(String[] args) throws Exception {
        final Field uf = Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        final Unsafe unsafe = (Unsafe) uf.get(null);
        final Field location = L1Object.class.getDeclaredField("_loc");
        location.setAccessible(true);
        final Field health = L1Character.class.getDeclaredField("_currentHp");
        health.setAccessible(true);

        final byte[][] terrain = new byte[48][48];
        for (byte[] col : terrain) Arrays.fill(col, (byte) 0x03);
        final L1Map map = new L1V1Map(992, terrain, 0, 0,
                false, true, true, true, true, true,
                true, true, true, true, true, false);
        final L1MonsterInstance npc = fixture(unsafe, location, health,
                98001, 10, 10, map);
        final L1MonsterInstance targetA = fixture(unsafe, location, health,
                98002, 30, 10, map);
        final L1MonsterInstance targetB = fixture(unsafe, location, health,
                98003, 12, 10, map);

        // NC dFlee 1500 world units -> existing L1 15-tile leash;
        // dFlee uses >=, while NC dFleeEx uses > (not >=).
        check(!dFlee.reachedDistance(14, 15, false),
                "default flee within 15 tiles must continue");
        check(dFlee.reachedDistance(15, 15, false),
                "native dFlee equality must finish");
        check(!dFlee.reachedDistance(15, 15, true),
                "native dFleeEx equality must not finish");
        check(dFlee.reachedDistance(16, 15, true),
                "native dFleeEx beyond threshold must finish");

        check(dFlee.dFlee(npc, targetA)
                    == CCreatureController.RESULT_DONE,
                "dFlee target 20 tiles away is already complete");
        check(dFlee.dFleeEx(npc, targetA, 15)
                    == CCreatureController.RESULT_DONE,
                "dFleeEx 20 > 15 completes");
        check(CEscapeAction.onTick(npc, targetA)
                    == CCreatureController.RESULT_DONE,
                "CEscapeAction delegates to native dFlee");
        check(CEscapeAction.onTickEx(npc, targetA, 15)
                    == CCreatureController.RESULT_DONE,
                "CEscapeAction exposes native extended flee");

        // dFleeEx invalid/dead target is a completed *current pass*, and the
        // default action retires only its own captured key on completion.
        targetA.setDead(true);
        check(dFlee.dFlee(npc, targetA)
                    == CCreatureController.RESULT_DONE,
                "standard flee completes when target dies");
        check(dFlee.dFleeEx(npc, targetA, 15)
                    == CCreatureController.RESULT_INVALID,
                "extended flee rejects dead target without moving");
        targetA.setDead(false);
        check(dFlee.dFlee(npc, null)
                    == CCreatureController.RESULT_INVALID,
                "null target must not move");
        check(dFlee.dFleeEx(npc, (L1Character) null, 15)
                    == CCreatureController.RESULT_INVALID,
                "extended null target must not move");

        // The crucial stale-finish race: FLEE(A) exits after B became the
        // current target. A cleanup must not clear B's target or its hate.
        npc._target = targetB;
        npc._aiAction = CCreatureController.ACTION_ESCAPE;
        npc._hateList.add(targetA, 1);
        npc._hateList.add(targetB, 10);
        CEscapeAction.onEnd(npc, targetA, CCreatureController.RESULT_DONE);
        check(npc.getTarget() == targetB,
                "FLEE(A) completion must preserve successor B");
        check(npc._hateList.containsKey(targetB),
                "FLEE(A) completion must preserve successor B hate");
        check(!npc._hateList.containsKey(targetA),
                "FLEE(A) completion must retire A hate only");
        check(npc._aiAction == CCreatureController.ACTION_THINK,
                "FLEE(A) completion must request new desire arbitration");

        // Regular exit removes the exact current target and returns to THINK.
        npc._target = targetA;
        npc._aiAction = CCreatureController.ACTION_ESCAPE;
        npc._hateList.add(targetA, 1);
        CEscapeAction.onEnd(npc, targetA, CCreatureController.RESULT_DONE);
        check(npc.getTarget() == null, "FLEE current target is retired");
        check(npc._aiAction == CCreatureController.ACTION_THINK,
                "FLEE completion must return to THINK");

        // The no-target owner tick must never dereference a null hate key.
        npc._aiAction = CCreatureController.ACTION_ESCAPE;
        CEscapeAction.onEnd(npc, null, CCreatureController.RESULT_INVALID);
        check(npc._aiAction == CCreatureController.ACTION_THINK,
                "invalid flee returns to THINK");

        System.out.println("PASS NC dFlee/dFleeEx: threshold equality, "
                + "dead/invalid target, action dispatch, exact-target cleanup, "
                + "successor B preservation");
    }
}
