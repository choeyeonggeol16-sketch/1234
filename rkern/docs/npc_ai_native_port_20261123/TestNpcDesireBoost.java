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

        System.out.println("PASS NC desire boost: single-apply, exact key, immediate, "
                + "custom boost, heap swap, removal, capacity growth");
    }
}
