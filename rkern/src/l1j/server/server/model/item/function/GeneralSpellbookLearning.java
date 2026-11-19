package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.templates.L1Skills;

/** Phase 12: original SpellBookItems.java SpellBook body, unchanged. */
final class GeneralSpellbookLearning {
    private GeneralSpellbookLearning() {}

	static void SpellBook(L1PcInstance pc, L1ItemInstance item, boolean isLawful) {
		String s = "";
		int i = 0;
		int level1 = 0;
		int level2 = 0;
		int l = 0;
		int i1 = 0;
		int j1 = 0;
		int k1 = 0;
		int l1 = 0;
		int i2 = 0;
		int j2 = 0;
		int k2 = 0;
		int l2 = 0;
		int i3 = 0;
		int j3 = 0;
		int k3 = 0;
		int l3 = 0;
		int i4 = 0;
		int j4 = 0;
		int k4 = 0;
		int l4 = 0;
		int i5 = 0;
		int j5 = 0;
		int k5 = 0;
		int l5 = 0;
		int i6 = 0;
		int dk3 = 0;
		int bw1 = 0;
		int bw2 = 0;
		int bw3 = 0;
		L1Skills l1skills = null;
		for (int skillId = 1; skillId < 81; skillId++) {
			l1skills = SkillsTable.getInstance().getTemplate(skillId);
            if (l1skills == null) continue;
			if (SkillBookLearning.matches(item.getItem().getName(), "마법서 (", l1skills.getName())) {
				int skillLevel = l1skills.getSkillLevel();
				int i7 = l1skills.getId();
				s = l1skills.getName();
				i = l1skills.getSkillId();
				switch (skillLevel) {
				case 1:
					level1 = i7;
					break;
				case 2:
					level2 = i7;
					break;
				case 3:
					l = i7;
					break;
				case 4:
					i1 = i7;
					break;
				case 5:
					j1 = i7;
					break;
				case 6:
					k1 = i7;
					break;
				case 7:
					l1 = i7;
					break;
				case 8:
					i2 = i7;
					break;
				case 9:
					j2 = i7;
					break;
				case 10:
					k2 = i7;
					break;
				case 11:
					l2 = i7;
					break;
				case 12:
					i3 = i7;
					break;
				case 13:
					j3 = i7;
					break;
				case 14:
					k3 = i7;
					break;
				case 15:
					l3 = i7;
					break;
				case 16:
					i4 = i7;
					break;
				case 17:
					j4 = i7;
					break;
				case 18:
					k4 = i7;
					break;
				case 19:
					l4 = i7;
					break;
				case 20:
					i5 = i7;
					break;
				case 21:
					j5 = i7;
					break;
				case 22:
					k5 = i7;
					break;
				case 23:
					l5 = i7;
					break;
				case 24:
					i6 = i7;
					break;
				case 25:
					dk3 = i7;
					break;
				case 26:
					bw1 = i7;
					break;
				case 27:
					bw2 = i7;
					break;
				case 28:
					bw3 = i7;
					break;
				}
			}
		}
        if (i == 0) {
            SC_SystemMessage.send(pc, "습득할 수 있는 스킬을 찾지 못했습니다.");
            return;
        }
        if (pc.isSkillMastery(i)) {
            SC_SystemMessage.send(pc, "이미 배운 마법입니다.");
            return;
        }
        int[] learnedLevels = { level1, level2, l, i1, j1, k1, l1, i2, j2, k2,
                l2, i3, j3, k3, l3, i4, j4, k4, l4, i5,
                j5, k5, l5, i6, dk3, bw1, bw2, bw3, 0, 0 };
        SkillBookLearning.commitLearning(pc, item, i, s, learnedLevels, isLawful ? 224 : 231);
	}
}
