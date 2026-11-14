package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import java.util.ArrayList;
import java.util.List;

import l1j.server.MJDShopSystem.MJDShopItem;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.Inventory.ItemInfo;
import l1j.server.server.packets.Protocol.Packet.Inventory.TradeItemInfo;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ShopItem;

/**
 * 개인상점/상점 아이템 목록 통지 (0x0407)
 */
public final class SC_PersonalShopItemListNoti extends ProtoPacket {

    private ePersonalShopType type;       // 1 enum
    private Integer sellerId;             // 2 int32
    private Integer size;                 // 3 int32
    private final List<TradeItemInfo> items = new ArrayList<TradeItemInfo>(128); // 4

    private SC_PersonalShopItemListNoti() {
    }

    public static SC_PersonalShopItemListNoti newInstance() {
        return new SC_PersonalShopItemListNoti();
    }

    public SC_PersonalShopItemListNoti set_type(ePersonalShopType v) {
        type = v;
        return this;
    }

    public SC_PersonalShopItemListNoti set_seller_id(int v) {
        sellerId = v;
        return this;
    }

    public SC_PersonalShopItemListNoti set_size(int v) {
        size = v;
        return this;
    }

    public SC_PersonalShopItemListNoti add_item_list(TradeItemInfo v) {
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

    // ============================================================
    // 개인상점 - 구매
    // ============================================================

    private static void do_send_buy(
            L1PcInstance pc,
            L1PcInstance shopPc) {

        if (pc == null || shopPc == null) {
            return;
        }

        final ArrayList<MJDShopItem> source = shopPc.getSellings();
        if (source == null || source.isEmpty()) {
            pc.setPartnersPrivateShopItemCount(0);
            return;
        }

        final List<MJDShopItem> list = new ArrayList<MJDShopItem>(source);

        long notiWire_present = 0L;
        ePersonalShopType notiWire_type = null;
        int notiWire_sellerId = 0;
        int notiWire_size = 0;
        List<TradeItemInfo> notiWire_items = new ArrayList<TradeItemInfo>(128);


        { notiWire_type = ePersonalShopType.TRADE_BUY; }
        { { notiWire_sellerId = (shopPc.getId()); notiWire_present |= (1L << 0); } }

        int realSize = 0;

        for (int i = 0; i < list.size(); i++) {
            final MJDShopItem ditem = list.get(i);
            if (ditem == null) {
                continue;
            }

            try {
                final L1ItemInstance item = shopPc.getInventory().getItem(ditem.objId);
                if (item == null || item.getCount() <= 0) {
                    continue;
                }

                if (ditem.price < 0 || ditem.count <= 0) {
                    continue;
                }

                final TradeItemInfo tInfo = TradeItemInfo.newInstance();
                tInfo.set_index(realSize);
                tInfo.set_price(ditem.price);
                tInfo.set_count(ditem.count);
                tInfo.set_trade_info(0);

                final ItemInfo iInfo = ItemInfo.newInstance(item);
                if (iInfo == null) {
                    continue;
                }

                tInfo.set_item_info(iInfo);
                {
                TradeItemInfo proto913_arg0 = tInfo;

                    if (proto913_arg0 != null) {
                        notiWire_items.add(proto913_arg0);
                    }
                }
                realSize++;

            } catch (Exception e) {
                System.err.println(
                        "[PERSONAL SHOP BUY BUILD ERROR]"
                        + " seller=" + shopPc.getName()
                        + " sellerId=" + shopPc.getId()
                        + " index=" + i);
                e.printStackTrace();
            }
        }

        pc.setPartnersPrivateShopItemCount(realSize);
        { { notiWire_size = realSize; notiWire_present |= (1L << 1); } }
        SC_PersonalShopItemListNoti.sendFields(pc, notiWire_present, notiWire_type, notiWire_sellerId, notiWire_size, notiWire_items);
    }

    // ============================================================
    // 개인상점 - 판매
    // ============================================================

    private static void do_send_sell(
            L1PcInstance pc,
            L1PcInstance shopPc) {

        if (pc == null || shopPc == null) {
            return;
        }

        final ArrayList<MJDShopItem> source = shopPc.getPurchasings();
        if (source == null || source.isEmpty()) {
            return;
        }

        final List<MJDShopItem> purchasingsItems = new ArrayList<MJDShopItem>(source);

        final List<L1ItemInstance> inventorySource = pc.getInventory().getItems();
        if (inventorySource == null || inventorySource.isEmpty()) {
            return;
        }

        final List<L1ItemInstance> inventoryItems = new ArrayList<L1ItemInstance>(inventorySource);

        long notiWire_present = 0L;
        ePersonalShopType notiWire_type = null;
        int notiWire_sellerId = 0;
        int notiWire_size = 0;
        List<TradeItemInfo> notiWire_items = new ArrayList<TradeItemInfo>(128);


        { notiWire_type = ePersonalShopType.TRADE_SELL; }
        { { notiWire_sellerId = (shopPc.getId()); notiWire_present |= (1L << 0); } }

        final int purchasingsSize = purchasingsItems.size();
        int realSize = 0;

        for (int i = 0; i < inventoryItems.size(); i++) {
            final L1ItemInstance item = inventoryItems.get(i);

            if (item == null || item.isEquipped() || item.getCount() <= 0) {
                continue;
            }

            if (realSize >= purchasingsSize) {
                break;
            }

            for (int j = 0; j < purchasingsSize; j++) {
                final MJDShopItem ditem = purchasingsItems.get(j);
                if (ditem == null) {
                    continue;
                }

                if (item.getItemId() != ditem.itemId) {
                    continue;
                }

                if (item.getEnchantLevel() != ditem.enchant
                        || item.getAttrEnchantLevel() != ditem.attr) {
                    continue;
                }

                if (ditem.price < 0 || ditem.count <= 0) {
                    continue;
                }

                final int count = Math.min(ditem.count, item.getCount());
                if (count <= 0) {
                    continue;
                }

                try {
                    final TradeItemInfo tInfo = TradeItemInfo.newInstance();
                    tInfo.set_index(realSize);
                    tInfo.set_price(ditem.price);
                    tInfo.set_count(count);
                    tInfo.set_buy_count(count);
                    tInfo.set_trade_info(0);

                    final ItemInfo iInfo = ItemInfo.newInstance(item);
                    if (iInfo == null) {
                        continue;
                    }

                    tInfo.set_item_info(iInfo);
                    {
                    TradeItemInfo proto917_arg0 = tInfo;

                        if (proto917_arg0 != null) {
                            notiWire_items.add(proto917_arg0);
                        }
                    }
                    realSize++;

                    if (realSize >= purchasingsSize) {
                        break;
                    }

                } catch (Exception e) {
                    System.err.println(
                            "[PERSONAL SHOP SELL BUILD ERROR]"
                            + " buyer=" + shopPc.getName()
                            + " buyerId=" + shopPc.getId()
                            + " inventoryIndex=" + i
                            + " purchaseIndex=" + j
                            + " itemId=" + item.getItemId());
                    e.printStackTrace();
                }
            }
        }

        { { notiWire_size = realSize; notiWire_present |= (1L << 1); } }
        SC_PersonalShopItemListNoti.sendFields(pc, notiWire_present, notiWire_type, notiWire_sellerId, notiWire_size, notiWire_items);
    }

    // ============================================================
    // 개인상점 공통 진입점
    // ============================================================

    public static void do_send(
            L1PcInstance pc,
            int objectId,
            ePersonalShopType type) {

        if (pc == null || type == null) {
            return;
        }

        final L1Object obj = L1World.getInstance().findObject(objectId);
        if (!(obj instanceof L1PcInstance)) {
            return;
        }

        final L1PcInstance shopPc = (L1PcInstance) obj;

        if (type == ePersonalShopType.TRADE_BUY) {
            do_send_buy(pc, shopPc);
        } else if (type == ePersonalShopType.TRADE_SELL) {
            do_send_sell(pc, shopPc);
        }
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

        final List<L1ShopItem> source = shop.getSellingItems();
        if (source == null || source.isEmpty()) {
            return;
        }

        final List<L1ShopItem> shopItems = new ArrayList<L1ShopItem>(source);
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
            SC_PersonalShopItemListNoti.sendFields(pc, notiWire_present, notiWire_type, notiWire_sellerId, notiWire_size, notiWire_items);
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

        final List<L1ItemInstance> inventorySource = pc.getInventory().getItems();
        if (inventorySource == null || inventorySource.isEmpty()) {
            return;
        }

        final List<L1ItemInstance> inventoryItems = new ArrayList<L1ItemInstance>(inventorySource);

        long notiWire_present = 0L;
        ePersonalShopType notiWire_type = null;
        int notiWire_sellerId = 0;
        int notiWire_size = 0;
        List<TradeItemInfo> notiWire_items = new ArrayList<TradeItemInfo>(128);


        { notiWire_type = ePersonalShopType.TRADE_SELL; }
        { { notiWire_sellerId = (npc.getId()); notiWire_present |= (1L << 0); } }

        int realSize = 0;

        for (int i = 0; i < shopItems.size(); i++) {
            final L1ShopItem shopItem = shopItems.get(i);
            if (shopItem == null) {
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

            final int price = shopItem.getPrice();
            if (price < 0) {
                continue;
            }

            for (int j = 0; j < inventoryItems.size(); j++) {
                final L1ItemInstance pcItem = inventoryItems.get(j);

                if (pcItem == null || pcItem.isEquipped() || pcItem.getCount() <= 0) {
                    continue;
                }

                if (shopItem.getItemId() != pcItem.getItemId()
                        || shopItem.getEnchant() != pcItem.getEnchantLevel()) {
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
                    realSize++;

                } catch (Exception e) {
                    System.err.println(
                            "[SHOP SELL BUILD ERROR]"
                            + " npcId=" + npc.getNpcTemplate().get_npcId()
                            + " objectId=" + npc.getId()
                            + " shopIndex=" + i
                            + " inventoryIndex=" + j
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
            SC_PersonalShopItemListNoti.sendFields(pc, notiWire_present, notiWire_type, notiWire_sellerId, notiWire_size, notiWire_items);
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

        final L1Object obj = L1World.getInstance().findObject(objectId);
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

    public static boolean sendFields(CIOClient recipient, long presentMask, ePersonalShopType type, int sellerId, int size, List<TradeItemInfo> items) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_PERSONAL_SHOP_ITEM_LIST_NOTI, Proto.capacityBound(65535L));
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

    private static boolean sendCtor(CIOClient protoRecipient) {
        long proto55_present = 0L;
        ePersonalShopType proto55_type = null;
        int proto55_sellerId = 0;
        int proto55_size = 0;
        List<TradeItemInfo> proto55_items = new ArrayList<TradeItemInfo>(128);


        return sendFields(protoRecipient, proto55_present, proto55_type, proto55_sellerId, proto55_size, proto55_items);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient) {
        return sendCtor(protoRecipient);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
