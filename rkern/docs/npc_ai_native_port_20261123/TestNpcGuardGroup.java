package l1j.server.server.model.npc.ai.action;

import java.lang.reflect.Field;
import java.util.Arrays;
import sun.misc.Unsafe;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1HateList;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1MobGroupInfo;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;

/** NC guard home return and L1 private-group leader lifecycle regression. */
public final class TestNpcGuardGroup {
    private TestNpcGuardGroup() { }

    private static void check(final boolean ok, final String message) {
        if (!ok) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static <T extends L1NpcInstance> T fixture(
            final Unsafe unsafe, final Class<T> clazz,
            final Field loc, final Field hp, final int id,
            final int x, final int y, final L1Map map)
            throws Exception {
        final T npc = (T) unsafe.allocateInstance(clazz);
        npc.setId(id);
        loc.set(npc, new L1Location(x, y, map));
        hp.setInt(npc, 100);
        npc._hateList = new L1HateList();
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

        final byte[][] tiles = new byte[64][64];
        for (byte[] column : tiles) Arrays.fill(column, (byte) 0x03);
        final L1Map map = new L1V1Map(1708, tiles, 0, 0,
                false, true, true, true, true, true,
                true, true, true, true, true, false);
        final L1Map differentMap = new L1V1Map(1710, tiles, 0, 0,
                false, true, true, true, true, true,
                true, true, true, true, true, false);
        final L1GuardInstance guard = fixture(
                unsafe, L1GuardInstance.class,
                loc, hp, 981000, 10, 10, map);
        final L1CastleGuardInstance castle = fixture(
                unsafe, L1CastleGuardInstance.class,
                loc, hp, 981001, 11, 11, map);

        final L1MonsterInstance monster = fixture(
                unsafe, L1MonsterInstance.class,
                loc, hp, 981002, 12, 12, map);
        check(guard.isSightTargetCandidate(monster),
                "map-1708 living monster is a guard sight candidate");
        monster.setDead(true);
        check(!guard.isSightTargetCandidate(monster),
                "dead monster with stale positive HP cannot trigger guard SEE");
        monster.setDead(false);
        hp.setInt(monster, 0);
        check(!guard.isSightTargetCandidate(monster),
                "zero-HP monster marked alive cannot trigger guard SEE");
        hp.setInt(monster, 100);

        // Original castle-guard target invalidation teleported home before
        // checking for remaining hate, and recursive target invalidation
        // could exhaust the JVM stack with a large simultaneous hate purge.
        guard.setHomeX(10);
        guard.setHomeY(10);
        castle.setHomeX(11);
        castle.setHomeY(11);
        for (int i = 0; i < 140; i++) {
            final L1MonsterInstance oldTarget = fixture(
                    unsafe, L1MonsterInstance.class,
                    loc, hp, 982000 + i, 14, 14, differentMap);
            guard._hateList.add(oldTarget, 1);
            castle._hateList.add(oldTarget, 1);
        }
        // First call bounded to 128 retirements, second call drains the rest.
        guard._target = guard._hateList.getMaxHateCharacter();
        castle._target = castle._hateList.getMaxHateCharacter();
        guard.checkTarget();
        castle.checkTarget();
        guard.checkTarget();
        castle.checkTarget();
        check(guard._target == null && guard._hateList.isEmpty(),
                "guard must retire all invalid hate without recursion");
        check(castle._target == null && castle._hateList.isEmpty(),
                "castle guard must retire invalid hate without teleport");
        check(castle.getX() == 11 && castle.getY() == 11,
                "castle invalid target must not teleport during hate scan");

        // Static home target outside this map: NC dMoveTo must reject it
        // without bypassing occupancy using the old immediate teleport.
        guard.setHomeX(999);
        guard.setHomeY(999);
        castle.setHomeX(999);
        castle.setHomeY(999);
        check(!guard.noTarget() && guard.getX() == 10 && guard.getY() == 10,
                "guard blocked home return must retry instead of teleporting");
        check(!castle.noTarget()
                && castle.getX() == 11 && castle.getY() == 11,
                "castle blocked home return must retry instead of teleporting");
        check(guard.getSleepTime() >= 1_000L
                && castle.getSleepTime() >= 1_000L,
                "invalid guard home must not busy-spin in CIO");

        // Group membership/assist identity is always current leader's ID.
        final L1MonsterInstance leader = fixture(
                unsafe, L1MonsterInstance.class,
                loc, hp, 983000, 20, 20, map);
        final L1MonsterInstance first = fixture(
                unsafe, L1MonsterInstance.class,
                loc, hp, 983001, 20, 21, map);
        final L1MonsterInstance second = fixture(
                unsafe, L1MonsterInstance.class,
                loc, hp, 983002, 20, 22, map);
        final L1MobGroupInfo group = new L1MobGroupInfo();
        check(!group.isLeader(leader),
                "empty NPC group must not throw on leader lookup");
        group.addMember(leader);
        group.addMember(first);
        group.addMember(second);
        group.addMember(second);
        check(group.getNumOfMembers() == 3
                && group.getLeader() == leader
                && group.isLeader(leader)
                && first.getMobGroupId() == leader.getId(),
                "group addition must retain one leader and unique members");

        group.removeMember(leader);
        check(group.getLeader() == first
                && group.isLeader(first)
                && second.getMobGroupId() == first.getId(),
                "leader death must rebase all group-help IDs onto successor");
        check(leader.getMobGroupInfo() == null
                && leader.getMobGroupId() == 0,
                "removed leader may not retain group membership or help ID");

        final L1MobGroupInfo foreign = new L1MobGroupInfo();
        foreign.addMember(leader);
        final int countBefore = group.getNumOfMembers();
        group.removeMember(leader);
        check(group.getNumOfMembers() == countBefore
                && leader.getMobGroupInfo() == foreign,
                "late duplicate delete must not unregister a new group");

        first.setDead(true);
        check(!CMoveAction.followGroup(second, first),
                "dead group leader pauses follow without stopping NPC AI");
        first.setDead(false);

        group.setRemoveGroup(true);
        group.removeMember(first);
        check(group.getNumOfMembers() == 0
                && group.getLeader() == null
                && second.getMobGroupInfo() == null
                && second.getMobGroupId() == 0,
                "remove-group on leader death releases all minion refs and IDs");

        System.out.println("PASS NC guard/group: dead and zero-HP sight "
                + "rejected, 140-target bounded prune, castle no-teleport "
                + "home, leader succession/assist IDs, duplicate deletion, "
                + "dead-leader pause, group disband cleanup");
    }
}
