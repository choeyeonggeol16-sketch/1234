package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Arrays;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1DwarfForPackageInventory;
import l1j.server.server.model.Warehouse.ElfWarehouse;
import l1j.server.server.model.Warehouse.PrivateWarehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class SC_RetrieveList {
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_RetrieveList() {}

    public static void sendPrivate(L1PcInstance pc, int objId) {
        if (!canView(pc)) return;
        PrivateWarehouse wh = WarehouseManager.getInstance().getPrivateWarehouse(pc.getAccountName());
        if (wh != null) sendItems(pc, objId, wh.getItems(), 3);
    }

    public static boolean sendElf(L1PcInstance pc, int objId) {
        if (!canView(pc)) return false;
        ElfWarehouse wh = WarehouseManager.getInstance().getElfWarehouse(pc.getAccountName());
        return sendItems(pc, objId, wh == null ? null : wh.getItems(), 9);
    }

    public static boolean sendPackage(L1PcInstance pc, int objId) {
        if (!canView(pc)) return false;
        L1DwarfForPackageInventory wh = pc.getDwarfForPackageInventory();
        return sendItems(pc, objId, wh == null ? null : wh.getItems(), 15);
    }

    private static boolean canView(L1PcInstance pc) {
        if (pc == null || pc.getInventory() == null) return false;
        if (pc.getInventory().getSize() < 180) return true;
        SC_ServerMessage.send(pc, 263);
        return false;
    }

    private static boolean sendItems(L1PcInstance pc, int objId, List<?> raw, int type) {
        if (raw == null || raw.isEmpty()) return true;
        final int initial = Math.max(1, raw.size());
        int[] items = new int[initial * 6];
        byte[][] names = new byte[initial][];
        int count = 0;
        long wireSize = 2 + 8 + (type == 15 ? 1 : 10);
        for (Object value : raw) {
            if (!(value instanceof L1ItemInstance)) continue;
            final L1ItemInstance item = (L1ItemInstance) value;
            if (count == names.length) {
                final int next = count + (count >> 1) + 1;
                if (next > Integer.MAX_VALUE / 6) return false;
                items = Arrays.copyOf(items, next * 6);
                names = Arrays.copyOf(names, next);
            }
            final int p = count * 6;
            items[p] = item.getId();
            items[p + 1] = item.getItem() == null ? 0 : item.getItem().getType2();
            items[p + 2] = item.get_gfxid();
            items[p + 3] = item.getItem() == null ? 1 : item.getItem().getBless();
            items[p + 4] = (int) item.getCount();
            items[p + 5] = item.isIdentified() ? 1 : 0;
            String name = item.getViewName();
            names[count] = (name == null ? "" : name).getBytes(MS949);
            wireSize += 14L + names[count].length + (type == 15 ? 0 : 1);
            count++;
        }
        if (count == 0) return true;
        if (wireSize > Integer.MAX_VALUE) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) wireSize);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer b = frame.buffer();
            b.writeC(Opcodes.S_RETRIEVE_LIST);
            b.writeD(objId);
            b.writeH(count);
            b.writeC(type);
            for (int j = 0; j < count; j++) {
                final int p = j * 6;
                b.writeD(items[p]);
                b.writeC(items[p + 1]);
                b.writeH(items[p + 2]);
                b.writeC(items[p + 3]);
                b.writeD(items[p + 4]);
                b.writeC(items[p + 5]);
                b.writeByte(names[j]);
                b.writeC(0);
                if (type != 15) b.writeC(0);
            }
            if (type == 15) {
                b.writeC(30);
            } else {
                b.writeD(type == 3 ? 100 : 30);
                b.writeD(0);
                b.writeH(0);
            }
            committed = connection.commitDirectFrame(frame);
            return false;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
