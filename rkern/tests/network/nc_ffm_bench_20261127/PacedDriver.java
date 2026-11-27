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

    private static final class Request {
        final byte[] bytes = new byte[CoreProtocol.FRAME_BYTES];
        final ByteBuffer wire = ByteBuffer.wrap(bytes);
        final int bodyHash;
        long plannedAt, sentAt;
        boolean measured;

        Request(int actor, int slot) {
            bodyHash = CoreProtocol.initializeClientFrame(bytes, actor, slot);
        }

        void prepare(int actor, int sequence, long plannedAt, boolean measured) {
            this.plannedAt = plannedAt;
            this.measured = measured;
            sentAt = 0;
            CoreProtocol.writeRequest(bytes, actor, sequence, bodyHash);
            wire.clear();
        }
    }

    private static final class Peer {
        final int actor;
        final SocketChannel socket;
        final Request[] requests = new Request[WINDOW];
        final byte[] reply = new byte[CoreProtocol.FRAME_BYTES];
        final ByteBuffer input = ByteBuffer.wrap(reply);
        SelectionKey key;
        int queued, written, read;
        boolean measured;

        Peer(int actor, SocketChannel socket) {
            this.actor = actor;
            this.socket = socket;
            for (int slot = 0; slot < WINDOW; slot++) requests[slot] = new Request(actor, slot);
        }
    }

    private final Peer[] peers;
    private final int rate, warmupCount, scheduledCount;
    private final long warmupNanos, measureNanos;
    private final long[] plannedLatency, roundTrip, sendLag;
    private int samples, measuredClients, completedInWindow, drops, sentMeasured;
    private int pending, waiting, peakPending, peakWaiting;
    private int pendingAtEnd = -1, unscheduledAtEnd;
    private long began, measureStart, measureEnd, finishedAt;

    private PacedDriver(int clients, int warmupMs, int measureMs, int rate) {
        if (clients < 1 || warmupMs < 1 || measureMs < 1 || rate < 1)
            throw new IllegalArgumentException("positive clients, times and rate required");
        this.rate = rate;
        peers = new Peer[clients];
        warmupNanos = TimeUnit.MILLISECONDS.toNanos(warmupMs);
        measureNanos = TimeUnit.MILLISECONDS.toNanos(measureMs);
        warmupCount = Math.toIntExact((long) warmupMs * rate / 1000);
        scheduledCount = Math.toIntExact((long) measureMs * rate / 1000);
        if (scheduledCount < clients || (long) warmupCount + scheduledCount > 5_000_000)
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
                Peer peer = new Peer(opening + 1, socket);
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
        if (offered < warmupCount) return began + (long) offered * SECOND / rate;
        return measureStart + (long) (offered - warmupCount) * SECOND / rate;
    }

    private void offer(Peer peer, int ordinal, long plannedAt) throws IOException {
        boolean measured = ordinal >= warmupCount;
        if (peer.queued - peer.read == WINDOW) {
            if (measured) drops++;
            return;
        }
        peer.requests[peer.queued % WINDOW].prepare(peer.actor, peer.queued, plannedAt, measured);
        peer.queued++;
        waiting++;
        pending++;
        if (measured) {
            peakWaiting = Math.max(peakWaiting, waiting);
            peakPending = Math.max(peakPending, pending);
        }
        if (peer.queued - peer.written == 1) write(peer);
    }

    private void write(Peer peer) throws IOException {
        while (peer.written < peer.queued) {
            Request req = peer.requests[peer.written % WINDOW];
            long beforeWrite = req.wire.position() == 0 ? System.nanoTime() : 0;
            if (peer.socket.write(req.wire) == 0) break;
            if (beforeWrite != 0) req.sentAt = beforeWrite;
            if (req.wire.hasRemaining()) break;
            peer.written++;
            waiting--;
            if (req.measured) sentMeasured++;
        }
        peer.key.interestOps(SelectionKey.OP_READ | (peer.written < peer.queued ? SelectionKey.OP_WRITE : 0));
    }

    private void read(Peer peer) throws IOException {
        for (int frame = 0; frame < WINDOW; frame++) {
            int n = peer.socket.read(peer.input);
            if (n < 0) throw new IOException("Unexpected EOF for actor " + peer.actor);
            if (n == 0 || peer.input.hasRemaining()) return;
            long completed = System.nanoTime();
            if (peer.read >= peer.written) throw new IOException("Unexpected reply for actor " + peer.actor);
            Request req = peer.requests[peer.read % WINDOW];
            if (!CoreProtocol.validReply(req.bytes, peer.reply, peer.actor, peer.read, req.bodyHash)) {
                throw new IOException("Invalid reply for actor " + peer.actor + " sequence " + peer.read);
            }
            if (req.measured) {
                plannedLatency[samples] = completed - req.plannedAt;
                roundTrip[samples] = completed - req.sentAt;
                sendLag[samples++] = req.sentAt - req.plannedAt;
                if (completed < measureEnd) completedInWindow++;
                if (!peer.measured) {
                    peer.measured = true;
                    measuredClients++;
                }
            }
            peer.read++;
            pending--;
            peer.input.clear();
        }
    }

    private void work(Selector selector) throws IOException {
        began = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100);
        measureStart = began + warmupNanos;
        measureEnd = measureStart + measureNanos;
        final int total = warmupCount + scheduledCount;
        int offered = 0;
        long pollAt = began;
        while (offered < total || pending != 0) {
            long now = System.nanoTime();
            if (now >= measureEnd && pendingAtEnd < 0) {
                pendingAtEnd = pending;
                unscheduledAtEnd = total - Math.max(warmupCount, offered);
            }
            if (now >= measureEnd + 8 * SECOND)
                throw new IOException("Drain timed out, offered=" + offered + " pending=" + pending);
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
                || Arrays.stream(peers).anyMatch(peer -> peer.read != peer.queued || peer.written != peer.queued))
            throw new IOException("Frame accounting mismatch: accepted=" + sentMeasured + " valid=" + samples
                    + " dropped=" + drops + " scheduled=" + scheduledCount);
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
        if (args.length != 5)
            throw new IllegalArgumentException("PacedDriver <port> <clients> <warmupMs> <measureMs> <aggregateRate>");
        var address = new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0]));
        new PacedDriver(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                Integer.parseInt(args[3]), Integer.parseInt(args[4])).run(address);
    }
}
