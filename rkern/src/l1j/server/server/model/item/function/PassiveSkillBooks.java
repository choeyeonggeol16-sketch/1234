package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.item.function.ItemLearningRule.Gate;
import l1j.server.server.model.item.function.ItemLearningRule.Requirement;
import static l1j.server.server.model.item.function.ItemLearningRule.*;

/** Item rules are immutable; callbacks retain only the player until the existing commit completes. */
final class PassiveSkillBooks {
    private PassiveSkillBooks() {}

    static void use(L1PcInstance pc, L1ItemInstance item, int itemId) {
        ItemLearningRule rule = PassiveSkillLearningRules.ruleFor(itemId);
        if (rule != null && rule.canLearn(pc)) rule.learn(pc, item, PassiveSkillLearningEffects.effectFor(pc, itemId));
    }




}
