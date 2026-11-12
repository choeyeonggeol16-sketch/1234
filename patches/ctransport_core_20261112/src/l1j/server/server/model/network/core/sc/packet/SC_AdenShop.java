package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;
import java.util.List;

import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ShopItem;

public final class SC_AdenShop {
    private static final int DEFAULT_NPC_ID = 5;
    private static final Charset MS949 = Charset.forName("MS949");

    private SC_AdenShop() {}

    public static boolean send(L1PcInstance receiver, L1PcInstance pc) {
        return receiver != null && send(receiver.getNetConnection(), pc);
    }

    public static boolean send(CIOClient client, L1PcInstance pc) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final L1Shop shop = ShopTable.getInstance().get(DEFAULT_NPC_ID);
        final List<L1ShopItem> list = shop == null ? null : shop.getSellingItems();
        final int length = list == null ? 0 : list.size();
        final int[] values = new int[length * 3];
        final String[] names = new String[length];
        final byte[][] statuses = new byte[length][];
        int count = 0;
        long capacity = 11L;
        for (int i = 0; i < length; ++i) {
            final L1ShopItem shopItem = list.get(i);
            if (shopItem == null || shopItem.getItem() == null) continue;
            final L1Item item = shopItem.getItem();
            final String name = shopItem.getPackCount() > 1
                    ? item.getName() + " (" + shopItem.getPackCount() + ")" : item.getName();
            final L1Item template = ItemTable.getInstance().getTemplate(item.getItemId());
            byte[] status = null;
            if (template != null) {
                final L1ItemInstance dummy = new L1ItemInstance();
                dummy.setItem(template);
                status = dummy.getStatusBytes();
            }
            if (status != null && status.length > 255) return false;
            if (count >= 65535) return false;
            capacity += 16L + name.getBytes(MS949).length + (status == null ? 0 : status.length);
            if (capacity > Integer.MAX_VALUE) return false;
            final int j = count * 3;
            values[j] = item.getGfxId();
            values[j + 1] = shopItem.getPrice();
            values[j + 2] = Math.max(0, item.getUseType());
            names[count] = name;
            statuses[count++] = status;
        }
        final CIOFrame frame = connection.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_BUY_LIST);
            buffer.writeD(7626);
            buffer.writeH(count);
            for (int i = 0; i < count; ++i) {
                final int j = i * 3;
                buffer.writeD(i);
                buffer.writeH(values[j]);
                buffer.writeD(values[j + 1]);
                buffer.writeS(names[i]);
                buffer.writeD(values[j + 2]);
                final byte[] status = statuses[i];
                buffer.writeC(status == null ? 0 : status.length);
                if (status != null) buffer.writeByte(status);
            }
            buffer.writeH(30050);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
