package l1j.server.server.packets.Protocol.MainServer.Proto;

import java.util.List;
import java.util.Map;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

/**
 * SC_PARTY_SYNC_PERIODIC_INFO (0x033B) — 파티원 상태 주기 동기화
 *  1 status  repeated { 1 name, 2 objectId, 3 hpRatio, 4 mpRatio,
 *                       5 status, 6 world, 7 location, 8 mark }
 */
public final class SC_PARTY_SYNC_PERIODIC_INFO extends ProtoPacket {

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

    public SC_PARTY_SYNC_PERIODIC_INFO(List<MemberStatus> members) {
        _members = members;
    }

    @Override
    public int msgId() { return ProtoMsg.SC_PARTY_SYNC_PERIODIC_INFO; }

    @Override
    protected void body(ProtoWriter w) {
        writeFields(w, _members);
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, List<MemberStatus> _members) {
        for (MemberStatus member : _members) {
            w.msg(1);
            SC_PARTY_MEMBER_STATUS.writeFields(w, member.name, member.objectId, member.hpRatio,
                    member.mpRatio, member.status, member.world, member.location, member.mark);
            w.end();
        }
    }

    public static byte[] encodeConnectedMembers(L1PcInstance[] members, Map<Integer, Integer> marks) {
        try (ProtoWriter writer = ProtoWriter.borrow()) {
            for (L1PcInstance member : members) {
                if (member == null || member.getNetConnection() == null) continue;
                writer.msg(1);
                SC_PARTY_MEMBER_STATUS.writeFrom(writer, member, marks.getOrDefault(member.getId(), 0), false);
                writer.end();
            }
            return writer.finishWithTrailer(ProtoMsg.SC_PARTY_SYNC_PERIODIC_INFO);
        }
    }

    public static boolean sendFields(L1PcInstance recipient, List<MemberStatus> _members) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _members);
    }

    public static boolean sendFields(CUserSocket recipient, List<MemberStatus> _members) {
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

    private static boolean sendCtor(CUserSocket protoRecipient, List<MemberStatus> members) {
        List<MemberStatus> proto54_members = null;

            proto54_members = members;

        return sendFields(protoRecipient, proto54_members);
    }
    public static boolean sendConstructed(CUserSocket protoRecipient, List<MemberStatus> members) { return sendCtor(protoRecipient, members); }

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
