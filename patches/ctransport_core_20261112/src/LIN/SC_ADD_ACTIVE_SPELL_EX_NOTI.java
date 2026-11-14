package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_ADD_ACTIVE_SPELL_EX_NOTI (0x0192) — 스킬/패시브 획득 알림
 *  1 spell_id   int32 [required]
 *  2 spell_type int32 [required]
 */
public final class SC_ADD_ACTIVE_SPELL_EX_NOTI extends ProtoPacket {

    private final int _spellId;
    private final int _spellType;

    private SC_ADD_ACTIVE_SPELL_EX_NOTI(int spellId, int spellType) {
        _spellId = spellId;
        _spellType = spellType;
    }

    public static void send(L1PcInstance pc, int spellId, int spellType){
        LIN.SC_ADD_ACTIVE_SPELL_EX_NOTI.sendConstructed(pc, spellId, spellType);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_ADD_ACTIVE_SPELL_EX_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _spellId)
         .int32(2, _spellType);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _spellId, int _spellType) {
        w.int32(1, _spellId).int32(2, _spellType);
    }

    public static boolean sendFields(L1PcInstance recipient, int _spellId, int _spellType) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _spellId, _spellType);
    }

    public static boolean sendFields(CIOClient recipient, int _spellId, int _spellType) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ADD_ACTIVE_SPELL_EX_NOTI, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _spellId, _spellType);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _spellId, int _spellType) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _spellId, _spellType);
        return writer.finishWithTrailer(ProtoMsg.SC_ADD_ACTIVE_SPELL_EX_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int spellId, int spellType) {
        int proto3_spellId = 0;
        int proto3_spellType = 0;

            proto3_spellId = spellId;
            proto3_spellType = spellType;

        return sendFields(protoRecipient, proto3_spellId, proto3_spellType);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, int spellId, int spellType) { return sendCtor(protoRecipient, spellId, spellType); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int spellId, int spellType) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), spellId, spellType); }

}
