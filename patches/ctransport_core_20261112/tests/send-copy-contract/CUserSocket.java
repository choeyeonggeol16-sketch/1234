package l1j.server.server.model.network.core.connection;
import java.nio.*;import java.util.concurrent.atomic.*;
public final class CUserSocket {
 public final ByteBuffer source;public final AtomicInteger callbacks=new AtomicInteger();public int result;
 public CUserSocket(ByteBuffer b){source=b;}
 public void transportWriteCompleted(int n){result=n;callbacks.incrementAndGet();for(int i=0;i<source.capacity();i++)source.put(i,(byte)0);}
 public void transportGatherCompleted(int n){transportWriteCompleted(n);}public void transportReadCompleted(int n){}
 public boolean transportNativePacket(long seq,ByteBuffer b,int n){return false;}public int transportNetworkDeadline(){return 5000;}
 public void onTransportClosing(){}public void onTransportTerminated(){}public void close(){}
 public int closeOperation=-1,closeError=-1;
 public void onNativeCloseStatus(int operation,int error){closeOperation=operation;closeError=error;}
}
