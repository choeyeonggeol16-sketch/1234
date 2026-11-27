import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;

public class WorkerAB {
    enum Mode { INLINE, PINNED, SHARED }
    static final int WORKERS = 2, CONNECTIONS = 5000, QUANTUM = 8;
    static final int RATE = 11500, PACED = 23000, FLOOD = 14000;
    static final InetAddress LOOP = InetAddress.getLoopbackAddress();
    static final AtomicReference<Throwable> failure = new AtomicReference<>();
    static final ArrayBlockingQueue<Endpoint> shared = new ArrayBlockingQueue<>(CONNECTIONS);
    static final List<ArrayBlockingQueue<Endpoint>> pinned = List.of(
            new ArrayBlockingQueue<>(CONNECTIONS), new ArrayBlockingQueue<>(CONNECTIONS));
    static final AtomicIntegerArray sleeping = new AtomicIntegerArray(WORKERS);
    static final CountDownLatch opened = new CountDownLatch(CONNECTIONS);
    static final ConcurrentMap<Integer,Integer> ownerByPort = new ConcurrentHashMap<>();
    static final SocketChannel[] clients = new SocketChannel[CONNECTIONS];
    static final int[] owners = new int[CONNECTIONS];
    static final int[][] hot = new int[WORKERS][32];
    static volatile List<CSelectorTransport.OwnerDriver> drivers;
    static volatile Run run;
    static volatile Mode mode = Mode.INLINE;
    static Selector reader;
    static int iterations;
    static volatile long calibrationSink;

    static long work(long x, int rounds) {
        for (int i = 0; i < rounds; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
        }
        return x;
    }

    static void calibrate() {
        int rounds = 40000;
        for (int j = 0; j < 7; j++) {
            long start = System.nanoTime();
            for (int k = 0; k < 400; k++) calibrationSink = work(k + 1, rounds);
            if (j >= 3) {
                long elapsed = System.nanoTime() - start;
                rounds = (int) Math.max(2000, Math.min(200000, rounds * (400.0 * 85000) / elapsed));
            }
        }
        iterations = rounds;
    }

    static final class Packet {
        final int id, client, seq;
        long due, sent, admitted, wait, ack;
        Packet(int id, int client, int seq) { this.id = id; this.client = client; this.seq = seq; }
    }

    static final class Run {
        final Packet[] packets;
        final int[] serverSeq = new int[CONNECTIONS], clientSeq = new int[CONNECTIONS];
        final int[] handled = new int[WORKERS];
        final long[] checksum = new long[WORKERS];
        int received;
        Run(boolean skew, int count, long seed) {
            SplittableRandom random = new SplittableRandom(seed);
            int[] seq = new int[CONNECTIONS];
            packets = new Packet[count];
            for (int i = 0; i < count; i++) {
                int client;
                if (random.nextInt(10) == 0) client = random.nextInt(CONNECTIONS);
                else {
                    int slot = random.nextInt(32);
                    client = skew ? hot[0][slot] : hot[slot % 2][slot / 2];
                }
                packets[i] = new Packet(i, client, seq[client]++);
            }
        }
    }

    static void schedule(Endpoint endpoint) {
        boolean dynamic = mode == Mode.SHARED;
        (dynamic ? shared : pinned.get(endpoint.owner)).add(endpoint);
        for (int i = 0; i < WORKERS; i++)
            if ((dynamic || i == endpoint.owner) && sleeping.get(i) != 0) drivers.get(i).wakeup();
    }

    static final class Endpoint implements CSelectorTransport.RawEndpoint {
        final CSelectorTransport.Connection socket;
        final int owner;
        final ByteBuffer input = ByteBuffer.allocate(1024), output = ByteBuffer.allocate(1024);
        final ArrayDeque<Packet> pending = new ArrayDeque<>(), replies = new ArrayDeque<>();
        boolean active, writing;
        Endpoint(CSelectorTransport.Connection socket) {
            this.socket = socket;
            owner = socket.ownerIndex();
            socket.attachRawEndpoint(this);
            socket.readRaw(input);
        }
        public void onRawRead(int bytes) {
            if (bytes <= 0) return;
            try {
                input.flip();
                while (input.remaining() >= 4) {
                    Packet p = run.packets[input.getInt()];
                    p.admitted = System.nanoTime();
                    if (mode == Mode.INLINE) process(p, owner);
                    else {
                        boolean start;
                        synchronized (pending) {
                            pending.add(p);
                            start = !active;
                            active = true;
                        }
                        if (start) schedule(this);
                    }
                }
                input.compact();
                if (!socket.readRaw(input)) throw new IllegalStateException("read admission");
            } catch (Throwable t) { failure.compareAndSet(null,t); }
        }
        void drain(int worker) {
            for (int i = 0; i < QUANTUM; i++) {
                Packet p;
                synchronized (pending) {
                    p = pending.poll();
                    if (p == null) { active = false; return; }
                }
                process(p, worker);
            }
            synchronized (pending) {
                if (pending.isEmpty()) { active = false; return; }
            }
            schedule(this);
        }
        void process(Packet p, int worker) {
            Run r = run;
            p.wait = System.nanoTime() - p.admitted;
            if (r.serverSeq[p.client]++ != p.seq) throw new IllegalStateException("handler order");
            r.checksum[worker] ^= work(p.id + 1, iterations);
            r.handled[worker]++;
            // Use one FIFO submission path in all modes, even on the local owner.
            if (!socket.submit(connection -> {
                replies.add(p);
                if (!writing) flush();
            }, true)) throw new IllegalStateException("reply admission");
        }
        void flush() {
            output.clear();
            while (output.remaining() >= 4 && !replies.isEmpty()) output.putInt(replies.remove().id);
            output.flip();
            writing = output.hasRemaining();
            if (writing && !socket.writeRaw(output)) throw new IllegalStateException("write admission");
        }
        public void onRawWrite(long bytes, boolean gather) {
            if (bytes < 0) return;
            if (output.hasRemaining()) {
                if (!socket.writeRaw(output)) throw new IllegalStateException("partial write");
            } else flush();
        }
        public void onRawDeadline() {}
    }

    static void attach(List<CSelectorTransport.OwnerDriver> list) {
        drivers = list;
        for (int i = 0; i < WORKERS; i++) {
            int index = i;
            Thread.ofPlatform().daemon().name("selector-worker-" + i).start(() -> {
                var driver = list.get(index);
                try {
                    while (driver.active()) {
                        driver.advance();
                        var queue = mode == Mode.SHARED ? shared : pinned.get(index);
                        Endpoint endpoint = queue.poll();
                        if (endpoint != null) { endpoint.drain(index); continue; }
                        sleeping.set(index, 1);
                        if (queue.isEmpty()) driver.awaitWork(10);
                        sleeping.set(index, 0);
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null,t);
                    driver.closeOnOwner();
                }
            });
        }
    }

    static void check() { if (failure.get() != null) throw new IllegalStateException(failure.get()); }

    static void connect(InetSocketAddress addr) throws Exception {
        reader = Selector.open();
        int[] ports = new int[CONNECTIONS];
        for (int i = 0; i < CONNECTIONS; i++) {
            var s = clients[i] = SocketChannel.open();
            s.setOption(StandardSocketOptions.TCP_NODELAY, true);
            s.connect(addr);
            ports[i] = ((InetSocketAddress)s.getLocalAddress()).getPort();
            s.configureBlocking(false);
            s.register(reader, SelectionKey.OP_READ, new ClientReader(i));
        }
        if (!opened.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("connections "+opened.getCount());
        int[] counts = new int[WORKERS];
        for (int i = 0; i < CONNECTIONS; i++) {
            int owner = owners[i] = ownerByPort.get(ports[i]);
            if (counts[owner] < 32) hot[owner][counts[owner]] = i;
            counts[owner]++;
        }
        if (Math.min(counts[0],counts[1]) < 32) throw new IllegalStateException("owner placement");
        System.err.println("connections per owner "+Arrays.toString(counts)+", rounds per packet "+iterations);
    }

    static final class ClientReader {
        final int client;
        final ByteBuffer in = ByteBuffer.allocate(128);
        ClientReader(int client) { this.client = client; }
        void read(Run r) throws Exception {
            if (clients[client].read(in) < 0) throw new IllegalStateException("early EOF");
            long now = System.nanoTime();
            in.flip();
            while (in.remaining() >= 4) {
                Packet p = r.packets[in.getInt()];
                if (p.client != client || p.seq != r.clientSeq[client]++ || p.ack != 0)
                    throw new IllegalStateException("reply order or duplicate");
                p.ack = now;
                r.received++;
            }
            in.compact();
        }
    }

    static double p99(long[] a) {
        if (a.length == 0) return 0;
        Arrays.sort(a);
        return a[(int)Math.ceil(a.length * 0.99) - 1] / 1e6;
    }

    static void trial(int rep, Mode chosen, boolean skew, int rate, int n, boolean record) throws Exception {
        Run r = new Run(skew, n, 9196 + rep);
        run = r;
        mode = chosen;
        for (var d : drivers) d.wakeup();
        ByteBuffer out = ByteBuffer.allocate(4);
        int sent = 0;
        long start = System.nanoTime(), limit = start + 45_000_000_000L;
        boolean partial = false;
        while (r.received < n) {
            check();
            long now = System.nanoTime();
            if (now > limit) throw new IllegalStateException("timeout sent="+sent+" ack="+r.received);
            for (int burst = 0; burst < 32 && sent < n; burst++) {
                Packet p = r.packets[sent];
                long due = rate == 0 ? now : start + sent * 1_000_000_000L / rate;
                if (now < due) break;
                if (!partial) {
                    out.clear().putInt(p.id).flip();
                    p.sent = System.nanoTime();
                    p.due = rate == 0 ? p.sent : due;
                }
                clients[p.client].write(out);
                partial = out.hasRemaining();
                if (partial) break;
                sent++;
            }
            if (sent == n) reader.select(2);
            else reader.selectNow();
            var keys = reader.selectedKeys().iterator();
            while (keys.hasNext()) {
                var key = keys.next();
                keys.remove();
                ((ClientReader)key.attachment()).read(r);
            }
            if (rate != 0 && sent < n) {
                long until = start + sent * 1_000_000_000L / rate - System.nanoTime();
                if (until > 10_000L) LockSupport.parkNanos(Math.min(until,60_000L));
            }
        }
        long end = System.nanoTime();
        check();
        if (r.handled[0] + r.handled[1] != n) throw new IllegalStateException("missing handlers");
        long[][] waits = new long[WORKERS][n];
        int[] count = new int[WORKERS];
        long[] scheduled = new long[n], wire = new long[n], lag = new long[n];
        for (Packet p : r.packets) {
            scheduled[p.id] = p.ack - p.due;
            wire[p.id] = p.ack - p.sent;
            lag[p.id] = p.sent - p.due;
            int owner = owners[p.client];
            waits[owner][count[owner]++] = p.wait;
        }
        if (record)
            System.out.printf(Locale.ROOT,"%d,%s,%s,%d,%d,%.1f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d,%x%n",
                    rep,chosen,skew?"one_owner_hot":"spread_hot",rate,n,n*1e9/(end-start),
                    p99(scheduled),p99(wire),p99(lag),p99(Arrays.copyOf(waits[0], count[0])),
                    p99(Arrays.copyOf(waits[1], count[1])),r.handled[0],r.handled[1],count[0],count[1],
                    r.checksum[0]^r.checksum[1]);
    }

    public static void main(String[] args) throws Exception {
        int targetRate = args.length > 0 ? Integer.parseInt(args[0]) : RATE;
        if (args.length > 1) iterations = Integer.parseInt(args[1]);
        else calibrate();
        boolean pacedOnly = args.length > 2 && args[2].equals("paced");
        var config = new CSelectorTransport.Config(WORKERS, CONNECTIONS, 1024, 65536, 1, 1, 1,
                65536,1024,0,Duration.ofSeconds(30));
        var handler = new CSelectorTransport.RawHandler() {
            public void onOpen(CSelectorTransport.Connection c) {
                new Endpoint(c);
                ownerByPort.put(c.remoteAddress().getPort(),c.ownerIndex());
                opened.countDown();
            }
        };
        try (var transport = CSelectorTransport.bindHosted(new InetSocketAddress(LOOP,0), config, handler, WorkerAB::attach)) {
            connect(transport.localAddress());
            for (Mode m : Mode.values()) trial(0,m,false,6000,5000,false);
            System.out.println("rep,mode,load,target_pps,packets,completed_pps,schedule_p99_ms,rtt_p99_ms,sender_lag_p99_ms,owner0_queue_p99_ms,owner1_queue_p99_ms,worker0_handled,worker1_handled,owner0_input,owner1_input,checksum");
            for (int rep=1; rep<=3; rep++) {
                for (int placement=0; placement<2; placement++) {
                    boolean skew = (placement + rep) % 2 == 0;
                    for (int phase=0; phase<2; phase++) {
                        boolean flood = (phase + rep) % 2 == 0;
                        if (pacedOnly && flood) continue;
                        for (int i=0; i<3; i++)
                            trial(rep,Mode.values()[(i+rep+placement+phase)%3],skew,
                                    flood?0:targetRate, flood?FLOOD:targetRate*2,true);
                    }
                }
            }
        } finally {
            if (reader != null) reader.close();
            for (var c : clients) if (c != null) c.close();
        }
    }
}
