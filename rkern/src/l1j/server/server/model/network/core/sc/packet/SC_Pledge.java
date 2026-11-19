package l1j.server.server.model.network.core.sc.packet;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;

import l1j.server.server.Opcodes;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_Pledge {
    private SC_Pledge() {}

    public static boolean sendAnnounce(L1PcInstance pc, int clanId) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final L1Clan clan = ClanTable.getInstance().getTemplate(clanId);
        final String clanName = clan == null || clan.getClanName() == null ? "" : clan.getClanName();
        final String leaderName = clan == null || clan.getLeaderName() == null ? "" : clan.getLeaderName();
        final int emblemId = clan == null ? 0 : clan.getEmblemId();
        final boolean hasHouse = clan != null && clan.getHouseId() != 0;
        final boolean hasCastle = clan != null && clan.getCastleId() != 0;
        final int birthDay = clan == null || clan.getClanBirthDay() == null ? 0
                : (int) (clan.getClanBirthDay().getTime() / 1000);
        final String announcement = clan == null || clan.getAnnouncement() == null
                ? "" : clan.getAnnouncement();
        final CIOFrame frame = connection.beginDirectFrame(24 + ((clanName.length() + leaderName.length() + announcement.length()) << 1));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(167);
            buffer.writeS(clanName);
            buffer.writeS(leaderName);
            buffer.writeD(emblemId);
            buffer.writeC(hasHouse ? 1 : 0);
            buffer.writeC(hasCastle ? 1 : 0);
            buffer.writeC(0);
            buffer.writeD(birthDay);
            buffer.writeS(announcement);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean sendMembers(L1PcInstance pc, int page, int currentPage, ArrayList<String> list) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int limit = list == null ? 0 : list.size();
        final String[] names = new String[limit];
        final int[] values = new int[limit * 5];
        final byte[][] notes = new byte[limit][];
        int count = 0;
        long capacity = 10L;
        for (int i = 0; i < limit; ++i) {
            final String name = list.get(i);
            if (name == null) continue;
            final L1PcInstance member;
            try { member = CharacterTable.getInstance().restoreCharacter(name); }
            catch (Exception ignored) { continue; }
            if (member == null) continue;
            final String memberName = member.getName() == null ? "" : member.getName();
            capacity += 78L + ((long) memberName.length() << 1);
            if (capacity > Integer.MAX_VALUE || count >= 255) return false;
            names[count] = memberName;
            notes[count] = encodeNotes(member.getClanMemberNotes());
            final int j = count * 5;
            values[j] = "MJBOT".equals(member.getAccountName()) && member.getClanRank() == 0
                    ? 7 : member.getClanRank();
            values[j + 1] = member.getLevel();
            values[j + 2] = member.getClanMemberId();
            values[j + 3] = member.getType();
            values[j + 4] = (int) (System.currentTimeMillis() / 1000L);
            count++;
        }
        final CIOFrame frame = connection.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(170);
            buffer.writeC(page);
            buffer.writeC(currentPage);
            buffer.writeC(count);
            for (int i = 0; i < count; ++i) {
                final int j = i * 5;
                buffer.writeS(names[i]);
                buffer.writeC(values[j]);
                buffer.writeC(values[j + 1]);
                buffer.writeByte(notes[i]);
                buffer.writeD(values[j + 2]);
                buffer.writeC(values[j + 3]);
                buffer.writeD(values[j + 4]);
            }
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean sendWriteNotes(L1PcInstance pc, String name, String notes) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final String targetName = name == null ? "" : name;
        final byte[] noteBytes = encodeNotes(notes);
        final CIOFrame frame = connection.beginDirectFrame(72 + (targetName.length() << 1));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(169);
            buffer.writeS(targetName);
            buffer.writeByte(noteBytes);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    /**
     * Clan notification is recipient-independent during a single locked clan
     * tick. Snapshot the source fields once; retain the individual sendBless
     * method for asynchronous, recipient-timed updates (e.g. CS_Craft).
     */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter
            nativeBlessWriter(L1Clan clan, int bless) {
        final long blessCount = clan == null ? 0L : clan.getBlessCount();
        final int[] times = clan == null || clan.getBuffTime() == null
                ? new int[4] : clan.getBuffTime().clone();
        return out -> {
            out.put((byte) Opcodes.S_EXTENDED_PROTOBUF).putShort((short) 0x8a);
            out.put((byte) 0x08);
            writeNativeVarint(out, (int) (blessCount / 10000));
            out.put((byte) 0x10);
            writeNativeVarint(out, 40000);
            out.put((byte) 0x18);
            writeNativeVarint(out, 30000);
            out.put((byte) 0x20);
            writeNativeVarint(out, 1000);
            for (int i = 0; i < 4; i++) {
                final int value = i < times.length ? times[i] : 0;
                final int time = value == 0 ? 172800 : value;
                final int size = nativeVarintSize(time);
                out.put((byte) 0x2a);
                writeNativeVarint(out, 27 + size);
                out.put((byte) 0x0a).put((byte) (size + 6));
                out.put((byte) 0x08);
                writeNativeVarint(out, 2724 + i);
                out.put((byte) 0x10);
                writeNativeVarint(out, time);
                out.put((byte) 0x18);
                out.put((byte) (value == 0 ? 1 : bless == i + 1 ? 2 : 3));
                out.put((byte) 0x12);
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949S2(out, "$" + (22503 + i));
                out.put((byte) 0x1a);
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949S2(out, "$" + (22508 + i));
                out.put((byte) 0x20);
                writeNativeVarint(out, 7233 + i * 2);
            }
            out.putShort((short) 0);
        };
    }

    private static int nativeVarintSize(int n) {
        if (n < 0) return 10;
        if (n < (1 << 7)) return 1;
        if (n < (1 << 14)) return 2;
        if (n < (1 << 21)) return 3;
        if (n < (1 << 28)) return 4;
        return 5;
    }

    private static void writeNativeVarint(java.nio.ByteBuffer out, long n) {
        while ((n & ~0x7fL) != 0L) {
            out.put((byte) ((n & 0x7fL) | 0x80L));
            n >>>= 7;
        }
        out.put((byte) n);
    }

    public static boolean sendBless(L1PcInstance pc, L1Clan clan, int bless) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final long blessCount = clan == null ? 0L : clan.getBlessCount();
        final int[] times = clan == null || clan.getBuffTime() == null ? new int[4] : clan.getBuffTime().clone();
        final CIOFrame frame = connection.beginDirectFrame(512);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(0x8a);
            buffer.writeC(0x08);
            buffer.write7B((int) (blessCount / 10000));
            buffer.writeC(0x10);
            buffer.write7B(40000);
            buffer.writeC(0x18);
            buffer.write7B(30000);
            buffer.writeC(0x20);
            buffer.write7B(1000);
            for (int i = 0; i < 4; i++) {
                final int value = i < times.length ? times[i] : 0;
                final int time = value == 0 ? 172800 : value;
                buffer.writeC(0x2a);
                buffer.write7B(27 + buffer.bitlengh(time));
                buffer.writeC(0x0a);
                buffer.writeC(buffer.bitlengh(time) + 6);
                buffer.writeC(0x08);
                buffer.write7B(2724 + i);
                buffer.writeC(0x10);
                buffer.write7B(time);
                buffer.writeC(0x18);
                buffer.writeC(value == 0 ? 1 : bless == i + 1 ? 2 : 3);
                buffer.writeC(0x12);
                buffer.writeS2("$" + (22503 + i));
                buffer.writeC(0x1a);
                buffer.writeS2("$" + (22508 + i));
                buffer.writeC(0x20);
                buffer.write7B(7233 + (i * 2));
            }
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }


	private static byte[] encodeNotes(String notes) {
		byte[] text = new byte[62];
		Arrays.fill(text, (byte) 0);

		if (notes != null && !notes.isEmpty()) {
			try {
				byte[] bytes = notes.getBytes("MS949");
				int length = Math.min(bytes.length, text.length);
				System.arraycopy(bytes, 0, text, 0, length);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		return text;
	}

}
