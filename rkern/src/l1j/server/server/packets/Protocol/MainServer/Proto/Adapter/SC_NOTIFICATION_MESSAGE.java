package l1j.server.server.packets.Protocol.MainServer.Proto.Adapter;

import java.nio.charset.Charset;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.shared.SimpleRgb;

/** Compatibility entry point for survey notifications; shares the proto writer. */
public final class SC_NOTIFICATION_MESSAGE {
    public static final int DISPLAY_POSITION_TOP = 1;
    public static final int DISPLAY_POSITION_MIDDLE = 2;
    public static final int DISPLAY_POSITION_BOTTOM = 3;
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_NOTIFICATION_MESSAGE() {}

    public static boolean send(L1PcInstance pc, int position, String message,
                               SimpleRgb rgb, int durationSecond) {
        return send(pc, position, message, rgb == null ? null : rgb.get_bytes(), durationSecond);
    }

    public static boolean send(L1PcInstance pc, int position, String message,
                               byte[] rgb, int durationSecond) {
        if (pc == null) return false;
        // Preserve the legacy caller's 255 encoded-byte admission limit.
        if (message != null && message.getBytes(MS949).length > 255) return false;
        return l1j.server.server.packets.Protocol.MainServer.Proto.SC_NOTIFICATION_MESSAGE
                .sendFields(pc, position, message, rgb, durationSecond, 0, false);
    }
}
