package l1j.server.server.model.character.effect.poison;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

/** Untimed silence poison: lives in the same owner state until a cure arrives. */
public final class SilencePoison extends PoisonEffect {
    private final int time;

    private SilencePoison(L1Character target, int time) {
        super(target);
        this.time = time;
    }

    public static boolean doInfection(L1Character target) {
        return doInfection(target, 0);
    }

    public static boolean doInfection(L1Character target, int time) {
        return isValidTarget(target) && new SilencePoison(target, time).install();
    }

    @Override
    protected long onApply() {
        target.setPoisonEffect(1);
        sendMessageIfPlayer(target, 310);
        if (target instanceof L1PcInstance pc)
            SC_PacketBox.send(pc, SC_PacketBox.POSION_ICON, pc, 6, time == 0 ? -1 : time);
        target.setSkillEffect(L1SkillId.STATUS_POISON_SILENCE, 0);
        return KEEP;
    }

    @Override
    protected void onCure() {
        target.setPoisonEffect(0);
        sendMessageIfPlayer(target, 311);
        if (target instanceof L1PcInstance pc)
            SC_PacketBox.send(pc, SC_PacketBox.POSION_ICON, pc, 0, 0);
        target.killSkillEffectTimer(L1SkillId.STATUS_POISON_SILENCE);
    }

    @Override
    public int getEffectId() {
        return 1;
    }
}
