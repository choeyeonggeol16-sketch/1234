package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.ActionCodes;

public final class SC_DoorPack {
    private SC_DoorPack() {}

    public static boolean send(L1PcInstance receiver, L1DoorInstance door) {
        return receiver != null && send(receiver.getNetConnection(), door);
    }

    public static boolean send(CIOClient client, L1DoorInstance door) {
        if (client == null || !client.isConnected() || door == null ) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        int x = 0, y = 0, id = 0, spriteId = 0, statusCode = 0, poisonEffectId = 0;
        try {
            x = door.getX(); y = door.getY(); id = door.getId();
            spriteId = door.getCurrentSpriteId();
            final int maxHp = door.getMaxHp();
            final int doorStatus = door.getStatus();
            final int openStatus = door.getOpenStatus();
            if (door.isDead()) statusCode = doorStatus;
            else if (openStatus == ActionCodes.ACTION_Open) statusCode = openStatus;
            else if (maxHp > 1 && doorStatus != 0) statusCode = doorStatus;
            else statusCode = openStatus;
            if (door.getPoison() != null) poisonEffectId = door.getPoison().getEffectId();
        } catch (Exception ignored) {}
        final CIOFrame frame = connection.beginDirectFrame(96);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();

                    buffer.writeC(Opcodes.S_PUT_OBJECT);
                    buffer.writeH(x);
                    buffer.writeH(y);
                    buffer.writeD(id);
                    buffer.writeH(spriteId);
                    buffer.writeC(statusCode);
                    buffer.writeC(0);
                    buffer.writeC(0);
                    buffer.writeC(0);
                    buffer.writeD(1);
                    buffer.writeH(0);

                    // 문 스프라이트 ID별 명칭 분기 처리
                    if (spriteId == 12164 || spriteId == 12167 || spriteId == 12170 || // 오크요새 외성문
                        spriteId == 12987 || spriteId == 12989 || spriteId == 12991 || // 켄트성 외성문
                        spriteId == 12127 || spriteId == 12129 || spriteId == 12131 || spriteId == 12133) { // 기란성 외성문
                        buffer.writeS("$440");
                    } else if (spriteId == 339 ||    // 켄트성 내성문
                               spriteId == 1336 ||    // 기란성 내성문
                               spriteId == 12163) { // 오크요새 내성문
                        buffer.writeS("$441");
                    } else {
                        buffer.writeS(null);
                    }

                    buffer.writeS(null);

                    int status = 0;
                    if (poisonEffectId == 1) {
                        status |= 1;
                    }
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
