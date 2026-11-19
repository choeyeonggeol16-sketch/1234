package l1j.server.server.model.character.skill.helper;

import l1j.server.MJWarSystem.MJWar;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SupportInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.templates.L1Skills;

public enum SkillTargetFilter {
    INSTANCE;

    public boolean isTarget(L1SkillUse su, L1Character cha) {
        if (su == null || cha == null) return false;

        final L1Skills skill = su.tfSkill();
        if (skill == null) return false;

        final int skillId = su.tfSkillId();
        final int calcType = su.tfCalcType();

        final L1Character user = su.tfUser();
        if (user == null) return false;

        final L1PcInstance player = su.tfPlayer();
        final L1NpcInstance npc = su.tfNpc();
        final L1Character firstTarget = su.tfTarget();

        // ghost 제외
        if (cha instanceof L1PcInstance) {
            if (((L1PcInstance) cha).isGhost()) return false;
        }

        // 문 HP 0/1 제외
        if (cha instanceof L1DoorInstance) {
            final int mhp = cha.getMaxHp();
            if (mhp == 0 || mhp == 1) return false;
        }

        boolean ok = su.tfBaseTargetByTargetTo(cha);
        final boolean isAttackSkill = su.tfIsAttackSkill();

        // NPC가 PC를 공격할 때: 펫/소환/동료만 허용
        if (isAttackSkill && calcType == su.tfCalcTypeNpcPc()) {
            if (!su.tfIsPcOrControllable(cha)) return false;
        }

        // 몬스터 vs 몬스터 공격 금지
        if (isAttackSkill && calcType == su.tfCalcTypeNpcNpc()
                && (user instanceof L1MonsterInstance) && (cha instanceof L1MonsterInstance)) {
            return false;
        }

        // 인형은 HASTE만
        if (cha instanceof L1DollInstance && skillId != L1SkillId.HASTE) return false;

        // PC가 NPC에게 쓸 때(컨트롤 불가 NPC): PC/컨트롤 대상/서포트 제외
        if (calcType == su.tfCalcTypePcNpc()
                && firstTarget instanceof L1NpcInstance
                && !su.tfIsControllable(firstTarget)
                && (su.tfIsPcOrControllable(cha) || cha instanceof L1SupportInstance)) {
            return false;
        }

        // NPC가 PC를 공격할 때: 마스터/세이프존 예외
        if (isAttackSkill && calcType == su.tfCalcTypeNpcPc()) {
            final int userMasterId = su.tfGetMasterIdOrZero(user);
            if (userMasterId != 0 && cha.getId() == userMasterId) return false;
            if (cha instanceof L1PcInstance && cha.getZoneType() == 1) return false;
        }

        // 공격스킬 자기 자신 타겟 금지
        if (skill.getType() == L1Skills.TYPE_ATTACK && cha.getId() == user.getId()) return false;

        // HEAL_ALL 자기 자신 금지
        if (cha.getId() == user.getId() && skillId == L1SkillId.HEAL_ALL) return false;

        // 자기 자신에게 버프류 허용(타겟To 플래그 기반)
        if (cha.getId() == user.getId() && skillId != L1SkillId.HEAL_ALL) {
            final int to = skill.getTargetTo();
            if (((to & L1Skills.TARGET_TO_PC) == L1Skills.TARGET_TO_PC)
                    || ((to & L1Skills.TARGET_TO_CLAN) == L1Skills.TARGET_TO_CLAN)
                    || ((to & L1Skills.TARGET_TO_PARTY) == L1Skills.TARGET_TO_PARTY)) {
                return true;
            }
        }

        // PC 공격스킬인데 PK 아니면: 내 소환/펫/동료는 제외
        if (user instanceof L1PcInstance && isAttackSkill && !su.tfIsPK()) {
            final int myId = (player != null ? player.getId() : 0);
            if (myId != 0) {
                final int targetMasterId = su.tfGetMasterIdOrZero(cha);
                if (targetMasterId != 0 && targetMasterId == myId) return false;
            }
        }

        // 공격스킬인데 PK 아니고 대상이 PC이면: 기본적으로 불가(예외만 허용)
        if (isAttackSkill && !su.tfIsPK() && (cha instanceof L1PcInstance) && !(cha instanceof L1MonsterInstance)) {
            final L1PcInstance enemy = (L1PcInstance) cha;

            // 카운터 디텍션 예외
            if (skillId == L1SkillId.COUNTER_DETECTION
            		&& player.getId() != enemy.getId()
                    && enemy.getZoneType() != 1
                    && (enemy.hasSkillEffect(L1SkillId.INVISIBILITY) || enemy.hasSkillEffect(L1SkillId.BLIND_HIDING))) {
                return true;
            }

            // 전쟁 중 같은 편 처리
            if (player != null) {
                if (MJWar.isSameWar(player.getClan(), enemy.getClan())) {
                    return L1CastleLocation.checkInAllWarArea(enemy.getX(), enemy.getY(), enemy.getMapId());
                }
            }

            // PC가 PC를 평시 공격하려는 케이스 차단(기존 조건 유지)
            if (npc == null && !(user instanceof L1NpcInstance)) return false;
        }

        // 관통 아니면 glanceCheck
        if (!skill.getIsThrough() && !user.glanceCheck(cha.getX(), cha.getY())) {
            if (skill.getType() != L1Skills.TYPE_CHANGE && skill.getType() != L1Skills.TYPE_RESTORE) {
                su.tfSetGlanceCheckFail(true);
                return false;
            }
        }

        if (cha.hasSkillEffect(L1SkillId.ICE_LANCE) && SkillUseHelper.isIceLanceBlockedDebuff(skillId)) return false;
        if (cha.hasSkillEffect(L1SkillId.EARTH_BIND) && skillId != L1SkillId.CANCELLATION) return false;

        if (cha.hasSkillEffect(L1SkillId.MOB_BASILL) && skillId == L1SkillId.MOB_BASILL) return false;
        if (cha.hasSkillEffect(L1SkillId.MOB_COCA) && skillId == L1SkillId.MOB_COCA) return false;

        // 테이밍/좀비는 몬스터만
        if (!(cha instanceof L1MonsterInstance)
                && (skillId == L1SkillId.TAMING_MONSTER || skillId == L1SkillId.CREATE_ZOMBIE)) return false;

        // 사망/부활류 조건
        if (cha.isDead()) {
            if (skillId != L1SkillId.CREATE_ZOMBIE && skillId != L1SkillId.RESURRECTION
                    && skillId != L1SkillId.GREATER_RESURRECTION && skillId != L1SkillId.CALL_OF_NATURE) {
                return false;
            }
            if ((cha instanceof L1TowerInstance || cha instanceof L1DoorInstance)) return false;
        } else {
            if (skillId == L1SkillId.CREATE_ZOMBIE || skillId == L1SkillId.RESURRECTION
                    || skillId == L1SkillId.GREATER_RESURRECTION || skillId == L1SkillId.CALL_OF_NATURE) {
                return false;
            }
        }

        // 절대방어
        if (cha instanceof L1PcInstance) {
            final L1PcInstance pc = (L1PcInstance) cha;
            if (pc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) {
                return SkillUseHelper.isAbsoluteBarrierAllowed(skillId);
            }
        }

        // NPC 숨김 상태
        if (cha instanceof L1NpcInstance) {
            final int hiddenStatus = ((L1NpcInstance) cha).getHiddenStatus();
            if (hiddenStatus == L1NpcInstance.HIDDEN_STATUS_SINK) {
                return SkillUseHelper.isNpcSinkAllowed(skillId);
            } else if (hiddenStatus == L1NpcInstance.HIDDEN_STATUS_FLY) {
                return false;
            }
        }

        // PC->PC 타겟To(클랜/파티) 예외
        if (calcType == su.tfCalcTypePcPc() && (cha instanceof L1PcInstance)) {
            try {
                final int to = skill.getTargetTo();
                final L1PcInstance tpc = (L1PcInstance) cha;

                if ((to & L1Skills.TARGET_TO_CLAN) == L1Skills.TARGET_TO_CLAN) {
                    if (player != null && player.getClanid() != 0 && player.getClanid() == tpc.getClanid()) return true;
                }
                if ((to & L1Skills.TARGET_TO_PARTY) == L1Skills.TARGET_TO_PARTY) {
                    if (player != null && player.getParty() != null && player.getParty().isMember(tpc)) return true;
                }
            } catch (Throwable ignore) {
                return false;
            }
        }
        
        return ok;
    }
}
