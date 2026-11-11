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
package l1j.server.server.model.Instance;

import l1j.server.BugRace.BugRaceController;
import l1j.server.DogFightSystem.DogFightController;
import l1j.server.DogFightSystem.DogFightSystemSettings;
import l1j.server.Templates.L1EventNpcSpawn;
import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1Npc;
import l1j.server.server.Controller.EventNpcSpawnCotroller;
import l1j.server.server.datatables.BuffNpcListTable;
import l1j.server.server.datatables.BuffNpcListTable.BuffNpc;
import l1j.server.server.datatables.EventNpcSpawnTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.datatables.TownTable;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.gametime.GameTimeClock;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_NPCTalkReturn;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.CommonUtil;

public class L1MerchantInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;

	/**
	 * @param template
	 */
	public L1MerchantInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance player) {
		Attack attack = new Attack(player, this);
		attack.calcHit();
		attack.action();
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(getNpcTemplate().get_npcId());
		int npcid = getNpcTemplate().get_npcId();
		L1Quest quest = player.getQuest();
		String htmlid = null;
		String[] htmldata = null;
  	
		BuffNpc bn = BuffNpcListTable.getInstance().getBuffNpc(npcid);
		if (bn != null) {
			
			if (bn._Level >= player.getLevel()) {
				player.sendPackets(new S_SystemMessage("레벨 (" + bn._Level + ") 이상 버프를 받을수 있습니다."));
				return;
			}
			
			String[] skill_ids = bn._skill_ids.split(",");
			if (bn._itemid != 0) { 
				
				if (!player.getInventory().consumeItem(bn._itemid, bn._itemcount)) {
					L1Item it = ItemTable.getInstance().getTemplate(bn._itemid);
					if (it != null) {
						player.sendPackets(new S_SystemMessage(it.getName() + "(" + bn._itemcount + ") (이)가 부족합니다."));
						return;
					} else {
						player.sendPackets(new S_SystemMessage("잘못된 아이템 지정입니다. 운영자에게 문의하세요."));
						return;
					}
				}
			}

			//player.setBuffnoch(1);
			SkillUse skilluse = new SkillUse();
			for (int i = 0; i < skill_ids.length; i++) {
				skilluse.handleCommands(player, Integer.valueOf(skill_ids[i]), player.getId(), player.getX(),
						player.getY(), null, 0, SkillUse.TYPE_GMBUFF);
			}
			//player.setBuffnoch(0);
			return;
		}
	
		
		L1EventNpcSpawn event_npc = EventNpcSpawnTable.getInstance().getEventSpawn(npcid);
	    if (event_npc != null) { 
	      if ((event_npc.getJoinMaxLevel() != 0) && (player.getLevel() > event_npc.getJoinMaxLevel()))
	      {
	        player.sendPackets(new S_SystemMessage("\\fY[!] : 레벨 " + event_npc.getJoinMaxLevel() + " 이하만 입장할 수 있습니다."));
	        return;
	      }  
			if (!EventNpcSpawnCotroller.isSpawn(event_npc)) {
				player.sendPackets(new S_ChatPacket(player, String.format("%s", "[사냥터 시간이 아닙니다]"), 1));
				return;
			}
	      
			if (player.getLevel() >= event_npc.getJoinLevel()) {
				int x = 0;
				int y = 0;
				int mapid = 0;
				if (event_npc.getJoinLoc().length > 1) {
					int rndLoc = CommonUtil.random(event_npc.getJoinLoc().length);
					String[] loc = event_npc.getJoinLoc()[rndLoc].split(",");
					x = Integer.valueOf(loc[0]).intValue();
					y = Integer.valueOf(loc[1]).intValue();
					mapid = Integer.valueOf(loc[2]).intValue();
				} else {
					String[] loc = event_npc.getJoinLoc()[0].split(",");
					x = Integer.valueOf(loc[0]).intValue();
					y = Integer.valueOf(loc[1]).intValue();
					mapid = Integer.valueOf(loc[2]).intValue();
				}
				if ((x == 0) || (y == 0)) {
					player.sendPackets(new S_SystemMessage("\\fY[!] : 입장좌표설정이 잘못되었습니다."));
					return;
				} else { 
					player.teleport(x, y, (short) mapid, player.getHeading(), 169, false, false);
				}
			} else {
				player.sendPackets(new S_SystemMessage("\\fY[!] : 레벨 " + event_npc.getJoinLevel() + " 이상만 입장할 수 있습니다."));
			}
			return;
		}

		if (talking != null) {
			if (npcid == 70841) { // 루 디 엘
				if (player.isElf()) { // 에르프
					htmlid = "luudielE1";
				} else if (player.isDarkelf()) { // 다크 에르프
					htmlid = "luudielCE1";
				} else {
					htmlid = "luudiel1";
				}
			} else if (npcid == 70577) { 
				player.sendPackets(new S_SystemMessage("아지트는 5분후 경매 입찰이 완료됩니다. ")); 
					//추가작업			
			} else if (npcid == 4204000) { 	// 요정 달장퀘스트 로빈후드
				if(!player.isElf()){ 
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 0) {
					htmlid = "robinhood1";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 1) {
					htmlid = "robinhood8";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 2) {
					htmlid = "robinhood13";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 6) {
					htmlid = "robinhood9";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 7) {
					htmlid = "robinhood11";
				}else {
					htmlid = "robinhood3";
				}		
			} else if (npcid == 4210000) { //지브릴
				if (!player.isElf()) {
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 2) {
					htmlid = "zybril1";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 3) {
					htmlid = "zybril7";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 4) {
					htmlid = "zybril8";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 5) {
					htmlid = "zybril18";
				}else {
					htmlid = "zybril16";
				}
				//추가작업		
			} else if (npcid == 70522) { // 군타
				if (player.isCrown()) { // 군주
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step == 2 || lv15_step == L1Quest.QUEST_END) { // 클리어가 끝난 상태
							htmlid = "gunterp11";
						} else {
							htmlid = "gunterp9";
						}
					} else { // Lv15 미만
						htmlid = "gunterp12";
					}
				} else if (player.isKnight()) { // 나이트
					int lv30_step = quest.get_step(L1Quest.QUEST_LEVEL30);
					if (lv30_step == 0) { // 미개시
						htmlid = "gunterk9";
					} else if (lv30_step == 1) {
						htmlid = "gunterkE1";
					} else if (lv30_step == 2) { // 군타 동의가 끝난 상태
						htmlid = "gunterkE2";
					} else if (lv30_step >= 3) { // 군타 종료가 끝난 상태
						htmlid = "gunterkE3";
					}
				} else if (player.isElf()) { // 에르프
					htmlid = "guntere1";
				} else if (player.isWizard()) { // 위저드
					htmlid = "gunterw1";
				} else if (player.isDarkelf()) { // 다크 에르프
					htmlid = "gunterde1";
				}
			} else if (npcid == 70653) { // 마샤
				if (player.isCrown()) { // 군주
					if (player.getLevel() >= 45) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL30)) { // lv30 클리어가 끝난 상태
							int lv45_step = quest
									.get_step(L1Quest.QUEST_LEVEL45);
							if (lv45_step == L1Quest.QUEST_END) { // 클리어가 끝난 상태
								htmlid = "masha4";
							} else if (lv45_step >= 1) { // 동의가 끝난 상태
								htmlid = "masha3";
							} else { // 미동의
								htmlid = "masha1";
							}
						}
					}
				} else if (player.isKnight()) { // 나이트
					if (player.getLevel() >= 45) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL30)) { // Lv30 퀘스트 종료가 끝난 상태
							int lv45_step = quest
									.get_step(L1Quest.QUEST_LEVEL45);
							if (lv45_step == L1Quest.QUEST_END) { // 클리어가 끝난 상태
								htmlid = "mashak3";
							} else if (lv45_step == 0) { // 미개시
								htmlid = "mashak1";
							} else if (lv45_step >= 1) { // 동의가 끝난 상태
								htmlid = "mashak2";
							}
						}
					}
				} else if (player.isElf()) { // 에르프
					if (player.getLevel() >= 45) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL30)) { // Lv30 퀘스트 종료가 끝난 상태
							int lv45_step = quest
									.get_step(L1Quest.QUEST_LEVEL45);
							if (lv45_step == L1Quest.QUEST_END) { // 클리어가 끝난 상태
								htmlid = "mashae3";
							} else if (lv45_step >= 1) { // 동의가 끝난 상태
								htmlid = "mashae2";
							} else { // 미동의
								htmlid = "mashae1";
							}
						}
					}
				}
			} else if (npcid == 70554) { // 제로
				if (player.isCrown()) { // 군주
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step == 1) { // 제로 클리어가 끝난 상태
							htmlid = "zero5";
						} else if (lv15_step == L1Quest.QUEST_END) { // 제로, 군타크리아가 끝난 상태
							htmlid = "zero6";
						} else {
							htmlid = "zero1";
						}
					} else { // Lv15 미만
						htmlid = "zero6";
					}
				}
			} else if (npcid == 70783) { // 아리아
				if (player.isCrown()) { // 군주
					if (player.getLevel() >= 30) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) { // lv15 시련 클리어가 끝난 상태
							int lv30_step = quest
									.get_step(L1Quest.QUEST_LEVEL30);
							if (lv30_step == L1Quest.QUEST_END) { // 클리어가 끝난 상태
								htmlid = "aria3";
							} else if (lv30_step == 1) { // 동의가 끝난 상태
								htmlid = "aria2";
							} else { // 미동의
								htmlid = "aria1";
							}
						}
					}
				}
			} else if (npcid == 70782) { // 서치안트
				if (player.getCurrentSpriteId() == 1037) {// 쟈이안트안트 변신
					if (player.isCrown()) { // 군주
						if (quest.get_step(L1Quest.QUEST_LEVEL30) == 1) {
							htmlid = "ant1";
						} else {
							htmlid = "ant3";
						}
					} else { // 군주 이외
						htmlid = "ant3";
					}
				}
			} else if (npcid == 70545) { // 리처드
				if (player.isCrown()) { // 군주
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step >= 1 && lv45_step != L1Quest.QUEST_END) { // 개시 또한 미종료
						if (player.getInventory().checkItem(40586)) { // 왕가의 문장(왼쪽)
							htmlid = "richard4";
						} else {
							htmlid = "richard1";
						}
					}
				}
			} else if (npcid == 70776) { // 메그
				if (player.isCrown()) { // 군주
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step == 1) {
						htmlid = "meg1";
					} else if (lv45_step == 2) { // 메그 동의가 끝난 상태
						htmlid = "meg2";
					} else if (lv45_step >= 3) { // 메그크리아가 끝난 상태
						htmlid = "meg3";
					}
				}
			} else if (npcid == 70751) { // 블래드
				if (player.isCrown()) { // 군주
					if (player.getLevel() >= 45) {
						if (quest.get_step(L1Quest.QUEST_LEVEL45) == 2) { // 메그 동의가 끝난 상태
							htmlid = "brad1";
						}
					}
				}
			} else if (npcid == 70798) { // 릭키
				if (player.isKnight()) { // 나이트
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step >= 1) { // 릭키크리아가 끝난 상태
							htmlid = "riky5";
						} else {
							htmlid = "riky1";
						}
					} else { // Lv15 미만
						htmlid = "riky6";
					}
				}
			} else if (npcid == 70802) { // 아논
				if (player.isKnight()) { // 나이트
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step == L1Quest.QUEST_END) { // 아논크리아가 끝난 상태
							htmlid = "aanon7";
						} else if (lv15_step == 1) { // 릭키크리아가 끝난 상태
							htmlid = "aanon4";
						}
					}
				}
			} else if (npcid == 70775) { // 마크
				if (player.isKnight()) { // 나이트
					if (player.getLevel() >= 30) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) { // LV15 퀘스트 종료가 끝난 상태
							int lv30_step = quest
									.get_step(L1Quest.QUEST_LEVEL30);
							if (lv30_step == 0) { // 미개시
								htmlid = "mark1";
							} else {
								htmlid = "mark2";
							}
						}
					}
				}
			} else if (npcid == 70794) { // 게라드
				if (player.isCrown()) { // 군주
					htmlid = "gerardp1";
				} else if (player.isKnight()) { // 나이트
					int lv30_step = quest.get_step(L1Quest.QUEST_LEVEL30);
					if (lv30_step == L1Quest.QUEST_END) { // 게라드 종료가 끝난 상태
						htmlid = "gerardkE5";
					} else if (lv30_step < 3) { // 군타미종료
						htmlid = "gerardk7";
					} else if (lv30_step == 3) { // 군타 종료가 끝난 상태
						htmlid = "gerardkE1";
					} else if (lv30_step == 4) { // 게라드 동의가 끝난 상태
						htmlid = "gerardkE2";
					} else if (lv30_step == 5) { // 라미아의 비늘 종료가 끝난 상태
						htmlid = "gerardkE3";
					} else if (lv30_step >= 6) { // restore의 일부 동의가 끝난 상태
						htmlid = "gerardkE4";
					}
				} else if (player.isElf()) { // 에르프
					htmlid = "gerarde1";
				} else if (player.isWizard()) { // 위저드
					htmlid = "gerardw1";
				} else if (player.isDarkelf()) { // 다크 에르프
					htmlid = "gerardde1";
				}
			} else if (npcid == 70555) { // 짐
				if (player.getCurrentSpriteId() == 2374) { // 스켈리턴 변신
					if (player.isKnight()) { // 나이트
						if (quest.get_step(L1Quest.QUEST_LEVEL30) == 6) { // restore의 일부 동의가 끝난 상태
							htmlid = "jim2";
						} else {
							htmlid = "jim4";
						}
					} else { // 나이트 이외
						htmlid = "jim4";
					}
				}
			} else if (npcid == 70715) { // 짐
				if (player.isKnight()) { // 나이트
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step == 1) { // 마샤 동의가 끝난 상태
						htmlid = "jimuk1";
					} else if (lv45_step >= 2) { // 짐 동의가 끝난 상태
						htmlid = "jimuk2";
					}
				}
			} else if (npcid == 70711) { // 쟈이안트에르다
				if (player.isKnight()) { // 나이트
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step == 2) { // 짐 동의가 끝난 상태
						if (player.getInventory().checkItem(20026)) { // 나이트 비전
							htmlid = "giantk1";
						}
					} else if (lv45_step == 3) { // 쟈이안트에르다 동의가 끝난 상태
						htmlid = "giantk2";
					} else if (lv45_step >= 4) { // 고대의 키：상반분
						htmlid = "giantk3";
					}
				}
			} else if (npcid == 70826) { // 수컷
				if (player.isElf()) { // 에르프
					if (player.getLevel() >= 15) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) {
							htmlid = "oth5";
						} else {
							htmlid = "oth1";
						}
					} else { // 레벨 15 미만
						htmlid = "oth6";
					}
				}
			} else if (npcid == 70844) { // 숲과 에르프의 어머니
				if (player.isElf()) { // 에르프
					if (player.getLevel() >= 30) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) { // Lv15 종료가 끝난 상태
							int lv30_step = quest
									.get_step(L1Quest.QUEST_LEVEL30);
							if (lv30_step == L1Quest.QUEST_END) { // 종료가 끝난 상태
								htmlid = "motherEE3";
							} else if (lv30_step >= 1) { // 동의가 끝난 상태
								htmlid = "motherEE2";
							} else if (lv30_step <= 0) { // 미동의
								htmlid = "motherEE1";
							}
						} else { // Lv15미종료
							htmlid = "mothere1";
						}
					} else { // Lv30 미만
						htmlid = "mothere1";
					}
				}
			} else if (npcid == 70724) { // 헤이트
				if (player.isElf()) { // 에르프
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step >= 4) { // 헤이트 종료가 끝난 상태
						htmlid = "heit5";
					} else if (lv45_step >= 3) { // 플룻 교환이 끝난 상태
						htmlid = "heit3";
					} else if (lv45_step >= 2) { // 헤이트 동의가 끝난 상태
						htmlid = "heit2";
					} else if (lv45_step >= 1) { // 마샤 동의가 끝난 상태
						htmlid = "heit1";
					}
				}
			} else if (npcid == 70531) { // 젬
				if (player.isWizard()) { // 위저드
					if (player.getLevel() >= 15) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) { // 종료가 끝난 상태
							htmlid = "jem6";
						} else {
							htmlid = "jem1";
						}
					}
				}
			} else if (npcid == 70009) { // 게렌
				htmlid = "gerengT4"; 
//				if (player.isCrown()) { // 군주
//					htmlid = "gerengp1";
//				} else if (player.isKnight()) { // 나이트
//					htmlid = "gerengT4"; 
//				} else if (player.isElf()) { // 에르프
//					htmlid = "gerenge1";
//				} else if (player.isWizard()) { // 위저드
//					if (player.getLevel() >= 30) {
//						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) {
//							int lv30_step = quest
//									.get_step(L1Quest.QUEST_LEVEL30);
//							if (lv30_step >= 4) { // 게렌 종료가 끝난 상태
//								htmlid = "gerengw3";
//							} else if (lv30_step >= 3) { // 요구가 끝난 상태
//								htmlid = "gerengT4";
//							} else if (lv30_step >= 2) { // 안 데드의 뼈교환이 끝난 상태
//								htmlid = "gerengT3";
//							} else if (lv30_step >= 1) { // 동의가 끝난 상태
//								htmlid = "gerengT2";
//							} else { // 미동의
//								htmlid = "gerengT1";
//							}
//						} else { // Lv15 퀘스트미종료
//							htmlid = "gerengw3";
//						}
//					} else { // Lv30 미만
//						htmlid = "gerengw3";
//					}
//				} else if (player.isDarkelf()) { // 다크 에르프
//					htmlid = "gerengde1";
//				}
			} else if (npcid == 70763) { // 타라스
				if (player.isWizard()) { // 위저드
					int lv30_step = quest.get_step(L1Quest.QUEST_LEVEL30);
					if (lv30_step == L1Quest.QUEST_END) {
						if (player.getLevel() >= 45) {
							int lv45_step = quest
									.get_step(L1Quest.QUEST_LEVEL45);
							if (lv45_step >= 1
									&& lv45_step != L1Quest.QUEST_END) { // 동의가 끝난 상태
								htmlid = "talassmq2";
							} else if (lv45_step <= 0) { // 미동의
								htmlid = "talassmq1";
							}
						}
					} else if (lv30_step == 4) {
						htmlid = "talassE1";
					} else if (lv30_step == 5) {
						htmlid = "talassE2";
					}
				}
			} else if (npcid == 81105) { // 신비의 바위
				if (player.isWizard()) { // 위저드
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step >= 3) { // 신비의 바위 종료가 끝난 상태
						htmlid = "stoenm3";
					} else if (lv45_step >= 2) { // 신비의 바위 동의가 끝난 상태
						htmlid = "stoenm2";
					} else if (lv45_step >= 1) { // 타라스 동의가 끝난 상태
						htmlid = "stoenm1";
					}
				}
			} else if (npcid == 70739) { // 디가르딘
				if (player.getLevel() >= 50) {
					int lv50_step = quest.get_step(L1Quest.QUEST_LEVEL50);
					if (lv50_step == L1Quest.QUEST_END) {
						if (player.isCrown()) { // 군주
							htmlid = "dicardingp3";
						} else if (player.isKnight()) { // 나이트
							htmlid = "dicardingk3";
						} else if (player.isElf()) { // 에르프
							htmlid = "dicardinge3";
						} else if (player.isWizard()) { // 위저드
							htmlid = "dicardingw3";
						} else if (player.isDarkelf()) { // 다크 에르프
							htmlid = "dicarding";
						}
					} else if (lv50_step >= 1) { // 디가르딘 동의가 끝난 상태
						if (player.isCrown()) { // 군주
							htmlid = "dicardingp2";
						} else if (player.isKnight()) { // 나이트
							htmlid = "dicardingk2";
						} else if (player.isElf()) { // 에르프
							htmlid = "dicardinge2";
						} else if (player.isWizard()) { // 위저드
							htmlid = "dicardingw2";
						} else if (player.isDarkelf()) { // 다크 에르프
							htmlid = "dicarding";
						}
					} else if (lv50_step >= 0) {
						if (player.isCrown()) { // 군주
							htmlid = "dicardingp1";
						} else if (player.isKnight()) { // 나이트
							htmlid = "dicardingk1";
						} else if (player.isElf()) { // 에르프
							htmlid = "dicardinge1";
						} else if (player.isWizard()) { // 위저드
							htmlid = "dicardingw1";
						} else if (player.isDarkelf()) { // 다크 에르프
							htmlid = "dicarding";
						}
					} else {
						htmlid = "dicarding";
					}
				} else { // Lv50 미만
					htmlid = "dicarding";
				}
			} else if (npcid == 70885) { // 칸
				if (player.isDarkelf()) { // 다크 에르프
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step == L1Quest.QUEST_END) { // 종료가 끝난 상태
							htmlid = "kanguard3";
						} else if (lv15_step >= 1) { // 동의가 끝난 상태
							htmlid = "kanguard2";
						} else { // 미동의
							htmlid = "kanguard1";
						}
					} else { // Lv15 미만
						htmlid = "kanguard5";
					}
				}
			} else if (npcid == 70892) { // 론두
				if (player.isDarkelf()) { // 다크 에르프
					if (player.getLevel() >= 30) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) {
							int lv30_step = quest
									.get_step(L1Quest.QUEST_LEVEL30);
							if (lv30_step == L1Quest.QUEST_END) { // 종료가 끝난 상태
								htmlid = "ronde5";
							} else if (lv30_step >= 2) { // 명부 교환이 끝난 상태
								htmlid = "ronde3";
							} else if (lv30_step >= 1) { // 동의가 끝난 상태
								htmlid = "ronde2";
							} else { // 미동의
								htmlid = "ronde1";
							}
						} else { // Lv15 퀘스트미종료
							htmlid = "ronde7";
						}
					} else { // Lv30 미만
						htmlid = "ronde7";
					}
				}
			} else if (npcid == 70895) { // 브르디카
				if (player.isDarkelf()) { // 다크 에르프
					if (player.getLevel() >= 45) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL30)) {
							int lv45_step = quest
									.get_step(L1Quest.QUEST_LEVEL45);
							if (lv45_step == L1Quest.QUEST_END) { // 종료가 끝난 상태
								if (player.getLevel() < 50) { // Lv50미만
									htmlid = "bluedikaq3";
								} else {
									int lv50_step = quest
											.get_step(L1Quest.QUEST_LEVEL50);
									if (lv50_step == L1Quest.QUEST_END) { // 종료가 끝난 상태
										htmlid = "bluedikaq8";
									} else {
										htmlid = "bluedikaq6";
									}
								}
							} else if (lv45_step >= 1) { // 동의가 끝난 상태
								htmlid = "bluedikaq2";
							} else { // 미동의
								htmlid = "bluedikaq1";
							}
						} else { // Lv30 퀘스트미종료
							htmlid = "bluedikaq5";
						}
					} else { // Lv45 미만
						htmlid = "bluedikaq5";
					}
				}
			} else if (npcid == 70904) { // 쿠프
				if (player.isDarkelf()) {
					if (quest.get_step(L1Quest.QUEST_LEVEL45) == 1) { // 브르디카 동의가 끝난 상태
						htmlid = "koup12";
					}
				}
			} else if (npcid == 70824) { // 아사신마스타의 추종자
				if (player.getCurrentSpriteId() == 3634) { // 아사신 변신
					if (player.isDarkelf()) {
						int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
						if (lv45_step == 1) {
							htmlid = "assassin1";
						} else if (lv45_step == 2) {
							htmlid = "assassin2";
						} else {
							htmlid = "assassin3";
						}
					} else { // 다크 에르프 이외
						htmlid = "assassin4";
					}
				}
			} else if (npcid == 70744) { // 로제
				if (player.isDarkelf()) { // 다크 에르프
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step >= 5) { // 로제 2번째 동의가 끝난 상태
						htmlid = "roje14";
					} else if (lv45_step >= 4) { // 설인의 머리 부분 교환이 끝난 상태
						htmlid = "roje13";
					} else if (lv45_step >= 3) { // 로제 동의가 끝난 상태
						htmlid = "roje12";
					} else if (lv45_step >= 2) { // 아사신마스타의 추종자 동의가 끝난 상태
						htmlid = "roje11";
					} else { // 아사신마스타의 추종자미동의
						htmlid = "roje15";
					}
				}
			} else if (npcid == 70811) { // 라이라
				if (quest.get_step(L1Quest.QUEST_LYRA) >= 1) { // 계약필
					htmlid = "lyraEv3";
				} else { // 미계약
					htmlid = "lyraEv1";
				}
			} else if (npcid == 70087) { // 세디아
				if (player.isDarkelf()) {
					htmlid = "sedia";
				}
			} else if (npcid == 70099) { // 쿠퍼
				if (!quest.isEnd(L1Quest.QUEST_OILSKINMANT)) {
					if (player.getLevel() > 13) {
						htmlid = "kuper1";
					}
				}
			} else if (npcid == 70796) { // 댄 햄
				if (!quest.isEnd(L1Quest.QUEST_OILSKINMANT)) {
					if (player.getLevel() > 13) {
						htmlid = "dunham1";
					}
				}
			} else if (npcid == 70011) { // 이야기할 수 있는 섬의 배 도착해 관리인
				int time = GameTimeClock.getInstance().getGameTime().getSeconds() % 86400;
				if (time < 60 * 60 * 6 || time > 60 * 60 * 20) { // 20:00?6:00
					htmlid = "shipEvI6";
				}
			} else if (npcid == 70553) { // 켄트성시종장 이스마엘
				boolean hascastle = checkHasCastle(player,
						L1CastleLocation.KENT_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					if (checkClanLeader(player)) { // 혈맹주
						htmlid = "ishmael1";
					} else {
						htmlid = "ishmael6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "ishmael7";
				}
			} else if (npcid == 70822) { // 오크의 숲세겜아트바
				boolean hascastle = checkHasCastle(player,
						L1CastleLocation.OT_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					if (checkClanLeader(player)) { // 혈맹주
						htmlid = "seghem1";
					} else {
						htmlid = "seghem6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "seghem7";
				}
			} else if (npcid == 70784) { // 윈다웃드성시종장 수컷 사교계
				boolean hascastle = checkHasCastle(player,
						L1CastleLocation.WW_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					if (checkClanLeader(player)) { // 혈맹주
						htmlid = "othmond1";
					} else {
						htmlid = "othmond6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "othmond7";
				}
			} else if (npcid == 70623) { // 기란성시종장 오 빌딩
				boolean hascastle = checkHasCastle(player,
						L1CastleLocation.GIRAN_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					if (checkClanLeader(player)) { // 혈맹주
						htmlid = "orville1";
					} else {
						htmlid = "orville6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "orville7";
				}
			} else if (npcid == 70880) { // Heine성시종장 피셔
				boolean hascastle = checkHasCastle(player,
						L1CastleLocation.HEINE_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					if (checkClanLeader(player)) { // 혈맹주
						htmlid = "fisher1";
					} else {
						htmlid = "fisher6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "fisher7";
				}
			} else if (npcid == 70665) { // 드워후성시종장 포텐핀
				boolean hascastle = checkHasCastle(player,
						L1CastleLocation.DOWA_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					if (checkClanLeader(player)) { // 혈맹주
						htmlid = "potempin1";
					} else {
						htmlid = "potempin6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "potempin7";
				}
			} else if (npcid == 70721) { // 에덴성시종장 티몬
				boolean hascastle = checkHasCastle(player,
						L1CastleLocation.ADEN_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					if (checkClanLeader(player)) { // 혈맹주
						htmlid = "timon1";
					} else {
						htmlid = "timon6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "timon7";
				}
			} else if (npcid == 81155) { // 디아드 요새 오레
				boolean hascastle = checkHasCastle(player,
						L1CastleLocation.DIAD_CASTLE_ID);
				if (hascastle) { // 성주 크란원
					if (checkClanLeader(player)) { // 혈맹주
						htmlid = "olle1";
					} else {
						htmlid = "olle6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "olle7";
				}
			} else if (npcid == 80057) { // 아르폰스
				switch (player.getKarmaLevel()) {
				case 0:
					htmlid = "alfons1";
					break;
				case -1:
					htmlid = "cyk1";
					break;
				case -2:
					htmlid = "cyk2";
					break;
				case -3:
					htmlid = "cyk3";
					break;
				case -4:
					htmlid = "cyk4";
					break;
				case -5:
					htmlid = "cyk5";
					break;
				case -6:
					htmlid = "cyk6";
					break;
				case -7:
					htmlid = "cyk7";
					break;
				case -8:
					htmlid = "cyk8";
					break;
				case 1:
					htmlid = "cbk1";
					break;
				case 2:
					htmlid = "cbk2";
					break;
				case 3:
					htmlid = "cbk3";
					break;
				case 4:
					htmlid = "cbk4";
					break;
				case 5:
					htmlid = "cbk5";
					break;
				case 6:
					htmlid = "cbk6";
					break;
				case 7:
					htmlid = "cbk7";
					break;
				case 8:
					htmlid = "cbk8";
					break;
				default:
					htmlid = "alfons1";
					break;
				}
			} else if (npcid == 80058) { // 차원의 문(사막)
				int level = player.getLevel();
				if (level <= 44) {
					htmlid = "cpass03";
				} else if (level <= 51 && 45 <= level) {
					htmlid = "cpass02";
				} else {
					htmlid = "cpass01";
				}
			} else if (npcid == 80048) { // 공간의 일그러짐
			    int level = player.getLevel();
			    if (level <= 44) {
			     htmlid = "entgate3";
				} else if (level >= 45 && level <= 51) {
			     htmlid = "entgate2";
			    } else {
			     htmlid = "entgate";
			    }
			} else if (npcid == 10000272) { // 말하는섬 던전 입장
			    int level = player.getLevel();
			    if (level <= 59) {
			     htmlid = "entgate3";
			    }
			 } else if (npcid == 80059) { // 차원의 문(토)
				if (player.getKarmaLevel() > 0) {
					htmlid = "cpass03";
				} else if (player.getInventory().checkItem(40921)) { // 원소의 지배자
					htmlid = "wpass02";
				} else if (player.getInventory().checkItem(40917)) { // 지의 지배자
					htmlid = "wpass14";
				} else if (player.getInventory().checkItem(40912) // 풍의 통행증
						|| player.getInventory().checkItem(40910) // 수의 통행증
						|| player.getInventory().checkItem(40911)) { // 불의 통행증
					htmlid = "wpass04";
				} else if (player.getInventory().checkItem(40909)) { // 지의 통행증
					int count = getNecessarySealCount(player);
					if (player.getInventory().checkItem(40913, count)) { // 지의 인장
						createRuler(player, 1, count);
						htmlid = "wpass06";
					} else {
						htmlid = "wpass03";
					}
				} else if (player.getInventory().checkItem(40913)) { // 지의 인장
					htmlid = "wpass08";
				} else {
					htmlid = "wpass05";
				}
			} else if (npcid == 80060) { // 차원의 문(바람)
				if (player.getKarmaLevel() > 0) {
					htmlid = "cpass03";
				} else if (player.getInventory().checkItem(40921)) { // 원소의 지배자
					htmlid = "wpass02";
				} else if (player.getInventory().checkItem(40920)) { // 풍의 지배자
					htmlid = "wpass13";
				} else if (player.getInventory().checkItem(40909) // 지의 통행증
						|| player.getInventory().checkItem(40910) // 수의 통행증
						|| player.getInventory().checkItem(40911)) { // 불의 통행증
					htmlid = "wpass04";
				} else if (player.getInventory().checkItem(40912)) { // 풍의 통행증
					int count = getNecessarySealCount(player);
					if (player.getInventory().checkItem(40916, count)) { // 풍의 인장
						createRuler(player, 8, count);
						htmlid = "wpass06";
					} else {
						htmlid = "wpass03";
					}
				} else if (player.getInventory().checkItem(40916)) { // 풍의 인장
					htmlid = "wpass08";
				} else {
					htmlid = "wpass05";
				}
			} else if (npcid == 80061) { // 차원의 문(수)
				if (player.getKarmaLevel() > 0) {
					htmlid = "cpass03";
				} else if (player.getInventory().checkItem(40921)) { // 원소의 지배자
					htmlid = "wpass02";
				} else if (player.getInventory().checkItem(40918)) { // 수의 지배자
					htmlid = "wpass11";
				} else if (player.getInventory().checkItem(40909) // 지의 통행증
						|| player.getInventory().checkItem(40912) // 풍의 통행증
						|| player.getInventory().checkItem(40911)) { // 불의 통행증
					htmlid = "wpass04";
				} else if (player.getInventory().checkItem(40910)) { // 수의 통행증
					int count = getNecessarySealCount(player);
					if (player.getInventory().checkItem(40914, count)) { // 수의 인장
						createRuler(player, 4, count);
						htmlid = "wpass06";
					} else {
						htmlid = "wpass03";
					}
				} else if (player.getInventory().checkItem(40914)) { // 수의 인장
					htmlid = "wpass08";
				} else {
					htmlid = "wpass05";
				}
			} else if (npcid == 80062) { // 차원의 문(화)
				if (player.getKarmaLevel() > 0) {
					htmlid = "cpass03";
				} else if (player.getInventory().checkItem(40921)) { // 원소의 지배자
					htmlid = "wpass02";
				} else if (player.getInventory().checkItem(40919)) { // 불의 지배자
					htmlid = "wpass12";
				} else if (player.getInventory().checkItem(40909) // 지의 통행증
						|| player.getInventory().checkItem(40912) // 풍의 통행증
						|| player.getInventory().checkItem(40910)) { // 수의 통행증
					htmlid = "wpass04";
				} else if (player.getInventory().checkItem(40911)) { // 불의 통행증
					int count = getNecessarySealCount(player);
					if (player.getInventory().checkItem(40915, count)) { // 불의 인장
						createRuler(player, 2, count);
						htmlid = "wpass06";
					} else {
						htmlid = "wpass03";
					}
				} else if (player.getInventory().checkItem(40915)) { // 불의 인장
					htmlid = "wpass08";
				} else {
					htmlid = "wpass05";
				}
			} else if (npcid == 80065) { // 바르로그의 밀정
				if (player.getKarmaLevel() < 3) {
					htmlid = "uturn0";
				} else {
					htmlid = "uturn1";
				}
			} else if (npcid == 80047) { // 야히의 하인
				if (player.getKarmaLevel() > -3) {
					htmlid = "uhelp1";
				} else {
					htmlid = "uhelp2";
				}
			} else if (npcid == 80049) { // 요동하는 사람
				if (player.getKarma() <= -10000000) {
					htmlid = "betray11";
				} else {
					htmlid = "betray12";
				}
			} else if (npcid == 80050) { // 야히의 집정관
				if (player.getKarmaLevel() > -1) {
					htmlid = "meet103";
				} else {
					htmlid = "meet101";
				}
			} else if (npcid == 80053) { // 야히의 대장간
				int karmaLevel = player.getKarmaLevel();
				if (karmaLevel == 0) {
					htmlid = "aliceyet";
				} else if (karmaLevel >= 1) {
					if (player.getInventory().checkItem(196)
							|| player.getInventory().checkItem(197)
							|| player.getInventory().checkItem(198)
							|| player.getInventory().checkItem(199)
							|| player.getInventory().checkItem(200)
							|| player.getInventory().checkItem(201)
							|| player.getInventory().checkItem(202)
							|| player.getInventory().checkItem(203)) {
						htmlid = "alice_gd";
					} else {
						htmlid = "gd";
					}
				} else if (karmaLevel <= -1) {
					if (player.getInventory().checkItem(40991)) {
						if (karmaLevel <= -1) {
							htmlid = "Mate_1";
						}
					} else if (player.getInventory().checkItem(196)) {
						if (karmaLevel <= -2) {
							htmlid = "Mate_2";
						} else {
							htmlid = "alice_1";
						}
					} else if (player.getInventory().checkItem(197)) {
						if (karmaLevel <= -3) {
							htmlid = "Mate_3";
						} else {
							htmlid = "alice_2";
						}
					} else if (player.getInventory().checkItem(198)) {
						if (karmaLevel <= -4) {
							htmlid = "Mate_4";
						} else {
							htmlid = "alice_3";
						}
					} else if (player.getInventory().checkItem(199)) {
						if (karmaLevel <= -5) {
							htmlid = "Mate_5";
						} else {
							htmlid = "alice_4";
						}
					} else if (player.getInventory().checkItem(200)) {
						if (karmaLevel <= -6) {
							htmlid = "Mate_6";
						} else {
							htmlid = "alice_5";
						}
					} else if (player.getInventory().checkItem(201)) {
						if (karmaLevel <= -7) {
							htmlid = "Mate_7";
						} else {
							htmlid = "alice_6";
						}
					} else if (player.getInventory().checkItem(202)) {
						if (karmaLevel <= -8) {
							htmlid = "Mate_8";
						} else {
							htmlid = "alice_7";
						}
					} else if (player.getInventory().checkItem(203)) {
						htmlid = "alice_8";
					} else {
						htmlid = "alice_no";
					}
				}
			} else if (npcid == 80055) { // 야히의 보좌관
				int amuletLevel = 0;
				if (player.getInventory().checkItem(20358)) { // 노예의 아뮤렛트
					amuletLevel = 1;
				} else if (player.getInventory().checkItem(20359)) { // 약속의 아뮤렛트
					amuletLevel = 2;
				} else if (player.getInventory().checkItem(20360)) { // 해방의 아뮤렛트
					amuletLevel = 3;
				} else if (player.getInventory().checkItem(20361)) { // 사냥개의 아뮤렛트
					amuletLevel = 4;
				} else if (player.getInventory().checkItem(20362)) { // 마족의 아뮤렛트
					amuletLevel = 5;
				} else if (player.getInventory().checkItem(20363)) { // 용사의 아뮤렛트
					amuletLevel = 6;
				} else if (player.getInventory().checkItem(20364)) { // 장군의 아뮤렛트
					amuletLevel = 7;
				} else if (player.getInventory().checkItem(20365)) { // 대장군의 아뮤렛트
					amuletLevel = 8;
				}
				if (player.getKarmaLevel() == -1) {
					if (amuletLevel >= 1) {
						htmlid = "uamuletd";
					} else {
						htmlid = "uamulet1";
					}
				} else if (player.getKarmaLevel() == -2) {
					if (amuletLevel >= 2) {
						htmlid = "uamuletd";
					} else {
						htmlid = "uamulet2";
					}
				} else if (player.getKarmaLevel() == -3) {
					if (amuletLevel >= 3) {
						htmlid = "uamuletd";
					} else {
						htmlid = "uamulet3";
					}
				} else if (player.getKarmaLevel() == -4) {
					if (amuletLevel >= 4) {
						htmlid = "uamuletd";
					} else {
						htmlid = "uamulet4";
					}
				} else if (player.getKarmaLevel() == -5) {
					if (amuletLevel >= 5) {
						htmlid = "uamuletd";
					} else {
						htmlid = "uamulet5";
					}
				} else if (player.getKarmaLevel() == -6) {
					if (amuletLevel >= 6) {
						htmlid = "uamuletd";
					} else {
						htmlid = "uamulet6";
					}
				} else if (player.getKarmaLevel() == -7) {
					if (amuletLevel >= 7) {
						htmlid = "uamuletd";
					} else {
						htmlid = "uamulet7";
					}
				} else if (player.getKarmaLevel() == -8) {
					if (amuletLevel >= 8) {
						htmlid = "uamuletd";
					} else {
						htmlid = "uamulet8";
					}
				} else {
					htmlid = "uamulet0";
				}
			} else if (npcid == 80056) { // 업의 관리자
				if (player.getKarma() <= -10000000) {
					htmlid = "infamous11";
				} else {
					htmlid = "infamous12";
				}
			} else if (npcid == 80064) { // 바르로그의 집정관
				if (player.getKarmaLevel() < 1) {
					htmlid = "meet003";
				} else {
					htmlid = "meet001";
				}
			} else if (npcid == 80066) { // 흔들거리는 사람
				if (player.getKarma() >= 10000000) {
					htmlid = "betray01";
				} else {
					htmlid = "betray02";
				}
			} else if (npcid == 80071) { // 바르로그의 보좌관
				int earringLevel = 0;
				if (player.getInventory().checkItem(21020)) { // 좋아서 뜀의 귀 링
					earringLevel = 1;
				} else if (player.getInventory().checkItem(21021)) { // 쌍둥이의 귀 링
					earringLevel = 2;
				} else if (player.getInventory().checkItem(21022)) { // 우호의 귀 링
					earringLevel = 3;
				} else if (player.getInventory().checkItem(21023)) { // 극지의 귀 링
					earringLevel = 4;
				} else if (player.getInventory().checkItem(21024)) { // 크래쉬의 귀 링
					earringLevel = 5;
				} else if (player.getInventory().checkItem(21025)) { // 종마의 귀 링
					earringLevel = 6;
				} else if (player.getInventory().checkItem(21026)) { // 혈족의 귀 링
					earringLevel = 7;
				} else if (player.getInventory().checkItem(21027)) { // 노예의 귀 링
					earringLevel = 8;
				}
				if (player.getKarmaLevel() == 1) {
					if (earringLevel >= 1) {
						htmlid = "lringd";
					} else {
						htmlid = "lring1";
					}
				} else if (player.getKarmaLevel() == 2) {
					if (earringLevel >= 2) {
						htmlid = "lringd";
					} else {
						htmlid = "lring2";
					}
				} else if (player.getKarmaLevel() == 3) {
					if (earringLevel >= 3) {
						htmlid = "lringd";
					} else {
						htmlid = "lring3";
					}
				} else if (player.getKarmaLevel() == 4) {
					if (earringLevel >= 4) {
						htmlid = "lringd";
					} else {
						htmlid = "lring4";
					}
				} else if (player.getKarmaLevel() == 5) {
					if (earringLevel >= 5) {
						htmlid = "lringd";
					} else {
						htmlid = "lring5";
					}
				} else if (player.getKarmaLevel() == 6) {
					if (earringLevel >= 6) {
						htmlid = "lringd";
					} else {
						htmlid = "lring6";
					}
				} else if (player.getKarmaLevel() == 7) {
					if (earringLevel >= 7) {
						htmlid = "lringd";
					} else {
						htmlid = "lring7";
					}
				} else if (player.getKarmaLevel() == 8) {
					if (earringLevel >= 8) {
						htmlid = "lringd";
					} else {
						htmlid = "lring8";
					}
				} else {
					htmlid = "lring0";
				}
			} else if (npcid == 80072) { // 바르로그의 대장간
				int karmaLevel = player.getKarmaLevel();
				if (karmaLevel == 1) {
					htmlid = "lsmith0";
				} else if (karmaLevel == 2) {
					htmlid = "lsmith1";
				} else if (karmaLevel == 3) {
					htmlid = "lsmith2";
				} else if (karmaLevel == 4) {
					htmlid = "lsmith3";
				} else if (karmaLevel == 5) {
					htmlid = "lsmith4";
				} else if (karmaLevel == 6) {
					htmlid = "lsmith5";
				} else if (karmaLevel == 7) {
					htmlid = "lsmith7";
				} else if (karmaLevel == 8) {
					htmlid = "lsmith8";
				} else {
					htmlid = "";
				}
			} else if (npcid == 80074) { // 업의 관리자
				if (player.getKarma() >= 10000000) {
					htmlid = "infamous01";
				} else {
					htmlid = "infamous02";
				}
			} else if (npcid == 80104) { // 에덴 기마 단원
				if (!player.isCrown()) { // 군주
					htmlid = "horseseller4";
				}
			} else if (npcid == 70528) { // 이야기할 수 있는 섬의 마을 타운 마스터
				htmlid = talkToTownmaster(player,
						L1TownLocation.TOWNID_TALKING_ISLAND);
			} else if (npcid == 70546) { // 켄트마을 타운 마스터
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_KENT);
			} else if (npcid == 70567) { // 그르딘마을 타운 마스터
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_GLUDIO);
			} else if (npcid == 70815) { // 화전마을 타운 마스터
				htmlid = talkToTownmaster(player,
						L1TownLocation.TOWNID_ORCISH_FOREST);
			} else if (npcid == 70774) { // 우드 베크마을 타운 마스터
				htmlid = talkToTownmaster(player,
						L1TownLocation.TOWNID_WINDAWOOD);
			} else if (npcid == 70799) { // 실버 나이트 타운 타운 마스터
				htmlid = talkToTownmaster(player,
						L1TownLocation.TOWNID_SILVER_KNIGHT_TOWN);
			} else if (npcid == 70594) { // 기란 도시 타운 마스터
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_GIRAN);
			} else if (npcid == 70860) { // Heine 도시 타운 마스터
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_HEINE);
			} else if (npcid == 70654) { // 완숙마을 타운 마스터
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_WERLDAN);
			} else if (npcid == 70748) { // 상아의 탑의 마을 타운 마스터
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_OREN);
			} else if (npcid == 70534) { // 이야기할 수 있는 섬의 마을 타운 어드바이저
				htmlid = talkToTownadviser(player,
						L1TownLocation.TOWNID_TALKING_ISLAND);
			} else if (npcid == 70556) { // 켄트마을 타운 어드바이저
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_KENT);
			} else if (npcid == 70572) { // 그르딘마을 타운 어드바이저
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_GLUDIO);
			} else if (npcid == 70830) { // 화전마을 타운 어드바이저
				htmlid = talkToTownadviser(player,
						L1TownLocation.TOWNID_ORCISH_FOREST);
			} else if (npcid == 70788) { // 우드 베크마을 타운 어드바이저
				htmlid = talkToTownadviser(player,
						L1TownLocation.TOWNID_WINDAWOOD);
			} else if (npcid == 70806) { // 실버 나이트 타운 타운 어드바이저
				htmlid = talkToTownadviser(player,
						L1TownLocation.TOWNID_SILVER_KNIGHT_TOWN);
			} else if (npcid == 70631) { // 기란 도시 타운 어드바이저
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_GIRAN);
			} else if (npcid == 70876) { // Heine 도시 타운 어드바이저
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_HEINE);
			} else if (npcid == 70663) { // 완숙마을 타운 어드바이저
				htmlid = talkToTownadviser(player,
						L1TownLocation.TOWNID_WERLDAN);
			} else if (npcid == 70761) { // 상아의 탑의 마을 타운 어드바이저
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_OREN);
			} else if (npcid == 70997) { //드로몬드
				htmlid = talkToDoromond(player);
			} else if (npcid == 70998) { // 노래하는 섬의 가이드
				htmlid = talkToSIGuide(player);
			} else if (npcid == 70999) { // 알렉스(노래하는 섬)
				htmlid = talkToAlex(player);
			} else if (npcid == 71000) { // 알렉스(훈련장)
				htmlid = talkToAlexInTrainingRoom(player);
			} else if (npcid == 71002) { // 왈가닥 세레이션사
				htmlid = cancellation(player);
			} else if (npcid == 70506) { // 르바
				htmlid = talkToRuba(player);
			} else if (npcid == 71005) { // 포피레아
				htmlid = talkToPopirea(player);
			} else if (npcid == 71009) { // 방어 아나운서
				if (player.getLevel() < 13) {
					htmlid = "jpe0071";
				}
			} else if (npcid == 71011) { // 치코리
				if (player.getLevel() < 13) {
					htmlid = "jpe0061";
				}
			} else if (npcid == 71013) { // 카렌
				if (player.isDarkelf()) {
					if (player.getLevel() <= 3) {
						htmlid = "karen1";
					} else if (player.getLevel() > 3 && player.getLevel() < 50) {
						htmlid = "karen3";
					} else if (player.getLevel() >= 50) {
						htmlid = "karen4";
					}
				}
			} else if (npcid == 71014) { // 마을의 자경단( 오른쪽)
				if (player.getLevel() < 13) {
					htmlid = "en0241";
				}
			} else if (npcid == 71015) { // 마을의 자경단(위)
				if (player.getLevel() < 13) {
					htmlid = "en0261";
				} else if (player.getLevel() >= 13 && player.getLevel() < 25) {
					htmlid = "en0262";
				}
			} else if (npcid == 71031) { // 용병 라이언
				if (player.getLevel() < 25) {
					htmlid = "en0081";
				}
			} else if (npcid == 71032) { // 모험자 에이타
				if (player.isElf()) {
					htmlid = "en0091e";
				} else if (player.isDarkelf()) {
					htmlid = "en0091d";
				} else if (player.isKnight()) {
					htmlid = "en0091k";
				} else if (player.isWizard()) {
					htmlid = "en0091w";
				} else if (player.isCrown()) {
					htmlid = "en0091p";
				}
			} else if (npcid == 71034) { // 율법박사
				if (player.getInventory().checkItem(41227)) { // 알렉스의 소개장
					if (player.isElf()) {
						htmlid = "en0201e";
					} else if (player.isDarkelf()) {
						htmlid = "en0201d";
					} else if (player.isKnight()) {
						htmlid = "en0201k";
					} else if (player.isWizard()) {
						htmlid = "en0201w";
					} else if (player.isCrown()) {
						htmlid = "en0201p";
					}
				}
			} else if (npcid == 71033) { // 하-미트
				if (player.getInventory().checkItem(41228)) { // 율법박사의 부적
					if (player.isElf()) {
						htmlid = "en0211e";
					} else if (player.isDarkelf()) {
						htmlid = "en0211d";
					} else if (player.isKnight()) {
						htmlid = "en0211k";
					} else if (player.isWizard()) {
						htmlid = "en0211w";
					} else if (player.isCrown()) {
						htmlid = "en0211p";
					}
				}
			} else if (npcid == 71026) { // 코코
				if (player.getLevel() < 10) {
					htmlid = "en0113";
				} else if (player.getLevel() >= 10 && player.getLevel() < 25) {
					htmlid = "en0111";
				} else if (player.getLevel() > 25) {
					htmlid = "en0112";
					}
			} else if (npcid == 71027) { // 쿠
				if (player.getLevel() < 10) {
					htmlid = "en0283";
				} else if (player.getLevel() >= 10 && player.getLevel() < 25) {
					htmlid = "en0281";
				} else if (player.getLevel() > 25) {
					htmlid = "en0282";
				}
			} else if (npcid == 71021) { // 뼈세공인 맛티
				if (player.getLevel() < 12) {
					htmlid = "en0197";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					htmlid = "en0191";
				}
			} else if (npcid == 71022) { // 뼈세공인 지난
				if (player.getLevel() < 12) {
					htmlid = "jpe0155";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41230)
							|| player.getInventory().checkItem(41231)
							|| player.getInventory().checkItem(41232)
							|| player.getInventory().checkItem(41233)
							|| player.getInventory().checkItem(41235)
							|| player.getInventory().checkItem(41238)
							|| player.getInventory().checkItem(41239)
							|| player.getInventory().checkItem(41240)) {
						htmlid = "jpe0158";
					}
				}
			} else if (npcid == 71023) { // 뼈세공인 케이이
				if (player.getLevel() < 12) {
					htmlid = "jpe0145";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41233)
							|| player.getInventory().checkItem(41234)) {
						htmlid = "jpe0143";
					} else if (player.getInventory().checkItem(41238)
							|| player.getInventory().checkItem(41239)
							|| player.getInventory().checkItem(41240)) {
						htmlid = "jpe0147";
					} else if (player.getInventory().checkItem(41235)
							|| player.getInventory().checkItem(41236)
							|| player.getInventory().checkItem(41237)) {
						htmlid = "jpe0144";
					}
				}
			} else if (npcid == 71020) { // 존
				if (player.getLevel() < 12) {
					htmlid = "jpe0125";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41231)) {
						htmlid = "jpe0123";
					} else if (player.getInventory().checkItem(41232)
							|| player.getInventory().checkItem(41233)
							|| player.getInventory().checkItem(41234)
							|| player.getInventory().checkItem(41235)
							|| player.getInventory().checkItem(41238)
							|| player.getInventory().checkItem(41239)
							|| player.getInventory().checkItem(41240)) {
						htmlid = "jpe0126";
					}
				}
			} else if (npcid == 71019) { // 제자 비트
				if (player.getLevel() < 12) {
					htmlid = "jpe0114";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41239)) { // 비트에의 편지
						htmlid = "jpe0113";
					} else {
						htmlid = "jpe0111";
					}
				}
			} else if (npcid == 71018) { // 페다
				if (player.getLevel() < 12) {
					htmlid = "jpe0133";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41240)) { // 페다에의 편지
						htmlid = "jpe0132";
					} else {
						htmlid = "jpe0131";
					}
				}
			} else if (npcid == 71025) { // 케스킨
				if (player.getLevel() < 10) {
					htmlid = "jpe0086";
				} else if (player.getLevel() >= 10 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41226)) { // 파고의 약
						htmlid = "jpe0084";
					} else if (player.getInventory().checkItem(41225)) { // 케스킨의 발주서
						htmlid = "jpe0083";
					} else if (player.getInventory().checkItem(40653)
							|| player.getInventory().checkItem(40613)) { // 붉은영쇠.검은열쇠
						htmlid = "jpe0081";
					}
				}
/*			} else if (npcid == 70512) { // 치료사(노래하는 시마무라안) //## revision 682
				if (player.getLevel() >= 25) {
					htmlid = "jpe0102";
				}
			} else if (npcid == 70514) { // 헤이 파업사
				if (player.getLevel() >= 25) {
					htmlid = "jpe0092";
				} */
			} else if (npcid == 71038) { // 장로 노나메
				if (player.getInventory().checkItem(41060)) { // 노나메의 추천서
					if (player.getInventory().checkItem(41090) // 네르가의 토템
							|| player.getInventory().checkItem(41091) // 두다마라의 토템
							|| player.getInventory().checkItem(41092)) { // 아트바의 토템
						htmlid = "orcfnoname7";
					} else {
						htmlid = "orcfnoname8";
					}
				} else {
					htmlid = "orcfnoname1";
				}
			} else if (npcid == 71040) { // 조사단장 아트바노아
				if (player.getInventory().checkItem(41060)) { // 노나메의 추천서
					if (player.getInventory().checkItem(41065)) { // 조사단의 증서
						if (player.getInventory().checkItem(41086) // 스피릿드의 뿌리
								|| player.getInventory().checkItem(41087) // 스피릿드의 표피
								|| player.getInventory().checkItem(41088) // 스피릿드의 잎
								|| player.getInventory().checkItem(41089)) { // 스피릿드의 나뭇가지
							htmlid = "orcfnoa6";
						} else {
							htmlid = "orcfnoa5";
						}
					} else {
						htmlid = "orcfnoa2";
					}
				} else {
					htmlid = "orcfnoa1";
				}
			} else if (npcid == 71041) { // 네르가후우모
				if (player.getInventory().checkItem(41060)) { // 노나메의 추천서
					if (player.getInventory().checkItem(41064)) { // 조사단의 증서
						if (player.getInventory().checkItem(41081) // 오크의 배지
								|| player.getInventory().checkItem(41082) // 오크의 아뮤렛트
								|| player.getInventory().checkItem(41083) // 셔맨 가루
								|| player.getInventory().checkItem(41084) // 일루젼 가루
								|| player.getInventory().checkItem(41085)) { // 예언자의 펄
							htmlid = "orcfhuwoomo2";
						} else {
							htmlid = "orcfhuwoomo8";
						}
					} else {
						htmlid = "orcfhuwoomo1";
					}
				} else {
					htmlid = "orcfhuwoomo5";
				}
			} else if (npcid == 71042) { // 네르가바크모
				if (player.getInventory().checkItem(41060)) { // 노나메의 추천서
					if (player.getInventory().checkItem(41062)) { // 조사단의 증서
						if (player.getInventory().checkItem(41071) // 은의 추석
								|| player.getInventory().checkItem(41072) // 은의 촛대
								|| player.getInventory().checkItem(41073) // 반디드의 열쇠
								|| player.getInventory().checkItem(41074) // 반디드의 봉투
								|| player.getInventory().checkItem(41075)) { // 더러워진 머리카락
							htmlid = "orcfbakumo2";
						} else {
							htmlid = "orcfbakumo8";
						}
					} else {
						htmlid = "orcfbakumo1";
					}
				} else {
					htmlid = "orcfbakumo5";
				}
			} else if (npcid == 71043) { // 두다마라브카
				if (player.getInventory().checkItem(41060)) { // 노나메의 추천서
					if (player.getInventory().checkItem(41063)) { // 조사단의 증서
						if (player.getInventory().checkItem(41076) // 더러워진 땅의 코어
								|| player.getInventory().checkItem(41077) // 더러워진 물의 코어
								|| player.getInventory().checkItem(41078) // 더러워진 불의 코어
								|| player.getInventory().checkItem(41079) // 더러워진 바람의 코어
								|| player.getInventory().checkItem(41080)) { // 더러워진 정령의 코어
							htmlid = "orcfbuka2";
						} else {
							htmlid = "orcfbuka8";
						}
					} else {
						htmlid = "orcfbuka1";
					}
				} else {
					htmlid = "orcfbuka5";
				}
			} else if (npcid == 71044) { // 두다마라카메
				if (player.getInventory().checkItem(41060)) { // 노나메의 추천서
					if (player.getInventory().checkItem(41061)) { // 조사단의 증서
						if (player.getInventory().checkItem(41066) // 더러워진 뿌리
								|| player.getInventory().checkItem(41067) // 더러워진 가지
								|| player.getInventory().checkItem(41068) // 더러워진 빈껍질
								|| player.getInventory().checkItem(41069) // 더러워진 타테가미
								|| player.getInventory().checkItem(41070)) { // 더러워진 요정의 날개
							htmlid = "orcfkame2";
						} else {
							htmlid = "orcfkame8";
						}
					} else {
						htmlid = "orcfkame1";
					}
				} else {
					htmlid = "orcfkame5";
				}
			} else if (npcid == 71055) { // 루케 인(해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_RESTA)
						== 3) {
					htmlid = "lukein13";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1)
						== L1Quest.QUEST_END
						&& player.getQuest().get_step(L1Quest.QUEST_RESTA)
						== 2
						&& player.getInventory().checkItem(40631)) {
					htmlid = "lukein10";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1)
						== L1Quest.QUEST_END) {
					htmlid = "lukein0";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1)
						== 11) {
					if (player.getInventory().checkItem(40716)) {
						htmlid = "lukein9";
					}
				} else if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1)
						>= 1
						&& player.getQuest().get_step(L1Quest.QUEST_LUKEIN1)
						<= 10) {
					htmlid = "lukein8";
				}
			} else if (npcid == 71063) { // 작은 상자-1번째 (해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_TBOX1)
						== L1Quest.QUEST_END) {
				} else if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1)
						== 1) {
					htmlid = "maptbox";
				}
			} else if (npcid == 71064) { // 작은 상자-2번째 -b지점(해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 2) {
					htmlid = talkToSecondtbox(player);
				}
			} else if (npcid == 71065) { // 작은 상자-2번째 -c지점(해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 3) {
					htmlid = talkToSecondtbox(player);
				}
			} else if (npcid == 71066) { // 작은 상자-2번째 -d지점(해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 4) {
					htmlid = talkToSecondtbox(player);
				}
			} else if (npcid == 71067) { // 작은 상자-3번째 -e지점(해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 5) {
					htmlid = talkToThirdtbox(player);
				}
			} else if (npcid == 71068) { // 작은 상자-3번째 -f지점(해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 6) {
					htmlid = talkToThirdtbox(player);
				}
			} else if (npcid == 71069) { // 작은 상자-3번째 -g지점(해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 7) {
					htmlid = talkToThirdtbox(player);
				}
			} else if (npcid == 71070) { // 작은 상자-3번째 -h지점(해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 8) {
					htmlid = talkToThirdtbox(player);
				}
			} else if (npcid == 71071) { // 작은 상자-3번째 -i지점(해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 9) {
					htmlid = talkToThirdtbox(player);
				}
			} else if (npcid == 71072) { // 작은 상자-3번째 -j지점(해적섬의 비밀)
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 10) {
					htmlid = talkToThirdtbox(player);
				}
			} else if (npcid == 71056) { // 시미즈(사라진 아들)
				if (player.getQuest().get_step(L1Quest.QUEST_RESTA)
						== 4) {
					if (player.getInventory().checkItem(40631)) {
						htmlid = "SIMIZZ11";
					} else {
						htmlid = "SIMIZZ0";
					}
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ)
						== 2) {
					htmlid = "SIMIZZ0";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ)
						== L1Quest.QUEST_END) {
					htmlid = "SIMIZZ15";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ)
						== 1) {
					htmlid = "SIMIZZ6";
				}
			} else if (npcid == 71057) { // 도일(보물의 지도 1)
				if (player.getQuest().get_step(L1Quest.QUEST_DOIL)
						== L1Quest.QUEST_END) {
					htmlid = "doil4b";
				}
			} else if (npcid == 71059) { // 루디안(보물의 지도 2)
				if (player.getQuest().get_step(L1Quest.QUEST_RUDIAN)
						== L1Quest.QUEST_END) {
					htmlid = "rudian1c";
				} else if (player.getQuest().get_step(L1Quest.QUEST_RUDIAN)
						== 1) {
					htmlid = "rudian7";
				} else if (player.getQuest().get_step(L1Quest.QUEST_DOIL)
						== L1Quest.QUEST_END) {
					htmlid = "rudian1b";
				} else {
					htmlid = "rudian1a";
				}
			} else if (npcid == 71060) { // 레스타(보물의 지도 3)
				if (player.getQuest().get_step(L1Quest.QUEST_RESTA)
						== L1Quest.QUEST_END) {
					htmlid = "resta1e";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ)
						== L1Quest.QUEST_END) {
					htmlid = "resta14";
				} else if (player.getQuest().get_step(L1Quest.QUEST_RESTA)
						== 4) {
					htmlid = "resta13";
				} else if (player.getQuest().get_step(L1Quest.QUEST_RESTA)
						== 3) {
					htmlid = "resta11";
					player.getQuest().set_step(L1Quest.QUEST_RESTA, 4);
				} else if (player.getQuest().get_step(L1Quest.QUEST_RESTA)
						== 2) {
					htmlid = "resta16";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ)
						== 2 
							&& player.getQuest().get_step(L1Quest.
									QUEST_CADMUS) == 1
							|| player.getInventory().checkItem(40647)) {
					htmlid = "resta1a";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CADMUS)
						== 1 
						|| player.getInventory().checkItem(40647)) {
					htmlid = "resta1c";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ)
						== 2) {
					htmlid = "resta1b";
				}
			} else if (npcid == 71061) { // 카좀스(보물의 지도 4)
				if (player.getQuest().get_step(L1Quest.QUEST_CADMUS)
						== L1Quest.QUEST_END) {
					htmlid = "cadmus1c";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CADMUS)
						== 3) {
					htmlid = "cadmus8";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CADMUS)
						== 2) {
					htmlid = "cadmus1a";
				} else if (player.getQuest().get_step(L1Quest.QUEST_DOIL)
						== L1Quest.QUEST_END) {
					htmlid = "cadmus1b";
				}
			} else if (npcid == 71062) { // 카미트(보물의 지도 4)
				if (player.getQuest().get_step(L1Quest.QUEST_CADMUS)
						>= 3) {
					htmlid = "kamit2";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CADMUS)
						== 2) {
					htmlid = "kamit1b";
				}
			} else if (npcid == 71036) { // 카미라(드레이크의 진실)
				if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA)
						== L1Quest.QUEST_END) {
					htmlid = "kamyla26";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA)
						== 4 && player.getInventory().checkItem(40717)) {
					htmlid = "kamyla15";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA)
						== 4 ) {
					htmlid = "kamyla14";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA)
						== 3 && player.getInventory().checkItem(40630)) {
					htmlid = "kamyla12";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA)
						== 3 ) {
					htmlid = "kamyla11";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA)
						== 2 && player.getInventory().checkItem(40644)) {
					htmlid = "kamyla9";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA)
						== 1 ) {
					htmlid = "kamyla8";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CADMUS)
						==  L1Quest.QUEST_END && player.getInventory()
							.checkItem(40621)) {
					htmlid = "kamyla1";
				}
			} else if (npcid == 71089) { // 흐랑코(드레이크의 진실)
				if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA)
						== 2 ) {
					htmlid = "francu12";
				}
			} else if (npcid == 71090) { // 시련의 크리스탈 2(드레이크의 진실)
				if (player.getQuest().get_step(L1Quest.QUEST_CRYSTAL)
						== 1 && player.getInventory().checkItem(40620)) {
					htmlid = "jcrystal2";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CRYSTAL)
						== 1){
					htmlid = "jcrystal3";
				}
			} else if (npcid == 71091) { // 시련의 크리스탈 3(드레이크의 진실)
				if (player.getQuest().get_step(L1Quest.QUEST_CRYSTAL)
						== 2 && player.getInventory().checkItem(40654)) {
					htmlid = "jcrystall2";
				}
			} else if (npcid == 71074) { // 리자드맨의 장로
				if (player.getQuest().get_step(L1Quest.QUEST_LIZARD)
						== L1Quest.QUEST_END) {
					htmlid = "lelder0";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LIZARD)
						== 3  && player.getInventory().checkItem(40634)) {
					htmlid = "lelder12";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LIZARD)
						== 3) {
					htmlid = "lelder11";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LIZARD)
						== 2  && player.getInventory().checkItem(40633)) {
					htmlid = "lelder7";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LIZARD)
						== 2) {
					htmlid = "lelder7b";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LIZARD)
						== 1) {
					htmlid = "lelder7b";
				} else if (player.getLevel() >= 40) {
					htmlid = "lelder1";
				}
			} else if (npcid == 71075) { // 지친 리자드맨 전사
				if (player.getQuest().get_step(L1Quest.QUEST_LIZARD)
						== 1) {
					htmlid = "llizard1b";
				} else {
				}
			} else if (npcid == 71076) { // 리자드맨 청년전사
				if (player.getQuest().get_step(L1Quest.QUEST_LIZARD)
						== L1Quest.QUEST_END) {
					htmlid = "ylizardb";
				} else {
				}
			} else if (npcid == 71168) { // 진명황 단테스 << 윗부분에 추가
			    if (player.getInventory().checkItem(41028)) {     
			     htmlid = "dantes1";
			     player.getInventory().consumeItem(41028, 1);
			    } else {
			     htmlid = "dantes";
			    }
			} else if (npcid == 80079) { // 케프리샤
				if (player.getQuest().get_step(L1Quest.QUEST_KEPLISHA)
						== L1Quest.QUEST_END
								&& !player.getInventory().checkItem(41312)) {
					htmlid = "keplisha6";
				} else {
					if (player.getInventory().checkItem(41314)) { // 점성술사의 부적
						htmlid = "keplisha3";
					} else if (player.getInventory().checkItem(41313)) { // 점성술사의 구슬
						htmlid = "keplisha2";
					} else if (player.getInventory().checkItem(41312)) { // 점성술사의 항아리
						htmlid = "keplisha4";
					}
				}
			} else if (npcid == 80102) { // 피리스
				if (player.getInventory().checkItem(41329)) { // 박제의 제작 의뢰서
					htmlid = "fillis3";
				}
			} else if (npcid == 71167) { // 후리무
				if (player.getCurrentSpriteId() == 3887) {// 캬린구다쿠에루후 변신
					htmlid = "frim1";
				}
			} else if (npcid == 71141) { // 갱부 옴1
				if (player.getCurrentSpriteId() == 3887) {// 캬린구다쿠에루후 변신
					htmlid = "moumthree1";
				}
			} else if (npcid == 71142) { // 갱부 옴2
				if (player.getCurrentSpriteId() == 3887) {// 캬린구다쿠에루후 변신
					htmlid = "moumtwo1";
				}
			} else if (npcid == 71145) { // 갱부 옴3
				if (player.getCurrentSpriteId() == 3887) {// 캬린구다쿠에루후 변신
					htmlid = "moumone1";
				}
			} else if (npcid == 71198) { // 용병 단장 티온
				if (player.getQuest().get_step(71198) == 1) {
					htmlid = "tion4";
				} else if (player.getQuest().get_step(71198) == 2) {
					htmlid = "tion5";
				} else if (player.getQuest().get_step(71198) == 3) {
					htmlid = "tion6";
				} else if (player.getQuest().get_step(71198) == 4) {
					htmlid = "tion7";
				} else if (player.getQuest().get_step(71198) == 5) {
					htmlid = "tion5";
				} else if (player.getInventory().checkItem(21059, 1)) {
					htmlid = "tion19";
				}
			} else if (npcid == 71199) { // 제론
				if (player.getQuest().get_step(71199) == 1) {
					htmlid = "jeron3";
				} else if (player.getInventory().checkItem(21059, 1)
						|| player.getQuest().get_step(71199) == 255) {
					htmlid = "jeron7";
				}
			// 투견
			} else if (npcid == DogFightSystemSettings.DogfightInfo.Ticket_Shop_Npcid) {
				if (DogFightController.getInstance().getdogState() == 1) {
					htmlid = "maeno3";
				} else if (DogFightController.getInstance().getdogState() == 2) {
					htmlid = "maeno5";
				}
			} else if (npcid == 81181 || npcid == 81182 || npcid == 81183) {
				if (BugRaceController.getInstance().getBugState() == 1) {
					htmlid = "maeno3";
				} else if (BugRaceController.getInstance().getBugState() == 2) {
					htmlid = "maeno5";
				}
			} else if (npcid == 4220012) {
					if (player.getInventory().checkItem(437027, 1)
							|| player.getInventory().checkItem(437028, 1)
							|| player.getInventory().checkItem(437029, 1)
							|| player.getInventory().checkItem(437030, 1)){
						htmlid = "suschef2";
					}
			}
			// html 표시 패킷 송신
			if (htmlid != null) { // htmlid가 지정되고 있는 경우
				if (htmldata != null) { // html 지정이 있는 경우는 표시
					player.sendPackets(new S_NPCTalkReturn(objid, htmlid,
							htmldata));
				} else {
					player.sendPackets(new S_NPCTalkReturn(objid, htmlid));
				}
			} else {
				if (player.getLawful() < -1000) { // 플레이어가 카오틱
					player.sendPackets(new S_NPCTalkReturn(talking, objid, 2));
				} else {
					player.sendPackets(new S_NPCTalkReturn(talking, objid, 1));
				}
			}
		}
	}

	private static String talkToTownadviser(L1PcInstance pc, int town_id) {
		String htmlid;
		if (pc.getHomeTownId() == town_id
				&& TownTable.getInstance().isLeader(pc, town_id)) {
			htmlid = "secretary1";
		} else {
			htmlid = "secretary2";
		}

		return htmlid;
	}

	private static String talkToTownmaster(L1PcInstance pc, int town_id) {
		String htmlid;
		if (pc.getHomeTownId() == town_id) {
			htmlid = "hometown";
		} else {
			htmlid = "othertown";
		}
		return htmlid;
	}

	@Override
	public void onFinalAction(L1PcInstance player, String action) {
	}

	public void doFinalAction(L1PcInstance player) {
	}

	private boolean checkHasCastle(L1PcInstance player, int castle_id) {
		if (player.getClanid() != 0) { // 크란 소속중
			L1Clan clan = L1World.getInstance().getClan(player.getClanname());
			if (clan != null) {
				if (clan.getCastleId() == castle_id) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean checkClanLeader(L1PcInstance player) {
		if (player.isCrown()) { // 군주
			L1Clan clan = L1World.getInstance().getClan(player.getClanname());
			if (clan != null) {
				if (player.getId() == clan.getLeaderId()) {
					return true;
				}
			}
		}
		return false;
	}

	private int getNecessarySealCount(L1PcInstance pc) {
		int rulerCount = 0;
		int necessarySealCount = 10;
		if (pc.getInventory().checkItem(40917)) { // 지의 지배자
			rulerCount++;
		}
		if (pc.getInventory().checkItem(40920)) { // 풍의 지배자
			rulerCount++;
		}
		if (pc.getInventory().checkItem(40918)) { // 수의 지배자
			rulerCount++;
		}
		if (pc.getInventory().checkItem(40919)) { // 불의 지배자
			rulerCount++;
		}
		if (rulerCount == 0) {
			necessarySealCount = 10;
		} else if (rulerCount == 1) {
			necessarySealCount = 100;
		} else if (rulerCount == 2) {
			necessarySealCount = 200;
		} else if (rulerCount == 3) {
			necessarySealCount = 500;
		}
		return necessarySealCount;
	}

	private void createRuler(L1PcInstance pc, int attr, int sealCount) {
		// 1.땅속성, 2.불속성, 4.물속성, 8.바람 속성
		int rulerId = 0;
		int protectionId = 0;
		int sealId = 0;
		if (attr == 1) {
			rulerId = 40917;
			protectionId = 40909;
			sealId = 40913;
		} else if (attr == 2) {
			rulerId = 40919;
			protectionId = 40911;
			sealId = 40915;
		} else if (attr == 4) {
			rulerId = 40918;
			protectionId = 40910;
			sealId = 40914;
		} else if (attr == 8) {
			rulerId = 40920;
			protectionId = 40912;
			sealId = 40916;
		}
		pc.getInventory().consumeItem(protectionId, 1);
		pc.getInventory().consumeItem(sealId, sealCount);
		ItemInstance item = pc.getInventory().storeItem(rulerId, 1);
		if (item != null) {
			pc.sendPackets(new S_ServerMessage(143,
					getNpcTemplate().get_name(), item.getLogName())); // \f1%0이%1를 주었습니다.
		}
	}

	private String talkToDoromond(L1PcInstance pc) {
		String htmlid = "";
		if (pc.getQuest().get_step(L1Quest.QUEST_DOROMOND) == 0) {
			htmlid = "jpe0011";
		} else if (pc.getQuest().get_step(L1Quest.QUEST_DOROMOND) == 1) {
			htmlid = "jpe0015";
		}

		return htmlid;
	}

	private String talkToAlex(L1PcInstance pc) {
		String htmlid = "";
		if (pc.getLevel() < 3) {
			htmlid = "jpe0021";
		} else if (pc.getQuest().get_step(L1Quest.QUEST_DOROMOND) < 2) {
			htmlid = "jpe0022";
		} else if (pc.getQuest().get_step(L1Quest.QUEST_AREX) == L1Quest.QUEST_END) {
			htmlid = "jpe0023";
		} else if (pc.getLevel() >= 10 && pc.getLevel() < 25) {
			if (pc.getInventory().checkItem(41227)) { // 알렉스의 소개장
				htmlid = "jpe0023";
			} else if (pc.isCrown()) {
				htmlid = "jpe0024p";
			} else if (pc.isKnight()) {
				htmlid = "jpe0024k";
			} else if (pc.isElf()) {
				htmlid = "jpe0024e";
			} else if (pc.isWizard()) {
				htmlid = "jpe0024w";
			} else if (pc.isDarkelf()) {
				htmlid = "jpe0024d";
			}
		} else if (pc.getLevel() > 25) {
			htmlid = "jpe0023";
		} else {
			htmlid = "jpe0021";
		}
		return htmlid;
	}

	private String talkToAlexInTrainingRoom(L1PcInstance pc) {
		String htmlid = "";
		if (pc.getLevel() < 3) {
			htmlid = "jpe0031";
		} else {
			if (pc.getQuest().get_step(L1Quest.QUEST_DOROMOND) < 2) {
				htmlid = "jpe0035";
			} else {
				htmlid = "jpe0036";
			}
		}

		return htmlid;
	}

	private String cancellation(L1PcInstance pc) {
		String htmlid = "";
		if (pc.getLevel() < 13) {
			htmlid = "jpe0161";
		} else {
			htmlid = "jpe0162";
		}

		return htmlid;
	}

	private String talkToRuba(L1PcInstance pc) {
		String htmlid = "";

		if (pc.isCrown() || pc.isWizard()) {
			htmlid = "en0101";
		} else if (pc.isKnight() || pc.isElf() || pc.isDarkelf()) {
			htmlid = "en0102";
		}

		return htmlid;
	}

	private String talkToSIGuide(L1PcInstance pc) {
		String htmlid = "";
		if (pc.getLevel() < 3) {
			htmlid = "en0301";
		} else if (pc.getLevel() >= 3 && pc.getLevel() < 7) {
			htmlid = "en0302";
		} else if (pc.getLevel() >= 7 && pc.getLevel() < 9) {
			htmlid = "en0303";
		} else if (pc.getLevel() >= 9 && pc.getLevel() < 12) {
			htmlid = "en0304";
		} else if (pc.getLevel() >= 12 && pc.getLevel() < 13) {
			htmlid = "en0305";
		} else if (pc.getLevel() >= 13 && pc.getLevel() < 25) {
			htmlid = "en0306";
		} else {
			htmlid = "en0307";
		}
		return htmlid;
	}

	private String talkToPopirea(L1PcInstance pc) {
		String htmlid = "";
		if (pc.getLevel() < 25) {
			htmlid = "jpe0041";
			if (pc.getInventory().checkItem(41209)
					|| pc.getInventory().checkItem(41210)
					|| pc.getInventory().checkItem(41212)) {
				htmlid = "jpe0043";
			}
			if (pc.getInventory().checkItem(41213)) {
				htmlid = "jpe0044";
			}
		} else {
			htmlid = "jpe0045";
		}
		return htmlid;
	}

	private String talkToSecondtbox(L1PcInstance pc) {
		String htmlid = "";
		if (pc.getQuest().get_step(L1Quest.QUEST_TBOX1) ==  L1Quest.QUEST_END) {
			if (pc.getInventory().checkItem(40701)) {
				htmlid = "maptboxa";
			} else {
				htmlid = "maptbox0";
			}
		} else {
			htmlid = "maptbox0";
		}
		return htmlid;
	}	

	private String talkToThirdtbox(L1PcInstance pc) {
		String htmlid = "";
		if (pc.getQuest().get_step(L1Quest.QUEST_TBOX2) ==  L1Quest.QUEST_END) {
			if (pc.getInventory().checkItem(40701)) {
				htmlid = "maptboxd";
			} else {
				htmlid = "maptbox0";
			}
		} else {
			htmlid = "maptbox0";
		}
		return htmlid;
	}

}
