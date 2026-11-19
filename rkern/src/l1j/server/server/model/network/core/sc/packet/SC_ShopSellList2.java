package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;
import java.util.List;

import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ShopItem;

public final class SC_ShopSellList2 {
    private static final Charset MS949 = Charset.forName("MS949");
    private static final int DEFAULT_NPC_ID = 8;
    private static final int CHECK_PACKET_ID = 8626;

    private SC_ShopSellList2() {}

    public static boolean send(L1PcInstance gm) {
        if (gm == null) return false;
        final CIOClient client = gm.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;

        final L1Shop shop = ShopTable.getInstance().get(DEFAULT_NPC_ID);
        final List<L1ShopItem> list = shop == null ? null : shop.getSellingItems();
        final int limit = list == null ? 0 : list.size();
        final int[] integers = new int[limit * 4];
        final String[] names = new String[limit];
        final byte[][] statuses = new byte[limit][];
        final L1ItemInstance dummy = limit == 0 ? null : new L1ItemInstance();
        int count = 0;
        long wire = 11;
        for (int i = 0; i < limit; ++i) {
            final L1ShopItem shopItem = list.get(i);
            if (shopItem == null || shopItem.getItem() == null) continue;
            final L1Item item = shopItem.getItem();
            int gfxId;
            try { gfxId = item.getGfxId(); } catch (Exception ignored) { gfxId = 0; }
            final String itemName = shopItem.getPackCount() > 1
                    ? item.getName() + " (" + shopItem.getPackCount() + ")" : item.getName();
            final int useType = Math.max(0, item.getUseType());
            byte[] status = null;
            final L1Item template = ItemTable.getInstance().getTemplate(item.getItemId());
            if (template != null) {
                dummy.setItem(template);
                status = dummy.getStatusBytes();
            }
            if (status == null) status = new byte[0];
            if (status.length > 255) return false;
            final long next = wire + 16L + itemName.getBytes(MS949).length + status.length;
            if (next > Integer.MAX_VALUE || count >= 65535) return false;
            wire = next;
            final int j = count << 2;
            integers[j] = i;
            integers[j + 1] = gfxId;
            integers[j + 2] = shopItem.getPrice();
            integers[j + 3] = useType;
            names[count] = itemName;
            statuses[count++] = status;
        }
        final CIOFrame frame = connection.beginDirectFrame((int) wire);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_BUY_LIST);
            buffer.writeD(CHECK_PACKET_ID);
            buffer.writeH(count);
            for (int i = 0; i < count; ++i) {
                final int j = i << 2;
                buffer.writeD(integers[j]);
                buffer.writeH(integers[j + 1]);
                buffer.writeD(integers[j + 2]);
                buffer.writeS(names[i]);
                buffer.writeD(integers[j + 3]);
                buffer.writeC(statuses[i].length);
                buffer.writeByte(statuses[i]);
            }
            buffer.writeH(0x07);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
