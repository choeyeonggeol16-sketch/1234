import java.nio.*;
import java.util.*;
import java.nio.charset.*;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;

public final class MessageWireParity89 {
    private static byte[] wire(CSharedBroadcast.NativeWireWriter writer) {
        ByteBuffer buffer=ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN);
        writer.write(buffer);
        return Arrays.copyOf(buffer.array(),buffer.position());
    }
    public static void main(String[] args) {
        Random random=new Random(89322L);
        CUserSocket socket=new CUserSocket();
        L1PcInstance pc=new L1PcInstance(socket);
        String[] dictionary={"","테스트","테스트 ü漢字","Clan Name","₩?","\uD83D\uDE00","\\n\\t\\r","\"x\""};
        int count=0;
        for(int i=0;i<20000;i++) {
            final String a=dictionary[random.nextInt(dictionary.length)]+i;
            final String b=dictionary[random.nextInt(dictionary.length)]+(i^0x7788);
            if(!SC_ServerMessage.send(pc,95,a,b))throw new AssertionError("SC_ServerMessage direct refused");
            final byte[] actual=wire(SC_ServerMessage.nativeWriter(95,a,b,null));
            if(!Arrays.equals(socket.last,actual)) throw new AssertionError("server message bytes @"+i);
            count++;
            if(!SC_SystemMessage.send(pc,a))throw new AssertionError("SC_SystemMessage direct refused");
            if(!Arrays.equals(socket.last,wire(SC_SystemMessage.nativeWriter(a))))
                throw new AssertionError("system message bytes @"+i);
            count++;
        }
        System.out.println("89 production SC_ServerMessage/SC_SystemMessage native wire parity PASS: "+count);
    }
}
