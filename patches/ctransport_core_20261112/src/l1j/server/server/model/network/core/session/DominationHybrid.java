package l1j.server.server.model.network.core.session;

import java.nio.charset.Charset;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.MJActionListener.ActionListenerLoader;
import l1j.server.server.Account;
import l1j.server.server.Controller.LoginController;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.cs.packet.CS_EnterWorld;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.storage.CacheD;

/**
 * GM-only trial of the original hybrid reconnect wire flow on one physical server.
 * A single bounded pending handoff avoids a new executor, queue, or persistent user state.
 * Standard tower NPC / scroll entry paths are intentionally untouched.
 */
public final class DominationHybrid {
    private static final Logger LOG = Logger.getLogger(DominationHybrid.class.getName());
    private static final long TTL_NS = 90_000_000_000L;
    // 115 is decoded by ProtoReader.readString() as MS949. The 113 token
    // includes Korean character names, so UTF-8 bytes fail the round-trip.
    private static final Charset TOKEN_CHARSET = Charset.forName("MS949");
    // GM-only A/B: compare every client kind without modifying ordinary NPC travel.
    private static final int DEFAULT_GM_KIND = 7;
    private static final int MAX_GM_KIND = 100;
    // GM-only test selector. On wire, 102 uses interkind=7 with worldmove=true.
    private static final int WORLD_MOVE_GM_KIND = 102;
    private static final int RETURN_RESERVED = 99;

    // Reservation IDs from the remaster 113 builder. Unknown experimental kinds
    // retain the tower reservation while their client behavior is investigated.
    private static int reservedForKind(int kind) {
        switch (kind) {
        case 0: return 0;
        case 4: return 111;
        case 7: return 112;
        case 8: return 8;
        case 9: return 310;
        case 10: return 10;
        case 99: return 99;
        case 100: return 100;
        default: return 112;
        }
    }

    private static final class Pending {
        final CIOClient oldClient;
        final Account account;
        final String character;
        final String ip;
        final int characterId, mapId, x, y, backX, backY, backMapId;
        final int interKind, reservedNumber;
        final String token;
        final long createdNs;
        CIOClient newClient;
        boolean oldClosed;
        boolean finishing;
        Pending(L1PcInstance pc, CIOClient client, int destX, int destY,
                int destMap, int interKind, int reservedNumber) {
            oldClient = client;
            account = client.getAccount();
            character = pc.getName();
            characterId = pc.getId();
            ip = client.getIp();
            this.x = destX;
            this.y = destY;
            this.mapId = destMap;
            this.backX = pc.getX();
            this.backY = pc.getY();
            this.backMapId = pc.getMapId();
            this.interKind = interKind;
            this.reservedNumber = reservedNumber;
            // Same 10-field destination / origin contract as the remaster.
            // Still checked against this short-lived, one-shot GM pending record
            // on 115; no trust is placed in the incoming token fields.
            token = new StringBuilder(112)
                    .append(account.getName()).append('|').append(character).append('|')
                    .append(characterId).append('|').append(x).append('|').append(y).append('|')
                    .append(mapId).append('|').append(backX).append('|').append(backY)
                    .append('|').append(backMapId).append('|').append(RETURN_RESERVED).toString();
            createdNs = System.nanoTime();
        }
    }

    // Only one GM handoff is enabled at a time, not one record per player.
    private static Pending pending;

    private DominationHybrid() { }

    public static void gmCommand(L1PcInstance gm, String param) {
        if (gm == null || gm.getNetConnection() == null || gm.getAccount() == null
                || !gm.getAccount().isGameMaster()) return;
        final String[] args = param == null ? new String[0] : param.trim().split("\\s+");
        if ((args.length != 3 && args.length != 4) || !"지배".equals(args[0])) {
            gm.sendPackets("[INTER-TEST] .인터서버 지배 <1~11> <서버 IPv4> <kind:0~100 또는 102> (생략=7)");
            return;
        }
        final int interKind;
        try { interKind = args.length == 4 ? Integer.parseInt(args[3]) : DEFAULT_GM_KIND; }
        catch (NumberFormatException e) {
            gm.sendPackets("[INTER-TEST] kind는 0~100 또는 102의 정수여야 합니다.");
            return;
        }
        if (interKind < 0 || (interKind > MAX_GM_KIND && interKind != WORLD_MOVE_GM_KIND)) {
            gm.sendPackets("[INTER-TEST] kind 범위는 0~100 또는 102입니다.");
            return;
        }
        final int floor;
        try { floor = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) { gm.sendPackets("[INTER-TEST] 층 번호는 1~11."); return; }
        if (floor < 1 || floor > 11 || gm.isDead() || gm.isLogoutStarted()
                || gm.isPrivateShop() || gm.isAutoClanjoin() || gm.getTradeID() != 0
                || !gm.getNetConnection().isGameSessionReady()) {
            gm.sendPackets("[INTER-TEST] 입장 불가: 층/캐릭터 상태 확인.");
            return;
        }
        final String serverIP = args[2];
        final int ip32 = parseIpv4(serverIP);
        if (ip32 == -1 || Config.GAME_SERVER_PORT < 1 || Config.GAME_SERVER_PORT > 65535) {
            gm.sendPackets("[INTER-TEST] 유효한 서버 IPv4와 포트를 입력하세요.");
            return;
        }
        final int[] dbDestination = new int[4];
        if (!ActionListenerLoader.getInstance().copyTowerTeleport(floor, dbDestination)) {
            gm.sendPackets("[INTER-TEST] DB 지배의 탑 좌표 없음: tb_act_listener_npc_teleporter / NPC "
                    + (8500027 + floor) + " / action " + floor);
            return;
        }
        final int centerX = dbDestination[0], centerY = dbDestination[1];
        final int targetMap = dbDestination[2];
        final int range = dbDestination[3];
        final L1Map map = L1WorldMap.getInstance().getMap((short) targetMap);
        if (map == null || range < 0 || range > 16) {
            gm.sendPackets("[INTER-TEST] DB 지배의 탑 맵/범위가 잘못됨: " + targetMap + '/' + range);
            return;
        }
        // Same semantics as TeleporterActionListener.makePassableMap(): uniformly
        // choose a walkable tile within the DB radius; keep only primitives.
        int selectedX = 0, selectedY = 0, validTiles = 0;
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int x = centerX - range; x <= centerX + range; ++x) {
            for (int y = centerY - range; y <= centerY + range; ++y) {
                if (map.isInMap(x, y) && map.isPassable(x, y)
                        && rng.nextInt(++validTiles) == 0) {
                    selectedX = x;
                    selectedY = y;
                }
            }
        }
        if (validTiles == 0) {
            gm.sendPackets("[INTER-TEST] DB 지배의 탑 이동 타일 없음: "
                    + centerX + ',' + centerY + ',' + targetMap + " range=" + range);
            return;
        }
        final int targetX = selectedX, targetY = selectedY;
        // Tower-only same-server fallback: the 2019 client drops the new
        // connection when kind=7 enters a different map directly. A real
        // preliminary teleport followed by a kind=7 reconnect succeeds but
        // visibly enters the map twice. For GM tower trials 7/102 use one
        // normal relocation on the existing authenticated socket instead.
        // This is NOT an inter-server handoff; keep every other kind unchanged.
        if (interKind == 7 || interKind == WORLD_MOVE_GM_KIND) {
            final CIOClient client = gm.getNetConnection();
            if (client == null || !client.isConnected() || !client.isGameSessionReady()
                    || client.getActiveChar() != gm || gm.get_teleport()) {
                gm.sendPackets("[INTER-TOWER] 현재 이동 불가: 접속 또는 텔레포트 상태 확인");
                return;
            }
            synchronized (DominationHybrid.class) {
                if (pending != null && !isExpired(pending)) {
                    gm.sendPackets("[INTER-TOWER] 다른 인터서버 전환 진행 중");
                    return;
                }
            }
            final int sourceX = gm.getX(), sourceY = gm.getY(), sourceMap = gm.getMapId();
            try {
                gm.start_teleport(targetX, targetY, targetMap,
                        gm.getHeading(), 18339, false, false);
            } catch (Throwable error) {
                LOG.log(Level.WARNING, "[INTER-TOWER] normal teleport failed", error);
                gm.sendPackets("[INTER-TOWER] 이동 처리 오류");
                return;
            }
            if (!gm.get_teleport()) {
                LOG.warning("[INTER-TOWER] teleport not scheduled; 113 suppressed");
                gm.sendPackets("[INTER-TOWER] 이동 예약 실패");
                return;
            }
            LOG.info("[INTER-TOWER] one-step local fallback (NOT hybrid) from="
                    + sourceX + ',' + sourceY + ',' + sourceMap
                    + " to=" + targetX + ',' + targetY + ',' + targetMap
                    + " kind=" + interKind + " 113=not-sent; GM only");
            return;
        }
        // 102 selects a separate GM trial; it is never sent as an interkind.
        // Compare against existing GM kind=7 (worldmove=false) without
        // changing the login, teleport, token, or ACK implementation.
        final boolean worldMove = interKind == WORLD_MOVE_GM_KIND;
        final int wireKind = worldMove ? 7 : interKind;
        final int reservedNumber = reservedForKind(wireKind);
        final Pending p;
        synchronized (DominationHybrid.class) {
            if (pending != null && !isExpired(pending)) {
                gm.sendPackets("[INTER-TEST] 다른 재접속 테스트 진행 중. 90초 후 재시도.");
                return;
            }
            p = new Pending(gm, gm.getNetConnection(), targetX, targetY, targetMap,
                    wireKind, reservedNumber);
            pending = p;
        }
        // Remaster 10-field position token, strictly matched to our one-shot
        // GM pending session (not accepted as a standalone authentication).
        final ProtoWriter writer = Proto.begin(gm.getNetConnection(),
                ProtoMsg.SC_CONNECT_HIBREEDSERVER_NOTI_PACKET, 256);
        if (writer == null) { clear(p); gm.sendPackets("[INTER-TEST] 전송 프레임 할당 실패."); return; }
        boolean sent = false;
        try {
            writer.uint32(1, ip32).int32(2, Config.GAME_SERVER_PORT)
                    .uint32(3, p.reservedNumber)
                    .bytes(4, p.token.getBytes(TOKEN_CHARSET))
                    .int32(5, p.interKind).string(6, serverIP).bool(7, worldMove);
            sent = Proto.commit(writer);
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "[INTER-TEST] 113 build/send failed", e);
        } finally {
            Proto.abort(writer);
        }
        if (!sent) { clear(p); gm.sendPackets("[INTER-TEST] 113 전송 실패."); return; }
        LOG.info("[INTER-TEST] 113 sent gmKind=" + interKind
                + " wireKind=" + p.interKind + " worldmove=" + worldMove
                + " reserved=" + p.reservedNumber + " floor=" + floor
                + " dest=" + serverIP + ':' + Config.GAME_SERVER_PORT
                + " target=" + p.x + ',' + p.y + ',' + p.mapId
                + " dbCenter=" + centerX + ',' + centerY + " range=" + range
                + " validTiles=" + validTiles
                + " from=" + p.backX + ',' + p.backY + ',' + p.backMapId
                + " returnReserved=" + RETURN_RESERVED + " tokenFields=10 tokenCharset=MS949"
                + " char=" + p.character + " (GM only)");
    }

    /** C->S protobuf message 115: fields 1=reserved, 2=one-time token. */
    public static void onHybridAuth(CIOClient arriving, long reserved, String token) {
        if (arriving == null) return;
        CIOClient retire = null;
        synchronized (DominationHybrid.class) {
            final Pending p = pending;
            // No diagnostics or extra work on ordinary non-GM arrivals.
            if (p == null || !p.ip.equals(arriving.getIp())) return;
            final boolean valid = !isExpired(p) && p.newClient == null && !p.finishing
                    && arriving.isConnected() && arriving.getAccount() == null
                    && arriving.getActiveChar() == null && token != null
                    && reserved == p.reservedNumber && p.token.equals(token);
            if (!valid) {
                // Never print the one-time credential. This distinguishes a 115
                // decode mismatch from no 115 arriving after the old socket closes.
                LOG.warning("[INTER-TEST] 115 rejected reserved=" + reserved
                        + " expectedReserved=" + p.reservedNumber
                        + " tokenMatches=" + p.token.equals(token)
                        + " tokenChars=" + (token == null ? -1 : token.length())
                        + " expectedChars=" + p.token.length()
                        + " expired=" + isExpired(p)
                        + " busy=" + (p.newClient != null || p.finishing)
                        + " connected=" + arriving.isConnected());
                return;
            }
            p.newClient = arriving;
            LOG.info("[INTER-TEST] 115 verified kind=" + p.interKind
                    + " reserved=" + p.reservedNumber
                    + " target=" + p.x + ',' + p.y + ',' + p.mapId
                    + " oldClosed=" + p.oldClosed);
            if (p.oldClosed) finishOffOwner(p);
            else retire = p.oldClient;
        }
        // The original client may leave its old TCP session open while
        // connecting to the destination. Retire it only after a valid 115.
        if (retire != null) retire.close();
    }

    /** Called only AFTER old logout + LoginController removal + socket cleanup. */
    public static void onClientClosed(CIOClient closed) {
        synchronized (DominationHybrid.class) {
            final Pending p = pending;
            if (p == null) return;
            if (p.newClient == closed) {
                pending = null;
                return;
            }
            if (p.oldClient != closed) return;
            p.oldClosed = true;
            if (p.newClient != null) finishOffOwner(p);
        }
    }

    private static void finishOffOwner(Pending p) {
        if (p.finishing || p.newClient == null || !p.oldClosed) return;
        p.finishing = true;
        // Persistence barriers must not run on CIO completion workers or CacheD workers.
        try {
            ThreadPool.getInstance().execute(() -> {
                try {
                    if (isExpired(p) || !p.newClient.isConnected()) { fail(p); return; }
                    final CacheD cache = CacheD.getInstance();
                    if (!cache.flushCharacter(p.character, 10_000L)
                            || !cache.flushItemsByCharacter(p.characterId, 10_000L)
                            || !cache.flushDbOwner(p.characterId, 10_000L)
                            || !cache.flushAccount(p.account.getName(), 10_000L)
                            || !cache.flushDbOwner(CacheD.accountOrderKey(p.account.getName()), 10_000L)) {
                        LOG.warning("[INTER-TEST] persistence barrier failed: " + p.character);
                        fail(p); return;
                    }
                    // Complete on the new socket's existing CIO session executor.
                    if (!l1j.server.server.model.network.thread.CIOThreadCallback.getInstance()
                            .completeSession(p.newClient, () -> attach(p))) {
                        fail(p);
                    }
                } catch (Throwable e) {
                    LOG.log(Level.WARNING, "[INTER-TEST] handoff barrier failed", e);
                    fail(p);
                }
            });
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "[INTER-TEST] existing thread pool rejected handoff", ex);
            fail(p);
        }
    }

    private static void attach(Pending p) {
        final CIOClient next = p.newClient;
        try {
            if (isExpired(p) || !next.isConnected() || next.getAccount() != null
                    || LoginController.getInstance().getClientByAccount(p.account.getName()) != null
                    || L1World.getInstance().getPlayer(p.character) != null) {
                fail(p); return;
            }
            LoginController.getInstance().login(next, p.account);
            next.setAccount(p.account);
            next.setConnectionState(ConnectionState.AUTHENTICATED);
            final ProtoWriter writer = Proto.begin(next,
                    ProtoMsg.SC_HIBREED_AUTH_ACK_PACKET, 32);
            if (writer == null) { fail(p); return; }
            boolean sent = false;
            try {
                writer.int32(1, 1); // Result_sucess in the source pack.
                sent = Proto.commit(writer);
            } finally {
                Proto.abort(writer);
            }
            if (!sent) { fail(p); return; }
            // 116 is enqueued before the login/world frames on this CIO owner.
            // No artificial 2-second hold: EnterWorld performs its own async
            // CacheD load, then resumes on the same session owner.
            if (!next.isConnected() || next.getAccount() != p.account
                    || next.getConnectionState() != ConnectionState.AUTHENTICATED
                    || next.getActiveChar() != null || next.getConnection() == null) {
                fail(p);
                return;
            }
            LOG.info("[INTER-TEST] 116 committed (not client ACK); starting EnterWorld immediately: "
                    + p.character);
            CS_EnterWorld.doEnterWorld(p.character, next);
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "[INTER-TEST] new session admission failed", e);
            fail(p);
        }
    }

    /** Override normal logout get-back only for this authenticated, one-shot arrival. */
    public static void applyLoginDestination(CIOClient client, L1PcInstance pc) {
        synchronized (DominationHybrid.class) {
            final Pending p = pending;
            if (p == null || !p.finishing || p.newClient != client || isExpired(p)
                    || pc.getId() != p.characterId || !pc.getName().equals(p.character)) return;
            pc.setX(p.x);
            pc.setY(p.y);
            pc.setMap((short) p.mapId);
            LOG.info("[INTER-TEST] DB tower destination applied x=" + p.x
                    + " y=" + p.y + " map=" + p.mapId
                    + " return=" + p.backX + ',' + p.backY + ',' + p.backMapId);
        }
    }

    public static void onEnterComplete(CIOClient client, L1PcInstance pc) {
        synchronized (DominationHybrid.class) {
            Pending p = pending;
            if (p != null && p.finishing && p.newClient == client
                    && pc != null && pc.getId() == p.characterId) {
                pending = null;
                LOG.info("[INTER-TEST] entered same-server tower kind=" + p.interKind + " map=" + pc.getMapId());
                LOG.info("[INTER-TEST] hybrid post-enter teleport skipped (GM A/B only)");
            }
        }
    }

    private static void fail(Pending p) {
        final CIOClient next;
        synchronized (DominationHybrid.class) {
            if (pending != p) return;
            pending = null;
            next = p.newClient;
        }
        if (next != null) next.close();
    }

    private static void clear(Pending p) {
        synchronized (DominationHybrid.class) {
            if (pending == p) pending = null;
        }
    }

    private static boolean isExpired(Pending p) {
        return System.nanoTime() - p.createdNs > TTL_NS;
    }

    private static int parseIpv4(String addr) {
        if (addr == null) return -1;
        String[] parts = addr.split("\\.", -1);
        if (parts.length != 4) return -1;
        int result = 0;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return -1;
            int v = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') return -1;
                v = 10 * v + (c - '0');
            }
            if (v > 255) return -1;
            result = (result << 8) | v;
        }
        return result;
    }
}
