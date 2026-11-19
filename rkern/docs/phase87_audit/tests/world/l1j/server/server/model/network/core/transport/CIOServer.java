package l1j.server.server.model.network.core.transport;
import java.lang.foreign.MemorySegment;
public class CIOServer { public int releases; public void releaseConstBuffer(MemorySegment handle) { releases++; } }
