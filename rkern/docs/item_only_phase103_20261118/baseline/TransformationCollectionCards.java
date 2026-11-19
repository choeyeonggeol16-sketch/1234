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

/** Item-specific action effects dispatched by item ID. */
final class TransformationCollectionCards {
    private TransformationCollectionCards() {}

    static ItemActionResult handleUnlockCollection(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.getAccount().setInfo_kir_poly(pc.getAccount().getInfo_kir_poly()+1);
        pc.getInventory().removeItem(item, 1);
        pc.getAccount().updateKir();
        pc.getAccount().setInfo_col_y(1);
        pc.getAccount().updateCollection();
        L1CardCollection.solejoin(pc);
        SC_ChatPacket.sendDirect(pc, pc, "\\aD[유일]지배자 기르타스 (이)가 변신 컬렉션에 등록되었습니다.");
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollection(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.myth(pc);
            return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollectionGroup2(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.legend1(pc);
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollectionGroup3(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.legend2(pc);
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollectionGroup4(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.hero1(pc);
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollectionGroup5(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.hero2(pc);
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSelectCollectionGroup6(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        pc.setCollecItem(item);
        L1CardAdd.SkillTree(pc);
        return ItemActionResult.COMPLETE;
    }

}
