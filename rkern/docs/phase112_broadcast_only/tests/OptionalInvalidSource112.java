package l1j.server.server.model.network.core.broadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.model.L1World;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

/** Production CSharedBroadcast: broken first source vs per-recipient SEND failure. */
public final class OptionalInvalidSource112 {
    private static final CIOServer owner = new CIOServer();
    private static final L1World world = L1World.getInstance();
    private static int checks = 0;
    private static void ck(boolean yes, String msg) {
        if (!yes) throw new AssertionError(msg);
        checks++;
    }
    private static L1PcInstance pc(int id) {
        L1PcInstance p = new L1PcInstance(id, 1, new CIOClient(new CUserSocket(owner)));
        p.gm=true;
        return p;
    }
    @FunctionalInterface interface Invocation {
        void send(CSharedBroadcast.NativeWireWriter writer);
    }
    private static void broken(String label, Invocation send) {
        AtomicInteger attempts = new AtomicInteger();
        final int released = owner.releases;
        send.send(b -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("bad shared packet: " + label);
        });
        ck(attempts.get()==1, label + " encoded invalid packet " + attempts.get() + " times");
        ck(owner.releases==released+1, label+" did not release exactly one failed Native source");
    }
    public static void main(String[] args) {
        List<L1PcInstance> recipients=new ArrayList<>(5000);
        for(int i=0;i<5000;i++) recipients.add(pc(i+1));
        world.all=new ArrayList<>(recipients);
        broken("iterable", writer -> CSharedBroadcast.sendWrittenRecipientsBestEffort(recipients,writer));
        broken("array", writer -> CSharedBroadcast.sendWrittenArrayBestEffort(recipients.toArray(new L1PcInstance[0]),writer));
        broken("groups", writer -> CSharedBroadcast.sendWrittenGroupsBestEffort(
                recipients.subList(0,2500),recipients.subList(2500,5000),writer));
        broken("playable", CSharedBroadcast::sendWrittenAllPlayableBestEffort);
        broken("GM", CSharedBroadcast::sendWrittenAllGmsBestEffort);
        L1Clan clan=new L1Clan();
        for(L1PcInstance p:recipients)clan.members.add(new L1Clan.Member(p));
        broken("clan", writer -> CSharedBroadcast.sendWrittenClanBestEffort(clan,writer));
        world.visible = recipients;
        broken("objectPlayers", writer -> CSharedBroadcast.sendWrittenObjectPlayersBestEffort(recipients,writer));
        L1PcInstance areaOrigin=pc(8888);
        broken("selfVisible", writer -> CSharedBroadcast.sendWrittenSelfAndVisibleBestEffort(areaOrigin,writer));
        broken("nestedVisible", writer -> CSharedBroadcast.sendWrittenNestedVisibleBestEffort(areaOrigin,writer));
        for(L1PcInstance p:recipients)p.team=77;
        broken("mapTeam", writer -> CSharedBroadcast.sendWrittenMapTeamBestEffort(1,77,writer));
        // Oversized (>65535 wire) message overflows first 16KiB source and
        // the single large-frame retry, not 5,000 separate receivers.
        AtomicInteger overflowCalls = new AtomicInteger();
        int releasesBeforeOverflow=owner.releases;
        CSharedBroadcast.sendWrittenAllPlayableBestEffort(b -> {
            overflowCalls.incrementAndGet();
            for(int i=0;i<70000;i++)b.put((byte)0x45);
        });
        ck(overflowCalls.get()==2,"oversized frame retried for each receiver: "+overflowCalls.get());
        ck(owner.releases==releasesBeforeOverflow+1,"overflowed small Native slab leaked");

        // A recipient-local enqueue failure AFTER a valid frozen plaintext must
        // not stop later recipients or repeat encoding.
        List<L1PcInstance> two=List.of(pc(9001),pc(9002),pc(9003));
        two.get(0).client.socket.failEnqueueOnce=true;
        AtomicInteger encodes=new AtomicInteger();
        int released=owner.releases;
        CSharedBroadcast.sendWrittenArrayBestEffort(two.toArray(new L1PcInstance[0]),b -> {
            encodes.incrementAndGet();b.put((byte)0x6F);
        });
        ck(encodes.get()==1,"valid source re-encoded after socket-local SEND failure");
        ck(two.get(0).client.socket.frames.isEmpty(),"failed socket received data");
        ck(two.get(1).client.socket.frames.size()==1 && two.get(2).client.socket.frames.size()==1,
                "healthy recipients skipped after SEND error");
        for(L1PcInstance p:two)p.client.socket.drain();
        ck(owner.releases==released+1,"valid Native source leaked after recipient failure");

        // Explicit object recipients: first socket fails, duplicate healthy
        // recipients retain both deliveries and one common source.
        L1PcInstance failed=pc(9004), healthy=pc(9005);
        failed.client.socket.failEnqueueOnce=true;
        encodes.set(0);released=owner.releases;
        CSharedBroadcast.sendWrittenObjectPlayersBestEffort(List.of(failed,healthy,healthy),out -> {
            encodes.incrementAndGet();out.put((byte)0x37);
        });
        ck(encodes.get()==1 && healthy.client.socket.frames.size()==2,
           "object-player list lost duplicate or rebuilt packet after failed socket");
        healthy.client.socket.drain();
        ck(owner.releases==released+1,"object-player source leaked");
        System.out.println("112 optional Native source failure isolation PASS checks="+checks);
    }
}
