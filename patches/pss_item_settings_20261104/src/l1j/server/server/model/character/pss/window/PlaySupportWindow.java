package l1j.server.server.model.character.pss.window;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.utill.ConnectionState;

/** Item opens the existing native settings window; starting/stopping remains native PSS. */
public final class PlaySupportWindow {
    public static final int ITEM_ID = 900000901;
    public static final String ITEM_NAME = "PSS 설정서";
    public static final int REGISTER_START = 0x7DF0, REGISTER_COMMIT = 0x7DF1, REGISTER_BYTE = 0x7E00;
    private static final Logger LOG = Logger.getLogger(PlaySupportWindow.class.getName());
    private static volatile PssWindowTransport transport;
    private static long retryAt;
    private static boolean shutdownHookInstalled;

    private PlaySupportWindow() { }

    private static L1PcInstance player(CIOClient client) {
        if (client == null || !client.isConnected() || !client.isGameSessionReady()
                || client.getConnectionState() != ConnectionState.IN_GAME) return null;
        L1PcInstance pc = client.getActiveChar();
        return pc != null && pc.getNetConnection() == client && !pc.isLogoutStarted() ? pc : null;
    }

    private static boolean empty(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 0 || length > 2 || offset > data.length - length) return false;
        for (int i = 0; i < length; i++) if (data[offset + i] != 0) return false;
        return true;
    }

    public static void registerStart(CIOClient client, byte[] data, int offset, int length) {
        L1PcInstance pc = player(client);
        if (pc != null && empty(data, offset, length)) client.pssWindowSession().begin(pc);
    }

    public static void registerByte(CIOClient client, int value, byte[] data, int offset, int length) {
        Session session = client == null ? null : client.peekPssWindowSession();
        if (session != null && empty(data, offset, length)) session.append(value);
    }

    public static void registerCommit(CIOClient client, byte[] data, int offset, int length) {
        Session session = client == null ? null : client.peekPssWindowSession();
        if (session != null && empty(data, offset, length)) session.commit();
    }

    /** Source-only companion fallback. Consume before any chat broadcast or command logging. */
    public static boolean handleLinkCommand(L1PcInstance pc, String message) {
        String prefix = ".psslink";
        if (message == null || !message.regionMatches(true, 0, prefix, 0, prefix.length())
                || message.length() > prefix.length() && !Character.isWhitespace(message.charAt(prefix.length()))) return false;
        CIOClient client = pc == null ? null : pc.getNetConnection();
        if (pc == null || player(client) != pc) return true;
        String hex = message.substring(prefix.length()).trim();
        byte[] token = new byte[16];
        boolean valid = hex.length() == token.length * 2;
        for (int i = 0; valid && i < token.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16), low = Character.digit(hex.charAt(i * 2 + 1), 16);
            valid = high >= 0 && low >= 0;
            token[i] = (byte) (high << 4 | low);
        }
        if (!valid) SC_SystemMessage.send(pc, "연결 도구에 표시된 .psslink 명령을 그대로 입력해 주세요.");
        else if (client.pssWindowSession().link(pc, token))
            SC_SystemMessage.send(pc, "PSS 창 연결 등록 완료. 연결 도구의 READY 확인 후 설정서를 사용하세요.");
        else SC_SystemMessage.send(pc, "PSS 창 연결 등록 실패. 잠시 후 다시 입력해 주세요.");
        Arrays.fill(token, (byte) 0);
        return true;
    }

    public static boolean open(L1PcInstance pc) {
        CIOClient client = pc == null ? null : pc.getNetConnection();
        if (player(client) != pc || pc == null) return false;
        Session session = client.peekPssWindowSession();
        PssWindowTransport.Peer peer = session == null ? null : session.peer;
        boolean queued = peer != null && peer.requestOpen();
        if (!queued) SC_SystemMessage.send(pc, "PSS 연결을 확인한 뒤 다시 사용해 주세요.");
        else LOG.info("[PSS-WINDOW] item/window request queued player=" + pc.getId());
        return queued;
    }

    private static synchronized PssWindowTransport transport() {
        if (transport != null && transport.isOpen()) return transport;
        long now = System.nanoTime();
        if (retryAt != 0 && now - retryAt < 0) return null;
        int port = Integer.getInteger("pss.uiBridge.port", Config.GAME_SERVER_PORT + 1);
        PssWindowTransport created = null;
        try {
            if (port < 1 || port > 65535) throw new IOException("invalid companion port");
            created = new PssWindowTransport(new InetSocketAddress(port), 4096);
            if (!shutdownHookInstalled) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    PssWindowTransport current = transport;
                    if (current != null) current.close();
                }, "RKern-PSS-UI-shutdown"));
                shutdownHookInstalled = true;
            }
            transport = created;
            retryAt = 0;
            LOG.info("[PSS-WINDOW] companion listening port=" + created.port());
        } catch (IOException | RuntimeException failure) {
            if (created != null) created.close();
            retryAt = now + TimeUnit.SECONDS.toNanos(10);
            LOG.warning("[PSS-WINDOW] companion unavailable: " + failure.getMessage());
        }
        return transport;
    }

    /** Allocated only for clients advertising the native helper capability. */
    public static final class Session implements PssWindowTransport.Owner, AutoCloseable {
        private final CIOClient client;
        private final byte[] bytes = new byte[16];
        private int count = -1;
        private long deadline, lastBegin;
        private volatile int stamp, characterId;
        private volatile boolean closed;
        private PssWindowTransport.Token pending;
        private volatile PssWindowTransport.Peer peer;

        public Session(CIOClient client) { this.client = client; }

        private synchronized void begin(L1PcInstance pc) {
            long now = System.nanoTime();
            count = -1;
            if (closed || player(client) != pc || lastBegin != 0 && now - lastBegin < TimeUnit.SECONDS.toNanos(1)) return;
            cancelPending();
            lastBegin = now;
            stamp = pc.getSessionStamp();
            characterId = pc.getId();
            deadline = now + TimeUnit.SECONDS.toNanos(5);
            count = 0;
            Arrays.fill(bytes, (byte) 0);
        }

        private synchronized void append(int value) {
            if (count < 0 || count >= bytes.length || value < 0 || value > 255 || !valid() || System.nanoTime() - deadline >= 0) {
                count = -1;
                return;
            }
            bytes[count++] = (byte) value;
        }

        private synchronized boolean commit() {
            if (count != bytes.length || !valid() || System.nanoTime() - deadline >= 0) { count = -1; return false; }
            count = -1;
            PssWindowTransport server = transport();
            if (server == null) return false;
            PssWindowTransport.Token token = PssWindowTransport.Token.from(bytes);
            pending = token;
            if (!server.register(token, this)) { pending = null; return false; }
            return true;
        }

        private synchronized boolean link(L1PcInstance pc, byte[] token) {
            begin(pc);
            if (count != 0) return false;
            System.arraycopy(token, 0, bytes, 0, bytes.length);
            count = bytes.length;
            return commit();
        }

        @Override public boolean valid() {
            L1PcInstance pc = player(client);
            return !closed && pc != null && pc.getId() == characterId && pc.getSessionStamp() == stamp;
        }

        @Override public String remoteIp() { return client.getIp(); }

        @Override public boolean attach(PssWindowTransport.Token token, PssWindowTransport.Peer attached) {
            PssWindowTransport.Peer old;
            synchronized (this) {
                if (!valid() || pending == null || !pending.equals(token)) return false;
                pending = null;
                old = peer;
                peer = attached;
            }
            if (old != null && old != attached) old.close();
            LOG.info("[PSS-WINDOW] companion bound player=" + characterId);
            return true;
        }

        @Override public synchronized void detach(PssWindowTransport.Peer detached) {
            if (peer == detached) peer = null;
        }

        private void cancelPending() {
            PssWindowTransport server = transport;
            if (server != null && pending != null) server.cancel(pending, this);
            pending = null;
        }

        @Override public void close() {
            PssWindowTransport.Peer old;
            synchronized (this) {
                if (closed) return;
                closed = true;
                count = -1;
                cancelPending();
                old = peer;
                peer = null;
                Arrays.fill(bytes, (byte) 0);
            }
            if (old != null) old.close();
        }
    }
}
