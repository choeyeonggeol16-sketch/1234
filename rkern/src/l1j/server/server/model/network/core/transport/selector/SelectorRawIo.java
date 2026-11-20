package l1j.server.server.model.network.core.transport.selector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/** One pending operation in each direction. The caller owns each borrowed buffer until completion. */
final class SelectorRawIo {
    private ByteBuffer read;
    private IntConsumer readCompletion;
    private ByteBuffer singleWrite;
    private ByteBuffer[] gather;
    private int offset;
    private int count;
    private long drainBytes;
    private LongConsumer writeCompletion;

    void armRead(ByteBuffer target, IntConsumer completion) {
        if (read != null) throw new IllegalStateException("READ already pending");
        read = target;
        readCompletion = completion;
    }

    void armWrite(ByteBuffer single, ByteBuffer[] buffers, int start, int length, LongConsumer completion) {
        if (writeCompletion != null) throw new IllegalStateException("WRITE already pending");
        singleWrite = single;
        gather = buffers;
        offset = start;
        count = length;
        writeCompletion = completion;
    }

    boolean reading() { return read != null; }
    boolean writing() { return writeCompletion != null; }

    int read(SocketChannel channel) throws IOException {
        if (read == null) return 0;
        int bytes = channel.read(read);
        if (bytes != 0) completeRead(bytes);
        return bytes;
    }

    long write(SocketChannel channel, boolean draining) throws IOException {
        if (!writing()) return 0;
        long bytes = singleWrite != null ? channel.write(singleWrite) : channel.write(gather, offset, count);
        if (draining) {
            // After the drain starts, the provider owns the remainder of the last request.
            // Its completion callback cannot register another WRITE to send a partial tail.
            drainBytes += bytes;
            if (!hasWriteRemaining()) completeWrite(drainBytes);
        } else if (bytes != 0) completeWrite(bytes);
        return bytes;
    }

    private boolean hasWriteRemaining() {
        if (singleWrite != null) return singleWrite.hasRemaining();
        for (int i = offset; i < offset + count; i++) {
            if (gather[i].hasRemaining()) return true;
        }
        return false;
    }

    void cancel(Consumer<RuntimeException> report) {
        try {
            if (reading()) completeRead(-1);
        } catch (RuntimeException error) {
            report.accept(error);
        } finally {
            try {
                if (writing()) completeWrite(-1);
            } catch (RuntimeException error) {
                report.accept(error);
            }
        }
    }

    private void completeRead(int bytes) {
        IntConsumer completion = readCompletion;
        read = null;
        readCompletion = null;
        completion.accept(bytes);
    }

    private void completeWrite(long bytes) {
        LongConsumer completion = writeCompletion;
        singleWrite = null;
        gather = null;
        offset = count = 0;
        drainBytes = 0L;
        writeCompletion = null;
        completion.accept(bytes);
    }
}
