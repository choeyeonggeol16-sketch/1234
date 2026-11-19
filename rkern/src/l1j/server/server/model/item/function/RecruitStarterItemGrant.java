package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;

/** Prepares a detached reward inside the caller's single inventory receipt. */
final class RecruitStarterItemGrant {
    private RecruitStarterItemGrant() {}

    static boolean 봉인템(L1PcInstance pc, L1InventoryTransaction tx,
            ArrayList<String> announcements, int itemId, int count,
            int enchantLevel, int bless, int attr, boolean identified) {
        final L1Item template = ItemTable.getInstance().getTemplate(itemId);
        if (template == null || count <= 0) {
            SC_ServerMessage.send(pc, 82);
            return false;
        }
        // Do NOT use ItemTable.createItem(): it registers an uncommitted object in L1World.
        final L1ItemInstance item = new L1ItemInstance(template, count);
        item.setId(IdFactory.getInstance().nextId());
        item.setEnchantLevel(enchantLevel);
        item.setAttrEnchantLevel(attr);
        // The original helper sets identified=true unconditionally before persisting.
        item.setIdentified(true);
        item.setBless(bless);
        final String logName = item.getLogName();
        if (tx.grant(item) == null) {
            SC_ServerMessage.send(pc, 82);
            return false;
        }
        // Original 403 messages are produced in grant order, now only after COMMIT.
        announcements.add(logName);
        return true;
    }
}
