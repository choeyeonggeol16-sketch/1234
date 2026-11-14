package LIN;

import java.util.LinkedList;
import java.util.Map;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;



/**
 * SC_ALCHEMY_EXTRA_INFO_NOTI (0x0080) — 소환중인 인형(펫볼) 목록
 *  1 summoned_petball_item_id repeated int32
 */
public final class SC_ALCHEMY_EXTRA_INFO_NOTI extends ProtoPacket {

    private final LinkedList<Integer> _petballIds;

    private SC_ALCHEMY_EXTRA_INFO_NOTI(LinkedList<Integer> petballIds) {
        _petballIds = petballIds;
    }

    public static void send(L1PcInstance pc){
        Map<Integer, L1DollInstance> dolls = pc.getDollList();
        if(dolls.size() <= 0)
            return;

        LinkedList<Integer> ids = new LinkedList<>();
        for(L1DollInstance doll : dolls.values())
            ids.add(doll.getItemObjId());
        LIN.SC_ALCHEMY_EXTRA_INFO_NOTI.sendConstructed(pc, ids);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_ALCHEMY_EXTRA_INFO_NOTI; }

    @Override
    protected void body(ProtoWriter w) {
        for(int id : _petballIds)
            w.int32(1, id);  
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, LinkedList<Integer> _petballIds) {
        for (int id : _petballIds) w.int32(1, id);
    }

    public static boolean sendFields(L1PcInstance recipient, LinkedList<Integer> _petballIds) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _petballIds);
    }

    public static boolean sendFields(CIOClient recipient, LinkedList<Integer> _petballIds) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ALCHEMY_EXTRA_INFO_NOTI, Proto.capacityBound(128L + (_petballIds == null ? 0L : 64L * _petballIds.size())));
        if (writer == null) return false;
        try {
            writeFields(writer, _petballIds);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(LinkedList<Integer> _petballIds) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _petballIds);
        return writer.finishWithTrailer(ProtoMsg.SC_ALCHEMY_EXTRA_INFO_NOTI);
    }

    private static boolean sendCtor(CIOClient protoRecipient, LinkedList<Integer> petballIds) {
        LinkedList<Integer> proto9_petballIds = null;

            proto9_petballIds = petballIds;

        return sendFields(protoRecipient, proto9_petballIds);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, LinkedList<Integer> petballIds) { return sendCtor(protoRecipient, petballIds); }

    public static boolean sendConstructed(L1PcInstance protoRecipient, LinkedList<Integer> petballIds) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), petballIds); }

}
