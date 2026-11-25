package l1j.server.server.model.character.combat.titan;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.GameUtils;

/** src22 TitanProcEngine: victim passive, HP threshold, probability, catalyst,
 * counter damage and original-hit cancellation. No per-hit callback or state map. */
public final class TitanProcEngine {
    public enum Mode { ROCK, BLITZ, MAGIC }

    private static final int RING_ITEM_ID = 202014;
    private static final int CATALYST_ITEM_ID = 41246;
    private static final int CATALYST_COUNT = 10;
    private static final int BASE_HP_THRESHOLD = 40;

    private TitanProcEngine() {}

    /** true: proc succeeded and caller must cancel the incoming HP commit. */
    public static boolean tryProc(L1Character attacker, L1PcInstance defender, Mode mode, int chance) {
        if (attacker == null || defender == null || mode == null || attacker == defender
                || defender.isDead() || GameUtils.isUnbeatable(defender)
                || SkillUseHelper.isSkillBlock(defender)) return false;

        final int passive, effect;
        switch (mode) {
        case ROCK:
            passive = PassiveID.TITAN_ROCK.toInt();
            effect = 12555;
            break;
        case BLITZ:
            passive = PassiveID.TITAN_BLITZ.toInt();
            effect = 12557;
            break;
        case MAGIC:
            passive = PassiveID.TITAN_MAGIC.toInt();
            effect = 12559;
            break;
        default:
            return false;
        }
        if (!defender.isPassive(passive) || chance <= 0) return false;

        final int maxHp = defender.getMaxHp();
        final int hp = defender.getCurrentHp();
        if (maxHp <= 0 || hp <= 0 || defender.getInventory() == null) return false;
        final int ringCount = defender.getInventory().checkEquippedcount(RING_ITEM_ID);
        final int ringBonus = ringCount >= 2 ? 10 : ringCount == 1 ? 5 : 0;
        final int threshold = Math.min(100, BASE_HP_THRESHOLD + ringBonus
                + Math.max(0, defender.getRisingUp()));
        // src22 evaluates the integer HP percentage before comparison.
        final int hpPercent = (int) (((long) hp * 100L) / maxHp);
        if (hpPercent > threshold) return false;
        if (chance < 100 && ThreadLocalRandom.current().nextInt(100) >= chance) return false;

        // Do not reflect when CacheD will reject the catalyst mutation.
        if (defender.isItemTransferBusy()) return false;
        if (!defender.getInventory().HaveItemByClassId(CATALYST_ITEM_ID, CATALYST_COUNT)) {
            SC_SystemMessage.send(defender, "타이탄: 촉매제가 부족합니다.");
            return false;
        }
        final boolean consumed;
        try {
            consumed = defender.getInventory().DeleteItemInInventory(CATALYST_ITEM_ID, CATALYST_COUNT);
        } catch (IllegalStateException ex) {
            if ("Item mutation refused while CacheD transfer pending".equals(ex.getMessage()))
                return false;
            throw ex;
        }
        if (!consumed) return false;

        final L1ItemInstance weapon = defender.getWeapon();
        int damage = 0;
        if (weapon != null) {
            damage = (int) Math.round((weapon.getItem().getDmgLarge()
                    + weapon.getEnchantLevel() + weapon.getItem().getDmgModifier()) * 2.0);
            if (damage < 0) damage = 0;
        }
        if (attacker instanceof L1PcInstance pc) {
            pc.receiveCounterBarrierDamage(defender, damage);
        } else if (attacker instanceof L1NpcInstance npc) {
            npc.receiveCounterBarrierDamage(defender, damage);
        } else {
            attacker.receiveDamage(defender, damage);
        }
        defender.send_effect(effect);
        return true;
    }
}
