package l1j.server.server.model.item.function;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import static l1j.server.server.model.item.function.TravelUseTables.*;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
final class TravelUtilityExchangeItems {
    private TravelUtilityExchangeItems() {}
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 700021: // 봉인해제 주문서 신청서
			if (pc.getInventory().HaveItemByClassId(50021)) {
				SC_ChatPacket.sendDirect(pc, pc, "이미 봉인해제 주문서를 갖고 있습니다.");
				return;
			}
			if (pc.getInventory().HaveItemByClassId(700021, 1)) {
				ItemReceiptOperations.UseDurableExchange(pc, l1iteminstance, 50021, 15, null);
			}
			break;

		case 60256:
			if (pc.getInventory().HaveItemByClassId(60255)) {
				SC_ServerMessage.send(pc, 939);
				SC_SystemMessage.send(pc, "드래곤의 자수정 보유.");
				return;
			}
			ItemReceiptOperations.UseDurableExchange(pc, l1iteminstance, 60255);
			break;

        }
    }
}
