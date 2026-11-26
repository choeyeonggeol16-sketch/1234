package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.LetterTable;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.monitor.Logger.ItemActionType;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.function.ExtendedItemActions;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.CommonUtil;

public final class User {
    private User() {}

    // Synchronous on the already admitted execution context; never enqueue again.
    static void OnNpcActionPacket(CUserSocket client, int objId, String command,
            String argument, byte[] actionData) {
        HtmlCmdHandler.handle(client, objId, command, argument, actionData);
    }

    static void OnNpcTalkPacket(CUserSocket client, int objId) {
        NpcTalkHandler.handle(client, objId);
    }

    static void OnUseItemPacket(CUserSocket client, int itemObjid, int targetId,
            int x, int y, String name, int skillId) {
        OnUseItemPacket0(client, itemObjid, targetId, x, y, name, skillId);
    }

    /** OFF DestroyItem -> User::OnDestroyItemPacket. L1 keeps its multi-item wire format. */
    static void OnDestroyItemPacket(CUserSocket client, int length, int objectId, int count, int[] pairs) {
        if (client == null || length <= 0 || length > 100) return;
        final L1PcInstance pc = client.getActiveChar();
        if (pc == null || pc.isDead() || pc.isGhost() || pc.get_teleport()
                || pc.isPrivateShop() || pc.isLogoutStarted()
                || pc.isItemTransferBusy() || pc.GetTrade() != null) return;

        if (length == 1) {
            if (objectId <= 0 || count < 0) return;
        } else {
            if (pairs == null || pairs.length != (length << 1)) return;
            // A row may appear only once in one destroy request. No partial batch.
            for (int i = 0; i < pairs.length; i += 2) {
                if (pairs[i] <= 0 || pairs[i + 1] < 0) return;
                for (int j = 0; j < i; j += 2)
                    if (pairs[j] == pairs[i]) return;
            }
        }
        RequestDestroyItem(pc, length, objectId, count, pairs);
    }

    private static boolean CanDestroyItem(L1PcInstance pc, L1ItemInstance item) {
        if (item == null || item.getItem() == null || item.getCount() <= 0) return false;
        if (item.isEquipped() || (!pc.isGm() && item.getItem().isCantDelete())) {
            SC_ServerMessage.send(pc, 125);
            return false;
        }
        final L1CompanionInstance companion = pc.get_companion();
        if ((!pc.isGm() && item.getBless() >= 128)
                || !CompanionInstanceCache.is_companion_oblivion(item.getId())
                || (companion != null && companion.get_control_object_id() == item.getId())) {
            SC_ServerMessage.send(pc, 210, item.getItem().getName());
            return false;
        }
        for (Object value : pc.getDollList().values()) {
            if (value instanceof L1DollInstance doll && doll.getItemObjId() == item.getId()) {
                SC_ServerMessage.send(pc, 210, item.getItem().getName());
                return false;
            }
        }
        // Deployed furniture owns a live world object. Require retrieval first;
        // the receipt also checks the persisted placement under the SQL lock.
        if (item.getItemId() >= 41383 && item.getItemId() <= 41400) {
            for (L1Object object : L1World.getInstance().getObject()) {
                if (object instanceof L1FurnitureInstance furniture
                        && furniture.getItemObjId() == item.getId()) {
                    SC_SystemMessage.send(pc, "설치된 가구를 회수한 뒤 삭제해 주세요.");
                    return false;
                }
            }
        }
        return true;
    }

    /** Prepare all deletions under one owner gate; nothing visible changes before COMMIT.
     * The existing mailbox lane also orders physical letter deletion against mail operations. */
    private static void RequestDestroyItem(L1PcInstance pc, int length, int objectId, int count, int[] pairs) {
        final L1InventoryTransaction receipt;
        try {
            receipt = L1InventoryTransaction.begin(pc, LetterTable.mailboxOrderKey(pc.getName()));
        } catch (IllegalStateException | java.util.concurrent.RejectedExecutionException unavailable) {
            return;
        }
        try (receipt) {
            final L1ItemInstance[] items = new L1ItemInstance[length];
            final int[] amounts = new int[length];
            for (int i = 0; i < length; i++) {
                final int id = length == 1 ? objectId : pairs[i << 1];
                final int requested = length == 1 ? count : pairs[(i << 1) + 1];
                final L1ItemInstance item = pc.getInventory().GetByServerID(id);
                if (!CanDestroyItem(pc, item)) return;
                // The L1 client uses zero to request deletion of the entire stack.
                final int amount = requested == 0 ? item.getCount() : requested;
                if (amount <= 0 || amount > item.getCount() || amount > CInventory.MAX_AMOUNT
                        || (!item.isStackable() && amount != 1)) return;
                if (!receipt.StageDestroyItem(item, amount)) return;
                items[i] = item;
                amounts[i] = amount;
            }
            receipt.onFailure(() -> SC_SystemMessage.send(pc, "아이템 삭제에 실패했습니다."));
            receipt.commit(() -> DestroyItemReturn(pc, items, amounts));
        } catch (IllegalStateException | java.util.concurrent.RejectedExecutionException unavailable) {
            SC_SystemMessage.send(pc, "아이템 삭제에 실패했습니다.");
        }
    }

    /** Only the confirmed CacheD/CIO reply may report successful deletion.
     * L1InventoryTransaction has already applied counts/removal and inventory packets. */
    private static void DestroyItemReturn(L1PcInstance pc, L1ItemInstance[] items, int[] amounts) {
        for (int i = 0; i < items.length; i++)
            LoggerInstance.getInstance().addItemAction(ItemActionType.Delete, pc, items[i], amounts[i]);
        pc.getLight().turnOnOffLight();
    }

    // L1 adaptation of reference server pending admission: the existing inventory transaction
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
			CUserSocket client,
			int itemObjid,
			int targetId,
			int x,
			int y,
			String name,
			int skillId) {

		final L1PcInstance pc = client.getActiveChar();
		if (pc == null) return;
		final L1ItemInstance l1iteminstance = pc.getInventory().GetByServerID(itemObjid);

        // This item only opens an owned settings window. It has no item effects,
        // consumption, transfer or PSS start, so it must not enter effect gates.
        if (l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow.tryOpenItem(client, pc, l1iteminstance)) return;
		if (pc.isDead() || pc.isGhost() || pc.getMapId() == 5166) {
			return;
		}

        // Reject before effects, charges, materials or direct item setters.
        if (pc.isItemTransferBusy() || pc.isTradeResolving() || pc.getTradeID() != 0) return;

		if (!CanUseItem(client, pc, l1iteminstance)) return;
		UseItem(client, pc, l1iteminstance, itemObjid, targetId, x, y, name, skillId);
	}

	private static boolean CanUseItem(CUserSocket client, L1PcInstance pc, L1ItemInstance item) {
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

    private static void UseItem(CUserSocket client, L1PcInstance pc, L1ItemInstance item,
            int itemObjectId, int targetId, int x, int y, String name, int skillId) {
        ExtendedItemActions.use(client, pc, item, itemObjectId, targetId, x, y, name, skillId);
    }

}
