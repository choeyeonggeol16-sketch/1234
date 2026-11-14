package l1j.server.server.packets.Protocol.Packet;

import l1j.server.MJCompanion.Basic.Skills.MJCompanionClassSkillInfo;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillEnchantInfo;
import l1j.server.MJCompanion.Instance.MJCompanionUpdateFlag;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.Packet.SC_COMPANION_SKILL_ENCHANT_ACK.eResult;

/**
 * CS_COMPANION_SKILL_ENCHANT_REQ (0x07D8) — 컴패니언 스킬 인챈트 요청
 * wire: [1]=skill_id uint32 (required)
 */
public final class CS_COMPANION_SKILL_ENCHANT_REQ {

    private CS_COMPANION_SKILL_ENCHANT_REQ() {}

    public static void handleRequest(CIOClient client, ProtoReader r) {
        // ── 파싱 ──
        int skillId = 0;
        boolean hasSkillId = false;
        try {
            while (!r.isAtEnd()) {
                int tag = r.tag();
                switch (tag) {
                case (1 << 3) | 0:
                    skillId = r.varint32();
                    hasSkillId = true;
                    break;
                default:
                    r.skip(tag & 7);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // required 계약
        if (!hasSkillId) return;

        // ── 처리 (구 readFrom 로직 이식) ──
        try {
            L1PcInstance pc = client.getActiveChar();
            if (pc == null) return;

            L1CompanionInstance companion = pc.get_companion();
            if (companion == null) {
                SC_COMPANION_SKILL_ENCHANT_ACK.send(pc, skillId, eResult.NOTHING);
                return;
            }
            int tier = MJCompanionClassSkillInfo.id_to_tier(skillId);
            if (tier == -1) {
                System.out.println(String.format("%s님이 알 수 없는 스킬아이디로 인챈트를 시도했습니다. 스킬아이디 : %d", pc.getName(), skillId));
                SC_COMPANION_SKILL_ENCHANT_ACK.send(pc, skillId, eResult.NOTHING);
                return;
            }
            if (companion.get_tier() < tier) {
                System.out.println(String.format("%s님이 높은 티어의 스킬아이디로 인챈트를 시도했습니다. 스킬아이디 : %d, 스킬티어 : %d, 펫 티어 : %d",
                        pc.getName(), skillId, tier, companion.get_tier()));
                SC_COMPANION_SKILL_ENCHANT_ACK.send(pc, skillId, eResult.NOTHING);
                return;
            }

            SC_COMPANION_SKILL_NOTI.Skill sk = companion.find_skills(skillId);
            if (sk == null) {
                System.out.println(String.format("%s님이 획득되지 않은 스킬아이디로 인챈트를 시도했습니다. 스킬아이디 : %d, 스킬티어 : %d, 펫 티어 : %d",
                        pc.getName(), skillId, tier, companion.get_tier()));
                SC_COMPANION_SKILL_ENCHANT_ACK.send(pc, skillId, eResult.NOTHING);
                return;
            }
            MJCompanionSkillEnchantInfo eInfo = MJCompanionSkillEnchantInfo.get_enchant_info(tier, sk.get_enchant() + 1);
            if (eInfo == null) {
                System.out.println(String.format("%s님이 시도한 인챈트 스킬에 대한 다음 정보를 찾을 수 없습니다. 스킬아이디 : %d, 스킬티어 : %d, 펫 티어 : %d, 현재인챈트 : %d",
                        pc.getName(), skillId, tier, companion.get_tier(), sk.get_enchant()));
                SC_COMPANION_SKILL_ENCHANT_ACK.send(pc, skillId, eResult.NOTHING);
                return;
            }
            if (companion.get_friend_ship_marble() < eInfo.get_enchant_cost_friend_ship()
                    || !pc.getInventory().checkItem(eInfo.get_enchant_cost_item_id())) {
                SC_COMPANION_SKILL_ENCHANT_ACK.send(pc, skillId, eResult.NOTHING);
                return;
            }
            if (!pc.getInventory().consumeItem(eInfo.get_enchant_cost_item_id(), 1)) {
                SC_COMPANION_SKILL_ENCHANT_ACK.send(pc, skillId, eResult.NOTHING);
                return;
            }
            companion.set_friend_ship_marble(companion.get_friend_ship_marble() - eInfo.get_enchant_cost_friend_ship());
            if (!MJRnd.isWinning(1000000, eInfo.get_probability())) {
                SC_COMPANION_SKILL_ENCHANT_ACK.send(pc, skillId, eResult.FAIL);
                SC_COMPANION_STATUS_NOTI.send(pc, companion, MJCompanionUpdateFlag.UPDATE_FRIEND_SHIP);
            } else {
                sk.set_enchant(eInfo.get_enchant());
                companion.update_skill_effect();
                SC_COMPANION_SKILL_ENCHANT_ACK.send(pc, skillId, eResult.SUCCESS);
                SC_COMPANION_SKILL_NOTI.send(pc, sk);
                SC_COMPANION_STATUS_NOTI.send(pc, companion,
                        MJCompanionUpdateFlag.UPDATE_HP |
                        MJCompanionUpdateFlag.UPDATE_DELAY_REDUCE |
                        MJCompanionUpdateFlag.UPDATE_FRIEND_SHIP |
                        MJCompanionUpdateFlag.UPDATE_MR |
                        MJCompanionUpdateFlag.UPDATE_PVP_DAMAGE |
                        MJCompanionUpdateFlag.UPDATE_AC);
            }
            companion.update_friend_ship_marble();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}