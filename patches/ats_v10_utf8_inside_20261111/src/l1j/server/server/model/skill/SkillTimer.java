
package l1j.server.server.model.skill;

import l1j.server.Templates.L1Skills;
import l1j.server.configure.Config;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.item.Cooking;
import l1j.server.server.model.item.PolyBuffSystem;
import l1j.server.server.serverpackets.S_Coma;
import l1j.server.server.serverpackets.S_CurseBlind;
import l1j.server.server.serverpackets.S_Dexup;
import l1j.server.server.serverpackets.S_HPUpdate;
import l1j.server.server.serverpackets.S_Liquor;
import l1j.server.server.serverpackets.S_MPUpdate;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_Paralysis;
import l1j.server.server.serverpackets.S_Poison;
import l1j.server.server.serverpackets.S_SPMR;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillBrave;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillIconAura;
import l1j.server.server.serverpackets.S_SkillIconBlessOfEva;
import l1j.server.server.serverpackets.S_SkillIconShield;
import l1j.server.server.serverpackets.S_SkillIconWindShackle;
import l1j.server.server.serverpackets.S_SkillIconWisdomPotion;
import l1j.server.server.serverpackets.S_Strup;

public class SkillTimer implements Runnable {
	public SkillTimer(L1Character cha, int skillId, int timeMillis) {
		_cha = cha;
		_skillId = skillId;
		_timeMillis = timeMillis;

		_remainingTime = _timeMillis / 1000;
		_stop = false;
	}

	@Override
	public void run() {
		if (_stop) {
			return;
		}
		_remainingTime--;
		if (_remainingTime <= 0) {
			_cha.removeSkillEffect(_skillId);
			return;
		}
		GeneralThreadPool.getInstance().schedule(this, 1000);
	}

	public void begin() {
		GeneralThreadPool.getInstance().schedule(this, 1000);
	}

	public void end() {
		_stop = true;
		L1SkillStop.stopSkill(_cha, _skillId);
	}

	public void kill() {
		_stop = true;
	}

	public int getRemainingTime() {
		return _remainingTime;
	}

	private final L1Character _cha;
	private final int _timeMillis;
	private final int _skillId;
	private int _remainingTime;
	private boolean _stop;
}

class L1SkillStop {
	public static void stopSkill(L1Character cha, int skillId) {

		Cooking cook = Cooking.getBySkillId(skillId);
		if (cook != null) {
			cook.effectToMaster(cha, cook, -1, 0);
			return;
		}
		switch (skillId) { 
		case SkillId.SKILL_DELAY: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.sendPackets(new S_PacketBox(S_PacketBox.MINIGAME_END));
		}
		break;
		case SkillId.라이트: {
			if (!cha.isInvisble()) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.getLight().turnOnOffLight();
			}
		}
		break;
		case SkillId.언케니_닷지: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addDg(-Config.DODGEHIT);
		}
		break;
		case SkillId.지룡_마안: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getResistance().addregist_spirit(-15);
		}
		break;
		case SkillId.POLY_BUFF:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				if (pc.getPolyBuffId() != 0) {
					PolyBuffSystem pbs = PolyBuffSystem.get(pc.getPolyBuffId());
					if (pbs != null) {
						pbs.use(pc, pc.getPolyBuffId(), 0, -1);
					}
					pc.setPolyBuffId(0);
				}
			}
			break;
		case SkillId.상태_체력_증강:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.addMaxHp(-50);
				pc.addHpr(-4);
			}
			break;
		case SkillId.상태_마나_증강:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.addMaxMp(-40);
				pc.addMpr(-4);
			}
			break;
		case SkillId.상태_전투_강화:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.addMeleeHit(-3);
				pc.addMeleeDmg(-3);
				pc.addRangedHit(-3);
				pc.addRangedDmg(-3);
				pc.addSp(-3);
			}
			break;
		case SkillId.흑사_코인: 
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.getAC().addAc(2);
				pc.addMeleeHit(-3);
				pc.addRangedHit(-3);
				pc.addMaxHp(-20);
				pc.addMaxMp(-13);
				pc.getResistance().addregist_spirit(10);
			}
			break;
		case SkillId.코마_축복_코인_8:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(new S_Coma(41, 0));
				pc.getAbility().addAddedCon(-1);
				pc.getAbility().addAddedDex(-5);
				pc.getAbility().addAddedStr(-5);
				pc.addMeleeHit(-5);
				pc.addRangedHit(-5);
				pc.getAC().addAc(8);
				pc.addSp(-1);
			}
			break;
		case SkillId.글루잉_오라: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeHit(-5);
			pc.addRangedHit(-5);
			pc.getResistance().addMr(-20);
			pc.sendPackets(new S_SkillIconAura(113, 0));
		}
		break;
		case SkillId.샤이닝_오라: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAC().addAc(8);
			pc.sendPackets(new S_SkillIconAura(114, 0));
		}
		break;
		case SkillId.브레이브_오라: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeDmg(-5);
			pc.addRangedDmg(-5);
			pc.sendPackets(new S_SkillIconAura(116, 0));
		}
		break;
		case SkillId.실드: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAC().addAc(2);
			pc.sendPackets(new S_SkillIconShield(5, 0));
		}
		break;
		case SkillId.쉐도우_아머: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAC().addAc(3);
			pc.sendPackets(new S_SkillIconShield(3, 0));
		}
		break;
		case SkillId.블라인드_하이딩: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.delInvis();
		}
		break;
		case SkillId.드레스_덱스터리티: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addAddedDex((byte) -3);
			pc.sendPackets(new S_Dexup(pc, 2, 0));
		}
		break;
		case SkillId.드레스_마이티: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addAddedStr((byte) -3);
			pc.sendPackets(new S_Strup(pc, 2, 0));
		}
		break;  
		case SkillId.쉐도우_팽: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeDmg(-5); 
		}
		break;
		case SkillId.홀리_웨폰:{
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeDmg(-1);
			pc.addMeleeHit(-1); 
		}
		break;
		case SkillId.인챈트_웨폰: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeDmg(-2); 
		}
		break;
		case SkillId.안타라스_레이드_버프_크레이: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeDmg(-1);
			pc.addMeleeHit(-5);
			pc.addRangedDmg(-1);
			pc.addRangedHit(-5);
			pc.addMaxHp(-100);
			pc.addMaxMp(-50);
			pc.addHpr(-3);
			pc.addMpr(-3);
			pc.getResistance().addEarth(-30);	
		}
		break;
		case SkillId.블레스_웨폰: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeDmg(-2);
			pc.addMeleeHit(-2); 
		}
		break;
		case SkillId.블레스드_아머: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAC().addAc(3);
		}
		break;
		case SkillId.블레스_오브_어스: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAC().addAc(7);
			pc.sendPackets(new S_SkillIconShield(7, 0));
		}
		break;
		case SkillId.레지스트_매직: {	
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.getResistance().addMr(-10);
			}
		}
		break;		
		case SkillId.클리어_마인드: {	
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.getAbility().addAddedWis((byte) -3);
				pc.resetBaseMr();
			}
		}
		break;
		case SkillId.레지스트_엘리멘트:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.getResistance().addWind(-10);
				pc.getResistance().addWater(-10);
				pc.getResistance().addFire(-10);
				pc.getResistance().addEarth(-10);
				pc.sendPackets(new S_OwnCharAttrDef(pc));
			}
			break;
		case SkillId.프로텍션_프롬_엘리멘탈: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				int attr = pc.getElfAttr();
				if (attr == 1) {
					cha.getResistance().addEarth(-50);
				} else if (attr == 2) {
					cha.getResistance().addFire(-50);
				} else if (attr == 4) {
					cha.getResistance().addWater(-50);
				} else if (attr == 8) {
					cha.getResistance().addWind(-50);
				}
				pc.sendPackets(new S_OwnCharAttrDef(pc));
			}
		}
		break;
		case SkillId.엘리멘탈_폴다운: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				int attr = pc.getAddAttrKind();
				int i = 50;
				switch (attr) {
				case 1:
					pc.getResistance().addEarth(i);
					break;
				case 2:
					pc.getResistance().addFire(i);
					break;
				case 4:
					pc.getResistance().addWater(i);
					break;
				case 8:
					pc.getResistance().addWind(i);
					break;
				default:
					break;
				}
				pc.setAddAttrKind(0);
			} else if (cha instanceof L1NpcInstance) {
				L1NpcInstance npc = (L1NpcInstance) cha;
				int attr = npc.getAddAttrKind();
				int i = 50;
				switch (attr) {
				case 1:
					npc.getResistance().addEarth(i);
					break;
				case 2:
					npc.getResistance().addFire(i);
					break;
				case 4:
					npc.getResistance().addWater(i);
					break;
				case 8:
					npc.getResistance().addWind(i);
					break;
				default:
					break;
				}
				npc.setAddAttrKind(0);
			}
		}
		break;
		case SkillId.네이쳐스_터치: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addHpr(-15);
		}
		break;
		case SkillId.메디테이션: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMpr(-5);
		}
		break;
		case SkillId.상태_파란물약: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMpr(-2);
		}
		break;
		case SkillId.아이언_스킨: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAC().addAc(10);
			pc.sendPackets(new S_SkillIconShield(10, 0));

		}
		break;
		case SkillId.어스_스킨: {	
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAC().addAc(4);
			pc.sendPackets(new S_SkillIconShield(6, 0));

		}
		break;
		case SkillId.인챈트_마이티: {
			L1PcInstance pc = (L1PcInstance) cha;
			cha.getAbility().addAddedStr((byte) -5);
			pc.sendPackets(new S_Strup(pc, 5, 0));
		}
		break;
		case SkillId.인챈트_덱스터리티: {	
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAbility().addAddedDex((byte) -5);
			pc.sendPackets(new S_Dexup(pc, 5, 0));
		}
		break;
		case SkillId.파이어_웨폰: {   //근거리 데미지-4
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeDmg(-4);
			pc.sendPackets(new S_SkillIconAura(147, 0));
		//	pc.sendPackets(723);
		//	System.out.println("22222");
		}
		break;
		case SkillId.블레스_오브_파이어: {  // 근거리데미지 -4
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeDmg(-4);
			pc.sendPackets(new S_SkillIconAura(154, 0));
		}
		break;
		case SkillId.버닝_웨폰: {  // 근거리 데미지 -6 근거리 명중-3
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeDmg(-6);
			pc.addMeleeHit(-3);
			pc.sendPackets(new S_SkillIconAura(162, 0));
		}
		break; 
		case SkillId.윈드_샷: {  // 원거리명중 -6
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addRangedHit(-6);
			pc.sendPackets(new S_SkillIconAura(148, 0));
			//pc.sendPackets(724);
		}
		break;
		case SkillId.아이_오브_스톰: {  // 원거리 데미지-3 원거리명중 -2
			L1PcInstance pc = (L1PcInstance) cha;
	          pc.addRangedDmg(-3);
	          pc.addRangedHit(-2);
			pc.sendPackets(new S_SkillIconAura(155, 0));
		}
		break;
		case SkillId.스톰_샷: { // 원거리 데미지 -6  원거리 명중-3
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addRangedDmg(-6);
			pc.addRangedHit(-3);
			pc.sendPackets(new S_SkillIconAura(165, 0));
		}
		break;
		case SkillId.버서커스: {  // ac+10 근거리데미지 -2 근거리명중 -8 
			L1PcInstance pc = (L1PcInstance) cha;
			pc.getAC().addAc(-10);
            pc.addMeleeDmg(-2);
            pc.addMeleeHit(-8);
		}
		break;
		
		case SkillId.바운스_어택: {
			L1PcInstance pc = (L1PcInstance) cha;
			pc.addMeleeHit(-6);
		}
		break;	
		case SkillId.셰이프_체인지:
			L1PolyMorph.undoPoly(cha); 
			break;
		case SkillId.어드밴스_스피릿: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.addMaxHp(-pc.getAdvenHp());
				pc.addMaxMp(-pc.getAdvenMp());
				pc.setAdvenHp(0);
				pc.setAdvenMp(0);
			}
		}
		break;
		case SkillId.헤이스트:
		case SkillId.그레이터_헤이스트: {
			cha.setMoveSpeed(0);
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				S_SkillHaste ss = S_SkillHaste.get(pc.getId(), 0, 0);
				pc.sendPackets(ss);
				pc.broadcastPacket(ss);
			}
		}
		break;
		case SkillId.홀리_워크:
		case SkillId.무빙_악셀레이션:
		case SkillId.윈드_워크: {
			cha.setBraveSpeed(0);
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				S_SkillBrave ss = S_SkillBrave.get(pc.getId(), 0, 0);
				pc.sendPackets(ss);
				pc.broadcastPacket(ss);
			}
		}
		break;
		case SkillId.커스_블라인드:
		case SkillId.다크니스: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(new S_CurseBlind(0));
			}
		}
		break; 
		case SkillId.위크니스: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.addMeleeDmg(5);
				pc.addMeleeHit(1);
			}
		}
		break;
		case SkillId.디지즈: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.addMeleeHit(6);
				pc.getAC().addAc(-12);
			}
		}
		break;
		case SkillId.아이스_랜스: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				S_Poison poison1 = new S_Poison(pc.getId(), 0);
				pc.sendPackets(poison1);
				pc.broadcastPacket(poison1);
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_FREEZE, false));
			} else if (cha instanceof MonsterInstance || cha instanceof SummonInstance || cha instanceof PetInstance) {
				L1NpcInstance npc = (L1NpcInstance) cha;
				npc.broadcastPacket(new S_Poison(npc.getId(), 0));
				npc.setParalyzed(false);
			}
		}
		break;
		case SkillId.어스_바인드: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				S_Poison poison2 = new S_Poison(pc.getId(), 0);
				pc.sendPackets(poison2);
				pc.broadcastPacket(poison2);
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_FREEZE, false));
			} else if (cha instanceof MonsterInstance || cha instanceof SummonInstance || cha instanceof PetInstance) {
				L1NpcInstance npc = (L1NpcInstance) cha;
				npc.broadcastPacket(new S_Poison(npc.getId(), 0));
				npc.setParalyzed(false);
			}
		}
		break;
		case SkillId.쇼크_스턴: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_STUN, false));
			} else if (cha instanceof MonsterInstance 
					|| cha instanceof SummonInstance
					|| cha instanceof PetInstance) {
				L1NpcInstance npc = (L1NpcInstance) cha;
				npc.setParalyzed(false);
			}
		}
		break;
		case SkillId.포그_오브_슬리핑: {
			cha.setSleeped(false);
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_SLEEP, false));
			}
		}
		break;
		case SkillId.윈드_셰클: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(new S_SkillIconWindShackle(pc.getId(), 0));
			}
		}
		break;
		case SkillId.슬로우:
		case SkillId.인탱글:
		case SkillId.매스_슬로우: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				S_SkillHaste ss = S_SkillHaste.get(pc.getId(), 0, 0);
				pc.sendPackets(ss);
				pc.broadcastPacket(ss);
			}
			cha.setMoveSpeed(0);
		}
		break;
		case SkillId.상태_동결: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				S_Poison poison = new S_Poison(pc.getId(), 0);
				pc.sendPackets(poison);
				pc.broadcastPacket(poison);
				pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_BIND, false));
			}
		}
		break;
		case SkillId.상태_2단_가속: 
		case SkillId.상태_요정_2단_가속:{
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				S_SkillBrave sss = S_SkillBrave.get(pc.getId(), 0, 0);
				pc.sendPackets(sss);
				pc.broadcastPacket(sss, true);
			}
			cha.setBraveSpeed(0);
		}
		break;
		case SkillId.상태_1단_가속: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				S_SkillHaste ss = S_SkillHaste.get(pc.getId(), 0, 0);
				pc.sendPackets(ss);
				pc.broadcastPacket(ss);
			}
			cha.setMoveSpeed(0);
		}
		break;
		case SkillId.상태_드래곤_진주_3단계:{
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(new S_PacketBox(S_PacketBox.DRAGONPERL, 0, 0));
				S_Liquor sss = new S_Liquor(pc.getId(), 0);
				pc.sendPackets(sss);
				pc.broadcastPacket(sss);
			}
		}
		break;
		case SkillId.상태_호흡: { //--이거 추가함 혹시몰라서
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(new S_SkillIconBlessOfEva(pc.getId(), 0));
			}
		}
			break;
		case SkillId.상태_지혜: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(new S_SkillIconWisdomPotion(0));
			}
		}
		break;
		case SkillId.상태_채팅_금지: {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.sendPackets(new S_ServerMessage(288)); // 채팅을 할 수 있게 되었습니다.
			}
		}
		break;
		case SkillId.SPR_TIME:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.settingdmg();
			}
			break;
		case SkillId.운세_버프_A:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.addMeleeDmg(-2);
				pc.addMeleeHit(-2);
				pc.addRangedHit(-2);  // 원거리 명중 +2
				pc.addRangedDmg(-2);  // 원거리 대미지+2 
				pc.addDamageReduction(-3); //대미지 감소 -3
				pc.addSp(-2);
				pc.sendPackets(new S_SPMR(pc));
				pc.addHpr(-3);
				pc.addMaxHp(-50);
				pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
				if (pc.isInParty()) { 
					pc.getParty().updateMiniHP(pc);
				}
				pc.addMpr(-3);
				pc.addMaxMp(-30);
				pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()));
			}
			break;
		case SkillId.운세_버프_B:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.addMeleeHit(-2);   //근거리 명중-2
				pc.addRangedHit(-2);  // 원거리 명중 -2
				pc.addDamageReduction(-2); //대미지 감소 -2
				pc.addSp(-1);
				pc.sendPackets(new S_SPMR(pc));
				pc.addMaxHp(-50);
				pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
				if (pc.isInParty()) { 
					pc.getParty().updateMiniHP(pc);
				}
				pc.addMaxMp(-30);
				pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()));
			}
			break;
		case SkillId.운세_버프_C:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.addMaxHp(-50);
				pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
				if (pc.isInParty()) { 
					pc.getParty().updateMiniHP(pc);
				}
				pc.addMaxMp(-30);
				pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()));
				pc.getAC().addAc(2);
				pc.sendPackets(new S_OwnCharAttrDef(pc));
			}
			break;
		case SkillId.운세_버프_D:
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.getAC().addAc(1);
				pc.sendPackets(new S_OwnCharAttrDef(pc));
			}
			break;
		default:
			break;
		}
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			sendStopMessage(pc, skillId);
			pc.sendPackets(new S_OwnCharStatus(pc));
		}
	}

	// 메세지의 표시(종료할 때)
	private static void sendStopMessage(L1PcInstance charaPc, int skillid) {
		L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillid);
		if (l1skills == null || charaPc == null) {
			return;
		}

		int msgID = l1skills.getSysmsgIdStop();
		if (msgID > 0) {
			charaPc.sendPackets(new S_ServerMessage(msgID));
		}
	}
}
