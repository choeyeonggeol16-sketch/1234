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

/** The RKern raw framed echo path with one Selector owner and no Netty dependencies. */
public final class SelectorEcho {
    private static final int BACKLOG = 256;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("SelectorEcho <frameBytes> <maxClients>");
        final int wireBytes = Integer.parseInt(args[0]);
        final int clients = Integer.parseInt(args[1]);
        if (wireBytes < 3 || wireBytes > 65535 || clients < 1 || clients > 100_000)
            throw new IllegalArgumentException("Invalid frame size or connection capacity");
        final int capacity = Math.max(128, clients + 32);
        var raw = new RawEcho(wireBytes);
        var config = new CSelectorTransport.Config(1, capacity, BACKLOG, capacity,
                1, 1, 1, 256 * 1024, 256, 0, Duration.ofSeconds(5));
        try (var server = CSelectorTransport.bind(new InetSocketAddress("127.0.0.1", 0), config, raw)) {
            System.out.println("READY " + server.localAddress().getPort());
            var control = new BufferedReader(new InputStreamReader(System.in));
            final String command = control.readLine();
            if (command != null && !command.equals("STOP")) throw new IllegalArgumentException("Unexpected control message");
            server.close();
            raw.verifyReleased();
            for (var owner : server.shutdownStats()) {
                if (owner.remainingConnections() != 0 || owner.remainingCommands() != 0)
                    throw new IllegalStateException("Outstanding connections or mailbox work");
            }
            System.out.printf("CLOSED accepted=%d closed=%d echoes=%d pooled_bytes=%d%n",
                    raw.opened, raw.closed, raw.echoes, raw.allocated * (long) wireBytes);
        }
    }

    private static final class RawEcho implements CSelectorTransport.RawHandler {
        private final Map<CSelectorTransport.Connection, Session> sessions = new IdentityHashMap<>();
        private final ArrayDeque<ByteBuffer> free = new ArrayDeque<>();
        private final int wireBytes;
        private int allocated;
        private long opened;
        private long closed;
        private long echoes;
        private String protocolError;

        private RawEcho(int wireBytes) {
            this.wireBytes = wireBytes;
        }

        @Override public void onOpen(CSelectorTransport.Connection connection) {
            if (connection.ownerIndex() != 0) throw new IllegalStateException("Unexpected Selector owner");
            ByteBuffer buffer = free.pollFirst();
            if (buffer == null) {
                buffer = ByteBuffer.allocateDirect(wireBytes);
                allocated++;
            }
            var session = new Session(connection, buffer);
            sessions.put(connection, session);
            opened++;
            session.read();
        }

        @Override public void onClosed(CSelectorTransport.Connection connection, CSelectorTransport.CloseReason reason) {
            Session session = sessions.remove(connection);
            if (session != null) {
                free.addLast(session.buffer);
                closed++;
            }
        }

        private void verifyReleased() {
            if (!sessions.isEmpty() || free.size() != allocated || opened != closed)
                throw new IllegalStateException("Outstanding raw connection buffers");
            if (protocolError != null) throw new IllegalStateException(protocolError);
        }

        private final class Session {
            private final CSelectorTransport.Connection connection;
            private final ByteBuffer buffer;
            private final IntConsumer readCompletion = this::onRead;
            private final LongConsumer writeCompletion = this::onWrite;

            private Session(CSelectorTransport.Connection connection, ByteBuffer buffer) {
                this.connection = connection;
                this.buffer = buffer;
            }

            private void read() {
                buffer.clear();
                connection.readRaw(buffer, readCompletion);
            }

            private void onRead(int bytes) {
                if (bytes <= 0) return;
                if (buffer.hasRemaining()) {
                    connection.readRaw(buffer, readCompletion);
                    return;
                }
                buffer.flip();
                if (((buffer.get(0) & 255) | ((buffer.get(1) & 255) << 8)) != buffer.remaining()) {
                    protocolError = "Unexpected framed echo request length";
                    throw new IllegalStateException(protocolError);
                }
                connection.writeRaw(buffer, writeCompletion);
            }

            private void onWrite(long bytes) {
                if (bytes < 0) return;
                if (buffer.hasRemaining()) connection.writeRaw(buffer, writeCompletion);
                else {
                    echoes++;
                    read();
                }
            }
        }
    }
}
