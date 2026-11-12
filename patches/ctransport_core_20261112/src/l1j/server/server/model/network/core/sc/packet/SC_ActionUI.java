package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;
import java.util.List;
import java.util.StringTokenizer;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

public final class SC_ActionUI {

    private SC_ActionUI() {}

	public static final int TAM = 450;
	public static final int CLAN_JOIN_MESSAGE = 323;
	public static final int TEST = 0xcc;
	public static final int TEST2 = 0x3A;
	public static final int SAFETYZONE = 0xcf;
	public static final int PCBANG_SET = 0x7e;
	public static final int EVENT_SYSTEM = ProtoMsg.SC_NOTIFICATION_INFO_NOTI;

	public static final int MONSTER_BOOK_WEEK_QUEST = 810;

	public static final int START_PLAY_SUPPORT_ACK = 2102;
	public static final int FINISH_PLAY_SUPPORT_ACK = 2104;

	public static final int SUPPORT_VALID = 0;
	public static final int SUPPORT_INVALID_MAP = 1;
	public static final int SUPPORT_INVALID_LEVEL = 2;
	public static final int SUPPORT_TIME_EXPIRE = 3;
	public static final int SUPPORT_UNKNOWN_ERROR = 99;

	public static final int LOGIN_ADD_SKILL = 401;
	public static final int ADD_SKILL = 402;

	private static final int EVENT_DURATION_SECONDS = 300;

	private static final String 테스트진행 = ""
			+ "0a e3 01 0a 55 "
			+ "12 10 08 01 10 9a 83 2c 18 00 22 06 08 d8 87 01 10 01 "
			+ "12 11 08 02 10 81 88 6e 18 c6 20 22 06 08 d8 87 01 10 02 "
			+ "12 12 08 03 10 84 a0 b8 03 18 9f 78 22 06 08 d8 87 01 10 05 "
			+ "1a 0b 08 01 10 b5 bf f0 06 18 c1 87 01 "
			+ "1a 0b 08 01 10 84 a0 b8 03 18 c0 87 01 20 01 ";
	
	
	private static final Charset MS949 = Charset.forName("MS949");

	public static final class EventEntry {

		public final L1NpcInstance npc;
		public final int bossType;
		public final int startTime;
		public final int endTime;

		public EventEntry(
				L1NpcInstance npc,
				int bossType,
				int startTime,
				int endTime) {

			this.npc = npc;
			this.bossType = bossType;
			this.startTime = startTime;
			this.endTime = endTime;
		}
	}

    // Stable, non-boss IDs reserved for the two store notifications.
    public static final int EVENT_ADEN_SHOP = 15001;
    public static final int EVENT_GENERAL_SHOP = 15002;

    /** One immutable event row. A live NPC is optional; never fabricate object IDs. */
    public static final class ShopEntry {
        public final int notificationId;
        final byte[] name;
        final byte[] npcName;
        final L1NpcInstance npc;

        public ShopEntry(int id, String name, L1NpcInstance npc) {
            this.notificationId = id;
            this.name = name.getBytes(MS949);
            this.npcName = this.name;
            this.npc = npc;
        }

        int liveNpcId() {
            if (npc == null || npc.isDestroyed() || npc.isDead()) return 0;
            final int id = npc.getId();
            return L1World.getInstance().findObject(id) == npc ? id : 0;
        }
    }

	private static int getBitSize(long value) {

		int size = 1;

		while ((value & ~0x7FL) != 0) {

			value >>>= 7;
			size++;
		}

		return size;
	}

    private static CUserSocket connectionOf(L1PcInstance pc) {
        if (pc == null) return null;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return null;
        final CUserSocket connection = socket.getConnection();
        return connection != null && connection.isClientConnected() ? connection : null;
    }

    public static boolean send(L1PcInstance pc, int code, boolean isOpen) {
        final CUserSocket connection = connectionOf(pc);
        if (connection == null) return false;
        final CIOFrame frame = connection.beginDirectFrame(16);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeC(code);
            switch (code) {
            case PCBANG_SET:
                buffer.writeC(0);
                buffer.writeC(8);
                buffer.writeC(isOpen ? 1 : 0);
                buffer.writeC(0x10);
                buffer.writeC(1);
                break;
            case SAFETYZONE:
                buffer.writeC(1);
                buffer.writeC(8);
                buffer.write7B(isOpen ? 128 : 0);
                buffer.writeC(0x10);
                buffer.writeC(0);
                buffer.writeC(0x18);
                buffer.writeC(0);
                break;
            }
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int type, int value) {
        final CUserSocket connection = connectionOf(pc);
        if (connection == null) return false;
        final CIOFrame frame = connection.beginDirectFrame(32);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(type);
            switch (type) {
            case FINISH_PLAY_SUPPORT_ACK:
            case START_PLAY_SUPPORT_ACK:
                buffer.writeC(8);
                buffer.writeBit(value);
                break;
            case TAM:
                buffer.writeC(8);
                buffer.write4bit(value);
                break;
            case CLAN_JOIN_MESSAGE:
                buffer.writeC(8);
                buffer.writeBit(value);
                break;
            }
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    // Older TAM(account) packet uses opcode 180, not the ordinary extended opcode.
    public static boolean sendTamAccount(L1PcInstance pc, L1PcInstance source) {
        final CUserSocket connection = connectionOf(pc);
        if (connection == null) return false;
        final int tam = source != null && source.getAccount() != null
                ? Math.max(0, source.getAccount().getTamPoint()) : 0;
        final CIOFrame frame = connection.beginDirectFrame(24);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(180);
            buffer.writeH(TAM);
            buffer.writeC(8);
            buffer.writeBit(tam);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, String clanname, int rank) {
        final CUserSocket connection = connectionOf(pc);
        if (connection == null) return false;
        final byte[] data = clanname != null ? clanname.getBytes() : null;
        final int capacity = data == null ? 32 : 32 + data.length;
        final CIOFrame frame = connection.beginDirectFrame(capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeC(0x19);
            buffer.writeC(2);
            buffer.writeC(0x0a);
            if (data != null && data.length > 0) {
                buffer.writeC(data.length);
                buffer.writeByte(data);
                buffer.writeC(0x10);
                buffer.writeC(rank);
            } else {
                buffer.writeC(0);
            }
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, byte[] flag) {
        final CUserSocket connection = connectionOf(pc);
        if (connection == null) return false;
        if (flag != null && flag.length > Integer.MAX_VALUE - 32) return false;
        final CIOFrame frame = connection.beginDirectFrame(flag == null ? 32 : 32 + flag.length);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(339);
            if (flag != null) buffer.writeByte(flag);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static final byte[] QUEST_BYTES = parseQuestBytes();
    private static byte[] parseQuestBytes() {
        final StringTokenizer tokens = new StringTokenizer(테스트진행);
        final byte[] result = new byte[tokens.countTokens()];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(tokens.nextToken(), 16);
        }
        return result;
    }

    public static boolean sendQuest(L1PcInstance pc) {
        final CUserSocket connection = connectionOf(pc);
        if (connection == null) return false;
        final CIOFrame frame = connection.beginDirectFrame(QUEST_BYTES.length + 16);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(MONSTER_BOOK_WEEK_QUEST);
            buffer.writeByte(QUEST_BYTES);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static final byte[] MAIN_NAME_BYTES = "orc".getBytes(MS949);
    private static final byte[] EMPTY_BYTES = new byte[0];

    public static boolean sendEvents(L1PcInstance pc, int type, List<EventEntry> events) {
        return sendEvents(pc, type, events, null, null);
    }

    // Pack boss notifications and two shop notifications in the existing 0x008D frame.
    // Optional NPC links carry live world object IDs, not NPC template IDs.
    public static boolean sendEvents(L1PcInstance pc, int type, List<EventEntry> events,
                                     ShopEntry aden, ShopEntry general) {
        final CUserSocket connection = connectionOf(pc);
        if (connection == null || type != EVENT_SYSTEM) return false;
        long capacity = 32;
        byte[][] encodedNames = null;
        if (events != null && !events.isEmpty()) {
            encodedNames = new byte[events.size()][];
            for (int i = 0; i < events.size(); i++) {
                final EventEntry event = events.get(i);
                if (event == null || event.npc == null) continue;
                final String name = event.npc.getNpcId() == 81111
                        ? "붉은 기사단의 진격" : event.npc.getName();
                final byte[] encoded = name == null ? EMPTY_BYTES : name.getBytes(MS949);
                encodedNames[i] = encoded;
                final int bodySize = bossEntrySize(event, encoded);
                capacity += 1L + getBitSize(bodySize) + bodySize;
                if (capacity > 65_535L) return false;
            }
        }
        final long start = Math.max(0L, System.currentTimeMillis() / 1000L - 1L);
        final long end = start + 86_400L;
        final int adenNpc = aden == null ? 0 : aden.liveNpcId();
        final int generalNpc = general == null ? 0 : general.liveNpcId();
        if (aden != null) {
            final int size = shopEntrySize(aden, pc.getId(), start, end, adenNpc);
            capacity += 1L + getBitSize(size) + size;
        }
        if (general != null) {
            final int size = shopEntrySize(general, pc.getId(), start, end, generalNpc);
            capacity += 1L + getBitSize(size) + size;
        }
        if (capacity > 65_535L) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer out = frame.buffer();
            out.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            out.writeH(ProtoMsg.SC_NOTIFICATION_INFO_NOTI);
            out.writeC(8);   // max page count
            out.writeC(1);
            out.writeC(0x10); // current page count
            out.writeC(1);
            if (encodedNames != null) {
                for (int i = 0; i < encodedNames.length; i++) {
                    if (encodedNames[i] == null) continue;
                    final EventEntry e = events.get(i);
                    out.writeC(0x1A);
                    out.writeBit(bossEntrySize(e, encodedNames[i]));
                    out.writeC(8);
                    out.writeBit(e.bossType);
                    out.writeC(0x1A);
                    out.writeBit(MAIN_NAME_BYTES.length);
                    out.writeByte(MAIN_NAME_BYTES);
                    out.writeC(0x22);
                    out.writeBit(encodedNames[i].length);
                    out.writeByte(encodedNames[i]);
                    out.writeC(0x28);
                    out.writeBit(e.startTime);
                    out.writeC(0x30);
                    out.writeBit(e.endTime);
                    out.writeC(0x3A);
                    out.writeC(9);
                    out.writeC(0x0A);
                    out.writeC(4);
                    out.writeC(0x34);
                    out.writeC(0x36);
                    out.writeC(0x35);
                    out.writeC(0x34);
                    out.writeC(0x10);
                    out.writeC(0x90);
                    out.writeC(0x4E);
                }
            }
            if (aden != null) writeShop(out, aden, pc.getId(), start, end, adenNpc);
            if (general != null) writeShop(out, general, pc.getId(), start, end, generalNpc);
            out.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static int bossEntrySize(EventEntry e, byte[] displayName) {
        return 1 + getBitSize(e.bossType) // 1: notification_id
                + 1 + getBitSize(MAIN_NAME_BYTES.length) + MAIN_NAME_BYTES.length // 3: hyperlink
                + 1 + getBitSize(displayName.length) + displayName.length // 4: displaydesc
                + 1 + getBitSize(e.startTime) // 5: startdate
                + 1 + getBitSize(e.endTime) // 6: enddate
                + 1 + 1 + 9; // 7: nested TeleportData (9 bytes)
    }

    private static int npcInfoSize(ShopEntry e, int npcId) {
        return 1 + getBitSize(npcId) + 1 + getBitSize(e.npcName.length) + e.npcName.length;
    }

    private static int eventNpcDataSize(ShopEntry e, int npcId) {
        final int nested = npcInfoSize(e, npcId);
        return 1 + getBitSize(nested) + nested;
    }

    private static int shopEntrySize(ShopEntry e, int pcId, long start, long end, int npcId) {
        int size = 1 + getBitSize(e.notificationId)
                + 1 + getBitSize(pcId)
                + 1 + getBitSize(MAIN_NAME_BYTES.length) + MAIN_NAME_BYTES.length
                + 1 + getBitSize(e.name.length) + e.name.length
                + 1 + getBitSize(start)
                + 1 + getBitSize(end);
        if (npcId != 0) {
            final int nested = eventNpcDataSize(e, npcId);
            size += 1 + getBitSize(nested) + nested;
        }
        return size;
    }

    private static void writeShop(WriteBuffer out, ShopEntry e, int pcId,
                                  long start, long end, int npcId) {
        out.writeC(0x1A); // notificationinfo, field 3
        out.writeBit(shopEntrySize(e, pcId, start, end, npcId));
        out.writeC(0x08); out.writeBit(e.notificationId); // notification_id
        out.writeC(0x10); out.writeBit(pcId); // objectid
        out.writeC(0x1A); out.writeBit(MAIN_NAME_BYTES.length); out.writeByte(MAIN_NAME_BYTES);
        out.writeC(0x22); out.writeBit(e.name.length); out.writeByte(e.name);
        out.writeC(0x28); out.writeBit(start);
        out.writeC(0x30); out.writeBit(end);
        if (npcId != 0) {
            out.writeC(0x42); // EventNpcData, field 8
            out.writeBit(eventNpcDataSize(e, npcId));
            out.writeC(0x0A); // EventNpcInfo, field 1
            out.writeBit(npcInfoSize(e, npcId));
            out.writeC(0x08); out.writeBit(npcId);
            out.writeC(0x12); out.writeBit(e.npcName.length); out.writeByte(e.npcName);
        }
    }

    // 0x008E removes only the shop rows; the existing boss entries are untouched.
    public static boolean removeShopEvents(L1PcInstance pc) {
        final CUserSocket connection = connectionOf(pc);
        if (connection == null) return false;
        final CIOFrame frame = connection.beginDirectFrame(40);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer out = frame.buffer();
            out.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            out.writeH(ProtoMsg.SC_NOTIFICATION_CHANGE_NOTI);
            out.writeC(0x08); out.writeBit(EVENT_ADEN_SHOP);
            out.writeC(0x08); out.writeBit(EVENT_GENERAL_SHOP);
            out.writeC(0x10); out.writeBit(pc.getId());
            out.writeC(0x10); out.writeBit(pc.getId());
            out.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
