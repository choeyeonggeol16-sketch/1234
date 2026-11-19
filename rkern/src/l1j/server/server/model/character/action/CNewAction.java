package l1j.server.server.model.character.action;

/**
 * Action 객체는 CCreatureController가 PC 생명주기 동안 재사용한다.
 * packet/action마다 new 하지 않는다.
 */
public abstract class CNewAction {

    protected final CCreatureController controller;

    protected CNewAction(CCreatureController controller) {
        this.controller = controller;
    }

    /** action 진입. false면 시작 실패. */
    public abstract boolean OnStart();

    /** timer tick. true면 현재 action 완료, false면 계속/전환. */
    public abstract boolean OnTick(long nowMs);

    /** action 종료 hook. */
    public void OnEnd(boolean bFinishedWell) {
    }

    protected final void AddTimer(int nTick) {
        controller.AddTimer(nTick);
    }
}
