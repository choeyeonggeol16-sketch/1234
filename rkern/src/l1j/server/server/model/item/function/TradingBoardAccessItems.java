package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_MITHRIL_POWDER;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_WATER;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_WATER_OF_EVA;
import l1j.server.Config;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_Board;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

final class TradingBoardAccessItems {
    private TradingBoardAccessItems() {}
    static void use(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
		case 3000195: {
		    SC_SkillSound.send(pc, pc.getId(), 21178);

		    final L1NpcInstance boardNpc =
		            CWorld.getInstance()
		                    .findNpcByNpcId(4200015);

		    if (boardNpc != null) {
		        SC_Board.send(pc, boardNpc);
		    }

		    break;
		}


		case 474750: { // 자유
		    SC_SkillSound.send(pc, pc.getId(), 21178);

		    SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "거래 전, 공지사항의 '거래 게시판 이용 수칙'을 반드시 정독해주시기 바랍니다.");

		    final L1NpcInstance boardNpc =
		            CWorld.getInstance()
		                    .findNpcByNpcId(4200022);

		    if (boardNpc != null) {
		        SC_Board.send(pc, boardNpc);
		    }

		    break;
		}


		case 474751: { // 영자
		    final L1NpcInstance boardNpc =
		            CWorld.getInstance()
		                    .findNpcByNpcId(4200015);

		    if (boardNpc != null) {
		        SC_Board.send(pc, boardNpc);
		    }

		    break;
		}



        }
    }
}
