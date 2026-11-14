package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;

/** CS_CRAFT_LIST_ALL_REQ (0x0036) — 클라 hash 접수 → 목록/SAME_HASH 응답 */
public final class CS_CraftListAllReq {
    private CS_CraftListAllReq() {}

    public static void handle(CIOClient client, ProtoReader r) {
        try {
          
            byte[] hash = null;
            while (!r.isAtEnd()) {
                final int tag = r.tag();
                switch (tag) {
                case 0x0A:                       // field1 = hash_val (bytes), tag 0x0A = field1 wire2
                    hash = r.readBytes();
                    break;
                default:
                    r.skip(tag & 7);
                    break;
                }
            }
            if (hash == null) return;
            L1PcInstance pc = client.getActiveChar();
			
            SC_CraftListAllAck.send(pc, hash);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}