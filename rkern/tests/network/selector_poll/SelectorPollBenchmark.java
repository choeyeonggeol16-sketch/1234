import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.packet.CBytesPacket;
import l1j.server.server.model.network.packet.UserPacketCallbackMap;
import l1j.server.server.model.network.packet.IPacketWriter;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/** Production CIO and encrypted reference clients in separate Java 21 processes. */
public final class SelectorPollBenchmark {
    private static final int REQUEST = Opcodes.C_ALIVE & 255;
    private static final int RESPONSE = 0x45;

    public static void main(String[] args) throws Exception {
        if (args[0].equals("server")) serve(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                Integer.parseInt(args[3]), args[4]);
        else if (args[0].equals("client-rate")) driveRate(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]),
                Integer.parseInt(args[6]), Integer.parseInt(args[7]), Integer.parseInt(args[8]),
                args.length > 9 ? Integer.parseInt(args[9]) : 2_000);
        else drive(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]),
                Integer.parseInt(args[7]), Integer.parseInt(args[8]));
    }

    private static void serve(int clients, int owners, int payload, String packetKind) throws Exception {
        PrintStream protocol = System.out;
        System.setOut(System.err);
        Files.createDirectory(Path.of("config"));
        Files.writeString(Path.of("config/Network.ini"), "DisruptorShardCount=" + owners
                + "\nNetworkClientPermission=" + (clients + 64)
                + "\nMaxPacketsPerCompletion=32\nPacketLimitPerSec=1000000\nDropPackets=false\nUsePreLoginTimeout=false\n");
        Config.MAX_ONLINE_USERS = (short) (clients + 64);
        CPacketFilter.getInstance().load();
        var address = new InetSocketAddress("127.0.0.1", 0);
        var config = new CIOConfig(address);
        var scheduler = CIOThreadCallback.getInstance();
        int shardCount = scheduler.bindNetworkIngress(config);
        int maximum = Config.MAX_ONLINE_USERS;
        int capacity = maximum + Math.max(256, maximum >>> 3);
        var pool = new CIOBufferPool(capacity, capacity * 3, 256, 512, shardCount);
        var packets = ThreadLocal.withInitial(() -> new Reply(payload));
        var rejects = new java.util.concurrent.atomic.AtomicLong();
        var previous = UserPacketCallbackMap.gPacketTableFromClient[REQUEST];
        UserPacketCallbackMap.gPacketTableFromClient[REQUEST] = (request, socket) -> {
            check(socket.affinityShard() == CIOThreadCallback.currentShardIndex(), "packet owner affinity");
            int sequence = request.readInt();
            check(request.remaining() == payload - 5, "expected symmetric request size");
            Reply packet = packets.get();
            body(packet.body, RESPONSE, sequence);
            boolean bytes = packetKind.equals("bytes") || (packetKind.equals("mixed") && (sequence & 1) == 0);
            if (socket.tryWritePacket(socket, bytes ? new CBytesPacket(packet.body) : packet)) return true;
            rejects.incrementAndGet();
            return false;
        };
        try (var server = new CIOServer(address, capacity, shardCount, Math.min(capacity, 1024), pool, scheduler, config)) {
            var timers = new TimerProbe[shardCount];
            for (int i = 0; i < timers.length; i++) {
                timers[i] = new TimerProbe(scheduler, i);
                timers[i].schedule();
            }
            var cpu = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            if (threads.isThreadCpuTimeSupported() && !threads.isThreadCpuTimeEnabled())
                threads.setThreadCpuTimeEnabled(true);
            if (threads.isThreadAllocatedMemorySupported() && !threads.isThreadAllocatedMemoryEnabled())
                threads.setThreadAllocatedMemoryEnabled(true);
            var input = new BufferedReader(new InputStreamReader(System.in));
            protocol.println("READY " + server.localAddress().getPort());
            int[] timerStarts = null;
            for (String command; (command = input.readLine()) != null;) {
                if (command.equals("STOP")) break;
                else if (command.equals("POOL")) printPool(protocol, server);
                else if (command.equals("POLLS")) printPolls(protocol, server);
                else if (command.equals("SAMPLE")) {
                    long ownerCpu = 0, ownerBytes = 0;
                    for (TimerProbe timer : timers) {
                        long id = timer.threadId;
                        check(id != 0, "timer ran on each owner before measurement");
                        ownerCpu += threads.isThreadCpuTimeSupported() ? threads.getThreadCpuTime(id) : 0;
                        ownerBytes += threads.isThreadAllocatedMemorySupported() ? threads.getThreadAllocatedBytes(id) : 0;
                    }
                    long allocated = threads.isThreadAllocatedMemorySupported() ? threads.getTotalThreadAllocatedBytes() : 0;
                    protocol.printf(Locale.ROOT, "SAMPLE %d %d %d %d %d%n", cpu.getProcessCpuTime(), ownerCpu,
                            allocated, ownerBytes, rejects.get());
                    if (timerStarts == null) timerStarts = Arrays.stream(timers).mapToInt(t -> t.count).toArray();
                    else printTimers(protocol, timers, timerStarts);
                } else throw new IllegalArgumentException(command);
            }
            for (TimerProbe timer : timers) timer.cancelled = true;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!drained(server) && System.nanoTime() < deadline) Thread.sleep(5);
            check(drained(server) && rejects.get() == 0, "all connections and leases drained; no rejected sends");
            check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "no JVM deadlock");
            protocol.println("DRAINED");
        } finally {
            UserPacketCallbackMap.gPacketTableFromClient[REQUEST] = previous;
            CSessionLifecycle.stopAccepting();
            scheduler.shutdown();
            check(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "session releases drained");
            check(scheduler.awaitTermination(5, TimeUnit.SECONDS), "CIO owners joined");
        }
    }

    private static void printPool(PrintStream output, CIOServer server) {
        long used = 0, bytes = 0;
        for (long[] bucket : server.poolSnapshot()) {
            used += bucket[2];
            bytes += bucket.length >= 7 ? bucket[5] : bucket[3];
        }
        output.printf(Locale.ROOT, "POOL %d %d %d %d%n", server.accountRegistryDiagnostics()[0], used, bytes,
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    }

    private static boolean drained(CIOServer server) {
        return server.accountRegistryDiagnostics()[0] == 0 && Arrays.stream(server.poolSnapshot())
                .allMatch(bucket -> bucket[2] == 0 && (bucket.length < 7 || bucket[3] == bucket[4]));
    }

    private static void printPolls(PrintStream output, CIOServer server) throws ReflectiveOperationException {
        var field = CIOServer.class.getDeclaredField("transport");
        field.setAccessible(true);
        Object transport = field.get(server);
        try {
            output.println("POLLS " + transport.getClass().getMethod("selectorProbeCounters").invoke(transport));
        } catch (NoSuchMethodException unavailable) {
            output.println("POLLS unavailable");
        }
    }

    private static void printTimers(PrintStream output, TimerProbe[] timers, int[] start) {
        int minimum = Integer.MAX_VALUE;
        long[] samples = new long[0];
        for (int i = 0; i < timers.length; i++) {
            TimerProbe timer = timers[i];
            int end = timer.count;
            int previous = samples.length;
            samples = Arrays.copyOf(samples, previous + end - start[i]);
            System.arraycopy(timer.lateness, start[i], samples, previous, end - start[i]);
            minimum = Math.min(minimum, end - start[i]);
        }
        Arrays.sort(samples);
        check(minimum > 0 && samples.length > 0, "every CIO timer progressed under traffic");
        output.printf(Locale.ROOT, "TIMERS ticks=%d min_owner_ticks=%d p99_us=%.3f max_us=%.3f%n",
                samples.length, minimum, percentile(samples, 990), samples[samples.length - 1] / 1000.0);
    }

    private static final class TimerProbe implements CIOThreadCallback.WheelTask {
        private static final long INTERVAL_MS = 20;
        private final CIOThreadCallback scheduler;
        private final int owner;
        private final long[] lateness = new long[32_768];
        private long due;
        volatile int count;
        volatile long threadId;
        volatile boolean cancelled;

        TimerProbe(CIOThreadCallback scheduler, int owner) {
            this.scheduler = scheduler;
            this.owner = owner;
        }

        void schedule() {
            due = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(INTERVAL_MS);
            check(scheduler.scheduleNetworkTimeout(owner, this, INTERVAL_MS), "timer accepted");
        }

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void run() {
            check(CIOThreadCallback.currentShardIndex() == owner, "timer owner affinity");
            if (threadId == 0) threadId = Thread.currentThread().threadId();
            if (count < lateness.length) {
                lateness[count] = Math.max(0L, System.nanoTime() - due);
                count++;
            }
            if (!cancelled) schedule();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void drive(int port, int clients, int window, int payload, int warmupMillis, int measureMillis,
            int activeClients, int paceMillis)
            throws Exception {
        var ready = new CountDownLatch(clients);
        var go = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var opening = new Semaphore(Math.min(64, clients));
        var begin = new AtomicLong();
        long measure = TimeUnit.MILLISECONDS.toNanos(measureMillis);
        try (var executor = clients <= 256 ? Executors.newFixedThreadPool(clients)
                                          : Executors.newVirtualThreadPerTaskExecutor()) {
            var samples = new ArrayList<Future<long[]>>();
            for (int connection = 0; connection < clients; connection++) {
                boolean active = connection < activeClients;
                samples.add(executor.submit(() -> {
                    try (var socket = new Socket()) {
                        DataInputStream in;
                        opening.acquire();
                        try {
                            socket.setTcpNoDelay(true);
                            socket.setSoLinger(true, 0);
                            socket.setSoTimeout(clients > 256 ? 15000 : 5000);
                            socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
                            in = new DataInputStream(socket.getInputStream());
                            if (!Arrays.equals(read(in), Opcodes.S_KEYBYTES)) throw new AssertionError("initial key");
                        } finally { opening.release(); }
                        ready.countDown();
                        if (!active) {
                            finished.await();
                            return new long[0];
                        }
                        var out = socket.getOutputStream();
                        var codec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
                        var request = new byte[5];
                        var expected = new byte[payload];
                        int frame = payload + 6;
                        var batch = new byte[window * frame];
                        go.await();
                        long from = begin.get(), to = from + measure;
                        long[] measured = new long[512];
                        int count = 0, sequence = 0;
                        while (System.nanoTime() < to) {
                            for (int i = 0; i < window; i++) {
                                body(request, REQUEST, sequence + i);
                                byte[] encoded = codec.toServer(request);
                                int offset = i * frame;
                                batch[offset] = (byte) frame;
                                batch[offset + 1] = (byte) (frame >>> 8);
                                System.arraycopy(encoded, 0, batch, offset + 2, encoded.length);
                            }
                            long sent = System.nanoTime();
                            out.write(batch);
                            for (int i = 0; i < window; i++) {
                                body(expected, RESPONSE, sequence++);
                                if (!Arrays.equals(read(in), codec.fromServer(expected))) throw new AssertionError("wire mismatch");
                                long received = System.nanoTime();
                                if (sent >= from && received <= to) {
                                    if (count == measured.length) measured = Arrays.copyOf(measured, count * 2);
                                    measured[count++] = received - sent;
                                }
                            }
                            if (paceMillis != 0) Thread.sleep(paceMillis);
                        }
                        return Arrays.copyOf(measured, count);
                    }
                }));
            }
            try {
                if (!ready.await(clients > 256 ? 35 : 6, TimeUnit.SECONDS)) {
                    for (var sample : samples) if (sample.isDone()) sample.get();
                    throw new AssertionError("client connect timeout, missing initial keys=" + ready.getCount());
                }
                System.out.println("READY");
                if (!"".equals(new BufferedReader(new InputStreamReader(System.in)).readLine()))
                    throw new AssertionError("expected start");
                begin.set(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(warmupMillis));
                go.countDown();
                waitUntil(begin.get());
                System.out.println("MEASURE");
                waitUntil(begin.get() + measure);
                System.out.println("END");
                finished.countDown();
                var all = new ArrayList<long[]>();
                int count = 0, min = Integer.MAX_VALUE, max = 0, progressed = 0;
                for (var sample : samples) {
                    long[] result = sample.get(8, TimeUnit.SECONDS);
                    count += result.length;
                    if (all.size() < activeClients) {
                        min = Math.min(min, result.length);
                        max = Math.max(max, result.length);
                        if (result.length != 0) progressed++;
                    }
                    all.add(result);
                }
                if (count == 0) throw new AssertionError("no samples");
                long[] sorted = new long[count];
                int offset = 0;
                for (long[] result : all) {
                    System.arraycopy(result, 0, sorted, offset, result.length);
                    offset += result.length;
                }
                Arrays.sort(sorted);
                printProgress(activeClients, progressed, min, max);
                System.out.printf(Locale.ROOT, "RESULT %d %.2f %.3f %.3f %.3f %.3f%n", count, count * 1e9 / measure,
                        percentile(sorted, 500), percentile(sorted, 950), percentile(sorted, 990), percentile(sorted, 999));
            } finally {
                go.countDown();
                finished.countDown();
                executor.shutdownNow();
            }
        }
    }

    private static final long SECOND_NS = TimeUnit.SECONDS.toNanos(1);

    private static void driveRate(int port, int clients, int window, int payload, int warmupMs,
            int measureMs, int active, int rate, int drainMs) throws Exception {
        if (clients <= 0 || active <= 0 || active > clients || window < 1 || window > 512
                || payload < 5 || payload > 65533 || rate <= 0 || rate > 1_000_000
                || warmupMs < 0 || measureMs <= 0 || (long) warmupMs + measureMs > 300_000
                || drainMs <= 0 || drainMs > 15_000) throw new IllegalArgumentException("invalid rate test settings");
        final int burst = Integer.getInteger("rkern.test.fixedBurst", 1);
        if (burst < 1 || burst > window || rate % burst != 0) throw new IllegalArgumentException("fixed burst");
        final int slotsPerSecond = rate / burst;
        final long firstMeasured = ((long) warmupMs * slotsPerSecond + 999) / 1000;
        final long totalSlots = ((long) (warmupMs + measureMs) * slotsPerSecond + 999) / 1000;
        final long offered = (totalSlots - firstMeasured) * burst;
        if (offered == 0) throw new IllegalArgumentException("no scheduled measurement requests");
        var ready = new CountDownLatch(clients);
        var go = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var opening = new Semaphore(Math.min(64, clients));
        var startAt = new AtomicLong();
        long warmupNs = TimeUnit.MILLISECONDS.toNanos(warmupMs);
        long measureNs = TimeUnit.MILLISECONDS.toNanos(measureMs);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var jobs = new ArrayList<Future<RateSamples>>(clients);
            for (int index = 0; index < clients; index++) {
                final int lane = index;
                jobs.add(executor.submit(() -> {
                    try (var socket = new Socket()) {
                        final DataInputStream in;
                        opening.acquire();
                        try {
                            socket.setTcpNoDelay(true);
                            socket.setSoLinger(true, 0);
                            socket.setSoTimeout(15_000);
                            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
                            in = new DataInputStream(socket.getInputStream());
                            if (!Arrays.equals(read(in), Opcodes.S_KEYBYTES)) throw new AssertionError("initial key");
                        } finally { opening.release(); }
                        ready.countDown();
                        if (lane >= active) {
                            finished.await();
                            return null;
                        }
                        go.await();
                        long start = startAt.get();
                        long end = start + warmupNs + measureNs;
                        return driveRatePeer(socket, in, window, payload, lane, active, slotsPerSecond, burst, start,
                                firstMeasured, totalSlots, end, end + TimeUnit.MILLISECONDS.toNanos(drainMs));
                    }
                }));
            }
            try {
                if (!ready.await(35, TimeUnit.SECONDS)) {
                    for (var job : jobs) if (job.isDone()) job.get();
                    throw new AssertionError("client connect timeout, missing initial keys=" + ready.getCount());
                }
                System.out.println("READY");
                if (!"".equals(new BufferedReader(new InputStreamReader(System.in)).readLine()))
                    throw new AssertionError("expected start");
                long start = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(20);
                startAt.set(start);
                go.countDown();
                waitUntil(start + warmupNs);
                System.out.println("MEASURE");
                waitUntil(start + warmupNs + measureNs);
                System.out.println("END");
                long attempted = 0, windowMisses = 0, failures = 0, onTime = 0;
                var latencies = new long[0];
                var rtts = new long[0];
                int count = 0, min = Integer.MAX_VALUE, max = 0, progressed = 0;
                for (int i = 0; i < active; i++) {
                    RateSamples peer = jobs.get(i).get(drainMs + 3_000L, TimeUnit.MILLISECONDS);
                    attempted += peer.attempted;
                    windowMisses += peer.windowMisses;
                    failures += peer.failures;
                    onTime += peer.onTime;
                    if (count + peer.count > latencies.length) {
                        int capacity = Math.max(count + peer.count, Math.max(64, latencies.length * 2));
                        latencies = Arrays.copyOf(latencies, capacity);
                        rtts = Arrays.copyOf(rtts, capacity);
                    }
                    System.arraycopy(peer.latencies, 0, latencies, count, peer.count);
                    System.arraycopy(peer.rtts, 0, rtts, count, peer.count);
                    min = Math.min(min, peer.count);
                    max = Math.max(max, peer.count);
                    if (peer.count != 0) progressed++;
                    count += peer.count;
                }
                Arrays.sort(latencies, 0, count);
                Arrays.sort(rtts, 0, count);
                long scheduleMisses = offered - attempted - windowMisses;
                long incomplete = attempted - count;
                if (scheduleMisses < 0 || incomplete < 0 || count < onTime)
                    throw new AssertionError("incorrect fixed-rate accounting");
                double p50 = ratePercentile(latencies, count, 500), p95 = ratePercentile(latencies, count, 950);
                double p99 = ratePercentile(latencies, count, 990), p999 = ratePercentile(latencies, count, 999);
                double withinWindowRps = onTime * 1e9 / measureNs;
                System.out.printf(Locale.ROOT, "RATE_RESULT offered=%d attempted=%d completed=%d received_by_end=%d "
                        + "received_during_drain=%d missed_window=%d missed_schedule=%d incomplete=%d io_errors=%d "
                        + "timely_rps=%.2f scheduled_p50_us=%.3f scheduled_p95_us=%.3f scheduled_p99_us=%.3f "
                        + "scheduled_p999_us=%.3f%n", offered, attempted, count, onTime, count - onTime,
                        windowMisses, scheduleMisses, incomplete, failures, withinWindowRps, p50, p95, p99, p999);
                printProgress(active, progressed, min, max);
                System.out.printf(Locale.ROOT, "RESULT %d %.2f %.3f %.3f %.3f %.3f%n", onTime, withinWindowRps,
                        ratePercentile(rtts, count, 500), ratePercentile(rtts, count, 950),
                        ratePercentile(rtts, count, 990), ratePercentile(rtts, count, 999));
            } finally {
                finished.countDown();
                go.countDown();
                executor.shutdownNow();
            }
        }
    }

    private static void printProgress(int active, int progressed, int min, int max) {
        System.out.printf(Locale.ROOT, "PROGRESS active=%d responded=%d min_replies=%d max_replies=%d%n",
                active, progressed, min, max);
    }

    private static double ratePercentile(long[] latencies, int count, int rank) {
        return count == 0 ? Double.NaN : latencies[(int) ((count * (long) rank + 999) / 1000) - 1] / 1000.0;
    }

    private static final class RateSamples {
        // The semaphore handoff publishes deadlines; only the writer touches sequence allocation.
        final Semaphore writable;
        final Semaphore readable = new Semaphore(0);
        final long[] deadlines;
        final long[] sends;
        final boolean[] measured;
        long[] latencies = new long[32];
        long[] rtts = new long[32];
        volatile int issued;
        volatile boolean done;
        int attempted, windowMisses, sendFailures, receiveFailures, onTime, count;
        int failures;

        RateSamples(int window) {
            writable = new Semaphore(window);
            deadlines = new long[window];
            sends = new long[window];
            measured = new boolean[window];
        }

        void record(long latency, long rtt, boolean withinWindow) {
            if (count == latencies.length) {
                latencies = Arrays.copyOf(latencies, count * 2);
                rtts = Arrays.copyOf(rtts, count * 2);
            }
            rtts[count] = rtt;
            latencies[count++] = latency;
            if (withinWindow) onTime++;
        }
    }

    private static RateSamples driveRatePeer(Socket socket, DataInputStream in, int window, int payload,
            int lane, int active, int rate, int burst, long start, long firstMeasured, long totalSlots, long end, long drainEnd)
            throws Exception {
        var peer = new RateSamples(window);
        var out = socket.getOutputStream();
        var writer = Thread.ofVirtual().start(() -> {
            var codec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
            byte[] request = new byte[payload];
            int frame = payload + 6;
            byte[] batch = new byte[burst * frame];
            for (int i = 0; i < burst; i++) {
                batch[i * frame] = (byte) frame;
                batch[i * frame + 1] = (byte) (frame >>> 8);
            }
            try {
                for (long slot = lane; slot < totalSlots; slot += active) {
                    long deadline = start + slot * SECOND_NS / rate;
                    waitUntil(deadline);
                    if (System.nanoTime() >= end) break;
                    boolean measured = slot >= firstMeasured;
                    if (!peer.writable.tryAcquire(burst)) {
                        if (measured) peer.windowMisses += burst;
                        continue;
                    }
                    final int previous = peer.issued;
                    for (int i = 0; i < burst; i++) {
                        int sequence = previous + i;
                        int index = sequence % window;
                        body(request, REQUEST, sequence);
                        byte[] encrypted = codec.toServer(request);
                        System.arraycopy(encrypted, 0, batch, i * frame + 2, encrypted.length);
                        peer.deadlines[index] = deadline;
                        peer.measured[index] = measured;
                    }
                    if (measured) peer.attempted += burst;
                    long sent = System.nanoTime();
                    for (int i = 0; i < burst; i++) peer.sends[(previous + i) % window] = sent;
                    peer.issued = previous + burst;
                    peer.readable.release(burst);
                    out.write(batch);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException failure) {
                peer.sendFailures++;
            } finally {
                peer.done = true;
                peer.readable.release();
            }
        });
        try {
            var codec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
            var expected = new byte[payload];
            for (int sequence = 0; ; sequence++) {
                long left = drainEnd - System.nanoTime();
                if (left <= 0 || !peer.readable.tryAcquire(left, TimeUnit.NANOSECONDS)) break;
                if (peer.done && sequence >= peer.issued) break;
                socket.setSoTimeout((int) Math.max(1, (drainEnd - System.nanoTime()) / 1_000_000L));
                body(expected, RESPONSE, sequence);
                byte[] response = read(in);
                long received = System.nanoTime();
                if (!Arrays.equals(response, codec.fromServer(expected))) throw new AssertionError("wire mismatch at sequence " + sequence);
                int index = sequence % window;
                if (peer.measured[index] && received <= drainEnd)
                    peer.record(received - peer.deadlines[index], received - peer.sends[index], received <= end);
                peer.writable.release();
            }
        } catch (java.net.SocketTimeoutException drained) {
            // Outstanding requests are retained in the incomplete count.
        } catch (java.io.IOException failure) {
            peer.receiveFailures++;
        } finally {
            if (writer.isAlive()) {
                writer.interrupt();
                socket.close();
                writer.join();
            }
        }
        peer.failures = peer.sendFailures + peer.receiveFailures;
        return peer;
    }

    private static void waitUntil(long end) throws InterruptedException {
        long wait;
        while ((wait = end - System.nanoTime()) > 0) TimeUnit.NANOSECONDS.sleep(wait);
    }

    private static double percentile(long[] sorted, int rank) {
        return sorted[(int) ((sorted.length * (long) rank + 999) / 1000) - 1] / 1000.0;
    }

    private static byte[] read(DataInputStream in) throws Exception {
        int length = in.readUnsignedByte() | (in.readUnsignedByte() << 8);
        if (length < 3) throw new AssertionError("frame length " + length);
        byte[] body = new byte[length - 2];
        in.readFully(body);
        return body;
    }

    private static void body(byte[] data, int opcode, int sequence) {
        data[0] = (byte) opcode;
        for (int i = 0; i < 4; i++) data[i + 1] = (byte) (sequence >>> (i * 8));
        Arrays.fill(data, 5, data.length, (byte) sequence);
    }

    private static final class Reply extends IPacketWriter<CUserSocket> {
        final byte[] body;
        Reply(int payload) { body = new byte[payload]; }
        @Override protected boolean write(CUserSocket client, Assemble writer) {
            writer.writeBytes(body);
            return true;
        }
    }
}
