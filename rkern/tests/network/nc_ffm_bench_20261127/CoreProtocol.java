import java.nio.ByteBuffer;
import java.util.Arrays;

/** Shared framed protocol for the isolated, synthetic transport comparison. */
final class CoreProtocol {
    static final int PAYLOAD_BYTES = 256;
    static final int FRAME_BYTES = PAYLOAD_BYTES + 2;
    static final int BODY = 20;
    static final int MOVE = 0x31, ACTION = 0x32;
    static final int MOVE_ACK = 0x51, ACTION_ACK = 0x52;

    private CoreProtocol() {}

    /** One full request at index zero. Leave position, limit and byte order unchanged. */
    static void handle(ByteBuffer buffer, int sessionSequence) {
        handleAt(buffer, 0, sessionSequence);
    }

    /** Variant for a frame already in a bounded batch buffer. */
    static void handleAt(ByteBuffer buffer, int offset, int sessionSequence) {
        if (offset < 0 || offset > buffer.limit() - FRAME_BYTES
                || (u(buffer.get(offset)) | u(buffer.get(offset + 1)) << 8) != FRAME_BYTES)
            throw new IllegalArgumentException("Invalid core frame length");
        final int command = u(buffer.get(offset + 2));
        final int sequence = readInt(buffer, offset + 4);
        final int actor = readInt(buffer, offset + 8);
        final int argument = readInt(buffer, offset + 12);
        if (sequence != sessionSequence || actor <= 0 || command != requestOpcode(sequence)
                || buffer.get(offset + 3) != 0 || argument != requestArgument(actor, sequence))
            throw new IllegalArgumentException("Invalid core request metadata");

        int bodyHash = 0x811c9dc5;
        for (int i = offset + BODY; i < offset + FRAME_BYTES; i++)
            bodyHash = (bodyHash ^ u(buffer.get(i))) * 0x01000193;
        if (readInt(buffer, offset + 16) != checksum(bodyHash, command, 0, sequence, actor, argument))
            throw new IllegalArgumentException("Invalid core request checksum");

        final int reply = command == MOVE ? MOVE_ACK : ACTION_ACK;
        final int result = responseArgument(command, actor, sequence, argument);
        final int status = responseStatus(command, result);
        buffer.put(offset + 2, (byte) reply);
        buffer.put(offset + 3, (byte) status);
        writeInt(buffer, offset + 12, result);
        writeInt(buffer, offset + 16, checksum(bodyHash, reply, status, sequence, actor, result));
    }

    /** Prepare a different body per connection and window slot; it is echoed verbatim. */
    static int initializeClientFrame(byte[] frame, int actor, int slot) {
        frame[0] = (byte) FRAME_BYTES;
        frame[1] = (byte) (FRAME_BYTES >>> 8);
        int state = actor * 0x9e3779b9 ^ (slot + 1);
        int hash = 0x811c9dc5;
        for (int i = BODY; i < FRAME_BYTES; i++) {
            state = state * 1664525 + 1013904223;
            int next = state >>> 24;
            frame[i] = (byte) next;
            hash = (hash ^ next) * 0x01000193;
        }
        return hash;
    }

    static void writeRequest(byte[] frame, int actor, int sequence, int bodyHash) {
        final int command = requestOpcode(sequence);
        final int argument = requestArgument(actor, sequence);
        frame[2] = (byte) command;
        frame[3] = 0;
        writeInt(frame, 4, sequence);
        writeInt(frame, 8, actor);
        writeInt(frame, 12, argument);
        writeInt(frame, 16, checksum(bodyHash, command, 0, sequence, actor, argument));
    }

    static boolean validReply(byte[] request, byte[] response, int actor, int sequence, int bodyHash) {
        final int command = requestOpcode(sequence);
        final int result = responseArgument(command, actor, sequence, requestArgument(actor, sequence));
        final int opcode = command == MOVE ? MOVE_ACK : ACTION_ACK;
        final int status = responseStatus(command, result);
        return response[0] == request[0] && response[1] == request[1]
                && u(response[2]) == opcode && u(response[3]) == status
                && readInt(response, 4) == sequence && readInt(response, 8) == actor
                && readInt(response, 12) == result
                && readInt(response, 16) == checksum(bodyHash, opcode, status, sequence, actor, result)
                && Arrays.equals(request, BODY, FRAME_BYTES, response, BODY, FRAME_BYTES);
    }

    private static int requestOpcode(int sequence) {
        return (sequence & 1) == 0 ? MOVE : ACTION;
    }

    private static int requestArgument(int actor, int sequence) {
        return Integer.rotateLeft(actor * 0x045d9f3b, sequence & 31) ^ (sequence * 31);
    }

    private static int responseArgument(int command, int actor, int sequence, int request) {
        if (command == MOVE) {
            int x = Math.clamp((request & 1023) - 20, 0, 999);
            int y = Math.clamp(((request >>> 10) & 1023) + 20, 0, 999);
            return y << 16 | x;
        }
        int target = request & 0x3fff;
        int amount = 15 + ((request >>> 16 ^ actor ^ sequence) & 255);
        return target << 16 | amount;
    }

    private static int responseStatus(int command, int result) {
        return command == MOVE ? 1 : ((result & 0xffff) > 127 ? 3 : 2);
    }

    private static int checksum(int bodyHash, int opcode, int status, int sequence, int actor, int argument) {
        int hash = Integer.rotateLeft(bodyHash ^ sequence, 7) + actor * 31;
        return Integer.rotateLeft(hash ^ argument, 11) + (opcode << 8) + status;
    }

    private static int u(byte value) { return value & 255; }

    private static int readInt(ByteBuffer b, int i) {
        return u(b.get(i)) | u(b.get(i + 1)) << 8 | u(b.get(i + 2)) << 16 | u(b.get(i + 3)) << 24;
    }

    private static int readInt(byte[] b, int i) {
        return u(b[i]) | u(b[i + 1]) << 8 | u(b[i + 2]) << 16 | u(b[i + 3]) << 24;
    }

    private static void writeInt(ByteBuffer b, int i, int value) {
        b.put(i, (byte) value);
        b.put(i + 1, (byte) (value >>> 8));
        b.put(i + 2, (byte) (value >>> 16));
        b.put(i + 3, (byte) (value >>> 24));
    }

    private static void writeInt(byte[] b, int i, int value) {
        b[i] = (byte) value;
        b[i + 1] = (byte) (value >>> 8);
        b[i + 2] = (byte) (value >>> 16);
        b[i + 3] = (byte) (value >>> 24);
    }
}
