package l1j.server.server.model.network.thread;
import java.util.concurrent.atomic.AtomicReference;
public final class CIOThreadCallback {
 public interface PacketTask extends l1j.server.server.model.network.core.CIOObject {}
 public static final CIOThreadCallback INSTANCE=new CIOThreadCallback();
 public final AtomicReference<PacketTask> queued=new AtomicReference<>();
 public static CIOThreadCallback getInstance(){return INSTANCE;}
 public int shardCount(){return 1;}
 public static int currentShardIndex(){return 0;}
 public boolean enqueuePacketImmediate(int shard,PacketTask task){return queued.compareAndSet(null,task);}
}
