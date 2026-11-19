package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ShowPolyList;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.TransformationItems.useLevelPolyScroll;
import static l1j.server.server.model.item.function.TransformationItems.usePolyPotion;
import static l1j.server.server.model.item.function.TransformationItems.usePolyScroll;

/** Item-specific action effects dispatched by item ID. */
final class TransformationListItems {
    private TransformationListItems() {}

    static ItemActionResult handleShowPixieForms(ItemActionContext use) {
        L1PcInstance pc = use.player();
        int itemId = use.itemId();
        SC_ShowPolyList.send(pc, pc.getId(), "pixies");
        if (!pc.isMagicItem()) {
            pc.setMagicItem(true);
            pc.setMagicItemId(itemId);
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleShowTamForms(ItemActionContext use) {
        L1PcInstance pc = use.player();
        int itemId = use.itemId();
        SC_ShowPolyList.send(pc, pc.getId(), "tam60");
        if (!pc.isMagicItem()) {
            pc.setMagicItem(true);
            pc.setMagicItemId(itemId);
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleShowJinDeathForms(ItemActionContext use) {
        L1PcInstance pc = use.player();
        SC_ShowPolyList.send(pc, pc.getId(), "jindeath2017");
        return ItemActionResult.COMPLETE;
    }

    /** Card transformation list, intentionally invoked before normal admission. */
    static boolean showNeigeSelection(L1PcInstance pc) {
        SC_SkillSound.send(pc,pc.getId(),21174);
        if(pc.isInvisble()) {
            pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
            return false;
        }
        SC_NPCTalkReturn.send(pc,pc.getId(),"neigetele4");
        return true;
    }

}
