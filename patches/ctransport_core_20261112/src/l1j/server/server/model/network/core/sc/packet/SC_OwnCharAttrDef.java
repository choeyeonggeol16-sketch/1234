package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_OwnCharAttrDef {
    private SC_OwnCharAttrDef() {}

    public static boolean send(L1PcInstance pc) {
        int ac = 0, fire = 0, water = 0, wind = 0, earth = 0, dg = 0, er = 0;
        if (pc != null) {
            try { if (pc.getAC() != null) ac = pc.getAC().getAc(); } catch (Throwable ignored) {}
            try { if (pc.getResistance() != null) {
                fire = pc.getResistance().getFire(); water = pc.getResistance().getWater();
                wind = pc.getResistance().getWind(); earth = pc.getResistance().getEarth();
            } } catch (Throwable ignored) {}
            try { dg = pc.getDg(); } catch (Throwable ignored) {}
            try { er = pc.getTotalER(); } catch (Throwable ignored) {}
        }
        return sendRaw(pc == null ? null : pc.getNetConnection(), ac, fire, water, wind, earth, dg, er);
    }
    private static boolean sendRaw(CIOClient client, int ac, int fire, int water, int wind, int earth, int dg, int er) {

        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(17);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_AC);
            buffer.writeD(ac);
            buffer.writeH(fire);
            buffer.writeH(water);
            buffer.writeH(wind);
            buffer.writeH(earth);
            buffer.writeC(dg);
            buffer.writeC(er);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }

    }
}
