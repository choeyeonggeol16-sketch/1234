package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import java.nio.charset.Charset;
import java.util.List;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1DwarfForPackageInventory;
import l1j.server.server.model.inventory.warehouse.ElfWarehouse;
import l1j.server.server.model.inventory.warehouse.PrivateWarehouse;
import l1j.server.server.model.inventory.warehouse.WarehouseManager;

public final class SC_RetrieveList {
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_RetrieveList() {}

    public static void sendPrivate(L1PcInstance pc, int objId) {
        sendPrivate(pc, objId, 0);
    }

    public static void sendPrivate(L1PcInstance pc, int objId, int suppliedPassword) {
        final CUserSocket client = pc == null ? null : pc.getNetConnection();
        if (client == null) return;
        final int generation = client.BeginWarehouseView(pc, objId, 3, suppliedPassword, 0);
        if (generation == 0 || !canView(pc)) return;
        PrivateWarehouse wh = WarehouseManager.getInstance().getPrivateWarehouse(pc.getAccountName());
        if (wh != null) sendItems(client, pc, objId, wh.GetItemsSnapshot(), 3, suppliedPassword, generation);
    }

    public static boolean sendElf(L1PcInstance pc, int objId) {
        final CUserSocket client = pc == null ? null : pc.getNetConnection();
        if (client == null) return false;
        final int generation = client.BeginWarehouseView(pc, objId, 9, 0, 0);
        if (generation == 0 || !canView(pc)) return false;
        ElfWarehouse wh = WarehouseManager.getInstance().getElfWarehouse(pc.getAccountName());
        return sendItems(client, pc, objId, wh == null ? null : wh.GetItemsSnapshot(), 9, 0, generation);
    }

    public static boolean sendPackage(L1PcInstance pc, int objId) {
        final CUserSocket client = pc == null ? null : pc.getNetConnection();
        if (client == null) return false;
        final int generation = client.BeginWarehouseView(pc, objId, 10, 0, 0);
        if (generation == 0 || !canView(pc)) return false;
        L1DwarfForPackageInventory wh = pc.getDwarfForPackageInventory();
        return sendItems(client, pc, objId, wh == null ? null : wh.GetItemsSnapshot(), 15, 0, generation);
    }

    private static boolean canView(L1PcInstance pc) {
        if (pc == null || pc.getInventory() == null) return false;
        if (pc.getInventory().getSize() < 180) return true;
        SC_ServerMessage.send(pc, 263);
        return false;
    }

    private static boolean sendItems(CUserSocket client, L1PcInstance pc, int objId, List<L1ItemInstance> raw, int type, int suppliedPassword, int generation) {
        if (raw == null || raw.isEmpty()) return true;
        if (!client.IsPendingWarehouseView(pc, objId, type == 15 ? 10 : type, generation)) return false;
        final int size = raw.size();
        if (size > 0xffff) return false; // count is encoded as unsigned short
        final int[] items = new int[size * 4];
        final int[] listedItemIds = new int[size];
        final byte[][] names = new byte[size][];
        int count = 0;
        long wireSize = 2 + 8 + (type == 15 ? 1 : 10);
        for (int i = 0; i < size; i++) {
            final L1ItemInstance item = raw.get(i);
            if (item == null) continue;
            final int p = count * 4;
            final var template = item.getItem();
            final int type2 = template == null ? 0 : template.getType2();
            final int bless = template == null ? 1 : template.getBless();
            items[p] = item.getId();
            listedItemIds[count] = items[p];
            // Wire widths: C(type2), H(gfx), C(bless). Preserve low bits exactly.
            items[p + 1] = (type2 & 0xff) | ((item.get_gfxid() & 0xffff) << 8)
                    | ((bless & 0xff) << 24);
            items[p + 2] = item.getCount();
            items[p + 3] = item.isIdentified() ? 1 : 0;
            final String name = item.getViewName();
            names[count] = (name == null ? "" : name).getBytes(MS949);
            wireSize += 14L + names[count].length + (type == 15 ? 0 : 1);
            count++;
        }
        if (count == 0) return true;
        if (wireSize > Integer.MAX_VALUE) return false;
        if (!client.IsPendingWarehouseView(pc, objId, type == 15 ? 10 : type, generation)) return false;
        final CUserSocket connection = client;
        if (!connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) wireSize);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble b = frame.buffer();
            b.writeC(Opcodes.S_RETRIEVE_LIST);
            b.writeD(objId);
            b.writeH(count);
            b.writeC(type);
            for (int j = 0; j < count; j++) {
                final int p = j * 4;
                final int packed = items[p + 1];
                b.writeD(items[p]);
                b.writeC(packed & 0xff);
                b.writeH((packed >>> 8) & 0xffff);
                b.writeC(packed >>> 24);
                b.writeD(items[p + 2]);
                b.writeC(items[p + 3]);
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
            if (!connection.IsPendingWarehouseView(pc, objId, type == 15 ? 10 : type, generation)) return false;
            committed = connection.commitDirectFrame(frame);
            if (committed) connection.BindWarehouseView(pc, objId, type == 15 ? 10 : type,
                    suppliedPassword, 0, generation, listedItemIds);
            return false;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
