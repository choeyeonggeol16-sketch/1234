package l1j.server.server.model.character.combat.attack;

import l1j.server.server.shared.GameRandom;


import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.character.effect.poison.DamagePoison;
import l1j.server.server.model.character.effect.poison.ParalysisPoison;
import l1j.server.server.model.character.effect.poison.SilencePoison;

/**
 * NPC 독/상태이상 공격 헬퍼
 * - 히트 확정된 뒤(또는 dmg>0) 호출 권장
 * - npcTemplate poisonAtk / paralysisAtk 기반
 */
public final class NpcStatusAttackHelper {

    private NpcStatusAttackHelper() {}

    // poisonAtk types
    private static final int POISON_NORMAL   = 1; // 3초 주기, 5데미지, 30초
    private static final int POISON_SILENCE  = 2; // 침묵독
    private static final int POISON_PARALYZE = 4; // 마비독

    // normal poison params
    private static final int NORMAL_TICK_MS = 3000;
    private static final int NORMAL_DMG     = 5;
    private static final int NORMAL_DUR_MS  = 30000;

    /**
     * NPC의 poisonAtk/paralysisAtk을 시도한다.
     * @param npc      공격 NPC
     * @param attacker 공격자(보통 npc 자신이지만, 호출부 일관성 위해 분리)
     * @param target   타겟
     * @return true면 어떤 상태이상이든 "시도 성공(감염/발동)" 했음
     */
    public static boolean tryApplyNpcStatusAttack(L1NpcInstance npc, L1Character attacker, L1Character target) {
        return tryApplyNpcStatusAttack(npc, attacker, target, 15);
    }

    /**
     * @param chancePct 0~100
     */
    public static boolean tryApplyNpcStatusAttack(L1NpcInstance npc, L1Character attacker, L1Character target, int chancePct) {
        if (npc == null || attacker == null || target == null) return false;

        final int poisonAtk = npc.getNpcTemplate().get_poisonatk();
        if (poisonAtk != 0) {
            if (!rollPct(chancePct)) return false;
            return applyPoisonAtk(poisonAtk, attacker, target);
        }
        return false;
    }

    private static boolean applyPoisonAtk(int poisonAtk, L1Character attacker, L1Character target) {
        switch (poisonAtk) {
            case POISON_NORMAL:
                DamagePoison.doInfection(attacker, target, NORMAL_TICK_MS, NORMAL_DMG, false, NORMAL_DUR_MS);
                return true;

            case POISON_SILENCE:
                SilencePoison.doInfection(target);
                return true;
            case POISON_PARALYZE:
                ParalysisPoison.doInfection(target, 9999);
                return true;

            default:
                return false;
        }
    }

    public static boolean rollPct(int pct) {
        if (pct <= 0) return false;
        if (pct >= 100) return true;
        return GameRandom.nextInt(100) < pct;
    }
}