package l1j.server.server.model.network.core;

import java.nio.ByteBuffer;
import l1j.server.server.model.network.buffer.WriteBuffer;

/** Test-only lease; the production WriteBuffer and protobuf writer are real. */
public final class CIOFrame {
    public final WriteBuffer writer = new WriteBuffer();
    final ByteBuffer storage = ByteBuffer.allocate(65535).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    public boolean active;
    public int ticket;
    public WriteBuffer buffer() { return writer; }
    void begin(int capacity, int sequence) {
        if (active) throw new AssertionError("lease still owned");
        active = true;
        ticket = sequence;
        writer.armExternal(storage, 0, capacity);
        writer.beginDirect();
        writer.writeH(0); // Transport-owned wire length, just as acquireSend().
    }
    void release() {
        if (!active) throw new AssertionError("double release / recycled lease");
        active = false;
        writer.detach();
    }
}
