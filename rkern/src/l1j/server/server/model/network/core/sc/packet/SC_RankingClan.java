package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.L1ClanRanking.RankData;
import l1j.server.server.utils.BinaryOutputStream;

public final class SC_RankingClan {
    private SC_RankingClan() {}

    public static boolean send(L1PcInstance pc, RankData[] datas) {
        return pc != null && send(pc.getNetConnection(), datas);
    }

    public static boolean send(CIOClient client, RankData[] datas) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;

        final List<byte[]> entries = new ArrayList<>(datas == null ? 0 : datas.length);
        int wireSize = 7;
        if (datas != null) {
            for (RankData data : datas) {
                if (data == null) continue;
                final BinaryOutputStream os = new BinaryOutputStream();
                try {
                    final String name = data.getClanName();
                    final java.util.Date date = data.getDate();
                    os.writeC(0x08);
                    os.writeBit(data.getCurrentR());
                    os.writeC(0x10);
                    os.writeBit(data.getPastR());
                    os.writeC(0x18);
                    os.writeBit(data.getClanid());
                    os.writeC(0x22);
                    os.writeS2(name == null ? "" : name);
                    os.writeC(0x28);
                    os.writeC(0);
                    os.writeC(0x30);
                    os.writeBit(data.getComTime());
                    os.writeC(0x38);
                    os.writeBit(date == null ? 0 : (int) (date.getTime() / 1000L));
                    final byte[] bytes = os.getBytes();
                    if (wireSize > Integer.MAX_VALUE - bytes.length - 2) return false;
                    wireSize += bytes.length + 2;
                    entries.add(bytes);
                } finally {
                    try { os.close(); } catch (IOException ignored) {}
                }
            }
        }

        final CIOFrame frame = connection.beginDirectFrame(wireSize);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(147);
            for (int i = 0, n = entries.size(); i < n; i++) {
                final byte[] bytes = entries.get(i);
                buffer.writeC(0x0A);
                buffer.writeC(bytes.length);
                buffer.writeByte(bytes);
            }
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
