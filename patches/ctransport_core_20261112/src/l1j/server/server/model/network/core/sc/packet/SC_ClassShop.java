package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ShopItem;

public final class SC_ClassShop {
    private static final Charset MS949 = Charset.forName("MS949");
    private static final Logger LOG = Logger.getLogger(SC_ClassShop.class.getName());
    private SC_ClassShop() {}

    public static boolean send(L1PcInstance pc, int objId) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final L1Object npcObj = L1World.getInstance().findObject(objId);
        if (!(npcObj instanceof L1NpcInstance)) return false;
        int npcId = ((L1NpcInstance) npcObj).getNpcTemplate().get_npcId();
        final int offset = getClassOffset(pc);
        if (offset > 0) {
            if (npcId == 7320121 || npcId == 7320085) npcId = 73201210 + offset;
            else if (npcId == 2020561) npcId = 2020561 + offset;
            else if (npcId == 415333) npcId = 2020569 + offset;
        }
        final L1Shop shop = ShopTable.getInstance().get(npcId);
        if (shop == null) {
            LOG.log(Level.WARNING, "SC_ClassShop 존재하지 않는 상점 ID - npcId: " + npcId);
            return false;
        }
        final List<L1ShopItem> list = shop.getSellingItems();
        final int limit = list == null ? 0 : list.size();
        final int[] values = new int[limit << 2];
        final String[] names = new String[limit];
        final byte[][] statuses = new byte[limit][];
        final L1ItemInstance dummy = limit > 0 ? new L1ItemInstance() : null;
        int count = 0;
        long size = 11L;
        for (int i = 0; i < limit; i++) {
            final L1ShopItem shopItem = list.get(i);
            if (shopItem == null || shopItem.getItem() == null) continue;
            final L1Item item = shopItem.getItem();
            final String name = shopItem.getPackCount() > 1
                    ? item.getName() + " (" + shopItem.getPackCount() + ")" : item.getName();
            final L1Item template = ItemTable.getInstance().getTemplate(item.getItemId());
            byte[] status = null;
            if (template != null) {
                dummy.setItem(template);
                status = dummy.getStatusBytes();
            }
            if (status != null && status.length > 255) return false;
            if (count >= 65535) return false;
            size += 16L + name.getBytes(MS949).length + (status == null ? 0 : status.length);
            if (size > Integer.MAX_VALUE) return false;
            final int j = count << 2;
            values[j] = i;
            values[j + 1] = item.getGfxId();
            values[j + 2] = shopItem.getPrice();
            values[j + 3] = Math.max(0, item.getUseType());
            names[count] = name;
            statuses[count++] = status;
        }
        final CIOFrame frame = connection.beginDirectFrame((int) size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_BUY_LIST);
            buffer.writeD(npcId);
            buffer.writeH(count);
            for (int i = 0; i < count; ++i) {
                final int j = i << 2;
                buffer.writeD(values[j]);
                buffer.writeH(values[j + 1]);
                buffer.writeD(values[j + 2]);
                buffer.writeS(names[i]);
                buffer.writeD(values[j + 3]);
                final byte[] status = statuses[i];
                buffer.writeC(status == null ? 0 : status.length);
                if (status != null) buffer.writeByte(status);
            }
            buffer.writeH(7);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static int getClassOffset(L1PcInstance pc) {
        if (pc.isCrown()) return 1;
        if (pc.isWizard()) return 2;
        if (pc.isElf()) return 3;
        if (pc.isDarkelf()) return 4;
        if (pc.isDragonknight()) return 5;
        if (pc.isBlackwizard()) return 6;
        if (pc.isWarrior()) return 7;
        if (pc.isKnight()) return 8;
        return 0;
    }
}
