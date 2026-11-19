package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_FollowerPack {
    private SC_FollowerPack() {}

    public static boolean send(L1PcInstance receiver, L1FollowerInstance follower) {
        return receiver != null && send(receiver.getNetConnection(), follower);
    }

    public static boolean send(CIOClient client, L1FollowerInstance follower) {
        if (client == null || !client.isConnected() || follower == null ) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        int x = 0, y = 0, id = 0, spriteId = 0, status = 0, heading = 0;
        int chaLightSize = 0, moveSpeed = 0, poisonStatus = 0, level = 0;
        String nameId = "", title = "";
        try {
            x = follower.getX(); y = follower.getY(); id = follower.getId();
            spriteId = follower.getCurrentSpriteId(); status = follower.getStatus();
            heading = follower.getHeading();
            if (follower.getLight() != null) chaLightSize = follower.getLight().getChaLightSize();
            moveSpeed = follower.getMoveSpeed();
            nameId = follower.getNameId(); title = follower.getTitle();
            level = follower.getLevel();
            if (follower.getPoison() != null && follower.getPoison().getEffectId() == 1) poisonStatus = 1;
        } catch (Exception ignored) {}
        final CIOFrame frame = connection.beginDirectFrame(88 + ((nameId == null ? 0 : nameId.length()) << 1) + ((title == null ? 0 : title.length()) << 1));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();

                    buffer.writeC(Opcodes.S_PUT_OBJECT);
                    buffer.writeH(x);
                    buffer.writeH(y);
                    buffer.writeD(id);
                    buffer.writeH(spriteId);
                    buffer.writeC(status);
                    buffer.writeC(heading);
                    buffer.writeC(chaLightSize);
                    buffer.writeC(moveSpeed);
                    buffer.writeD(0);
                    buffer.writeH(0);
                    buffer.writeS(nameId);
                    buffer.writeS(title);
                    buffer.writeC(poisonStatus);
                    buffer.writeD(0);
                    buffer.writeS(null);
                    buffer.writeS(null);
                    buffer.writeC(0);
                    buffer.writeC(0xFF);
                    buffer.writeC(0);
                    buffer.writeC(level);
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
