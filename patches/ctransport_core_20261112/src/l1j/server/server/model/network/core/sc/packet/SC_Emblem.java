package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import java.io.File;
import java.nio.file.Files;

public final class SC_Emblem {
    private SC_Emblem() {}
    public static boolean send(CIOClient client, int emblemId) {
        if (client == null || !client.isConnected()) return false;
        byte[] data = new byte[0];
        try {
            final File file = new File("emblem/" + emblemId);
            data = file.exists() ? Files.readAllBytes(file.toPath()) : new byte[0];
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (data.length > 65535) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(9 + data.length);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EMBLEM);
            buffer.writeD(emblemId);
            buffer.writeH(data.length);
            if (data.length > 0) buffer.writeByte(data);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, int emblemId) {
        return pc != null && send(pc.getNetConnection(), emblemId);
    }

}
