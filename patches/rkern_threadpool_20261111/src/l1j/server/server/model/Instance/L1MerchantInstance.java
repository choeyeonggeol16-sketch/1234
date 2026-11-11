package l1j.server.server.model.Instance;

import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;

import static l1j.server.server.model.character.skill.L1SkillId.FULL_HEAL;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.HASTE;

import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.Config;
import l1j.server.server.ThreadPool;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.datatables.TownTable;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_Invis;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;

import l1j.server.server.model.network.core.sc.packet.SC_ChangeHeading;
public class L1MerchantInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;

	private static final int[] BEGINNER_BUFF_SKILLS = {
			HASTE,
			FULL_HEAL
	};

	// private GameServerSetting _GameServerSetting;
	private int clanid = 0;

	private String clanname;

	public int getClanid() {
		return clanid;
	}

	public void setClanid(int i) {
		clanid = i;
	}

	public String getClanname() { // 크란명
		return clanname;
	}

	public void setClanname(String s) {
		clanname = s;
	}

	public L1MerchantInstance(L1Npc template) {
		super(template);
		_restCallCount = new AtomicInteger(0);
	}

	@Override
	public void onAction(L1PcInstance pc) {
		if (pc == null) {
			return;
		}

		CAttackAction.hitAndSwing(pc, this);
	}

	@Override
	public void onNpcAI() {
		if (isAiRunning()) {
			return;
		}
		setActived(false);
		startAI();
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		if (player == null) {
			return;
		}

		final int objid = getId();
		final int npcid = getNpcTemplate().get_npcId();
		final L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(npcid);
		final L1Quest quest = player.getQuest();

		String htmlid = null;
		String[] htmldata = null;

		final int pcX = player.getX();
		final int pcY = player.getY();
		final int npcX = getX();
		final int npcY = getY();

		final long curtime = System.currentTimeMillis() / 1000L;
		if (player.getNpcActionTime() + 2 > curtime) {
			return;
		}
		player.setNpcActionTime(curtime);

		if (getNpcTemplate().getChangeHead()) {
			if (pcX == npcX && pcY < npcY) {
				setHeading(0);
			} else if (pcX > npcX && pcY < npcY) {
				setHeading(1);
			} else if (pcX > npcX && pcY == npcY) {
				setHeading(2);
			} else if (pcX > npcX && pcY > npcY) {
				setHeading(3);
			} else if (pcX == npcX && pcY > npcY) {
				setHeading(4);
			} else if (pcX < npcX && pcY > npcY) {
				setHeading(5);
			} else if (pcX < npcX && pcY == npcY) {
				setHeading(6);
			} else if (pcX < npcX && pcY < npcY) {
				setHeading(7);
			}
			SC_ChangeHeading.broadcast(this);

			if (_restCallCount.getAndIncrement() == 0) {
				setRest(true);
			}

			ThreadPool.getInstance().scheduleRequired(new RestMonitor(), REST_MILLISEC);
		}


		if (talking != null) {
			switch (npcid) {
			case 7310085:// 마법의문 액션값 체크
				if (player.getLevel() > 30 && player.getLevel() < 70) {
					htmlid = "talkinggate1";
				} else {
					htmlid = "talkinggate2";
				}
				break;
			case 5162:
				if (player.getInventory().checkItem(3000400)) {
					htmlid = "slots";
				} else if (player.getInventory().checkItem(3000401)) 
					htmlid = "slotr";
				else if (player.getInventory().checkItem(3000402))
					htmlid = "slotb";
				break;
			case 70005:// 파고
				htmlid = "pago";
				break;
			/*case 81210:
				if (!player.PC방_버프) {
					SC_SystemMessage.send(player, "PC방 코인 상품을 사용 중인 유저만 입장 가능합니다.");
					return;
				}
				break;*/
			case 7320101: 
				SC_SystemMessage.send(player, "\\fY항상 좋은 서버를 위해 최선을 다하겠습니다.");
				player.start_teleport(33439, 32812, 4, player.getHeading(), 169, true, false);
		//		player.setGmInvis(false);
		//		SC_Invis.send(player, player.getId(), 0);
		//		Broadcaster.broadcastPacket(player, new S_OtherCharPacks(player));
//				player.killSkillEffectTimer(L1SkillId.INVISIBILITY);
//				player.sendPackets(new SC_SystemMessage("투명상태를 해제했습니다. "));
				break;
			case 75854:
				SC_SystemMessage.send(player, "\\fY기란성은 그리 쉽사리 함락하기 힘들다네.");
				player.start_teleport(33614, 32763, 4, player.getHeading(), 169, true, false);
				break;
			case 82700:
				if(player.getInventory().checkItem(131300)) { 
					int count = player.getInventory().countItems(131300);
					SC_PacketBox.send(player, SC_PacketBox.GREEN_MESSAGE, "\\fY현재 보유중인 마일리지는 \\aG"+count+"개"+" \\fY입니다.");
				} else { 
					SC_PacketBox.send(player, SC_PacketBox.GREEN_MESSAGE, "\\fY현재 보유중인 마일리지는 \\aG0개 \\fY입니다.");
				}
				break;
			case 9320101:
//				if(Config.무기고급인첸트 == Config.맥스인첸1차) {
//					player.start_teleport(Config.맵1번x좌표, Config.맵1번y좌표, Config.맵1번map좌표, player.getHeading(), 169, true, false);
//				} else if(Config.무기고급인첸트 == Config.맥스인첸2차) {
//					player.start_teleport(Config.맵2번x좌표, Config.맵2번y좌표, Config.맵2번map좌표, player.getHeading(), 169, true, false);
//				} else if(Config.무기고급인첸트 == Config.맥스인첸3차) {
//					player.start_teleport(Config.맵3번x좌표, Config.맵3번y좌표, Config.맵3번map좌표, player.getHeading(), 169, true, false);
//				}
				player.start_teleport(32738, 32872, 2236, player.getHeading(), 169, true, false);
				SC_SystemMessage.send(player, "\\fY후원샵으로 이동 합니다.");
				player.setGmInvis(true);
				SC_Invis.send(player, player.getId(), 1);
				SC_Invis.broadcast(player, player.getId(), 1);
				SC_RemoveObject.broadcast(player);
				player.setSkillEffect(
						L1SkillId.INVISIBILITY, 0);
				SC_Ability.send(player, 3, true);
				SC_SystemMessage.send(player, "후원샵에서는 투명상태가 유지됩니다.");
				break;

			case 505011:
				SC_SystemMessage.send(player, "\\fY기란마을로 이동하였습니다.");
				player.start_teleport(33437, 32806, 4, player.getHeading(), 169, true, false);
				break;
			case 778877: // 기란 비밀던전

				SC_SystemMessage.send(player, "\\fY기란감옥 비밀던전으로 입장합니다.");
				player.start_teleport(32735, 32842, 481, player.getHeading(), 169, true, false);

				break;
			case 778888: // 기란 비밀던전

				SC_SystemMessage.send(player, "\\fY지배결계 비밀던전으로 입장합니다.");
				player.start_teleport(32544, 32873, 12257, player.getHeading(), 169, true, false);

				break;
			case 778899: // 기란 비밀던전

				SC_SystemMessage.send(player, "\\fY지배정상 비밀던전으로 입장합니다.");
				player.start_teleport(32736, 32799, 41, player.getHeading(), 169, true, false);

				break;

			case 7320215:
				if (player.getLevel() >= 1 && player.getLevel() <= 55) {
					if (player.getLevel() < Config.경험치지급단) {
						player.addExp((ExpTable.getExpByLevel(Config.경험치지급단) - 1) - player.getExp()+ ((ExpTable.getExpByLevel(Config.경험치지급단) - 1) / 1000));
						player.setCurrentHp(player.getMaxHp());
						player.setCurrentMp(player.getMaxMp());
					}
				}
				break;
			case 202057://라라
				if (player.getLevel() >= 56) {
					htmlid = "tel_lala2";
				} else if (player.getLevel() >= 54 && player.getLevel() <= 55) {
					SC_SystemMessage.send(player, "게렝에게 군터의 추천서를 주고 버프를 받으세요.");
					player.start_teleport(32586, 32954, 0, player.getHeading(), 169, true, false);
					htmlid = "tel_lala2";
				}
				break;
			case 70841:
				if (player.isElf()) {
					htmlid = "luudielE1";
				} else if (player.isDarkelf()) {
					htmlid = "luudielCE1";
				} else {
					htmlid = "luudiel1";
				}
				break;
			case 70522: // 군터
				if (player.isCrown()) {
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step == 2 || lv15_step == L1Quest.QUEST_END) {
							htmlid = "gunterp11";
						} else {
							htmlid = "gunterp9";
						}
					} else {
						htmlid = "gunterp12";
					}
				} else if (player.isKnight()) {
					int lv30_step = quest.get_step(L1Quest.QUEST_LEVEL30);
					if (lv30_step == 0) {
						htmlid = "gunterk9";
					} else if (lv30_step == 1) {
						htmlid = "gunterkE1";
					} else if (lv30_step == 2) {
						htmlid = "gunterkE2";
					} else if (lv30_step >= 3) {
						htmlid = "gunterkE3";
					}
				} else if (player.isElf()) {
					htmlid = "guntere1";
				} else if (player.isWizard()) {
					htmlid = "gunterw1";
				} else if (player.isDarkelf()) {
					htmlid = "gunterde1";
				}
				break;
			case 70653: // 마샤
				if (player.isCrown()) {
					if (player.getLevel() >= 45) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL30)) {
							int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
							if (lv45_step == L1Quest.QUEST_END) {
								htmlid = "masha4";
							} else if (lv45_step >= 1) {
								htmlid = "masha3";
							} else {
								htmlid = "masha1";
							}
						}
					}
				} else if (player.isKnight()) {
					if (player.getLevel() >= 45) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL30)) {
							int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
							if (lv45_step == L1Quest.QUEST_END) {
								htmlid = "mashak3";
							} else if (lv45_step == 0) {
								htmlid = "mashak1";
							} else if (lv45_step >= 1) {
								htmlid = "mashak2";
							}
						}
					}
				} else if (player.isElf()) {
					if (player.getLevel() >= 45) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL30)) {
							int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
							if (lv45_step == L1Quest.QUEST_END) {
								htmlid = "mashae3";
							} else if (lv45_step >= 1) {
								htmlid = "mashae2";
							} else {
								htmlid = "mashae1";
							}
						}
					}
				}
				break;
			case 66110:
			case 66111:
			case 66112:
			case 66113:
			case 66114:
			case 66115:
			case 66116:
			case 66117:
			case 66118:
			case 66119:
			case 66120:
				if (Config.혈맹포인트조회) {
					if (player.getClanid() != 0 ) {
				혈맹포인트(player);
					} else {
						혈맹포인트3(player);
					}
				} else {
					if (player.getClanid() != 0 ) {
				혈맹포인트1(player);
					} else {
						혈맹포인트3(player);
					}
				}
				break;
			case 70554: // 제로
				if (player.isCrown()) {
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step == 1) {
							htmlid = "zero5";
						} else if (lv15_step == L1Quest.QUEST_END) {
							htmlid = "zero1";
						} else {
							htmlid = "zero1";
						}
					} else {
						htmlid = "zero6";
					}
				}
				break;
			case 522:
				if (player.getInventory().checkItem(40308, 200000)) {
					player.getInventory().consumeItem(40308, 200000);
					if (Config.POLY_EVENT1) {
						L1PolyMorph.doPoly(player, 7925, 600, L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
					}
					L1SkillUse.cast(
							player,
							L1SkillId.HUNTER_BLESS,
							player.getId(),
							player.getX(),
							player.getY(),
							null,
							0,
							L1SkillUse.TYPE_GMBUFF);
					SC_SkillSound.broadcast(player, player.getId(), 9009);
					SC_SkillSound.send(player, player.getId(), 9009);
					htmlid = "stonescroll";
				} else {
					player.sendPackets("아데나가 부족 합니다.");
				}
				break;
			case 70783: // 아리아
				if (player.isCrown()) {
					if (player.getLevel() >= 30) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) {
							int lv30_step = quest.get_step(L1Quest.QUEST_LEVEL30);
							if (lv30_step == L1Quest.QUEST_END) {
								htmlid = "aria3";
							} else if (lv30_step == 1) {
								htmlid = "aria2";
							} else {
								htmlid = "aria1";
							}
						}
					}
				}
				break;
			case 70000: // 마빈
				if (player.getLevel() < 52) {
					htmlid = "marbinquestA";
				} else {
					if (player.getInventory().checkItem(700012, 1)) {
						htmlid = "marbinquest3";
					} else {
						htmlid = "marbinquest1";
					}
				}
				break;
			case 70782: // 수색개미
				if (player.getCurrentSpriteId() == 1037) {
					if (player.isCrown()) {
						if (quest.get_step(L1Quest.QUEST_LEVEL30) == 1) {
							htmlid = "ant1";
						} else {
							htmlid = "ant3";
						}
					} else {
						htmlid = "ant3";
					}
				}
				break;
			case 70545: // 리차드
				if (player.isCrown()) {
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step >= 1 && lv45_step != L1Quest.QUEST_END) {
						if (player.getInventory().checkItem(40586)) {
							htmlid = "richard4";
						} else {
							htmlid = "richard1";
						}
					}
				}
				break;
			case 70776: // 맥
				if (player.isCrown()) {
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step == 1) {
						htmlid = "meg1";
					} else if (lv45_step == 2 && lv45_step <= 3) {
						htmlid = "meg2";
					} else if (lv45_step >= 4) {
						htmlid = "meg3";
					}
				}
				break;
			case 71200: // 백마법사피에타
				if (player.isCrown()) {
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step == 2 && player.getInventory().checkItem(41422)) {
						player.getInventory().consumeItem(41422, 1);
						player.getInventory().storeItem(40568, 1);
					}
				}
				break;
			case 70802:// 아논
				if (player.isKnight()) {
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step == L1Quest.QUEST_END) {
							htmlid = "aanon7";
						} else if (lv15_step == 1) {
							htmlid = "aanon4";
						}
					}
				}
				break;
			case 70775:// 마크
				if (player.isKnight()) {
					if (player.getLevel() >= 30) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) {
							int lv30_step = quest.get_step(L1Quest.QUEST_LEVEL30);
							if (lv30_step == 0) {
								htmlid = "mark1";
							} else {
								htmlid = "mark2";
							}
						}
					}
				}
				break;
			case 70794: // 게라드
				if (player.isCrown()) {
					htmlid = "gerardp1";
				} else if (player.isKnight()) {
					int lv30_step = quest.get_step(L1Quest.QUEST_LEVEL30);
					if (lv30_step == L1Quest.QUEST_END) {
						htmlid = "gerardkEcg";
					} else if (lv30_step < 3) {
						htmlid = "gerardk7";
					} else if (lv30_step == 3) {
						htmlid = "gerardkE1";
					} else if (lv30_step == 4) {
						htmlid = "gerardkE2";
					} else if (lv30_step == 5) {
						htmlid = "gerardkE3";
					} else if (lv30_step >= 6) {
						htmlid = "gerardkE4";
					}
				} else if (player.isElf()) {
					htmlid = "gerarde1";
				} else if (player.isWizard()) {
					htmlid = "gerardw1";
				} else if (player.isDarkelf()) {
					htmlid = "gerardde1";
				}
				break;
			case 2020561:
				if (player.getLevel() >= 70) {
					htmlid = "clgunter4no";
				}
				break;
			case 202056:
				if (player.getLevel() >= 70) {
					htmlid = "clgunter4no";
				}
				break;
				
			case 50000040:
				if (player.getLevel() >= 70) {
					htmlid = "clgunter4no";
				}
				break;
			case 70555:
				if (player.getCurrentSpriteId() == 2374) {
					if (player.isKnight()) {
						if (quest.get_step(L1Quest.QUEST_LEVEL30) == 6) {
							htmlid = "jim2";
						} else {
							htmlid = "jim4";
						}
					} else {
						htmlid = "jim4";
					}
				}
				break;
			case 70715:
				if (player.isKnight()) {
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step == 1) {
						htmlid = "jimuk1";
					} else if (lv45_step >= 2) {
						htmlid = "jimuk2";
					}
				}
				break;
			case 70760:
			case 9291:
				if(player.isElf()){
					if(player.getElfAttr() != 0){
						htmlid = "ellyonne11";
					} else {
						htmlid = "siabeth";
					}
				} else {
					htmlid = "siabeth6";
				}
				break;
			case 70711:
				if (player.isKnight()) {
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step == 2) {
						if (player.getInventory().checkItem(20026)) {
							htmlid = "giantk1";
						}
					} else if (lv45_step == 3) {
						htmlid = "giantk2";
					} else if (lv45_step >= 4) {
						htmlid = "giantk3";
					}
				}
				break;
			case 70826:
				if (player.isElf()) {
					if (player.getLevel() >= 15) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) {
							htmlid = "oth5";
						} else {
							htmlid = "oth1";
						}
					} else {
						htmlid = "oth6";
					}
				}
				break;
			case 70844:
				if (player.isElf()) {
					if (player.getLevel() >= 30) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) {
							int lv30_step = quest.get_step(L1Quest.QUEST_LEVEL30);
							if (lv30_step == L1Quest.QUEST_END) {
								htmlid = "motherEE3";
							} else if (lv30_step >= 1) {
								htmlid = "motherEE2";
							} else if (lv30_step <= 0) {
								htmlid = "motherEE1";
							}
						} else {
							htmlid = "mothere1";
						}
					} else {
						htmlid = "mothere1";
					}
				}
				break;
			case 70724:
				if (player.isElf()) {
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step >= 4) {
						htmlid = "heit5";
					} else if (lv45_step >= 3) {
						htmlid = "heit3";
					} else if (lv45_step >= 2) {
						htmlid = "heit2";
					} else if (lv45_step >= 1) {
						htmlid = "heit1";
					}
				}
				break;
			case 70531:
				if (player.isWizard()) {
					if (player.getLevel() >= 15) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) {
							htmlid = "jem6";
						} else {
							htmlid = "jem1";
						}
					}
				}
				break;
			case 70009:
				htmlid = "gereng01";
				break;
			case 81105:
				if (player.isWizard()) {
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step >= 3) {
						htmlid = "stoenm3";
					} else if (lv45_step >= 2) {
						htmlid = "stoenm2";
					} else if (lv45_step >= 1) {
						htmlid = "stoenm1";
					}
				}
				break;
			case 70739: // 디가르뎅
				if (player.getLevel() >= 50) {
					int lv50_step = quest.get_step(L1Quest.QUEST_LEVEL50);
					if (lv50_step == L1Quest.QUEST_END) {
						if (player.isCrown()) {
							htmlid = "dicardingp3";
						} else if (player.isKnight()) {
							htmlid = "dicardingk3";
						} else if (player.isElf()) {
							htmlid = "dicardinge3";
						} else if (player.isWizard()) {
							htmlid = "dicardingw3";
						} else if (player.isDarkelf()) {
							htmlid = "dicarding";
						}
					} else if (lv50_step >= 1) {
						if (player.isCrown()) {
							htmlid = "dicardingp2";
						} else if (player.isKnight()) {
							htmlid = "dicardingk2";
						} else if (player.isElf()) {
							htmlid = "dicardinge2";
						} else if (player.isWizard()) {
							htmlid = "dicardingw2";
						} else if (player.isDarkelf()) {
							htmlid = "dicarding";
						}
					} else if (lv50_step >= 0) {
						if (player.isCrown()) {
							htmlid = "dicardingp1";
						} else if (player.isKnight()) {
							htmlid = "dicardingk1";
						} else if (player.isElf()) {
							htmlid = "dicardinge1";
						} else if (player.isWizard()) {
							htmlid = "dicardingw1";
						} else if (player.isDarkelf()) {
							htmlid = "dicarding";
						}
					} else {
						htmlid = "dicarding";
					}
				} else {
					htmlid = "dicarding";
				}
				break;
			case 70885:
				if (player.isDarkelf()) {
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step == L1Quest.QUEST_END) {
							htmlid = "kanguard3";
						} else if (lv15_step >= 1) {
							htmlid = "kanguard2";
						} else {
							htmlid = "kanguard1";
						}
					} else {
						htmlid = "kanguard5";
					}
				}
				break;
			case 70892:
				if (player.isDarkelf()) {
					if (player.getLevel() >= 30) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL15)) {
							int lv30_step = quest.get_step(L1Quest.QUEST_LEVEL30);
							if (lv30_step == L1Quest.QUEST_END) {
								htmlid = "ronde5";
							} else if (lv30_step >= 2) {
								htmlid = "ronde3";
							} else if (lv30_step >= 1) {
								htmlid = "ronde2";
							} else {
								htmlid = "ronde1";
							}
						} else {
							htmlid = "ronde7";
						}
					} else {
						htmlid = "ronde7";
					}
				}
				break;
			case 70895:
				final L1NpcInstance npc = this;
				SC_NpcChatPacket.sendNpc(player, npc, "완성된 라스타바드의 역사서를 더블클릭 하면 되는데 굳이 저에게 온 이유는 무엇인가...?", 0);
				if (player.isDarkelf()) {
					if (player.getLevel() >= 45) {
						if (quest.isEnd(L1Quest.QUEST_LEVEL30)) {
							int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
							if (lv45_step == L1Quest.QUEST_END) {
								if (player.getLevel() < 50) {
									htmlid = "bluedikaq3";
								} else {
									int lv50_step = quest.get_step(L1Quest.QUEST_LEVEL50);
									if (lv50_step == L1Quest.QUEST_END) {
										htmlid = "bluedikaq8";
									} else {
										htmlid = "bluedikaq6";
									}
								}
							} else if (lv45_step >= 1) {
								htmlid = "bluedikaq2";
							} else {
								htmlid = "bluedikaq1";
							}
						} else {
							htmlid = "bluedikaq5";
						}
					} else {
						htmlid = "bluedikaq5";
					}
				}
				break;
			case 70904:
				if (player.isDarkelf()) {
					if (quest.get_step(L1Quest.QUEST_LEVEL45) == 1) {
						htmlid = "koup12";
					}
				}
				break;
			case 70824:
				if (player.isDarkelf()) {
					if (player.getCurrentSpriteId() == 3634) {
						int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
						if (lv45_step == 1) {
							htmlid = "assassin1";
						} else if (lv45_step == 2) {
							htmlid = "assassin2";
						} else {
							htmlid = "assassin3";
						}
					} else {
						htmlid = "assassin3";
					}
				}
				break;
			case 70744:
				if (player.isDarkelf()) {
					int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
					if (lv45_step >= 5) {
						htmlid = "roje14";
					} else if (lv45_step >= 4) {
						htmlid = "roje13";
					} else if (lv45_step >= 3) {
						htmlid = "roje12";
					} else if (lv45_step >= 2) {
						htmlid = "roje11";
					} else {
						htmlid = "roje15";
					}
				}
				break;
			case 3000003: // 장로 프로켈
				if (player.isDragonknight()) {
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step == 1) {
							htmlid = "prokel4";
						} else if (lv15_step == 2 || lv15_step == L1Quest.QUEST_END) {
							htmlid = "prokel7";
						} else {
							htmlid = "prokel2";
						}
					} else {
						htmlid = "prokel1"; // 레벨 15이하
					}
				}
				break;
			case 3100004: // 장로 실레인
				if (player.isBlackwizard()) {
					if (player.getLevel() >= 15) {
						int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
						if (lv15_step == 1) {
							htmlid = "silrein4";
						} else if (lv15_step == 2 || lv15_step == L1Quest.QUEST_END) {
							htmlid = "silrein5";
						} else {
							htmlid = "silrein2";
						}
					} else {
						htmlid = "prokel1"; // 레벨 15이하
					}
				}
				break;
			case 70087:
				if (player.isDarkelf()) {
					htmlid = "sedia";
				}
				break;
			case 70099:
				if (!quest.isEnd(L1Quest.QUEST_OILSKINMANT)) {
					if (player.getLevel() > 13) {
						htmlid = "kuper1";
					}
				}
				break;
			case 70796:
				if (!quest.isEnd(L1Quest.QUEST_OILSKINMANT)) {
					if (player.getLevel() > 13) {
						htmlid = "dunham1";
					}
				}
				break;
			case 70011:
				int time = L1GameTimeClock.getInstance().getGameTime().getSeconds() % 86400;
				if (time < 60 * 60 * 6 || time > 60 * 60 * 20) { // 20:00?6:00
					htmlid = "shipEvI6";
				}
				break;
			case 70553:// 이스마엘(시종장) 켄트성 리뉴얼
				boolean hascastle = checkHasCastle(player, L1CastleLocation.KENT_CASTLE_ID);				
				if (hascastle) {					
					if (checkClanLeader(player)) {
						htmlid = "ishmael1";
					} else if (player.getClanRank() == L1Clan.수호) {
						htmlid = "ishmael1";
						htmldata = new String[] { player.getName() };
					} else {
						htmlid = "ishmael6";
					}
				} else {
					htmlid = "ishmael7";
				}
				break;
			case 70822:
				boolean hascastle1 = checkHasCastle(player, L1CastleLocation.OT_CASTLE_ID);
				if (hascastle1) {
					if (checkClanLeader(player)) {
						htmlid = "seghem1";
					} else {
						htmlid = "seghem6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "seghem7";
				}
				break;
			case 70784:
				boolean hascastle2 = checkHasCastle(player, L1CastleLocation.WW_CASTLE_ID);
				if (hascastle2) {
					if (checkClanLeader(player)) {
						htmlid = "othmond1";
					} else {
						htmlid = "othmond6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "othmond7";
				}
				break;
			case 70623:// 오빌(시종장) 기란성 리뉴얼
				boolean hascastle3 = checkHasCastle(player, L1CastleLocation.GIRAN_CASTLE_ID);
				if (hascastle3) {
					if (checkClanLeader(player)) {
						htmlid = "orville1";
					} else if (player.getClanRank() == L1Clan.수호) {
						htmlid = "orville1";
						htmldata = new String[] { player.getName() };
					} else {
						htmlid = "orville6";
					}
				} else {
					htmlid = "orville7";
				}
				break;
			case 70880:
				boolean hascastle4 = checkHasCastle(player, L1CastleLocation.HEINE_CASTLE_ID);
				if (hascastle4) {
					if (checkClanLeader(player)) {
						htmlid = "fisher1";
					} else {
						htmlid = "fisher6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "fisher7";
				}
				break;
			case 70665:
				boolean hascastle5 = checkHasCastle(player, L1CastleLocation.DOWA_CASTLE_ID);
				if (hascastle5) {
					if (checkClanLeader(player)) {
						htmlid = "potempin1";
					} else {
						htmlid = "potempin6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "potempin7";
				}
				break;
			case 70721:
				boolean hascastle6 = checkHasCastle(player, L1CastleLocation.ADEN_CASTLE_ID);
				if (hascastle6) {
					if (checkClanLeader(player)) {
						htmlid = "timon1";
					} else {
						htmlid = "timon6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "timon7";
				}
				break;
			case 81155:
				boolean hascastle7 = checkHasCastle(player, L1CastleLocation.DIAD_CASTLE_ID);
				if (hascastle7) {
					if (checkClanLeader(player)) {
						htmlid = "olle1";
					} else {
						htmlid = "olle6";
						htmldata = new String[] { player.getName() };
					}
				} else {
					htmlid = "olle7";
				}
				break;
			case 80057:
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
				break;
			case 80058:
				int level5 = player.getLevel();
				if (level5 <= 44) {
					htmlid = "cpass03";
				} else if (level5 <= 51 && 45 <= level5) {
					htmlid = "cpass02";
				} else {
					htmlid = "cpass01";
				}
				break;
			case 80059:
				if (player.getKarmaLevel() >= 3) {
					htmlid = "cpass03";
				} else if (player.getInventory().checkItem(40921)) {
					htmlid = "wpass02";
				} else if (player.getInventory().checkItem(40917)) {
					htmlid = "wpass14";
				} else if (player.getInventory().checkItem(40912) || player.getInventory().checkItem(40910)
						|| player.getInventory().checkItem(40911)) {
					htmlid = "wpass04";
				} else if (player.getInventory().checkItem(40909)) {
					int count = getNecessarySealCount(player);
					if (player.getInventory().checkItem(40913, count)) {
						createRuler(player, 1, count);
						htmlid = "wpass06";
					} else {
						htmlid = "wpass03";
					}
				} else if (player.getInventory().checkItem(40913)) {
					htmlid = "wpass08";
				} else {
					htmlid = "wpass05";
				}
				break;
			case 80060:
				if (player.getKarmaLevel() >= 3) {
					htmlid = "cpass03";
				} else if (player.getInventory().checkItem(40921)) {
					htmlid = "wpass02";
				} else if (player.getInventory().checkItem(40920)) {
					htmlid = "wpass13";
				} else if (player.getInventory().checkItem(40909) || player.getInventory().checkItem(40910)
						|| player.getInventory().checkItem(40911)) {
					htmlid = "wpass04";
				} else if (player.getInventory().checkItem(40912)) {
					int count = getNecessarySealCount(player);
					if (player.getInventory().checkItem(40916, count)) {
						createRuler(player, 8, count);
						htmlid = "wpass06";
					} else {
						htmlid = "wpass03";
					}
				} else if (player.getInventory().checkItem(40916)) {
					htmlid = "wpass08";
				} else {
					htmlid = "wpass05";
				}
				break;
			case 80061:
				if (player.getKarmaLevel() >= 3) {
					htmlid = "cpass03";
				} else if (player.getInventory().checkItem(40921)) {
					htmlid = "wpass02";
				} else if (player.getInventory().checkItem(40918)) {
					htmlid = "wpass11";
				} else if (player.getInventory().checkItem(40909) || player.getInventory().checkItem(40912)
						|| player.getInventory().checkItem(40911)) {
					htmlid = "wpass04";
				} else if (player.getInventory().checkItem(40910)) {
					int count = getNecessarySealCount(player);
					if (player.getInventory().checkItem(40914, count)) {
						createRuler(player, 4, count);
						htmlid = "wpass06";
					} else {
						htmlid = "wpass03";
					}
				} else if (player.getInventory().checkItem(40914)) {
					htmlid = "wpass08";
				} else {
					htmlid = "wpass05";
				}
				break;
			case 80062:
				if (player.getKarmaLevel() >= 3) {
					htmlid = "cpass03";
				} else if (player.getInventory().checkItem(40921)) {
					htmlid = "wpass02";
				} else if (player.getInventory().checkItem(40919)) {
					htmlid = "wpass12";
				} else if (player.getInventory().checkItem(40909) || player.getInventory().checkItem(40912)
						|| player.getInventory().checkItem(40910)) {
					htmlid = "wpass04";
				} else if (player.getInventory().checkItem(40911)) {
					int count = getNecessarySealCount(player);
					if (player.getInventory().checkItem(40915, count)) {
						createRuler(player, 2, count);
						htmlid = "wpass06";
					} else {
						htmlid = "wpass03";
					}
				} else if (player.getInventory().checkItem(40915)) {
					htmlid = "wpass08";
				} else {
					htmlid = "wpass05";
				}
				break;
			case 80065:
				if (player.getKarmaLevel() < 3) {
					htmlid = "uturn0";
				} else {
					htmlid = "uturn1";
				}
				break;
			case 80047:
				if (player.getKarmaLevel() > -3) {
					htmlid = "uhelp1";
				} else {
					htmlid = "uhelp2";
				}
				break;
			case 80049:
				if (player.getKarma() <= -10000000) {
					htmlid = "betray11";
				} else {
					htmlid = "betray12";
				}
				break;
			case 80050:
				if (player.getKarmaLevel() > -1) {
					htmlid = "meet103";
				} else {
					htmlid = "meet101";
				}
				break;
			case 80053:
				int karmaLevel = player.getKarmaLevel();
				if (karmaLevel == 0) {
					htmlid = "aliceyet";
				} else if (karmaLevel >= 1) {
					if (player.getInventory().checkItem(196) || player.getInventory().checkItem(197)
							|| player.getInventory().checkItem(198) || player.getInventory().checkItem(199)
							|| player.getInventory().checkItem(200) || player.getInventory().checkItem(201)
							|| player.getInventory().checkItem(202) || player.getInventory().checkItem(203)) {
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
				break;
			case 80056:
				if (player.getKarma() <= -10000000) {
					htmlid = "infamous11";
				} else {
					htmlid = "infamous12";
				}
				break;
			case 80064:
				if (player.getKarmaLevel() < 1) {
					htmlid = "meet003";
				} else {
					htmlid = "meet001";
				}
				break;
			case 80066:
				if (player.getKarma() >= 10000000) {
					htmlid = "betray01";
				} else {
					htmlid = "betray02";
				}
				break;
			case 80072:
				int karmaLevel1 = player.getKarmaLevel();
				if (karmaLevel1 == 1) {
					htmlid = "lsmith0";
				} else if (karmaLevel1 == 2) {
					htmlid = "lsmith1";
				} else if (karmaLevel1 == 3) {
					htmlid = "lsmith2";
				} else if (karmaLevel1 == 4) {
					htmlid = "lsmith3";
				} else if (karmaLevel1 == 5) {
					htmlid = "lsmith4";
				} else if (karmaLevel1 == 6) {
					htmlid = "lsmith5";
				} else if (karmaLevel1 == 7) {
					htmlid = "lsmith7";
				} else if (karmaLevel1 == 8) {
					htmlid = "lsmith8";
				} else {
					htmlid = "";
				}
				break;
			case 80074:
				if (player.getKarma() >= 10000000) {
					htmlid = "infamous01";
				} else {
					htmlid = "infamous02";
				}
				break;
			case 80104:
				if (!player.isCrown()) {
					htmlid = "horseseller4";
				}
				break;
			case 70528:
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_TALKING_ISLAND);
				break;
			case 70546:
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_KENT);
				break;
			case 70567:
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_GLUDIO);
				break;
			case 70815:
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_ORCISH_FOREST);
				break;
			case 70774:
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_WINDAWOOD);
				break;
			case 70799:
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_SILVER_KNIGHT_TOWN);
				break;
			case 70594:
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_GIRAN);
				break;
			case 70860:
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_HEINE);
				break;
			case 70654:
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_WERLDAN);
				break;
			case 70748:
				htmlid = talkToTownmaster(player, L1TownLocation.TOWNID_OREN);
				break;
			case 70534:
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_TALKING_ISLAND);
				break;
			case 70556:
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_KENT);
				break;
			case 70572:
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_GLUDIO);
				break;
			case 70830:
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_ORCISH_FOREST);
				break;
			case 70788:
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_WINDAWOOD);
				break;
			case 70806:
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_SILVER_KNIGHT_TOWN);
				break;
			case 70631:
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_GIRAN);
				break;
			case 70876:
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_HEINE);
				break;
			case 70663:
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_WERLDAN);
				break;
			case 70761:
				htmlid = talkToTownadviser(player, L1TownLocation.TOWNID_OREN);
				break;
			case 70998:
				htmlid = talkToSIGuide(player);
				break;
			case 71005:
				htmlid = talkToPopirea(player);
				break;
			case 71013:
				if (player.isDarkelf()) {
					if (player.getLevel() <= 3) {
						htmlid = "karen1";
					} else if (player.getLevel() > 3 && player.getLevel() < 50) {
						htmlid = "karen3";
					} else if (player.getLevel() >= 50) {
						htmlid = "karen4";
					}
				}
				break;
			case 71031:
				if (player.getLevel() < 25) {
					htmlid = "en0081";
				}
				break;
			case 71021:
				if (player.getLevel() < 12) {
					htmlid = "en0197";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					htmlid = "en0191";
				}
				break;
			case 71022:
				if (player.getLevel() < 12) {
					htmlid = "jpe0155";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41230) || player.getInventory().checkItem(41231)
							|| player.getInventory().checkItem(41232) || player.getInventory().checkItem(41233)
							|| player.getInventory().checkItem(41235) || player.getInventory().checkItem(41238)
							|| player.getInventory().checkItem(41239) || player.getInventory().checkItem(41240)) {
						htmlid = "jpe0158";
					}
				}
				break;
			case 71023:
				if (player.getLevel() < 12) {
					htmlid = "jpe0145";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41233) || player.getInventory().checkItem(41234)) {
						htmlid = "jpe0143";
					} else if (player.getInventory().checkItem(41238) || player.getInventory().checkItem(41239)
							|| player.getInventory().checkItem(41240)) {
						htmlid = "jpe0147";
					} else if (player.getInventory().checkItem(41235) || player.getInventory().checkItem(41236)
							|| player.getInventory().checkItem(41237)) {
						htmlid = "jpe0144";
					}
				}
				break;
			case 71020:
				if (player.getLevel() < 12) {
					htmlid = "jpe0125";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41231)) {
						htmlid = "jpe0123";
					} else if (player.getInventory().checkItem(41232) || player.getInventory().checkItem(41233)
							|| player.getInventory().checkItem(41234) || player.getInventory().checkItem(41235)
							|| player.getInventory().checkItem(41238) || player.getInventory().checkItem(41239)
							|| player.getInventory().checkItem(41240)) {
						htmlid = "jpe0126";
					}
				}
				break;
			case 71019:
				if (player.getLevel() < 12) {
					htmlid = "jpe0114";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41239)) {
						htmlid = "jpe0113";
					} else {
						htmlid = "jpe0111";
					}
				}
				break;
			case 71018:
				if (player.getLevel() < 12) {
					htmlid = "jpe0133";
				} else if (player.getLevel() >= 12 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41240)) {
						htmlid = "jpe0132";
					} else {
						htmlid = "jpe0131";
					}
				}
				break;
			case 71025:
				if (player.getLevel() < 10) {
					htmlid = "jpe0086";
				} else if (player.getLevel() >= 10 && player.getLevel() < 25) {
					if (player.getInventory().checkItem(41226)) {
						htmlid = "jpe0084";
					} else if (player.getInventory().checkItem(41225)) {
						htmlid = "jpe0083";
					} else if (player.getInventory().checkItem(40653) || player.getInventory().checkItem(40613)) {
						htmlid = "jpe0081";
					}
				}
				break;
			/*case 70512:
				if (player.getLevel() >= 90) {
					htmlid = "jpe0102";
					break;
				}
			case 70514:
				if (player.getLevel() >= 90) {
					htmlid = "jpe0092";
					break;
				}
			 /* case 70035: case 70041: // 버경 상인 case 70042:
			 * if(BugRaceController.getInstance().getBugState() == 1){ htmlid =
			 * "maeno3"; }else if(BugRaceController.getInstance().getBugState()
			 * == 2){ htmlid = "maeno5"; }else{ htmlid = "pandora"; } break;
			 */
			case 70035:
			case 70041:
			case 70042:
				if (BugRaceController.getInstance().getBugState() == 1) {
					htmlid = "maeno3";
				} else if (BugRaceController.getInstance().getBugState() == 2) {
					htmlid = "maeno5";
				}
				break;
			case 71038:
				if (player.getInventory().checkItem(41060)) {
					if (player.getInventory().checkItem(41090) || player.getInventory().checkItem(41091)
							|| player.getInventory().checkItem(41092)) {
						htmlid = "orcfnoname7";
					} else {
						htmlid = "orcfnoname8";
					}
				} else {
					htmlid = "orcfnoname1";
				}
				break;
			case 71040:
				if (player.getInventory().checkItem(41060)) {
					if (player.getInventory().checkItem(41065)) {
						if (player.getInventory().checkItem(41086) || player.getInventory().checkItem(41087)
								|| player.getInventory().checkItem(41088) || player.getInventory().checkItem(41089)) {
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
				break;
			case 71041:
				if (player.getInventory().checkItem(41060)) {
					if (player.getInventory().checkItem(41064)) {
						if (player.getInventory().checkItem(41081) || player.getInventory().checkItem(41082)
								|| player.getInventory().checkItem(41083) || player.getInventory().checkItem(41084)
								|| player.getInventory().checkItem(41085)) {
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
				break;
			case 71042:
				if (player.getInventory().checkItem(41060)) {
					if (player.getInventory().checkItem(41062)) {
						if (player.getInventory().checkItem(41071) || player.getInventory().checkItem(41072)
								|| player.getInventory().checkItem(41073) || player.getInventory().checkItem(41074)
								|| player.getInventory().checkItem(41075)) {
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
				break;
			case 71043:
				if (player.getInventory().checkItem(41060)) {
					if (player.getInventory().checkItem(41063)) {
						if (player.getInventory().checkItem(41076) || player.getInventory().checkItem(41077)
								|| player.getInventory().checkItem(41078) || player.getInventory().checkItem(41079)
								|| player.getInventory().checkItem(41080)) {
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
				break;
			case 71044:
				if (player.getInventory().checkItem(41060)) {
					if (player.getInventory().checkItem(41061)) {
						if (player.getInventory().checkItem(41066) || player.getInventory().checkItem(41067)
								|| player.getInventory().checkItem(41068) || player.getInventory().checkItem(41069)
								|| player.getInventory().checkItem(41070)) {
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
				break;
			case 71055:
				if (player.getQuest().get_step(L1Quest.QUEST_RESTA) == 3) {
					htmlid = "lukein13";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == L1Quest.QUEST_END
						&& player.getQuest().get_step(L1Quest.QUEST_RESTA) == 2
						&& player.getInventory().checkItem(40631)) {
					htmlid = "lukein10";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == L1Quest.QUEST_END) {
					htmlid = "lukein0";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 11) {
					if (player.getInventory().checkItem(40716)) {
						htmlid = "lukein9";
					}
				} else if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) >= 1
						&& player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) <= 10) {
					htmlid = "lukein8";
				}
				break;
			case 71063:
				if (player.getQuest().get_step(L1Quest.QUEST_TBOX1) == L1Quest.QUEST_END) {
				} else if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 1) {
					htmlid = "maptbox";
				}
				break;
			case 71064:
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 2) {
					htmlid = talkToSecondtbox(player);
				}
				break;
			case 71065:
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 3) {
					htmlid = talkToSecondtbox(player);
				}
				break;
			case 71066:
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 4) {
					htmlid = talkToSecondtbox(player);
				}
				break;
			case 71067:
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 5) {
					htmlid = talkToThirdtbox(player);
				}
				break;
			case 71068:
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 6) {
					htmlid = talkToThirdtbox(player);
				}
				break;
			case 71069:
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 7) {
					htmlid = talkToThirdtbox(player);
				}
				break;
			case 71070:
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 8) {
					htmlid = talkToThirdtbox(player);
				}
				break;
			case 71071:
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 9) {
					htmlid = talkToThirdtbox(player);
				}
				break;
			case 71072:
				if (player.getQuest().get_step(L1Quest.QUEST_LUKEIN1) == 10) {
					htmlid = talkToThirdtbox(player);
				}
				break;
			case 71056:
				if (player.getQuest().get_step(L1Quest.QUEST_RESTA) == 4) {
					if (player.getInventory().checkItem(40631)) {
						htmlid = "SIMIZZ11";
					} else {
						htmlid = "SIMIZZ0";
					}
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ) == 2) {
					htmlid = "SIMIZZ0";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ) == L1Quest.QUEST_END) {
					htmlid = "SIMIZZ15";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ) == 1) {
					htmlid = "SIMIZZ6";
				}
				break;
			case 71057:
				if (player.getQuest().get_step(L1Quest.QUEST_DOIL) == L1Quest.QUEST_END) {
					htmlid = "doil4b";
				}
				break;
			case 71059:
				if (player.getQuest().get_step(L1Quest.QUEST_RUDIAN) == L1Quest.QUEST_END) {
					htmlid = "rudian1c";
				} else if (player.getQuest().get_step(L1Quest.QUEST_RUDIAN) == 1) {
					htmlid = "rudian7";
				} else if (player.getQuest().get_step(L1Quest.QUEST_DOIL) == L1Quest.QUEST_END) {
					htmlid = "rudian1b";
				} else {
					htmlid = "rudian1a";
				}
				break;
			case 71060:
				if (player.getQuest().get_step(L1Quest.QUEST_RESTA) == L1Quest.QUEST_END) {
					htmlid = "resta1e";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ) == L1Quest.QUEST_END) {
					htmlid = "resta14";
				} else if (player.getQuest().get_step(L1Quest.QUEST_RESTA) == 4) {
					htmlid = "resta13";
				} else if (player.getQuest().get_step(L1Quest.QUEST_RESTA) == 3) {
					htmlid = "resta11";
					player.getQuest().set_step(L1Quest.QUEST_RESTA, 4);
				} else if (player.getQuest().get_step(L1Quest.QUEST_RESTA) == 2) {
					htmlid = "resta16";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ) == 2
						&& player.getQuest().get_step(L1Quest.QUEST_CADMUS) == 1
						|| player.getInventory().checkItem(40647)) {
					htmlid = "resta1a";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CADMUS) == 1
						|| player.getInventory().checkItem(40647)) {
					htmlid = "resta1c";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SIMIZZ) == 2) {
					htmlid = "resta1b";
				}
				break;
			case 71061:
				if (player.getQuest().get_step(L1Quest.QUEST_CADMUS) == L1Quest.QUEST_END) {
					htmlid = "cadmus1c";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CADMUS) == 3) {
					htmlid = "cadmus8";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CADMUS) == 2) {
					htmlid = "cadmus1a";
				} else if (player.getQuest().get_step(L1Quest.QUEST_DOIL) == L1Quest.QUEST_END) {
					htmlid = "cadmus1b";
				}
				break;
			case 71036:
				if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA) == L1Quest.QUEST_END) {
					htmlid = "kamyla26";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 4
						&& player.getInventory().checkItem(40717)) {
					htmlid = "kamyla15";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 4) {
					htmlid = "kamyla14";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 3
						&& player.getInventory().checkItem(40630)) {
					htmlid = "kamyla12";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 3) {
					htmlid = "kamyla11";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 2
						&& player.getInventory().checkItem(40644)) {
					htmlid = "kamyla9";
				} else if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 1) {
					htmlid = "kamyla8";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CADMUS) == L1Quest.QUEST_END
						&& player.getInventory().checkItem(40621)) {
					htmlid = "kamyla1";
				}
				break;
			case 71089:
				if (player.getQuest().get_step(L1Quest.QUEST_KAMYLA) == 2) {
					htmlid = "francu12";
				}
				break;
			case 71090:
				if (player.getQuest().get_step(L1Quest.QUEST_CRYSTAL) == 1 && player.getInventory().checkItem(40620)) {
					htmlid = "jcrystal2";
				} else if (player.getQuest().get_step(L1Quest.QUEST_CRYSTAL) == 1) {
					htmlid = "jcrystal3";
				}
				break;
			case 71091:
				if (player.getQuest().get_step(L1Quest.QUEST_CRYSTAL) == 2 && player.getInventory().checkItem(40654)) {
					htmlid = "jcrystall2";
				}
				break;
			case 71074:
				if (player.getQuest().get_step(L1Quest.QUEST_LIZARD) == L1Quest.QUEST_END) {
					// htmlid = "lelder0";
					htmlid = "lelder1"; // 퀘스트 무한 재생
				} else if (player.getQuest().get_step(L1Quest.QUEST_LIZARD) == 3
						&& player.getInventory().checkItem(40634)) {
					htmlid = "lelder12";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LIZARD) == 3) {
					htmlid = "lelder11";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LIZARD) == 2
						&& player.getInventory().checkItem(40633)) {
					htmlid = "lelder7";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LIZARD) == 2) {
					htmlid = "lelder7b";
				} else if (player.getQuest().get_step(L1Quest.QUEST_LIZARD) == 1) {
					htmlid = "lelder7b";
				} else if (player.getLevel() >= 40) {
					htmlid = "lelder1";
				}
				break;
			case 71076:
				if (player.getQuest().get_step(L1Quest.QUEST_LIZARD) == L1Quest.QUEST_END) {
					htmlid = "ylizardb";
				} else {
				}
				break;
			case 70840: // 달의장궁 로빈후드
				if (player.isCrown() && player.isWizard() && player.isKnight()) {
					// int MOONBOW_step = quest.get_step(L1Quest.QUEST_MOONBOW);
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
				} else {
					htmlid = "robinhood3";
				}
				break;
			case 600005: // 달의장궁 지브릴
				if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 2) {
					htmlid = "zybril1";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 3) {
					htmlid = "zybril7";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 4) {
					htmlid = "zybril8";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 5) {
					htmlid = "zybril18";
				} else {
					htmlid = "zybril16";
				}
				break;
			case 71168: // 진명황 단테스
				if (player.getInventory().checkItem(41028)) {
					htmlid = "dantes1";
				}
				break;
			case 71180: // 제이프
				if (player.get_sex() == 0) {
					htmlid = "jp1";// 남자
				} else {
					htmlid = "jp3";
				}
				break;
			case 80079:
				/*if (player.getQuest().get_step(L1Quest.QUEST_KEPLISHA) == L1Quest.QUEST_END
						&& !player.getInventory().checkItem(41312)) {
					htmlid = "keplisha6";
				} else {*/
					if (player.getInventory().checkItem(41314)) {
						htmlid = "keplisha3";
					} else if (player.getInventory().checkItem(41313)) {
						htmlid = "keplisha2";
					} else if (player.getInventory().checkItem(41312)) {
						htmlid = "keplisha4";
					}
//				}
				break;
			case 71167:
				if (player.getCurrentSpriteId() == 3887) {
					htmlid = "frim1";
				}
				break;
			case 71141:
				if (player.getCurrentSpriteId() == 3887) {
					htmlid = "moumthree1";
				}
				break;
			case 71142:
				if (player.getCurrentSpriteId() == 3887) {
					htmlid = "moumtwo1";
				}
				break;
			case 71145:
				if (player.getCurrentSpriteId() == 3887) {
					htmlid = "moumone1";
				}
				break;
			case 71198:
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
				break;
			case 71199:
				if (player.getQuest().get_step(71199) == 1) {
					htmlid = "jeron3";
				} else if (player.getInventory().checkItem(21059, 1) || player.getQuest().get_step(71199) == 255) {
					htmlid = "jeron7";
				}
				break;
			case 6000015:// 마야의 그림자
				if (player.getInventory().checkItem(41158)) {
					htmlid = "adenshadow1";
				} else {
					htmlid = "adenshadow2";
				}
				break;
			case 7: // 아놀드
				if (player.getLevel() >= 52) {
					htmlid = "anold1";
				} else {
					htmlid = "anold2";
				}
				break;
			// 에킨스
			case 7200000:
				if (player.getLevel() >= 56) {
					if (player.getInventory().checkItem(3000215, 1) || player.getInventory().checkItem(1000004, 1)) {
						htmlid = "ekins2";
					} else {
						htmlid = "ekins1";
					}
				} else {
					htmlid = "ekins3";
				}
				break;
			case 81200:
				if (player.getInventory().checkItem(21069) || player.getInventory().checkItem(21074)) {
					htmlid = "c_belt";
				}
				break;
			case 900135:// 유리에
				if (player.getInventory().checkItem(410096, 1) || player.getInventory().checkItem(410096, 1)) {
					htmlid = "j_html00";
				} else {
					htmlid = "j_html01";
				}
				break;
			case 80076: // 넘어진 항해사
				if (player.getInventory().checkItem(41058)) { // 완성한 항해 일지
					htmlid = "voyager8";
				} else if (player.getInventory().checkItem(49082) // 미완성의 항해 일지
						|| player.getInventory().checkItem(49083)) {
					// 페이지를 추가하고 있지 않는 상태
					if (player.getInventory().checkItem(41038) // 항해 일지 1 페이지
							|| player.getInventory().checkItem(41039) // 항해 일지 2
																		// 페이지
							|| player.getInventory().checkItem(41039) // 항해 일지 3
																		// 페이지
							|| player.getInventory().checkItem(41039) // 항해 일지 4
																		// 페이지
							|| player.getInventory().checkItem(41039) // 항해 일지 5
																		// 페이지
							|| player.getInventory().checkItem(41039) // 항해 일지 6
																		// 페이지
							|| player.getInventory().checkItem(41039) // 항해 일지 7
																		// 페이지
							|| player.getInventory().checkItem(41039) // 항해 일지 8
																		// 페이지
							|| player.getInventory().checkItem(41039) // 항해 일지 9
																		// 페이지
							|| player.getInventory().checkItem(41039)) { // 항해
																			// 일지
																			// 10
																			// 페이지
						htmlid = "voyager9";
					} else {
						htmlid = "voyager7";
					}
				} else if (player.getInventory().checkItem(49082) // 미완성의 항해 일지
						|| player.getInventory().checkItem(49083) || player.getInventory().checkItem(49084)
						|| player.getInventory().checkItem(49085) || player.getInventory().checkItem(49086)
						|| player.getInventory().checkItem(49087) || player.getInventory().checkItem(49088)
						|| player.getInventory().checkItem(49089) || player.getInventory().checkItem(49090)
						|| player.getInventory().checkItem(49091)) {
					// 페이지를 추가한 상태
					htmlid = "voyager7";
				}
				break;
			case 205: // 테베 오시리스의 제단 문지기
				/** 보스 공략 시간이 아니라면 */
//				if (CrockController.getInstance().isKillBoss()) {
//					htmlid = "thebegate5";
//				} else if (!CrockController.getInstance().isBoss()) {
//					htmlid = "tebegate2";
//					/** 보스 공략 시간이라면 */
//				} else {
//					/** 열쇠가 없다면 */
//					if (!player.getInventory().checkItem(100036, 1))
//						htmlid = "tebegate3";
//					/** 선착순 인원이 다 찼다면 */
//					else if (CrockController.getInstance().size() >= 20)
//						htmlid = "tebegate4";
//					/** 만족 */
//					else
//						htmlid = "tebegate1";
//				}
				break;
			case 500063: // 티칼 쿠쿨칸
//				if (CrockController.getInstance().isKillBoss()) {
//					htmlid = "tikalgate5";
//				} else if (!CrockController.getInstance().isBoss()) {
//					htmlid = "tikalgate2";
//				} else if (CrockController.getInstance().isBoss()) {
//
//					if (!player.getInventory().checkItem(500210, 1)) {
//						htmlid = "tikalgate3";
//					} else if (CrockController.getInstance().size() >= 20) {
//						htmlid = "tikalgate4";
//					} else {
//						htmlid = "tikalgate1";
//					}
//				}
				break;
			case 50112: // 세리안
				if (player.isCrown() || player.isWizard() || player.isDragonknight()) {
					int talk_step = quest.get_step(L1Quest.QUEST_FIRSTQUEST);
					if (talk_step == 1) {
						if (player.getLevel() >= 5) {
							htmlid = "orenb4";
						} else {
							htmlid = "orenb14";
						}
					} else if (talk_step == 255) {
						htmlid = "orenb11";
					}
				} else {
					htmlid = "orenb12";
				}
				break;
			case 50113: // 레크만
				if (player.isKnight() || player.isElf() || player.isDarkelf() || player.isBlackwizard()) {
					int talk_step = quest.get_step(L1Quest.QUEST_FIRSTQUEST);
					if (talk_step == 1) {
						if (player.getLevel() >= 5) {
							htmlid = "orena4";
						} else {
							htmlid = "orena14";
						}
					} else if (talk_step == 255) {
						htmlid = "orena11";
					}
				} else {
					htmlid = "orena12";
				}
				break;
			case 80067: // 첩보원(욕망의 동굴)
				if (player.getQuest().get_step(L1Quest.QUEST_DESIRE) == L1Quest.QUEST_END) {
					htmlid = "minicod10";
				} else if (player.getKarmaLevel() >= 1) {
					htmlid = "minicod07";
				} else if (player.getQuest().get_step(L1Quest.QUEST_DESIRE) == 1 && player.getCurrentSpriteId() == 6034) { // 코라프프리스트
																														// 변신
					htmlid = "minicod03";
				} else if (player.getQuest().get_step(L1Quest.QUEST_DESIRE) == 1 && player.getCurrentSpriteId() != 6034) {
					htmlid = "minicod05";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SHADOWS) == L1Quest.QUEST_END // 그림자의
																									// 신전측
																									// 퀘스트
																									// 종료
						|| player.getInventory().checkItem(41121) // 카헬의 지령서
						|| player.getInventory().checkItem(41122)) { // 카헬의 명령서
					htmlid = "minicod01";
				} else if (player.getInventory().checkItem(41130) // 핏자국의 지령서
						&& player.getInventory().checkItem(41131)) { // 핏자국의 명령서
					htmlid = "minicod06";
				} else if (player.getInventory().checkItem(41130)) { // 핏자국의 명령서
					htmlid = "minicod02";
				}
				break;
			case 4201000: // 환술사 아샤
				if (player.isBlackwizard())
					htmlid = "asha1";
				else
					htmlid = "asha2";
				break;
			case 4202000:
				if (player.isDragonknight())
					htmlid = "feaena1";
				else
					htmlid = "feaena2";
				break;
			case 3200021:
				if (!player.isElf()) {
					// int MOONBOW_step = quest.get_step(L1Quest.QUEST_MOONBOW);
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
				} else {
					htmlid = "robinhood3";
				}
				break;
			case 3200022:
				if (!player.isElf()) {
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 2) {
					htmlid = "zybril1";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 3) {
					htmlid = "zybril7";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 4) {
					htmlid = "zybril8";
				} else if (player.getQuest().get_step(L1Quest.QUEST_MOONBOW) == 5) {
					htmlid = "zybril18";
				} else {
					htmlid = "zybril16";
				}
				break;
			case 81202: // 첩보원(그림자의 신전)
				if (player.getQuest().get_step(L1Quest.QUEST_SHADOWS) == L1Quest.QUEST_END) {
					htmlid = "minitos10";
				} else if (player.getKarmaLevel() <= -1) {
					htmlid = "minitos07";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SHADOWS) == 1 && player.getCurrentSpriteId() == 6035) { // 렛서데이몬
																														// 변신
					htmlid = "minitos03";
				} else if (player.getQuest().get_step(L1Quest.QUEST_SHADOWS) == 1 && player.getCurrentSpriteId() != 6035) {
					htmlid = "minitos05";
				} else if (player.getQuest().get_step(L1Quest.QUEST_DESIRE) == L1Quest.QUEST_END // 욕망의
																									// 동굴측
																									// 퀘스트
																									// 종료
						|| player.getInventory().checkItem(41130) // 핏자국의 지령서
						|| player.getInventory().checkItem(41131)) { // 핏자국의 명령서
					htmlid = "minitos01";
				} else if (player.getInventory().checkItem(41121) // 카헬의 지령서
						&& player.getInventory().checkItem(41122)) { // 카헬의 명령서
					htmlid = "minitos06";
				} else if (player.getInventory().checkItem(41121)) { // 카헬의 명령서
					htmlid = "minitos02";
				}
				break;
			case 81208: // 더러워진 브롭브
				if (player.getInventory().checkItem(41129) // 핏자국의 정수
						|| player.getInventory().checkItem(41138)) { // 카헬의 정수
					htmlid = "minibrob04";
				} else if (player.getInventory().checkItem(41126) // 핏자국의 타락 한
																	// 정수
						&& player.getInventory().checkItem(41127) // 핏자국의 무력한 정수
						&& player.getInventory().checkItem(41128) // 핏자국의 아집인 정수
						|| player.getInventory().checkItem(41135) // 카헬의 타락 한 정수
								&& player.getInventory().checkItem(41136) // 카헬의
																			// 아집인
																			// 정수
								&& player.getInventory().checkItem(41137)) { // 카헬의
																				// 아집인
																				// 정수
					htmlid = "minibrob02";
				}
				break;

			case 5000006:
				멘트(player);
				break;
			case 4200018:// 경험치지급
				경험치멘트(player);
				break;
			case 777849: // 킬톤 (호랑이 사육)
				if (player.getInventory().checkItem(87050)) {
					htmlid = "killton2";
				}
				break;
			case 777848: // 메린 (진돗개 사육)
				if (player.getInventory().checkItem(87051)) {
					htmlid = "merin2";
				}
				break;
			case 900015: // 숨겨진 용들의 땅 입구(노랑포탈)
				if (player.getLevel() >= 30 && player.getLevel() <= 51) {
					htmlid = "dsecret2";
				} else if (player.getLevel() > 51) {
					htmlid = "dsecret1";
				} else {
					htmlid = "dsecret3";
				}
				break;
			// 숨겨진 계곡 리뉴얼
			case 9274: // 초보자 도우미
				if (player.getLevel() < 2) {
					player.setExp(ExpTable.getExpByLevel(2));
				} else if (player.getLevel() >= 5 && player.getLevel() <= 9) {
					htmlid = "tutorrw3";
				} else if (player.getLevel() >= 10 && player.getLevel() <= 14) {
					htmlid = "tutorrw2";
				} else if (player.getLevel() > 14) {
					htmlid = "tutorrw1";
				}
				applyBeginnerSupport(player);
				break;
			case 9275: // 수련장 관리인
				if (player.getLevel() < 2) {
					htmlid = "adminrw3";
				} else if (player.getLevel() == 2) {
					player.setExp(ExpTable.getExpByLevel(3));
				} else if (player.getLevel() >= 3 && player.getLevel() <= 4) {
					htmlid = "adminrw1";
				} else if (player.getLevel() >= 5) {
					if (player.getLevel() == 5) {
						player.setExp(player.getExp() + 1000);
						htmlid = "adminrw2";
					} else {
						htmlid = "adminrw4";
					}
				}
				break;
			case 9273: // 페이하스
				if (player.isElf()) {
					if (player.getLevel() > 7) {
						htmlid = "siriss";
					} else {
						htmlid = "sirissnt";
					}
				} else if (player.isWizard()) {
					if (player.getLevel() > 3) {
						htmlid = "siriss";
					} else {
						htmlid = "sirissnt";
					}
				} else if (player.isDarkelf()) {
					if (player.getLevel() > 11) {
						htmlid = "siriss";
					} else {
						htmlid = "sirissnt";
					}
				} else if (player.isCrown()) {
					if (player.getLevel() > 9) {
						htmlid = "siriss";
					} else {
						htmlid = "sirissnt";
					}
				} else if (player.isDragonknight() || player.isBlackwizard()) {
					htmlid = "sirissnw";
				} else if (player.isKnight() || player.isWarrior()) {
					htmlid = "sirissnw";
				}
				break;
			case 9134:
				if (player.getLevel() < 52) {
					htmlid = "marbinquestA";
				} else {
					if (player.getInventory().checkItem(46115, 1)) {
						htmlid = "marbinquest3";
					} else {
						htmlid = "marbinquest1";
					}
				}
				break;
			case 5088: // 보석세공사 데이빗(얼녀귀걸이)
				if (player.getInventory().checkItem(49031)) {
					if (player.getInventory().checkItem(21081)) {
						htmlid = "gemout1";
					} else if (player.getQuest().get_step(L1Quest.QUEST_ICEQUEENRING) == 1) {
						htmlid = "gemout2";
					} else if (player.getQuest().get_step(L1Quest.QUEST_ICEQUEENRING) == 2) {
						htmlid = "gemout3";
					} else if (player.getQuest().get_step(L1Quest.QUEST_ICEQUEENRING) == 3) {
						htmlid = "gemout4";
					} else if (player.getQuest().get_step(L1Quest.QUEST_ICEQUEENRING) == 4) {
						htmlid = "gemout5";
					} else if (player.getQuest().get_step(L1Quest.QUEST_ICEQUEENRING) == 5) {
						htmlid = "gemout6";
					} else if (player.getQuest().get_step(L1Quest.QUEST_ICEQUEENRING) == 6) {
						htmlid = "gemout7";
					} else if (player.getQuest().get_step(L1Quest.QUEST_ICEQUEENRING) == 7) {
						htmlid = "gemout8";
					} else { // 보석만 가지고있다.
						htmlid = "gemout17";
					}
				}
				break;
			case 5092: // 엘핀 [ 요정의 숲 잡화상 ]
				if (player.isElf()) {
					htmlid = "elfin";
				} else {
					htmlid = "elfin2";
				}
				break;
			case 5093: // 엘리 [ 요정의 숲 수정 상인 ]
				if (player.isElf()) {
					htmlid = "elli";
				} else {
					htmlid = "elli2";
				}
				break;
			case 70842: // 마르바
				if (player.getLawful() <= -501) {
					htmlid = "marba1";
				} else if (!player.isElf()) {
					htmlid = "marba2";
				} else if (player.getInventory().checkItem(40665)
						&& (player.getInventory().checkItem(40693) || player.getInventory().checkItem(40694)
								|| player.getInventory().checkItem(40695) || player.getInventory().checkItem(40697)
								|| player.getInventory().checkItem(40698) || player.getInventory().checkItem(40699))) {
					htmlid = "marba8";
				} else if (player.getInventory().checkItem(40665)) {
					htmlid = "marba17";
				} else if (player.getInventory().checkItem(40664)) {
					htmlid = "marba19";
				} else if (player.getInventory().checkItem(40637)) {
					htmlid = "marba18";
				} else {
					htmlid = "marba3";
				}
				break;
			case 70854: // 후린달렌
				if (player.isCrown() || player.isKnight() || player.isWizard() || player.isWarrior()) {
					htmlid = "hurinM1";
				} else if (player.isDarkelf()) {
					htmlid = "hurinE3";
				} else if (player.isDragonknight()) {
					htmlid = "hurinE4";
				} else if (player.isBlackwizard()) {
					htmlid = "hurinE5";
				}
				break;
			case 70839: // 도에트
				if (player.isCrown() || player.isKnight() || player.isWizard() || player.isWarrior()) {
					htmlid = "doettM1";
				} else if (player.isDarkelf()) {
					htmlid = "doettM2";
				} else if (player.isDragonknight()) {
					htmlid = "doettM3";
				} else if (player.isBlackwizard()) {
					htmlid = "doettM4";
				}
				break;
			case 70843: // 모리엔
				if (player.isCrown() || player.isKnight() || player.isWizard() || player.isWarrior()) {
					htmlid = "morienM1";
				} else if (player.isDarkelf()) {
					htmlid = "morienM2";
				} else if (player.isDragonknight()) {
					htmlid = "morienM3";
				} else if (player.isBlackwizard()) {
					htmlid = "morienM4";
				}
				break;
			case 70849: // 테오도르
				if (player.isCrown() || player.isKnight() || player.isWizard() || player.isWarrior()) {
					htmlid = "theodorM1";
				} else if (player.isDarkelf()) {
					htmlid = "theodorM2";
				} else if (player.isDragonknight()) {
					htmlid = "theodorM3";
				} else if (player.isBlackwizard()) {
					htmlid = "theodorM4";
				}
				break;
			case 5131: // 다크엘프 생존자
				if (!player.isGhost()) {
					htmlid = "exitkir1";
				} else {
					htmlid = "exitkir";
				}
				break;
			case 5133: // 감시자의 눈
				if (!player.isGhost()) {
					htmlid = "exitghostel1";
				} else {
					htmlid = "exitghostel";
				}
				break;
			case 71126: // 이리스
				if (player.getLevel() < Config.라던입장레벨) {
					htmlid = "eris21";
				} else {
					htmlid = "eris1";
				}
				break;
			default:
				break;
			}
			// html 표시 패킷 송신
			if (htmlid != null) { // htmlid가 지정되고 있는 경우
				if (htmldata != null) { // html 지정이 있는 경우는 표시
					SC_NPCTalkReturn.send(player, objid, htmlid, htmldata);
				} else {
					SC_NPCTalkReturn.send(player, objid, htmlid);
				}
			} else {
				if (player.getLawful() < -1000) { // 플레이어가 카오틱
					SC_NPCTalkReturn.send(player, talking, objid, 2);
				} else {
					SC_NPCTalkReturn.send(player, talking, objid, 1);
				}
			}
		}
	}


	private static void applyBeginnerSupport(L1PcInstance player) {
		final int targetId = player.getId();
		final int targetX = player.getX();
		final int targetY = player.getY();

		player.setBuffnoch(1);

		try {
			for (int i = 0; i < BEGINNER_BUFF_SKILLS.length; i++) {
				L1SkillUse.cast(
						player,
						BEGINNER_BUFF_SKILLS[i],
						targetId,
						targetX,
						targetY,
						null,
						0,
						L1SkillUse.TYPE_GMBUFF);
			}
		} finally {
			player.setBuffnoch(0);
		}

		player.setCurrentHp(player.getMaxHp());
		player.setCurrentMp(player.getMaxMp());
	}


	private static String talkToTownadviser(L1PcInstance pc, int town_id) {
		String htmlid;
		if (pc.getHomeTownId() == town_id && TownTable.getInstance().isLeader(pc, town_id)) {
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

	private static boolean checkHasCastle(
			L1PcInstance player,
			int castleId) {

		if (player == null || player.getClanid() == 0) {
			return false;
		}

		final L1Clan clan =
				L1World.getInstance().getClan(player.getClanid());

		return clan != null && clan.getCastleId() == castleId;
	}

	private static boolean checkClanLeader(L1PcInstance player) {
		if (player == null || !player.isCrown() || player.getClanid() == 0) {
			return false;
		}

		final L1Clan clan =
				L1World.getInstance().getClan(player.getClanid());

		return clan != null && player.getId() == clan.getLeaderId();
	}

	private static int getNecessarySealCount(L1PcInstance pc) {
		int rulerCount = 0;

		if (pc.getInventory().checkItem(40917)) {
			rulerCount++;
		}
		if (pc.getInventory().checkItem(40920)) {
			rulerCount++;
		}
		if (pc.getInventory().checkItem(40918)) {
			rulerCount++;
		}
		if (pc.getInventory().checkItem(40919)) {
			rulerCount++;
		}

		switch (rulerCount) {
		case 1:
			return 100;
		case 2:
			return 200;
		case 3:
			return 500;
		default:
			return 10;
		}
	}

	private void 멘트(L1PcInstance pc) {
		SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "상인에게서 판매하는 무기는 확률적으로 획득 할 수 있습니다.");
	}

	private void 경험치멘트(L1PcInstance pc) {
		
		  SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f<[ Lv: "+ Config.경험치지급단 +" ]\\f:까지 경험치를 지급해드립니다.");
//		  pc.sendPackets(new SC_SystemMessage("\\aA시스템: 경험치 지급은 \\aG[" +
//		  Config.경험치지급단 + "]\\aA 까지 가능합니다"));
		 
	}
	
	private void 혈맹포인트(L1PcInstance pc) {
		SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f=현재 혈맹의 군주만 [혈맹포인트]로 구매가능한 상점입니다");

		SC_SystemMessage.send(pc, "\\f=현재 혈맹의 군주만 [혈맹포인트]로 구매가능한 상점입니다");

		final L1Clan clan = pc.getClan();

		if (clan != null && pc.getId() == clan.getLeaderId()) {
			final String pointMessage =
					"\\f=현재 혈맹포인트 : [" + clan.getWarPoint() + "]";

			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, pointMessage);

			SC_SystemMessage.send(pc, pointMessage);
		}
	}

	private void 혈맹포인트1(L1PcInstance pc) {
		SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f=혈맹의 군주만 [혈맹포인트]로 구매가능한 상점입니다");

		final L1Clan clan = pc.getClan();

		if (clan != null) {
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f=현재 혈맹포인트 : [" + clan.getWarPoint() + "]");
		}
	}

	private void 혈맹포인트3(L1PcInstance pc) {
		SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f=혈맹의 군주만 [혈맹포인트]로 구매가능한 상점입니다");
	}


	private void createRuler(L1PcInstance pc, int attr, int sealCount) {
		final int rulerId;
		final int protectionId;
		final int sealId;

		switch (attr) {
		case 1:
			rulerId = 40917;
			protectionId = 40909;
			sealId = 40913;
			break;
		case 2:
			rulerId = 40919;
			protectionId = 40911;
			sealId = 40915;
			break;
		case 4:
			rulerId = 40918;
			protectionId = 40910;
			sealId = 40914;
			break;
		case 8:
			rulerId = 40920;
			protectionId = 40912;
			sealId = 40916;
			break;
		default:
			return;
		}

		pc.getInventory().consumeItem(protectionId, 1);
		pc.getInventory().consumeItem(sealId, sealCount);

		final L1ItemInstance item =
				pc.getInventory().storeItem(rulerId, 1);

		if (item != null) {
			SC_ServerMessage.send(pc, 143, getNpcTemplate().get_name(), item.getLogName());
		}
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
			if (pc.getInventory().checkItem(41209) || pc.getInventory().checkItem(41210)
					|| pc.getInventory().checkItem(41211) || pc.getInventory().checkItem(41212)) {
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
		if (pc.getQuest().get_step(L1Quest.QUEST_TBOX1) == L1Quest.QUEST_END) {
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
		if (pc.getQuest().get_step(L1Quest.QUEST_TBOX2) == L1Quest.QUEST_END) {
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

	private static final long REST_MILLISEC = 10000;

	private AtomicInteger _restCallCount;

	public class RestMonitor implements Runnable {
		@Override
		public void run() {
			if (_restCallCount.decrementAndGet() == 0) {
				setRest(false);
			}
		}

	}
}
