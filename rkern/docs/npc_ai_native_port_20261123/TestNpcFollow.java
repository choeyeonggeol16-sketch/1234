package l1j.server.server.model.npc.ai.action;

import java.lang.reflect.Field;
import java.util.Arrays;
import sun.misc.Unsafe;

import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;

/**
 * Java 21 production dFollow / GetFollowPosition regression, no MySQL/world.
 *
 * The tiny in-memory L1 map exercises the real 8-way passability and
 * WorldCollisionHash destination checks; follower state is reused by CIO.
 */
public final class TestNpcFollow {
    private TestNpcFollow() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static L1MonsterInstance fixture(
            final Unsafe unsafe, final Field location,
            final int id, final int x, final int y, final L1Map map)
            throws Exception {
        final L1MonsterInstance mob =
                (L1MonsterInstance) unsafe.allocateInstance(
                        L1MonsterInstance.class);
        mob.setId(id);
        location.set(mob, new L1Location(x, y, map));
        return mob;
    }

    public static void main(String[] args) throws Exception {
        final Field uf = Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        final Unsafe unsafe = (Unsafe) uf.get(null);
        final Field location = L1Object.class.getDeclaredField("_loc");
        location.setAccessible(true);

        final byte[][] open = new byte[32][32];
        for (byte[] column : open) Arrays.fill(column, (byte) 0x03);
        final L1Map map = new L1V1Map(
                902, open, 0, 0,
                false, true, true, true, true, true,
                true, true, true, true, true, false);

        final L1MonsterInstance follower =
                fixture(unsafe, location, 900001, 10, 16, map);
        final L1MonsterInstance leader =
                fixture(unsafe, location, 900002, 16, 16, map);

        final long selected = CMoveAction.GetFollowPosition(
                follower, leader);
        check(selected == MoveIntentCache.pack(14, 16),
                "NC GetFollowPosition must choose nearest free radius-2 slot");

        MoveIntentCache.setGoal(
                follower, MoveIntentCache.KIND_FOLLOW,
                leader.getId(), leader.getX(), leader.getY(),
                14, 16);
        check(MoveIntentCache.getGoal(
                follower, MoveIntentCache.KIND_FOLLOW,
                leader.getId(), 16, 16, 2) == selected,
                "same anchor must reuse the existing destination");

        leader.getLocation().set(17, 16, map);
        check(MoveIntentCache.getGoal(
                follower, MoveIntentCache.KIND_FOLLOW,
                leader.getId(), 17, 16, 2) == selected,
                "one-tile master drift retains current follow goal");

        leader.getLocation().set(20, 16, map);
        check(MoveIntentCache.getGoal(
                follower, MoveIntentCache.KIND_FOLLOW,
                leader.getId(), 20, 16, 2) == MoveIntentCache.NO_POS,
                "master moving beyond allowed drift invalidates goal");

        leader.getLocation().set(16, 16, map);
        follower.getLocation().set(15, 16, map);
        MoveIntentCache.setGoal(
                follower, MoveIntentCache.KIND_FOLLOW,
                leader.getId(), 16, 16, 14, 16);
        check(!dFollow.follow(follower, leader),
                "L1 distance <=2 must stop normal follow without stopping AI");
        check(MoveIntentCache.getGoal(
                follower, MoveIntentCache.KIND_FOLLOW,
                leader.getId(), 16, 16, 2) == MoveIntentCache.NO_POS,
                "following complete: stale slot must be released");

        // The actual modern L1 pet is L1CompanionInstance (NOT L1Pet).
        // Exercise its noTarget() call path with a PC master. This catches
        // the old bug where noTarget skipped dFollow at <=2 tiles, leaving
        // the previous owner-local follow destination alive.
        final L1CompanionInstance companion =
                (L1CompanionInstance) unsafe.allocateInstance(L1CompanionInstance.class);
        final L1PcInstance pcMaster =
                (L1PcInstance) unsafe.allocateInstance(L1PcInstance.class);
        companion.setId(900003);
        pcMaster.setId(900004);
        location.set(companion, new L1Location(14, 16, map));
        location.set(pcMaster, new L1Location(16, 16, map));
        companion.set_master(pcMaster);
        final Field command = L1CompanionInstance.class.getDeclaredField("m_command_state");
        command.setAccessible(true);
        command.set(companion,
                l1j.server.server.packets.Protocol.MainServer.Companion.Proto.CompanionT.eCommand.TM_Aggressive);
        MoveIntentCache.setGoal(
                companion, MoveIntentCache.KIND_FOLLOW,
                pcMaster.getId(), pcMaster.getX(), pcMaster.getY(), 12, 16);
        check(MoveIntentCache.getGoal(
                companion, MoveIntentCache.KIND_FOLLOW,
                pcMaster.getId(), pcMaster.getX(), pcMaster.getY(), 2)
                != MoveIntentCache.NO_POS, "companion follow goal was set");
        check(!companion.noTarget(), "current companion noTarget keeps AI alive near master");
        check(MoveIntentCache.getGoal(
                companion, MoveIntentCache.KIND_FOLLOW,
                pcMaster.getId(), pcMaster.getX(), pcMaster.getY(), 2)
                == MoveIntentCache.NO_POS,
                "current companion noTarget clears old follow goal at two tiles");

        check(!CMoveAction.followGroup(follower, leader),
                "group leader uses the same dFollow action");
        check(dFollow.follow(follower, null),
                "missing master must terminate the follow action");
        check(dFollow.follow(null, leader),
                "null follower must be rejected");

        System.out.println("PASS NC dFollow: nearest slot, cached goal, "
                + "anchor drift, close-range release, current companion noTarget, group handoff, invalid master");
    }
}
