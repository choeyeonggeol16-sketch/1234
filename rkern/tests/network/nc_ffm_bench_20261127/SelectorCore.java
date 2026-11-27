import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;

/** Benchmark adapter for RKern's actual pure-Java two-owner Selector transport. */
public final class SelectorCore {
    private static final int OWNERS = 2;
    private static final int BACKLOG = 256;
    private static final int BATCH = 8;

    public static void main(String[] args) throws Exception {
        if ((args.length != 2 && args.length != 3) || (args.length == 3 && Integer.parseInt(args[2]) != OWNERS))
            throw new IllegalArgumentException("SelectorCore <frameBytes> <maxClients> [2]");
        final int wireBytes = Integer.parseInt(args[0]);
        final int clients = Integer.parseInt(args[1]);
        if (wireBytes != CoreProtocol.FRAME_BYTES || clients < 1 || clients > 100_000)
            throw new IllegalArgumentException("Invalid frame size or connection capacity");
        final int capacity = Math.max(128, (clients + OWNERS - 1) / OWNERS + 32);
        var raw = new RawCore();
        var config = new CSelectorTransport.Config(OWNERS, capacity, BACKLOG, capacity,
                1, 1, 1, 256 * 1024, 256, 0, Duration.ofSeconds(5));
        try (var server = CSelectorTransport.bind(new InetSocketAddress("127.0.0.1", 0), config, raw)) {
            System.out.println("READY " + server.localAddress().getPort());
            var control = new BufferedReader(new InputStreamReader(System.in));
            final String command = control.readLine();
            if (command != null && !command.equals("STOP"))
                throw new IllegalArgumentException("Unexpected control message");
            server.close();
            raw.verifyReleased();
            for (var owner : server.shutdownStats()) {
                if (owner.remainingConnections() != 0 || owner.remainingCommands() != 0)
                    throw new IllegalStateException("Outstanding Selector connections or mailbox work");
            }
            System.out.printf("CLOSED accepted=%d closed=%d frames=%d%n", raw.opened(), raw.closed(), raw.frames());
        }
    }

    private static final class Owner {
        private final Map<CSelectorTransport.Connection, Session> sessions = new IdentityHashMap<>();
        private final ArrayDeque<ByteBuffer> free = new ArrayDeque<>();
        private int allocated;
        private long opened;
        private long closed;
        private long frames;
    }

    private static final class RawCore implements CSelectorTransport.RawHandler {
        private final Owner[] owners = {new Owner(), new Owner()};

        @Override public void onOpen(CSelectorTransport.Connection connection) {
            Owner owner = owners[connection.ownerIndex()];
            ByteBuffer buffer = owner.free.pollFirst();
            if (buffer == null) {
                buffer = ByteBuffer.allocateDirect(BATCH * CoreProtocol.FRAME_BYTES);
                owner.allocated++;
            }
            buffer.clear();
            var session = new Session(owner, connection, buffer);
            owner.sessions.put(connection, session);
            owner.opened++;
            session.read();
        }

        @Override public void onClosed(CSelectorTransport.Connection connection, CSelectorTransport.CloseReason reason) {
            Owner owner = owners[connection.ownerIndex()];
            Session session = owner.sessions.remove(connection);
            if (session != null) {
                owner.free.addLast(session.buffer);
                owner.closed++;
            }
        }

        long opened() { return owners[0].opened + owners[1].opened; }
        long closed() { return owners[0].closed + owners[1].closed; }
        long frames() { return owners[0].frames + owners[1].frames; }

        private void verifyReleased() {
            for (Owner owner : owners) {
                if (!owner.sessions.isEmpty() || owner.free.size() != owner.allocated || owner.opened != owner.closed)
                    throw new IllegalStateException("Outstanding raw-batch connection buffers");
            }
        }
    }

    private static final class Session {
        private final Owner owner;
        private final CSelectorTransport.Connection connection;
        private final ByteBuffer buffer;
        private final IntConsumer readCompletion = this::onRead;
        private final LongConsumer writeCompletion = this::onWrite;
        private int sequence;
        private int receivedEnd;
        private int replyFrames;

        private Session(Owner owner, CSelectorTransport.Connection connection, ByteBuffer buffer) {
            this.owner = owner;
            this.connection = connection;
            this.buffer = buffer;
        }

        private void read() {
            connection.readRaw(buffer, readCompletion);
        }

        private void onRead(int bytes) {
            if (bytes <= 0) return;
            receivedEnd = buffer.position();
            replyFrames = receivedEnd / CoreProtocol.FRAME_BYTES;
            if (replyFrames == 0) {
                read();
                return;
            }

            final int replyEnd = replyFrames * CoreProtocol.FRAME_BYTES;
            buffer.limit(replyEnd).position(0);
            try {
                for (int offset = 0; offset < replyEnd; offset += CoreProtocol.FRAME_BYTES) {
                    CoreProtocol.handleAt(buffer, offset, sequence);
                    sequence++;
                }
            } catch (IllegalArgumentException invalidRequest) {
                connection.abort();
                return;
            }
            connection.writeRaw(buffer, writeCompletion);
        }

        private void onWrite(long bytes) {
            if (bytes < 0) return;
            if (buffer.hasRemaining()) {
                connection.writeRaw(buffer, writeCompletion);
                return;
            }
            owner.frames += replyFrames;
            buffer.limit(receivedEnd).compact();
            read();
        }
    }
}
