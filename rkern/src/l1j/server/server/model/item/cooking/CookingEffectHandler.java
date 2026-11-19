package l1j.server.server.model.item.cooking;

import l1j.server.server.model.Instance.L1PcInstance;

/**
 * 실제 효과 정의/적용은 CookingRegistry가 담당한다.
 */
public final class CookingEffectHandler {

    private CookingEffectHandler() {}

    public static boolean isCookingSkill(int skillId) {
        return CookingRegistry.containsSkill(skillId);
    }

    public static void onStart(L1PcInstance pc, int skillId) {
        CookingRegistry.ensureStarted(pc, skillId);
    }

    public static void onEnd(L1PcInstance pc, int skillId) {
        CookingRegistry.end(pc, skillId);
    }

    public static void clearRuntimeState(L1PcInstance pc) {
        CookingRegistry.clearRuntimeState(pc);
    }
}
