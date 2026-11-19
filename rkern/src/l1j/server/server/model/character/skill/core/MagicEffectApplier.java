package l1j.server.server.model.character.skill.core;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.helper.SkillIconHelper;

public enum MagicEffectApplier {
	INSTANCE;

	public void applySkillEffect(L1SkillUse su, L1Character cha, boolean repetition) {
		if (su == null || cha == null || su.exSkill() == null) return;

		final int durationMs = DurationResolver.INSTANCE.resolveDurationMs(su);
		if (durationMs <= 0) return;

		if (shouldSkipAddMagicList(su.hdSkillId())) return;

		if (su.hdSkillId() == L1SkillId.MOB_COCA  && cha.hasSkillEffect(L1SkillId.MOB_COCA))  return;
		if (su.hdSkillId() == L1SkillId.MOB_BASILL && cha.hasSkillEffect(L1SkillId.MOB_BASILL)) return;

		cha.setSkillEffect(su.hdSkillId(), durationMs);

		if (repetition && (cha instanceof L1PcInstance)) {
			sendIcon(su, (L1PcInstance) cha);
		}
	}

	private void sendIcon(L1SkillUse su, L1PcInstance pc) {
		if (su == null || pc == null || su.exSkill() == null) return;
		final int sec = su.exBuffIconDurationSec();
		SkillIconHelper.sendIcon(pc, su.hdSkillId(), Math.max(sec, 0));
	}

	private boolean shouldSkipAddMagicList(int skillId) {
		if (skillId == L1SkillId.CURSE_POISON || skillId == L1SkillId.TOMAHAWK) return true;
		if (skillId == L1SkillId.CURSE_PARALYZE || skillId == L1SkillId.CURSE_PARALYZE2) return true;
		switch (skillId) {
		case L1SkillId.CUBE_AVATAR:
		case L1SkillId.CUBE_OGRE:
		case L1SkillId.CUBE_GOLEM:
		case L1SkillId.CUBE_RICH:
		case L1SkillId.IMPACT:
		case L1SkillId.HOLY_WEAPON:
		case L1SkillId.ENCHANT_WEAPON:
		case L1SkillId.BLESS_WEAPON:
			return true;
		}
		return false;
	}
}
