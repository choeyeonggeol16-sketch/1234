package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_CharPass {
    private SC_CharPass() {}

public static final int _케릭선택창진입 = 0x40;
	public static final int _케릭선택창진입2 = 0x0a;
	public static final int _케릭선택창진입3 = 0x16;
	public static final int _비번생성창 = 0x17;
	public static final int _비번생성완료창 = 0x11;
	public static final int _비번입력창 = 0x14;
	public static final int _비번입력비번틀림 = 0x15;
	public static final int _비번변경답변 = 0x13;
	public static final int _비번인증완료 = 0x3f;

	
    public static boolean send(CIOClient client, int value) { return send0(client, value, false, false, false, 0, 0); }
    public static boolean send(CIOClient client, int value, boolean check) { return send0(client, value, true, check, false, 0, 0); }
    public static boolean sendFailedPassword(CIOClient client, int failures, int maximum) {
        return send0(client, _비번입력비번틀림, false, false, true, failures, maximum);
    }
    private static boolean send0(CIOClient client, int val, boolean hasCheck, boolean check,
                                 boolean customFail, int failures, int maximum) {
        int extra = 0;
        if (customFail) extra = 12;
        else if (hasCheck) {
            if (val == _비번변경답변 || val == _케릭선택창진입3) extra = (val == _비번변경답변 ? 12 : 16);
        } else {
            switch (val) {
                case _비번입력비번틀림: extra = 12; break;
                case _비번생성완료창: case _케릭선택창진입2: extra = 4; break;
                case _케릭선택창진입3: extra = 16; break;
                default: break;
            }
        }

        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(4 + extra);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_VOICE_CHAT);
            buffer.writeC(val);
            if (customFail) {
                buffer.writeD(0xA5); buffer.writeH(failures); buffer.writeH(maximum); buffer.writeD(0);
            } else if (hasCheck) {
                switch (val) {
                    case _비번변경답변:
                        if (check) { buffer.writeD(0); buffer.writeH(0); buffer.writeH(5); buffer.writeD(0); }
                        else { buffer.writeD(0xA5); buffer.writeH(1); buffer.writeH(5); buffer.writeD(0); }
                        break;
                    case _케릭선택창진입3:
                        if (check) { buffer.writeD(0); buffer.writeH(0); buffer.writeD(5); buffer.writeD(0); buffer.writeH(1); }
                        else { buffer.writeD(170); buffer.writeD(0); buffer.writeD(0); buffer.writeH(0); buffer.writeH(1); }
                        break;
                    default: break;
                }
            } else {
                switch (val) {
                    case _비번입력비번틀림:
                        buffer.writeD(0xA5); buffer.writeH(2); buffer.writeH(5); buffer.writeD(0); break;
                    case _비번생성완료창: buffer.writeD(0); break;
                    case _케릭선택창진입2: buffer.writeD(2); break;
                    case _케릭선택창진입3:
                        buffer.writeD(170); buffer.writeD(0); buffer.writeD(0);
                        buffer.writeH(0); buffer.writeC(1); buffer.writeC(0); break;
                    default: break;
                }
            }
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }

    }

}
