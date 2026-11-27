package l1j.server.server.packets.Protocol.MainServer.Proto;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import sun.misc.Unsafe;

public final class PartyProtoRegression {
    private static final Charset MS949 = Charset.forName("MS949");

    public static void main(String[] args) throws Exception {
        // The packet row predicate only inspects connection presence. No socket methods run.
        final var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        final var connected = (CUserSocket) ((Unsafe) field.get(null)).allocateInstance(CUserSocket.class);
        final var leader = new TestPlayer(11, "가".repeat(65), 38_000, 1_234, connected);
        final var offline = new TestPlayer(12, "제외", 90, 80, null);
        final var member = new TestPlayer(13, "동료🙂", 200, 300, connected);
        member.setDead(true);
        final L1PcInstance[] snapshot = {leader, offline, null, member};
        final Map<Integer, Integer> marks = Map.of(11, -27, 12, 8);

        verifyRoster(leader, member, snapshot, marks);
        verifyPeriodic(leader, member, snapshot, marks);
        verifySingleMember(leader, marks.get(11));

        same(SC_PARTY_MEMBER_LIST.empty().encode(), SC_PARTY_MEMBER_LIST.encodeEmpty(), "empty roster");
        same(SC_PARTY_MEMBER_LIST_CHANGE.ofOutUser(member.getName()).encode(),
                SC_PARTY_MEMBER_LIST_CHANGE.encodeOutUser(member.getName()), "out user");
        same(SC_PARTY_MEMBER_LIST_CHANGE.ofLeaderChange(leader.getName()).encode(),
                SC_PARTY_MEMBER_LIST_CHANGE.encodeLeaderChange(leader.getName()), "leader change");
        same(new SC_PARTY_MEMBER_MARK_CHANGE_NOTI(leader.getId(), -27).encode(),
                SC_PARTY_MEMBER_MARK_CHANGE_NOTI.encodeFields(leader.getId(), -27), "mark change");
        System.out.println("PASS party roster and status protobuf regression");
    }

    private static void verifyRoster(L1PcInstance leader, L1PcInstance member,
            L1PcInstance[] snapshot, Map<Integer, Integer> marks) {
        final var leaderRow = SC_PARTY_MEMBER_LIST.Member.from(leader, marks.get(leader.getId()));
        final var memberRow = SC_PARTY_MEMBER_LIST.Member.from(member, 0);
        final byte[] oldRoster = new SC_PARTY_MEMBER_LIST(leader.getName(), List.of(leaderRow, memberRow)).encode();
        final long before = System.currentTimeMillis();
        final byte[] roster = SC_PARTY_MEMBER_LIST.encodeConnectedMembers(leader.getName(), snapshot, marks);
        final long after = System.currentTimeMillis();
        final ProtoReader expected = body(oldRoster, ProtoMsg.SC_PARTY_MEMBER_LIST);
        final ProtoReader actual = body(roster, ProtoMsg.SC_PARTY_MEMBER_LIST);
        require(expected.tag() == 0x0a && actual.tag() == 0x0a, "leader appears before roster");
        same(leader.getName().getBytes(MS949), actual.readBytes(), "leader name MS949");
        expected.readBytes();
        for (int row = 0; row < 2; row++) {
            require(expected.tag() == 0x12 && actual.tag() == 0x12, "ordered roster row");
            compareRosterRow(expected.subReader(), actual.subReader(), before, after);
        }
        require(expected.isAtEnd() && actual.isAtEnd(), "detached or null roster rows excluded");

        final ProtoReader oldChange = body(SC_PARTY_MEMBER_LIST_CHANGE.ofNewMember(memberRow).encode(),
                ProtoMsg.SC_PARTY_MEMBER_LIST_CHANGE);
        final long beforeChange = System.currentTimeMillis();
        final ProtoReader newChange = body(SC_PARTY_MEMBER_LIST_CHANGE.encodeNewMember(member, 0),
                ProtoMsg.SC_PARTY_MEMBER_LIST_CHANGE);
        require(oldChange.tag() == 0x22 && newChange.tag() == 0x22, "new member field");
        compareRosterRow(oldChange.subReader(), newChange.subReader(), beforeChange, System.currentTimeMillis());
        require(oldChange.isAtEnd() && newChange.isAtEnd(), "no extra new-member fields");
    }

    private static void compareRosterRow(ProtoReader expected, ProtoReader actual, long before, long after) {
        require(expected.tag() == 0x0a && actual.tag() == 0x0a, "roster name field");
        same(expected.readBytes(), actual.readBytes(), "roster name MS949");
        for (int field = 2; field <= 12; field++) {
            require(expected.tag() == (field << 3) && actual.tag() == (field << 3), "roster field " + field);
            final long oldValue = expected.varint64();
            final long newValue = actual.varint64();
            require(field == 11 ? newValue >= before && newValue <= after : oldValue == newValue,
                    "roster value " + field);
        }
        require(expected.isAtEnd() && actual.isAtEnd(), "no extra roster row fields");
    }

    private static void verifyPeriodic(L1PcInstance leader, L1PcInstance member,
            L1PcInstance[] snapshot, Map<Integer, Integer> marks) {
        final var expected = new SC_PARTY_SYNC_PERIODIC_INFO(List.of(
                new SC_PARTY_SYNC_PERIODIC_INFO.MemberStatus(leader.getName(), leader.getId(), 34, 65, 1,
                        42, leader.getLongLocation(), marks.get(leader.getId())),
                new SC_PARTY_SYNC_PERIODIC_INFO.MemberStatus(member.getName(), member.getId(), 34, 65, 0,
                        42, member.getLongLocation(), 0)));
        same(expected.encode(), SC_PARTY_SYNC_PERIODIC_INFO.encodeConnectedMembers(snapshot, marks),
                "periodic order, forward location, alive flag and default mark");
    }

    private static void verifySingleMember(L1PcInstance leader, int mark) {
        final byte[] reverse = SC_PARTY_MEMBER_STATUS.encodeFrom(leader, mark, true);
        same(SC_PARTY_MEMBER_STATUS.from(leader, mark, true).encode(), reverse, "reverse location");
        final byte[] forward = SC_PARTY_MEMBER_STATUS.encodeFrom(leader, mark, false);
        same(SC_PARTY_MEMBER_STATUS.from(leader, mark, false).encode(), forward, "forward location");
        require(!Arrays.equals(forward, reverse), "different coordinates distinguish location direction");
    }

    private static ProtoReader body(byte[] frame, int messageId) {
        require(frame.length >= 5 && frame[0] == (byte) Opcodes.S_EXTENDED_PROTOBUF, "packet opcode");
        require(((frame[1] & 255) | (frame[2] & 255) << 8) == messageId, "message ID");
        require(frame[frame.length - 1] == 0 && frame[frame.length - 2] == 0, "packet trailer");
        return new ProtoReader(frame, 3, frame.length - 5);
    }

    private static void same(byte[] expected, byte[] actual, String message) {
        require(Arrays.equals(expected, actual), message);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class TestPlayer extends L1PcInstance {
        private final CUserSocket socket;

        private TestPlayer(int id, String name, int x, int y, CUserSocket socket) {
            this.socket = socket;
            setId(id);
            setName(name);
            setX(x);
            setY(y);
            set_sex(1);
        }

        @Override public CUserSocket getNetConnection() { return socket; }
        @Override public int getClassNumber() { return 6; }
        @Override public short getMapId() { return 42; }
        @Override public double getCurrentHpPercent() { return 34.9; }
        @Override public double getCurrentMpPercent() { return 65.1; }
    }
}
