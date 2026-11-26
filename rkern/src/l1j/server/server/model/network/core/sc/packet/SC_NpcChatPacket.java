package l1j.server.server.model.network.core.sc.packet;

import java.util.List;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.templates.L1Npc;

public final class SC_NpcChatPacket {

	public static final int NPC_CHAT_NOMAL_4 = 21;
    private static final int FIXED_WIRE_BYTES = 9;

	private SC_NpcChatPacket() {}

    private static String formatNpcChat(L1NpcInstance npc, String chat, int type) {
        if (npc == null || chat == null) return null;
        final L1Npc template = npc.getNpcTemplate();
        final int id = template == null ? 0 : template.get_npcId();
        switch (type) {
        case 0:
        case 4:
            return id == 8500200 ? chat : npc.getName() + ": " + chat;
        case 2:
            return id == 70518 || id == 70506
                    ? npc.getName() + ": " + chat : "<" + npc.getName() + "> " + chat;
        case 3:
            return "[" + npc.getName() + "] " + chat;
        default:
            return null;
        }
    }


    
    private static boolean sendFormatted(L1PcInstance pc, int npcId, int color, String text) {
        if (pc == null || text == null) return false;
        final CUserSocket connection = pc.getNetConnection();
        if (connection == null || !connection.isConnected() || !connection.isClientConnected()) return false;
        final int capacity = chatCapacity(text);
        if (capacity == 0) return false;
        final CIOFrame frame = connection.beginDirectFrame(capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_SAY_CODE);
            buffer.writeC(color);
            buffer.writeD(npcId);
            buffer.writeS(text);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean sendCharacter(L1PcInstance pc, L1Character cha, String chat) {
        if (cha == null) return false;
        return sendFormatted(pc, cha.getId(), 0, chat == null ? "" : chat);
    }

    public static void broadcastCharacter(L1Character source, L1Character cha, String chat) {
        if (source == null || cha == null) return;
        final NativeWireWriter writer = nativeTextWriter(cha.getId(), 0, chat == null ? "" : chat);
        if (writer != null) CSharedBroadcast.sendWrittenRecipientsBestEffort(
                L1World.getInstance().getVisiblePlayer(source), writer);
    }

    public static void broadcastNpcFast(L1Character source, L1NpcInstance npc, String chat, int type) {
        if (source == null) return;
        final NativeWireWriter writer = nativeNpcWriter(npc, chat, type);
        if (writer == null) return;
        final L1World world = L1World.getInstance();
        final List<L1PcInstance> visible = world.borrowVisiblePlayerFast(source);
        try {
            CSharedBroadcast.sendWrittenRecipientsBestEffort(visible, writer);
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }
    }


    /** Preformat an NPC's message once for identical multi-recipient dialogue.
     * Reuses precisely the normal sendNpc() formatter, object ID and color.
     */
    public static NativeWireWriter nativeNpcWriter(L1NpcInstance npc, String chat, int type) {
        final String formatted = formatNpcChat(npc, chat, type);
        return formatted == null ? null
                : nativeTextWriter(npc.getId(), type == 4 ? NPC_CHAT_NOMAL_4 : type, formatted);
    }

    private static NativeWireWriter nativeTextWriter(int id, int color, String text) {
        if (chatCapacity(text) == 0) return null;
        return out -> {
            out.put((byte) Opcodes.S_SAY_CODE).put((byte) color).putInt(id);
            CSharedBroadcast.writeMS949(out, text);
            out.put((byte) 0);
        };
    }

    private static int chatCapacity(String text) {
        final long maximum = FIXED_WIRE_BYTES + 2L * text.length();
        if (maximum <= CIOBufferPool.MAX_WIRE_BYTES) return (int) maximum;
        final byte[] encoded = ProtoBroadcastFields.encode(text);
        return encoded != null && encoded.length <= CIOBufferPool.MAX_WIRE_BYTES - FIXED_WIRE_BYTES
                ? FIXED_WIRE_BYTES + encoded.length : 0;
    }


    public static boolean sendNpc(L1PcInstance pc, L1NpcInstance npc, String chat, int type) {
        if (npc == null) return false;
        return sendFormatted(pc, npc.getId(), type == 4 ? NPC_CHAT_NOMAL_4 : type,
                formatNpcChat(npc, chat, type));
    }

    public static void broadcastNpc(L1Character source, L1NpcInstance npc, String chat, int type) {
        if (source == null) return;
        final NativeWireWriter writer = nativeNpcWriter(npc, chat, type);
        if (writer != null) CSharedBroadcast.sendWrittenRecipientsBestEffort(
                L1World.getInstance().getVisiblePlayer(source), writer);
    }

    public static void broadcastNpcWide(L1Character source, L1NpcInstance npc, String chat, int type) {
        if (source == null) return;
        final NativeWireWriter writer = nativeNpcWriter(npc, chat, type);
        if (writer != null) CSharedBroadcast.sendWrittenRecipientsBestEffort(
                L1World.getInstance().getVisiblePlayer(source, 50), writer);
    }

}
