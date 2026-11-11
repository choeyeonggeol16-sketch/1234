package l1j.server.server.model;

import l1j.server.commons;
import l1j.server.Template.SpellProp.SpellProbabilityLoader;
import l1j.server.Templates.L1Skills;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.configure.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.item.MagicDoll;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.EffectUtil;
import l1j.server.server.utils.SkillUtil;

public class Magic {

	private int _calcType;
	private final int PC_PC = 1;
	private final int PC_NPC = 2;
	private final int NPC_PC = 3;
	private final int NPC_NPC = 4;
	private L1PcInstance _pc = null;
	private L1PcInstance _targetPc = null;
	private L1NpcInstance _npc = null;
	private L1NpcInstance _targetNpc = null;

	private int _leverage = 10; // 1/10배로 표현한다.
	public void setLeverage(int i) {
		_leverage = i;
	}
	private int getLeverage() {
		return _leverage;
	}
	/**
	 * 초기 설정 
	 * @param attacker
	 * @param target
	 */
	public Magic(L1Character attacker, L1Character target) {
		if(attacker instanceof L1PcInstance) {
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
			if(target instanceof L1PcInstance) {
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
	/**
	 * 캐릭터의 현재 마법 보너스를 가져온다.
	 * @return
	 */
	private int getMagicBonus() {
		int result = 0;
		switch(_calcType) {
		case PC_PC: case PC_NPC: result = _pc.getMagicBonus(); break;
		case NPC_PC: case NPC_NPC: result = _npc.getMagicBonus(); break;
		}
		return result;
	}
	/**
	 * 캐릭터의 현재 라우풀 상태를 가져온다.
	 * @return
	 */
	private int getLawful() {
		int result = 0;
		switch(_calcType) {
		case PC_PC: case PC_NPC: result = _pc.getLawful(); break;
		case NPC_PC: case NPC_NPC: result = _npc.getLawful(); break;
		}
		return result;
	}

	/**
	 * 타겟의 현재 마방을 가져온다.
	 * @return
	 */
	private int getTargetMr() {
		int result = 0;
		switch(_calcType) {
		case PC_PC: case NPC_PC: result = _targetPc.getResistance().getEffectedMr(); break;
		case PC_NPC: case NPC_NPC: result = _targetNpc.getResistance().getEffectedMr(); break;
		}
		return result;
	}
	/**
	 * 공격자의 인트 값을 가져온다.
	 */
	private int getAttackerInt() {
		int result = 0;
		switch(_calcType) {
		case PC_PC: case PC_NPC: result = _pc.getAbility().getTotalInt(); break;
		case NPC_PC: case NPC_NPC: result = _npc.getAbility().getTotalInt(); break;
		}
		return result;
	}
	/**
	 * 타겟의 레벨을 가져온다.
	 */
	private int getTargetLevel() {
		int result = 0;
		switch(_calcType) {
		case PC_PC: case NPC_PC: result = _targetPc.getLevel(); break;
		case PC_NPC: case NPC_NPC: result = _targetNpc.getLevel(); break;
		}
		return result;
	}
	/**
	 * 현재 나의 마법명중을 계산한다. 엔피시 포함
	 * @return
	 */
	private int getMagicHit() {
		int result = 0;
		switch(_calcType) {
		case PC_PC: case PC_NPC: result = CalcUtil.getMagicHitStat(_pc.getAbility().getTotalInt()) + _pc.getMagicHit(); break;
		case NPC_PC: case NPC_NPC: result = CalcUtil.getMagicHitStat(_npc.getAbility().getTotalInt()) + _npc.getMagicHit(); break;
		}
		return result;
	}
	/**
	 * 플레이어의 마법 사용 판정 여부를 판단한다.
	 * @param skillId
	 * @return
	 */
	public boolean calcSkillProbability(int skillId) {
		int probability = 0;
		boolean isSuccess = false;

		// 캔슬 레이션 상태 처리 
		if(skillId == SkillId.캔슬레이션) {
			if (_calcType == PC_PC && _pc != null && _targetPc != null) {
				if (_pc.getId() == _targetPc.getId()) {
					return true;
				}
				if (_pc.getClanid() > 0) {
					if(_pc.getClanid() == _targetPc.getClanid()) {
						return true;
					}				
				}
				if (_pc.isInParty()) {
					if (_pc.getParty().isMember(_targetPc)) {
						return true;
					}
				}
			}
		}
		// 체크존 일경우 
		if(!checkZone(skillId)) {
			return false;
		} 
		// 50렙 이상 npc 에게 아래 마법  판정
		if(_calcType == PC_NPC && _targetNpc.getLevel() >= 50) {
			if (skillId == SkillId.웨폰_브레이크 || skillId == SkillId.슬로우 || skillId == SkillId.인탱글 
					|| skillId == SkillId.사일런스 || skillId == SkillId.이레이즈_매직 || skillId == SkillId.에이리어_오브_사일런스
					|| skillId == SkillId.윈드_셰클 || skillId == SkillId.스트라이커_게일 || skillId == SkillId.쇼크_스턴 || skillId == SkillId.포그_오브_슬리핑
					|| skillId == SkillId.아이스_랜스 || skillId == SkillId.플루트_워터 || skillId == SkillId.엘리멘탈_폴다운 || skillId == SkillId.리턴_투_네이쳐 || skillId == SkillId.아머_브레이크) {
				return false;
			}
		} 	
		// 확률계 스킬 처리 
		probability = calcProbability(skillId);

		if(skillId == SkillId.에이리어_오브_사일런스 && _targetPc != null) {
			if (_pc != _targetPc) {
				if (CommonUtil.randomRange(1, 100) <= probability) {
					isSuccess = true;	
					S_SkillSound ss = S_SkillSound.get(_targetPc.getId(), EffectUtil.에어리어_오브_사일런스_이팩트);
					_pc.sendPackets(ss);
					Broadcaster.broadcastPacket(_pc, ss);	
				} 
			} else {
				if (CommonUtil.randomRange(1, 100) <= probability) {
					isSuccess = true;		
				} else {
					isSuccess = false;		
				}
			}
			// 에싸만 따로 보라돌이 처리 
			if (_targetPc instanceof L1PcInstance) {
				L1PcInstance target = (L1PcInstance) _targetPc;
				L1PinkName.onAction(target, _pc);
			}
		}
		if(_calcType == PC_PC || _calcType == NPC_PC) {
			if(_targetPc.hasSkillEffect(SkillId.앱솔루트_배리어)) { // 타겟이 앱솔루트 배리어 상태일 때
				if(SkillUtil.isSkilldAbol(skillId)) {
					if(CommonUtil.randomRange(1, 100) <= probability) {
						isSuccess = true;
					}
				}
			} else if(_targetPc.hasSkillEffect(SkillId.아이스_랜스) || _targetPc.hasSkillEffect(SkillId.어스_바인드) || _targetPc.hasSkillEffect(SkillId.상태_동결)) {
				if(!SkillUtil.isNotSkills(skillId)) { // 해당 스킬중에 사용 불가한 스킬.
					if(CommonUtil.randomRange(1, 100) <= probability) {
						isSuccess = true;
					}
				}	 						
			} else {
				int 확률마법회피 =  _targetPc.getResistance().getProbabilityMagicEvasion();
				if (CommonUtil.randomRange(1, 100) <= 확률마법회피) { // 확률 마법 아이템을 착용했을 때
					isSuccess = false;
				} else { // 그게 아닐 경우 
					if (CommonUtil.randomRange(1, 100) <= probability) {
						isSuccess = true;
					} else {
						isSuccess = false;	
					}		
				}			
			}
		} else { // 엔피시 일때 
			if (_targetNpc.hasSkillEffect(SkillId.커스_블라인드) || _targetNpc.hasSkillEffect(SkillId.다크니스)) { // 해당 스킬상태인경우 몬스터를 깨우려고 추가.
				_targetNpc.removeSkillEffect(SkillId.커스_블라인드);
				_targetNpc.removeSkillEffect(SkillId.다크니스);
			}
			if(_targetNpc.hasSkillEffect(SkillId.아이스_랜스) || _targetNpc.hasSkillEffect(SkillId.어스_바인드) || _targetNpc.hasSkillEffect(SkillId.상태_동결)) {
				if(!SkillUtil.isNotSkills(skillId)) { // 해당 스킬중에 사용 불가한 스킬.
					if(CommonUtil.randomRange(1, 100) <= probability) {
						isSuccess = true;
					}
				}
			} else {
				if (CommonUtil.randomRange(1, 100) <= probability) {
					isSuccess = true;
				} else {
					isSuccess = false;	
				}	
			}
		}
		// 언데드 몬스터 버서커 상태 처리
		if (!isSuccess & skillId == SkillId.턴_언데드) {	
			if (_calcType == PC_NPC) {	
				_targetNpc.setHate(_pc, 1);
				if(CommonUtil.randomRange(1, 100) <= 20) {
					Broadcaster.broadcastPacket(_targetNpc, S_SkillSound.get(_targetNpc.getId(), EffectUtil.버서커_이팩트));
					Broadcaster.broadcastPacket(_targetNpc, S_SkillHaste.get(_targetNpc.getId(), 1, 0));
					_targetNpc.setMoveSpeed(1);
				}
			}
		}
		// 운영자 메세지용 
		if (!Config.ALT_ATKMSG) {
			return isSuccess; 
		}
		if (this._targetPc == null && this._targetNpc == null) {
			return isSuccess; 
		}
		String msg2 = "확률:" + probability + "%";
		String msg3 = "";
		if (isSuccess) {
			msg3 = "성공";
		} else {
			msg3 = "실패";
		} 
		if (this._pc != null && this._pc.isGm()) {
			this._pc.sendPackets(new S_SystemMessage("\\fT[" + this._pc.getName() + "] ==> [" + ((this._targetPc == null) ? this._targetNpc.getName() : this._targetPc.getName()) + "][== " + msg2 + " ==][" + msg3 + "]"), true); 
		}
		if (this._targetPc != null && this._targetPc.isGm()) {
			this._targetPc.sendPackets(new S_SystemMessage("\\fY[" + this._targetPc.getName() + "] <== [" + ((this._pc == null) ? this._npc.getName() : this._pc.getName()) + "][== " + msg2 + " ==][" + msg3 + "]"), true); 
		}
		return isSuccess;
	}
	/**
	 * 현재 플레이어의 존 타입을 돌려준다.
	 * @param skillId
	 * @return
	 */
	private boolean checkZone(int skillId) {
		if (_pc != null && _targetPc != null) {
			if (_pc.getZoneType() == 1 || _targetPc.getZoneType() == 1) { // 세이프티 존
				if (skillId == SkillId.웨폰_브레이크 || skillId == SkillId.슬로우 || skillId == SkillId.마나_드레인 || skillId == SkillId.다크니스
						|| skillId == SkillId.위크니스 || skillId == SkillId.디지즈 || skillId == SkillId.디케이_포션 || skillId == SkillId.매스_슬로우 
						|| skillId == SkillId.인탱글 || skillId == SkillId.이레이즈_매직 || skillId == SkillId.어스_바인드 || skillId == SkillId.에이리어_오브_사일런스 
						|| skillId == SkillId.윈드_셰클 || skillId == SkillId.스트라이커_게일 || skillId == SkillId.쇼크_스턴 || skillId == SkillId.포그_오브_슬리핑 
						|| skillId == SkillId.아이스_랜스 || skillId == SkillId.플루트_워터 || skillId == SkillId.캔슬레이션 || skillId == SkillId.리턴_투_네이쳐
						|| skillId == SkillId.엘리멘탈_폴다운 || skillId == SkillId.커스_포이즌 || skillId == SkillId.커스_블라인드|| skillId == SkillId.사일런스 
						|| skillId == SkillId.다크_블라인드 || skillId == SkillId.셰이프_체인지) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 실질적으로 스킬의 확률을 계산한다.
	 * @param skillId
	 * @return
	 */
	private int calcProbability(int skillId) {
		int defenseLevel = getTargetLevel(), probability = 0, attackInt = getAttackerInt(), defenseMr = getTargetMr();
		L1Character receiver = null;
		// 타겟 지정
		switch (_calcType) {
		case PC_PC: receiver = _targetPc; break;
		case PC_NPC: receiver = _targetNpc; break;
		}
		if(skillId == SkillId.리턴_투_네이쳐) {
			if (_targetNpc instanceof SummonInstance) {
				SummonInstance summon = (SummonInstance) _targetNpc;
				defenseLevel = summon.getMaster().getLevel();
			}
		}
		// 확률계 스킬 관련
		if ((_calcType == PC_PC || _calcType == PC_NPC) && SpellProbabilityLoader.getInstance().containsProbability(skillId)) {
			probability = SpellProbabilityLoader.getInstance().calc_probability(skillId, _pc, receiver, attackInt, defenseMr);	
		} else { 
			L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillId);
			// 엔피시 마법 확률 산출
			int 몬스터매직보너스 = getMagicBonus(), 몬스터매직레벨 = _npc.getMagicLevel(), 타겟마방 = getTargetMr(),
					마법적중 = getMagicHit(), 마법계수a = l1skills.getProbabilityA(),
					마법계수B = l1skills.getProbabilityB(), 마법기본확률 = l1skills.getProbabilityValue(), 
					타겟기술내성포인트 = _targetPc.getResistance().getregist_technology(), 타겟정령내성포인트 = _targetPc.getResistance().getregist_spirit();	

			if (l1skills.getSkillType().equalsIgnoreCase("MAGICHIT")) {	
				probability = (마법계수a * (몬스터매직보너스 + 몬스터매직레벨)) + (마법적중 + 마법계수B + 마법기본확률) - 타겟마방;
			} else if(l1skills.getSkillType().equalsIgnoreCase("ABILRITY")) {
				if (_npc.getLevel() >= defenseLevel) {			
					probability = (((_npc.getLevel() - defenseLevel) * 마법계수a) + (마법적중 - 타겟기술내성포인트) + 마법기본확률);
				} else if(_npc.getLevel() < defenseLevel) {
					probability = (((_npc.getLevel() - defenseLevel) * 마법계수B) + (마법적중 - 타겟기술내성포인트) + 마법기본확률);
				}
			} else if(l1skills.getSkillType().equalsIgnoreCase("SPIRIT")) {
				if (_npc.getLevel() >= defenseLevel) {			
					probability = (((_npc.getLevel() - defenseLevel) * 마법계수a) + (마법적중 - 타겟정령내성포인트) + 마법기본확률);
				} else if(_npc.getLevel() < defenseLevel) {
					probability = (((_npc.getLevel() - defenseLevel) * 마법계수B) + (마법적중 - 타겟정령내성포인트) + 마법기본확률);
				}
			}
			probability = Math.max(probability, 10);
			probability = Math.min(probability, 90);
		}			
		return probability;
	}

	/**
	 * 마법 데미지를 산출한다.
	 * @param skillId
	 * @return
	 */
	public int calcMagicDamage(int skillId) {
		int damage = 0;
		switch (_calcType) {
		case PC_PC: case NPC_PC: 
			damage = calcPcMagicDamage(skillId);
			// 마안 구현부
			if(_targetPc != null && _targetPc.hasSkillEffect(SkillId.수룡_마안)) {
				if(CommonUtil.randomRange(1, 100) <= 10) {
					damage >>= 1;
				}
			}	
			if (damage > _targetPc.getCurrentHp()) {
				damage = _targetPc.getCurrentHp();
			} else if(damage < 0) {
				damage = 0;
			}
			break;
		case PC_NPC: case NPC_NPC: 
			damage = calcNpcMagicDamage(skillId);
			if(damage > _targetNpc.getCurrentHp()) {
				damage = _targetNpc.getCurrentHp();
			} else if(damage < 0) {
				damage = 0;
			}
			break;
		}	
		return damage;
	}
	/**
	 * 엔피시 -> 플레이어 : 플레이어 -> 플레이어 데미지를 계산한다.
	 * @param skillId
	 * @return
	 */
	private int calcPcMagicDamage(int skillId) {
		int dmg = 0;
		dmg = calcSkillDamage(skillId);

		if(_targetPc != null && dmg > 0) {
			// 룸티스 검은빛 귀걸이 구현	
			if(_pc != null && _pc.toArmorEquipped().isRoomtisblackArmor()) {
				int 인챈트레벨 = _pc.toArmorEquipped().getRoomtisblackEn(), 확률 = (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 2);
				if (인챈트레벨 > 4 && CommonUtil.randomRange(1, 100) <= 확률) {
					dmg += 20;
					_pc.sendForMe(EffectUtil.룸티스_검은빛_귀걸이_이팩트);
				}
				// 축복 룸티스 검은빛 귀걸이 구현 
			} else if(_pc != null && _pc.toArmorEquipped().isBlessRoomtisBlackArmor()) {
				int 인챈트레벨 = _pc.toArmorEquipped().getBlessRoomtisBlackEn(), 확률 = (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 3 : 2);
				if (인챈트레벨 > 3 && CommonUtil.randomRange(1, 100) <= 확률) {
					dmg += 20;
					_pc.sendForMe(EffectUtil.룸티스_검은빛_귀걸이_이팩트);
				}
			}		
			if (_calcType == PC_PC) {			
				dmg += MagicDoll.useSkillByDoll(_pc, _targetPc);

				if (_targetPc.hasSkillEffect(SkillId.카운터_미러)) {	
					int 인트 = _targetPc.getAbility().getTotalInt() >= 25 ? 25 : _targetPc.getAbility().getTotalInt(), 확률 = Config.카운터_미러_발동_확률;
					if(CommonUtil.randomRange(1, 100) <= (확률 + 인트)) {
						_targetPc.sendAll(EffectUtil.카운터_미러_이팩트);
						_pc.receiveDamage(_targetPc, dmg); 
						dmg = 0;
						_pc.broadcastPacket(S_DoActionGFX.get(_pc.getId(), ActionCodes.ACTION_Damage));	
						_targetPc.killSkillEffectTimer(SkillId.카운터_미러);	
					}
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
			}
			// 펫 , 서먼 데미지 감소 처리 
			if (_calcType == NPC_PC) { 	
				dmg = (dmg * getLeverage()) / 10;		
				boolean isNowWar = false;
				int castleId = L1CastleLocation.getCastleIdByArea(_targetPc);
				if (castleId > 0) {
					isNowWar = CastleWarBusiness.getInstance().isNowWar(castleId);
				}
				if (!isNowWar) {
					if (_npc instanceof PetInstance) {
						dmg /= 8;
					}
					if (_npc instanceof SummonInstance) {
						SummonInstance summon = (SummonInstance) _npc;
						if (summon.isExsistMaster()) {
							dmg /= 8;
						}
					}			
				}
				if (_targetPc.hasSkillEffect(SkillId.카운터_미러)) {	
					int 인트 = _targetPc.getAbility().getTotalInt() >= 25 ? 25 : _targetPc.getAbility().getTotalInt(), 확률 = Config.카운터_미러_발동_확률;
					if(CommonUtil.randomRange(1, 100) <= (확률 + 인트)) {
						_targetPc.sendAll(EffectUtil.카운터_미러_이팩트);
						_npc.receiveDamage(_targetPc, dmg); 
						dmg = 0;
						_npc.broadcastPacket(S_DoActionGFX.get(_npc.getId(), ActionCodes.ACTION_Damage));	
						_targetPc.killSkillEffectTimer(SkillId.카운터_미러);	
					}	
				}
				if (_targetPc.getMapId() == 777 || _targetPc.getMapId() == 778|| _targetPc.getMapId() == 779) {
					dmg *= 0.3;
				}
			}
			commons.getReducIitems(_targetPc, dmg);
	//		if (_targetPc.hasSkillEffect(SkillId.운세_버프_A)) {
	//			dmg -= 3;
	//		}
//			if (_targetPc.hasSkillEffect(SkillId.운세_버프_B)) {
//				dmg -= 2;
//			}
			dmg -= _targetPc.getDamageReduction();

			if (_targetPc != null && _targetPc.hasSkillEffect(SkillId.이뮨_투함)) {
				dmg *= Config.IMMNUE_TO_HARM_RATE;
			}
		}
		return dmg;
	}
	/**
	 * 플레이어 -> 엔피시 : 엔피시 -> 엔피시 데미지 를 산출한다.
	 * @param skillId
	 * @return
	 */
	private int calcNpcMagicDamage(int skillId) {
		int dmg = 0;
		dmg = calcSkillDamage(skillId);
		dmg = (dmg * getLeverage()) / 10;

		if(_targetNpc != null && dmg > 0) {
			if (_targetNpc != null && _targetNpc.hasSkillEffect(SkillId.커스_블라인드) || _targetNpc.hasSkillEffect(SkillId.다크니스)) {
				_targetNpc.removeSkillEffect(SkillId.커스_블라인드);
				_targetNpc.removeSkillEffect(SkillId.다크니스);
			}	
			// 룸티스 검은빛 귀걸이 구현
			if(_pc != null &&_pc.toArmorEquipped().isRoomtisblackArmor()) {
				int 인챈트레벨 = _pc.toArmorEquipped().getRoomtisblackEn(),
						확률 = (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 2);
				if (인챈트레벨 > 4 && CommonUtil.randomRange(1, 100) <= 확률) {
					dmg += 20;
					_pc.sendForMe(EffectUtil.룸티스_검은빛_귀걸이_이팩트);
				}
				// 축복 룸티스 검은빛 귀걸이 구현 
			} else if(_pc != null && _pc.toArmorEquipped().isBlessRoomtisBlackArmor()) {
				int 인챈트레벨 = _pc.toArmorEquipped().getBlessRoomtisBlackEn(), 확률 = (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 3 : 2);
				if (인챈트레벨 > 3 && CommonUtil.randomRange(1, 100) <= 확률) {
					dmg += 20;
					_pc.sendForMe(EffectUtil.룸티스_검은빛_귀걸이_이팩트);
				}
			}
			if (_calcType == PC_NPC) { // 플레이어로부터 애완동물, 서먼에 공격
				dmg += MagicDoll.useSkillByDoll(_pc, _targetNpc);

				boolean isNowWar = false;
				int castleId = L1CastleLocation.getCastleIdByArea(_targetNpc);
				if (castleId > 0) {
					isNowWar = CastleWarBusiness.getInstance().isNowWar(castleId);
				}
				if (!isNowWar) {
					if (_targetNpc instanceof PetInstance) {
						dmg /= 8;
					}
					if (_targetNpc instanceof SummonInstance) {
						SummonInstance summon = (SummonInstance) _targetNpc;
						if (summon.isExsistMaster()) {
							dmg /= 8;
						}
					}
				}	
			}
			if (_calcType == NPC_NPC) {
				if(_targetNpc instanceof PetInstance || _targetNpc instanceof SummonInstance) {
					dmg /= 8; 
				}		
			}	
		}
		return dmg;
	}	
	/**
	 * 스킬 데미지를 실질적으로 계산해서 산출한다.
	 * @param skillId
	 * @return
	 */
	private int calcSkillDamage(int skillId) {
		L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillId);
		int 대미지최소 = l1skills.getDamageMin(), 데미지최대 = l1skills.getDamageMax(), 고유데미지 = l1skills.getDamageValue(),
				마법데미지 = 0, 인트스펠 = 0, 타겟엠알 = getTargetMr(), 내마법명중 = getMagicHit();
		// 기본 데미지 설정 
		int 기본데미지 = CommonUtil.random(((데미지최대 - 대미지최소) + 1) + 대미지최소) + 고유데미지;
		switch(_calcType) {
		case PC_PC: case PC_NPC: int trueSp = _pc.getSp() - _pc.getTrueSp(); 인트스펠 = (_pc.getAbility().getTotalInt() + trueSp) - 12; break;
		case NPC_PC: case NPC_NPC: 인트스펠 = _npc.getAbility().getTotalInt(); break;
		}	
		마법데미지 += 기본데미지;	
		// 속성 방어 감소 처리 
		double 속성방어 = calcAttrResistance(l1skills.getAttr());
		double 중간마법데미지 = ((1.0 + 3.0 / 32.0 * 인트스펠) - 속성방어);

		// 디스 인티 그레이트 라우풀 성향 처리  카오틱이 되었을 경우 33퍼 데미지 감소를 한다.
		if(skillId == SkillId.디스_인티_그레이트) {
			int lawful = _pc.getLawful();			
			if (lawful < 0) {
				중간마법데미지 += (lawful / 21626);
			}
		}		
		마법데미지 *= 중간마법데미지;	
		if (_calcType == PC_PC || _calcType == PC_NPC) {
			// 광역 마법을 제외한 공격 마법 1,2단계 스킬 제외 
			int magicCri = _pc.getMagicCritical(); 
			if(skillId == SkillId.콘_오브_콜드 || skillId == SkillId.콜_라이트닝 
					|| skillId == SkillId.선버스트 || skillId == SkillId.이럽션 || skillId == SkillId.디스_인티_그레이트) {
				if (CommonUtil.randomRange(1, 100)  <= magicCri) {
					마법데미지 *= 1.5;
				}
			}
			// 발라카스 완력, 예지력  근거리 일경우 
			if(_pc.toArmorEquipped().isValakasArmorMagic()) {
				int 인챈트레벨 = _pc.toArmorEquipped().getValakasArmorMagicEn(), 확률 = (인챈트레벨 >= 9 ? 5 : 인챈트레벨 == 8 ? 4 : 인챈트레벨 == 7 ? 3 : 2);
				if(CommonUtil.randomRange(1, 100) <= 확률) {
					마법데미지 *= 1.5;
					_pc.sendAll(EffectUtil.발라카스_가호);
				}
			}	
		} else { // 엔피시일경우 
			마법데미지 *= 1.0;								
		}	
		double 마법방어 = 0;
		// 마방데미지 감소 처리 
		if(타겟엠알 <= 100) {
			마법방어 = Math.floor((타겟엠알 - 내마법명중) / 2);
		} else if (타겟엠알 >= 100) {
			마법방어 = Math.floor((타겟엠알 - 내마법명중) / 10);
		}
		double 마법방어데미지감소 = 0;
		if(타겟엠알 <= 100) {
			마법방어데미지감소 = 1.2 - 0.01 * 마법방어;
		} else if(타겟엠알 > 100) {
			마법방어데미지감소 = 0.6 - 0.01 * 마법방어;
		}
		마법데미지 *= 마법방어데미지감소;

		return 마법데미지;
	}

	/**
	 * 힐량을 계산해서 산출한다. 언데드 몬스터 포함 
	 * @param skillId
	 * @return
	 */
	public int calcHealing(int skillId) {
		L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillId);
		int 힐량최소 = l1skills.getDamageMin(), 힐량최대 = l1skills.getDamageMax(), 힐량 = 0, 마법보너스 = getMagicBonus(); 
		if (마법보너스 > 25) {
			마법보너스 = 25;
		}		
		int 중간힐량 = 힐량최소 + 마법보너스;
		for (int i = 0; i < 중간힐량; i++) {
			힐량 += (CommonUtil.randomRange(1, 힐량최대));
		}		
		double alignmentRevision = 1.0;
		if (getLawful() > 0) {
			alignmentRevision += (getLawful() / 32768.0);
		}		
		힐량 *= alignmentRevision;	
		힐량 = (힐량 * getLeverage()) / 10;	
		return 힐량;
	}

	/**
	 * 대상의 속성 데미지 감소 처리를 한다.
	 * @param attr
	 * @return
	 */ 
	private double calcAttrResistance(int attr) {
		int resist = 0;
		if (_calcType == PC_PC || _calcType == NPC_PC) {
			switch (attr) {
			case L1Skills.ATTR_EARTH: resist = _targetPc.getResistance().getEarth(); break;
			case L1Skills.ATTR_FIRE: resist = _targetPc.getResistance().getFire(); break; 
			case L1Skills.ATTR_WATER: resist = _targetPc.getResistance().getWater(); break; 
			case L1Skills.ATTR_WIND: resist = _targetPc.getResistance().getWind(); break; 
			}
		} else if(_calcType == PC_NPC || _calcType == NPC_NPC) {
			switch (attr) {
			case L1Skills.ATTR_EARTH: resist = _targetNpc.getNpcTemplate().getWeakWind(); break;
			case L1Skills.ATTR_FIRE: resist = _targetNpc.getNpcTemplate().getWeakEarth(); break; 
			case L1Skills.ATTR_WATER: resist = _targetNpc.getNpcTemplate().getWeakFire(); break; 
			case L1Skills.ATTR_WIND: resist = _targetNpc.getNpcTemplate().getWeakWater(); break; 
			}
		}	
		int resistFloor = (int) (0.32 * Math.abs(resist));
		if (resist >= 0) {
			resistFloor *= 1;
		} else {
			resistFloor *= -1;
		}
		double attrDeffence = resistFloor / 32.0;
		return attrDeffence;
	}
	/**
	 * 데미지 계산결과과 반영한다.
	 * @param damage
	 * @param drainMana
	 */
	public void commit(int damage, int drainMana) {
		switch(_calcType) {
		case PC_PC: case NPC_PC: commitPc(damage, drainMana); break;
		case PC_NPC: case NPC_NPC: commitNpc(damage, drainMana); break;
		}
	}
	/**
	 * 플레이어의 데미지 계산결과을 반영한다.
	 * @param damage
	 * @param drainMana
	 */
	private void commitPc(int damage, int drainMana) {
		if(_targetPc.hasSkillEffect(SkillId.앱솔루트_배리어)|| _targetPc.hasSkillEffect(SkillId.어스_바인드) 
				|| _targetPc.hasSkillEffect(SkillId.상태_동결) || _targetPc.hasSkillEffect(SkillId.아이스_랜스)) {
			drainMana = 0;
			damage = 0;
		} 
		if(_calcType == PC_PC) {
			if (drainMana > 0 && _targetPc.getCurrentMp() > 0) {
				if (drainMana > _targetPc.getCurrentMp()) {
					drainMana = _targetPc.getCurrentMp();
				}
				int newMp = _pc.getCurrentMp() + drainMana;
				_pc.setCurrentMp(newMp);
			}
			_targetPc.receiveManaDamage(_pc, drainMana);
			_targetPc.receiveDamage(_pc, damage);
		} else if(_calcType == NPC_PC) {
			_targetPc.receiveDamage(_npc, damage);
		}
	}
	/**
	 * 엔피시의 데미지 계산결과을 반영한다.
	 * @param damage
	 * @param drainMana
	 */
	private void commitNpc(int damage, int drainMana) {
		// 스킬 데미지 처리 
		if(_targetNpc.hasSkillEffect(SkillId.어스_바인드)|| _targetNpc.hasSkillEffect(SkillId.상태_동결) 
				|| _targetNpc.hasSkillEffect(SkillId.아이스_랜스)) {
			drainMana = 0;
			damage = 0;
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
