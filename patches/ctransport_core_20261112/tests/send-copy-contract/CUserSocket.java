package l1j.server.server.model.network.core;
import java.nio.*;import java.util.concurrent.atomic.*;
final class CUserSocket {
 final ByteBuffer source;final AtomicInteger callbacks=new AtomicInteger();int result;
 CUserSocket(ByteBuffer b){source=b;}
 void transportWriteCompleted(int n){result=n;callbacks.incrementAndGet();for(int i=0;i<source.capacity();i++)source.put(i,(byte)0);}
 void transportGatherCompleted(int n){transportWriteCompleted(n);}void transportReadCompleted(int n){}
 boolean transportNativePacket(long seq,ByteBuffer b,int n){return false;}int transportNetworkDeadline(){return 5000;}
 void onTransportClosing(){}void onTransportTerminated(){}void close(){}
 int closeOperation=-1,closeError=-1;
 void onNativeCloseStatus(int operation,int error){closeOperation=operation;closeError=error;}
}
