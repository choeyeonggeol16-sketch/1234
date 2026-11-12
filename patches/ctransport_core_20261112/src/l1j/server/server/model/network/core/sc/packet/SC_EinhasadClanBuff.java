package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;

public final class SC_EinhasadClanBuff {
    private SC_EinhasadClanBuff() {}

    public static boolean send(L1PcInstance receiver, L1PcInstance pc) {
        return receiver != null && send(receiver.getNetConnection(), pc);
    }

    public static boolean send(CIOClient client, L1PcInstance pc) {
        if (client == null || !client.isConnected() || pc == null ) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
        final boolean hasClan = clan != null;
        final int buffFirst = hasClan ? clan.getBuffFirst() : 0;
        final int buffSecond = hasClan ? clan.getBuffSecond() : 0;
        final int buffThird = hasClan ? clan.getBuffThird() : 0;
        final int einhasadBlessBuff = hasClan ? clan.getEinhasadBlessBuff() : 0;
        final CIOFrame frame = connection.beginDirectFrame(48);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();

                    buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
                    buffer.writeC(0xfb);
                    buffer.writeC(0x03);

                    if (!hasClan) {
                        buffer.writeC(0xfb);
                        buffer.writeC(0x03);            
                        buffer.writeC(0x0a); 
                        buffer.writeC(0x00);
                        buffer.writeH(0x00);

                    }

                     else {
            if (buffFirst != 0) {
                        buffer.writeC(0x0a); //넘버 
                        buffer.writeC(0x05); //사이즈
                        buffer.writeC(0x08); //구분자
                        buffer.writeBit(buffFirst); //메세지 넘버
                        buffer.writeC(0x10); //구분자
                        if (einhasadBlessBuff == 0) {
                            buffer.writeC(0x01); //온 오프
                        } else if (einhasadBlessBuff == buffFirst) {
                            buffer.writeC(0x02); //온 오프
                        } else {
                            buffer.writeC(0x03); //온 오프
                        }
                    }

                    if (buffSecond != 0) {
                        buffer.writeC(0x0a);
                        buffer.writeC(0x05);
                        buffer.writeC(0x08);
                        buffer.writeBit(buffSecond);
                        buffer.writeC(0x10);
                        if (einhasadBlessBuff == 0) {
                            buffer.writeC(0x01); //온 오프
                        } else if (einhasadBlessBuff == buffSecond) {
                            buffer.writeC(0x02); //온 오프
                        } else {
                            buffer.writeC(0x03); //온 오프
                        }
                    }

                    if (buffThird != 0) {
                        buffer.writeC(0x0a);
                        buffer.writeC(0x05);
                        buffer.writeC(0x08);
                        buffer.writeBit(buffThird);
                        buffer.writeC(0x10);
                        if (einhasadBlessBuff == 0) {
                            buffer.writeC(0x01); //온 오프
                        } else if (einhasadBlessBuff == buffThird) {
                            buffer.writeC(0x02); //온 오프
                        } else {
                            buffer.writeC(0x03); //온 오프
                        }
                    }

                    buffer.writeC(0x10); //구분자
                    buffer.writeC(0x01); //전체변경 활성화 0이면 비활 1이면 활성

                    buffer.writeH(0x00);

            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

}
