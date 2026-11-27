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
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.character.skill.L1SkillId.ABSOLUTE_BARRIER;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;

/** Domain-specific item handler; original transaction ordering preserved. */
final class NpcRemovalWandItems {
    private NpcRemovalWandItems() {}

static void useFieldObjectRemovalWand(L1PcInstance pc, int targetId, L1ItemInstance item) {
    SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
    SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
    L1Object target = CWorld.getInstance().findObject(targetId);
    if (target != null && target instanceof L1NpcInstance && !(target instanceof L1DollInstance)) {
        L1NpcInstance npc = (L1NpcInstance) target;
        NpcSpawnTable.getInstance().removeSpawn(npc);
        npc.setRespawn(false);
        npc.startTimedDelete(1 * 1);
        SC_SystemMessage.send(pc, "GM: 해당 엔피씨가 삭제 되었습니다.");
        pc.getInventory().updateItem(item, CUserInventory.COL_CHARGE_COUNT);
    }
}
}
