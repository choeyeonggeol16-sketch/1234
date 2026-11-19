package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_SupportPack {
    private SC_SupportPack() {}

    public static boolean send(L1PcInstance receiver, L1SupportInstance pet) {
        return receiver != null && send(receiver.getNetConnection(), pet);
    }

    public static boolean send(CIOClient client, L1SupportInstance pet) {
        if (client == null || !client.isConnected() || pet == null ) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int x = pet.getX(), y = pet.getY(), id = pet.getId();
        final int spriteId = pet.getCurrentSpriteId(), status = pet.getStatus();
        final int heading = pet.getHeading();
        final int lightSize = pet.getLight() == null ? 0 : pet.getLight().getChaLightSize();
        final int moveSpeed = pet.getMoveSpeed(), level = pet.getLevel();
        final String title = pet.getTitle() == null ? "" : pet.getTitle();
        int poisonAndFastMoveStatus = 0;
        if (pet.getPoison() != null && pet.getPoison().getEffectId() == 1) poisonAndFastMoveStatus |= 1;
        if (pet.isFastMovable()) poisonAndFastMoveStatus |= 64;
        final CIOFrame frame = connection.beginDirectFrame(88 + (title.length() << 1));
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
                    buffer.writeC(lightSize);
                    buffer.writeC(moveSpeed);
                    buffer.writeD(0);
                    buffer.writeH(0);
                    buffer.writeS("");
                    buffer.writeS(title);
                    buffer.writeC(poisonAndFastMoveStatus);
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
