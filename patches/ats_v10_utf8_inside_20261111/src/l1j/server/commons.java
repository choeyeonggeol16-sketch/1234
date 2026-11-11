package l1j.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;

import l1j.server.configure.Config;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_Paralysis;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.ServerBasePacket;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.EffectUtil;
import l1j.server.server.utils.SkillUtil;

public class commons {

	public static boolean isNullOrEmpty(String s) {
		return (s == null) || (s.equals("")) || (s.length() <= 0);
	}

	public static int[] parseToIntArrange(String s, String tok) {
		if (s == null || s.equals(""))
			return null;

		String[] arr = s.split(tok);
		int size = arr.length;
		int[] result = new int[size];
		for (int i = size - 1; i >= 0; --i) {
			try {
				result[i] = Integer.parseInt(arr[i]);
			} catch (Exception e) {
				break;
			}
		}
		return result;
	}

	public static ArrayList<Integer> parseToIntArray(String s, String tok) {
		if (s == null || s.equals(""))
			return null;

		String[] arr = s.split(tok);
		int size = arr.length;
		ArrayList<Integer> list = new ArrayList<Integer>(size);
		for (int i = 0; i < size; ++i) {
			try {
				if (arr[i].startsWith("0x"))
					list.add(Integer.parseInt(arr[i].replaceFirst("^0x", ""), 16));
				else
					list.add(Integer.parseInt(arr[i]));
			} catch (Exception e) {
				break;
			}
		}
		return list;
	}
	public static final int[] _headingWideLines = new int[] {
		6, 7, 0, 1, 2, 3, 4, 5
	};
	public static boolean isWideAroundDirection(L1Character owner, L1Character target) {
		int oh = owner.getHeading();
		int th = calcheading(owner.getX(), owner.getY(), target.getX(), target.getY());
		return (oh == th || _headingWideLines[oh] == th || _headingWideLines[(oh + 1) % 8] == th || _headingWideLines[(oh + 2) % 8] == th|| _headingWideLines[(oh + 3) % 8] == th || _headingWideLines[(oh + 4) % 8] == th); 
	}

	public static ArrayList<Integer> parseToIntArray(String s) {
		return parseToIntArray(s, " ");
	}

	public static ArrayDeque<Integer> parseToIntQ(String s) {
		return parseToIntQ(s, " ");
	}

	public static ArrayDeque<Integer> parseToIntQ(String s, String tok) {
		if (s == null || s.equals(""))
			return null;

		String[] arr = s.split(tok);
		int size = arr.length;
		ArrayDeque<Integer> argsQ = new ArrayDeque<Integer>(size);
		for (int i = 0; i < size; i++) {
			try {
				argsQ.offer(Integer.parseInt(arr[i]));
			} catch (Exception e) {
				break;
			}
		}
		return argsQ;
	}

	public static final ServerBasePacket on_paralysis = new S_Paralysis(S_Paralysis.TYPE_BIND, true);
	public static final ServerBasePacket off_paralysis = new S_Paralysis(S_Paralysis.TYPE_BIND, false);

	protected static Random _rnd = new Random(System.nanoTime());

	/**
	 * 현재 나와 타겟의 거리를 계산한다.
	 * @param x
	 * @param y
	 * @param tx
	 * @param ty
	 * @return
	 */
	public static int getDistance(int x, int y, int tx, int ty) {
		long dx = tx - x;
		long dy = ty - y;
		return (int) Math.sqrt(dx * dx + dy * dy);
	}

	public static final int[][] _headings = new int[][] { { 7, 0, 1 }, { 6, 1, 2 }, { 5, 4, 3 }, };

	public static final byte HEADING_TABLE_X[] = { 0, 1, 1, 1, 0, -1, -1, -1 };
	public static final byte HEADING_TABLE_Y[] = { -1, -1, 0, 1, 1, 1, 0, -1 };

	public static int calcheading(int myx, int myy, int tx, int ty) {
		return _headings[(ty > myy ? 2 : (ty < myy ? 0 : 1))][(tx > myx ? 2 : (tx < myx ? 0 : 1))];
	}

	/**
	 * 타겟이 현재 스킬 상태라면 행동을 제어한다.
	 * @param target
	 * @return
	 */
	public static boolean isNonAction(L1Character c) {
		return (c.hasSkillEffect(SkillId.쇼크_스턴)) || (c.hasSkillEffect(SkillId.어스_바인드)) 
				|| (c.hasSkillEffect(SkillId.아이스_랜스)) || (c.hasSkillEffect(SkillId.상태_동결))
				|| (c.hasSkillEffect(SkillId.포그_오브_슬리핑) || (c.isParalyzed()) || (c.isSleeped()));
	}

	/**
	 * 드래곤 레이드 진행중인 캐릭터가 현재 스킬 상태 일경우 false 처리
	 * @param cha
	 * @return
	 */
	public static boolean isDragonRaidLock(L1Character cha) {
		return cha.hasSkillEffect(SkillId.쇼크_스턴) || cha.hasSkillEffect(SkillId.어스_바인드)
		|| cha.hasSkillEffect(SkillId.앱솔루트_배리어) || cha.hasSkillEffect(SkillId.아이스_랜스) 
		|| cha.hasSkillEffect(SkillId.상태_동결);
	}
	/**
	 * 타겟이 현재 스킬 상태라면 데미지를 0처리 한다.
	 * @param target
	 * @return
	 */
	public static boolean isLock(L1Character target) {
		return target.hasSkillEffect(SkillId.어스_바인드) || target.hasSkillEffect(SkillId.앱솔루트_배리어)
				|| target.hasSkillEffect(SkillId.아이스_랜스) || target.hasSkillEffect(SkillId.상태_동결);
	}

	/**
	 * 마법인형의 마법데미지를 산출한다.
	 * @return getMagicDollMagicDmg
	 */
	public static double getMagicDollMagicDmg(L1Character attacker, L1Character defender, int weakattr, int min_dmg, int max_dmg, int in_dmg) {
		int dmg = 0, dd = 3;
		int 기본데미지 = CommonUtil.random(((max_dmg - min_dmg) + 1) + min_dmg) + in_dmg;
		try {
			dmg += 기본데미지;	
			double 속성방어 = calcAttrResistance(defender, weakattr);
			double 중간마법데미지 = ((1.0 + 3.0 / 32.0 * dd) - 속성방어);
			if (중간마법데미지 < 1) {
				중간마법데미지 = 1;
			}
			dmg *= 중간마법데미지;	
			int 타겟엠알 = defender.getResistance().getMr();

			// 타겟의 마법에 대해 데미지 처리 
			if(타겟엠알 <= 70) {
				if(CommonUtil.randomRange(1, 100) <= 10) {
					dmg *= 1.5;
				}
			} 
			if(defender instanceof L1PcInstance) {
				L1PcInstance targetPc = (L1PcInstance) defender;
				// 이레 삭제
				SkillUtil.cancelEraseMagic(targetPc);
				// 카매 제거
				if(targetPc.hasSkillEffect(SkillId.카운터_매직)) {
					targetPc.removeSkillEffect(SkillId.카운터_매직);
					S_SkillSound ss = S_SkillSound.get(defender.getId(), EffectUtil.카운터_매직);
					targetPc.sendPackets(ss, false); 
					targetPc.broadcastPacket(ss);
					dmg = 0;
				}
				commons.getReducIitems(targetPc, dmg);
				dmg -= targetPc.getDamageReduction();
				if (defender != null && defender.hasSkillEffect(SkillId.이뮨_투함)) {
					dmg *= Config.IMMNUE_TO_HARM_RATE;
				}
			}	
			return dmg;
		} catch (Exception e) {
			e.printStackTrace(); }
		return 0;
	}
	/**
	 * 레이드 몬스터의 마법 데미지를 산출한다.
	 */
	public static int getDragonRaidMagicDamage(L1Character attacker, L1Character target, int min, int max) {
		int dmg = 0;
		try {
			int intel = attacker.getAbility().getTotalInt();
			int 기본대미지 = _rnd.nextInt(max - min) + min;
			dmg += 기본대미지;
			double 중간마법데미지 = (1.0 + 3.0 / 32.0 * intel);
			dmg *= 중간마법데미지;	
			int 타겟엠알 = target.getResistance().getMr();
			int 내마법명중 = (CalcUtil.getMagicHitStat(attacker.getAbility().getTotalInt()) + attacker.getMagicHit());
			double 마법방어데미지감소 = 0;
			// 마법 방어 처리 
			double 마법방어 = 0;
			if (타겟엠알 <= 100) {
				마법방어 = Math.floor((타겟엠알 - 내마법명중) / 2);
			} else if (타겟엠알 >= 100) {
				마법방어 = Math.floor((타겟엠알 - 내마법명중) / 10);
			}
			if(타겟엠알 <= 100) {
				마법방어데미지감소 = 1 - 0.01 * 마법방어;
			} else if(타겟엠알 > 100) {
				마법방어데미지감소 = 0.6 - 0.01 * 마법방어;
			}
			dmg *= 마법방어데미지감소;	
		} catch(Exception e) {
			System.out.println("용 레이드 데미지 산출 오류 " + attacker.getName());
		}
		return dmg;
		
	}
	/**
	 * 리덕션 아이템에 관한 로직 (여러군데에서 쓰므로 하나에서  괸리함)
	 */
	public static int getReducIitems(L1PcInstance defender, int dmg) {
		
		if(defender.toArmorEquipped().isLindviorArmor()) {
			ItemInstance armor = defender.toArmorEquipped().getArmor();
			int 인챈트레벨 = defender.toArmorEquipped().getLindviorArmorEn(), armorid = armor.getItemId(), 확률 
					= (인챈트레벨 >= 10 ? 10 : 인챈트레벨 == 9 ? 9 : 인챈트레벨 == 8 ? 8 : 인챈트레벨 == 7 ? 7 : 인챈트레벨 == 6 ? 6 : 5);
			if (!defender.hasSkillEffect(SkillId.SKILL_DELAY)) {
				if(CommonUtil.randomRange(1, 100) <= 확률) {
					if(armorid == ItemId.린드비오르_완력 && dmg >= 30) {
						defender.setCurrentMp(defender.getCurrentMp() + 8 + CommonUtil.random(7));
					} else if(armorid == ItemId.린드비오르_예지력 && dmg >= 25) {
						defender.setCurrentMp(defender.getCurrentMp() + 9 + CommonUtil.random(7));
					} else if(armorid == ItemId.린드비오르_인내력 && dmg >= 20) {
						defender.setCurrentMp(defender.getCurrentMp() + 16 + CommonUtil.random(9));
					} else if(armorid == ItemId.린드비오르_마력 && dmg >= 15) {
						defender.setCurrentMp(defender.getCurrentMp() + 20 + CommonUtil.random(11));
					}				
					defender.setSkillEffect(SkillId.SKILL_DELAY, 5000);
					defender.sendAll(EffectUtil.린드비오르_가호);
				}
			}	
		}		
		else if(defender.toArmorEquipped().isPapurionArmor()) {
			int 인챈트레벨 = defender.toArmorEquipped().getPapurionArmorEn(),
					인챈트힐량 = CommonUtil.randomRange(1, 8), 회복량 = (인챈트레벨 >= 9 ? 101 + 인챈트힐량 
							: 인챈트레벨 == 8 ? 93 + 인챈트힐량 : 인챈트레벨 == 7 ? 84 + 인챈트힐량 : 76 + 인챈트힐량), 찬스 = 6;
			if(CommonUtil.randomRange(1, 100) <= 찬스) {
				if (defender.hasSkillEffect(SkillId.워터_라이프)) {
					회복량 <<= 1;
				}
				else if (defender.hasSkillEffect(SkillId.플루트_워터)) {
					회복량 >>= 1;
				}	
				defender.setCurrentHp(defender.getCurrentHp() + 회복량);
				defender.sendAll(EffectUtil.파푸리온_가호);
			}
		}  	
		else if(defender.toArmorEquipped().isHolyFairyRlateArmor()) {
			int 회복량 = 31 + CommonUtil.randomRange(1, 10);
			if(CommonUtil.randomRange(1, 100) <= 4) {
				if (defender.hasSkillEffect(SkillId.워터_라이프)) {
					회복량 <<= 1;
				}
				else if (defender.hasSkillEffect(SkillId.플루트_워터)) {
					회복량 >>= 1;
				}	
				defender.setCurrentHp(defender.getCurrentHp() + 회복량);
				defender.sendAll(EffectUtil.페어리_가호);
			}	
		}
		// 반역자 방패  구현  
		if (defender.toArmorEquipped().isTraitorshield()) {
			int chance = defender.toArmorEquipped().getTraitorShieldEn() * 2;
			if (CommonUtil.randomRange(1, 100) <= chance) {
				dmg -= 50;
				defender.sendAll(EffectUtil.반역자_방패_이팩트);
			}
		}
		if(defender.toArmorEquipped().isHolyFairyShield()) {
			int 확률 = defender.toArmorEquipped().getHolyFairyShieldEn();
			if (CommonUtil.randomRange(1, 100) <= 확률) {
				dmg -= 10;
				defender.sendAll(EffectUtil.신성한_요정족_방패_이팩트);
			}
		}
		if(defender.toArmorEquipped().isRoomtisRedArmor()) { // 룸티스 붉은빛 귀걸이 구현
			int 인챈트레벨 = defender.toArmorEquipped().getRoomtisRedEn(), 확률 = (인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 2);
			if (인챈트레벨 > 4 && CommonUtil.randomRange(1, 100) <= 확률) {
				dmg -= 20;
				defender.sendForMe(EffectUtil.룸티스_붉은빛_귀걸이_이팩트);
			}		
		} else if(defender.toArmorEquipped().isBlessRoomtisRedArmor()) { 	// 축복 룸티스 붉은빛 귀걸이 구현
			int 인챈트레벨 = defender.toArmorEquipped().getBlessRoomtisRedEn(), 확률 = (인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 3 : 2);
			if (인챈트레벨 > 3 && CommonUtil.randomRange(1, 100) <= 확률) {
				dmg -= 20;
				defender.sendForMe(EffectUtil.룸티스_붉은빛_귀걸이_이팩트);
			}
		}
		if(defender.toArmorEquipped().isSnapperHealthRing()) { // 스냅퍼 체력 반지 구현
			int 인챈트레벨 = defender.toArmorEquipped().getSnapperHealthRingEn(), 확률 = (인챈트레벨 == 9 ? 4 : 인챈트레벨 == 8 ? 2 : 인챈트레벨 == 7 ? 1 : 0);
			if (인챈트레벨 > 6 && CommonUtil.randomRange(1, 100) <= 확률) {
				dmg -= 20;
				defender.sendForMe(EffectUtil.룸티스_붉은빛_귀걸이_이팩트);
			}	
		}
		if(defender.toArmorEquipped().isBlessSnapperHealthRing()) { // 축복 스냅퍼 체력 반지 구현
			int 인챈트레벨 = defender.toArmorEquipped().getBlessSnapperHealthRingEn(), 확률 = (인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 2 : 인챈트레벨 == 6 ? 1 : 0);
			if (인챈트레벨 > 5 && CommonUtil.randomRange(1, 100) <= 확률) {
				dmg -= 20;
				defender.sendForMe(EffectUtil.룸티스_붉은빛_귀걸이_이팩트);
			}	
		}
		if(defender.hasSkillEffect(SkillId.리덕션_아머)) {
			int targetLevel = defender.getLevel() < 50 ? 50 : defender.getLevel();
			dmg -= (targetLevel - 50) / 5 + 1;
		}
		return dmg;
	}
	
	/**
	 * 마법 검 데미지를 계산해서 산출한다.
	 * @return getMagicDamage
	 */
	public static double getMagicDamage(L1Character attacker,
			L1Character defender, boolean cri, boolean erase, boolean counter, int weakattr, int min_dmg, int max_dmg, int in_dmg) {		
		int dmg = 0, 인트스펠 = 0, trueSp = attacker.getSp() - attacker.getTrueSp();
		try {
			인트스펠 = attacker.getAbility().getTotalInt() + trueSp - 12;
			
			int 기본데미지 = CommonUtil.random(((max_dmg - min_dmg) + 1) + min_dmg) + in_dmg;
			dmg += 기본데미지;	
			double 속성방어 = calcAttrResistance(defender, weakattr);

			double 중간마법데미지 = ((1.0 + 3.0 / 32.0 * 인트스펠) - 속성방어);
			dmg *= 중간마법데미지;	

			// 크리티컬 데미지 처리 
			if(cri) {	
				if(attacker instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) attacker;
					int 베이스마법치명타 =  CalcUtil.getBaseMagicCritical(pc.getType(), pc.getAbility().getTotalInt());
					if (CommonUtil.randomRange(1, 100) <= 베이스마법치명타) {
						dmg *= 1.5;
					}
				}	
			}
			int 타겟엠알 = defender.getResistance().getMr();
			int 내마법명중 = (CalcUtil.getMagicHitStat(attacker.getAbility().getTotalInt()) + attacker.getMagicHit());
			double 마법방어데미지감소 = 0;
			// 마법 방어 처리 
			double 마법방어 = 0;
			if (타겟엠알 <= 100) {
				마법방어 = Math.floor((타겟엠알 - 내마법명중) / 2);
			} else if (타겟엠알 >= 100) {
				마법방어 = Math.floor((타겟엠알 - 내마법명중) / 10);
			}
			if(타겟엠알 <= 100) {
				마법방어데미지감소 = 1 - 0.01 * 마법방어;
			} else if(타겟엠알 > 100) {
				마법방어데미지감소 = 0.6 - 0.01 * 마법방어;
			}
			dmg *= 마법방어데미지감소;	
			// 이레이즈 매직 유무 판별
			if(erase) {
				SkillUtil.cancelEraseMagic(defender);
			}
			// 카운터 매직 유무
			if(counter) {
				if(defender.hasSkillEffect(SkillId.카운터_매직)) {
					defender.removeSkillEffect(SkillId.카운터_매직);
					S_SkillSound ss = S_SkillSound.get(defender.getId(), EffectUtil.카운터_매직);
					defender.broadcastPacket(ss);
					if (defender instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) defender;
						pc.sendPackets(ss, false); 
						dmg = 0;
					}
				}
			}
			// 타겟의 데미지 리덕션 처리 
			if(defender instanceof L1PcInstance) {
				L1PcInstance targetPc = (L1PcInstance) defender;
				commons.getReducIitems(targetPc, dmg);		
				dmg -= targetPc.getDamageReduction();
				if (defender != null && defender.hasSkillEffect(SkillId.이뮨_투함)) {
					dmg *= Config.IMMNUE_TO_HARM_RATE;
				}
			}
			//	attacker.sendPackets(new S_ChatPacket((L1PcInstance) attacker,String.format("%s%s", "나의 마법검 데미지는 : " , dmg), 1));
			return dmg;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}
	/**
	 * 마법검 데미지의 속성 데미지 감소 처리를 한다.
	 * @param attr
	 * @return
	 */ 
	private static double calcAttrResistance(L1Character defender, int attr) {
		double dmg = 0;
		try {
			if(defender instanceof L1PcInstance) {
				L1PcInstance targetPc = (L1PcInstance) defender;		
				switch (attr) {
				case 1: dmg = targetPc.getResistance().getEarth(); break;
				case 2: dmg = targetPc.getResistance().getFire(); break;
				case 4: dmg = targetPc.getResistance().getWater(); break;
				case 8: dmg = targetPc.getResistance().getWind(); break;
				}
			}
			else if(defender instanceof L1NpcInstance) {
				L1NpcInstance targetNpc = (L1NpcInstance) defender;
				switch (attr) {
				case 1: dmg = targetNpc.getNpcTemplate().getWeakWind(); break;
				case 2: dmg = targetNpc.getNpcTemplate().getWeakEarth(); break;
				case 4: dmg = targetNpc.getNpcTemplate().getWeakFire(); break;
				case 8: dmg = targetNpc.getNpcTemplate().getWeakWater(); break;
				}			
			}	
			int resistFloor = (int) (0.32 * Math.abs(dmg));
			if (dmg >= 0) {
				resistFloor *= 1;
			} else {
				resistFloor *= -1;
			}
			double attrDeffence = resistFloor / 32.0;
			return attrDeffence;

		} catch(Exception e) {
			return 0;
		}
	}
}
