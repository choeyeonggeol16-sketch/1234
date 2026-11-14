package l1j.server.server.packets.Protocol.Packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeParsee;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeParseeFactory;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeParser;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.Craft.Craft;
import l1j.server.server.utils.MJHexHelper;

public final class SC_CRAFT_LIST_ALL_ACK {

    private static final Logger _log = Logger.getLogger(SC_CRAFT_LIST_ALL_ACK.class.getName());

    private static final ConcurrentHashMap<CIOClient, Boolean> CRAFT_SENDING = new ConcurrentHashMap<>();

    /*
     * 단일 제작 목록 protobuf chunk 제한.
     * 실제 전송은 write completion 기반으로 한 청크씩 진행하므로
     * 전체 1.2MB를 한꺼번에 Client queue에 밀어 넣지 않는다.
     */
    private static final int SAFE_CHUNK_BYTES = 55300;
    private static final AtomicLong SEND_SEQUENCE = new AtomicLong();
    
    private static final long CRAFT_CHUNK_DELAY_MS = 50L;

    public static int BATCH_TRANSACTION = 0;
    public static final int CRAFT_ITEM_SUCCESS_MILLIONS = 1000000;

    public static final int RP_CRAFT_LOAD_START = 0;
    public static final int RP_CRAFT_LOADING = 1;
    public static final int RP_CRAFT_LOAD_FINISH = 2;
    public static final int RP_ERROR_SAME_HASH_VAL = 3;
    public static final int RP_ERROR_INVALID_HASH_VAL = 4;

    private static ArrayList<byte[]> _craftCaches;
    private static byte[] _craftSameCache;
    private static HashMap<Integer, byte[]> _npc2craftId;

    private SC_CRAFT_LIST_ALL_ACK() {}

    // ------------------------------------------------------------------
    // 전체 제작 리스트 캐시
    // ------------------------------------------------------------------

    public static void buildCaches() {
        final ArrayList<byte[]> caches = new ArrayList<>();

        try {
            final ArrayList<Craft> crafts = new ArrayList<>(Craft.all().values());
            ProtoWriter w = null;
            int inChunk = 0;
            int resultType = RP_CRAFT_LOAD_START;

            for (Craft c : crafts) {
                if (w == null) {
                    w = ProtoWriter.newInstance();
                    w.enumV(1, resultType);
                    resultType = RP_CRAFT_LOADING;
                }

                w.msg(2);
                c.writeTo(w);
                w.end();
                ++inChunk;

                // 개수 제한 + body size 이중 제한
                if (w.length() >= SAFE_CHUNK_BYTES || inChunk >= Config.CRAFT_FLAGMENTATION_SIZE) {
                    caches.add(w.finishWithTrailer(ProtoMsg.SC_CRAFT_LIST_ALL_ACK));
                    w = null;
                    inChunk = 0;
                }
            }

            // 마지막 데이터 chunk
            if (w != null) {
                caches.add(w.finishWithTrailer(ProtoMsg.SC_CRAFT_LIST_ALL_ACK));
                w = null;
            }

            // LOAD_FINISH
            w = ProtoWriter.newInstance();
            w.enumV(1, RP_CRAFT_LOAD_FINISH);
            caches.add(w.finishWithTrailer(ProtoMsg.SC_CRAFT_LIST_ALL_ACK));

            // SAME_HASH
            _craftSameCache = makeErrorCache(RP_ERROR_SAME_HASH_VAL);

            // startup 이후 읽기 전용
            _craftCaches = caches;

        } catch (Exception e) {
            _log.log(Level.SEVERE, "[SC_CRAFT_LIST_ALL_ACK] cache build failed", e);
        }
    }

    private static byte[] makeErrorCache(int resultCode) {
        final ProtoWriter w = ProtoWriter.newInstance();
        w.enumV(1, resultCode);
        return w.finishWithTrailer(ProtoMsg.SC_CRAFT_LIST_ALL_ACK);
    }

    // ------------------------------------------------------------------
    // 제작 목록 요청
    // ------------------------------------------------------------------

    public static void send(final L1PcInstance pc, final byte[] hash_val) {
        if (pc == null) {
            return;
        }

        if (_craftCaches == null || _craftSameCache == null) {
            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, makeErrorCache(RP_ERROR_SAME_HASH_VAL));
            return;
        }

        final CIOClient client = pc.getNetConnection();

        if (client == null || !client.isConnected()) {
            return;
        }

        /*
         * 제작 전체 목록을 이미 보내는 중이면
         * SAME_HASH도 보내지 말고 그냥 무시한다.
         *
         * 전송 도중 다른 제작 응답이 끼면
         * 클라 제작 로딩 상태가 깨질 수 있다.
         */
        if (CRAFT_SENDING.containsKey(client)) {
            return;
        }

        final long curSecond = System.currentTimeMillis() / 1000L;

        /*
         * 전체 전송 중이 아닌 상태에서만
         * 기존 연타 제한 적용.
         */
        if (pc.getQuizTime() + 10 > curSecond) {
            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, _craftSameCache);
            return;
        }

        pc.setQuizTime(curSecond);

        if (MJHexHelper.compareArrays(Config.CRAFT_VERSION_HASH, hash_val, Config.CRAFT_VERSION_HASH.length)) {
            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, _craftSameCache);
            return;
        }

        if (Config.CRAFT_TRANSMIT_SAFELINE <= L1World.getInstance().getAllPlayersSize()) {
            pc.sendPackets("현재 클라이언트의 제작 정보가 이전 버전임으로 모든 목록이 나오지 않을 수 있습니다.");
            pc.sendPackets("접속기에서 오류패치를 하신 후 다시 접속하면 모든 목록을 받아볼 수 있습니다.");
            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, _craftSameCache);
            return;
        }

        final ArrayList<byte[]> caches = _craftCaches;

        if (caches.isEmpty()) {
            return;
        }

        /*
         * 여기서부터 이 클라이언트는 제작목록 전송 중.
         */
        if (CRAFT_SENDING.putIfAbsent(client, Boolean.TRUE) != null) {
            return;
        }

        final long sendId = SEND_SEQUENCE.incrementAndGet();

        // The scheduler returns null on a full/shutting-down queue. Never
        // retain the per-connection sending barrier for a task that cannot run.
        if (ThreadPool.getInstance().schedule(
                () -> sendNextChunk(pc, client, caches, sendId, 0, 0L, System.nanoTime()), 0L) == null) {
            CRAFT_SENDING.remove(client);
            _log.warning("[CRAFT ADMISSION] initial chunk rejected id=" + sendId);
        }
    }

    // ------------------------------------------------------------------
    // Write completion 기반 제작 목록 전송
    // ------------------------------------------------------------------

    private static void sendNextChunk(
            final L1PcInstance pc, 
            final CIOClient client, 
            final ArrayList<byte[]> caches, 
            final long sendId, 
            final int index, 
            final long sentBytes, 
            final long startNanos) {

        if (pc == null || client == null || caches == null || !client.isConnected()) {
            if (client != null) {
                CRAFT_SENDING.remove(client);
            }
            return;
        }

        if (index >= caches.size()) {
            CRAFT_SENDING.remove(client);
            return;
        }

        final byte[] bytes = caches.get(index);

        if (bytes == null || bytes.length == 0) {
            scheduleNextChunk(pc, client, caches, sendId, index + 1, sentBytes, startNanos);
            return;
        }

        try {
            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, bytes);
        } catch (Throwable t) {
            CRAFT_SENDING.remove(client);
            _log.log(Level.SEVERE, String.format("[CRAFT SEND FAIL] id=%d idx=%d/%d IP=%s", 
                    sendId, index, caches.size() - 1, client.getHostAddress()), t);
            return;
        }

        final long nextSentBytes = sentBytes + bytes.length;
        final int nextIndex = index + 1;

        /*
         * 마지막 LOAD_FINISH까지 전송했으면
         * 실제 소켓 drain 이후 sending 상태 해제.
         */
        if (nextIndex >= caches.size()) {
            final boolean registered = client.whenWriteDrained(() -> {
                CRAFT_SENDING.remove(client);
                _log.info(String.format("[CRAFT SEND END] id=%d bytes=%d IP=%s", 
                        sendId, nextSentBytes, client.getHostAddress()));
            });

            if (!registered) {
                CRAFT_SENDING.remove(client);
            }
            return;
        }

        /*
         * 소켓 drain 후 다음 청크.
         */
        final boolean registered = client.whenWriteDrained(
                () -> scheduleNextChunk(pc, client, caches, sendId, nextIndex, nextSentBytes, startNanos));

        if (!registered) {
            CRAFT_SENDING.remove(client);
            if (client.isConnected()) {
                _log.warning(String.format("[CRAFT BACKPRESSURE] callback 등록 실패 id=%d idx=%d IP=%s", 
                        sendId, index, client.getHostAddress()));
            }
        }
    }

    private static void scheduleNextChunk(
            final L1PcInstance pc, 
            final CIOClient client, 
            final ArrayList<byte[]> caches, 
            final long sendId, 
            final int index, 
            final long sentBytes, 
            final long startNanos) {

        if (!client.isConnected()) {
            CRAFT_SENDING.remove(client);
            return;
        }

        // whenWriteDrained already fired. If this delayed admission fails,
        // there will be no more callbacks to clear CRAFT_SENDING.
        if (ThreadPool.getInstance().schedule(
                () -> sendNextChunk(pc, client, caches, sendId, index, sentBytes, startNanos),
                CRAFT_CHUNK_DELAY_MS) == null) {
            CRAFT_SENDING.remove(client);
            _log.warning("[CRAFT ADMISSION] continuation rejected id=" + sendId
                    + " index=" + index);
        }
    }

    // ------------------------------------------------------------------
    // NPC별 craft id list 캐시
    // ------------------------------------------------------------------

    public static void buildNpcCaches() {
        final HashMap<Integer, byte[]> map = new HashMap<>();

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement("select npc_id, craft_list from craftlist");
             ResultSet rs = pstm.executeQuery()) {

            final MJArrangeParsee<Integer> parsee = MJArrangeParseeFactory.createIntArrange();

            while (rs.next()) {
                map.put(rs.getInt("npc_id"), buildNpcAckBytes(rs.getString("craft_list"), parsee));
            }

            _npc2craftId = map;

        } catch (Exception e) {
            _log.log(Level.SEVERE, "[SC_CRAFT_LIST_ALL_ACK] npc cache build failed", e);
        }
    }

    
    // ------------------------------------------------------------------
    // NPC craft ID ACK
    // ------------------------------------------------------------------

    private static byte[] buildNpcAckBytes(String craftListCsv, MJArrangeParsee<Integer> parsee) {
        final ProtoWriter w = ProtoWriter.newInstance();

        w.enumV(1, 0);
        w.int32(3, 0);
        w.enumV(4, 0);

        try {
            final Integer[] ids = (Integer[]) MJArrangeParser.parsing(craftListCsv, ",", parsee).result();

            for (Integer id : ids) {
                if (id == null) continue;

                w.msg(2);
                w.int32(1, id);
                w.int32(2, 0);
                w.int32(3, 0);
                w.end();
            }
        } catch (Exception e) {
            _log.log(Level.WARNING, "[SC_CRAFT_LIST_ALL_ACK] NPC craft id parsing failed", e);
        }

        return w.finishWithTrailer(ProtoMsg.SC_CRAFT_ID_LIST_ACK);
    }

    public static byte[] getCraftIdListFromNpcId(int npcId) {
        return _npc2craftId == null ? null : _npc2craftId.get(npcId);
    }
    
    public static void onDisconnect(CIOClient client) {
        if (client != null) {
            CRAFT_SENDING.remove(client);
        }
    }
}