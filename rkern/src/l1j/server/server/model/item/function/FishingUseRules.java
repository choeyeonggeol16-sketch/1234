package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

final class FishingUseRules {
    private FishingUseRules() {}
    private static final int INV_LIMIT = (180 - 16);
    private static final int WEIGHT_LIMIT_100 = 82;

    static boolean preCheckCommon(L1PcInstance pc) {
        try {
            if (pc.isFishing()) return false;
        } catch (Throwable ignore) {}

        try {
            if (pc.isDead()) return false;
            if (pc.get_teleport()) return false;
        } catch (Throwable ignore) {}

        return true;
    }

    static boolean checkInventory(L1PcInstance pc) {
        try {
            if (pc.getInventory().getWeight100() > WEIGHT_LIMIT_100) {
                SC_SystemMessage.send(pc, "무게가 너무 무거워서 낚시를 할 수 없습니다.");
                return false;
            }
            if (pc.getInventory().getSize() > INV_LIMIT) { // Controller와 동일(>)
                SC_ServerMessage.send(pc, 263);
                return false;
            }
        } catch (Throwable ignore) {
            return false;
        }
        return true;
    }

    static boolean requiresCharge(int itemId) {
        return itemId == 41294 || itemId == 41305 || itemId == 41306
                || itemId == 600229 || itemId == 9991
                || itemId == 87058 || itemId == 87059
                || itemId == 4100293;
    }

    static int safeCharge(L1ItemInstance item) {
        try { return item.getChargeCount(); } catch (Throwable t) { return 0; }
    }

    static int tileDistance(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2; if (dx < 0) dx = -dx;
        int dy = y1 - y2; if (dy < 0) dy = -dy;
        return (dx > dy) ? dx : dy;
    }

    static boolean inBox(int x, int y, int cx, int cy, int px, int py) {
        return x >= (cx - px) && x <= (cx + px) && y >= (cy - py) && y <= (cy + py);
    }

    static int getOriginalTile(int mapId, int x, int y) {
        return L1WorldMap.getInstance()
                .getMap((short) mapId)
                .getOriginalTile(x, y);
    }
}
