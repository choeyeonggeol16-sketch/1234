package l1j.server.server.model.character.effect.poison;

import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.datatables.MonsterParalyzeDelay.MonsterParalyze;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.ControlSkillEffects;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType;

/** Delay and paralysis expiry are two phases of one target-owned effect. */
public final class ParalysisPoison extends PoisonEffect {
    private static final int PENDING = 0;
    private static final int PARALYZED = 1;
    private static final int START_NOTICE_BITS = 1 | 2 | 4 | 8 | 16 | 32 | 64 | 128 | 512 | 1024;
    private static final int END_NOTICE_BITS = 1 | 2 | 8 | 1024;

    private final int delay;
    private final int duration;
    private int stage = PENDING;
    private volatile int effectId = 1;

    private ParalysisPoison(L1Character target, MonsterParalyze paralyze) {
        super(target);
        this.delay = paralyze.paralyze_delay;
        this.duration = paralyze.paralyze_millis;
    }

    public static boolean doInfection(L1Character target, int skillId) {
        return doInfection(target, MonsterParalyzeDelay.getInstance().get_paralyze(skillId));
    }

    public static boolean doInfection(L1Character target, MonsterParalyze paralyze) {
        return paralyze != null && isValidTarget(target)
                && new ParalysisPoison(target, paralyze).install();
    }

    @Override
    protected long onApply() {
        sendMessageIfPlayer(target, 212);
        target.setPoisonEffect(1);
        if (target instanceof L1PcInstance pc) {
            target.setSkillEffect(L1SkillId.STATUS_POISON_PARALYZING, 0);
            sendStartNotice(pc, delay / 1000, 212);
            return Math.max(1L, delay);
        }
        // Original non-PC paralysis applies the initial visual only.
        return KEEP;
    }

    @Override
    protected long onTick() {
        if (stage == PARALYZED) return STOP;
        stage = PARALYZED;
        effectId = 2;
        target.setPoisonEffect(2);
        if (target instanceof L1PcInstance pc) {
            if (pc.isDead()) return STOP;
            sendStartNotice(pc, duration / 1000, 213);
            SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, true);
            target.setSkillEffect(L1SkillId.STATUS_POISON_PARALYZED, 0);
            target.setPoisonParalyzed(true);
            return Math.max(1L, duration);
        }
        return KEEP;
    }

    private static void sendStartNotice(L1PcInstance pc, int seconds, int tooltipId) {
        SC_SPELL_BUFF_NOTI.sendFields(pc, eNotiType.RESTAT, L1SkillId.CURSE_PARALYZE,
                seconds, eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC,
                5270, 5270, 10, tooltipId, 0, 0, false, 0, 0, 0, START_NOTICE_BITS);
    }

    private static void sendEndNotice(L1PcInstance pc) {
        SC_SPELL_BUFF_NOTI.sendFields(pc, eNotiType.END, L1SkillId.CURSE_PARALYZE,
                0, eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC,
                0, 0, 0, 0, 0, 0, false, 0, 0, 0, END_NOTICE_BITS);
    }

    @Override
    protected void onCure() {
        target.setPoisonParalyzed(false);
        target.killSkillEffectTimer(L1SkillId.STATUS_POISON_PARALYZING);
        target.killSkillEffectTimer(L1SkillId.STATUS_POISON_PARALYZED);
        if (target instanceof L1PcInstance pc) {
            sendEndNotice(pc);
            if (!pc.isDead() && !pc.isParalyzed()
                    && !ControlSkillEffects.hasSkillFreeze(pc, -1))
                SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, false);
        }
        if (!target.isParalyzed() && !ControlSkillEffects.hasSkillFreeze(target, -1))
            target.setPoisonEffect(0);
    }

    @Override
    public int getEffectId() {
        return effectId;
    }
}
