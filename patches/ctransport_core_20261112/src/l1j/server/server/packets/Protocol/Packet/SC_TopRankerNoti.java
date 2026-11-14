package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeHelper;
import l1j.server.MJTemplate.MJClassesType.EClassesType;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.rank.buff.RankBuff;
import l1j.server.server.model.rank.loader.RankLoadManager;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.Ranker.Ranker;

/**
 * SC_TOP_RANKER_NOTI (0x0089) — 개인 랭킹 상태 + 랭킹 버프
 */
public class SC_TopRankerNoti extends ProtoPacket implements Comparable<SC_TopRankerNoti> {

    private static final double _expError = 0.05D;

    public static boolean isError(int e1, int e2){
        double diff = e1 - e2;
        return diff < ((double)e2 * _expError);
    }

    /** 순위 → 버프등급 매핑 (인덱스=순위, 값=버프레벨) */
    private static final int[] _rank2RankLevel;

    /** 버프레벨 → 스킬ID (인덱스=버프레벨) */
    private static final int[] RANK_BUFF_SKILLS;

    static{
        _rank2RankLevel = new int[201];
        setRange(_rank2RankLevel,   0,  10, 11);
        setRange(_rank2RankLevel,  11,  20, 10);
        setRange(_rank2RankLevel,  21,  40,  9);
        setRange(_rank2RankLevel,  41,  60,  8);
        setRange(_rank2RankLevel,  61,  80,  7);
        setRange(_rank2RankLevel,  81, 100,  6);
        setRange(_rank2RankLevel, 101, 120,  5);
        setRange(_rank2RankLevel, 121, 140,  4);
        setRange(_rank2RankLevel, 141, 160,  3);
        setRange(_rank2RankLevel, 161, 180,  2);
        setRange(_rank2RankLevel, 181, 200,  1);

        RANK_BUFF_SKILLS = new int[10];
        RANK_BUFF_SKILLS[1] = L1SkillId.RANK_BUFF_1;
        RANK_BUFF_SKILLS[2] = L1SkillId.RANK_BUFF_2;
        RANK_BUFF_SKILLS[3] = L1SkillId.RANK_BUFF_3;
        RANK_BUFF_SKILLS[4] = L1SkillId.RANK_BUFF_4;
        RANK_BUFF_SKILLS[5] = L1SkillId.RANK_BUFF_5;
        RANK_BUFF_SKILLS[6] = L1SkillId.RANK_BUFF_6;
        RANK_BUFF_SKILLS[7] = L1SkillId.RANK_BUFF_7;
        RANK_BUFF_SKILLS[8] = L1SkillId.RANK_BUFF_8;
        RANK_BUFF_SKILLS[9] = L1SkillId.RANK_BUFF_9;
        
    }

    private static void setRange(int[] arr, int from, int to, int level){
        for(int i = from; i <= to; i++)
            arr[i] = level;
    }

    /** 캐릭이 현재 보유중인 랭킹버프 스킬 레벨 (없으면 0) */
    private static int heldRankBuffLevel(L1PcInstance pc){
        for(int lv = 1; lv <= 9; lv++){
            if(pc.hasSkillEffect(RANK_BUFF_SKILLS[lv]))
                return lv;
        }
        if(pc.hasSkillEffect(L1SkillId.RANK_BUFF_10_STR)
                || pc.hasSkillEffect(L1SkillId.RANK_BUFF_10_DEX)
                || pc.hasSkillEffect(L1SkillId.RANK_BUFF_10_INT))
            return 10;
        if(pc.hasSkillEffect(L1SkillId.RANK_BUFF_11_STR)
                || pc.hasSkillEffect(L1SkillId.RANK_BUFF_11_DEX)
                || pc.hasSkillEffect(L1SkillId.RANK_BUFF_11_INT))
            return 11;
        return 0;
    }

    // ── 데이터 ──
    private int _objectId;
    private L1PcInstance _characterInstance;
    private int _exp;
    private int _class;
    private Ranker _total_ranker;
    private Ranker _class_ranker;
    private boolean _almost_upper_total;
    private boolean _almost_lower_total;
    private boolean _almost_upper_class;
    private boolean _almost_lower_class;

    // ── has 플래그 ──
    private boolean _hasTotalRanker, _hasClassRanker;
    private boolean _hasUpperTotal, _hasLowerTotal, _hasUpperClass, _hasLowerClass;

    // ────────────── 팩토리 ──────────────

    public static SC_TopRankerNoti newInstance(L1PcInstance pc){
        SC_TopRankerNoti noti = newInstance();
        noti.set_objectId(pc.getId());
        noti.set_exp(pc.getExp());

        Ranker rnk = Ranker.newInstance(pc.getName(), EClassesType.fromGfx(pc.getClassId()));
        noti.set_class_ranker(rnk);
        noti.set_class(rnk.get_class());
        rnk.set_previous_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);
        rnk.set_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);
        rnk = rnk.deepCopy();
        rnk.set_previous_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
        rnk.set_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
        noti.set_total_ranker(rnk);
        noti.set_characterInstance(pc);
        noti.set_almost_lower_class(false);
        noti.set_almost_lower_total(false);
        noti.set_almost_upper_class(false);
        noti.set_almost_upper_total(false);
        return noti;
    }

    public static SC_TopRankerNoti newInstance(ResultSet rs) throws SQLException{
        SC_TopRankerNoti noti = newInstance();
        noti.set_objectId(rs.getInt("objid"));
        noti.set_exp(rs.getInt("Exp"));

        Ranker rnk = Ranker.newInstance(rs);
        noti.set_class_ranker(rnk);
        noti.set_class(rnk.get_class());
        rnk.set_previous_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);
        rnk.set_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);

        rnk = rnk.deepCopy();
        rnk.set_previous_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
        rnk.set_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
        noti.set_total_ranker(rnk);
        noti.set_characterInstance(L1World.getInstance().getPlayer(rnk.get_name()));
        noti.set_almost_lower_class(false);
        noti.set_almost_lower_total(false);
        noti.set_almost_upper_class(false);
        noti.set_almost_upper_total(false);
        return noti;
    }

    public static SC_TopRankerNoti newDummyInstance(){
        SC_TopRankerNoti noti = newInstance();
        noti.set_objectId(1);
        noti.set_exp(100);

        Ranker rnk = Ranker.newInstance("", EClassesType.fromRand());
        noti.set_class_ranker(rnk);
        noti.set_class(rnk.get_class());
        rnk.set_previous_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);
        rnk.set_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);

        rnk = rnk.deepCopy();
        rnk.set_previous_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
        rnk.set_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
        noti.set_total_ranker(rnk);
        noti.set_characterInstance(null);
        noti.set_almost_lower_class(false);
        noti.set_almost_lower_total(false);
        noti.set_almost_upper_class(false);
        noti.set_almost_upper_total(false);
        return noti;
    }

    public static SC_TopRankerNoti newInstance(){
        return new SC_TopRankerNoti();
    }

    // ────────────── 버프 정합화 (단일 진입점) ──────────────

    /**
     * rating 기준으로 캐릭터의 랭킹버프/스탯 상태를 정합화.
     * 접속/재접속/주기갱신 어디서 불려도 항상 같은 결과.
     *   rating == 보유스킬  → 무시 (정합)
     *   rating != 보유스킬  → 틀린 것 제거 후 올바른 것 부여
     * rating == 0 → 버프가 있으면 제거만. rating이 나중에 부여되면
     *              다음 syncBuff에서 자동 부여된다.
     */
    public void syncBuff(){
        L1PcInstance pc = _characterInstance;
        if(pc == null)
            return;

        int currentRating = _total_ranker.get_rating();
        int held = heldRankBuffLevel(pc);
        if(currentRating == held)
            return;

        int classId = _class;
        int classGfx = EClassesType.fromGfx(pc.getClassId()).toInt();

        if(held != 0)
            RankBuff.remove(pc, held, classId, classGfx);   
        if(currentRating != 0)
            RankBuff.apply(pc, currentRating, classId, classGfx);
    }

    /** 주기갱신 — 기존 호출부 호환 */
    public void buff(){
        if(!RankLoadManager.MRK_SYS_ISON)
            return;
        syncBuff();
    }

    /** 접속 — 기존 호출부 호환 */
    public void onBuff(){
        syncBuff();
    }

    /** 명시적 해제 — rating 0 처리 후 정합화 */
    public void offBuff(){
        _total_ranker.set_rating(0);
        _class_ranker.set_rating(0);
        syncBuff();
    }

    // ────────────── 랭킹 갱신 로직 ──────────────

    public boolean isInRank(){
        return _characterInstance == null || _characterInstance.getLevel() >= RankLoadManager.MRK_SYS_MINLEVEL;
    }

    public void updateRating(int class_place, int total_place){
        if(RankLoadManager.MRK_SYS_ISON){
            int total_rating = total_place <= 0 || total_place >= _rank2RankLevel.length ? 0 : _rank2RankLevel[total_place];
            _class_ranker.set_rating(total_rating);
            _total_ranker.set_rating(total_rating);
            if(total_rating < 9 && class_place <= 3){
                _class_ranker.set_class_rating(11);
                _total_ranker.set_class_rating(11);
            }
        }
    }

    public void updatePlace(int class_place, int total_place){
        _class_ranker.set_rank(class_place);
        _total_ranker.set_rank(total_place);

        int limit_place = RankLoadManager.MRK_SYS_CLASS_RANGE + 1;
        int prev_place = _class_ranker.get_previous_rank();
        if(prev_place == limit_place && class_place < limit_place)
            _class_ranker.set_previous_rank(class_place);

        limit_place = RankLoadManager.MRK_SYS_TOTAL_RANGE + 1;
        prev_place = _total_ranker.get_previous_rank();
        if(prev_place == limit_place && total_place < limit_place)
            _total_ranker.set_previous_rank(total_place);
    }

    public void updateAlmost(SC_TopRankerNoti prev_class, SC_TopRankerNoti prev_total){
        _almost_upper_class = false;
        _almost_upper_total = false;
        if(prev_class != null){
            _almost_upper_class = isError(prev_class.get_exp(), get_exp());
            prev_class.set_almost_lower_class(_almost_upper_class);
        }

        if(prev_total != null){
            _almost_upper_total = isError(prev_total.get_exp(), get_exp());
            prev_total.set_almost_lower_total(_almost_upper_total);
        }
    }

    public void updatePlace(){
        _total_ranker.set_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
        _class_ranker.set_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);
        _class_ranker.set_rating(0);
        _total_ranker.set_rating(0);
        _class_ranker.set_class_rating(0);
        _total_ranker.set_class_rating(0);
    }

    public void updateAlmost(){
        _almost_upper_total =
        _almost_lower_total =
        _almost_upper_class =
        _almost_lower_class = false;
    }

    // ────────────── getter/setter ──────────────

    public int get_objectId(){ return _objectId; }
    public void set_objectId(int val){ _objectId = val; }

    public L1PcInstance get_characterInstance(){ return _characterInstance; }
    public void set_characterInstance(L1PcInstance pc){ _characterInstance = pc; }

    public int get_exp(){
        if(_characterInstance != null)
            _exp = _characterInstance.getExp();
        return _exp;
    }
    public void set_exp(int exp){ _exp = exp; }

    public int get_class(){ return _class; }
    public void set_class(int cls){ _class = cls; }

    public Ranker get_total_ranker(){ return _total_ranker; }
    public void set_total_ranker(Ranker val){ _hasTotalRanker = true; _total_ranker = val; }

    public Ranker get_class_ranker(){ return _class_ranker; }
    public void set_class_ranker(Ranker val){ _hasClassRanker = true; _class_ranker = val; }

    public boolean get_almost_upper_total(){ return _almost_upper_total; }
    public void set_almost_upper_total(boolean val){ _hasUpperTotal = true; _almost_upper_total = val; }

    public boolean get_almost_lower_total(){ return _almost_lower_total; }
    public void set_almost_lower_total(boolean val){ _hasLowerTotal = true; _almost_lower_total = val; }

    public boolean get_almost_upper_class(){ return _almost_upper_class; }
    public void set_almost_upper_class(boolean val){ _hasUpperClass = true; _almost_upper_class = val; }

    public boolean get_almost_lower_class(){ return _almost_lower_class; }
    public void set_almost_lower_class(boolean val){ _hasLowerClass = true; _almost_lower_class = val; }

    // ────────────── 직렬화 ──────────────

    @Override
    public int msgId() { return ProtoMsg.SC_TOP_RANKER_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        if (_hasTotalRanker) {
            w.msg(1);
            _total_ranker.writeTo(w);
            w.end();
        }
        if (_hasClassRanker) {
            w.msg(2);
            _class_ranker.writeTo(w);
            w.end();
        }
        if (_hasUpperTotal) w.bool(3, _almost_upper_total);
        if (_hasLowerTotal) w.bool(4, _almost_lower_total);
        if (_hasUpperClass) w.bool(5, _almost_upper_class);
        if (_hasLowerClass) w.bool(6, _almost_lower_class);
    }

    @Override
    public int compareTo(SC_TopRankerNoti o) {
        return o.get_exp() - get_exp();
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(
            ProtoWriter w, int _objectId, L1PcInstance _characterInstance, int _exp, int _class, Ranker _total_ranker,
            Ranker _class_ranker, boolean _almost_upper_total, boolean _almost_lower_total, boolean _almost_upper_class,
            boolean _almost_lower_class, boolean _hasTotalRanker, boolean _hasClassRanker, boolean _hasUpperTotal,
            boolean _hasLowerTotal, boolean _hasUpperClass, boolean _hasLowerClass) {
        if (_hasTotalRanker) {
                w.msg(1);
                _total_ranker.writeTo(w);
                w.end();
            }
            if (_hasClassRanker) {
                w.msg(2);
                _class_ranker.writeTo(w);
                w.end();
            }
            if (_hasUpperTotal) w.bool(3, _almost_upper_total);
            if (_hasLowerTotal) w.bool(4, _almost_lower_total);
            if (_hasUpperClass) w.bool(5, _almost_upper_class);
            if (_hasLowerClass) w.bool(6, _almost_lower_class);
    }

    public static boolean sendFields(
            L1PcInstance recipient, int _objectId, L1PcInstance _characterInstance, int _exp, int _class,
            Ranker _total_ranker, Ranker _class_ranker, boolean _almost_upper_total, boolean _almost_lower_total,
            boolean _almost_upper_class, boolean _almost_lower_class, boolean _hasTotalRanker, boolean _hasClassRanker,
            boolean _hasUpperTotal, boolean _hasLowerTotal, boolean _hasUpperClass, boolean _hasLowerClass) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), _objectId, _characterInstance, _exp, _class,
                _total_ranker, _class_ranker, _almost_upper_total, _almost_lower_total, _almost_upper_class,
                _almost_lower_class, _hasTotalRanker, _hasClassRanker, _hasUpperTotal, _hasLowerTotal, _hasUpperClass,
                _hasLowerClass);
    }

    public static boolean sendFields(
            CIOClient recipient, int _objectId, L1PcInstance _characterInstance, int _exp, int _class, Ranker _total_ranker,
            Ranker _class_ranker, boolean _almost_upper_total, boolean _almost_lower_total, boolean _almost_upper_class,
            boolean _almost_lower_class, boolean _hasTotalRanker, boolean _hasClassRanker, boolean _hasUpperTotal,
            boolean _hasLowerTotal, boolean _hasUpperClass, boolean _hasLowerClass) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_TOP_RANKER_NOTI, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(
                    writer, _objectId, _characterInstance, _exp, _class, _total_ranker, _class_ranker, _almost_upper_total,
                    _almost_lower_total, _almost_upper_class, _almost_lower_class, _hasTotalRanker, _hasClassRanker,
                    _hasUpperTotal, _hasLowerTotal, _hasUpperClass, _hasLowerClass);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(
            int _objectId, L1PcInstance _characterInstance, int _exp, int _class, Ranker _total_ranker,
            Ranker _class_ranker, boolean _almost_upper_total, boolean _almost_lower_total, boolean _almost_upper_class,
            boolean _almost_lower_class, boolean _hasTotalRanker, boolean _hasClassRanker, boolean _hasUpperTotal,
            boolean _hasLowerTotal, boolean _hasUpperClass, boolean _hasLowerClass) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(
                writer, _objectId, _characterInstance, _exp, _class, _total_ranker, _class_ranker, _almost_upper_total,
                _almost_lower_total, _almost_upper_class, _almost_lower_class, _hasTotalRanker, _hasClassRanker,
                _hasUpperTotal, _hasLowerTotal, _hasUpperClass, _hasLowerClass);
        return writer.finishWithTrailer(ProtoMsg.SC_TOP_RANKER_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        int proto69_objectId = 0;
        L1PcInstance proto69_characterInstance = null;
        int proto69_exp = 0;
        int proto69_class = 0;
        Ranker proto69_total_ranker = null;
        Ranker proto69_class_ranker = null;
        boolean proto69_almost_upper_total = false;
        boolean proto69_almost_lower_total = false;
        boolean proto69_almost_upper_class = false;
        boolean proto69_almost_lower_class = false;
        boolean proto69_hasTotalRanker = false;
        boolean proto69_hasClassRanker = false;
        boolean proto69_hasUpperTotal = false;
        boolean proto69_hasLowerTotal = false;
        boolean proto69_hasUpperClass = false;
        boolean proto69_hasLowerClass = false;

        return sendFields(
                protoRecipient, proto69_objectId, proto69_characterInstance, proto69_exp, proto69_class,
                proto69_total_ranker, proto69_class_ranker, proto69_almost_upper_total, proto69_almost_lower_total,
                proto69_almost_upper_class, proto69_almost_lower_class, proto69_hasTotalRanker, proto69_hasClassRanker,
                proto69_hasUpperTotal, proto69_hasLowerTotal, proto69_hasUpperClass, proto69_hasLowerClass);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient, L1PcInstance pc) {
        int notiWire_objectId = 0;
                L1PcInstance notiWire_characterInstance = null;
                int notiWire_exp = 0;
                int notiWire_class = 0;
                Ranker notiWire_total_ranker = null;
                Ranker notiWire_class_ranker = null;
                boolean notiWire_almost_upper_total = false;
                boolean notiWire_almost_lower_total = false;
                boolean notiWire_almost_upper_class = false;
                boolean notiWire_almost_lower_class = false;
                boolean notiWire_hasTotalRanker = false;
                boolean notiWire_hasClassRanker = false;
                boolean notiWire_hasUpperTotal = false;
                boolean notiWire_hasLowerTotal = false;
                boolean notiWire_hasUpperClass = false;
                boolean notiWire_hasLowerClass = false;

                { notiWire_objectId = (pc.getId()); }
                { notiWire_exp = (pc.getExp()); }

                Ranker rnk = Ranker.newInstance(pc.getName(), EClassesType.fromGfx(pc.getClassId()));
                { notiWire_hasClassRanker = true; notiWire_class_ranker = rnk; }
                { notiWire_class = (rnk.get_class()); }
                rnk.set_previous_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);
                rnk.set_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);
                rnk = rnk.deepCopy();
                rnk.set_previous_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
                rnk.set_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
                { notiWire_hasTotalRanker = true; notiWire_total_ranker = rnk; }
                { notiWire_characterInstance = pc; }
                { notiWire_hasLowerClass = true; notiWire_almost_lower_class = false; }
                { notiWire_hasLowerTotal = true; notiWire_almost_lower_total = false; }
                { notiWire_hasUpperClass = true; notiWire_almost_upper_class = false; }
                { notiWire_hasUpperTotal = true; notiWire_almost_upper_total = false; }
                return SC_TopRankerNoti.sendFields(
                        protoRecipient, notiWire_objectId, notiWire_characterInstance, notiWire_exp, notiWire_class,
                        notiWire_total_ranker, notiWire_class_ranker, notiWire_almost_upper_total,
                        notiWire_almost_lower_total, notiWire_almost_upper_class, notiWire_almost_lower_class,
                        notiWire_hasTotalRanker, notiWire_hasClassRanker, notiWire_hasUpperTotal, notiWire_hasLowerTotal,
                        notiWire_hasUpperClass, notiWire_hasLowerClass);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient, L1PcInstance pc) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection(), pc);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient, ResultSet rs) throws SQLException {
        int notiWire_objectId = 0;
                L1PcInstance notiWire_characterInstance = null;
                int notiWire_exp = 0;
                int notiWire_class = 0;
                Ranker notiWire_total_ranker = null;
                Ranker notiWire_class_ranker = null;
                boolean notiWire_almost_upper_total = false;
                boolean notiWire_almost_lower_total = false;
                boolean notiWire_almost_upper_class = false;
                boolean notiWire_almost_lower_class = false;
                boolean notiWire_hasTotalRanker = false;
                boolean notiWire_hasClassRanker = false;
                boolean notiWire_hasUpperTotal = false;
                boolean notiWire_hasLowerTotal = false;
                boolean notiWire_hasUpperClass = false;
                boolean notiWire_hasLowerClass = false;

                { notiWire_objectId = (rs.getInt("objid")); }
                { notiWire_exp = (rs.getInt("Exp")); }

                Ranker rnk = Ranker.newInstance(rs);
                { notiWire_hasClassRanker = true; notiWire_class_ranker = rnk; }
                { notiWire_class = (rnk.get_class()); }
                rnk.set_previous_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);
                rnk.set_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);

                rnk = rnk.deepCopy();
                rnk.set_previous_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
                rnk.set_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
                { notiWire_hasTotalRanker = true; notiWire_total_ranker = rnk; }
                { notiWire_characterInstance = (L1World.getInstance().getPlayer(rnk.get_name())); }
                { notiWire_hasLowerClass = true; notiWire_almost_lower_class = false; }
                { notiWire_hasLowerTotal = true; notiWire_almost_lower_total = false; }
                { notiWire_hasUpperClass = true; notiWire_almost_upper_class = false; }
                { notiWire_hasUpperTotal = true; notiWire_almost_upper_total = false; }
                return SC_TopRankerNoti.sendFields(
                        protoRecipient, notiWire_objectId, notiWire_characterInstance, notiWire_exp, notiWire_class,
                        notiWire_total_ranker, notiWire_class_ranker, notiWire_almost_upper_total,
                        notiWire_almost_lower_total, notiWire_almost_upper_class, notiWire_almost_lower_class,
                        notiWire_hasTotalRanker, notiWire_hasClassRanker, notiWire_hasUpperTotal, notiWire_hasLowerTotal,
                        notiWire_hasUpperClass, notiWire_hasLowerClass);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient, ResultSet rs) throws SQLException {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection(), rs);
    }

    public static boolean sendNewDummyInstance(CIOClient protoRecipient) {
        int notiWire_objectId = 0;
                L1PcInstance notiWire_characterInstance = null;
                int notiWire_exp = 0;
                int notiWire_class = 0;
                Ranker notiWire_total_ranker = null;
                Ranker notiWire_class_ranker = null;
                boolean notiWire_almost_upper_total = false;
                boolean notiWire_almost_lower_total = false;
                boolean notiWire_almost_upper_class = false;
                boolean notiWire_almost_lower_class = false;
                boolean notiWire_hasTotalRanker = false;
                boolean notiWire_hasClassRanker = false;
                boolean notiWire_hasUpperTotal = false;
                boolean notiWire_hasLowerTotal = false;
                boolean notiWire_hasUpperClass = false;
                boolean notiWire_hasLowerClass = false;

                { notiWire_objectId = 1; }
                { notiWire_exp = 100; }

                Ranker rnk = Ranker.newInstance("", EClassesType.fromRand());
                { notiWire_hasClassRanker = true; notiWire_class_ranker = rnk; }
                { notiWire_class = (rnk.get_class()); }
                rnk.set_previous_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);
                rnk.set_rank(RankLoadManager.MRK_SYS_CLASS_RANGE + 1);

                rnk = rnk.deepCopy();
                rnk.set_previous_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
                rnk.set_rank(RankLoadManager.MRK_SYS_TOTAL_RANGE + 1);
                { notiWire_hasTotalRanker = true; notiWire_total_ranker = rnk; }
                { notiWire_characterInstance = null; }
                { notiWire_hasLowerClass = true; notiWire_almost_lower_class = false; }
                { notiWire_hasLowerTotal = true; notiWire_almost_lower_total = false; }
                { notiWire_hasUpperClass = true; notiWire_almost_upper_class = false; }
                { notiWire_hasUpperTotal = true; notiWire_almost_upper_total = false; }
                return SC_TopRankerNoti.sendFields(
                        protoRecipient, notiWire_objectId, notiWire_characterInstance, notiWire_exp, notiWire_class,
                        notiWire_total_ranker, notiWire_class_ranker, notiWire_almost_upper_total,
                        notiWire_almost_lower_total, notiWire_almost_upper_class, notiWire_almost_lower_class,
                        notiWire_hasTotalRanker, notiWire_hasClassRanker, notiWire_hasUpperTotal, notiWire_hasLowerTotal,
                        notiWire_hasUpperClass, notiWire_hasLowerClass);
    }

    public static boolean sendNewDummyInstance(L1PcInstance protoRecipient) {
        return sendNewDummyInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }

    public static boolean sendNewInstance(CIOClient protoRecipient) {
        return sendCtor(protoRecipient);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
