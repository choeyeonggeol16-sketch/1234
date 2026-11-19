package l1j.server.MJCompanion;

import l1j.server.MJCompanion.Basic.Buff.MJCompanionBuffInfo;
import l1j.server.MJCompanion.Basic.Category.MJCompanionCategoryInfo;
import l1j.server.MJCompanion.Basic.ClassInfo.MJCompanionClassInfo;
import l1j.server.MJCompanion.Basic.Elemental.MJCompanionElemental;
import l1j.server.MJCompanion.Basic.Exp.MJCompanionExpPenalty;
import l1j.server.MJCompanion.Basic.FriendShip.MJCompanionFriendShipBonus;
import l1j.server.MJCompanion.Basic.HuntingGround.MJCompanionHuntingGround;
import l1j.server.MJCompanion.Basic.Joke.MJCompanionJokeInfo;
import l1j.server.MJCompanion.Basic.Potion.MJCompanionPotionInfo;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionClassSkillInfo;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillEnchantInfo;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillInfo;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillTierOpenInfo;
import l1j.server.MJCompanion.Basic.Stat.MJCompanionStatEffect;
import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.MJCompanion.Instance.MJCompanionRegenerator;

public class MJCompanionLoader {
	private static MJCompanionLoader _instance;
	public static MJCompanionLoader getInstance(){
		if(_instance == null)
			_instance = new MJCompanionLoader();
		return _instance;
	}
	
	private MJCompanionLoader(){
		MJCompanionCategoryInfo.do_load();
		CompanionSettings.do_load();
		MJCompanionElemental.do_load();
		MJCompanionClassInfo.do_load();
		MJCompanionSkillInfo.do_load();
		MJCompanionSkillEnchantInfo.do_load();
		MJCompanionSkillTierOpenInfo.do_load();
		MJCompanionClassSkillInfo.do_load();
		MJCompanionStatEffect.do_load();
		MJCompanionInstanceCache.do_load();
		MJCompanionPotionInfo.do_load();
		MJCompanionJokeInfo.do_load();
		MJCompanionHuntingGround.do_load();
		MJCompanionBuffInfo.do_load();
		MJCompanionFriendShipBonus.do_load();
		MJCompanionExpPenalty.do_load();
		MJCompanionRegenerator.getInstance().do_run();
	}
}
