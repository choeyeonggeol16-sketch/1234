package l1j.server.server.model.network.core.broadcast;

import java.util.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

/** Runs real production broadcast+SC_ChatPacket classes with typed socket stubs. */
public final class BroadcastBoundary102 {
    private static void ck(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        CIOServer owner=new CIOServer();
        CUserSocket exploding=new CUserSocket(owner) {
            @Override public boolean enqueueConstBufferConfirmed(
                    CAsyncSendConstBuffer.Source source,int pendingLimit) {
                throw new AssertionError("injected first-recipient fatal send");
            }
        };
        L1PcInstance fatal=new L1PcInstance(301,1,new CIOClient(exploding));
        boolean propagated=false;
        try {
            CSharedBroadcast.sendWrittenRecipientsBestEffort(List.of(fatal),
                    out->out.put((byte)0x42));
        } catch(AssertionError expected) { propagated=true; }
        ck(propagated,"fatal Error must propagate");
        ck(owner.releases==1,
                "first optional recipient Error must release newly created Native source");
        ck(exploding.nativeAllocs==1,"fatal injected after Native source allocation");

        // Release errors must be attached to the original fatal SEND error,
        // not silently replace it. This failure is deliberately injected.
        CIOServer releaseFailureOwner=new CIOServer() {
            @Override public void releaseConstBuffer(java.lang.foreign.MemorySegment handle) {
                throw new IllegalStateException("injected Native source release error");
            }
        };
        CUserSocket bothBad=new CUserSocket(releaseFailureOwner) {
            @Override public boolean enqueueConstBufferConfirmed(
                    CAsyncSendConstBuffer.Source source,int pendingLimit) {
                throw new AssertionError("original SEND Error");
            }
        };
        try {
            CSharedBroadcast.sendWrittenArrayBestEffort(
                    new L1PcInstance[]{new L1PcInstance(307,1,new CIOClient(bothBad))},
                    out->out.put((byte)0x55));
            throw new AssertionError("fatal SEND Error missing");
        } catch (AssertionError expected) {
            ck("original SEND Error".equals(expected.getMessage()),
                    "cleanup failure must not replace primary SEND error");
            ck(expected.getSuppressed().length==1,
                    "Native source release failure must remain observable");
        }

        // Ordinary recipient failure must still continue with a shared source.
        CUserSocket bad=new CUserSocket(owner), good=new CUserSocket(owner);
        bad.failEnqueueOnce=true;
        L1PcInstance a=new L1PcInstance(302,1,new CIOClient(bad));
        L1PcInstance b=new L1PcInstance(303,1,new CIOClient(good));
        int before=owner.releases;
        CSharedBroadcast.sendWrittenRecipientsBestEffort(List.of(a,b),
                out->out.put((byte)0x43));
        ck(bad.frames.isEmpty() && good.frames.size()==1 && good.frames.get(0)[2]==(byte)0x43,
                "ordinary failed recipient must not cancel the following recipient");
        good.drain();
        ck(owner.releases==before+1,"shared source must return exactly once after consumers release");

        // Real native chat broadcast implementations, including 16KiB overflow fallback.
        L1World world=L1World.getInstance();
        world.visible=new ArrayList<>(List.of(a,b));
        world.all=new ArrayList<>(List.of(a,b));
        String valid="x".repeat(20000);
        int messages=good.frames.size();
        SC_ChatPacket.broadcastMessage(valid);
        ck(good.frames.size()==messages+1,"valid 20,000-byte chat broadcast silently dropped");
        byte[] msg=good.frames.get(messages);
        ck(msg.length==20005,"20k ASCII chat frame length");
        ck(((msg[0]&255)|((msg[1]&255)<<8))==msg.length,"20k chat header length");
        ck(msg[2]==(byte)l1j.server.server.Opcodes.S_MESSAGE && msg[3]==1,
                "broadcastMessage opcode/type unchanged");
        ck(msg[4]=='x' && msg[msg.length-1]==0,"chat payload/trailing NUL unchanged");
        SC_ChatPacket.broadcastOpcode(valid,l1j.server.server.Opcodes.S_SAY);
        ck(good.frames.size()==messages+2,"valid long broadcastOpcode dropped");
        ck(good.frames.get(messages+1)[2]==(byte)l1j.server.server.Opcodes.S_SAY,
                "broadcastOpcode must preserve caller opcode");
        SC_ChatPacket.broadcastComplex0(a,valid,l1j.server.server.Opcodes.S_SAY);
        ck(good.frames.size()==messages+3,"valid long broadcastComplex0 dropped");
        ck(world.releases>=1,"borrowed AOI list must be returned");
        ck(SC_ChatPacket.nativeTargetWriter("To",1,valid)!=null,
                "valid long nativeTargetWriter incorrectly rejected");
        int blocked=good.frames.size();
        SC_ChatPacket.broadcastMessage("x".repeat(65531));
        ck(good.frames.size()==blocked,"oversized >16-bit packet must be rejected");
        SC_ChatPacket.broadcastMessage("x".repeat(65530));
        ck(good.frames.size()==blocked+1,"maximum 65535-byte frame must be accepted");
        ck(good.frames.get(blocked).length==65535,"maximum frame exact length");
        SC_ChatPacket.broadcastMessage("한".repeat(20000));
        ck(good.frames.size()==blocked+2,"valid 40k-byte Korean MS949 frame must be accepted");
        ck(good.frames.get(blocked+1).length==40005,"Korean MS949 wire byte count");
        SC_ChatPacket.broadcastMessage("한".repeat(33000));
        ck(good.frames.size()==blocked+2,"over-maximum Korean MS949 frame must be rejected");
        good.drain();bad.drain();
        System.out.println("102 production broadcast: fatal first-recipient source release, "
                +"ordinary FIFO continuation, 20k/65535 MS949 chat acceptance/rejection PASS");
    }
}
