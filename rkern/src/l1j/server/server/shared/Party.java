package l1j.server.server.shared;

import l1j.server.server.model.CParty;

/** Arena party: preserve leader succession instead of normal L1 disband. */
public class Party extends CParty {
    @Override
    protected boolean dismissOnMasterLeave() { return false; }
}
