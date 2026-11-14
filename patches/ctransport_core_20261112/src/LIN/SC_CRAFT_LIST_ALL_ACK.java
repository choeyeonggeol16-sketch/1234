package LIN;

import LIN.SC_CRAFT_LIST_ALL_ACK.Craft;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeParsee;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeParseeFactory;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeParser;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.CraftProbability;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Stream.ProtoInputStream;
import l1j.server.server.utils.MJHexHelper;



public final class SC_CRAFT_LIST_ALL_ACK {

    private static final Logger _log = Logger.getLogger(LIN.SC_CRAFT_LIST_ALL_ACK.class.getName());

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
            final ArrayList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft> crafts = new ArrayList<>(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.all().values());
            ProtoWriter w = null;
            int inChunk = 0;
            int resultType = RP_CRAFT_LOAD_START;

            for (LIN.SC_CRAFT_LIST_ALL_ACK.Craft c : crafts) {
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
            _log.log(Level.SEVERE, "[SC_CraftListAllAck] cache build failed", e);
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
            _log.log(Level.SEVERE, "[SC_CraftListAllAck] npc cache build failed", e);
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
            _log.log(Level.WARNING, "[SC_CraftListAllAck] NPC craft id parsing failed", e);
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


    // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft
    public static class Craft {

        private int _id;
        private LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftAttr _attr;
        private int _requiredClasses;
        private LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredQuestList _requiredQuests;
        private LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredSpriteList _requiredSprites;
        private LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredItemList _requiredItems;
        private LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList _inputs;
        private LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList _outputs;
        private int _batchDelaySec;

        private static final HashMap<Integer, LIN.SC_CRAFT_LIST_ALL_ACK.Craft> CRAFTS = new HashMap<Integer, LIN.SC_CRAFT_LIST_ALL_ACK.Craft>();

        public static void put(LIN.SC_CRAFT_LIST_ALL_ACK.Craft c){ CRAFTS.put(c._id, c); }
        public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft get(int craftId){ return CRAFTS.get(craftId); }
        public static HashMap<Integer, LIN.SC_CRAFT_LIST_ALL_ACK.Craft> all(){ return CRAFTS; }
        public static void clear(){ CRAFTS.clear(); }

        /** LEN 필드 본문을 바이트로 뽑아서 서브 파서에 넘김 */
        static byte[] readSub(ProtoInputStream in) throws IOException {
            byte[] b = in.readBytes();
            return b == null ? new byte[0] : b;
        }

        // ── getter/setter ──
        public int id(){ return _id; }
        public void set_id(int v){ _id = v; }
        public LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftAttr craft_attr(){ return _attr; }
        public void set_craft_attr(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftAttr v){ _attr = v; }
        public int required_classes(){ return _requiredClasses; }
        public void set_required_classes(int v){ _requiredClasses = v; }
        public LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredQuestList required_quests(){ return _requiredQuests; }
        public void set_required_quests(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredQuestList v){ _requiredQuests = v; }
        public LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredSpriteList required_sprites(){ return _requiredSprites; }
        public void set_required_sprites(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredSpriteList v){ _requiredSprites = v; }
        public LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredItemList required_items(){ return _requiredItems; }
        public void set_required_items(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredItemList v){ _requiredItems = v; }
        public LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList inputs(){ return _inputs; }
        public void set_inputs(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList v){ _inputs = v; }
        public LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList outputs(){ return _outputs; }
        public void set_outputs(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList v){ _outputs = v; }
        public int batch_delay_sec(){ return _batchDelaySec; }
        public void set_batch_delay_sec(int v){ _batchDelaySec = v; }

        // ── 역직렬화 (craftinfo.dat) ──
        public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft parse(ProtoInputStream in) throws IOException {
            LIN.SC_CRAFT_LIST_ALL_ACK.Craft c = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft();
            while(!in.isAtEnd()){
                int tag = in.readTag();
                switch(tag >>> 3){
                case 1: c._id = in.readInt32(); break;
                case 2: c._attr = LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftAttr.parse(in.readSlice()); break;
                case 3: c._requiredClasses = in.readInt32(); break;
                case 4: c._requiredQuests = LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredQuestList.parse(in.readSlice()); break;
                case 5: c._requiredSprites = LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredSpriteList.parse(in.readSlice()); break;
                case 6: c._requiredItems = LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredItemList.parse(in.readSlice()); break;
                case 7: c._inputs = LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.parse(in.readSlice()); break;
                case 8: c._outputs = LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.parse(in.readSlice()); break;
                case 9: c._batchDelaySec = in.readInt32(); break;
                default: in.skipField(tag); break;
                }
            }
            return c;
        }
        
        public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft getCraft(int craftId){
            return all().get(craftId);
        }

        // ── 직렬화 ──
        public void writeTo(ProtoWriter w){
            w.int32(1, _id);
            if(_attr != null){ w.msg(2); _attr.writeTo(w); w.end(); }
            w.int32(3, _requiredClasses);
            if(_requiredQuests != null){ w.msg(4); _requiredQuests.writeTo(w); w.end(); }
            if(_requiredSprites != null){ w.msg(5); _requiredSprites.writeTo(w); w.end(); }
            if(_requiredItems != null){ w.msg(6); _requiredItems.writeTo(w); w.end(); }
            if(_inputs != null){ w.msg(7); _inputs.writeTo(w); w.end(); }
            if(_outputs != null){ w.msg(8); _outputs.writeTo(w); w.end(); }
            w.int32(9, _batchDelaySec);
        }


        // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftAttr
        public static class CraftAttr {

            private int _desc;
            private int _minLevel;
            private int _maxLevel;
            private int _requiredGender;
            private int _minAlign;
            private int _maxAlign;
            private int _minKarma;
            private int _maxKarma;
            private int _maxCount;
            private boolean _show;

            // getter/setter
            public int desc(){ return _desc; }
            public void set_desc(int v){ _desc = v; }
            public int min_level(){ return _minLevel; }
            public void set_min_level(int v){ _minLevel = v; }
            public int max_level(){ return _maxLevel; }
            public void set_max_level(int v){ _maxLevel = v; }
            public int required_gender(){ return _requiredGender; }
            public void set_required_gender(int v){ _requiredGender = v; }
            public int min_align(){ return _minAlign; }
            public void set_min_align(int v){ _minAlign = v; }
            public int max_align(){ return _maxAlign; }
            public void set_max_align(int v){ _maxAlign = v; }
            public int min_karma(){ return _minKarma; }
            public void set_min_karma(int v){ _minKarma = v; }
            public int max_karma(){ return _maxKarma; }
            public void set_max_karma(int v){ _maxKarma = v; }
            public int max_count(){ return _maxCount; }
            public void set_max_count(int v){ _maxCount = v; }
            public boolean show(){ return _show; }
            public void set_show(boolean v){ _show = v; }

            public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftAttr parse(ProtoInputStream in) throws IOException {
                LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftAttr a = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftAttr();
                while(!in.isAtEnd()){
                    int tag = in.readTag();
                    switch(tag >>> 3){
                    case 1: a._desc = in.readInt32(); break;
                    case 2: a._minLevel = in.readInt32(); break;
                    case 3: a._maxLevel = in.readInt32(); break;
                    case 4: a._requiredGender = in.readInt32(); break;
                    case 5: a._minAlign = in.readInt32(); break;
                    case 6: a._maxAlign = in.readInt32(); break;
                    case 7: a._minKarma = in.readInt32(); break;
                    case 8: a._maxKarma = in.readInt32(); break;
                    case 9: a._maxCount = in.readInt32(); break;
                    case 10: a._show = in.readBool(); break;
                    default: in.skipField(tag); break;
                    }
                }
                return a;
            }

            public void writeTo(ProtoWriter w){
                w.int32(1, _desc);
                w.int32(2, _minLevel);
                w.int32(3, _maxLevel);
                w.int32(4, _requiredGender);
                w.int32(5, _minAlign);
                w.int32(6, _maxAlign);
                w.int32(7, _minKarma);
                w.int32(8, _maxKarma);
                w.int32(9, _maxCount);
                w.bool(10, _show);
            }
        }


        // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList
        public static class CraftInputList {

            private static final int FIELD_ARR_INPUT_ITEM  = 1;
            private static final int FIELD_ARR_OPTION_ITEM = 2;

            private ArrayList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem> _arr_input_item  = new ArrayList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem>();
            private ArrayList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem> _arr_option_item = new ArrayList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem>();

            public ArrayList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem> arr_input_item()  { return _arr_input_item; }
            public void add_arr_input_item(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem v)       { _arr_input_item.add(v); }
            public boolean has_arr_input_item()                    { return !_arr_input_item.isEmpty(); }

            public ArrayList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem> arr_option_item() { return _arr_option_item; }
            public void add_arr_option_item(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem v)      { _arr_option_item.add(v); }

            /** 유저 슬롯 검증 + 성공확률 증가치 계산 */
            public int[] calculateSlotAndIncreaseProb(LinkedList<LIN.CS_CRAFT_MAKE_REQ.CraftInputItemSlotInfo> slotInfo){
                int currentSlotNumber = 1;
                int increase_prob = 0;
                if (slotInfo == null || slotInfo.isEmpty()) return new int[]{0, 0};

                for (LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem input : _arr_input_item) {
                    int slot = input.slot();
                    if (currentSlotNumber < slot) {
                        break;
                    } else if (currentSlotNumber > slot)
                        continue;

                    if (currentSlotNumber > slotInfo.size()) break;
                    LIN.CS_CRAFT_MAKE_REQ.CraftInputItemSlotInfo sInfo = slotInfo.get(currentSlotNumber - 1);
                    if (sInfo.validation(input)) {
                        increase_prob += input.increase_prob();
                        sInfo.set_count(input.count());
                        ++currentSlotNumber;
                    }
                }
                return new int[]{currentSlotNumber, increase_prob};
            }

            // ── 역직렬화 ──
            public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList parse(ProtoInputStream in) throws IOException {
                LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList l = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList();
                while (!in.isAtEnd()) {
                    int tag = in.readTag();
                    switch (tag >>> 3) {
                    case FIELD_ARR_INPUT_ITEM:  l.add_arr_input_item(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem.parse(in.readSlice())); break;
                    case FIELD_ARR_OPTION_ITEM: l.add_arr_option_item(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem.parse(in.readSlice())); break;
                    default: in.skipField(tag); break;
                    }
                }
                return l;
            }

            // ── 직렬화 ──
            public void writeTo(ProtoWriter w) {
                for (LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem v : _arr_input_item) {
                    w.msg(FIELD_ARR_INPUT_ITEM);
                    v.writeTo(w);
                    w.end();
                }
                for (LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem v : _arr_option_item) {
                    w.msg(FIELD_ARR_OPTION_ITEM);
                    v.writeTo(w);
                    w.end();
                }
            }


            // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem
            public static class CraftInputItem {

                private int _nameId;
                private int _count;
                private int _slot;
                private int _enchant;
                private int _bless;
                private String _desc;
                private int _iconId;
                private int _elementalEnchantType;
                private int _elementalEnchantValue;
                private int _increaseProb;
                private boolean _allEnchantsAllowed;

                // getter/setter
                public int name_id(){ return _nameId; }
                public void set_name_id(int v){ _nameId = v; }
                public int count(){ return _count; }
                public void set_count(int v){ _count = v; }
                public int slot(){ return _slot; }
                public void set_slot(int v){ _slot = v; }
                public int enchant(){ return _enchant; }
                public void set_enchant(int v){ _enchant = v; }
                public int bless(){ return _bless; }
                public void set_bless(int v){ _bless = v; }
                public String desc(){ return _desc; }
                public void set_desc(String v){ _desc = v; }
                public int iconId(){ return _iconId; }
                public void set_iconId(int v){ _iconId = v; }
                public int elemental_enchant_type(){ return _elementalEnchantType; }
                public void set_elemental_enchant_type(int v){ _elementalEnchantType = v; }
                public int elemental_enchant_value(){ return _elementalEnchantValue; }
                public void set_elemental_enchant_value(int v){ _elementalEnchantValue = v; }
                public int increase_prob(){ return _increaseProb; }
                public void set_increase_prob(int v){ _increaseProb = v; }
                public boolean all_enchants_allowed(){ return _allEnchantsAllowed; }
                public void set_all_enchants_allowed(boolean v){ _allEnchantsAllowed = v; }

                public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem parse(ProtoInputStream in) throws IOException {
                    LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem i = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftInputList.CraftInputItem();
                    while(!in.isAtEnd()){
                        int tag = in.readTag();
                        switch(tag >>> 3){
                        case 1: i._nameId = in.readInt32(); break;
                        case 2: i._count = in.readInt32(); break;
                        case 3: i._slot = in.readInt32(); break;
                        case 4: i._enchant = in.readInt32(); break;
                        case 5: i._bless = in.readInt32(); break;
                        case 6: i._desc = in.readString(); break;
                        case 7: i._iconId = in.readInt32(); break;
                        case 8: i._elementalEnchantType = in.readInt32(); break;
                        case 9: i._elementalEnchantValue = in.readInt32(); break;
                        case 10: i._increaseProb = in.readInt32(); break;
                        case 11: i._allEnchantsAllowed = in.readBool(); break;
                        default: in.skipField(tag); break;
                        }
                    }
                    return i;
                }
                

                public void writeTo(ProtoWriter w){
                    w.int32(1, _nameId);
                    w.int32(2, _count);
                    w.int32(3, _slot);
                    w.int32(4, _enchant);
                    w.int32(5, _bless);
                    w.string(6, _desc);
                    w.int32(7, _iconId);
                    w.int32(8, _elementalEnchantType);
                    w.int32(9, _elementalEnchantValue);
                    w.int32(10, _increaseProb);
                    w.bool(11, _allEnchantsAllowed);
                }
            }
        }


        // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList
        public static class CraftOutputList {

            public static final int CRAFT_ITEM_SUCCESS_MILLIONS = 1000000;

            private LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList _success;
            private LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList _failure;
            private int _successProbByMillion;

            // getter/setter
            public LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList success(){ return _success; }
            public void set_success(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList v){ _success = v; }
            public LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList failure(){ return _failure; }
            public void set_failure(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList v){ _failure = v; }
            public int success_prob_by_million(){ return _successProbByMillion; }
            public void set_success_prob_by_million(int v){ _successProbByMillion = v; }

            // ── 역직렬화 ──
            public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList parse(ProtoInputStream in) throws IOException {
                LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList l = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList();
                while(!in.isAtEnd()){
                    int tag = in.readTag();
                    switch(tag >>> 3){
                    case 1: l._success = LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.parse(in.readSlice()); break;
                    case 2: l._failure = LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.parse(in.readSlice()); break;
                    case 3: l._successProbByMillion = in.readInt32(); break;
                    default: in.skipField(tag); break;
                    }
                }
                return l;
            }
            

            public l1j.server.server.packets.Protocol.Packet.Craft.CraftOutputItemResult createOutputItem(int increase_prob, int craft_id){
                int prob = success_prob_by_million() + increase_prob;
                if(prob >= CRAFT_ITEM_SUCCESS_MILLIONS)
                    return new l1j.server.server.packets.Protocol.Packet.Craft.CraftOutputItemResult(extractOutputItem(success(), prob), true);

                int real_prob = (int)((double)prob * Config.CRAFT_INCREASE_PROB_BYMILLION);
                Integer db_prob = CraftProbability.getInstance().get(craft_id);
                if(db_prob != null && db_prob > 0)
                    real_prob = db_prob;

                if(MJRnd.isWinning(CRAFT_ITEM_SUCCESS_MILLIONS, real_prob))
                    return new l1j.server.server.packets.Protocol.Packet.Craft.CraftOutputItemResult(extractOutputItem(success(), real_prob), true);

                LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList fail = failure();
                if(fail.prob_count() <= 0 && fail.non_prob_count() <= 0)
                    return new l1j.server.server.packets.Protocol.Packet.Craft.CraftOutputItemResult(null, false);
                return new l1j.server.server.packets.Protocol.Packet.Craft.CraftOutputItemResult(extractOutputItem(failure(), real_prob), false);
            }

            private LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem extractOutputItem(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList sfList, int prob){
                int probCount = sfList.prob_count();
                if(probCount > 0){
                    java.util.List<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem> outputItems = sfList.output_prob_items();
                    return outputItems.get((probCount == 1 || l1j.server.MJTemplate.MJRnd.isWinning(CRAFT_ITEM_SUCCESS_MILLIONS, prob) ? 0 : l1j.server.MJTemplate.MJRnd.next(outputItems.size() - 1) + 1));
                }
                return sfList.output_items().get(0);
            }

            // ── 직렬화 ──
            public void writeTo(ProtoWriter w){
                if(_success != null){ w.msg(1); _success.writeTo(w); w.end(); }
                if(_failure != null){ w.msg(2); _failure.writeTo(w); w.end(); }
                w.int32(3, _successProbByMillion);
            }


            // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList
            public static class CraftOutputSFList {

                private long _onFlag1;
                private long _onFlag2;
                private long _offFlag1;
                private long _offFlag2;
                private int _probCount;
                private int _nonProbCount;
                private LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem> _outputProbItems;
                private LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem> _outputItems;
                private LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftEvent> _events;
                private int _eventCount;

                // getter/setter
                public long get_onFlag1(){ return _onFlag1; }
                public void set_onFlag1(long v){ _onFlag1 = v; }
                public long get_onFlag2(){ return _onFlag2; }
                public void set_onFlag2(long v){ _onFlag2 = v; }
                public long get_offFlag1(){ return _offFlag1; }
                public void set_offFlag1(long v){ _offFlag1 = v; }
                public long get_offFlag2(){ return _offFlag2; }
                public void set_offFlag2(long v){ _offFlag2 = v; }
                public int prob_count(){ return _probCount; }
                public void set_prob_count(int v){ _probCount = v; }
                public int non_prob_count(){ return _nonProbCount; }
                public void set_non_prob_count(int v){ _nonProbCount = v; }
                public LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem> output_prob_items(){ return _outputProbItems; }
                public void add_outputProbItem(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem v){
                    if(_outputProbItems == null) _outputProbItems = new LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem>();
                    _outputProbItems.add(v);
                }
                public LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem> output_items(){ return _outputItems; }
                public void add_outputItem(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem v){
                    if(_outputItems == null) _outputItems = new LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem>();
                    _outputItems.add(v);
                }
                public LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftEvent> events(){ return _events; }
                public void add_event(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftEvent v){
                    if(_events == null) _events = new LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftEvent>();
                    _events.add(v);
                }
                public int event_count(){ return _eventCount; }
                public void set_event_count(int v){ _eventCount = v; }

                // ── 역직렬화 (craftinfo.dat) ──
                public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList parse(ProtoInputStream in) throws IOException {
                    LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList l = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList();
                    while(!in.isAtEnd()){
                        int tag = in.readTag();
                        switch(tag >>> 3){
                        case 1: parseQuestFlag(in, l, true); break;      // on_flag
                        case 2: parseQuestFlag(in, l, false); break;     // off_flag
                        case 3: l._probCount = in.readInt32(); break;
                        case 4: l._nonProbCount = in.readInt32(); break;
                        case 5: l.add_outputProbItem(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem.parse(in.readSlice())); break;
                        case 6: l.add_outputItem(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem.parse(in.readSlice())); break;
                        case 7: l.add_event(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftEvent.parse(in.readSlice())); break;
                        case 8: l._eventCount = in.readInt32(); break;
                        default: in.skipField(tag); break;
                        }
                    }
                    return l;
                }

                /** CraftQuestFlag(flag1/flag2) 서브메시지 파싱 — 별도 클래스 대신 인라인 */
                private static void parseQuestFlag(ProtoInputStream in, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList target, boolean isOn) throws IOException {
                    byte[] b = in.readBytes();
                    ProtoInputStream sub = ProtoInputStream.newInstance(b == null ? new byte[0] : b);
                    long f1 = 0, f2 = 0;
                    while(!sub.isAtEnd()){
                        int tag = sub.readTag();
                        switch(tag >>> 3){
                        case 1: f1 = sub.readInt64(); break;
                        case 2: f2 = sub.readInt64(); break;
                        default: sub.skipField(tag); break;
                        }
                    }
                    if(isOn){
                        target._onFlag1 = f1;
                        target._onFlag2 = f2;
                    }else{
                        target._offFlag1 = f1;
                        target._offFlag2 = f2;
                    }
                }

                // ── 직렬화 ──
                public void writeTo(ProtoWriter w){
                    w.msg(1);                                    // on_flag
                    w.int64(1, _onFlag1);
                    w.int64(2, _onFlag2);
                    w.end();
                    w.msg(2);                                    // off_flag
                    w.int64(1, _offFlag1);
                    w.int64(2, _offFlag2);
                    w.end();
                    w.int32(3, _probCount);
                    w.int32(4, _nonProbCount);
                    if(_outputProbItems != null){
                        for(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem v : _outputProbItems){ w.msg(5); v.writeTo(w); w.end(); }
                    }
                    if(_outputItems != null){
                        for(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem v : _outputItems){ w.msg(6); v.writeTo(w); w.end(); }
                    }
                    if(_events != null){
                        for(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftEvent v : _events){ w.msg(7); v.writeTo(w); w.end(); }
                    }
                    w.int32(8, _eventCount);
                }


                // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftEvent
                public static class CraftEvent {

                    private int _eventId;
                    private int _probCount;
                    private int _nonProbCount;
                    private LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem> _outputProbItems;
                    private LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem> _outputItems;

                    // getter/setter
                    public int event_id(){ return _eventId; }
                    public void set_event_id(int v){ _eventId = v; }
                    public int prob_count(){ return _probCount; }
                    public void set_prob_count(int v){ _probCount = v; }
                    public int non_prob_count(){ return _nonProbCount; }
                    public void set_non_prob_count(int v){ _nonProbCount = v; }
                    public LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem> output_prob_items(){ return _outputProbItems; }
                    public void add_outputProbItem(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem v){
                        if(_outputProbItems == null) _outputProbItems = new LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem>();
                        _outputProbItems.add(v);
                    }
                    public LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem> output_items(){ return _outputItems; }
                    public void add_outputItem(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem v){
                        if(_outputItems == null) _outputItems = new LinkedList<LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem>();
                        _outputItems.add(v);
                    }

                    public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftEvent parse(ProtoInputStream in) throws IOException {
                        LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftEvent e = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftEvent();
                        while(!in.isAtEnd()){
                            int tag = in.readTag();
                            switch(tag >>> 3){
                            case 1: e._eventId = in.readInt32(); break;
                            case 2: e._probCount = in.readInt32(); break;
                            case 3: e._nonProbCount = in.readInt32(); break;
                            case 4: e.add_outputProbItem(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem.parse(in.readSlice())); break;
                            case 5: e.add_outputItem(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem.parse(in.readSlice())); break;
                            default: in.skipField(tag); break;
                            }
                        }
                        return e;
                    }

                    public void writeTo(ProtoWriter w){
                        w.int32(1, _eventId);
                        w.int32(2, _probCount);
                        w.int32(3, _nonProbCount);
                        if(_outputProbItems != null){
                            for(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem v : _outputProbItems){ w.msg(4); v.writeTo(w); w.end(); }
                        }
                        if(_outputItems != null){
                            for(LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem v : _outputItems){ w.msg(5); v.writeTo(w); w.end(); }
                        }
                    }
                }


                // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem
                public static class CraftOutputItem {

                    private int _nameId;
                    private int _count;
                    private int _slot;
                    private int _enchant;
                    private int _bless;
                    private int _elementalType;
                    private int _elementalLevel;
                    private String _desc;
                    private int _systemDesc;
                    private int _broadcastDesc;
                    private int _iconId;
                    private String _url;
                    private byte[] _extraDesc;
                    private int _inheritEnchantFrom;
                    private int _inheritElementalEnchantFrom;
                    private int _eventId;

                    // getter/setter
                    public int name_id(){ return _nameId; }
                    public void set_name_id(int v){ _nameId = v; }
                    public int count(){ return _count; }
                    public void set_count(int v){ _count = v; }
                    public int slot(){ return _slot; }
                    public void set_slot(int v){ _slot = v; }
                    public int enchant(){ return _enchant; }
                    public void set_enchant(int v){ _enchant = v; }
                    public int bless(){ return _bless; }
                    public void set_bless(int v){ _bless = v; }
                    public int elemental_type(){ return _elementalType; }
                    public void set_elemental_type(int v){ _elementalType = v; }
                    public int elemental_level(){ return _elementalLevel; }
                    public void set_elemental_level(int v){ _elementalLevel = v; }
                    public String desc(){ return _desc; }
                    public void set_desc(String v){ _desc = v; }
                    public int system_desc(){ return _systemDesc; }
                    public void set_system_desc(int v){ _systemDesc = v; }
                    public int broadcast_desc(){ return _broadcastDesc; }
                    public void set_broadcast_desc(int v){ _broadcastDesc = v; }
                    public int iconId(){ return _iconId; }
                    public void set_iconId(int v){ _iconId = v; }
                    public String url(){ return _url; }
                    public void set_url(String v){ _url = v; }
                    public byte[] extra_desc(){ return _extraDesc; }
                    public void set_extra_desc(byte[] v){ _extraDesc = v; }
                    public int inherit_enchant_from(){ return _inheritEnchantFrom; }
                    public void set_inherit_enchant_from(int v){ _inheritEnchantFrom = v; }
                    public int inherit_elemental_enchant_from(){ return _inheritElementalEnchantFrom; }
                    public void set_inherit_elemental_enchant_from(int v){ _inheritElementalEnchantFrom = v; }
                    public int event_id(){ return _eventId; }
                    public void set_event_id(int v){ _eventId = v; }

                    public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem parse(ProtoInputStream in) throws IOException {
                        LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem o = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftOutputList.CraftOutputSFList.CraftOutputItem();
                        while(!in.isAtEnd()){
                            int tag = in.readTag();
                            switch(tag >>> 3){
                            case 1: o._nameId = in.readInt32(); break;
                            case 2: o._count = in.readInt32(); break;
                            case 3: o._slot = in.readInt32(); break;
                            case 4: o._enchant = in.readInt32(); break;
                            case 5: o._bless = in.readInt32(); break;
                            case 6: o._elementalType = in.readInt32(); break;
                            case 7: o._elementalLevel = in.readInt32(); break;
                            case 8: o._desc = in.readString(); break;
                            case 9: o._systemDesc = in.readInt32(); break;
                            case 10: o._broadcastDesc = in.readInt32(); break;
                            case 11: o._iconId = in.readInt32(); break;
                            case 12: o._url = in.readString(); break;
                            case 13: o._extraDesc = in.readBytes(); break;
                            case 14: o._inheritEnchantFrom = in.readInt32(); break;
                            case 15: o._inheritElementalEnchantFrom = in.readInt32(); break;
                            case 16: o._eventId = in.readInt32(); break;
                            default: in.skipField(tag); break;
                            }
                        }
                        return o;
                    }

                    public void writeTo(ProtoWriter w){
                        w.int32(1, _nameId);
                        w.int32(2, _count);
                        w.int32(3, _slot);
                        w.int32(4, _enchant);
                        w.int32(5, _bless);
                        w.int32(6, _elementalType);
                        w.int32(7, _elementalLevel);
                        w.string(8, _desc);
                        w.int32(9, _systemDesc);
                        w.int32(10, _broadcastDesc);
                        w.int32(11, _iconId);
                        w.string(12, _url);
                        w.bytes(13, _extraDesc);
                        w.int32(14, _inheritEnchantFrom);
                        w.int32(15, _inheritElementalEnchantFrom);
                        w.int32(16, _eventId);
                    }
                }
            }
        }


        // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredItemList
        public static class CraftRequiredItemList {

            private static final int ITEM_FIELD_NAME_ID	= 1;
            private static final int ITEM_FIELD_COUNT	= 2;

            private boolean _andOr;
            private int _count;
            private ArrayList<int[]> _items = new ArrayList<int[]>();	// [0]=nameId, [1]=count

            // getter/setter
            public boolean and_or(){ return _andOr; }
            public void set_and_or(boolean v){ _andOr = v; }
            public int count(){ return _count; }
            public void set_count(int v){ _count = v; }
            public ArrayList<int[]> items(){ return _items; }
            public void add_item(int nameId, int itemCount){
                _items.add(new int[]{ nameId, itemCount });
            }

            // ── 역직렬화 ──
            public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredItemList parse(ProtoInputStream in) throws IOException {
                LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredItemList l = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredItemList();
                while(!in.isAtEnd()){
                    int tag = in.readTag();
                    switch(tag >>> 3){
                    case 1: l._andOr = in.readBool(); break;
                    case 2: l._count = in.readInt32(); break;
                    case 3: parseItem(in, l); break;
                    default: in.skipField(tag); break;
                    }
                }
                return l;
            }

            private static void parseItem(ProtoInputStream in, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredItemList target) throws IOException {
                byte[] b = in.readBytes();
                ProtoInputStream sub = ProtoInputStream.newInstance(b == null ? new byte[0] : b);
                int nameId = 0, itemCount = 0;
                while(!sub.isAtEnd()){
                    int tag = sub.readTag();
                    switch(tag >>> 3){
                    case ITEM_FIELD_NAME_ID: nameId = sub.readInt32(); break;
                    case ITEM_FIELD_COUNT: itemCount = sub.readInt32(); break;
                    default: sub.skipField(tag); break;
                    }
                }
                target.add_item(nameId, itemCount);
            }

            // ── 직렬화 ──
            public void writeTo(ProtoWriter w){
                w.bool(1, _andOr);
                w.int32(2, _count);
                for(int[] item : _items){
                    w.msg(3);
                    w.int32(ITEM_FIELD_NAME_ID, item[0]);
                    w.int32(ITEM_FIELD_COUNT, item[1]);
                    w.end();
                }
            }
        }


        // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredQuestList
        public static class CraftRequiredQuestList {

            private boolean _andOr;
            private int _count;
            private long _flag1;
            private long _flag2;

            // getter/setter
            public boolean and_or(){ return _andOr; }
            public void set_and_or(boolean v){ _andOr = v; }
            public int count(){ return _count; }
            public void set_count(int v){ _count = v; }
            public long get_flag1(){ return _flag1; }
            public void set_flag1(long v){ _flag1 = v; }
            public long get_flag2(){ return _flag2; }
            public void set_flag2(long v){ _flag2 = v; }

            // ── 역직렬화 ──
            public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredQuestList parse(ProtoInputStream in) throws IOException {
                LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredQuestList q = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredQuestList();
                while(!in.isAtEnd()){
                    int tag = in.readTag();
                    switch(tag >>> 3){
                    case 1: q._andOr = in.readBool(); break;
                    case 2: q._count = in.readInt32(); break;
                    case 3: parseFlag(in, q); break;
                    default: in.skipField(tag); break;
                    }
                }
                return q;
            }

            private static void parseFlag(ProtoInputStream in, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredQuestList target) throws IOException {
                byte[] b = in.readBytes();
                ProtoInputStream sub = ProtoInputStream.newInstance(b == null ? new byte[0] : b);
                while(!sub.isAtEnd()){
                    int tag = sub.readTag();
                    switch(tag >>> 3){
                    case 1: target._flag1 = sub.readInt64(); break;
                    case 2: target._flag2 = sub.readInt64(); break;
                    default: sub.skipField(tag); break;
                    }
                }
            }

            // ── 직렬화 ──
            public void writeTo(ProtoWriter w){
                w.bool(1, _andOr);
                w.int32(2, _count);
                w.msg(3);                       // flag 서브메시지
                w.int64(1, _flag1);
                w.int64(2, _flag2);
                w.end();
            }
        }


        // Descriptor: LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredSpriteList
        public static class CraftRequiredSpriteList {

            // ⚠ MJ 원본 내부 CraftRequiredSprite의 writeTo에서 번호 확인 (아마 1=sprite_id, 2=count)
            private static final int SPRITE_FIELD_ID	= 1;
            private static final int SPRITE_FIELD_COUNT	= 2;

            private boolean _andOr;
            private int _count;
            private ArrayList<int[]> _sprites = new ArrayList<int[]>();	// [0]=spriteId, [1]=count

            // getter/setter
            public boolean get_andOr(){ return _andOr; }
            public void set_andOr(boolean v){ _andOr = v; }
            public int count(){ return _count; }
            public void set_count(int v){ _count = v; }
            public ArrayList<int[]> get_sprites(){ return _sprites; }
            public void add_sprite(int spriteId, int spriteCount){
                _sprites.add(new int[]{ spriteId, spriteCount });
            }

            // ── 역직렬화 ──
            public static LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredSpriteList parse(ProtoInputStream in) throws IOException {
                LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredSpriteList s = new LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredSpriteList();
                while(!in.isAtEnd()){
                    int tag = in.readTag();
                    switch(tag >>> 3){
                    case 1: s._andOr = in.readBool(); break;
                    case 2: s._count = in.readInt32(); break;
                    case 3: parseSprite(in, s); break;
                    default: in.skipField(tag); break;
                    }
                }
                return s;
            }

            private static void parseSprite(ProtoInputStream in, LIN.SC_CRAFT_LIST_ALL_ACK.Craft.CraftRequiredSpriteList target) throws IOException {
                byte[] b = in.readBytes();
                ProtoInputStream sub = ProtoInputStream.newInstance(b == null ? new byte[0] : b);
                int spriteId = 0, spriteCount = 0;
                while(!sub.isAtEnd()){
                    int tag = sub.readTag();
                    switch(tag >>> 3){
                    case SPRITE_FIELD_ID: spriteId = sub.readInt32(); break;
                    case SPRITE_FIELD_COUNT: spriteCount = sub.readInt32(); break;
                    default: sub.skipField(tag); break;
                    }
                }
                target.add_sprite(spriteId, spriteCount);
            }

            // ── 직렬화 ──
            public void writeTo(ProtoWriter w){
                w.bool(1, _andOr);
                w.int32(2, _count);
                for(int[] spr : _sprites){
                    w.msg(3);
                    w.int32(SPRITE_FIELD_ID, spr[0]);
                    w.int32(SPRITE_FIELD_COUNT, spr[1]);
                    w.end();
                }
            }
        }
    }
}