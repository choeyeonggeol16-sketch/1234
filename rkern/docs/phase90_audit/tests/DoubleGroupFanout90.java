package l1j.server.server.model.network.core.broadcast;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

/** Production CSharedBroadcast + pooled source, test sockets only. */
public final class DoubleGroupFanout90 {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
    static L1PcInstance pc(CIOServer owner, int id) {
        return new L1PcInstance(id, 1, new CIOClient(new CUserSocket(owner)));
    }
    public static void main(String[] args) {
        final CIOServer pool = new CIOServer();
        final L1PcInstance red = pc(pool, 1);
        final L1PcInstance blue = pc(pool, 2);
        final L1PcInstance disconnected = pc(pool, 3);
        disconnected.client.socket.connected = false;
        final AtomicInteger encoded = new AtomicInteger();
        // The same recipient in both teams must receive twice, red before blue.
        // The disconnected member does not create another native frame.
        CSharedBroadcast.sendWrittenGroups(
                List.of(red, blue), List.of(disconnected, red), b -> {
                    encoded.incrementAndGet();
                    b.put((byte)0x45).put((byte)0x7f);
                });
        check(encoded.get() == 1, "source encoded more than once");
        check(red.client.socket.frames.size() == 2, "duplicate/team order changed");
        check(blue.client.socket.frames.size() == 1, "blue lost frame");
        check(disconnected.client.socket.frames.isEmpty(), "disconnected received");
        check(red.client.socket.nativeAllocs == 1, "not one source");
        check(blue.client.socket.nativeAllocs == 0, "second source acquired");
        for (byte[] frame : red.client.socket.frames) {
            check(frame.length == 4 && frame[2] == 0x45 && frame[3] == 0x7f, "bad frame");
        }
        check(pool.releases == 0, "released before socket drains");
        red.client.socket.drain();
        blue.client.socket.drain();
        check(pool.releases == 1, "source not returned exactly once");
        System.out.println("90 group broadcast: red->blue, duplicate, disconnect, 1 assembly, ref release PASS");
    }
}
