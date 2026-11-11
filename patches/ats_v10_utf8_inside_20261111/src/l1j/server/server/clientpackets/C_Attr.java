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

package l1j.server.server.clientpackets;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.CharacterAction.Teleport.Teleport;
import l1j.server.Templates.L1House;
import l1j.server.Templates.L1Pet;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.WarSystem.War;
import l1j.server.WarSystem.WarFactory;
import l1j.server.configure.Config;
import l1j.server.server.GameClient;
import l1j.server.server.UserCommands;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1ChatParty; //## A140 채팅 파티 추가 
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_CharVisualUpdate;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_Emblem;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_Paralysis;
import l1j.server.server.serverpackets.S_Resurrection;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_Trade;
import l1j.server.server.serverpackets.S_WorldObject;
import l1j.server.server.serverpackets.S_bonusstats;

public class C_Attr extends ClientBasePacket {

	private static final Logger _log = Logger.getLogger(C_Attr.class.getName());
	private static final String C_ATTR = "[C] C_Attr";
	 

	public C_Attr(byte abyte0[], GameClient clientthread) throws Exception {
		super(abyte0);
		int i = readH();  
		int c;
		String name;
		

		L1PcInstance pc = clientthread.getActiveChar();
		if (pc == null) {
			return;
		}  
		if (pc.hasSkillEffect(SkillId.어스_바인드) && i != 622) {
			return;
		}
		 
		switch (i) {  
		case 622:
			c = readC();
			if (pc.waitQuiz()) {
				if (c != 0) {
					UserCommands.getInstance().setQuiz(pc, pc.getTempQuiz());
				}
			}
			break;
		case 97: // %0가 혈맹에 가입했지만은 있습니다. 승낙합니까? (Y/N)
			c = readC();
			L1PcInstance joinPc = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			if (joinPc != null) {
				if (c == 0) { // No
					joinPc.sendPackets(new S_ServerMessage(96, pc.getName())); // \f1%0은 당신의 요청을 거절했습니다.
				} else if (c == 1) { // Yes
					int clan_id = pc.getClanid();
					String clanName = pc.getClanname();
					L1Clan clan = L1World.getInstance().getClan(clanName);
					if (clan != null) {
						int maxMember = 0;
						int charisma = pc.getAbility().getTotalCha();
						boolean lv45quest = false;
						if (pc.getQuest().isEnd(L1Quest.QUEST_LEVEL45)) {
							lv45quest = true;
						}
						if (pc.getLevel() >= 50) { // Lv50 이상
							if (lv45quest == true) { // Lv45 퀘스트 클리어가 끝난 상태
								maxMember = charisma * 9;
							} else {
								maxMember = charisma * 3;
							}
						} else { // Lv50 미만
							if (lv45quest == true) { // Lv45 퀘스트 클리어가 끝난 상태
								maxMember = charisma * 6;
							} else {
								maxMember = charisma * 2;
							}
						} 
						if (Config.MAX_CLAN_MEMBER > 0) { // Clan 인원수의 상한의 설정 있어
							maxMember = Config.MAX_CLAN_MEMBER;
						}

						if (joinPc.getClanid() == 0) { // 크란미가입
							
							if (Config.ACCOUNT_CLAN_JOIN) {
								if (joinPc.getClanid() == 0) {
									boolean Checking = joinPc.AccountClanCheck(joinPc.getNetConnection().getAccountName(),
											clan.getClanId());
									if (Checking == true) { // 상대의 클랜아디이와 다르면 가입을 거부시킨다.
										joinPc.sendPackets(new S_SystemMessage("당신의 계정내 한캐릭은 이미 다른 혈맹에 가입중입니다. "));
										joinPc.sendPackets(new S_SystemMessage("한 계정내 캐릭들은  같은 혈맹만 가입 가능합니다. "));
										return;
									}
								}
							}
							
							
							
							String clanMembersName[] = clan.getAllMembers();
							if (maxMember <= clanMembersName.length) { // 빈 곳이 없다
								// %0는 당신을 혈맹원으로서 받아들일 수가 없습니다.
								joinPc.sendPackets(new S_ServerMessage(188, pc.getName()));
								return;
							}
							for (L1PcInstance clanMembers : clan.getOnlineClanMember()) {
								clanMembers.sendPackets(new S_ServerMessage(94, joinPc.getName())); // \f1%0이 혈맹의 일원으로서
																									// 받아들여졌습니다.
							}
							joinPc.setClanid(clan_id);
							joinPc.setClanname(clanName);
							joinPc.setClanRank(L1Clan.CLAN_RANK_PUBLIC);
							joinPc.save(); // DB에 캐릭터 정보를 기입한다
							clan.addMemberName(joinPc.getName());
							joinPc.sendPackets(new S_ServerMessage(95, clanName),true); // \f1%0 혈맹에 가입했습니다.
							//joinPc.sendPackets(new S_PacketBox(S_PacketBox.MSG_RANK_CHANGED, 0x07, joinPc.getName()));
							
							joinPc.sendPackets(new S_Emblem(clan.getEmblemId()), true); 
							
							joinPc.teleport(joinPc.getX(), joinPc.getY(), joinPc.getMapId(), joinPc.getHeading(), 169, false, false);
						} else { // 크란 가입이 끝난 상태(크란 연합)
							if (Config.CLAN_ALLIANCE) {
								changeClan(clientthread, pc, joinPc, maxMember);
							} else {
								joinPc.sendPackets(new S_ServerMessage(89)); // \f1당신은 벌써 혈맹에 가입하고 있습니다.
							}
						}
					}
				}
			}
			break;
		 

		case 217: // %0혈맹의%1가 당신의 혈맹과의 전쟁을 바라고 있습니다.전쟁에 응합니까? (Y/N)
		case 221: // %0혈맹이 항복을 바라고 있습니다.받아들입니까? (Y/N)
		case 222: // %0혈맹이 전쟁의 종결을 바라고 있습니다.종결합니까? (Y/N)
			c = readC();
			L1PcInstance enemyLeader = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			if (enemyLeader == null) {
				return;
			}
			pc.setTempID(0);
			String clanName = pc.getClanname();
			String enemyClanName = enemyLeader.getClanname();
			if (c == 0) { // No
				if (i == 217) {
					enemyLeader.sendPackets(new S_ServerMessage(236, clanName)); // %0혈맹이 당신의  혈맹과의 전쟁을 거절했습니다.
				} else if (i == 221 || i == 222) {
					enemyLeader.sendPackets(new S_ServerMessage(237, clanName)); // %0혈맹이  당신의 제안을 거절했습니다.
				}
			} else if (c == 1) { // Yes
				L1Clan defense = L1World.getInstance().findClan(clanName);
				L1Clan offense = L1World.getInstance().findClan(enemyClanName);
				if(defense == null || offense == null)
					return;
				
				if (i == 217) {
					War war = WarFactory.createNormalWar(defense);
					war.register(offense);
				} else{
					War war = offense.getCurrentWar();
					if(war == null)
						return;
					
					war.notifySurrender(defense, offense);
					war.dispose();
				}
			}
			break;

		case 252: // %0%s가 당신과 아이템의 거래를 바라고 있습니다. 거래합니까? (Y/N)
			c = readC();
			if (pc.getTradeID() == 0 || pc.getTradeReady())
				return;
			L1Object trading_partner = L1World.getInstance().findObject(pc.getTradeID());
			if (trading_partner != null) {
				if (trading_partner instanceof L1PcInstance) {
					L1PcInstance target = (L1PcInstance) trading_partner;
					if (target.getTradeReady())
						return;
					if (c == 0) { // No
						target.sendPackets(new S_ServerMessage(253, pc.getName()));
						// %0%d는 당신과의 거래에 응하지 않았습니다.
						pc.setTradeID(0);
						target.setTradeID(0);
						pc.setTradeReady(false);
						target.setTradeReady(false);
					} else if (c == 1) { // Yes
						pc.sendPackets(new S_Trade(target.getName()));
						target.sendPackets(new S_Trade(pc.getName()));
						pc.setTradeReady(true);
						target.setTradeReady(true);
					}
				}

			}
			break;

		case 321: // 또 restore하고 싶습니까? (Y/N)
			c = readC();
			L1PcInstance resusepc1 = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			if (resusepc1 != null) { // restore 스크롤
				if (c == 0) { // No
					;
				} else if (c == 1) { // Yes
					ArrayList<L1Object> allList2 = L1World.getInstance().getVisibleObjects(resusepc1, 0);
					if(allList2.size() > 0){
						pc.sendPackets(new S_ServerMessage(592));
						return;
					}
					S_SkillSound s = S_SkillSound.get(pc.getId(), '\346');
					pc.sendPackets(s);
					pc.broadcastPacket(s);
					// pc.resurrect(pc.getLevel());
					// pc.setCurrentHp(pc.getLevel());
					pc.resurrect(pc.getMaxHp() / 2);
					pc.setCurrentHp(pc.getMaxHp() / 2);
					pc.startHpMpRegeneration();
					pc.startMpRegenerationByDoll();
					pc.sendPackets(new S_Resurrection(pc, resusepc1, 0));
					pc.broadcastPacket(new S_Resurrection(pc, resusepc1, 0));
					S_CharVisualUpdate ss = S_CharVisualUpdate.get(pc);
					pc.sendPackets(ss);
					pc.broadcastPacket(ss);
				}
			}
			break;

		case 322: // 또 restore하고 싶습니까? (Y/N)
			c = readC();
			L1PcInstance resusepc2 = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);  
			if (resusepc2 != null) { // 축복된 restore 스크롤, 리자레크션, 그레이타리자레크션
				if (c == 0) {  // No
					;
				} else if (c == 1) {  // Yes
					ArrayList<L1Object> allList2 = L1World.getInstance().getVisibleObjects(resusepc2, 0);
					if(allList2.size() > 0){
						pc.sendPackets(new S_ServerMessage(592));
						return;
					} 
					S_SkillSound s = S_SkillSound.get(pc.getId(), '\346');
					pc.sendPackets(s);
					pc.broadcastPacket(s);
					pc.resurrect(pc.getMaxHp());
					pc.setCurrentHp(pc.getMaxHp());
					pc.startHpMpRegeneration();
					pc.startMpRegenerationByDoll();
					pc.sendPackets(new S_Resurrection(pc, resusepc2, 0));
					pc.broadcastPacket(new S_Resurrection(pc, resusepc2, 0));
					S_CharVisualUpdate ss = S_CharVisualUpdate.get(pc);
					pc.sendPackets(ss);
					pc.broadcastPacket(ss);
					// EXP 로스트 하고 있는, G-RES를 걸 수 있던, EXP 로스트 한 사망
					// 모두를 채우는 경우만 EXP 복구
					if (pc.getExpRes() == 1 && pc.isGres() && pc.isGresValid()) {
						pc.resExp();
						pc.setExpRes(0);
						pc.setGres(false);
					}
				}
			}
			break;

		case 325: // 동물의 이름을 결정해 주세요：
			c = readC(); // ?
			name = readS();
			PetInstance pet = (PetInstance) L1World.getInstance()
					.findObject(pc.getTempID());
			pc.setTempID(0);
			renamePet(pet, name);
			break;

		case 512: // 가의 이름은?
			c = readC(); // ?
			name = readS();
			int houseId = pc.getTempID();
			pc.setTempID(0);
			if (name.length() <= 16) {
				L1House house = HouseTable.getInstance().getHouseTable(houseId);
				house.setHouseName(name);
				HouseTable.getInstance().updateHouse(house); // DB에 기입해
			} else {
				pc.sendPackets(new S_ServerMessage(513)); // 가의 이름이 너무 깁니다.
			}
			break;

		case 653: // 이혼을 하면(자) 링은 사라져 버립니다.이혼을 바랍니까? (Y/N)
			c = readC();
			if (c == 0) { // No
				;
			} else if (c == 1) { // Yes
				pc.setPartnerId(0);
				pc.save(); // DB에 캐릭터 정보를 기입한다
			}
			break;

		case 654: // %0%s당신과 결혼 하고 싶어하고 있습니다.%0과 결혼합니까? (Y/N)
			c = readC();
			L1PcInstance partner = (L1PcInstance) L1World.getInstance()
					.findObject(pc.getTempID());
			pc.setTempID(0);
			if (partner != null) {
				if (c == 0) { // No
					partner.sendPackets(new S_ServerMessage( // %0%s는 당신과의 결혼을 거절했습니다.
							656, pc.getName()));
				} else if (c == 1) { // Yes
					pc.setPartnerId(partner.getId());
					pc.save();
					pc.sendPackets(new S_ServerMessage( // 모두의 축복 중(안)에서, 두 명의 결혼을 했습니다.
							790));
					pc.sendPackets(new S_ServerMessage( // 축하합니다!%0과 결혼했습니다.
							655, partner.getName()));

					partner.setPartnerId(pc.getId());
					partner.save();
					partner.sendPackets(new S_ServerMessage( // 모두의 축복 중(안)에서, 두 명의 결혼을 했습니다.
							790));
					partner.sendPackets(new S_ServerMessage( // 축하합니다!%0과 결혼했습니다.
							655, pc.getName()));
				}
			}
			break;

		case 738: // 경험치를 회복하려면%0의 아데나가 필요합니다.경험치를 회복합니까?
			c = readC();
			if (c == 0) { // No
				;
			} else if (c == 1) { // Yes
				int cost = 0;
				int level = pc.getLevel();
				int lawful = pc.getLawful();
				if (level < 45) {
					cost = level * level * 100;
				} else {
					cost = level * level * 200;
				}
				if (lawful >= 0) {
					cost = (cost / 2);
				}
				if (pc.getInventory().consumeItem(ItemId.아데나, cost)) {
					pc.resExp();
					pc.setExpRes(0);
				} else {
					pc.sendPackets(new S_ServerMessage(189)); // \f1아데나가 부족합니다.
				}
			}
			break;

		// 콜 클랜
		case 748: // 혈맹원이 당신을 텔레포트 시키려고 하고 있습니다.응합니까? (Y/N)
			c = readC();
			if (c == 0) { // No
				;
			} else if (c == 1) { // Yes
				L1PcInstance callClanPc = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
				pc.setTempID(0);
				if (callClanPc != null) {
					if (pc.getMap().isEscapable() || pc.isGm()) {
						boolean isInWarArea = false;
						int castleId = L1CastleLocation.getCastleIdByArea(callClanPc);
						if (castleId != 0) {
							isInWarArea = true;
							if (CastleWarBusiness.getInstance().isNowWar(castleId)) {
								isInWarArea = false;
							}
						}
						if ((callClanPc.getMapId() == 0 || callClanPc.getMapId() == 4 || callClanPc.getMapId() == 304)
								&& !isInWarArea) {										
							Teleport.Collteleport(pc, callClanPc.getLocation(), 5, true);
						} else {
							pc.sendPackets(new S_ServerMessage(547)); // \f1당신의 파트너는 지금 당신이 갈 수 없는 곳에서 플레이중입니다.
						}
					} else {
						pc.sendPackets(new S_ServerMessage(647));
						pc.sendPackets(new S_Paralysis(S_Paralysis.TYPE_TELEPORT_UNLOCK, false)); 
					}
				}
			}
			break;

		case 951: // 채팅 파티 초대를 허가합니까? (Y/N)
			c = readC();
			L1PcInstance chatPc = (L1PcInstance) L1World.getInstance().findObject(pc.getPartyID());
			if (chatPc != null) {
				if (c == 0) { // No
					chatPc.sendPackets(new S_ServerMessage(423, pc.getName())); // %0가 초대를 거부했습니다.
					pc.setPartyID(0);
				} else if (c == 1) { // Yes
					if (chatPc.isInChatParty()) {
						if (chatPc.getChatParty().isVacancy() || chatPc.isGm()) {
							chatPc.getChatParty().addMember(pc);
						
						} else {
							chatPc.sendPackets(new S_ServerMessage(417)); // 더 이상 파티 멤버를 받아들일 수 없습니다.
						}
					} else {
						L1ChatParty chatParty = new L1ChatParty();
						chatParty.addMember(chatPc);
						chatParty.addMember(pc);
						chatPc.sendPackets(new S_ServerMessage(424, pc.getName())); // %0가 파티에 들어갔습니다. 
					}
				}
			}
			break;
		case 953: // 파티 초대를 허가합니까? (Y/N)
		case 954:
			c = readC();
			L1PcInstance target = (L1PcInstance) L1World.getInstance().findObject(pc.getPartyID());
			if (target != null) {
				if (c == 0) // No
				{
					target.sendPackets(new S_ServerMessage(423, pc.getName()));
					pc.setPartyID(0);
				} else if (c == 1) // Yes
				{
					if (target.isInParty()) {
						// 초대주가 파티중
						if (target.getParty().isVacancy() || target.isGm()) {
							// 파티에 빈 곳이 있다
							target.getParty().addMember(pc);
						} else {
							// 파티에 빈 곳이 없다
							target.sendPackets(new S_ServerMessage(417));
						}
					} else {
						// 초대주가 파티중이 아니다
						L1Party party = new L1Party();
						party.addMember(target);
						party.addMember(pc);
						target.sendPackets(new S_ServerMessage(424, pc.getName()));
					}
				}
			}
			break;

		case 479: // 어느 능력치를 향상시킵니까? (str, dex, int, con, wis, cha)
 
			if (readC() == 1) { 
				String s = readS(); 
				if (!(pc.getLevel() - 50 > pc.getBonusStats())) {  
					return;
				}
				if (pc.isTwoLogin(pc)) {
					return;
				}
				if (pc.getOnlineStatus() != 1) {		// 127 스텟 버그 수정	
					pc.sendPackets(new S_Disconnect());
				    return;
				}   
				if (s.toLowerCase().equals("str".toLowerCase())) {
				
					if (pc.getAbility().getStr() < 35) {
						pc.getAbility().addStr((byte) 1); // 소의 STR치에+1
						pc.setBonusStats(pc.getBonusStats() + 1);
						pc.sendPackets(new S_OwnCharStatus(pc));
						pc.sendPackets(S_CharVisualUpdate.get(pc));						
						pc.save(); // DB에 캐릭터 정보를 기입한다
						pc.sendPackets(new S_SystemMessage("       * 보너스 스텟을 확인 합니다 *    "));
						pc.sendPackets(new S_SystemMessage(" STR: " +pc.getAbility().getStr()+
								" DEX:"+pc.getAbility().getDex()+
								" CON:"+pc.getAbility().getCon()+
								" INT:"+pc.getAbility().getInt()+
								" WIS:"+pc.getAbility().getWis()+
								" CHA:"+pc.getAbility().getCha()));
					} else {
						pc.sendPackets(new S_ServerMessage(481));
					}
				} else if (s.toLowerCase().equals("dex".toLowerCase())) {
					if (pc.getAbility().getDex() < 35) {
						pc.getAbility().addDex((byte) 1); // 소의 DEX치에+1
						pc.resetBaseAc();
						pc.setBonusStats(pc.getBonusStats() + 1);
						pc.sendPackets(new S_OwnCharStatus(pc));
						pc.sendPackets(S_CharVisualUpdate.get(pc));
						pc.save(); // DB에 캐릭터 정보를 기입한다
						pc.sendPackets(new S_SystemMessage("       * 보너스 스텟을 확인 합니다 *    "));
						pc.sendPackets(new S_SystemMessage(" STR: " +pc.getAbility().getStr()+
								" DEX:"+pc.getAbility().getDex()+
								" CON:"+pc.getAbility().getCon()+
								" INT:"+pc.getAbility().getInt()+
								" WIS:"+pc.getAbility().getWis()+
								" CHA:"+pc.getAbility().getCha()));
					} else {
						pc.sendPackets(new S_ServerMessage(481));
						// 하나의 능력치의 최대치는 25입니다. 다른 능력치를 선택해 주세요
					}
				} else if (s.toLowerCase().equals("con".toLowerCase())) {
					if (pc.getAbility().getCon() < 35) {
						pc.getAbility().addCon((byte) 1); // 소의 CON치에+1
						pc.setBonusStats(pc.getBonusStats() + 1);
						pc.sendPackets(new S_OwnCharStatus(pc));
						pc.sendPackets(S_CharVisualUpdate.get(pc));
						pc.save(); // DB에 캐릭터 정보를 기입한다
						pc.sendPackets(new S_SystemMessage("       * 보너스 스텟을 확인 합니다 *    "));
						pc.sendPackets(new S_SystemMessage(" STR: " +pc.getAbility().getStr()+
								" DEX:"+pc.getAbility().getDex()+
								" CON:"+pc.getAbility().getCon()+
								" INT:"+pc.getAbility().getInt()+
								" WIS:"+pc.getAbility().getWis()+
								" CHA:"+pc.getAbility().getCha()));
					} else {
						pc.sendPackets(new S_ServerMessage(481)); 
						// 하나의 능력치의 최대치는 25입니다. 다른 능력치를 선택해 주세요
					}
				} else if (s.toLowerCase().equals("int".toLowerCase())) {
					if (pc.getAbility().getInt() < 35) {
						pc.getAbility().addInt((byte) 1); // 소의 INT치에+1
						pc.setBonusStats(pc.getBonusStats() + 1);
						pc.sendPackets(new S_OwnCharStatus(pc));
						pc.sendPackets(S_CharVisualUpdate.get(pc));
						pc.save(); // DB에 캐릭터 정보를 기입한다
						pc.sendPackets(new S_SystemMessage("       * 보너스 스텟을 확인 합니다 *    "));
						pc.sendPackets(new S_SystemMessage(" STR: " +pc.getAbility().getStr()+
								" DEX:"+pc.getAbility().getDex()+
								" CON:"+pc.getAbility().getCon()+
								" INT:"+pc.getAbility().getInt()+
								" WIS:"+pc.getAbility().getWis()+
								" CHA:"+pc.getAbility().getCha()));
					} else {
						pc.sendPackets(new S_ServerMessage(481)); 
						// 하나의 능력치의 최대치는 25입니다. 다른 능력치를 선택해 주세요
					}
				} else if (s.toLowerCase().equals("wis".toLowerCase())) {
					if (pc.getAbility().getWis() < 35) {
						pc.getAbility().addWis((byte) 1); // 소의 WIS치에+1
						pc.resetBaseMr();
						pc.setBonusStats(pc.getBonusStats() + 1);
						pc.sendPackets(new S_OwnCharStatus(pc));
						pc.sendPackets(S_CharVisualUpdate.get(pc));
						pc.save(); // DB에 캐릭터 정보를 기입한다
						pc.sendPackets(new S_SystemMessage("       * 보너스 스텟을 확인 합니다 *    "));
						pc.sendPackets(new S_SystemMessage(" STR: " +pc.getAbility().getStr()+
								" DEX:"+pc.getAbility().getDex()+
								" CON:"+pc.getAbility().getCon()+
								" INT:"+pc.getAbility().getInt()+
								" WIS:"+pc.getAbility().getWis()+
								" CHA:"+pc.getAbility().getCha()));
					} else {
						pc.sendPackets(new S_ServerMessage(481)); 
						// 하나의 능력치의 최대치는 25입니다. 다른 능력치를 선택해 주세요
					}
				} else if (s.toLowerCase().equals("cha".toLowerCase())) { 
					if (pc.getAbility().getCha() < 35) {
						pc.getAbility().addCha((byte) 1); // 소의 CHA치에+1
						pc.setBonusStats(pc.getBonusStats() + 1);
						pc.sendPackets(new S_OwnCharStatus(pc));
						pc.sendPackets(S_CharVisualUpdate.get(pc));
						pc.save(); // DB에 캐릭터 정보를 기입한다 
						pc.sendPackets(new S_SystemMessage("       * 보너스 스텟을 확인 합니다 *    "));
						pc.sendPackets(new S_SystemMessage(" STR: " +pc.getAbility().getStr()+
								" DEX:"+pc.getAbility().getDex()+
								" CON:"+pc.getAbility().getCon()+
								" INT:"+pc.getAbility().getInt()+
								" WIS:"+pc.getAbility().getWis()+
								" CHA:"+pc.getAbility().getCha()));
					} else {
						pc.sendPackets(new S_ServerMessage(481)); 
						// 하나의 능력치의 최대치는 25입니다. 다른 능력치를 선택해 주세요
					}
				}
				if (pc.getLevel() >= 51 && pc.getLevel() - 50 > pc.getBonusStats()) {
					if ((pc.getAbility().getStr() + pc.getAbility().getDex() + pc.getAbility().getCon()
							+ pc.getAbility().getInt() + pc.getAbility().getWis() + pc.getAbility().getCha()) < 150) {
						pc.sendPackets(new S_bonusstats(pc.getId(), 1));
					}
				}
				pc.CheckStatus();
			}
			break;
		default:
			break;
		}
	}

	private void changeClan(GameClient clientthread, L1PcInstance pc, L1PcInstance joinPc, int maxMember) {
		int clanId = pc.getClanid();
		String clanName = pc.getClanname();
		L1Clan clan = L1World.getInstance().getClan(clanName);
		String clanMemberName[] = clan.getAllMembers();
		int clanNum = clanMemberName.length;

		int oldClanId = joinPc.getClanid();
		String oldClanName = joinPc.getClanname();
		L1Clan oldClan = L1World.getInstance().getClan(oldClanName);
		String oldClanMemberName[] = oldClan.getAllMembers();
		int oldClanNum = oldClanMemberName.length;
		if (clan != null && oldClan != null && joinPc.isCrown() && // 자신이 군주
				joinPc.getId() == oldClan.getLeaderId()) {
			if (maxMember < clanNum + oldClanNum) { // 빈 곳이 없다
				joinPc.sendPackets( // %0는 당신을 혈맹원으로서 받아들일 수가 없습니다.
						new S_ServerMessage(188, pc.getName()));
				return;
			}
			L1PcInstance clanMember[] = clan.getOnlineClanMember();
			for (int cnt = 0; cnt < clanMember.length; cnt++) {
				clanMember[cnt].sendPackets(new S_ServerMessage(94, joinPc.getName())); // \f1%0이 혈맹의 일원으로서 받아들여졌습니다.
			}

			for (int i = 0; i < oldClanMemberName.length; i++) {
				L1PcInstance oldClanMember = L1World.getInstance().getPlayer(oldClanMemberName[i]);
				if (oldClanMember != null) { // 온라인중의 구크란 멤버
					oldClanMember.setClanid(clanId);
					oldClanMember.setClanname(clanName);
					// 혈맹 연합에 가입한 군주는 가디안
					// 군주가 데려 온 혈맹원은 본받아
					if (oldClanMember.getId() == joinPc.getId()) {
						oldClanMember.setClanRank(L1Clan.CLAN_RANK_GUARDIAN);
					} else {
						oldClanMember.setClanRank(L1Clan.CLAN_RANK_PROBATION);
					}
					try {
						// DB에 캐릭터 정보를 기입한다
						oldClanMember.save();
					} catch (Exception e) {
						_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
					}
					clan.addMemberName(oldClanMember.getName());
					oldClanMember.sendPackets(new S_ServerMessage(95, clanName)); // \f1%0 혈맹에 가입했습니다.
					oldClanMember.sendPackets(new S_Emblem(clan.getEmblemId())); 
					oldClanMember.teleport(oldClanMember.getX(), oldClanMember.getY(), oldClanMember.getMapId(), oldClanMember.getHeading(),169, false, false);
 
				} else { // 오프 라인중의 구크란 멤버
					try {
						L1PcInstance offClanMember = CharacterTable.getInstance().restoreCharacter(oldClanMemberName[i]);
						offClanMember.setClanid(clanId);
						offClanMember.setClanname(clanName);
						offClanMember.setClanRank(L1Clan.CLAN_RANK_PROBATION);
						offClanMember.save(); // DB에 캐릭터 정보를 기입한다
						clan.addMemberName(offClanMember.getName());
						
					} catch (Exception e) {
						_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
					}
				}
			}
			// 구크란 삭제
			String emblem_file = String.valueOf(oldClanId);
			File file = new File("emblem/" + emblem_file);
			file.delete();
			ClanTable.getInstance().deleteClan(oldClanName);
		}
	}

	private static void renamePet(PetInstance pet, String name) {
		if (pet == null || name == null) {
			throw new NullPointerException();
		}

		int petItemObjId = pet.getItemObjId();
		L1Pet petTemplate = PetTable.getInstance().getTemplate(petItemObjId);
		if (petTemplate == null) {
			throw new NullPointerException();
		}

		L1PcInstance pc = (L1PcInstance) pet.getMaster();
		if (PetTable.isNameExists(name)) {
			pc.sendPackets(new S_ServerMessage(327)); // 같은 이름이 벌써 존재하고 있습니다.
			return;
		}
		pet.setName(name);
		petTemplate.set_name(name);
		PetTable.getInstance().storePet(petTemplate); // DB에 기입해
		pc.sendPackets(S_WorldObject.pet(pet, pc));
	}

	@Override
	public String getType() {
		return C_ATTR;
	}
}