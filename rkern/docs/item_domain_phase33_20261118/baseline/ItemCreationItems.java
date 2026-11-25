package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Random;
import java.util.logging.Logger;
import l1j.server.server.ActionCodes;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.character.skill.L1SkillId.ABSOLUTE_BARRIER;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;

/** Domain-specific item handler; original transaction ordering preserved. */
final class ItemCreationItems {
    private ItemCreationItems() {}

public static boolean createNewItem(L1PcInstance pc, int item_id, int count) {
    L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
    if (item != null) {
        item.setCount(count);
        if (pc.getInventory().checkAddItem(item, count) == CInventory.OK) {
            pc.getInventory().storeItem(item);
        } else {
            SC_ServerMessage.send(pc, 82);
            return false;
        }
        SC_ServerMessage.send(pc, 403, item.getLogName());
        return true;
    } else {
        return false;
    }
}

static boolean createGMItem(L1PcInstance pc, int item_id, int count) {
    L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
    if (item != null) {
        item.setCount(count);
        item.setIdentified(true);
        if (pc.getInventory().checkAddItem(item, count) == CInventory.OK) {
            pc.getInventory().storeItem(item);
        } else {
            SC_ServerMessage.send(pc, 82);
            return false;
        }
        SC_ServerMessage.send(pc, 403, item.getLogName());
        return true;
    } else {
        return false;
    }
}

static boolean createNewItem2(L1PcInstance pc, int item_id, int count, int EnchantLevel) {
    L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
    if (item != null) {
        item.setCount(count);
        item.setEnchantLevel(EnchantLevel);
        item.setIdentified(true);
        if (pc.getInventory().checkAddItem(item, count) == CInventory.OK) {
            pc.getInventory().storeItem(item);
        } else {
            SC_ServerMessage.send(pc, 82);

            return false;
        }
        SC_ServerMessage.send(pc, 403, item.getLogName());

        return true;
    } else {
        return false;
    }
}
}
