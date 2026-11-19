package l1j.server.server.model.network.core.broadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

/** Actual production broadcast methods; verifies 16KiB boundary and original AOI/FIFO. */
public final class LargeAndFanout110 {
    private static void ck(boolean x, String s) { if (!x) throw new AssertionError(s); }
    private static L1PcInstance pc(CIOServer owner, int id, int map) {
        return new L1PcInstance(id,map,new CIOClient(new CUserSocket(owner)));
    }
    private static CSharedBroadcast.NativeWireWriter sized(int length,AtomicInteger writes) {
        return b -> {
            writes.incrementAndGet();
            for (int i=2;i<length;i++) b.put((byte)(i*7+3));
        };
    }
    private static void assertFrame(byte[] bytes,int length) {
        ck(bytes.length==length,"wrong frame size");
        ck(((bytes[0]&255)|((bytes[1]&255)<<8))==length,"wrong length header");
        for(int i=2;i<bytes.length;i++)
            ck(bytes[i]==(byte)(i*7+3),"payload differed at byte "+i);
    }
    public static void main(String[] args) {
        final CIOServer owner=new CIOServer();
        final L1World world=L1World.getInstance();
        final L1PcInstance self=pc(owner,11,7), a=pc(owner,12,7), b=pc(owner,13,7);
        final L1PcInstance wrongMap=pc(owner,14,8);
        world.visible=new ArrayList<>(List.of(self,a,wrongMap,b,a));
        AtomicInteger writes=new AtomicInteger();
        final int releases=world.releases;
        CSharedBroadcast.sendWrittenLarge(self,16385,sized(16385,writes));
        ck(writes.get()==1,"large AOI writer encoded more than once");
        ck(world.releases==releases+1,"large AOI list not returned");
        ck(self.client.socket.frames.isEmpty(),"large AOI sent to self");
        ck(a.client.socket.frames.size()==2 && b.client.socket.frames.size()==1,
            "large AOI filtered map/self or lost duplicate");
        ck(wrongMap.client.socket.frames.isEmpty(),"large AOI sent across map");
        for(byte[] frame:a.client.socket.frames)assertFrame(frame,16385);
        assertFrame(b.client.socket.frames.get(0),16385);
        // Large self+broadcast must send self FIRST and exact visible sequence, even duplicates.
        writes.set(0);
        CSharedBroadcast.sendWrittenLargeSelfAndVisible(self,65535,sized(65535,writes));
        ck(writes.get()==1,"large self and AOI writer encoded more than once");
        ck(self.client.socket.frames.size()==2,"self-first + visible duplicate order changed");
        ck(a.client.socket.frames.size()==4 && wrongMap.client.socket.frames.size()==1,
           "explicit visible traversal changed");
        assertFrame(self.client.socket.frames.get(0),65535);
        assertFrame(a.client.socket.frames.get(2),65535);
        // Invalid length or mismatched writer must fail, never create malformed wire.
        boolean failed=false;
        try {CSharedBroadcast.sendWrittenLarge(self,65536,sized(65536,writes));}
        catch(IllegalArgumentException expected){failed=true;}
        ck(failed,"16-bit overflow incorrectly accepted");
        final int prior=world.releases;
        failed=false;
        try {CSharedBroadcast.sendWrittenLarge(self,16385,buff->buff.put((byte)1));}
        catch(IllegalStateException expected){failed=true;}
        ck(failed && world.releases==prior+1,"failed large frame leaked visible borrow");
        // No eligible viewers must avoid constructing a large frozen frame.
        writes.set(0);
        world.visible=new ArrayList<>(List.of(self,wrongMap));
        CSharedBroadcast.sendWrittenLarge(self,16385,sized(16385,writes));
        ck(writes.get()==0,"large writer invoked without any eligible viewer");
        // Group per-socket failure is isolated without deduplication or reordering.
        CUserSocket failing=a.client.socket;failing.failEnqueueOnce=true;
        world.visible=new ArrayList<>(List.of(a));
        final int beforeA=failing.frames.size(),beforeB=b.client.socket.frames.size();
        CSharedBroadcast.sendWrittenGroupsBestEffort(List.of(a,b),List.of(b,a),out->out.put((byte)0x6B));
        ck(failing.frames.size()==beforeA+1,"failed first group receiver disrupted group continuation");
        ck(b.client.socket.frames.size()==beforeB+2,"repeated group recipient incorrectly deduplicated");
        ck(b.client.socket.frames.get(beforeB)[2]==(byte)0x6B,"group payload changed");
        for(L1PcInstance p:List.of(self,a,b,wrongMap))p.client.socket.drain();
        ck(owner.releases==1,"one group Native source should have one producer release");
        System.out.println("110 large frame 16KiB/65535, AOI FIFO/duplicates, group failure, owner release PASS");
    }
}
