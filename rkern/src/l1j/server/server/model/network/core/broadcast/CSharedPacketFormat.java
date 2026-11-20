package l1j.server.server.model.network.core.broadcast;

import java.nio.ByteBuffer;

/** L1 payload layouts used by the shared action, movement and combat packets. */
public final class CSharedPacketFormat {
    private CSharedPacketFormat() { }

    public static void writeFormatted(ByteBuffer out, int kind, int opcode,
            int objectId, int a, int b, int c) {
        switch (kind) {
            case 1 -> out.put((byte) opcode).putInt(objectId).put((byte) a);
            case 2 -> out.put((byte) opcode).putInt(objectId).putShort((short) a).putShort((short) b)
                .put((byte) c).put((byte) 129).putInt(0);
            case 3 -> out.put((byte) opcode).putInt(objectId).put((byte) a);
            case 4 -> out.put((byte) opcode).put((byte) 0).putShort((short) objectId);
            case 5, 6 -> out.put((byte) opcode).putInt(objectId).putShort((short) (kind==5?a:0));
            case 7 -> out.put((byte) opcode).putInt(objectId).putShort((short) a).putShort((short) 0)
                .putInt(0);
            case 8 -> out.put((byte) opcode).putShort((short) objectId).putShort((short) a).putShort((short) b)
                .put((byte) 0);
            case 9, 13 -> out.put((byte) opcode).putInt(objectId).put((byte) a);
            case 10 -> out.put((byte) opcode).putInt(objectId).put((byte) (a==1?1:0)).put((byte) (a==2?1:0));
            case 11 -> out.put((byte) opcode).putInt(objectId).put((byte) a).put((byte) b)
                .putShort((short) 0);
            case 12 -> out.put((byte) opcode).putInt(objectId).put((byte) a).put((byte) b)
                .put((byte) 0);
            case 14 -> out.put((byte) opcode).putInt(objectId).put((byte) a).putShort((short) b)
                .putShort((short) c).putInt(0).putShort((short) 0);
            case 15 -> out.put((byte) opcode).putShort((short) objectId).putShort((short) a).put((byte) b)
                .put((byte) c);
            case 16 -> out.put((byte) opcode).putInt(objectId).putShort((short) a).putInt(0);
            case 17, 18 -> out.put((byte) opcode).putInt(objectId).put((byte) a);
            case 19, 20 -> out.put((byte) opcode).putInt(objectId).put((byte) a).putShort((short) b);
            case 21, 25 -> out.put((byte) opcode).put((byte) objectId).put((byte) a).putShort((short) b);
            case 22 -> out.put((byte) opcode).putInt(objectId).putShort((short) a);
            case 23 -> out.put((byte) opcode).putShort((short) objectId).put((byte) a).putInt(0);
            case 24 -> out.put((byte) opcode).put((byte) 0x2c).putInt(objectId).put((byte) 0)
                .put((byte) 0x0a).put((byte) 0x80);
            case 26 -> out.put((byte) opcode).putInt(objectId).put((byte) a).putInt(b)
                .putInt(c);
            case 27 -> out.put((byte) opcode).putShort((short) objectId).put((byte) a).put((byte) b)
                .putInt(0);
            case 28 -> out.put((byte) opcode).putShort((short) objectId).put((byte) a).put((byte) b)
                .put((byte) c).putInt(0);
            case 29 -> out.put((byte) opcode).putInt(objectId).put((byte) a).put((byte) 0xff)
                .put((byte) 0xff).putShort((short) 0);
            case 30 -> out.put((byte) opcode).put((byte) 0xc2).putInt(objectId).put((byte) 0x4f)
                .put((byte) 0x33).put((byte) 0).put((byte) 0).putInt(a)
                .putShort((short) 0);
            default -> throw new IllegalArgumentException("Unknown shared packet format " + kind);
        }
    }

    public static void writeExtended(ByteBuffer out, int kind, int opcode,
            int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8, int p9, int p10) {
        switch (kind) {
            case 31 -> out.put((byte) opcode).putInt(p0).putShort((short) p1).put((byte) p2)
                .put((byte) 0xff).put((byte) 0xff).put((byte) 0).put((byte) 97)
                .put((byte) 98).put((byte) 99).put((byte) 100).put((byte) 0);
            case 32 -> out.put((byte) opcode).put((byte) p2).putInt(p0).putInt(p1)
                .put((byte) 0).put((byte) 0).put((byte) p3).putInt(0)
                .put((byte) 0);
            case 33 -> out.put((byte) opcode).put((byte) p0).putInt(p1).putInt(p2)
                .putShort((short) 1).put((byte) p3).putShort((short) 0).putShort((short) 0)
                .put((byte) 0);
            case 34 -> out.put((byte) opcode).put((byte) p3).putInt(p0).putInt(p2)
                .put((byte) 1).put((byte) 0).put((byte) p1).putInt(0);
            case 35 -> out.put((byte) opcode).put((byte) 1).putInt(p0).putInt(p1)
                .putShort((short) p2).put((byte) p3).putInt(p4).putShort((short) p5)
                .put((byte) 0).putShort((short) p6).putShort((short) p7).putShort((short) p8)
                .putShort((short) p9).putInt(0).putShort((short) 0).put((byte) 0)
                .putShort((short) 0);
            case 36 -> out.put((byte) opcode).put((byte) 1).putInt(p0).putInt(p1)
                .putShort((short) 1).put((byte) p2).putShort((short) 0).putShort((short) 0)
                .put((byte) 2).putInt(p3).putShort((short) 0);
            case 37 -> out.put((byte) opcode).put((byte) p3).putInt(p0).putInt(p1)
                .put((byte) (p4 != 0 ? 6 : 0)).put((byte) 0).put((byte) p2).putInt(p5)
                .putShort((short) p6).put((byte) 0).putShort((short) p7).putShort((short) p8)
                .putShort((short) p9).putShort((short) p10).put((byte) 0).put((byte) 0)
                .put((byte) 0).put((byte) 0);
            case 38 -> out.put((byte) opcode).put((byte) p3).putInt(p2).putInt(p1)
                .put((byte) 0x4e).put((byte) 0).put((byte) p4).putInt(0)
                .put((byte) 0);
            case 39 -> out.put((byte) opcode).put((byte) p3).putInt(p1).putInt(p2)
                .putShort((short) 2).put((byte) p4).putShort((short) 0).putShort((short) 0)
                .put((byte) p5);
            case 40 -> out.put((byte) opcode).put((byte) p3).putInt(p1).putInt(p2)
                .put((byte) 1).put((byte) 0).put((byte) p4).putShort((short) 0)
                .putShort((short) 0).put((byte) p5).putShort((short) p6).putShort((short) 0)
                .putShort((short) 0);
            case 41 -> out.put((byte) opcode).put((byte) p4).putInt(p0).putInt(p5)
                .put((byte) (p9 != 0 ? 6 : 0)).put((byte) 0).put((byte) p3).putInt(p10)
                .putShort((short) p6).put((byte) 0).putShort((short) p1).putShort((short) p2)
                .putShort((short) p7).putShort((short) p8).put((byte) 0).put((byte) 0)
                .put((byte) 0).put((byte) 0).put((byte) 0);
            case 42 -> out.put((byte) opcode).put((byte) p0).putInt(p1).putInt(p2)
                .put((byte) p3).put((byte) 0).put((byte) p4).putInt(p5)
                .putShort((short) p6).put((byte) 6).putShort((short) p7).putShort((short) p8)
                .putShort((short) p9).putShort((short) p10).put((byte) 0).put((byte) 0)
                .put((byte) 0).put((byte) 0).put((byte) 0);
            default -> throw new IllegalArgumentException("Unknown shared packet format " + kind);
        }
    }
}
