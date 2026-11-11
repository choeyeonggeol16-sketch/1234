package l1j.server.server.network;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.ByteBuf;import io.netty.buffer.Unpooled;
import java.net.SocketAddress;import java.net.InetSocketAddress;import java.nio.charset.StandardCharsets;
import l1j.server.server.GameClient;import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
public class NativeStepHudChannelTest {
    static final class Peer extends EmbeddedChannel {
        Peer(){super(new NativeStepControl.Handler());}
        @Override protected SocketAddress remoteAddress0(){return new InetSocketAddress("127.0.0.1",45000);}
    }
    static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    static void input(Peer c,String s){c.writeInbound(Unpooled.copiedBuffer(s,StandardCharsets.US_ASCII));}
    static String output(Peer c){ByteBuf b=c.readOutbound();if(b==null)return null;try{return b.toString(StandardCharsets.US_ASCII);}finally{b.release();}}
    public static void main(String[] a){
        L1PcInstance p=new L1PcInstance(new GameClient("127.0.0.1",12345));L1World.getInstance().players.add(p);
        Peer c=new Peer();input(c,"HELLO 123");check(output(c)==null,"partial greeting not paired");
        input(c,"45 AT");input(c,"S1\n");check("OK\n".equals(output(c)),"ATS1 negotiated");
        NativeStepControl.send(p,"@@NATIVESTEP:START@@");NativeStepControl.sendAts(p,70380,true);
        check("S\n".equals(output(c)),"start order");check("H70380,1\n".equals(output(c)),"remaining frame");
        NativeStepControl.sendAts(p,0,false);check("H0,0\n".equals(output(c)),"hide frame");
        input(c,"U");check(p.stops==0,"split manual stop");input(c,"\n");check(p.stops==1,"manual stop once");
        c.finishAndReleaseAll();check(!NativeStepControl.isBound(p),"disconnect removes binding");
        Peer legacy=new Peer();input(legacy,"HELLO 12345\n");check("OK\n".equals(output(legacy)),"legacy paired");
        NativeStepControl.sendAts(p,100,true);check(output(legacy)==null,"legacy never gets unknown HUD frames");
        NativeStepControl.send(p,"@@NATIVESTEP:2@@");check("M2\n".equals(output(legacy)),"legacy move preserved");legacy.finishAndReleaseAll();
        Peer invalid=new Peer();input(invalid,"HELLO 12345 ATS2\n");check(!invalid.isActive(),"unknown version rejected");invalid.finishAndReleaseAll();
        L1World.getInstance().players.add(new L1PcInstance(new GameClient("127.0.0.1",23456)));
        Peer ambiguous=new Peer();input(ambiguous,"HELLO 9999 ATS1\n");check(!ambiguous.isActive(),"ambiguous NAT pairing rejected");ambiguous.finishAndReleaseAll();
        System.out.println("PASS Netty HUD: fragmented negotiation/framing/order/manual stop/legacy/disconnect/NAT ambiguity");
    }
}
