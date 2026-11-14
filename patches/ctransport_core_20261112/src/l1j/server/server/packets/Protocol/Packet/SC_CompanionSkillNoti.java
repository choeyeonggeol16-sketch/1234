package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import java.util.ArrayList;
import java.util.List;

import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_COMPANION_SKILL_NOTI (0x07D6) — 컴패니언 스킬 목록 통지
 * wire: [1]=skills repeated message
 *  Skill: [1]=id uint32 (required), [2]=enchant uint32 (required)
 */
public final class SC_CompanionSkillNoti extends ProtoPacket {

    /** 서브메시지 Skill — required: id + enchant (구 isInitialized 계승) */
    public static final class Skill {
        private int _id;
        private int _enchant;
        private int _bit;

        public static Skill newInstance() {
            return new Skill();
        }

        public int get_id()             { return _id; }
        public void set_id(int val)     { _bit |= 0x1; _id = val; }
        public boolean has_id()         { return (_bit & 0x1) == 0x1; }
        public int get_enchant()        { return _enchant; }
        public void set_enchant(int val){ _bit |= 0x2; _enchant = val; }
        public boolean has_enchant()    { return (_bit & 0x2) == 0x2; }

        /** 구 deep_copy 계승 — 같은 스킬 복제 (NOTI 재사용 시 원본 보호) */
        public Skill deep_copy() {
            Skill sk = newInstance();
            sk.set_id(_id);
            sk.set_enchant(_enchant);
            return sk;
        }

        void body(ProtoWriter w) {
            if (has_id())
                w.uint32(1, _id);
            if (has_enchant())
                w.uint32(2, _enchant);
        }
    }

    private List<Skill> _skills;

    private SC_CompanionSkillNoti() {}

    public static SC_CompanionSkillNoti newInstance() {
        return new SC_CompanionSkillNoti();
    }

    public void add_skills(Skill val) {
        if (!has_skills())
            _skills = new ArrayList<>();
        _skills.add(val);
    }
    public boolean has_skills() { return _skills != null; }
    public List<Skill> get_skills() { return _skills; }

    // ── 구 send 3종 — 시그니처 동일 ──

    /** 스킬 목록 전체 통지 */
    public static void send(L1PcInstance master, ArrayList<Skill> skills) {
        if (master == null || skills == null) return;
        List<Skill> notiWire_skills = null;


        for (Skill sk : skills)
            {
            Skill proto975_arg0 = sk.deep_copy();

                if (!(notiWire_skills != null)) notiWire_skills = new ArrayList<>();
                notiWire_skills.add(proto975_arg0);
            }
        SC_CompanionSkillNoti.sendFields(master, notiWire_skills);
    }

    /** 컴패니언 전체 스킬 통지 */
    public static void send(L1PcInstance master, L1CompanionInstance companionInstance) {
        send(master, companionInstance.get_all_skills());
    }

    /** 단일 스킬 통지 */
    public static void send(L1PcInstance master, Skill skill) {
        if (master == null || skill == null) return;
        List<Skill> notiWire_skills = null;


        {
        Skill proto976_arg0 = skill.deep_copy();

            if (!(notiWire_skills != null)) notiWire_skills = new ArrayList<>();
            notiWire_skills.add(proto976_arg0);
        }
        SC_CompanionSkillNoti.sendFields(master, notiWire_skills);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_COMPANION_SKILL_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        if (has_skills()) {
            for (Skill val : _skills) {
                w.msg(1);               // 구: writeMessage(1, val)
                val.body(w);
                w.end();
            }
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, List<Skill> _skills) {
        if ((_skills != null)) {
                for (Skill val : _skills) {
                    w.msg(1);
                    val.body(w);
                    w.end();
                }
            }
    }

    public static boolean sendFields(L1PcInstance recipient, List<Skill> _skills) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _skills);
    }

    public static boolean sendFields(CIOClient recipient, List<Skill> _skills) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_COMPANION_SKILL_NOTI, Proto.capacityBound(128L + (_skills == null ? 0L : 64L * _skills.size())));
        if (writer == null) return false;
        try {
            writeFields(writer, _skills);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(List<Skill> _skills) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _skills);
        return writer.finishWithTrailer(ProtoMsg.SC_COMPANION_SKILL_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        List<Skill> proto24_skills = null;


        return sendFields(protoRecipient, proto24_skills);
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
