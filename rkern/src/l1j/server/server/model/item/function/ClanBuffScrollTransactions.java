package l1j.server.server.model.item.function;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.Config;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;



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
        final int skill;
        final int icon;
        final String green;
        final String notice;
        switch (itemId) {
        case 145001:
            skill = L1SkillId.혈맹버프AC; icon = 5538;
            green = "혈맹의 군주가 [혈맹버프: AC -5]를 사용하였습니다";
            notice = "\\f:혈맹의 군주가 [혈맹버프:AC -5]를 사용하였습니다";
            break;
        case 145002:
            skill = L1SkillId.혈맹버프마방; icon = 5539;
            green = "혈맹의 군주가 [혈맹버프: MR +10%]를 사용하였습니다";
            notice = "\\f:혈맹의 군주가 [혈맹버프: MR +10%]를 사용하였습니다";
            break;
        case 145003:
            skill = L1SkillId.혈맹버프PVP추가; icon = 5540;
            green = "혈맹의 군주가 [혈맹버프: PVP추가대미지 +3]를 사용하였습니다";
            notice = "\\f:혈맹의 군주가 [혈맹버프: PVP추가대미지 +3]를 사용하였습니다";
            break;
        case 145004:
            skill = L1SkillId.혈맹버프PVP감소; icon = 5541;
            green = "혈맹의 군주가 [혈맹버프: PVP대미지감소 +3]를 사용하였습니다";
            notice = "\\f:혈맹의 군주가 [혈맹버프: PVP대미지감소 +3]를 사용하였습니다";
            break;
        case 145005:
            skill = L1SkillId.혈맹버프HP증가; icon = 5542;
            green = "혈맹의 군주가 [혈맹버프: HP증가 +150]를 사용하였습니다";
            notice = "\\f:혈맹의 군주가 [혈맹버프: HP증가 +150]를 사용하였습니다";
            break;
        case 145006:
            skill = L1SkillId.혈맹버프올내성; icon = 5543;
            green = "혈맹의 군주가 [혈맹버프: 전체 내성 +3]를 사용하였습니다";
            notice = "\\f:혈맹의 군주가 [혈맹버프: 전체 내성 +3]를 사용하였습니다";
            break;
        case 145007:
            skill = L1SkillId.혈맹버프올적중; icon = 5544;
            green = "혈맹의 군주가 [혈맹버프: 전체 적중 +3]를 사용하였습니다";
            notice = "\\f:혈맹의 군주가 [혈맹버프: 전체 적중 +3]를 사용하였습니다";
            break;
        case 145008:
            skill = L1SkillId.혈맹버프올스텟; icon = 5545;
            green = "혈맹의 군주가 [혈맹버프: 전체 스텟 +3]를 사용하였습니다";
            notice = "\\f:혈맹의 군주가 [혈맹버프: 전체 스텟 +3]를 사용하였습니다";
            break;
        case 145009:
            skill = L1SkillId.혈맹버프회복; icon = 5546;
            green = "혈맹의 군주가 [혈맹버프: 물약회복량 +" + Config.혈맹버프회복 + "%]을 사용하였습니다";
            notice = "\\f:혈맹의 군주가 [혈맹버프: 물약회복량 +" + Config.혈맹버프회복 + "%]를 사용하였습니다";
            break;
        default: return;
        }
        final int seconds = Config.혈맹버프지속시간 * 60;
        if (pc.hasSkillEffect(skill)) pc.removeSkillEffect(skill);
        try(var greenShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_PacketBox.nativeGreenWriter(green),Integer.MIN_VALUE,Integer.MAX_VALUE);
var noticeShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_SystemMessage.nativeWriter(notice),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance member : clan.getOnlineClanMember()) {
            if (member.hasSkillEffect(skill)) member.removeSkillEffect(skill);
            switch (itemId) {
            case 145001: member.혈맹버프AC(true); break;
            case 145002: member.혈맹버프마방(true); break;
            case 145003: member.혈맹버프PVP추가(true); break;
            case 145004: member.혈맹버프PVP감소(true); break;
            case 145005: member.혈맹버프HP증가(true); break;
            case 145006: member.혈맹버프올내성(true); break;
            case 145007: member.혈맹버프올적중(true); break;
            case 145008: member.혈맹버프올스텟(true); break;
            default: break;
            }
            member.setSkillEffect(skill, seconds * 1000);
            SC_InventoryIcon.icoNew(member, skill, icon, seconds, true);
            SC_SkillSound.send(member, member.getId(), 13566);
            SC_SkillSound.broadcast(member, member.getId(), 13566);
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(greenShared,member);
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(noticeShared,member);
        }
}
    }
}
