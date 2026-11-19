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

final class EventUtilityConsumables {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(EventUtilityConsumables.class.getName());

    private EventUtilityConsumables() {}

    private static void notifyEvaWater(L1PcInstance pc) {
        try {
            SC_SkillSound.send(pc, pc.getId(), 190);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Eva holy water self sound failed", failure);
        }
        try {
            SC_SkillSound.broadcast(pc, pc.getId(), 190);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Eva holy water broadcast failed", failure);
        }
        try {
            SC_ServerMessage.send(pc, 1140);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Eva holy water notice failed", failure);
        }
    }
    static void use(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
		case 474748: {
		    SC_SkillSound.send(pc, pc.getId(), 21178);

		    SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "거래는 1천만 수표로만 이용이 가능합니다.");

		    final L1NpcInstance boardNpc =
		            L1World.getInstance()
		                    .findNpcByNpcId(4747470);

		    if (boardNpc != null) {
		        SC_Boarditem.sendListAsync(pc, boardNpc, 0);
		    }

		    SC_SystemMessage.send(pc, "[.물품판매] [.물품구입]으로 판매/구매 가능합니다.");

		    SC_SystemMessage.send(pc, "[.물품취소] 를 통해 본인이 등록한 물품을 취소할 수 있습니다.");

		    SC_SystemMessage.send(pc, "[.물품멘트 켬]하시면 실시간으로 판매등록된 물품들이 채팅창에 출력이 됩니다.");

		    break;
		}


		case 810011:
			SC_ChatPacket.sendDirect(pc, pc, "몽환의 섬 입구 크루나에게 물건을 구입할 수 있다.");
			break;

		case 3000048: // 꼬마 요정의 마음
			SC_ChatPacket.sendDirect(pc, pc, "생일 축하 요정에게 '코마'를 한 번 받을 수 있다.");
			break;

		case 40328:// 6단계 마법주사위
			int gfxid = 3204 + java.util.concurrent.ThreadLocalRandom.current().nextInt(6);
			SC_SkillSound.send(pc, pc.getId(), gfxid);
			SC_SkillSound.broadcast(pc, pc.getId(), gfxid);
			break;

		case 45450:
		case 45451:
		case 45452:
		case 45453:
			SC_SkillSound.send(pc, pc.getId(), 21129);//21178 편지소리
			pc.sendPackets("기존 자동물약/자동버프는 제거되었습니다. 플레이서포터를 사용하세요.");
			break;

		case 12301:
			SC_SkillSound.send(pc, pc.getId(), 21129);
			//if(pc.getLevel() < 30){
			//	pc.sendPackets("튜토리얼 중에는 사용할 수 없습니다.");
			//	return;}
			SC_NPCTalkReturn.send(pc, pc.getId(), "GMt");

			break;

		case 41354:// 신성한 에바의 물
			if (pc.hasSkillEffect(STATUS_HOLY_WATER) || pc.hasSkillEffect(STATUS_HOLY_MITHRIL_POWDER)) {
				SC_ServerMessage.send(pc, 79); // \f1 아무것도 일어나지 않았습니다.
				return;
			}
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.setSkillEffect(STATUS_HOLY_WATER_OF_EVA, 900 * 1000);
				notifyEvaWater(pc);
			});
			break;

        }
    }
}
