package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_PARTY_MEMBER_MARK_CHANGE_NOTI (0x0153) — 파티원 표식 변경
 *  1 member_id  2 mark
 */
public final class SC_PARTY_MEMBER_MARK_CHANGE_NOTI extends ProtoPacket {

    private final int _memberId;
    private final int _mark;

    public SC_PARTY_MEMBER_MARK_CHANGE_NOTI(int memberId, int mark) {
        _memberId = memberId;
        _mark = mark;
    }

    @Override
    public int msgId() { return ProtoMsg.SC_PARTY_MEMBER_MARK_CHANGE; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _memberId)
         .int32(2, _mark);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _memberId, int _mark) {
        w.int32(1, _memberId).int32(2, _mark);
    }

    public static boolean sendFields(L1PcInstance recipient, int _memberId, int _mark) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _memberId, _mark);
    }

    public static boolean sendFields(CIOClient recipient, int _memberId, int _mark) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_PARTY_MEMBER_MARK_CHANGE, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _memberId, _mark);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _memberId, int _mark) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _memberId, _mark);
        return writer.finishWithTrailer(ProtoMsg.SC_PARTY_MEMBER_MARK_CHANGE);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int memberId, int mark) {
        int proto51_memberId = 0;
        int proto51_mark = 0;

            proto51_memberId = memberId;
            proto51_mark = mark;

        return sendFields(protoRecipient, proto51_memberId, proto51_mark);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, int memberId, int mark) { return sendCtor(protoRecipient, memberId, mark); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int memberId, int mark) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), memberId, mark); }

}
