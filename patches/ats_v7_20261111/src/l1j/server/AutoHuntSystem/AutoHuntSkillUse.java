package l1j.server.AutoHuntSystem;

import l1j.server.ActionCodes.EActionCodes;
import l1j.server.ActionCodes.Loader.SpriteInformationLoader;
import l1j.server.Templates.L1Skills;
import l1j.server.server.SkillCheck;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.utils.CalcUtil;

public class AutoHuntSkillUse {
	private L1PcInstance owner = null;
	private static final int MAGIC_PENALTY = 1000;

	public AutoHuntSkillUse(L1PcInstance pc) {
		owner = pc;
	}

	public void toUseSkills() {
		int[] skillIds = null;
		if(owner.isCrown()) {
			skillIds = new int[]{SkillId.실드};
		} else if(owner.isKnight()) {
			skillIds = new int[]{SkillId.실드, SkillId.리덕션_아머, SkillId.바운스_어택, SkillId.카운터_배리어};
		} else if(owner.isElf()) {
			if(owner.getWeapon() == null) {
				return;
			}
			if(owner.getWeapon().getItem().getType1() == 20) {//활
				skillIds = new int[] { SkillId.인챈트_마이티, SkillId.인챈트_덱스터리티, SkillId.실드 };
			} else {// 검요정
				skillIds = new int[]{SkillId.버닝_웨폰, 
						SkillId.엘리멘탈_파이어, SkillId.어디셔널_파이어, SkillId.소울_오브_프레임, 
						SkillId.인챈트_마이티, SkillId.인챈트_덱스터리티,
						SkillId.블레스_웨폰, SkillId.실드 };
			}
		} else if(owner.isDarkelf()) {
			skillIds = new int[]{SkillId.드레스_마이티, SkillId.쉐도우_아머, SkillId.인챈트_베놈, SkillId.무빙_악셀레이션,
					SkillId.드레스_덱스터리티, SkillId.버닝_스피릿츠, SkillId.더블_브레이크, SkillId.드레스_이베이젼, SkillId.언케니_닷지};
		} else if(owner.isWizard()) {
			skillIds = new int[]{SkillId.홀리_워크, SkillId.실드, SkillId.블레스_웨폰,
					SkillId.인챈트_마이티, SkillId.인챈트_덱스터리티, SkillId.어드밴스_스피릿};
		}

		SkillUse _skilluse;
		L1Skills _skill;
		for(int i = 0; i < skillIds.length; i++) {
			try {	
				long current = System.currentTimeMillis();
				long interval = getInterval(skillIds[i]);
				long delay = SpriteInformationLoader.getInstance().getUseSpellInterval(owner, skillIds[i]) + interval;

				if(current - owner.getAutoSkillDelay() < 0)
					break;

				if(!SkillCheck.getInstance().CheckSkill(owner, skillIds[i])) 
					continue;

				if(owner.hasSkillEffect(skillIds[i])) continue;				
				_skill = SkillsTable.getInstance().getTemplate(skillIds[i]);

				if(!isHPMPConsume(_skill, skillIds[i])) {
					return;
				} else if(!isItemConsume(_skill)){
					return;
				}

				if(skillIds[i] == SkillId.블레스_웨폰) {
					if(owner.getWeapon() == null) continue;
				}

				if(skillIds[i] == SkillId.카운터_배리어) {
					if (owner.getWeapon() == null || owner.getWeapon().getItem().getType() != 3) {
						continue;
					}
				}

				_skilluse = new SkillUse();
				_skilluse.handleCommands(owner, skillIds[i], owner.getId(), owner.getX(), owner.getY(), null, 0, SkillUse.TYPE_NORMAL);				
				owner.setAutoSkillDelay(System.currentTimeMillis() + delay + MAGIC_PENALTY);
			}catch(Exception e) {
				e.printStackTrace();
				break;
			}
		}
	}

	public void toUseHealingMagic() {
		if(!SkillCheck.getInstance().CheckSkill(owner, SkillId.그레이터_힐)) 
			return;

		if(!owner.isWizard()) return;
		int _hp = (int) (((double) owner.getCurrentHp() / (double) owner.getMaxHp()) * 100);
		SkillUse _skilluse;
		long interval = getInterval(SkillId.그레이터_힐);
		long delay = SpriteInformationLoader.getInstance().getUseSpellInterval(owner, SkillId.그레이터_힐) + interval;

		if(_hp <= 70) {
			int _mp = (int) (((double) owner.getCurrentMp() / (double) owner.getMaxMp()) * 100);
			if(_mp <= 30) return;
			long current = System.currentTimeMillis();
			if (current - owner.getAutoSkillDelay() < 0) 
				return;

			_skilluse = new SkillUse();
			_skilluse.handleCommands(owner, SkillId.그레이터_힐, owner.getId(), owner.getX(), owner.getY(), null, 0, SkillUse.TYPE_NORMAL);
			owner.setAutoSkillDelay(System.currentTimeMillis() + delay + MAGIC_PENALTY);
		}
	}

	public void toTripleArrow(L1Character target) {			
		if(!SkillCheck.getInstance().CheckSkill(owner, SkillId.트리플_애로우)) 
			return;

		L1Skills _skill = SkillsTable.getInstance().getTemplate(SkillId.트리플_애로우);
		long interval = getInterval(SkillId.트리플_애로우);
		long delay = SpriteInformationLoader.getInstance().getUseSpellInterval(owner, SkillId.트리플_애로우) + interval;

		long current = System.currentTimeMillis();
		if(current - owner.getAutoSkillDelay() < 0) 
			return;

		if(!isHPMPConsume(_skill, SkillId.트리플_애로우)) {
			return;
		}
		SkillUse _skilluse = new SkillUse();
		_skilluse.handleCommands(owner, SkillId.트리플_애로우, target.getId(), target.getX(), target.getY(), null, 0, SkillUse.TYPE_NORMAL);
		owner.setAutoSkillDelay(System.currentTimeMillis() + delay + MAGIC_PENALTY);
	}

	private boolean isItemConsume(L1Skills _skill) {
		int itemConsume = _skill.getItemConsumeId();
		int itemConsumeCount = _skill.getItemConsumeCount();
		if (itemConsume == 0) {
			return true;
		}
		if (!owner.getInventory().checkItem(itemConsume, itemConsumeCount)) {
			return false;
		}
		return true;
	}

	private boolean isHPMPConsume(L1Skills _skill, int _skillId) {		
		int _hpConsume;
		int _mpConsume;
		_mpConsume = _skill.getMpConsume();
		_hpConsume = _skill.getHpConsume();
		int currentMp = 0;
		int currentHp = 0;

		currentMp = owner.getCurrentMp();
		currentHp = owner.getCurrentHp();

		// 엠피 소모 감소 구현 기사 스킬은 제외 
		if(!(_skillId >= SkillId.쇼크_스턴 && _skillId <= SkillId.카운터_배리어)) {
			double intPre = CalcUtil.getMpConsumptionDecrease(owner.getAbility().getTotalInt()) * 0.01;
			int minMp = (int) (_mpConsume * intPre);
			if (minMp > 0) {
				_mpConsume -= minMp;
			}
		}
		// 장비에 대한 mp 소모 구현 
		if (_skillId == SkillId.인챈트_덱스터리티 || _skillId == SkillId.헤이스트
				&& owner.toArmorEquipped().isMagicHelmFast()) {
			_mpConsume >>= 1;
		} 
		else if(_skillId == SkillId.힐 || _skillId == SkillId.익스트라_힐
				&& owner.toArmorEquipped().isMagicHelmCure()) {
			_mpConsume >>= 1;
		}
		else if(_skillId == SkillId.인챈트_웨폰 || _skillId == SkillId.디텍션 || _skillId == SkillId.인챈트_마이티 
				&& owner.toArmorEquipped().isMagicHelmStr()) {
			_mpConsume >>= 1;
		}
		else if(_skillId == SkillId.헤이스트 && owner.toArmorEquipped().isOldWindHelm()) {
			_mpConsume >>= 1;
		}
		else if(_skillId == SkillId.그레이터_헤이스트 && owner.toArmorEquipped().isWindHelm()) {
			_mpConsume >>= 1;
		}	
		if (0 < _skill.getMpConsume()) {
			_mpConsume = Math.max(_mpConsume, 0);
		}

		if (currentHp < _hpConsume + 1) {
			return false;
		} else if (currentMp < _mpConsume) {
			return false;
		}
		return true;
	}

	public long getInterval(int skillId) {
		L1Skills skill = SkillsTable.getInstance().getTemplate(skillId);
		return owner.getCurrentSpriteInterval(skill.getTarget().equals("buff") || skill.getTarget().equals("attack") ? EActionCodes.spell_dir : EActionCodes.spell_nodir);
	}
}
