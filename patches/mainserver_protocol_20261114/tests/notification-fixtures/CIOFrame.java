package l1j.server.server.model.network.core.connection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import l1j.server.server.model.network.buffer.WriteBuffer;
public final class CIOFrame {
    final ByteBuffer bytes = ByteBuffer.allocateDirect(65536).order(ByteOrder.LITTLE_ENDIAN);
    private final WriteBuffer writer = new WriteBuffer();
    CIOFrame() {
        writer.armExternal(bytes, 0, bytes.capacity());
        writer.reset(CIOFrame.class);
    }
    public WriteBuffer buffer() { return writer; }
}
