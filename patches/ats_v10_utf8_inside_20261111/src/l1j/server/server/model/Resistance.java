package l1j.server.server.model;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_SPMR;
import l1j.server.server.utils.IntRange;

public class Resistance {
	private static final int LIMIT_MIN = -128;
	private static final int LIMIT_MAX = 127;
	private static final int LIMIT_MIN_MR = 0;
	private static final int LIMIT_MAX_MR = 250;

	private int baseMr 	= 0; // 기본 마법 방어
	private int addedMr = 0; // 아이템이나 마법에 의해 추가된 마법 방어를 포함한 마법 방어
	
	private int fire  = 0; // 불 저항
	private int water = 0; // 물 저항
	private int wind  = 0; // 바람 저항 
	private int earth = 0; // 땅 저항
	
	private int regist_technology = 0;			// 스턴내성
	private int technique_hit = 0;				// 스턴적중
	
	private int regist_spirit = 0; 				// 정령내성
	private int spirit_hit = 0; 				// 정령적중
	
	private int prob_magic_Evasion = 0;			// 확률 마법 회피
	
	private L1Character character = null;

	public void dispose(){
		character = null;
	}

	public Resistance() {}
	public Resistance(L1Character cha) {
		init();
		character = cha;
	}
	
	public void init() {
		baseMr = addedMr = 0;
		fire = water = wind = earth = 0;
		regist_technology = technique_hit = regist_spirit = spirit_hit = 0;
	}
	
	private int checkMrRange(int i, final int MIN) {
		return IntRange.ensure(i, MIN, LIMIT_MAX_MR);
	}
	
	private byte checkRange(int i) {
		return (byte)IntRange.ensure(i, LIMIT_MIN, LIMIT_MAX);
	}
	
	public int getAddedMr() 	 { return addedMr; 										}
	public int getMr() { 
		return checkMrRange(baseMr + addedMr, LIMIT_MIN_MR);	
	}
	public int getBaseMr() 		 { return baseMr; 										}
	public void addMr(int i) 	 { 
		setAddedMr(addedMr + i);	
		if(character != null) {
			if(character instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) character;
				pc.sendPackets(new S_SPMR(pc), true);
			}		
		}	
	}	
	public void setBaseMr(int i) { baseMr = checkMrRange(i, LIMIT_MIN_MR); 				}
	private void setAddedMr(int i) 	 { addedMr = checkMrRange(i, -baseMr); 				}

	public int getregist_technology()  		{ return regist_technology;  		}
	public int gettechnique_hit() 		{ return technique_hit; 		}
	
	public int getregist_spirit(){ 		return regist_spirit; 	}
	public int getspirit_hit() 		{ return spirit_hit; 		} 
	
	public int getProbabilityMagicEvasion() { 
		return prob_magic_Evasion; 			
	}
	public void addProbabilityMagicEvasion(int i) { 
		prob_magic_Evasion = checkRange(prob_magic_Evasion + i); 		
	}
	public int getFire() 	{ return fire; 	}
	public int getWater()	{ return water; }
	public int getWind() 	{ return wind; 	}
	public int getEarth()	{ return earth; }
	
	public void addFire(int i) 	{ fire  = checkRange(fire + i);  }
	public void addWater(int i) { water = checkRange(water + i); }
	public void addWind(int i) 	{ wind  = checkRange(wind + i);  }
	public void addEarth(int i) { earth = checkRange(earth + i); }
	
	public void addregist_technology(int i)  		{ regist_technology 			= checkRange(regist_technology + i); 		}
	public void addtechnique_hit(int i) 		{ technique_hit 		= checkRange(technique_hit + i); 		}
	public void addregist_spirit(int i) 	{ regist_spirit 	= checkRange(regist_spirit + i);	} 
	public void addspirit_hit(int i) 		{ spirit_hit 		= checkRange(spirit_hit + i); 		}

	// 전체 내성 스턴, 정령 만 
	public void regist_all(int i) {
		addregist_technology(i);
		addregist_spirit(i);
	}
	// 전체 적중 
	public void hit_all(int i) {
		addtechnique_hit(i);
		addspirit_hit(i);
	}
	public void addAllNaturalResistance(int i) {
		addFire(i);
		addWater(i);
		addWind(i);
		addEarth(i);
	}
	public int getEffectedMr() {	
		int 토탈마방 = getMr(), 베이스마방 = getBaseMr(), value = 0;
		if(character.hasSkillEffect(SkillId.이레이즈_매직)) {
			value += (토탈마방 - 베이스마방) / 2;
		} else {
			value += 토탈마방;
		}
		return value;		
	}
}
