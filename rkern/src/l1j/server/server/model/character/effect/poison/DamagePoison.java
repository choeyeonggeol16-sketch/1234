package l1j.server.server.model.character.effect.poison;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

/** Periodic poison damage, driven by the target's existing owner and CIO wheel. */
public final class DamagePoison extends PoisonEffect {
    private final L1Character attacker;
    private final int damageSpan;
    private final int damage;
    private final boolean tomahawk;
    private final int time;
    private boolean firstTick = true;

    private DamagePoison(L1Character attacker, L1Character target,
                         int damageSpan, int damage, boolean tomahawk, int time) {
        super(target);
        this.attacker = attacker;
        this.damageSpan = Math.max(1, damageSpan);
        this.damage = damage;
        this.tomahawk = tomahawk;
        this.time = time;
    }

    public static boolean doInfection(L1Character attacker, L1Character target,
                                      int damageSpan, int damage, boolean tomahawk, int time) {
        return isValidTarget(target)
                && new DamagePoison(attacker, target, damageSpan, damage, tomahawk, time).install();
    }

    @Override
    protected long onApply() {
        if (tomahawk) {
            target.setSkillEffect(L1SkillId.STATUS_TOMAHAWK, time);
        } else {
            if (target instanceof L1PcInstance pc)
                SC_PacketBox.send(pc, SC_PacketBox.POSION_ICON, pc, 1, 30);
            target.setSkillEffect(L1SkillId.STATUS_POISON, time);
            target.setPoisonEffect(1);
        }
        // Preserve the original first damage tick without an interval wait.
        return tickDamage();
    }

    @Override
    protected long onTick() {
        return tickDamage();
    }

    private long tickDamage() {
        // Check only the status belonging to this poison instance.
        final int status = tomahawk ? L1SkillId.STATUS_TOMAHAWK : L1SkillId.STATUS_POISON;
        if (!firstTick && !target.hasSkillEffect(status)) return STOP;
        firstTick = false;
        if (target.hasSkillEffect(L1SkillId.ICE_LANCE)
                || target.hasSkillEffect(L1SkillId.MOB_COCA)
                || target.hasSkillEffect(L1SkillId.MOB_BASILL)
                || target.hasSkillEffect(L1SkillId.EARTH_BIND)) return STOP;

        if (target instanceof L1PcInstance pc) {
            pc.receiveDamage(attacker, damage);
            if (pc.isDead()) return STOP;
        } else if (target instanceof L1MonsterInstance monster) {
            monster.receiveDamage(attacker, damage);
            if (monster.isDead()) return STOP;
        } else {
            // Preserve the base-character case: status remains until manually cured.
            return KEEP;
        }
        return damageSpan;
    }

    @Override
    protected void onCure() {
        if (tomahawk) {
            target.killSkillEffectTimer(L1SkillId.STATUS_TOMAHAWK);
        } else {
            if (target instanceof L1PcInstance pc)
                SC_PacketBox.send(pc, SC_PacketBox.POSION_ICON, pc, 0, 0);
            target.setPoisonEffect(0);
            target.killSkillEffectTimer(L1SkillId.STATUS_POISON);
        }
    }

    @Override
    public int getEffectId() {
        return 1;
    }
}
