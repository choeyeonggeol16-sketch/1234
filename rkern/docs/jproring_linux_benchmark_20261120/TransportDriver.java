import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class TransportDriver {
    private static final int PAYLOAD_BYTES = 64;

    public static void main(String[] args) throws Exception {
        var address = new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0]));
        int clients = Integer.parseInt(args[1]);
        boolean churn = args[2].equals("churn");
        long warmup = TimeUnit.MILLISECONDS.toNanos(Long.parseLong(args[3]));
        long measure = TimeUnit.MILLISECONDS.toNanos(Long.parseLong(args[4]));
        var ready = new CountDownLatch(clients);
        var start = new CountDownLatch(1);
        var startTime = new AtomicLong();
        var workers = Executors.newFixedThreadPool(clients);
        try {
            var tasks = new java.util.ArrayList<Future<Result>>(clients);
            for (int i = 0; i < clients; i++) {
                final int client = i;
                tasks.add(workers.submit(() -> {
                    try (Socket persistent = churn ? null : connect(address)) {
                        ready.countDown();
                        start.await();
                        return drive(address, persistent, client, startTime.get() + warmup, measure);
                    }
                }));
            }
            if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Client setup timed out");
            startTime.set(System.nanoTime());
            start.countDown();
            var replies = new Samples();
            var connects = new Samples();
            for (var task : tasks) {
                Result result = task.get(12, TimeUnit.SECONDS);
                replies.addAll(result.replies);
                connects.addAll(result.connects);
            }
            double rate = replies.size * 1e9 / measure;
            System.out.printf(Locale.ROOT, "RESULT %d %.3f %.3f %s %s%n",
                    replies.size, rate, rate / clients, replies.quantiles(), connects.quantiles());
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private static Result drive(InetSocketAddress address, Socket persistent, int client,
                                long begin, long measure) throws IOException {
        var replies = new Samples();
        var connects = new Samples();
        byte[] sent = new byte[PAYLOAD_BYTES];
        byte[] received = new byte[PAYLOAD_BYTES];
        Arrays.fill(sent, (byte) (0x40 + client));
        long end = begin + measure;
        DataInputStream in = persistent == null ? null : new DataInputStream(persistent.getInputStream());
        OutputStream out = persistent == null ? null : persistent.getOutputStream();
        // Each server port and churn worker gets its own loopback source IP.
        // The generator then has a separate ephemeral port range per worker.
        var source = persistent == null ? new InetSocketAddress(
                "127." + (address.getPort() >>> 8) + "." + (address.getPort() & 255) + "." + (client + 2), 0) : null;
        long started;
        while ((started = System.nanoTime()) < end) {
            sent[0]++;
            long connectTime = 0, completed;
            if (persistent == null) {
                try (Socket socket = new Socket()) {
                    configure(socket);
                    socket.bind(source);
                    long dialing = System.nanoTime();
                    socket.connect(address, 3000);
                    connectTime = System.nanoTime() - dialing;
                    echo(new DataInputStream(socket.getInputStream()), socket.getOutputStream(), sent, received);
                    completed = System.nanoTime();
                }
            } else {
                echo(in, out, sent, received);
                completed = System.nanoTime();
            }
            if (started >= begin && completed <= end) {
                replies.add(completed - started);
                if (persistent == null) connects.add(connectTime);
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

    private static void echo(DataInputStream in, OutputStream out, byte[] sent, byte[] received) throws IOException {
        out.write(sent);
        in.readFully(received);
        if (!Arrays.equals(sent, received)) throw new IOException("Echo payload differs");
    }

    private record Result(Samples replies, Samples connects) { }

    private static final class Samples {
        private long[] values = new long[4096];
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
            if (size + count > values.length) values = Arrays.copyOf(values, Math.max(size + count, values.length * 2));
        }

        String quantiles() {
            if (size == 0) return "NA NA NA";
            Arrays.sort(values, 0, size);
            return String.format(Locale.ROOT, "%.3f %.3f %.3f", percentile(50), percentile(95), percentile(99));
        }

        private double percentile(int p) {
            int rank = (int) ((p * (long) size + 99) / 100);
            return values[rank - 1] / 1000.0;
        }
    }
}
