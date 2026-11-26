package l1j.server.server.model.network.core.cs.packet;

import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.ActionCodes;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.model.inventory.L1PolyMorph;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeShape;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionShop;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.shop.dynamic.DynamicShopItem;
import l1j.server.server.model.shop.dynamic.DynamicShopStorage;
import l1j.server.server.model.inventory.CTrade;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.monitor.Logger.ItemActionType;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
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
            for (L1Object object : CWorld.getInstance().getObject()) {
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
        pc.refreshLight();
    }

    // Do not add a second pending flag or release it on DB submission.
    public static l1j.server.server.model.inventory.L1InventoryTransaction CheckAndSetPendingUseETCItem(
            L1PcInstance pc, int resourceKey) {
        return l1j.server.server.model.inventory.L1InventoryTransaction.begin(pc, resourceKey);
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
		if (pc == null || !client.ownsReadyGameSession() || client.getActiveChar() != pc
				|| CWorld.getInstance().findObject(pc.getId()) != pc) return;
		final L1ItemInstance l1iteminstance = pc.getInventory().GetByServerID(itemObjid);

        // This item only opens an owned settings window. It has no item effects,
        // consumption, transfer or PSS start, so it must not enter effect gates.
        if (l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow.tryOpenItem(client, pc, l1iteminstance)) return;
		if (pc.isDead() || pc.isGhost() || pc.getMapId() == 5166) {
			return;
		}

        // Reject before effects, charges, materials or direct item setters.
        if (pc.isItemTransferBusy() || pc.isTradeResolving() || pc.getTradeID() != 0) return;

		if (!CanUseItem(pc, l1iteminstance)) return;
		UseItem(client, pc, l1iteminstance, itemObjid, targetId, x, y, name, skillId);
	}

	private static boolean CanUseItem(L1PcInstance pc, L1ItemInstance item) {
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

		return true;
	}

    private static void UseItem(CUserSocket client, L1PcInstance pc, L1ItemInstance item,
            int itemObjectId, int targetId, int x, int y, String name, int skillId) {
        ExtendedItemActions.use(client, pc, item, itemObjectId, targetId, x, y, name, skillId);
    }


    private static final Logger PRIVATE_STORE_LOG = Logger.getLogger(User.class.getName());

    private static final int MAX_SHOP_ITEMS = 7;

    private static final byte[] EMPTY_CHAT = new byte[0];

    private static final int[] RUNE_ARMOR_IDS = {
            22232, 22233, 22234, 22235,
            22236, 22237, 22238, 22239,
            22240, 22241, 22242, 22243,
            22244, 22245, 22246, 22247,
            22248, 22249
    };


    /** The L1 client submits both store lists in one packet. The OFF User
     * admission and list-set/quit roles are kept without inventing a manage packet. */
    static void OnPrivateStorePacket(CUserSocket client, int type, byte[] data) {
        OnPrivateStorePacket(client, type, data, null, false);
    }

    static void OnPrivateStorePacket(CUserSocket client, int type, CS_ActionUi.ShopPayload payload) {
        OnPrivateStorePacket(client, type, null, payload, true);
    }

    private static void OnPrivateStorePacket(CUserSocket client, int type, byte[] data,
            CS_ActionUi.ShopPayload payload, boolean actionUi) {
        if (client == null || (type != 0 && type != 1)) return;
        final L1PcInstance pc = client.getActiveChar();
        if (pc == null) return;
        final int stamp = pc.getSessionStamp();
        // Existing reentrant synchronous inventory gate: UI morph removal may
        // call equipment methods, and both wire formats must exclude settlement.
        if (!pc.isItemTransferSession(client, stamp) || !pc.getInventory().tryBeginEquipmentChange()) return;
        try {
            if (!pc.isItemTransferSession(client, stamp)) return;
            if (type == 1) {
                if (pc.isPrivateShop() && (!actionUi || !pc.isPrivateReady())) QuitPrivateStore(pc);
                return;
            }
            if (!CanOpenPrivateStore(pc, actionUi)) return;
            if (actionUi) SetPrivateStoreList(pc, client, stamp, payload);
            else SetPrivateStoreList(pc, client, stamp, data);
        } finally {
            pc.getInventory().endEquipmentChange();
        }
    }

    private static boolean CanOpenPrivateStore(L1PcInstance pc, boolean actionUi) {
        if (pc.isDead() || pc.isGhost() || pc.get_teleport() || pc.isLogoutStarted()
                || pc.GetTrade() != null || CTrade.HasPendingTradeBlocker(pc)) return false;
        if (pc.isInvisble()) {
            SC_ServerMessage.send(pc, 755);
            return false;
        }
        if (pc.getMapId() != 800) {
            if (pc.isFishing()) stopFishing(pc);
            else SC_SystemMessage.send(pc, "개인상점은 시장에서만 열 수 있습니다.");
            return false;
        }
        if (pc.isFishing()) return false;
        if (hasEquippedRune(pc)) {
            SC_ChatPacket.sendDirect(pc, pc, "룬을 착용하셨다면 해제하시기 바랍니다.");
            return false;
        }
        if (!actionUi && pc.getCurrentSpriteId() != pc.getClassId() && !pc.isPrivateShop()
                && pc.getSkillEffectTimeSec(L1SkillId.SHAPE_CHANGE) <= 0) {
            SC_SystemMessage.send(pc, "변신 아이템을 해제하세요.");
            return false;
        }
        return true;
    }

    static boolean CanUsePrivateStore(L1PcInstance customer) {
        return customer != null && !customer.isDead() && !customer.isGhost()
                && !customer.get_teleport() && !customer.isLogoutStarted()
                && !customer.isPrivateShop() && !customer.isFishing()
                && customer.GetTrade() == null && !CTrade.HasPendingTradeBlocker(customer);
    }

    static void PrivateStoreBuy(L1PcInstance customer, L1PcInstance shop,
            boolean purchase, int size, long first, byte[] rest) {
        PlayerShopTransactions.execute(customer, shop, purchase, size, first, rest);
    }

    // =========================================================
    // OPEN
    // =========================================================

    private static void SetPrivateStoreList(L1PcInstance pc, CUserSocket session, int stamp, byte[] data) {

        if (data == null || data.length < 4) {
            return;
        }

        int cursor = 0;

        /*
         * H sellCount
         */
        if (!has(data, cursor, 2)) {
            return;
        }

        final int sellCount = readH(data, cursor);
        cursor += 2;

        if (sellCount > MAX_SHOP_ITEMS) {
            sendMaxItems(pc);
            return;
        }

        final int sellBytes = sellCount * 12;

        if (!has(data, cursor, sellBytes)) {
            return;
        }

        boolean tradable = true;
        final ArrayList<DynamicShopItem> sells = sellCount == 0 ? null : new ArrayList<>(sellCount);

        for (int i = 0; i < sellCount; i++) {
            final int objectId = readD(data, cursor);
            final int price = readD(data, cursor + 4);
            int count = readD(data, cursor + 8);

            cursor += 12;

            final L1ItemInstance item = pc.getInventory().GetByServerID(objectId);

            if (price <= 0 || item == null || item.getItem() == null || objectId <= 0 || objectId != item.getId() || item.isEquipped()
                    || item.getItemId() == L1ItemId.ADENA || (long) price * count > CInventory.MAX_AMOUNT
                    || (!item.isStackable() && count != 1) || item.getCount() < count || count <= 0
                    || containsListing(sells, objectId)) {
                SC_Disconnect.send(pc);
                return;
            }

            if (item.getBless() >= 128) {
                SC_ServerMessage.send(pc, 210, item.getItem().getName());
                return;
            }

            if (!item.getItem().isTradable()) {
                tradable = false;
                sendNotTradable(pc, item);
            }

            if (!CompanionInstanceCache.is_companion_oblivion(item.getId())) {
                sendNotTradable(pc, item);
                return;
            }

            if (IsCompanionItem(pc, item.getId())) {
                tradable = false;
                sendNotTradable(pc, item);
            }

            if (IsDollItem(pc, item.getId())) {
                tradable = false;
                sendNotTradable(pc, item);
            }

            sells.add(DynamicShopItem.create(item, count, price, false));
        }

        
        if (!has(data, cursor, 2)) {
            return;
        }

        final int buyCount = readH(data, cursor);
        cursor += 2;

        if (buyCount > MAX_SHOP_ITEMS) {
            sendMaxItems(pc);
            return;
        }

        final int buyBytes = buyCount * 12;

        if (!has(data, cursor, buyBytes)) {
            return;
        }

        final ArrayList<DynamicShopItem> buys = buyCount == 0 ? null : new ArrayList<>(buyCount);
        for (int i = 0; i < buyCount; i++) {
            final int objectId = readD(data, cursor);
            final int price = readD(data, cursor + 4);
            int count = readD(data, cursor + 8);

            cursor += 12;

            final L1ItemInstance item = pc.getInventory().GetByServerID(objectId);

            if (price <= 0 || item == null || item.getItem() == null || objectId <= 0 || objectId != item.getId()
                    || item.getItemId() == L1ItemId.ADENA || item.getBless() >= 128
                    || (long) price * count > CInventory.MAX_AMOUNT || (!item.isStackable() && count != 1) || count <= 0 || item.getCount() <= 0
                    || containsListing(sells, objectId) || containsListing(buys, objectId)) {
                SC_Disconnect.send(pc);
                return;
            }

            if (count > item.getCount()) {
                count = item.getCount();
            }

            if (!item.getItem().isTradable()) {
                tradable = false;
                sendNotTradable(pc, item);
            }

            if (!CompanionInstanceCache.is_companion_oblivion(item.getId())) {
                sendNotTradable(pc, item);
                return;
            }

            if (IsCompanionItem(pc, item.getId())) {
                tradable = false;
                sendNotTradable(pc, item);
            }

            if (IsDollItem(pc, item.getId())) {
                tradable = false;
                sendNotTradable(pc, item);
            }

            buys.add(DynamicShopItem.create(item, count, price, true));
        }

        if (!tradable || (sellCount == 0 && buyCount == 0)) return;

        final int chatLength = data.length - cursor;
        final byte[] chat;

        if (chatLength <= 0) {
            chat = EMPTY_CHAT;
        } else {
            chat = new byte[chatLength];
            System.arraycopy(data, cursor, chat, 0, chatLength);
        }

        CompletePrivateStoreOpen(pc, session, stamp, sells, buys, chat, resolveShopPoly(chat), false);
    }

    /** ActionUI uses explicit entry tags and a separate skin string, not the old wire layout. */
    private static void SetPrivateStoreList(L1PcInstance pc, CUserSocket session, int stamp,
            CS_ActionUi.ShopPayload payload) {
        if (payload == null || payload.count <= 0 || payload.count > MAX_SHOP_ITEMS * 2
                || payload.entries == null || payload.entries.length < payload.count * 4) return;
        if (!pc.getInventory().HaveItemByClassId(L1ItemId.ADENA, 1000)) {
            SC_ChatPacket.sendDirect(pc, pc, "\\aG상점 설치는 1,000원이 필요합니다.");
            return;
        }
        for (L1PcInstance other : CWorld.getInstance().getAllPlayers3()) {
            if (other != pc && other.getId() != pc.getId() && other.isPrivateShop()
                    && pc.getAccountName().equalsIgnoreCase(other.getAccountName())) {
                SC_SystemMessage.send(pc, "이미 당신의 보조 캐릭터가 무인상점 상태입니다.");
                if (pc.isPrivateShop()) QuitPrivateStore(pc);
                return;
            }
        }
        ArrayList<DynamicShopItem> sells = null, buys = null;
        for (int i = 0; i < payload.count; i++) {
            final int base = i << 2;
            final int type = payload.entries[base];
            if (type != 0x12 && type != 0x1A) return;
            final boolean purchase = type == 0x1A;
            final int id = payload.entries[base + 1], price = payload.entries[base + 2],
                    amount = payload.entries[base + 3];
            final L1ItemInstance item = pc.getInventory().GetByServerID(id);
            if (id <= 0 || item == null || item.getId() != id || item.getItem() == null
                    || item.getCount() <= 0 || amount <= 0 || price <= 0
                    || item.getItemId() == L1ItemId.ADENA || (long) price * amount > CInventory.MAX_AMOUNT
                    || (!item.isStackable() && amount != 1)
                    || (!purchase && (item.isEquipped() || amount > item.getCount()))
                    || containsListing(sells, id) || containsListing(buys, id)) {
                SC_Disconnect.send(pc);
                return;
            }
            if (item.getBless() >= 128 || !item.getItem().isTradable()
                    || !CompanionInstanceCache.is_companion_oblivion(id)
                    || IsCompanionItem(pc, id) || IsDollItem(pc, id)) {
                sendNotTradable(pc, item);
                return;
            }
            ArrayList<DynamicShopItem> list = purchase ? buys : sells;
            if (list == null) {
                list = new ArrayList<>(MAX_SHOP_ITEMS);
                if (purchase) buys = list; else sells = list;
            }
            if (list.size() >= MAX_SHOP_ITEMS) {
                sendMaxItems(pc);
                return;
            }
            list.add(DynamicShopItem.create(item, amount, price, purchase));
        }
        final byte[] chat = payload.chat == null ? EMPTY_CHAT : payload.chat;
        CompletePrivateStoreOpen(pc, session, stamp, sells, buys, chat, resolveShopPoly(payload.polyNum), true);
    }

    private static void CompletePrivateStoreOpen(L1PcInstance pc, CUserSocket session, int stamp,
            ArrayList<DynamicShopItem> sells, ArrayList<DynamicShopItem> buys,
            byte[] chat, int poly, boolean actionUi) {
        if (!pc.isItemTransferSession(session, stamp) || !CanOpenPrivateStore(pc, actionUi)) return;
        if (!PublishPrivateStoreList(pc, sells, buys, chat)) return;
        if (actionUi) {
            // Same ActionUI morph semantics as before; after accepted list and
            // under the reentrant gate, never against a rejected registration.
            L1PolyMorph.undoPoly(pc);
            pc.killSkillEffectTimer(L1SkillId.SHAPE_CHANGE);
            if (pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER))
                pc.removeSkillEffect(L1SkillId.POLY_RING_MASTER);
        }
        final CUserSocket client = session;

        if (client != null && client.getAccount() != null) {
            SC_PacketBox.send(pc, SC_PacketBox.상점개설횟수, client.getAccount().Shop_open_count);
        }

        SC_DoActionShop.send(pc, pc.getId(), ActionCodes.ACTION_Shop, chat);
        SC_DoActionShop.broadcast(pc, pc.getId(), ActionCodes.ACTION_Shop, chat);

        pc.상점변신 = poly;
        pc.setCurrentSprite(poly);

        SC_ChangeShape.send(pc, pc.getId(), poly, 70);
        SC_ChangeShape.broadcast(pc, pc.getId(), poly, 70);

        final long visual = SC_CharVisualUpdate.pack(pc);
        SC_CharVisualUpdate.sendPacked(pc, visual);
        SC_CharVisualUpdate.broadcastPacked(pc, visual);

        SC_WorldPutObject.broadcastPc(pc);
        pc.curePoison();
    }

    // =========================================================
    // CLOSE
    // =========================================================

    private static void QuitPrivateStore(L1PcInstance pc) {
        if (!RemovePrivateStoreList(pc)) return;
        pc.상점변신 = 0;
        sendIdle(pc);
        L1PolyMorph.undoPolyPrivateShop(pc);
    }

    private static boolean PublishPrivateStoreList(L1PcInstance pc, ArrayList<DynamicShopItem> sells,
            ArrayList<DynamicShopItem> buys, byte[] chat) {
        try {
            DynamicShopStorage.replacePlayerListings(pc, sells, buys);
            pc.setSellings(sells);
            pc.setPurchasings(buys);
            pc.setShopChat(chat);
            pc.setPrivateShop(true);
            return true;
        } catch (RejectedExecutionException rejected) {
            PRIVATE_STORE_LOG.log(Level.WARNING, "Private shop listing admission rejected", rejected);
            SC_SystemMessage.send(pc, "잠시 후 개인상점을 다시 열어 주세요.");
            return false;
        }
    }

    static boolean RemovePrivateStoreList(L1PcInstance pc) {
        try {
            DynamicShopStorage.deleteProcess(pc);
            pc.setPrivateShop(false);
            return true;
        } catch (RejectedExecutionException rejected) {
            SC_SystemMessage.send(pc, "잠시 후 개인상점을 다시 닫아 주세요.");
            return false;
        }
    }

    private static boolean containsListing(ArrayList<DynamicShopItem> list, int objectId) {
        if (list == null) return false;
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).objId == objectId) return true;
        return false;
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private static boolean hasEquippedRune(L1PcInstance pc) {

        for (int i = 0; i < RUNE_ARMOR_IDS.length; i++) {
            if (pc.getInventory().isEquippedAny(RUNE_ARMOR_IDS[i])) {
                return true;
            }
        }

        return false;
    }

    public static boolean IsCompanionItem(L1PcInstance pc, int itemObjectId) {
        final var companion = pc.get_companion();
        return companion != null && companion.get_control_object_id() == itemObjectId;
    }

    public static boolean IsDollItem(L1PcInstance pc, int itemObjectId) {

        for (Object value : pc.getDollList().values()) {
            if (value instanceof L1DollInstance) {
                final L1DollInstance doll = (L1DollInstance) value;

                if (doll.getItemObjId() == itemObjectId) {
                    return true;
                }
            }
        }

        return false;
    }

    // =========================================================
    // CHAT -> POLY (상점 스킨 변신)
    // =========================================================

    private static int resolveShopPoly(String skin) {
        if (skin == null || skin.length() != 10 || !skin.regionMatches(true, 0, "tradezone", 0, 9)) return 0;
        return switch (skin.charAt(9)) {
            case '1' -> 11479; case '2' -> 11483; case '3' -> 11480; case '4' -> 11485;
            case '5' -> 11482; case '6' -> 11486; case '7' -> 11481; case '8' -> 11484;
            default -> 0;
        };
    }

    private static int resolveShopPoly(byte[] chat) {

        if (containsAscii(chat, "tradezone1")) {
            return 11479;
        }
        if (containsAscii(chat, "tradezone2")) {
            return 11483;
        }
        if (containsAscii(chat, "tradezone3")) {
            return 11480;
        }
        if (containsAscii(chat, "tradezone4")) {
            return 11485;
        }
        if (containsAscii(chat, "tradezone5")) {
            return 11482;
        }
        if (containsAscii(chat, "tradezone6")) {
            return 11486;
        }
        if (containsAscii(chat, "tradezone7")) {
            return 11481;
        }
        if (containsAscii(chat, "tradezone8")) {
            return 11484;
        }
        return 0;
    }

    private static boolean containsAscii(byte[] data, String target) {

        if (data == null) {
            return false;
        }

        final int targetLength = target.length();
        final int last = data.length - targetLength;

        if (last < 0) {
            return false;
        }

        for (int i = 0; i <= last; i++) {
            int j = 0;

            while (j < targetLength && (data[i + j] & 0xFF) == target.charAt(j)) {
                j++;
            }

            if (j == targetLength) {
                return true;
            }
        }

        return false;
    }

    // =========================================================
    // WIRE (네트워크 버퍼 읽기)
    // =========================================================

    private static boolean has(byte[] data, int offset, int length) {
        return offset >= 0 && length >= 0 && offset <= data.length - length;
    }

    private static int readH(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readD(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    // =========================================================
    // COMMON (공통 유틸리티)
    // =========================================================

    private static void stopFishing(L1PcInstance pc) {

        try {
            pc.setFishing(false);
            pc.setFishingTime(0);
            pc.setFishingReady(false);

            final long visual = SC_CharVisualUpdate.pack(pc);

            SC_CharVisualUpdate.sendPacked(pc, visual);
            SC_CharVisualUpdate.broadcastPacked(pc, visual);

            FishingTimeController.getInstance().removeMember(pc);

            SC_ServerMessage.send(pc, 2120);

        } catch (Exception ignored) {
        }
    }

    private static void sendNotTradable(L1PcInstance pc, L1ItemInstance item) {
        SC_ServerMessage.send(pc, 166, item.getItem().getName(), "거래 불가능합니다.");
    }

    private static void sendMaxItems(L1PcInstance pc) {
        SC_ChatPacket.sendDirect(pc, pc, "물품 등록은 " + MAX_SHOP_ITEMS + "개까지만 가능합니다.");
    }

    private static void sendIdle(L1PcInstance pc) {
        SC_DoActionGFX.send(
                pc.getNetConnection(),
                pc.getId(),
                ActionCodes.ACTION_Idle);
        SC_DoActionGFX.broadcast(pc, ActionCodes.ACTION_Idle);
    }

}
