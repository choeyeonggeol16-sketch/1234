import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;
import l1j.server.server.packets.Protocol.ProtoReader;

public final class ProtoReaderContract {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("check " + checks);
    }
    private static byte[] varint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((value & ~0x7fL) != 0) {
            out.write((int) value & 0x7f | 0x80);
            value >>>= 7;
        }
        out.write((int) value);
        return out.toByteArray();
    }
    private static ProtoReader reader(byte[] bytes) {
        return new ProtoReader(bytes, 0, bytes.length);
    }
    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException expected) { checks++; return; }
        throw new AssertionError("accepted malformed protobuf");
    }
    public static void main(String[] args) {
        long[] edges = {0, 1, 127, 128, 16383, 16384, Integer.MAX_VALUE,
                        0xffff_ffffL, Long.MAX_VALUE, Long.MIN_VALUE, -1};
        Random random = new Random(0x20191114L);
        for (int i = 0; i < 10011; i++) {
            long value = i < edges.length ? edges[i] : random.nextLong();
            byte[] encoded = varint(value);
            ProtoReader in = reader(encoded);
            check(in.varint64() == value);
            check(in.isAtEnd());
        }
        for (int last : new int[] {2, 127, 128, 129, 255}) {
            byte[] bytes = new byte[11];
            Arrays.fill(bytes, 0, 9, (byte) 0xff);
            bytes[9] = (byte) last;
            bytes[10] = 8; // A following field tag must remain unread.
            ProtoReader in = reader(bytes);
            rejects(in::varint64);
            check(in.remaining() == 1);
        }
        for (int n = 1; n < 10; n++) {
            byte[] bytes = new byte[n];
            Arrays.fill(bytes, (byte) 0x80);
            rejects(reader(bytes)::varint64);
        }
        for (long tag : new long[] {0, 1, 7, 0x1_0000_0008L, -1L}) {
            rejects(reader(varint(tag))::tag);
        }
        check(reader(varint(0xffff_fffdL)).tag() == -3); // Highest field number, fixed32.
        check(reader(varint(8)).tag() == 8);
        rejects(reader(new byte[] {5, 1})::readBytes);
        rejects(reader(varint(0x8000_0000L))::readBytes);
        byte[] paddedZero = new byte[10];
        Arrays.fill(paddedZero, 0, 9, (byte) 0x80);
        check(reader(paddedZero).varint64() == 0);
        check(reader(varint(-1)).varint32() == -1);
        System.out.println("PASS ProtoReader " + checks + " boundary/random assertions");
    }
}
