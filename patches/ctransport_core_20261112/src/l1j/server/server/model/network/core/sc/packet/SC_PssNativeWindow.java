package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CUserSocket;

/**
 * Experimental PSS settings-open command on the already-authenticated game TCP
 * stream. Native 2019 ACK 2102 with result=4 (native-ignored) and a private field-15
 * discriminator; never send a valid-start ACK or any gameplay command here.
 * Client DLL consumes the private marker before native ACK decoding.
 */
public final class SC_PssNativeWindow {
    public static final int TYPE = 2102;
    public static final int RESULT = 4;
    private static final byte[] MARKER = { 'R','K','E','R','N','P','S','S',
            'O','P','E','N','2','0','2','6' };

    private SC_PssNativeWindow() { }

    public static boolean send(CIOClient client) {
        if (client == null || !client.isConnected() || !client.isGameSessionReady()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(32);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(TYPE);
            buffer.writeC(0x08);  // field 1: native-ignored result, never VALID
            buffer.writeC(RESULT);
            buffer.writeC(0x7a);  // field 15, length-delimited private marker
            buffer.writeC(MARKER.length);
            buffer.writeByte(MARKER);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
