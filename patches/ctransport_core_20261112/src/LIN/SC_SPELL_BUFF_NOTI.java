package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_SPELL_BUFF_NOTI (0x006E) — 스펠/버프 아이콘 통지 (그랑카인 피로도 등)
 * wire:
 *  [1] noti_type enum, [2] spell_id uint32, [3] duration uint32,
 *  [4] duration_show_type enum, [5] on_icon_id uint32, [6] off_icon_id uint32,
 *  [7] icon_priority uint32, [8] tooltip_str_id uint32, [9] new_str_id uint32,
 *  [10] end_str_id uint32, [11] is_good bool, [12] overlap_buff_icon uint32,
 *  [13] main_tooltip_str_id uint32, [14] buff_icon_priority uint32
 *
 * mutable 패킷 — 아이콘 조합이 다양해서 빌더 대신 setter 조립.
 * has_ 비트 세팅된 필드만 와이어로 나감 (구 generated 동일).
 */
public final class SC_SPELL_BUFF_NOTI extends ProtoPacket {

    /**  [아이콘ID, 툴팁ID] 매핑 (구 ANGER_STAT_MAPPER 1:1 계승) */
    private static final int[][] ANGER_STAT_MAPPER = new int[][]{
        {0, 0},
        {6432, 3932},
        {6433, 3933},
        {6434, 3934},
        {6435, 3935},
        {6436, 3936},
        {6437, 3937},
    };

    // ── 구 static 헬퍼 2종 — 시그니처 동일 유지 ──

    /** 피로도 패널티 종료 (아이콘 OFF) */
    public static void sendFatigueOff(L1PcInstance pc, int penaltyLevel) {
        if (penaltyLevel <= 0 || penaltyLevel >= ANGER_STAT_MAPPER.length)
            return;

        int iconId = ANGER_STAT_MAPPER[penaltyLevel][0];
        LIN.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
        int notiWire_spellId = 0;
        int notiWire_onIconId = 0;
        int notiWire_offIconId = 0;
        int notiWire_bit = 0;


        { notiWire_bit |= 1; notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.END; }
        { notiWire_bit |= 2; notiWire_spellId = iconId; }
        { notiWire_bit |= 32; notiWire_offIconId = 0; }
        { notiWire_bit |= 16; notiWire_onIconId = 0; }
        LIN.SC_SPELL_BUFF_NOTI.sendFields(
                pc, notiWire_notiType, notiWire_spellId, 0, null, notiWire_onIconId, notiWire_offIconId, 0, 0, 0, 0, false,
                0, 0, 0, notiWire_bit);
    }

    /** 피로도 패널티 시작 (아이콘 ON + 지속시간) */
    public static void sendFatigueOn(L1PcInstance pc, int penaltyLevel, int time) {
        if (penaltyLevel <= 0 || penaltyLevel >= ANGER_STAT_MAPPER.length)
            return;

        int iconId = ANGER_STAT_MAPPER[penaltyLevel][0];
        int tooltipId = ANGER_STAT_MAPPER[penaltyLevel][1];
        LIN.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
        int notiWire_spellId = 0;
        int notiWire_duration = 0;
        LIN.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
        int notiWire_onIconId = 0;
        int notiWire_offIconId = 0;
        int notiWire_iconPriority = 0;
        int notiWire_tooltipStrId = 0;
        int notiWire_newStrId = 0;
        int notiWire_endStrId = 0;
        boolean notiWire_isGood = false;
        int notiWire_bit = 0;


        { notiWire_bit |= 1; notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.RESTAT; }
        { notiWire_bit |= 2; notiWire_spellId = iconId; }
        { notiWire_bit |= 4; notiWire_duration = (time * 60); }
        { notiWire_bit |= 8; notiWire_durationShowType = LIN.SC_SPELL_BUFF_NOTI.eDurationShowType.TYPE_EFF_UNLIMIT; }
        { notiWire_bit |= 16; notiWire_onIconId = iconId; }
        { notiWire_bit |= 32; notiWire_offIconId = iconId; }
        { notiWire_bit |= 64; notiWire_iconPriority = 1; }
        { notiWire_bit |= 128; notiWire_tooltipStrId = tooltipId; }
        { notiWire_bit |= 256; notiWire_newStrId = 3215; }
        { notiWire_bit |= 512; notiWire_endStrId = 2238; }
        { notiWire_bit |= 1024; notiWire_isGood = true; }
        LIN.SC_SPELL_BUFF_NOTI.sendFields(
                pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId,
                notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId,
                notiWire_isGood, 0, 0, 0, notiWire_bit);
    }

    // ── 패킷 본체 ──

    public static LIN.SC_SPELL_BUFF_NOTI newInstance() {
        return new LIN.SC_SPELL_BUFF_NOTI();
    }

    private LIN.SC_SPELL_BUFF_NOTI.eNotiType _notiType;
    private int _spellId;
    private int _duration;
    private LIN.SC_SPELL_BUFF_NOTI.eDurationShowType _durationShowType;
    private int _onIconId;
    private int _offIconId;
    private int _iconPriority;
    private int _tooltipStrId;
    private int _newStrId;
    private int _endStrId;
    private boolean _isGood;
    private int _overlapBuffIcon;
    private int _mainTooltipStrId;
    private int _buffIconPriority;
    private int _bit;

    private SC_SPELL_BUFF_NOTI() {}

    public LIN.SC_SPELL_BUFF_NOTI.eNotiType noti_type()                    { return _notiType; }
    public void set_noti_type(LIN.SC_SPELL_BUFF_NOTI.eNotiType val)            { _bit |= 0x1; _notiType = val; }
    public boolean has_noti_type()                      { return (_bit & 0x1) == 0x1; }
    public int spell_id()                           { return _spellId; }
    public void set_spell_id(int val)                   { _bit |= 0x2; _spellId = val; }
    public boolean has_spell_id()                       { return (_bit & 0x2) == 0x2; }
    public int duration()                           { return _duration; }
    public void set_duration(int val)                   { _bit |= 0x4; _duration = val; }
    public boolean has_duration()                       { return (_bit & 0x4) == 0x4; }
    public LIN.SC_SPELL_BUFF_NOTI.eDurationShowType duration_show_type()   { return _durationShowType; }
    public void set_duration_show_type(LIN.SC_SPELL_BUFF_NOTI.eDurationShowType val) { _bit |= 0x8; _durationShowType = val; }
    public boolean has_duration_show_type()             { return (_bit & 0x8) == 0x8; }
    public int on_icon_id()                         { return _onIconId; }
    public void set_on_icon_id(int val)                 { _bit |= 0x10; _onIconId = val; }
    public boolean has_on_icon_id()                     { return (_bit & 0x10) == 0x10; }
    public int off_icon_id()                        { return _offIconId; }
    public void set_off_icon_id(int val)                { _bit |= 0x20; _offIconId = val; }
    public boolean has_off_icon_id()                    { return (_bit & 0x20) == 0x20; }
    public int icon_priority()                      { return _iconPriority; }
    public void set_icon_priority(int val)              { _bit |= 0x40; _iconPriority = val; }
    public boolean has_icon_priority()                  { return (_bit & 0x40) == 0x40; }
    public int tooltip_str_id()                     { return _tooltipStrId; }
    public void set_tooltip_str_id(int val)             { _bit |= 0x80; _tooltipStrId = val; }
    public boolean has_tooltip_str_id()                 { return (_bit & 0x80) == 0x80; }
    public int new_str_id()                         { return _newStrId; }
    public void set_new_str_id(int val)                 { _bit |= 0x100; _newStrId = val; }
    public boolean has_new_str_id()                     { return (_bit & 0x100) == 0x100; }
    public int end_str_id()                         { return _endStrId; }
    public void set_end_str_id(int val)                 { _bit |= 0x200; _endStrId = val; }
    public boolean has_end_str_id()                     { return (_bit & 0x200) == 0x200; }
    public boolean is_good()                        { return _isGood; }
    public void set_is_good(boolean val)                { _bit |= 0x400; _isGood = val; }
    public boolean has_is_good()                        { return (_bit & 0x400) == 0x400; }
    public int overlap_buff_icon()                  { return _overlapBuffIcon; }
    public void set_overlap_buff_icon(int val)          { _bit |= 0x800; _overlapBuffIcon = val; }
    public boolean has_overlap_buff_icon()              { return (_bit & 0x800) == 0x800; }
    public int main_tooltip_str_id()                { return _mainTooltipStrId; }
    public void set_main_tooltip_str_id(int val)        { _bit |= 0x1000; _mainTooltipStrId = val; }
    public boolean has_main_tooltip_str_id()            { return (_bit & 0x1000) == 0x1000; }
    public int buff_icon_priority()                 { return _buffIconPriority; }
    public void set_buff_icon_priority(int val)         { _bit |= 0x2000; _buffIconPriority = val; }
    public boolean has_buff_icon_priority()             { return (_bit & 0x2000) == 0x2000; }

    @Override
    public int msgId() { return ProtoMsg.SC_SPELL_BUFF_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        if (has_noti_type() && _notiType != null)
            w.enumV(1, _notiType.toInt());
        if (has_spell_id())
            w.uint32(2, _spellId);
        if (has_duration())
            w.uint32(3, _duration);
        if (has_duration_show_type() && _durationShowType != null)
            w.enumV(4, _durationShowType.toInt());
        if (has_on_icon_id())
            w.uint32(5, _onIconId);
        if (has_off_icon_id())
            w.uint32(6, _offIconId);
        if (has_icon_priority())
            w.uint32(7, _iconPriority);
        if (has_tooltip_str_id())
            w.uint32(8, _tooltipStrId);
        if (has_new_str_id())
            w.uint32(9, _newStrId);
        if (has_end_str_id())
            w.uint32(10, _endStrId);
        if (has_is_good())
            w.bool(11, _isGood);
        if (has_overlap_buff_icon())
            w.uint32(12, _overlapBuffIcon);
        if (has_main_tooltip_str_id())
            w.uint32(13, _mainTooltipStrId);
        if (has_buff_icon_priority())
            w.uint32(14, _buffIconPriority);
    }

    // ── enums — 구와 1:1 (와이어 계약) ──
    public enum eNotiType {
        NEW(1),
        RESTAT(2),
        END(3);
        private final int value;
        eNotiType(int val) { value = val; }
        public int toInt() { return value; }
        public static LIN.SC_SPELL_BUFF_NOTI.eNotiType fromInt(int i) {
            switch (i) {
            case 1: return NEW;
            case 2: return RESTAT;
            case 3: return END;
            default: return null;
            }
        }
    }

    public enum eDurationShowType {
        TYPE_EFF_NONE(0),
        TYPE_EFF_PERCENT(1),
        TYPE_EFF_MINUTE(2),
        TYPE_EFF_PERCENT_ORC_SERVER(3),
        TYPE_EFF_EINHASAD_COOLTIME_MINUTE(4),
        TYPE_EFF_LEGACY_TIME(5),
        TYPE_EFF_VARIABLE_VALUE(6),
        TYPE_EFF_DAY_HOUR_MIN(7),
        TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC(8),
        TYPE_EFF_NSERVICE_TOPPING(9),
        TYPE_EFF_UNLIMIT(10),
        TYPE_EFF_CUSTOM(11),
        TYPE_EFF_COUNT(12);
        private final int value;
        eDurationShowType(int val) { value = val; }
        public int toInt() { return value; }
        public static LIN.SC_SPELL_BUFF_NOTI.eDurationShowType fromInt(int i) {
            switch (i) {
            case 0: return TYPE_EFF_NONE;
            case 1: return TYPE_EFF_PERCENT;
            case 2: return TYPE_EFF_MINUTE;
            case 3: return TYPE_EFF_PERCENT_ORC_SERVER;
            case 4: return TYPE_EFF_EINHASAD_COOLTIME_MINUTE;
            case 5: return TYPE_EFF_LEGACY_TIME;
            case 6: return TYPE_EFF_VARIABLE_VALUE;
            case 7: return TYPE_EFF_DAY_HOUR_MIN;
            case 8: return TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC;
            case 9: return TYPE_EFF_NSERVICE_TOPPING;
            case 10: return TYPE_EFF_UNLIMIT;
            case 11: return TYPE_EFF_CUSTOM;
            case 12: return TYPE_EFF_COUNT;
            default: return null;
            }
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(
            ProtoWriter w, LIN.SC_SPELL_BUFF_NOTI.eNotiType _notiType, int _spellId, int _duration, LIN.SC_SPELL_BUFF_NOTI.eDurationShowType _durationShowType,
            int _onIconId, int _offIconId, int _iconPriority, int _tooltipStrId, int _newStrId, int _endStrId,
            boolean _isGood, int _overlapBuffIcon, int _mainTooltipStrId, int _buffIconPriority, int _bit) {
        if (((_bit & 1) == 1) && _notiType != null) w.enumV(1, _notiType.toInt());
            if (((_bit & 2) == 2)) w.uint32(2, _spellId);
            if (((_bit & 4) == 4)) w.uint32(3, _duration);
            if (((_bit & 8) == 8) && _durationShowType != null) w.enumV(4, _durationShowType.toInt());
            if (((_bit & 16) == 16)) w.uint32(5, _onIconId);
            if (((_bit & 32) == 32)) w.uint32(6, _offIconId);
            if (((_bit & 64) == 64)) w.uint32(7, _iconPriority);
            if (((_bit & 128) == 128)) w.uint32(8, _tooltipStrId);
            if (((_bit & 256) == 256)) w.uint32(9, _newStrId);
            if (((_bit & 512) == 512)) w.uint32(10, _endStrId);
            if (((_bit & 1024) == 1024)) w.bool(11, _isGood);
            if (((_bit & 2048) == 2048)) w.uint32(12, _overlapBuffIcon);
            if (((_bit & 4096) == 4096)) w.uint32(13, _mainTooltipStrId);
            if (((_bit & 8192) == 8192)) w.uint32(14, _buffIconPriority);
    }

    public static boolean sendFields(
            L1PcInstance recipient, LIN.SC_SPELL_BUFF_NOTI.eNotiType _notiType, int _spellId, int _duration, LIN.SC_SPELL_BUFF_NOTI.eDurationShowType _durationShowType,
            int _onIconId, int _offIconId, int _iconPriority, int _tooltipStrId, int _newStrId, int _endStrId,
            boolean _isGood, int _overlapBuffIcon, int _mainTooltipStrId, int _buffIconPriority, int _bit) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), _notiType, _spellId, _duration, _durationShowType,
                _onIconId, _offIconId, _iconPriority, _tooltipStrId, _newStrId, _endStrId, _isGood, _overlapBuffIcon,
                _mainTooltipStrId, _buffIconPriority, _bit);
    }

    public static boolean sendFields(
            CIOClient recipient, LIN.SC_SPELL_BUFF_NOTI.eNotiType _notiType, int _spellId, int _duration, LIN.SC_SPELL_BUFF_NOTI.eDurationShowType _durationShowType,
            int _onIconId, int _offIconId, int _iconPriority, int _tooltipStrId, int _newStrId, int _endStrId,
            boolean _isGood, int _overlapBuffIcon, int _mainTooltipStrId, int _buffIconPriority, int _bit) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_SPELL_BUFF_NOTI, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(
                    writer, _notiType, _spellId, _duration, _durationShowType, _onIconId, _offIconId, _iconPriority,
                    _tooltipStrId, _newStrId, _endStrId, _isGood, _overlapBuffIcon, _mainTooltipStrId, _buffIconPriority,
                    _bit);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(
            LIN.SC_SPELL_BUFF_NOTI.eNotiType _notiType, int _spellId, int _duration, LIN.SC_SPELL_BUFF_NOTI.eDurationShowType _durationShowType, int _onIconId,
            int _offIconId, int _iconPriority, int _tooltipStrId, int _newStrId, int _endStrId, boolean _isGood,
            int _overlapBuffIcon, int _mainTooltipStrId, int _buffIconPriority, int _bit) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(
                writer, _notiType, _spellId, _duration, _durationShowType, _onIconId, _offIconId, _iconPriority,
                _tooltipStrId, _newStrId, _endStrId, _isGood, _overlapBuffIcon, _mainTooltipStrId, _buffIconPriority, _bit);
        return writer.finishWithTrailer(ProtoMsg.SC_SPELL_BUFF_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        LIN.SC_SPELL_BUFF_NOTI.eNotiType proto64_notiType = null;
        int proto64_spellId = 0;
        int proto64_duration = 0;
        LIN.SC_SPELL_BUFF_NOTI.eDurationShowType proto64_durationShowType = null;
        int proto64_onIconId = 0;
        int proto64_offIconId = 0;
        int proto64_iconPriority = 0;
        int proto64_tooltipStrId = 0;
        int proto64_newStrId = 0;
        int proto64_endStrId = 0;
        boolean proto64_isGood = false;
        int proto64_overlapBuffIcon = 0;
        int proto64_mainTooltipStrId = 0;
        int proto64_buffIconPriority = 0;
        int proto64_bit = 0;


        return sendFields(
                protoRecipient, proto64_notiType, proto64_spellId, proto64_duration, proto64_durationShowType,
                proto64_onIconId, proto64_offIconId, proto64_iconPriority, proto64_tooltipStrId, proto64_newStrId,
                proto64_endStrId, proto64_isGood, proto64_overlapBuffIcon, proto64_mainTooltipStrId,
                proto64_buffIconPriority, proto64_bit);
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
