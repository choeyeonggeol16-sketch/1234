package l1j.server.server.model.character.pss.window;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public class TransportContract {
    static int checks, groups;
    static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    static void await(BooleanSupplier condition, String why) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() - until < 0) Thread.sleep(1);
        check(condition.getAsBoolean(), why);
    }
    interface Test { void run() throws Exception; }
    static void group(String name, Test test) throws Exception { test.run(); groups++; System.out.println("PASS " + name); }
    static PssWindowTransport.Token token(long n) { return new PssWindowTransport.Token(0x123456789abcdef0L, n); }
    static byte[] hello(PssWindowTransport.Token token) {
        return ByteBuffer.allocate(24).put(new byte[]{'P','S','S','U','I',1,0,0}).putLong(token.high()).putLong(token.low()).array();
    }
    static Socket connect(PssWindowTransport server) throws Exception {
        Socket socket = new Socket("127.0.0.1", server.port()); socket.setSoTimeout(3000); return socket;
    }
    static class Owner implements PssWindowTransport.Owner {
        volatile boolean valid = true;
        volatile String ip = "127.0.0.1";
        volatile PssWindowTransport.Token expected;
        volatile PssWindowTransport.Peer peer;
        volatile int detaches;
        Owner(PssWindowTransport.Token expected) { this.expected = expected; }
        public boolean valid() { return valid; }
        public String remoteIp() { return ip; }
        public boolean attach(PssWindowTransport.Token key, PssWindowTransport.Peer attached) {
            if (!valid || !expected.equals(key)) return false;
            PssWindowTransport.Peer old = peer; peer = attached; if (old != null) old.close(); return true;
        }
        public void detach(PssWindowTransport.Peer detached) { if (peer == detached) peer = null; detaches++; }
    }
    static PssWindowTransport server(int capacity) throws Exception {
        return new PssWindowTransport(new InetSocketAddress("127.0.0.1",0), capacity);
    }
    public static void main(String[] args) throws Exception {
        List<Throwable> uncaught = java.util.Collections.synchronizedList(new ArrayList<>());
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> uncaught.add(failure));
        group("game registration first; fragmented hello; exact bound/open bytes", () -> {
            try (var server = server(8); var socket = connect(server)) {
                var key = token(1); var owner = new Owner(key); check(server.register(key, owner), "register");
                for (byte value : hello(key)) socket.getOutputStream().write(value);
                check(socket.getInputStream().read() == 16, "BOUND ordering");
                await(() -> owner.peer != null, "owner attached");
                check(owner.peer.requestOpen(), "open admitted");
                check(socket.getInputStream().read() == 1, "OPEN byte");
                socket.getOutputStream().write(2);
                check(server.pendingCount() == 0 && server.waitingCount() == 0, "auth entries retained");
            }
        });
        group("helper registration first binds when game registration arrives", () -> {
            try (var server = server(8); var socket = connect(server)) {
                var key = token(2); socket.getOutputStream().write(hello(key));
                await(() -> server.waitingCount() == 1, "waiting helper");
                var owner = new Owner(key); check(server.register(key, owner), "late game registration");
                check(socket.getInputStream().read() == 16, "late BOUND");
                check(server.pendingCount() == 0 && server.waitingCount() == 0, "late bind leaked");
            }
        });
        group("malformed hello does not consume game registration", () -> {
            try (var server = server(8)) {
                var key = token(3); var owner = new Owner(key); server.register(key, owner);
                try (var bad = connect(server)) {
                    byte[] bytes = hello(key); bytes[0] = 0; bad.getOutputStream().write(bytes);
                    check(bad.getInputStream().read() == -1, "bad magic accepted");
                }
                check(server.pendingCount() == 1, "bad hello consumed registration");
                try (var good = connect(server)) {
                    good.getOutputStream().write(hello(key)); check(good.getInputStream().read() == 16, "retry bind");
                }
            }
        });
        group("wrong source IP cannot take another game session", () -> {
            try (var server = server(8); var socket = connect(server)) {
                var key = token(4); var owner = new Owner(key); owner.ip = "127.0.0.2";
                server.register(key, owner); socket.getOutputStream().write(hello(key));
                check(socket.getInputStream().read() == -1 && owner.peer == null, "IP mismatch bound");
            }
        });
        group("expiry removes waiting peers, pending game references and idle channels", () -> {
            AtomicLong clock = new AtomicLong(1_000_000_000L);
            try (var server = new PssWindowTransport(new InetSocketAddress("127.0.0.1",0),8,clock::get)) {
                try (var unpaired = connect(server)) {
                    unpaired.getOutputStream().write(hello(token(50))); await(() -> server.waitingCount() == 1, "wait");
                    clock.addAndGet(TimeUnit.SECONDS.toNanos(6)); server.sweep();
                    check(unpaired.getInputStream().read() == -1, "unpaired expiry");
                    check(server.waitingCount() == 0, "waiting entry leaked");
                }
                var key = token(5); var owner = new Owner(key); server.register(key, owner);
                clock.addAndGet(TimeUnit.SECONDS.toNanos(16)); server.sweep(); check(server.pendingCount() == 0, "pending expiry");
                try (var socket = connect(server)) {
                    server.register(key, owner); socket.getOutputStream().write(hello(key)); check(socket.getInputStream().read() == 16, "bind");
                    clock.addAndGet(TimeUnit.SECONDS.toNanos(46)); server.sweep(); check(socket.getInputStream().read() == -1, "idle expiry");
                    await(() -> server.connectionCount() == 0 && owner.peer == null, "idle resources");
                }
            }
        });
        group("logout invalidates bound peer and releases owner reference", () -> {
            try (var server = server(8); var socket = connect(server)) {
                var key = token(6); var owner = new Owner(key); server.register(key, owner);
                socket.getOutputStream().write(hello(key)); check(socket.getInputStream().read() == 16, "bind");
                owner.valid = false; server.sweep(); check(socket.getInputStream().read() == -1, "logout did not close");
                await(() -> owner.peer == null && server.connectionCount() == 0, "logout cleanup");
            }
        });
        group("nonce replay needs a new authenticated game registration", () -> {
            AtomicLong clock = new AtomicLong(1_000_000_000L);
            try (var server = new PssWindowTransport(new InetSocketAddress("127.0.0.1",0),8,clock::get)) {
                var key = token(7); var owner = new Owner(key); server.register(key, owner);
                try (var first = connect(server); var replay = connect(server)) {
                    first.getOutputStream().write(hello(key)); check(first.getInputStream().read() == 16, "bind");
                    replay.getOutputStream().write(hello(key)); await(() -> server.waitingCount() == 1, "replay wait");
                    clock.addAndGet(TimeUnit.SECONDS.toNanos(6)); server.sweep(); check(replay.getInputStream().read() == -1, "replay bound");
                    check(owner.peer.requestOpen() && first.getInputStream().read() == 1, "replay changed owner");
                }
            }
        });
        group("bounded connection and pending-registration capacity", () -> {
            try (var server = server(1); var first = connect(server)) {
                await(() -> server.connectionCount() == 1, "first accept");
                try (var overflow = connect(server)) { check(overflow.getInputStream().read() == -1, "capacity overflow"); }
                var a = token(8); var b = token(9); var owner = new Owner(a);
                check(server.register(a,owner), "first pending"); check(!server.register(b,new Owner(b)), "pending overflow");
                server.cancel(a,owner); check(server.pendingCount() == 0, "cancel pending");
            }
        });
        group("concurrent repeated item clicks keep writer serialization and bounded state", () -> {
            try (var server = server(8); var socket = connect(server)) {
                var key = token(10); var owner = new Owner(key); server.register(key,owner);
                socket.getOutputStream().write(hello(key)); check(socket.getInputStream().read() == 16, "bind");
                var pool = Executors.newFixedThreadPool(8); CountDownLatch gate = new CountDownLatch(1);
                var futures = new ArrayList<java.util.concurrent.Future<?>>();
                for (int i=0;i<8;i++) futures.add(pool.submit(() -> {gate.await();for(int n=0;n<100;n++)owner.peer.requestOpen();return null;}));
                gate.countDown(); for(var future:futures)future.get(); pool.shutdown();
                check(socket.getInputStream().read() == 1, "concurrent open missing");
                owner.peer.close(); int count;
                while ((count=socket.getInputStream().read()) != -1) check(count == 1,"interleaved writer byte");
                await(() -> server.connectionCount() == 0, "concurrent close cleanup");
            }
        });
        Thread.sleep(50);
        check(uncaught.isEmpty(), "asynchronous failure: "+uncaught);
        System.out.println("RESULT groups="+groups+" assertions="+checks+"; native game UI is tested separately");
    }
}
