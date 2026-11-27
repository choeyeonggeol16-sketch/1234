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
import l1j.server.server.model.shop.L1Shop;
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
    // Direct ProtoWriter reserves five bytes for each variable length before
    // compacting it. A row, ItemInfo and its companion are at most three levels.
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
        final PlayerShopView view;
        final List<TradeItemInfo> items;
        final int wireCapacity;

        if (listings == null) {
            view = new PlayerShopView(pc, shop, purchase, 1);
            items = List.of();
            wireCapacity = PLAYER_SHOP_WIRE_OVERHEAD;
        } else {
            synchronized (listings) {
                final int capacity = Math.min(PlayerShopView.MAX_ROWS, listings.size());
                view = new PlayerShopView(pc, shop, purchase, capacity);
                items = new ArrayList<>(capacity);
                final long now = System.currentTimeMillis();
                int remainingBytes = PLAYER_SHOP_ROW_BYTES;
                if (purchase && capacity != 0) {
                    final int[] classIds = new int[listings.size()];
                    final boolean shopGm = shop.isGm();
                    for (int i = 0; i < classIds.length; i++) {
                        final DynamicShopItem listing = listings.get(i);
                        if (listing != null && !NoTradable.blocksPlayerTransfer(shopGm, listing.itemId))
                            classIds[i] = listing.itemId;
                    }
                    // Keep inventory order, then the merchant's listing order, even when
                    // several of the customer's items match one buy order.
                    for (L1ItemInstance item : pc.getInventory().GetUnequippedItemListByClassIDs(classIds)) {
                        for (int i = 0; i < classIds.length; i++) {
                            if (classIds[i] != item.getItemId()) continue;
                            remainingBytes = addPlayerShopRow(items, view, pc, listings.get(i), item,
                                    true, now, remainingBytes);
                            if (remainingBytes == 0 || view.size() == PlayerShopView.MAX_ROWS) break;
                        }
                        if (remainingBytes == 0 || view.size() == PlayerShopView.MAX_ROWS) break;
                    }
                } else {
                    for (DynamicShopItem listing : listings) {
                        if (listing == null) continue;
                        final L1ItemInstance item = shop.getInventory().GetByServerID(listing.objId);
                        remainingBytes = addPlayerShopRow(items, view, shop, listing, item,
                                false, now, remainingBytes);
                        if (remainingBytes == 0 || view.size() == PlayerShopView.MAX_ROWS) break;
                    }
                }
                wireCapacity = items.isEmpty() ? PLAYER_SHOP_WIRE_OVERHEAD
                        : CIOBufferPool.MAX_WIRE_BYTES - remainingBytes;
            }
        }
        // Receipt replay takes session ownership before its listing monitor.
        // Publish and send outside the listing monitor in the captured viewer session.
        synchronized (client) {
            if (pc.getSessionStamp() != viewerStamp || !view.currentFor(client, pc)
                    || !view.matches(shop, purchase) || !PlayerShopTransactions.canReach(pc, shop)
                    || listings != (purchase ? shop.getPurchasings() : shop.getSellings())) return;
            if (sendFields(client, 3L, type, objectId, view.size(), items, wireCapacity)) {
                if (view.size() != 0) client.publishPlayerShopView(pc, view);
                else client.clearPlayerShopView(client.getPlayerShopView());
            }
        }
    }

    private static int addPlayerShopRow(List<TradeItemInfo> items, PlayerShopView view, L1PcInstance seller,
            DynamicShopItem listing, L1ItemInstance item, boolean purchase, long now, int remainingBytes) {
        if (listing == null || item == null || listing.isPurchase != purchase || listing.price <= 0)
            return remainingBytes;
        final int count = Math.min(item.getCount(), listing.count);
        if (!PlayerShopTransactions.canTransfer(seller, item, listing, count, now)) return remainingBytes;
        final ItemInfo info = ItemInfo.newInstance(item);
        if (info == null) return remainingBytes;
        TradeItemInfo row = TradeItemInfo.newInstance().set_index(view.size()).set_price(listing.price)
                .set_count(count).set_trade_info(0).set_item_info(info);
        if (purchase) row.set_buy_count(count);
        final int wireBytes = playerShopRowWireBytes(row);
        if (wireBytes > remainingBytes) return 0;
        items.add(row);
        view.add(listing.objId, purchase ? item.getId() : 0, listing.price, count);
        return remainingBytes - wireBytes;
    }

    private static int playerShopRowWireBytes(TradeItemInfo row) {
        final ProtoWriter sizer = ProtoWriter.tl();
        sizer.msg(4);
        row.body(sizer);
        return sizer.end().length();
    }

    // ============================================================
    // NPC 상점 - 구매
    // ============================================================

    private static void do_send_buy_for_npc(
            L1PcInstance pc,
            L1NpcInstance npc,
            L1Shop shop) {

        if (pc == null || npc == null || shop == null) {
            return;
        }

        final CUserSocket client = pc.getNetConnection();
        if (client == null) return;
        final List<L1ShopItem> source = shop.getSellingItems();
        if (source == null || source.isEmpty()) {
            return;
        }

        final List<L1ShopItem> shopItems = new ArrayList<L1ShopItem>(source);
        final L1ShopItem[] offers = new L1ShopItem[shopItems.size()];
        final boolean isNormal = L1Shop.is_normal_shop(npc);

        long notiWire_present = 0L;
        ePersonalShopType notiWire_type = null;
        int notiWire_sellerId = 0;
        int notiWire_size = 0;
        List<TradeItemInfo> notiWire_items = new ArrayList<TradeItemInfo>(128);


        { notiWire_type = ePersonalShopType.TRADE_BUY; }
        { { notiWire_sellerId = (npc.getId()); notiWire_present |= (1L << 0); } }

        int realSize = 0;

        for (int i = 0; i < shopItems.size(); i++) {
            final L1ShopItem shopItem = shopItems.get(i);
            if (shopItem == null) {
                continue;
            }

            try {
                final L1Item item = shopItem.getItem();
                if (item == null) {
                    System.err.println(
                            "[SHOP INVALID ITEM]"
                            + " npcId=" + npc.getNpcTemplate().get_npcId()
                            + " objectId=" + npc.getId()
                            + " index=" + i
                            + " itemId=" + shopItem.getItemId());
                    continue;
                }

                final int price = shopItem.getPrice();
                if (price < 0) {
                    System.err.println(
                            "[SHOP INVALID PRICE]"
                            + " npcId=" + npc.getNpcTemplate().get_npcId()
                            + " itemId=" + shopItem.getItemId()
                            + " price=" + price);
                    continue;
                }

                final L1ItemInstance dummy = new L1ItemInstance();
                dummy.setItem(item);
                dummy.setEnchantLevel(shopItem.getEnchant());
                dummy.setBless(item.getBless());
                dummy.setId(i);
                dummy.setIdentified(true);

                final TradeItemInfo tInfo = TradeItemInfo.newInstance();
                tInfo.set_index(realSize);
                tInfo.set_price(price);

                if (isNormal) {
                    tInfo.set_count(price > 100000000 ? 1 : 99);
                } else {
                    tInfo.set_count(shopItem.getCount());
                }

                tInfo.set_trade_info(0);

                final ItemInfo iInfo = ItemInfo.newInstance(dummy);
                if (iInfo == null) {
                    System.err.println(
                            "[SHOP ITEMINFO NULL]"
                            + " npcId=" + npc.getNpcTemplate().get_npcId()
                            + " itemId=" + shopItem.getItemId());
                    continue;
                }

                tInfo.set_item_info(iInfo);
                {
                TradeItemInfo proto921_arg0 = tInfo;

                    if (proto921_arg0 != null) {
                        notiWire_items.add(proto921_arg0);
                    }
                }
                offers[i] = shopItem;
                realSize++;

            } catch (Exception e) {
                System.err.println(
                        "[SHOP BUY BUILD ERROR]"
                        + " npcId=" + npc.getNpcTemplate().get_npcId()
                        + " objectId=" + npc.getId()
                        + " index=" + i
                        + " itemId=" + shopItem.getItemId());
                e.printStackTrace();
            }
        }

        { { notiWire_size = realSize; notiWire_present |= (1L << 1); } }

        if (realSize >= 200) {
            System.out.println(
                    "[SHOP LARGE LIST]"
                    + " type=BUY"
                    + " npcId=" + npc.getNpcTemplate().get_npcId()
                    + " objectId=" + npc.getId()
                    + " items=" + realSize);
        }

        try {
            final NpcShopView view = NpcShopView.purchase(client, pc, npc, npc.getId(), shop, offers);
            if (sendFields(client, notiWire_present, notiWire_type, notiWire_sellerId, notiWire_size, notiWire_items))
                client.publishNpcShopView(pc, view);
        } catch (Exception e) {
            System.err.println(
                    "[SHOP BUY SEND ERROR]"
                    + " npcId=" + npc.getNpcTemplate().get_npcId()
                    + " objectId=" + npc.getId()
                    + " items=" + realSize);
            e.printStackTrace();
        }
    }

    // ============================================================
    // NPC 상점 - 판매
    // ============================================================

    private static void do_send_sell_for_npc(
            L1PcInstance pc,
            L1NpcInstance npc,
            L1Shop shop) {

        if (pc == null || npc == null || shop == null) {
            return;
        }

        final List<L1ShopItem> source = shop.getBuyingItems();
        if (source == null || source.isEmpty()) {
            return;
        }

        final List<L1ShopItem> shopItems = new ArrayList<L1ShopItem>(source);

        final CUserInventory inventory = pc.getInventory();
        if (inventory.getSize() == 0) {
            return;
        }

        long notiWire_present = 0L;
        ePersonalShopType notiWire_type = null;
        int notiWire_sellerId = 0;
        int notiWire_size = 0;
        List<TradeItemInfo> notiWire_items = new ArrayList<TradeItemInfo>(128);


        { notiWire_type = ePersonalShopType.TRADE_SELL; }
        { { notiWire_sellerId = (npc.getId()); notiWire_present |= (1L << 0); } }

        final CUserSocket client = pc.getNetConnection();
        if (client == null) return;
        final TIntArrayList saleIds = new TIntArrayList();
        int realSize = 0;

        for (int i = 0; i < shopItems.size(); i++) {
            final L1ShopItem shopItem = shopItems.get(i);
            if (shopItem == null || NpcReissuedItemPolicy.isProtected(shopItem.getItemId())) {
                continue;
            }

            final L1Item template = shopItem.getItem();
            if (template == null) {
                System.err.println(
                        "[SHOP INVALID BUY ITEM]"
                        + " npcId=" + npc.getNpcTemplate().get_npcId()
                        + " objectId=" + npc.getId()
                        + " index=" + i
                        + " itemId=" + shopItem.getItemId());
                continue;
            }

            final int price = shop.getAssessedPrice(shopItem);
            if (price < 0) {
                continue;
            }

            for (L1ItemInstance pcItem : inventory.GetUnequippedItemListByClassID(shopItem.getItemId())) {
                if (pcItem.isEquipped() || pcItem.getCount() <= 0) {
                    continue;
                }

                if (shopItem.getEnchant() != pcItem.getEnchantLevel()) {
                    continue;
                }

                try {
                    final L1ItemInstance dummy = new L1ItemInstance();
                    dummy.setItem(template);
                    dummy.setEnchantLevel(shopItem.getEnchant());
                    dummy.setId(i);
                    dummy.setIdentified(true);

                    final TradeItemInfo tInfo = TradeItemInfo.newInstance();
                    tInfo.set_index(realSize);
                    tInfo.set_count(shopItem.getCount());
                    tInfo.set_buy_count(shopItem.getCount());
                    tInfo.set_price(price);
                    tInfo.set_trade_info(0);

                    final ItemInfo iInfo = ItemInfo.newInstance(dummy);
                    if (iInfo == null) {
                        continue;
                    }

                    tInfo.set_item_info(iInfo);
                    {
                    TradeItemInfo proto925_arg0 = tInfo;

                        if (proto925_arg0 != null) {
                            notiWire_items.add(proto925_arg0);
                        }
                    }
                    saleIds.add(pcItem.getId());
                    realSize++;

                } catch (Exception e) {
                    System.err.println(
                            "[SHOP SELL BUILD ERROR]"
                            + " npcId=" + npc.getNpcTemplate().get_npcId()
                            + " objectId=" + npc.getId()
                            + " shopIndex=" + i
                            + " itemObjectId=" + pcItem.getId()
                            + " itemId=" + shopItem.getItemId());
                    e.printStackTrace();
                }
            }
        }

        { { notiWire_size = realSize; notiWire_present |= (1L << 1); } }

        if (realSize >= 200) {
            System.out.println(
                    "[SHOP LARGE LIST]"
                    + " type=SELL"
                    + " npcId=" + npc.getNpcTemplate().get_npcId()
                    + " objectId=" + npc.getId()
                    + " items=" + realSize);
        }

        try {
            final NpcShopView view = NpcShopView.sale(client, pc, npc, shop, saleIds.toArray());
            if (sendFields(client, notiWire_present, notiWire_type, notiWire_sellerId, notiWire_size, notiWire_items))
                client.publishNpcShopView(pc, view);
        } catch (Exception e) {
            System.err.println(
                    "[SHOP SELL SEND ERROR]"
                    + " npcId=" + npc.getNpcTemplate().get_npcId()
                    + " objectId=" + npc.getId()
                    + " items=" + realSize);
            e.printStackTrace();
        }
    }

    // ============================================================
    // NPC 상점 공통 진입점
    // ============================================================

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
        final L1Shop shop = L1Shop.find_shop(npc);

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
