"""Exercise the production buy/sell decoder with bounded byte buffers."""
from pathlib import Path
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[2] /
          "src/l1j/server/server/model/network/packet/UserPacketCallbackMap.java").read_text(encoding="utf-8")
decoder = source.split("    static boolean decodeBuySell(", 1)[1].split("    static boolean decodeNpcItemControl(", 1)[0]
decoder = decoder.replace("Disassemble buffer", "ReadBuffer buffer").replace("CUserSocket client", "CIOClient client")
program = r'''
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class DecodeMarketCheck {
    static int calls, checks, rows, kind, firstPresent;
    static long first;
    static byte[] rest;

    static class CIOClient {}
    static class ReadBuffer {
        private final ByteBuffer input;
        ReadBuffer(byte[] bytes, int size) {
            input = ByteBuffer.wrap(bytes, 0, size).slice().order(ByteOrder.LITTLE_ENDIAN);
        }
        int remaining() { return input.remaining(); }
        int readInt(int at) { return input.getInt(at); }
        byte readByte(int at) { return input.get(at); }
        long readLong(int at) { return input.getLong(at); }
        void readBytes(int at, byte[] dest, int start, int len) {
            input.get(at, dest, start, len);
        }
    }

    static void handleBuySell(CIOClient client, int object, int type, int size,
            int hasFirst, long packed, byte[] payload) {
        check(object == 42601);
        calls++;
        kind = type;
        rows = size;
        firstPresent = hasFirst;
        first = packed;
        rest = payload;
    }

    static boolean decodeBuySell(''' + decoder + r'''
    static void check(boolean success) {
        checks++;
        if (!success) throw new AssertionError("check " + checks);
    }

    public static void main(String[] args) {
        for (int count = 1; count <= 255; count++) {
            byte[] bytes = new byte[8 + count * 8 + 4];
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(1, 42601);
            buffer.put(5, (byte) 9);
            buffer.put(6, (byte) count);
            buffer.put(7, (byte) 127);
            for (int i = 0; i < count; i++) {
                buffer.putInt(8 + i * 8, 5000 + i);
                buffer.putInt(12 + i * 8, i == 0 ? 19 : 293 + i);
            }
            for (int length = 0; length < 8 + 8 * count; length++) {
                calls = 0;
                decodeBuySell(new ReadBuffer(bytes, length), null);
                check(calls == 0);
            }
            for (int length : new int[] {8 + 8 * count, bytes.length}) {
                calls = 0;
                decodeBuySell(new ReadBuffer(bytes, length), null);
                check(calls == 1 && rows == count && kind == 9 && firstPresent == 1);
                check(first == ((19L << 32) | 5000));
                check(count == 1 ? rest == null : Arrays.equals(rest, Arrays.copyOfRange(bytes, 16, 8 + count * 8)));
            }
        }
        byte[] close = new byte[12];
        ByteBuffer control = ByteBuffer.wrap(close).order(ByteOrder.LITTLE_ENDIAN);
        control.putInt(1, 42601);
        control.put(5, (byte) 5);
        calls = 0;
        decodeBuySell(new ReadBuffer(close, 7), null);
        check(calls == 1 && rows == 0 && firstPresent == 0);
        control.put(5, (byte) 3);
        control.putInt(8, 9331);
        decodeBuySell(new ReadBuffer(close, 12), null);
        check(calls == 2 && rows == 0 && firstPresent == 1 && first == 9331);
        System.out.println("PASS buy/sell decoder checks=" + checks);
    }
}
'''
with tempfile.TemporaryDirectory() as path:
    file = Path(path, "DecodeMarketCheck.java")
    file.write_text(program, encoding="utf-8")
    subprocess.run(["javac", "-d", path, str(file)], check=True)
    subprocess.run(["java", "-cp", path, "DecodeMarketCheck"], check=True)
