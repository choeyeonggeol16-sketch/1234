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


    /** Identical S_SAY_CODE payload for all viewers; socket-specific cipher
     * is applied by the existing native FIFO, never to this shared plaintext.
     */
    public static byte[] sharedWire(int objectId,int color,String text) {
        if(text==null)return null;
        final byte[] chars=text.getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=9+chars.length;
        if(size>0xffff)return null;
        final byte[] wire=new byte[size];
        wire[0]=(byte)size;
        wire[1]=(byte)(size>>>8);
        wire[2]=(byte)Opcodes.S_SAY_CODE;
        wire[3]=(byte)color;
        wire[4]=(byte)objectId;
        wire[5]=(byte)(objectId>>>8);
        wire[6]=(byte)(objectId>>>16);
        wire[7]=(byte)(objectId>>>24);
        System.arraycopy(chars,0,wire,8,chars.length);
        return wire;
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
        if(source==null || cha==null)return;
        final String message=chat==null?"":chat;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipients(
                L1World.getInstance().getVisiblePlayer(source),
                sharedWire(cha.getId(),0,message));
    }

    public static void broadcastNpcFast(L1Character source, L1NpcInstance npc, String chat, int type) {
        if(source==null || npc==null)return;
        final String formatted=formatNpcChat(npc,chat,type);
        if(formatted==null)return;
        final int color=type==4?NPC_CHAT_NOMAL_4:type;
        final byte[] wire=sharedWire(npc.getId(),color,formatted);
        final L1World world=L1World.getInstance();
        final java.util.List<L1PcInstance> visible=world.borrowVisiblePlayerFast(source);
        try {
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipients(visible,wire);
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }
    }


    /** Preformat an NPC's message once for identical multi-recipient dialogue.
     * Reuses precisely the normal sendNpc() formatter, object ID and color.
     */
    public static byte[] sharedNpcWire(L1NpcInstance npc,String chat,int type) {
        if(npc==null)return null;
        final String formatted=formatNpcChat(npc,chat,type);
        if(formatted==null)return null;
        return sharedWire(npc.getId(),
                type==4?NPC_CHAT_NOMAL_4:type,formatted);
    }

    public static boolean sendNpc(L1PcInstance pc, L1NpcInstance npc, String chat, int type) {
        if (npc == null) return false;
        return sendFormatted(pc, npc.getId(), type == 4 ? NPC_CHAT_NOMAL_4 : type,
                formatNpcChat(npc, chat, type));
    }

    public static void broadcastNpc(L1Character source, L1NpcInstance npc, String chat, int type) {
        if(source==null || npc==null)return;
        final String formatted=formatNpcChat(npc,chat,type);
        if(formatted==null)return;
        final int color=type==4?NPC_CHAT_NOMAL_4:type;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipients(
                L1World.getInstance().getVisiblePlayer(source),
                sharedWire(npc.getId(),color,formatted));
    }

    public static void broadcastNpcWide(L1Character source, L1NpcInstance npc, String chat, int type) {
        if(source==null || npc==null)return;
        final String formatted=formatNpcChat(npc,chat,type);
        if(formatted==null)return;
        final int color=type==4?NPC_CHAT_NOMAL_4:type;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipients(
                L1World.getInstance().getVisiblePlayer(source,50),
                sharedWire(npc.getId(),color,formatted));
    }

}
