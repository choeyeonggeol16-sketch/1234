package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_FISHING_NOT;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;

final class FishingRodProfile {
    private FishingRodProfile() {}
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


    static RodSpec findRodSpec(int rodId) {
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

    static void applyRodUiOnly(L1PcInstance pc, int rodId) {
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
}
