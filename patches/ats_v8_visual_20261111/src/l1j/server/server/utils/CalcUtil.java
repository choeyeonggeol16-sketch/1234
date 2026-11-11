package l1j.server.server.utils;

import java.util.Random;

import l1j.server.configure.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;

public class CalcUtil {

	private static Random rnd = new Random();

	private CalcUtil() {}
	
	/**
	 * 베이스 마법 치명타를 계산한다 
	 * @param chartype
	 * @param intel
	 * @return
	 */
	public static int getBaseMagicCritical(int charType, int intel) {
		int value = 0;
		switch(charType) {
		case 2: value += (intel >= 16 ? 4 : intel == 14 || intel == 15 ? 2 : 0); break;	// 요정
		case 3: value += (intel >= 18 ? 8 : intel == 17 ? 6 : intel == 16 ? 4 : intel == 15 ? 2 : 0); break; // 마법사
		}
		return value;
	}
	/**
	 * 원거리 회피 값을 계산한다.
	 */
	public static int getRangedEvasion(L1PcInstance pc, int charType, int dex, int level) {
		int value = 0;		
		value += (dex - 8) / 2;
		switch(charType) {
		case 1: value += (level / 4); break; // 기사			
		case 0: case 2: value += (level / 8); break; // 군주 , 요정
		case 3: value += (level / 10); break; // 마법사
		case 4: value += (level / 6); break; // 다크엘프
		}
		if (pc.hasSkillEffect(SkillId.드레스_이베이젼)) {	value += 12;}
		if (pc.hasSkillEffect(SkillId.솔리드_캐리지)) {	value += 15;} 	
		return value;		
	}
	
	/**
	 * 베이스 스텟에 따른 마법 명중 값을 계산한다 
	 * @param chartype
	 * @param intel
	 * @return
	 */
	public static int getBaseMagicHit(int charType, int intel) {
		int value = 0;
		switch(charType) {
		case 0: value += (intel >= 14 ? 2 : intel == 12 || intel == 13 ? 1 : 0); break; // 군주
		case 1: value += (intel >= 12 ? 2 : intel == 10 || intel == 11 ? 1 : 0); break; // 기사			
		case 2: value += (intel >= 15 ? 2 : intel == 13 || intel == 14 ? 1 : 0); break;	// 요정	
		case 3: value += (intel >= 14 ? 1 : 0); break; // 마법사
		}
		return value;
	}

	/**
	 * 스텟, 레벨에 의한 근거리 치명타 값을 걔산한다.
	 * @param str
	 * @return
	 */
	public static int getMeleeCriticalStat(int charType, int str, int level) {
		int value = 0;
		switch(charType) {
		/** TEST 나중에 변경할것 */
		case 0: value += 30; break; // 군주 
		case 1: value += 30; break; // 기사 
		case 2: value += 30; break; // 요정
		case 3: value += 30; break; // 마법사
		case 4: value += 30; break; // 다크엘프
		
		/*case 0: case 1: value += ((str - 30) / 10) + (level - 50) / 20; break; // 군주 , 기사
		case 2: value += ((str - 30) / 10) + (level - 50) / 24; break; // 요정
		case 3: value += ((str - 30) / 10) + (level - 50) / 30; break; // 마법사
		case 4: value += ((str - 30) / 10) + (level - 50) / 10; break; // 다크엘프*/
		}
		return value;
	}
	/**
	 * 스텟에 의한 원거리 치명타 값을 계산한다.
	 * @param dex
	 * @return
	 */
	public static int getRangedCriticalStat(int charType, int dex, int level) {
		int value = 0;
		switch(charType) {
		/** TEST 나중에 변경할것 */
		case 0: value += 30; break; // 군주 
		case 1: value += 30; break; // 기사 
		case 2: value += 30; break; // 요정
		case 3: value += 30; break; // 마법사
		case 4: value += 30; break; // 다크엘프
		
		/*case 0: value += ((dex - 30) / 10) + (level - 50) / 30; break; // 군주 
		case 1: value += ((dex - 30) / 10) + (level - 50) / 40; break; // 기사 
		case 2: value += ((dex - 30) / 10) + (level - 50) / 16; break; // 요정
		case 3: value += ((dex - 30) / 10) + (level - 50) / 50; break; // 마법사
		case 4: value += ((dex - 30) / 10) + (level - 50) / 20; break; // 다크엘프*/
		}
		return value;
	}

	/**
	 * 최대 소지 무게 값을 걔산한다.
	 * @param str
	 * @param con
	 * @return
	 */
	public static int getMaxWeight(int str, int con) {
		int total = str + con;
		if (total > 150) {
			total = 150;
		}
		if(total > 0) {
			total /= 2;
		}
		return (int) ((1000 + (total * 100)) * Config.RATE_WEIGHT_LIMIT);
	}
	/**
	 * 스텟에 의한 근거리명중 값을 계산한다.
	 * @param str
	 * @return
	 */
	public static int getMeleeHitStat(int str) {		
		return (str >= 60 ? 18 : str >= 57 && str <= 59 ? 17 : str >= 54 && str <= 56 ? 16 : str >= 51 && str <= 53 ? 15 :
			str >= 48 && str <= 50 ? 14 : str >= 45 && str <= 47 ? 13 : str >= 42 && str <= 44 ? 12 : str >= 39 && str <= 41 ? 11 :
			str >= 36 && str <= 38 ? 10 : str >= 33 && str <= 35 ? 9 : str >= 30 && str <= 32 ? 8 : str >= 27 && str <= 29 ? 7 :
			str >= 24 && str <= 26 ? 6 : str >= 21 && str <= 23 ? 5 : str >= 18 && str <= 20 ? 4 : str == 16 || str == 17 ? 3 :
			str == 14 || str == 15 ? 2 : str == 12 || str == 13 ? 1 : 0);
	}
	/**
	 * 스텟에 의한 원거리명중 값을 계산한다.
	 * @param dex
	 * @return
	 */
	public static int getRangedHitStat(int dex) {		
		return (dex >= 60 || dex == 59 ? 30 : dex >= 56 && dex <= 58 ? 29 :	dex >= 53 && dex <= 55 ? 28 : dex >= 49 && dex <= 52 ? 27 :
			dex >= 46 && dex <= 48 ? 26 : dex >= 42 && dex <= 45 ? 25 : dex >= 39 && dex <= 41 ? 24 : dex == 38 ? 23 : dex == 37 ? 22 :
			dex == 35 || dex == 36 ? 21 : dex == 34 ? 20 : dex == 33 ? 19 : dex == 32 ? 18 : dex == 31 ? 17 : dex == 30 ? 16 : dex == 29 ? 15 :
			dex == 28 ? 14 : dex == 27 ? 13 : dex == 26 ? 12 : dex == 25 ? 11 : dex == 24 ? 10 : dex == 23 ? 9 : dex == 22 ? 8 : dex == 21 ? 7 :
			dex == 20 ? 6 : dex == 19 ? 5 : dex == 17 || dex == 18 ? 4 : dex == 15 || dex == 16 ? 3 : dex == 13 || dex == 14 ? 2 :
			dex == 11 || dex == 12 ? 1 : 0);
	}
	/**
	 * 스텟에 의한 근거리데미지 값을 계산한다.
	 * @param str
	 * @return
	 */
	public static int getMeleeDmgStat(int str) {		
		return (str >= 60 || str == 59 ? 18 : str >= 55 && str <= 58 ? 17 : str >= 51 && str <= 54 ? 16 : str >= 47 && str <= 50 ? 15 :
			str >= 43 && str <= 46 ? 14 : str >= 39 && str <= 42 ? 13 : str >= 35 && str <= 38 ? 12 : str == 34 ? 11 : str == 33 ? 10 :
			str == 31 || str == 32 ? 9 : str == 29 || str == 30 ? 8 : str >= 26 && str <= 28 ? 7 : str >= 23 && str <= 25 ? 6 :
			str == 21 || str == 22 ? 5 : str >= 18 && str <= 20 ? 4 : str == 16 || str == 17 ? 3 : str == 14 || str == 15 ? 2 :
			str == 12 || str == 13 ? 1 : 0);
	}
	/**
	 * 스텟에 의한 원거리데미지 값을 계산한다.
	 * @param dex
	 * @return
	 */
	public static int getRangedDmgStat(int dex) {		
		return (dex >= 60 ? 19 : dex >= 56 && dex <= 59 ? 16 : dex >= 52 && dex <= 55 ? 15 : dex >= 48 && dex <= 51 ? 14 :
			dex == 47 ? 13 : dex >= 44 && dex <= 46 ? 12 : dex >= 40 && dex <= 43 ? 11 : dex >= 36 && dex <= 39 ? 10 :
			dex >= 33 && dex <= 35 ? 9 : dex >= 30 && dex <= 32 ? 8 : dex >= 27 && dex <= 29 ? 7 : dex >= 24 && dex <= 26 ? 6 :
			dex >= 21 && dex <= 23 ? 5 : dex >= 18 && dex <= 20 ? 4 : dex == 17 ? 3 : dex == 16 ? 2 : dex == 15 ? 1 : 0);
	}
	/**
	 * 스텟에 의한 마법 명중 값을 계산한다.
	 * @param intel
	 * @return
	 */
	public static int getMagicHitStat(int intel) {		
		return (intel >= 60 && intel <= 58 ? 13 : intel >= 55 && intel <= 57 ? 12 : intel >= 52 && intel <= 54 ? 11 :
			intel == 50 || intel == 51 ? 10 : intel >= 47 && intel <= 49 ? 9 : intel >= 44 && intel <= 46 ? 8 :
			intel >= 41 && intel <= 43 ? 7 : intel >= 38 && intel <= 40 ? 6 : intel >= 35 && intel <= 37 ? 5 :
			intel >= 32 && intel <= 34 ? 4 : intel >= 29 && intel <= 31 ? 3 : intel >= 26 && intel <= 28 ? 2 :
			intel >= 23 && intel <= 25 ? 1 : 0);
	}
	/**
	 * 스텟에 의해 엠소모 감소 값을 계산한다
	 * @param intel
	 * @return
	 */
	public static int getMpConsumptionDecrease(int intel) {		
		return (intel >= 45 ? 30 : intel == 44 ? 29 : intel == 42 || intel == 43 ? 28 : intel == 41 ? 27 :
			intel == 39 || intel == 40 ? 26 : intel == 38 ? 25 : intel ==  36 || intel == 37 ? 24 :
			intel == 35 ? 23 : intel == 33 || intel == 34 ? 22 : intel == 32 ? 21 : intel == 30 || intel == 31 ? 20 :
			intel == 29 ? 19 : intel == 27 || intel == 28 ? 18 : intel == 26 ? 17 : intel == 24 || intel == 25 ? 16 :
			intel == 23 ? 15 : intel == 21 || intel == 22 ? 14 : intel == 20 ? 13 : intel == 18 || intel == 19 ? 12 :
			intel == 17 ? 11 : intel == 15 || intel == 16 ? 10 : intel == 14 ? 9 : intel == 12 || intel == 13 ? 8 :
			intel == 11 ? 7 : intel == 10 ? 6 : intel >= 9 && intel <= 1 ? 5 : 0);
	}	
	/**
	 * 마법 보너스 값을 계산한다.
	 * @param inte
	 * @return
	 */
	public static int getMagicBonusStat(int intel) {
		try {
			if (intel < 12) {
				return 2;
			}		
			int temp = 2 + ((intel - 8) / 4);
			return temp;
		} catch (Exception e) {
			e.printStackTrace();
			return 2;
		}
	}
	/**
	 * AC보너스 값을 계산한다.
	 * @param level
	 * @param dex
	 * @return calcAc
	 */
	public static int calcAc(int level, int dex) {
		int acBonus = 10;
		switch(dex) {
		case 6: case 7: case 8: case 9: acBonus -= level / 8; break;
		case 10: case 11: case 12: acBonus -= level / 7; break;
		case 13: case 14: case 15: acBonus -= level / 6; break;
		case 16: case 17: acBonus -= level / 5; break;
		default: acBonus -= level / 4; break;
		}
		return acBonus;
	}
	/**
	 * 레벨, 베이스 스텟에 의한  근거리 명중값을 계산한다.
	 * @param pc
	 * @param str
	 * @return MeleeHit
	 */
	public static int calcBaseMeleeHit(int charType, int str, int level) {
		int result = 0;
		switch(charType) {
		case 0: result += (str >= 19 ? 2 : str >= 16 && str <= 18 ? 1 : 0); result += (level / 5) + level; break;  // 군주
		case 1: result += (str >= 19 ? 4 : str == 17 || str == 18 ? 2 : 0); result += (level / 3) + level; break;  // 기사
		case 2: result += (str >= 15 ? 2 : str == 13 || str == 14 ? 1 : 0); result += (level / 5) + level; break;  // 요정
		case 3: result += (str >= 13 ? 2 : str == 11 || str == 12 ? 1 : 0); result += (level / 3) + level; break;  // 마법사
		case 4: result += (str >= 17 ? 2 : str == 15 || str == 16 ? 1 : 0); result += (level / 8) + level; break;  // 다크엘프
		}
		return result;
	}
	/**
	 * 레벨, 베이스 스텟에 의한 근거리 데미지값을 계산한다.
	 * @param pc
	 * @param str
	 * @return MeleeDmg
	 */
	public static int calcBaseMeleeDmg(int charType, int str, int level) {
		int result = 0;
		switch(charType) {
		case 0: result += (str >= 18 ? 2 : str >= 15 && str <= 17 ? 1 : 0); result += (level / 30); break;  // 군주
		case 1: result += (str >= 20 ? 4 : str == 18 || str == 19 ? 2 : 0); result += (level / 10); break;  // 기사
		case 2: result += (str >= 14 ? 2 : str == 12 || str == 13 ? 1 : 0); result += (level / 30); break;  // 요정
		case 3: result += (str >= 12 ? 2 : str == 10 || str == 11 ? 1 : 0); result += (level / 40); break;  // 마법사
		case 4: result += (str >= 18 ? 2 : str >= 14 && str <= 17 ? 1 : 0); result += (level / 10); break;  // 다크엘프
		}
		return result;
	}
	/**
	 * 레벨, 베이스 스텟에 의한 원거리 명중 값을 계산한다.
	 * @param pc
	 * @param dex
	 * @return RangedHit
	 */
	public static int calcBaseRangedHit(int charType, int dex, int level) {
		int result = 0;
		switch(charType) {
		case 0: case 1: result += (level / 5) + level; break; 	
		case 2: result += (dex >= 16 ? 3 : dex >= 13 && dex <= 15 ? 2 : 0); result += (level / 3) + level; break;
		case 3: result += (level / 8) + level; break; 	
		case 4: result += (dex >= 18 ? 2 : dex == 17 ? 1 : 0); result += (level / 5) + level; break; 
		}
		return result;
	}
	/**
	 * 레벨, 베이스 스텟에 의한 원거리 데미지 값을 계산한다.
	 * @param pc
	 * @param dex
	 * @return RangedDmg
	 */
	public static int calcBaseRangedDmg(int charType, int dex, int level) {
		int result = 0;
		switch(charType) {
		case 0: result += (dex >= 13 ? 1 : 0); break;
		case 2: result += (dex >= 17 ? 3 : dex >= 14 && dex <= 16 ? 2 : 0); result += (level / 10); break;
		case 4: result += (dex >= 18 ? 2 : 0); break;
		}
		return result;
	}
	/**
	 * 베이스 스텟에 의한 원거리 회피력 값을  계산한다.
	 * @param pc
	 * @param dex
	 * @return Er
	 */
	public static int calcBaseEr(int charType, int dex) {
		int result = 0;
		switch(charType) {
		case 0: result += (dex >= 18 ? 3 : dex == 16 || dex == 17 ? 2 : dex == 14 || dex == 15 ? 1 : 0); break;    
		case 1: result += (dex >= 16 ? 3 : dex == 14 || dex == 15 ? 1 : 0); break;
		case 3: result += (dex >= 11 ? 2 : dex == 9 || dex == 10 ? 1 : 0); break;
		case 4: result += (dex >= 16 ? 2 : 0); break;
		}
		return result;
	}
	/**
	 * 베이스 스텟에 의한 AC보너스 값을  계산한다.
	 * @param pc
	 * @param dex
	 * @return acBonus
	 */
	public static int calcBaseAc(int charType, int dex) {
		int result = 0;
		switch(charType) {
		case 0: result -= (dex >= 17 ? 3 : dex == 15 || dex == 16 ? 2 : dex >= 12 && dex <= 14 ? 1 : 0); break;		
		case 1: result -= (dex >= 15 ? 3 : dex == 13 || dex == 14 ? 1 : 0); break;
		case 2: result -= (dex >= 18 ? 2 : dex >= 15 && dex <= 17 ? 1 : 0); break;
		case 3: result -= (dex >= 10 ? 2 : dex == 8 || dex == 9 ? 1 : 0); break;
		case 4: result -= (dex >= 17 ? 1 : 0); break;
		}
		return result;
	}
	/**
	 * 베이스 스텟에 의한 마법방어 값을 계산한다.
	 * @param pc
	 * @param wis
	 * @return MR
	 */
	public static int calcBaseMr(int charType, int wis, int level) {
		int result = 0;
		switch(charType) {
		case 0: result += (wis >= 14 ? 2 : wis == 12 || wis == 13 ? 1 : 0); result += 10; result += level / 2; break; 
		case 1: result += (wis >= 12 ? 2 : wis == 10 || wis == 11 ? 1 : 0); result += level / 2; break; 
		case 2: result += (wis >= 16 ? 2 : wis >= 13 && wis <= 15 ? 1 : 0); result += 25; result += level / 2; break; 
		case 3: result += (wis >= 15 ? 1 : 0); result += 15; result += level / 2; break; 
		case 4: result += (wis >= 16 ? 4 : wis == 15 ? 3 : wis == 14 ? 2 : wis >= 11 && wis <= 13 ? 1 : 0); result += 10; result += level / 2; break; 
		}
		return result;
	}
	/**
	 * 베이스 스텟에 의한 피 회복틱을 계산한다.
	 * @param con
	 * @return BonusHpr 
	 */
	public static int calcBaseHpr(int charType, int con) {
		int result = 0;
		switch(charType) {
		case 0: result += (con >= 18 ? 4 : con == 17 ? 3 : con == 15 || con == 16 ? 2 : con == 13 || con == 14 ? 1 : 0); break; // 군주
		case 1: result += (con >= 18 ? 4 : con == 16 || con == 17 ? 2 : 0); break; // 기사
		case 2: result += (con >= 17 ? 3 : con == 16 ? 2 : con == 14 || con == 15 ? 1 : 0); break; // 요정
		case 3: result += (con >= 18 ? 2 : con == 17 ? 1 : 0); break; // 마법사
		case 4: result += (con >= 13 ? 2 : con == 12 ? 1 : 0); break; // 다크엘프
		}	
		return result;
	}
	/**
	 * 스텟에 의한 마법 방어 값을 계산한다.
	 * @param wis
	 * @return mrBonus
	 */
	public static int calcStatMr(int wis) {
		return (wis >= 50 ? 65 : wis >= 47 && wis <= 49 ? 64 : wis >= 44 && wis <= 46 ? 62 :
			wis >= 40 && wis <= 43 ? 59 : wis >= 35 && wis <= 39 ? 55 : wis >= 30 && wis <= 34 ? 52 :
			wis >= 24 && wis <= 29 ? 50 : wis == 23 ? 47 : wis == 22 ? 37 : wis == 21 ? 28 : wis == 20 ? 21 :
			wis == 19 ? 15 : wis == 18 ? 10 : wis == 17 ? 6 : wis == 15 || wis == 16 ? 3 : 0);
	}

	/**
	 * 베이스 스텟에 의한 MP 회복 보너스를 돌려준다.
	 * @param chartype(캐릭터타입)
	 * @param con
	 * @return BonusMpr
	 * 
	 */
	public static int calcBaseMpr(int charType, int con) {
		int result = 0;
		switch(charType) {
		case 0: result += (con >= 15 ? 2 : con == 13 || con == 14 ? 1 : 0); break; // 군주	
		case 1: result += (con >= 13 ? 2 : con == 11 || con == 12 ? 1 : 0); break; // 기사
		case 2: result += (con >= 18 ? 2 : con >= 15 && con <= 17 ? 1 : 0); break; // 요정	
		case 3: result += (con >= 18 ? 3 : con == 16 || con == 17 ? 2 : con == 14 || con == 15? 1 : 0); break; // 마법사
		case 4: result += (con >= 13 ? 1 : 0); break; // 다크엘프
		}	
		return result;
	}

	/**
	 * 각 클래스의 LVUP시의 HP상승치를 돌려준다
	 * 
	 * @param charType
	 * @param baseMaxHp
	 * @param baseCon
	 * @return HP상승치
	 */
	public static short calcStatHp(int charType, int baseMaxHp, byte baseCon) {
		short randomhp = 0;
		int addCon = 0;

		if (charType == 0) { // 군주
			int calCon = 10; 
			switch(baseCon - calCon) {
			case 0 : case 1 : addCon = 1 + rnd.nextInt(2); break;
			case 2 : case 3 : addCon = 2 + rnd.nextInt(2); break;
			case 4 : case 5 : addCon = 3 + rnd.nextInt(2); break;
			case 6 : addCon = 5 + rnd.nextInt(2); break;
			case 7 : addCon = 6 + rnd.nextInt(2); break;
			case 8 : addCon = 7 + rnd.nextInt(2); break;
			default : addCon = (baseCon - calCon - 1) + (int)((baseCon - calCon)/5) + rnd.nextInt(2); break;	
			}
			randomhp += calCon + addCon;

			if (baseMaxHp + randomhp > Config.PRINCE_MAX_HP) {
				randomhp = (short) (Config.PRINCE_MAX_HP - baseMaxHp);
			}
		} else if (charType == 1) { // 기사
			int calCon = 14; 
			switch(baseCon - calCon){
			case 0 : addCon = 4 + rnd.nextInt(2); break;
			case 1 : addCon = 5 + rnd.nextInt(2); break;
			case 2 : addCon = 6 + rnd.nextInt(2); break;
			case 3 : addCon = 8 + rnd.nextInt(2); break;
			case 4 : addCon = 9 + rnd.nextInt(2); break;
			default : addCon = 9 + rnd.nextInt(2) + (int)((baseCon - calCon)/3); break;	
			}

			randomhp += calCon + addCon;

			if (baseMaxHp + randomhp > Config.KNIGHT_MAX_HP) {
				randomhp = (short) (Config.KNIGHT_MAX_HP - baseMaxHp);
			}
		} else if (charType == 2) { // 요정
			int calCon = 12; 
			switch(baseCon - calCon){
			case 0 : addCon = 0 + rnd.nextInt(2); break;
			case 1 : case 2 : case 3 : addCon = 1 + rnd.nextInt(2); break;
			case 4 : addCon = 2 + rnd.nextInt(2); break;
			case 5 : addCon = 3 + rnd.nextInt(2); break;
			case 6 : addCon = 4 + rnd.nextInt(2); break;
			default : addCon = 4 + rnd.nextInt(2) + (int)((baseCon - calCon)/3); break;	
			}
			randomhp += calCon - 2  + addCon;

			if (baseMaxHp + randomhp > Config.ELF_MAX_HP) {
				randomhp = (short) (Config.ELF_MAX_HP - baseMaxHp);
			}
		} else if (charType == 3) { // 마법사
			int calCon = 12; 
			switch(baseCon - calCon){
			case 0 : case 1 : addCon = 0 + rnd.nextInt(2); break;
			case 2 : case 3 : addCon = 1 + rnd.nextInt(2); break;
			case 4 : addCon = 2 + rnd.nextInt(2); break;
			case 5 : addCon = 4 + rnd.nextInt(2); break;
			case 6 : addCon = 5 + rnd.nextInt(2); break;
			default : addCon = 5 + rnd.nextInt(2) + (int)((baseCon - calCon)/3); break;	
			}
			randomhp += calCon - 5  + addCon;

			if (baseMaxHp + randomhp > Config.WIZARD_MAX_HP) {
				randomhp = (short) (Config.WIZARD_MAX_HP - baseMaxHp);
			}
		} else if (charType == 4) { // 다크엘프
			int calCon = 8; 
			switch(baseCon - calCon){
			case 0 : case 1 : addCon = 0 + rnd.nextInt(2); break;
			case 2 : case 3 : addCon = 1 + rnd.nextInt(2); break;
			case 4 : case 5 : case 6 : case 7 : addCon = 2 + rnd.nextInt(2); break;
			case 8 : case 9 : case 10 : addCon = 3 + rnd.nextInt(2); break;
			default : addCon = 3 + rnd.nextInt(2) + (int)((baseCon - calCon)/4); break;	
			}
			randomhp += calCon + 3 + addCon;

			if (baseMaxHp + randomhp > Config.DARKELF_MAX_HP) {
				randomhp = (short) (Config.DARKELF_MAX_HP - baseMaxHp);
			}
		}
		if (randomhp < 0) {
			randomhp = 0;
		}
		return randomhp;
	}

	/**
	 * 클래스  레벨업의 경우  MP상승치를 돌려준다
	 * @param charType
	 * @param baseMaxMp
	 * @param baseWis
	 * @return MP상승치
	 */
	public static short calcStatMp(int charType, int baseMaxMp, byte baseWis) {
		int randommp = 0;

		if (charType == 0) { // 공주
			int addWis = 0;
			int calWis = 11; 
			switch(baseWis - calWis){
			case 0 : case 1 : addWis = 2 + rnd.nextInt(2); break;
			case 2 : case 3 : addWis = 3 + rnd.nextInt(2); break;
			case 4 : case 5 : addWis = 4 + rnd.nextInt(2); break;
			case 6 : case 7 : addWis = 5 + rnd.nextInt(2); break;
			default : addWis = 5 + rnd.nextInt(2) + (int)((baseWis - calWis)/3); break;	
			}
			randommp +=  addWis;

			if (baseMaxMp + randommp > Config.PRINCE_MAX_MP) {
				randommp = Config.PRINCE_MAX_MP - baseMaxMp;
			}
		} else if (charType == 1) { // 기사
			int addWis = 0;
			int calWis = 9; 
			switch(baseWis - calWis){
			case 0 : case 1 : addWis = 1 + rnd.nextInt(2); break;
			case 2 : case 3 : case 4 : addWis = 2+ rnd.nextInt(2); break;
			case 5 : case 6 : case 7 :	 addWis = 3 + rnd.nextInt(2); break;
			default : addWis = 3 + rnd.nextInt(2) + (int)((baseWis - calWis)/3); break;	
			}
			randommp +=  addWis;

			if (baseMaxMp + randommp > Config.KNIGHT_MAX_MP) {
				randommp = Config.KNIGHT_MAX_MP - baseMaxMp;
			}
		} else if (charType == 2) { // 엘프
			int addWis = 0;
			int calWis = 12; 
			switch(baseWis - calWis){
			case 0 : case 1 : addWis = 4 + rnd.nextInt(2); break;
			case 2 : case 3 : addWis = 5 + rnd.nextInt(2); break;
			case 4 : case 5 : addWis = 6 + rnd.nextInt(2); break;
			case 6 : addWis = 8 + rnd.nextInt(2); break;
			default : addWis = 8 + rnd.nextInt(2) + (int)((baseWis - calWis)/5); break;	
			}
			randommp +=  addWis;

			if (baseMaxMp + randommp > Config.ELF_MAX_MP) {
				randommp = Config.ELF_MAX_MP - baseMaxMp;
			}
		} else if (charType == 3) { // 마법사
			int addWis = 0;
			int calWis = 12; 
			switch(baseWis - calWis){
			case 0 : addWis = 6 + rnd.nextInt(2); break;
			case 1 : case 2 : addWis = 7 + rnd.nextInt(2); break;
			case 3 : addWis = 8 + rnd.nextInt(2); break;
			case 4 : case 5 : addWis = 9 + rnd.nextInt(2); break;
			case 6 : addWis = 10 + rnd.nextInt(2); break;
			default : addWis = 11 + rnd.nextInt(2) + (int)((baseWis - calWis)/5); break;	
			}
			randommp +=  addWis;

			if (baseMaxMp + randommp > Config.WIZARD_MAX_MP) {
				randommp = Config.WIZARD_MAX_MP - baseMaxMp;
			}
		} else if (charType == 4) { // 다크엘프
			int addWis = 0;
			int calWis = 10; 
			switch(baseWis - calWis) {
			case 0 : case 1 : addWis = 3 + rnd.nextInt(2); break;
			case 2 : case 3 : addWis = 4 + rnd.nextInt(2); break;
			case 4 : case 5 : addWis = 5 + rnd.nextInt(2); break;
			case 6 : case 7 : addWis = 6 + rnd.nextInt(2); break;
			case 8 : addWis = 7 + rnd.nextInt(2); break;
			default : addWis = 7 + rnd.nextInt(2) + (int)((baseWis - calWis)/5); break;	
			}
			randommp +=  addWis;

			if (baseMaxMp + randommp > Config.DARKELF_MAX_MP) {
				randommp = Config.DARKELF_MAX_MP - baseMaxMp;
			}
		}
		if (randommp < 0) {
			randommp = 0;
		}
		return (short) randommp;
	}
}
