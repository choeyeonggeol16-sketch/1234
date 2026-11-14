package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.MJCompanion.Instance.MJCompanionUpdateFlag;
import l1j.server.MJCompanion.Basic.ClassInfo.MJCompanionClassInfo;
import l1j.server.server.model.Ability;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_COMPANION_STATUS_NOTI (0x07D0) — 컴패니언 상태 통지 (부분 갱신 지원)
 *
 * 와이어 스키마 (구 generated에서 추출):
 *  [1] obj_id uint32 (required)
 *  [2] name bytes
 *  [3] class_id uint32
 *  [4] level uint32
 *  [5] exp int32
 *  [6] base_max_hp uint32, [7] temp_max_hp uint32, [8] hp uint32
 *  [9] base_str, [10] base_con, [11] base_int (uint32)
 *  [12] bonus_str, [13] bonus_con, [14] bonus_int (uint32)
 *  [15] elixir_use_count uint32, [16] remain_stats uint32
 *  [17] temp_str, [18] temp_con, [19] temp_int (uint32)
 *  [20] friend_ship_guage int32, [21] friend_ship_marble int32
 *  [22] minus_exp_penalty bool
 *  (field 23~29 미사용 — 구 스키마 그대로)
 *  [30] ac int32, [31] mr int32
 *  [32] movedelay_reduce double(fixed64), [33] attackdelay_reduce double(fixed64)
 *  [34] pvp_dmg_ratio uint32, [35] is_combo_time bool
 *
 * 부분 갱신 계약: update_flag에 해당하는 필드만 세팅 → 해당 필드만 와이어로 나감.
 *   has_* 플래그가 곧 protobuf 필드 존재 여부라서, 세팅 안 된 필드는 절대 나가면 안 됨.
 */
public final class SC_COMPANION_STATUS_NOTI extends ProtoPacket {

    private int _objId;
    private byte[] _name;
    private int _classId;
    private int _level;
    private int _exp;
    private int _baseMaxHp;
    private int _tempMaxHp;
    private int _hp;
    private int _baseStr;
    private int _baseCon;
    private int _baseInt;
    private int _bonusStr;
    private int _bonusCon;
    private int _bonusInt;
    private int _elixirUseCount;
    private int _remainStats;
    private int _tempStr;
    private int _tempCon;
    private int _tempInt;
    private int _friendShipGuage;
    private int _friendShipMarble;
    private boolean _minusExpPenalty;
    private int _ac;
    private int _mr;
    private double _movedelayReduce;
    private double _attackdelayReduce;
    private int _pvpDmgRatio;
    private boolean _isComboTime;

    private long _bit;   // has_* 플래그 — 구 generated 비트 레이아웃과 1:1 동일

    private SC_COMPANION_STATUS_NOTI() {}

    public static SC_COMPANION_STATUS_NOTI newInstance() {
        return new SC_COMPANION_STATUS_NOTI();
    }

    /**
     * 구 시그니처 동일 — update_flag 기반 부분 갱신.
     * 컴패니언 라인 전체가 이 메서드를 통해 상태를 통지한다.
     */
    public static void send(L1PcInstance master, L1CompanionInstance companionInstance, int updateFlag) {
        if (master == null || companionInstance == null) return;

        int notiWire_objId = 0;
        byte[] notiWire_name = null;
        int notiWire_classId = 0;
        int notiWire_level = 0;
        int notiWire_exp = 0;
        int notiWire_baseMaxHp = 0;
        int notiWire_tempMaxHp = 0;
        int notiWire_hp = 0;
        int notiWire_baseStr = 0;
        int notiWire_baseCon = 0;
        int notiWire_baseInt = 0;
        int notiWire_bonusStr = 0;
        int notiWire_bonusCon = 0;
        int notiWire_bonusInt = 0;
        int notiWire_elixirUseCount = 0;
        int notiWire_remainStats = 0;
        int notiWire_tempStr = 0;
        int notiWire_tempCon = 0;
        int notiWire_tempInt = 0;
        int notiWire_friendShipGuage = 0;
        int notiWire_friendShipMarble = 0;
        boolean notiWire_minusExpPenalty = false;
        int notiWire_ac = 0;
        int notiWire_mr = 0;
        double notiWire_movedelayReduce = 0;
        double notiWire_attackdelayReduce = 0;
        int notiWire_pvpDmgRatio = 0;
        boolean notiWire_isComboTime = false;
        long notiWire_bit = 0;


        MJCompanionClassInfo cInfo = companionInstance.get_class_info();
        { notiWire_bit |= 1L; notiWire_objId = (companionInstance.getId()); }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_NAME) == MJCompanionUpdateFlag.UPDATE_NAME) {
            try {
                { notiWire_bit |= 2L; notiWire_name = (companionInstance.getName().getBytes("MS949")); }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_CLASS_ID) == MJCompanionUpdateFlag.UPDATE_CLASS_ID)
            { notiWire_bit |= 4L; notiWire_classId = (cInfo.get_class_id()); }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_EXP) == MJCompanionUpdateFlag.UPDATE_EXP) {
            { notiWire_bit |= 8L; notiWire_level = (companionInstance.getLevel()); }
            { notiWire_bit |= 16L; notiWire_exp = (companionInstance.getExp()); }
            { notiWire_bit |= 2097152L; notiWire_minusExpPenalty = false; }
        }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_HP) == MJCompanionUpdateFlag.UPDATE_HP) {
            { notiWire_bit |= 32L; notiWire_baseMaxHp = (companionInstance.get_base_max_hp()); }
            { notiWire_bit |= 64L; notiWire_tempMaxHp = (companionInstance.get_temp_max_hp()); }
            { notiWire_bit |= 128L; notiWire_hp = (companionInstance.getCurrentHp()); }
        }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_STATS) == MJCompanionUpdateFlag.UPDATE_STATS) {
            Ability ability = companionInstance.getAbility();
            { notiWire_bit |= 256L; notiWire_baseStr = (ability.getStr()); }
            { notiWire_bit |= 512L; notiWire_baseCon = (ability.getCon()); }
            { notiWire_bit |= 1024L; notiWire_baseInt = (ability.getInt()); }
            { notiWire_bit |= 2048L; notiWire_bonusStr = (ability.getAddedStr()); }
            { notiWire_bit |= 4096L; notiWire_bonusCon = (ability.getAddedCon()); }
            { notiWire_bit |= 8192L; notiWire_bonusInt = (ability.getAddedInt()); }
            { notiWire_bit |= 16384L; notiWire_elixirUseCount = (companionInstance.get_elixir_use_count()); }
            { notiWire_bit |= 32768L; notiWire_remainStats = (companionInstance.get_remain_stats()); }
            { notiWire_bit |= 65536L; notiWire_tempStr = 0; }
            { notiWire_bit |= 131072L; notiWire_tempCon = 0; }
            { notiWire_bit |= 262144L; notiWire_tempInt = 0; }
        }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_FRIEND_SHIP) == MJCompanionUpdateFlag.UPDATE_FRIEND_SHIP) {
            { notiWire_bit |= 524288L; notiWire_friendShipGuage = (companionInstance.get_friend_ship_guage()); }
            { notiWire_bit |= 1048576L; notiWire_friendShipMarble = (companionInstance.get_friend_ship_marble()); }
        }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_AC) == MJCompanionUpdateFlag.UPDATE_AC) {
            { notiWire_bit |= 536870912L; notiWire_ac = (companionInstance.getAC().getAc()); }
        }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_MR) == MJCompanionUpdateFlag.UPDATE_MR) {
            { notiWire_bit |= 1073741824L; notiWire_mr = (companionInstance.getResistance().getEffectedMrBySkill()); }
        }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_DELAY_REDUCE) == MJCompanionUpdateFlag.UPDATE_DELAY_REDUCE) {
            { notiWire_bit |= 4294967296L; notiWire_attackdelayReduce = (companionInstance.get_attackdelay_reduce()); }
            { notiWire_bit |= 2147483648L; notiWire_movedelayReduce = (companionInstance.get_movedelay_reduce()); }
        }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_PVP_DAMAGE) == MJCompanionUpdateFlag.UPDATE_PVP_DAMAGE) {
            { notiWire_bit |= 8589934592L; notiWire_pvpDmgRatio = (companionInstance.get_pvp_dmg_ratio()); }
        }
        if ((updateFlag & MJCompanionUpdateFlag.UPDATE_COMBO_TIME) == MJCompanionUpdateFlag.UPDATE_COMBO_TIME) {
            { notiWire_bit |= 17179869184L; notiWire_isComboTime = (companionInstance.get_is_combo_time()); }
        }
         SC_COMPANION_STATUS_NOTI.sendFields(
                 master, notiWire_objId, notiWire_name, notiWire_classId, notiWire_level, notiWire_exp, notiWire_baseMaxHp,
                 notiWire_tempMaxHp, notiWire_hp, notiWire_baseStr, notiWire_baseCon, notiWire_baseInt, notiWire_bonusStr,
                 notiWire_bonusCon, notiWire_bonusInt, notiWire_elixirUseCount, notiWire_remainStats, notiWire_tempStr,
                 notiWire_tempCon, notiWire_tempInt, notiWire_friendShipGuage, notiWire_friendShipMarble,
                 notiWire_minusExpPenalty, notiWire_ac, notiWire_mr, notiWire_movedelayReduce, notiWire_attackdelayReduce,
                 notiWire_pvpDmgRatio, notiWire_isComboTime, notiWire_bit);
    }

    // ── has_* 비트는 구 generated 레이아웃 그대로 계승 (와이어 존재 여부가 곧 비트) ──
    public int get_obj_id()                     { return _objId; }
    public void set_obj_id(int val)             { _bit |= 0x1L; _objId = val; }
    public boolean has_obj_id()                 { return (_bit & 0x1L) == 0x1L; }
    public byte[] get_name()                    { return _name; }
    public void set_name(byte[] val)            { _bit |= 0x2L; _name = val; }
    public boolean has_name()                   { return (_bit & 0x2L) == 0x2L; }
    public int get_class_id()                   { return _classId; }
    public void set_class_id(int val)           { _bit |= 0x4L; _classId = val; }
    public boolean has_class_id()               { return (_bit & 0x4L) == 0x4L; }
    public int get_level()                      { return _level; }
    public void set_level(int val)              { _bit |= 0x8L; _level = val; }
    public boolean has_level()                  { return (_bit & 0x8L) == 0x8L; }
    public int get_exp()                        { return _exp; }
    public void set_exp(int val)                { _bit |= 0x10L; _exp = val; }
    public boolean has_exp()                    { return (_bit & 0x10L) == 0x10L; }
    public int get_base_max_hp()                { return _baseMaxHp; }
    public void set_base_max_hp(int val)        { _bit |= 0x20L; _baseMaxHp = val; }
    public boolean has_base_max_hp()            { return (_bit & 0x20L) == 0x20L; }
    public int get_temp_max_hp()                { return _tempMaxHp; }
    public void set_temp_max_hp(int val)        { _bit |= 0x40L; _tempMaxHp = val; }
    public boolean has_temp_max_hp()            { return (_bit & 0x40L) == 0x40L; }
    public int get_hp()                         { return _hp; }
    public void set_hp(int val)                 { _bit |= 0x80L; _hp = val; }
    public boolean has_hp()                     { return (_bit & 0x80L) == 0x80L; }
    public int get_base_str()                   { return _baseStr; }
    public void set_base_str(int val)           { _bit |= 0x100L; _baseStr = val; }
    public boolean has_base_str()               { return (_bit & 0x100L) == 0x100L; }
    public int get_base_con()                   { return _baseCon; }
    public void set_base_con(int val)           { _bit |= 0x200L; _baseCon = val; }
    public boolean has_base_con()               { return (_bit & 0x200L) == 0x200L; }
    public int get_base_int()                   { return _baseInt; }
    public void set_base_int(int val)           { _bit |= 0x400L; _baseInt = val; }
    public boolean has_base_int()               { return (_bit & 0x400L) == 0x400L; }
    public int get_bonus_str()                  { return _bonusStr; }
    public void set_bonus_str(int val)          { _bit |= 0x800L; _bonusStr = val; }
    public boolean has_bonus_str()              { return (_bit & 0x800L) == 0x800L; }
    public int get_bonus_con()                  { return _bonusCon; }
    public void set_bonus_con(int val)          { _bit |= 0x1000L; _bonusCon = val; }
    public boolean has_bonus_con()              { return (_bit & 0x1000L) == 0x1000L; }
    public int get_bonus_int()                  { return _bonusInt; }
    public void set_bonus_int(int val)          { _bit |= 0x2000L; _bonusInt = val; }
    public boolean has_bonus_int()              { return (_bit & 0x2000L) == 0x2000L; }
    public int get_elixir_use_count()           { return _elixirUseCount; }
    public void set_elixir_use_count(int val)   { _bit |= 0x4000L; _elixirUseCount = val; }
    public boolean has_elixir_use_count()       { return (_bit & 0x4000L) == 0x4000L; }
    public int get_remain_stats()               { return _remainStats; }
    public void set_remain_stats(int val)       { _bit |= 0x8000L; _remainStats = val; }
    public boolean has_remain_stats()           { return (_bit & 0x8000L) == 0x8000L; }
    public int get_temp_str()                   { return _tempStr; }
    public void set_temp_str(int val)           { _bit |= 0x10000L; _tempStr = val; }
    public boolean has_temp_str()               { return (_bit & 0x10000L) == 0x10000L; }
    public int get_temp_con()                   { return _tempCon; }
    public void set_temp_con(int val)           { _bit |= 0x20000L; _tempCon = val; }
    public boolean has_temp_con()               { return (_bit & 0x20000L) == 0x20000L; }
    public int get_temp_int()                   { return _tempInt; }
    public void set_temp_int(int val)           { _bit |= 0x40000L; _tempInt = val; }
    public boolean has_temp_int()               { return (_bit & 0x40000L) == 0x40000L; }
    public int get_friend_ship_guage()          { return _friendShipGuage; }
    public void set_friend_ship_guage(int val)  { _bit |= 0x80000L; _friendShipGuage = val; }
    public boolean has_friend_ship_guage()      { return (_bit & 0x80000L) == 0x80000L; }
    public int get_friend_ship_marble()         { return _friendShipMarble; }
    public void set_friend_ship_marble(int val) { _bit |= 0x100000L; _friendShipMarble = val; }
    public boolean has_friend_ship_marble()     { return (_bit & 0x100000L) == 0x100000L; }
    public boolean get_minus_exp_penalty()      { return _minusExpPenalty; }
    public void set_minus_exp_penalty(boolean v){ _bit |= 0x200000L; _minusExpPenalty = v; }
    public boolean has_minus_exp_penalty()      { return (_bit & 0x200000L) == 0x200000L; }
    public int get_ac()                         { return _ac; }
    public void set_ac(int val)                 { _bit |= 0x20000000L; _ac = val; }
    public boolean has_ac()                     { return (_bit & 0x20000000L) == 0x20000000L; }
    public int get_mr()                         { return _mr; }
    public void set_mr(int val)                 { _bit |= 0x40000000L; _mr = val; }
    public boolean has_mr()                     { return (_bit & 0x40000000L) == 0x40000000L; }
    public double get_movedelay_reduce()        { return _movedelayReduce; }
    public void set_movedelay_reduce(double v)  { _bit |= 0x80000000L; _movedelayReduce = v; }
    public boolean has_movedelay_reduce()       { return (_bit & 0x80000000L) == 0x80000000L; }
    public double get_attackdelay_reduce()      { return _attackdelayReduce; }
    public void set_attackdelay_reduce(double v){ _bit |= 0x100000000L; _attackdelayReduce = v; }
    public boolean has_attackdelay_reduce()     { return (_bit & 0x100000000L) == 0x100000000L; }
    public int get_pvp_dmg_ratio()              { return _pvpDmgRatio; }
    public void set_pvp_dmg_ratio(int val)      { _bit |= 0x200000000L; _pvpDmgRatio = val; }
    public boolean has_pvp_dmg_ratio()          { return (_bit & 0x200000000L) == 0x200000000L; }
    public boolean get_is_combo_time()          { return _isComboTime; }
    public void set_is_combo_time(boolean val)  { _bit |= 0x400000000L; _isComboTime = val; }
    public boolean has_is_combo_time()          { return (_bit & 0x400000000L) == 0x400000000L; }

    @Override
    public int msgId() { return ProtoMsg.SC_COMPANION_STATUS_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        if (has_obj_id())                w.uint32(1, _objId);
        if (has_name())                  w.bytes(2, _name);
        if (has_class_id())              w.uint32(3, _classId);
        if (has_level())                 w.uint32(4, _level);
        if (has_exp())                   w.int32(5, _exp);
        if (has_base_max_hp())           w.uint32(6, _baseMaxHp);
        if (has_temp_max_hp())           w.uint32(7, _tempMaxHp);
        if (has_hp())                    w.uint32(8, _hp);
        if (has_base_str())              w.uint32(9, _baseStr);
        if (has_base_con())              w.uint32(10, _baseCon);
        if (has_base_int())              w.uint32(11, _baseInt);
        if (has_bonus_str())             w.uint32(12, _bonusStr);
        if (has_bonus_con())             w.uint32(13, _bonusCon);
        if (has_bonus_int())             w.uint32(14, _bonusInt);
        if (has_elixir_use_count())      w.uint32(15, _elixirUseCount);
        if (has_remain_stats())          w.uint32(16, _remainStats);
        if (has_temp_str())              w.uint32(17, _tempStr);
        if (has_temp_con())              w.uint32(18, _tempCon);
        if (has_temp_int())              w.uint32(19, _tempInt);
        if (has_friend_ship_guage())     w.int32(20, _friendShipGuage);
        if (has_friend_ship_marble())    w.int32(21, _friendShipMarble);
        if (has_minus_exp_penalty())     w.bool(22, _minusExpPenalty);
        // field 23~29 미사용 — 구 스키마 그대로
        if (has_ac())                    w.int32(30, _ac);
        if (has_mr())                    w.int32(31, _mr);
        if (has_movedelay_reduce())      w.doubleV(32, _movedelayReduce);
        if (has_attackdelay_reduce())    w.doubleV(33, _attackdelayReduce);
        if (has_pvp_dmg_ratio())         w.uint32(34, _pvpDmgRatio);
        if (has_is_combo_time())         w.bool(35, _isComboTime);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(
            ProtoWriter w, int _objId, byte[] _name, int _classId, int _level, int _exp, int _baseMaxHp, int _tempMaxHp,
            int _hp, int _baseStr, int _baseCon, int _baseInt, int _bonusStr, int _bonusCon, int _bonusInt,
            int _elixirUseCount, int _remainStats, int _tempStr, int _tempCon, int _tempInt, int _friendShipGuage,
            int _friendShipMarble, boolean _minusExpPenalty, int _ac, int _mr, double _movedelayReduce,
            double _attackdelayReduce, int _pvpDmgRatio, boolean _isComboTime, long _bit) {
        if (((_bit & 1L) == 1L)) w.uint32(1, _objId);
            if (((_bit & 2L) == 2L)) w.bytes(2, _name);
            if (((_bit & 4L) == 4L)) w.uint32(3, _classId);
            if (((_bit & 8L) == 8L)) w.uint32(4, _level);
            if (((_bit & 16L) == 16L)) w.int32(5, _exp);
            if (((_bit & 32L) == 32L)) w.uint32(6, _baseMaxHp);
            if (((_bit & 64L) == 64L)) w.uint32(7, _tempMaxHp);
            if (((_bit & 128L) == 128L)) w.uint32(8, _hp);
            if (((_bit & 256L) == 256L)) w.uint32(9, _baseStr);
            if (((_bit & 512L) == 512L)) w.uint32(10, _baseCon);
            if (((_bit & 1024L) == 1024L)) w.uint32(11, _baseInt);
            if (((_bit & 2048L) == 2048L)) w.uint32(12, _bonusStr);
            if (((_bit & 4096L) == 4096L)) w.uint32(13, _bonusCon);
            if (((_bit & 8192L) == 8192L)) w.uint32(14, _bonusInt);
            if (((_bit & 16384L) == 16384L)) w.uint32(15, _elixirUseCount);
            if (((_bit & 32768L) == 32768L)) w.uint32(16, _remainStats);
            if (((_bit & 65536L) == 65536L)) w.uint32(17, _tempStr);
            if (((_bit & 131072L) == 131072L)) w.uint32(18, _tempCon);
            if (((_bit & 262144L) == 262144L)) w.uint32(19, _tempInt);
            if (((_bit & 524288L) == 524288L)) w.int32(20, _friendShipGuage);
            if (((_bit & 1048576L) == 1048576L)) w.int32(21, _friendShipMarble);
            if (((_bit & 2097152L) == 2097152L)) w.bool(22, _minusExpPenalty);
            if (((_bit & 536870912L) == 536870912L)) w.int32(30, _ac);
            if (((_bit & 1073741824L) == 1073741824L)) w.int32(31, _mr);
            if (((_bit & 2147483648L) == 2147483648L)) w.doubleV(32, _movedelayReduce);
            if (((_bit & 4294967296L) == 4294967296L)) w.doubleV(33, _attackdelayReduce);
            if (((_bit & 8589934592L) == 8589934592L)) w.uint32(34, _pvpDmgRatio);
            if (((_bit & 17179869184L) == 17179869184L)) w.bool(35, _isComboTime);
    }

    public static boolean sendFields(
            L1PcInstance recipient, int _objId, byte[] _name, int _classId, int _level, int _exp, int _baseMaxHp,
            int _tempMaxHp, int _hp, int _baseStr, int _baseCon, int _baseInt, int _bonusStr, int _bonusCon, int _bonusInt,
            int _elixirUseCount, int _remainStats, int _tempStr, int _tempCon, int _tempInt, int _friendShipGuage,
            int _friendShipMarble, boolean _minusExpPenalty, int _ac, int _mr, double _movedelayReduce,
            double _attackdelayReduce, int _pvpDmgRatio, boolean _isComboTime, long _bit) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), _objId, _name, _classId, _level, _exp, _baseMaxHp,
                _tempMaxHp, _hp, _baseStr, _baseCon, _baseInt, _bonusStr, _bonusCon, _bonusInt, _elixirUseCount,
                _remainStats, _tempStr, _tempCon, _tempInt, _friendShipGuage, _friendShipMarble, _minusExpPenalty, _ac, _mr,
                _movedelayReduce, _attackdelayReduce, _pvpDmgRatio, _isComboTime, _bit);
    }

    public static boolean sendFields(
            CIOClient recipient, int _objId, byte[] _name, int _classId, int _level, int _exp, int _baseMaxHp,
            int _tempMaxHp, int _hp, int _baseStr, int _baseCon, int _baseInt, int _bonusStr, int _bonusCon, int _bonusInt,
            int _elixirUseCount, int _remainStats, int _tempStr, int _tempCon, int _tempInt, int _friendShipGuage,
            int _friendShipMarble, boolean _minusExpPenalty, int _ac, int _mr, double _movedelayReduce,
            double _attackdelayReduce, int _pvpDmgRatio, boolean _isComboTime, long _bit) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_COMPANION_STATUS_NOTI, Proto.capacityBound(1024L + (_name == null ? 0L : _name.length)));
        if (writer == null) return false;
        try {
            writeFields(
                    writer, _objId, _name, _classId, _level, _exp, _baseMaxHp, _tempMaxHp, _hp, _baseStr, _baseCon,
                    _baseInt, _bonusStr, _bonusCon, _bonusInt, _elixirUseCount, _remainStats, _tempStr, _tempCon, _tempInt,
                    _friendShipGuage, _friendShipMarble, _minusExpPenalty, _ac, _mr, _movedelayReduce, _attackdelayReduce,
                    _pvpDmgRatio, _isComboTime, _bit);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(
            int _objId, byte[] _name, int _classId, int _level, int _exp, int _baseMaxHp, int _tempMaxHp, int _hp,
            int _baseStr, int _baseCon, int _baseInt, int _bonusStr, int _bonusCon, int _bonusInt, int _elixirUseCount,
            int _remainStats, int _tempStr, int _tempCon, int _tempInt, int _friendShipGuage, int _friendShipMarble,
            boolean _minusExpPenalty, int _ac, int _mr, double _movedelayReduce, double _attackdelayReduce,
            int _pvpDmgRatio, boolean _isComboTime, long _bit) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(
                writer, _objId, _name, _classId, _level, _exp, _baseMaxHp, _tempMaxHp, _hp, _baseStr, _baseCon, _baseInt,
                _bonusStr, _bonusCon, _bonusInt, _elixirUseCount, _remainStats, _tempStr, _tempCon, _tempInt,
                _friendShipGuage, _friendShipMarble, _minusExpPenalty, _ac, _mr, _movedelayReduce, _attackdelayReduce,
                _pvpDmgRatio, _isComboTime, _bit);
        return writer.finishWithTrailer(ProtoMsg.SC_COMPANION_STATUS_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        int proto25_objId = 0;
        byte[] proto25_name = null;
        int proto25_classId = 0;
        int proto25_level = 0;
        int proto25_exp = 0;
        int proto25_baseMaxHp = 0;
        int proto25_tempMaxHp = 0;
        int proto25_hp = 0;
        int proto25_baseStr = 0;
        int proto25_baseCon = 0;
        int proto25_baseInt = 0;
        int proto25_bonusStr = 0;
        int proto25_bonusCon = 0;
        int proto25_bonusInt = 0;
        int proto25_elixirUseCount = 0;
        int proto25_remainStats = 0;
        int proto25_tempStr = 0;
        int proto25_tempCon = 0;
        int proto25_tempInt = 0;
        int proto25_friendShipGuage = 0;
        int proto25_friendShipMarble = 0;
        boolean proto25_minusExpPenalty = false;
        int proto25_ac = 0;
        int proto25_mr = 0;
        double proto25_movedelayReduce = 0;
        double proto25_attackdelayReduce = 0;
        int proto25_pvpDmgRatio = 0;
        boolean proto25_isComboTime = false;
        long proto25_bit = 0;


        return sendFields(
                protoRecipient, proto25_objId, proto25_name, proto25_classId, proto25_level, proto25_exp, proto25_baseMaxHp,
                proto25_tempMaxHp, proto25_hp, proto25_baseStr, proto25_baseCon, proto25_baseInt, proto25_bonusStr,
                proto25_bonusCon, proto25_bonusInt, proto25_elixirUseCount, proto25_remainStats, proto25_tempStr,
                proto25_tempCon, proto25_tempInt, proto25_friendShipGuage, proto25_friendShipMarble,
                proto25_minusExpPenalty, proto25_ac, proto25_mr, proto25_movedelayReduce, proto25_attackdelayReduce,
                proto25_pvpDmgRatio, proto25_isComboTime, proto25_bit);
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
