import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Fixed, open-loop round-robin offers on persistent TCP peers. */
public final class PacedDriver {
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
    private static final long POLL_NANOS = TimeUnit.MICROSECONDS.toNanos(100);
    private static final int WINDOW = 8, CONNECT_BATCH = 64, OFFER_BATCH = 64;

    private static final class Batch {
        final byte[][] frames;
        final int[] bodyHashes;
        final long[] sentAt;
        final ByteBuffer wire, reply;
        long plannedAt;
        int size, validated;
        boolean measured;

        Batch(int actor, int firstSlot, int batchSize) {
            frames = new byte[batchSize][CoreProtocol.FRAME_BYTES];
            bodyHashes = new int[batchSize];
            sentAt = new long[batchSize];
            for (int i = 0; i < batchSize; i++)
                bodyHashes[i] = CoreProtocol.initializeClientFrame(frames[i], actor, firstSlot + i);
            int bytes = batchSize * CoreProtocol.FRAME_BYTES;
            wire = batchSize == 1 ? ByteBuffer.wrap(frames[0]) : ByteBuffer.allocate(bytes);
            reply = ByteBuffer.allocate(bytes);
        }

        void prepare(int actor, int firstSequence, int size, long plannedAt, boolean measured) {
            this.size = size;
            this.plannedAt = plannedAt;
            this.measured = measured;
            validated = 0;
            int bytes = size * CoreProtocol.FRAME_BYTES;
            wire.clear().limit(bytes);
            reply.clear().limit(bytes);
            for (int i = 0; i < size; i++) {
                byte[] frame = frames[i];
                CoreProtocol.writeRequest(frame, actor, firstSequence + i, bodyHashes[i]);
                if (frames.length > 1)
                    System.arraycopy(frame, 0, wire.array(), i * CoreProtocol.FRAME_BYTES, frame.length);
            }
        }
    }

    private static final class Peer {
        final int actor;
        final SocketChannel socket;
        final Batch[] batches;
        SelectionKey key;
        int queued, written, read, nextSequence, readSequence;
        boolean measured, writeArmed;

        Peer(int actor, SocketChannel socket, int batchSize) {
            this.actor = actor;
            this.socket = socket;
            batches = new Batch[WINDOW / batchSize];
            for (int slot = 0; slot < batches.length; slot++)
                batches[slot] = new Batch(actor, slot * batchSize, batchSize);
        }
    }

    private final Peer[] peers;
    private final int rate, batchSize, warmupCount, scheduledCount, warmupBatches, scheduledBatches;
    private final long warmupNanos, measureNanos;
    private final long[] plannedLatency, roundTrip, sendLag;
    private final byte[] singleReply = new byte[CoreProtocol.FRAME_BYTES];
    private final ByteBuffer unexpected = ByteBuffer.allocate(1);
    private int samples, measuredClients, completedInWindow, drops, sentMeasured;
    private int warmupValid, warmupDrops;
    private int pending, waiting, peakPending, peakWaiting;
    private int pendingAtEnd = -1, unscheduledAtEnd;
    private long began, measureStart, measureEnd, finishedAt;

    private PacedDriver(int clients, int warmupMs, int measureMs, int rate, int batchSize) {
        if (clients < 1 || warmupMs < 1 || measureMs < 1 || rate < 1 || batchSize < 1 || WINDOW % batchSize != 0)
            throw new IllegalArgumentException("positive clients, times and rate with compatible batch size required");
        this.rate = rate;
        this.batchSize = batchSize;
        peers = new Peer[clients];
        warmupNanos = TimeUnit.MILLISECONDS.toNanos(warmupMs);
        measureNanos = TimeUnit.MILLISECONDS.toNanos(measureMs);
        warmupCount = Math.toIntExact((long) warmupMs * rate / 1000);
        scheduledCount = Math.toIntExact((long) measureMs * rate / 1000);
        warmupBatches = Math.ceilDiv(warmupCount, batchSize);
        scheduledBatches = Math.ceilDiv(scheduledCount, batchSize);
        if (scheduledBatches < clients || (long) warmupCount + scheduledCount > 5_000_000)
            throw new IllegalArgumentException("Each peer needs one measured offer; at most five million offers");
        plannedLatency = new long[scheduledCount];
        roundTrip = new long[scheduledCount];
        sendLag = new long[scheduledCount];
    }

    private void connect(Selector selector, InetSocketAddress address) throws IOException {
        long deadline = System.nanoTime() + 35 * SECOND;
        int opening = 0, connected = 0;
        while (connected < peers.length) {
            while (opening < peers.length && opening - connected < CONNECT_BATCH) {
                SocketChannel socket = SocketChannel.open();
                Peer peer = new Peer(opening + 1, socket, batchSize);
                peers[opening++] = peer;
                socket.configureBlocking(false);
                socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
                if (socket.connect(address)) {
                    peer.key = socket.register(selector, SelectionKey.OP_READ, peer);
                    connected++;
                } else {
                    peer.key = socket.register(selector, SelectionKey.OP_CONNECT, peer);
                }
            }
            if (connected == peers.length) break;
            if (System.nanoTime() >= deadline) throw new IOException("Connection setup timed out at " + connected);
            selector.select(250);
            var keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                Peer peer = (Peer) key.attachment();
                if (key.isConnectable() && peer.socket.finishConnect()) {
                    key.interestOps(SelectionKey.OP_READ);
                    connected++;
                }
            }
        }
    }

    private long deadline(int offered) {
        if (offered < warmupBatches) return began + (long) offered * batchSize * SECOND / rate;
        return measureStart + (long) (offered - warmupBatches) * batchSize * SECOND / rate;
    }

    private void offer(Peer peer, int ordinal, long plannedAt) throws IOException {
        boolean measured = ordinal >= warmupBatches;
        int remaining = measured ? scheduledCount - (ordinal - warmupBatches) * batchSize
                : warmupCount - ordinal * batchSize;
        int frames = Math.min(batchSize, remaining);
        if (peer.queued - peer.read == peer.batches.length) {
            if (measured) drops += frames;
            else warmupDrops += frames;
            return;
        }
        Batch batch = peer.batches[peer.queued % peer.batches.length];
        batch.prepare(peer.actor, peer.nextSequence, frames, plannedAt, measured);
        peer.nextSequence += frames;
        peer.queued++;
        waiting += frames;
        pending += frames;
        if (measured) {
            peakWaiting = Math.max(peakWaiting, waiting);
            peakPending = Math.max(peakPending, pending);
        }
        if (peer.queued - peer.written == 1) write(peer);
    }

    private void write(Peer peer) throws IOException {
        while (peer.written < peer.queued) {
            Batch batch = peer.batches[peer.written % peer.batches.length];
            int before = batch.wire.position();
            long beforeWrite = System.nanoTime();
            if (peer.socket.write(batch.wire) == 0) break;
            int after = batch.wire.position();
            int firstStarted = Math.ceilDiv(before, CoreProtocol.FRAME_BYTES);
            int nextStarted = Math.ceilDiv(after, CoreProtocol.FRAME_BYTES);
            Arrays.fill(batch.sentAt, firstStarted, nextStarted, beforeWrite);
            int frames = after / CoreProtocol.FRAME_BYTES - before / CoreProtocol.FRAME_BYTES;
            waiting -= frames;
            if (batch.measured) sentMeasured += frames;
            if (batch.wire.hasRemaining()) break;
            peer.written++;
        }
        boolean wantsWrite = peer.written < peer.queued;
        if (peer.writeArmed != wantsWrite) {
            peer.key.interestOps(SelectionKey.OP_READ | (wantsWrite ? SelectionKey.OP_WRITE : 0));
            peer.writeArmed = wantsWrite;
        }
    }

    private void read(Peer peer) throws IOException {
        if (peer.read == peer.queued) {
            if (peer.socket.read(unexpected.clear()) != 0)
                throw new IOException("Unexpected input with no request for actor " + peer.actor);
            return;
        }
        for (int i = 0; i < peer.batches.length && peer.read < peer.queued; i++) {
            Batch batch = peer.batches[peer.read % peer.batches.length];
            int n = peer.socket.read(batch.reply);
            if (n < 0) throw new IOException("Unexpected EOF for actor " + peer.actor);
            if (n == 0) return;
            long completed = System.nanoTime();
            int ready = batch.reply.position() / CoreProtocol.FRAME_BYTES;
            if (ready > batch.wire.position() / CoreProtocol.FRAME_BYTES)
                throw new IOException("Reply before a full write for actor " + peer.actor);
            while (batch.validated < ready) {
                int slot = batch.validated++;
                byte[] response = batch.reply.array();
                if (batchSize > 1) {
                    System.arraycopy(response, slot * CoreProtocol.FRAME_BYTES, singleReply, 0, singleReply.length);
                    response = singleReply;
                }
                if (!CoreProtocol.validReply(batch.frames[slot], response, peer.actor,
                        peer.readSequence, batch.bodyHashes[slot]))
                    throw new IOException("Invalid reply for actor " + peer.actor + " sequence " + peer.readSequence);
                if (batch.measured) {
                    plannedLatency[samples] = completed - batch.plannedAt;
                    roundTrip[samples] = completed - batch.sentAt[slot];
                    sendLag[samples++] = batch.sentAt[slot] - batch.plannedAt;
                    if (completed < measureEnd) completedInWindow++;
                    if (!peer.measured) {
                        peer.measured = true;
                        measuredClients++;
                    }
                } else {
                    warmupValid++;
                }
                peer.readSequence++;
                pending--;
            }
            if (batch.reply.hasRemaining()) return;
            peer.read++;
        }
    }

    private void work(Selector selector) throws IOException {
        began = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100);
        measureStart = began + warmupNanos;
        measureEnd = measureStart + measureNanos;
        final int total = warmupBatches + scheduledBatches;
        int offered = 0;
        long pollAt = began;
        while (offered < total || pending != 0) {
            long now = System.nanoTime();
            if (now >= measureEnd && pendingAtEnd < 0) {
                pendingAtEnd = pending;
                unscheduledAtEnd = Math.max(0, scheduledCount - Math.max(0, offered - warmupBatches) * batchSize);
            }
            if (now >= measureEnd + 8 * SECOND)
                throw new IOException("Drain timed out, offered batches=" + offered + " pending frames=" + pending);
            for (int batch = 0; batch < OFFER_BATCH && offered < total; batch++) {
                long plannedAt = deadline(offered);
                if (now < plannedAt) break;
                offer(peers[offered % peers.length], offered, plannedAt);
                offered++;
            }
            boolean due = offered < total && deadline(offered) <= now;
            if (due || now >= pollAt) {
                if (offered == total && pending != 0) selector.select(1);
                else selector.selectNow();
                pollAt = now + POLL_NANOS;
                var keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    Peer peer = (Peer) key.attachment();
                    if (key.isReadable()) read(peer);
                    if (key.isWritable()) write(peer);
                }
            } else if (now < began) {
                // There are no sends or replies before the first offer.
                selector.select(Math.max(1, TimeUnit.NANOSECONDS.toMillis(began - now)));
            } else {
                Thread.onSpinWait();
            }
        }
        finishedAt = System.nanoTime();
    }

    private static double p99Us(long[] values, int count) {
        Arrays.sort(values, 0, count);
        return values[(int) (((long) count * 99 + 99) / 100) - 1] / 1000.0;
    }

    private void report() throws IOException {
        if (samples == 0 || samples != sentMeasured || samples + drops != scheduledCount
                || warmupValid + warmupDrops != warmupCount
                || Arrays.stream(peers).anyMatch(peer -> peer.read != peer.queued || peer.written != peer.queued
                        || peer.nextSequence != peer.readSequence))
            throw new IOException("Frame accounting mismatch: accepted=" + sentMeasured + " valid=" + samples
                    + " dropped=" + drops + " scheduled=" + scheduledCount);
        System.err.printf("WARMUP scheduled=%d valid=%d dropped=%d%n", warmupCount, warmupValid, warmupDrops);
        // The rates include only measurements scheduled in the measured phase.
        // Timely counts actual replies before the end; drained rate charges time past that end.
        double timelyRate = completedInWindow * (double) SECOND / measureNanos;
        long drainNanos = Math.max(0, finishedAt - measureEnd);
        double drainedRate = samples * (double) SECOND / (measureNanos + drainNanos);
        System.out.printf(Locale.ROOT,
                "RESULT %d %d %.3f %.3f %.3f %.3f %d %d %d %d %d %d %d %.3f %.3f%n",
                scheduledCount, measuredClients, timelyRate, p99Us(plannedLatency, samples), p99Us(roundTrip, samples),
                p99Us(sendLag, samples), samples, completedInWindow, drops, peakPending, peakWaiting,
                Math.max(0, pendingAtEnd), unscheduledAtEnd, drainNanos / 1e6, drainedRate);
    }

    private void run(InetSocketAddress address) throws IOException {
        try (Selector selector = Selector.open()) {
            connect(selector, address);
            work(selector);
            report();
        } finally {
            for (Peer peer : peers) {
                if (peer != null) peer.socket.close();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        runMode(args, 1);
    }

    static void runMode(String[] args, int batchSize) throws IOException {
        if (args.length != 5)
            throw new IllegalArgumentException("Usage: <port> <clients> <warmupMs> <measureMs> <aggregateRate>");
        var address = new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0]));
        new PacedDriver(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                Integer.parseInt(args[3]), Integer.parseInt(args[4]), batchSize).run(address);
    }
}
