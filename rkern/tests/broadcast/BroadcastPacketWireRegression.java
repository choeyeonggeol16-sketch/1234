package l1j.server.server.model.network.core.sc.packet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_NOTIFICATION_MESSAGE_NOT;

public final class BroadcastPacketWireRegression {
    private static final Charset MS949 = Charset.forName("MS949");
    private static final byte[] GM_COLOR = {(byte) 249, 68, (byte) 253};

    public static void main(String[] args) {
        verifyChatLengths();
        verifyKillNotice();
        verifyClanAndNpc();
        System.out.println("PASS broadcast packet wire regression");
    }

    private static void verifyChatLengths() {
        for (String text : List.of("x".repeat(127), "가".repeat(64),
                "x".repeat(256), "x".repeat(20_000))) {
            byte[] message = text.getBytes(MS949);
            String target = "나".repeat(80);
            byte[] targetBytes = target.getBytes(MS949);

            ByteBuffer whisper = encode(SC_NewChat.nativeWriter(SC_NewChat.prepare(null, 3, 1, text, target)));
            header(whisper, 0x203);
            intField(whisper, 0x08, 0);
            intField(whisper, 0x10, 1);
            bytesField(whisper, 0x1a, message);
            bytesField(whisper, 0x22, targetBytes);
            intField(whisper, 0x28, 0);
            intField(whisper, 0x30, 0);
            trailer(whisper);

            ByteBuffer macro = encode(SC_ChatMessageNoti.nativeMacroWriter(0x11, text, target));
            header(macro, 0x204);
            intField(macro, 0x08, 0);
            intField(macro, 0x10, 0x11);
            bytesField(macro, 0x1a, message);
            bytesField(macro, 0x2a, targetBytes);
            intField(macro, 0x30, 0);
            trailer(macro);

            byte[] notification = SC_NOTIFICATION_MESSAGE_NOT.encodeFields(28, text, GM_COLOR, 60);
            require(encode(SC_IconMessage.nativeGmWriter(text)).equals(ByteBuffer.wrap(notification)),
                    "GM icon matches notification protocol");
        }

        String oversized = "x".repeat(65_535);
        require(SC_NewChat.nativeWriter(SC_NewChat.prepare(null, 4, 3, oversized, "")) == null,
                "world chat rejects oversized frames");
        require(SC_ChatMessageNoti.nativeMacroWriter(0x11, oversized, "") == null,
                "macro chat rejects oversized frames");
        require(SC_IconMessage.nativeGmWriter(oversized) == null, "icon rejects oversized frames");
    }

    private static void verifyKillNotice() {
        byte[] killer = SC_ShowCmd.encode("가".repeat(65));
        byte[] victim = SC_ShowCmd.encode("x".repeat(256));
        byte[] expectedKiller = killer.clone();
        NativeWireWriter writer = SC_ShowCmd.nativePkWriter(killer, victim);
        killer[0] = 0;

        ByteBuffer notice = encode(writer);
        header(notice, 0x247);
        intField(notice, 0x08, 0);
        bytesField(notice, 0x12, expectedKiller);
        intField(notice, 0x18, 0);
        bytesField(notice, 0x22, victim);
        intField(notice, 0x28, 1);
        trailer(notice);
    }

    private static void verifyClanAndNpc() {
        ByteBuffer clan = encode(SC_ClanAttention.nativeListWriter(9, List.of("alpha", "beta")));
        require(Byte.toUnsignedInt(clan.get()) == Opcodes.S_PLEDGE_WATCH && clan.getShort() == 2
                && clan.getInt() == 2, "clan packet advertises its actual name count");

        L1NpcInstance npc = new L1NpcInstance(null);
        npc.setId(42);
        npc.setName("testnpc");
        ByteBuffer dialogue = encode(SC_NpcChatPacket.nativeNpcWriter(npc, "hello", 0));
        require(Byte.toUnsignedInt(dialogue.get()) == Opcodes.S_SAY_CODE
                && dialogue.get() == 0 && dialogue.getInt() == 42,
                "NPC without a template produces dialogue");
    }

    private static ByteBuffer encode(NativeWireWriter writer) {
        require(writer != null, "valid packet has a writer");
        ByteBuffer wire = ByteBuffer.allocate(65_535).order(ByteOrder.LITTLE_ENDIAN);
        wire.position(2);
        writer.write(wire);
        return wire.flip().position(2);
    }

    private static void header(ByteBuffer wire, int messageId) {
        require(Byte.toUnsignedInt(wire.get()) == Opcodes.S_EXTENDED_PROTOBUF
                && Short.toUnsignedInt(wire.getShort()) == messageId, "extended message header");
    }

    private static void intField(ByteBuffer wire, int tag, int expected) {
        require(Byte.toUnsignedInt(wire.get()) == tag && varint(wire) == expected, "integer field " + tag);
    }

    private static void bytesField(ByteBuffer wire, int tag, byte[] expected) {
        require(Byte.toUnsignedInt(wire.get()) == tag, "bytes field " + tag);
        require(varint(wire) == expected.length, "encoded field length " + tag);
        byte[] actual = new byte[expected.length];
        wire.get(actual);
        require(Arrays.equals(expected, actual), "field contents " + tag);
    }

    private static int varint(ByteBuffer wire) {
        int value = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int next = Byte.toUnsignedInt(wire.get());
            value |= (next & 0x7f) << shift;
            if ((next & 0x80) == 0) {
                require(shift == 0 || next != 0, "canonical varint length");
                return value;
            }
        }
        throw new AssertionError("unterminated varint");
    }

    private static void trailer(ByteBuffer wire) {
        require(wire.getShort() == 0 && !wire.hasRemaining(), "message trailer");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
