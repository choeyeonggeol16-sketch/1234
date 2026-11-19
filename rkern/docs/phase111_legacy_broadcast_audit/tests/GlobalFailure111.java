package l1j.server.server.model.network.core.broadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

/** Exercises the ACTUAL production sendWrittenAll() with per-recipient failures. */
public final class GlobalFailure111 {
    private static void ck(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static L1PcInstance pc(CIOServer server, int id) {
        return new L1PcInstance(id, 4, new CIOClient(new CUserSocket(server)));
    }
    private static void checkFrame(CUserSocket socket, int count, int expectedOpcode) {
        ck(socket.frames.size() == count, "recipient frame count=" + socket.frames.size() + ", wanted=" + count);
        if (count > 0) {
            byte[] last = socket.frames.get(count - 1);
            ck(last.length == 3 && last[0] == 3 && last[1] == 0
                    && (last[2] & 255) == expectedOpcode, "wire format changed");
        }
    }
    public static void main(String[] args) {
        CIOServer server = new CIOServer();
        L1World world = L1World.getInstance();
        L1PcInstance first = pc(server, 101), second = pc(server, 102), third = pc(server, 103);
        AtomicInteger encodes = new AtomicInteger();
        // A failed first recipient must not suppress all subsequent announcements.
        first.client.socket.failEnqueueOnce = true;
        world.all = new ArrayList<>(List.of(first, second, third));
        CSharedBroadcast.sendWrittenAll(b -> {encodes.incrementAndGet(); b.put((byte)0x71);});
        checkFrame(first.client.socket, 0, 0x71);
        checkFrame(second.client.socket, 1, 0x71);
        checkFrame(third.client.socket, 1, 0x71);
        ck(encodes.get() == 1, "global announcement encoded more than once");
        for (L1PcInstance p : world.all) p.client.socket.drain();
        ck(server.releases == 1, "one source must release exactly once after first-recipient SEND failure");

        // Same recipient in iterable twice must receive twice, in original FIFO order.
        world.all = new ArrayList<>(List.of(second, third, second));
        encodes.set(0);
        CSharedBroadcast.sendWrittenAll(b -> {encodes.incrementAndGet();b.put((byte)0x72);});
        checkFrame(second.client.socket, 3, 0x72);
        checkFrame(third.client.socket, 2, 0x72);
        ck(encodes.get()==1, "duplicate global recipients reencoded payload");
        second.client.socket.drain();third.client.socket.drain();
        ck(server.releases == 2, "duplicate global source leaked Native reference");

        // Source construction must remain lazy when only disconnected players exist.
        first.client.socket.connected=false;
        world.all=new ArrayList<>(List.of(first));
        encodes.set(0);
        CSharedBroadcast.sendWrittenAll(b -> {encodes.incrementAndGet();b.put((byte)0x73);});
        ck(encodes.get()==0 && server.releases==2,"disconnected-only world allocated a Native source");

        // Strict explicit-recipient API must KEEP its previous failure contract.
        first.client.socket.connected=true;
        first.client.socket.failEnqueueOnce=true;
        boolean threw=false;
        try {
            CSharedBroadcast.sendWrittenRecipients(List.of(first,second), b -> b.put((byte)0x74));
        } catch (IllegalStateException expected) { threw=true; }
        ck(threw,"strict explicit-recipient SEND contract changed");
        checkFrame(second.client.socket,3,0x72);
        ck(server.releases==3,"strict source not returned after first-recipient error");

        // An invalid GLOBAL packet must NOT trigger 5,000 costly retries
        // against the native pool. Socket failures must continue; encoding
        // failures have no valid shared source to fan out.
        world.all = new ArrayList<>(List.of(second, third));
        AtomicInteger badWrites = new AtomicInteger();
        CSharedBroadcast.sendWrittenAll(b -> {
            badWrites.incrementAndGet();
            throw new IllegalArgumentException("injected broken native wire format");
        });
        ck(badWrites.get() == 1, "invalid global packet repeatedly re-encoded: " + badWrites.get());
        ck(server.releases == 4, "failed native source not returned");

        // Public global announcements with 5,000 recipients: one bad socket
        // must not lose the next 2,499+ recipients or rebuild packet bytes.
        ArrayList<L1PcInstance> crowd = new ArrayList<>(5000);
        for(int i=0;i<5000;i++) crowd.add(pc(server,10000+i));
        crowd.get(2500).client.socket.failEnqueueOnce = true;
        world.all = crowd;
        encodes.set(0);
        CSharedBroadcast.sendWrittenAll(b -> {encodes.incrementAndGet();b.put((byte)0x75);});
        ck(encodes.get() == 1, "5000-receiver announcement was re-encoded");
        for(int i=0;i<crowd.size();i++) {
            checkFrame(crowd.get(i).client.socket, i==2500 ? 0 : 1, 0x75);
            crowd.get(i).client.socket.drain();
        }
        ck(server.releases == 5, "5000-receiver announcement leaked Native source");

        // Malformed frames abort after ONE attempt even with 5,000 clients.
        AtomicInteger broken = new AtomicInteger();
        CSharedBroadcast.sendWrittenAll(b -> {
            broken.incrementAndGet();
            throw new IllegalArgumentException("invalid 5000-receiver global frame");
        });
        ck(broken.get() == 1, "5000 invalid frame repeated: " + broken.get());
        ck(server.releases == 6, "5000-client writer failure leaked source");
        System.out.println("111 actual production global fanout: failure isolation, FIFO, bytes, lazy source and strict API PASS");
    }
}
