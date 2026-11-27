package l1j.server.server.model.item.function;

import java.util.ArrayList;

import l1j.server.server.model.CWorld;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class NearbyItemTargetQueries {
    private NearbyItemTargetQueries() {}

    public static ArrayList<L1NpcInstance> get어드밴스1(L1Object origin, int radius) {
        ArrayList<L1NpcInstance> result = new ArrayList<>();
        for (L1Object candidate : CWorld.getInstance().getVisibleObjects(origin, radius)) {
            if (candidate instanceof L1NpcInstance npc) result.add(npc);
        }
        return result;
    }

    public static ArrayList<L1PcInstance> get어드밴스(L1PcInstance user, L1Object origin, int radius) {
        CWorld world = CWorld.getInstance();
        ArrayList<L1PcInstance> visible = world.getVisiblePlayer(origin, radius);
        if (radius > 0 && visible.isEmpty()
                && world.hasOtherMapPlayer(origin.getMap(), origin instanceof L1PcInstance pc ? pc : null)) {
            String message = "\\fY거리가 멀어서 사용할 수 없습니다.";
            SC_PacketBox.send(user, SC_PacketBox.GREEN_MESSAGE, message);
            SC_SystemMessage.send(user, message);
        }
        return visible;
    }
}
