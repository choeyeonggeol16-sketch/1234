package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.L1NpcHtml;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_NPCTalkReturn {

    // Direct async I/O path: no packet object or defensive String[] clone.
    public static boolean send(L1PcInstance pc, int objId, String htmlId) {
        return send(pc, objId, htmlId, (String[]) null);
    }

    public static boolean send(L1PcInstance pc, int objId, String htmlId, String[] data) {
        return sendRaw(pc, objId, htmlId, data, null, Opcodes.S_HYPERTEXT, false);
    }

    public static boolean send(L1PcInstance pc, int objId, String htmlId, String[] data, int opcode) {
        return sendRaw(pc, objId, htmlId, data, null, opcode, true);
    }

    public static boolean sendList(L1PcInstance pc, int objId, String htmlId, java.util.List<String> data) {
        return sendRaw(pc, objId, htmlId, null, data, Opcodes.S_HYPERTEXT, false);
    }

    public static boolean send(L1PcInstance pc, int objId, L1NpcHtml html) {
        return send(pc, objId, html == null ? "" : html.getName(), html == null ? null : html.getArgs());
    }

    public static boolean send(L1PcInstance pc, int objId, L1NpcHtml html, String[] data) {
        return send(pc, objId, html == null ? "" : html.getName(), data);
    }

    public static boolean send(L1PcInstance pc, L1NpcTalkData npc, int objId, int action) {
        return send(pc, npc, objId, action, null);
    }

    public static boolean send(L1PcInstance pc, L1NpcTalkData npc, int objId, int action, String[] data) {
        if (npc == null) throw new IllegalArgumentException("L1NpcTalkData is null");
        if (action == 1) return send(pc, objId, npc.getNormalAction(), data);
        if (action == 2) return send(pc, objId, npc.getCaoticAction(), data);
        throw new IllegalArgumentException("Invalid action type: " + action);
    }

    private static boolean sendRaw(L1PcInstance pc, int objId, String htmlId, String[] array,
                                   java.util.List<String> list, int opcode, boolean mode2) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final String name = htmlId == null ? "" : htmlId;
        final int count = array != null ? array.length : list != null ? list.size() : 0;
        long size = 96L + (long) name.length() * 4L + count * 8L;
        for (int i = 0; i < count; i++) {
            final String item = array != null ? array[i] : list.get(i);
            if (item != null) size += (long) item.length() * 4L;
        }
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(opcode);
            buffer.writeD(objId);
            buffer.writeS(name);
            if (count > 0) {
                buffer.writeH(1);
                buffer.writeH(count);
                for (int i = 0; i < count; i++) {
                    final String item = array != null ? array[i] : list.get(i);
                    buffer.writeS(item == null ? "" : item);
                }
            } else if (mode2) {
                buffer.writeH(1);
                buffer.writeH(1);
            } else {
                buffer.writeH(0);
                buffer.writeH(0);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private SC_NPCTalkReturn() {}
}
