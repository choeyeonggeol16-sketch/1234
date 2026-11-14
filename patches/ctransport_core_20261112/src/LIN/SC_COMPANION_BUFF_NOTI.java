package LIN;

import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;



/**
 * SC_COMPANION_BUFF_NOTI (0x07D5) — 컴패니언 버프 목록 통지
 * wire: [1]=buff_list repeated message
 *  Buff: [1]=spell_id int32 (required), [2]=duration uint32 (required)
 */
public final class SC_COMPANION_BUFF_NOTI extends ProtoPacket {

    /** 서브메시지 Buff — required: spell_id + duration 둘 다 (구 isInitialized 계승) */
    public static final class Buff {
        private int _spellId;
        private int _duration;

        public static LIN.SC_COMPANION_BUFF_NOTI.Buff newInstance() {
            return new LIN.SC_COMPANION_BUFF_NOTI.Buff();
        }

        public int spell_id()           { return _spellId; }
        public void set_spell_id(int val)   { _bit |= 0x1; _spellId = val; }
        public boolean has_spell_id()       { return (_bit & 0x1) == 0x1; }
        public int duration()           { return _duration; }
        public void set_duration(int val)   { _bit |= 0x2; _duration = val; }
        public boolean has_duration()       { return (_bit & 0x2) == 0x2; }

        private int _bit;

        void body(ProtoWriter w) {
            if (has_spell_id())
                w.int32(1, _spellId);       // 구: wirteInt32(1, ...) — 오타 포함 int32
            if (has_duration())
                w.uint32(2, _duration);     // 구: writeUInt32(2, ...)
        }
    }

    private List<LIN.SC_COMPANION_BUFF_NOTI.Buff> _buffList;

    private SC_COMPANION_BUFF_NOTI() {}

    public static LIN.SC_COMPANION_BUFF_NOTI newInstance() {
        return new LIN.SC_COMPANION_BUFF_NOTI();
    }

    public void add_buff_list(LIN.SC_COMPANION_BUFF_NOTI.Buff val) {
        if (!has_buff_list())
            _buffList = new ArrayList<>();
        _buffList.add(val);
    }
    public boolean has_buff_list() {
        return _buffList != null;
    }
    public List<LIN.SC_COMPANION_BUFF_NOTI.Buff> buff_list() {
        return _buffList;
    }

    // ── 구 send 2종 — 시그니처 동일 유지 ──

    /** 단일 버프 추가 통지 */
    public static void send(L1PcInstance master, int spellId, int duration) {
        if (master == null) return;
        List<LIN.SC_COMPANION_BUFF_NOTI.Buff> notiWire_buffList = null;


        LIN.SC_COMPANION_BUFF_NOTI.Buff buff = LIN.SC_COMPANION_BUFF_NOTI.Buff.newInstance();
        buff.set_spell_id(spellId);
        buff.set_duration(duration);
        {
        LIN.SC_COMPANION_BUFF_NOTI.Buff proto909_arg0 = buff;

            if (!(notiWire_buffList != null)) notiWire_buffList = new ArrayList<>();
            notiWire_buffList.add(proto909_arg0);
        }
        LIN.SC_COMPANION_BUFF_NOTI.sendFields(master, notiWire_buffList);
    }

    /** 컴패니언 현재 버프 전체 통지 */
    public static void send(L1PcInstance master, L1CompanionInstance companionInstance) {
        if (master == null || companionInstance == null) return;
        List<LIN.SC_COMPANION_BUFF_NOTI.Buff> notiWire_buffList = null;


        for (int spellId : L1SkillId.COMPANION_BUFFS) {
            if (!companionInstance.hasSkillEffect(spellId))
                continue;

            LIN.SC_COMPANION_BUFF_NOTI.Buff buff = LIN.SC_COMPANION_BUFF_NOTI.Buff.newInstance();
            buff.set_spell_id(spellId);
            buff.set_duration(companionInstance.getSkillEffectTimeSec(spellId));
            {
            LIN.SC_COMPANION_BUFF_NOTI.Buff proto910_arg0 = buff;

                if (!(notiWire_buffList != null)) notiWire_buffList = new ArrayList<>();
                notiWire_buffList.add(proto910_arg0);
            }
        }
        LIN.SC_COMPANION_BUFF_NOTI.sendFields(master, notiWire_buffList);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_COMPANION_BUFF_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        if (has_buff_list()) {
            for (LIN.SC_COMPANION_BUFF_NOTI.Buff val : _buffList) {
                w.msg(1);                   // 구: writeMessage(1, val)
                val.body(w);
                w.end();
            }
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, List<LIN.SC_COMPANION_BUFF_NOTI.Buff> _buffList) {
        if ((_buffList != null)) {
                for (LIN.SC_COMPANION_BUFF_NOTI.Buff val : _buffList) {
                    w.msg(1);
                    val.body(w);
                    w.end();
                }
            }
    }

    public static boolean sendFields(L1PcInstance recipient, List<LIN.SC_COMPANION_BUFF_NOTI.Buff> _buffList) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _buffList);
    }

    public static boolean sendFields(CIOClient recipient, List<LIN.SC_COMPANION_BUFF_NOTI.Buff> _buffList) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_COMPANION_BUFF_NOTI, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _buffList);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(List<LIN.SC_COMPANION_BUFF_NOTI.Buff> _buffList) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _buffList);
        return writer.finishWithTrailer(ProtoMsg.SC_COMPANION_BUFF_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        List<LIN.SC_COMPANION_BUFF_NOTI.Buff> proto20_buffList = null;


        return sendFields(protoRecipient, proto20_buffList);
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
