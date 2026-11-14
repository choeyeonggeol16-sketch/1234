import java.lang.reflect.Method;
import java.util.Arrays;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.packets.Protocol.ProtoReader;

/** Actual adapter + Proto/ProtoWriter/WriteBuffer; only the network/PC are fixtures. */
public final class NotificationAdapterContract {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("notification wire contract");
    }
    public static void main(String[] args) throws Exception {
        Method send = Class.forName(args[0]).getMethod("send", L1PcInstance.class,
                int.class, String.class, byte[].class, int.class);
        CUserSocket socket = new CUserSocket();
        L1PcInstance pc = new L1PcInstance(new CIOClient(socket));
        int cases = 0;
        for (int length : new int[] {0, 1, 127, 128, 255}) {
            for (byte[] rgb : new byte[][] {null, {1, 2, 3}, new byte[128]}) {
                String text = "x".repeat(length);
                check((boolean) send.invoke(null, pc, 1, text, rgb, 128));
                byte[] frame = socket.sent;
                check((frame[0] & 255) == 0xb4 && (frame[1] & 255) == 0x3c && frame[2] == 1);
                check(frame[frame.length-1] == 0 && frame[frame.length-2] == 0);
                ProtoReader reader = new ProtoReader(frame, 3, frame.length - 5);
                check(reader.next() == 8 && reader.varint32() == 1);
                check(reader.next() == 18 && reader.readString().equals(text));
                check(reader.next() == 26 && Arrays.equals(reader.readBytes(), rgb == null ? new byte[0] : rgb));
                check(reader.next() == 32 && reader.varint32() == 128 && reader.isAtEnd());
                cases++;
            }
        }
        check(!(boolean) send.invoke(null, pc, 1, "x".repeat(256), null, 60));
        socket.rejectCommit = true;
        check(!(boolean) send.invoke(null, pc, 1, "test", new byte[] {1, 2, 3}, 60));
        check(socket.aborted == 1);
        System.out.println("PASS notification adapter: " + cases + " field-layout cases; length admission and lease rollback");
    }
}
