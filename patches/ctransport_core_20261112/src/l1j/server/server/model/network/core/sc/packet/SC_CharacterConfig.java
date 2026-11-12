package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_CharacterConfig {
    private static final Logger LOG = Logger.getLogger(SC_CharacterConfig.class.getName());
    private SC_CharacterConfig() {}

    public static final class Snapshot {
        final int length;
        final byte[] data;
        private Snapshot(int length, byte[] data) {
            this.length = length;
            this.data = data == null ? new byte[0] : data;
        }
    }

    public static Snapshot load(int objectId) {
        int length = 0;
        byte[] data = null;
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT length, data FROM character_config WHERE object_id=?")) {
            ps.setInt(1, objectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    length = rs.getInt(1);
                    data = rs.getBytes(2);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "SC_CharacterConfig DB 조회 중 예외 발생 - objectId: " + objectId, e);
        }
        return new Snapshot(length, data);
    }

    public static boolean send(L1PcInstance pc, Snapshot snapshot) {
        return pc != null && send(pc.getNetConnection(), snapshot);
    }

    public static boolean send(CIOClient client, Snapshot snapshot) {
        if (client == null || !client.isConnected() || snapshot == null) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final boolean hasData = snapshot.length != 0 && snapshot.data.length > 0;
        final CIOFrame frame = connection.beginDirectFrame(
                10 + (hasData ? snapshot.data.length : 0));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(41);
            buffer.writeD(hasData ? snapshot.length : 0);
            if (hasData) buffer.writeByte(snapshot.data);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
