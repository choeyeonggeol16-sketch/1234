import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Persistent, paced only by the closed-loop window of replies on each connection. */
public final class TransportDriver {
    public static void main(String[] args) throws Exception {
        if (args.length != 8)
            throw new IllegalArgumentException("TransportDriver <port> <clients> <warmupMs> <measureMs> <payloadBytes> <window> <serverPid> <ticks/s>");
        var address = new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0]));
        int clients = Integer.parseInt(args[1]);
        long warmup = TimeUnit.MILLISECONDS.toNanos(Long.parseLong(args[2]));
        long measure = TimeUnit.MILLISECONDS.toNanos(Long.parseLong(args[3]));
        int payloadBytes = Integer.parseInt(args[4]);
        int window = Integer.parseInt(args[5]);
        Path serverStat = Path.of("/proc", args[6], "stat");
        long ticksPerSecond = Long.parseLong(args[7]);
        if (clients < 1 || warmup < 1 || measure < 1 || payloadBytes < 1 || payloadBytes > 65533
                || window < 1 || (payloadBytes + 2L) * window > 65_536 || ticksPerSecond < 1)
            throw new IllegalArgumentException("Invalid workload");

        var ready = new CountDownLatch(clients);
        var start = new CountDownLatch(1);
        var startTime = new AtomicLong();
        var setupFailure = new AtomicReference<Throwable>();
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var sockets = new ArrayList<Future<Samples>>(clients);
            for (int i = 0; i < clients; i++) {
                final int client = i;
                sockets.add(workers.submit(() -> {
                    final Socket socket;
                    try {
                        socket = connect(address);
                    } catch (IOException | RuntimeException failure) {
                        setupFailure.compareAndSet(null, failure);
                        throw failure;
                    } finally {
                        ready.countDown();
                    }
                    try (socket) {
                        start.await();
                        return drive(socket, client, payloadBytes + 2, window, startTime.get() + warmup, measure);
                    }
                }));
            }
            if (!ready.await(25, TimeUnit.SECONDS)) throw new IOException("Client connection setup timed out");
            if (setupFailure.get() != null) throw new IOException("Could not connect every client", setupFailure.get());

            final long began = System.nanoTime();
            startTime.set(began);
            start.countDown();
            waitUntil(began + warmup);
            final long startCpu = cpuTicks(serverStat);
            System.out.println("MEASURING " + clients);
            waitUntil(began + warmup + measure);
            final long measuredCpu = cpuTicks(serverStat) - startCpu;

            var replies = new Samples();
            int measuredClients = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(16);
            for (var task : sockets) {
                Samples own = task.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (own.size > 0) measuredClients++;
                replies.addAll(own);
            }
            if (replies.size == 0)
                throw new IOException("No completed echo samples in the measurement interval");
            double rate = replies.size * 1e9 / measure;
            double cpuNsPerOperation = measuredCpu * (1e9 / ticksPerSecond) / replies.size;
            System.out.printf(Locale.ROOT, "RESULT %d %d %.3f %.3f %s %.3f%n",
                    replies.size, measuredClients, rate, rate / clients, replies.quantiles(), cpuNsPerOperation);
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private static long cpuTicks(Path status) throws IOException {
        String line = Files.readString(status);
        String[] stat = line.substring(line.lastIndexOf(')') + 2).trim().split(" +");
        return Long.parseLong(stat[11]) + Long.parseLong(stat[12]);
    }

    private static void waitUntil(long deadline) throws InterruptedException {
        long delay;
        while ((delay = deadline - System.nanoTime()) > 0) TimeUnit.NANOSECONDS.sleep(delay);
    }

    private static Samples drive(Socket socket, int client, int wireBytes, int window, long begin, long measure)
            throws IOException {
        var replies = new Samples();
        byte[][] sent = new byte[window][wireBytes];
        for (int i = 0; i < sent.length; i++) {
            Arrays.fill(sent[i], (byte) (0x40 + client + i));
            sent[i][0] = (byte) wireBytes;
            sent[i][1] = (byte) (wireBytes >>> 8);
        }
        byte[] received = new byte[wireBytes];
        long[] sentAt = new long[window];
        long end = begin + measure;
        var in = new DataInputStream(socket.getInputStream());
        var out = socket.getOutputStream();
        while (System.nanoTime() < end) {
            for (int i = 0; i < window; i++) {
                sent[i][2]++;
                sentAt[i] = System.nanoTime();
                out.write(sent[i]);
            }
            for (int i = 0; i < window; i++) {
                in.readFully(received);
                if (!Arrays.equals(sent[i], received))
                    throw new IOException("Echo frame or payload differs for client " + client);
                long completed = System.nanoTime();
                if (sentAt[i] >= begin && completed <= end) replies.add(completed - sentAt[i]);
            }
        }
        return replies;
    }

    private static Socket connect(InetSocketAddress address) throws IOException {
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(8_000);
            socket.connect(address, 8_000);
            return socket;
        } catch (IOException | RuntimeException failure) {
            socket.close();
            throw failure;
        }
    }

    // 128 subdivisions per power of two. Only touched blocks are allocated.
    // The upper-bound percentile error is less than 1/128 of its true value;
    // counts and maximum latency remain exact, regardless of sample count.
    private static final class Samples {
        private static final int SHIFT = 7;
        private static final int WIDTH = 1 << SHIFT;
        private static final int MASK = WIDTH - 1;
        private static final int BLOCKS = 64 - SHIFT;
        private long[][] blocks;
        private long size;
        private long max;

        void add(long time) {
            int bucket = bucket(time);
            block(bucket >>> SHIFT)[bucket & MASK]++;
            size++;
            max = Math.max(max, time);
        }

        void addAll(Samples other) {
            if (other.size == 0) return;
            for (int i = 0; i < BLOCKS; i++) {
                long[] source = other.blocks[i];
                if (source == null) continue;
                long[] target = block(i);
                for (int j = 0; j < WIDTH; j++) target[j] += source[j];
            }
            size += other.size;
            max = Math.max(max, other.max);
        }

        private long[] block(int index) {
            if (blocks == null) blocks = new long[BLOCKS][];
            long[] block = blocks[index];
            if (block == null) blocks[index] = block = new long[WIDTH];
            return block;
        }

        private static int bucket(long time) {
            if (time < 0) throw new IllegalArgumentException("negative latency");
            int shift = 63 - Long.numberOfLeadingZeros(time) - SHIFT;
            return shift <= 0 ? (int) time : (shift << SHIFT) + (int) (time >>> shift);
        }

        private static long upperBound(int bucket) {
            if (bucket < 2 * WIDTH) return bucket;
            int shift = (bucket >>> SHIFT) - 1;
            return ((long) (WIDTH + (bucket & MASK)) << shift) | ((1L << shift) - 1);
        }

        String quantiles() {
            if (size == 0) return "NA NA NA NA NA";
            return String.format(Locale.ROOT, "%.3f %.3f %.3f %.3f %.3f", percentile(500), percentile(950),
                    percentile(990), percentile(999), percentile(1000));
        }

        private double percentile(int p) {
            if (p == 1000) return max / 1000.0;
            long rank = (size / 1000) * p + ((size % 1000) * p + 999) / 1000;
            for (int i = 0; i < BLOCKS; i++) {
                long[] block = blocks[i];
                if (block == null) continue;
                for (int j = 0; j < WIDTH; j++) {
                    rank -= block[j];
                    if (rank <= 0) return Math.min(max, upperBound((i << SHIFT) + j)) / 1000.0;
                }
            }
            throw new IllegalStateException("Histogram count mismatch");
        }
    }
}
