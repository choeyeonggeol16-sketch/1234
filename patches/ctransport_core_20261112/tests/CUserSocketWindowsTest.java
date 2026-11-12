package l1j.server.server.model.network.core;

import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Real shipped DLL/FFM/socket/codec; only the game-world CIOUserSocket is stubbed. */
public final class CUserSocketWindowsTest {
    static void check(boolean value, String why) { if(!value)throw new AssertionError(why); }
    static void until(BooleanSupplier test) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!test.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(1);
        check(test.getAsBoolean(),"Windows user-socket deadline");
    }
    static byte[] frame(int n,int seed) {
        byte[] frame=new byte[n];frame[0]=(byte)n;frame[1]=(byte)(n>>>8);
        for(int i=2;i<n;i++)frame[i]=(byte)(i*seed+17);return frame;
    }
    static int decode(CIOPacketCodec codec,List<byte[]> out) {
        int count=0;
        for(;;){int n=codec.next();if(n==CIOPacketCodec.INCOMPLETE)return count;
            if(n!=CIOPacketCodec.EMPTY){byte[] bytes=new byte[n];codec.plaintext().duplicate().get(bytes);out.add(bytes);}count++;}
    }
    public static void main(String[] args) throws Exception {
        List<byte[]> input=new ArrayList<>();input.add(frame(2,1));input.add(frame(10,3));input.add(frame(65535,7));input.add(frame(211,9));
        for(int n=0;n<64;n++)input.add(frame(10,n+1));
        int size=input.stream().mapToInt(b->b.length).sum();byte[] wire=new byte[size];int at=0;
        for(byte[] frame:input){System.arraycopy(frame,0,wire,at,frame.length);at+=frame.length;}
        List<byte[]> expected=new ArrayList<>();
        try(CIOPacketCodec codec=new CIOPacketCodec(73461)) {
            ByteBuffer bytes=ByteBuffer.allocateDirect(1024);
            for(at=0;at<wire.length;){int n=Math.min(bytes.capacity(),wire.length-at);bytes.clear();bytes.put(wire,at,n).flip();codec.feed(bytes);decode(codec,expected);at+=n;}
            check(codec.pending()==0,"reference codec retained complete frames");
        }
        AtomicReference<Throwable> failure=new AtomicReference<>();
        List<byte[]> actual=Collections.synchronizedList(new ArrayList<>());
        AtomicInteger frames=new AtomicInteger(),reads=new AtomicInteger();
        AtomicReference<CTransportSocket> accepted=new AtomicReference<>();
        AtomicReference<Runnable> resume=new AtomicReference<>();
        CountDownLatch closed=new CountDownLatch(1);
        AtomicBoolean held=new AtomicBoolean();
        final class UserOwner extends CIOUserSocket {
            final CTransportSocket channel; final CIOPacketCodec codec=new CIOPacketCodec(73461);
            final ByteBuffer bytes=ByteBuffer.allocateDirect(4096);boolean done;
            UserOwner(CTransportSocket channel){this.channel=channel;check(channel.enableUserSocket(),"native L1 user profile denied");channel.bindUserSocketOwner(this);}
            void read(){bytes.clear();channel.read(bytes,this);}
            @Override void transportReadCompleted(int n){
                if(n<0){if(!done){done=true;codec.close();closed.countDown();}return;}
                try{reads.incrementAndGet();check(channel.currentNativeOwner()>=0,"wire decode escaped IOCP worker");bytes.flip();codec.feed(bytes);frames.addAndGet(decode(codec,actual));
                    if(frames.get()==2&&held.compareAndSet(false,true))return;
                    read();}
                catch(Throwable problem){failure.compareAndSet(null,problem);channel.close();}
            }
        }
        try(CTransportServer server=new CTransportServer(new InetSocketAddress("127.0.0.1",0),8,2,32,channel->{UserOwner owner=new UserOwner(channel);accepted.set(channel);resume.set(owner::read);owner.read();});
            Socket client=new Socket("127.0.0.1",server.localAddress().getPort())) {
            client.setSoTimeout(5000);until(()->accepted.get()!=null);
            client.getOutputStream().write(wire,0,1);
            until(()->{long[] state=accepted.get().userSocketState();return state!=null&&state[0]==0&&state[2]==1;});
            check(reads.get()==0,"partial wire header reached codec/handler");
            client.getOutputStream().write(wire,1,wire.length-1);
            until(held::get);Thread.sleep(20);
            check(accepted.get().userSocketState()[3]==2,"assembler ignored frame-boundary backpressure");
            check(server.postCompletion(new CTransportTask(){public void run(){resume.get().run();}public void cancel(){failure.set(new AssertionError("resume cancelled"));}}),"READ resume completion denied");
            until(()->frames.get()==input.size()||failure.get()!=null);
            if(failure.get()!=null)throw new AssertionError(failure.get());
            check(actual.size()==expected.size(),"L1 plaintext frame count");
            for(int n=0;n<expected.size();n++)check(Arrays.equals(expected.get(n),actual.get(n)),"C2S rolling key or plaintext changed");
            until(()->{long[] state=accepted.get().userSocketState();return state!=null&&state[0]==0&&state[3]==input.size();});
            client.getOutputStream().write(new byte[]{3,0});
            check(closed.await(5,TimeUnit.SECONDS),"invalid L1 length did not close game socket");
            check(accepted.get().isNativeIoDrained(),"Java abort was mistaken for native I/O terminal");
        }
        System.out.println("PASS actual Windows CUserSocket/FFM/socket + unchanged L1 codec: partial header held, frame-boundary backpressure/resume, empty/65535/coalesced frames, 67 exact plaintext/rolling-key matches, invalid-length close, native worker identity");

        AtomicInteger ran=new AtomicInteger(),cancelled=new AtomicInteger();
        CountDownLatch blockers=new CountDownLatch(2),release=new CountDownLatch(1);
        CTransportServer server=new CTransportServer(new InetSocketAddress("127.0.0.1",0),8,2,32,channel->channel.close());
        check(server.currentNativeOwner()==-1,"external Java thread reported native worker identity");
        CTransportTask blocker=new CTransportTask(){
            public void run(){check(server.currentNativeOwner()>=0,"completion task escaped IOCP");blockers.countDown();try{check(release.await(5,TimeUnit.SECONDS),"blocker release");}catch(InterruptedException e){throw new AssertionError(e);}}
            public void cancel(){throw new AssertionError("running task cancelled");}
        };
        check(server.postCompletion(blocker)&&server.postCompletion(blocker),"blocker posts denied");
        check(blockers.await(5,TimeUnit.SECONDS),"workers did not enter blockers");
        for(int n=0;n<64;n++)check(server.postCompletion(new CTransportTask(){public void run(){ran.incrementAndGet();}public void cancel(){cancelled.incrementAndGet();}}),"packet completion admission");
        AtomicReference<Throwable> closeFailure=new AtomicReference<>();
        Thread stop=new Thread(()->{try{server.close();}catch(Throwable e){closeFailure.set(e);}});stop.start();
        until(server::isStopping);release.countDown();stop.join(5000);
        check(!stop.isAlive()&&closeFailure.get()==null,"native completion shutdown failed");
        check(ran.get()==0&&cancelled.get()==64,"shutdown stranded/ran/duplicated an admitted packet lease");
        check(!server.postCompletion(blocker),"post accepted after shutdown");
        System.out.println("PASS actual Windows native packet completions: IOCP worker TLS identity, 64 pending packet leases cancel once on shutdown, no post after stop");
    }
}
