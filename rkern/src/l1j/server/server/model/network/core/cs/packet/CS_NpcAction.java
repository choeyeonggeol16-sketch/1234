package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CUserSession;

/** Client NPC command entry. Execution belongs to the existing User path. */
public final class CS_NpcAction {
    private CS_NpcAction() {}

    public static void handle(CUserSession client, int objid, String s, String s2, byte[] actionData) {
        User.OnNpcActionPacket(client, objid, s, s2, actionData);
    }

    // Preserve the existing public helpers, including callers outside this source tree.
    public static void 마빈경험치지급2(L1PcInstance pc) {
        NpcExperienceCommands.마빈경험치지급2(pc);
    }

    public static void 마빈경험치지급6(L1PcInstance pc) {
        NpcExperienceCommands.마빈경험치지급6(pc);
    }
}
