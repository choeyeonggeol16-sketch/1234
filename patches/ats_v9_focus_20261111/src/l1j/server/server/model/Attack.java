package l1j.server.server.model;

import l1j.server.commons;
import l1j.server.ItemSkill.Loader.ItemSkillModelLoader;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.Templates.L1Item;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.configure.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.WeaponBalanceTable;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.gametime.GameTimeClock;
import l1j.server.server.model.item.EnchantDamage;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.item.MagicDoll;
import l1j.server.server.model.poison.CurseParalysis;
import l1j.server.server.model.poison.DamagePoison;
import l1j.server.server.model.poison.SilencePoison;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_AttackMissPacket;
import l1j.server.server.serverpackets.S_AttackPacket;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_UseArrowSkill;
import l1j.server.server.serverpackets.S_UseAttackSkill;
import l1j.server.server.types.Point;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.EffectUtil;

/**
 * attack.java 명중, 데미지를 관리 새로 구현한다.
 * 기존의 관련된 모든 문제점 수정.
 * @author SJ
 *
 */
public class Attack {

	private L1PcInstance _pc = null;
	private L1Character _target = null;
	private L1PcInstance _targetPc = null;
	private L1NpcInstance _npc = null;
	private L1NpcInstance _targetNpc = null;
	private final int _targetId;
	private int _targetX;
	private int _targetY;	
	private int _hitRate = 0;
	private int _calcType;
	private static final int PC_PC = 1;
	private static final int PC_NPC = 2;
	private static final int NPC_PC = 3;
	private static final int NPC_NPC = 4;
	private boolean 버닝스프릿츠  = false;
	private boolean 더블브레이크  = false;
	private boolean 이도류크리티컬 = false;
	private boolean 크로우크리티컬 = false;
	private boolean 근거리크리티컬 = false;
	private boolean 원거리크리티컬 = false;
	private boolean 엘리멘탈파이어 = false;
	private boolean 발라원거리가호 = false;
	private boolean 발라근거리가호 = false;
	private boolean 소울오브프레임 = false;
	private boolean _isHit   = false;
	private ItemInstance weapon = null;
	private ItemInstance _arrow = null;
	private ItemInstance _sting = null;
	private int _damage = 0;
	private int _attckGrfxId = 0;
	private int _attckActId = 0;
	private int _weaponId = 0;	
	private int _weaponType = 0;	
	private int _weaponAddDmg = 0;
	private int _weaponSmall = 0;
	private int _weaponLarge = 0;
	private int _weaponBless = 1;
	private int _weaponEnchant = 0;
	private int _weaponMaterial = 0;
	private int _weaponDoubleDmgChance = 0;
	private int _attackType = 0;
	private int _weaponAttrLevel = 0;
	private int _leverage = 10; // 1/10배로 표현한다.

	public void setLeverage(int i) {
		_leverage = i;
	}
	private int getLeverage() {
		return _leverage;
	}
	public void setActId(int actId) {
		_attckActId = actId;
	}
	public void setGfxId(int gfxId) {
		_attckGrfxId = gfxId;
	}
	public int getActId() {
		return _attckActId;
	}
	public int getGfxId() {
		return _attckGrfxId;
	}

	public Attack(L1Character attacker, L1Character target) {
		if (attacker instanceof L1PcInstance) {
			_pc = (L1PcInstance) attacker;
			if (target instanceof L1PcInstance) {
				_targetPc = (L1PcInstance) target;
				_calcType = PC_PC;
			} else if (target instanceof L1NpcInstance) {
				_targetNpc = (L1NpcInstance) target;
				_calcType = PC_NPC;
			}
			// 무기 정보 시작 
			weapon = _pc.getWeapon();
			if (weapon != null) {
				_weaponId = weapon.getItem().getItemId();    
				_weaponType = weapon.getItem().getType1();
				_weaponAddDmg = weapon.getItem().getDmgModifier();
				_weaponSmall = weapon.getItem().getDmgSmall();
				_weaponLarge = weapon.getItem().getDmgLarge();
				_weaponBless = weapon.getItem().getBless();
				if (_weaponType != 20 && _weaponType != 62) {
					_weaponEnchant = weapon.getEnchantLevel() - weapon.getDurability(); // 손상분
				} else {
					_weaponEnchant = weapon.getEnchantLevel();
				}
				_weaponMaterial = weapon.getItem().getMaterial();
				if (_weaponType == 20) { // 화살 취득
					_arrow = _pc.getInventory().getArrow();
					if (_arrow != null) {
						_weaponBless = _arrow.getItem().getBless();
						_weaponMaterial = _arrow.getItem().getMaterial();
					}
				}
				if (_weaponType == 62) { // 스팅 취득
					_sting = _pc.getInventory().getSting();
					if (_sting != null) {
						_weaponBless = _sting.getItem().getBless();
						_weaponMaterial = _sting.getItem().getMaterial();
					}
				}
				_weaponDoubleDmgChance = weapon.getItem().getDoubleDmgChance(); // 다크엘프 이팩트 찬스 
				_weaponAttrLevel = weapon.getAttrEnchantLevel();                // 속성 인챈트 
			}		
		} else if (attacker instanceof L1NpcInstance) {
			_npc = (L1NpcInstance) attacker;
			if (target instanceof L1PcInstance) {
				_targetPc = (L1PcInstance) target;
				_calcType = NPC_PC;
			} else if (target instanceof L1NpcInstance) {
				_targetNpc = (L1NpcInstance) target;
				_calcType = NPC_NPC;
			}
		}
		_target = target;
		_targetId = target.getId();
		_targetX = target.getX();
		_targetY = target.getY();
	}
	/**
	 * 명중 초기 설정
	 * @return
	 */
	public boolean calcHit() {	

		if (_target.hasSkillEffect(SkillId.앱솔루트_배리어) || _target.hasSkillEffect(SkillId.아이스_랜스) || _target.hasSkillEffect(SkillId.상태_동결)) {
			return _isHit = false;
		}
		// 타겟 스킬 효과 처리
		if(_calcType == PC_PC || _calcType == PC_NPC) {
			if (_pc == null || _target == null) {
				return _isHit; 
			}
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				if (!_pc.glanceCheck(_targetX, _targetY) || !_target.glanceCheck(_pc.getX(), _pc.getY())) {
					return _isHit = false; // 공격자가 플레이어의 경우는 장애물 판정
				} 
			} else {
				if (!_npc.glanceCheck(_targetX, _targetY) || !_target.glanceCheck(_npc.getX(), _npc.getY()))
					return _isHit = false; // 공격자가 플레이어의 경우는 장애물 판정
			}
			if (_weaponType == 20 && _weaponId != ItemId.사이하_활 && _weaponId != ItemId.가이아_격노 && _arrow == null) {
				_isHit = false; // 화살이 없는 경우는 미스
			} else if (_weaponType == 62 && _sting == null) {
				_isHit = false; // 스팅이 없는 경우는 미스
			} else if (!_pc.glanceCheck(_targetX, _targetY)) { 
				_isHit = false; // 공격자가 플레이어의 경우는 장애물 판정
			} else if (_calcType == PC_PC) {
				_isHit = calcPcPcHit();
			} else if (_calcType == PC_NPC) {
				if (_targetNpc.getNpcId() == 45003 || _targetNpc.getNpcId() == 10345796) {
					_isHit = true;
				} else {
					_isHit = calcPcNpcHit();
				}
			} 
		} else if (_calcType == NPC_PC) {
			_isHit = calcNpcPcHit();
		} else if (_calcType == NPC_NPC) {
			_isHit = calcNpcNpcHit();
		} 


		return _isHit;
	}
	/**
	 * 플레이어의 공격 모션을 송신한다.
	 * @return
	 */
	public void action() {
		try {
			switch(_calcType) {
			case PC_PC: case PC_NPC: actionPc(); break;
			case NPC_PC: case NPC_NPC: actionNpc();  break;
			}
		} catch (Exception e) {}
	}
	/**
	 * 플레이어, 엔피시의 데미지를 계산해서 돌려준다.
	 * @return
	 */
	public int calcDamage() {
		try {
			switch (_calcType) {
			case PC_PC: _damage = calcPcPcDamage(); break;
			case PC_NPC: _damage = calcPcNpcDamage(); break;
			case NPC_PC: _damage = calcNpcPcDamage(); break;
			case NPC_NPC: _damage = calcNpcNpcDamage(); break;
			}
		} catch (Exception e) {}
		return _damage <= 0 ? 0 : _damage;
	}

	/**
	 * 플레이어 대 플레이어 피격 명중을 계산 판정한다.
	 */
	private boolean calcPcPcHit() {
		boolean 활타입체크 = _weaponType == 20 || _weaponType == 62;
		if(활타입체크) {
			_hitRate = (CalcUtil.getRangedHitStat(_pc.getAbility().getTotalDex()) + _pc.getRangedLevelHit() + _pc.getRangedHit() + (_weaponEnchant / 2)) - _targetPc.getEr();
		} else {
			_hitRate = (CalcUtil.getMeleeHitStat(_pc.getAbility().getTotalStr()) + _pc.getMeleeLevelHit() + _pc.getMeleeHit() + (_weaponEnchant / 2)) - _targetPc.getDg();		
		}
		if (_targetPc.getAC().getAc() < 0) {
			if(_targetPc.getAC().getAc() >= -100) {
				_hitRate += 10;
			}
			_hitRate += (int) ((-10 + _targetPc.getAC().getAc()) * 0.30D);
		} else {
			_hitRate -= (int) ((10 - _targetPc.getAC().getAc()) * 0.30D);
		}
		
		// 마안, 구현부
		if(_targetPc.hasSkillEffect(SkillId.지룡_마안)) {
			if(CommonUtil.randomRange(1, 100) <= 10) {
				_hitRate -= 10;
			}
		}
		
		// 최소 최대 명중율 계산
		if (_hitRate > 95) {
			_hitRate = 95;
		} else if (_hitRate < 5) {
			_hitRate = 5;
		}
		
		if (_pc.getAutoPotionUse()) {
			if (!_pc.isGm()) {
				_pc.EndAutoPotionController();
				_pc.setAutoPotionUse(false);
				_pc.sendPackets("[PvP]자동 물약을 종료합니다.");
			}
		}
		
		if (_targetPc.getAutoPotionUse()) {
			if (!_targetPc.isGm()) {
				_targetPc.EndAutoPotionController();
				_targetPc.setAutoPotionUse(false);
				_targetPc.sendPackets("[PvP]자동 물약을 종료합니다.");
			}
		}
		
		// 미스 이팩트 처리 
		if(CommonUtil.randomRange(1, 100) <= _hitRate) {
			return true;
		} else {
			if(!_targetPc.isInvisble()) {
				_targetPc.sendPackets(S_SkillSound.get(_targetPc.getId(),EffectUtil.미스_이팩트), true);	
			}		
			return false;
		}
	}
	/**
	 * 플레이어 대 엔피시 피격 명중을 계산 판정한다.
	 */
	private boolean calcPcNpcHit() {
		boolean 활타입체크 = _weaponType == 20 || _weaponType == 62;
		if (활타입체크) {
			_hitRate = CalcUtil.getRangedHitStat(_pc.getAbility().getTotalDex()) + _pc.getRangedLevelHit() + _pc.getRangedHit() + (_weaponEnchant / 2);
		} else {
			_hitRate = CalcUtil.getMeleeHitStat(_pc.getAbility().getTotalStr()) + _pc.getMeleeLevelHit() + _pc.getMeleeHit() + (_weaponEnchant / 2);
		}
		if (_targetNpc.getAC().getAc() < 0) {
			_hitRate += (int) ((-10 + _targetNpc.getAC().getAc()) * 0.30D);
		} else {
			_hitRate -= (int) ((10 - _targetNpc.getAC().getAc()) * 0.30D);
		}
		// 최소 최대 명중율 계산
		if (_hitRate > 95) {
			_hitRate = 95;
		} else if (_hitRate < 5) {
			_hitRate = 5;
		}	
		return CommonUtil.randomRange(1, 100) <= _hitRate;
	}
	/**
	 * 엔피시 대 플레이어 피격 명중을 계산 판정한다.
	 */
	private boolean calcNpcPcHit() {
		// 몬스터의 거리가 4칸 이상인 경우 원거리로 판단한다.
		boolean 거리체크 = (_npc.getNpcTemplate().get_ranged() > 4);

		if (_npc instanceof PetInstance || _npc instanceof SummonInstance) {
			_hitRate += _npc.getLevel() * 2;
		} else {
			if(거리체크) {
				_hitRate = (_npc.getNpcTemplate().get_dex() + _npc.getRangedHit() + _npc.getLevel()) - _targetPc.getEr();
			} else {
				_hitRate = (_npc.getNpcTemplate().get_str() + _npc.getMeleeHit() + _npc.getLevel()) - _targetPc.getDg();
			}
		}		
		if (_targetPc.getAC().getAc() < 0) {
			if(_targetPc.getAC().getAc() >= -200) {
				_hitRate += 30;
			}
			_hitRate += (int) ((-10 + _targetPc.getAC().getAc()) * 0.30D);
		} else {
			_hitRate -= (int) ((10 - _targetPc.getAC().getAc()) * 0.30D);
		}
		// 마안, 구현부
		if(_targetPc.hasSkillEffect(SkillId.지룡_마안)) {
			if(CommonUtil.randomRange(1, 100) <= 10) {
				_hitRate -= 10;
			}
		}
		// 최소 최대 명중율 계산
		if (_hitRate > 100) {
			_hitRate = 100;
		}
		if (CommonUtil.randomRange(1, 100) <= _hitRate) {
			return true;
		} 
		else {
			if(!_targetPc.isInvisble()) {
				_targetPc.sendPackets(S_SkillSound.get(_targetPc.getId(),EffectUtil.미스_이팩트), true);	
			}
			return false;
		}
	}
	// ●●●● NPC 로부터 NPC 에의 명중 판정 ●●●●
	private boolean calcNpcNpcHit() {
		// 몬스터의 거리가 4칸 이상인 경우 원거리로 판단한다.
		boolean 거리체크 = (_npc.getNpcTemplate().get_ranged() > 4);

		if (_npc instanceof PetInstance || _npc instanceof SummonInstance) {
			_hitRate += _npc.getLevel() * 2;
		} else {
			if(거리체크) {
				_hitRate = _npc.getNpcTemplate().get_dex() + _npc.getRangedHit() + _npc.getLevel();
			} else {
				_hitRate = _npc.getNpcTemplate().get_str() + _npc.getMeleeHit() + _npc.getLevel();
			}
		}	
		if (_targetNpc.getAC().getAc() < 0) {
			_hitRate += (int) ((-10 + _targetNpc.getAC().getAc()) * 0.30D);
		} else {
			_hitRate -= (int) ((10 - _targetNpc.getAC().getAc()) * 0.30D);
		}
		// 최소 최대 명중율 계산
		if (_hitRate > 100) {
			_hitRate = 100;
		}
		return CommonUtil.randomRange(1, 100) <= _hitRate;
	}
	/**
	 * 캐릭터의 스텟  데미지를 산출한다.
	 * @return
	 */
	private int calcStatDamage(boolean 활타입체크, int dmg) {
		if(활타입체크) {
			dmg = CalcUtil.getRangedDmgStat(_pc.getAbility().getTotalDex());
		} else {
			dmg = CalcUtil.getMeleeDmgStat(_pc.getAbility().getTotalStr());
		}
		return dmg;
	}

	/**
	 * 캐릭터 무기의 작은 몹 , 큰몹 사이즈를 계산한다.
	 * @return
	 */
	private int calcMonsterSizeDamage(int dmg) {	
		boolean 몬스터사이즈체크 = _targetNpc.getNpcTemplate().get_size().equalsIgnoreCase("large");
		if(몬스터사이즈체크) {
			dmg = _weaponLarge;
		} else {
			dmg = _weaponSmall;
		}
		return dmg;
	}

	/**
	 * 플레이어 대 플레이어 데미지를 산출한다.
	 * @return
	 */
	private int calcPcPcDamage() {
		// 무기 타입 체크
		boolean 활타입체크 = _weaponType == 20 || _weaponType == 62, 
				다크엘프무기타입체크 = (_weaponType == L1Item.TYPE_WEAPONID_EDORYU || _weaponType == L1Item.TYPE_WEAPONID_CLAW);
		// 캐릭터 베이스 데미지 계산
		int 토탈데미지 = 0, 무기데미지 = 0, 무기최대데미지 = 0, 스텟데미지 = calcStatDamage(활타입체크, 토탈데미지),
				속성데미지 = (int) attrEnchantDmg(토탈데미지), 근거리버프데미지 = _pc.getMeleeDmg() + _pc.getBapodmg(),
				원거리버프데미지 = _pc.getRangedDmg() + _pc.getBapodmg(), 무기추가데미지 = _weaponEnchant + _weaponAddDmg, 
				무기작은몹데미지 = _weaponSmall;
		
		if(활타입체크) { // 활인 경우 
			int arrowDmg = 0;
			int stingDmg = 0;			
			무기데미지 =  CommonUtil.randomRange(1, 무기작은몹데미지) + 무기추가데미지 + 속성데미지 + arrowDmg + stingDmg;
			무기최대데미지 = 무기작은몹데미지  + 무기추가데미지 + 속성데미지 + arrowDmg + stingDmg;
			if (_arrow != null) {
				int dmg = _arrow.getItem().getDmgSmall();
				arrowDmg += CommonUtil.randomRange(1, dmg);
			} else if (_weaponId == ItemId.사이하_활 || _weaponId == ItemId.가이아_격노) {
				arrowDmg += CommonUtil.randomRange(1, 12);
			} 
			if (_sting != null) {
				int dmg = _sting.getItem().getDmgSmall();
				stingDmg += CommonUtil.randomRange(1, dmg);
			}
			int 원거리치명타 = (CalcUtil.getRangedCriticalStat(_pc.getType(), _pc.getAbility().getTotalDex(), _pc.getLevel()) + _pc.getRangedCritical()); 
			if (CommonUtil.randomRange(1, 100) <= 원거리치명타) {
				원거리크리티컬 = true;
			} 
			// 발라카스 인내력 구현 원거리 일경우 
			if(_pc.toArmorEquipped().isValakasArmorRegend()) {
				int 인챈트레벨 = _pc.toArmorEquipped().getValakasArmorRegendEn(), 확률 = (인챈트레벨 >= 9 ? 6 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 3);
				if(CommonUtil.randomRange(1, 100) <= 확률) {
					발라원거리가호 = true;
					_pc.sendAll(EffectUtil.발라카스_가호);
				}
			}
			if (원거리크리티컬 || 발라원거리가호) {
				토탈데미지 = 무기최대데미지 + 스텟데미지 + 원거리버프데미지;
				//_pc.sendAll(EffectUtil.일반_크리티컬);
			} else {
				토탈데미지 = 무기데미지 + 스텟데미지 + 원거리버프데미지;
			}
		} else {
			무기데미지 =  CommonUtil.randomRange(1, 무기작은몹데미지) + 무기추가데미지 + 속성데미지;
			무기최대데미지 = 무기작은몹데미지  + 무기추가데미지 + 속성데미지;
			if (_weaponType == 0) { // 맨손 일때 
				토탈데미지 = CommonUtil.randomRange(1, 4);
			} else {				
				if(_pc.isDarkelf()) {
					if (_weaponType == L1Item.TYPE_WEAPONID_CLAW) {
						if(CommonUtil.randomRange(1, 100) <= _weaponDoubleDmgChance) {
							크로우크리티컬 = true;
						}
					}
					else if (_weaponType == L1Item.TYPE_WEAPONID_EDORYU) {
						if(CommonUtil.randomRange(1, 100) <= _weaponDoubleDmgChance) {
							이도류크리티컬 = true;
						}
					}
					if(_pc.hasSkillEffect(SkillId.인챈트_베놈)) {
						if(CommonUtil.randomRange(1, 100) <= Config.인챈트_베놈_발동_확률) {
							DamagePoison.doInfection(_pc, _target, 3000, 5);
						}
					}
					if (_pc.hasSkillEffect(SkillId.더블_브레이크) && 다크엘프무기타입체크) {
						if(CommonUtil.randomRange(1, 100) <= Config.더블_브레이크_발동_확률) {
							더블브레이크 = true;	
						}
					}
					if (_pc.hasSkillEffect(SkillId.버닝_스피릿츠) && 다크엘프무기타입체크) {
						if(CommonUtil.randomRange(1, 100) <= Config.버닝_스피릿츠_발동_확률) {
							버닝스프릿츠 = true;
						}		
					}
				}	
				// 발라카스 완력, 예지력  근거리 일경우 
				if(_pc.toArmorEquipped().isValakasArmorMelee()) {
					int 인챈트레벨 = _pc.toArmorEquipped().getValakasArmorMeleeEn(), 확률 = (인챈트레벨 >= 9 ? 6 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 3);
					if(CommonUtil.randomRange(1, 100) <= 확률) {
						발라근거리가호 = true;
						_pc.sendAll(EffectUtil.발라카스_가호);
					}
				}	
				// 소프 상태 일때 상태 일때
				int 근거리치명타 = (CalcUtil.getMeleeCriticalStat(_pc.getType(), _pc.getAbility().getTotalStr(), _pc.getLevel()) + _pc.getMeleeCritical());
				if(_pc.hasSkillEffect(SkillId.소울_오브_프레임)) {
					소울오브프레임 = true;
				}
				
				//다크엘프는 땁 데미지 로 크리티컬 대상에서 제외 한다
				if (!_pc.isDarkelf()) {
					if (CommonUtil.randomRange(1, 100) <= 근거리치명타) {
						근거리크리티컬 = true;
					}
				}
				
				if (_pc.hasSkillEffect(SkillId.엘리멘탈_파이어)) {
					if(CommonUtil.randomRange(1, 100) <= Config.엘리멘탈_파이어_발동_확률) { 
						엘리멘탈파이어 = true;
					}
				}
				if(크로우크리티컬 && 버닝스프릿츠 && 더블브레이크) {
					토탈데미지 = (int) (((무기최대데미지 * 2) + 스텟데미지 + 근거리버프데미지) * 1.5);
					//_pc.sendAll(EffectUtil.다엘_크리티컬2);
				} else if(크로우크리티컬 && 버닝스프릿츠) {
					토탈데미지 = (int) ((무기최대데미지 + 스텟데미지 + 근거리버프데미지) * 1.5);
					//_pc.sendAll(EffectUtil.다엘_크리티컬);
				} else if(크로우크리티컬 && 더블브레이크) {
					토탈데미지 = (무기최대데미지 * 2) + 스텟데미지 + 근거리버프데미지;
					//_pc.sendAll(EffectUtil.다엘_크리티컬);
				} else if(이도류크리티컬 && 더블브레이크 && 버닝스프릿츠) {
					토탈데미지 = (int) (((무기데미지 * 4) + 스텟데미지 + 근거리버프데미지) * 1.5);
					//_pc.sendAll(EffectUtil.다엘_크리티컬2);
				} else if(이도류크리티컬 && 버닝스프릿츠) {
					토탈데미지 = (int) (((무기데미지 * 2) + 스텟데미지 + 근거리버프데미지) * 1.5);
					//_pc.sendAll(EffectUtil.다엘_크리티컬);
				} else if(이도류크리티컬 && 더블브레이크) {
					토탈데미지 = (무기데미지 * 4) + 스텟데미지 + 근거리버프데미지;
					//_pc.sendAll(EffectUtil.다엘_크리티컬);
				} else if(버닝스프릿츠 && 더블브레이크) {
					토탈데미지 = (int) (((무기데미지 * 2) + 스텟데미지 + 근거리버프데미지) * 1.5);
					//_pc.sendAll(EffectUtil.다엘_크리티컬);
				} else if(버닝스프릿츠 || 엘리멘탈파이어) {
					토탈데미지 = (int) ((무기데미지 + 스텟데미지 + 근거리버프데미지) * 1.5);	
				} else if(더블브레이크 || 이도류크리티컬) {
					토탈데미지 = ((무기데미지 * 2) + 스텟데미지 + 근거리버프데미지);
					//_pc.sendAll(EffectUtil.일반_크리티컬);
				} else if(근거리크리티컬 || 크로우크리티컬 || 발라근거리가호) {
					토탈데미지 = 무기데미지 + 스텟데미지 + 근거리버프데미지;
					//_pc.sendAll(EffectUtil.일반_크리티컬);
				} else if(소울오브프레임) {
					토탈데미지 = 무기최대데미지 + 스텟데미지 + 근거리버프데미지;
				} else {
					토탈데미지 = 무기데미지 + 스텟데미지 + 근거리버프데미지;
				}
				// 아머 브레이크 데미지 판정
				if (_targetPc != null &&_targetPc.hasSkillEffect(SkillId.아머_브레이크)) {
					토탈데미지 *= Config.아머_브레이크;
				}
				// 늑대인간, 크러스트 시안 인형 데미지 
				int probMagicDoll = MagicDoll.getProbDamageAddByDoll(_pc, _targetPc);
				토탈데미지 += probMagicDoll;
			}
		}
		// 이팩트가  존재하는 마법인형
		토탈데미지 += MagicDoll.useSkillByDoll(_pc, _targetPc);
		// 아이템 스킬에 대한 데미지 판정
		ItemSkillModel model = ItemSkillModelLoader.getInstance().getAtk(_weaponId);
		if (model != null) {
			토탈데미지 += model.get(_pc, _targetPc, weapon, 토탈데미지);
		}
		EnchantDamage Lo = WeaponBalanceTable.getInstance().getTemplate(_weaponId);
		int e0 = Lo.get0(), e1 = Lo.get1(), e2 = Lo.get2(), e3 = Lo.get3(),
				e4 = Lo.get4(), e5 = Lo.get5(), e6 = Lo.get6(), e7 = Lo.get7(),
				e8 = Lo.get8(), e9 = Lo.get9(), e10 = Lo.get10(), e11 = Lo.get11(),
				e12 = Lo.get12(), e13 = Lo.get13(), e14 = Lo.get14();
		try{
			if(weapon != null) {
				int enchant = weapon.getEnchantLevel();
				switch(enchant){
				case 0 : 토탈데미지 += e0; break;
				case 1 : 토탈데미지 += e1; break;
				case 2 : 토탈데미지 += e2; break;
				case 3 : 토탈데미지 += e3; break;
				case 4 : 토탈데미지 += e4; break;
				case 5 : 토탈데미지 += e5; break;
				case 6 : 토탈데미지 += e6; break;
				case 7 : 토탈데미지 += e7; break;
				case 8 : 토탈데미지 += e8; break;
				case 9 : 토탈데미지 += e9; break;
				case 10 : 토탈데미지 += e10; break;
				case 11 : 토탈데미지 += e11; break;
				case 12 : 토탈데미지 += e12; break;
				case 13 : 토탈데미지 += e13; break;
				case 14 : 토탈데미지 += e14; break;
				default:
					break;
				}
			}
		} catch (Exception e){
			System.out.println("무기 밸런스 에러! 확인 요망!");
		}	
		// 룸티스 검은빛 귀걸이 구현
		if(_pc.toArmorEquipped().isRoomtisblackArmor()) {
			int 인챈트레벨 = _pc.toArmorEquipped().getRoomtisblackEn(), 확률 = (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 2);
			if (인챈트레벨 > 4 && CommonUtil.randomRange(1, 100) <= 확률) {
				토탈데미지 += 20;
				_pc.sendForMe(EffectUtil.룸티스_검은빛_귀걸이_이팩트);
			}
			// 축복 룸티스 검은빛 귀걸이 구현 
		} else if(_pc.toArmorEquipped().isBlessRoomtisBlackArmor()) {
			int 인챈트레벨 = _pc.toArmorEquipped().getBlessRoomtisBlackEn(), 확률 = (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 3 : 2);
			if (인챈트레벨 > 3 && CommonUtil.randomRange(1, 100) <= 확률) {
				토탈데미지 += 20;
				_pc.sendForMe(EffectUtil.룸티스_검은빛_귀걸이_이팩트);
			}
		}
		commons.getReducIitems(_targetPc, 토탈데미지);
				
		// pvp 데미지 감소 처리 
		토탈데미지 -= (_targetPc.getDmgDownPvP() - _pc.getDmgupPvP());
		// 대미지 리덕션 처리 
		토탈데미지 -= (_targetPc.getDamageReduction() - _pc.getReducCancel());	
		// 이뮨 투함 데미지 감소 처리
		if(_targetPc.hasSkillEffect(SkillId.이뮨_투함)) {
			토탈데미지 *= Config.IMMNUE_TO_HARM_RATE;
		}	
		// 맞는 모션은 존재하나, 데미지는 0 처리.
		if(_targetPc.hasSkillEffect(SkillId.어스_바인드)) {
			return 0;
		}
		// 데미지 0 미스 판정 처리	
		if(토탈데미지 <= 0) {
			_isHit = false;
		}
		return (int) 토탈데미지;
	}
	/**
	 * 플레이어 대 엔피시 데미지를 산출한다..
	 * @return
	 */
	private int calcPcNpcDamage() {
		boolean 활타입체크 = _weaponType == 20 || _weaponType == 62,
				다크엘프무기타입체크 = (_weaponType == L1Item.TYPE_WEAPONID_EDORYU || _weaponType == L1Item.TYPE_WEAPONID_CLAW);
		// 캐릭터 베이스 데미지 계산
		int 토탈데미지 = 0, 무기데미지 = 0, 무기최대데미지 = 0, 스텟데미지 = calcStatDamage(활타입체크, 토탈데미지),
				속성데미지 = (int) attrEnchantDmg(토탈데미지), 근거리버프데미지 = _pc.getMeleeDmg() + _pc.getBapodmg(),
				원거리버프데미지 = _pc.getRangedDmg() + _pc.getBapodmg(), 무기추가데미지 = _weaponEnchant + _weaponAddDmg,
				무기재질데미지 = calcMaterialBlessDmg(활타입체크, 토탈데미지), 무기사이즈데미지 = calcMonsterSizeDamage(토탈데미지);
		
		if(활타입체크) {
			// 화살에 대한 데미지 판정
			int arrowDmg = 0;
			int stingDmg = 0;
			int dmg = 0;
			if (_arrow != null) {	
				if (_targetNpc.getNpcTemplate().get_size().equalsIgnoreCase("large")) {
					dmg += _arrow.getItem().getDmgLarge();
				} else {
					dmg += _arrow.getItem().getDmgSmall();
				}
				if (_targetNpc.getNpcTemplate().isHard()) { // 손상도 몬스터 판별
					dmg /= 2;
				}
				arrowDmg += CommonUtil.randomRange(1, dmg);
			} else if (_weaponId == ItemId.사이하_활 || _weaponId == ItemId.가이아_격노) {
				arrowDmg += CommonUtil.randomRange(1, 12);				
				if (_targetNpc.getNpcTemplate().isHard()) {
					arrowDmg /= 2;
				}
			} 
			if (_sting != null) {
				if (_targetNpc.getNpcTemplate().get_size().equalsIgnoreCase("large")) {
					dmg += _sting.getItem().getDmgLarge();
				} else {
					dmg += _sting.getItem().getDmgSmall();
				}
				if (_targetNpc.getNpcTemplate().isHard()) {
					dmg /= 2;
				}
				stingDmg += CommonUtil.randomRange(1, dmg);
			}
			
			무기데미지 =  CommonUtil.randomRange(1, 무기사이즈데미지) + 무기추가데미지 + 속성데미지 + 무기재질데미지 + arrowDmg + stingDmg;
			무기최대데미지 = 무기사이즈데미지  + 무기추가데미지 + 속성데미지 + 무기재질데미지 + arrowDmg + stingDmg;
			
			int 원거리치명타 = (CalcUtil.getRangedCriticalStat(_pc.getType(), _pc.getAbility().getTotalDex(), _pc.getLevel()) + _pc.getRangedCritical()); 
			if (CommonUtil.randomRange(1, 100) <= 원거리치명타) {
				원거리크리티컬 = true;
			} 
			// 발라카스 인내력 구현 원거리 일경우 
			if(_pc.toArmorEquipped().isValakasArmorRegend()) { // 착용한 아이템이 발라카스 인내력일때
				int 인챈트레벨 = _pc.toArmorEquipped().getValakasArmorRegendEn(), 확률 = (인챈트레벨 >= 9 ? 6 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 3);
				if(CommonUtil.randomRange(1, 100) <= 확률) {
					발라원거리가호 = true;
					_pc.sendAll(EffectUtil.발라카스_가호);
				}
			}
			if (원거리크리티컬 || 발라원거리가호) {
				토탈데미지 = 무기최대데미지 + 스텟데미지 + 원거리버프데미지;
				//_pc.sendAll(EffectUtil.일반_크리티컬);
			} else {
				토탈데미지 = 무기데미지 + 스텟데미지 + 원거리버프데미지;
			}
		} else {
			무기데미지 =  CommonUtil.randomRange(1, 무기사이즈데미지) + 무기추가데미지 + 속성데미지 + 무기재질데미지;
			무기최대데미지 = 무기사이즈데미지  + 무기추가데미지 + 속성데미지 + 무기재질데미지;
			if (_weaponType == 0) { // 맨손 일때 
				토탈데미지 = CommonUtil.randomRange(1, 4);
			} else {
				// 다크엘프 무기 타입 체크
				if(_pc.isDarkelf()) {
					if (_weaponType == L1Item.TYPE_WEAPONID_CLAW) {
						if(CommonUtil.randomRange(1, 100) <= _weaponDoubleDmgChance) {
							크로우크리티컬 = true;
							//_pc.sendAll(EffectUtil.일반_크리티컬);
						}
					}
					else if (_weaponType == L1Item.TYPE_WEAPONID_EDORYU) {
						if(CommonUtil.randomRange(1, 100) <= _weaponDoubleDmgChance) {
							이도류크리티컬 = true;
						}
					}
					if(_pc.hasSkillEffect(SkillId.인챈트_베놈)) {
						if(CommonUtil.randomRange(1, 100) <= Config.인챈트_베놈_발동_확률) {
							DamagePoison.doInfection(_pc, _target, 3000, 5);
						}
					}
					if (_pc.hasSkillEffect(SkillId.더블_브레이크) && 다크엘프무기타입체크) {
						if(CommonUtil.randomRange(1, 100) <= Config.더블_브레이크_발동_확률) {
							더블브레이크 = true;
					//		_targetNpc.sendAll(EffectUtil.더블);
						}
					}
					if (_pc.hasSkillEffect(SkillId.버닝_스피릿츠) && 다크엘프무기타입체크) {
						if(CommonUtil.randomRange(1, 100) <= Config.버닝_스피릿츠_발동_확률) {
							버닝스프릿츠 = true;
					//		_targetNpc.sendAll(EffectUtil.버닝);
						}		
					}
				}	
				// 발라카스 완력, 예지력  근거리 일경우 
				if(_pc.toArmorEquipped().isValakasArmorMelee()) {
					int 인챈트레벨 = _pc.toArmorEquipped().getValakasArmorMeleeEn(), 확률 = (인챈트레벨 >= 9 ? 6 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 3);
					if(CommonUtil.randomRange(1, 100) <= 확률) {
						발라근거리가호 = true;
						_pc.sendAll(EffectUtil.발라카스_가호);
					}
				}
				
				int 근거리치명타 = (CalcUtil.getMeleeCriticalStat(_pc.getType(), _pc.getAbility().getTotalStr(), _pc.getLevel()) + _pc.getMeleeCritical());
				if(_pc.hasSkillEffect(SkillId.소울_오브_프레임)) {
					소울오브프레임 = true;
				}
				
				//다크엘프는 땁 데미지 로 크리티컬 대상에서 제외 한다
				if (!_pc.isDarkelf()) {
					if (CommonUtil.randomRange(1, 100) <= 근거리치명타) {
						근거리크리티컬 = true;
					}
				}
				
				if (_pc.hasSkillEffect(SkillId.엘리멘탈_파이어)) {
					if(CommonUtil.randomRange(1, 100) <= Config.엘리멘탈_파이어_발동_확률) { 
						엘리멘탈파이어 = true;
					}
				}
				// 다크엘프 및 그외 스킬 이팩트 처리.
				if(크로우크리티컬 && 버닝스프릿츠 && 더블브레이크) {
					토탈데미지 = (int) (((무기최대데미지 * 2) + 스텟데미지 + 근거리버프데미지) * 1.5);
					//_pc.sendAll(EffectUtil.다엘_크리티컬2);
				} else if(크로우크리티컬 && 버닝스프릿츠) {
					토탈데미지 = (int) ((무기최대데미지 + 스텟데미지 + 근거리버프데미지) * 1.5);
					//_pc.sendAll(EffectUtil.다엘_크리티컬);
				} else if(크로우크리티컬 && 더블브레이크) {
					토탈데미지 = (무기최대데미지 * 2) + 스텟데미지 + 근거리버프데미지;
					//_pc.sendAll(EffectUtil.다엘_크리티컬);
				} else if(이도류크리티컬 && 더블브레이크 && 버닝스프릿츠) {
					토탈데미지 = (int) (((무기데미지 * 4) + 스텟데미지 + 근거리버프데미지) * 1.8);
					//_pc.sendAll(EffectUtil.다엘_크리티컬2);
				} else if(이도류크리티컬 && 버닝스프릿츠) {
					토탈데미지 = (int) (((무기데미지 * 2) + 스텟데미지 + 근거리버프데미지) * 1.5);
					//_pc.sendAll(EffectUtil.다엘_크리티컬);
				} else if(이도류크리티컬 && 더블브레이크) {
					토탈데미지 = (무기데미지 * 4) + 스텟데미지 + 근거리버프데미지;
					//_pc.sendAll(EffectUtil.다엘_크리티컬);
				} else if(버닝스프릿츠 && 더블브레이크) {
					토탈데미지 = (int) (((무기데미지 * 2) + 스텟데미지 + 근거리버프데미지) * 1.5);
					//_pc.sendAll(EffectUtil.다엘_크리티컬);
				} else if(버닝스프릿츠 || 엘리멘탈파이어) {
					토탈데미지 = (int) ((무기데미지 + 스텟데미지 + 근거리버프데미지) * 1.5);	
				} else if(더블브레이크 || 이도류크리티컬) {
					토탈데미지 = ((무기데미지 * 2) + 스텟데미지 + 근거리버프데미지);
					//_pc.sendAll(EffectUtil.일반_크리티컬);
				} else if(근거리크리티컬) {
					토탈데미지 = 무기최대데미지 + 스텟데미지 + 근거리버프데미지;
					//_pc.sendAll(EffectUtil.일반_크리티컬);
				} else if(크로우크리티컬) {
					토탈데미지 = (무기최대데미지 + 스텟데미지 + 근거리버프데미지);
					//_pc.sendAll(EffectUtil.일반_크리티컬);
				} else if(발라근거리가호 || 소울오브프레임) {
					토탈데미지 = (무기최대데미지 + 스텟데미지 + 근거리버프데미지);
				} else {
					토탈데미지 = 무기데미지 + 스텟데미지 + 근거리버프데미지;
				}
				//	 아머 브레이크 데미지 판정
				if (_targetPc != null &&_targetPc.hasSkillEffect(SkillId.아머_브레이크)) {
					토탈데미지 *= Config.아머_브레이크;
				}
				// 늑대인간, 크러스트 시안 인형 데미지 
				int probMagicDoll = MagicDoll.getProbDamageAddByDoll(_pc, _targetNpc);
				토탈데미지 += probMagicDoll;
			}		
		} 
		// 이팩트가  존재하는 마법인형
		토탈데미지 += MagicDoll.useSkillByDoll(_pc, _targetNpc);

		// 아이템 스킬에 대한 데미지 판정
		ItemSkillModel model = ItemSkillModelLoader.getInstance().getAtk(_weaponId);
		if (model != null) {
			토탈데미지 += model.get(_pc, _targetNpc, weapon, 토탈데미지);
		}	
		EnchantDamage Lo = WeaponBalanceTable.getInstance().getTemplate(_weaponId);
		int e0 = Lo.get0(), e1 = Lo.get1(), e2 = Lo.get2(), e3 = Lo.get3(),
				e4 = Lo.get4(), e5 = Lo.get5(), e6 = Lo.get6(), e7 = Lo.get7(),
				e8 = Lo.get8(), e9 = Lo.get9(), e10 = Lo.get10(), e11 = Lo.get11(),
				e12 = Lo.get12(), e13 = Lo.get13(), e14 = Lo.get14();
		try{
			if(weapon != null) {
				int enchant = weapon.getEnchantLevel();
				switch(enchant) {
				case 0 : 토탈데미지 += e0; break;
				case 1 : 토탈데미지 += e1; break;
				case 2 : 토탈데미지 += e2; break;
				case 3 : 토탈데미지 += e3; break;
				case 4 : 토탈데미지 += e4; break;
				case 5 : 토탈데미지 += e5; break;
				case 6 : 토탈데미지 += e6; break;
				case 7 : 토탈데미지 += e7; break;
				case 8 : 토탈데미지 += e8; break;
				case 9 : 토탈데미지 += e9; break;
				case 10 : 토탈데미지 += e10; break;
				case 11 : 토탈데미지 += e11; break;
				case 12 : 토탈데미지 += e12; break;
				case 13 : 토탈데미지 += e13; break;
				case 14 : 토탈데미지 += e14; break;
				default:
					break;
				}
			}
		} catch (Exception e){
			System.out.println("무기 밸런스 에러! 확인 요망!");
		}
		// 룸티스 검은빛 귀걸이 구현	
		if(_pc.toArmorEquipped().isRoomtisblackArmor()) {
			int 인챈트레벨 = _pc.toArmorEquipped().getRoomtisblackEn(), 확률 = (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 2);
			if (인챈트레벨 > 4 && CommonUtil.randomRange(1, 100) <= 확률) {
				토탈데미지 += 20;
				_pc.sendForMe(EffectUtil.룸티스_검은빛_귀걸이_이팩트);
			}
			// 축복 룸티스 검은빛 귀걸이 구현 
		} else if(_pc.toArmorEquipped().isBlessRoomtisBlackArmor()) {
			int 인챈트레벨 = _pc.toArmorEquipped().getBlessRoomtisBlackEn(), 확률 = (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 3 : 2);
			if (인챈트레벨 > 3 && CommonUtil.randomRange(1, 100) <= 확률) {
				토탈데미지 += 20;
				_pc.sendForMe(EffectUtil.룸티스_검은빛_귀걸이_이팩트);
			}
		}	
		boolean isNowWar = false;
		int castleId = L1CastleLocation.getCastleIdByArea(_targetNpc);
		if (castleId > 0) {
			isNowWar = CastleWarBusiness.getInstance().isNowWar(castleId);
		}
		if (!isNowWar) {
			if (_targetNpc instanceof PetInstance) {
				토탈데미지 /= 8;
			}
			if (_targetNpc instanceof SummonInstance) {
				SummonInstance summon = (SummonInstance) _targetNpc;
				if (summon.isExsistMaster()) {
					토탈데미지 /= 8;
				}
			}
		}	
		// 효과 상태중인 몬스터 깨우려고 추가.
		if (_targetNpc != null &&_targetNpc.hasSkillEffect(SkillId.커스_블라인드) || _targetNpc.hasSkillEffect(SkillId.다크니스)) {
			_targetNpc.removeSkillEffect(SkillId.커스_블라인드);
			_targetNpc.removeSkillEffect(SkillId.다크니스);
		}
		// 몬스터 데미지리덕션 처리 
		토탈데미지 -= calcNpcDamageReduction();

		// 몬스터 데미지 리덕션 판정
		if(_targetNpc.getAC().getAc() < 0) {
			토탈데미지 -= 토탈데미지 *(-10 - _targetNpc.getAC().getAc()) / 265;
		} else {
			토탈데미지 += 토탈데미지 *(10 - _targetNpc.getAC().getAc()) / 265;
		}
		// 맞는 모션은 존재하나, 데미지는 0 처리.
		if(_targetNpc.hasSkillEffect(SkillId.어스_바인드)) {
			return 0;
		}
		
		if (_targetNpc.getNpcId() == 45003 || _targetNpc.getNpcId() == 10345796) {
		} else {
			if (_targetNpc.getCurrentHp() < 토탈데미지) {
				토탈데미지 = _targetNpc.getCurrentHp();
			}
		}
		return (int) 토탈데미지;
	}
	/**
	 * 엔피시 대 플레이어 데미지를 산출한다..
	 * @return
	 */
	private int calcNpcPcDamage() {
		int 몬스터레벨 = _npc.getLevel() / 2, str = _npc.getAbility().getTotalStr(), 
				dex = _npc.getAbility().getTotalDex(), con = _npc.getAbility().getTotalCon();
		int 토탈데미지 = 0;
		// 몬스터의 거리가 4칸 이상인 경우 원거리로 판단한다.
		boolean 거리체크 = (_npc.getNpcTemplate().get_ranged() > 4);

		//  마법 펫 , 물리 펫, 서먼 몬스터  데미지 판정
		if(_npc instanceof PetInstance || _npc instanceof SummonInstance) {
			if(str < 10) {
				if(con <= 10) {
					토탈데미지 += (몬스터레벨 / 8) * 2 + CommonUtil.random(str) * 2;
				} else if(con > 10){
					토탈데미지 += (몬스터레벨 / 8) * 2 + CommonUtil.random(str) * 2;
				}
			} else if(str >= 10) {
				if(con <= 10) {
					토탈데미지 += (몬스터레벨 / 4) * 2 + CommonUtil.random(str) * 2;		
				} else if(con > 10) {
					토탈데미지 += (몬스터레벨 / 4) * 2 + CommonUtil.random(str) * 2;
				}
			} 
		} else { // 몬스터 데미지 
			if(거리체크) { 
				토탈데미지 += (CommonUtil.random(몬스터레벨)) + dex + (몬스터레벨 * 몬스터레벨 / 100) + _npc.getRangedDmg();
			} else {
				토탈데미지 += (CommonUtil.random(몬스터레벨)) + str + (몬스터레벨 * 몬스터레벨 / 100) + _npc.getMeleeDmg();
			}
		} 
		
		// 언데드 몬스터 데미지 판정
		if(isUndeadDamage()) {
			토탈데미지 *= 1.1;
		}
		// 몬스터 독 데미지 판정
		addNpcPoisonAttack(_npc, _targetPc);

		// 몬스터 공격력 표현 
		토탈데미지 += getLeverage();

		// 몬스터 웨폰 브레이크 데미지 판정
		if(_npc.isWeaponBreaked()) {
			토탈데미지 /= 2;
		}
		// 펫, 서먼 데미지 판정
		boolean isNowWar = false;
		int castleId = L1CastleLocation.getCastleIdByArea(_targetPc);
		if (castleId > 0) {
			isNowWar = CastleWarBusiness.getInstance().isNowWar(castleId);
		}
		if (!isNowWar) {
			if (_npc instanceof PetInstance) {
				토탈데미지 /= 8;
			}
			if (_npc instanceof SummonInstance) {
				SummonInstance summon = (SummonInstance) _npc;
				if (summon.isExsistMaster()) {
					토탈데미지 /= 8;
				}
			}
		}	
		
		
		commons.getReducIitems(_targetPc, 토탈데미지);	
		토탈데미지 -= _targetPc.getDamageReduction();

		if (_targetPc.hasSkillEffect(SkillId.이뮨_투함)) {
			토탈데미지 *= Config.IMMNUE_TO_HARM_RATE;
		}
		if (_targetPc.getMapId() == 777 || _targetPc.getMapId() == 778|| _targetPc.getMapId() == 779) {
			토탈데미지 *= 0.3;
		}
		// 맞는 모션은 존재하나, 데미지는 0 처리.
		if(_targetPc.hasSkillEffect(SkillId.어스_바인드)) {
			return 0;
		}
		// 데미지 0 미스 판정 처리	
		if(토탈데미지 <= 0) {
			_isHit = false;
		}
		return (int) 토탈데미지;
	}
	/**
	 * 엔피시 대 엔피시 데미지를 산출한다..
	 * @return
	 */
	private int calcNpcNpcDamage() {
		int 몬스터레벨 = _npc.getLevel() / 2, str = _npc.getAbility().getTotalStr(),
				dex = _npc.getAbility().getTotalDex(), con = _npc.getAbility().getTotalCon();
		double 토탈데미지 = 0;
		// 몬스터의 거리가 4칸 이상인 경우 원거리로 판단한다.
		boolean 거리체크 = (_npc.getNpcTemplate().get_ranged() > 4);
		//  마법 펫 , 물리 펫 데미지 판정
		if (_npc instanceof PetInstance || _npc instanceof SummonInstance) {
			if(str < 10) {
				if(con <= 10) {
					토탈데미지 += (몬스터레벨 / 8) * 2 + CommonUtil.random(str) * 2;
				} else if(con > 10) {
					토탈데미지 += (몬스터레벨 / 8) * 2 + CommonUtil.random(str) * 2;
				}
			} else if(str >= 10) {
				if(con <= 10) {
					토탈데미지 += (몬스터레벨 / 4) * 2 + CommonUtil.random(str) * 2;		
				} else if(con > 10) {
					토탈데미지 += (몬스터레벨 / 4) * 2 + CommonUtil.random(str) * 2;
				}
			}
		} else { // 몬스터 데미지 
			if(거리체크) { 
				토탈데미지 += (CommonUtil.random(몬스터레벨)) + dex + (몬스터레벨 * 몬스터레벨 / 100) + _npc.getRangedDmg();
			} else {
				토탈데미지 += (CommonUtil.random(몬스터레벨)) + str + (몬스터레벨 * 몬스터레벨 / 100) + _npc.getMeleeDmg();
			}
		}  
		if (isUndeadDamage()) {
			토탈데미지 *= 1.1;
		}
		토탈데미지 = 토탈데미지 * getLeverage() / 10;

		if (_npc.isWeaponBreaked()) { // NPC가 웨폰브레이크중.
			토탈데미지 /= 2;
		}
		addNpcPoisonAttack(_npc, _targetNpc);
		// 몬스터 데미지리덕션 처리 
		토탈데미지 -= calcNpcDamageReduction();

		if (_targetNpc != null &&_targetNpc.hasSkillEffect(SkillId.커스_블라인드) || _targetNpc.hasSkillEffect(SkillId.다크니스)) {
			_targetNpc.removeSkillEffect(SkillId.커스_블라인드);
			_targetNpc.removeSkillEffect(SkillId.다크니스);
		}
		// 맞는 모션은 존재하나, 데미지는 0 처리.
		if(_targetNpc.hasSkillEffect(SkillId.어스_바인드)) {
			return 0;
		}
		return (int) 토탈데미지;
	}
	/**
	 * 무기 재질에 따라 데미지를 계산한다.
	 * @return
	 */
	private int calcMaterialBlessDmg(boolean 활타입체크, int dmg) {
		int 언데드타입 = _targetNpc.getNpcTemplate().getUndead();
		//무기 재질 타입 ,언데드 계열 몬스터 체크 
		if(활타입체크 && !(_weaponId == ItemId.사이하_활) || !(_weaponId == ItemId.가이아_격노)) {		
			if(_arrow != null) {
				if(_arrow.getItemId() == ItemId.오리하루콘_화살 
						|| _arrow.getItemId() == ItemId.미스릴_화살 || _arrow.getItemId() == ItemId.은화살) {
					if(언데드타입 == 1 || 언데드타입 == 2 || 언데드타입 == 3) {
						dmg += CommonUtil.randomRange(1, 4);
					}
				}
			} else if(_sting != null) {
				if(_sting.getItemId() == ItemId.실버_스팅) {
					if(언데드타입 == 1 || 언데드타입 == 2 || 언데드타입 == 3) {
						dmg += CommonUtil.randomRange(1, 4);
					}	
				}
			}
		} else {
			if (_weaponMaterial == L1Item.TYPE_MATARIAL_SILVER 
					|| _weaponMaterial ==  L1Item.TYPE_MATARIAL_MITHRIL 
					|| _weaponMaterial == L1Item.TYPE_MATARIAL_ORIHARUKON) {
				if(언데드타입 == 1 || 언데드타입 == 3) {
					if(GameTimeClock.getInstance().getGameTime().isNight()) { // 밤일 경우
						dmg += CommonUtil.randomRange(1, 10);
					} else { // 밤이 아닐 경우		
						dmg += CommonUtil.randomRange(1, 20);
					}	
				}			
			}
			// 블레스 타입 언데드 ,데몬 계열 몬스터 체크
			if (_weaponBless == 0) {
				if(언데드타입 == 1 || 언데드타입 == 2 || 언데드타입 == 3) {
					dmg += CommonUtil.randomRange(3, 6);
				}
			}
			// 스킬 체크 
			if(_pc.hasSkillEffect(SkillId.홀리_웨폰)) {
				dmg += 1;
			}
		}
		return dmg;
	}
	/**
	 * 엔피시의 야간에 따른 공격력을 변화 시킨다.
	 * @return
	 */
	private boolean isUndeadDamage() {
		boolean flag = false;
		int undead = _npc.getNpcTemplate().getUndead();
		boolean isNight = GameTimeClock.getInstance().getGameTime().isNight();
		if (isNight && (undead == 1 || undead == 3)) { // 18~6시, 언데드, 보스몬스터
			flag = true;
		}
		return flag;
	}
	/**
	 * 엔피시의 데미지 독 공격을 부여한다. 
	 * @return
	 */
	private void addNpcPoisonAttack(L1Character attacker, L1Character target) {
		int poisonType = _npc.getNpcTemplate().get_poisonatk();
		if (poisonType == 0) {
			return;
		}
		if (CommonUtil.randomRange(1, 100) <= 10) {
			if (poisonType == 1) {
				DamagePoison.doInfection(attacker, target, 3000, 5);
			} else if (poisonType == 2) {
				SilencePoison.doInfection(target, 50);
			} else if (poisonType == 4) {
				CurseParalysis.curse(target, 16000, 8000);
			}
		}
	}
	private void actionPc() {		
		_pc.setHeading(_pc.targetDirection(_targetX, _targetY)); // 방향세트
		if (_weaponType == 20) {
			if(_arrow != null) {
				_pc.getInventory().removeItem(_arrow, 1);
				int sprId = _pc.getCurrentSpriteId(), arrowEffect = 0;		
				switch(sprId) {
				case 7968: case 7967: arrowEffect = EffectUtil.화살_이미지_1; break;
				case 8842: case 8900: case 16002: arrowEffect = EffectUtil.화살_이미지_2; break;
				case 8845: case 8913: case 16074: arrowEffect = EffectUtil.화살_이미지_4; break;
				case 15814: arrowEffect = EffectUtil.화살_이미지_5; break;
				case 13635: arrowEffect = EffectUtil.화살_이미지_6; break;
				case 13631: arrowEffect = EffectUtil.화살_이미지_7; break;
				case 17535: arrowEffect = EffectUtil.화살_이미지_8; break;
				case 18352: arrowEffect = EffectUtil.화살_이미지_9; break; //달의 질리언
				case 18330: arrowEffect = EffectUtil.화살_이미지_10; break; //가드리아
				case 18320: arrowEffect = EffectUtil.화살_이미지_1; break; //기르타스
				default: arrowEffect = EffectUtil.화살_이미지_3; break; }
				S_UseArrowSkill ua = S_UseArrowSkill.get(_pc, _targetId, arrowEffect, _targetX, _targetY, _isHit);
				_pc.sendPackets(ua);
				Broadcaster.broadcastPacket(_pc, ua);				
				if (_isHit) {
					Broadcaster.broadcastPacketExceptTargetSight(_target, S_DoActionGFX.get(_targetId, ActionCodes.ACTION_Damage), _pc);
				}
			} else if (_weaponId == ItemId.사이하_활 || _weaponId == ItemId.가이아_격노) {
				S_UseArrowSkill ua = S_UseArrowSkill.get(_pc, _targetId, 2349, _targetX, _targetY, _isHit);
				_pc.sendPackets(ua);
				Broadcaster.broadcastPacket(_pc, ua);		
				if (_isHit) {
					Broadcaster.broadcastPacketExceptTargetSight(_target, S_DoActionGFX.get(_targetId, ActionCodes.ACTION_Damage), _pc);
				}
			}
		} else if (_weaponType == 62 && _sting != null) {
			_pc.getInventory().removeItem(_sting, 1);
			int sprId = _pc.getCurrentSpriteId(), stingEffect = 0;
			switch(sprId) {
			case 7968: case 7967: stingEffect = EffectUtil.화살_이미지_1; break;
			case 8842: case 8900: case 16002: stingEffect = EffectUtil.화살_이미지_2; break;
			case 8845: case 8913: case 16074: stingEffect = EffectUtil.화살_이미지_4; break;
			case 15814: stingEffect = EffectUtil.화살_이미지_5; break;
			case 13635: stingEffect = EffectUtil.화살_이미지_6; break;
			case 13631: stingEffect = EffectUtil.화살_이미지_7; break;
			case 17535: stingEffect = EffectUtil.화살_이미지_8; break;
			case 18352: stingEffect = EffectUtil.화살_이미지_9; break; //달의 질리언
			case 18330: stingEffect = EffectUtil.화살_이미지_10; break; //가드리아
			case 18320: stingEffect = EffectUtil.화살_이미지_1; break; //기르타스
			default: stingEffect = EffectUtil.스팅_이미지; break; }
			S_UseArrowSkill ua = S_UseArrowSkill.get(_pc, _targetId, stingEffect, _targetX, _targetY, _isHit);
			_pc.sendPackets(ua);
			Broadcaster.broadcastPacket(_pc, ua);						
			if (_isHit) {
				Broadcaster.broadcastPacketExceptTargetSight(_target, S_DoActionGFX.get(_targetId, ActionCodes.ACTION_Damage), _pc);
			}
		} else {
			if(이도류크리티컬 || 크로우크리티컬) {
				S_AttackPacket attack = S_AttackPacket.cri(_pc, _targetId, _weaponType, _attackType);
				_pc.sendPackets(attack);
				Broadcaster.broadcastPacket(_pc, attack);
				Broadcaster.broadcastPacketExceptTargetSight(_target, S_DoActionGFX.get(_targetId, ActionCodes.ACTION_Damage), _pc);
			} else {
				if (_isHit) {
					S_AttackPacket attack = S_AttackPacket.get(_pc, _targetId, _weaponType, _attackType);
					_pc.sendPackets(attack);
					Broadcaster.broadcastPacket(_pc, attack);
					Broadcaster.broadcastPacketExceptTargetSight(_target, S_DoActionGFX.get(_targetId, ActionCodes.ACTION_Damage), _pc);
				} else {
					if (_targetId > 0) {
						S_AttackMissPacket missPacket = S_AttackMissPacket.miss(_pc, _targetId, _weaponType);
						_pc.sendPackets(missPacket);
						Broadcaster.broadcastPacket(_pc, missPacket);
					} else {
						S_AttackPacket miss = S_AttackPacket.get(_pc, 0, ActionCodes.ACTION_Attack);
						_pc.sendPackets(miss);
						Broadcaster.broadcastPacket(_pc, miss);
					}
				}
			}
		}
	}
	private void actionNpc() {
		int _npcObjectId = _npc.getId(), bowActId = 0, actId = 0;
		_npc.setHeading(_npc.targetDirection(_targetX, _targetY)); // 방향세트

		// 타겟과의 거리가 2이상 있으면 원거리 공격
		boolean isLongRange = (_npc.getLocation().getTileLineDistance(new Point(_targetX, _targetY)) > 1);
		bowActId = _npc.getNpcTemplate().getBowActId();
		int sprId = _npc.getCurrentSpriteId();
		if (getActId() > 0) {
			actId = getActId();
		} 
		else if(sprId == 7558) {
			if(CommonUtil.randomRange(1, 100) <= 50) {
				actId = ActionCodes.ACTION_AxeAttack;
			} else {
				actId = ActionCodes.ACTION_SwordAttack;
			}		
		}
		else {
			actId = ActionCodes.ACTION_Attack;
		}
		if (_isHit) {
			// 거리가 2이상, 공격자의 활의 액션 ID가 있는 경우는 원공격
			if (isLongRange && bowActId > 0) {
				_npc.broadcastPacket(S_UseArrowSkill.get(_npc, _targetId, bowActId, _targetX, _targetY));
			} else {
				if (getGfxId() > 0) {
					_npc.broadcastPacket(S_UseAttackSkill.gett(_target, _npcObjectId, getGfxId(), _targetX, _targetY, actId, 6, true), _target);
					_target.broadcastPacketExceptTargetSight(S_DoActionGFX.get(_targetId, ActionCodes.ACTION_Damage), _npc);
				} else {	
					_npc.broadcastPacket(S_AttackPacket.get(_target, _npcObjectId, actId), _target);
					_target.broadcastPacketExceptTargetSight(S_DoActionGFX.get(_targetId, ActionCodes.ACTION_Damage), _npc);
				}
			}
		} else {
			// 거리가 2이상, 공격자의 활의 액션 ID가 있는 경우는 원공격
			if (isLongRange && bowActId > 0) {
				_npc.broadcastPacket(S_UseArrowSkill.get(_npc, 0, bowActId, _targetX, _targetY));
			} else {
				if (getGfxId() > 0) {
					_npc.broadcastPacket(S_UseAttackSkill.gett(_target, _npcObjectId, getGfxId(), _targetX, _targetY, actId, 0, true),_target);
				} else {
					_npc.broadcastPacket(S_AttackMissPacket.miss(_npc, _targetId, actId), _target);
				}
			}
		}
	}

	/**
	 * 데미지 계산 결과를 반영한다.
	 */
	public void commit() {
		if (_isHit) {
			switch(_calcType) {		
			case PC_PC: _targetPc.receiveDamage(_pc, _damage); break;
			case NPC_PC: _targetPc.receiveDamage(_npc, _damage); break;
			case PC_NPC: damageNpcWeaponDurability(); _targetNpc.receiveDamage(_pc, _damage); break;
			case NPC_NPC: _targetNpc.receiveDamage(_npc, _damage); break;
			}			
		}
		// 데미지치 및 명중율 확인용 메세지
		if (!Config.ALT_ATKMSG) {
			return;
		}
		if (_target == null) {
			return;
		}
		if (_pc != null && _pc.isGm()) {
			StringBuffer sb = new StringBuffer();
			sb.append("\\fV[").append(_pc.getName()).append("]=>[")
			.append(_target.getName()).append("] \\fW[D:").append(_damage)
			.append("]\\f:[P:").append(_target.getCurrentHp())
			.append("]")
			.append("\\fT [").append(_hitRate).append("%]");
			_pc.sendPackets(new S_SystemMessage(sb.toString()), true);
			sb = null;
		}
		if (_targetPc != null && _targetPc.isGm()) {
			StringBuffer sb = new StringBuffer();
			sb.append("\\fY[").append(_target.getName()).append("]<=[")
			.append((_pc == null ? _npc.getName() : _pc.getName()))
			.append("] \\fW[D:").append(_damage).append("] \\f:[P:")
			.append(_target.getCurrentHp()).append("]")
			.append("\\fT [").append(_hitRate).append("%]");
			_targetPc.sendPackets(new S_SystemMessage(sb.toString()), true);
			sb = null;
		}	
	} 
	/**
	 * 카운터 배리어의 공격 모션을 송출한다.
	 */
	public void actionCounterBarrier() {
		if (_calcType == PC_PC) {
			_pc.setHeading(_pc.targetDirection(_targetX, _targetY)); // 방향세트
			S_AttackMissPacket s = S_AttackMissPacket.miss(_pc, _targetId);
			_pc.sendPackets(s);
			_pc.broadcastPacket(s);
			S_DoActionGFX s1 = S_DoActionGFX.get(_pc.getId(), ActionCodes.ACTION_Damage);
			_pc.sendPackets(s1);
			_pc.broadcastPacket(s1);
			S_SkillSound s2 = S_SkillSound.get(_targetId, EffectUtil.카운터_배리어_이팩트);
			_pc.sendPackets(s2);
			_pc.broadcastPacket(s2);
		} else if (_calcType == NPC_PC) {
			int actId = 0;
			_npc.setHeading(_npc.targetDirection(_targetX, _targetY)); // 방향세트
			if (getActId() > 0) {
				actId = getActId();
			} else {
				actId = ActionCodes.ACTION_Attack;
			}
			if (getGfxId() > 0) {
				_npc.broadcastPacket(S_UseAttackSkill.gett(_target, _npc.getId(), getGfxId(), _targetX, _targetY, actId, 0, true));
			} else {
				_npc.broadcastPacket(S_AttackMissPacket.miss(_npc, _targetId, actId));
			}
			_npc.broadcastPacket(S_DoActionGFX.get(_npc.getId(), ActionCodes.ACTION_Damage));
			_npc.broadcastPacket(S_SkillSound.get(_targetId, EffectUtil.카운터_배리어_이팩트));

		}
	}
	/**
	 * 타겟의 공격 상태에 따라 카운터 배리어가 유효한지 체크한다.
	 */
	public boolean isShortDistance() {
		boolean isShortDistance = true;
		if (_calcType == PC_PC) {
			if (_weaponType == 20 || _weaponType == 62) { // 활이나 건틀렛
				isShortDistance = false;
			}
		} else if (_calcType == NPC_PC) {
			boolean isLongRange = (_npc.getLocation().getTileLineDistance(new Point(_targetX, _targetY)) > 1);
			int bowActId = _npc.getNpcTemplate().getBowActId();
			if (isLongRange && bowActId > 0) {
				isShortDistance = false;
			}
		}
		return isShortDistance;
	}
	

	/**
	 * 카운터 배리어의 데미지를 반영한다.
	 */
	public void commitCounterBarrier() {
		int damage = calcCounterBarrierDamage();
		if (damage == 0) {
			return;
		}
		switch(_calcType) {
		case PC_PC: _pc.receiveDamage(_targetPc, damage); break;
		case NPC_PC: _npc.receiveDamage(_targetPc, damage); break;
		}
	}
	/**
	 * 카운터 배리어의 데미지를 산출한다.
	 */
	private int calcCounterBarrierDamage() {
		int damage = 0;
		ItemInstance weapon = null;
		weapon = _targetPc.getWeapon();
		int weaponType = weapon.getItem().getType();
		if (weapon != null && weaponType == 3) { // 양손검
			damage = (weapon.getItem().getDmgLarge() + weapon.getEnchantLevel() + weapon.getItem().getDmgModifier() * 2);
			if (_calcType == NPC_PC) {
				damage = (weapon.getItem().getDmgLarge() + weapon.getEnchantLevel() + weapon.getItem().getDmgModifier() * 2);
			} 
		}
		return damage;
	}
	/**
	 * 무기의 손상확률을 계산한다 .
	 */
	private void damageNpcWeaponDurability() {
		if (_calcType != PC_NPC || _targetNpc.getNpcTemplate().isHard() == false || _weaponType == 0
				|| weapon.getItem().get_canbedmg() == 0 || _pc.hasSkillEffect(SkillId.소울_오브_프레임)) {
			return;
		}
		if (_weaponBless == 1 || _weaponBless == 2) {
			if(CommonUtil.randomRange(1, 100) < 5) {
				_pc.sendPackets(new S_ServerMessage(268, weapon.getLogName()));
				_pc.getInventory().receiveDamage(weapon);
			}		
		}
		else if (_weaponBless == 0) {
			if(CommonUtil.randomRange(1, 100) < 3) {
				_pc.sendPackets(new S_ServerMessage(268, weapon.getLogName()));
				_pc.getInventory().receiveDamage(weapon);
			}
		} 
	}
	/**
	 * 속성 인챈트 대미지를 계산한다.
	 * @param attrDmg
	 * @return
	 */
	private double attrEnchantDmg(int attrDmg) {

		int Attr = _weaponAttrLevel, baseDmg = 0;	
		switch (Attr) { // 순수 속성 인챈트 별 대미지 계산
		case 1: case 4: case 7: case 10: baseDmg += 3; break;
		case 2: case 5: case 8: case 11: baseDmg += 5; break;
		case 3: case 6: case 9: case 12: baseDmg += 7; break;
		}		
		if(_targetPc != null) {
			switch (Attr) { // 타겟 속성에 따른 대미지 감소 처리 , 나의 속성 대미지 계산  버프 그외 
			case 1: case 2: case 3: baseDmg += _pc.getAttrFireDmg();
			int resistFire = _targetPc.getResistance().getFire();
			attrDmg = (int) (baseDmg * (32 - resistFire * 32 / 100) / 32); break;
			case 4: case 5: case 6: baseDmg += _pc.getAttrWaterDmg();
			int resistWater = _targetPc.getResistance().getWater();
			attrDmg = (int) (baseDmg * (32 - resistWater * 32 / 100) / 32); break;
			case 7: case 8: case 9:  baseDmg += _pc.getAttrWindDmg();
			int resistWind = _targetPc.getResistance().getWind();
			attrDmg = (int) (baseDmg * (32 - resistWind * 32 / 100) / 32); break;
			case 10: case 11: case 12:  baseDmg += _pc.getAttrEarthDmg();
			int resistEarth = _targetPc.getResistance().getEarth();
			attrDmg = (int) (baseDmg * (32 - resistEarth * 32 / 100) / 32); break; default: attrDmg = attrDmg <= 0 ? 0 : 0; break;
			}
		} else if(_targetNpc != null) {
			switch (Attr) { // 타겟 속성에 따른 대미지 감소 처리 , 나의 속성 대미지 계산  버프 그외  취약속성 계산
			case 1: case 2: case 3: baseDmg += _pc.getAttrFireDmg();
			int resistEarth = _targetNpc.getNpcTemplate().getWeakEarth();
			attrDmg = (int) (baseDmg * (32 - resistEarth * 12 / 100) / 32); break;
			case 4: case 5: case 6: baseDmg += _pc.getAttrWaterDmg();
			int resistFire = _targetNpc.getNpcTemplate().getWeakFire();
			attrDmg = (int) (baseDmg * (32 - resistFire * 12 / 100) / 32); break;
			case 7: case 8: case 9:  baseDmg += _pc.getAttrWindDmg();
			int resistkWater = _targetNpc.getNpcTemplate().getWeakWater();
			attrDmg = (int) (baseDmg * (32 - resistkWater * 12 / 100) / 32); break;
			case 10: case 11: case 12:  baseDmg += _pc.getAttrEarthDmg();
			int resistWind = _targetNpc.getNpcTemplate().getWeakWind();
			attrDmg = (int) (baseDmg * (32 - resistWind * 12 / 100) / 32); break; default: attrDmg = attrDmg <= 0 ? 0 : 0; break;
			}
		}
		return attrDmg;
	}		
	/**
	 * 일부 몬스터 데미지 감소를 추가하기 위해 만듬.
	 * @return
	 */
	private int calcNpcDamageReduction() {
		return _targetNpc.getNpcTemplate().getDamagereduction();
	}
}
