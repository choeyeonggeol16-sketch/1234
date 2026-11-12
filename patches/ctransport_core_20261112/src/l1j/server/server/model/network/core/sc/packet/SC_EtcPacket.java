package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.L1HauntedHouse;

public final class SC_EtcPacket {
    private SC_EtcPacket() {}
    public static boolean send(CIOClient client, int type) {
        if (client == null || !client.isConnected()) return false;
        int membersCount = 0;
        List<String> names = null;
        int size = 12;
        if (type == 2) {
            try {
                membersCount = L1HauntedHouse.getInstance().getMembersCount();
                L1PcInstance[] members = L1HauntedHouse.getInstance().getMembersArray();
                if (members != null) {
                    names = new ArrayList<>(members.length);
                    for (L1PcInstance player : members) {
                        if (player != null && player.getName() != null) {
                            String name = player.getName();
                            names.add(name);
                            size += 1 + (name.length() << 1);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            switch (type) {
                case 0: buffer.writeC(71); buffer.writeC(500); break;
                case 1:
                    buffer.writeC(0x46); buffer.writeC(147); buffer.writeC(92);
                    buffer.writeC(151); buffer.writeC(220); buffer.writeC(42); buffer.writeC(74);
                    break;
                case 2:
                    buffer.writeC(66); buffer.writeH(membersCount); buffer.writeH(1);
                    if (names != null) for (int i = 0, n = names.size(); i < n; ++i)
                        buffer.writeS(names.get(i));
                    break;
                case 3:
                    buffer.writeC(64); buffer.writeC(5); buffer.writeC(129);
                    buffer.writeC(252); buffer.writeC(125); buffer.writeC(110); buffer.writeC(17);
                    break;
                case 4:
                    buffer.writeC(0x41); buffer.writeC(100); buffer.writeC(0); buffer.writeC(0);
                    buffer.writeC(0); buffer.writeC(0); buffer.writeC(0);
                    break;
                case 5: buffer.writeC(72); break;
                default: break;
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, int type) {
        return pc != null && send(pc.getNetConnection(), type);
    }

}
