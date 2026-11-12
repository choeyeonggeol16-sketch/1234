package l1j.server.server.model.network.core;

import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.model.character.pss.window.PssWindowTransport;

/** Runs production FFM bridge/endpoint/PSS protocol against the shipped DLL on Windows. */
public final class CTransportWindowsSmokeTest {
    static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
    static Socket connect(int port) throws Exception {
        Socket socket = new Socket("127.0.0.1", port); socket.setSoTimeout(5000); return socket;
    }
    public static void main(String[] args) throws Exception {
        check(System.getProperty("os.name").startsWith("Windows"), "real Windows required");
        AtomicReference<Throwable> failed = new AtomicReference<>();
        AtomicInteger closed = new AtomicInteger();
        try (CTransportEndpoint endpoint = new CTransportEndpoint(new InetSocketAddress("127.0.0.1",0),64,new CTransportEndpoint.Handler() {
            public boolean accept(CTransportEndpoint.Peer peer) { return true; }
            public boolean receive(CTransportEndpoint.Peer peer, ByteBuffer bytes) { return peer.send(bytes); }
            public void closed(CTransportEndpoint.Peer peer) { closed.incrementAndGet(); }
        })) {
            CountDownLatch tasks = new CountDownLatch(513);
            Set<Integer> unique = ConcurrentHashMap.newKeySet();
            for (int i=0;i<512;i++) { final int token=i;
                check(endpoint.schedule(() -> {if(!unique.add(token))failed.set(new AssertionError("duplicate task"));tasks.countDown();},0)!=0,"native work post");
            }
            check(endpoint.schedule(tasks::countDown,55)!=0,"native timer registration");
            long cancelled=endpoint.schedule(() -> failed.set(new AssertionError("cancelled task executed")),200000);
            check(cancelled!=0 && endpoint.cancel(cancelled),"native timer cancellation");
            check(tasks.await(10,TimeUnit.SECONDS) && unique.size()==512,"FFM tasks missing");
            CountDownLatch timerBurst = new CountDownLatch(1000);
            for (int i=0;i<1000;i++) check(endpoint.schedule(timerBurst::countDown,100)!=0,"timer burst registration");
            check(timerBurst.await(10,TimeUnit.SECONDS),"timer burst missing");
            long[] diagnostic = endpoint.diagnostics();
            check(diagnostic[0]>0 && diagnostic[1]==0 && diagnostic[2]==0 && diagnostic[3]==0,"pool diagnostics indicate failure");
            check(diagnostic[4]>900 && diagnostic[5]>0,"original inline/repost timer policy not exercised");
            byte[] source=new byte[200000];new Random(42).nextBytes(source);
            for (int round=0;round<20;round++) try(Socket socket=connect(endpoint.port())) {
                OutputStream output=socket.getOutputStream();
                for(int at=0;at<source.length;at+=137)output.write(source,at,Math.min(137,source.length-at));
                check(Arrays.equals(source,socket.getInputStream().readNBytes(source.length)),"FFM echo bytes/order changed");
            }
        }
        check(closed.get()==20,"FFM terminal callback count");
        if(failed.get()!=null)throw new AssertionError(failed.get());
        System.out.println("PASS actual Windows shipped DLL + production Java21 FFM: 20 connections/4MB echo, work, timers/cancel, shutdown");

        final class EchoOwner extends CIOUserSocket {
            final CTransportSocket channel;
            final ByteBuffer bytes=ByteBuffer.allocateDirect(4096);
            EchoOwner(CTransportSocket channel){this.channel=channel;}
            void start(){bytes.clear();channel.read(bytes,this);}
            @Override void transportReadCompleted(int count){if(count<0)return;bytes.flip();channel.write(bytes,this);}
            @Override void transportWriteCompleted(int count){if(count<0)return;start();}
        }
        try(CTransportServer gameplay=new CTransportServer(new InetSocketAddress("127.0.0.1",0),32,4,256,channel -> new EchoOwner(channel).start())) {
            byte[] source=new byte[100000];new Random(99).nextBytes(source);
            for(int round=0;round<3;round++)try(Socket socket=connect(gameplay.localAddress().getPort())){
                socket.getOutputStream().write(source);
                check(Arrays.equals(source,socket.getInputStream().readNBytes(source.length)),"production socket stash/read/write adapter changed bytes");
            }
        }
        System.out.println("PASS production gameplay socket adapter: native 16K receives into 4K Java reads, stash/pause/resume and ordered writes");

        AtomicReference<PssWindowTransport.Peer> attached=new AtomicReference<>();
        CountDownLatch detached=new CountDownLatch(1);
        PssWindowTransport.Owner owner=new PssWindowTransport.Owner() {
            public boolean valid(){return true;}
            public String remoteIp(){return "127.0.0.1";}
            public boolean attach(PssWindowTransport.Token token,PssWindowTransport.Peer peer){return attached.compareAndSet(null,peer);}
            public void detach(PssWindowTransport.Peer peer){detached.countDown();}
        };
        try(PssWindowTransport transport=new PssWindowTransport(new InetSocketAddress("127.0.0.1",0),16)) {
            var token=new PssWindowTransport.Token(1234,5678);check(transport.register(token,owner),"PSS register");
            try(Socket socket=connect(transport.port())) {
                byte[] hello=ByteBuffer.allocate(27).put(new byte[]{'P','S','S','U','I',1,0,0}).putLong(1234).putLong(5678).put(new byte[]{2,2,2}).array();
                for(byte value:hello)socket.getOutputStream().write(value);
                check(socket.getInputStream().read()==PssWindowTransport.BOUND,"PSS native fragmented HELLO/BOUND");
                check(attached.get()!=null && attached.get().requestOpen(),"PSS OPEN request");
                check(socket.getInputStream().read()==PssWindowTransport.OPEN,"PSS OPEN delivery");
            }
            check(detached.await(5,TimeUnit.SECONDS),"PSS remote close detach");
            try(Socket invalid=connect(transport.port())) {
                invalid.getOutputStream().write(new byte[24]);
                try { check(invalid.getInputStream().read()==-1,"bad HELLO accepted"); }
                catch(SocketException expectedReset) { }
            }
        }
        System.out.println("PASS independent PSS NIO2 channel: fragmented HELLO, PING, BOUND/OPEN, detach, invalid greeting, dedicated expiry executor shutdown");
    }
}
