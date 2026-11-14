package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_PARTY_MEMBER_STATUS (0x0339) — 단건 상태 갱신
 *  1 name, 2 objectId, 3 hpRatio, 4 mpRatio, 5 status(0=dead), 6 world, 7 location, 8 mark
 */
public final class SC_PARTY_MEMBER_STATUS extends ProtoPacket {

    private final String _name;
    private final int _objectId, _hpRatio, _mpRatio, _status, _world, _location, _mark;

    public SC_PARTY_MEMBER_STATUS(String name, int objectId, int hpRatio, int mpRatio,
            int status, int world, int location, int mark) {
        _name = name;
        _objectId = objectId;
        _hpRatio = hpRatio;
        _mpRatio = mpRatio;
        _status = status;
        _world = world;
        _location = location;
        _mark = mark;
    }
    public static SC_PARTY_MEMBER_STATUS from(L1PcInstance pc, int mark, boolean reverse) {
        return new SC_PARTY_MEMBER_STATUS(
                pc.getName(), pc.getId(),
                (int) pc.getCurrentHpPercent(), (int) pc.getCurrentMpPercent(),
                pc.isDead() ? 0 : 1,
                pc.getMapId(),
                reverse ? pc.getLongLocationReverse() : pc.getLongLocation(),
                mark);
    }

    @Override
    public int msgId() { return ProtoMsg.SC_PARTY_MEMBER_STATUS; }

    @Override
    protected void body(ProtoWriter w) {
        w.string(1, _name)
         .uint32(2, _objectId)
         .int32(3, _hpRatio)
         .int32(4, _mpRatio)
         .int32(5, _status)
         .int32(6, _world)
         .int32(7, _location)
         .int32(8, _mark);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, String _name, int _objectId, int _hpRatio, int _mpRatio, int _status, int _world, int _location, int _mark) {
        w.string(1, _name).uint32(2, _objectId).int32(3, _hpRatio).int32(4, _mpRatio).int32(5, _status).int32(6, _world).int32(7, _location).int32(8, _mark);
    }

    public static boolean sendFields(
            L1PcInstance recipient, String _name, int _objectId, int _hpRatio, int _mpRatio, int _status, int _world,
            int _location, int _mark) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _name, _objectId, _hpRatio, _mpRatio, _status, _world, _location, _mark);
    }

    public static boolean sendFields(
            CIOClient recipient, String _name, int _objectId, int _hpRatio, int _mpRatio, int _status, int _world,
            int _location, int _mark) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_PARTY_MEMBER_STATUS, Proto.capacityBound(1024L + (_name == null ? 0L : 2L * _name.length())));
        if (writer == null) return false;
        try {
            writeFields(writer, _name, _objectId, _hpRatio, _mpRatio, _status, _world, _location, _mark);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(String _name, int _objectId, int _hpRatio, int _mpRatio, int _status, int _world, int _location, int _mark) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _name, _objectId, _hpRatio, _mpRatio, _status, _world, _location, _mark);
        return writer.finishWithTrailer(ProtoMsg.SC_PARTY_MEMBER_STATUS);
    }

    private static boolean sendCtor(
            CIOClient protoRecipient, String name, int objectId, int hpRatio, int mpRatio, int status, int world,
            int location, int mark) {
        String proto52_name = null;
        int proto52_objectId = 0;
        int proto52_hpRatio = 0;
        int proto52_mpRatio = 0;
        int proto52_status = 0;
        int proto52_world = 0;
        int proto52_location = 0;
        int proto52_mark = 0;

            proto52_name = name;
            proto52_objectId = objectId;
            proto52_hpRatio = hpRatio;
            proto52_mpRatio = mpRatio;
            proto52_status = status;
            proto52_world = world;
            proto52_location = location;
            proto52_mark = mark;

        return sendFields(
                protoRecipient, proto52_name, proto52_objectId, proto52_hpRatio, proto52_mpRatio, proto52_status,
                proto52_world, proto52_location, proto52_mark);
    }

    public static boolean sendFrom(CIOClient protoRecipient, L1PcInstance pc, int mark, boolean reverse) {
        return sendCtor(
                protoRecipient, pc.getName(), pc.getId(), (int) pc.getCurrentHpPercent(), (int) pc.getCurrentMpPercent(),
                pc.isDead() ? 0 : 1, pc.getMapId(), reverse ? pc.getLongLocationReverse() : pc.getLongLocation(), mark);
    }

    public static boolean sendFrom(L1PcInstance protoRecipient, L1PcInstance pc, int mark, boolean reverse) {
        return sendFrom(protoRecipient == null ? null : protoRecipient.getNetConnection(), pc, mark, reverse);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, String name, int objectId, int hpRatio, int mpRatio, int status, int world,
            int location, int mark) { return sendCtor(protoRecipient, name, objectId, hpRatio, mpRatio, status, world, location, mark); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, String name, int objectId, int hpRatio, int mpRatio, int status, int world,
            int location, int mark) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), name, objectId, hpRatio, mpRatio, status, world, location, mark); }

}
