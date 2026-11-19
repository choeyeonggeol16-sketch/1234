package l1j.server.server.model.network.core.broadcast;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.transport.CIOServer;
public final class BroadcastStress87 {
 static void check(boolean value,String detail) {if(!value)throw new AssertionError(detail);}
 static void run() {
  final int[] sizes={3,8,9,64,8192,16384,16385,32768,65535};
  final int clients=300, iterations=5;
  final CIOServer a=new CIOServer(),b=new CIOServer();
  final CUserSocket[] sockets=new CUserSocket[clients];
  final int[] next=new int[clients];
  final Random rand=new Random(870216);
  for(int i=0;i<clients;i++){
   sockets[i]=new CUserSocket(i%3==0?b:a);
   sockets[i].constCapable=i%5!=0;
   sockets[i].admit=i%7!=0;
   sockets[i].connected=i%11!=0;
  }
  AtomicInteger assemblies=new AtomicInteger();
  for(int trial=0;trial<iterations;trial++){
   for(int size:sizes){
    final int fsize=size, seed=trial+7;
    final CAsyncSendConstBuffer shared=new CAsyncSendConstBufferExceptSelf(out->{
     assemblies.incrementAndGet();
     while(out.position()<fsize)out.put((byte)(out.position()*17+seed));
    },42,Integer.MAX_VALUE);
    final int before=assemblies.get(),releaseBefore=a.releases+b.releases;
    try {
     for(int t=0;t<clients;t++){
      int i=rand.nextInt(clients);
      CUserSocket socket=sockets[i];
      int prior=socket.frames.size();
      boolean accepted=shared.send(socket,i);
      boolean should=socket.connected&&i!=42;
      check(accepted==should,"admission [trial="+trial+",size="+size+",id="+i+"]");
      check(socket.frames.size()==prior+(should?1:0),"frame count mismatch");
      if(should){
       byte[] data=socket.frames.get(prior);
       check(data.length==size && ((data[0]&255)|((data[1]&255)<<8))==size,"wire header");
       for(int k=2;k<size;k++)check(data[k]==(byte)(k*17+seed),"payload content");
      }
     }
    } finally {shared.close();shared.close();}
    check(assemblies.get()-before==(size>16384?2:1),"assembly count");
    for(CUserSocket s:sockets)s.drain();
    check(a.releases+b.releases==releaseBefore+1,"pooled source release once");
   }
   for(CUserSocket s:sockets)s.frames.clear();
  }
  System.out.println("Broadcast 87: 5 randomized trials x 9 sizes x 300 targets, payload/filter/FIFO/reference release PASS");
 }
 public static void main(String[]args){run();}
}
