package l1j.server.server.packets.Protocol.Packet;

import l1j.server.MJCompanion.Basic.Skills.MJCompanionClassSkillInfo;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillTierOpenInfo;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;

/**
 * CS_COMPANION_SKILL_NEXT_TIER_OPEN_REQ (0x07D7) — 컴패니언 야성(스킬 티어) 개방 요청
 * wire: 필드 없음 (빈 body)
 */
public final class CS_COMPANION_SKILL_NEXT_TIER_OPEN_REQ {

    private CS_COMPANION_SKILL_NEXT_TIER_OPEN_REQ() {}

    public static void handleRequest(CIOClient client, ProtoReader r) {
        // ── 파싱: 필드 없음 (프레임 무결성만 ProtoReader로 확인) ──
        try {
            while (!r.isAtEnd()) {
                int tag = r.tag();
                r.skip(tag & 7);            // 알 수 없는 필드 흡수 (현재 스키마상 빈 body)
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // ── 처리 (구 readFrom 로직 이식) ──
        try {
            L1PcInstance pc = client.getActiveChar();
            if (pc == null) return;

            L1CompanionInstance companion = pc.get_companion();
            if (companion == null) return;

            int sourceTier = companion.get_tier();
            int nextTier = sourceTier + 1;
            MJCompanionSkillTierOpenInfo oInfo = MJCompanionSkillTierOpenInfo.get_tier_open_info(nextTier);
            MJCompanionClassSkillInfo nextClassSkillInfo = MJCompanionClassSkillInfo.get_companion_skills(
                    companion.get_class_info().get_class_name(), nextTier);
            if (oInfo == null || nextClassSkillInfo == null) {
                pc.sendPackets("다음 단계 정보를 찾을 수 없습니다.");
                System.out.println(String.format(
                        "%s님의 펫 %s(%d) 야성 열기에 실패했습니다. 현재 티어 : %d, 다음 티어 : %d",
                        pc.getName(), companion.getName(), companion.getId(), sourceTier, nextTier));
                return;
            }
            MJCompanionClassSkillInfo sourceClassSkillInfo = MJCompanionClassSkillInfo.get_companion_skills(
                    companion.get_class_info().get_class_name(), sourceTier);
            if (sourceClassSkillInfo == null) {
                pc.sendPackets("현재 단계 정보를 찾을 수 없습니다.");
                System.out.println(String.format(
                        "%s님의 펫 %s(%d)[%s] 야성 열기에 실패했습니다. 현재 티어 : %d, 다음 티어 : %d",
                        pc.getName(), companion.getName(), companion.getId(),
                        companion.get_class_info().get_class_name(), sourceTier, nextTier));
                return;
            }
            if (companion.getLevel() < oInfo.get_open_cost_level()) {
                pc.sendPackets(String.format("%d레벨 이상부터 사용 가능합니다.", oInfo.get_open_cost_level()));
                return;
            }
            if (!check_enchant(companion, sourceClassSkillInfo.get_skills_id(), oInfo.get_open_cost_min_enchant())) {
                pc.sendPackets(String.format("%d단계 야성의 인챈트가 부족합니다. (필요 인챈트:%d)",
                        sourceTier, oInfo.get_open_cost_min_enchant()));
                return;
            }
            if (!pc.getInventory().checkItem(L1ItemId.ADENA, oInfo.get_open_cost_adena()) ||
                    !pc.getInventory().consumeItem(L1ItemId.ADENA, oInfo.get_open_cost_adena())) {
                pc.sendPackets(String.format("%d단계 야성을 활성화 하기 위해서는 아데나 %d이(가) 필요합니다.",
                        nextTier, oInfo.get_open_cost_adena()));
                return;
            }
            companion.do_open_tier(nextClassSkillInfo);
            companion.update_tier();
            companion.update_skill_effect();
            SC_COMPANION_SKILL_NOTI.send(pc, companion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 현재 티어 스킬들이 최소 인챈트 요건을 충족하는지 */
    private static boolean check_enchant(L1CompanionInstance companion, int[] checkSkillsId, int minEnchant) {
        for (int skillId : checkSkillsId) {
            SC_COMPANION_SKILL_NOTI.Skill sk = companion.find_skills(skillId);
            if (sk == null || sk.get_enchant() < minEnchant)
                return false;
        }
        return true;
    }
}