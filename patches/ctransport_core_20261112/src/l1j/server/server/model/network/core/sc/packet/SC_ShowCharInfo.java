package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.MJCTSystem.MJCTObject;

public final class SC_ShowCharInfo {
    private SC_ShowCharInfo() {}

    public static boolean send(L1PcInstance receiver, MJCTObject obj) {
        return receiver != null && send(receiver.getNetConnection(), obj);
    }

    public static boolean send(CIOClient client, MJCTObject obj) {
        if (client == null || !client.isConnected() || obj == null ) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int number = 1;
        final String name = obj.name == null ? "" : obj.name;
        final String className = (obj.classn == 0 || obj.classn == 1) ? "군주" :
                (obj.classn == 61 || obj.classn == 48) ? "기사" : "준비중";
        final StringBuilder sb = new StringBuilder();
        sb.append("이름 :").append(name)
          .append("\n레벨 :").append(obj.level)
          .append("\n클래스 : ").append(className)
          .append("\nStr :").append(obj.value1)
          .append("\nDex :").append(obj.dex)
          .append("\nCon :").append(obj.con)
          .append("\nWis :").append(obj.wis)
          .append("\nInt :").append(obj.ints)
          .append("\nCha :").append(obj.cha)
          .append("\nHp :").append(obj.hp)
          .append("\nMp :").append(obj.mp);
        final String content = sb.toString();
        final CIOFrame frame = connection.beginDirectFrame(38 + ((name.length() + content.length()) << 1));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();

                    buffer.writeC(Opcodes.S_BOARD_READ);
                    buffer.writeD(number);
                    buffer.writeS("캐릭터 저장 구슬");
                    buffer.writeS(name);
                    buffer.writeS("");
                    buffer.writeS(content);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

}
