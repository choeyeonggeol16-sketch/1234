import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import l1j.server.server.model.network.core.transport.selector.SelectorTransport;

public class RKernMailboxMeasure {
    public static void main(String[] args) throws Exception {
        System.out.println("producers,trial,ns_per_command,allocated_bytes_per_command");
        var mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (int producers : new int[] {1, 2, 4, 8, 20}) {
            run(mx, producers, 0);
            for (int i = 1; i <= 3; i++) run(mx, producers, i);
        }
    }

    private static void run(ThreadMXBean mx, int producers, int trial) throws Exception {
        int count = 4_000;
        var ready = new CountDownLatch(producers);
        var go = new CountDownLatch(1);
        var end = new CountDownLatch(count * producers);
        var open = new LinkedBlockingQueue<SelectorTransport.Connection>();
        var settings = new SelectorTransport.Config(1, 2, 16, 512, 1, 1, 1, 256, 8, 0, Duration.ofSeconds(5));
        try (var transport = SelectorTransport.bind(new InetSocketAddress("127.0.0.1", 0), settings,
                new SelectorTransport.RawHandler() {
                    @Override public void onOpen(SelectorTransport.Connection peer) { open.add(peer); }
                });
                var socket = new Socket("127.0.0.1", transport.localAddress().getPort());
                var executor = Executors.newFixedThreadPool(producers)) {
            var connection = open.poll(5, TimeUnit.SECONDS);
            var jobs = new ArrayList<Future<Long>>();
            SelectorTransport.OwnerCommand command = ignored -> end.countDown();
            for (int i = 0; i < producers; ++i) jobs.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                long before = mx.getCurrentThreadAllocatedBytes();
                for (int n = 0; n < count; ++n) {
                    while (!connection.tryExecute(command)) Thread.yield();
                }
                return mx.getCurrentThreadAllocatedBytes() - before;
            }));
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("ready");
            long start = System.nanoTime();
            go.countDown();
            if (!end.await(10, TimeUnit.SECONDS)) throw new AssertionError("drain");
            long elapsed = System.nanoTime() - start;
            long allocated = 0;
            for (var job : jobs) allocated += job.get(5, TimeUnit.SECONDS);
            if (trial != 0) System.out.printf(Locale.ROOT, "%d,%d,%.2f,%.2f%n", producers, trial,
                    (double) elapsed / (count * producers), (double) allocated / (count * producers));
        }
    }
}
