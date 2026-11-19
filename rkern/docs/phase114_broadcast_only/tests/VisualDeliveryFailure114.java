package l1j.server.server.model.network.core.broadcast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

/** Actual production CSharedBroadcast / pooled source / socket stub failure injection. */
public final class VisualDeliveryFailure114 {
    static void ck(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
    static final class PooledSocket extends CUserSocket {
        int creations;
        int enqueueCalls, failOnEnqueue;
        boolean denySource;
        PooledSocket(CIOServer owner) { super(owner); }
        CAsyncSendConstBuffer.Source nativeFrame(int opcode) {
            creations++;
            if (denySource) return null;
            ByteBuffer b = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN);
            b.putShort((short)8).put((byte)opcode).putInt(0x12345678).put((byte)1).flip();
            return new CAsyncSendConstBuffer.Source(server, MemorySegment.NULL, b);
        }
        @Override public boolean enqueueConstBufferConfirmed(CAsyncSendConstBuffer.Source s,int limit) {
            if(++enqueueCalls==failOnEnqueue)
                throw new IllegalStateException("injected frame-index send failure");
            return super.enqueueConstBufferConfirmed(s,limit);
        }
        @Override public CAsyncSendConstBuffer.Source createPooledMoveConstBuffer(int a,int b,int c,int d,int e) { return nativeFrame(a); }
        @Override public CAsyncSendConstBuffer.Source createPooledActionConstBuffer(int a,int b,int c) { return nativeFrame(a); }
        @Override public CAsyncSendConstBuffer.Source createPooledFormattedConstBuffer(int a,int b,int c,int d,int e,int f) { return nativeFrame(b); }
        @Override public CAsyncSendConstBuffer.Source createPooledExtendedConstBuffer(int a,int b,int c,int d,int e,int f,int g,int h,int i,int j,int k,int l,int m) { return nativeFrame(b); }
    }
    static class Fixture {
        final CIOServer owner = new CIOServer();
        final PooledSocket failedSocket = new PooledSocket(owner), successSocket = new PooledSocket(owner);
        final L1PcInstance failed = new L1PcInstance(1,7,new CIOClient(failedSocket));
        final L1PcInstance success = new L1PcInstance(2,7,new CIOClient(successSocket));
        final L1Object anchor = new L1Object(999,7);
        final L1World world = L1World.getInstance();
        Fixture() {
            world.visible=new ArrayList<>(List.of(failed,success));
            failedSocket.failEnqueueOnce=true;
        }
        void verify(String label, int expectedCreations, int expectedReleases) {
            ck(failedSocket.frames.isEmpty(),label+": failed socket should receive nothing");
            ck(successSocket.frames.size()==1,label+": later eligible socket missed broadcast");
            ck(failedSocket.creations+successSocket.creations==expectedCreations,label+": source re-created");
            successSocket.drain();failedSocket.drain();
            ck(owner.releases==expectedReleases,label+": native producer/lease leak, releases="+owner.releases);
        }
    }
    public static void main(String[] args) {
        {
            Fixture f=new Fixture();int b=f.world.borrows,r=f.world.releases;
            CSharedBroadcast.sendMove(f.anchor,999,32000,32001,3);
            f.verify("movement",1,1);
            ck(f.world.borrows==b+1 && f.world.releases==r+1,"movement fast AOI release");
        }
        {
            Fixture f=new Fixture();
            CSharedBroadcast.sendAction(f.anchor,999,12);
            f.verify("action",1,1);
        }
        {
            Fixture f=new Fixture();
            CSharedBroadcast.sendFormatted(f.anchor,CSharedBroadcast.FORMAT_SOUND,0x19,1,2,3,4,true,-1,null);
            f.verify("formatted",1,1);
        }
        {
            Fixture f=new Fixture();
            CSharedBroadcast.sendExtended(f.anchor,CSharedBroadcast.FORMAT_ATTACK,0x20,1,2,3,4,5,6,7,8,9,10,11,true);
            f.verify("combat",1,1);
        }
        {
            Fixture f=new Fixture();
            AtomicInteger writes=new AtomicInteger();
            CSharedBroadcast.sendWritten(f.anchor,b->{writes.incrementAndGet();b.put((byte)0x33);},true,-1,null);
            ck(writes.get()==1,"written source encoded more than once");
            // createPooledWritableConstBuffer is in the base socket stub.
            ck(f.successSocket.frames.size()==1,"written remaining recipient missing");
            f.successSocket.drain();ck(f.owner.releases==1,"written source leak");
        }
        {
            Fixture f=new Fixture();
            CSharedBroadcast.sendExtendedObjects(List.of(f.failed,f.success),CSharedBroadcast.FORMAT_ATTACK,0x20,1,2,3,4,5,6,7,8,9,10,11);
            f.verify("explicit-combat",1,1);
        }
        {
            Fixture f=new Fixture();
            AtomicInteger writes=new AtomicInteger();
            // World borrows item recipients, but a failed socket may not prevent
            // later players from seeing a ground item.
            L1ItemInstance item=new L1ItemInstance(777,7);
            int admitted=CSharedBroadcast.sendWrittenGroundItem(item,true,b->{writes.incrementAndGet();b.put((byte)0x70);});
            ck(admitted==1,"ground item admitted count");
            ck(f.failed.known==0 && f.success.known==1,"ground item known before admission");
            ck(writes.get()==1 && f.successSocket.frames.size()==1,"ground item source/remainder");
            f.successSocket.drain(); ck(f.owner.releases==1,"ground item leaked source");
        }
        {
            Fixture f=new Fixture();
            f.failed.team=9;f.success.team=9;
            AtomicInteger firstWrites=new AtomicInteger(),secondWrites=new AtomicInteger();
            CSharedBroadcast.NativeWireWriter[] writers={
                b->{firstWrites.incrementAndGet();b.put((byte)0x51);},
                b->{secondWrites.incrementAndGet();b.put((byte)0x52);}
            };
            CSharedBroadcast.sendWrittenMapTeamFrames(7,9,true,writers);
            ck(f.successSocket.frames.size()==2,"multi-frame team notice skipped remaining recipients");
            ck(firstWrites.get()==1 && secondWrites.get()==1,"multi-frame source encoded multiple times");
            ck((f.successSocket.frames.get(0)[2]&255)==0x51 &&
               (f.successSocket.frames.get(1)[2]&255)==0x52,"multi-frame team FIFO");
            f.successSocket.drain();ck(f.owner.releases==2,"multi-frame two native sources leaked");
        }
        {
            Fixture f=new Fixture();
            f.failedSocket.failEnqueueOnce=false;
            f.failedSocket.failOnEnqueue=2;
            f.failed.team=9;f.success.team=9;
            CSharedBroadcast.NativeWireWriter[] writers={
                b->b.put((byte)0x53), b->b.put((byte)0x54)
            };
            CSharedBroadcast.sendWrittenMapTeamFrames(7,9,true,writers);
            ck(f.failedSocket.frames.size()==1,"failing recipient first frame should remain admitted");
            ck(f.successSocket.frames.size()==2,"second team frame failed to continue others");
            f.failedSocket.drain();f.successSocket.drain();
            ck(f.owner.releases==2,"second-frame failure native sources leak");
        }
        {
            Fixture f=new Fixture();
            f.failedSocket.failEnqueueOnce=false;
            f.failed.team=9;f.success.team=9;
            AtomicInteger writes=new AtomicInteger();
            boolean threw=false;
            try {
                CSharedBroadcast.sendWrittenMapTeamFrames(7,9,true,
                    new CSharedBroadcast.NativeWireWriter[]{
                        b->{writes.incrementAndGet();throw new IllegalArgumentException("bad codec");},
                        b->{writes.incrementAndGet();b.put((byte)1);}
                    });
            } catch (IllegalArgumentException expected) {threw=true;}
            ck(threw && writes.get()==1,"broken multi-frame writer must abort once, not be retried");
            ck(f.successSocket.frames.isEmpty(),"invalid team frame delivered");
            ck(f.owner.releases==1,"invalid frame source failed to release");
        }
        {
            Fixture f=new Fixture();
            AtomicInteger visits=new AtomicInteger();
            CSharedBroadcast.sendFormatted(f.anchor,CSharedBroadcast.FORMAT_SOUND,0x19,
                1,2,3,4,false,-1,pc->{
                    if(visits.incrementAndGet()==1)
                        throw new IllegalStateException("injected recipient filter failure");
                    return true;
                });
            ck(visits.get()==2,"filter error stopped later eligible receiver");
            ck(f.failedSocket.frames.isEmpty() && f.successSocket.frames.size()==1,
                "filter failure recipient policy");
            ck(f.successSocket.creations==1,"filter failure source ownership");
            f.successSocket.drain(); ck(f.owner.releases==1,"filter failure source leak");
        }
        {
            Fixture f=new Fixture();
            AtomicInteger encodes=new AtomicInteger();
            boolean threw=false;
            try {
                CSharedBroadcast.sendWritten(f.anchor,b->{
                    encodes.incrementAndGet();
                    throw new IllegalArgumentException("invalid visual packet");
                },true,-1,null);
            } catch(IllegalArgumentException expected) { threw=true; }
            ck(threw && encodes.get()==1,"bad written source must fail once");
            ck(f.successSocket.frames.isEmpty() && f.owner.releases==1,
                "bad visual source leaked or reached next viewer");
        }
        {
            Fixture f=new Fixture();
            AtomicInteger encodes=new AtomicInteger();
            boolean threw=false;
            try {
                CSharedBroadcast.sendWrittenGroundItem(new L1ItemInstance(777,7),true,b->{
                    encodes.incrementAndGet();
                    throw new IllegalArgumentException("invalid ground item frame");
                });
            } catch(IllegalArgumentException expected) { threw=true; }
            ck(threw && encodes.get()==1,"bad item source must fail once");
            ck(f.failed.known==0 && f.success.known==0 && f.owner.releases==1,
                "invalid ground frame published to known objects or leaked");
        }
        {
            CIOServer owner=new CIOServer();
            L1World world=L1World.getInstance();
            List<L1PcInstance> players=new ArrayList<>(5000);
            for(int i=0;i<5000;i++)
                players.add(new L1PcInstance(i+1,7,new CIOClient(new PooledSocket(owner))));
            world.visible=players;
            PooledSocket first=(PooledSocket)players.get(0).client.socket;
            first.failEnqueueOnce=true;
            CSharedBroadcast.sendMove(new L1Object(9999,7),9999,3,4,5);
            int delivered=0, built=0;
            for(L1PcInstance pc:players) {
                PooledSocket socket=(PooledSocket)pc.client.socket;
                delivered+=socket.frames.size();
                built+=socket.creations;
                socket.drain();
            }
            ck(delivered==4999,"5000-recipient move should skip exactly one failed socket");
            ck(built==1,"5000-recipient move Native source built repeatedly");
            ck(owner.releases==1,"5000-recipient move Native source refcount leak");
        }
        {
            Fixture f=new Fixture();
            f.failedSocket.denySource=true;
            boolean failed=false;
            try { CSharedBroadcast.sendMove(f.anchor,999,0,0,0); }
            catch (IllegalStateException expected) { failed=true; }
            ck(failed,"fatal source construction must not masquerade as a socket failure");
            ck(f.successSocket.frames.isEmpty(),"missing source should not use alternate serializer");
        }
        System.out.println("114 production broadcast: 10 failure scenarios + 5000-recipient stress, FIFO/refs/known-object accounting, invalid encoder isolation PASS");
    }
}
