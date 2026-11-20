package l1j.server.server.model.item.function;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

final class CardFusionChanceItems {
    private static final Logger LOG = Logger.getLogger(CardFusionChanceItems.class.getName());
    private CardFusionChanceItems() {}

    // The input item's debit has already committed before this runs. Failure
    // to render a message is not a transaction failure and must not quarantine
    // the character or prevent the independent second notification.
    private static void notifyApplied(L1PcInstance pc, String message) {
        try {
            SC_SystemMessage.send(pc, message);
        } catch (RuntimeException packetFailure) {
            LOG.log(Level.WARNING, "Card fusion potion committed; system message failed: " + pc.getId(),
                    packetFailure);
        }
        try {
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);
        } catch (RuntimeException packetFailure) {
            LOG.log(Level.WARNING, "Card fusion potion committed; packet box failed: " + pc.getId(),
                    packetFailure);
        }
    }

    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
		case 5200101:{ //영웅카드 합성확률 증가
			if(pc.영웅합성확률증가) {
				SC_SystemMessage.send(pc, "이미 확률포션이 적용중입니다.");
				return;
			}

			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.영웅합성확률증가 = true;
				notifyApplied(pc, "영웅카드 합성확률이 증가합니다!");
			});
		}
		break;

		case 5200102:{ //전설카드 합성확률증가
			if(pc.전설합성확률증가) {
				SC_SystemMessage.send(pc, "이미 확률포션이 적용중입니다.");
				return;
			}

			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.전설합성확률증가 = true;
				notifyApplied(pc, "전설카드 합성확률이 증가합니다!");
			});
		}
		break;

		case 5200103:{ //신화카드 합성확률 증가
			if(pc.신화합성확률증가) {
				SC_SystemMessage.send(pc, "이미 확률포션이 적용중입니다.");
				return;
			}

			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.신화합성확률증가 = true;
				notifyApplied(pc, "신화카드 합성확률이 증가합니다!");
			});
		}
		break;

        }
    }
}
