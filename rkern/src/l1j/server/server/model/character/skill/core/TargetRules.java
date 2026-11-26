
package l1j.server.server.model.character.skill.core;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Instance.L1SupportInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.L1SkillUse.FailReason;
import l1j.server.server.templates.L1Npc;

public enum TargetRules {
    INSTANCE;

    public boolean isTargetCalc(L1SkillUse su, L1Character cha) {
        if (su == null || cha == null) return false;

        final int skillId = su.hdSkillId();
        final String tgt = (su.exSkill() != null ? su.exSkill().getTarget() : null);

        if ("attack".equals(tgt) && skillId != L1SkillId.TURN_UNDEAD) {
            if (isPcSummonPet(su, cha)) {
                final L1PcInstance p = su.hdPlayer();
                if (p != null) {
                    if (p.getZoneType() == 1 || cha.getZoneType() == 1 || p.checkNonPvP(p, cha)) {
                        return false;
                    }
                }
            }
        }

        if (skillId == L1SkillId.FOG_OF_SLEEPING) {
            final L1Character u = su.hdUser();
            if (u != null && u.getId() == cha.getId()) return false;
        }

        final int userId = (su.hdUser() != null ? su.hdUser().getId() : 0);

        if (cha instanceof L1CompanionInstance) {
            if (userId == ((L1CompanionInstance) cha).get_master_id()) return false;
        }

        if (skillId == L1SkillId.MASS_TELEPORT) {
            return (su.hdUser() != null && userId == cha.getId());
        }

        return true;
    }

    public boolean isTargetFailure(L1SkillUse su, L1Character cha) {
        if (su == null || cha == null) return true;

        final int sid = su.hdSkillId();

        // TU는 "언데드 몬스터"만 허용. 몬스터 아니면 전부 실패 처리.
        if (sid == L1SkillId.TURN_UNDEAD) {
            if (!(cha instanceof L1MonsterInstance)) {        	
            	su.fail(FailReason.INVALID_TARGET);
            	return true;
            }
        }

        if (cha instanceof L1TowerInstance || cha instanceof L1DoorInstance) {
            return true;
        }

        if (cha instanceof L1PcInstance) {
            if (su.hdCalcType() == su.tfCalcTypePcPc() && su.hdPlayer() != null && su.hdPlayer().checkNonPvP(su.hdPlayer(), cha)) {
                final L1PcInstance pc = (L1PcInstance) cha;

                if (su.hdPlayer().getId() == pc.getId()) return false;
                if (pc.getClanid() != 0 && su.hdPlayer().getClanid() == pc.getClanid()) return false;

                return true;
            }
            return false;
        }

        if (!(cha instanceof L1MonsterInstance)) {
            return false; 
        }

        final L1MonsterInstance mon = (L1MonsterInstance) cha;
        final L1Npc tpl = mon.getNpcTemplate();
        if (tpl == null) return false;

        final boolean isTU = tpl.get_IsTU();
        final boolean isErase = tpl.get_IsErase();
        final int undeadType = tpl.get_undead();

        if (sid == L1SkillId.TURN_UNDEAD) {
            if (undeadType == 0 || undeadType == 2) {
                su.fail(FailReason.INVALID_TARGET); 
                return true;
            }
            if (!isTU) {
                su.fail(FailReason.INVALID_TARGET);
                return true;
            }
            return false;
        }

        if (sid == L1SkillId.ERASE_MAGIC || sid == L1SkillId.SLOW || sid == L1SkillId.MOB_SLOW_1
                || sid == L1SkillId.MOB_SLOW_18 || sid == L1SkillId.MANA_DRAIN || sid == L1SkillId.WIND_SHACKLE) {
            return !isErase;
        }

        return false;
    }

    public boolean isPcSummonPet(L1SkillUse su, L1Character cha) {
        if (su == null || cha == null) return false;

        if (su.hdCalcType() == su.tfCalcTypePcPc()) return true;
        if (su.hdCalcType() != su.tfCalcTypePcNpc()) return false;

        if (cha instanceof L1SummonInstance) {
            return ((L1SummonInstance) cha).isExsistMaster();
        }
        return (cha instanceof L1CompanionInstance) || (cha instanceof L1SupportInstance);
    }
}
