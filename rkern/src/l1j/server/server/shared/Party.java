package l1j.server.server.shared;

import l1j.server.server.model.CParty;

/** 경기장 파티는 파티장이 탈퇴하면 다음 파티장을 선출한다. */
public class Party extends CParty {
    @Override
    protected boolean dismissOnMasterLeave() { return false; }
}
