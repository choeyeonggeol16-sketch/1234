import java.io.DataInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.Config;
import l1j.server.Server;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.packet.CBytesPacket;
import l1j.server.server.model.network.packet.CPacketHandler;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow;

/** Uses the real listener, one immutable plaintext and an independent cipher for each peer. */
public final class GameFanoutVerification {
    private static final int ALIVE = Opcodes.C_ALIVE & 255;
    private static final int BROADCAST = -1;
    private static final byte[] SHARED = new byte[64];

    public static void main(String[] args) throws Exception {
        Arrays.fill(SHARED, (byte) 0x42);
        Files.createDirectory(Path.of("config"));
        Files.writeString(Path.of("config/Network.ini"), "DisruptorShardCount=2\nNetworkClientPermission=502\nUsePreLoginTimeout=false\n");
        Config.MAX_ONLINE_USERS = 502;
        Config.GAME_SERVER_PORT = reservePort();
        int companion = reservePort();
        while (companion == Config.GAME_SERVER_PORT) companion = reservePort();
        System.setProperty("pss.uiBridge.port", Integer.toString(companion));
        CPacketFilter.getInstance().load();
        Set<CUserSocket> recipients = ConcurrentHashMap.newKeySet();
        var encodes = new AtomicInteger();
        var denials = new AtomicInteger();
        var previous = CPacketHandler.HANDLERS[ALIVE];
        CPacketHandler.HANDLERS[ALIVE] = (request, client) -> {
            int sequence = request.readInt();
            if (sequence != BROADCAST) {
                recipients.add(client.getConnection());
                return client.getConnection().tryWritePacket(client, new CBytesPacket(body(0x45, sequence)));
            }
            try (var shared = new CAsyncSendConstBuffer(out -> {
                encodes.incrementAndGet();
                out.put(SHARED);
            }, -1, Integer.MAX_VALUE)) {
                for (CUserSocket socket : recipients) if (!shared.send(socket, 1)) denials.incrementAndGet();
            }
            return true;
        };
        var peers = new ArrayList<Peer>();
        try {
            if (!Server.startLoginServer()) throw new AssertionError("game listener failed");
            for (int count : new int[] {20, 100, 500}) {
                while (peers.size() < count) {
                    int index = peers.size();
                    var peer = new Peer(Config.GAME_SERVER_PORT);
                    peers.add(peer);
                    for (int i = 0; i <= index % 3; i++) {
                        peer.send(i);
                        peer.expect(body(0x45, i));
                    }
                }
                int before = encodes.get();
                peers.getFirst().send(BROADCAST);
                if (recipients.size() != count) throw new AssertionError("wrong recipient count");
                for (Peer peer : peers) peer.expect(SHARED);
                if (encodes.get() != before + 1 || denials.get() != 0) throw new AssertionError("fan-out admission");
                System.out.println("PASS game fan-out " + count + " recipients, one encode, independent ciphertext");
            }
        } finally {
            try {
                for (Peer peer : peers) peer.socket.close();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (Server.networkAccountRegistryDiagnostics()[0] > 0 && System.nanoTime() < deadline) Thread.sleep(5);
                if (Server.networkAccountRegistryDiagnostics()[0] != 0
                        || !Arrays.stream(Server.networkPoolSnapshot()).allMatch(row -> row[2] == 0 && row[3] == row[4]))
                    throw new AssertionError("fan-out resources retained: " + Server.networkPoolReport());
                System.out.println("PASS game fan-out drained all sessions and leases");
            } finally {
                try { Server.stopLoginServer(); }
                finally {
                    CPacketHandler.HANDLERS[ALIVE] = previous;
                    PlaySupportWindow.stopIndependent();
                    var owners = CIOThreadCallback.getInstance();
                    owners.shutdown();
                    if (!owners.awaitTermination(5, TimeUnit.SECONDS)) throw new AssertionError("owner shutdown");
                    l1j.server.server.ThreadPool.getInstance().shutdown();
                }
            }
        }
    }

    private static int reservePort() throws Exception {
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { return socket.getLocalPort(); }
    }

    private static byte[] body(int opcode, int sequence) {
        return ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put((byte) opcode).putInt(sequence).array();
    }

    private static final class Peer {
        private final Socket socket;
        private final DataInputStream input;
        private final SelectorTransportTest.ScalarClient codec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());

        private Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            try {
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(5000);
                input = new DataInputStream(socket.getInputStream());
                if (!Arrays.equals(read(), Opcodes.S_KEYBYTES)) throw new AssertionError("handshake");
            } catch (Exception | Error failure) {
                socket.close();
                throw failure;
            }
        }

        private void send(int value) throws Exception {
            byte[] body = codec.toServer(body(ALIVE, value));
            socket.getOutputStream().write(ByteBuffer.allocate(body.length + 2).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short) (body.length + 2)).put(body).array());
        }

        private byte[] read() throws Exception {
            int length = (input.readUnsignedByte() | input.readUnsignedByte() << 8) - 2;
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            return bytes;
        }

        private void expect(byte[] body) throws Exception {
            if (!Arrays.equals(read(), codec.fromServer(body))) throw new AssertionError("recipient ciphertext mismatch");
        }
    }
}
