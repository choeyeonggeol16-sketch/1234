package l1j.server.server.model.network.core;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.utill.ConnectionState;

/** L1 policy for the original user-socket opcode table/session gate.
 * The supplied L2 opcode IDs, cipher and handler return values are never used.
 * Multiplexed L1 protocols retain their existing per-subcommand validation.
 */
public final class CUserSocketDispatch {
    private CUserSocketDispatch() {}
    public static boolean isSessionOpcode(int opcode) {
        return opcode == (Opcodes.C_LOGIN & 0xFF)
                || opcode == (Opcodes.C_ENTER_WORLD & 0xFF)
                || opcode == (Opcodes.C_CREATE_CUSTOM_CHARACTER & 0xFF)
                || opcode == (Opcodes.C_DELETE_CHARACTER & 0xFF)
                || opcode == (Opcodes.C_RESTART & 0xFF)
                || opcode == (Opcodes.C_QUIT & 0xFF)
                || opcode == (Opcodes.C_LOGOUT & 0xFF)
                || opcode == (Opcodes.C_CHANNEL & 0xFF)
                || opcode == (Opcodes.C_EXTENDED_PROTOBUF & 0xFF)
                || opcode == (Opcodes.C_EXTENDED & 0xFF)
                || opcode == (Opcodes.C_HYPERTEXT_INPUT_RESULT & 0xFF);
    }
    static boolean sessionOwner(int opcode, boolean ready) {
        return !ready || isSessionOpcode(opcode);
    }
    static boolean allowed(int opcode, ConnectionState state, boolean hasAccount,
                           boolean hasCharacter, boolean ready) {
        if (opcode < 0 || opcode > 255 || state == null || state == ConnectionState.DISCONNECTED) return false;
        if (opcode == (Opcodes.C_ALIVE & 255) || opcode == (Opcodes.C_QUIT & 255)
                || opcode == (Opcodes.C_EXTENDED & 255)
                || opcode == (Opcodes.C_EXTENDED_PROTOBUF & 255)
                || opcode == (Opcodes.C_CHANNEL & 255)
                || opcode == (Opcodes.C_HYPERTEXT_INPUT_RESULT & 255)) return true;
        if (opcode == (Opcodes.C_LOGIN & 255))
            return state == ConnectionState.CONNECTED && !hasAccount && !hasCharacter;
        if (opcode == (Opcodes.C_READ_NEWS & 255))
            return !hasCharacter;
        if (opcode == (Opcodes.C_CREATE_CUSTOM_CHARACTER & 255)
                || opcode == (Opcodes.C_DELETE_CHARACTER & 255)
                || opcode == (Opcodes.C_ENTER_WORLD & 255))
            return hasAccount && !hasCharacter && (state == ConnectionState.AUTHENTICATED
                    || state == ConnectionState.OUT_GAME);
        if (opcode == (Opcodes.C_RESTART & 255) || opcode == (Opcodes.C_LOGOUT & 255))
            return hasAccount;
        // L1 game handlers require a fully published active player. Character
        // loading packets use the multiplexed exceptions above.
        return state == ConnectionState.IN_GAME && hasAccount && hasCharacter && ready;
    }
}
