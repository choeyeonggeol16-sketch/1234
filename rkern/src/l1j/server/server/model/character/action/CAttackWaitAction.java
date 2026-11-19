package l1j.server.server.model.character.action;

import l1j.server.server.model.Instance.L1PcInstance;

public final class CAttackWaitAction extends CNewAction {

    CAttackWaitAction(CCreatureController controller) {
        super(controller);
    }

    @Override
    public boolean OnStart() {
        AddTimer(Math.max(1, controller.i3()));
        return true;
    }

    @Override
    public boolean OnTick(long nowMs) {
        final L1PcInstance actor = controller.actor();
        if (actor == null
                || actor.isDead()
                || actor.getNetConnection() == null
                || !actor.getNetConnection().isConnected()) {
            return true;
        }

        // wait 중 새 입력이 들어왔으면 pending action이 먼저 실행된다.
        if (controller.HasPendingAction()) {
            return true;
        }

        // 실제 공격 가능 여부는 CAttackAction이 다시 판정한다.
        controller.ResumeAttackFromWait();
        return false;
    }
}
