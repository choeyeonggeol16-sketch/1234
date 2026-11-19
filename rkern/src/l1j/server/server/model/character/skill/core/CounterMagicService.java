
package l1j.server.server.model.character.skill.core;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;

public enum CounterMagicService {
    INSTANCE;

    public boolean consumeIfPresent(boolean enabled, L1Character target) {
        if (!enabled || target == null) return false;
        if (!target.hasSkillEffect(L1SkillId.COUNTER_MAGIC)) return false;

        target.removeSkillEffect(L1SkillId.COUNTER_MAGIC);

        final int id = target.getId();
        SC_SkillSound.broadcastFast(target, id, 10702);
        if (target instanceof L1PcInstance) {
            SC_SkillSound.send(((L1PcInstance) target), id, 10702);
        }
        return true;
    }
}
