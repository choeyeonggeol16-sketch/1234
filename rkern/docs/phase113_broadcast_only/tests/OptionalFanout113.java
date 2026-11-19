package l1j.server.server.model.network.core.broadcast;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

/** Exact production optional fanout to the original recipient sequences. */
public final class OptionalFanout113 {
    static void ck(boolean value,String why){if(!value)throw new AssertionError(why);}
    static L1PcInstance pc(CIOServer server,int id,int map) {
        return new L1PcInstance(id,map,new CIOClient(new CUserSocket(server)));
    }
    public static void main(String[] args) {
        CIOServer owner = new CIOServer();
        L1World world = L1World.getInstance();
        L1PcInstance bad=pc(owner,1,1),b=pc(owner,2,1),c=pc(owner,3,1);
        AtomicInteger writes=new AtomicInteger();
        // Self is sent first. Its failure must not prevent two identical
        // visible copies or change native source ownership.
        world.visible=new ArrayList<>(List.of(b,c,b));
        bad.client.socket.failEnqueueOnce=true;
        CSharedBroadcast.sendWrittenSelfAndVisibleBestEffort(bad,out -> {
            writes.incrementAndGet();out.put((byte)0x31);
        });
        ck(writes.get()==1,"self-visible source reencoded");
        ck(bad.client.socket.frames.isEmpty(),"failed self delivered");
        ck(b.client.socket.frames.size()==2 && c.client.socket.frames.size()==1,
                "self-visible lost duplicate or later recipient");
        for(L1PcInstance p:List.of(bad,b,c))p.client.socket.drain();
        ck(owner.releases==1,"self-visible source leak");
        b.client.socket.frames.clear();c.client.socket.frames.clear();

        // Nested AOI keeps repeated eligible viewers, including the same
        // member reached from multiple anchors.
        world.visible=new ArrayList<>(List.of(bad,b,c));
        bad.client.socket.failEnqueueOnce=true;
        writes.set(0);
        CSharedBroadcast.sendWrittenNestedVisibleBestEffort(pc(owner,999,1),out -> {
            writes.incrementAndGet();out.put((byte)0x32);
        });
        ck(writes.get()==1,"nested-visible source reencoded");
        ck(b.client.socket.frames.size()==2 && c.client.socket.frames.size()==2,
                "nested-visible duplicate/recipient order broken");
        ck(bad.client.socket.frames.size()==1,"nested-visible after failed socket did not resume");
        for(L1PcInstance p:List.of(bad,b,c))p.client.socket.drain();
        ck(owner.releases==2,"nested-visible source leak");
        bad.client.socket.frames.clear();b.client.socket.frames.clear();c.client.socket.frames.clear();

        // Team mismatch still excluded; one failing team member cannot block
        // the next matching member.
        bad.team=9;b.team=9;c.team=8;
        world.visible=new ArrayList<>(List.of(bad,b,c,b));
        bad.client.socket.failEnqueueOnce=true;
        writes.set(0);
        CSharedBroadcast.sendWrittenMapTeamBestEffort(1,9,out -> {
            writes.incrementAndGet();out.put((byte)0x33);
        });
        ck(writes.get()==1,"map-team source reencoded");
        ck(b.client.socket.frames.size()==2 && c.client.socket.frames.isEmpty(),
                "map-team filter/duplicates broken");
        b.client.socket.drain();
        ck(owner.releases==3,"map-team source leak");
        System.out.println("113 production broadcast: self-first, nested AOI, map team, duplicate FIFO, SEND failure isolation PASS");
    }
}
