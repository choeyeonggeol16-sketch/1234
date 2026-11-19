package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_SignboardPack {
    private SC_SignboardPack() {}

    public static boolean send(L1PcInstance receiver, L1SignboardInstance signboard) {
        return receiver != null && send(receiver.getNetConnection(), signboard);
    }

    public static boolean send(CIOClient client, L1SignboardInstance signboard) {
        if (client == null || !client.isConnected() || signboard == null ) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int x = signboard.getX(), y = signboard.getY(), id = signboard.getId();
        final int spriteId = signboard.getCurrentSpriteId();
        final int direction;
        switch (signboard.getHeading()) {
        case 2: direction = 1; break;
        case 3: direction = 2; break;
        case 4: direction = 3; break;
        case 6: direction = 4; break;
        case 7: direction = 5; break;
        default: direction = 0; break;
        }
        final String name = signboard.getName() == null ? "" : signboard.getName();
        int status = signboard.getPoison() != null && signboard.getPoison().getEffectId() == 1 ? 1 : 0;
        final CIOFrame frame = connection.beginDirectFrame(88 + (name.length() << 1));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();

                    buffer.writeC(Opcodes.S_PUT_OBJECT);
                    buffer.writeH(x);
                    buffer.writeH(y);
                    buffer.writeD(id);
                    buffer.writeH(spriteId);
                    buffer.writeC(0);
                    buffer.writeC(direction);
                    buffer.writeC(0);
                    buffer.writeC(0);       
                    buffer.writeD(0);
                    buffer.writeH(0);
                    buffer.writeS(null);
                    buffer.writeS(name);
                    buffer.writeC(status);
                    buffer.writeD(0);
                    buffer.writeS(null);
                    buffer.writeS(null);
                    buffer.writeC(0);
                    buffer.writeC(0xFF);
                    buffer.writeC(0);
                    buffer.writeC(0);
                    buffer.writeC(0);
                    buffer.writeC(0xFF);
                    buffer.writeC(0xFF);
                    buffer.writeC(0);
                    buffer.writeC(0);
                    buffer.writeC(0xFF);
                    buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

}
