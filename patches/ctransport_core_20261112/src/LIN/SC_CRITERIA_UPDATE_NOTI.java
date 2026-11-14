package LIN;

import LIN.CriteriaProgress;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * SC_CRITERIA_UPDATE_NOTI — 업적 진행도(기준치) 갱신 알림
 * wire: [1]=criteria_id uint32, [2]=quantity uint64
 */
public final class SC_CRITERIA_UPDATE_NOTI extends ProtoPacket {

    private final int _criteriaId;
    private final long _quantity;

    public SC_CRITERIA_UPDATE_NOTI(int criteriaId, long quantity) {
        _criteriaId = criteriaId;
        _quantity = quantity;
    }

    /** 구 newInstance(CriteriaProgress) 대응 편의 팩토리 */
    public static LIN.SC_CRITERIA_UPDATE_NOTI of(LIN.CriteriaProgress p) {
        return new LIN.SC_CRITERIA_UPDATE_NOTI(p.criteria_id(), p.quantity());
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_CRITERIA_UPDATE_NOTI;
    }

    @Override
    protected void body(ProtoWriter w) {
        w.uint32(1, _criteriaId);  
        w.uint64(2, _quantity);    
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _criteriaId, long _quantity) {
        w.uint32(1, _criteriaId);
            w.uint64(2, _quantity);
    }

    public static boolean sendFields(L1PcInstance recipient, int _criteriaId, long _quantity) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _criteriaId, _quantity);
    }

    public static boolean sendFields(CIOClient recipient, int _criteriaId, long _quantity) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_CRITERIA_UPDATE_NOTI, Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _criteriaId, _quantity);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _criteriaId, long _quantity) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _criteriaId, _quantity);
        return writer.finishWithTrailer(ProtoMsg.SC_CRITERIA_UPDATE_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int criteriaId, long quantity) {
        int proto29_criteriaId = 0;
        long proto29_quantity = 0;

            proto29_criteriaId = criteriaId;
            proto29_quantity = quantity;

        return sendFields(protoRecipient, proto29_criteriaId, proto29_quantity);
    }

    public static boolean sendOf(CIOClient protoRecipient, LIN.CriteriaProgress p) {
        return sendCtor(protoRecipient, p.criteria_id(), p.quantity());
    }

    public static boolean sendOf(L1PcInstance protoRecipient, LIN.CriteriaProgress p) {
        return sendOf(protoRecipient == null ? null : protoRecipient.getNetConnection(), p);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, int criteriaId, long quantity) { return sendCtor(protoRecipient, criteriaId, quantity); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int criteriaId, long quantity) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), criteriaId, quantity); }

}
