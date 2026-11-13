package l1j.server.server.model.network.core;

import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import l1j.server.server.model.network.packet.BytesWritablePacket;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/** Actual production FIFO + pools + FFM + Windows DLL, distinct L1 rolling keys. */
public final class CSharedBroadcastWindowsTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void until(BooleanSupplier test)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!test.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(1);
        check(test.getAsBoolean(),"shared broadcast deadline");
    }
    static int sources(CIOServer server)throws Exception {
        var field=CIOServer.class.getDeclaredField("constBuffers");field.setAccessible(true);
        return ((AtomicInteger)field.get(server)).get();
    }
    static final class World implements AutoCloseable {
        final CIOBufferPool pool=new CIOBufferPool(8,64,4,4);
        final CUserSocket[] users=new CUserSocket[4];final CIOClient[] clients=new CIOClient[4];
        final Socket[] peers=new Socket[4];final CIOPacketCodecReference[] codecs=new CIOPacketCodecReference[4];
        final CountDownLatch admitted=new CountDownLatch(4),closed=new CountDownLatch(4);
        final AtomicInteger next=new AtomicInteger();final CIOServer server;
        World()throws Exception {
            server=new CIOServer(new InetSocketAddress("127.0.0.1",0),8,2,32,channel->{
                int i=next.getAndIncrement();
                CUserSocket user=new CUserSocket(channel,pool,CIOThreadCallback.INSTANCE,"127.0.0.1",0,64,x->closed.countDown());
                CIOClient client=new CIOClient(user);user.bindClient(client);user.SetKey(73461+i);
                user.configureNativeDeadlines(false);users[i]=user;clients[i]=client;user.startRead();admitted.countDown();
            });
            for(int i=0;i<4;i++){peers[i]=new Socket("127.0.0.1",server.localAddress().getPort());peers[i].setSoTimeout(5000);codecs[i]=new CIOPacketCodecReference(73461+i);}
            check(admitted.await(5,TimeUnit.SECONDS),"four shared recipients");
            byte[] key={1,2,3,4,5,6,7,8};
            for(int i=0;i<4;i++){check(users[i].tryWritePacket(clients[i],new BytesWritablePacket(key)),"plain handshake");read(i,key,true);}
            for(CUserSocket user:users)until(user::isWriteIdle);
        }
        void read(int i,byte[] body,boolean plain)throws Exception {
            ByteBuffer data=ByteBuffer.allocateDirect(body.length+2).order(ByteOrder.LITTLE_ENDIAN);
            data.putShort((short)(body.length+2)).put(body);
            if(!plain)codecs[i].encrypt(data,2,body.length);
            byte[] expected=new byte[data.capacity()];data.clear();data.get(expected);
            byte[] actual=peers[i].getInputStream().readNBytes(expected.length);
            check(Arrays.equals(actual,expected),"distinct-key/FIFO wire mismatch recipient "+i);
        }
        @Override public void close()throws Exception {
            for(Socket peer:peers)if(peer!=null)peer.close();server.close();
            check(closed.await(5,TimeUnit.SECONDS),"shared recipients close stranded");
            for(CIOPacketCodecReference codec:codecs)if(codec!=null)codec.close();
            check(pool.usedRecvSlots()==0&&pool.usedSendSlots()==0&&sources(server)==0,"shared Java source/lease leak");
        }
    }
    static void fill(CIOFrame frame,byte[] bytes) {
        WriteBuffer writer=frame.buffer();for(byte value:bytes)writer.writeC(value&255);
    }
    public static void main(String[] args)throws Exception {
        byte[] frame=ByteBuffer.allocate(16384).order(ByteOrder.LITTLE_ENDIAN).putShort((short)16384).array();
        for(int i=2;i<frame.length;i++)frame[i]=(byte)(i*17+9);
        byte[] body=Arrays.copyOfRange(frame,2,frame.length),before=frame.clone();
        byte[] prefix={7,6,5,4,3,2,1,0},suffix={9,8,7,6,5,4,3,2};
        try(World world=new World()) {
            CIOFrame reserved=world.users[0].beginDirectFrame(10);check(reserved!=null,"earlier unpublished FIFO encoder");
            fill(reserved,prefix);
            try(var broadcast=new CAsyncSendConstBufferExceptSelf(frame,999,Integer.MAX_VALUE)) {
                for(int i=0;i<4;i++)check(broadcast.apply(world.users[i],i==3?999:100+i),"shared broadcast unavailable");
                check(world.users[0].tryWritePacket(world.clients[0],new BytesWritablePacket(suffix)),"following ordinary packet");
            }
            world.read(1,body,false);world.read(2,body,false);
            check(world.users[3].isWriteIdle(),"ExceptSelf admitted excluded recipient");
            world.peers[0].setSoTimeout(150);
            try{world.peers[0].getInputStream().read();throw new AssertionError("shared packet overtook unpublished encoder");}
            catch(SocketTimeoutException expected){}finally{world.peers[0].setSoTimeout(5000);}
            check(world.users[0].commitDirectFrame(reserved),"prefix encoder commit");
            world.read(0,prefix,false);world.read(0,body,false);world.read(0,suffix,false);
            until(()->{try{return sources(world.server)==0;}catch(Exception e){throw new RuntimeException(e);}});
            check(Arrays.equals(frame,before),"producer plaintext mutated");

            CIOFrame blocked=world.users[1].beginDirectFrame(10);check(blocked!=null,"cancel reservation");
            try(var broadcast=new CAsyncSendConstBufferExceptSelf(frame,999,Integer.MAX_VALUE)) {
                check(broadcast.apply(world.users[1],101)&&broadcast.apply(world.users[2],102),"cancel broadcast");
            }
            world.users[1].close();world.users[1].abortDirectFrame(blocked);
            world.read(2,body,false);
            until(()->{try{return sources(world.server)==0;}catch(Exception e){throw new RuntimeException(e);}});

            CIOFrame pressure=world.users[2].beginDirectFrame(10);check(pressure!=null,"pending limit reservation");
            try(var limited=new CAsyncSendConstBufferExceptSelf(frame,999,1)) {
                check(limited.apply(world.users[2],102)&&sources(world.server)==0,"pending limit did not skip before allocation");
            }
            world.users[2].abortDirectFrame(pressure);

            var producer=new CAsyncSendConstBuffer(frame,999,Integer.MAX_VALUE);
            check(producer.apply(world.users[3],103),"shutdown producer send");world.read(3,body,false);
            until(world.users[3]::isWriteIdle);check(sources(world.server)==1,"producer does not pin FFM source");
            try { world.server.closeWithBudget(TimeUnit.MILLISECONDS.toNanos(700));throw new AssertionError("DLL arena freed under live producer"); }
            catch(java.io.IOException expected){check(expected.getMessage().contains("release downcall"),"unexpected shutdown phase: "+expected);}
            producer.close();world.server.closeWithBudget(TimeUnit.SECONDS.toNanos(3));
        }
        System.out.println("PASS actual Windows shared broadcast:one16K source,three distinct L1 rolling keys,ExceptSelf,unpublished encoder/shared/ordinary FIFO,close isolation,pending limit,zero refs/leases,live producer pins DLL across bounded shutdown and retry");
    }
}
