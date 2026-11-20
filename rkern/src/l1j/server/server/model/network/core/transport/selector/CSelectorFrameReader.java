package l1j.server.server.model.network.core.transport.selector;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;

import l1j.server.server.model.network.buffer.CPacketReadBuffer;

/** Receive state for one connection, owned by its Selector thread. */
public final class CSelectorFrameReader implements AutoCloseable {
    @FunctionalInterface
    public interface Handler {
        /** Consume the borrowed payload before returning. Return false to pause after this frame. */
        boolean onFrame(CPacketReadBuffer frame) throws IOException;
    }

    private static final int HEADER_BYTES = Short.BYTES;

    private final CSelectorBufferPool pool;
    private final Thread owner = Thread.currentThread();
    private final CPacketReadBuffer frame = new CPacketReadBuffer();
    private CSelectorBufferPool.Lease lease;
    private int readOffset;
    private boolean dispatching;
    private boolean closed;

    public CSelectorFrameReader(CSelectorBufferPool pool) {
        this.pool = Objects.requireNonNull(pool);
    }

    /** Perform one nonblocking read. Buffered complete frames must be drained before another read. */
    public int read(SocketChannel channel) throws IOException {
        requireIdle();
        try {
            if (hasBufferedFrame()) return 0;
            if (lease == null) {
                lease = pool.acquireCompact();
                if (lease == null) throw new IOException("Compact receive pool exhausted");
            }
            ByteBuffer input = lease.buffer;
            if (!input.hasRemaining() && readOffset != 0) {
                input.flip().position(readOffset);
                input.compact();
                readOffset = 0;
            }
            if (!input.hasRemaining()) {
                // With no complete frame at the head, a full compact buffer
                // belongs to one frame larger than the compact lease.
                CSelectorBufferPool.Lease large = pool.acquireLarge();
                if (large == null) throw new IOException("Large receive pool exhausted");
                CSelectorBufferPool.Lease compact = lease;
                lease = large;
                try {
                    large.buffer.put(input.flip());
                } finally {
                    pool.release(compact);
                }
                input = large.buffer;
            }
            int bytes = channel.read(input);
            if (bytes < 0) {
                if (input.position() != readOffset) throw new EOFException("Truncated packet frame");
                close();
            }
            return bytes;
        } catch (IOException | RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /** True also for a malformed buffered header so the owner can reject it without more I/O. */
    public boolean hasBufferedFrame() {
        requireOwner();
        if (closed || lease == null) return false;
        ByteBuffer input = lease.buffer;
        int available = input.position() - readOffset;
        return available >= HEADER_BYTES && Short.toUnsignedInt(input.getShort(readOffset)) <= available;
    }

    /** Dispatch up to the owner's frame budget. Buffered work can continue without new socket readiness. */
    public int drain(int maxFrames, Handler handler) throws IOException {
        requireIdle();
        if (maxFrames < 1) throw new IllegalArgumentException("Frame budget must be positive");
        Objects.requireNonNull(handler);
        if (lease == null) return 0;
        ByteBuffer input = lease.buffer;
        int filled = input.position();
        int processed = 0;
        dispatching = true;
        try {
            while (!closed && processed < maxFrames && filled - readOffset >= HEADER_BYTES) {
                int wireLength = Short.toUnsignedInt(input.getShort(readOffset));
                if (wireLength < HEADER_BYTES) throw new IOException("Invalid packet frame length");
                if (wireLength > filled - readOffset) break;
                frame.resetTrusted(input, readOffset + HEADER_BYTES, wireLength - HEADER_BYTES);
                boolean proceed;
                try {
                    proceed = handler.onFrame(frame);
                } finally {
                    frame.clear();
                }
                readOffset += wireLength;
                processed++;
                if (!proceed) break;
            }
            return processed;
        } catch (IOException | RuntimeException | Error failure) {
            closed = true;
            throw failure;
        } finally {
            dispatching = false;
            if (closed) {
                releaseLease();
            } else if (readOffset == filled) {
                readOffset = 0;
                if (input.capacity() > CSelectorBufferPool.RECEIVE_BYTES) releaseLease();
                else input.clear();
            }
        }
    }

    @Override
    public void close() {
        requireOwner();
        closed = true;
        if (!dispatching) releaseLease();
    }

    private void releaseLease() {
        CSelectorBufferPool.Lease released = lease;
        lease = null;
        readOffset = 0;
        pool.release(released);
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Wrong receive owner");
    }

    private void requireIdle() {
        requireOwner();
        if (closed || dispatching) throw new IllegalStateException("Receive path closed or dispatching");
    }
}
