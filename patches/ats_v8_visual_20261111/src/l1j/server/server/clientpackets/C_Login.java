/* This program is free software; you can redistribute it and/or modify
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



import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.commons;
import l1j.server.CTSystem.MJCTHandler;
import l1j.server.CTSystem.Loader.CTLoadManager;
import l1j.server.DungeonTimer.Loader.DungeonTimeProgressLoader;
import l1j.server.Templates.L1BookMark;
import l1j.server.Templates.L1EventNpcSpawn;
import l1j.server.Templates.L1GetBackRestart;
import l1j.server.Templates.L1House;
import l1j.server.Templates.L1Skills;
import l1j.server.WarSystem.CastleWar;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.WarSystem.War;
import l1j.server.configure.Config;
import l1j.server.server.Account;
import l1j.server.server.GameClient;
import l1j.server.server.GameServer;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.SkillCheck;
import l1j.server.server.Controller.EventNpcSpawnCotroller;
import l1j.server.server.Controller.LoginController;
import l1j.server.server.Controller.UserRankingController;
import l1j.server.server.datatables.CharacterSlotItemTable;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.EventNpcSpawnTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.GetBackRestartTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.datatables.WantedTelTable;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.item.Cooking;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.serverpackets.S_AddSkill;
import l1j.server.server.serverpackets.S_Bookmarks;
import l1j.server.server.serverpackets.S_CharAmount;
import l1j.server.server.serverpackets.S_CharVisualUpdate;
import l1j.server.server.serverpackets.S_CharacterConfig;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_HPUpdate;
import l1j.server.server.serverpackets.S_InvList;
import l1j.server.server.serverpackets.S_Liquor;
import l1j.server.server.serverpackets.S_MPUpdate;
import l1j.server.server.serverpackets.S_MapID;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ReturnedStat;
import l1j.server.server.serverpackets.S_SPMR;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillBrave;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillIconGFX;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_UnityIcon;
import l1j.server.server.serverpackets.S_Unknown1;
import l1j.server.server.serverpackets.S_Weather;
import l1j.server.server.serverpackets.S_WorldObject;
import l1j.server.server.serverpackets.S_bonusstats;
import l1j.server.server.utils.CheckInitStat;
import l1j.server.server.utils.MessageUtil;
import l1j.server.server.utils.SQLUtil;
import l1j.server.server.utils.SystemUtil;
import manager.SJManager;

public class C_Login extends ClientBasePacket {

	private static final String C_LOGIN = "[C] C_Login";
	private static Logger _log = Logger.getLogger(C_Login.class.getName());

	class BuffInfo {
		public int skillId;
		public int remainTime;
		public int polyId;
	}

	public C_Login(byte abyte0[], GameClient client) throws FileNotFoundException, Exception {
		super(abyte0);
		Calendar cal = Calendar.getInstance();

		int 시간 = Calendar.HOUR;
		int 분 = Calendar.MINUTE;

		/** 0 오전 , 1 오후 * */
		String 오전오후 = "오후";
		if (cal.get(Calendar.AM_PM) == 0) {
			오전오후 = "오전";
		}

		String login = client.getAccountName();

		String charName = readS();
		Account mjct_account = Account.load(login);
		String mjct_target_name = null;
		if (!commons.isNullOrEmpty(mjct_account.getMJCT_Char_Name())) {
			mjct_target_name = charName;
			charName = mjct_account.getMJCT_Char_Name();
		}
		L1PcInstance pc = L1PcInstance.load(charName);
		Account account = null;
		if(pc == null){ 
			System.out.println(String.format("캐릭터를 찾을 수 없습니다.(월드 접속) : %s(%s)", charName, client.getIp()));
			return;
		}
		if (pc.getAccountName() != null) {
			account = Account.load(pc.getAccountName());// 오류부근
		}

		if (client.getAccount() == null) {
			System.out.println("─────────────────────────────────");
			System.out.println("계정 Null 접속 시도 " + charName);
			System.out.println("─────────────────────────────────");
			client.kick();
			client.close();
			return;
		}
		if (client.getActiveChar() != null) {
			System.out.println("─────────────────────────────────");
			System.out.println("동일 ID의 중복 접속이므로 (" + client.getIp() + ")의 접속을 강제 종료합니다. #1");
			System.out.println("─────────────────────────────────");
			client.close();
			return;
		}
		GameClient clientByAccount = LoginController.getInstance().getClientByAccount(login);

		if (clientByAccount == null || clientByAccount != client) {
			System.out.println("─────────────────────────────────");
			System.out.println("동일 Account의 중복 접속이므로 (" + client.getIp() + ")의 접속을 강제 종료합니다. #1");
			System.out.println("─────────────────────────────────");
			client.close();
			return;
		}
		/** 2캐릭 버그 방지 Start */
		L1PcInstance OtherPc = L1World.getInstance().getPlayer(charName);

		if (OtherPc != null) {
			boolean isPrivateShop = OtherPc.isPrivateShop();
			GameServer.disconnectChar(OtherPc);
			OtherPc = null;
			if (isPrivateShop == false) {
				System.out.println("─────────────────────────────────");
				System.out.println("동일 ID의 중복 접속이므로 (" + client.getIp() + ")의 접속을 강제 종료합니다. #2");
				System.out.println("─────────────────────────────────");
				client.kick();
				return;
			}
		}
		Collection<L1PcInstance> pcs = L1World.getInstance().getAllPlayers();
		for (L1PcInstance bugpc : pcs) {
			if (bugpc.getAccountName().equals(client.getAccountName())) {
				if (!bugpc.isPrivateShop() || bugpc.getNetConnection() != null) {
					System.out.println("─────────────────────────────────");
					System.out.println("동일 Account의 중복 접속이므로 (" + client.getIp() + ")의 접속을 강제 종료합니다.");
					System.out.println("─────────────────────────────────");
					client.kick();
					GameServer.disconnectChar(bugpc);
				}
			}
		}
		pcs = null;
		/** 2캐릭 버그 방지 End */
		if ((pc == null) || !login.equals(pc.getAccountName())) {
			System.out.println("─────────────────────────────────");
			System.out.println("현재 계정에 없는 캐릭 접속시도 : " + charName + " 계정 : " + client.getAccountName());
			System.out.println("─────────────────────────────────");
			client.kick();
			client.close();
			return;
		}
		if (!pc.isGm() && Config.LEVEL_DOWN_RANGE != 0) {
			if (pc.getHighLevel() - pc.getLevel() >= Config.LEVEL_DOWN_RANGE) {
				System.out.println("─────────────────────────────────");
				_log.info("렙다운 허용범위 초과: " + charName + " 계정= " + login + " host=" + client.getIp());
				System.out.println("─────────────────────────────────");
				client.kick();
				return;
			}
		}
		if (pc.getLevel() == 1) {
			pc.setExp(ExpTable.getExpByLevel(Config.GOGO_LEVEL));
		}
		System.out.println("[" + 오전오후 + "] [" + cal.get(시간) + "시] [" + cal.get(분) + "분] [" + charName + "] [" + login + "] [" + client.getIp() + "] 메모리: [" + SystemUtil.getUsedMemoryMB() + "]");
		pc.setOnlineStatus(1);
		CharacterTable.updateOnlineStatus(pc);
		L1World.getInstance().storeObject(pc);

		pc.setNetConnection(client); 
		client.setActiveChar(pc);

		pc.sendPackets(new S_Unknown1(), true);

		bookmarks(pc);
		
		pc.setHeading(0);
		
		GetBackRestartTable gbrTable = GetBackRestartTable.getInstance();
		L1GetBackRestart[] gbrList = gbrTable.getGetBackRestartTableList();
		for (L1GetBackRestart gbr : gbrList) {
			if (pc.getMapId() == gbr.getArea()) {
				// RETURN이 마을로 복귀해야하는경우 클랜원일경우 클랜복귀장소로 복귀
				int[] loc = { gbr.getLocX(), gbr.getLocY(), gbr.getMapId()};
				int castle_id = 0;
				int house_id = 0;
				if (pc.getClanid() != 0) { // 크란 소속
					L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
					if (clan != null) {
						castle_id = clan.getCastleId();
						house_id = clan.getHouseId();
					}
				}
				if (castle_id != 0) { // 성주 크란원
					if (pc.getMapId() == 15)
						break; // 켄트내성이면 그대로
					loc = L1CastleLocation.getCastleLoc(castle_id);
				} else if (pc.getMapId() != 34 && house_id != 0) { // 아지트 소유 크란원
					L1House house = HouseTable.getInstance().getHouseTable(house_id);
					if (house.isPurchaseBasement()) {
						loc = L1HouseLocation.getBasementLoc(house_id);
					} else {
						loc = L1HouseLocation.getHouseLoc(house_id);
					}

				}
				pc.setX(loc[0]);
				pc.setY(loc[1]);
				pc.setMap((short) loc[2]);
			}
		}

		//  altsettings.properties로 GetBack가 true라면 거리에 이동시킨다
		if (Config.GET_BACK) {
			int[] loc = Getback.GetBack_Location(pc, true);
			pc.setX(loc[0]);
			pc.setY(loc[1]);
			pc.setMap((short) loc[2]);
		}

		if (WantedTelTable.getInstance().isMapZoneChange(pc.getMapId())) {
			if (pc.getHuntCount() == 0) {  
				pc.setX(33436);
				pc.setY(32802);
				pc.setMap((short) 4);
			}
		}
		// 전쟁중의 기내에 있었을 경우, 성주 혈맹이 아닌 경우는 귀환시킨다.
		int castle_id = L1CastleLocation.getCastleIdByArea(pc);
		if (pc.getMapId() == 66) {
			castle_id = 6;
		}
		if (0 < castle_id) {
			if (CastleWarBusiness.getInstance().isNowWar(castle_id)) {
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null && clan.getCastleId() != castle_id) {
					int[] loc = new int[3];
					loc = L1CastleLocation.getGetBackLoc(castle_id);
					pc.setX(loc[0]);
					pc.setY(loc[1]);
					pc.setMap((short) loc[2]);
					loc = null;
				} else if (pc.getMapId() == 4) {
					int[] loc = new int[3];
					loc = L1CastleLocation.getGetBackLoc(castle_id);
					pc.setX(loc[0]);
					pc.setY(loc[1]);
					pc.setMap((short) loc[2]);
					loc = null;
				}
			}
		}

		L1World.getInstance().addVisibleObject(pc);
		pc.beginGameTimeCarrier();
		pc.sendPackets(new S_Weather(L1World.getInstance().getWeather()), true);		
		pc.sendClanMarks();	
		pc.sendPackets(new S_OwnCharStatus(pc), true);
		pc.sendPackets(new S_MapID(pc.getMap().getBaseMapId(), pc.getMap().isUnderwater()), true);

		pc.sendPackets(S_WorldObject.in(pc), true);
		pc.sendPackets(new S_SPMR(pc), true);
		pc.sendVisualEffectAtLogin(); // 크라운, 독, 수중등의 시각 효과를 표시
		pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.LOGIN), true);

		itemsLoad(pc);
		int[] skillList = skillLoad(pc);
		sendSkillPacket(pc, skillList);
			
		List<BuffInfo> buffList = buffLoad(pc);
		buffProcess(pc, buffList);
	
		einHasadBuffLoad(pc);
     	pc.getLight().turnOnOffLight();

		L1PcInstance jonje = L1World.getInstance().getPlayer(pc.getName());
		if (jonje == null) {
			pc.sendPackets(new S_SystemMessage("존재버그 강제종료! 재접속하세요"), true);
			client.kick();
			return;
		}
		if (pc.getCurrentHp() > 0) {
			pc.setDead(false);
		} else {
			pc.setDead(true);
		}
		if (pc.getLevel() >= 51 && pc.getLevel() - 50 > pc.getBonusStats()) {
			if ((pc.getAbility().getAmount()) < 150) {
				pc.sendPackets(new S_bonusstats(pc.getId(), 1), true);
			}
		}
		if (Config.CHARACTER_CONFIG_IN_SERVER_SIDE) {
			pc.sendPackets(new S_CharacterConfig(pc.getId()), true);
		}
		
		if (pc.getLevel() <= Config.바포메트_시스템_보호_레벨) {// 바포메트 시스템 관련 처리
			pc.sendPackets(new S_PacketBox(S_PacketBox.BAPHOMET_SYSTEM, 6, true));
			pc.setNBapoLevel(7);
		} else {
			if (pc.getLawful() >= 0 && pc.getLawful() <= 9999) {
				pc.sendPackets(new S_PacketBox(S_PacketBox.BAPHOMET_SYSTEM, 7, true));
				pc.setNBapoLevel(7);
			} else {
				pc.sendPackets(new S_PacketBox(S_PacketBox.BAPHOMET_SYSTEM, pc.getNBapoLevel(), true));
			}
		}

		serchSummon(pc);

		CastleWarBusiness.getInstance().viewNowCastleWarState(pc);
		
		///수배관련
		if (pc.getHuntCount() > 0) {
			//pc.sendPackets(new S_SystemMessage("수배로 인한 근/원 대미지+3,공성+3,SP+3,ac-3 적용."));
			pc.initBeWanted();
			int[] beWanted = { 3, 3, 3, 3, 3, 3, 3 };
			pc.setBeWanted(beWanted);
			pc.addBeWanted();
			new SkillUse().handleCommands(pc, SkillId.수배_버프_아이콘, pc.getId(), pc.getX(), pc.getY(), null, 0, SkillUse.TYPE_GMBUFF);
		}
		

		if (pc.getClanid() != 0) { // 크란 소속중
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				if (pc.getClanid() == clan.getClanId() && // 크란을 해산해, 재차, 동명의 크란이 창설되었을 때의 대책
						pc.getClanname().toLowerCase().equals(clan.getClanName().toLowerCase())) {
					for (L1PcInstance clanMember : clan.getOnlineClanMember()) {
						if (clanMember.getId() != pc.getId()) {
							clanMember.sendPackets(new S_ServerMessage(843, pc.getName()), true); // 지금, 혈맹원의%0%s가 게임에 접속했습니다.
						}
					}
					War war = clan.getCurrentWar();
					if (war != null) {
						war.notifyEnenmy(pc);
						if (war instanceof CastleWar) {
							CastleWar castleWar = (CastleWar) war;
							if (castleWar.isRun()) {
								castleWar.onLordBuff(pc);
							}
						}
					}
				} else {
					pc.setClanid(0);
					pc.setClanname("");
					pc.setClanRank(0);
					pc.save(); // DB에 캐릭터 정보를 기입한다
				}
			} else {
				pc.setClanid(0);
				pc.setClanname("");
				pc.setClanRank(0);
				pc.save(); // DB에 캐릭터 정보를 기입한다
			}
		}

		if (pc.getPartnerId() != 0) { // 결혼중
			L1PcInstance partner = (L1PcInstance) L1World.getInstance().findObject(pc.getPartnerId());
			if (partner != null && partner.getPartnerId() != 0) {
				if (pc.getPartnerId() == partner.getId() && partner.getPartnerId() == pc.getId()) {
					pc.sendPackets(new S_ServerMessage(548), true); // 당신의 파트너는 지금 게임중입니다.
					partner.sendPackets(new S_ServerMessage(549), true); // 당신의 파트너는 방금 로그인했습니다.
				}
			}
		}
		pc.startObjectAutoUpdate();
		pc.startHpMpRegeneration();

		client.CharReStart(false);
		pc.beginExpMonitor();
		pc.save(); // DB에 캐릭터 정보를 기입한다
		pc.setDistributionMsg(false);
		pc.setWolrdKillMessage(true);
		pc.setDistributionlogin(false);
		SJManager.getInstance().LogConnectAppend(pc.getName(), client.getHostname());
		pc.sendPackets(new S_OwnCharStatus(pc), true);
		pc.sendPackets(new S_PacketBox(S_PacketBox.UPDATE_DG, pc.getDg()), true);
		
		if (CheckMail(pc) > 0) {
			pc.sendPackets(S_SkillSound.get(pc.getId(), 1091), true);
			pc.sendPackets(new S_ServerMessage(428), true); // 편지가 도착했습니다.
		}
		if (pc.getReturnStat() != 0) {
			SkillUse l1skilluse = new SkillUse();
			l1skilluse.handleCommands(pc, SkillId.캔슬레이션, pc.getId(), pc.getX(), pc.getY(), null, 0,
					SkillUse.TYPE_LOGIN);

			if (pc.getWeapon() != null) {
				pc.getInventory().setEquipped(pc.getWeapon(), false, false, false);
			}

			pc.sendPackets(S_CharVisualUpdate.get(pc), true);
			pc.sendPackets(new S_OwnCharStatus(pc), true);

			for (ItemInstance armor : pc.getInventory().getItems()) {
				for (int type = 0; type <= 12; type++) {
					if (armor != null) {
						pc.getInventory().setEquipped(armor, false, false, false);
					}
				}
			}
			pc.sendPackets(new S_SPMR(pc), true);
			pc.sendPackets(new S_OwnCharAttrDef(pc), true);
			pc.sendPackets(new S_OwnCharStatus(pc), true);
			pc.setReturnStatus(1);
			pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.START), true);

		} else if (!pc.LoadCheckStatus()) {
			if (!CheckInitStat.CheckPcStat(pc)) {
				pc.setReturnStat(pc.getExp());
				pc.setReturnStatus(1);
				pc.sendPackets(new S_ReturnedStat(pc, S_ReturnedStat.START), true);
				return;
			}
		}
		
		int currentHpAtLoad = pc.getMaxHp();
		int currentMpAtLoad = pc.getMaxMp();
		
		if (currentHpAtLoad < pc.getCurrentHp()) {
			pc.setCurrentHp(currentHpAtLoad);
		}
		if (currentMpAtLoad < pc.getCurrentMp()) {
			pc.setCurrentMp(currentMpAtLoad);
		}

		L1EventNpcSpawn event_map_check = EventNpcSpawnTable.getInstance().getEventMapCheck(pc.getMapId());
		if ((event_map_check != null) && (!EventNpcSpawnCotroller.isSpawn(event_map_check))) {
			pc.teleport(event_map_check.getExitLoc()[0], event_map_check.getExitLoc()[1], (short) event_map_check.getExitLoc()[2], pc.getHeading(), 169, false, false);
			pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[사냥터 시간이 아닙니다.]"), 1), true);
		}
		/** 시간제 **/
		DungeonTimeProgressLoader.load(pc);

		 L1World.getInstance().castPacketlogin(new S_SystemMessage("\\fY" + pc.getName() +" 님이 접속. \\fVIP:"+ client.getIp()+" 계정: "+ client.getAccountName()));

		if (!commons.isNullOrEmpty(mjct_account.getMJCT_Char_Name())) {
			if (!mjct_target_name.equalsIgnoreCase(mjct_account.getMJCT_Char_Name())) {
				L1PcInstance target_pc = L1PcInstance.load(mjct_target_name);
				MJCTHandler.store(pc, target_pc);
				account.setMJCT_Char_Name("");

				GeneralThreadPool.getInstance().schedule(new Runnable() {
					@Override
					public void run() {
						C_NewCharSelect.restartProcess(pc);
						GeneralThreadPool.getInstance().schedule(new Runnable() {
							@Override
							public void run() {
								Account acc = client.getAccount();
								client.sendPacket(new S_CharAmount(acc.countCharacters()));
								if (acc.countCharacters() > 0)
									C_CommonClick.sendCharPacks(client);
							}
						}, 200);
					}
				}, 200);
			} else {
				pc.getInventory().storeItem(CTLoadManager.CTSYSTEM_STORE_ID, 1);
				account.setMJCT_Char_Name("");
				pc.sendPackets("봉인 하려는 캐릭터가 봉인 아이템을 사용한 캐릭터와 같습니다. 다시 시도해 주시기 바랍니다.");
			}
		}
		if (pc.getLevel() >= 55) { 
			UserRankingController.getInstance().setBuffSetting(pc); 
		}
		CharacterSlotItemTable.getInstance().selectCharSlot(pc, 0); // 1번슬롯
		CharacterSlotItemTable.getInstance().selectCharSlot(pc, 1); // 2번슬롯
	}


	private static void itemsLoad(L1PcInstance pc) {
		// DB로부터 캐릭터와 창고의 아이템을 읽어들인다
		CharacterTable.getInstance().restoreInventory(pc);
		pc.sendPackets(new S_InvList(pc, pc.getInventory().getItems()), true);
	}

	private void bookmarks(L1PcInstance pc) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_teleport WHERE char_id=? ORDER BY name ASC");
			pstm.setInt(1, pc.getId());

			rs = pstm.executeQuery();
			while (rs.next()) {
				L1BookMark bookmark = new L1BookMark();
				bookmark.setId(rs.getInt("id"));
				bookmark.setCharId(rs.getInt("char_id"));
				bookmark.setName(rs.getString("name"));
				bookmark.setLocX(rs.getInt("locx"));
				bookmark.setLocY(rs.getInt("locy"));
				bookmark.setMapId(rs.getShort("mapid"));
				S_Bookmarks s_bookmarks = new S_Bookmarks(bookmark.getName(), bookmark.getMapId(), bookmark.getId());
				pc.addBookMark(bookmark);
				pc.sendPackets(s_bookmarks);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private static int[] skillLoad(L1PcInstance pc) {	
		int[] skillList = new int[24];
		for (int i = 0; i < 24; ++i) {
			skillList[i] = 0;
		}	
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_skills WHERE char_obj_id=?");
			pstm.setInt(1, pc.getId());
			rs = pstm.executeQuery();

			List<Integer> skillIdList = new ArrayList<Integer>();	
			while (rs.next()) {
				int skillId = rs.getInt("skill_id");
				L1Skills l1skills = SkillsTable.getInstance().getTemplate(skillId);
				if (skillId == 0) {
					continue;
				}
				if (l1skills != null && l1skills.getSkillLevel() > 0 && l1skills.getSkillLevel() <= 24) {		
					skillList[l1skills.getSkillLevel() - 1] |= l1skills.getId();	
				}
				skillIdList.add(skillId);
				pc.setSkillMastery(skillId);
			}
			SkillCheck.getInstance().AddSkill(pc.getId(), skillIdList);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return skillList;
	}

	private void serchSummon(L1PcInstance pc) {
		for (SummonInstance summon : L1World.getInstance().getAllSummons()) {
			if (summon.getMaster().getId() == pc.getId()) {
				summon.setMaster(pc);
				pc.addPet(summon);
			}
		}
	}

	private List<BuffInfo> buffLoad(L1PcInstance pc) {
		List<BuffInfo> buffList = new ArrayList<BuffInfo>();

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_buff WHERE char_obj_id=?");
			pstm.setInt(1, pc.getId());
			rs = pstm.executeQuery();		
			while (rs.next()) {		
				BuffInfo buffInfo = new BuffInfo();
				buffInfo.skillId = rs.getInt("skill_id");
				buffInfo.remainTime = rs.getInt("remaining_time");
				buffInfo.polyId = rs.getInt("poly_id");
				buffList.add(buffInfo);		
			}

		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return buffList;
	}


	private void buffProcess(L1PcInstance pc, List<BuffInfo> buffList) {
	
		int icon[] = {	0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0};

		for (BuffInfo buffInfo : buffList) {
			int skillid = buffInfo.skillId;
			int remaining_time = buffInfo.remainTime;
	
			if (skillid >= SkillId.요리_시작 && skillid <= SkillId.요리_끝) {
				Cooking cook = Cooking.getBySkillId(skillid);
				if (cook != null)
					cook.effectToMaster(pc, cook, 1, remaining_time);
				continue;
			}
			
			switch (skillid) {
			case SkillId.셰이프_체인지:
				int poly_id = buffInfo.polyId;
				L1PolyMorph.doPoly(pc, poly_id, remaining_time, L1PolyMorph.MORPH_BY_LOGIN);
				break;
			case SkillId.상태_요정_2단_가속:
				pc.sendPackets(S_SkillBrave.get(pc.getId(), 3, remaining_time));
				pc.broadcastPacket(S_SkillBrave.get(pc.getId(), 3, remaining_time));
				pc.setBraveSpeed(1);
				pc.setSkillEffect(skillid, remaining_time * 1000);
				break;
			case SkillId.상태_2단_가속:
				pc.sendPackets(S_SkillBrave.get(pc.getId(), 1, remaining_time));
				pc.broadcastPacket(S_SkillBrave.get(pc.getId(), 1, remaining_time));
				pc.setBraveSpeed(1);
				pc.setSkillEffect(skillid, remaining_time * 1000);
				break;
			case SkillId.상태_1단_가속:
				pc.sendPackets(S_SkillHaste.get(pc.getId(), 1, remaining_time));
				pc.broadcastPacket(S_SkillHaste.get(pc.getId(), 1, remaining_time)); 
				pc.setMoveSpeed(1);
				pc.setSkillEffect(skillid, remaining_time * 1000);
				pc.sendPackets(new S_ServerMessage(183));
				break;
			case SkillId.상태_파란물약:
				pc.sendPackets(new S_SkillIconGFX(34, remaining_time));
				pc.setSkillEffect(skillid, remaining_time * 1000);
				break;
			case SkillId.디크리즈_웨이트: icon[0] = remaining_time / 16; break;
			case SkillId.디케이_포션: icon[1] = remaining_time / 4; break;
			case SkillId.사일런스: icon[2] = remaining_time / 4; break;
			case SkillId.베놈_레지스트: icon[3] = remaining_time / 4; break;
			case SkillId.위크니스: icon[4] = remaining_time / 4; pc.addMeleeDmg(-5); pc.addMeleeHit(-1); break;
			case SkillId.디지즈: icon[5] = remaining_time / 4; pc.addMeleeHit(-6); pc.getAC().addAc(12); break;
			case SkillId.드레스_이베이젼:	icon[6] = remaining_time / 4; break;
			case SkillId.버서커스: icon[7] = remaining_time / 4; pc.getAC().addAc(10); pc.addMeleeDmg(2); pc.addMeleeHit(8); break;
			case SkillId.네이쳐스_터치: icon[8] = remaining_time / 4; pc.addHpr(15); break;
			case SkillId.윈드_셰클: icon[9] = remaining_time / 4; break;
			case SkillId.이레이즈_매직: icon[10] = remaining_time / 4; break;
			case SkillId.어디셔널_파이어: icon[11] = remaining_time / 16; break;
			case SkillId.엘리멘탈_폴다운:
				icon[12] = remaining_time / 4;
				int playerAttr = pc.getElfAttr();
				int i = -50;
				switch (playerAttr) {
				case 0: pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_1)); break;
				case 1: pc.getResistance().addEarth(i); pc.setAddAttrKind(1); break;
				case 2: pc.getResistance().addFire(i); pc.setAddAttrKind(2); break;
				case 4: pc.getResistance().addWater(i); pc.setAddAttrKind(4); break;
				case 8: pc.getResistance().addWind(i); pc.setAddAttrKind(8); break; default: break; }
				break;
			case SkillId.엘리멘탈_파이어: icon[13] = remaining_time / 4; break;
			case SkillId.스트라이커_게일: icon[14] = remaining_time / 4; break;	
			case SkillId.소울_오브_프레임: icon[15] = remaining_time / 4; break;
			case SkillId.플루트_워터: icon[16] = remaining_time / 4; break;
			case SkillId.천상_물약: icon[17] = remaining_time / 16; break;
			case SkillId.상태_체력_증강: icon[18] = (remaining_time + 8) / 16; icon[19] = 0;	pc.addMaxHp(50); pc.addHpr(4); break;
			case SkillId.상태_마나_증강: icon[18] = (remaining_time + 8) / 16; icon[19] = 1; pc.addMaxMp(40); pc.addMpr(4); break;	
			case SkillId.상태_전투_강화: 
				icon[18] = (remaining_time + 8) / 16; 
				icon[19] = 2; 
				pc.addMeleeHit(3); 
				pc.addMeleeDmg(3);
				pc.addRangedHit(3); 
				pc.addRangedDmg(3); 
				pc.addSp(3); break;
			case SkillId.코마_축복_코인_8: 	
				icon[30] = (remaining_time + 16) / 32;
				icon[31] = 41;
				pc.getAbility().addAddedCon(1); 
				pc.getAbility().addAddedDex(5); 
				pc.getAbility().addAddedStr(5);
				pc.addMeleeHit(5);
				pc.addRangedHit(5); 
				pc.getAC().addAc(-8); 
				pc.addSp(1); 
				break;
			case SkillId.지룡_마안: 
				icon[34] = remaining_time / 30; 
				icon[35] = 46;	
				pc.getResistance().addregist_spirit(15);
				break;
			case SkillId.수룡_마안: 
				icon[34] = remaining_time / 30; 
				icon[35] = 47; 
				break;
			case SkillId.상태_채팅_금지: pc.sendPackets(new S_SkillIconGFX(36, remaining_time), true); break;
			case SkillId.상태_드래곤_진주_3단계:
				int stime = (remaining_time / 4) - 2;
				S_Liquor ssss = new S_Liquor(pc.getId(), 8);
				pc.sendPackets(ssss);
				pc.broadcastPacket(ssss, true);
				pc.sendPackets(new S_PacketBox(S_PacketBox.DRAGONPERL, 8, stime), true);
				break;
			case SkillId.운세_버프_A:// 운세에 따른 깃털 버프 // 매우좋은
				icon[36] = remaining_time/16;
				icon[37] = 70;
				pc.addMeleeDmg(2);  // 근거리대미지+2
				pc.addMeleeHit(2);  // 근거리 명중 +2
				pc.addRangedHit(2);  // 원거리 명중 +2
				pc.addRangedDmg(2);  // 원거리 대미지+2 
				pc.addDamageReduction(3); //대미지 감소 +3
				pc.addSp(2);         // sp +2
				pc.sendPackets(new S_SPMR(pc));
				pc.addHpr(3);          //피틱+3
				pc.addMaxHp(50);       //hp50 
				pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
				if (pc.isInParty()) {
				pc.getParty().updateMiniHP(pc);
				}
				pc.addMpr(3);          //엠틱+3
				pc.addMaxMp(30);       //엠피+30
				pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()));
				break;
			case SkillId.운세_버프_B:// 운세에 따른 깃털 버프 // 좋은
				icon[36] = remaining_time/16;
				icon[37] = 71;
				pc.addMeleeHit(2);      //근거리 명중+2
				pc.addRangedHit(2);  // 원거리 명중 +2
				pc.addDamageReduction(2); //대미지 감소 +2
				pc.addSp(1);                //sp+1
				pc.sendPackets(new S_SPMR(pc));
				pc.addMaxHp(50);  //hp+50
				pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
				if (pc.isInParty()) {
					pc.getParty().updateMiniHP(pc);
				}
				pc.addMaxMp(30);     //mp+30
				pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()));
				break;
			case SkillId.운세_버프_C:// 운세에 따른 깃털 버프 // 보통
				icon[36] = remaining_time/16;
				icon[37] = 72;
				pc.addMaxHp(50);         //hp50
				pc.sendPackets(new S_HPUpdate(pc.getCurrentHp(), pc.getMaxHp()));
				if (pc.isInParty()) {
					pc.getParty().updateMiniHP(pc);
				}
				pc.addMaxMp(30);          //mp30
				pc.sendPackets(new S_MPUpdate(pc.getCurrentMp(), pc.getMaxMp()));
				pc.getAC().addAc(-2);             //ac2
				break;
			case SkillId.운세_버프_D:// 운세에 따른 깃털 버프 // 나쁜
				icon[36] = remaining_time/16;
				icon[37] = 73;
				pc.getAC().addAc(-1);          //ac1
				break;
			default:
				SkillUse l1skilluse = new SkillUse();
				l1skilluse.handleCommands(pc, skillid, pc.getId(), pc.getX(), pc.getY(), null, remaining_time, SkillUse.TYPE_LOGIN);
				break;
			}
			pc.setSkillEffect(skillid, remaining_time * 1000);
		}
		pc.sendPackets(new S_UnityIcon(icon[0], icon[1], icon[2], icon[3], icon[4], icon[5], icon[6], icon[7], icon[8],
				icon[9], icon[10], icon[11], icon[12], icon[13], icon[14], icon[15], icon[16], icon[17], icon[18],
				icon[19], icon[20], icon[21], icon[22], icon[23], icon[24], icon[25], icon[26], icon[27], icon[28],
				icon[29], icon[30], icon[31], icon[32], icon[33], icon[34], icon[35], icon[36], icon[37]));
	}

	private void einHasadBuffLoad(L1PcInstance pc) {
		int einhasad = pc.getAccount().getBlessOfAin();
		if (einhasad > 2000000) {
			einhasad = 2000000;
		}
		pc.getAccount().setBlessOfAin(einhasad);

		if (einhasad > 0) {
			pc.sendPackets(new S_PacketBox(S_PacketBox.EINHASAD, einhasad), true); 
		} 
	}

	private static int CheckMail(L1PcInstance pc) {
		int count = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(" SELECT count(*) as cnt FROM letter where receiver = ? AND isCheck = 0");
			pstm.setString(1, pc.getName());
			rs = pstm.executeQuery();
			if (rs.next()) {
				count = rs.getInt("cnt");
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}

		return count;
	}
	private static void sendSkillPacket(L1PcInstance pc, int[] skillList) {
		pc.sendPackets(new S_AddSkill(skillList[0], skillList[1], skillList[2], skillList[3], skillList[4],
				skillList[5], skillList[6], skillList[7], skillList[8], skillList[9], skillList[10], skillList[11],
				skillList[12], skillList[13], skillList[14], skillList[15], skillList[16], skillList[17], skillList[18],
				skillList[19], skillList[20], skillList[21], skillList[22], skillList[23]));
	}

	@Override
	public String getType() {
		return C_LOGIN;
	}
}
