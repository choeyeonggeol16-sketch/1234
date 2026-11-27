package l1j.server.server.shared;

import l1j.server.server.model.CParty;
import l1j.server.server.model.Instance.L1PcInstance;

public class Party extends CParty {
    @Override
    public void leaveMember(L1PcInstance pc) {
        leave(pc, false, false);
    }
}
