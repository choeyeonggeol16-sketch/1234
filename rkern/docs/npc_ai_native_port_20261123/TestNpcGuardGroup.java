package l1j.server.server.model.npc.ai.action;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import sun.misc.Unsafe;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.npc.ai.CNPCEvent;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.model.L1HateList;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1MobGroupInfo;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1GuardianInstance;
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
        check(group.getActiveLeader() == second
                && group.isLeader(second)
                && first.getMobGroupId() == second.getId(),
                "ordinary group elects living leader before corpse deletion");
        check(!CMoveAction.followGroup(second, first),
                "new group leader must not follow its own group ID");
        first.setDead(false);

        group.setRemoveGroup(true);
        group.removeMember(second);
        check(group.getNumOfMembers() == 0
                && group.getLeader() == null
                && first.getMobGroupInfo() == null
                && first.getMobGroupId() == 0,
                "remove-group on current leader deletion releases minion refs");

        // For configured leader-death disband groups, a dead leader must
        // NOT be auto-elected away (or the disband policy would be bypassed).
        final L1MobGroupInfo disband = new L1MobGroupInfo();
        final L1MonsterInstance leader2 = fixture(
                unsafe, L1MonsterInstance.class,
                loc, hp, 983003, 21, 20, map);
        final L1MonsterInstance minion2 = fixture(
                unsafe, L1MonsterInstance.class,
                loc, hp, 983004, 21, 21, map);
        disband.addMember(leader2);
        disband.addMember(minion2);
        disband.setRemoveGroup(true);
        leader2.setDead(true);
        check(disband.getActiveLeader() == leader2,
                "remove-group policy must preserve dead leader until delete");
        disband.removeMember(leader2);
        check(disband.getNumOfMembers() == 0
                && disband.getLeader() == null
                && minion2.getMobGroupInfo() == null
                && minion2.getMobGroupId() == 0,
                "remove-group leader death releases entire group");

        System.out.println("PASS NC guard/group: dead and zero-HP sight "
                + "rejected, 140-target bounded prune, castle no-teleport "
                + "home, leader succession/assist IDs, duplicate deletion, "
                + "live-leader election, disband policy and cleanup");
        testTargetEvents(unsafe, loc, hp, map, differentMap);
    }

    private static final class SightPc extends L1PcInstance {
        boolean invisible;
        @Override public boolean isInvisble() { return invisible; }
    }

    private static Field accessibleField(Class<?> type, String name) throws Exception {
        final Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    // Run real production inbox + desire/target code synchronously on a
    // test-only owner. Only object identity and map/health are seeded;
    // there is no socket, DB or running game server.
    private static void testTargetEvents(Unsafe unsafe, Field loc, Field hp,
            L1Map map, L1Map otherMap) throws Exception {
        final L1World world = (L1World) unsafe.allocateInstance(L1World.class);
        final Class<?> worldType = Class.forName("l1j.server.server.model.CWorld");
        final Constructor<?> ctor = worldType.getDeclaredConstructor(L1World.class);
        ctor.setAccessible(true);
        final Object executor = ctor.newInstance(world);
        accessibleField(L1World.class, "_worldExecutor").set(world, executor);
        final Field singleton = accessibleField(L1World.class, "_instance");
        final Object previous = singleton.get(null);
        singleton.set(null, world);
        final Object factory = accessibleField(worldType, "objectFactory").get(executor);
        final Method install = factory.getClass().getDeclaredMethod("install", L1Object.class);
        install.setAccessible(true);
        final Method drain = CNpcAI.class.getDeclaredMethod("HandleEvent",
                L1NpcInstance.class, long.class);
        drain.setAccessible(true);
        final Field templateField = accessibleField(L1NpcInstance.class, "_npcTemplate");
        final Field maxHp = accessibleField(L1Character.class, "_maxHp");
        final long now = System.currentTimeMillis();

        try {
            final L1Npc template = new L1Npc();
            template.set_npcId(45001);
            template.set_agro(true);
            template.set_agrogfxid1(-1);
            template.set_agrogfxid2(-1);
            final L1MonsterInstance mob = fixture(unsafe, L1MonsterInstance.class,
                    loc, hp, 984000, 20, 20, map);
            templateField.set(mob, template);
            maxHp.setInt(mob, 100);
            mob._dropHateList = new L1HateList();
            CCreatureController.currentAction(mob, CCreatureController.ACTION_THINK);
            install.invoke(factory, mob);
            final SightPc a = (SightPc) unsafe.allocateInstance(SightPc.class);
            final SightPc b = (SightPc) unsafe.allocateInstance(SightPc.class);
            a.setId(984001); b.setId(984002);
            loc.set(a, new L1Location(21, 20, map));
            loc.set(b, new L1Location(22, 20, map));
            hp.setInt(a, 100); hp.setInt(b, 100);
            install.invoke(factory, a); install.invoke(factory, b);

            check(NpcEventFactory.isSightTargetCandidate(mob, a, now),
                    "ordinary aggressive monster sees living PC");
            template.set_agro(false);
            check(!NpcEventFactory.isSightTargetCandidate(mob, a, now),
                    "passive monster does not initiate normal attack");
            template.set_agro(true);
            a.invisible = true;
            check(!NpcEventFactory.isSightTargetCandidate(mob, a, now),
                    "normal monster does not see invisible PC");
            template.set_agrocoi(true);
            check(NpcEventFactory.isSightTargetCandidate(mob, a, now),
                    "invisibility-detecting monster sees invisible PC");
            template.set_agrocoi(false);
            a.invisible = false;
            a.setGhost(true);
            check(!NpcEventFactory.isSightTargetCandidate(mob, a, now),
                    "ghost excluded");
            a.setGhost(false);
            a.setPrivateShop(true);
            check(!NpcEventFactory.isSightTargetCandidate(mob, a, now),
                    "private shop excluded");
            a.setPrivateShop(false);
            hp.setInt(a, 0);
            check(!NpcEventFactory.isSightTargetCandidate(mob, a, now), "zero HP excluded");
            hp.setInt(a, 100);
            loc.set(a, new L1Location(21, 20, otherMap));
            check(!NpcEventFactory.isSightTargetCandidate(mob, a, now), "other map excluded");
            loc.set(a, new L1Location(21, 20, map));

            check(mob.PostNpcEvent(NpcEventFactory.CreateSeeCreatureEvent(mob, a)),
                    "SEE event admitted");
            check((boolean) drain.invoke(null, mob, now), "SEE consumed by production owner logic");
            check(mob._target == a && mob._hateList.containsKey(a),
                    "first SEE selects PC A without a hit");
            check(mob.PostNpcEvent(NpcEventFactory.CreateAttackedEvent(mob, b, 95)),
                    "ATTACKED event admitted");
            check((boolean) drain.invoke(null, mob, now), "ATTACKED consumed");
            check(mob._target == b, "higher damage PC B wins desire");
            b.invisible = true;
            check(CDesirePQ.topAttackTarget(mob, now) == a,
                    "lost invisible target falls back to remaining admitted PC");
            b.invisible = false;
            check(mob.PostNpcEvent(NpcEventFactory.CreateAttackedEvent(mob, b, 95)),
                    "returning attacker may be admitted again");
            drain.invoke(null, mob, now);
            check(mob._target == b, "visible attacker can win again");
            b.setDead(true);
            check(CDesirePQ.topAttackTarget(mob, now) == a,
                    "dead current attacker retires, PC A remains");
            b.setDead(false);

            final L1GuardInstance guard = fixture(unsafe, L1GuardInstance.class,
                    loc, hp, 984003, 20, 21, map);
            templateField.set(guard, template);
            guard.setHomeX(20); guard.setHomeY(21);
            guard._dropHateList = new L1HateList();
            CCreatureController.currentAction(guard, CCreatureController.ACTION_THINK);
            check(guard.PostNpcEvent(NpcEventFactory.CreateSeeCreatureEvent(guard, mob)),
                    "guard SEE admitted");
            drain.invoke(null, guard, now);
            check(guard._target == mob, "guard acquires living nearby monster");
            check(!guard.isSightTargetCandidate(a), "ordinary guard does not auto-target PC");

            for (Class<? extends L1NpcInstance> type : java.util.List.of(
                    L1PetInstance.class, L1SummonInstance.class, L1CompanionInstance.class)) {
                final L1NpcInstance follower = fixture(unsafe, type, loc, hp,
                        984010 + type.getName().length(), 20, 22, map);
                templateField.set(follower, template);
                follower._dropHateList = new L1HateList();
                follower.setFirstAttack(true);
                CCreatureController.currentAction(follower, CCreatureController.ACTION_THINK);
                check(follower.PostNpcEvent(NpcEventFactory.CreateHateEvent(follower, a, 10)),
                        type.getSimpleName() + " first hate admitted");
                drain.invoke(null, follower, now);
                check(follower._target == a, type.getSimpleName() + " first target");
                check(follower.PostNpcEvent(NpcEventFactory.CreateHateEvent(follower, b, 90)),
                        type.getSimpleName() + " second hate admitted");
                drain.invoke(null, follower, now);
                check(follower._target == b, type.getSimpleName() + " higher hate target");
                b.setDead(true);
                follower.checkTarget();
                check(follower._target == a, type.getSimpleName() + " dead-target fallback");
                b.setDead(false);
                loc.set(a, new L1Location(21, 20, otherMap));
                follower.checkTarget();
                check(follower._target == null, type.getSimpleName() + " map-change clear");
                loc.set(a, new L1Location(21, 20, map));
            }
            // Exercise the shared family/range gate and the actual owner inbox.
            template.set_family(7);
            template.set_agrofamily(1);
            final Method eligible = NpcEventFactory.class.getDeclaredMethod(
                    "IsClanHelpCandidate", L1NpcInstance.class, L1Npc.class,
                    L1NpcInstance.class, int.class, int.class, int.class, int.class);
            eligible.setAccessible(true);
            final L1MonsterInstance helper = fixture(unsafe, L1MonsterInstance.class,
                    loc, hp, 984040, 32, 32, map);
            templateField.set(helper, template);
            maxHp.setInt(helper, 100);
            helper._dropHateList = new L1HateList();
            install.invoke(factory, helper);
            CCreatureController.currentAction(helper, CCreatureController.ACTION_THINK);
            check((boolean) eligible.invoke(null, mob, template, helper, 20, 20, mob.getMapId(), 0),
                    "awareness keeps diagonal 12-tile limit");
            check(!(boolean) eligible.invoke(null, mob, template, helper, 20, 20, mob.getMapId(), 10),
                    "damage event preserves circular radius");
            loc.set(helper, new L1Location(32, 20, map));
            check((boolean) eligible.invoke(null, mob, template, helper, 20, 20, mob.getMapId(), 10),
                    "damage event includes 12-tile axial boundary");
            loc.set(helper, new L1Location(33, 20, map));
            check(!(boolean) eligible.invoke(null, mob, template, helper, 20, 20, mob.getMapId(), 0),
                    "outside awareness radius rejected");
            loc.set(helper, new L1Location(22, 21, map));
            final L1Npc foreignTemplate = new L1Npc();
            foreignTemplate.set_family(8);
            foreignTemplate.set_agrofamily(1);
            templateField.set(helper, foreignTemplate);
            check(!(boolean) eligible.invoke(null, mob, template, helper, 20, 20, mob.getMapId(), 10),
                    "different family rejected");
            templateField.set(helper, template);
            template.set_agrofamily(0);
            check(!(boolean) eligible.invoke(null, mob, template, helper, 20, 20, mob.getMapId(), 0),
                    "helper without family aggro rejected");
            template.set_agrofamily(1);
            check(!(boolean) eligible.invoke(null, mob, template, mob, 20, 20, mob.getMapId(), 10),
                    "self excluded");
            helper.setDead(true);
            check(!(boolean) eligible.invoke(null, mob, template, helper, 20, 20, mob.getMapId(), 0),
                    "dead helper excluded");
            helper.setDead(false);
            final Field createdAt = accessibleField(L1MonsterInstance.class, "_desireCreatedAtMs");
            createdAt.setLong(helper, now - 7000L);
            check(helper.PostNpcEvent(NpcEventFactory.CreateClanAttackedEvent(helper, a, 80)),
                    "young helper receives clan event");
            drain.invoke(null, helper, now);
            check(helper._hateList.isEmpty() && helper._target == null, "seven-second clan gate");
            createdAt.setLong(helper, now - 7001L);
            check(helper.PostNpcEvent(NpcEventFactory.CreateClanAttackedEvent(helper, a, 80)),
                    "mature helper receives clan event");
            drain.invoke(null, helper, now);
            check(helper._target == a && helper._hateList.getHate(a) == 0,
                    "damage-scaled clan desire selects attacker without direct hate");
            check(helper.PostNpcEvent(NpcEventFactory.CreateHelpEvent(helper, b)), "busy helper event queued");
            drain.invoke(null, helper, now);
            check(helper._target == a && !helper._hateList.containsKey(b),
                    "awareness cannot override occupied helper");

            final L1GuardianInstance guardian = fixture(unsafe, L1GuardianInstance.class,
                    loc, hp, 984041, 21, 21, map);
            templateField.set(guardian, template);
            guardian._dropHateList = new L1HateList();
            CCreatureController.currentAction(guardian, CCreatureController.ACTION_THINK);
            check((boolean) eligible.invoke(null, mob, template, guardian, 20, 20, mob.getMapId(), 0),
                    "guardian eligible for zero-hate awareness");
            check(!(boolean) eligible.invoke(null, mob, template, guardian, 20, 20, mob.getMapId(), 10),
                    "guardian not converted to warrior damage-scaled clan event");
            a.invisible = true;
            check(guardian.PostNpcEvent(NpcEventFactory.CreateHelpEvent(guardian, a)), "hidden help queued");
            drain.invoke(null, guardian, now);
            check(guardian._target == null && guardian._hateList.isEmpty(), "hidden help rejected");
            a.invisible = false;
            check(guardian.PostNpcEvent(NpcEventFactory.CreateHelpEvent(guardian, a)), "guardian help queued");
            check(guardian._target == null && guardian._hateList.isEmpty(), "producer does not write hate");
            drain.invoke(null, guardian, now);
            check(guardian._target == a && guardian._hateList.containsKey(a)
                    && guardian._dropHateList.isEmpty(), "guardian acquires target only on owner");
            check(guardian.PostNpcEvent(NpcEventFactory.CreateHelpEvent(guardian, b)), "repeat help queued");
            drain.invoke(null, guardian, now);
            check(guardian._target == a && !guardian._hateList.containsKey(b), "busy guardian unchanged");
            System.out.println("PASS clan-help: range/family/type/death gates, 7s lifetime, "
                    + "damage vs awareness, busy-helper preservation, guardian owner-only acquisition");
            System.out.println("PASS live NPC target events: initial SEE, passive/ghost/shop/map/HP gates, "
                    + "invisibility detection, ATTACKED retarget, invisible/dead fallback, "
                    + "guard acquisition and pet/summon/companion hate handoff");
        } finally {
            singleton.set(null, previous);
        }
    }
}
