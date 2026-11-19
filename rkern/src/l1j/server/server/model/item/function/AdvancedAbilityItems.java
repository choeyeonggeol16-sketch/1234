package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/** Routes learning and active items without changing item eligibility. */
final class AdvancedAbilityItems {
    private AdvancedAbilityItems() {}

    static void use(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        switch (itemId) {
            case 70701, 70702, 80805, 80804, 70716, 70704, 70705, 210122, 50512, 40228, 40231, 70712, 70713, 70717, 70718, 70719, 10889, 10888, 70732, 40271, 40279, 210008, 210013, 41147, 70720, 40224, 4100100 -> PassiveSkillBooks.use(pc, l1iteminstance, itemId);
            case 90905, 80889, 70707, 70734, 70709, 70711, 70715, 70722, 44002, 70730, 220013, 70731, 707778, 707780, 707782, 70741 -> SkillLearningItems.use(pc, l1iteminstance, itemId);
            case 70706 -> BerserkSkillConsumable.use(pc);
            case 70708 -> PrimeSkillConsumable.use(pc);
            case 70733 -> MassImmuneSkillConsumable.use(pc);
            case 70710 -> HalphasSkillConsumable.use(pc);
            case 70714 -> PotentialSkillConsumable.use(pc);
            case 44001 -> BurningShotSkillConsumable.use(pc);
            case 70735 -> LiberationSkillConsumable.use(pc);
            case 880800 -> PotentialScrollSelection.usePotentialScroll(pc, l1iteminstance);
            case 70740 -> ImmuneBladeSkillItems.UseImmuneBlade(pc);
        }
    }
}
