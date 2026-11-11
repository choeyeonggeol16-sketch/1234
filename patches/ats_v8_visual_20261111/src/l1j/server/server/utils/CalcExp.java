/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */

package l1j.server.server.utils;

import java.util.ArrayList;
import java.util.logging.Logger;

import l1j.server.Templates.L1Pet;
import l1j.server.configure.Config;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.item.MagicDoll;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_WorldObject;

public class CalcExp {
 
	private static Logger _log = Logger.getLogger(CalcExp.class.getName());
 
	private CalcExp() {
	}

	public static void calcExp(L1PcInstance l1pcinstance, int targetid, ArrayList<?> acquisitorList,
			ArrayList<?> hateList, int exp) {

		int i = 0;
		double party_level = 0;
		double dist = 0;
		int member_exp = 0;
		int member_lawful = 0;
		L1Object l1object = L1World.getInstance().findObject(targetid);
		L1NpcInstance npc = (L1NpcInstance) l1object;
		
		// 헤이트의 합계를 취득
		L1Character acquisitor;
		int hate = 0;
		int acquire_exp = 0;
		int acquire_lawful = 0;
		int party_exp = 0;
		int party_lawful = 0;
		int totalHateExp = 0;
		int totalHateLawful = 0;
		int partyHateExp = 0;
		int partyHateLawful = 0;
		int ownHateExp = 0;

		if (acquisitorList.size() != hateList.size()) {
			return;
		}
		for (i = hateList.size() - 1; i >= 0; i--) {
			acquisitor = (L1Character) acquisitorList.get(i);
			hate = (Integer) hateList.get(i);
			if (acquisitor != null && acquisitor.getMapId() == npc.getMapId() && !acquisitor.isDead()) {
				totalHateExp += hate;
				if (acquisitor instanceof L1PcInstance) {
					totalHateLawful += hate;
				}
			} else { // null였거나 죽어 있으면(자) 배제
				acquisitorList.remove(i);
				hateList.remove(i);
			}
		}
		if (totalHateExp == 0) { // 취득자가 없는 경우
			return;
		}
		if (l1object != null && !(npc instanceof PetInstance) && !(npc instanceof SummonInstance)) {
			// int exp = npc.get_exp();
			if (!L1World.getInstance().isProcessingContributionTotal() && l1pcinstance.getHomeTownId() > 0) {
				int contribution = npc.getLevel() / 10;
				l1pcinstance.addContribution(contribution);
			}
			int lawful = npc.getLawful();

			if (l1pcinstance.isInParty()) { // 파티중
				// 파티의 헤이트의 합계를 산출
				// 파티 멤버 이외에는 그대로 배분
				partyHateExp = 0;
				partyHateLawful = 0;
				for (i = hateList.size() - 1; i >= 0; i--) {
					acquisitor = (L1Character) acquisitorList.get(i);
					hate = (Integer) hateList.get(i);
					if (acquisitor instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) acquisitor;
						if (pc == l1pcinstance) {
							partyHateExp += hate;
							partyHateLawful += hate;
						} else if (l1pcinstance.getParty().isMember(pc)) {
							partyHateExp += hate;
							partyHateLawful += hate;
						} else {
							if (totalHateExp > 0) {
								acquire_exp = (exp * hate / totalHateExp);
							}
							if (totalHateLawful > 0) {
								acquire_lawful = (lawful * hate / totalHateLawful);
							}
							AddExp(pc, acquire_exp, acquire_lawful);
						}
					} else if (acquisitor instanceof PetInstance) {
						PetInstance pet = (PetInstance) acquisitor;
						L1PcInstance master = (L1PcInstance) pet.getMaster();
						if (master == l1pcinstance) {
							partyHateExp += hate;
						} else if (l1pcinstance.getParty().isMember(master)) {
							partyHateExp += hate;
						} else {
							if (totalHateExp > 0) {
								acquire_exp = (exp * hate / totalHateExp);
							}
							AddExpPet(pet, acquire_exp);
						}
					} else if (acquisitor instanceof SummonInstance) {
						SummonInstance summon = (SummonInstance) acquisitor;
						L1PcInstance master = (L1PcInstance) summon.getMaster();
						if (master == l1pcinstance) {
							partyHateExp += hate;
						} else if (l1pcinstance.getParty().isMember(master)) {
							partyHateExp += hate;
						} else {
						}
					}
				}
				if (totalHateExp > 0) {
					party_exp = (exp * partyHateExp / totalHateExp);
				}
				if (totalHateLawful > 0) {
					party_lawful = (lawful * partyHateLawful / totalHateLawful);
				}

				// EXP, 로우훌 배분

				// 프리보나스
				double pri_bonus = 0;
				L1PcInstance leader = l1pcinstance.getParty().getLeader();
				if (leader.isCrown() && (l1pcinstance.knownsObject(leader) || l1pcinstance.equals(leader))) {
					pri_bonus = 0.059;
				}

				// PT경험치의 계산
				L1PcInstance[] ptMembers = l1pcinstance.getParty().getMembers();
				double pt_bonus = 0;
				for (L1PcInstance each : l1pcinstance.getParty().getMembers()) {
					if (l1pcinstance.knownsObject(each) || l1pcinstance.equals(each)) {
						party_level += each.getLevel() * each.getLevel();
					}
					if (l1pcinstance.knownsObject(each)) {
						pt_bonus += 0.04;
					}
				}

				party_exp = (int) (party_exp * (1 + pt_bonus + pri_bonus));

				// 자캐릭터와 그 애완동물·사몬의 헤이트의 합계를 산출
				if (party_level > 0) {
					dist = ((l1pcinstance.getLevel() * l1pcinstance.getLevel()) / party_level);
				}
				member_exp = (int) (party_exp * dist);
				member_lawful = (int) (party_lawful * dist);

				ownHateExp = 0;
				for (i = hateList.size() - 1; i >= 0; i--) {
					acquisitor = (L1Character) acquisitorList.get(i);
					hate = (Integer) hateList.get(i);
					if (acquisitor instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) acquisitor;
						if (pc == l1pcinstance) {
							ownHateExp += hate;
						}
					} else if (acquisitor instanceof PetInstance) {
						PetInstance pet = (PetInstance) acquisitor;
						L1PcInstance master = (L1PcInstance) pet.getMaster();
						if (master == l1pcinstance) {
							ownHateExp += hate;
						}
					} else if (acquisitor instanceof SummonInstance) {
						SummonInstance summon = (SummonInstance) acquisitor;
						L1PcInstance master = (L1PcInstance) summon.getMaster();
						if (master == l1pcinstance) {
							ownHateExp += hate;
						}
					}
				}
				// 자캐릭터와 그 애완동물·사몬에 분배
				if (ownHateExp != 0) { // 공격에 참가하고 있었다
					for (i = hateList.size() - 1; i >= 0; i--) {
						acquisitor = (L1Character) acquisitorList.get(i);
						hate = (Integer) hateList.get(i);
						if (acquisitor instanceof L1PcInstance) {
							L1PcInstance pc = (L1PcInstance) acquisitor;
							if (pc == l1pcinstance) {
								if (ownHateExp > 0) {
									acquire_exp = (member_exp * hate / ownHateExp);
								}

								if (pc.getMapId() == 111) { // 정상
									int newExp = (exp * partyHateExp / totalHateExp);
									AddExp(pc, newExp, member_lawful);
								} else {
									AddExp(pc, acquire_exp, member_lawful);
								}
							}
						} else if (acquisitor instanceof PetInstance) {
							PetInstance pet = (PetInstance) acquisitor;
							L1PcInstance master = (L1PcInstance) pet.getMaster();
							if (master == l1pcinstance) {
								if (ownHateExp > 0) {
									acquire_exp = (member_exp * hate / ownHateExp);
								}
								AddExpPet(pet, acquire_exp);
							}
						} else if (acquisitor instanceof SummonInstance) {
						}
					}
				} else { // 공격에 참가하고 있지 않았다
					// 자캐릭터에만 분배
					if (l1pcinstance.getMapId() == 111) { // 정상
						int newExp = (exp * partyHateExp / totalHateExp);
						AddExp(l1pcinstance, newExp, member_lawful);
					} else {
						AddExp(l1pcinstance, member_exp, member_lawful);
					}
				}

				// 파티 멤버와 그 애완동물·사몬의 헤이트의 합계를 산출
				for (int cnt = 0; cnt < ptMembers.length; cnt++) {
					if (l1pcinstance.knownsObject(ptMembers[cnt])) {
						if (party_level > 0) {
							dist = ((ptMembers[cnt].getLevel() * ptMembers[cnt].getLevel()) / party_level);
						}
						member_exp = (int) (party_exp * dist);
						member_lawful = (int) (party_lawful * dist);

						ownHateExp = 0;
						for (i = hateList.size() - 1; i >= 0; i--) {
							acquisitor = (L1Character) acquisitorList.get(i);
							hate = (Integer) hateList.get(i);
							if (acquisitor instanceof L1PcInstance) {
								L1PcInstance pc = (L1PcInstance) acquisitor;
								if (pc == ptMembers[cnt]) {
									ownHateExp += hate;
								}
							} else if (acquisitor instanceof PetInstance) {
								PetInstance pet = (PetInstance) acquisitor;
								L1PcInstance master = (L1PcInstance) pet.getMaster();
								if (master == ptMembers[cnt]) {
									ownHateExp += hate;
								}
							} else if (acquisitor instanceof SummonInstance) {
								SummonInstance summon = (SummonInstance) acquisitor;
								L1PcInstance master = (L1PcInstance) summon.getMaster();
								if (master == ptMembers[cnt]) {
									ownHateExp += hate;
								}
							}
						}
						// 파티 멤버와 그 애완동물·사몬에 분배
						if (ownHateExp != 0) { // 공격에 참가하고 있었다
							for (i = hateList.size() - 1; i >= 0; i--) {
								acquisitor = (L1Character) acquisitorList.get(i);
								hate = (Integer) hateList.get(i);
								if (acquisitor instanceof L1PcInstance) {
									L1PcInstance pc = (L1PcInstance) acquisitor;
									if (pc == ptMembers[cnt]) {
										if (ownHateExp > 0) {
											acquire_exp = (member_exp * hate / ownHateExp);
										}
										if (pc.getMapId() == 111) { // 정상
											int newExp = (exp * partyHateExp / totalHateExp);
											AddExp(pc, newExp, member_lawful);
										} else {
											AddExp(pc, acquire_exp, member_lawful);
										}
									}
								} else if (acquisitor instanceof PetInstance) {
									PetInstance pet = (PetInstance) acquisitor;
									L1PcInstance master = (L1PcInstance) pet.getMaster();
									if (master == ptMembers[cnt]) {
										if (ownHateExp > 0) {
											acquire_exp = (member_exp * hate / ownHateExp);
										}
										AddExpPet(pet, acquire_exp);
									}
								} else if (acquisitor instanceof SummonInstance) {
								}
							}
						} else { // 공격에 참가하고 있지 않았다
							// 파티 멤버에만 분배
							if (ptMembers[cnt].getMapId() == 111) { // 정상
								int newExp = (exp * partyHateExp / totalHateExp);
								AddExp(ptMembers[cnt], newExp, member_lawful);
							} else {
								AddExp(ptMembers[cnt], member_exp, member_lawful);
							}
						}
					}
				}
			} else { // 파티를 짜지 않았다
				// EXP, 로우훌의 분배
				for (i = hateList.size() - 1; i >= 0; i--) {
					acquisitor = (L1Character) acquisitorList.get(i);
					hate = (Integer) hateList.get(i);
					acquire_exp = (exp * hate / totalHateExp);//
					if (acquisitor instanceof L1PcInstance) {
						if (totalHateLawful > 0) {
							acquire_lawful = (lawful * hate / totalHateLawful);
						}
					}

					if (acquisitor instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) acquisitor;
						AddExp(pc, acquire_exp, acquire_lawful);
					} else if (acquisitor instanceof PetInstance) {
						PetInstance pet = (PetInstance) acquisitor;
						AddExpPet(pet, acquire_exp);
					} else if (acquisitor instanceof SummonInstance) {
					}
				}
			}
		}
	}


	private static void AddExp(L1PcInstance pc, int exp, int lawful) {
		if (pc == null || pc.isGhost()) {
			return;
		}
		if (pc.getLevel() >= Config.LIMITLEVEL) {// 경험치
			return;
		}
		
		int add_lawful = (int) (lawful * Config.RATE_LA) * -1;
		pc.addLawful(add_lawful);
			
		double exppenalty = ExpTable.getPenaltyRate(pc.getLevel()); 
		double einhasadBonus = calcExpBonusByAin(pc, exp);
		double dollBonus = MagicDoll.getExpBonus(pc);
		double itemBonus = calcExpBonusByItem(pc);
		double buffBonus = calcExpBonusByBuff(pc);

		int add_exp = (int)(exp * exppenalty * Config.RATE_XP * buffBonus * einhasadBonus * dollBonus * itemBonus);
		// 폭렙 방지 소스  	
		if (pc.getLevel() >= 1) {
			if ((add_exp + pc.getExp()) > ExpTable.getExpByLevel((pc.getLevel() + 1))) {
				add_exp = (ExpTable.getExpByLevel((pc.getLevel() + 1)) - pc.getExp());
			} 
		}  
		pc.addExp(add_exp);    
	}
	
	public static double calcExpBonusByItem(L1PcInstance pc) {
		double expBonus = 1.0D;
  
		if (pc.getExpEmblem() > 0) {
			expBonus += 0.01 * pc.getExpEmblem();
		}
		return expBonus;
	}
	
	public static double calcExpBonusByBuff(L1PcInstance pc) {
		double expBonus = 1.0D;
 
		if ((pc.getMapId() >= 1708 && pc.getMapId() <= 1712) && pc.isInParty()) {
			expBonus += 0.3;
		}
		if (pc.hasSkillEffect(SkillId.천상_물약)) {
			expBonus += 0.20;
		}
		// 코마 축복 코인 경험치 추가
		if(pc.hasSkillEffect(SkillId.코마_축복_코인_8)) {
			expBonus += 0.20;
		}

		return expBonus;
	}

	private static void AddExpPet(PetInstance pet, int exp) {
		L1PcInstance pc = (L1PcInstance) pet.getMaster();

		int petItemObjId = pet.getItemObjId();

		int levelBefore = pet.getLevel();
		int totalExp = (int) (exp * Config.RATE_PET_XP + pet.getExp()); 
		if (totalExp >= ExpTable.getExpByLevel(51)) {
			totalExp = ExpTable.getExpByLevel(51) - 1;
		}
		pet.setExp(totalExp);

		pet.setLevel(ExpTable.getLevelByExp(totalExp));

		int expPercentage = ExpTable.getExpPercentage(pet.getLevel(), totalExp);

		int gap = pet.getLevel() - levelBefore;
		for (int i = 1; i <= gap; i++) {
			IntRange hpUpRange = pet.getPetType().getHpUpRange();
			IntRange mpUpRange = pet.getPetType().getMpUpRange();
			pet.addMaxHp(hpUpRange.randomValue());
			pet.addMaxMp(mpUpRange.randomValue());
		}
		pet.setExpPercent(expPercentage);
		pc.sendPackets(S_WorldObject.pet(pet, pc));

		if (gap != 0) { // 레벨업하면(자) DB에 기입한다
			L1Pet petTemplate = PetTable.getInstance().getTemplate(petItemObjId);
			if (petTemplate == null) { // PetTable에 없다
				_log.warning("L1Pet == null");
				return;
			}
			petTemplate.set_exp(pet.getExp());
			petTemplate.set_level(pet.getLevel());
			petTemplate.set_hp(pet.getMaxHp());
			petTemplate.set_mp(pet.getMaxMp());
			PetTable.getInstance().storePet(petTemplate); // DB에 기입해
			pc.sendPackets(new S_ServerMessage(320, pet.getName())); // \f1%0의 레벨이 올랐습니다.
		}
	}
	
	
	public static double calcExpBonusByAin(L1PcInstance pc, int exp) {

		if ((pc == null) || (pc.getAccount() == null)) {
			return 1.0D;
		}	
		double expBonus = 1.0D;
		int blessOfAin = pc.getAccount().getBlessOfAin();
		if (blessOfAin > 0) {
			int consumeAin = exp;
			if (blessOfAin <= 2000000) {
				expBonus += 1.5; 
			}
			pc.getAccount().addBlessOfAin(pc, -consumeAin -Config.AINBLESS);
			pc.sendPackets(new S_PacketBox(S_PacketBox.EINHASAD, pc.getAccount().getBlessOfAin()));
		} 
		return expBonus;
	}
}