package l1j.server.server.model;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;

import static l1j.server.server.model.character.skill.L1SkillId.ABSOLUTE_BARRIER;
import static l1j.server.server.model.character.skill.L1SkillId.COUNTER_MAGIC;
import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.ERASE_MAGIC;
import static l1j.server.server.model.character.skill.L1SkillId.ICE_LANCE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import static l1j.server.server.model.character.skill.L1SkillId.TURN_UNDEAD;

import java.util.Random;

import l1j.server.Config;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;


import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;
import l1j.server.server.templates.L1Skills;

public class L1WeaponSkill {

	private static Random _random = new Random(System.nanoTime());

	private int _weaponId;

	private int _probability;

	private int _fixDamage;

	private int _randomDamage;

	private int _area;

	private int _skillId;

	private int _skillTime;

	private int _effectId;

	private int _effectTarget;

	private boolean _isArrowType;

	private int _attr;

	public L1WeaponSkill(int weaponId, int probability, int fixDamage, int randomDamage, int area, int skillId,
			int skillTime, int effectId, int effectTarget, boolean isArrowType, int attr) {
		_weaponId = weaponId;
		_probability = probability;
		_fixDamage = fixDamage;
		_randomDamage = randomDamage;
		_area = area;
		_skillId = skillId;
		_skillTime = skillTime;
		_effectId = effectId;
		_effectTarget = effectTarget;
		_isArrowType = isArrowType;
		_attr = attr;
	}

	public int getWeaponId() {
		return _weaponId;
	}

	public int getProbability() {
		return _probability;
	}

	public int getFixDamage() {
		return _fixDamage;
	}

	public int getRandomDamage() {
		return _randomDamage;
	}

	public int getArea() {
		return _area;
	}

	public int getSkillId() {
		return _skillId;
	}

	public int getSkillTime() {
		return _skillTime;
	}

	public int getEffectId() {
		return _effectId;
	}

	public int getEffectTarget() {
		return _effectTarget;
	}

	public boolean isArrowType() {
		return _isArrowType;
	}

	public int getAttr() {
		return _attr;
	}

	
	public static double 실렌의결의(L1PcInstance pc, L1Character cha, int enchant) { 
		double dmg = 20;
		int aa = _random.nextInt(Config.결심최대 - Config.결심최소) + Config.결심최소;
		int chance = _random.nextInt(100) + 1;
		int val = enchant * Config.결심인챈트 + Config.결심인챈트트;
		
		if (isFreeze(cha)) return 0;
		
		if (chance <= val) {
			dmg += aa + (enchant * Config.결심데미지인챈트);
			
			if (cha instanceof L1PcInstance) {
				// 유저 타격 시 (18488) 및 기존 결박 유지
				SC_SkillSound.send(pc, cha.getId(), 18488);
				SC_SkillSound.broadcast(pc, cha.getId(), 18488);
				
				L1PcInstance targetPc = (L1PcInstance) cha;
				targetPc.setSkillEffect(STATUS_FREEZE, 2000);
				SC_SkillSound.send(targetPc, targetPc.getId(), 4184);
				SC_SkillSound.broadcast(targetPc, targetPc.getId(), 4184);
				SC_Paralysis.send(targetPc, SC_Paralysis.TYPE_BIND, true);
			} else {
				// 몬스터 타격 시 광역 데미지 (18492)
				SC_SkillSound.send(pc, cha.getId(), 18492);
				SC_SkillSound.broadcast(pc, cha.getId(), 18492);
				
				L1PcInstance targetPc = null;
				L1NpcInstance targetNpc = null;
				for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) {
					if (object == null || !(object instanceof L1Character)) continue;
					if (object.getId() == pc.getId() || object.getId() == cha.getId()) continue;
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						if (targetPc.getZoneType() == 1) continue;
					}
					if (cha instanceof L1MonsterInstance && !(object instanceof L1MonsterInstance)) continue;
					if (cha instanceof L1PcInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance) {
						if (!(object instanceof L1PcInstance || object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance)) continue;
					}
					if (dmg <= 0) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						SC_DoActionGFX.send(targetPc.getNetConnection(), targetPc.getId(), ActionCodes.ACTION_Damage);
						SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage);
						targetPc.receiveDamage(pc, 0);
					} else if (object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance) {
						targetNpc = (L1NpcInstance) object;
						SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage);
						targetNpc.receiveDamage(pc, (int) dmg);
					}
				}
			}
		}
		return dmg;
	}

	
	public static double 사이하의집념(L1PcInstance pc, L1Character cha, int enchant) { 	
		double dmg = 20;
		// 다시 원본 Config 이름인 '고통'으로 복구
		int aa = _random.nextInt(Config.고통최대 - Config.고통최소) + Config.고통최소;
		int chance = _random.nextInt(100) + 1;
		
		// 원본 소스에 있던 오타(고통인챈트트) 그대로 복구
		int val = enchant * Config.고통인챈트 + Config.고통인챈트트; 
		
		if (isFreeze(cha)) return 0;
		
		if (chance <= val) {
			dmg += aa + (enchant * Config.고통데미지인챈트);
			
			if (cha instanceof L1PcInstance) {
				// 유저 타격 시 (18484)
				SC_SkillSound.send(pc, cha.getId(), 18484);
				SC_SkillSound.broadcast(pc, cha.getId(), 18484);
			} else {
				// 몬스터 타격 시 광역 데미지 (18486)
				SC_SkillSound.send(pc, cha.getId(), 18486);
				SC_SkillSound.broadcast(pc, cha.getId(), 18486);
				
				L1PcInstance targetPc = null;
				L1NpcInstance targetNpc = null;
				for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) {
					if (object == null || !(object instanceof L1Character)) continue;
					if (object.getId() == pc.getId() || object.getId() == cha.getId()) continue;
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						if (targetPc.getZoneType() == 1) continue;
					}
					if (cha instanceof L1MonsterInstance && !(object instanceof L1MonsterInstance)) continue;
					if (cha instanceof L1PcInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance) {
						if (!(object instanceof L1PcInstance || object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance)) continue;
					}
					if (dmg <= 0) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						SC_DoActionGFX.send(targetPc.getNetConnection(), targetPc.getId(), ActionCodes.ACTION_Damage);
						SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage);
						targetPc.receiveDamage(pc, 0);
					} else if (object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance) {
						targetNpc = (L1NpcInstance) object;
						SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage);
						targetNpc.receiveDamage(pc, (int) dmg);
					}
				}
			}
		}
		return dmg;
	}
	public static int 키링크데미지(L1PcInstance pc, L1Character cha) {
		int dmg = 0;
		int dice = 9;
		int diceCount = 5;
		int value = Config.키링크;
		int KiringkuDamage = 0;
		int charaIntelligence = 0;

		for (int i = 0; i < diceCount; i++) {
			KiringkuDamage += (_random.nextInt(dice) + 1);
		}
		KiringkuDamage += value;

		int spByItem = pc.getAbility().getSp() - pc.getAbility().getTrueSp();
		charaIntelligence = pc.getAbility().getTotalInt() + spByItem - 12;
		if (charaIntelligence < 1) {
			charaIntelligence = 1;
		}
		double KiringkuCoefficientA = (1.0 + charaIntelligence * 3.0 / 32.0);

		KiringkuDamage *= KiringkuCoefficientA;

		double Mrfloor = 0;
		if (cha.getResistance().getEffectedMrBySkill() <= 100) {
			Mrfloor = Math.floor((cha.getResistance().getEffectedMrBySkill() - pc.getTotalMagicHitup()) / 2);
		} else if (cha.getResistance().getEffectedMrBySkill() >= 100) {
			Mrfloor = Math.floor((cha.getResistance().getEffectedMrBySkill() - pc.getTotalMagicHitup()) / 10);
		}

		double KiringkuCoefficientB = 0;
		if (cha.getResistance().getEffectedMrBySkill() <= 100) {
			KiringkuCoefficientB = 1 - 0.01 * Mrfloor;
		} else if (cha.getResistance().getEffectedMrBySkill() > 100) {
			KiringkuCoefficientB = 0.6 - 0.01 * Mrfloor;
		}

		double Kiringkufloor = Math.floor(KiringkuDamage);

		dmg += Kiringkufloor + (pc.getWeapon().getEnchantLevel() * 2);

		dmg *= KiringkuCoefficientB;

		if (pc.getWeapon().getItem().getItemId() == 503) {
			SC_SkillSound.send(pc, pc.getId(), 6983);
			SC_SkillSound.broadcast(pc, pc.getId(), 6983);
		} else {
			SC_SkillSound.send(pc, pc.getId(), 7049);
			SC_SkillSound.broadcast(pc, pc.getId(), 7049);
		}
		return dmg;
	}

	public static double 악운의단검(L1PcInstance pc, L1PcInstance targetPc, L1ItemInstance weapon) {
		double dmg = 0;
		int chance = _random.nextInt(100) + 1;
		if (5 >= chance) {
			dmg = targetPc.getCurrentHp() / 1.2;
			if (targetPc.getCurrentHp() - dmg < 0) {
				dmg = 0;
			}
			String msg = weapon.getLogName();
			SC_ServerMessage.send(pc, 158, msg);
			pc.getInventory().removeItem(weapon, 1);
		}
		L1PinkName.onAction(targetPc, pc);
		return dmg;
	}

	public static void 붉은그림자의이도류(L1PcInstance pc, L1Character cha, int enchant) {
		int fettersTime = 2000;// 이펙트 타이머

		if (isFreeze(cha)) {
			return;
		}
		if ((_random.nextInt(100) + 1) <= 10) {
			if (cha instanceof L1PcInstance) {
				L1PcInstance targetPc = (L1PcInstance) cha;
                L1EffectSpawn.getInstance().spawnEffect(81182, fettersTime, cha.getX(), cha.getY(), cha.getMapId());
				targetPc.setSkillEffect(STATUS_FREEZE, fettersTime);
				SC_SkillSound.send(targetPc, targetPc.getId(), 4184);
				SC_SkillSound.broadcast(targetPc, targetPc.getId(), 4184);
				SC_Paralysis.send(targetPc, SC_Paralysis.TYPE_BIND, true);
			} else if (cha instanceof L1MonsterInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance || cha instanceof L1CompanionInstance) {
				L1NpcInstance npc = (L1NpcInstance) cha;
                if (!npc.trySetSkillEffect(STATUS_FREEZE, fettersTime)) {
                    throw new java.util.concurrent.RejectedExecutionException("NPC weapon bind admission exhausted");
                }
                L1EffectSpawn.getInstance().spawnEffect(81182, fettersTime, cha.getX(), cha.getY(), cha.getMapId());
				SC_SkillSound.broadcast(npc, npc.getId(), 4184);
				npc.setParalyzed(true);
			}
		}
	}

	public static void 체인소드(L1PcInstance pc) { // 체인소드 대미지.
		if (pc.getInventory().checkEquipped(123007)) {
			if (_random.nextInt(100) >= Config.약점노출 + 7)
				return;
		} else {

			if (_random.nextInt(100) >= Config.약점노출)
				return;
		}
		if(pc.hasSkillEffect(L1SkillId.CHAINSWORD1)){
			pc.killSkillEffectTimer(L1SkillId.CHAINSWORD1);
			pc.setSkillEffect(L1SkillId.CHAINSWORD2, 15000);
			SC_PacketBox.send(pc, SC_PacketBox.SPOT, 2);
		}else if(pc.hasSkillEffect(L1SkillId.CHAINSWORD2)){
			pc.killSkillEffectTimer(L1SkillId.CHAINSWORD2);
			pc.setSkillEffect(L1SkillId.CHAINSWORD3, 15000);
			SC_PacketBox.send(pc, SC_PacketBox.SPOT, 3);	
		}else if(pc.hasSkillEffect(L1SkillId.CHAINSWORD3)){
			if(pc.isPassive(MJPassiveID.FOU_SLAYER_BRAVE.toInt())){
				pc.setSkillEffect(L1SkillId.CHAINSWORD4, 15000);
				SC_PacketBox.send(pc, SC_PacketBox.SPOT, 4);
			}
		}else if(pc.hasSkillEffect(L1SkillId.CHAINSWORD4)){
		}else {
			pc.setSkillEffect(L1SkillId.CHAINSWORD1, 15000);
			SC_PacketBox.send(pc, SC_PacketBox.SPOT, 1);			
		}
	}

	public static void getDiseaseWeapon(L1PcInstance pc, L1Character cha, int weaponid) {
		int chance = _random.nextInt(100) + 1;
		int skilltime = weaponid == 412003 ? 64 : 20;
		if (7 >= chance) {
			if (!cha.hasSkillEffect(56)) {
				cha.addDmgup(-6);
				cha.getAC().addAc(12);
				if (cha instanceof L1PcInstance) {
					L1PcInstance target = (L1PcInstance) cha;
					SC_OwnCharAttrDef.send(target);
				}
			}
			cha.setSkillEffect(56, skilltime * 1000);
			SC_SkillSound.send(pc, cha.getId(), 2230);
			SC_SkillSound.broadcast(pc, cha.getId(), 2230);
			if (cha.hasSkillEffect(ERASE_MAGIC))
				cha.removeSkillEffect(ERASE_MAGIC);
		}
	}

	public static double 블레이즈쇼크(L1PcInstance pc, L1Character cha, int enchant) {
		double dmg = 0;
		int chance = _random.nextInt(100) + 1;
		int val = enchant * 1;
		if (val <= 0) {
			val = 1;
		} else
			val += 1;

		if (val >= chance) {
			int randmg = _random.nextInt(50) + 20;
			dmg = randmg;

			if (dmg < 20)
				dmg = 20;

			SC_SkillSound.send(pc, cha.getId(), 3939);
			SC_SkillSound.broadcast(pc, cha.getId(), 3939);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_FIRE);
	}

	public static double 블레이즈쇼크1(L1PcInstance pc, L1Character cha, int enchant) {
		double dmg = 0;
		int chance = _random.nextInt(100) + 1;
		int val = enchant * 1;
		if (val <= 0) {
			val = 1;
		} else
			val += 1;

		if (val >= chance) {
			int randmg = _random.nextInt(50) + 25;
			dmg = randmg;

			if (dmg < 25)
				dmg = 25;

			SC_SkillSound.send(pc, cha.getId(), 3939);
			SC_SkillSound.broadcast(pc, cha.getId(), 3939);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_FIRE);
	}

	public static double 체인소드블레이즈쇼크(L1PcInstance pc, L1Character cha, int enchant) {
		double dmg = 0;
		int chance = _random.nextInt(100) + 1;
		int val = enchant * 1;
		if (val <= 0) {
			val = 1;
		} else
			val += 1;

		if (val >= chance) {
			int randmg = _random.nextInt(50) + 20;
			dmg = randmg;

			if (dmg < 20)
				dmg = 20;

			SC_SkillSound.send(pc, cha.getId(), 3939);
			SC_SkillSound.broadcast(pc, cha.getId(), 3939);
		}
		if (_random.nextInt(100) < 15) {
			if(pc.hasSkillEffect(L1SkillId.CHAINSWORD1)){
				pc.killSkillEffectTimer(L1SkillId.CHAINSWORD1);
				pc.setSkillEffect(L1SkillId.CHAINSWORD2, 15000);
				SC_PacketBox.send(pc, SC_PacketBox.SPOT, 2);
			}else if(pc.hasSkillEffect(L1SkillId.CHAINSWORD2)){
				pc.killSkillEffectTimer(L1SkillId.CHAINSWORD2);
				pc.setSkillEffect(L1SkillId.CHAINSWORD3, 15000);
				SC_PacketBox.send(pc, SC_PacketBox.SPOT, 3);	
			}else if(pc.hasSkillEffect(L1SkillId.CHAINSWORD3)){
				if(pc.isPassive(MJPassiveID.FOU_SLAYER_BRAVE.toInt())){
					pc.setSkillEffect(L1SkillId.CHAINSWORD4, 15000);
					SC_PacketBox.send(pc, SC_PacketBox.SPOT, 4);
				}
			}else if(pc.hasSkillEffect(L1SkillId.CHAINSWORD4)){
			}else {
				pc.setSkillEffect(L1SkillId.CHAINSWORD1, 15000);
				SC_PacketBox.send(pc, SC_PacketBox.SPOT, 1);			
			}
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_FIRE);
	}

	public static double 환영의체인소드(L1PcInstance pc, L1Character cha, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int chance = _random.nextInt(100) + 1;
		if (5 + enchant >= chance) {
			dmg = 15;
			if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, 7398);
			SC_EffectLocation.broadcast(pc, locx, locy, 7398);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WATER);
	}
	public static double 파아그리오공포(L1PcInstance pc, L1Character cha, int enchant) { 
		double dmg = 20;
		int aa = _random.nextInt(Config.환상최대 - Config.환상최소) + Config.환상최소;
		int chance = _random.nextInt(100) + 1;
		int val = enchant * Config.환상인챈트 + Config.환상인챈트트;
		
		if (isFreeze(cha)) return 0;
		
		if (chance <= val) {
			dmg += aa + (enchant * Config.환상데미지인챈트);
			
			if (cha instanceof L1PcInstance) {
				// 유저 타격 시 (18478)
				SC_SkillSound.send(pc, cha.getId(), 18516);
				SC_SkillSound.broadcast(pc, cha.getId(), 18516);
			} else {
				// 몬스터 타격 시 광역 데미지 (18482)
				SC_SkillSound.send(pc, cha.getId(), 18514);
				SC_SkillSound.broadcast(pc, cha.getId(), 18514);
				
				L1PcInstance targetPc = null;
				L1NpcInstance targetNpc = null;
				for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) {
					if (object == null || !(object instanceof L1Character)) continue;
					if (object.getId() == pc.getId() || object.getId() == cha.getId()) continue;
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						if (targetPc.getZoneType() == 1) continue;
					}
					if (cha instanceof L1MonsterInstance && !(object instanceof L1MonsterInstance)) continue;
					if (cha instanceof L1PcInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance) {
						if (!(object instanceof L1PcInstance || object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance)) continue;
					}
					if (dmg <= 0) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						SC_DoActionGFX.send(targetPc.getNetConnection(), targetPc.getId(), ActionCodes.ACTION_Damage);
						SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage);
						targetPc.receiveDamage(pc, 0);
					} else if (object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance) {
						targetNpc = (L1NpcInstance) object;
						SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage);
						targetNpc.receiveDamage(pc, (int) dmg);
					}
				}
			}
		}
		return dmg;
	}
	
	public static double 단테스일격(L1PcInstance pc, L1Character cha, int enchant) { 
		double dmg = 20;
		int aa = _random.nextInt(Config.환상최대 - Config.환상최소) + Config.환상최소;
		int chance = _random.nextInt(100) + 1;
		
		// 질문자님이 수정하신 정상적인 인챈트 변수명 유지
		int val = enchant * Config.환상인챈트 + Config.환상인챈트;
		
		if (isFreeze(cha)) return 0;
		
		if (chance <= val) {
			
			// 데미지 계산
			dmg += aa + (enchant * Config.환상데미지인챈트);
			
			// 타겟이 유저(PC)인지 몬스터(NPC)인지 확인
			if (cha instanceof L1PcInstance) {
				// 1. 유저(PC) 타격 시 이펙트 (18478) 및 결박 효과
				SC_SkillSound.send(pc, cha.getId(), 18478);
				SC_SkillSound.broadcast(pc, cha.getId(), 18478);
				
				int fettersTime = 2000;
				L1PcInstance targetPc = (L1PcInstance) cha;
				targetPc.setSkillEffect(L1SkillId.STATUS_FREEZE, fettersTime);
				SC_SkillSound.send(targetPc, targetPc.getId(), 4184);
				SC_SkillSound.broadcast(targetPc, targetPc.getId(), 4184);
				SC_Paralysis.send(targetPc, SC_Paralysis.TYPE_BIND, true);
				
			} else {
				// 2. 몬스터(NPC) 타격 시 이펙트 (18482) 및 광역 데미지
				SC_SkillSound.send(pc, cha.getId(), 18482);
				SC_SkillSound.broadcast(pc, cha.getId(), 18482);
				
				// 추가된 주변 3칸 광역(스플래시) 데미지 로직
				L1PcInstance targetPc = null;
				L1NpcInstance targetNpc = null;
				for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) {
					if (object == null || !(object instanceof L1Character)) continue;
					if (object.getId() == pc.getId() || object.getId() == cha.getId()) continue;
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						if (targetPc.getZoneType() == 1) continue; // 세이프존 무시
					}
					if (cha instanceof L1MonsterInstance && !(object instanceof L1MonsterInstance)) continue;
					if (cha instanceof L1PcInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance) {
						if (!(object instanceof L1PcInstance || object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance)) continue;
					}
					
					if (dmg <= 0) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						SC_DoActionGFX.send(targetPc.getNetConnection(), targetPc.getId(), ActionCodes.ACTION_Damage);
						SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage);
						targetPc.receiveDamage(pc, 0);
					} else if (object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance) {
						targetNpc = (L1NpcInstance) object;
						SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage);
						targetNpc.receiveDamage(pc, (int) dmg);
					}
				}
			}
		}
		return dmg;
	}
	public static double 아인하사드고독(L1PcInstance pc, L1Character cha, int enchant) { // 6553	
		// 타겟이 유저(PC)인지 몬스터(NPC)인지 확인
		if (cha instanceof L1PcInstance) {
			// ==========================================
			// 1. 타겟이 유저(PC)일 경우 (단일 타격 + 결박)
			// ==========================================
			double dmg = 20;
			int aa = _random.nextInt(Config.고독최대 - Config.고독최소) + Config.고독최소;
			int chance = _random.nextInt(100) + 1;
			int val = enchant * Config.고독인챈트 + Config.고독인챈트;
			
			if (isFreeze(cha)) return 0;
			
			if (chance <= val) {
				// 데미지 계산
				dmg += aa + (enchant * Config.고독데미지인챈트);
				
				// PC 타격 이펙트 (18520)
				SC_SkillSound.send(pc, cha.getId(), 18520);
				SC_SkillSound.broadcast(pc, cha.getId(), 18520);
				
				// PC 타격 시 2초간 결박 효과
				L1PcInstance targetPc = (L1PcInstance) cha;
				targetPc.setSkillEffect(STATUS_FREEZE, 2000);
				SC_SkillSound.send(targetPc, targetPc.getId(), 4184);
				SC_SkillSound.broadcast(targetPc, targetPc.getId(), 4184);
				SC_Paralysis.send(targetPc, SC_Paralysis.TYPE_BIND, true);
			}
			return dmg;
			
		} else {
			// ==========================================
			// 2. 타겟이 몬스터(NPC)일 경우 (광역 스플래시 타격)
			// ==========================================
			double dmg = 20;
			int aa = _random.nextInt(Config.고독최대 - Config.고독최소) + Config.고독최소;
			int chance = _random.nextInt(100) + 1;
			int val = enchant * Config.고독인챈트 + Config.고독인챈트;
			
			if (isFreeze(cha)) return 0;
			
			if (chance <= val) {
				// 데미지 계산
				dmg += aa + (enchant * Config.고독데미지인챈트);
				
				// 몬스터 타격 이펙트 (18510)
				SC_SkillSound.send(pc, cha.getId(), 18510);
				SC_SkillSound.broadcast(pc, cha.getId(), 18510);
				
				// 주변 3칸 광역 데미지 로직 
				L1PcInstance targetPc = null;
				L1NpcInstance targetNpc = null;
				for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) {
					if (object == null) continue;
					if (!(object instanceof L1Character)) continue;
					if (object.getId() == pc.getId() || object.getId() == cha.getId()) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						if (targetPc.getZoneType() == 1) continue; // 세이프존 무시
					}
					if (cha instanceof L1MonsterInstance) {
						if (!(object instanceof L1MonsterInstance)) continue;
					}
					if (cha instanceof L1PcInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance) {
						if (!(object instanceof L1PcInstance || object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance)) {
							continue;
						}
					}
					
					if (dmg <= 0) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						SC_DoActionGFX.send(targetPc.getNetConnection(), targetPc.getId(), ActionCodes.ACTION_Damage);
						SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage);
						targetPc.receiveDamage(pc, 0);
					} else if (object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance) {
						targetNpc = (L1NpcInstance) object;
						SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage);
						targetNpc.receiveDamage(pc, (int) dmg);
					}
				}
			}
			return dmg;
		}
	}
	public static double 아인하사드의섬광(L1PcInstance pc, L1Character cha, int enchant) { 
		// 타겟이 유저(PC)인지 몬스터(NPC)인지에 따라 분기 처리
		if (cha instanceof L1PcInstance) {
			// ==========================================
			// 1. 타겟이 유저(PC)일 경우 (단일 타격 + 결박)
			// ==========================================
			double dmg = 20;
			int aa = _random.nextInt(Config.아인최대 - Config.아인최소) + Config.아인최소;
			int chance = _random.nextInt(100) + 1;
			int val = enchant * Config.아인하사드인챈트트 + Config.아인하사드인챈트;
			
			if (isFreeze(cha)) return 0;
			
			if (chance <= val) {
				// 데미지 계산 (기존 PC 팩 설정 유지)
				dmg += aa + (enchant * Config.아인데미지인챈트);
				
				// PC 타격 이펙트 (18500)
				SC_SkillSound.send(pc, cha.getId(), 18500);
				SC_SkillSound.broadcast(pc, cha.getId(), 18500);
				
				// PC 타격 시 2초간 결박 효과 적용
				L1PcInstance targetPc = (L1PcInstance) cha;
				targetPc.setSkillEffect(STATUS_FREEZE, 2000);
				SC_SkillSound.send(targetPc, targetPc.getId(), 4184);
				SC_SkillSound.broadcast(targetPc, targetPc.getId(), 4184);
				SC_Paralysis.send(targetPc, SC_Paralysis.TYPE_BIND, true);
			}
			return dmg;
			
		} else {
			// ==========================================
			// 2. 타겟이 몬스터(NPC)일 경우 (광역 스플래시 타격)
			// ==========================================
			double dmg = 20;
			// 몬스터용 전용 Config 설정 (뒤에 1이 붙은 변수들) 유지
			int aa = _random.nextInt(Config.아인최대1 - Config.아인최소1) + Config.아인최소1;
			int val = enchant * Config.아인하사드인챈트트1 + Config.아인하사드인챈트1;
			int chance = _random.nextInt(100) + 1;
			
			if (isFreeze(cha)) return 0;
			
			if (val >= chance) {
				// 데미지 계산 (몬스터용 Config 유지)
				dmg += aa + (enchant * Config.아인데미지인챈트1);
				
				// 몬스터 타격 이펙트 (18496)
				SC_SkillSound.send(pc, cha.getId(), 18496);
				SC_SkillSound.broadcast(pc, cha.getId(), 18496);
				
				// 주변 3칸 광역 데미지 로직 (기존 소스 100% 유지)
				L1PcInstance targetPc = null;
				L1NpcInstance targetNpc = null;
				for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) {
					if (object == null) continue;
					if (!(object instanceof L1Character)) continue;
					if (object.getId() == pc.getId() || object.getId() == cha.getId()) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						if (targetPc.getZoneType() == 1) continue; // 세이프존 무시
					}
					if (cha instanceof L1MonsterInstance) {
						if (!(object instanceof L1MonsterInstance)) continue;
					}
					if (cha instanceof L1PcInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance) {
						if (!(object instanceof L1PcInstance || object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance)) {
							continue;
						}
					}
					
					if (dmg <= 0) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						SC_DoActionGFX.send(targetPc.getNetConnection(), targetPc.getId(), ActionCodes.ACTION_Damage);
						SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage);
						targetPc.receiveDamage(pc, 0);
					} else if (object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance) {
						targetNpc = (L1NpcInstance) object;
						SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage);
						targetNpc.receiveDamage(pc, (int) dmg);
					}
				}
			}
			return dmg;
		}
	}
	public static double 에바의서약(L1PcInstance pc, L1Character cha, int enchant) { 
		double dmg = 20; 
		int aa = _random.nextInt(Config.에바최대 - Config.에바최소) + Config.에바최소;
		int chance = _random.nextInt(100) + 1;
		int val = enchant * Config.에바인챈트1 + Config.에바인챈트;
		
		if (isFreeze(cha)) return 0;
		
		if (chance <= val) {
			dmg += aa + (enchant * Config.에바데미지인챈트);
			
			if (cha instanceof L1PcInstance) {
				// 유저 타격 시 (18502)
				SC_SkillSound.send(pc, cha.getId(), 18502);
				SC_SkillSound.broadcast(pc, cha.getId(), 18502);
				//유저 결박 효과가 필요하면 이 주석을 푸세요
				L1PcInstance targetPc = (L1PcInstance) cha;
				targetPc.setSkillEffect(L1SkillId.STATUS_FREEZE, 2000);
				SC_SkillSound.send(targetPc, targetPc.getId(), 4184);
				SC_SkillSound.broadcast(targetPc, targetPc.getId(), 4184);
				SC_Paralysis.send(targetPc, SC_Paralysis.TYPE_BIND, true);
				
			} else {
				// 몬스터 타격 시 광역 데미지 (18506)
				SC_SkillSound.send(pc, cha.getId(), 18506);
				SC_SkillSound.broadcast(pc, cha.getId(), 18506);
				
				L1PcInstance targetPc = null;
				L1NpcInstance targetNpc = null;
				for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) {
					if (object == null || !(object instanceof L1Character)) continue;
					if (object.getId() == pc.getId() || object.getId() == cha.getId()) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						if (targetPc.getZoneType() == 1) continue;
					}
					if (cha instanceof L1MonsterInstance && !(object instanceof L1MonsterInstance)) continue;
					if (cha instanceof L1PcInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance) {
						if (!(object instanceof L1PcInstance || object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance)) continue;
					}
					
					if (dmg <= 0) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						SC_DoActionGFX.send(targetPc.getNetConnection(), targetPc.getId(), ActionCodes.ACTION_Damage);
						SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage);
						targetPc.receiveDamage(pc, 0);
					} else if (object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance) {
						targetNpc = (L1NpcInstance) object;
						SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage);
						targetNpc.receiveDamage(pc, (int) dmg);
					}
				}
			}
		}
		return dmg;
	}
	public static double 그랑카인의심판(L1PcInstance pc, L1Character cha, int enchant) { 
		// 타겟이 유저(PC)인지 몬스터(NPC)인지에 따라 분기 처리
		if (cha instanceof L1PcInstance) {
			// ==========================================
			// 1. 타겟이 유저(PC)일 경우 (단일 타격 + 결박)
			// ==========================================
			double dmg = 20;
			int aa = _random.nextInt(Config.그랑최대 - Config.그랑최소) + Config.그랑최소;
			int chance = _random.nextInt(100) + 1;
			int val = enchant * Config.그랑카인인챈트트 + Config.그랑카인인챈트;
			
			if (isFreeze(cha)) return 0;
			
			if (chance <= val) {
				// 데미지 계산 (기존 팩 설정 유지 - 에바데미지인챈트로 되어 있던 오타도 원본대로 유지)
				dmg += aa + (enchant * Config.에바데미지인챈트);
				
				// PC 타격 이펙트 (18474)
				SC_SkillSound.send(pc, cha.getId(), 18474);
				SC_SkillSound.broadcast(pc, cha.getId(), 18474);
				
				// PC 타격 시 2초간 결박 효과 (원치 않으시면 아래 4줄 삭제)
				L1PcInstance targetPc = (L1PcInstance) cha;
				targetPc.setSkillEffect(STATUS_FREEZE, 2000);
				SC_SkillSound.send(targetPc, cha.getId(), 4184);
				SC_SkillSound.broadcast(targetPc, cha.getId(), 4184);
				SC_Paralysis.send(targetPc, SC_Paralysis.TYPE_BIND, true);
			}
			return dmg;
			
		} else {
			// ==========================================
			// 2. 타겟이 몬스터(NPC)일 경우 (광역 스플래시 타격)
			// ==========================================
			double dmg = 20;
			int aa = _random.nextInt(Config.그랑최대1 - Config.그랑최소1) + Config.그랑최소1;
			int val = enchant * Config.그랑카인인챈트트1 + Config.그랑카인인챈트1;
			int chance = _random.nextInt(100) + 1;
			
			if (isFreeze(cha)) return 0;
			
			if (val >= chance) {
				// 데미지 계산
				dmg += aa + (enchant * Config.그랑데미지인챈트1);
				
				// 몬스터 타격 이펙트 (18470)
				SC_SkillSound.send(pc, cha.getId(), 18470);
				SC_SkillSound.broadcast(pc, cha.getId(), 18470);
				
				// 주변 3칸 광역 데미지 로직 (기존 소스 100% 유지)
				L1PcInstance targetPc = null;
				L1NpcInstance targetNpc = null;
				for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) {
					if (object == null) continue;
					if (!(object instanceof L1Character)) continue;
					if (object.getId() == pc.getId() || object.getId() == cha.getId()) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						if (targetPc.getZoneType() == 1) continue; // 세이프존 무시
					}
					if (cha instanceof L1MonsterInstance) {
						if (!(object instanceof L1MonsterInstance)) continue;
					}
					if (cha instanceof L1PcInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance) {
						if (!(object instanceof L1PcInstance || object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance)) {
							continue;
						}
					}
					
					if (dmg <= 0) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						SC_DoActionGFX.send(targetPc.getNetConnection(), targetPc.getId(), ActionCodes.ACTION_Damage);
						SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage);
						targetPc.receiveDamage(pc, 0);
					} else if (object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance) {
						targetNpc = (L1NpcInstance) object;
						SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage);
						targetNpc.receiveDamage(pc, (int) dmg);
					}
				}
			}
			return dmg;
		}
	}
	public static double 아우라키아의초월(L1PcInstance pc, L1Character cha, int enchant) { 
		double dmg = 0;
		int chance = _random.nextInt(100) + 1;
		
		if (isFreeze(cha)) return 0;
		
		// 가져온 소스의 자체 확률 및 데미지 계산 공식 적용
		if (7 + enchant >= chance) {
			int Str = pc.getAbility().getTotalStr();
			int div = (int) ((Str / 10) * 10);
			int min = 160 + div;
			int max = 420 + div;
			dmg = _random.nextInt(max - min) + min;
			if (dmg <= 0) dmg = 0;
			
			// 타겟이 유저(PC)인지 몬스터(NPC)인지 확인
			if (cha instanceof L1PcInstance) {
				// 1. 유저(PC) 타격 시 이펙트 (13469) 및 2초 결박 효과
				SC_SkillSound.send(pc, cha.getId(), 13469);
				SC_SkillSound.broadcast(pc, cha.getId(), 13469);
				
				int fettersTime = 2000;
				L1PcInstance targetPc = (L1PcInstance) cha;
				targetPc.setSkillEffect(L1SkillId.STATUS_FREEZE, fettersTime);
				SC_SkillSound.send(targetPc, targetPc.getId(), 4184);
				SC_SkillSound.broadcast(targetPc, targetPc.getId(), 4184);
				SC_Paralysis.send(targetPc, SC_Paralysis.TYPE_BIND, true);
				
			} else {
				// 2. 몬스터(NPC) 타격 시 이펙트 (13471) 및 광역 데미지
				SC_SkillSound.send(pc, cha.getId(), 13471);
				SC_SkillSound.broadcast(pc, cha.getId(), 13471);
				
				// 주변 3칸 광역(스플래시) 데미지 로직
				L1PcInstance targetPc = null;
				L1NpcInstance targetNpc = null;
				for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(cha, 3)) {
					if (object == null || !(object instanceof L1Character)) continue;
					if (object.getId() == pc.getId() || object.getId() == cha.getId()) continue;
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						if (targetPc.getZoneType() == 1) continue; // 세이프존 무시
					}
					if (cha instanceof L1MonsterInstance && !(object instanceof L1MonsterInstance)) continue;
					if (cha instanceof L1PcInstance || cha instanceof L1SummonInstance || cha instanceof L1PetInstance) {
						if (!(object instanceof L1PcInstance || object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance)) continue;
					}
					if (dmg <= 0) continue;
					
					if (object instanceof L1PcInstance) {
						targetPc = (L1PcInstance) object;
						SC_DoActionGFX.send(targetPc.getNetConnection(), targetPc.getId(), ActionCodes.ACTION_Damage);
						SC_DoActionGFX.broadcast(targetPc, ActionCodes.ACTION_Damage);
						targetPc.receiveDamage(pc, 0);
					} else if (object instanceof L1SummonInstance || object instanceof L1PetInstance || object instanceof L1MonsterInstance) {
						targetNpc = (L1NpcInstance) object;
						SC_DoActionGFX.broadcast(targetNpc, ActionCodes.ACTION_Damage);
						targetNpc.receiveDamage(pc, (int) dmg);
					}
				}
			}
		}
		// 가져온 소스의 속성 저항 계산 로직 유지
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_FIRE);
	}
	public static double 군주의검(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (2 + enchant >= chance) {
			dmg = _random.nextInt(intel / 2) + (intel);
			if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double 파괴의이도류크로우(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (1 + enchant >= chance) {
			dmg = _random.nextInt(intel / 2) + (intel * 2);
			if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_EARTH);
	}

	public static double 광풍의도끼(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (2 + enchant >= chance) {
			dmg = _random.nextInt(intel / 2) + (intel * 2);
			if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double 혹한의창(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (2 + enchant >= chance) {
			dmg = _random.nextInt(intel / 2) + (intel);
			if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double 살천의활(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int dex = pc.getAbility().getTotalDex();
		int chance = _random.nextInt(100) + 1;
		if (1 + enchant >= chance) {
			dmg = _random.nextInt(dex) + (dex);
			if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double getMoonBowDamage(L1PcInstance pc, L1Character cha, int enchant) {
		double dmg = 0;
		int dex = pc.getAbility().getTotalDex();
		int chance = _random.nextInt(100) + 1;
		if (1 + enchant >= chance) {
			dmg = _random.nextInt(dex) + (dex);
			if (dmg <= 0) {
				dmg = 0;
			}
			SC_UseAttackSkill.sendAndBroadcast(
					pc,
					cha.getId(),
					6288,
					cha.getX(),
					cha.getY(),
					ActionCodes.ACTION_Attack,
					false);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double 뇌신검(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (1 + enchant >= chance) {
			dmg = _random.nextInt(intel) + (intel * 2);
			if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double 공명의키링크(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (2 + enchant >= chance) {
			dmg = _random.nextInt(intel) + 20 + (intel * 2);
			if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double 냉한의키링크(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (6 + enchant >= chance) {// 3
			dmg = _random.nextInt(intel / 2) + (intel * 1.2);
			if (cha.getCurrentMp() >= 5) {
				cha.setCurrentMp(cha.getCurrentMp() - 5);
				if (dmg <= 0) {
					dmg = 0;
				}
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WATER);
	}

	public static double 히페리온의절망(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int sp = pc.getSp();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (10 + enchant >= chance) {// 3
			dmg = _random.nextInt(enchant * 2 + 5) + ((sp + intel) * 4); // 공식변경
			if (cha.getCurrentMp() >= 5) {
				cha.setCurrentMp(cha.getCurrentMp() - 10);
				if (5 >= chance) {// 이펙트 랜타 찬스
					dmg += dmg * 0.7;// 대미지
				} else if (dmg <= 0) {
					dmg = 0;
				}
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WATER);
	}
	
	public static double 진싸울아비대검(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int Str = pc.getAbility().getTotalStr();
		int chance = _random.nextInt(100) + 1;
		if (7 + enchant >= chance) {
			int div = (int)((Str / 10) * 1.5);
			int min = 20 + div;
			int max = 50 + div;	
			dmg = _random.nextInt(max - min) + min;
			if(dmg <= 0) dmg = 0;
			// dex 20일때, 최소 23~최대 53
			// 70일때, 최소 37~최대 67
			
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double 악몽의장궁(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int dex = pc.getAbility().getTotalDex();
		int chance = _random.nextInt(100) + 1;
		if (4 + enchant >= chance) {
			int div = (int)((dex / 10) * 1.2);
			int min = 20 + div;
			int max = 50 + div;	
			dmg = _random.nextInt(max - min) + min;
			if(dmg <= 0) dmg = 0;
			// dex 20일때, 최소 23~최대 53
			// 70일때, 최소 37~최대 67

			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double 섬멸자의체인소드(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int Str = pc.getAbility().getTotalStr();
		int chance = _random.nextInt(100) + 1;
		if (7 + enchant >= chance) {
			int div = (int)((Str / 10) * 1.2);
			int min = 15 + div;
			int max = 40 + div;	
			dmg = _random.nextInt(max - min) + min;
			if(dmg <= 0) dmg = 0;
			// dex 20일때, 최소 23~최대 53
			// 70일때, 최소 37~최대 67
			
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double 태풍의도끼(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int Str = pc.getAbility().getTotalStr();
		int chance = _random.nextInt(100) + 1;
		if (3 + enchant >= chance) {
			int div = (int)((Str / 10) * 1.2);
			int min = 13 + div;
			int max = 47 + div;	
			dmg = _random.nextInt(max - min) + min;
			if(dmg <= 0) dmg = 0;
			// dex 20일때, 최소 23~최대 53
			// 70일때, 최소 37~최대 67
			
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}
	
	public static double 드래곤슬레이어(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int Str = pc.getAbility().getTotalStr();
		int chance = _random.nextInt(100) + 1;
		if (5 + enchant >= chance) {// 찬스
			dmg = _random.nextInt(Str) + (Str);
			if (3 >= chance) {// 이펙트 랜타 찬스
				dmg += dmg * 0.1;// 대미지
			} else if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double Redskill(L1PcInstance pc, L1Character cha, int effect, int enchant) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (3 + enchant >= chance) {
			dmg = _random.nextInt(intel / 2) + (intel * 3);
			if (cha.getCurrentMp() >= 5) {
				cha.setCurrentMp(cha.getCurrentMp() - 5);
				if (dmg <= 0) {
					dmg = 0;
				}
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WATER);
	}

	public static double 커츠의검(L1PcInstance pc, L1Character cha, int enchant, int effect) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int sp = pc.getSp();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (5 + enchant >= chance) {
			int div = (int)((intel+(sp) / 10) * 1.2);
			int min = 10 + div;
			int max = 40 + div;
			dmg = _random.nextInt(max - min) + min;
			if(dmg <= 0) dmg = 0;
			// dex 20일때, 최소 23~최대 53
			// 70일때, 최소 37~최대 67
			
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static double 데스나이트의불검(L1PcInstance pc, L1Character cha, int enchant, int effect) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int sp = pc.getSp();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (7 + enchant >= chance) {
			int div = (int)((intel+(sp) / 10) * 1.2);
			int min = 10 + div;
			int max = 40 + div;
			dmg = _random.nextInt(max - min) + min;
			if(dmg <= 0) dmg = 0;
			// dex 20일때, 최소 23~최대 53
			// 70일때, 최소 37~최대 67
			
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_FIRE);
	}

	public static double 바포메트의지팡이(L1PcInstance pc, L1Character cha, int enchant, int effect) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int sp = pc.getSp();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (15 + enchant >= chance) {
			dmg = _random.nextInt(enchant * 2 + 3) + ((sp + intel) * 3); // 리뉴얼로상향
			if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_EARTH);
	}

	public static double 제로스의지팡이(L1PcInstance pc, L1Character cha, int enchant, int effect) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int sp = pc.getSp();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (7 + enchant >= chance) {
			int div = (int)((intel+(sp) / 10) * 2.8);
			int min = 15 + div;
			int max = 45 + div;
			dmg = _random.nextInt(sp + intel) + ((sp + intel) * 2.5) + sp * 2;
			if(dmg <= 0) dmg = 0;
			// dex 20일때, 최소 23~최대 53
			// 70일때, 최소 37~최대 67
			
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_FIRE);
	}
	public static double 제로스의지팡이1(L1PcInstance pc, L1Character cha, int enchant, int effect) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int sp = pc.getSp();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (10 + enchant >= chance) {
			int div = (int)((intel+(sp) / 10) * 2.8);
			int min = 15 + div;
			int max = 45 + div;
			dmg = _random.nextInt(sp + intel) + ((sp + intel) * 2) + sp * 2;
			if(dmg <= 0) dmg = 0;
			// dex 20일때, 최소 23~최대 53
			// 70일때, 최소 37~최대 67
			
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_FIRE);
	}

	public static double 수정결정체지팡이(L1PcInstance pc, L1Character cha, int enchant, int effect) {
		double dmg = 0;
		int locx = cha.getX();
		int locy = cha.getY();
		int sp = pc.getSp();
		int intel = pc.getAbility().getTotalInt();
		int chance = _random.nextInt(100) + 1;
		if (20 + enchant >= chance && 5 <= chance) {
			dmg = _random.nextInt(sp + intel) + ((sp + intel) * 4.5);
			if (5 >= chance) {
				dmg += 50;
			} else if (dmg <= 0) {
				dmg = 0;
			}
			SC_EffectLocation.send(pc, locx, locy, effect);
			SC_EffectLocation.broadcast(pc, locx, locy, effect);
		}
		return calcDamageReduction(cha, dmg, L1Skills.ATTR_WIND);
	}

	public static void 천사의지팡이(L1PcInstance pc, L1Character cha, int enchant) {
		int chance = _random.nextInt(100) + 1;
		int undeadType = ((L1MonsterInstance) cha).getNpcTemplate().get_undead();
		if (undeadType == 1 || undeadType == 3) {
			if(pc.getMapId() != 15403 && pc.getMapId() != 111 && pc.getMapId() != 12862){
			if (enchant+10 >= chance) {
				L1SkillUse.cast(pc, TURN_UNDEAD, cha.getId(), cha.getX(), cha.getY(), null, 0,
						L1SkillUse.TYPE_GMBUFF);
			}
			}
		}
	}

	public static double calcDamageReduction(L1Character cha, double dmg, int attr) {
		if (isFreeze(cha)) {
			return 0;
		}

		int ran1 = 0; // 랜덤 수치 적용
		int mrset = 0; // 엠알에서 랜덤 수치를 뺀값
		int mrs = cha.getResistance().getEffectedMrBySkill();
		ran1 = _random.nextInt(5) + 1;
		mrset = mrs - ran1;
		double calMr = 0.00D;
		calMr = (220 - mrset) / 250.00D;
		dmg *= calMr;

		if (dmg < 0) {
			dmg = 0;
		}

		int resist = 0;
		if (attr == L1Skills.ATTR_EARTH) {
			resist = cha.getResistance().getEarth();
		} else if (attr == L1Skills.ATTR_FIRE) {
			resist = cha.getResistance().getFire();
		} else if (attr == L1Skills.ATTR_WATER) {
			resist = cha.getResistance().getWater();
		} else if (attr == L1Skills.ATTR_WIND) {
			resist = cha.getResistance().getWind();
		}
        resist = Math.min(100, resist);
		int resistFloor = (int) (0.32 * Math.abs(resist));
		if (resist >= 0) {
			resistFloor *= 1;
		} else {
			resistFloor *= -1;
		}
		double attrDeffence = resistFloor / 32.0;
		dmg = (1.0 - attrDeffence) * dmg;

		return dmg;
	}

	private static boolean isFreeze(L1Character cha) {

//		if (cha.hasSkillEffect(STATUS_FREEZE)) {
//			return true;
//		}
		if (cha.hasSkillEffect(ABSOLUTE_BARRIER)) {
			return true;
		}
		if (cha.hasSkillEffect(ICE_LANCE)) {
			return true;
		}
		if (cha.hasSkillEffect(EARTH_BIND)) {
			return true;
		}

		if (cha.hasSkillEffect(COUNTER_MAGIC)) {
			cha.removeSkillEffect(COUNTER_MAGIC);
			int castgfx = SkillsTable.getInstance().getTemplate(COUNTER_MAGIC).getCastGfx();
			SC_SkillSound.broadcast(cha, cha.getId(), 10702);
			
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				SC_SkillSound.send(pc, pc.getId(), 10702);
				
			}
			return true;
		}
		return false;
	}
}
