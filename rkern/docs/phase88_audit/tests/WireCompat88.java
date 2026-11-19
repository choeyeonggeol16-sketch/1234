import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_Pledge;
import l1j.server.server.model.L1Clan;

/** Real production SC packet methods against tiny byte-capture socket stubs. */
public final class WireCompat88 {
    static byte[] writer(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter w) {
        ByteBuffer b=ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN);
        w.write(b);
        return Arrays.copyOf(b.array(),b.position());
    }
    static void eq(byte[] old,byte[] now,String where) {
        if(!Arrays.equals(old,now)) throw new AssertionError(where+" old="+Arrays.toString(old)+" new="+Arrays.toString(now));
    }
    public static void main(String[] args) {
        CUserSocket socket=new CUserSocket();
        L1PcInstance pc=new L1PcInstance(socket);
        Random r=new Random(880716L);
        int tested=0;
        for(int i=0;i<10000;i++){
            int id=r.nextInt();
            int gfx=r.nextInt();
            if(!SC_SkillSound.send(pc,id,gfx)) throw new AssertionError("skill send rejected");
            eq(socket.last,writer(SC_SkillSound.nativeWriter(id,gfx)),"skill#"+i);
            int x=r.nextInt(),y=r.nextInt(),effect=r.nextInt();
            if(!SC_EffectLocation.send(pc,x,y,effect)) throw new AssertionError("effect send rejected");
            eq(socket.last,writer(SC_EffectLocation.nativeWriter(x,y,effect)),"effect#"+i);
            long packed=SC_EffectLocation.pack(x,y,effect);
            eq(socket.last,writer(SC_EffectLocation.nativeWriter((int)(packed>>32)&0xffff,
                    (int)(packed>>16)&0xffff,(int)packed&0xffff)),"packed effect#"+i);
            tested+=3;
        }
        // Clan tick: all members receive the same state under the clan monitor.
        for(int i=0;i<10000;i++){
            int[] values=new int[r.nextInt(8)];
            for(int j=0;j<values.length;j++) {
                int mode=r.nextInt(7);
                values[j]=switch(mode){case 0->0; case 1->1; case 2->172800; case 3->-1; case 4->Integer.MIN_VALUE; case 5->Integer.MAX_VALUE; default -> r.nextInt(500000);};
            }
            L1Clan clan=new L1Clan(r.nextLong(),values);
            int bless=1+r.nextInt(4);
            if(!SC_Pledge.sendBless(pc,clan,bless))throw new AssertionError("bless send rejected");
            eq(socket.last,writer(SC_Pledge.nativeBlessWriter(clan,bless)),"clan bless#"+i);
            tested++;
        }
        System.out.println("Phase88 production SC SkillSound/EffectLocation/Pledge wire parity PASS: "+tested);
    }
}
