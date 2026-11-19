package l1j.server.server.model.character.Attack.Helper;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;

public final class TargetSizeHelper {

    private TargetSizeHelper() {}

    /**
     * 타겟이 SMALL인지 판정.
     * - PVP(PC 대상)면 무조건 SMALL
     * - NPC면 템플릿 size가 "large"면 LARGE, 그 외(unknown/null 포함)는 SMALL
     */
    public static boolean isSmall(final boolean pvp, final L1Character target) {
        if (pvp || target == null) return true;
        if (!(target instanceof L1NpcInstance)) return true;

        final L1NpcInstance npc = (L1NpcInstance) target;
        final String sz = (npc.getNpcTemplate() != null) ? npc.getNpcTemplate().get_size() : null;
        return (sz == null) || !sz.trim().equalsIgnoreCase("large");
    }
}
