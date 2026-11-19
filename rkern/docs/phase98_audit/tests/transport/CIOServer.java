package l1j.server.server.model.network.core.transport;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicInteger;
public class CIOServer {
 public final AtomicInteger releases=new AtomicInteger();
 public boolean throwOnRelease;
 public void releaseConstBuffer(MemorySegment handle) {
  releases.incrementAndGet();
  if(throwOnRelease) throw new IllegalStateException("mock native release failure");
 }
}
