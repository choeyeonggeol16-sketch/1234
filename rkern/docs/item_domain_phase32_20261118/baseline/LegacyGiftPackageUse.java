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
final class LegacyGiftPackageUse {
    private LegacyGiftPackageUse() {}

    static ItemActionResult handleOpenLegacyGift(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int 랜덤 = _random.nextInt(120) + 1;
        pc.getInventory().storeItem(40308, 500000);
        SC_SystemMessage.send(pc, "아데나 (500,000)을 얻었습니다.");
        if (랜덤 >= 1 && 랜덤 <= 12) {
            pc.getInventory().storeItem(20315, 1);
            SC_SystemMessage.send(pc, "영양 만점 허리끈 을 얻었습니다.");
        } else if (랜덤 >= 13 && 랜덤 <= 24) {
            pc.getInventory().storeItem(20262, 1);
            SC_SystemMessage.send(pc, "영양 만점 목걸이 을 얻었습니다.");
        } else if (랜덤 >= 25 && 랜덤 <= 36) {
            pc.getInventory().storeItem(20291, 1);
            SC_SystemMessage.send(pc, "영양 만점 반지 을 얻었습니다.");
        } else if (랜덤 >= 37 && 랜덤 <= 48) {
            pc.getInventory().storeItem(40087, 1);
            SC_SystemMessage.send(pc, "무기 마법 주문서 을 얻었습니다.");
        } else if (랜덤 >= 49 && 랜덤 <= 59) {
            pc.getInventory().storeItem(40074, 1);
            SC_SystemMessage.send(pc, "갑옷 마법 주문서 을 얻었습니다.");
        } else if (랜덤 >= 60 && 랜덤 <= 65) {
            pc.getInventory().storeItem(41248, 1);
            SC_SystemMessage.send(pc, "마법인형(버그베어) 을 얻었습니다.");
        } else if (랜덤 >= 66 && 랜덤 <= 71) {
            pc.getInventory().storeItem(210096, 1);
            SC_SystemMessage.send(pc, "마법인형(에티) 을 얻었습니다.");
        } else if (랜덤 >= 72 && 랜덤 <= 74) {
            pc.getInventory().storeItem(210105, 1);
            SC_SystemMessage.send(pc, "마법인형(코카트리스) 을 얻었습니다.");
        } else if (랜덤 >= 75 && 랜덤 <= 77) {
            pc.getInventory().storeItem(20422, 1);
            SC_SystemMessage.send(pc, "빛나는 고대 목걸이 을 얻었습니다.");
        } else if (랜덤 >= 78 && 랜덤 <= 79) {
            pc.getInventory().storeItem(22000, 1);
            SC_SystemMessage.send(pc, "고대명궁가더 을 얻었습니다.");
        } else if (랜덤 >= 80 && 랜덤 <= 81) {
            pc.getInventory().storeItem(22003, 1);
            SC_SystemMessage.send(pc, "고대투사가더 을 얻었습니다.");
        } else if (랜덤 >= 82 && 랜덤 <= 86) {
            pc.getInventory().storeItem(30127, 1);
            SC_SystemMessage.send(pc, "52레벨 퀘스트 아이템 상자를 얻었습니다.");
        } else {
            SC_SystemMessage.send(pc, "아이템을 획득하지 못하였습니다.");
        }
        pc.getInventory().removeItem(item, 1);
        return ItemActionResult.COMPLETE;
    }
}
