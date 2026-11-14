package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_COMPANION_COMBAT_DATA_NOTI (0x07DA) — 컴패니언 전투 데이터 통지 (부분 갱신 지원)
 * wire:
 *  [1] obj_id uint32 (required)
 *  [2] melee_hit int32, [3] melee_cri_hit double, [4] melee_cri_server_hit double,
 *  [5] melee_dice_dmg ~ [10] melee_last_dmg int32,
 *  [11] spell_id int32, [12] spell_hit int32, [13] spell_server_hit int32,
 *  [14] spell_cri_hit double, [15] spell_cri_server_hit double,
 *  [16] spell_dice_dmg ~ [21] spell_last_dmg int32,
 *  [22] total_dmg int32
 *
 * ⚠ has_ 비트 세팅된 필드만 와이어로 나감 — 부분 갱신 계약 (구 generated 동일)
 */
public final class SC_COMPANION_COMBAT_DATA_NOTI extends ProtoPacket {

    private int _objId;
    private int _meleeHit;
    private double _meleeCriHit;
    private double _meleeCriServerHit;
    private int _meleeDiceDmg;
    private int _meleeStatDmg;
    private int _meleeWildDmg;
    private int _meleeBuffDmg;
    private int _meleeElementalDmg;
    private int _meleeLastDmg;
    private int _spellId;
    private int _spellHit;
    private int _spellServerHit;
    private double _spellCriHit;
    private double _spellCriServerHit;
    private int _spellDiceDmg;
    private int _spellStatDmg;
    private int _spellWildDmg;
    private int _spellBuffDmg;
    private int _spellDmgMulti;
    private int _spellLastDmg;
    private int _totalDmg;

    private int _bit;   // 22필드 = int 비트로 충분 (구와 1:1 레이아웃)

    private SC_COMPANION_COMBAT_DATA_NOTI() {}

    public static SC_COMPANION_COMBAT_DATA_NOTI newInstance() {
        return new SC_COMPANION_COMBAT_DATA_NOTI();
    }

    public int get_obj_id()                         { return _objId; }
    public void set_obj_id(int val)                 { _bit |= 0x1; _objId = val; }
    public boolean has_obj_id()                     { return (_bit & 0x1) == 0x1; }
    public int get_melee_hit()                      { return _meleeHit; }
    public void set_melee_hit(int val)              { _bit |= 0x2; _meleeHit = val; }
    public boolean has_melee_hit()                  { return (_bit & 0x2) == 0x2; }
    public double get_melee_cri_hit()               { return _meleeCriHit; }
    public void set_melee_cri_hit(double val)       { _bit |= 0x4; _meleeCriHit = val; }
    public boolean has_melee_cri_hit()              { return (_bit & 0x4) == 0x4; }
    public double get_melee_cri_server_hit()        { return _meleeCriServerHit; }
    public void set_melee_cri_server_hit(double v)  { _bit |= 0x8; _meleeCriServerHit = v; }
    public boolean has_melee_cri_server_hit()       { return (_bit & 0x8) == 0x8; }
    public int get_melee_dice_dmg()                 { return _meleeDiceDmg; }
    public void set_melee_dice_dmg(int val)         { _bit |= 0x10; _meleeDiceDmg = val; }
    public boolean has_melee_dice_dmg()             { return (_bit & 0x10) == 0x10; }
    public int get_melee_stat_dmg()                 { return _meleeStatDmg; }
    public void set_melee_stat_dmg(int val)         { _bit |= 0x20; _meleeStatDmg = val; }
    public boolean has_melee_stat_dmg()             { return (_bit & 0x20) == 0x20; }
    public int get_melee_wild_dmg()                 { return _meleeWildDmg; }
    public void set_melee_wild_dmg(int val)         { _bit |= 0x40; _meleeWildDmg = val; }
    public boolean has_melee_wild_dmg()             { return (_bit & 0x40) == 0x40; }
    public int get_melee_buff_dmg()                 { return _meleeBuffDmg; }
    public void set_melee_buff_dmg(int val)         { _bit |= 0x80; _meleeBuffDmg = val; }
    public boolean has_melee_buff_dmg()             { return (_bit & 0x80) == 0x80; }
    public int get_melee_elemental_dmg()            { return _meleeElementalDmg; }
    public void set_melee_elemental_dmg(int val)    { _bit |= 0x100; _meleeElementalDmg = val; }
    public boolean has_melee_elemental_dmg()        { return (_bit & 0x100) == 0x100; }
    public int get_melee_last_dmg()                 { return _meleeLastDmg; }
    public void set_melee_last_dmg(int val)         { _bit |= 0x200; _meleeLastDmg = val; }
    public boolean has_melee_last_dmg()             { return (_bit & 0x200) == 0x200; }
    public int get_spell_id()                       { return _spellId; }
    public void set_spell_id(int val)               { _bit |= 0x400; _spellId = val; }
    public boolean has_spell_id()                   { return (_bit & 0x400) == 0x400; }
    public int get_spell_hit()                      { return _spellHit; }
    public void set_spell_hit(int val)              { _bit |= 0x800; _spellHit = val; }
    public boolean has_spell_hit()                  { return (_bit & 0x800) == 0x800; }
    public int get_spell_server_hit()               { return _spellServerHit; }
    public void set_spell_server_hit(int val)       { _bit |= 0x1000; _spellServerHit = val; }
    public boolean has_spell_server_hit()           { return (_bit & 0x1000) == 0x1000; }
    public double get_spell_cri_hit()               { return _spellCriHit; }
    public void set_spell_cri_hit(double val)       { _bit |= 0x2000; _spellCriHit = val; }
    public boolean has_spell_cri_hit()              { return (_bit & 0x2000) == 0x2000; }
    public double get_spell_cri_server_hit()        { return _spellCriServerHit; }
    public void set_spell_cri_server_hit(double v)  { _bit |= 0x4000; _spellCriServerHit = v; }
    public boolean has_spell_cri_server_hit()       { return (_bit & 0x4000) == 0x4000; }
    public int get_spell_dice_dmg()                 { return _spellDiceDmg; }
    public void set_spell_dice_dmg(int val)         { _bit |= 0x8000; _spellDiceDmg = val; }
    public boolean has_spell_dice_dmg()             { return (_bit & 0x8000) == 0x8000; }
    public int get_spell_stat_dmg()                 { return _spellStatDmg; }
    public void set_spell_stat_dmg(int val)         { _bit |= 0x10000; _spellStatDmg = val; }
    public boolean has_spell_stat_dmg()             { return (_bit & 0x10000) == 0x10000; }
    public int get_spell_wild_dmg()                 { return _spellWildDmg; }
    public void set_spell_wild_dmg(int val)         { _bit |= 0x20000; _spellWildDmg = val; }
    public boolean has_spell_wild_dmg()             { return (_bit & 0x20000) == 0x20000; }
    public int get_spell_buff_dmg()                 { return _spellBuffDmg; }
    public void set_spell_buff_dmg(int val)         { _bit |= 0x40000; _spellBuffDmg = val; }
    public boolean has_spell_buff_dmg()             { return (_bit & 0x40000) == 0x40000; }
    public int get_spell_dmg_multi()                { return _spellDmgMulti; }
    public void set_spell_dmg_multi(int val)        { _bit |= 0x80000; _spellDmgMulti = val; }
    public boolean has_spell_dmg_multi()            { return (_bit & 0x80000) == 0x80000; }
    public int get_spell_last_dmg()                 { return _spellLastDmg; }
    public void set_spell_last_dmg(int val)         { _bit |= 0x100000; _spellLastDmg = val; }
    public boolean has_spell_last_dmg()             { return (_bit & 0x100000) == 0x100000; }
    public int get_total_dmg()                      { return _totalDmg; }
    public void set_total_dmg(int val)              { _bit |= 0x200000; _totalDmg = val; }
    public boolean has_total_dmg()                  { return (_bit & 0x200000) == 0x200000; }

    @Override
    public int msgId() { return ProtoMsg.SC_COMPANION_COMBAT_DATA_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        if (has_obj_id())               w.uint32(1, _objId);
        if (has_melee_hit())            w.int32(2, _meleeHit);
        if (has_melee_cri_hit())        w.doubleV(3, _meleeCriHit);
        if (has_melee_cri_server_hit()) w.doubleV(4, _meleeCriServerHit);
        if (has_melee_dice_dmg())       w.int32(5, _meleeDiceDmg);
        if (has_melee_stat_dmg())       w.int32(6, _meleeStatDmg);
        if (has_melee_wild_dmg())       w.int32(7, _meleeWildDmg);
        if (has_melee_buff_dmg())       w.int32(8, _meleeBuffDmg);
        if (has_melee_elemental_dmg())  w.int32(9, _meleeElementalDmg);
        if (has_melee_last_dmg())       w.int32(10, _meleeLastDmg);
        if (has_spell_id())             w.int32(11, _spellId);
        if (has_spell_hit())            w.int32(12, _spellHit);
        if (has_spell_server_hit())     w.int32(13, _spellServerHit);
        if (has_spell_cri_hit())        w.doubleV(14, _spellCriHit);
        if (has_spell_cri_server_hit()) w.doubleV(15, _spellCriServerHit);
        if (has_spell_dice_dmg())       w.int32(16, _spellDiceDmg);
        if (has_spell_stat_dmg())       w.int32(17, _spellStatDmg);
        if (has_spell_wild_dmg())       w.int32(18, _spellWildDmg);
        if (has_spell_buff_dmg())       w.int32(19, _spellBuffDmg);
        if (has_spell_dmg_multi())      w.int32(20, _spellDmgMulti);
        if (has_spell_last_dmg())       w.int32(21, _spellLastDmg);
        if (has_total_dmg())            w.int32(22, _totalDmg);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(
            ProtoWriter w, int _objId, int _meleeHit, double _meleeCriHit, double _meleeCriServerHit, int _meleeDiceDmg,
            int _meleeStatDmg, int _meleeWildDmg, int _meleeBuffDmg, int _meleeElementalDmg, int _meleeLastDmg,
            int _spellId, int _spellHit, int _spellServerHit, double _spellCriHit, double _spellCriServerHit,
            int _spellDiceDmg, int _spellStatDmg, int _spellWildDmg, int _spellBuffDmg, int _spellDmgMulti,
            int _spellLastDmg, int _totalDmg, int _bit) {
        if (((_bit & 1) == 1)) w.uint32(1, _objId);
            if (((_bit & 2) == 2)) w.int32(2, _meleeHit);
            if (((_bit & 4) == 4)) w.doubleV(3, _meleeCriHit);
            if (((_bit & 8) == 8)) w.doubleV(4, _meleeCriServerHit);
            if (((_bit & 16) == 16)) w.int32(5, _meleeDiceDmg);
            if (((_bit & 32) == 32)) w.int32(6, _meleeStatDmg);
            if (((_bit & 64) == 64)) w.int32(7, _meleeWildDmg);
            if (((_bit & 128) == 128)) w.int32(8, _meleeBuffDmg);
            if (((_bit & 256) == 256)) w.int32(9, _meleeElementalDmg);
            if (((_bit & 512) == 512)) w.int32(10, _meleeLastDmg);
            if (((_bit & 1024) == 1024)) w.int32(11, _spellId);
            if (((_bit & 2048) == 2048)) w.int32(12, _spellHit);
            if (((_bit & 4096) == 4096)) w.int32(13, _spellServerHit);
            if (((_bit & 8192) == 8192)) w.doubleV(14, _spellCriHit);
            if (((_bit & 16384) == 16384)) w.doubleV(15, _spellCriServerHit);
            if (((_bit & 32768) == 32768)) w.int32(16, _spellDiceDmg);
            if (((_bit & 65536) == 65536)) w.int32(17, _spellStatDmg);
            if (((_bit & 131072) == 131072)) w.int32(18, _spellWildDmg);
            if (((_bit & 262144) == 262144)) w.int32(19, _spellBuffDmg);
            if (((_bit & 524288) == 524288)) w.int32(20, _spellDmgMulti);
            if (((_bit & 1048576) == 1048576)) w.int32(21, _spellLastDmg);
            if (((_bit & 2097152) == 2097152)) w.int32(22, _totalDmg);
    }

    public static boolean sendFields(
            L1PcInstance recipient, int _objId, int _meleeHit, double _meleeCriHit, double _meleeCriServerHit,
            int _meleeDiceDmg, int _meleeStatDmg, int _meleeWildDmg, int _meleeBuffDmg, int _meleeElementalDmg,
            int _meleeLastDmg, int _spellId, int _spellHit, int _spellServerHit, double _spellCriHit,
            double _spellCriServerHit, int _spellDiceDmg, int _spellStatDmg, int _spellWildDmg, int _spellBuffDmg,
            int _spellDmgMulti, int _spellLastDmg, int _totalDmg, int _bit) {
        return sendFields(
                recipient == null ? null : recipient.getNetConnection(), _objId, _meleeHit, _meleeCriHit,
                _meleeCriServerHit, _meleeDiceDmg, _meleeStatDmg, _meleeWildDmg, _meleeBuffDmg, _meleeElementalDmg,
                _meleeLastDmg, _spellId, _spellHit, _spellServerHit, _spellCriHit, _spellCriServerHit, _spellDiceDmg,
                _spellStatDmg, _spellWildDmg, _spellBuffDmg, _spellDmgMulti, _spellLastDmg, _totalDmg, _bit);
    }

    public static boolean sendFields(
            CIOClient recipient, int _objId, int _meleeHit, double _meleeCriHit, double _meleeCriServerHit,
            int _meleeDiceDmg, int _meleeStatDmg, int _meleeWildDmg, int _meleeBuffDmg, int _meleeElementalDmg,
            int _meleeLastDmg, int _spellId, int _spellHit, int _spellServerHit, double _spellCriHit,
            double _spellCriServerHit, int _spellDiceDmg, int _spellStatDmg, int _spellWildDmg, int _spellBuffDmg,
            int _spellDmgMulti, int _spellLastDmg, int _totalDmg, int _bit) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_COMPANION_COMBAT_DATA_NOTI, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(
                    writer, _objId, _meleeHit, _meleeCriHit, _meleeCriServerHit, _meleeDiceDmg, _meleeStatDmg,
                    _meleeWildDmg, _meleeBuffDmg, _meleeElementalDmg, _meleeLastDmg, _spellId, _spellHit, _spellServerHit,
                    _spellCriHit, _spellCriServerHit, _spellDiceDmg, _spellStatDmg, _spellWildDmg, _spellBuffDmg,
                    _spellDmgMulti, _spellLastDmg, _totalDmg, _bit);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(
            int _objId, int _meleeHit, double _meleeCriHit, double _meleeCriServerHit, int _meleeDiceDmg, int _meleeStatDmg,
            int _meleeWildDmg, int _meleeBuffDmg, int _meleeElementalDmg, int _meleeLastDmg, int _spellId, int _spellHit,
            int _spellServerHit, double _spellCriHit, double _spellCriServerHit, int _spellDiceDmg, int _spellStatDmg,
            int _spellWildDmg, int _spellBuffDmg, int _spellDmgMulti, int _spellLastDmg, int _totalDmg, int _bit) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(
                writer, _objId, _meleeHit, _meleeCriHit, _meleeCriServerHit, _meleeDiceDmg, _meleeStatDmg, _meleeWildDmg,
                _meleeBuffDmg, _meleeElementalDmg, _meleeLastDmg, _spellId, _spellHit, _spellServerHit, _spellCriHit,
                _spellCriServerHit, _spellDiceDmg, _spellStatDmg, _spellWildDmg, _spellBuffDmg, _spellDmgMulti,
                _spellLastDmg, _totalDmg, _bit);
        return writer.finishWithTrailer(ProtoMsg.SC_COMPANION_COMBAT_DATA_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        int proto21_objId = 0;
        int proto21_meleeHit = 0;
        double proto21_meleeCriHit = 0;
        double proto21_meleeCriServerHit = 0;
        int proto21_meleeDiceDmg = 0;
        int proto21_meleeStatDmg = 0;
        int proto21_meleeWildDmg = 0;
        int proto21_meleeBuffDmg = 0;
        int proto21_meleeElementalDmg = 0;
        int proto21_meleeLastDmg = 0;
        int proto21_spellId = 0;
        int proto21_spellHit = 0;
        int proto21_spellServerHit = 0;
        double proto21_spellCriHit = 0;
        double proto21_spellCriServerHit = 0;
        int proto21_spellDiceDmg = 0;
        int proto21_spellStatDmg = 0;
        int proto21_spellWildDmg = 0;
        int proto21_spellBuffDmg = 0;
        int proto21_spellDmgMulti = 0;
        int proto21_spellLastDmg = 0;
        int proto21_totalDmg = 0;
        int proto21_bit = 0;


        return sendFields(
                protoRecipient, proto21_objId, proto21_meleeHit, proto21_meleeCriHit, proto21_meleeCriServerHit,
                proto21_meleeDiceDmg, proto21_meleeStatDmg, proto21_meleeWildDmg, proto21_meleeBuffDmg,
                proto21_meleeElementalDmg, proto21_meleeLastDmg, proto21_spellId, proto21_spellHit, proto21_spellServerHit,
                proto21_spellCriHit, proto21_spellCriServerHit, proto21_spellDiceDmg, proto21_spellStatDmg,
                proto21_spellWildDmg, proto21_spellBuffDmg, proto21_spellDmgMulti, proto21_spellLastDmg, proto21_totalDmg,
                proto21_bit);
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
