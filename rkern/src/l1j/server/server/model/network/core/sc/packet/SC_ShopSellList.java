package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;

import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1TaxCalculator;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ShopItem;

public final class SC_ShopSellList {
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_ShopSellList() {}

    @SuppressWarnings("deprecation")
    public static boolean send(int objId, L1PcInstance pc) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final L1Object obj = L1World.getInstance().findObject(objId);
        if (!(obj instanceof L1NpcInstance)) return false;
        final int npcId = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
        final L1Shop shop = ShopTable.getInstance().get(npcId);
        if (shop == null) return false;
        final List<L1ShopItem> shopItems;
        try {
            shopItems = shop.getSellingItems();
        } catch (Exception e) {
            System.out.println("상점(엔피씨)클릭시 : NPCID(물품없음/이상) : " + npcId);
            return false;
        }
        if (shopItems == null) return false;
        final int max = shopItems.size();
        final int[] values = new int[max << 2];
        final String[] names = new String[max];
        final byte[][] statuses = new byte[max][];
        final L1ItemInstance dummy = new L1ItemInstance();
        final L1TaxCalculator calc = new L1TaxCalculator(npcId);
        final long currentTime = System.currentTimeMillis();
        final Date date = new Date(0);
        date.setTime(pc.getFishingShopBuyTime_1());
        final int itemDay = date.getDate();
        date.setTime(currentTime);
        final int currentDay = date.getDate();
        final boolean hideTimed = itemDay == currentDay;
        long capacity = 11;
        int count = 0;
        for (int i = 0; i < max; ++i) {
            final L1ShopItem shopItem = shopItems.get(i);
            if (shopItem == null || (hideTimed && shopItem.isTimeLimit()) || shopItem.getItem() == null)
                continue;
            final L1Item item = shopItem.getItem();
            final int basePrice = shopItem.getPrice();
            final int price = (npcId == 70035 || npcId == 70041 || npcId == 70042)
                    ? basePrice : calc.layTax((int) (basePrice * Config.RATE_SHOP_SELLING_PRICE));
            int gfxId;
            try { gfxId = item.getGfxId(); } catch (Exception ignored) { gfxId = 0; }
            final String name;
            if (shopItem.getPackCount() > 1) {
                name = item.getNameId() + " (" + shopItem.getPackCount() + ")";
            } else if (shopItem.getEnchant() > 0) {
                name = "+" + shopItem.getEnchant() + " " + item.getName();
            } else if (item.getMaxUseTime() > 0) {
                name = item.getName() + " [" + item.getMaxUseTime() + "]";
            } else if (item.getItemId() >= 140074 && item.getItemId() <= 140100) {
                name = "축복 받은 " + item.getName();
            } else if (item.getItemId() >= 240074 && item.getItemId() <= 240087) {
                name = "저주 받은 " + item.getName();
            } else {
                name = item.getName();
            }
            final L1Item template = ItemTable.getInstance().getTemplate(item.getItemId());
            byte[] status = null;
            if (template != null) {
                dummy.setItem(template);
                status = dummy.getStatusBytes();
            }
            if (status == null) status = new byte[0];
            if (status.length > 255 || count >= 65535) return false;
            capacity += 16L + name.getBytes(MS949).length + status.length;
            if (capacity > Integer.MAX_VALUE) return false;
            final int j = count << 2;
            values[j] = i;
            values[j + 1] = gfxId;
            values[j + 2] = price;
            values[j + 3] = Math.max(0, item.getUseType());
            names[count] = name;
            statuses[count++] = status;
        }
        int adenDesc = 7;
        if (npcId >= 66110 && npcId <= 66120) adenDesc = 0x3DE4;

        final CIOFrame frame = connection.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_BUY_LIST);
            buffer.writeD(objId);
            buffer.writeH(count);
            for (int i = 0; i < count; ++i) {
                final int j = i << 2;
                buffer.writeD(values[j]);
                buffer.writeH(values[j + 1]);
                buffer.writeD(values[j + 2]);
                buffer.writeS(names[i]);
                buffer.writeD(values[j + 3]);
                buffer.writeC(statuses[i].length);
                buffer.writeByte(statuses[i]);
            }
            buffer.writeH(adenDesc);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
