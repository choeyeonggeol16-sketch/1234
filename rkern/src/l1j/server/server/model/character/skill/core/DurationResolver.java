package l1j.server.server.model.character.skill.core;

import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.datatables.MonsterParalyzeDelay.MonsterParalyze;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;

public enum DurationResolver {
    INSTANCE;

    public int resolveDurationMs(L1SkillUse su) {
        if (su == null || su.exSkill() == null) return 0;

        int d;
        if (su.exSkillTimeSec() == 0) {
            int sec = su.exSkill().getBuffDuration();
            d = sec * 1000;

            if (sec == 0) {
                if (su.hdSkillId() == L1SkillId.INVISIBILITY) {
                    try {
                        if (su.hdTarget() != null) su.hdTarget().setSkillEffect(L1SkillId.INVISIBILITY, 0);
                    } catch (Throwable ignore) {}
                }
                return 0;
            }
        } else {
            d = su.exSkillTimeSec() * 1000;
        }

        if (SkillUseHelper.isShockStunDurationSkill(su.hdSkillId())) {
            d = su.exShockStunDurationMs();
        }
        if (su.hdSkillId() == L1SkillId.EARTH_BIND) {
            d = su.exEarthBindDurationMs();
        }

        // 몬스터 마비 딜레이 테이블
        if (MonsterParalyzeDelay.getInstance().contains_paralyze(su.hdSkillId())) {
            MonsterParalyze par = MonsterParalyzeDelay.getInstance().get_paralyze(su.hdSkillId());
            if (par != null) {
                return par.paralyze_delay + par.paralyze_millis;
            }
        }

        return d;
    }
}
