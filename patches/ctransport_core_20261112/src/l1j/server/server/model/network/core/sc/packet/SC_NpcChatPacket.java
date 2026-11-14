package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_NpcChatPacket {

	public static final int NPC_CHAT_NOMAL_4 = 21;

	private SC_NpcChatPacket() {}

    private static String formatNpcChat(L1NpcInstance npc, String chat, int type) {
        if (npc == null || chat == null) return null;
        switch (type) {
        case 0:
        case 4:
            return npc.getNpcId() == 8500200 ? chat : npc.getName() + ": " + chat;
        case 2:
            int id = npc.getNpcTemplate() != null ? npc.getNpcTemplate().get_npcId() : 0;
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
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final long capacity = 32L + 4L * text.length();
        if (capacity > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
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
        final String message = chat == null ? "" : chat;
        final int objId = cha.getId();
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(source))
            sendFormatted(pc, objId, 0, message);
    }

    public static void broadcastNpcFast(L1Character source, L1NpcInstance npc, String chat, int type) {
        if (source == null || npc == null) return;
        final String formatted = formatNpcChat(npc, chat, type);
        if (formatted == null) return;
        final int color = type == 4 ? NPC_CHAT_NOMAL_4 : type;
        final int npcId = npc.getId();
        final L1World world = L1World.getInstance();
        final java.util.List<L1PcInstance> visible = world.borrowVisiblePlayerFast(source);
        try {
            for (L1PcInstance pc : visible) {
                sendFormatted(pc, npcId, color, formatted);
            }
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }
    }

    public static boolean sendNpc(L1PcInstance pc, L1NpcInstance npc, String chat, int type) {
        if (npc == null) return false;
        return sendFormatted(pc, npc.getId(), type == 4 ? NPC_CHAT_NOMAL_4 : type,
                formatNpcChat(npc, chat, type));
    }

    public static void broadcastNpc(L1Character source, L1NpcInstance npc, String chat, int type) {
        if (source == null || npc == null) return;
        final String formatted = formatNpcChat(npc, chat, type);
        if (formatted == null) return;
        final int color = type == 4 ? NPC_CHAT_NOMAL_4 : type;
        final int npcId = npc.getId();
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(source))
            sendFormatted(pc, npcId, color, formatted);
    }

    public static void broadcastNpcWide(L1Character source, L1NpcInstance npc, String chat, int type) {
        if (source == null || npc == null) return;
        final String formatted = formatNpcChat(npc, chat, type);
        if (formatted == null) return;
        final int color = type == 4 ? NPC_CHAT_NOMAL_4 : type;
        final int npcId = npc.getId();
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(source, 50))
            sendFormatted(pc, npcId, color, formatted);
    }

}
