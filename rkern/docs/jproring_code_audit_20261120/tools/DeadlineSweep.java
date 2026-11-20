import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;

public class DeadlineSweep {
    private static final int SIZE = 5000, ROUNDS = 8, TRIALS = 5;
    private static final Runnable NOT_DUE = () -> { throw new AssertionError("timer should stay scheduled"); };
    public static void main(String[] args) throws Exception {
        var opened = new LinkedBlockingQueue<CSelectorTransport.Connection>();
        var handler = new CSelectorTransport.RawHandler() {
            @Override public void onOpen(CSelectorTransport.Connection socket) {
                socket.scheduleDeadline(900_000, NOT_DUE);
                opened.add(socket);
            }
        };
        var config = new CSelectorTransport.Config(1, SIZE, 128, 64, 1, 1, 1, 3, 1, 0, Duration.ofSeconds(5));
        var sockets = new ArrayList<Socket>(SIZE);
        var connections = new ArrayList<CSelectorTransport.Connection>(SIZE);
        var address = new InetSocketAddress("127.0.0.1", 0);
        try (var server = CSelectorTransport.bind(address, config, handler)) {
            for (int i = 0; i < SIZE; i++) {
                var socket = new Socket();
                sockets.add(socket);
                socket.connect(server.localAddress(), 3000);
                var connection = opened.poll(3, TimeUnit.SECONDS);
                if (connection == null) throw new AssertionError("missing connection");
                connections.add(connection);
            }
            var sample = new ArrayBlockingQueue<long[]>(1);
            if (!connections.getFirst().tryExecute(socket -> {
                int sweep = 0;
                var times = new long[TRIALS];
                for (int trial = -2; trial < TRIALS; trial++) {
                    long start = System.nanoTime();
                    for (int r = 0; r < ROUNDS; r++, sweep++) {
                        long delay = 800_000L - 1_000L * sweep;
                        for (int i = 0; i < SIZE; i++) {
                            if (!connections.get((i * 1553 + 397 * sweep) % SIZE).scheduleDeadline(delay, NOT_DUE))
                                throw new AssertionError("reschedule rejected");
                        }
                    }
                    if (trial >= 0) times[trial] = System.nanoTime() - start;
                }
                sample.add(times);
            })) throw new AssertionError("owner submission rejected");
            long[] results = sample.poll(20, TimeUnit.SECONDS);
            if (results == null) throw new AssertionError("timer sweep timed out");
            Arrays.sort(results);
            System.out.printf(Locale.ROOT, "connections=%d reschedules/trial=%d ns/op median=%.1f range=%.1f–%.1f%n",
                    SIZE, SIZE * ROUNDS, results[2] / (double) (SIZE * ROUNDS),
                    results[0] / (double) (SIZE * ROUNDS), results[4] / (double) (SIZE * ROUNDS));
            server.close();
            if (server.ownerDiagnostics().getFirst().deadlines() != 0) throw new AssertionError("timer retained");
        } finally {
            for (var socket : sockets) socket.close();
        }
    }
}
