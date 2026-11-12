package l1j.server.server.model.network.core.sc.packet;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_LoginResult {
    private SC_LoginResult() {}

public static final int BANNED_REASON_HACK = 95;
	public static final int BANNED_REASON_NOMANNER = 62;
	public static final int BANNED_REASON_COMMERCE = 87;

	public static final Map<Integer, Integer> banServerCodes;
	static {
		Map<Integer, Integer> map = new HashMap<>(8);
		map.put(1, BANNED_REASON_HACK);
		map.put(2, BANNED_REASON_NOMANNER);
		map.put(3, BANNED_REASON_COMMERCE);
		banServerCodes = Collections.unmodifiableMap(map);
	}

	public static final int REASON_LOGIN_OK = 0x00;
	public static final int REASON_ACCOUNT_IN_USE = 0x16;
	public static final int REASON_ACCOUNT_ALREADY_EXISTS = 100;
	public static final int REASON_ACCESS_FAILED = 0x08;
	public static final int REASON_USER_OR_PASS_WRONG = 0x08;
	public static final int REASON_BUG_WRONG = 0x39;
	public static final int REASON_WRONG_ACCOUNT = 0x09;
	public static final int REASON_WRONG_PASSWORD = 0x0A;
	public static final int REASON_BENNED = 62;
	public static final int REASON_MAX_USER = 217;
	public static final int REASON_SUCCESS = 104;

	
    public static boolean send(CIOClient client, int reason) {

        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final CIOFrame frame = conn.beginDirectFrame(16);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_LOGIN_CHECK);
            buffer.writeC(reason);
            buffer.writeD(0);
            buffer.writeD(0);
            buffer.writeD(0);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }

    }
}
