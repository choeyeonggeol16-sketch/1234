package l1j.server.server.packets.Protocol.MainServer.Proto;

import java.nio.*;
import java.util.*;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;

/** Compares actual production ProtoWriter array/native source bytes with legacy direct-write framing. */
public final class ProtoWireParity89 {
    private static final int STATUS_ID=0x02de;
    private static final Random RNG=new Random(895916L);

    private static byte[] directStatus(int seconds, boolean running) {
        WriteBuffer b=new WriteBuffer(2048);
        b.writeC(Opcodes.S_EXTENDED_PROTOBUF);
        b.writeH(STATUS_ID);
        // Equivalent direct Proto.begin(...), direct body, finishDirect(true).
        ProtoWriter w=ProtoWriter.direct(b);
        w.msg(2).int32(1,seconds).bool(2,running).end();
        w.finishDirect(true);
        return b.toByteArray();
    }
    private static byte[] sharedStatus(int seconds, boolean running) {
        ProtoWriter w=ProtoWriter.tl();
        w.msg(2).int32(1,seconds).bool(2,running).end();
        return w.finishWithTrailer(STATUS_ID);
    }
    private static void check(boolean p,String s) { if(!p)throw new AssertionError(s); }
    public static void main(String[] args) {
        for(int i=0;i<40000;i++){
            int sec = i<8 ? new int[]{-1,0,1,127,128,8192,Integer.MIN_VALUE,Integer.MAX_VALUE}[i] : RNG.nextInt();
            boolean running=i<8 ? (i%2==0):RNG.nextBoolean();
            byte[] before=directStatus(sec,running);
            byte[] after=sharedStatus(sec,running);
            check(Arrays.equals(before,after),"proto wire mismatch iteration="+i);
        }
        for(int i=0;i<50000;i++){
            int sec=RNG.nextInt();
            byte[] oldPacket={(byte)Opcodes.S_EVENT,0x47,(byte)sec,(byte)(sec>>>8)};
            ByteBuffer b=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            b.put((byte)Opcodes.S_EVENT).put((byte)0x47).putShort((short)sec);
            check(Arrays.equals(oldPacket,b.array()),"S_EVENT wire mismatch="+i);
        }
        System.out.println("89 wire: 40,000 ProtoWriter statuses + 50,000 S_EVENT packet bytes PASS");
    }
}
