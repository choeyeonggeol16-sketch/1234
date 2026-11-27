package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL2;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL3;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL4;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL5;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CASHSCROLL6;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.CSkillInfo;
import l1j.server.server.model.character.skill.CSkillEffectActivation;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.ItemPresentOutStream;

/** Shared item-to-buff mapping for cash scroll use and presentation. */
public final class CashScrollPotionEffects {
    private static final int DURATION_MINUTES = 30;

    private enum Scroll {
        HP(STATUS_CASHSCROLL),
        MP(STATUS_CASHSCROLL2),
        DAMAGE(STATUS_CASHSCROLL3),
        MAGIC(STATUS_CASHSCROLL4),
        RANGED(STATUS_CASHSCROLL5),
        MELEE(STATUS_CASHSCROLL6);

        final int skillId;
        final CSkillInfo.FixedItemBonuses bonuses;

        Scroll(int skillId) {
            this.skillId = skillId;
            bonuses = CSkillInfo.fixedItemBonuses(skillId);
        }
    }

    private static final Scroll[] SCROLLS = Scroll.values();

    private CashScrollPotionEffects() {}

    private static Scroll forItem(int itemId) {
        return switch (itemId) {
            case 410010 -> Scroll.HP;
            case 410011 -> Scroll.MP;
            case 410012, 30063 -> Scroll.DAMAGE;
            case 4100039 -> Scroll.MAGIC;
            case 4100041 -> Scroll.RANGED;
            case 4100042 -> Scroll.MELEE;
            default -> null;
        };
    }

    public static void useCashScroll(L1PcInstance pc, int itemId) {
        final Scroll scroll = forItem(itemId);
        if (scroll == null) return;
        for (Scroll previous : SCROLLS) {
            if (!pc.hasSkillEffect(previous.skillId)) continue;
            pc.killSkillEffectTimer(previous.skillId);
            CSkillEffectActivation.notifyCashScrollStats(pc, previous.skillId);
        }

        final int id = scroll.skillId;
        pc.applySkillModifierFromOwner(id, 0);
        CSkillEffectActivation.notifyCashScrollStats(pc, id);
        SC_SkillSound.send(pc, pc.getId(), id);
        SC_SkillSound.broadcast(pc, pc.getId(), id);
        pc.setSkillEffect(id, DURATION_MINUTES * 60_000);
    }

    public static void writeTooltip(int itemId, ItemPresentOutStream out) {
        final Scroll scroll = forItem(itemId);
        if (scroll == null) return;
        final var stats = scroll.bonuses;
        out.writeAddMaxHP(stats.hp());
        out.writeMaxMP(stats.mp());
        out.writeShortDMG(stats.meleeDamage());
        out.writeShortHIT(stats.meleeHit());
        out.writeLongDMG(stats.rangedDamage());
        out.writeLongHIT(stats.rangedHit());
        out.writeAddSP(stats.sp());
        out.writeMagicHIT(stats.magicHit());
        out.writePVPAddDMGdown(stats.pvpReduction());
        out.writeText("\\fI사용 시간: \\aA" + DURATION_MINUTES + "분.");
    }
}
