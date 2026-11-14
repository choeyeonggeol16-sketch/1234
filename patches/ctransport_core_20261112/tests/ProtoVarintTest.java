import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.Stream.ProtoInputStream;

/** Both receiving adapters must reject overflow without consuming another frame. */
public final class ProtoVarintTest {
    static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static byte[] encode(long value) {
        byte[] out = new byte[10]; int n = 0;
        do { int b = (int) value & 127; value >>>= 7; out[n++] = (byte) (b | (value == 0 ? 0 : 128)); } while (value != 0);
        return Arrays.copyOf(out, n);
    }
    static void valid(long value) throws Exception {
        byte[] bytes = encode(value);
        ProtoReader reader = new ProtoReader(bytes, 0, bytes.length);
        ProtoInputStream stream = ProtoInputStream.newInstance(bytes);
        check(reader.varint64() == value && reader.isAtEnd(), "reader valid " + value);
        check(stream.readRawVarInt64() == value && stream.isAtEnd(), "stream valid " + value);
    }
    static void invalid(byte[] bad, int length) throws Exception {
        byte[] framed = new byte[bad.length + 2];
        System.arraycopy(bad, 0, framed, 1, bad.length); framed[framed.length - 1] = 42;
        ProtoReader reader = new ProtoReader(framed, 1, length);
        ProtoInputStream stream = ProtoInputStream.newInstance(framed, 1, 1 + length);
        try { reader.varint64(); throw new AssertionError("reader accepted " + Arrays.toString(bad)); }
        catch (IllegalStateException expected) { }
        try { stream.readRawVarInt64(); throw new AssertionError("stream accepted " + Arrays.toString(bad)); }
        catch (IOException expected) { }
        check(reader.remaining() >= 0 && stream.getCurrentOffset() <= 1 + length, "crossed frame boundary");
        if (bad.length > 10) {
            check(reader.remaining() == length - 10 && stream.getCurrentOffset() == 11, "consumed eleventh byte");
        }
        check(framed[framed.length - 1] == 42, "sibling mutated");
    }
    public static void main(String[] args) throws Exception {
        for (long value : new long[]{0, 1, 127, 128, 16383, 16384, Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, -1}) valid(value);
        Random random = new Random(4917);
        for (int i = 0; i < 100_000; i++) valid(random.nextLong());
        for (int size = 0; size < 10; size++) { byte[] bytes = new byte[size]; Arrays.fill(bytes, (byte) 128); invalid(bytes, size); }
        for (int last = 2; last < 256; last++) {
            byte[] bytes = new byte[11]; Arrays.fill(bytes, 0, 9, (byte) 128); bytes[9] = (byte) last; bytes[10] = 1; invalid(bytes, bytes.length);
        }
        byte[] outside = new byte[10]; Arrays.fill(outside, 0, 9, (byte) 128); outside[9] = 1; invalid(outside, 9);
        System.out.println("PASS both varint64 readers:100011 valid values,265 truncated/overflow/frame-boundary cases;no eleventh-byte consumption");
    }
}
