package l1j.server.server.model;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1Npc;

/** 기존 스폰 호출부의 타입을 유지하는 이관용 클래스. 실행 상태는 상위 Maker가 관리한다. */
public class L1Spawn extends CNpcMaker {
    public L1Spawn(L1Npc template) throws SecurityException, ClassNotFoundException {
        super(template);
    }

    @Override
    protected final void bindSpawn(L1NpcInstance npc) {
        npc.setSpawn(this);
    }
}
