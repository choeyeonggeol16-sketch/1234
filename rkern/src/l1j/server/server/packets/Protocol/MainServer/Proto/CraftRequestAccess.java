package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.Craft;

final class CraftRequestAccess {
    static final int MAX_COUNT = 1000;

    private CraftRequestAccess() {}

    static boolean active(CUserSocket session, L1PcInstance pc) {
        return pc != null && session.getActiveChar() == pc && session.ownsReadyGameSession()
                && pc.getOnlineStatus() != 0 && !pc.isDead() && !pc.isGhost() && !pc.get_teleport()
                && !pc.isPrivateShop() && !pc.is무인상점() && !pc.isAutoClanjoin();
    }

    static L1NpcInstance findNpc(int objectId) {
        return CWorld.getInstance().findObject(objectId) instanceof L1NpcInstance npc && !npc.isDead() ? npc : null;
    }

    static boolean inRange(L1PcInstance pc, L1NpcInstance npc) {
        return pc.getMapId() == npc.getMapId()
                && Math.abs((long) pc.getX() - npc.getX()) <= 15
                && Math.abs((long) pc.getY() - npc.getY()) <= 15;
    }

    static Craft recipe(L1NpcInstance npc, int craftId) {
        return npc != null && npc.getNpcTemplate() != null
                && SC_CRAFT_LIST_ALL_ACK.offersRecipe(npc.getNpcId(), craftId) ? Craft.getCraft(craftId) : null;
    }

    static boolean stillAllowed(CUserSocket session, L1PcInstance pc, int stamp,
            L1NpcInstance npc, long npcIdentity, Craft recipe) {
        return active(session, pc) && pc.getSessionStamp() == stamp && npcIdentity != 0
                && CWorld.getInstance().findObjectBySmartId(npcIdentity) == npc && !npc.isDead()
                && inRange(pc, npc) && recipe(npc, recipe.get_id()) == recipe;
    }
}
