package l1j.server.server.datatables;

import java.util.Arrays;
import java.util.HashSet;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.SkillEffectContainer;

public final class CharBuffSnapshotRegression {
    public static void main(String[] args) {
        final int shape = L1SkillId.SHAPE_CHANGE;
        final int ring = L1SkillId.POLY_RING_MASTER;
        final int sprite = 1248;
        final var owner = new L1Character() {
            @Override public int getCurrentSpriteId() { return sprite; }
        };
        final var effects = new SkillEffectContainer(owner);
        final var allowed = new HashSet<Integer>();
        for (int id : CharBuffTable.buffSkill) allowed.add(id);

        effects.put(ring, 0);
        effects.put(-1234, 12_000);
        effects.put(L1SkillId.LIGHT, 1);
        effects.put(shape, 38_000);
        int[] expected = new int[CharBuffTable.buffSkill.length * 3];
        int used = 0;
        for (int id : CharBuffTable.buffSkill) {
            if (!effects.contains(id)) continue;
            expected[used++] = id;
            expected[used++] = effects.getRemainingTimeSec(id);
            expected[used++] = id == shape || id == ring ? sprite : 0;
        }
        final var snapshot = effects.snapshotActive(allowed::contains, shape, ring);
        check(Arrays.equals(Arrays.copyOf(expected, used), CharBuffTable.orderBuffRows(snapshot)),
                "whitelist order, exclusion, timed, unlimited and sprite");
        effects.remove(shape, false);
        effects.put(shape, 16_000);
        check(Arrays.equals(Arrays.copyOf(expected, used), CharBuffTable.orderBuffRows(snapshot)),
                "captured revision is immutable across remove and reapply");
        effects.clear();
        check(CharBuffTable.orderBuffRows(effects.snapshotActive(allowed::contains, shape, ring)).length == 0,
                "empty active set");
        System.out.println("PASS buff active snapshot and exact legacy SQL row order");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
