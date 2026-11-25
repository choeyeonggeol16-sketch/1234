package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Random;
import java.util.logging.Logger;
import l1j.server.server.ActionCodes;
import l1j.server.server.Controller.BugRaceController;
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
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.character.skill.L1SkillId.ABSOLUTE_BARRIER;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;

/** Compatibility entry and shared legacy effect helpers. */
public final class CreatureItemActions {
    private CreatureItemActions() {}

    static final Logger ITEM_USE2_LOG=Logger.getLogger(SpecialConsumableRouter.class.getName());
    static final Random _random=new Random(System.nanoTime());

    public static void UseItem(CUserSocket client, L1PcInstance pc, L1ItemInstance item,
            int itemObjid, int targetId, int x, int y, String name, int skillId) {
        ItemActionService.use(client,pc,item,itemObjid,targetId,x,y,name,skillId);
    }




public static boolean createNewItem(L1PcInstance pc, int item_id, int count) {
        return ItemCreationItems.createNewItem(pc, item_id, count);
    }

static boolean createGMItem(L1PcInstance pc, int item_id, int count) {
        return ItemCreationItems.createGMItem(pc, item_id, count);
    }

static boolean createNewItem2(L1PcInstance pc, int item_id, int count, int EnchantLevel) {
        return ItemCreationItems.createNewItem2(pc, item_id, count, EnchantLevel);
    }

public static void cancelAbsoluteBarrier(L1PcInstance pc) {
    if (pc.hasSkillEffect(ABSOLUTE_BARRIER)) {
        pc.removeSkillEffect(ABSOLUTE_BARRIER);
    }
}




}
