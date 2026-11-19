package l1j.server.server.model.character.action;

import l1j.server.server.model.Instance.L1PcInstance;

public final class CMoveToTargetWaitAction extends CNewAction {

    CMoveToTargetWaitAction(CCreatureController controller) {
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
        if (actor == null || actor.isDead()) {
            return true;
        }

        if (controller.HasPendingAction()) {
            return true;
        }

        controller.ResumeMoveToTargetFromWait();
        return false;
    }
}