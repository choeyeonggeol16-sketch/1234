package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import java.nio.charset.Charset;
import java.util.List;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_PetInventory {
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_PetInventory() {}

    public static boolean send(L1PcInstance pc, L1PetInstance pet) {
        return pc != null && send(pc.getNetConnection(), pet);
    }

    public static boolean send(CIOClient client, L1PetInstance pet) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int petId = pet == null ? 0 : pet.getId();
        final int ac = pet == null || pet.getAC() == null ? 0 : pet.getAC().getAc();
        final List<L1ItemInstance> items = pet == null || pet.getInventory() == null ? null : pet.getInventory().getItems();
        final int maxCount = items == null ? 0 : items.size();
        final int[] fields = new int[maxCount * 5];
        final String[] names = new String[maxCount];
        int count = 0;
        int wireSize = 11;
        if (items != null) {
            for (L1ItemInstance item : items) {
                if (item == null) continue;
                final int offset = count * 5;
                fields[offset] = item.getId();
                fields[offset + 1] = item.get_gfxid();
                fields[offset + 2] = item.getItem() == null ? 1 : item.getItem().getBless();
                fields[offset + 3] = item.getCount();
                fields[offset + 4] = item.isEquipped() ? 3 : item.isIdentified() ? 1 : 0;
                final String name = item.getViewName();
                names[count] = name == null ? "" : name;
                wireSize += 14 + names[count].getBytes(MS949).length;
                count++;
            }
        }
        final CIOFrame frame = connection.beginDirectFrame(wireSize);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_RETRIEVE_LIST);
            buffer.writeD(petId);
            buffer.writeH(count);
            buffer.writeC(0x0b);
            for (int i = 0; i < count; i++) {
                final int offset = i * 5;
                buffer.writeD(fields[offset]);
                buffer.writeC(0x16);
                buffer.writeH(fields[offset + 1]);
                buffer.writeC(fields[offset + 2]);
                buffer.writeD(fields[offset + 3]);
                buffer.writeC(fields[offset + 4]);
                buffer.writeS(names[i]);
            }
            buffer.writeC(ac);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
