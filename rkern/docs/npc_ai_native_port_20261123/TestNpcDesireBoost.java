package l1j.server.server.model.npc.ai;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import sun.misc.Unsafe;

import l1j.server.server.model.Instance.L1MonsterInstance;

/**
 * Java 21 regression against the production CDesirePQ class.
 * Test-only instances bypass the game/DB constructors; no world is started.
 */
public final class TestNpcDesireBoost {
    private static Field field(String name) throws Exception {
        Field f = CDesirePQ.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        L1MonsterInstance owner =
                (L1MonsterInstance) unsafe.allocateInstance(L1MonsterInstance.class);
        L1MonsterInstance target =
                (L1MonsterInstance) unsafe.allocateInstance(L1MonsterInstance.class);
        target.setId(100);

        Constructor<CDesirePQ> ctor = CDesirePQ.class.getDeclaredConstructor(long.class);
        ctor.setAccessible(true);
        CDesirePQ queue = ctor.newInstance(System.currentTimeMillis());
        owner._desirePQ = queue;

        byte[] states = (byte[]) field("states").get(queue);
        int[] targets = (int[]) field("targetIds").get(queue);
        float[] values = (float[]) field("values").get(queue);
        states[0] = CDesirePQ.PS_ATTACK;
        targets[0] = 100;
        values[0] = 20.0f;
        field("size").setInt(queue, 1);
        field("activeState").setByte(queue, CDesirePQ.PS_ATTACK);
        field("activeTargetId").setInt(queue, 100);

        // NC _BoostUp is a one-time event tied to the exact desire key.
        CDesirePQ.boostExecutedAttack(owner, target);
        check(values[0] == 320.0f, "default 300 boost");
        CDesirePQ.boostExecutedAttack(owner, target);
        check(values[0] == 320.0f, "duplicate tick must not stack boost");

        field("activeTargetId").setInt(queue, 101);
        CDesirePQ.boostExecutedAttack(owner, target);
        check(values[0] == 320.0f, "captured target mismatch");
        field("activeTargetId").setInt(queue, 100);

        field("activeImmediate").setBoolean(queue, true);
        CDesirePQ.boostExecutedAttack(owner, target);
        check(values[0] == 320.0f, "absolute key is not ordinary heap entry");
        field("activeImmediate").setBoolean(queue, false);

        boolean[] boosted = (boolean[]) field("boosted").get(queue);
        int[] attackPayload = new int[states.length << 2];
        attackPayload[2] = Float.floatToRawIntBits(77.0f);
        field("attackPayload").set(queue, attackPayload);
        boosted[0] = false;
        values[0] = 20.0f;
        CDesirePQ.boostExecutedAttack(owner, target);
        check(values[0] == 97.0f, "per-desire boost");
        CDesirePQ.boostExecutedAttack(owner, target);
        check(values[0] == 97.0f, "special boost only once");

        // Max-heap swaps must carry CDesire::boosted with its target/payload.
        states[1] = CDesirePQ.PS_ATTACK;
        targets[1] = 101;
        values[0] = 1.0f;
        values[1] = 50.0f;
        boosted[0] = true;
        boosted[1] = false;
        field("size").setInt(queue, 2);
        Method siftDown = CDesirePQ.class.getDeclaredMethod("siftDown", int.class);
        siftDown.setAccessible(true);
        siftDown.invoke(queue, 0);
        check(targets[0] == 101 && targets[1] == 100, "heap target swap");
        check(!boosted[0] && boosted[1], "heap boosted-marker swap");

        // Removing one heap entry must move the matching marker, not leak it.
        Method removeAt = CDesirePQ.class.getDeclaredMethod("removeAt", int.class);
        removeAt.setAccessible(true);
        removeAt.invoke(queue, 0);
        check(field("size").getInt(queue) == 1, "removeAt count");
        check(targets[0] == 100 && boosted[0], "removeAt compaction marker");
        check(!boosted[1], "removed slot cleared");

        Method ensureCapacity = CDesirePQ.class.getDeclaredMethod(
                "ensureCapacity", int.class);
        ensureCapacity.setAccessible(true);
        ensureCapacity.invoke(queue, 10);
        boosted = (boolean[]) field("boosted").get(queue);
        check(boosted.length >= 10 && boosted[0], "capacity growth preserves marker");

        // Actual production NC UseSkillFinished: ordinary failure BoostDown,
        // one-time failure removal, success exact-key removal.
        final long nowMs = System.currentTimeMillis();
        check(CDesirePQ.addUseSkillDesire(
                owner, 0, 123, 0, 0, 55.0f, 40.0f, 0, nowMs),
                "add reusable skill desire");

        Method findSkillKey = CDesirePQ.class.getDeclaredMethod(
                "findSkillKey", int.class, int.class);
        findSkillKey.setAccessible(true);
        int skillIndex = (int) findSkillKey.invoke(queue, 0, 123);
        check(skillIndex >= 0, "reusable skill key exists");
        values = (float[]) field("values").get(queue);
        boosted[skillIndex] = true;
        values[skillIndex] += 40.0f;

        check(CDesirePQ.UseSkillFinished(owner, 0, 123, false),
                "reusable skill failure callback");
        check(values[skillIndex] == 55.0f && !boosted[skillIndex],
                "reusable skill only loses its own boost");
        check(CDesirePQ.UseSkillFinished(owner, 0, 123, false),
                "duplicate reusable callback remains valid");
        check(values[skillIndex] == 55.0f,
                "repeat callback must not subtract boost twice");

        check(CDesirePQ.addUseSkillDesire(
                owner, 0, 456, 0, CDesirePQ.DF_ONE_TIME_METHOD,
                75.0f, 50.0f, 0, nowMs), "add one-time skill desire");
        int once = (int) findSkillKey.invoke(queue, 0, 456);
        check(once >= 0, "one-time key exists");
        boosted = (boolean[]) field("boosted").get(queue);
        boosted[once] = true;
        check(CDesirePQ.UseSkillFinished(owner, 0, 456, false),
                "one-time skill failure callback");
        check((int) findSkillKey.invoke(queue, 0, 456) == -1,
                "one-time skill removed on failure");
        check((int) findSkillKey.invoke(queue, 0, 123) >= 0,
                "unrelated reusable skill retained");

        check(CDesirePQ.addUseSkillDesire(
                owner, 0, 789, 0, 0, 90.0f, 20.0f, 0, nowMs),
                "add successful skill desire");
        check(CDesirePQ.UseSkillFinished(owner, 0, 789, true),
                "successful skill callback");
        check((int) findSkillKey.invoke(queue, 0, 789) == -1,
                "successful skill key removed");
        check((int) findSkillKey.invoke(queue, 0, 123) >= 0,
                "success does not erase other skill requests");

        System.out.println("PASS NC desire boost: single-apply, exact key, immediate, "
                + "custom boost, heap swap, removal, capacity growth");
        System.out.println("PASS NC skill completion: reusable BoostDown, one-time "
                + "failure removal, success exact-key removal, sibling preservation");

        // These are L1 LIVE-SERVER observations, deliberately not replaced
        // by the L2NPC dMoveAround distances. Guard them across all ports.
        final Class<?> wander = Class.forName(
                "l1j.server.server.model.npc.ai.action.CMoveAroundAction");
        Field minWalk = wander.getDeclaredField("MIN_MOVE_DISTANCE");
        Field maxWalk = wander.getDeclaredField("MAX_MOVE_DISTANCE");
        Field restDelay = wander.getDeclaredField("REST_DELAY_MS");
        minWalk.setAccessible(true);
        maxWalk.setAccessible(true);
        restDelay.setAccessible(true);
        check(minWalk.getInt(null) == 3, "L1 live-server walk minimum 3 tiles");
        check(maxWalk.getInt(null) == 6, "L1 live-server walk maximum 6 tiles");
        check(restDelay.getLong(null) == 6_000L,
                "L1 live-server pause exactly 6 seconds");
        System.out.println("PASS L1 live walk constants: 3..6 tiles, 6000ms rest");

        // The NPC-only cast entry rejects invalid actors / skill IDs before
        // entering the pooled L1SkillUse executor. The private normal-cast
        // gate must reject an uninitialized skill instead of silent success.
        check(!l1j.server.server.model.character.skill.L1SkillUse.castNpc(
                null, 1, 0, 0, 0, null, 0,
                l1j.server.server.model.character.skill.L1SkillUse.TYPE_NORMAL, 10),
                "NPC null attacker rejected");
        check(!l1j.server.server.model.character.skill.L1SkillUse.castNpc(
                owner, 0, 0, 0, 0, null, 0,
                l1j.server.server.model.character.skill.L1SkillUse.TYPE_NORMAL, 10),
                "NPC zero skill ID rejected");
        final Class<?> executorClass =
                l1j.server.server.model.character.skill.L1SkillUse.class;
        final Object emptyCast = unsafe.allocateInstance(executorClass);
        final Method canRunNormal =
                executorClass.getDeclaredMethod("canRunNormal");
        canRunNormal.setAccessible(true);
        check(!(boolean) canRunNormal.invoke(emptyCast),
                "no initialized skill must not be executed as NPC cast");
        System.out.println("PASS NPC cast prerequisites: attacker, skill ID, "
                + "normal-cast execution gate");
    }
}
