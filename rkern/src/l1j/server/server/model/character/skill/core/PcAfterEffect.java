
package l1j.server.server.model.character.skill.core;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillUse;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;

public enum PcAfterEffect {
    INSTANCE;

    public void onAffected(L1SkillUse su, L1PcInstance pc) {
        if (su == null || pc == null) return;
        
        if (!pc.refreshLight()) return;
        
        SC_OwnCharAttrDef.send(pc);
        SC_OwnCharStatus.send(pc);
        su.exSendHappenMessage(pc);
    }
}
