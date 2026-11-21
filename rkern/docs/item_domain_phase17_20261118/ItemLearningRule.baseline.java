package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.GameUtils;

/** Immutable learning policy. Rules retain IDs and text, never a player or item. */
final class ItemLearningRule {
    enum Gate {
        CROWN(0), CROWN_1(0, 1), CROWN_85(0, 85), CROWN_ADVANCE(0, 0),
        KNIGHT(1), KNIGHT_1(1, 1), KNIGHT_86(1, 86), ELF_85(2, 85),
        WIZARD(3), WIZARD_1(3, 1), WIZARD_80(3, 80), WIZARD_85(3, 85),
        DARK_ELF(4), DARK_ELF_1(4, 1), DARK_ELF_80(4, 80),
        DRAGON_KNIGHT(5), DRAGON_KNIGHT_1(5, 1),
        ILLUSIONIST(6), ILLUSIONIST_1(6, 1), ILLUSIONIST_85(6, 85),
        WARRIOR(7), WARRIOR_1(7, 1), WARRIOR_85(7, 85),
        GALE_85(-1, 85), IMMUNE_BLADE(-1, 0);

        private final int playerClass, minimumLevel;

        Gate(int playerClass) { this(playerClass, -1); }
        Gate(int playerClass, int minimumLevel) {
            this.playerClass=playerClass; this.minimumLevel=minimumLevel;
        }

        boolean allows(L1PcInstance pc) {
            if (playerClass>=0 && pc.getType()!=playerClass) return false;
            if (this==GALE_85 && !SkillsTable.getInstance().spellCheck(pc.getId(), 174)) return false;
            if (minimumLevel<0) return true;
            int level=switch(this) {
                case CROWN_ADVANCE -> Config.어드밴스레벨;
                case IMMUNE_BLADE -> Config.이뮨투함블레이드레벨;
                default -> minimumLevel;
            };
            return pc.getLevel()>=level;
        }
    }

    enum Requirement {
        NONE(0, false, false, ""),
        DISINTEGRATE(77, true, true, "디스인티그레이트를 습득한 후 네메시스를 습득할 수 있습니다."),
        IMMUNE(68, true, true, "이뮨 투 함을 습득한 후 세인트를 습득할 수 있습니다."),
        REDUCTION_ARMOR(88, true, true, "리덕션 아머를 배운 후 습득가능합니다."),
        NO_DESTROY_HORROR(L1SkillId.가드피어호러, false, false, "상위 단계 디스트로이 패시브가 적용 중입니다."),
        DESTROY_FEAR(L1SkillId.가드피어, false, true, "디스트로이:피어를 습득한 후에 배울 수 있습니다.");

        private final int skill;
        private final boolean persisted, required;
        private final String failure;

        Requirement(int skill, boolean persisted, boolean required, String failure) {
            this.skill=skill; this.persisted=persisted; this.required=required; this.failure=failure;
        }

        boolean allows(L1PcInstance pc) {
            if (this==NONE) return true;
            boolean known=persisted ? SkillsTable.getInstance().spellCheck(pc.getId(), skill) : pc.hasSkillEffect(skill);
            if (known==required) return true;
            pc.sendPackets(failure);
            return false;
        }
    }

    private final boolean passive, duplicateFirst, requiresAction;
    private final int target, replacedSkill;
    private final Gate gate;
    private final Requirement requirement;
    private final String alreadyKnown, unavailable;

    private ItemLearningRule(boolean passive, int target, int replacedSkill, Gate gate,
            Requirement requirement, boolean duplicateFirst, boolean requiresAction,
            String alreadyKnown, String unavailable) {
        this.passive=passive; this.target=target; this.replacedSkill=replacedSkill;
        this.gate=gate; this.requirement=requirement; this.duplicateFirst=duplicateFirst;
        this.requiresAction=requiresAction; this.alreadyKnown=alreadyKnown; this.unavailable=unavailable;
    }

    static ItemLearningRule passive(int skill, int replacedSkill, Gate gate,
            Requirement requirement, String alreadyKnown, String unavailable) {
        return new ItemLearningRule(true, skill, replacedSkill, gate, requirement,
                true, false, alreadyKnown, unavailable);
    }

    static ItemLearningRule exchange(int itemId, Gate gate, boolean duplicateFirst,
            boolean requiresAction, String alreadyKnown, String unavailable) {
        return new ItemLearningRule(false, itemId, 0, gate, Requirement.NONE,
                duplicateFirst, requiresAction, alreadyKnown, unavailable);
    }

    boolean canLearn(L1PcInstance pc) {
        // Some original items report class/level failure before checking ownership.
        if (!duplicateFirst && !gate.allows(pc)) return unavailable(pc);
        if (passive ? pc.hasSkillEffect(target) : pc.getInventory().checkItem(target)) {
            pc.sendPackets(alreadyKnown);
            return false;
        }
        if (!requirement.allows(pc)) return false;
        if (duplicateFirst && !gate.allows(pc)) return unavailable(pc);
        return !requiresAction || !GameUtils.isNonAction(pc);
    }

    private boolean unavailable(L1PcInstance pc) {
        pc.sendPackets(unavailable);
        return false;
    }

    void learn(L1PcInstance pc, L1ItemInstance item, Runnable effect) {
        if (passive) DurableSkillItems.useDurablePassive(pc, item, target, replacedSkill, effect);
        else ItemReceiptOperations.UseDurableExchange(pc, item, target, effect);
    }

    static void finishPassive(L1PcInstance pc, int skill) {
        pc.setSkillEffect(skill, -1);
        playLearnSound(pc);
    }

    static void playLearnSound(L1PcInstance pc) {
        SC_SkillSound.send(pc, pc.getId(), 224);
        SC_SkillSound.broadcast(pc, pc.getId(), 224);
    }
}
