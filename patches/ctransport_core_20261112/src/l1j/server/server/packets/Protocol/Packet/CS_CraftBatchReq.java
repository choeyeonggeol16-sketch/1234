package l1j.server.server.packets.Protocol.Packet;


import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;

/**
 * CS_CRAFT_BATCH_REQ (0x005C) 
 */
public final class CS_CraftBatchReq {
    private CS_CraftBatchReq() {}

    public static void handle(CIOClient client, ProtoReader r) {
        try {
            int npcId = 0, craftId = 0, count = 0;
            boolean hasNpc = false, hasCraft = false, hasCount = false;

            while (!r.isAtEnd()) {
                final int tag = r.tag();
                switch (tag) {
                case 0x08: npcId = r.varint32(); hasNpc = true; break;
                case 0x10: craftId = r.varint32(); hasCraft = true; break;
                case 0x18: count = r.varint32(); hasCount = true; break;
                default: r.skip(tag & 7); break;
                }
            }
            if (!hasNpc || !hasCraft || !hasCount) return;

            final L1PcInstance pc = client.getActiveChar();
            if (pc == null) return;
            SC_CraftBatchAck.send(pc, SC_CraftBatchAck.RP_SUCCESS, SC_CraftListAllAck.BATCH_TRANSACTION++);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}