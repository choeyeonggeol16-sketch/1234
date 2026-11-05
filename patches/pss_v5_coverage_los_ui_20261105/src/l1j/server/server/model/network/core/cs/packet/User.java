package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.function.ItemUseExtensions;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.CommonUtil;

public final class User {
    private User() {}

    static void OnUseItemPacket(CIOClient client, int itemObjid, int targetId,
            int x, int y, String name, int skillId) {
        OnUseItemPacket0(client, itemObjid, targetId, x, y, name, skillId);
    }

    // L1 adaptation of NC pending admission: the existing inventory transaction
    // atomically reserves the owner. Its CIO/CacheD completion releases the gate.
    // Do not add a second pending flag or release it on DB submission.
    public static l1j.server.server.model.L1InventoryTransaction CheckAndSetPendingUseETCItem(
            L1PcInstance pc, int resourceKey) {
        return l1j.server.server.model.L1InventoryTransaction.begin(pc, resourceKey);
    }

    // Called only after the existing CacheD receipt and owner-CIO replay succeed.
    // Inventory images and the FIFO owner-apply barrier have been retired.
    // Do not require the old gate here: legacy effects may legitimately reserve
    // a new receipt or change equipment, and must not borrow a replay bypass.
    static void OnReplyUseItemPacket(L1PcInstance pc, Runnable afterCommitted) {
        if (afterCommitted != null) afterCommitted.run();
    }

	private static void OnUseItemPacket0(
			CIOClient client,
			int itemObjid,
			int targetId,
			int x,
			int y,
			String name,
			int skillId) {

		final L1PcInstance pc = client.getActiveChar();
		if (pc == null) return;
		final L1ItemInstance l1iteminstance = pc.getInventory().getItem(itemObjid);

        // This item only opens an owned settings window. It has no item effects,
        // consumption, transfer or PSS start, so it must not enter effect gates.
        if (l1j.server.server.model.character.pss.window.PlaySupportWindow.tryOpenItem(client, pc, l1iteminstance)) return;
		if (pc.isDead() || pc.isGhost() || pc.getMapId() == 5166) {
			return;
		}

        // Reject before effects, charges, materials or direct item setters.
        if (pc.isItemTransferBusy() || pc.isTradeResolving() || pc.getTradeID() != 0) return;

		if (!CanUseItem(client, pc, l1iteminstance)) return;
		UseItem(client, pc, l1iteminstance, itemObjid, targetId, x, y, name, skillId);
	}

	private static boolean CanUseItem(CIOClient client, L1PcInstance pc, L1ItemInstance item) {
		if (!CItem.CheckItemUseCondition(pc, item)) return false;

		if (pc.get_teleport() || pc.isDead()) {
			return false;
		}

		if (!pc.getMap().isUsableItem()) {
			SC_ServerMessage.send(pc, 563);
			SC_Paralysis.send(pc, 7, false);
			return false;
		}

		if (CommonUtil.teleport_check(pc, item)) {
			SC_Paralysis.send(pc, 7, false);
			SC_ServerMessage.send(pc, 647);
			return false;
		}

		L1PcInstance jonje = L1World.getInstance().getPlayer(pc.getName());
		if (jonje == null && pc.getAccessLevel() != 200) {
			SC_SystemMessage.send(pc, "존재버그 강제종료! 재접속하세요");
			client.kick();
			return false;
		}

		return true;
	}

    private static void UseItem(CIOClient client, L1PcInstance pc, L1ItemInstance item,
            int itemObjectId, int targetId, int x, int y, String name, int skillId) {
        ItemUseExtensions.use(client, pc, item, itemObjectId, targetId, x, y, name, skillId);
    }

}
