package l1j.server.server.model.item.cooking;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.cooking.CookingUseService.UseSpec;

/**
 * 실제 데이터는 CookingRegistry 한 곳에만 존재한다.
 */
public final class CookingUseTable {

    private CookingUseTable() {}

    public static UseSpec resolve(int itemId, L1PcInstance pc) {
        return UseSpec.from(CookingRegistry.byItemId(itemId));
    }

    public static UseSpec bySkill(int skillId) {
        return UseSpec.from(CookingRegistry.bySkillId(skillId));
    }

    public static void applyLoginCookingIconAndGroup(
            L1PcInstance pc,
            int skillId,
            int remainSec) {

        CookingRegistry.restoreLogin(pc, skillId, remainSec);
    }
}
