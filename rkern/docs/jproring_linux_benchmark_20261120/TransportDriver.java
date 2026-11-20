import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class TransportDriver {
    public static void main(String[] args) throws Exception {
        var address = new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0]));
        int clients = Integer.parseInt(args[1]);
        boolean churn = args[2].equals("churn");
        long warmup = TimeUnit.MILLISECONDS.toNanos(Long.parseLong(args[3]));
        long measure = TimeUnit.MILLISECONDS.toNanos(Long.parseLong(args[4]));
        int payloadBytes = Integer.parseInt(args[5]);
        int window = Integer.parseInt(args[6]);
        boolean framed = Boolean.parseBoolean(args[7]);
        Path serverStat = Path.of("/proc", args[8], "stat");
        long ticksPerSecond = Long.parseLong(args[9]);
        var ready = new CountDownLatch(clients);
        var start = new CountDownLatch(1);
        var startTime = new AtomicLong();
        var workers = clients <= 256 ? Executors.newFixedThreadPool(clients) : Executors.newVirtualThreadPerTaskExecutor();
        try {
            var tasks = new java.util.ArrayList<Future<Result>>(clients);
            for (int i = 0; i < clients; i++) {
                final int client = i;
                tasks.add(workers.submit(() -> {
                    try (Socket persistent = churn ? null : connect(address)) {
                        ready.countDown();
                        start.await();
                        return drive(address, persistent, client, payloadBytes, window, framed,
                                startTime.get() + warmup, measure);
                    }
                }));
            }
            if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Client setup timed out");
            long began = System.nanoTime();
            startTime.set(began);
            start.countDown();
            waitUntil(began + warmup);
            long startCpu = cpuTicks(serverStat);
            waitUntil(began + warmup + measure);
            long measuredCpu = cpuTicks(serverStat) - startCpu;
            var replies = new Samples();
            var connects = new Samples();
            for (var task : tasks) {
                Result result = task.get(12, TimeUnit.SECONDS);
                replies.addAll(result.replies);
                connects.addAll(result.connects);
            }
            double rate = replies.size * 1e9 / measure;
            double cpuNsPerOperation = measuredCpu * (1e9 / ticksPerSecond) / replies.size;
            System.out.printf(Locale.ROOT, "RESULT %d %.3f %.3f %s %s %.3f%n",
                    replies.size, rate, rate / clients, replies.quantiles(), connects.quantiles(), cpuNsPerOperation);
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

    private static Result drive(InetSocketAddress address, Socket persistent, int client,
                                int payloadBytes, int window, boolean framed, long begin, long measure) throws IOException {
        var replies = new Samples();
        var connects = new Samples();
        int bodyStart = framed ? 2 : 0;
        int wireBytes = payloadBytes + bodyStart;
        byte[][] sent = new byte[window][wireBytes];
        for (int i = 0; i < sent.length; i++) {
            Arrays.fill(sent[i], (byte) (0x40 + client + i));
            if (framed) {
                sent[i][0] = (byte) wireBytes;
                sent[i][1] = (byte) (wireBytes >>> 8);
            }
        }
        byte[] received = new byte[wireBytes];
        long[] sentAt = new long[window];
        long end = begin + measure;
        DataInputStream in = persistent == null ? null : new DataInputStream(persistent.getInputStream());
        OutputStream out = persistent == null ? null : persistent.getOutputStream();
        // Each server port and churn worker gets its own loopback source IP.
        // The generator then has a separate ephemeral port range per worker.
        int sourceKey = address.getPort() * 257 + client + 2;
        var source = persistent == null ? new InetSocketAddress("127." + ((sourceKey >>> 16) & 255)
                + "." + ((sourceKey >>> 8) & 255) + "." + (sourceKey & 255), 0) : null;
        long started;
        while ((started = System.nanoTime()) < end) {
            if (persistent == null) {
                sent[0][bodyStart]++;
                long connectTime, completed;
                try (Socket socket = new Socket()) {
                    configure(socket);
                    socket.bind(source);
                    long dialing = System.nanoTime();
                    socket.connect(address, 3000);
                    connectTime = System.nanoTime() - dialing;
                    socket.getOutputStream().write(sent[0]);
                    expect(new DataInputStream(socket.getInputStream()), sent[0], received);
                    completed = System.nanoTime();
                }
                if (started >= begin && completed <= end) {
                    replies.add(completed - started);
                    connects.add(connectTime);
                }
            } else {
                for (int i = 0; i < window; i++) {
                    sent[i][bodyStart]++;
                    sentAt[i] = System.nanoTime();
                    out.write(sent[i]);
                }
                for (int i = 0; i < window; i++) {
                    expect(in, sent[i], received);
                    long completed = System.nanoTime();
                    if (sentAt[i] >= begin && completed <= end) replies.add(completed - sentAt[i]);
                }
            }
        }
        return new Result(replies, connects);
    }

    private static Socket connect(InetSocketAddress address) throws IOException {
        Socket socket = new Socket();
        try {
            configure(socket);
            socket.connect(address, 3000);
            return socket;
        } catch (IOException failure) {
            socket.close();
            throw failure;
        }
    }

    private static void configure(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(3000);
    }

    private static void expect(DataInputStream in, byte[] sent, byte[] received) throws IOException {
        in.readFully(received);
        if (!Arrays.equals(sent, received)) throw new IOException("Echo payload differs");
    }

    private record Result(Samples replies, Samples connects) { }

    private static final class Samples {
        private long[] values = new long[0];
        private int size;

        void add(long time) {
            reserve(1);
            values[size++] = time;
        }

        void addAll(Samples other) {
            reserve(other.size);
            System.arraycopy(other.values, 0, values, size, other.size);
            size += other.size;
        }

        private void reserve(int count) {
            if (size + count > values.length)
                values = Arrays.copyOf(values, Math.max(size + count, Math.max(64, values.length * 2)));
        }

        String quantiles() {
            if (size == 0) return "NA NA NA NA NA";
            Arrays.sort(values, 0, size);
            return String.format(Locale.ROOT, "%.3f %.3f %.3f %.3f %.3f", percentile(500), percentile(950),
                    percentile(990), percentile(999), percentile(1000));
        }

        private double percentile(int p) {
            int rank = (int) ((p * (long) size + 999) / 1000);
            return values[rank - 1] / 1000.0;
        }
    }
}
