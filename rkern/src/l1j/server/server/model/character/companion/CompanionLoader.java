package l1j.server.server.model.character.companion;

import l1j.server.server.model.character.companion.basic.buff.CompanionBuffInfo;
import l1j.server.server.model.character.companion.basic.category.CompanionCategoryInfo;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.model.character.companion.basic.elemental.CompanionElemental;
import l1j.server.server.model.character.companion.basic.exp.CompanionExpPenalty;
import l1j.server.server.model.character.companion.basic.friendship.CompanionFriendShipBonus;
import l1j.server.server.model.character.companion.basic.huntingground.CompanionHuntingGround;
import l1j.server.server.model.character.companion.basic.joke.CompanionJokeInfo;
import l1j.server.server.model.character.companion.basic.potion.CompanionPotionInfo;
import l1j.server.server.model.character.companion.basic.skills.CompanionClassSkillInfo;
import l1j.server.server.model.character.companion.basic.skills.CompanionSkillEnchantInfo;
import l1j.server.server.model.character.companion.basic.skills.CompanionSkillInfo;
import l1j.server.server.model.character.companion.basic.skills.CompanionSkillTierOpenInfo;
import l1j.server.server.model.character.companion.basic.stat.CompanionStatEffect;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.model.character.companion.instance.CompanionRegenerator;

public class CompanionLoader {
	private static CompanionLoader _instance;
	public static CompanionLoader getInstance(){
		if(_instance == null)
			_instance = new CompanionLoader();
		return _instance;
	}
	
	private CompanionLoader(){
		CompanionCategoryInfo.do_load();
		CompanionSettings.do_load();
		CompanionElemental.do_load();
		CompanionClassInfo.do_load();
		CompanionSkillInfo.do_load();
		CompanionSkillEnchantInfo.do_load();
		CompanionSkillTierOpenInfo.do_load();
		CompanionClassSkillInfo.do_load();
		CompanionStatEffect.do_load();
		CompanionInstanceCache.do_load();
		CompanionPotionInfo.do_load();
		CompanionJokeInfo.do_load();
		CompanionHuntingGround.do_load();
		CompanionBuffInfo.do_load();
		CompanionFriendShipBonus.do_load();
		CompanionExpPenalty.do_load();
		CompanionRegenerator.getInstance().do_run();
	}
}
