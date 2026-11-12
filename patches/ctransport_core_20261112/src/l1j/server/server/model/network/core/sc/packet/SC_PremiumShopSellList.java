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
import l1j.server.server.model.network.utill.ConnectionConfig;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ShopItem;

public final class SC_PremiumShopSellList {
    private static final Charset MS949 = Charset.forName("MS949");
    private static final byte[] EMPTY = new byte[0];

    private SC_PremiumShopSellList() {}

    public static boolean send(L1PcInstance pc, int npcId) {
        return pc != null && send(pc.getNetConnection(), npcId, 0);
    }

    public static boolean send(L1PcInstance pc, int npcId, int objId) {
        return pc != null && send(pc.getNetConnection(), npcId, objId);
    }

    public static boolean send(CIOClient client, int npcId) {
        return send(client, npcId, 0);
    }

    public static boolean send(CIOClient client, int npcId, int objId) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final L1Shop shop = ShopTable.getInstance().get(npcId);
        List<L1ShopItem> list = null;
        if (shop != null) {
            try { list = shop.getSellingItems(); } catch (Exception ignored) {}
        }
        final int limit = list == null ? 0 : list.size();
        final int[] values = new int[limit << 2];
        final byte[][] encodedNames = new byte[limit][];
        final byte[][] statuses = new byte[limit][];
        int count = 0;
        final int tailSize = premiumShopTailSize(npcId);
        long capacity = ConnectionConfig.HEADER_SIZE + 7L;
        for (int i = 0; i < limit; ++i) {
            final L1ShopItem shopItem = list.get(i);
            if (shopItem == null || shopItem.getItem() == null) continue;
            final L1Item item = shopItem.getItem();
            final String name;
            if (shopItem.getPackCount() > 1) {
                name = item.getName() + " (" + shopItem.getPackCount() + ")";
            } else if (shopItem.getEnchant() > 0) {
                name = "+" + shopItem.getEnchant() + " " + item.getName();
            } else if (item.getMaxUseTime() > 0) {
                name = item.getName() + " [" + item.getMaxUseTime() + "]";
            } else {
                name = item.getName();
            }
            byte[] status = null;
            final L1Item template = ItemTable.getInstance().getTemplate(item.getItemId());
            if (template != null) {
                final L1ItemInstance dummy = new L1ItemInstance();
                dummy.setItem(template);
                dummy.setEnchantLevel(shopItem.getEnchant());
                status = dummy.getStatusBytes();
            }
            if (status == null) status = EMPTY;
            if (status.length > 255 || count >= 65535) return false;
            final byte[] encodedName = name == null ? EMPTY : name.getBytes(MS949);
            capacity += 16L + encodedName.length + status.length;
            if (capacity + tailSize > ConnectionConfig.MAX_WIRE_PACKET_SIZE) return false;
            final int j = count << 2;
            values[j] = i;
            values[j + 1] = item.getGfxId();
            values[j + 2] = shopItem.getPrice();
            values[j + 3] = Math.max(0, item.getUseType());
            encodedNames[count] = encodedName;
            statuses[count++] = status;
        }
        capacity += tailSize;
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
                buffer.writeByte(encodedNames[i]);
                buffer.writeC(0);
                buffer.writeD(values[j + 3]);
                buffer.writeC(statuses[i].length);
                buffer.writeByte(statuses[i]);
            }
            writePremiumShopTail(buffer, npcId);
            if (buffer.position() != (int) capacity) {
                System.err.println("[RPremiumShopSellList] SEND length mismatch npcId="
                        + npcId + " reserved=" + capacity
                        + " written=" + buffer.position());
                return false;
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } catch (WriteBuffer.RegisteredCapacityExceededException e) {
            System.err.println("[RPremiumShopSellList] SEND capacity exceeded npcId="
                    + npcId + " reserved=" + capacity
                    + " written=" + frame.buffer().position());
            return false;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static int premiumShopTailSize(int npcId) {
        return npcId == 900107 ? 4 : 2;
    }

    private static void writePremiumShopTail(WriteBuffer buffer, int npcId) {
        if (npcId == 7200002) {
            buffer.writeC(253);
            buffer.writeC(255);
        } else if (npcId == 7000077) {
            buffer.writeC(73);
            buffer.writeC(58);
        } else if (npcId == 900107) {
            buffer.writeC(255);
            buffer.writeC(255);
            buffer.writeC(0);
            buffer.writeC(0);
        } else if (npcId == 6000002) {
            buffer.writeH(0x3ccf);
        } else if (npcId == 7310119 || npcId == 7310120
                || npcId == 7310126 || npcId == 7310124 || npcId == 520) {
            buffer.writeH(0x4215);
        } else if (npcId == 7310128) {
            buffer.writeH(0x41D8);
        } else if (npcId == 91245) {
            buffer.writeH(0x3F23);
        } else if (npcId == 70017 || npcId == 460000360 || npcId == 460000361) {
            buffer.writeH(0x4115);
        } else if (npcId == 7320250) {
            buffer.writeH(0x45AF);
        } else if (npcId == 521) {
            buffer.writeH(17619);
        } else if (npcId == 8500140) {
            buffer.writeH(17368);
        } else if (npcId == 7320055) {
            buffer.writeH(7626);
        } else if (npcId == 2999 || npcId == 2998) {
            buffer.writeH(30050);
        } else if (npcId == 200005 || npcId == 7320222 || npcId == 7320223) {
            buffer.writeH(7);
        } else {
            buffer.writeC(111);
            buffer.writeC(10);
        }
    }
}
