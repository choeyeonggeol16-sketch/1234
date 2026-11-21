package l1j.server.server.model.character.effect.poison;

import l1j.server.server.datatables.MonsterParalyzeDelay.MonsterParalyze;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.skill.L1SkillId;

/** Exercised against production poison classes, with a non-player fake target. */
public final class TestPoisonEffectLifecycle {
    private TestPoisonEffectLifecycle() {}

    private static final class Dummy extends L1Character {
        int visuals;
        int skillSets;
        int skillClears;
        int lastVisual;
        int lastSet;
        int lastClear;

        @Override public void setPoisonEffect(int effectId) {
            visuals++;
            lastVisual = effectId;
        }
        @Override public void setSkillEffect(int skillId, int time) {
            skillSets++;
            lastSet = skillId;
        }
        @Override public void killSkillEffectTimer(int skillId) {
            skillClears++;
            lastClear = skillId;
        }
    }

    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Dummy target = new Dummy();
        check(SilencePoison.doInfection(target, 15), "silence installs");
        PoisonEffect first = target.getPoison();
        check(first instanceof SilencePoison, "silence owns target");
        check(!DamagePoison.doInfection(target, target, 3000, 30, false, 30000),
                "poison does not overlap");
        check(target.lastSet == L1SkillId.STATUS_POISON_SILENCE, "silence skill status");
        first.cure();
        check(target.getPoison() == null && target.lastVisual == 0, "silence clears");
        int visuals = target.visuals, clears = target.skillClears;
        first.cure();
        check(target.visuals == visuals && target.skillClears == clears,
                "double cure does not repeat effects");

        check(SilencePoison.doInfection(target), "replacement first poison");
        PoisonEffect old = target.getPoison();
        target.setPoison(null); // simulate a stale callback after forced replacement
        check(DamagePoison.doInfection(target, target, 3000, 30, false, 30000),
                "replacement second poison");
        PoisonEffect current = target.getPoison();
        int before = target.visuals;
        old.cure();
        check(target.getPoison() == current && target.visuals == before,
                "stale cure must not erase new poison");
        target.curePoison();
        check(target.getPoison() == null && target.lastClear == L1SkillId.STATUS_POISON,
                "target cure clears owned damage poison");

        check(DamagePoison.doInfection(target, target, 1000, 20, true, 7000),
                "tomahawk poison installs");
        check(target.lastSet == L1SkillId.STATUS_TOMAHAWK, "tomahawk status");
        target.curePoison();
        check(target.getPoison() == null && target.lastClear == L1SkillId.STATUS_TOMAHAWK,
                "tomahawk cure");

        MonsterParalyze rule = new MonsterParalyze();
        rule.paralyze_delay = 6000;
        rule.paralyze_millis = 10000;
        check(ParalysisPoison.doInfection(target, rule), "paralysis installs");
        check(target.getPoison().getEffectId() == 1 && target.lastVisual == 1,
                "paralysis pre-delay stage");
        target.curePoison();
        check(target.getPoison() == null && target.lastVisual == 0,
                "paralysis cures safely");

        for (int i = 0; i < 200; i++) {
            check(SilencePoison.doInfection(target), "repeat infection " + i);
            target.curePoison();
            check(target.getPoison() == null, "repeat release " + i);
        }
        System.out.println("PASS poison lifecycle checks=" + checks);
    }
}
