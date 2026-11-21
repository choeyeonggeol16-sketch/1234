package l1j.server.server.model.character.effect.poison;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

/** One active poison effect per character; effect identity owns its cleanup. */
public abstract class PoisonEffect {
    private static final int[] IMMUNE_ITEMS = {
        20298, 20117, 22196, 22197, 22198, 22199, 123111, 123112, 123113
    };

    protected static boolean isValidTarget(L1Character target) {
        if (target == null || target.getPoison() != null) return false;
        if (!(target instanceof L1PcInstance player)) return true;

        if (target.hasSkillEffect(L1SkillId.ICE_LANCE)
                || target.hasSkillEffect(L1SkillId.MOB_COCA)
                || target.hasSkillEffect(L1SkillId.MOB_BASILL)
                || target.hasSkillEffect(L1SkillId.EARTH_BIND)
                || player.hasSkillEffect(L1SkillId.VENOM_RESIST)) return false;

        for (int itemId : IMMUNE_ITEMS) {
            if (player.getInventory().checkEquipped(itemId)) return false;
        }
        return true;
    }

    protected static void sendMessageIfPlayer(L1Character target, int messageId) {
        if (target instanceof L1PcInstance player) SC_ServerMessage.send(player, messageId);
    }

    public abstract int getEffectId();
    public abstract void cure();
}
