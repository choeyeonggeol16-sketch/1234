package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import l1j.server.MJTemplate.MJKeyValuePair;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_DogFightSellList {
    private SC_DogFightSellList() {}

    public static boolean send(L1PcInstance pc, int npcObjectId, ArrayList<MJKeyValuePair<Integer, Integer>> pairs) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int count = pairs == null ? 0 : pairs.size();
        if (count > 0xFFFF) return false;
        final long capacity = 2L + 1 + 4 + 2 + 8L * count + 2;
        if (capacity > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_SELL_LIST);
            buffer.writeD(npcObjectId);
            buffer.writeH(count);
            if (pairs != null) {
                for (MJKeyValuePair<Integer, Integer> pair : pairs) {
                    buffer.writeD(pair.key);
                    buffer.writeD(pair.value);
                }
            }
            buffer.writeH(0x07);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
