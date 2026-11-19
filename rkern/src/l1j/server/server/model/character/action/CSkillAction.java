package l1j.server.server.model.character.action;

import l1j.server.MJTemplate.MJL1Type;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.utils.MJCommons;

public final class CSkillAction extends CNewAction {

	CSkillAction(CCreatureController controller) {
        super(controller);
    }

    @Override
    public boolean OnStart() {
        AddTimer(1);
        return true;
    }

    @Override
    public boolean OnTick(long nowMs) {
        final L1PcInstance pc = controller.actor();
        if (pc == null
                || pc.isDead()
                || pc.isGhost()
                || pc.getNetConnection() == null
                || !pc.getNetConnection().isConnected()) {
            return true;
        }

        final int skillId = controller.i0();
        final int tId = controller.i1();
        final int tX = controller.i2();
        final int tY = controller.i3();
        final String message = controller.s0();

        if (!validateSpellValidation(pc, skillId, tId)) {
            return true;
        }

        if (pc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) {
            pc.removeSkillEffect(L1SkillId.ABSOLUTE_BARRIER);
        }

        if (!pc.hasSkillEffect(L1SkillId.비욘드)) {
            pc.killSkillEffectTimer(L1SkillId.MEDITATION);
        }

        pc.getRegenController().OnAttack(nowMs);

        L1SkillUse.cast(
                pc,
                skillId,
                tId,
                tX,
                tY,
                message,
                0,
                L1SkillUse.TYPE_NORMAL);

        return true;
    }

    public boolean OnLostTarget(int id, boolean byUser) {
        return true;
    }

    public boolean OnDisappear() {
        return true;
    }

    public boolean OnDamaged(int attackerId, int damage) {
        return false;
    }

    @Override
    public void OnEnd(boolean bFinishedWell) {
        // L1SkillUse.cast()가 packet/effect 종료 처리를 소유한다.
    }

    private boolean validateSpellValidation(
            L1PcInstance owner,
            int skillId,
            int tId) {

        final L1Map map = owner.getMap();
        if (map == null || !map.isUsableSkill()) {
            SC_ServerMessage.send(owner, 563);
            return false;
        }
        
        if(owner.isSkillDelay()) {	
        	owner.teleportunLock(skillId);
        	return false;
        }
        
        // 스킬을 배우지 않았는데 사용하는 경우
        if(!owner.isSkillMastery(skillId)) {
        	return false;
        }
 
        if (MJCommons.isNonAction(owner)) {
            return false;
        }

        if (!checkMorph(owner, tId)) {
            return false;
        }

        final L1Skills skill = SkillsTable.getInstance().getTemplate(skillId);
        if (skill == null) {
            return false;
        }

        owner.offFishing();

        double range = skill.getRanged();
        if (range < 0D) {
            range = 15D;
        }
        range += 4.5D;

        final L1Object target = L1World.getInstance().findObject(tId);
        if (target != null) {
            if (target.instanceOf(MJL1Type.L1TYPE_CHARACTER)) {
                if (owner.getMapId() != target.getMapId() || owner.getLocation().getLineDistance(target.getLocation()) > range) {
                    return false;
                }
            }
            if(target instanceof L1PcInstance targetPc) {
            	if(targetPc.get_teleport()) {
            		return false;
            	}
            }
            if ((owner.getMapId() == 254 || owner.getMapId() == 1930)
                    && target instanceof L1PcInstance) {
                SC_ServerMessage.send(owner, 563);
                return false;
            }
        }

        return true;
    }
    

    private boolean checkMorph(L1PcInstance owner, int tId) {
        if (owner.isGm() || tId == owner.getId() || tId == 0) {
            return true;
        }

        final int sprId = owner.getCurrentSpriteId();
        if (sprId != 12015
                && sprId != 5641
                && sprId != 11685
                && sprId != 11620
                && sprId != 11621) {
            return true;
        }

        final L1Object target = L1World.getInstance().findObject(tId);
        return target == null || !(target instanceof L1PcInstance);
    }
}
