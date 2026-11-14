package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import java.util.List;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;

public final class SC_PetList {
    private SC_PetList() {}

    public static boolean send(L1PcInstance pc, int npcObjId) {
        if (pc == null || pc.getInventory() == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;

        final java.util.Map<?, ?> pets = pc.getPetList();
        final int[] summoned = new int[pets == null ? 0 : pets.size()];
        int summonedCount = 0;
        if (pets != null) {
            for (Object petObj : pets.values()) {
                if (petObj instanceof L1PetInstance) {
                    summoned[summonedCount++] = ((L1PetInstance) petObj).getItemObjId();
                }
            }
        }

        final List<L1ItemInstance> items = pc.getInventory().getItems();
        if (items == null || items.isEmpty()) return false;
        final int[] entries = new int[items.size() * 2];
        int count = 0;
        for (L1ItemInstance item : items) {
            if (item == null || item.getItem() == null) continue;
            final int itemId = item.getItem().getItemId();
            if (itemId != 40314 && itemId != 40316) continue;
            final int objectId = item.getId();
            boolean summonedNow = false;
            for (int i = 0; i < summonedCount; i++) {
                if (summoned[i] == objectId) {
                    summonedNow = true;
                    break;
                }
            }
            if (!summonedNow) {
                entries[count * 2] = objectId;
                entries[count * 2 + 1] = (int) item.getCount();
                count++;
            }
        }
        if (count == 0) return false;

        final CIOFrame frame = connection.beginDirectFrame(9 + 5 * count);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_FIXABLE_ITEM_LIST);
            buffer.writeD(0x00000046);
            buffer.writeH(count);
            for (int i = 0; i < count; i++) {
                buffer.writeD(entries[i * 2]);
                buffer.writeC(entries[i * 2 + 1]);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
