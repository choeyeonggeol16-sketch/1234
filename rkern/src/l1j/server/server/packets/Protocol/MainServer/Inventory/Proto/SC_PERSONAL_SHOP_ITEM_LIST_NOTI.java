package l1j.server.server.packets.Protocol.MainServer.Inventory.Proto;

import gnu.trove.list.array.TIntArrayList;
import l1j.server.server.model.shop.NpcShopView;
import java.util.ArrayList;
import java.util.List;

import l1j.server.server.datatables.NoTradable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.item.NpcReissuedItemPolicy;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.PlayerShopTransactions;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.shop.CBuySell;
import l1j.server.server.model.shop.PlayerShopView;
import l1j.server.server.model.shop.dynamic.DynamicShopItem;
import l1j.server.server.packets.Protocol.MainServer.Proto.Proto;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoMsg;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoPacket;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ShopItem;

/**
 * 개인상점/상점 아이템 목록 통지 (0x0407)
 */
public final class SC_PERSONAL_SHOP_ITEM_LIST_NOTI extends ProtoPacket {

    // Frame length, opcode/message ID, trailer, type, largest positive seller
    // ID, and row count through 255. Encryption does not grow this frame.
    private static final int PLAYER_SHOP_WIRE_OVERHEAD = CIOBufferPool.HEADER_BYTES + 3 + 2 + 2 + 6 + 3;
    // Retain the established private-shop row cutoff so compact quotes do not change.
    private static final int PLAYER_SHOP_ROW_BYTES = CIOBufferPool.MAX_WIRE_BYTES - PLAYER_SHOP_WIRE_OVERHEAD - 12;

    private ePersonalShopType type;       // 1 enum
    private Integer sellerId;             // 2 int32
    private Integer size;                 // 3 int32
    private final List<TradeItemInfo> items = new ArrayList<TradeItemInfo>(128); // 4

    private SC_PERSONAL_SHOP_ITEM_LIST_NOTI() {
    }

    public static SC_PERSONAL_SHOP_ITEM_LIST_NOTI newInstance() {
        return new SC_PERSONAL_SHOP_ITEM_LIST_NOTI();
    }

    public SC_PERSONAL_SHOP_ITEM_LIST_NOTI set_type(ePersonalShopType v) {
        type = v;
        return this;
    }

    public SC_PERSONAL_SHOP_ITEM_LIST_NOTI set_seller_id(int v) {
        sellerId = v;
        return this;
    }

    public SC_PERSONAL_SHOP_ITEM_LIST_NOTI set_size(int v) {
        size = v;
        return this;
    }

    public SC_PERSONAL_SHOP_ITEM_LIST_NOTI add_item_list(TradeItemInfo v) {
        if (v != null) {
            items.add(v);
        }
        return this;
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_PERSONAL_SHOP_ITEM_LIST_NOTI;
    }

    @Override
    protected void body(ProtoWriter w) {
        if (type != null) {
            w.enumV(1, type.toInt());
        }

        if (sellerId != null) {
            w.int32(2, sellerId);
        }

        final int actualSize = items.size();
        size = actualSize;
        w.int32(3, actualSize);

        for (int i = 0; i < actualSize; i++) {
            final TradeItemInfo ti = items.get(i);
            if (ti == null) {
                continue;
            }

            w.msg(4);
            ti.body(w);
            w.end();
        }
    }

    /** Preserve the existing compact wire indices and remember which rows they represent. */
    public static void do_send(L1PcInstance pc, int objectId, ePersonalShopType type) {
        if (pc == null || type == null) return;
        final CUserSocket client = pc.getNetConnection();
        if (client == null || client.getActiveChar() != pc || !client.ownsReadyGameSession()) return;
        final int viewerStamp = pc.getSessionStamp();
        if (!(CWorld.getInstance().findObject(objectId) instanceof L1PcInstance shop)
                || !PlayerShopTransactions.canReach(pc, shop)) return;
        final boolean purchase = type == ePersonalShopType.TRADE_SELL;
        final ArrayList<DynamicShopItem> listings = purchase ? shop.getPurchasings() : shop.getSellings();
        try (ProtoWriter rows = ProtoWriter.borrow()) {
            final PlayerShopView view;
            if (listings == null) {
                view = new PlayerShopView(pc, shop, purchase, 1);
            } else {
                synchronized (listings) {
                    final int capacity = Math.min(PlayerShopView.MAX_ROWS, listings.size());
                    view = new PlayerShopView(pc, shop, purchase, capacity);
                    final long now = System.currentTimeMillis();
                    if (purchase && capacity != 0) {
                        final int[] classIds = new int[listings.size()];
                        final boolean shopGm = shop.isGm();
                        for (int i = 0; i < classIds.length; i++) {
                            final DynamicShopItem listing = listings.get(i);
                            if (listing != null && !NoTradable.blocksPlayerTransfer(shopGm, listing.itemId))
                                classIds[i] = listing.itemId;
                        }
                        // Customer inventory order, then merchant listing order for each object.
                        outer: for (L1ItemInstance item : pc.getInventory().GetUnequippedItemListByClassIDs(classIds)) {
                            for (int i = 0; i < classIds.length; i++) {
                                if (classIds[i] == item.getItemId() && !addPlayerShopRow(rows, view, pc,
                                        listings.get(i), item, true, now)) break outer;
                            }
                        }
                    } else {
                        for (DynamicShopItem listing : listings) {
                            if (listing == null) continue;
                            L1ItemInstance item = shop.getInventory().GetByServerID(listing.objId);
                            if (!addPlayerShopRow(rows, view, shop, listing, item, false, now)) break;
                        }
                    }
                }
            }
            // Keep session SEND admission outside the listing monitor, as in receipt replay.
            synchronized (client) {
                if (pc.getSessionStamp() != viewerStamp || !view.currentFor(client, pc)
                        || !view.matches(shop, purchase) || !PlayerShopTransactions.canReach(pc, shop)
                        || listings != (purchase ? shop.getPurchasings() : shop.getSellings())) return;
                if (sendRows(client, type, objectId, view.size(), rows)) {
                    if (view.size() != 0) client.publishPlayerShopView(pc, view);
                    else client.clearPlayerShopView(client.getPlayerShopView());
                }
            }
        }
    }

    private static boolean addPlayerShopRow(ProtoWriter rows, PlayerShopView view, L1PcInstance seller,
            DynamicShopItem listing, L1ItemInstance item, boolean purchase, long now) {
        if (listing == null || item == null || listing.isPurchase != purchase || listing.price <= 0) return true;
        final int count = Math.min(item.getCount(), listing.count);
        if (!PlayerShopTransactions.canTransfer(seller, item, listing, count, now)) return true;
        final int before = rows.length();
        writeRow(rows, view.size(), count, listing.price, item, purchase);
        if (rows.length() > PLAYER_SHOP_ROW_BYTES) {
            rows.rewindBody(before);
            return false;
        }
        view.add(listing.objId, purchase ? item.getId() : 0, listing.price, count);
        return rows.length() < PLAYER_SHOP_ROW_BYTES && view.size() < PlayerShopView.MAX_ROWS;
    }

    private static void writeRow(ProtoWriter rows, int index, int count, int price, L1ItemInstance item,
            boolean includeBuyCount) {
        rows.msg(4).int32(1, index).int32(2, count).int32(3, price).msg(4);
        ItemInfo.writeItem(rows, item);
        rows.end().int32(5, 0);
        if (includeBuyCount) rows.int32(6, count);
        rows.end();
    }

    // ============================================================
    // NPC 상점 - 구매
    // ============================================================

    private static void do_send_buy_for_npc(
            L1PcInstance pc,
            L1NpcInstance npc,
            CBuySell shop) {

        if (pc == null || npc == null || shop == null) {
            return;
        }

        final CUserSocket client = pc.getNetConnection();
        if (client == null) return;
        final List<L1ShopItem> source = shop.getSellingItems();
        if (source == null || source.isEmpty()) {
            return;
        }

        // Holes retain their original catalog index. The compact display index is separate.
        final L1ShopItem[] offers = source.toArray(L1ShopItem[]::new);
        final boolean normal = CBuySell.is_normal_shop(npc);
        final int sellerId = npc.getId();
        final L1TaxCalculator taxes = new L1TaxCalculator(shop.getNpcId());
        try (ProtoWriter rows = ProtoWriter.borrow()) {
            int shown = 0;
            boolean oversized = false;
            for (int i = 0; i < offers.length; i++) {
                L1ShopItem offer = offers[i];
                offers[i] = null;
                if (!shop.canOffer(offer, pc)) continue;
                int before = rows.length();
                try {
                    L1Item template = offer.getItem();
                    int price = shop.checkoutPrice(offer, taxes);
                    if (price < 0) continue;
                    L1ItemInstance display = displayItem(template, offer.getEnchant(), i, true);
                    int count = normal ? (price > 100_000_000 ? 1 : 99) : offer.getCount();
                    writeRow(rows, shown, count, price, display, false);
                    oversized |= rows.length() > CIOBufferPool.MAX_WIRE_BYTES;
                    offers[i] = offer;
                    shown++;
                    if (oversized) rows.rewindBody(0);
                } catch (RuntimeException failure) {
                    rows.rewindBody(before);
                    System.err.println("[SHOP BUY BUILD ERROR] npcId=" + npc.getNpcId() + " index=" + i);
                    failure.printStackTrace();
                }
            }
            logLargeList("BUY", npc, sellerId, shown);
            if (oversized) return;
            final NpcShopView view = NpcShopView.purchase(client, pc, npc, sellerId, shop, offers);
            if (sendRows(client, ePersonalShopType.TRADE_BUY, sellerId, shown, rows))
                client.publishNpcShopView(pc, view);
        } catch (RuntimeException failure) {
            System.err.println("[SHOP BUY SEND ERROR] npcId=" + npc.getNpcId() + " objectId=" + sellerId);
            failure.printStackTrace();
        }
    }

    // ============================================================
    // NPC 상점 - 판매
    // ============================================================

    private static void do_send_sell_for_npc(
            L1PcInstance pc,
            L1NpcInstance npc,
            CBuySell shop) {

        if (pc == null || npc == null || shop == null) {
            return;
        }

        final List<L1ShopItem> source = shop.getBuyingItems();
        if (source == null || source.isEmpty()) {
            return;
        }

        final CUserInventory inventory = pc.getInventory();
        if (inventory.getSize() == 0) return;
        final CUserSocket client = pc.getNetConnection();
        if (client == null) return;
        final int sellerId = npc.getId();
        final L1ShopItem[] offers = source.toArray(L1ShopItem[]::new);
        final TIntArrayList saleIds = new TIntArrayList();
        try (ProtoWriter rows = ProtoWriter.borrow()) {
            boolean oversized = false;
            for (int i = 0; i < offers.length; i++) {
                L1ShopItem offer = offers[i];
                if (offer == null || NpcReissuedItemPolicy.isProtected(offer.getItemId())) continue;
                L1Item template = offer.getItem();
                if (template == null) {
                    System.err.println("[SHOP INVALID BUY ITEM] npcId=" + npc.getNpcId() + " index=" + i
                            + " itemId=" + offer.getItemId());
                    continue;
                }
                int price = shop.getAssessedPrice(offer);
                if (price < 0) continue;
                for (L1ItemInstance item : inventory.GetUnequippedItemListByClassID(offer.getItemId())) {
                    if (!shop.canBuyBack(item) || offer.getEnchant() != item.getEnchantLevel())
                        continue;
                    int before = rows.length();
                    try {
                        L1ItemInstance display = displayItem(template, offer.getEnchant(), i, false);
                        writeRow(rows, saleIds.size(), offer.getCount(), price, display, true);
                        oversized |= rows.length() > CIOBufferPool.MAX_WIRE_BYTES;
                        saleIds.add(item.getId());
                        if (oversized) rows.rewindBody(0);
                    } catch (RuntimeException failure) {
                        rows.rewindBody(before);
                        System.err.println("[SHOP SELL BUILD ERROR] npcId=" + npc.getNpcId() + " index=" + i
                                + " itemObjectId=" + item.getId());
                        failure.printStackTrace();
                    }
                }
            }
            logLargeList("SELL", npc, sellerId, saleIds.size());
            if (oversized) return;
            final NpcShopView view = NpcShopView.sale(client, pc, npc, shop, saleIds.toArray());
            if (sendRows(client, ePersonalShopType.TRADE_SELL, sellerId, saleIds.size(), rows))
                client.publishNpcShopView(pc, view);
        } catch (RuntimeException failure) {
            System.err.println("[SHOP SELL SEND ERROR] npcId=" + npc.getNpcId() + " objectId=" + sellerId);
            failure.printStackTrace();
        }
    }

    private static void logLargeList(String direction, L1NpcInstance npc, int objectId, int count) {
        if (count >= 200) {
            System.out.println("[SHOP LARGE LIST] type=" + direction + " npcId=" + npc.getNpcId()
                    + " objectId=" + objectId + " items=" + count);
        }
    }

    private static L1ItemInstance displayItem(L1Item template, int enchant, int index, boolean useTemplateBless) {
        L1ItemInstance item = new L1ItemInstance();
        item.setItem(template);
        item.setEnchantLevel(enchant);
        if (useTemplateBless) item.setBless(template.getBless());
        item.setId(index);
        item.setIdentified(true);
        return item;
    }

    private static boolean sendRows(CUserSocket client, ePersonalShopType type, int sellerId, int count,
            ProtoWriter rows) {
        int sellerBytes = sellerId < 0 ? 10 : ProtoWriter.varintLenOf(sellerId);
        long capacity = CIOBufferPool.HEADER_BYTES + 3L + 2 + 2 + 1 + sellerBytes
                + 1 + ProtoWriter.varintLenOf(count) + rows.length();
        if (capacity > CIOBufferPool.MAX_WIRE_BYTES) return false;
        ProtoWriter frame = Proto.begin(client, ProtoMsg.SC_PERSONAL_SHOP_ITEM_LIST_NOTI, (int) capacity);
        if (frame == null) return false;
        try {
            frame.enumV(1, type.toInt()).int32(2, sellerId).int32(3, count).appendBody(rows);
            return Proto.commit(frame);
        } finally {
            Proto.abort(frame);
        }
    }

    public static void do_send_for_npc(
            L1PcInstance pc,
            int objectId,
            ePersonalShopType type) {

        if (pc == null || type == null) {
            return;
        }

        final L1Object obj = CWorld.getInstance().findObject(objectId);
        if (!(obj instanceof L1NpcInstance)) {
            return;
        }

        final L1NpcInstance npc = (L1NpcInstance) obj;
        final CBuySell shop = CBuySell.find_shop(npc);

        if (shop == null) {
            return;
        }

        if (type == ePersonalShopType.TRADE_BUY) {
            do_send_buy_for_npc(pc, npc, shop);
        } else if (type == ePersonalShopType.TRADE_SELL) {
            do_send_sell_for_npc(pc, npc, shop);
        }
    }

    public enum ePersonalShopType {
        TRADE_BUY(0),
        TRADE_SELL(1);

        private final int value;

        ePersonalShopType(int val) {
            value = val;
        }

        public int toInt() {
            return value;
        }

        public static ePersonalShopType fromInt(int i) {
            switch (i) {
            case 0:
                return TRADE_BUY;
            case 1:
                return TRADE_SELL;
            default:
                return null;
            }
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, long presentMask, ePersonalShopType type, int sellerId, int size, List<TradeItemInfo> items) {
        if (type != null) {
                w.enumV(1, type.toInt());
            }
            if ((presentMask & (1L << 0)) != 0) {
                w.int32(2, sellerId);
            }
            final int actualSize = items.size();
            size = actualSize;
            w.int32(3, actualSize);
            for (int i = 0; i < actualSize; i++) {
                final TradeItemInfo ti = items.get(i);
                if (ti == null) {
                    continue;
                }
                w.msg(4);
                ti.body(w);
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, long presentMask, ePersonalShopType type, int sellerId, int size, List<TradeItemInfo> items) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), presentMask, type, sellerId, size, items);
    }

    public static boolean sendFields(CUserSocket recipient, long presentMask, ePersonalShopType type, int sellerId, int size, List<TradeItemInfo> items) {
        return sendFields(recipient, presentMask, type, sellerId, size, items, CIOBufferPool.MAX_WIRE_BYTES);
    }

    private static boolean sendFields(CUserSocket recipient, long presentMask, ePersonalShopType type, int sellerId,
            int size, List<TradeItemInfo> items, int wireCapacity) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_PERSONAL_SHOP_ITEM_LIST_NOTI, wireCapacity);
        if (writer == null) return false;
        try {
            writeFields(writer, presentMask, type, sellerId, size, items);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(long presentMask, ePersonalShopType type, int sellerId, int size, List<TradeItemInfo> items) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, presentMask, type, sellerId, size, items);
        return writer.finishWithTrailer(ProtoMsg.SC_PERSONAL_SHOP_ITEM_LIST_NOTI);
    }

    private static boolean sendCtor(CUserSocket protoRecipient) {
        long proto55_present = 0L;
        ePersonalShopType proto55_type = null;
        int proto55_sellerId = 0;
        int proto55_size = 0;
        List<TradeItemInfo> proto55_items = new ArrayList<TradeItemInfo>(128);


        return sendFields(protoRecipient, proto55_present, proto55_type, proto55_sellerId, proto55_size, proto55_items);
    }

    public static boolean sendNewInstance(CUserSocket protoRecipient) {
        return sendCtor(protoRecipient);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }
    public static boolean sendConstructed(CUserSocket protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
