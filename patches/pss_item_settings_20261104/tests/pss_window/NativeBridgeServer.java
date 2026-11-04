package l1j.server.server.model.character.pss.window;
import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test game channel only; the separate contract exercises the real CSProto/item route. */
public final class NativeBridgeServer {
    public static void main(String[] args) throws Exception {
        try (var control=new PssWindowTransport(new InetSocketAddress("127.0.0.1",5001),8);
             var game=new ServerSocket(5000,1,InetAddress.getLoopbackAddress())) {
            game.setSoTimeout(20000);System.out.println("READY native bridge fixture");System.out.flush();
            try(var socket=game.accept()) {
                socket.setSoTimeout(15000);InputStream input=socket.getInputStream();byte[] nonce=new byte[16];int position=-1,frames=0;AtomicBoolean opened=new AtomicBoolean();
                for(;;) {
                    int low=input.read();if(low<0)break;int high=input.read();if((low|high<<8)!=7)throw new AssertionError("game frame length");
                    byte[] body=input.readNBytes(5);if(body.length!=5||body[0]!=0x51||body[3]!=0||body[4]!=0)throw new AssertionError("bootstrap frame");
                    int id=(body[1]&255)|(body[2]&255)<<8;frames++;
                    if(id==0x7df0)position=0;
                    else if((id&0xff00)==0x7e00&&position>=0&&position<16)nonce[position++]=(byte)id;
                    else if(id==0x7df1&&position==16) {
                        var key=PssWindowTransport.Token.from(nonce);
                        control.register(key,new PssWindowTransport.Owner(){
                            public boolean valid(){return !socket.isClosed();}
                            public String remoteIp(){return "127.0.0.1";}
                            public boolean attach(PssWindowTransport.Token token,PssWindowTransport.Peer peer){opened.set(peer.requestOpen());return true;}
                            public void detach(PssWindowTransport.Peer peer){}
                        });
                    }else throw new AssertionError("unexpected bootstrap sequence");
                }
                if(frames!=18||!opened.get())throw new AssertionError("bridge not opened");
                System.out.println("PASS native sender bootstrap + bound control + window-only command frames="+frames);
            }
        }
    }
}
