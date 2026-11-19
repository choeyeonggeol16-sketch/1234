package l1j.server.server.model.network.core.broadcast;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.transport.CIOServer;

/** Compiles the actual production broadcast classes with phase87 socket/world stubs. */
public class WorldOptionalRegression93 {
    private static void check(boolean b,String message) {
        if(!b)throw new AssertionError(message);
    }
    private static final class Pooled extends CAsyncSendConstBuffer {
        Pooled(Source source) {super(source,Integer.MIN_VALUE,Integer.MAX_VALUE);}
    }
    public static void main(String[]args) {
        final CIOServer owner=new CIOServer();
        final CUserSocket s1=new CUserSocket(owner);
        final CUserSocket s2=new CUserSocket(owner);
        final L1PcInstance a=new L1PcInstance(1,1,new CIOClient(s1));
        final L1PcInstance b=new L1PcInstance(2,1,new CIOClient(s2));
        final CAsyncSendConstBuffer corrupt=new CAsyncSendConstBuffer(
                writer->{throw new IllegalArgumentException("injected encode failure");},
                Integer.MIN_VALUE,Integer.MAX_VALUE);
        CSharedBroadcast.sendRecipientBestEffort(corrupt,a);
        CSharedBroadcast.sendRecipientBestEffort(corrupt,b);
        check(s1.frames.isEmpty()&&s2.frames.isEmpty(),"optional visual must not send broken payload");
        CSharedBroadcast.closeOptional(corrupt);
        check(owner.releases==2,"failed encodes must release both native sources");

        boolean failed=false;
        try(var strict=new CAsyncSendConstBuffer(
                writer->{throw new IllegalArgumentException("strict error");},
                Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            CSharedBroadcast.sendRecipient(strict,a);
        } catch(IllegalArgumentException expected) { failed=true; }
        check(failed,"strict regular send must still surface failure");

        CSharedBroadcast.sendWrittenRecipientsBestEffort(List.of(a,b),
                writer->{throw new IllegalArgumentException("optional list error");});
        CSharedBroadcast.sendWrittenGroupsBestEffort(List.of(a),List.of(b),
                writer->{throw new IllegalArgumentException("optional groups error");});
        CSharedBroadcast.sendWrittenArrayBestEffort(new L1PcInstance[]{a,b},
                writer->{throw new IllegalArgumentException("optional array error");});

        // Both recipients retain exact FIFO for successful optional messages.
        CSharedBroadcast.sendWrittenGroupsBestEffort(List.of(a,b),List.of(a),
                writer->writer.put((byte)0x77));
        check(s1.frames.size()==2 && s2.frames.size()==1,"optional fanout order/duplicates");
        check(s1.frames.get(0)[2]==(byte)0x77 && s1.frames.get(1)[2]==(byte)0x77,
                "optional frame content");
        s1.drain();s2.drain();

        // One socket failing to enqueue must not prevent later recipients
        // from receiving the same immutable native frame, in FIFO order.
        final CUserSocket bad=new CUserSocket(owner);
        final CUserSocket good=new CUserSocket(owner);
        final L1PcInstance failing=new L1PcInstance(3,1,new CIOClient(bad));
        final L1PcInstance healthy=new L1PcInstance(4,1,new CIOClient(good));
        bad.failEnqueueOnce=true;
        CSharedBroadcast.sendWrittenRecipientsBestEffort(List.of(failing,healthy),
                writer->writer.put((byte)0x5A));
        check(bad.frames.isEmpty()&&good.frames.size()==1,
                "one failed socket must not cancel remaining recipients");
        check(good.frames.get(0)[2]==(byte)0x5A,"healthy recipient payload");
        check(bad.nativeAllocs==1&&good.nativeAllocs==0,
                "reuse the single source after one socket fails");
        good.drain();
        bad.failEnqueueOnce=true;
        CSharedBroadcast.sendWrittenGroupsBestEffort(List.of(failing),List.of(healthy),
                writer->writer.put((byte)0x66));
        check(good.frames.size()==2&&good.frames.get(1)[2]==(byte)0x66,
                "group-2 must receive after group-1 failure");
        good.drain();
        bad.failEnqueueOnce=true;
        CSharedBroadcast.sendWrittenArrayBestEffort(new L1PcInstance[]{failing,healthy},
                writer->writer.put((byte)0x67));
        check(good.frames.size()==3&&good.frames.get(2)[2]==(byte)0x67,
                "array recipients must continue after one failure");
        good.drain();

        // Global notifications must preserve GM/private-shop/world filters,
        // and a failed GM must not suppress the later GM.
        final L1World world=L1World.getInstance();
        failing.gm=true; healthy.gm=true;
        world.all.clear();world.all.add(failing);world.all.add(a);world.all.add(healthy);
        bad.failEnqueueOnce=true;
        CSharedBroadcast.sendWrittenAllGmsBestEffort(writer->writer.put((byte)0x70));
        check(good.frames.size()==4&&good.frames.get(3)[2]==(byte)0x70,
                "later GM must receive after one GM fails");
        check(s1.frames.size()==2,"non-GM must be excluded");
        good.drain();
        world.all.clear();world.all.add(failing);world.all.add(a);world.all.add(healthy);
        a.shop=true;
        bad.failEnqueueOnce=true;
        CSharedBroadcast.sendWrittenAllPlayableBestEffort(writer->writer.put((byte)0x71));
        check(good.frames.size()==5&&good.frames.get(4)[2]==(byte)0x71,
                "all-playable must keep going after failed socket");
        check(s1.frames.size()==2,"private shop must be excluded");
        good.drain();
        world.all.clear();

        // Strict helper: first recipient fails only after Native allocation.
        // The caller never received that created wrapper and could not close
        // it.  Confirm the method closes its own newly created source.
        final CUserSocket strictBad=new CUserSocket(owner);
        strictBad.failEnqueueOnce=true;
        final L1PcInstance firstBroken=new L1PcInstance(5,1,new CIOClient(strictBad));
        final int beforeStrictFailure=owner.releases;
        boolean strictFirstFailed=false;
        try {
            CSharedBroadcast.sendWrittenSelfAndVisible(firstBroken,
                    writer->writer.put((byte)0x22));
        } catch(IllegalStateException expected) {strictFirstFailed=true;}
        check(strictFirstFailed,"strict helper must surface first socket failure");
        check(owner.releases==beforeStrictFailure+1,
                "strict first-recipient exception must release its Native source");

        // Post-send source release exception must not replace the game result.
        final CIOServer failingOwner=new CIOServer() {
            @Override public void releaseConstBuffer(MemorySegment h){
                throw new IllegalStateException("injected source release failure");
            }
        };
        final ByteBuffer wire=ByteBuffer.allocateDirect(3).order(ByteOrder.LITTLE_ENDIAN);
        wire.putShort(0,(short)3);wire.put(2,(byte)0x12);
        CSharedBroadcast.closeOptional(new Pooled(new CAsyncSendConstBuffer.Source(
                failingOwner,MemorySegment.NULL,wire)));
        System.out.println("93 actual production optional broadcast: encode/send/release failure + strict contract + FIFO PASS");
    }
}
