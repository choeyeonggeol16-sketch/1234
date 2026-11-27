package l1j.server.server.packets.Protocol.MainServer.Proto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.Craft;
import l1j.server.server.shared.arrangement.ArrangeParsee;
import l1j.server.server.shared.arrangement.ArrangeParseeFactory;
import l1j.server.server.shared.arrangement.ArrangeParser;
import l1j.server.server.utils.HexHelper;

public final class SC_CRAFT_LIST_ALL_ACK {

    private static final Logger _log = Logger.getLogger(SC_CRAFT_LIST_ALL_ACK.class.getName());

    /*
     * 단일 제작 목록 protobuf chunk 제한.
     * 실제 전송은 write completion 기반으로 한 청크씩 진행하므로
     * 전체 1.2MB를 한꺼번에 Client queue에 밀어 넣지 않는다.
     */
    private static final int SAFE_CHUNK_BYTES = 55300;
    private static final AtomicLong SEND_SEQUENCE = new AtomicLong();
    
    private static final long CRAFT_CHUNK_DELAY_MS = 50L;

    private static final java.util.concurrent.atomic.AtomicInteger BATCH_TRANSACTION =
            new java.util.concurrent.atomic.AtomicInteger(0);

    static int nextBatchTransaction() { return BATCH_TRANSACTION.getAndIncrement(); }
    public static final int CRAFT_ITEM_SUCCESS_MILLIONS = 1000000;

    public static final int RP_CRAFT_LOAD_START = 0;
    public static final int RP_CRAFT_LOADING = 1;
    public static final int RP_CRAFT_LOAD_FINISH = 2;
    public static final int RP_ERROR_SAME_HASH_VAL = 3;
    public static final int RP_ERROR_INVALID_HASH_VAL = 4;

    private static volatile ArrayList<byte[]> _craftCaches;
    private static final byte[] SAME_HASH_REPLY = makeErrorCache(RP_ERROR_SAME_HASH_VAL);
    private record NpcCatalog(byte[] ack, int[] craftIds) {}
    private static volatile java.util.Map<Integer, NpcCatalog> npcCatalogs = java.util.Map.of();

    private SC_CRAFT_LIST_ALL_ACK() {}

    // ------------------------------------------------------------------
    // 전체 제작 리스트 캐시
    // ------------------------------------------------------------------

    public static void buildCaches() {
        final ArrayList<byte[]> caches = new ArrayList<>();

        try (ProtoWriter w = ProtoWriter.borrow()) {
            final ArrayList<Craft> crafts = new ArrayList<>(Craft.all().values());
            int inChunk = 0;
            int resultType = RP_CRAFT_LOAD_START;

            for (Craft c : crafts) {
                if (inChunk == 0) {
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
                    w.reset();
                    inChunk = 0;
                }
            }

            // 마지막 데이터 chunk
            if (inChunk != 0) {
                caches.add(w.finishWithTrailer(ProtoMsg.SC_CRAFT_LIST_ALL_ACK));
                w.reset();
            }

            // LOAD_FINISH
            w.enumV(1, RP_CRAFT_LOAD_FINISH);
            caches.add(w.finishWithTrailer(ProtoMsg.SC_CRAFT_LIST_ALL_ACK));

            // startup 이후 읽기 전용
            _craftCaches = caches;

        } catch (Exception e) {
            _log.log(Level.SEVERE, "[SC_CRAFT_LIST_ALL_ACK] cache build failed", e);
        }
    }

    private static byte[] makeErrorCache(int resultCode) {
        try (ProtoWriter w = ProtoWriter.borrow()) {
            w.enumV(1, resultCode);
            return w.finishWithTrailer(ProtoMsg.SC_CRAFT_LIST_ALL_ACK);
        }
    }

    // ------------------------------------------------------------------
    // 제작 목록 요청
    // ------------------------------------------------------------------

    public static void send(final L1PcInstance pc, final byte[] hash_val) {
        if (pc == null) {
            return;
        }

        final ArrayList<byte[]> caches = _craftCaches;
        if (caches == null) {
            Proto.sendRaw(pc, SAME_HASH_REPLY);
            return;
        }

        final CUserSocket client = pc.getNetConnection();

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
        if (client.isCraftListSending()) {
            return;
        }

        final long curSecond = System.currentTimeMillis() / 1000L;

        /*
         * 전체 전송 중이 아닌 상태에서만
         * 기존 연타 제한 적용.
         */
        if (pc.getQuizTime() + 10 > curSecond) {
            Proto.sendRaw(pc, SAME_HASH_REPLY);
            return;
        }

        pc.setQuizTime(curSecond);

        if (HexHelper.compareArrays(Config.CRAFT_VERSION_HASH, hash_val, Config.CRAFT_VERSION_HASH.length)) {
            Proto.sendRaw(pc, SAME_HASH_REPLY);
            return;
        }

        if (Config.CRAFT_TRANSMIT_SAFELINE <= CWorld.getInstance().getAllPlayersSize()) {
            pc.sendPackets("현재 클라이언트의 제작 정보가 이전 버전임으로 모든 목록이 나오지 않을 수 있습니다.");
            pc.sendPackets("접속기에서 오류패치를 하신 후 다시 접속하면 모든 목록을 받아볼 수 있습니다.");
            Proto.sendRaw(pc, SAME_HASH_REPLY);
            return;
        }

        if (caches.isEmpty()) {
            return;
        }

        /*
         * 여기서부터 이 클라이언트는 제작목록 전송 중.
         */
        if (!client.beginCraftListSend()) {
            return;
        }

        final long sendId = SEND_SEQUENCE.incrementAndGet();

        scheduleNextChunk(pc, client, caches, sendId, pc.getSessionStamp(), 0, 0L);
    }

    // ------------------------------------------------------------------
    // Write completion 기반 제작 목록 전송
    // ------------------------------------------------------------------

    private static void sendNextChunk(
            final L1PcInstance pc, 
            final CUserSocket client, 
            final ArrayList<byte[]> caches, 
            final long sendId, 
            final int stamp,
            final int index, 
            final long sentBytes) {

        if (!client.isCurrentCharacter(pc) || pc.getSessionStamp() != stamp) {
            client.endCraftListSend();
            return;
        }

        if (index >= caches.size()) {
            client.endCraftListSend();
            return;
        }

        final byte[] bytes = caches.get(index);

        if (bytes == null || bytes.length == 0) {
            scheduleNextChunk(pc, client, caches, sendId, stamp, index + 1, sentBytes);
            return;
        }

        try {
            if (!Proto.sendRaw(client, bytes)) {
                client.endCraftListSend();
                return;
            }
        } catch (Throwable t) {
            client.endCraftListSend();
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
                client.endCraftListSend();
                _log.info(String.format("[CRAFT SEND END] id=%d bytes=%d IP=%s", 
                        sendId, nextSentBytes, client.getHostAddress()));
            });

            if (!registered) {
                client.endCraftListSend();
            }
            return;
        }

        /*
         * 소켓 drain 후 다음 청크.
         */
        final boolean registered = client.whenWriteDrained(
                () -> scheduleNextChunk(pc, client, caches, sendId, stamp, nextIndex, nextSentBytes));

        if (!registered) {
            client.endCraftListSend();
            if (client.isConnected()) {
                _log.warning(String.format("[CRAFT BACKPRESSURE] callback 등록 실패 id=%d idx=%d IP=%s", 
                        sendId, index, client.getHostAddress()));
            }
        }
    }

    private static void scheduleNextChunk(
            final L1PcInstance pc, 
            final CUserSocket client, 
            final ArrayList<byte[]> caches, 
            final long sendId, 
            final int stamp,
            final int index, 
            final long sentBytes) {

        if (!client.isCurrentCharacter(pc) || pc.getSessionStamp() != stamp) {
            client.endCraftListSend();
            return;
        }

        final long delayMs = index == 0 ? 0L : CRAFT_CHUNK_DELAY_MS;
        CIOThreadCallback.getInstance().completeSession(client, delayMs,
                () -> sendNextChunk(pc, client, caches, sendId, stamp, index, sentBytes), failure -> {
                    if (failure != null) {
                        client.endCraftListSend();
                        _log.log(Level.WARNING, "[CRAFT ADMISSION] chunk cancelled id="
                                + sendId + " index=" + index, failure);
                    }
                });
    }

    // ------------------------------------------------------------------
    // NPC별 craft id list 캐시
    // ------------------------------------------------------------------

    public static synchronized void buildNpcCaches() {
        final HashMap<Integer, NpcCatalog> map = new HashMap<>();

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement("select npc_id, craft_list from craftlist");
             ResultSet rs = pstm.executeQuery()) {

            final ArrangeParsee<Integer> parsee = ArrangeParseeFactory.createIntArrange();

            while (rs.next()) {
                map.put(rs.getInt("npc_id"), buildNpcCatalog(rs.getString("craft_list"), parsee));
            }

            npcCatalogs = java.util.Map.copyOf(map);

        } catch (Exception e) {
            _log.log(Level.SEVERE, "[SC_CRAFT_LIST_ALL_ACK] npc cache build failed", e);
        }
    }

    
    // ------------------------------------------------------------------
    // NPC craft ID ACK
    // ------------------------------------------------------------------

    private static NpcCatalog buildNpcCatalog(String csv, ArrangeParsee<Integer> parsee) {
        final Integer[] entries = csv == null || csv.isBlank() ? new Integer[0]
                : (Integer[]) ArrangeParser.parsing(csv, ",", parsee).result();
        final int[] craftIds = new int[entries.length];
        int count = 0;
        try (ProtoWriter w = ProtoWriter.borrow()) {
            w.enumV(1, 0);
            w.int32(3, 0);
            w.enumV(4, 0);
            for (Integer id : entries) {
                if (id == null || id <= 0) continue;
                craftIds[count++] = id;
                w.msg(2);
                w.int32(1, id);
                w.int32(2, 0);
                w.int32(3, 0);
                w.end();
            }
            final int[] allowed = java.util.Arrays.copyOf(craftIds, count);
            java.util.Arrays.sort(allowed);
            return new NpcCatalog(w.finishWithTrailer(ProtoMsg.SC_CRAFT_ID_LIST_ACK), allowed);
        }
    }

    public static byte[] getCraftIdListFromNpcId(int npcId) {
        final NpcCatalog catalog = npcCatalogs.get(npcId);
        return catalog == null ? null : catalog.ack();
    }

    static boolean offersRecipe(int npcId, int craftId) {
        final NpcCatalog catalog = npcCatalogs.get(npcId);
        return catalog != null && java.util.Arrays.binarySearch(catalog.craftIds(), craftId) >= 0;
    }

    public static void onDisconnect(CUserSocket client) {
        if (client != null) {
            client.endCraftListSend();
        }
    }
}
