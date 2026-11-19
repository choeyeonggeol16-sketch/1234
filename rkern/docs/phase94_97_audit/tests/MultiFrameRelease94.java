package l1j.server.server.model.network.core.broadcast;

import java.lang.foreign.MemorySegment;
import java.util.List;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

/** Actual production CSharedBroadcast + Java 21 typed socket stubs. */
public final class MultiFrameRelease94 {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static final class FailingOwner extends CIOServer {
        int calls;
        boolean failAll;
        @Override public void releaseConstBuffer(MemorySegment handle) {
            ++calls;
            if (failAll || calls == 1) throw new IllegalStateException("injected release " + calls);
            super.releaseConstBuffer(handle);
        }
    }
    public static void main(String[] args) {
        final L1World world = L1World.getInstance();
        final FailingOwner owner = new FailingOwner();
        final CUserSocket socket = new CUserSocket(owner);
        // Force the synchronous FIFO-copy branch, leaving only the producer
        // reference, which is released during the batch finally block.
        socket.constCapable = false;
        final L1PcInstance pc = new L1PcInstance(501, 123, new CIOClient(socket));
        pc.team = 7;
        world.visible.clear();
        world.visible.add(pc);
        RuntimeException first = null;
        try {
            CSharedBroadcast.sendWrittenMapTeamFrames(123, 7, true,
                    new CSharedBroadcast.NativeWireWriter[]{
                            b -> b.put((byte)0x45), b -> b.put((byte)0x46)});
        } catch (RuntimeException error) { first = error; }
        check(first != null && first.getMessage().contains("release 1"),
                "original release exception lost");
        check(owner.calls == 2, "second Native frame was not released after first failure");
        check(socket.frames.size() == 2, "frame FIFO lost data on release failure");
        check(socket.frames.get(0)[2] == 0x45 && socket.frames.get(1)[2] == 0x46,
                "frame order changed");

        // A release error from each source must be chained, not silently dropped.
        final FailingOwner allFailures = new FailingOwner();
        allFailures.failAll = true;
        final CUserSocket another = new CUserSocket(allFailures);
        another.constCapable = false;
        world.visible.clear();
        world.visible.add(new L1PcInstance(502, 123, new CIOClient(another)));
        RuntimeException aggregate = null;
        try {
            CSharedBroadcast.sendWrittenMapTeamFrames(123, 7, false,
                    new CSharedBroadcast.NativeWireWriter[]{
                            b -> b.put((byte)0x51), b -> b.put((byte)0x52)});
        } catch (RuntimeException error) { aggregate = error; }
        check(allFailures.calls == 2, "release loop stopped on first failed Native owner");
        check(aggregate != null && aggregate.getSuppressed().length == 1,
                "second Native release exception not preserved as suppressed");
        world.visible.clear();
        System.out.println("94 production map-team multi-frame: FIFO + all releases + suppressed exceptions PASS");
    }
}
