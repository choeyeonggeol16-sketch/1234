package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.Config;
import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.MJINNSystem.MJINNRoom;
import l1j.server.Stadium.StadiumManager;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1ItemBookMark;
import l1j.server.server.utils.CommonUtil;

import static l1j.server.server.model.item.function.TravelUseTables.*;
final class ExperienceAndElixirUseItems {
    private ExperienceAndElixirUseItems() {}
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 700000: { // 경험치 물약
			final int level = pc.getLevel();
			if (level > Config.ExpPo2) {
				pc.sendPackets("" + Config.ExpPo2 + "레벨 이하만 사용이 가능합니다.");
				return;
			}
			final int expReward;
			if (level >= 1 && level <= 48) expReward = 326144;
			else if (level <= 64) expReward = 2609152;
			else if (level <= 69) expReward = 1304576;
			else if (level <= 74) expReward = 652288;
			else if (level <= 78) expReward = 326144;
			else if (level == 79) expReward = 163072;
			else if (level <= 81) expReward = 81536;
			else if (level <= 83) expReward = 40768;
			else if (level <= 85) expReward = 20384;
			else if (level == 86) expReward = 10192;
			else if (level == 87) expReward = 5096;
			else if (level == 88) expReward = 2048;
			else if (level == 89) expReward = 1024;
			else expReward = 512;
			ExperiencePotionTransactions.useFixedExpPotion(pc, l1iteminstance, expReward);
			break;
		}

		case 4100074:
			if (pc.getLevel() > Config.ExpPo) {
				pc.sendPackets("" + Config.ExpPo + "레벨 이하만 사용이 가능합니다.");
				return;
			}
			ExperiencePotionTransactions.useFixedExpPotion(pc, l1iteminstance, 360650);
			break;

		case 4100075:
			if (pc.getLevel() < Config.ExpPo1) {
				pc.sendPackets("" + Config.ExpPo1 + "레벨 이상만 사용이 가능합니다.");
				return;
			}
			ExperiencePotionTransactions.useFixedExpPotion(pc, l1iteminstance, 360650);
			break;
//		case 350198:// 티칼달력
//			if (CrockController.getInstance().isTimeCrock()) {
//				pc.sendPackets(new SC_NPCTalkReturn(pc.getId(), "tcalendaro"));
//			} else {
//				pc.sendPackets(new SC_NPCTalkReturn(pc.getId(), "tcalendarc"));
//			}
//			break;

		case 40033: // 엘릭서 힘
		case 40034: // 엘릭서 체력
		case 40035: // 엘릭서 민첩
		case 40036: // 엘릭서 지능
		case 40037: // 엘릭서 지혜
		case 40038: // 엘릭서 매력
			ElixirStatTransactions.UseElixirAtomic(pc, l1iteminstance);
			break;

        }
    }
}
