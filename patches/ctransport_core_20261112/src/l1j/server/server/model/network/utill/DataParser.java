package l1j.server.server.model.network.utill;

import java.net.InetAddress;


public final class DataParser {

    private static final long U32_MASK = 0xFFFF_FFFFL;

    private DataParser() {
    }

    public static long parseLong64(char[] buff, int start) {
        return ((buff[3 + start] & 0xFFL) << 24)
                | ((buff[2 + start] & 0xFFL) << 16)
                | ((buff[1 + start] & 0xFFL) << 8)
                | (buff[start] & 0xFFL);
    }

    public static long parseLong64(byte[] buff, int start) {
        return ((buff[3 + start] & 0xFFL) << 24)
                | ((buff[2 + start] & 0xFFL) << 16)
                | ((buff[1 + start] & 0xFFL) << 8)
                | (buff[start] & 0xFFL);
    }

    public static long parseLong64(char[] buff) {
        return parseLong64(buff, 0);
    }

    public static long parseLong64(byte[] buff) {
        return parseLong64(buff, 0);
    }

    public static long parseLong64(long value) {
        return value & U32_MASK;
    }

    public static long parseLong64(int value) {
        return Integer.toUnsignedLong(value);
    }

    public static long parseLong64(long l1, long l2) {
        return Integer.toUnsignedLong((int) l1 + (int) l2);
    }

    public static int parseInt(InetAddress addr) {
        final byte[] b = addr.getAddress();

        return (b[3] & 0xff)
                | ((b[2] << 8) & 0xff00)
                | ((b[1] << 16) & 0xff0000)
                | ((b[0] << 24) & 0xff000000);
    }

   
    public static byte[] parseByteBuff(char[] src) {
        return parseByteBuff(src, 0, new byte[src.length], 0, src.length);
    }

    public static byte[] parseByteBuff(char[] src, byte[] dest) {
        return parseByteBuff(src, 0, dest, 0, dest.length);
    }

    public static byte[] parseByteBuff(
            char[] src,
            int srcPos,
            byte[] dest,
            int destPos,
            int length) {

        for (int i = length - 1; i >= 0; i--) {
            dest[i + destPos] = (byte) (src[i + srcPos] & 0xff);
        }

        return dest;
    }

    public static char[] parseCharBuff(long[] buf) {
        return parseCharBuff(buf, new char[buf.length * 4]);
    }

    public static char[] parseCharBuff(long[] buf, char[] charbuf) {
        for (int i = 0; i < buf.length; ++i) {
            final long value = buf[i];
            final int base = i << 2;

            charbuf[base] = (char) (value & 0xff);
            charbuf[base + 1] = (char) ((value >>> 8) & 0xff);
            charbuf[base + 2] = (char) ((value >>> 16) & 0xff);
            charbuf[base + 3] = (char) ((value >>> 24) & 0xff);
        }
        return charbuf;
    }

    public static char[] parseCharBuff(byte[] src) {
        return parseCharBuff(src, src.length);
    }

    public static char[] parseCharBuff(byte[] src, int size) {
        return parseCharBuff(src, 0, new char[size], 0, size);
    }

    public static char[] parseCharBuff(byte[] src, char[] dest) {
        return parseCharBuff(src, 0, dest, 0, dest.length);
    }

    public static char[] parseCharBuff(
            byte[] src,
            int srcPos,
            char[] dest,
            int destPos,
            int length) {

        for (int i = length - 1; i >= 0; i--) {
            /* 기존 signed-byte -> char 동작을 그대로 유지한다. */
            dest[i + destPos] = (char) src[i + srcPos];
        }

        return dest;
    }

    public static int rotr(int value, int distance) {
        return Integer.rotateRight(value, distance);
    }

    public static int rotr(long value, int distance) {
        return Integer.rotateRight((int) value, distance);
    }
}
