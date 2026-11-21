package l1j.server.server.model.item.function;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.character.effect.PcClanBuffEffects;

import l1j.server.Config;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;



final class ClanBuffScrollTransactions {
    private ClanBuffScrollTransactions() {}

    static void useClanBuff(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null) return;
        final int template = item.getItemId();
        if (template < 145001 || template > 145009) return;
        if (pc.getClanid() == 0 || pc.getClan() == null) {
            SC_SystemMessage.send(pc, "\\fY혈맹없이는 사용이 불가능합니다.");
            return;
        }
        final L1Clan clan = pc.getClan();
        final int clanId = pc.getClanid();
        if (clan.getLeaderId() != pc.getId()) {
            SC_SystemMessage.send(pc, "\\fY혈맹의 군주만 사용가능합니다.");
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
            if (!tx.debit(item, 1)) return;
            tx.onFailure(() -> SC_SystemMessage.send(pc, "혈맹 버프 아이템 소모가 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                // Never apply the original clan's buff to a player who has
                // changed leader/clan during the asynchronous commit.
                if (pc.getClan() != clan || pc.getClanid() != clanId
                        || clan.getLeaderId() != pc.getId()) {
                    SC_SystemMessage.send(pc, "혈맹 정보가 변경되어 버프를 적용하지 못했습니다.");
                    return;
                }
                applyClanBuff(pc, clan, template);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중입니다. 다시 시도해 주세요.");
        }
    }

    static void applyClanBuff(L1PcInstance pc, L1Clan clan, int itemId) {
        final PcClanBuffEffects.Kind kind = PcClanBuffEffects.Kind.fromItemId(itemId);
        if (kind == null) return;
        final int clanId = clan.getClanId();
        final int seconds = Config.혈맹버프지속시간 * 60;
        boolean casterIncluded = false;
        for (L1PcInstance member : clan.getOnlineClanMember()) {
            if (member == pc) casterIncluded = true;
            final var controller = member.getCreatureController();
            if (controller != null) controller.addExecutableCastBuff(member, kind.skillId(), seconds, clanId, itemId, null);
        }
        if (!casterIncluded && pc.hasSkillEffect(kind.skillId())) pc.removeSkillEffect(kind.skillId());
    }
}
