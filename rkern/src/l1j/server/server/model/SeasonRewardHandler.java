package l1j.server.server.model;

import l1j.server.Config;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.inventory.L1InventoryTransaction;

/**
 * 시즌 정보 및 레벨 보상 핸들러
 */
public class SeasonRewardHandler {

	/**
	 * 시즌 정보 출력 UI
	 */
	public static void showSeasonInfo(L1PcInstance pc) {
		if (pc == null) return;

		// 데이터 배열로 깔끔하게 정리
		String[] info = {
				"1",            // 시즌 회차
				"70",           // 만렙
				"전설",          // 등급
				"5단계(용 x)"     // 인형
		};

		SC_NPCTalkReturn.send(pc, pc.getId(), "neihelp2", info);
	}

	/**
	 * 레벨 보상 지급 
	 */
    public static void claimLevelReward(L1PcInstance pc) {
        if (pc == null || pc.isItemTransferBusy() || pc.getTradeID() != 0) return;
        if (pc.getLevel() < Config.SEASON_REWARD_LEVEL) {
            SC_ChatPacket.sendDirect(pc, pc, String.format("레벨이 부족합니다. (필요 레벨: %d)", Config.SEASON_REWARD_LEVEL));
            return;
        }
        if (pc.getLevel_q() >= 1) {
            SC_ChatPacket.sendDirect(pc, pc, "이미 수령한 보상입니다.");
            return;
        }
        final int itemId = getFallbackItemId(pc.getClassId());
        final int amount = 1;
        if (itemId <= 0 || amount <= 0) {
            SC_ChatPacket.sendDirect(pc, pc, "보상 설정을 확인할 수 없습니다.");
            return;
        }
        final int objectId = pc.getId();
        final int beforeFlag = pc.getLevel_q();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                CacheD.accountOrderKey("season-reward:" + objectId))) {
            tx.requireTables("characters");
            if (tx.grant(itemId, amount) == null) return;
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE characters SET level_q=1 WHERE objid=? AND level_q=? AND level>=?")) {
                    ps.setInt(1, objectId);
                    ps.setInt(2, beforeFlag);
                    ps.setInt(3, Config.SEASON_REWARD_LEVEL);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Season reward eligibility/claim changed: " + objectId);
                }
            });
            tx.commit(() -> {
                pc.setLevel_q(1);
                SC_SkillSound.send(pc, pc.getId(), Config.SEASON_REWARD_EFFECT);
                SC_SkillSound.broadcast(pc, pc.getId(), Config.SEASON_REWARD_EFFECT);
                SC_ChatPacket.sendDirect(pc, pc, "레벨 선물 상자를 지급받았습니다.");
            });
        } catch (RuntimeException failure) {
            java.util.logging.Logger.getLogger(SeasonRewardHandler.class.getName())
                    .log(java.util.logging.Level.WARNING, "Season reward preparation failed", failure);
            SC_ChatPacket.sendDirect(pc, pc, "보상 지급을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

	/**
	 * 보상 아이템 ID 매핑 
	 */
	private static int getFallbackItemId(int classId) {
		switch (classId) {
		case 0:    case 1:     return 875640400; // 군주
		case 61:   case 48:    return 875640401; // 기사
		case 138:  case 37:    return 875640402; // 요정
		case 734:  case 1186:  return 875640403; // 법사
		case 2786: case 2796:  return 875640404; // 다엘
		case 6658: case 6661:  return 875640405; // 용기사
		case 12490:case 12494: return 875640406; // 환술사
		default: return 0;
		}
	}
}