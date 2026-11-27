import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class CoreFrameCheck {
    private static final int BYTES = CoreProtocol.FRAME_BYTES;

    private static Socket connect(int port) throws Exception {
        var socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        socket.setTcpNoDelay(true);
        return socket;
    }

    private static void expectPartial(Socket s) throws Exception {
        s.setSoTimeout(80);
        try {
            int b = s.getInputStream().read();
            throw new AssertionError("reply on partial frame: " + b);
        } catch (SocketTimeoutException expected) {
            s.setSoTimeout(5000);
        }
    }

    private static void exercise(int port, int actor, int count) throws Exception {
        try (var s = connect(port)) {
            byte[][] requests = new byte[count][BYTES];
            int[] bodyHashes = new int[count];
            OutputStream send = s.getOutputStream();
            for (int i = 0; i < count; i++) {
                bodyHashes[i] = CoreProtocol.initializeClientFrame(requests[i], actor, i);
                CoreProtocol.writeRequest(requests[i], actor, i, bodyHashes[i]);
                if (i == 0) {
                    send.write(requests[i], 0, 1);
                    expectPartial(s);
                    send.write(requests[i], 1, 40);
                    expectPartial(s);
                    send.write(requests[i], 41, BYTES - 41);
                } else send.write(requests[i]);
            }
            byte[] reply = new byte[BYTES];
            var in = new DataInputStream(s.getInputStream());
            for (int i = 0; i < count; i++) {
                in.readFully(reply);
                if (!CoreProtocol.validReply(requests[i], reply, actor, i, bodyHashes[i]))
                    throw new AssertionError("response order/content at frame " + i);
            }
            s.shutdownOutput();
            if (in.read() != -1) throw new AssertionError("trailing bytes");
        }
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        exercise(port, 17, 32);
        try (var s = connect(port)) {
            s.getOutputStream().write(new byte[] {2, 1, 49});
            expectPartial(s);
        }
        exercise(port, 23, 12);
        System.out.println("PASS partial header/body, 32-frame ordered pipeline, close, new session sequence");
    }
}
