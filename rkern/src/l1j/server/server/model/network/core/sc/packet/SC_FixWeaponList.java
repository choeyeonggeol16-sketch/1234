package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1ItemInstance;

public final class SC_FixWeaponList {

    private SC_FixWeaponList() {}

    public static boolean send(L1PcInstance pc) {
        if (pc == null || pc.getInventory() == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final java.util.List<L1ItemInstance> items = pc.getInventory().getItems();
        int[] entries = new int[Math.max(4, items.size() << 1)];
        int size = 0;
        for (L1ItemInstance item : items) {
            if (item == null || item.getItem() == null || item.getItem().getType2() != 1) continue;
            final int durability = item.get_durability();
            if (durability <= 0) continue;
            if ((size << 1) == entries.length) {
                int[] grown = new int[entries.length << 1];
                System.arraycopy(entries, 0, grown, 0, entries.length);
                entries = grown;
            }
            entries[size << 1] = item.getId();
            entries[(size << 1) + 1] = durability;
            size++;
        }
        final CIOFrame frame = connection.beginDirectFrame(9 + size * 5);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_FIXABLE_ITEM_LIST);
            buffer.writeD(0x000000c8);
            buffer.writeH(size);
            for (int i = 0; i < size; i++) {
                buffer.writeD(entries[i << 1]);
                buffer.writeC(entries[(i << 1) + 1]);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }


}
