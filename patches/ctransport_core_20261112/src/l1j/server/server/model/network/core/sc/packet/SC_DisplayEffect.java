package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_DisplayEffect {

    private SC_DisplayEffect() {}

	public static final int PUPLE_BORDER_DISPLAY        = 0x01; // 보라색 테두리 디스플레이 효과
	public static final int QUAKE_DISPLAY             = 0x02; // 지진 디스플레이 효과
	public static final int FIREWORK_DISPLAY          = 0x03; // 폭죽 디스플레이 효과
	public static final int RINDVIOR_LIGHT_DISPLAY    = 0x04; // 린드비오르 번개 이펙트 디스플레이 효과
	public static final int LIFECRY_DISPLAY           = 0x05; // 생존의 외침 포만감 이펙트
	public static final int BLUE_BORDER_DISPLAY       = 0x06; // 파란색 테두리 디스플레이 효과
	public static final int VALAKAS_BORDER_DISPLAY    = 0x07; // 발라카스 화염 테두리 디스플레이 효과
	public static final int RINDVIOR_BORDER_DISPLAY   = 0x08; // 린드비오르 푸른 회색 테두리 디스플레이 효과
	public static final int BLACK_DISPLAY             = 0x0A; // 화면 어두워지는 디스플레이 효과

    public static boolean send(L1PcInstance pc, int value) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(16);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x53);
            buffer.writeD(value);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
