package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import java.util.ArrayList;
import java.util.List;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

/**
 * SC_PARTY_MEMBER_LIST (0x0337) — 파티원 목록 UI
 * 와이어:
 *   1 leader_name  string (MS949)  [required]
 *   2 member       repeated PartyMember {
 *      1  name              string
 *      2  accountid         uint32
 *      3  object_id         uint32
 *      4  game_class        int32
 *      5  gender            int32
 *      6  hp_ratio          int32
 *      7  mp_ratio          int32
 *      8  world             int32
 *      9  location          int32
 *      10 party_mark        int64   (varint)
 *      11 alive_time_stamp  uint64  (varint)
 *      12 server_no         uint32
 *   }
 */
public final class SC_PartyMemberList extends ProtoPacket {

    /** MJ PartyMember 이관 — LIST/CHANGE 양쪽에서 공유 */
    public static final class Member {
        public final String name;
        public final int accountId, objectId, gameClass, gender;
        public final int hpRatio, mpRatio, world, location;
        public final long partyMark, aliveTimeStamp;
        public final int serverNo;

        private Member(String name, int accountId, int objectId, int gameClass, int gender,
                int hpRatio, int mpRatio, int world, int location,
                long partyMark, long aliveTimeStamp, int serverNo) {
            this.name = name;
            this.accountId = accountId;
            this.objectId = objectId;
            this.gameClass = gameClass;
            this.gender = gender;
            this.hpRatio = hpRatio;
            this.mpRatio = mpRatio;
            this.world = world;
            this.location = location;
            this.partyMark = partyMark;
            this.aliveTimeStamp = aliveTimeStamp;
            this.serverNo = serverNo;
        }

        /** MJ PartyMember.newInstance(pc, markNo) 이관 */
        public static Member from(L1PcInstance pc, int markNo) {
            return new Member(
                    pc.getName(),
                    0,                          // accountid: MJ도 0 고정
                    pc.getId(),
                    pc.getClassNumber(),
                    pc.get_sex(),
                    (int) pc.getCurrentHpPercent(),
                    (int) pc.getCurrentMpPercent(),
                    pc.getMapId(),
                    pc.getLongLocation(),
                    markNo,
                    System.currentTimeMillis(),
                    0);                         // server_no: MJ도 0 고정
        }

        /** LIST(필드2) / CHANGE(필드4) 공용 직렬화 */
        public void body(ProtoWriter w) {
            w.string(1, name)
             .uint32(2, accountId)
             .uint32(3, objectId)
             .int32(4, gameClass)
             .int32(5, gender)
             .int32(6, hpRatio)
             .int32(7, mpRatio)
             .int32(8, world)
             .int32(9, location)
             .int64(10, partyMark)
             .uint64(11, aliveTimeStamp)
             .uint32(12, serverNo);
        }
    }

    private final String _leaderName;
    private final List<Member> _members;

    /** 일반: 리더 이름 + 멤버들 */
    public SC_PartyMemberList(String leaderName, List<Member> members) {
        _leaderName = (leaderName != null) ? leaderName : "";
        _members = members;
    }

    /** UI 정리용 빈 리스트 (leader_name="" — required 채워서 isInitialized 통과) */
    public static SC_PartyMemberList empty() {
        return new SC_PartyMemberList("", new ArrayList<>());
    }

    @Override
    public int msgId() {
        return ProtoMsg.SC_PARTY_MEMBER_LIST;
    }

    @Override
    protected void body(ProtoWriter w) {
        w.string(1, _leaderName);         
        for (Member m : _members) {
            w.msg(2);
            m.body(w);
            w.end();
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, String _leaderName, List<Member> _members) {
        w.string(1, _leaderName);
            for (Member m : _members) {
                w.msg(2);
                m.body(w);
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, String _leaderName, List<Member> _members) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _leaderName, _members);
    }

    public static boolean sendFields(CIOClient recipient, String _leaderName, List<Member> _members) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_PARTY_MEMBER_LIST, capacityFor(_leaderName, _members));
        if (writer == null) return false;
        try {
            writeFields(writer, _leaderName, _members);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(String _leaderName, List<Member> _members) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _leaderName, _members);
        return writer.finishWithTrailer(ProtoMsg.SC_PARTY_MEMBER_LIST);
    }

    private static boolean sendCtor(CIOClient protoRecipient, String leaderName, List<Member> members) {
        String proto49_leaderName = null;
        List<Member> proto49_members = null;

            proto49_leaderName = (leaderName != null) ? leaderName : "";
            proto49_members = members;

        return sendFields(protoRecipient, proto49_leaderName, proto49_members);
    }

    public static boolean sendEmpty(CIOClient protoRecipient) {
        return sendCtor(protoRecipient, "", new ArrayList<>());
    }

    public static boolean sendEmpty(L1PcInstance protoRecipient) {
        return sendEmpty(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }
    public static boolean sendConstructed(CIOClient protoRecipient, String leaderName, List<Member> members) { return sendCtor(protoRecipient, leaderName, members); }

    public static boolean sendConstructed(L1PcInstance protoRecipient, String leaderName, List<Member> members) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), leaderName, members); }


    private static int capacityFor(String leader, List<Member> members) {
        long bytes = 128L + (leader == null ? 0L : 2L * leader.length());
        if (members != null) for (int i = 0, n = members.size(); i < n; i++) {
            Member member = members.get(i);
            bytes += 256L + (member.name == null ? 0L : 2L * member.name.length());
        }
        return Proto.capacityBound(bytes);
    }
}
