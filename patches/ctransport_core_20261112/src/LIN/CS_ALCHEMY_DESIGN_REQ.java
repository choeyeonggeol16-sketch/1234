package LIN;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.utils.MJHexHelper;


/**
 * CS_ALCHEMY_DESIGN_REQ (0x007A) — 인형합성 설계 요청
 *  1 hash_value bytes [required]
 */
public final class CS_ALCHEMY_DESIGN_REQ {
    private CS_ALCHEMY_DESIGN_REQ() {}

    public static void handle(CIOClient client, ProtoReader r) {
        byte[] hashValue = null;
        boolean hasHash = false;

        try {
            while (!r.isAtEnd()) {
                int tag = r.tag();
                switch (tag) {
                case 0x0A:                       // field 1, LEN
                    hashValue = r.readBytes();
                    hasHash = true;
                    break;
                default:
                    r.skip(tag & 7);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[CS_AlchemyDesignReq] parse fail: " + e);
            return;
        }

        if (!hasHash) return;   // required

        L1PcInstance pc = client.getActiveChar();
        if (pc == null) return;

        if (pc.isGm())
            System.out.println(MJHexHelper.toSourceString(hashValue, hashValue.length));

        LIN.SC_ALCHEMY_EXTRA_INFO_NOTI.send(pc);
        LIN.SC_ALCHEMY_DESIGN_ACK.send(pc, hashValue);
    }
}