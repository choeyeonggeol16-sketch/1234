package l1j.server.server.model.network.core.cs.packet;

import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ActionCodes;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeShape;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionShop;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.shop.dynamic.DynamicShopItem;
import l1j.server.server.model.shop.dynamic.DynamicShopStorage;

public final class CS_PersonalShop {

    private static final Logger _log = Logger.getLogger(CS_PersonalShop.class.getName());

    private static final int MAX_SHOP_ITEMS = 7;

    private static final byte[] EMPTY_CHAT = new byte[0];

    private static final int[] RUNE_ARMOR_IDS = {
            22232, 22233, 22234, 22235,
            22236, 22237, 22238, 22239,
            22240, 22241, 22242, 22243,
            22244, 22245, 22246, 22247,
            22248, 22249
    };

    private CS_PersonalShop() {}

    public static void handle(CUserSocket client, int type, byte[] data) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null || pc.isGhost() || pc.isItemTransferBusy() || pc.isTradeResolving()) {
            return;
        }
        
        if (pc.isInvisble()) {
            SC_ServerMessage.send(pc, 755);
            return;
        }

        if (pc.getMapId() != 800) {
            if (pc.isFishing()) {
                stopFishing(pc);
            } else {
                SC_SystemMessage.send(pc, "개인상점은 시장에서만 열 수 있습니다.");
            }
            return;
        }

        if (hasEquippedRune(pc)) {
            SC_ChatPacket.sendDirect(pc, pc, "룬을 착용하셨다면 해제하시기 바랍니다.");
            return;
        }

        if (pc.getCurrentSpriteId() != pc.getClassId() && pc.getSkillEffectTimeSec(L1SkillId.SHAPE_CHANGE) <= 0) {
            SC_SystemMessage.send(pc, "변신 아이템을 해제하세요.");
            return;
        }

        switch (type) {
            case 0:
                openShop(pc, data);
                return;

            case 1:
                closeShop(pc);
                return;

            default:
                return;
        }
    }

    // =========================================================
    // OPEN
    // =========================================================

    private static void openShop(L1PcInstance pc, byte[] data) {

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

            if (price <= 0 || item == null || objectId != item.getId() || item.isEquipped()
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

            if (isPetItem(pc, item.getId())) {
                tradable = false;
                sendNotTradable(pc, item);
            }

            if (isDollItem(pc, item.getId())) {
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

            if (price <= 0 || item == null || objectId != item.getId()
                    || (!item.isStackable() && count != 1) || count <= 0 || item.getCount() <= 0
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

            if (isPetItem(pc, item.getId())) {
                tradable = false;
                sendNotTradable(pc, item);
            }

            if (isDollItem(pc, item.getId())) {
                tradable = false;
                sendNotTradable(pc, item);
            }

            buys.add(DynamicShopItem.create(item, count, price, true));
        }

        if (!tradable) return;

        final int chatLength = data.length - cursor;
        final byte[] chat;

        if (chatLength <= 0) {
            chat = EMPTY_CHAT;
        } else {
            chat = new byte[chatLength];
            System.arraycopy(data, cursor, chat, 0, chatLength);
        }

        if (!replaceListings(pc, sells, buys)) return;
        final CUserSocket client = pc.getNetConnection();

        if (client != null && client.getAccount() != null) {
            SC_PacketBox.send(pc, SC_PacketBox.상점개설횟수, client.getAccount().Shop_open_count);
        }

        pc.setShopChat(chat);

        SC_DoActionShop.send(pc, pc.getId(), ActionCodes.ACTION_Shop, chat);
        SC_DoActionShop.broadcast(pc, pc.getId(), ActionCodes.ACTION_Shop, chat);

        final int poly = resolveShopPoly(chat);

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

    private static void closeShop(L1PcInstance pc) {
        if (!removeListings(pc)) return;
        pc.상점변신 = 0;
        sendIdle(pc);
        L1PolyMorph.undoPolyPrivateShop(pc);
    }

    static boolean replaceListings(L1PcInstance pc, ArrayList<DynamicShopItem> sells, ArrayList<DynamicShopItem> buys) {
        if (!pc.tryBeginItemTransfer()) return false;
        try {
            DynamicShopStorage.replacePlayerListings(pc, sells, buys);
            pc.setSellings(sells);
            pc.setPurchasings(buys);
            pc.setPrivateShop(true);
            return true;
        } catch (RejectedExecutionException rejected) {
            _log.log(Level.WARNING, "Private shop listing admission rejected", rejected);
            SC_SystemMessage.send(pc, "잠시 후 개인상점을 다시 열어 주세요.");
            return false;
        } finally {
            pc.endItemTransfer();
        }
    }

    static boolean removeListings(L1PcInstance pc) {
        if (!pc.tryBeginItemTransfer()) return false;
        try {
            DynamicShopStorage.deleteProcess(pc);
            pc.setPrivateShop(false);
            return true;
        } catch (RejectedExecutionException rejected) {
            SC_SystemMessage.send(pc, "잠시 후 개인상점을 다시 닫아 주세요.");
            return false;
        } finally {
            pc.endItemTransfer();
        }
    }

    static boolean containsListing(ArrayList<DynamicShopItem> list, int objectId) {
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

    public static boolean isPetItem(L1PcInstance pc, int itemObjectId) {
        final var companion = pc.get_companion();
        return companion != null && companion.get_control_object_id() == itemObjectId;
    }

    public static boolean isDollItem(L1PcInstance pc, int itemObjectId) {

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