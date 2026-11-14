package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_COMPANION_SKILL_ENCHANT_ACK (0x07D9) — 컴패니언 스킬 인챈트 결과
 * wire: [1]=skill_id uint32, [2]=result enum(varint)
 */
public final class SC_COMPANION_SKILL_ENCHANT_ACK extends ProtoPacket {

    public enum eResult {
        SUCCESS(0),
        FAIL(1),
        NOTHING(2);

        private final int value;
        eResult(int val) { value = val; }
        public int toInt() { return value; }

        public static eResult fromInt(int i) {
            switch (i) {
            case 0: return SUCCESS;
            case 1: return FAIL;
            case 2: return NOTHING;
            default: return null;
            }
        }
    }

    private int _skillId;
    private eResult _result;

    private SC_COMPANION_SKILL_ENCHANT_ACK() {}

    public static SC_COMPANION_SKILL_ENCHANT_ACK newInstance() {
        return new SC_COMPANION_SKILL_ENCHANT_ACK();
    }

    /** 구 static send 시그니처 동일 */
    public static void send(L1PcInstance pc, int skillId, eResult result) {
        if (pc == null || result == null) return;
        int ackWire_skillId = 0;
        eResult ackWire_result = null;


        { ackWire_skillId = skillId; }
        { ackWire_result = result; }
        SC_COMPANION_SKILL_ENCHANT_ACK.sendFields(pc, ackWire_skillId, ackWire_result);
    }

    public int get_skill_id()              { return _skillId; }
    public void set_skill_id(int val)      { _skillId = val; }
    public boolean has_skill_id()          { return true; }   // 생성 후 항상 세팅됨 (구 required 계승)
    public eResult get_result()            { return _result; }
    public void set_result(eResult val)    { _result = val; }
    public boolean has_result()            { return _result != null; }

    @Override
    public int msgId() { return ProtoMsg.SC_COMPANION_SKILL_ENCHANT_ACK; }

    @Override
    protected void body(ProtoWriter w) {
        w.uint32(1, _skillId);              // 구: writeUInt32(1, ...) — required
        if (_result != null)
            w.enumV(2, _result.toInt());    // 구: writeEnum(2, ...)
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _skillId, eResult _result) {
        w.uint32(1, _skillId);
            if (_result != null) w.enumV(2, _result.toInt());
    }

    public static boolean sendFields(L1PcInstance recipient, int _skillId, eResult _result) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _skillId, _result);
    }

    public static boolean sendFields(CIOClient recipient, int _skillId, eResult _result) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_COMPANION_SKILL_ENCHANT_ACK, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _skillId, _result);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _skillId, eResult _result) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _skillId, _result);
        return writer.finishWithTrailer(ProtoMsg.SC_COMPANION_SKILL_ENCHANT_ACK);
    }

    private static boolean sendCtor(CIOClient protoRecipient) {
        int proto23_skillId = 0;
        eResult proto23_result = null;


        return sendFields(protoRecipient, proto23_skillId, proto23_result);
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
