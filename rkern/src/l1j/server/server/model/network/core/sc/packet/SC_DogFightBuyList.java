package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import l1j.server.DogFight.History.MJDogFightTicketInfo;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_DogFightBuyList {
    private SC_DogFightBuyList() {}

    public static boolean send(L1PcInstance pc, int objectId, ArrayList<MJDogFightTicketInfo> tickets, int price) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int count = tickets == null ? 0 : tickets.size();
        if (count > 0xFFFF) return false;
        final byte[][] status = count == 0 ? null : new byte[count][];
        long capacity = 2L + 1 + 4 + 2 + 2;
        for (int i = 0; i < count; ++i) {
            final MJDogFightTicketInfo info = tickets.get(i);
            final L1ItemInstance dummy = new L1ItemInstance();
            dummy.setItem(info.get_template());
            final byte[] bytes = dummy.getStatusBytes();
            status[i] = bytes;
            capacity += 4L + 2 + 4 + 2L * info.get_template().getName().length() + 1 + 4 + 1 + bytes.length;
        }
        if (capacity > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_BUY_LIST);
            buffer.writeD(objectId);
            buffer.writeH(count);
            for (int i = 0; i < count; ++i) {
                final MJDogFightTicketInfo info = tickets.get(i);
                buffer.writeD(i);
                buffer.writeH(info.get_template().getGfxId());
                buffer.writeD(price);
                buffer.writeS(info.get_template().getName());
                buffer.writeD(info.get_template().getUseType());
                final byte[] bytes = status[i];
                buffer.writeC(bytes.length);
                for (byte b : bytes) buffer.writeC(b & 0xFF);
            }
            buffer.writeH(7);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
