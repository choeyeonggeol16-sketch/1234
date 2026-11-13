package l1j.server.server.model.network.core;

import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Real Windows DLL/FFM/socket/codec; the game-world owner is a test fixture. */
public final class CUserSocketWindowsTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void until(BooleanSupplier test)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!test.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(1);
        check(test.getAsBoolean(),"Windows user-socket deadline");
    }
    static byte[] frame(int n,int seed){byte[] b=new byte[n];b[0]=(byte)n;b[1]=(byte)(n>>>8);for(int i=2;i<n;i++)b[i]=(byte)(i*seed+17);return b;}
    static void decode(CIOPacketCodecReference codec,List<byte[]> out){
        for(;;){int n=codec.next();if(n==CIOPacketCodecReference.INCOMPLETE)return;
            if(n!=CIOPacketCodecReference.EMPTY){byte[] bytes=new byte[n];codec.plaintext().duplicate().get(bytes);out.add(bytes);}}
    }
    static final class UserOwner extends CUserSocket {
        final CSocket channel;final CIOPacketCodec codec;
        final AtomicInteger frames=new AtomicInteger(),deadlines=new AtomicInteger();
        final List<byte[]> actual=Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch terminal=new CountDownLatch(1);
        volatile boolean hold=true;volatile long sequence;volatile ByteBuffer view;
        UserOwner(CSocket channel){this.channel=channel;codec=new CIOPacketCodec(channel,73461);channel.bindUserSocketOwner(this);}
        @Override boolean transportNativePacket(long sequence,ByteBuffer packet,int length){
            check(channel.currentNativeOwner()>=0,"plaintext escaped IOCP worker");
            if(length==0){frames.incrementAndGet();return true;}
            byte[] bytes=new byte[length];packet.duplicate().get(bytes);actual.add(bytes);
            if(hold){hold=false;view=packet;this.sequence=sequence;frames.incrementAndGet();return true;}
            check(channel.releaseUserPacket(sequence),"native plaintext release");frames.incrementAndGet();return true;
        }
        @Override int transportNetworkDeadline(){deadlines.incrementAndGet();return 5000;}
        @Override void onTransportTerminated(){terminal.countDown();}
        @Override void close(){channel.close();}
    }
    static byte[] copy(ByteBuffer source){byte[] out=new byte[source.remaining()];source.duplicate().get(out);return out;}
    public static void main(String[] args)throws Exception{
        List<byte[]> input=new ArrayList<>();input.add(frame(2,1));input.add(frame(10,3));input.add(frame(65535,7));input.add(frame(211,9));
        for(int n=0;n<64;n++)input.add(frame(10,n+1));
        int size=input.stream().mapToInt(b->b.length).sum();byte[] wire=new byte[size];int at=0;
        for(byte[] frame:input){System.arraycopy(frame,0,wire,at,frame.length);at+=frame.length;}
        List<byte[]> expected=new ArrayList<>();
        try(CIOPacketCodecReference codec=new CIOPacketCodecReference(73461)){
            ByteBuffer bytes=ByteBuffer.allocateDirect(1024);
            for(at=0;at<wire.length;){int n=Math.min(bytes.capacity(),wire.length-at);bytes.clear();bytes.put(wire,at,n).flip();codec.feed(bytes);decode(codec,expected);at+=n;}
            check(codec.pending()==0,"reference codec retained complete frames");
        }
        AtomicReference<UserOwner> accepted=new AtomicReference<>();
        try(CIOServer server=new CIOServer(new InetSocketAddress("127.0.0.1",0),8,2,32,channel->{UserOwner owner=new UserOwner(channel);accepted.set(owner);channel.pauseNativeUser(false);});
            Socket client=new Socket("127.0.0.1",server.localAddress().getPort())){
            until(()->accepted.get()!=null);UserOwner owner=accepted.get();
            int number=(int)owner.channel.socketNumber();
            check(number!=0&&server.findByNumber(number)!=0,"native user number lookup");
            until(()->server.threadDiagnostics()[51]>0);
            long[] listener=server.listenerDiagnostics(),timing=server.threadDiagnostics();
            check(listener.length==9&&listener[0]==1&&listener[1]>0&&listener[2]>0&&listener[6]==1&&listener[8]==1,"Windows event accept/map diagnostics");
            check(timing.length==54&&timing[51]>0,"Windows ListenThread owner17 timing");
            long[][] rows=server.ReportSocket();check(rows.length==1&&rows[0][1]==Integer.toUnsignedLong(number)&&rows[0][10]==0x7f000001L,"Windows ReportSocket peer/number");
            check(owner.channel.setMacIdentity(0x123456789abcL)&&!owner.channel.setMacIdentity(7),"Windows trusted MAC one-time binding");
            client.getOutputStream().write(wire,0,1);
            until(()->{long[] state=owner.channel.userSocketState();return state!=null&&state[0]==0&&state[2]==1;});
            check(owner.frames.get()==0,"partial wire reached plaintext handler");
            client.getOutputStream().write(wire,1,wire.length-1);until(()->owner.sequence!=0);long seq=owner.sequence;
            byte[] held=copy(owner.view);check(Arrays.equals(held,expected.get(0)),"native first decode");
            ByteBuffer out=ByteBuffer.allocateDirect(256),ref=ByteBuffer.allocateDirect(256);
            try(CIOPacketCodecReference codec=new CIOPacketCodecReference(73461)){
                for(int round=0;round<8;round++){
                    out.clear();ref.clear();for(int n=0;n<256;n++){out.put((byte)(n+round));ref.put((byte)(n+round));}
                    owner.codec.encrypt(out,0,256);codec.encrypt(ref,0,256);out.flip();ref.flip();
                    check(Arrays.equals(copy(out),copy(ref)),"native S2C rolling encryption changed");
                }
            }
            check(Arrays.equals(held,copy(owner.view))&&owner.frames.get()==2,"send or next overwrote borrowed plaintext");
            check(!owner.channel.releaseUserPacket(seq+1),"wrong sequence released packet");
            check(owner.channel.releaseUserPacket(seq),"foreign-thread native release rejected");
            until(()->owner.frames.get()==input.size());
            check(!owner.channel.releaseUserPacket(seq),"duplicate release accepted");
            check(owner.actual.size()==expected.size(),"L1 plaintext count");
            for(int n=0;n<expected.size();n++)check(Arrays.equals(expected.get(n),owner.actual.get(n)),"C2S plaintext/rolling key changed at "+n);
            client.getOutputStream().write(new byte[]{3,0});check(owner.terminal.await(5,TimeUnit.SECONDS),"invalid length did not close");
            check(owner.channel.isNativeIoDrained(),"terminal flag is not real I/O completion");
            until(()->server.registryDiagnostics()[0]==0&&server.registryDiagnostics()[1]==0);
        }
        System.out.println("PASS Windows direct C++ codec/FFM: partial/empty/65535/coalesced frames, 67 exact C2S plaintext/rolling-key matches, 8 S2C matches, stable plaintext lease, foreign/duplicate release and invalid-length terminal");

        AtomicReference<UserOwner> heldOwner=new AtomicReference<>();
        try(CIOServer server=new CIOServer(new InetSocketAddress("127.0.0.1",0),8,2,32,channel->{UserOwner owner=new UserOwner(channel);heldOwner.set(owner);channel.pauseNativeUser(false);})){
            UserOwner old;
            try(Socket client=new Socket("127.0.0.1",server.localAddress().getPort())){
                until(()->heldOwner.get()!=null);old=heldOwner.get();client.getOutputStream().write(frame(128,5));until(()->old.sequence!=0);
                byte[] before=copy(old.view);old.close();check(old.terminal.await(5,TimeUnit.SECONDS),"held terminal");
                check(server.registryDiagnostics()[0]==0&&server.registryDiagnostics()[1]==1,"retired context lost outstanding lease");
                check(Arrays.equals(before,copy(old.view)),"terminal freed borrowed plaintext");check(old.channel.releaseUserPacket(old.sequence),"retired native release");
                until(()->server.registryDiagnostics()[1]==0);
            }
            heldOwner.set(null);
            try(Socket client=new Socket("127.0.0.1",server.localAddress().getPort())){
                until(()->heldOwner.get()!=null);UserOwner replacement=heldOwner.get();
                check(!old.channel.releaseUserPacket(old.sequence)&&replacement.channel.isOpen(),"stale handle affected replacement");
                check(replacement.channel.configureDeadlines(80)&&replacement.channel.setUserState(1),"native login/auth configuration");
                until(()->replacement.deadlines.get()>0);check(replacement.channel.isOpen(),"authenticated connection hit login deadline");
                int calls=replacement.deadlines.get();check(replacement.channel.requestDeadline(1),"earlier native deadline");until(()->replacement.deadlines.get()>calls);
                replacement.close();check(replacement.terminal.await(5,TimeUnit.SECONDS),"auth close");
            }
            heldOwner.set(null);
            try(Socket client=new Socket("127.0.0.1",server.localAddress().getPort())){
                until(()->heldOwner.get()!=null);UserOwner login=heldOwner.get();check(login.channel.configureDeadlines(60),"native login timer");
                check(login.terminal.await(5,TimeUnit.SECONDS),"native login timeout failed");
            }
        }
        System.out.println("PASS Windows native registry/timers: retired plaintext survives I/O terminal, release removes context, stale handles rejected, login timeout/auth cancellation/earlier callback deadline");

        AtomicInteger ran=new AtomicInteger(),cancelled=new AtomicInteger();
        CountDownLatch blockers=new CountDownLatch(2),release=new CountDownLatch(1);
        CIOServer server=new CIOServer(new InetSocketAddress("127.0.0.1",0),8,2,32,channel->channel.close());
        check(server.currentNativeOwner()==-1,"external Java thread reported native worker identity");
        CIOObject blocker=new CIOObject(){
            public void run(){check(server.currentNativeOwner()>=0,"completion task escaped IOCP");blockers.countDown();try{check(release.await(5,TimeUnit.SECONDS),"blocker release");}catch(InterruptedException e){throw new AssertionError(e);}}
            public void cancel(){throw new AssertionError("running task cancelled");}
        };
        // A batched IOCP dequeue may take both posts onto the same worker.
        // Enter the first blocker before posting the second, so two distinct
        // workers are occupied rather than blocking an unexecuted batch entry.
        check(server.postCompletion(blocker),"first blocker post denied");
        until(()->blockers.getCount()==1);
        check(server.postCompletion(blocker),"second blocker post denied");
        check(blockers.await(5,TimeUnit.SECONDS),"workers did not enter blockers");
        for(int n=0;n<64;n++)check(server.postCompletion(new CIOObject(){public void run(){ran.incrementAndGet();}public void cancel(){cancelled.incrementAndGet();}}),"packet completion admission");
        AtomicReference<Throwable> closeFailure=new AtomicReference<>();
        Thread stop=new Thread(()->{try{server.close();}catch(Throwable e){closeFailure.set(e);}});stop.start();
        until(server::isStopping);release.countDown();stop.join(5000);
        check(!stop.isAlive()&&closeFailure.get()==null,"native completion shutdown failed");
        check(ran.get()==0&&cancelled.get()==64,"shutdown stranded/ran/duplicated an admitted packet lease");
        check(!server.postCompletion(blocker),"post accepted after shutdown");
        System.out.println("PASS actual Windows native packet completions: IOCP worker TLS identity, 64 pending packet leases cancel once on shutdown, no post after stop");
    }
}
