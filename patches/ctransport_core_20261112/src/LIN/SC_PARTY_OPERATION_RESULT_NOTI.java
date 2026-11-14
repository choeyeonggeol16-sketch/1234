package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_PARTY_OPERATION_RESULT_NOTI (0x021B) — 초대 수락/거절 결과
 *  1 type: 1=reject, 2=accept  2 actor_name
 */
public final class SC_PARTY_OPERATION_RESULT_NOTI extends ProtoPacket {

    public enum ePARTY_OPERATION_TYPE {
        INVITE_REJECT(1), INVITE_ACCEPT(2);
        private final int v;
        ePARTY_OPERATION_TYPE(int v) { this.v = v; }
        public int toInt() { return v; }
    }

    private final LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE _type;
    private final String _actorName;

    public SC_PARTY_OPERATION_RESULT_NOTI(LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE type, String actorName) {
        _type = type;
        _actorName = actorName;
    }

    @Override
    public int msgId() { return ProtoMsg.SC_PARTY_OPERATION_RESULT; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _type.toInt())
         .string(2, _actorName);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE _type, String _actorName) {
        w.int32(1, _type.toInt()).string(2, _actorName);
    }

    public static boolean sendFields(L1PcInstance recipient, LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE _type, String _actorName) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _type, _actorName);
    }

    public static boolean sendFields(CIOClient recipient, LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE _type, String _actorName) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_PARTY_OPERATION_RESULT, Proto.capacityBound(1024L + (_actorName == null ? 0L : 2L * _actorName.length())));
        if (writer == null) return false;
        try {
            writeFields(writer, _type, _actorName);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE _type, String _actorName) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _type, _actorName);
        return writer.finishWithTrailer(ProtoMsg.SC_PARTY_OPERATION_RESULT);
    }

    private static boolean sendCtor(CIOClient protoRecipient, LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE type, String actorName) {
        LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE proto53_type = null;
        String proto53_actorName = null;

            proto53_type = type;
            proto53_actorName = actorName;

        return sendFields(protoRecipient, proto53_type, proto53_actorName);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE type, String actorName) { return sendCtor(protoRecipient, type, actorName); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, LIN.SC_PARTY_OPERATION_RESULT_NOTI.ePARTY_OPERATION_TYPE type, String actorName) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), type, actorName); }

}
