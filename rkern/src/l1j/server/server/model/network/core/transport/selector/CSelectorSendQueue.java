package l1j.server.server.model.network.core.transport.selector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.codec.CPacketCryptor;

/** Owner-local FIFO. A slot stays owned until the last byte is accepted by the socket. */
final class CSelectorSendQueue implements AutoCloseable {
    private static final int HEADER_BYTES = Short.BYTES;
    private static final int MAX_WIRE_BYTES = 0xffff;
    private static final int GATHER_BYTES = 256 * 1024;

    private final CSelectorBufferPool pool;
    private final int maxBytes;
    private final int maxFrames;
    private CSelectorBufferPool.Lease head;
    private CSelectorBufferPool.Lease tail;
    private CPacketWriteBuffer writer;
    private int bytes;
    private int frames;
    private boolean closed;
    private boolean encoding;

    CSelectorSendQueue(CSelectorBufferPool pool, int maxBytes, int maxFrames) {
        this.pool = pool;
        this.maxBytes = maxBytes;
        this.maxFrames = maxFrames;
    }

    boolean send(int maxPayloadBytes, CSelectorTransport.FrameEncoder encoder, CPacketCryptor cipher, boolean plaintext)
            throws IOException {
        if (maxPayloadBytes < 1 || maxPayloadBytes > MAX_WIRE_BYTES - HEADER_BYTES)
            throw new IllegalArgumentException("Invalid outbound payload size");
        if (encoding) throw new IllegalStateException("Reentrant frame encoding");
        if (closed || frames == maxFrames || maxBytes - bytes <= HEADER_BYTES) return false;
        int capacity = maxPayloadBytes + HEADER_BYTES;
        CSelectorBufferPool.Lease lease = tail;
        boolean append = lease != null && lease.buffer.position() == 0
                && capacity <= lease.buffer.capacity() - lease.buffer.limit();
        if (!append) lease = pool.acquire(capacity);
        if (lease == null) return false;
        ByteBuffer buffer = lease.buffer;
        int start = append ? buffer.limit() : 0;
        boolean committed = false;
        encoding = true;
        try {
            if (writer == null) writer = new CPacketWriteBuffer();
            writer.armExternal(buffer, start, capacity);
            writer.beginDirect();
            writer.position(HEADER_BYTES);
            encoder.encode(writer);
            int length = writer.position();
            if (length <= HEADER_BYTES || length > capacity)
                throw new IllegalArgumentException("Invalid encoded frame size");
            if (closed || length > maxBytes - bytes) return false;
            buffer.putShort(start, (short) length);
            if (cipher != null && !plaintext) {
                try {
                    cipher.encrypt(writer, HEADER_BYTES, length - HEADER_BYTES);
                } catch (RuntimeException | Error failure) {
                    // A broken encryption attempt may already have advanced the outbound key.
                    close();
                    throw failure;
                }
            }
            buffer.position(0).limit(start + length);
            if (!append) {
                if (tail == null) head = lease;
                else tail.next = lease;
                tail = lease;
            }
            lease.frames++;
            bytes += length;
            frames++;
            committed = true;
            return true;
        } finally {
            if (writer != null) writer.releaseResources();
            encoding = false;
            if (!committed) {
                if (!append) pool.release(lease);
                else if (!closed) buffer.position(0).limit(start);
            }
        }
    }

    long flush(SocketChannel channel, ByteBuffer[] gather) throws IOException {
        int count = 0;
        int offered = 0;
        long written;
        try {
            for (CSelectorBufferPool.Lease lease = head; lease != null && count < gather.length; lease = lease.next) {
                int remaining = lease.buffer.remaining();
                if (count != 0 && offered + remaining > GATHER_BYTES) break;
                gather[count++] = lease.buffer;
                offered += remaining;
            }
            if (count == 0) return 0;
            written = count == 1 ? channel.write(gather[0]) : channel.write(gather, 0, count);
        } finally {
            for (int i = 0; i < count; i++) gather[i] = null;
        }
        bytes -= (int) written;
        while (head != null && !head.buffer.hasRemaining()) {
            CSelectorBufferPool.Lease sent = head;
            head = sent.next;
            frames -= sent.frames;
            pool.release(sent);
        }
        if (head == null) tail = null;
        return written;
    }

    int pendingBytes() { return bytes; }
    int pendingFrames() { return frames; }
    boolean isEmpty() { return head == null; }
    boolean isEncoding() { return encoding; }
    boolean isClosed() { return closed; }

    @Override
    public void close() {
        closed = true;
        while (head != null) {
            CSelectorBufferPool.Lease discarded = head;
            head = discarded.next;
            pool.release(discarded);
        }
        tail = null;
        bytes = 0;
        frames = 0;
    }
}
