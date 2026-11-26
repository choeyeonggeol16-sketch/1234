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

/** Domain-specific item handler; original transaction ordering preserved. */
final class BugRaceControlWandItems {
    private BugRaceControlWandItems() {}

static void use_bug_down_wand(L1PcInstance pc, int target_id, L1ItemInstance item){
    if(!pc.isGm())
        return;

    pc.sendPackets(BugRaceController.getInstance().down_bug(target_id) ? "선택한 버그베어를 넘어뜨렸습니다." : "넘어뜨릴 버그베어를 찾을 수 없습니다.");
}
    static void useNumberedDownWand(L1PcInstance pc, int itemId) {
        if (!pc.isGm()) return;
        int idx = itemId - 4100255;
        BugRaceController.getInstance()._is_downs[idx] = true;
        pc.sendPackets(String.format("%d번 버그베어를 넘어뜨렸습니다.", idx + 1));
    }

}
