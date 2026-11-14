package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import java.util.List;

import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_PARTY_SYNC_PERIODIC_INFO (0x033B) — 파티원 상태 주기 동기화
 *  1 status  repeated { 1 name, 2 objectId, 3 hpRatio, 4 mpRatio,
 *                       5 status, 6 world, 7 location, 8 mark }
 */
public final class SC_PartySyncPeriodicInfo extends ProtoPacket {

    public static final class MemberStatus {
        public final String name;
        public final int objectId, hpRatio, mpRatio, status, world, location, mark;

        public MemberStatus(String name, int objectId, int hpRatio, int mpRatio,
                int status, int world, int location, int mark) {
            this.name = name;
            this.objectId = objectId;
            this.hpRatio = hpRatio;
            this.mpRatio = mpRatio;
            this.status = status;
            this.world = world;
            this.location = location;
            this.mark = mark;
        }
    }

    private final List<MemberStatus> _members;

    public SC_PartySyncPeriodicInfo(List<MemberStatus> members) {
        _members = members;
    }

    @Override
    public int msgId() { return ProtoMsg.SC_PARTY_SYNC_PERIODIC_INFO; }

    @Override
    protected void body(ProtoWriter w) {
        for (MemberStatus m : _members) {
            w.msg(1)
             .string(1, m.name)
             .uint32(2, m.objectId)
             .int32(3, m.hpRatio)
             .int32(4, m.mpRatio)
             .int32(5, m.status)
             .int32(6, m.world)
             .int32(7, m.location)
             .int32(8, m.mark)
             .end();
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, List<MemberStatus> _members) {
        for (MemberStatus m : _members) {
                w.msg(1).string(1, m.name).uint32(2, m.objectId).int32(3, m.hpRatio).int32(4, m.mpRatio).int32(5, m.status).int32(6, m.world).int32(7, m.location).int32(8, m.mark).end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, List<MemberStatus> _members) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _members);
    }

    public static boolean sendFields(CIOClient recipient, List<MemberStatus> _members) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_PARTY_SYNC_PERIODIC_INFO, capacityFor(_members));
        if (writer == null) return false;
        try {
            writeFields(writer, _members);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(List<MemberStatus> _members) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _members);
        return writer.finishWithTrailer(ProtoMsg.SC_PARTY_SYNC_PERIODIC_INFO);
    }

    private static boolean sendCtor(CIOClient protoRecipient, List<MemberStatus> members) {
        List<MemberStatus> proto54_members = null;

            proto54_members = members;

        return sendFields(protoRecipient, proto54_members);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, List<MemberStatus> members) { return sendCtor(protoRecipient, members); }

    public static boolean sendConstructed(L1PcInstance protoRecipient, List<MemberStatus> members) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), members); }


    private static int capacityFor(List<MemberStatus> members) {
        long bytes = 128L;
        if (members != null) for (int i = 0, n = members.size(); i < n; i++) {
            MemberStatus member = members.get(i);
            bytes += 256L + (member.name == null ? 0L : 2L * member.name.length());
        }
        return Proto.capacityBound(bytes);
    }
}
