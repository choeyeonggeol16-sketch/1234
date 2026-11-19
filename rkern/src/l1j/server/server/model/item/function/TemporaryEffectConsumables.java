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

final class TemporaryEffectConsumables {
    private TemporaryEffectConsumables() {}
    static void use(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
		case 1000010:
			SC_SystemMessage.send(pc, "현재 사용할 수 없는 아이템입니다.");
			break;

		case 1000110:
			SC_SystemMessage.send(pc, "현재 사용할 수 없는 아이템입니다.");
			break;

		case 1000120:
			SC_SystemMessage.send(pc, "현재 사용할 수 없는 아이템입니다.");
			break;

		case 40493:
			GuardianFluteTransactions.useGuardianFlute(pc, l1iteminstance);
			break;

		case 41019:// 라스타바드 역사서 1권
			pc.start_teleport(32597, 32909, 400, pc.getHeading(), 169, true, pc.getAutoHunt());
			break;

		case 41027:// 완성한 라스타바드의 역사서
			//pc.sendPackets(new S_UserCommands7(1));
			pc.start_teleport(32745, 32791, 536, pc.getHeading(), 169, true, pc.getAutoHunt());
			break;

        }
    }
}
