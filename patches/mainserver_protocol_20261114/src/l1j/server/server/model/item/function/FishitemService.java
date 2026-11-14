package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Fishing;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.packets.Protocol.Packet.SC_FISHING_NOT;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class FishitemService {

    private FishitemService() {}

    private static final int MAP_TALKING_ISLAND = 4;
    private static final int MAP_FISHING = 5490;

    private static final int TILE_WATER = 28;

    private static final int INV_LIMIT = (180 - 16);
    private static final int WEIGHT_LIMIT_100 = 82;

    private static final int BAIT_ITEM_ID = 41295;

    // =========================================================
    // Rod Spec Registry
    //  - UI 표시(시간/아이콘)만 담당
    // =========================================================
    private static final class RodSpec {
        final int rodId;
        final int durationType; // 0=FIXED_SEC, 1=CFG_TIME1, 2=CFG_TIME2
        final int fixedSec;
        final boolean sendIcon;

        RodSpec(int rodId, int durationType, int fixedSec, boolean sendIcon) {
            this.rodId = rodId;
            this.durationType = durationType;
            this.fixedSec = fixedSec;
            this.sendIcon = sendIcon;
        }

        int seconds() {
            switch (durationType) {
            case 1: return Config.FISH_TIME1;
            case 2: return Config.FISH_TIME2;
            default: return fixedSec;
            }
        }
    }

    // rodId 오름차순 유지
    private static final RodSpec[] RODS = {
    		new RodSpec(41293,   0, 240, false),
    		new RodSpec(87058,   0,  90, true),
    		new RodSpec(87059,   0,  90, true),
    		new RodSpec(600229,  1,   0, true),
    		new RodSpec(4100293, 2,   0, true),
    		new RodSpec(4200293, 2,   0, true),
    };

    private static RodSpec findRodSpec(int rodId) {
        int lo = 0, hi = RODS.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int v = RODS[mid].rodId;
            if (v < rodId) lo = mid + 1;
            else if (v > rodId) hi = mid - 1;
            else return RODS[mid];
        }
        return null;
    }

    // =========================
    // Public API
    // =========================

    public static void clickItem(L1PcInstance pc, int itemId, int fishX, int fishY, L1ItemInstance item, int itemObjectId) {
        if (pc == null || item == null) return;

        if (!preCheckCommon(pc)) return;

        if (pc.getMapId() != MAP_TALKING_ISLAND) {
            SC_ServerMessage.send(pc, 1138);
            return;
        }

        if (tileDistance(pc.getX(), pc.getY(), fishX, fishY) > 13) {
            SC_SystemMessage.send(pc, "조금 더 가까이서 던져주세요.");
            return;
        }

        if (pc.hasSkillEffect(L1SkillId.SHAPE_CHANGE)) {
            SC_SystemMessage.send(pc, "변신중엔 낚시가 불가능합니다.");
            return;
        }
        if (pc.hasSkillEffect(L1SkillId.INVISIBILITY)) {
            SC_SystemMessage.send(pc, "투명상태에서는 낚시를 할 수 없습니다.");
            return;
        }

        if (requiresCharge(itemId) && safeCharge(item) <= 0) return;

        if (!checkInventory(pc)) return;

        final int baseX = 33602, baseY = 33245, px = 5, py = 5;
        if (!inBox(fishX, fishY, baseX, baseY, px, py)) {
            SC_ServerMessage.send(pc, 1138);
            return;
        }

        doStartFishing(pc, fishX, fishY, itemObjectId);
    }

    public static void clickItem1(
            L1PcInstance pc,
            int itemId,
            int fishX,
            int fishY,
            L1ItemInstance item,
            int itemObjectId) {

        if (pc == null || item == null) {
            return;
        }

        if (!preCheckCommon(pc)) {
            return;
        }

        if (pc.getMapId() != MAP_FISHING
                || fishX <= 32704
                || fishX >= 32831
                || fishY <= 32768
                || fishY >= 32895) {

            SC_ServerMessage.send(pc, 1138);
            return;
        }

        if (tileDistance(pc.getX(), pc.getY(), fishX, fishY) > 13) {
            SC_SystemMessage.send(pc, "조금 더 가까이서 던져주세요.");
            return;
        }

        if (requiresCharge(itemId) && safeCharge(item) <= 0) {
            return;
        }

        if (!checkInventory(pc)) {
            return;
        }

        // 실제 낚싯대를 던지는 목적지만 확인한다.
        if (getOriginalTile(MAP_FISHING, fishX, fishY) != TILE_WATER) {
            SC_ServerMessage.send(pc, 1138);
            return;
        }

        doStartFishing(pc, fishX, fishY, itemObjectId);
    }

    // =========================
    // Core
    // =========================

    private static boolean preCheckCommon(L1PcInstance pc) {
        try {
            if (pc.isFishing()) return false;
        } catch (Throwable ignore) {}

        try {
            if (pc.isDead()) return false;
            if (pc.get_teleport()) return false;
        } catch (Throwable ignore) {}

        return true;
    }

    private static boolean checkInventory(L1PcInstance pc) {
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

    private static void doStartFishing(L1PcInstance pc, int fishX, int fishY, int itemObjectId) {
        final L1ItemInstance rod = pc.getInventory().getItem(itemObjectId);
        if (rod == null) {
            SC_ServerMessage.send(pc, 1137);
            return;
        }

        pc._fishingRod = rod;
        final int rodId = rod.getItemId();

        if (requiresCharge(rodId) && safeCharge(rod) <= 0) {
            SC_ServerMessage.send(pc, 1137);
            return;
        }
        
         if (!isBaitlessRodFallback(rodId) && pc.getInventory().countItems(BAIT_ITEM_ID) <= 0) {
             SC_ServerMessage.send(pc, 1137);
             return;
         }

        pc._fishingX = fishX;
        pc._fishingY = fishY;

        SC_Fishing.send(pc, pc.getId(), ActionCodes.ACTION_Fishing, fishX, fishY);
        SC_Fishing.broadcast(pc, pc.getId(), ActionCodes.ACTION_Fishing, fishX, fishY);

        pc.setFishing(true);
        pc.setFishingReady(true);

        applyRodUiOnly(pc, rodId);

        FishingTimeController.getInstance().addMember(pc);
    }

    private static void applyRodUiOnly(L1PcInstance pc, int rodId) {
        RodSpec spec = findRodSpec(rodId);

        final int sec;
        final boolean sendIcon;

        if (spec == null) {
            // 릴류/기타 기본표시: 80초
            sec = 80;
            sendIcon = false;
        } else {
            sec = spec.seconds();
            sendIcon = spec.sendIcon;
        }

        if (sec <= 0) return;

        pc.setFishingTime(System.currentTimeMillis() + (long) sec * 1000L);
        SC_FISHING_NOT.sendCreate(pc, sec);

        if (sendIcon) {
            SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.Fishing_etc, 4497, true);
        }
    }

    // =========================
    // Helpers
    // =========================
    private static boolean requiresCharge(int itemId) {
        return itemId == 41294 || itemId == 41305 || itemId == 41306
                || itemId == 600229 || itemId == 9991
                || itemId == 87058 || itemId == 87059
                || itemId == 4100293;
    }

    private static boolean isBaitlessRodFallback(int rodId) {
        return rodId == 600229 || rodId == 87058 || rodId == 87059
                || rodId == 4100293 || rodId == 4200293;
    }

    private static int safeCharge(L1ItemInstance item) {
        try { return item.getChargeCount(); } catch (Throwable t) { return 0; }
    }

    private static int tileDistance(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2; if (dx < 0) dx = -dx;
        int dy = y1 - y2; if (dy < 0) dy = -dy;
        return (dx > dy) ? dx : dy;
    }

    private static boolean inBox(int x, int y, int cx, int cy, int px, int py) {
        return x >= (cx - px) && x <= (cx + px) && y >= (cy - py) && y <= (cy + py);
    }

    private static int getOriginalTile(int mapId, int x, int y) {
        return L1WorldMap.getInstance()
                .getMap((short) mapId)
                .getOriginalTile(x, y);
    }

}
