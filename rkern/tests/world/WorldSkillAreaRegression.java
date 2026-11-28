package l1j.server.server.model;

import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.helper.SkillTargetListBuilder;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.model.character.skill.SkillDefinitionFixture;

public final class WorldSkillAreaRegression {
    public static void main(String[] args) throws Exception {
        WorldShapeRegression.installMapDefinitions();
        final CWorld world = CWorld.getInstance();
        final L1Map main = map(91), nested = map(92);
        final L1Character caster = actor(main, 1000, 110, 110);
        final L1Character target = actor(main, 1001, 111, 110);
        for (int i = 0; i < 75; i++) {
            final L1Object object = i % 5 == 0 ? new L1Object() : new L1Character();
            object.setId(1 + i);
            object.getLocation().set(109 + i % 3, 110 + i % 4, main);
            world.addVisibleObject(object);
        }
        final L1Character innerCaster = actor(nested, 1002, 110, 110);
        final L1Character innerTarget = actor(nested, 1003, 111, 110);
        final L1Character innerVictim = actor(nested, 1004, 110, 111);
        world.addVisibleObject(innerVictim);

        for (int id : new int[]{65, 99}) {
            final int cap = id == 65 ? 48 : 64;
            final ArrayList<L1Object> nearby = new ArrayList<>();
            world.getClusterGrid().queryNearby(caster, -1, nearby);
            final List<L1Character> expected = nearby.subList(0, cap).stream()
                    .filter(L1Character.class::isInstance).map(L1Character.class::cast).toList();
            final Use outer = new Use(caster, target, id);
            final Use inner = new Use(innerCaster, innerTarget, 99);
            outer.nested = () -> SkillTargetListBuilder.INSTANCE.build(inner);
            SkillTargetListBuilder.INSTANCE.build(outer);
            check(outer.hits.equals(expected), "first " + cap + " positions survive nested targeting");
            check(inner.hits.equals(List.of(innerVictim)), "nested cast kept separate terrain");
        }

        final var grid = world.getClusterGrid();
        final List<L1Object> atDepthZero = grid.borrowNearby(caster, -1);
        grid.releaseNearby(atDepthZero);
        final L1Character unscannable = new L1Character() {
            @Override public L1Map getMap() { throw new AssertionError("fixture query failure"); }
        };
        unscannable.getLocation().set(110, 110, main);
        final Use failedQuery = new Use(unscannable, target, 99);
        SkillTargetListBuilder.INSTANCE.build(failedQuery);
        check(failedQuery.hits.isEmpty(), "legacy query error remains a no-target result");

        final Use failed = new Use(caster, target, 99);
        failed.nested = () -> { throw new AssertionError("combat callback failure"); };
        try {
            SkillTargetListBuilder.INSTANCE.build(failed);
            throw new IllegalStateException("Combat exception was swallowed");
        } catch (AssertionError error) {
            check(error.getMessage().equals("combat callback failure"), "original combat exception propagated");
        }
        final List<L1Object> borrowed = grid.borrowNearby(caster, -1);
        check(borrowed == atDepthZero, "exception unwinds both nested scratch frames");
        grid.releaseNearby(borrowed);
        check(borrowed.isEmpty(), "release removed prior actor references");
        System.out.println("PASS real nested AoE at 48/64 caps, target order, query failure, combat exception and scratch cleanup");
    }

    private static L1V1Map map(int id) {
        return new L1V1Map(id, new byte[64 * 64], 64, 64, 96, 96,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static L1Character actor(L1Map map, int id, int x, int y) {
        final L1Character actor = new L1Character();
        actor.setId(id);
        actor.getLocation().set(x, y, map);
        return actor;
    }

    private static final class Use extends L1SkillUse {
        final L1Character caster, target;
        final L1Skills template = SkillDefinitionFixture.definition(java.util.Map.of(
                "target", "none", "type", L1Skills.TYPE_ATTACK, "area", -1, "ranged", 100));
        final List<L1Character> hits = new ArrayList<>();
        final int id;
        Runnable nested;

        Use(L1Character caster, L1Character target, int id) {
            this.caster = caster;
            this.target = target;
            this.id = id;
        }
        @Override public int tlType() { return TYPE_NORMAL; }
        @Override public int tlSkillId() { return id; }
        @Override public L1Skills tlSkill() { return template; }
        @Override public L1Character tlUser() { return caster; }
        @Override public L1Character tlTarget() { return target; }
        @Override public boolean tlIsAttackSkill() { return true; }
        @Override public int exTargetCount() { return hits.size(); }
        @Override public boolean tlIsTarget(L1Character actor) {
            if (actor != target && nested != null) {
                final Runnable callback = nested;
                nested = null;
                callback.run();
            }
            return true;
        }
        @Override public boolean tlAlreadyInList(L1Character actor) { return hits.contains(actor); }
        @Override public void tlAddTarget(L1Character actor, boolean calc) { hits.add(actor); }
    }
    private static void check(boolean value, String detail) { if (!value) throw new AssertionError(detail); }
}
