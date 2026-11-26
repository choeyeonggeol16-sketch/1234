"""The retired history route must still send the legacy empty response."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / "src/l1j/server/server/model/network/core/sc/packet/SC_ClanWareHouseHistory.java").read_text()
method = source[source.index("    public static void send("):source.rfind("}")]
program = r'''
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
public final class Check {
    static final class Opcodes { static final int S_EVENT = 78; }
    static final class Player {
        int clanId = 1;
        final Session session = new Session(this);
        int getClanid() { return clanId; }
        Session getNetConnection() { return session; }
    }
    static final class Session {
        final Player owner;
        final Socket connection = new Socket();
        Session(Player owner) { this.owner = owner; }
        boolean isConnected() { return true; }
        Player getActiveChar() { return owner; }
        boolean isClientConnected() { return connection.isClientConnected(); }
        Frame beginDirectFrame(int bytes) { return connection.beginDirectFrame(bytes); }
        boolean commitDirectFrame(Frame frame) { return connection.commitDirectFrame(frame); }
        void abortDirectFrame(Frame frame) { connection.abortDirectFrame(frame); }
    }
    static final class Socket {
        int begins, commits, aborts;
        boolean reject;
        final Frame frame = new Frame();
        boolean isClientConnected() { return true; }
        Frame beginDirectFrame(int bytes) {
            if (bytes != 8) throw new AssertionError(bytes);
            begins++;
            frame.bytes.clear();
            return frame;
        }
        boolean commitDirectFrame(Frame frame) { commits++; return !reject; }
        void abortDirectFrame(Frame frame) { aborts++; }
    }
    static final class Frame {
        final ByteBuffer bytes = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
        Frame buffer() { return this; }
        void writeC(int n) { bytes.put((byte) n); }
        void writeD(int n) { bytes.putInt(n); }
    }
''' + method.replace("L1PcInstance", "Player") + r'''
    public static void main(String[] args) {
        var pc = new Player();
        var socket = pc.session.connection;
        send(pc);
        if (socket.begins != 1 || socket.commits != 1 || socket.aborts != 0 ||
                !Arrays.equals(socket.frame.bytes.array(), new byte[]{78, 117, 0, 0, 0, 0}))
            throw new AssertionError("wrong empty response");
        socket.reject = true;
        send(pc);
        if (socket.aborts != 1) throw new AssertionError("frame retained after rejection");
        pc.clanId = 0;
        send(pc);
        if (socket.begins != 2) throw new AssertionError("non-member got a history frame");
        System.out.println("PASS legacy empty clan-history frame and failure cleanup");
    }
}
'''
with tempfile.TemporaryDirectory() as path:
    file = Path(path, "Check.java")
    file.write_text(program, encoding="utf-8")
    subprocess.run(["javac", "-d", path, str(file)], check=True)
    subprocess.run(["java", "-cp", path, "Check"], check=True)
