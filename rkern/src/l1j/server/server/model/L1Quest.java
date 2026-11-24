package l1j.server.server.model;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.quest.CQuestSystem;

/** Source compatibility during quest call-site migration. No duplicate state. */
public final class L1Quest extends CQuestSystem {
    public L1Quest(L1PcInstance owner) {
        super(owner);
    }
}
