package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.L1CardAdd;
import l1j.server.server.model.item.Card.L1CardCollection;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.CreatureItemActions._random;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableTwoInput;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class LegacyGiftGroupTwoUse {
    private LegacyGiftGroupTwoUse() {}

    static ItemActionResult handleOpenLegacyGiftGroup2(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int 랜덤1 = _random.nextInt(170) + 1;
        pc.getInventory().storeItem(40308, 5000000);
        SC_SystemMessage.send(pc, "아데나 (5,000,000)을 얻었습니다.");
        if (랜덤1 >= 1 && 랜덤1 <= 25) {
            pc.getInventory().storeItem(41249, 1);
            SC_SystemMessage.send(pc, "마법인형(서큐버스) 을 얻었습니다.");
        } else if (랜덤1 >= 26 && 랜덤1 <= 51) {
            pc.getInventory().storeItem(41250, 1);
            SC_SystemMessage.send(pc, "마법인형(늑대인간) 을 얻었습니다.");
        } else if (랜덤1 >= 52 && 랜덤1 <= 77) {
            pc.getInventory().storeItem(210070, 1);
            SC_SystemMessage.send(pc, "마법인형(돌 골렘) 을 얻었습니다.");
        } else if (랜덤1 >= 78 && 랜덤1 <= 88) {
            pc.getInventory().storeItem(40038, 1);
            SC_SystemMessage.send(pc, "엘릭서(CHA) 을 얻었습니다.");
        } else if (랜덤1 >= 89 && 랜덤1 <= 99) {
            pc.getInventory().storeItem(140087, 1);
            SC_SystemMessage.send(pc, "축복받은 무기 마법 주문서 을 얻었습니다.");
        } else if (랜덤1 >= 100 && 랜덤1 <= 110) {
            pc.getInventory().storeItem(140074, 1);
            SC_SystemMessage.send(pc, "축복받은 갑옷 마법 주문서 을 얻었습니다.");
        } else if (랜덤1 >= 111 && 랜덤1 <= 112) {
            pc.getInventory().storeItem(202002, 1);
            SC_SystemMessage.send(pc, "붉은 기사의 대검 을 얻었습니다.");
        } else if (랜덤1 >= 113 && 랜덤1 <= 114) {
            pc.getInventory().storeItem(504, 1);
            SC_SystemMessage.send(pc, "흑요석 키링크 을 얻었습니다.");
        } else if (랜덤1 >= 115 && 랜덤1 <= 116) {
            pc.getInventory().storeItem(205, 1);
            SC_SystemMessage.send(pc, "달의 장궁 을 얻었습니다.");
        } else if (랜덤1 >= 117 && 랜덤1 <= 118) {
            pc.getInventory().storeItem(20165, 1);
            SC_SystemMessage.send(pc, "데몬의 장갑 을 얻었습니다.");
        } else if (랜덤1 >= 119 && 랜덤1 <= 120) {
            pc.getInventory().storeItem(20197, 1);
            SC_SystemMessage.send(pc, "데몬의 부츠 을 얻었습니다.");
        } else if (랜덤1 >= 121 && 랜덤1 <= 122) {
            pc.getInventory().storeItem(20160, 1);
            SC_SystemMessage.send(pc, "흑장로의 로브 을 얻었습니다.");
        } else if (랜덤1 >= 123 && 랜덤1 <= 124) {
            pc.getInventory().storeItem(20218, 1);
            SC_SystemMessage.send(pc, "흑장로의 샌달 을 얻었습니다.");
        } else if (랜덤1 >= 125 && 랜덤1 <= 126) {
            pc.getInventory().storeItem(20298, 1);
            SC_SystemMessage.send(pc, "제니스의 반지 을 얻었습니다.");
        } else if (랜덤1 >= 127 && 랜덤1 <= 131) {
            pc.getInventory().storeItem(30127, 1);
            SC_SystemMessage.send(pc, "52레벨 퀘스트 아이템 상자를 얻었습니다.");
        } else {
            SC_SystemMessage.send(pc, "아이템을 획득하지 못하였습니다.");
        }
        pc.getInventory().removeItem(item, 1);
        return ItemActionResult.COMPLETE;
    }
}
