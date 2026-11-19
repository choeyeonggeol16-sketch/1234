package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_MITHRIL_POWDER;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_WATER;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_WATER_OF_EVA;
import l1j.server.Config;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_AuctionSystemBoard;
import l1j.server.server.model.network.core.sc.packet.SC_Board;
import l1j.server.server.model.network.core.sc.packet.SC_Boarditem;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

final class EquipmentSlotVoucherItems {
    private EquipmentSlotVoucherItems() {}
    static void use(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
		case 4100203: // 슬롯 전체 개방권
		case 4100140: // 귀걸이 개방권
		case 4100141: // 왼쪽 반지 개방권
		case 4100142: // 오른쪽 반지 개방권
		case 4100143: // 견갑 개방권
		case 4100144: // 휘장 개방권
			EquipmentSlotUnlockReceipt.useSlotUnlockAtomic(pc, l1iteminstance);
			break;

        }
    }
}
