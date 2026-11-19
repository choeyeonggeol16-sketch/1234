package l1j.server.server.model.network.core.connection;
import java.nio.*;
import java.util.*;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.transport.CIOServer;
public class CUserSocket {
 public boolean connected=true, constCapable=true, admit=true, failEnqueueOnce=false; public CIOServer server;
 public int nativeAllocs, nativeSeals, directSends, sharedSends; public final List<byte[]> frames=new ArrayList<>();
 public final List<CAsyncSendConstBuffer.Source> retained=new ArrayList<>();
 public CUserSocket(CIOServer server) { this.server=server; }
 public boolean isClientConnected() { return connected; }
 public boolean supportsConstBuffer(){ return constCapable; }
 public int pendingSendBytes(){ return 0; }
 public boolean ownsConstBuffer(CAsyncSendConstBuffer.Source s){return s.owner==server;}
 public CAsyncSendConstBuffer.Source createPooledMoveConstBuffer(int a,int b,int c,int d,int e) { return null; }
 public CAsyncSendConstBuffer.Source createPooledActionConstBuffer(int a,int b,int c) { return null; }
 public CAsyncSendConstBuffer.Source createPooledFormattedConstBuffer(int a,int b,int c,int d,int e,int f) { return null; }
 public CAsyncSendConstBuffer.Source createPooledExtendedConstBuffer(int a,int b,int c,int d,int e,int f,int g,int h,int i,int j,int k,int l,int m) { return null; }

 public CAsyncSendConstBuffer.Source createPooledWritableConstBuffer(){ nativeAllocs++;
    ByteBuffer buffer=ByteBuffer.allocateDirect(16384).order(ByteOrder.LITTLE_ENDIAN);
    return new CAsyncSendConstBuffer.Source(server,java.lang.foreign.MemorySegment.NULL,buffer,true);
 }
 public void sealPooledWritableConstBuffer(CAsyncSendConstBuffer.Source s,int length) {
    if(length>16384||length<3)throw new IllegalArgumentException(); nativeSeals++;
    s.mutableWire.putShort(0,(short)length); s.wire.limit(length); s.mutableWire=null;
 }
 public boolean enqueueConstBufferConfirmed(CAsyncSendConstBuffer.Source s,int limit){
    if(failEnqueueOnce) {failEnqueueOnce=false;throw new IllegalStateException("injected per-socket send failure");}
    if(!admit) return false;
    sharedSends++; s.retain();retained.add(s);copy(s.wire);return true;
 }
 public boolean sendFrozenWire(ByteBuffer frozen){ directSends++;copy(frozen);return true; }
 private void copy(ByteBuffer b) { byte[] copy=new byte[b.remaining()]; b.duplicate().get(copy);frames.add(copy); }
 public void drain(){for(var s:retained)s.release();retained.clear();}
}
