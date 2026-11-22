package l1j.server.server;

import l1j.server.Config;
import l1j.server.server.model.L1Object;

/** Shared same-map, eight-direction tile range check for NPC and shop requests. */
public final class NpcActionRange {
    private NpcActionRange() {}

    public static int get() {
        return Config.NPC_ACTION_RANGE;
    }

    public static boolean isWithin(L1Object source, L1Object target, int range) {
        return source != null && target != null
                && source.getMapId() == target.getMapId()
                && Math.abs((long) source.getX() - target.getX()) <= range
                && Math.abs((long) source.getY() - target.getY()) <= range;
    }
}
