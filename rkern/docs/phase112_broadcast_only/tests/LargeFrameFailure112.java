package l1j.server.server.model.network.core.broadcast;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

/** Real large-area production broadcast methods, mock socket WRITE failures. */
public final class LargeFrameFailure112 {
    private static void ck(boolean pass,String msg) {if(!pass)throw new AssertionError(msg);}
    private static L1PcInstance pc(CIOServer server,int id,boolean fail) {
        CUserSocket socket= fail ? new CUserSocket(server) {
            @Override public boolean sendFrozenWire(ByteBuffer buf) {
                throw new IllegalStateException("injected large-frame SEND failure");
            }
        }:new CUserSocket(server);
        return new L1PcInstance(id,1,new CIOClient(socket));
    }
    private static CSharedBroadcast.NativeWireWriter writer(AtomicInteger writes) {
        return b -> {writes.incrementAndGet();for(int i=0;i<19998;i++)b.put((byte)(i&255));};
    }
    private static void checkWire(L1PcInstance pc) {
        ck(pc.client.socket.frames.size()==1,"valid large packet missing");
        byte[] bytes=pc.client.socket.frames.get(0);
        ck(bytes.length==20000,"large frame byte count wrong");
        ck((bytes[0]&255)==0x20 && (bytes[1]&255)==0x4e,"large frame length prefix wrong");
        ck((bytes[2]&255)==0 && (bytes[19999]&255)==((19997)&255),"payload bytes wrong");
    }
    public static void main(String[]args) {
        CIOServer server=new CIOServer();
        L1World world=L1World.getInstance();
        L1PcInstance first=pc(server,100,true),middle=pc(server,101,false),last=pc(server,102,false);
        world.visible=List.of(first,middle,last);
        AtomicInteger writes=new AtomicInteger();
        L1PcInstance origin=pc(server,200,false);
        // self first; AOI failure must not suppress subsequent viewers.
        CSharedBroadcast.sendWrittenLargeSelfAndVisible(origin,20000,writer(writes));
        ck(writes.get()==1,"self+visible large frame re-encoded");
        ck(origin.client.socket.frames.size()==1,"self packet missing");
        checkWire(middle);checkWire(last);
        // sameWorldOther, one failing viewer, and borrowed AOI list release.
        writes.set(0);
        int releases=world.releases;
        CSharedBroadcast.sendWrittenLarge(origin,20000,writer(writes));
        ck(writes.get()==1,"AOI large frame re-encoded");
        ck(world.releases==releases+1,"borrowed AOI list leaked");
        ck(middle.client.socket.frames.size()==2 && last.client.socket.frames.size()==2,
                "later AOI recipients missed large frame");
        // Failure in one viewer's eligibility lookup before first allocation.
        L1PcInstance brokenLookup=new L1PcInstance(300,1,new CIOClient(new CUserSocket(server))) {
            @Override public CIOClient getNetConnection() {
                throw new IllegalStateException("injected per-viewer connection query failure");
            }
        };
        world.visible=List.of(brokenLookup,middle,last);
        releases=world.releases;
        writes.set(0);
        CSharedBroadcast.sendWrittenLarge(origin,20000,writer(writes));
        ck(world.releases==releases+1,"viewer-lookup failure leaked AOI borrow");
        ck(writes.get()==1,"viewer-lookup failure re-encoded source");
        ck(middle.client.socket.frames.size()==3 && last.client.socket.frames.size()==3,
                "viewer-lookup failure suppressed later large-frame recipients");
        System.out.println("112 production 20KiB large AOI: isolated WRITE failures, FIFO, bytes, borrow release PASS");
    }
}
