package l1j.server.server.model.character.skill.helper;

import java.util.ArrayList;
import java.util.List;

import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.templates.L1Skills;

public enum SkillTargetListBuilder {
    INSTANCE;

    public void build(L1SkillUse su) {
        if (su == null) return;

        final L1Skills skill = su.tlSkill();
        if (skill == null) return;

        // ===== TYPE_LOGIN: 무조건 자기 자신 =====
        if (su.tlType() == L1SkillUse.TYPE_LOGIN) {
            su.tlAddTarget(su.tlUser(), true);
            return;
        }

        // ===== TARGET_TO_ME + 공격스킬 아니면 자기 자신 =====
        if (skill.getTargetTo() == L1Skills.TARGET_TO_ME
                && (skill.getType() & L1Skills.TYPE_ATTACK) != L1Skills.TYPE_ATTACK) {
            su.tlAddTarget(su.tlUser(), true);
            return;
        }

        final L1Character user = su.tlUser();
        final L1Character target = su.tlTarget();
        if (user == null || target == null) return;

        // ===== 사거리/스크린 체크 =====
        if (!isInRangeOrScreen(su, user, target)) return;

        // ===== 단일 타겟 스킬에서 타겟 유효성 =====
        final String skillTarget = skill.getTarget();
        if (!su.tlIsTarget(target) && !"none".equals(skillTarget)) {
            return;
        }

        // ===== 공성전 중 광역공격 단일화 =====
        if (shouldAoEBecomeSingleInSiege(su)) {
            if (isSpecialAreaTargetAllowed(su.tlSkillId(), target)) {
                su.tlAddTarget(target, true);
            }
            return;
        }

        // ===== LIGHTNING: 선형 타겟 =====
        if (su.tlSkillId() == L1SkillId.LIGHTNING) {
            final List<L1Object> olist = CWorld.getInstance().getVisibleLineObjects(user, target);
            if (olist == null || olist.isEmpty()) return;

            for (int i = 0, n = olist.size(); i < n; i++) {
                final L1Object obj = olist.get(i);
                if (!(obj instanceof L1Character)) continue;

                final L1Character cha = (L1Character) obj;
                if (!su.tlIsTarget(cha)) continue;
                su.tlAddTarget(cha, true);
            }
            return;
        }

        // ===== IMMUNE_TO_HARM: glance 필수 =====
        if (su.tlSkillId() == L1SkillId.IMMUNE_TO_HARM) {
            if (!user.glanceCheck(target.getX(), target.getY())) return;
        }

        // ===== 단일(AREA==0) =====
        if (skill.getArea() == 0) {
            if (!user.glanceCheck(target.getX(), target.getY())) {
                if ((skill.getType() & L1Skills.TYPE_ATTACK) == L1Skills.TYPE_ATTACK) {
                    su.tlAddTarget(target, false);
                    return;
                }
            }
            su.tlAddTarget(target, true);
            return;
        }

        // ===== 광역 =====
        final boolean isAttackSkill = su.tlIsAttackSkill();
        buildAreaTargets(su, skill.getArea(), isAttackSkill);
    }

    private static int aoeCap(L1SkillUse su) {
        final int sid = su.tlSkillId();
        if (sid == 65) return 48;
        return 64;
    }

    private void buildAreaTargets(L1SkillUse su, int area, boolean isAttackSkill) {
        final L1Skills skill = su.tlSkill();
        if (skill == null) return;

        final L1Character user = su.tlUser();
        final L1Character target = su.tlTarget();
        if (user == null || target == null) return;

        final int skillId = su.tlSkillId();

        if (!"none".equals(skill.getTarget())
                && isSpecialAreaTargetAllowed(skillId, target)) {
            su.tlAddTarget(target, true);
        }

        if (skillId != 49
                && !isAttackSkill
                && isSpecialAreaTargetAllowed(skillId, user)) {
            su.tlAddTarget(user, true);
        }

        final int cap = aoeCap(su);
        int added = su.exTargetCount();
        if (added >= cap) return;

        final List<L1Object> objects = getAreaObjects(su, area);
        if (objects == null || objects.isEmpty()) return;

        for (int i = 0, n = objects.size(); i < n; i++) {
            final L1Object obj = objects.get(i);
            if (!(obj instanceof L1Character)) continue;

            final L1Character cha = (L1Character) obj;

            if (!isSpecialAreaTargetAllowed(skillId, cha)) continue;
            if (!su.tlIsTarget(cha)) continue;
            if (su.tlAlreadyInList(cha)) continue;

            su.tlAddTarget(cha, true);
            if (++added >= cap) break;
        }
    }

    private boolean isSpecialAreaTargetAllowed(int skillId, L1Character cha) {
        if (cha == null) {
            return false;
        }

        // 블리자드: 몬스터만
        if (skillId == L1SkillId.BLIZZARD) {
            return cha instanceof L1MonsterInstance;
        }

        // 카운터 디텍션
        if (skillId == L1SkillId.COUNTER_DETECTION) {
            // 세이프존은 타겟 포함 안 함
            if (cha.getZoneType() == 1) {
                return false;
            }

            // 인비 상태일 때만
            if (cha instanceof L1PcInstance) {
                return ((L1PcInstance) cha).isInvisble();
            }

            // 일반 NPC 제외
            if (!(cha instanceof L1MonsterInstance)) {
                return false;
            }

            // 몬스터: 숨은(SINK) 상태만
            final L1NpcInstance npc = (L1NpcInstance) cha;
            return npc.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_SINK;
        }

        return true;
    }

    private static final ThreadLocal<ArrayList<L1Object>> TL_AREA =
            ThreadLocal.withInitial(() -> new ArrayList<>(128));

    private List<L1Object> getAreaObjects(L1SkillUse su, int area) {
        final ArrayList<L1Object> out = TL_AREA.get();

        final int limit = 64;
        final int sid = su.tlSkillId();
        final int lim = (sid == 65 ? 48 : limit);

        try {
            if (area == -1) {
                CWorld.getInstance().fillVisibleObjectsLimit(su.tlUser(), -1, lim, out);
            } else {
                CWorld.getInstance().fillVisibleObjectsLimit(su.tlTarget(), area, lim, out);
            }
            return out;
        } catch (Throwable t) {
            out.clear();
            return out;
        }
    }

    private boolean isInRangeOrScreen(L1SkillUse su, L1Character from, L1Character to) {
        try {
            final int ranged = su.tlSkill().getRanged();
            if (ranged != -1) {
                return from.getLocation().getTileLineDistance(to.getLocation()) <= ranged;
            }
            return from.getLocation().isInScreen(to.getLocation());
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean shouldAoEBecomeSingleInSiege(L1SkillUse su) {
        final L1Skills skill = su.tlSkill();
        if (skill == null) return false;
        if (skill.getArea() <= 0) return false;
        if (!su.tlIsAttackSkill()) return false;

        final L1Character user = su.tlUser();
        if (!(user instanceof L1PcInstance)) return false;

        final L1PcInstance pc = (L1PcInstance) user;

        final int castleId;
        try {
            castleId = L1CastleLocation.getCastleIdByArea(pc);
        } catch (Throwable t) {
            return false;
        }
        if (castleId == 0) return false;

        try {
            return CastleWarBusiness.getInstance().isNowWar(castleId);
        } catch (Throwable t) {
            return false;
        }
    }
}
