package l1j.server.server.model.network.thread;

import l1j.server.server.model.network.core.execution.CIOObject;
import java.util.concurrent.atomic.AtomicReference;
public final class CIOThreadCallback {
 public interface PacketTask extends l1j.server.server.model.network.core.execution.CIOObject {}
 public static final CIOThreadCallback INSTANCE=new CIOThreadCallback();
 public final AtomicReference<PacketTask> queued=new AtomicReference<>();
 public volatile java.util.concurrent.Executor performanceExecutor;
 public static CIOThreadCallback getInstance(){return INSTANCE;}
 public int shardCount(){return 1;}
 public static int currentShardIndex(){return 0;}
 public boolean enqueuePacketImmediate(int shard,PacketTask task){java.util.concurrent.Executor executor=performanceExecutor;if(executor!=null){executor.execute(task);return true;}return queued.compareAndSet(null,task);}
}
