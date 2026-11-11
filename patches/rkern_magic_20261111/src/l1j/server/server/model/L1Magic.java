package l1j.server.server.model;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import static l1j.server.server.model.character.skill.L1SkillId.*;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.util.Random;
import l1j.server.Config;
import l1j.server.server.model.character.Combat.CCreatureDamageReaction;
import l1j.server.server.model.character.ItemSkill.Doll.MagicDollCombatProcs;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.UserProtectMonsterTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.poison.L1DamagePoison;
import l1j.server.server.model.network.core.sc.packet.SC_OnlyEffect;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.utils.CalcStat;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.eKind;

import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
public class L1Magic { 

    private final boolean ordinarySpell;
    private boolean applyMagicStack;
    private boolean formula2016;
    private int formulaDefenderType, formulaDefenderWis;
    private long formulaEffectiveMr;

	private int _calcType;

	private final int PC_PC = 1;

	private final int PC_NPC = 2;

	private final int NPC_PC = 3;

	private final int NPC_NPC = 4;

	private L1PcInstance _pc = null;

	private L1PcInstance _targetPc = null;

	private L1NpcInstance _npc = null;

	private L1NpcInstance _targetNpc = null;
	
	private int _weaponAttrLevel = 0; // 속성 레벨

	private int _leverage = 10;
	
	private L1ItemInstance weapon = null;

	private static Random _random = new Random(System.nanoTime());

	public boolean _CriticalDamage = false;

	public boolean isCriticalDamage() {
		return _CriticalDamage;
	}

	public void setLeverage(int i) {
		_leverage = i;
	}

	private int getLeverage() {
		return _leverage;
	}

	private double _simsimLeverage = 1;

	public void setSimSimLeverge(double i) {
		_simsimLeverage = i;
	}

	private double getSimSimLeverage() {
		return _simsimLeverage;
	}
	
    /** Legacy/direct/weapon effects retain their existing damage rules. */
    public L1Magic(L1Character attacker, L1Character target) {
        this(attacker, target, false);
    }

    public L1Magic(L1Character attacker, L1Character target, boolean ordinarySpell) {
        this.ordinarySpell = ordinarySpell;
		if (attacker instanceof L1PcInstance) {
			if (target instanceof L1PcInstance) {
				_calcType = PC_PC;
				_pc = (L1PcInstance) attacker;
				_targetPc = (L1PcInstance) target;
			} else {
				_calcType = PC_NPC; 
				_pc = (L1PcInstance) attacker;
				_targetNpc = (L1NpcInstance) target;
			}
		} else {
			if (target instanceof L1PcInstance) {
				_calcType = NPC_PC;
				_npc = (L1NpcInstance) attacker;
				_targetPc = (L1PcInstance) target;
			} else {
				_calcType = NPC_NPC;
				_npc = (L1NpcInstance) attacker;
				_targetNpc = (L1NpcInstance) target;
			}
		}
	}

	private int getSpellPower() {
		int spellPower = 0;
		if (_calcType == PC_PC || _calcType == PC_NPC) {
			spellPower = _pc.getAbility().getSp();
		} else if (_calcType == NPC_PC || _calcType == NPC_NPC) {
			spellPower = _npc.getAbility().getSp();
		}
		return spellPower;
	}

	private int getMagicLevel() {
		int magicLevel = 0;
		if (_calcType == PC_PC || _calcType == PC_NPC) {
			magicLevel = _pc.getAbility().getMagicLevel();
		} else if (_calcType == NPC_PC || _calcType == NPC_NPC) {
			magicLevel = _npc.getAbility().getMagicLevel();
		}
		return magicLevel;
	}
	 private int getMagicHitupByArmor()
	  {
	    int HitupByArmor = 0;
	    if ((this._calcType == 1) || (this._calcType == 2)) {
	      HitupByArmor = this._pc.getMagicHit();
	    } else if ((this._calcType == 3) || (this._calcType == 4)) {
	      HitupByArmor = 0;
	    }
	    return HitupByArmor;
	  }
	private int getMagicBonus() {
		int magicBonus = 0;
		if (_calcType == PC_PC || _calcType == PC_NPC) {
			magicBonus = _pc.getAbility().getMagicBonus();
		} else if (_calcType == NPC_PC || _calcType == NPC_NPC) {
			magicBonus = _npc.getAbility().getMagicBonus();
		}
		return magicBonus;
	}

	private int getLawful() {
		int lawful = 0;
		if (_calcType == PC_PC || _calcType == PC_NPC) {
			lawful = _pc.getLawful();
		} else if (_calcType == NPC_PC || _calcType == NPC_NPC) {
			lawful = _npc.getLawful();
		}
		return lawful;
	}

	private int getTargetMr() {
		int mr = 0;
		if (_calcType == PC_PC || _calcType == NPC_PC) {
			mr = _targetPc.getResistance().getEffectedMrBySkill();
		} else {
			mr = _targetNpc.getResistance().getEffectedMrBySkill();
		}
		return mr;
	}

	/* ■■■■■■■■■■■■■■ 성공 판정[명중] ■■■■■■■■■■■■■ */
	// ●●●● 확률계 마법의 성공 판정 ●●●●
	// 계산방법
	// 공격측 포인트：LV + ((MagicBonus * 3) * 마법 고유 계수)
	// 방어측 포인트：((LV / 2) + (MR * 3)) / 2
	// 공격 성공율：공격측 포인트 - 방어측 포인트
	public boolean calcProbabilityMagic(int skillId) {
		double probability = 0;
		boolean isSuccess = false;

		if (_pc != null && _pc.isGm()) {
			return true;
		}

		if ((_calcType == PC_PC || _calcType == NPC_PC) && _targetPc != null) {
			if (_targetPc.getMagicDodgeProbability() > 0) {
				if (MJRnd.isWinning(100, _targetPc.getMagicDodgeProbability())) {
					SC_OnlyEffect.send(_targetPc, _targetPc.getId(), 10702);
                    SC_OnlyEffect.broadcast(_targetPc, _targetPc.getId(), 10702);
					return false;
				}
			}
		}

		if (_calcType == PC_NPC && _targetNpc != null) {//
			int npcId = _targetNpc.getNpcTemplate().get_npcId();
			if (npcId == 8500138)
				return false;
			if (npcId >= 45912 && npcId <= 45915 && !_pc.hasSkillEffect(STATUS_HOLY_WATER)) {
				return false;
			}
			if (npcId == 45916 && !_pc.hasSkillEffect(STATUS_HOLY_MITHRIL_POWDER)) {
				return false;
			}
			if (npcId == 45941 && !_pc.hasSkillEffect(STATUS_HOLY_WATER_OF_EVA)) {
				return false;
			}
//			if (npcId == 45752 && !_pc.hasSkillEffect(STATUS_CURSE_BARLOG)) {
//				return false;
//			}
//			if (npcId == 45753 && !_pc.hasSkillEffect(STATUS_CURSE_BARLOG)) {
//				return false;
//			}
//			if (npcId == 45675 && !_pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
//				return false;
//			}
//			if (npcId == 81082 && !_pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
//				return false;
//			}
//			if (npcId == 45625 && !_pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
//				return false;
	//		}
		/*	if (npcId == 45674 && !_pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
				return false;
			}
			if (npcId == 45685 && !_pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
				return false;
			}*/
			if (npcId >= 46068 && npcId <= 46091 && _pc.getCurrentSpriteId() == 6035) {
				return false;
			}
			if (npcId >= 46092 && npcId <= 46106 && _pc.getCurrentSpriteId() == 6034) {
				return false;
			}
			if (_targetNpc.getNpcTemplate().get_gfxid() == 7684 && !_pc.hasSkillEffect(PAP_FIVEPEARLBUFF)) {
				return false;
			}
			if (_targetNpc.getNpcTemplate().get_gfxid() == 7805 && !_pc.hasSkillEffect(PAP_MAGICALPEARLBUFF)) {
				return false;
			}
			if (_targetNpc.getNpcTemplate().get_gfxid() == 7720) {
				return false;
			}
		}

		if (_calcType == NPC_NPC) {
			if (_targetNpc.getNpcTemplate().get_npcId() == 8500138)
				return false;
		}

		if (!checkZone(skillId)) {
			return false;
		}
//		if ((_calcType == PC_PC) &&
//				(_targetPc.hasSkillEffect(SHOCK_STUN) || _targetPc.hasSkillEffect(BONE_BREAK) || _targetPc.hasSkillEffect(EMPIRE))){
//					if (skillId == SHOCK_STUN || skillId == BONE_BREAK  || skillId == EMPIRE) {
//				if (Config.연스턴) {
//					_pc.sendPackets(new SC_SystemMessage("111111111111"));
//					        return false;
//					      }
//		}
//				}
		if (skillId == CANCELLATION) {
			if (_calcType == PC_PC && _pc != null && _targetPc != null) {

				if (_pc.getId() == _targetPc.getId()) {
					return true;
				}
				
				if (_pc.getClanid() > 0 && (_pc.getClanid() == _targetPc.getClanid())) {
					SC_SystemMessage.send(_targetPc, "혈맹원 " + _pc.getName() + " 님이 캔슬레이션 마법을 시전했습니다.");
					return true;
				}
				if (_pc.isInParty()) {
					if (_pc.getParty().isMember(_targetPc)) {
						SC_SystemMessage.send(_targetPc, "파티원 " + _pc.getName() + " 님이 캔슬레이션 마법을 시전했습니다.");
						return true;
					}
				}
				
			
				// 대상이 인비지 상태일땐 켄슬 무효
				if (_targetPc.isInvisble()) {
					return false;
				}

				if (_pc.getZoneType() == 1 || _targetPc.getZoneType() == 1) {
					return false;
				}
			}
			if (_calcType == PC_NPC || _calcType == NPC_PC || _calcType == NPC_NPC) {
				return true;
			}
		}

		// 50렙 이상 npc 에게 아래 마법 안걸림.
		if (_calcType == PC_NPC && _targetNpc.getLevel() >= Config.몹레벨 && _targetNpc.getNpcTemplate().isCantResurrect()) {
			if (skillId == WEAPON_BREAK || skillId == SLOW || skillId == CURSE_PARALYZE || skillId == MANA_DRAIN
					|| skillId == WEAKNESS || skillId == DISEASE || skillId == DECAY_POTION  ||  skillId == DESPERADO
					|| skillId == QUAKE || skillId == ERASE_MAGIC || skillId == AREA_OF_SILENCE ||  skillId == EMPIRE
					|| skillId == WIND_SHACKLE || skillId == STRIKER_GALE || skillId == SHOCK_STUN
					|| skillId == FOG_OF_SLEEPING || skillId == ICE_LANCE || skillId == POLLUTE_WATER
					|| skillId == ELEMENTAL_FALL_DOWN || skillId == RETURN_TO_NATURE || skillId == THUNDER_GRAB
					|| skillId == ARMOR_BRAKE || skillId == DARKNESS) {
				return false;
			}
		}
		
		
		 

		/*** 신규레벨보호 ***/
		// if (_calcType == PC_PC) {
		// if (_targetPc.getLevel() < Config.AUTO_REMOVELEVEL || _pc.getLevel()
		// < Config.AUTO_REMOVELEVEL && !_pc.isPinkName()) {
		// if (skillId != EXTRA_HEAL && skillId != HEAL && skillId !=
		// GREATER_HEAL && skillId != HEAL_ALL && skillId != FULL_HEAL
		// && skillId != NATURES_BLESSING) { // 버프계
		// _pc.sendPackets(new SC_SystemMessage("신규보호로 상대방의 마법을 보호받고 있습니다"));
		// _targetPc.sendPackets(new SC_SystemMessage("신규보호로 상대방의 마법을 보호받고
		// 있습니다"));
		// return false;
		// }
		// }
		// }
		/** 신규혈맹 공격안되게 **/
		if (_calcType == PC_PC) {
			if(!(_pc.is_combat_field() || _targetPc.is_combat_field())){
			if (_pc.getClanid() == Config.신규혈맹클랜 || _targetPc.getClanid() == Config.신규혈맹클랜 && !_pc.isPinkName()) {
				if (skillId != EXTRA_HEAL && skillId != HEAL && skillId != GREATER_HEAL && skillId != HEAL_ALL && skillId != FULL_HEAL
						&& skillId != NATURES_BLESSING) { // 버프계

					boolean attack_ok = false;
					for (L1Object obj : L1World.getInstance().getVisibleObjects(_targetPc)) {
						if (!(obj instanceof L1MonsterInstance)) {
							continue;
						}

						if (obj instanceof L1MonsterInstance) {
							L1MonsterInstance mon = (L1MonsterInstance) obj;
							int monid = UserProtectMonsterTable.getInstance().getUserProtectMonsterId(mon.getNpcId());
							if (monid != 0) {
								attack_ok = true;
								break;
							}
						}
					}

					if (!attack_ok) {
						SC_SystemMessage.send(_pc, "중립유저는 상대방의 마법을 보호받고 있습니다");
						SC_SystemMessage.send(_targetPc, "중립유저는 상대방의 마법을 보호받고 있습니다");
						return false;
					}
				}
			}
			}
		}

		if (_calcType == PC_NPC && (_targetNpc.getNpcId() == 5042)) {
			if (skillId == TAMING_MONSTER)
				return false;
		}

		// if (_calcType == PC_NPC && (_targetNpc.getNpcId() == 45684 ||
		// _targetNpc.getNpcId() == 45683 || _targetNpc.getNpcId() == 45682
		// || _targetNpc.getNpcId() == 45681 || _targetNpc.getNpcId() == 81163
		// || _targetNpc.getNpcId() == 81047 || _targetNpc.getNpcId() == 45653))
		// {
		// if (skillId == DARKNESS)
		// return false;
		// }

		// 아스바인드중은 WB, 왈가닥 세레이션 이외 무효
		if (_calcType == PC_PC || _calcType == NPC_PC) {
			
			 
			if (_targetPc.hasSkillEffect(EARTH_BIND)) { //여기 추가 해야 어바 걸린 상태에서 스턴 안들어가는듯 EMPIRE
				if (skillId != WEAPON_BREAK && skillId != CANCELLATION // 확률계
						&& skillId != EXTRA_HEAL && skillId != HEAL && skillId != GREATER_HEAL && skillId != HEAL_ALL && skillId != FULL_HEAL
						&& skillId != NATURES_BLESSING && skillId == MANA_DRAIN || skillId == CURSE_PARALYZE || skillId == THUNDER_GRAB
						|| skillId == ERASE_MAGIC || skillId == SHOCK_STUN || skillId == EARTH_BIND || skillId == BONE_BREAK
						|| skillId == EMPIRE) { // 버프계
					return false;
				}
			}
		} else {
			if (_targetNpc.hasSkillEffect(EARTH_BIND)) {
				if (skillId != WEAPON_BREAK && skillId != CANCELLATION) {
					return false;
				}
			}
		}

		if (_calcType == PC_NPC || _calcType == NPC_NPC) {
			if ((skillId == SILENCE || skillId == AREA_OF_SILENCE) && (_targetNpc.getNpcId() == 45684 || _targetNpc.getNpcId() == 45683
					|| _targetNpc.getNpcId() == 45681 || _targetNpc.getNpcId() == 45682 || _targetNpc.getNpcId() == 900011 || _targetNpc.getNpcId() == 900012
					|| _targetNpc.getNpcId() == 900013 || _targetNpc.getNpcId() == 900038 || _targetNpc.getNpcId() == 900039 || _targetNpc.getNpcId() == 900040
					|| _targetNpc.getNpcId() == 5096 || _targetNpc.getNpcId() == 5097 || _targetNpc.getNpcId() == 5098 || _targetNpc.getNpcId() == 5099
					|| _targetNpc.getNpcId() == 5100)) {
				return false;
			}
		}

		// 100% 확률을 가지는 스킬
		if (skillId == MIND_BREAK || skillId == IllUSION_AVATAR) {
			return true;
		}

		probability = calcProbability(skillId);
		// int rnd = 0;
		// -- 에러가 뜬 이유 . int 형 에서
		int rnd = CommonUtil.random(100);
		switch (skillId) {
		case DARK_BLIND:
		case SHAPE_CHANGE:
		case CANCELLATION:
		case DECAY_POTION:
		case SILENCE:
		case CURSE_PARALYZE:
		case SLOW:
		case DEATH_HEAL:
		case WEAKNESS:
		case CURSE_POISON:
		case CURSE_BLIND:
		case WEAPON_BREAK:
		case MANA_DRAIN:
		case FOG_OF_SLEEPING:
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				if (skillId == 44 && _targetPc.getMr() >= 150) { // -- 캔슬
					if (_targetPc.getMr() >= 200)
						probability = 0;
					else if (_targetPc.getMr() >= 190)
						probability = 1;
					else if (_targetPc.getMr() >= 188)
						probability = 1.2;
					else if (_targetPc.getMr() >= 186)
						probability = 1.4;
					else if (_targetPc.getMr() >= 184)
						probability = 1.6;
					else if (_targetPc.getMr() >= 182)
						probability = 1.8;
					else if (_targetPc.getMr() >= 180)
						probability = 2;
					else if (_targetPc.getMr() >= 178)
						probability = 2.2;
					else if (_targetPc.getMr() >= 176)
						probability = 2.4;
					else if (_targetPc.getMr() >= 174)
						probability = 2.6;
					else if (_targetPc.getMr() >= 172)
						probability = 2.8;
					else if (_targetPc.getMr() >= 170)
						probability = 3;
					else if (_targetPc.getMr() >= 168)
						probability = 3.2;
					else if (_targetPc.getMr() >= 166)
						probability = 3.4;
					else if (_targetPc.getMr() >= 164)
						probability = 3.6;
					else if (_targetPc.getMr() >= 162)
						probability = 3.8;
					else if (_targetPc.getMr() >= 160)
						probability = 4;
					else if (_targetPc.getMr() >= 158)
						probability = 4.2;
					else if (_targetPc.getMr() >= 156)
						probability = 4.4;
					else if (_targetPc.getMr() >= 154)
						probability = 4.6;
					else if (_targetPc.getMr() >= 152)
						probability = 4.8;
					else if (_targetPc.getMr() >= 150)
						probability = 5;

					// -- 캔슬이며, 상대방 마방이 150 이상이라면 probability =
					// CommonUtil.random(1, 5); //-- 확률 조정 바람 1 ~ 5
				} else {
					if (_calcType == PC_PC) {
						rnd += CommonUtil.random(_targetPc.getResistance().getEffectedMrBySkill() + 1);
					} else if (_calcType == PC_NPC) {
						rnd += CommonUtil.random(_targetNpc.getResistance().getEffectedMrBySkill() + 1);
					}
				}
			}
			break;
		default:
			// rnd = _random.nextInt(100) + 1;
			if (probability > 90) {
				probability = 90;
			}
			/*
			if (skillId == L1SkillId.SHOCK_STUN) {
			if (probability < Config.스턴최소) {
				probability = Config.스턴최소;
			}
			}
			*/
			if (skillId == L1SkillId.DESPERADO) {
				if (probability < Config.데페최소) {
					probability = Config.데페최소;
				}
			
			}
			/*
			if (skillId == L1SkillId.EMPIRE) {
				if (probability < Config.엠파최소) {
					probability = Config.엠파최소;
				}
			
			}
			*/
			if (skillId == L1SkillId.BONE_BREAK) {
				if (probability < Config.본브최소) {
					probability = Config.본브최소;
				}
			
			}
			break;
		}
		 if ((skillId >= 1) && (skillId <= 80)) {

		     
		      probability += getMagicHitupByArmor();
		    }

		if (probability >= rnd) { // 마법실패시에도 미쓰 뜨게
			if (_calcType == PC_PC) {
			L1Skills skill = SkillsTable.getInstance().getTemplate(skillId);
			if ((_targetPc.hasSkillEffect(L1SkillId.매직실드1) &&_targetPc.hasSkillEffect(L1SkillId.매직실드2))  && (skillId == L1SkillId.CANCELLATION || skillId == L1SkillId.CURSE_BLIND
					|| skillId == L1SkillId.WEAPON_BREAK || skillId == L1SkillId.CURSE_PARALYZE || skillId == L1SkillId.DARKNESS || skillId == L1SkillId.WEAKNESS || skillId == L1SkillId.SHAPE_CHANGE
					|| skillId == L1SkillId.FOG_OF_SLEEPING || skillId == L1SkillId.DECAY_POTION || skillId == L1SkillId.DEATH_HEAL || skillId == L1SkillId.STRIKER_GALE
					|| skillId == L1SkillId.POLLUTE_WATER || skillId == L1SkillId.AREA_OF_SILENCE || skillId == L1SkillId.SILENCE || skillId == L1SkillId.EARTH_BIND 
					|| skillId == L1SkillId.ELEMENTAL_FALL_DOWN )) {
				isSuccess = false;
				_targetPc.removeSkillEffect(L1SkillId.매직실드1);
				SC_SystemMessage.send(_targetPc, "\\f9[매직실드] 시전자: "+_pc.getName()+" / 디버프면역: \\f3"+skill.getName());
				SC_SkillSound.send(_targetPc, _targetPc.getId(), 14446);
				SC_SkillSound.broadcast(_targetPc, _targetPc.getId(), 14446);
				//System.out.println("444");
				
			} else if (_targetPc.hasSkillEffect(L1SkillId.매직실드2) && (skillId == L1SkillId.CANCELLATION || skillId == L1SkillId.CURSE_BLIND || skillId == L1SkillId.SHAPE_CHANGE
					|| skillId == L1SkillId.WEAPON_BREAK || skillId == L1SkillId.CURSE_PARALYZE || skillId == L1SkillId.DARKNESS || skillId == L1SkillId.WEAKNESS
					|| skillId == L1SkillId.FOG_OF_SLEEPING || skillId == L1SkillId.DECAY_POTION || skillId == L1SkillId.DEATH_HEAL || skillId == L1SkillId.STRIKER_GALE
					|| skillId == L1SkillId.POLLUTE_WATER || skillId == L1SkillId.AREA_OF_SILENCE || skillId == L1SkillId.SILENCE || skillId == L1SkillId.EARTH_BIND 
					|| skillId == L1SkillId.ELEMENTAL_FALL_DOWN )) {
				
				int rnd1 = _random.nextInt(100) + 1;
				if (rnd1 <= Config.매직실드확률) {
					
					SC_SkillSound.send(_targetPc, _targetPc.getId(), 14446);
					SC_SkillSound.broadcast(_targetPc, _targetPc.getId(), 14446);
					SC_SystemMessage.send(_targetPc, "\\f9[매직실드] 시전자: "+_pc.getName()+" / 디버프면역: \\f3"+skill.getName());
					isSuccess = false;
					//System.out.println("333");
			} else {
				SC_SkillSound.send(_targetPc, _targetPc.getId(), 14446);
				SC_SkillSound.broadcast(_targetPc, _targetPc.getId(), 14446);
				SC_SystemMessage.send(_targetPc, "\\f9[매직실드\\f3해제\\f9] 시전자: "+_pc.getName()+" / 디버프면역: \\f3"+skill.getName());
				_targetPc.removeSkillEffect(L1SkillId.매직실드2);
				isSuccess = false;
				//System.out.println("222");
			}
				
			} else {
			
			isSuccess = true;
			//System.out.println("000");
			}
			
			} else {
				isSuccess = true;
				//System.out.println("111");
			}
			
	
	} else {
			if (_calcType == NPC_PC || _calcType == PC_PC) {
				/*
				 * _targetPc.sendPackets(new S_SkillSound(_targetPc.getId(),
				 * 13418)); Broadcaster.broadcastPacket(_targetPc, new
				 * S_SkillSound(_targetPc.getId(), 13418));
				 */
				isSuccess = false;
			} else if (_calcType == PC_NPC) {
				SC_SkillSound.send(_pc, _targetNpc.getId(), 13418);
				// Broadcaster.broadcastPacket(_pc, new
				// S_SkillSound(_targetNpc.getId(), 13418));// 이거는 다른 사람도 보게...
				isSuccess = false;
			}
		}
		if (!isSuccess & skillId == TURN_UNDEAD) {
			if (_calcType == PC_NPC) {
				_targetNpc.setHate(_pc, 1);
				int ran = _random.nextInt(100) + 1;
				if (ran <= 50) {
					SC_SkillSound.broadcast(_targetNpc, _targetNpc.getId(), 8987);
					SC_SkillHaste.broadcast(_targetNpc, _targetNpc.getId(), 1, 0);
					_targetNpc.setMoveSpeed(1);
				}
			}
		}
		 
		if (!Config.ALT_ATKMSG) {
			return isSuccess;
		}
		
		
		
		if (_targetPc == null && _targetNpc == null)
			return isSuccess;

		String msg2 = "확률:" + probability + "%";
		String msg3 = "";
		 int 찬스 = rnd;
		if (isSuccess == true) {
			msg3 = "성공";
		} else {
			msg3 = "실패";
		}

		if (_pc != null && _pc.isGm()) {
			SC_SystemMessage.send(_pc, "\\fD["+ _pc.getName()+ "] ==> ["+ (_targetPc == null ? _targetNpc.getName() : _targetPc
							.getName()) + "][" + msg2 + "("+찬스+") [" + msg3 + "]");
		}
		if (_targetPc != null && _targetPc.isGm()) {
			SC_SystemMessage.send(_targetPc, "\\fZ["+ _targetPc.getName() + "] <== ["+ (_pc == null ? _npc.getName() : _pc.getName()) + "]["
					+ msg2 + "("+찬스+")] [" + msg3 + "]");
		}
		return isSuccess;
	}
		
			
			 

	private boolean checkZone(int skillId) {
		if (_pc != null && _targetPc != null) {
			if (_pc.getZoneType() == 1 || _targetPc.getZoneType() == 1) {
				if (skillId == CURSE_POISON || skillId == CURSE_BLIND || skillId == WEAPON_BREAK || skillId == SLOW || skillId == CURSE_PARALYZE
						|| skillId == MANA_DRAIN || skillId == DARKNESS || skillId == WEAKNESS || skillId == DISEASE || skillId == SILENCE
						|| skillId == FOG_OF_SLEEPING || skillId == DECAY_POTION || skillId == SHOCK_STUN || skillId == ERASE_MAGIC || skillId == EARTH_BIND
						|| skillId == AREA_OF_SILENCE || skillId == WIND_SHACKLE || skillId == POLLUTE_WATER || skillId == STRIKER_GALE
						|| skillId == GUARD_BREAK || skillId == FEAR || skillId == HORROR_OF_DEATH || skillId == ICE_LANCE || skillId == ELEMENTAL_FALL_DOWN
						|| skillId == RETURN_TO_NATURE || skillId == PHANTASM || skillId == CONFUSION || skillId == DESPERADO || skillId == POWERRIP) {
					return false;
				}
			}
		}
		return true;
	}

	private int calcProbability(int skillId) {
		L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillId);
		int attackLevel = 0;
		int defenseLevel = 0;
		int probability = 0;
		int attackInt = 0;
		int defenseMr = 0;
		L1Character attacker = null;
		L1Character receiver = null;

		switch (_calcType) {
		case PC_PC:
			attacker = _pc;
			receiver = _targetPc;
			break;
		case PC_NPC:
			attacker = _pc;
			receiver = _targetNpc;
			break;
		case NPC_PC:
			attacker = _npc;
			receiver = _targetPc;
			break;
		case NPC_NPC:
			attacker = _npc;
			receiver = _targetNpc;
			break;
		}

		if (_calcType == PC_PC || _calcType == PC_NPC) {
			attackLevel = _pc.getLevel();
			attackInt = _pc.getAbility().getTotalInt();
		} else {
			attackLevel = _npc.getLevel();
			attackInt = _npc.getAbility().getTotalInt();
		}

		if (_calcType == PC_PC || _calcType == NPC_PC) {
			defenseLevel = _targetPc.getLevel();
			defenseMr = _targetPc.getResistance().getEffectedMrBySkill();
		} else {
			if (_targetNpc != null) {
				defenseLevel = _targetNpc.getLevel();
				defenseMr = _targetNpc.getResistance().getEffectedMrBySkill();
				if (skillId == RETURN_TO_NATURE) {
					if (_targetNpc instanceof L1SummonInstance) {
						L1SummonInstance summon = (L1SummonInstance) _targetNpc;
						defenseLevel = summon.getMaster().getLevel();
					}
				}
			}
		}
		switch (skillId) {
		/** 이레이즈매직 엘리멘탈폴다운 **/
		/** 동레벨일경우 40% 레벨 아래당 2% 성공확률 상향 레벨 높을때 2% 성공확률 감소 **/
		case AREA_OF_SILENCE:
		case ERASE_MAGIC:
		case ELEMENTAL_FALL_DOWN: {
			int ERASEMAGICPoint = attacker.getResistance().getSpecialPierce(eKind.SPIRIT) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			/*
			if (ERASEMAGICPoint > 0) {
				attackLevel += Math.round(((double) ERASEMAGICPoint / (double) Config.Erase_MagicT));
			}

			if (attackLevel >= defenseLevel)
				probability = (attackLevel - defenseLevel) * 2 + 43;
			else if (attackLevel < defenseLevel) {
				probability = (attackLevel - defenseLevel) * 3 + 43;
			}
			if (probability > 70) {
				probability = 70;
			}
			*/
			int levelp = (attackLevel - defenseLevel)*Config.ngslvpoint;
			int efpoint = 0;
			if (ERASEMAGICPoint > 0) {
				efpoint += Math.round(((double) ERASEMAGICPoint * (double) Config.ngmgpoint));
			}
			probability = (int)  + Config.nggdefault + efpoint;
			probability += levelp;
			if (probability < Config.네쥬스턴최소) {// 기본정의 10
				probability = Config.네쥬스턴최소;
			}
			
			if(probability > Config.네쥬스턴최대){
				probability = Config.네쥬스턴최대;
			}
		}
			break;
		/** 어스바인드 **/
		/** 동레벨일경우 35% 레벨아래당 2% 성공확률 증가, 레벨높을때 3% 성공확률 감소 **/
		case EARTH_BIND: {
			int EARTHBINDPoint = attacker.getResistance().getSpecialPierce(eKind.SPIRIT) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			/*
			if (EARTHBINDPoint > 0) {
				attackLevel += Math.round(((double) EARTHBINDPoint * (double) Config.EARTH_BINDT));
			}

			if (attackLevel >= defenseLevel)
				// 시전자가 대상자보다 레벨이 같거나 높은경우
				probability = (attackLevel - defenseLevel) * 2 + 40;
			else if (attackLevel < defenseLevel) {
				// 시전자가 대상자보다 레벨이 낮은경우
				// ex 84 : 85 = 84 - 85 = -1 * 3 = -3 + 30 = 27
				probability = (attackLevel - defenseLevel) * 3 + 30;
			}
			// 너무 잘걸리면 보정치를 낮춰라
			if (probability > 70) {
				probability = 70;
			}
			*/
			int levelp = (attackLevel - defenseLevel)*Config.ngslvpoint;
			int abpoint = 0;
			if (EARTHBINDPoint > 0) {
				abpoint += Math.round(((double) EARTHBINDPoint * (double) Config.ngmgpoint));
			}
			probability = (int)  + Config.nggdefault + abpoint;
			probability += levelp;
			if (probability < Config.네쥬스턴최소) {// 기본정의 10
				probability = Config.네쥬스턴최소;
			}
			
			if(probability > Config.네쥬스턴최대){
				probability = Config.네쥬스턴최대;
			}
		}
			break;
		/** 스트라이커게일 **/
		/** 레벨아래당 2% 성공확률 증가, 레벨높을때 3% 성공확률 감소 **/
		case STRIKER_GALE: {
			int STRIKERGALEPoint = attacker.getResistance().getSpecialPierce(eKind.SPIRIT) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			/*
			if (STRIKERGALEPoint > 0) {
				attackLevel += Math.round(((double) STRIKERGALEPoint * (double) Config.STRIKER_GALET));
			}

			if (attackLevel >= defenseLevel)
				probability = (attackLevel - defenseLevel) * 2;
			else if (attackLevel < defenseLevel) {
				probability = (attackLevel - defenseLevel) * 3;
			}
			if (_pc.hasSkillEffect(L1SkillId.게일샷)) {
				probability += Config.게일샷확률;
			}
			*/
			//
			
			
			int levelp = (attackLevel - defenseLevel)*Config.ngslvpoint;
			int galepoint = 0;
			if (STRIKERGALEPoint > 0) {
				galepoint += Math.round(((double) STRIKERGALEPoint * (double) Config.ngmgpoint));
			}
			probability = (int)  + Config.nggdefault + galepoint;
			probability += levelp;
			if (probability < Config.네쥬스턴최소) {// 기본정의 10
				probability = Config.네쥬스턴최소;
			}
			
			if(probability > Config.네쥬스턴최대){
				probability = Config.네쥬스턴최대;
			}

		}
			break;
		/** 폴루토워터 **/
		/** 레벨아래당 2% 성공확률 증가, 레벨높을때 3% 성공확률 감소 **/
		case POLLUTE_WATER: {
			int POLLUTEWATERPoint = attacker.getResistance().getSpecialPierce(eKind.SPIRIT) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			/*
			if (POLLUTEWATERPoint > 0) {
				attackLevel += Math.round(((double) POLLUTEWATERPoint * (double) Config.POLLUTE_WATERT));
			}

			if (attackLevel >= defenseLevel)
				probability = (attackLevel - defenseLevel) * 2;
			else if (attackLevel < defenseLevel) {
				probability = (attackLevel - defenseLevel) * 3;
			}

		if (probability < 10) {
			probability = 10;
		}

*/
			int levelp = (attackLevel - defenseLevel)*Config.ngslvpoint;
			int papoint = 0;
			if (POLLUTEWATERPoint > 0) {
				papoint += Math.round(((double) POLLUTEWATERPoint * (double) Config.ngmgpoint));
			}
			probability = (int)  + Config.nggdefault + papoint;
			probability += levelp;
			if (probability < Config.네쥬스턴최소) {// 기본정의 10
				probability = Config.네쥬스턴최소;
			}
			
			if(probability > Config.네쥬스턴최대){
				probability = Config.네쥬스턴최대;
			}
		}
		break;
			/** 윈드세클 **/
			/** 동레벨일경우 30% 레벨아래당 2% 성공확률 증가, 레벨높을때 3% 성공확률 감소 **/
		case WIND_SHACKLE: {
			int WINDSHACKLEPoint = attacker.getResistance().getSpecialPierce(eKind.SPIRIT) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			if (WINDSHACKLEPoint > 0) {
				attackLevel += Math.round(((double) WINDSHACKLEPoint / (double) Config.WIND_SHACKLET));
			}

			if (attackLevel >= defenseLevel)
				probability = (attackLevel - defenseLevel) * 2 + 30;
			else if (attackLevel < defenseLevel) {
				probability = (attackLevel - defenseLevel) * 3 + 30;
			}
//			if (probability > 70) {
//			probability = 70;
//		}
		if (probability < 30) {
			probability = 30;
		}
		}
			break;
		case SHAPE_CHANGE: // 셰이프 본섭 마방140에게 60% - 마방당 -1%
		case CANCELLATION:// 켄슬 본섭 마방100에게 46% - 마방당 -1%
		case SLOW: // 슬로우 본섭 마방100에게 58% - 마방당 -1%
		case DISEASE: // 디지즈 본섭 마방100에게 68% - 마방당 -1%
		case CURSE_PARALYZE:// 패럴라이즈 마방100에게 15%
			// case ICE_LANCE: // 아이스랜스 마방100에게 30%
		case WEAPON_BREAK:// 웨폰브레이크 마방100에게 30%
		case DECAY_POTION: // 디케이포션 마방100에게 23%
		case CURSE_BLIND: // 야매로 15로 설정함
		case CURSE_POISON: // 야매로 50로 설정함
		case SILENCE: // 야매로 35로 설정함
		case FOG_OF_SLEEPING:// 야매로 25로 설정함
		case DEATH_HEAL:
		case DEATH_HEAL_Mob:
//			if (attackInt > 25)
//				attackInt = 25;
//			probability = (int) (attackInt * 4 + l1skills.getProbabilityValue() - defenseMr);
//			if (_pc != null && _pc.isElf() && (_calcType == PC_PC || _calcType == PC_NPC)) {
//				probability -= 30;
//			}
//			if (probability < 1) {
//				probability = 1;
//			}
//			if (probability > 80) {
//				probability = 80;
//			}
			if (attackInt > 25)
				attackInt = 25;
			//probability = (int) (attackInt * 4 + l1skills.getProbabilityValue() - defenseMr);
			probability = (int) (attackInt * 4 + l1skills.getProbabilityValue() - defenseMr);
			if (_pc != null && _pc.isElf() && (_calcType == PC_PC || _calcType == PC_NPC)) {
				probability -= 30;
			}
			if (probability < 1) {
				probability = 1;
			}
			if (probability >= 100) {
				probability = 100;
			}
			/*
			 * SC_SystemMessage.send(_pc, "[확률] -> " + defenseMr + " "
			 * + probability + "%"); System.out.println("[마법확률] -> " +
			 * defenseMr + " " + probability + "%");
			 */
			break;
		case WEAKNESS:
		case ICE_LANCE:
		case DARKNESS:
			if (attackInt > 10)
				attackInt = 10;
			probability = (int) (attackInt * 4 + l1skills.getProbabilityValue() - defenseMr);
			
			if (_pc != null && _pc.isElf() && (_calcType == PC_PC)) {
				probability -= 10;
			}
			if (probability < 5) {
				probability = 5;
			}
			if (probability > 70) {
				probability = 70;
			}

			// _pc.sendPackets(new SC_SystemMessage("[확률] -> " + defenseMr + " "
			// + probability + "%"));
			// System.out.println("[마법확률] -> " + defenseMr + " " + probability +
			// "%");

			break;
		case THUNDER_GRAB:
			int THUNDERGRABPoint = attacker.getResistance().getSpecialPierce(eKind.DRAGON_SPELL) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			/*
			if (THUNDERGRABPoint > 0) {
				attackLevel += Math.round((double) THUNDERGRABPoint);
			}
			probability = (int) Config.THUNDER_GRABT + ((attackLevel - defenseLevel) / 2);
			if (probability < 10) {
				probability = 10;
			}
			if (probability > 100) {
				probability = 100;
			}
			*/
			
			int levelpd = (attackLevel - defenseLevel)*Config.ngslvpoint;
			int grpoints = 0;
			if (THUNDERGRABPoint > 0) {
				grpoints += Math.round(((double) THUNDERGRABPoint * (double) Config.ngmgpoint));
			}
			probability = (int)  + Config.nggdefault + grpoints;
			probability += levelpd;
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				probability += _pc.getImpactUp();
			}
			if (probability < Config.네쥬스턴최소) {
				probability = Config.네쥬스턴최소;
			}
			
			if(probability > Config.네쥬스턴최대){
				probability = Config.네쥬스턴최대;
			}
			break;
//		case COUNTER_BARRIER: // 카운터 배리어
//			probability = l1skills.getProbabilityValue(); // 확률
//			// System.out.print(String.format("카운터 배리어 확률 : 기본(%d)", probability));
//			
//			if (_pc != null && _pc.isPassive(MJPassiveID.COUNTER_BARRIER_VETERAN.toInt())) {
//				int lvl = _pc.getLevel();
//				if (lvl >= 85) {
//					probability += (lvl - 84);
//				}
//			}
//			// System.out.println(String.format(", 최종(%d)", probability));
//			break;
		case GUARD_BREAK:
			int GUARDBREAKPoint = attacker.getResistance().getSpecialPierce(eKind.DRAGON_SPELL) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			/*
			if (GUARDBREAKPoint > 0) {
				attackLevel += Math.round(((double) GUARDBREAKPoint * (double) Config.GUARD_BREAKT));
			}
			probability = (int) Config.가드확률 + ((attackLevel - defenseLevel) * 2);
			if (probability < 15) {
				probability = 15;
			}
			if (probability > 80) {
				probability = 80;
			}
			*/
			int levelpds = (attackLevel - defenseLevel)*Config.ngslvpoint;
			int gdpoints = 0;
			if (GUARDBREAKPoint > 0) {
				gdpoints += Math.round(((double) GUARDBREAKPoint * (double) Config.ngmgpoint));
			}
			probability = (int)  + Config.nggdefault + gdpoints;
			probability += levelpds;
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				probability += _pc.getImpactUp();
			}
			if (probability < Config.네쥬스턴최소) {
				probability = Config.네쥬스턴최소;
			}
			
			if(probability > Config.네쥬스턴최대){
				probability = Config.네쥬스턴최대;
			}
			break;
		case DESPERADO:
			int horroPoint = attacker.getResistance().getSpecialPierce(eKind.FEAR) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			int levelpdss = (attackLevel - defenseLevel)*Config.ngslvpoint;
			int stunpoints = 0;
			if (horroPoint > 0) {
				stunpoints += Math.round(((double) horroPoint * (double) Config.ngmgpoint));
			}
			probability = (int)  + Config.nggdefault + stunpoints;
			probability += levelpdss;
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				probability += _pc.getImpactUp();
			}
			if (probability < Config.네쥬스턴최소) {
				probability = Config.네쥬스턴최소;
			}
			
			if(probability > Config.네쥬스턴최대){
				probability = Config.네쥬스턴최대;
			}
			break;
		case POWERRIP:
			int POWERRIPPoint = attacker.getResistance().getSpecialPierce(eKind.FEAR) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			
			/*
			if (POWERRIPPoint > 0) {
				attackLevel += Math.round(((double) POWERRIPPoint / (double) Config.POWERRIP_T));
			}
			probability = (int) Config.그립확률 + ((attackLevel - defenseLevel) * 5);
			if (probability < 15) {
				probability = 15;
			}
			if (probability > 80) {
				probability = 80;
			}
			*/
			int levelps = (attackLevel - defenseLevel)*Config.ngslvpoint;
			int gpoints = 0;
			if (POWERRIPPoint > 0) {
				gpoints += Math.round(((double) POWERRIPPoint * (double) Config.ngmgpoint));
			}
			probability = (int)  + Config.nggdefault + gpoints;
			probability += levelps;
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				probability += _pc.getImpactUp();
			}
			if (probability < Config.네쥬스턴최소) {
				probability = Config.네쥬스턴최소;
			}
			
			if(probability > Config.네쥬스턴최대){
				probability = Config.네쥬스턴최대;
			}
			break;
		/** 전사스킬 토마호크 : 동레벨일경우 63% 레벨아래당 5% 성공확률 증가, 레벨높을때 3% 성공확률 감소 **/
//		case TOMAHAWK: {
//			int TOMAHAWKPoint = attacker.getResistance().getSpecialPierce(eKind.FEAR) + attacker.getResistance().getSpecialPierce(eKind.ALL);
//			if (TOMAHAWKPoint > 0) {
//				attackLevel += Math.round(((double) TOMAHAWKPoint / (double) Config.TOMAHAWKPoint_T));
//			}
//			if (attackLevel >= defenseLevel)
//				probability = (attackLevel - defenseLevel) * 5 + 63;
//			else if (attackLevel < defenseLevel) {
//				probability = (attackLevel - defenseLevel) * 3 + 63;
//			}
//			if (probability > 90) {
//				probability = 90;
//			}
//		}
//			break;
		case FEAR: // 피어 본섭 22% ~ 42% 베이스 스텟에 의한
			int FEARPoint = attacker.getResistance().getSpecialPierce(eKind.DRAGON_SPELL) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			if (FEARPoint > 0) {
				attackLevel += Math.round(((double) FEARPoint / (double) Config.FEAR_T));
			}
			probability = (int) Config.가드확률 + ((attackLevel - defenseLevel) * 2);
			if (probability < 15) {
				probability = 15;
			}
			if (probability > 80) {
				probability = 80;
			}
			break;
		case HORROR_OF_DEATH: // 인트빨 본섭화
			int HORROROFDEATHPoint = attacker.getResistance().getSpecialPierce(eKind.DRAGON_SPELL) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			if (HORROROFDEATHPoint > 0) {
				attackLevel += Math.round(((double) HORROROFDEATHPoint / (double) Config.HORROR_OF_DEATHT));
			}
			probability = (int) Config.가드확률 + ((attackLevel - defenseLevel) * 5);
			if (probability < 15) {
				probability = 15;
			}
			if (probability > 80) {
				probability = 80;
			}
			break;

		// 컨퓨젼, 판타즘 본섭 30%
		case CONFUSION:
		case PHANTASM:
			int CONFUSION_PHANTASMPoint = attacker.getResistance().getSpecialPierce(eKind.DRAGON_SPELL) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			if (CONFUSION_PHANTASMPoint > 0) {
				attackLevel += Math.round(((double) CONFUSION_PHANTASMPoint / (double) Config.CONFUSION_PHANTASMT));
			}
			if (_calcType == PC_PC) {
				if (_pc instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) _pc;
					L1ItemInstance weapon = _pc.getWeapon();
					if (weapon == null) {
						probability = 0;
						break;
					}
				}
			}
			probability = (int) 30 + (attackLevel - defenseLevel);
			if (probability < 10) {
				probability = 10;
			}
			if (probability > 100) {
				probability = 100;
			}
			
			
			break;
			
			
		case RETURN_TO_NATURE:
			probability = (int) (((l1skills.getProbabilityDice()) / 10D) * (attackLevel - defenseLevel)) + l1skills.getProbabilityValue();
			break;
		case BONE_BREAK:
		
			
			int boneBreakPoint = attacker.getResistance().getSpecialPierce(eKind.DRAGON_SPELL) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			if (boneBreakPoint > 0) {
				attackLevel += Math.round(((double) boneBreakPoint * (double) Config.STUNHIT_TO_LEVELL));
			}

			if (_calcType == PC_PC) {
				if (_pc instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) _pc;
					L1ItemInstance weapon = _pc.getWeapon();
					if (weapon == null) {
						probability = 0;
						break;
					}
					int itemId = weapon.getItem().getItemId();
					if (itemId == 202012) {
						attackLevel += 5;
					}
				}
			}
			probability = (int) Config.BONE_BREAK + (attackLevel - defenseLevel);
			if (probability < 10) {
				probability = 10;
			}
			if (probability > 100) {
				probability = 100;
			}
			break;
		case EMPIRE:
		{
			int shockStunPoint = attacker.getResistance().getSpecialPierce(eKind.ABILITY) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			
			
			int levelp = (attackLevel - defenseLevel)*Config.ngmlvpoint;
			int stunpoint = 0;
			if (shockStunPoint > 0) {
				stunpoint += Math.round(((double) shockStunPoint * (double) Config.ngmtunpoint));
			}
			if(shockStunPoint >= 40){
				System.out.println("엠파 확률 조작 의심: " + attacker.getName());
				System.out.println("기술 적중 :" + shockStunPoint);
			}
			probability = (int)  + Config.ngmdefault + stunpoint;
			probability += levelp;
			if (probability < Config.네쥬스턴최소) {// 기본정의 10
				probability = Config.네쥬스턴최소;
			}
			
			if(probability > Config.네쥬스턴최대){
				probability = Config.네쥬스턴최대;
			}
		}
			break;
		case SHOCK_STUN:// 쇼크스턴
			int shockStunPoint = attacker.getResistance().getSpecialPierce(eKind.ABILITY) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			
			int levelp = (attackLevel - defenseLevel)*Config.ngslvpoint;
			int stunpoint = 0;
			if (shockStunPoint > 0) {
				stunpoint += Math.round(((double) shockStunPoint * (double) Config.ngstunpoint));
			}
			if(shockStunPoint >= 40){
				System.out.println("스턴 확률 조작 의심: " + attacker.getName());
				System.out.println("기술 적중 :" + shockStunPoint);
			}
			probability = (int)  + Config.ngsdefault + stunpoint;
			probability += levelp;
			if (probability < Config.네쥬스턴최소) {// 기본정의 10
				probability = Config.네쥬스턴최소;
			}
			
			if(probability > Config.네쥬스턴최대){
				probability = Config.네쥬스턴최대;
			}
			//System.out.println("확률: " + probability);
			break;
		case MANA_DRAIN:
			if (attackInt > 25)
				attackInt = 25;
			probability = (int) ((attackInt - (defenseMr / 5.95)) + l1skills.getProbabilityValue());
			if (_pc != null && _pc.isElf() && (_calcType == PC_PC || _calcType == PC_NPC)) {
				probability -= 50;
			}
			if (probability < 0)
				probability = 0;
			break;
		/*case FINAL_BURN:
			probability = (int) ((attackLevel) - (defenseMr / 5)); // 파이널 번
			break;*/
		case TURN_UNDEAD:
			if (attackInt > 25)
				attackInt = 25;
			
			int level_probability = 0;
			switch (attackLevel) {
			case 30:
			case 31:
			case 32:
			case 33:
			case 34:
			case 35:
			case 36:
			case 37:
			case 38:
			case 39:
				level_probability = Config.TurnChant64UP;
				break;
			case 40:
			case 41:
			case 42:
			case 43:
			case 44:
			case 45:
			case 46:
			case 47:
			case 48:
			case 49:
			case 50:
				level_probability = Config.TurnChant65UP;
				break;
			case 51:
			case 52:
			case 53:
			case 54:
			case 55:
			case 56:
			case 57:
			case 58:
			case 59:
			case 60:
				level_probability = Config.TurnChant75UP;
				break;
			case 61:
			case 62:
			case 63:
			case 64:
			case 65:
			case 66:
			case 67:
			case 68:
			case 69:
			case 70:
				level_probability = Config.TurnChant80UP;
				break;
			case 71:
			case 72:
			case 73:
			case 74:
			case 75:
			case 76:
			case 77:
			case 78:
			case 79:
			case 80:
			case 81:
			case 82:
			case 83:
			case 84:
			case 85:
			case 86:
			case 87:
			case 88:
			case 89:
			case 90:
			case 91:
			case 92:
			case 93:
			case 94:
			case 95:
			case 96:
			case 97:
			case 98:
			case 99:
				level_probability = Config.TurnChant80UP;
				break;
			default :
				break;
				
					
			}

			probability = (int) ((attackInt * 3 + (level_probability * 3)) - (defenseMr + (defenseLevel / 2)) - 80);
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				if (!_pc.isWizard()) {
					probability -= 30;
				}
				if (_pc.getMapId() == 812){
					probability += 5;
				}
				if (_pc.getMapId() == 15403 || _pc.getMapId() == 111 || _pc.getMapId() == 12862){
					probability = 0;
				}
				/*
				if (!_pc.isElf()) {
					probability -= Config.TurnChantElf;
				}
				*/
			}
			break;
		/** 바란카 스턴레벨 상승에 따른 아머브레이크 확률 증가 **/
		
		case ARMOR_BRAKE:
			// 기본 확률 설정 (Config에서 시작)
			probability = (int) Config.아머기본;

			// 아머 브레이크 데스티니 패시브 보너스 계산
			if (_pc.isPassive(MJPassiveID.ARMOR_BREAK_DESTINY.toInt())) {
			    // 60레벨부터 1씩 증가, 최대 10단계 (69레벨에서 MAX 10)
			    int destinyBonus = Math.max(0, attacker.getLevel() - 59);
			    probability += Math.min(10, destinyBonus);
			}

			// 공격자 추가 확률 (ImpactUp) 반영
			probability += _pc.getImpactUp();

			// 용언/정령 관통(Special Pierce) 보너스 반영
			int dragonSpellPoint = attacker.getResistance().getSpecialPierce(eKind.SPIRIT) + attacker.getResistance().getSpecialPierce(eKind.ALL);
			if (dragonSpellPoint > 0) {
			    probability += (int) Math.round(dragonSpellPoint * Config.아머곱연산확률);
			}

			// 레벨 차이에 따른 가중치 반영
			probability += (attackLevel - defenseLevel) * Config.ngmlvpoint;

			// 최종 확률 보정 (최소 10% ~ 최대 90%)
			probability = Math.max(10, Math.min(90, probability));

			break;
		default: {
			int dice1 = l1skills.getProbabilityDice();
			int diceCount1 = 0;
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				if (_pc.isWizard()) {
					diceCount1 = getMagicBonus() + getMagicLevel() + 1;
				} else if (_pc.isElf()) {
					diceCount1 = getMagicBonus() + getMagicLevel() - 1;
				} else if (_pc.isDragonknight()) {
					diceCount1 = getMagicBonus() + getMagicLevel();
				} else {
					diceCount1 = getMagicBonus() + getMagicLevel() - 1;
				}
			} else {
				diceCount1 = getMagicBonus() + getMagicLevel();
			}
			if (diceCount1 < 1) {
				diceCount1 = 1;
			}
			if (dice1 > 0) {
				for (int i = 0; i < diceCount1; i++) {
					probability += (_random.nextInt(dice1) + 1);
				}
			}

			probability = probability * getLeverage() / 10;
			probability -= getTargetMr();

			if (skillId == TAMING_MONSTER) {
				double probabilityRevision = 1;
				if ((_targetNpc.getMaxHp() * 1 / 4) > _targetNpc.getCurrentHp()) {
					probabilityRevision = 1.3;
				} else if ((_targetNpc.getMaxHp() * 2 / 4) > _targetNpc.getCurrentHp()) {
					probabilityRevision = 1.2;
				} else if ((_targetNpc.getMaxHp() * 3 / 4) > _targetNpc.getCurrentHp()) {
					probabilityRevision = 1.1;
				}
				probability *= probabilityRevision;
			}
		}
			break;
		}

		eKind kind = null;
		// TODO 내성에 대한 타입
		switch (skillId) {

		// 공포내성
		case DESPERADO:
		case POWERRIP:
		//case TOMAHAWK:
			kind = eKind.FEAR;
			break;

		// 정령내성
		case ARMOR_BRAKE:
		case EARTH_BIND:
		case ERASE_MAGIC:
		case ELEMENTAL_FALL_DOWN:
		case STRIKER_GALE:
		case POLLUTE_WATER:
		case WIND_SHACKLE:
		case AREA_OF_SILENCE:
			kind = eKind.SPIRIT;
			break;

		// 용언내성
		case BONE_BREAK:
		case THUNDER_GRAB:
		case GUARD_BREAK:
		case FEAR:
		case PHANTASM:
		case CONFUSION:
			kind = eKind.DRAGON_SPELL;
			break;

		// 기술내성
		case EMPIRE:
		case SHOCK_STUN:
		case 30081:
			kind = eKind.ABILITY;
			break;
		default:
			break;
		}
		int resistance = (receiver.getResistance().getSpecialResistance(kind) + receiver.getResistance().getSpecialResistance(eKind.ALL)) / Config.내성배율;
		if (resistance > 0) {
			probability -= resistance;
		}
		return calc_character_magic_hit(skillId, probability);
	}

	// 마법적중 실제 적용
	private int calc_character_magic_hit(int skill_id, int probability) {
		if (_calcType != PC_PC && _calcType != PC_NPC) {
			return probability;
		}

		double result = probability;
		switch (skill_id) {
		default: {
			double p = Config.CHARACTER_MAGICHIT_RATE * _pc.getTotalMagicHitup();
			if (_pc.isWizard() || _pc.isElf() && (skill_id == 44 || skill_id == 27 || skill_id == 29 || skill_id == 33 || skill_id == 36) ) {
			result += p;
			} else {
				result += 0;
			}
			break;
		}
		}

		return (int) result;
	}

	public int calcMagicDamage(int skillId) {
        _CriticalDamage = false;
        applyMagicStack = ordinarySpell && skillId != MIND_BREAK
                && (_calcType == PC_PC || _calcType == NPC_PC);
        formula2016 = uses2016Formula(skillId);
		int damage = 0;
		if (_calcType == PC_PC || _calcType == NPC_PC) {
			damage = calcPcMagicDamage(skillId);
		} else if (_calcType == PC_NPC || _calcType == NPC_NPC) {
			damage = calcNpcMagicDamage(skillId);
		}

		if (!formula2016 && skillId != CONFUSION && skillId != MIND_BREAK && skillId != MAGMA_BREATH) {
			damage = calcMrDefense(damage);
		}

		if (_calcType == PC_PC || _calcType == NPC_PC) {

			if (skillId == ENERGY_BOLT || skillId == CALL_LIGHTNING || skillId == DISINTEGRATE) {

				for (L1ItemInstance armor : _targetPc.getEquipSlot().getArmors()) {
					// 붉은 기사의 방패
					if (armor.getItemId() == 20230) {
						int probability = 1;

						if (armor.getEnchantLevel() >= 10) {
							probability = 5;
						} else if (armor.getEnchantLevel() < 6) {
							probability = 1;
						} else {
							probability = armor.getEnchantLevel() - 5;
						}

						if (_random.nextInt(100) < probability) {
							damage *= 0.8D;
						}

						break;
					}
				}
			}
			if (!applyMagicStack && damage > _targetPc.getCurrentHp()) {
				if (_targetPc.isElf() && _targetPc.hasSkillEffect(L1SkillId.SOUL_BARRIER)) {
					if (damage > _targetPc.getCurrentHp() + _targetPc.getCurrentMp()) {
						damage = _targetPc.getCurrentHp() + _targetPc.getCurrentMp();
					}
				} else {
					damage = _targetPc.getCurrentHp();
				}
			}
            // src22 Titan/MortalBody reaction order: Titan Magic cancels the
            // incoming magic hit; Mortal Body reflects but does not cancel it.
            if (damage > 0 && !MJCommons.isUnbeatable(_targetPc)) {
                final L1Character attacker = _pc != null ? _pc : _npc;
                if (CCreatureDamageReaction.onMagicDamage(attacker, _targetPc)) {
                    return 0;
                }
            }

		} else {
			if (damage > _targetNpc.getCurrentHp()) {
				damage = _targetNpc.getCurrentHp();
			}
			
		}
		
		
		
		


		//린엠 neige
			
		
		if (_calcType == PC_PC || _calcType == PC_NPC ){
			
			int awakeeff1 = 0;
			int awakeeff2 = 0;
			int spid = _pc.getCurrentSpriteId();
			if(spid == 19094 || spid == 19168 || spid == 19995 || spid == 19955 || spid == 18291 
					|| spid == 18273 || spid == 18269 || spid == 19510 || spid ==  18295){
				awakeeff1 = 362;
				awakeeff2 = 4585;
			}
			if(spid >= 21258 && spid <= 21310){
				awakeeff1 = 362;
				awakeeff2 = 4585;
			}
			if(spid == 21082 || spid == 21086 || spid == 19082 || spid == 18942  || spid == 21070
					|| spid == 21066  || spid == 21049  || spid == 21053  || spid == 21074
					|| spid == 21078 || spid == 21090 || spid == 21094  || spid == 19086
					|| spid == 19090  || spid == 21132  || spid == 21136 || spid ==  21404 || spid ==  21409
					|| spid ==  21413 || spid ==  21417){
				awakeeff1 = 21165;
				awakeeff2 = 4177;
			}

			int chance3 = _random.nextInt(100) + 1;
			if(_pc.getWeaponSwap() != null){
				weapon = _pc.getWeaponSwap();
				_weaponAttrLevel = weapon.getAttrEnchantLevel();

				if(weapon != null &&_weaponAttrLevel != 0 && chance3 <= 8){
					if(_calcType == PC_PC){
						if(_weaponAttrLevel == 5){SC_SkillSound.send(_pc, _targetPc.getId(), 416); damage += 15;}
						else if(_weaponAttrLevel == 10){SC_SkillSound.send(_pc, _targetPc.getId(), 418); damage += 15;}
						else if(_weaponAttrLevel == 15){SC_SkillSound.send(_pc, _targetPc.getId(), 414); damage += 15;}
						else if(_weaponAttrLevel == 20){SC_SkillSound.send(_pc, _targetPc.getId(), 412); damage += 15;}
					}else{
						if(_weaponAttrLevel == 5){SC_SkillSound.send(_pc, _targetNpc.getId(), 416); damage += 15;}
						else if(_weaponAttrLevel == 10){SC_SkillSound.send(_pc, _targetNpc.getId(), 418); damage += 15;}
						else if(_weaponAttrLevel == 15){SC_SkillSound.send(_pc, _targetNpc.getId(), 414); damage += 15;}
						else if(_weaponAttrLevel == 20){SC_SkillSound.send(_pc, _targetNpc.getId(), 412); damage += 15;}
					}
				}
			}
			if(_CriticalDamage){
				if(_calcType == PC_PC){SC_SkillSound.send(_pc, _targetPc.getId(), 400);
				}else{
					SC_SkillSound.send(_pc, _targetNpc.getId(), 400);
				}
			}else{
				if(_calcType == PC_PC){
					if(chance3 < 40){
						damage += 10;
						SC_SkillSound.send(_pc, _targetPc.getId(), 402);
					}
				}else{
					if(chance3 < 40){
						damage += 10;
						SC_SkillSound.send(_pc, _targetNpc.getId(), 402);
					}
				}
			}
			if(_pc.getLevel() != 99){
				if(_pc.getMagicadd() != 1 && _pc.getAccount().getTree_select() == 562){
					int chancet = _random.nextInt(100) + 1;
					if(chancet < 3){
						damage += 15;
						if(_pc.getLinMeff() != 2){
							SC_SkillSound.send(_pc, _pc.getId(), 562);
						}
						SC_SkillSound.broadcastM(_pc, _pc.getId(), 562);
					}
				}

				if(_pc.getMagicadd() == 1){
					if(_calcType == PC_PC){
						if(_pc.getAccount().getTree_select() != 0){
							int chancet = _random.nextInt(100) + 1;
							int s = _pc.getAccount().getTree_select();
							if(chancet < 3){
								if(_pc.getLinMeff() != 2){
									SC_SkillSound.send(_pc, _targetPc.getId(), s);
								}
								SC_SkillSound.broadcastM(_pc, _targetPc.getId(), s);
								switch (s) {
								case 569:
									damage += 15;
									break;
								case 580:
									damage += 15;
									break;
								case 600:
									damage += 20;
									break;
								case 589:
									damage += 20;
									break;
								case 562:
									damage += 30;
									break;
								default :
									break;
								}
							}
						}
						if(_pc.getAccount().getTree_select2() != 0){
							int chancet = _random.nextInt(100) + 1;
							int s = _pc.getAccount().getTree_select2();
							if(chancet < 3){
								if(_pc.getLinMeff() != 2){
									SC_SkillSound.send(_pc, _targetPc.getId(), s);
								}
								SC_SkillSound.broadcastM(_pc, _targetPc.getId(), s);
								switch (s) {
								case 379:
									damage += 15;
									break;
								case 394:
									damage += 25;
									break;
								case 605:
									damage += 30;
									break;
								default :
									break;
								}
							}
						}
						if(_pc.getAccount().getTree_select3() != 0){
							int chancet = _random.nextInt(100) + 1;
							int s = _pc.getAccount().getTree_select3();
							if(chancet < 3){
								if(_pc.getLinMeff() != 2){
									SC_SkillSound.send(_pc, _targetPc.getId(), s);
								}
								SC_SkillSound.broadcastM(_pc, _targetPc.getId(), s);
								switch (s) {
								case 420:
									damage += 15;
									break;
								case 422:
									damage += 25;
									break;
								case 426:
									damage += 30;
									break;
								default :
									break;
								}
							}
						}
						if(awakeeff1 != 0){
							if(chance3 <= 3){
								damage += 20;
								SC_SkillSound.send(_pc, _pc.getId(), awakeeff1);
								SC_SkillSound.broadcast(_pc, _pc.getId(), awakeeff1);
								SC_SkillSound.send(_pc, _targetPc.getId(), awakeeff2);
								SC_SkillSound.broadcast(_pc, _targetPc.getId(), awakeeff2);
							}
						}
						if(_pc.getDollskill() == 1){
							damage += 30;
							SC_SkillSound.send(_pc, _targetPc.getId(), 21161);
							SC_SkillSound.broadcast(_pc, _targetPc.getId(), 21161);
							_pc.setDollskill(0);
						}
						if(_pc.getDollskill() == 2){
							damage += 20;
							SC_SkillSound.send(_pc, _targetPc.getId(), 374);
							SC_SkillSound.broadcast(_pc, _targetPc.getId(), 374);
							_pc.setDollskill(0);
						}
					}else{
						if(_pc.getAccount().getTree_select() != 0){
							int chancet = _random.nextInt(100) + 1;
							int s = _pc.getAccount().getTree_select();
							if(chancet < 3){
								if(_pc.getLinMeff() != 2){
									SC_SkillSound.send(_pc, _targetNpc.getId(), s);
								}
								SC_SkillSound.broadcastM(_pc, _targetNpc.getId(), s);
								switch (s) {
								case 569:
									damage += 15;
									break;
								case 580:
									damage += 15;
									break;
								case 600:
									damage += 20;
									break;
								case 589:
									damage += 20;
									break;
								case 562:
									damage += 30;
									break;
								default :
									break;
								}
							}
						}
						if(_pc.getAccount().getTree_select2() != 0){
							int chancet = _random.nextInt(100) + 1;
							int s = _pc.getAccount().getTree_select2();
							if(chancet < 3){
								if(_pc.getLinMeff() != 2){
									SC_SkillSound.send(_pc, _targetNpc.getId(), s);
								}
								SC_SkillSound.broadcastM(_pc, _targetNpc.getId(), s);
								switch (s) {
								case 379:
									damage += 15;
									break;
								case 394:
									damage += 25;
									break;
								case 605:
									damage += 30;
									break;
								default :
									break;
								}
							}
						}
						if(_pc.getAccount().getTree_select3() != 0){
							int chancet = _random.nextInt(100) + 1;
							int s = _pc.getAccount().getTree_select3();
							if(chancet < 3){
								if(_pc.getLinMeff() != 2){
									SC_SkillSound.send(_pc, _targetNpc.getId(), s);
								}
								SC_SkillSound.broadcastM(_pc, _targetNpc.getId(), s);
								switch (s) {
								case 420:
									damage += 15;
									break;
								case 422:
									damage += 25;
									break;
								case 426:
									damage += 30;
									break;
								default :
									break;
								}
							}
						}
						if(awakeeff1 != 0){
							if(chance3 <= 3){
								damage += 20;
								SC_SkillSound.send(_pc, _pc.getId(), awakeeff1);
								SC_SkillSound.broadcast(_pc, _pc.getId(), awakeeff1);
								SC_SkillSound.send(_pc, _targetNpc.getId(), awakeeff2);
								SC_SkillSound.broadcast(_pc, _targetNpc.getId(), awakeeff2);
							}
						}
						if(_pc.getDollskill() == 1){
							damage += 30;
							SC_SkillSound.send(_pc, _targetNpc.getId(), 21161);
							SC_SkillSound.broadcast(_pc, _targetNpc.getId(), 21161);
							_pc.setDollskill(0);
						}
						if(_pc.getDollskill() == 2){
							damage += 20;
							SC_SkillSound.send(_pc, _targetNpc.getId(), 374);
							SC_SkillSound.broadcast(_pc, _targetNpc.getId(), 374);
							_pc.setDollskill(0);
						}
					}

				}
			}
			_pc.setMagicadd(0);
		}
		
        // Original src22 L1Magic triggers doll procs on PC->PC magic hits.
        // Werewolf procs are physical-melee only, and cannot trigger from a spell.
        if (_calcType == PC_PC && _pc != null && _targetPc != null && damage > 0) {
            damage = MagicDollCombatProcs.applyOnHit(_pc, _targetPc, damage, false);
        }
		return damage;
	}

	public int calcPcFireWallDamage() {
		int dmg = 0;

		L1Skills l1skills = SkillsTable.getInstance().getTemplate(FIRE_WALL);
		dmg = calcAttrDefence(l1skills.getDamageValue(), L1Skills.ATTR_FIRE);

		if (_targetPc.hasSkillEffect(ABSOLUTE_BARRIER)) {
			dmg = 0;
		}
		if (_targetPc.hasSkillEffect(ICE_LANCE)) {
			dmg = 0;
		}
		if (_targetPc.hasSkillEffect(EARTH_BIND)) {
			dmg = 0;
		}
		if (_targetPc.hasSkillEffect(MOB_BASILL)) { // 바실얼리기대미지0
			dmg = 0;
		}
		if (_targetPc.hasSkillEffect(MOB_COCA)) { // 코카얼리기대미지0
			dmg = 0;
		}
		if (dmg < 0) {
			dmg = 0;
		}

		return dmg;
	}

	public int calcNpcFireWallDamage() {
		int dmg = 0;

		L1Skills l1skills = SkillsTable.getInstance().getTemplate(FIRE_WALL);
		dmg = calcAttrDefence(l1skills.getDamageValue(), L1Skills.ATTR_FIRE);

		if (_targetNpc.hasSkillEffect(ICE_LANCE)) {
			dmg = 0;
		}
		if (_targetNpc.hasSkillEffect(EARTH_BIND)) {
			dmg = 0;
		}
		if (_targetNpc.hasSkillEffect(MOB_BASILL)) { // 바실얼리기대미지0
			dmg = 0;
		}
		if (_targetNpc.hasSkillEffect(MOB_COCA)) { // 코카얼리기대미지0
			dmg = 0;
		}
		if (dmg < 0) {
			dmg = 0;
		}

		return dmg;
	}

	/** NPC -> PC (PC->PC) 마법 대미지 산출 **/
	private int calcPcMagicDamage(int skillId) {
		int dmg = 0;

		/*if (skillId == FINAL_BURN) {
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				dmg = _pc.getCurrentMp() / 2;
				dmg = (int) ((dmg * getLeverage()) / Config.Finalburnpc);
			} else {
				dmg = _npc.getCurrentMp() / 2;
				dmg = (int) ((dmg * getLeverage()) / Config.Finalburnpc);
			}
		} else {*/
			dmg = calcMagicDiceDamage(skillId);
			dmg = (dmg * getLeverage()) / 10;
		//}
			
			
			if (_calcType == PC_PC){
				if (_targetPc.getPVPMagicDamageReduction() > 0){
				  //  dmg -= dmg /_targetPc.getPVPMagicDamageReduction();
				    dmg -=   _targetPc.getPVPMagicDamageReduction() * 0.01 * dmg;
				}
			}
		
		if (_targetPc.hasSkillEffect(REDUCTION_ARMOR)) {
			int targetPcLvl = _targetPc.getLevel();
			if (targetPcLvl < 50) {
				targetPcLvl = 50;
			}
			dmg -= (targetPcLvl - 50) / 5 + 1;
		}
		
		
		if (_targetPc.hasSkillEffect(MAJESTY)) {
			int targetPcLvl = _targetPc.getLevel();
			if (targetPcLvl < 50) {
				targetPcLvl = 50;
			}
			int r = (targetPcLvl - 50) / 5 + 1;
			
			dmg -= r;
		}
		/*수호성 아툰 추가하기
		if(_targetPc.getAccount().getTree_cri() == 1){
			
		}
		*/
		/*
		/**수호성 아툰**/
		
		if(_targetPc.getAccount().getTree_select4() != 0){
			int a = _targetPc.getAccount().getTree_select4();
			int r = _random.nextInt(100) + 1;
			if(r <= 2){
				switch(a){
				case 20796:
					dmg -= dmg/20;
					break;
				case 19052:
					dmg -= dmg/10;
					break;
				case 19055:
					dmg -= dmg/5;
					break;
				}
				if(_targetPc.getLinMeff() != 2){
					SC_SkillSound.send(_targetPc, _targetPc.getId(), a);
				}
				SC_SkillSound.broadcastM(_targetPc, _targetPc.getId(), a);
			}
		}
		
		
		if (_calcType == PC_PC) {
			L1ItemInstance item = _pc.getInventory().findEquippedItemId(123666);
			L1ItemInstance item1 = _pc.getInventory().findEquippedItemId(134);
			if (item != null) {
				if (item.getEnchantLevel() <= 0) {
				dmg += dmg * 0.1;
				} else {
					dmg += dmg * (0.1 + item.getEnchantLevel() * 0.01);
				}//////////// 100 = 10 
		}
			if (item1 != null) {
				
					dmg += dmg * (item1.getEnchantLevel() * 0.01);
				
		}
			
		}
		
		
//		if (_calcType == PC_PC) {
//			
//
//				if (skillId == DISINTEGRATE) {
//					if (_pc.hasSkillEffect(L1SkillId.네메시스)) {
//					int 랜덤 = _random.nextInt(100) + 1;
//					int 렙차 = _pc.getLevel() - _targetPc.getLevel();
//					int 기본확률 = Config.이터기본;
//					int 양렙차 = 기본확률 + (렙차 * Config.이터양);
//					int 음렙차 = 기본확률 + (렙차 * Config.이터음);
//
//					if (렙차 >= 0) {
//						if (랜덤 <= 양렙차) {
//							if (양렙차 > 100) {
//								양렙차 = 100;
//							}
//
//							int 요정 = (int) ((double)Config.이터제약  * Config.리버레이션절감);
//							
//							  if (_targetPc.hasSkillEffect(L1SkillId.리버레이션)) {
//							_targetPc.setSkillEffect(L1SkillId.이터디스, 요정);
//							_targetPc.setSkillEffect(STATUS_FREEZE, 요정);
//							L1EffectSpawn.getInstance().spawnEffect(991162, 요정,
//									_targetPc.getX(), _targetPc.getY(), _targetPc.getMapId());
//							  } else {
//								  _targetPc.setSkillEffect(L1SkillId.이터디스, Config.이터제약);
//									_targetPc.setSkillEffect(STATUS_FREEZE, Config.이터제약);  
//									L1EffectSpawn.getInstance().spawnEffect(991162, Config.이터제약,
//											_targetPc.getX(), _targetPc.getY(), _targetPc.getMapId());
//							  }
//							//_targetPc.sendPackets(new S_SkillSound(_targetPc.getId(), 13136));
//							//_targetPc.broadcastPacket(new S_SkillSound(_targetPc.getId(), 13136));
//							_targetPc.sendPackets(new S_SkillSound(_targetPc.getId(), 14453));
//							_targetPc.broadcastPacket(new S_SkillSound(_targetPc.getId(), 14453));
//							
//							SC_Paralysis.send(_targetPc, SC_Paralysis.TYPE_BIND, true);
//							_targetPc.sendPackets("창에 궤뚫려 행동에 제약을 받습니다");
//							Broadcaster.broadcastPacketExceptTargetSight(_targetPc,
//									new S_DoActionGFX(_targetPc.getId(), ActionCodes.ACTION_Damage), _pc);
//
//							Start();
//							
//
//								
//								
//						
//							
//
//						}else {
//						_targetPc.sendPackets(new S_SkillSound(_targetPc.getId(), 14452));
//						_targetPc.broadcastPacket(new S_SkillSound(_targetPc.getId(), 14452));
//						}
//						if (_pc.isGm()) {
//							_pc.sendPackets("제약: 확률[" + 랜덤 + "]   렙차[" + 렙차 + "*" + Config.이터양 + "+" + 기본확률
//									+ "] = 확률범위[1~" + 양렙차 + "]");
//
//						}
//
//					} else {
//						if (랜덤 <= 음렙차) {
//							if (음렙차 < 30) {
//								음렙차 = 30;
//							}
//							
//							int 요정 = (int) ((double)Config.이터제약  * Config.리버레이션절감);
//							
//							  if (_targetPc.hasSkillEffect(L1SkillId.리버레이션)) {
//								  _targetPc.setSkillEffect(L1SkillId.이터디스, 요정);
//									_targetPc.setSkillEffect(STATUS_FREEZE, 요정);
//									L1EffectSpawn.getInstance().spawnEffect(991162, 요정,
//											_targetPc.getX(), _targetPc.getY(), _targetPc.getMapId());
//							  } else {
//							_targetPc.setSkillEffect(L1SkillId.이터디스, Config.이터제약);
//							_targetPc.setSkillEffect(STATUS_FREEZE, Config.이터제약);
//							L1EffectSpawn.getInstance().spawnEffect(991162, Config.이터제약,
//									_targetPc.getX(), _targetPc.getY(), _targetPc.getMapId());
//							  }
//							//_targetPc.sendPackets(new S_SkillSound(_targetPc.getId(), 13136));
//							//_targetPc.broadcastPacket(new S_SkillSound(_targetPc.getId(), 13136));
//							_targetPc.sendPackets(new S_SkillSound(_targetPc.getId(), 14453));
//							_targetPc.broadcastPacket(new S_SkillSound(_targetPc.getId(), 14453));
//							SC_Paralysis.send(_targetPc, SC_Paralysis.TYPE_BIND, true);
//							_targetPc.sendPackets("창에 궤뚫려 행동에 제약을 받습니다");
//							
//							Start();
//
//						}else {
//							
//							_targetPc.sendPackets(new S_SkillSound(_targetPc.getId(), 14452));
//							_targetPc.broadcastPacket(new S_SkillSound(_targetPc.getId(), 14452));
//							}
//						if (_pc.isGm()) {
//							_pc.sendPackets("제약: 확률[" + 랜덤 + "]   렙차[-" + 렙차 + "*" + Config.이터음 + "+" + 기본확률
//									+ "] = 확률범위[1~" + 음렙차 + "]");
//						}
//					}
//
//				}
//			} 
//				
				
				
				
				
				
		//}
		if (_calcType == NPC_PC) {
			boolean isNowWar = false;
			int castleId = L1CastleLocation.getCastleIdByArea(_targetPc);
			if (castleId > 0) {
				isNowWar = MJCastleWarBusiness.getInstance().isNowWar(castleId);
			}
			if (!isNowWar) {
				if (_npc instanceof L1PetInstance) {
					dmg /= 8;
				}
				if (_npc instanceof L1SummonInstance) {
					L1SummonInstance summon = (L1SummonInstance) _npc;
					if (summon.isExsistMaster()) {
						dmg /= 8;
					}
				}
			}
		}
		/** 마법사 일경우 대미지외부화 적용 */
		if (_calcType == PC_PC) {
			if (_pc.getType() == 3) {
				dmg *= Config.마법사마법대미지;
			}
		}

		if (dmg > 0 && _targetPc.hasSkillEffect(IMMUNE_TO_HARM))
			dmg -= (dmg * _targetPc.getImmuneReduction());

		if (_calcType == PC_PC) {
		if (_pc.hasSkillEffect(L1SkillId.잠재력이뮨무시)) {
			if (_targetPc.hasSkillEffect(L1SkillId.IMMUNE_TO_HARM)){
				//dmg -= doll.getimmun();
				dmg += (_targetPc.getImmuneReduction() * Config.잠재력이뮨 * dmg);
			
			}
		}
		}


		/*** 신규레벨보호 ***/
		// if (_calcType == PC_PC) {
		// int castle_id = L1CastleLocation.getCastleIdByArea(_pc);
		// if (castle_id == 0) {
		// if (_targetPc.getLevel() < Config.AUTO_REMOVELEVEL
		// || _pc.getLevel() < Config.AUTO_REMOVELEVEL && !_pc.isPinkName()) {
		// dmg /= 2;
		// _pc.sendPackets(new SC_SystemMessage("신규유저는 대미지의 50%만 가해집니다."));
		// _targetPc.sendPackets(new SC_SystemMessage("신규유저는 대미지를 50%만 받습니다."));
		// }
		// }
		// }

//		/** 신규혈맹 공격안되게 **/
//		if (_calcType == PC_PC) {
//			int castle_id = L1CastleLocation.getCastleIdByArea(_pc);
//			if (castle_id == 0) {
//				if(!(_pc.is_combat_field() || _targetPc.is_combat_field())){
//				if (Config.신규혈맹보호처리) {
//					boolean attack_ok = false;
//					for (L1Object obj : L1World.getInstance().getVisibleObjects(_targetPc)) {
//						if (!(obj instanceof L1MonsterInstance)) {
//							continue;
//						}
//
//						if (obj instanceof L1MonsterInstance) {
//							L1MonsterInstance mon = (L1MonsterInstance) obj;
//							int monid = UserProtectMonsterTable.getInstance().getUserProtectMonsterId(mon.getNpcId());
//							if (monid != 0) {
//								attack_ok = true;
//								break;
//							}
//						}
//					}
//
//					if (!attack_ok) {
//						if (_pc.getClanid() == Config.신규혈맹클랜 || _targetPc.getClanid() == Config.신규혈맹클랜) {
//							if (skillId != EXTRA_HEAL && skillId != HEAL && skillId != GREATER_HEAL && skillId != HEAL_ALL && skillId != FULL_HEAL
//									&& skillId != NATURES_BLESSING) { // 버프계
//								
//								_pc.sendPackets(new SC_SystemMessage("\\fY중립은 상호간에 마법대미지가 적용되지 않습니다."));
//								_targetPc.sendPackets(new SC_SystemMessage("\\fY중립은 상호간에 마법공격이 적용되지 않습니다."));
//								return 0;
//							}
//						}
//					}
//				} else {
//					if (_pc.getClanid() == Config.신규혈맹클랜 || _targetPc.getClanid() == Config.신규혈맹클랜) {
//						if (skillId != EXTRA_HEAL && skillId != HEAL && skillId != GREATER_HEAL && skillId != HEAL_ALL && skillId != FULL_HEAL
//								&& skillId != NATURES_BLESSING) { // 버프계
//							dmg /= 2;
//							
//							_pc.sendPackets(new SC_SystemMessage("\\fY중립은 상호간의 마법대미지 50%만 가해집니다."));
//							_targetPc.sendPackets(new SC_SystemMessage("\\fY중립은 상호간의 마법대미지를 50%만 받습니다."));
//						}
//					}
//				}
//				}
//			}
//		}
		if (_calcType == PC_PC) {
		if (_targetPc.hasSkillEffect(L1SkillId.잠재력pvp마법감소1)) {
			dmg -= dmg * 0.1;
			}
			if (_targetPc.hasSkillEffect(L1SkillId.잠재력pvp마법감소2)) {
				dmg -= dmg * 0.02;
				}
			if (_targetPc.hasSkillEffect(L1SkillId.잠재력pvp마법감소3)) {
				dmg -= dmg * 0.05;
				}
			
		
		}
		
		if (_calcType == PC_PC || _calcType == NPC_PC) {
			if (_targetPc.hasSkillEffect(L1SkillId.아머데스티니) && _targetPc.hasSkillEffect(L1SkillId.SHADOW_ARMOR)) {
				if (_targetPc.getLevel() == 85 || _targetPc.getLevel() == 86) {
					dmg -= dmg * 0.05;
				} else if (_targetPc.getLevel() == 87 || _targetPc.getLevel() == 88) {
					dmg -= dmg * 0.06;
				}else if (_targetPc.getLevel() == 89 || _targetPc.getLevel() == 90) {
					dmg -= dmg * 0.07;
				}else if (_targetPc.getLevel() == 91 || _targetPc.getLevel() == 92) {
					dmg -= dmg * 0.08;
				}else if (_targetPc.getLevel() == 93 || _targetPc.getLevel() == 94) {
					dmg -= dmg * 0.09;
				}else if (_targetPc.getLevel() >= 95) {
					dmg -= dmg * 0.1;
				}
			}
		}
		
			
		if (_calcType == NPC_PC) {
			if (_targetPc.getAccount() != null && _targetPc.getAccount().getGrangKinAngerStat() != 0 && dmg > 0) {
				dmg *= _targetPc.getAccount().getGrangKinAngerReducCalc();
			}
			
		}

		/** 특정지역 PK금지 **/
		if (_calcType == PC_PC) {
			//if (_pc.getMapId() == 254 || _pc.getMapId() == 612 || _pc.getMapId() == 1930) {
			
			if(_pc.getClanid() != 0){
				if(_pc.getClan().getEmblemId() == 0){
					_pc.sendPackets("\\fY혈맹에 마크가 없으면 대인 공격이 불가합니다.");
					_targetPc.sendPackets("\\fY[공격 무효:"+_pc.getName()+"][원인 : 혈맹 마크 부재]");
					dmg = 0;
				}
			}
			if (_pc.getMapId() == 612) {
				_pc.sendPackets("\\fY현재 사냥터에서는 PK가 불가능 합니다.");
				_targetPc.sendPackets("\\fY현재 사냥터에서는 PK가 불가능 합니다.");
				dmg = 0;
			}
			if (_pc.getMapId() == 63) {
				_pc.sendPackets("\\fY현재 사냥터에서는 PK가 불가능 합니다.");
				_targetPc.sendPackets("\\fY현재 사냥터에서는 PK가 불가능 합니다.");
				dmg = 0;
			}
		}
		
		double total_reduction = 0;

		total_reduction += _targetPc.getDamageReductionByArmor();
		total_reduction += _targetPc.getDamageReduction(); // 방어구에 의한 대미지 감소
		total_reduction *= Config.MAGIC_REDUCTION_RATION;
		dmg = (int) Math.max(dmg - total_reduction, 0);
		if (dmg < 0) {
			dmg = 0;
		}

		return dmg;
	}

	/** 플레이어-> NPC에게 마법 대미지 산출 **/
	private int calcNpcMagicDamage(int skillId) {
		int dmg = 0;
		/*if (skillId == FINAL_BURN) {
			if (_calcType == PC_PC || _calcType == PC_NPC) {
				dmg = _pc.getCurrentMp();
				dmg = (int) ((dmg * getLeverage()) / Config.Finalburnnpc);
			} else {
				dmg = _npc.getCurrentMp();
				dmg = (int) ((dmg * getLeverage()) / Config.Finalburnnpc);
			}
		} else {*/
			dmg = calcMagicDiceDamage(skillId);
			/** PC->NPC 마법 대미지 외부화 **/
			if (_calcType == PC_NPC) {
				dmg = (dmg * getLeverage()) / Config.pcnpcmagicdmg;
				dmg *= getSimSimLeverage();
			} else {
				dmg = (dmg * getLeverage()) / Config.pcnpcmagicdmg;
			}
		//}
		if (_calcType == PC_NPC) {
			boolean isNowWar = false;
			int castleId = L1CastleLocation.getCastleIdByArea(_targetNpc);
			if (castleId > 0) {
				isNowWar = MJCastleWarBusiness.getInstance().isNowWar(castleId);
			}
			if (!isNowWar) {
				if (_targetNpc instanceof L1PetInstance) {
					dmg /= 8;
				}
				if (_targetNpc instanceof L1SummonInstance) {
					L1SummonInstance summon = (L1SummonInstance) _targetNpc;
					if (summon.isExsistMaster()) {
						dmg /= 8;
					}
				}
			}
		}
		
		if (_calcType == PC_NPC) {
			L1ItemInstance item = _pc.getInventory().findEquippedItemId(123666);
			L1ItemInstance item1 = _pc.getInventory().findEquippedItemId(134);
			if (item != null) {
				if (item.getEnchantLevel() <= 0) {
				dmg += dmg * 0.1;
				} else {
					dmg += dmg * (0.1 + item.getEnchantLevel() * 0.01);
				}//////////// 100 = 10 
		}
			if (item1 != null) {
				
					dmg += dmg * (item1.getEnchantLevel() * 0.01);
				
		}
			
		}
		
		
//		if (_calcType == PC_NPC) {
//			if (skillId == DISINTEGRATE) {
//			if (_pc.hasSkillEffect(L1SkillId.네메시스)) {
//
//				int qq = Config.이터몬스터;
//			
//					int 랜덤 = _random.nextInt(100) + 1;
//
//					if (랜덤 <= qq) {
//
//						_targetNpc.setSkillEffect(L1SkillId.이터디스, Config.이터제약);
//						_targetNpc.setSkillEffect(STATUS_FREEZE, Config.이터제약);
//						//_targetNpc.sendPackets(new S_SkillSound(_targetNpc.getId(), 13136));
//						//_targetNpc.broadcastPacket(new S_SkillSound(_targetNpc.getId(), 13136));
//						_targetNpc.sendPackets(new S_SkillSound(_targetNpc.getId(), 14453));
//						_targetNpc.broadcastPacket(new S_SkillSound(_targetNpc.getId(), 14453));
//						SC_Paralysis.send(_targetNpc, SC_Paralysis.TYPE_BIND, true);
//						Start1();
//						@SuppressWarnings("unused")
//						L1EffectInstance eff12 = L1EffectSpawn.getInstance().spawnEffect(991162, Config.이터제약,
//								_targetNpc.getX(), _targetNpc.getY(), _targetNpc.getMapId());
//
//					} else {
//						
//						_targetNpc.sendPackets(new S_SkillSound(_targetNpc.getId(), 14452));
//						_targetNpc.broadcastPacket(new S_SkillSound(_targetNpc.getId(), 14452));
//					}
//					if (_pc.isGm()) {
//						_pc.sendPackets("제약: 확률[" + 랜덤 + "]  확률범위[1~" + Config.이터몬스터 + "]");
//					}
//
//				} 
//			} 
//		}
		if (_calcType == PC_NPC && _targetNpc != null) {
			int npcId = _targetNpc.getNpcTemplate().get_npcId();
			if (npcId >= 45912 && npcId <= 45915 && !_pc.hasSkillEffect(STATUS_HOLY_WATER)) {
				dmg = 0;
			}
			if (npcId == 45916 && !_pc.hasSkillEffect(STATUS_HOLY_MITHRIL_POWDER)) {
				dmg = 0;
			}
			if (npcId == 45941 && !_pc.hasSkillEffect(STATUS_HOLY_WATER_OF_EVA)) {
				dmg = 0;
			}
//			if (npcId == 45752 && !_pc.hasSkillEffect(STATUS_CURSE_BARLOG)) {
//				dmg = 0;
//			}
//			if (npcId == 45753 && !_pc.hasSkillEffect(STATUS_CURSE_BARLOG)) {
//				dmg = 0;
//			}
//			if (npcId == 45675 && !_pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
//				dmg = 0;
//			}
//			if (npcId == 81082 && !_pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
//				dmg = 0;
//			}
		/*	if (npcId == 45625 && !_pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
				dmg = 0;
			}
			if (npcId == 45674 && !_pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
				dmg = 0;
			}
			if (npcId == 45685 && !_pc.hasSkillEffect(STATUS_CURSE_YAHEE)) {
				dmg = 0;
			}*/
			if (npcId >= 46068 && npcId <= 46091 && _pc.getCurrentSpriteId() == 6035) {
				dmg = 0;
			}
			if (npcId >= 46092 && npcId <= 46106 && _pc.getCurrentSpriteId() == 6034) {
				dmg = 0;
			}
			if (_targetNpc.getNpcTemplate().get_gfxid() == 7684 && !_pc.hasSkillEffect(PAP_FIVEPEARLBUFF)) {
				dmg = 0;
			}
			if (_targetNpc.getNpcTemplate().get_gfxid() == 7805 && !_pc.hasSkillEffect(PAP_MAGICALPEARLBUFF)) {
				dmg = 0;
			}
			if ((_targetNpc.getNpcTemplate().get_gfxid() == 7864 || _targetNpc.getNpcTemplate().get_gfxid() == 7869
					|| _targetNpc.getNpcTemplate().get_gfxid() == 7870)) {
				dmg *= 1.5;
			} // 파푸리온 혈흔1.5뎀


		}
		if (_calcType == PC_NPC) {
			if (_pc.getAccount() != null && _pc.getAccount().getGrangKinAngerStat() != 0 && dmg > 0) {
				dmg = _pc.getAccount().getGrangKinAngerDmgCalc(dmg);
			}
		}

		return dmg;
	}

    private long effectiveMagicMr() {
        final long hit = _pc != null
                ? (long) _pc.getTotalMagicHitup() + _pc.getMagicHit() : _npc.getMagicHit();
        return (long) getTargetMr() - hit;
    }

    private boolean uses2016Formula(int skillId) {
        if (!ordinarySpell || _targetPc == null || skillId == CONFUSION
                || skillId == MIND_BREAK || skillId == MAGMA_BREATH) return false;
        // Snapshot once: a concurrent defender buff/class change cannot turn a
        // validated MR branch into an invalid Math.pow call later in this hit.
        formulaDefenderType = _targetPc.getType();
        formulaDefenderWis = _targetPc.getAbility().getTotalWis();
        formulaEffectiveMr = effectiveMagicMr();
        return SpellDamageFormula.supports(formulaDefenderType,
                formulaDefenderWis, formulaEffectiveMr);
    }

    private int targetElementResist(int attr) {
        return switch (attr) {
        case L1Skills.ATTR_EARTH -> _targetPc.getResistance().getEarth();
        case L1Skills.ATTR_FIRE -> _targetPc.getResistance().getFire();
        case L1Skills.ATTR_WATER -> _targetPc.getResistance().getWater();
        case L1Skills.ATTR_WIND -> _targetPc.getResistance().getWind();
        default -> 0;
        };
    }

    private int disintegrateLawfulBonus(int skillId) {
        if (skillId != DISINTEGRATE) return 0;
        final int lawful = getLawful();
        if (lawful > 0) return lawful / Config.DISINT_LAWFUL_WEIGHT;
        if (lawful < 0) return lawful / Config.DISINT_CHAOTIC_WEIGHT;
        return 0;
    }

	/** 플레이어->플레이어 에게 마법 대미지 산출 **/
	private int calcMagicDiceDamage(int skillId) {
		L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillId);
		int dice = l1skills.getDamageDice();
		int diceCount = l1skills.getDamageDiceCount();
		int value = l1skills.getDamageValue();
		int magicDamage = 0;
		double PowerMr = 0; // 마방

		dice += getSpellPower() / 2;

		for (int i = 0; i < diceCount; i++) {
			int plus_dmg = (_random.nextInt(dice) + 1);
			magicDamage += plus_dmg;
		}

		magicDamage += value * (1 + getSpellPower() / 10);

		/** 치명타 발생 부분 */
		double criticalCoefficient = 1.4;
		int rnd = java.util.concurrent.ThreadLocalRandom.current().nextInt(100) + 1;
        boolean critical = false;

		if (_calcType == PC_PC || _calcType == PC_NPC) {
			int propCritical = CalcStat.마법치명타(_pc.ability.getTotalInt());
			switch (skillId) {
			// 6레벨 이하 광역마법 제외한 공격마법
			case ENERGY_BOLT:
			case ICE_DAGGER:
			case WIND_CUTTER:
			case CHILL_TOUCH:
			case SMASH:
			case FIRE_ARROW:
			case STALAC:
			case VAMPIRIC_TOUCH:
			case CONE_OF_COLD:
			case CONE_OF_COLD_mob:
			case CALL_LIGHTNING:
			case DISINTEGRATE:
				propCritical = +10;
				break;
			}

			// 마안 일정확률로 마법치명타+1
			if (_pc.hasSkillEffect(LIND_MAAN) || _pc.hasSkillEffect(SHAPE_MAAN) || _pc.hasSkillEffect(LIFE_MAAN)) {
				propCritical += 1;
			}
			propCritical += (_pc.getBaseMagicCritical() * Config.CHARACTER_MAGICCRI_RATE);
			if (criticalOccur(propCritical)) {
                critical = true;
                if (!formula2016) magicDamage *= 1.5;
			}
		} else if (_calcType == NPC_PC || _calcType == NPC_NPC) {
			if (rnd <= 15) {
                critical = true;
                if (formula2016) _CriticalDamage = true;
				if (!formula2016) magicDamage *= criticalCoefficient;
			}
		}

        if (formula2016) {
            final L1Character caster = _pc != null ? _pc : _npc;
            final Ability stats = caster.getAbility();
            final int a100 = SpellDamageFormula.coefficientA100(stats.getTotalInt(),
                    stats.getSp(), stats.getTrueSp(), targetElementResist(l1skills.getAttr()));
            final int b100 = SpellDamageFormula.coefficientB100(formulaDefenderType,
                    formulaDefenderWis, formulaEffectiveMr);
            int md = _pc == null ? 0 : _pc.getBaseMagicDmg();
            if (_pc != null && _pc.getWeapon() != null)
                md += _pc.getWeapon().getItem().getMagicDmgModifier();
            // Existing armor/skill/PvP DR remains in calcPcMagicDamage, once,
            // after this MR calculation. It must not go through calcMrDefense again.
            return SpellDamageFormula.calculate(magicDamage, a100, b100, md, 0,
                    disintegrateLawfulBonus(skillId), critical);
        }

		// 디스마법은 라우풀에 따라 데미지 상향처리.
		// : 카오틱수치가 높을수록 데미지 하향
		/** 디스 본섭화 **/
		if (skillId == DISINTEGRATE) {
			int lawful = getLawful();
			if (lawful > 0) {
				// magicDamage += (magicDamage * (lawful * 0.000001));
				magicDamage += (lawful / Config.DISINT_LAWFUL_WEIGHT);
			} else if (lawful < 0) {
				magicDamage += (lawful / Config.DISINT_CHAOTIC_WEIGHT);
			}

		}

		if (getTargetMr() < 101) {
			PowerMr = getTargetMr() / (double) 170; // 마방170되면 10당 (기본대미지*마법상수)의
													// 5% 대미지 줄어들게 설정 총50%
		} else {
			PowerMr = 0.5 + (getTargetMr() - 100) / (double) 400; // 마방100초과분에
																	// 대해 10당
																	// (기본대미지*마법상수)의
																	// 1% 줄어들게
																	// 설정 100당
																	// 10%
		} // 마방 400되면 마법대미지 0

		/*if (skillId == FINAL_BURN) {
			PowerMr = 0;
		}*/
		magicDamage -= magicDamage * PowerMr; // 먼저 마방에 의한 대미지 감소부터 처리

		double attrDeffence = calcAttrResistance(l1skills.getAttr());

		// 속성방어 100당 45% 줄어듬.
		// 10당4.5%초과분에대해서
		// 10당 0.9% 줄어들게 설정
		magicDamage -= magicDamage * attrDeffence; // 마방에 의한 대미지 감소후 속성방어에 의한
		// 대미지 감소 처리

		if (_calcType == PC_PC || _calcType == PC_NPC) {
			magicDamage += _pc.getBaseMagicDmg(); // 베이스 스탯 마법 대미지 보너스 추가
		}
		if (_calcType == PC_PC || _calcType == PC_NPC) {
			int weaponAddDmg = 0;
			L1ItemInstance weapon = _pc.getWeapon();
			if (weapon != null) {
				weaponAddDmg = weapon.getItem().getMagicDmgModifier();
			}
			magicDamage += weaponAddDmg; // 무기에 의한 마법 대미지 추가
		}
		return magicDamage;
	}

	public int calcHealing(int skillId) { // 힐 회복량 neige
		L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillId);
		int dice = l1skills.getDamageDice();
		int value = l1skills.getDamageValue();
		int magicDamage = 0;

		int magicBonus = getMagicBonus();
		if (magicBonus > 10) {
			magicBonus = 10;
		}
		double alignmentRevision = 1.0;
		if (getLawful() > 0) {
			alignmentRevision += (getLawful() / 32768.0);
		}
		if (_calcType == PC_PC || _calcType == PC_NPC) {

			int a = 0;
			int b = _pc.getAbility().getTotalInt();
			int c = _random.nextInt(dice);
			int d = _random.nextInt(10);
			if(b< 8){
				b = 8;
			}
			
			magicDamage = 0;
			magicDamage = (b*value) -20;
			magicDamage += c;
			magicDamage += magicBonus;
			magicDamage *= alignmentRevision;
			magicDamage -= d;
			//magicDamage = magicDamage/2;
			if(_pc.isGm()){
			_pc.sendPackets("mb : "+magicBonus);
			_pc.sendPackets("힐량 : "+magicDamage);
			}
		}else{
			int diceCount = value + magicBonus;
			
			for (int i = 0; i < diceCount; i++) {
				magicDamage += (_random.nextInt(dice) + 1);
			}
			magicDamage *= alignmentRevision;
			magicDamage = (magicDamage * getLeverage()) / 10;
			
		}
		return magicDamage;
	}

	/**
	 * MR에 의한 마법 대미지 감소를 처리 한다 수정일자 : 2013.02.22 수정자 : 메르키스
	 * 
	 * @param dmg
	 * @return dmg
	 */

	public int calcMrDefense(int dmg) {
		int PInt = 0;
		int mrs = 0;
		int attackPcLvSp = 0;
		int targetPcLvMr = 0;
		int ran1 = 0;
		int mrset = 0;

		if (_calcType == PC_PC || _calcType == PC_NPC) {
			PInt = _pc.getSp() * 2;
		} else if (_calcType == NPC_PC) {
			PInt = _npc.getSp() * 2;
		}
		if (_calcType == PC_PC || _calcType == NPC_PC) {
			mrs = (int) (_targetPc.getMr() * 1.7D - 20);
		} else {
			mrs = (int) (_targetNpc.getMr() * 1.7D - 20);
		}
		if (_calcType == PC_PC || _calcType == PC_NPC) {
			attackPcLvSp = _pc.getLevel();
		} else if (_calcType == NPC_PC) {
			attackPcLvSp = _npc.getLevel();
		}
		if (_calcType == PC_PC || _calcType == NPC_PC) {
			targetPcLvMr = _targetPc.getLevel();
		} else {
			targetPcLvMr = _targetNpc.getLevel();
		}

		Random random = new Random();
		ran1 = random.nextInt(15) + 1;
		mrset = mrs - ran1;

		int PPPP = (int) (attackPcLvSp / 8D + 1);
		int TTTT = (int) (targetPcLvMr / 10D + 1);
		int fail = PInt + PPPP - TTTT;

		if ((mrset - fail) >= 151) {
			dmg *= 0.01D;
		} else if ((mrset - fail) >= 146 && (mrset - fail) <= 150) {
			dmg *= 0.03D;
		} else if ((mrset - fail) >= 141 && (mrset - fail) <= 145) {
			dmg *= 0.07D;
		} else if ((mrset - fail) >= 136 && (mrset - fail) <= 140) {
			dmg *= 0.10D;
		} else if ((mrset - fail) >= 131 && (mrset - fail) <= 135) {
			dmg *= 0.13D;
		} else if ((mrset - fail) >= 126 && (mrset - fail) <= 130) {
			dmg *= 0.17D;
		} else if ((mrset - fail) >= 121 && (mrset - fail) <= 125) {
			dmg *= 0.20D;
		} else if ((mrset - fail) >= 116 && (mrset - fail) <= 120) {
			dmg *= 0.23D;
		} else if ((mrset - fail) >= 111 && (mrset - fail) <= 115) {
			dmg *= 0.27D;
		} else if ((mrset - fail) >= 106 && (mrset - fail) <= 110) {
			dmg *= 0.30D;
		} else if ((mrset - fail) >= 101 && (mrset - fail) <= 105) {
			dmg *= 0.33D;
		} else if ((mrset - fail) >= 96 && (mrset - fail) <= 100) {
			dmg *= 0.37D;
		} else if ((mrset - fail) >= 91 && (mrset - fail) <= 95) {
			dmg *= 0.40D;
		} else if ((mrset - fail) >= 86 && (mrset - fail) <= 90) {
			dmg *= 0.43D;
		} else if ((mrset - fail) >= 81 && (mrset - fail) <= 85) {
			dmg *= 0.47D;
		} else if ((mrset - fail) >= 76 && (mrset - fail) <= 80) {
			dmg *= 0.50D;
		} else if ((mrset - fail) >= 71 && (mrset - fail) <= 75) {
			dmg *= 0.53D;
		} else if ((mrset - fail) >= 66 && (mrset - fail) <= 70) {
			dmg *= 0.57D;
		} else if ((mrset - fail) >= 60 && (mrset - fail) <= 65) {
			dmg *= 0.60D;
		} else if ((mrset - fail) >= 51 && (mrset - fail) <= 56) {
			dmg *= 0.63D;
		} else if ((mrset - fail) >= 46 && (mrset - fail) <= 50) {
			dmg *= 0.67D;
		} else if ((mrset - fail) >= 41 && (mrset - fail) <= 45) {
			dmg *= 0.70D;
		} else if ((mrset - fail) >= 36 && (mrset - fail) <= 40) {
			dmg *= 0.73D;
		} else if ((mrset - fail) >= 31 && (mrset - fail) <= 35) {
			dmg *= 0.77D;
		} else if ((mrset - fail) >= 26 && (mrset - fail) <= 30) {
			dmg *= 0.80D;
		} else if ((mrset - fail) >= 21 && (mrset - fail) <= 25) {
			dmg *= 0.85D;
		} else if ((mrset - fail) >= 16 && (mrset - fail) <= 20) {
			dmg *= 0.90D;
		} else if ((mrset - fail) >= 11 && (mrset - fail) <= 15) {
			dmg *= 0.95D;
		} else if ((mrset - fail) >= 6 && (mrset - fail) <= 10) {
			dmg *= 1.00D;
		} else {
			dmg *= 1.05D;
		}
		return dmg;
	}

	private boolean criticalOccur(int prop) {
		if (_pc != null) {
			prop += _pc.get_magic_critical_rate();
		}

		int num = _random.nextInt(100) + 1;

		if (prop == 0) {
			return false;
		}
		if (num <= prop) {
			_CriticalDamage = true;
		}
		return _CriticalDamage;
	}

	private double calcAttrResistance(int attr) {
		int resist = 0;
		int resistFloor = 0;
		if (_calcType == PC_PC || _calcType == NPC_PC) {
			switch (attr) {
			case L1Skills.ATTR_EARTH:
				resist = _targetPc.getResistance().getEarth();
				break;
			case L1Skills.ATTR_FIRE:
				resist = _targetPc.getResistance().getFire();
				break;
			case L1Skills.ATTR_WATER:
				resist = _targetPc.getResistance().getWater();
				break;
			case L1Skills.ATTR_WIND:
				resist = _targetPc.getResistance().getWind();
				break;
			}
		} else if (_calcType == PC_NPC || _calcType == NPC_NPC) {
		}
		if (resist < 0) {
			resistFloor = (int) (-0.45 * Math.abs(resist));
		} else if (resist < 101) {
			resistFloor = (int) (0.45 * Math.abs(resist));
		} else {
			resistFloor = (int) (45 + 0.09 * Math.abs(resist));
			// 속성100초과분에 대해0.45의 1/5정도 감소되게 변경
		}
		double attrDeffence = resistFloor / 100;
		return attrDeffence;
	}

	private int calcAttrDefence(int dmg, int attr) {
		if (dmg < 1) {
			return dmg;
		}

		int resist = 0;

		if (_calcType == PC_PC || _calcType == NPC_PC) {
			switch (attr) {
			case L1Skills.ATTR_EARTH:
				resist = _targetPc.getResistance().getEarth();
				break;
			case L1Skills.ATTR_FIRE:
				resist = _targetPc.getResistance().getFire();
				break;
			case L1Skills.ATTR_WATER:
				resist = _targetPc.getResistance().getWater();
				break;
			case L1Skills.ATTR_WIND:
				resist = _targetPc.getResistance().getWind();
				break;
			}
		} else if (_calcType == PC_NPC || _calcType == NPC_NPC) {
		}

		dmg -= resist / 2;

		if (dmg < 1) {
			dmg = 1;
		}

		return dmg;
	}
	
	public void Start() {
		
		
		ThreadPool.getInstance().scheduleRequired(new Runnable(){
			@Override
			public void run(){
				if (!_targetPc.hasSkillEffect(L1SkillId.COUNTER_MAGIC)) {
					if (_targetPc.hasSkillEffect(L1SkillId.리버레이션)) {
						int 요정 = (int) ((double)Config.이터제약  * Config.리버레이션절감);
						L1DamagePoison.doInfection(_pc, _targetPc, 1000, 요정, true, 1);
					} else {
						int B = _random.nextInt(Config.이터이차최대-Config.이터이차최소) + Config.이터이차최소;
						L1DamagePoison.doInfection(_pc, _targetPc, 1000, B, true, 1);
					}
				}
			}
		}, Config.이터제약);
		
		
	
		
	}
public void Start1() {
		
		
		ThreadPool.getInstance().scheduleRequired(new Runnable(){
			@Override
			public void run(){
				if (!_targetNpc.hasSkillEffect(L1SkillId.COUNTER_MAGIC)) {
				int B = _random.nextInt(Config.이터이차최대-Config.이터이차최소) + Config.이터이차최소;
				L1DamagePoison.doInfection(_pc, _targetPc, 1000, B, true, 1);
				
				}
			}
		}, Config.이터제약);
		
		
	
		
	}
	public void commit(int damage, int drainMana) {
		if (_calcType == PC_PC || _calcType == NPC_PC) {
			commitPc(damage, drainMana);
		} else if (_calcType == PC_NPC || _calcType == NPC_NPC) {
			commitNpc(damage, drainMana);
		}

		if (!Config.ALT_ATKMSG) {
			return;
		}
		if (_targetPc == null && _targetNpc == null)
			return;
		if (_pc != null && _pc.isGm()) {
			SC_SystemMessage.send(_pc, "\\fD["
					+ _pc.getName()
					+ "] ==> ["
					+ (_targetPc == null ? _targetNpc.getName() : _targetPc
							.getName())
					+ "][== "
					+ damage
					+ " MAG ==][HP "
					+ (_targetPc == null ? _targetNpc.getCurrentHp()
							: _targetPc.getCurrentHp()) + "]");
		}
		if (_targetPc != null && _targetPc.isGm()) {
			SC_SystemMessage.send(_targetPc, "\\fZ["
							+ _targetPc.getName() + "] <== ["
							+ (_pc == null ? _npc.getName() : _pc.getName())
							+ "][== " + damage + " MAG ==][HP "
							+ _targetPc.getCurrentHp() + "]");
		}
		/*if (Config.ALT_ATKMSG) {
			if ((_calcType == PC_PC || _calcType == PC_NPC) && !_pc.isGm()) {
				return;
			}
			if ((_calcType == PC_PC || _calcType == NPC_PC) && !_targetPc.isGm()) {
				return;
			}
		}*/
		
		//String msg0 = "";
		//String msg2 = "";
	//	String msg3 = "";
		//String msg4 = "";

		/*if (_calcType == PC_PC || _calcType == PC_NPC) {
			msg0 = _pc.getName();
		} else if (_calcType == NPC_PC) {
			msg0 = _npc.getName();
		}

		if (_calcType == NPC_PC || _calcType == PC_PC) {
			msg4 = _targetPc.getName();
			msg2 = "HP:" + _targetPc.getCurrentHp();
		} else if (_calcType == PC_NPC) {
			msg4 = _targetNpc.getName();
			msg2 = "HP:" + _targetNpc.getCurrentHp();
		}

		msg3 = "DMG:" + damage;

		if (_calcType == PC_PC || _calcType == PC_NPC) {
			SC_SystemMessage.send(_pc, "\\fR[" + msg0 + "->" + msg4 + "] " + msg3 + " / " + msg2);
		}
		if (_calcType == NPC_PC || _calcType == PC_PC) {
			SC_SystemMessage.send(_targetPc, "\\fY[" + msg0 + "->" + msg4 + "] " + msg3 + " / " + msg2);
		}
	}*/
	}
	private void commitPc(int damage, int drainMana) {
		if (_targetPc.hasSkillEffect(ABSOLUTE_BARRIER)) {
			damage = 0;
			drainMana = 0;
		}
		if (_targetPc.hasSkillEffect(ICE_LANCE)) {
			damage = 0;
			drainMana = 0;
		}
		if (_targetPc.hasSkillEffect(EARTH_BIND)) {
			damage = 0;
			drainMana = 0;
		}
		if (_targetPc.hasSkillEffect(MOB_BASILL)) { // 바실얼리기대미지0
			damage = 0;
			drainMana = 0;
		}
		if (_targetPc.hasSkillEffect(MOB_COCA)) { // 코카얼리기대미지0
			damage = 0;
			drainMana = 0;
		}

		if (_calcType == PC_PC) {
			if (drainMana > 0 && _targetPc.getCurrentMp() > 0) {
				if (drainMana > _targetPc.getCurrentMp()) {
					drainMana = _targetPc.getCurrentMp();
				}
				int newMp = _pc.getCurrentMp() + drainMana;
				_pc.setCurrentMp(newMp);
			}
			_targetPc.receiveManaDamage(_pc, drainMana);
			_targetPc.receiveDamage(_pc, damage, applyMagicStack);
		} else if (_calcType == NPC_PC) {
			_targetPc.receiveDamage(_npc, damage, applyMagicStack);
		}
	}
	
	private void commitNpc(int damage, int drainMana) {
		if (_targetNpc.hasSkillEffect(ICE_LANCE)) {
			damage = 0;
			drainMana = 0;
		}
		if (_targetNpc.hasSkillEffect(EARTH_BIND)) {
			damage = 0;
			drainMana = 0;
		}
		if (_targetNpc.hasSkillEffect(MOB_BASILL)) { // 바실얼리기대미지0
			damage = 0;
			drainMana = 0;
		}
		if (_targetNpc.hasSkillEffect(MOB_COCA)) { // 코카얼리기대미지0
			damage = 0;
			drainMana = 0;
		}
		if (_targetNpc.getNpcTemplate().get_gfxid() == 7684 && _pc.hasSkillEffect(PAP_FIVEPEARLBUFF)) {
			damage = 1;
			drainMana = 0;
		}
		if (_targetNpc.getNpcTemplate().get_gfxid() == 7805 && _pc.hasSkillEffect(PAP_MAGICALPEARLBUFF)) {
			damage = 1;
			drainMana = 0;
		}
		if (_targetNpc.getNpcTemplate().get_gfxid() == 7720) {
			damage = 1;
			drainMana = 0;
		}

		if (_calcType == PC_NPC) {
			if (drainMana > 0) {
				int drainValue = _targetNpc.drainMana(drainMana);
				int newMp = _pc.getCurrentMp() + drainValue;
				_pc.setCurrentMp(newMp);
			}
			_targetNpc.ReceiveManaDamage(_pc, drainMana);
			_targetNpc.receiveDamage(_pc, damage);
		} else if (_calcType == NPC_NPC) {
			_targetNpc.receiveDamage(_npc, damage);
		}
	}


}