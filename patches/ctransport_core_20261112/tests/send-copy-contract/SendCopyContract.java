package l1j.server.server.model.network.core.transport;

import l1j.server.server.model.network.core.connection.CUserSocket;
import java.net.*;import java.nio.*;import java.util.*;import java.util.concurrent.*;
public final class SendCopyContract {
 public static void main(String[] args)throws Exception{
  for(boolean gather:new boolean[]{false,true}){
   CIOServer server=new CIOServer();CSocket socket=new CSocket(server,1,new InetSocketAddress(0),new InetSocketAddress(0));
   ByteBuffer data=ByteBuffer.allocateDirect(4096);byte[] expected=new byte[4096];for(int i=0;i<4096;i++)data.put(expected[i]=(byte)(i*17+3));data.flip();
   CUserSocket owner=new CUserSocket(data);Thread submit=new Thread(()->{if(gather)socket.write(new ByteBuffer[]{data.duplicate()},0,1,owner);else socket.write(data.duplicate(),owner);});submit.start();
   if(!server.entered.await(5,TimeUnit.SECONDS))throw new AssertionError("send not entered");socket.close();
   if(owner.callbacks.get()!=0)throw new AssertionError("WRITE lease released before native copy returned");
   server.release.countDown();submit.join(5000);if(submit.isAlive()||owner.callbacks.get()!=1||owner.result!=-1||!Arrays.equals(server.captured,expected))throw new AssertionError("copy/close once-only failure contract");
  }
  for(boolean lateBind:new boolean[]{false,true}){
   CIOServer server=new CIOServer();CSocket socket=new CSocket(server,1,new InetSocketAddress(0),new InetSocketAddress(0));
   CUserSocket owner=new CUserSocket(ByteBuffer.allocateDirect(1));
   if(!lateBind)socket.bindUserSocketOwner(owner);
   socket.nativeClosing(3,10054);
   if(lateBind)socket.bindUserSocketOwner(owner);
   socket.nativeTerminated(2,995);
   if(owner.closeOperation!=3||owner.closeError!=10054)throw new AssertionError("native close status lost to cancellation or late owner bind");
  }
  System.out.println("PASS production CSocket single/gather copy-close race:source lease retained until downcall exits,original bytes copied,one failed completion;first native status survives late bind and cancellation");
 }
}
