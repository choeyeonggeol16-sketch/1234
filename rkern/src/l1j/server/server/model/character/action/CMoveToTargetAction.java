package l1j.server.server.model.character.action;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;

public final class CMoveToTargetAction extends CNewAction {

    CMoveToTargetAction(CCreatureController controller) {
        super(controller);
    }

    @Override
    public boolean OnStart() {
        SetDistance();
        AddTimer(1);
        return true;
    }

    @Override
    public boolean OnTick(long nowMs) {
        final L1PcInstance actor = controller.actor();
        if (actor == null || actor.isDead()) {
            return true;
        }

        final int targetId = controller.i0();
        final L1Object obj = L1World.getInstance().findObject(targetId);
        if (!(obj instanceof L1Character)) {
            return true;
        }

        final L1Character target = (L1Character) obj;
        if (target.isDead()
                || target.getMapId() != actor.getMapId()
                || target.isInvisble()) {
            return true;
        }

        if (actor.getLocation().getTileLineDistance(target.getLocation())
                <= actor.getAttackRang()) {
            if (controller.isForceAttack()) {
                controller.Attack(
                        target.getId(),
                        target.getX(),
                        target.getY(),
                        true);
            }
            return true;
        }

        final int heading = actor.targetDirection(target.getX(), target.getY());
        final boolean moved = controller.MoveStepDirect(
                actor.getX(), actor.getY(), heading, nowMs);

        if (!moved) {
            controller.EnterMoveToTargetWait(1);
            return false;
        }

        AddTimer(1);
        return false;
    }

    @Override
    public void OnEnd(boolean bFinishedWell) {
    }

    public boolean OnLostTarget(int id, boolean byUser) {
        return id == controller.i0();
    }

    public boolean OnTargetOutOfSight(int id) {
        return id == controller.i0();
    }

    public void SetDistance() {
        // L1은 actor.getAttackRang()을 실시간 사용한다.
    }

    public void SetValidPendingAction() {
        // pending validation은 CCreatureController가 owner thread에서 직렬화한다.
    }

    public void SetMoving() {
    }
}
