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
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1ItemBookMark;
import l1j.server.server.utils.CommonUtil;

import static l1j.server.server.model.item.function.TravelUseTables.*;
final class CharacterPackageUseItems {
    private CharacterPackageUseItems() {}
    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 3000126: // 회복의문장 +3 주머니
		case 3000127: // 성장의문장 +3 주머니
			if (pc.getInventory().getSize() > 120) {
				SC_SystemMessage.send(pc, "소지하고 있는 아이템이 너무 많습니다.");
				return;
			}
			if (pc.getInventory().getWeight100() > 82) {
				SC_SystemMessage.send(pc, "소지품이 너무 무거워서 사용 할 수 없습니다.");
				return;
			}
			EnchantedBagTransactions.UseDurableEnchantedBag(pc, l1iteminstance,
					itemId == 3000126 ? 900021 : 900020, 3);
			break;

		case 89136: // 특수 슬롯 확장권 (20 -> 40)
			InventorySlotExpansionItems.UseSpecialSizeExpansion(pc, l1iteminstance);
			break;

		case 875640417:
			SC_SkillSound.send(pc, pc.getId(), 21174);
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop02");
			break;

		case 875640450:
			SC_SkillSound.send(pc, pc.getId(), 21174);
			SC_NPCTalkReturn.send(pc, pc.getId(), "inshop19");
			break;

		case 123124: 
			if (pc.getDollList().size() >= 1) {
				SC_SystemMessage.send(pc, "\\f:인형을 소환해제 후 가능합니다.");
				return;
			}			
			if (pc.getClanid() != 0) {
				SC_ChatPacket.sendDirect(pc, pc, "혈맹을 먼저 탈퇴하여 주시기 바랍니다.");
				return;
			}
			SC_NPCTalkReturn.send(pc, pc.getId(), "change");			
			break; 

		case 12302: 
			SC_NPCTalkReturn.send(pc, pc.getId(), "chang");			
			break; 

        }
    }
}
