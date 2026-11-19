package l1j.server.server.model.network.core.connection;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.transport.CIOServer;
import java.nio.ByteBuffer;
import java.lang.foreign.MemorySegment;
public class CUserSocket {
 public final CIOServer owner;
 public CUserSocket(CIOServer owner){this.owner=owner;}
 public boolean isClientConnected() {return true;}
 public boolean supportsConstBuffer() {return false;}
 public int pendingSendBytes(){return 0;}
 public boolean ownsConstBuffer(CAsyncSendConstBuffer.Source source){return source.owner==owner;}
 public boolean enqueueConstBufferConfirmed(CAsyncSendConstBuffer.Source source,int pendingLimit){return false;}
 public boolean sendFrozenWire(ByteBuffer source){return true;}
 public CAsyncSendConstBuffer.Source createPooledWritableConstBuffer(){
  return new CAsyncSendConstBuffer.Source(owner,MemorySegment.NULL,ByteBuffer.allocateDirect(256),true);
 }
 public void sealPooledWritableConstBuffer(CAsyncSendConstBuffer.Source source,int length) {
  if(length<3||length>source.mutableWire.capacity())throw new IllegalArgumentException();
  source.wire.limit(length);source.mutableWire=null;
 }
}
