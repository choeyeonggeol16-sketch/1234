package l1j.server.server.Controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.Templates.L1UserRanking;
import l1j.server.configure.Config;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.utils.SQLUtil;

public class UserRankingController implements Runnable {

	private static Logger _log = Logger.getLogger(UserRankingController.class.getName());

	private static UserRankingController _instance;

	public static boolean isRenewal = false;

	public static UserRankingController getInstance(){
		if (_instance == null){
			_instance = new UserRankingController();
		}
		return _instance;
	}

	private static ArrayList<L1UserRanking> list = null;
	private static ArrayList<L1UserRanking> listPrince = null;
	private static ArrayList<L1UserRanking> listKnight = null;
	private static ArrayList<L1UserRanking> listElf = null;
	private static ArrayList<L1UserRanking> listWizard = null;
	private static ArrayList<L1UserRanking> listDarkElf = null;


	public UserRankingController(){
		list = new ArrayList<L1UserRanking>();
		listPrince = new ArrayList<L1UserRanking>();
		listKnight = new ArrayList<L1UserRanking>();
		listElf = new ArrayList<L1UserRanking>();
		listWizard = new ArrayList<L1UserRanking>();
		listDarkElf = new ArrayList<L1UserRanking>();
		load();
		GeneralThreadPool.getInstance().schedule(this, 1000);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void run(){
		try {

			Date day = new Date(System.currentTimeMillis());

			if(isRenewal || (day.getMinutes() % 30 == 0 && day.getSeconds() == 0)) // 랭킹 갱신 30분 단위로 되는 구문

				//if(isRenewal || (day.getMinutes() == 0 && day.getSeconds() == 0))
			{				
				isRenewal = false;

				load();

				for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
					if (pc == null || pc.getLevel() < Config.RANKING_LEVEL) 
						continue;

					int RankScore = getStarCount(pc.getName());
					L1UserRanking rank = getTotalRank(pc.getName());
					int curRank = 0;
					if (rank != null) {
						curRank = rank.getCurRank();
					}

					// 랭킹이 변경되면 적용된 버프를 삭제한다
					if (pc.hasSkillEffect(SkillId.RANKING_BUFF_1) && RankScore != 11) {

						//pc.getInventory().consumeItem(3000051, 1);
						//if (Config.RANK_GAHO_ITEM) {
						if (pc.getInventory().checkItem(ItemId.정상의가호)) {
							pc.getInventory().consumeItem(ItemId.정상의가호);
						}
						//}

						//if (Config.RANK_GIVE_ITEM) {
						String rank_item_id = Config.RANK_GIVE_ITEM_ID;
						String rank_item_count = Config.RANK_GIVE_ITEM_COUNT;
						int itemid = Integer.valueOf(rank_item_id);
						int itemcount = Integer.valueOf(rank_item_count);
						deleteRankItem(pc, itemid, itemcount); 
						//}


						pc.killSkillEffectTimer(SkillId.RANKING_BUFF_1);
						pc.addMaxHp(-100);
						pc.getAC().addAc(2);
						setStatBuff(pc, -1); //스텟제거
					}else if (pc.hasSkillEffect(SkillId.RANKING_BUFF_2) && RankScore != 10) {
						pc.killSkillEffectTimer(SkillId.RANKING_BUFF_2);
						pc.addMaxHp(-50);
						pc.getAC().addAc(1);
						setStatBuff(pc, -1); //스텟제거
					}
					pc.sendPackets(new S_OwnCharStatus(pc));
					pc.sendPackets(new S_OwnCharAttrDef(pc));

					if (RankScore != 0) {
						if (!pc.hasSkillEffect(SkillId.RANKING_BUFF_1)
								&& !pc.hasSkillEffect(SkillId.RANKING_BUFF_2)) {
							setBuffSetting(pc);
						}
					}
				}
			}

		}catch(Exception e){
			e.printStackTrace();
		}
		GeneralThreadPool.getInstance().schedule(this, 1000);
	}

	public ArrayList<L1UserRanking> getList(int classId)
	{
		synchronized (list) {
			if(classId == 8)
				return list;
			else if(classId == 0)
				return listPrince;
			else if(classId == 1)
				return listKnight;
			else if(classId == 2)
				return listElf;
			else if(classId == 3)
				return listWizard;
			else if(classId == 4)
				return listDarkElf;
			return null;
		}
	}

	public L1UserRanking getTotalRank(String name) {
		synchronized (list) {
			for (L1UserRanking user : list) {
				if (user.getName().equalsIgnoreCase(name))
					return user;
			} 
			return null;
		}
	}

	public L1UserRanking getClassRank(int classId, String name) {
		for (L1UserRanking rank : getList(classId)) {
			if (rank.getName().equalsIgnoreCase(name)) {
				return rank;
			}
		}
		return null;
	}


	/*
	 * 랭킹을 11단계로 세분화 수정
	 */
	public int getStarCount(String name) {
		L1UserRanking rank = getTotalRank(name);
		if (rank != null) {
			int curRank = rank.getCurRank(); 
			if (curRank >= 1 && curRank <= 3) {
				return 11;
			} else if (curRank >= 4 && curRank <= 15) {
				return 10;
			}
		}

		return 0;
	}

	public void setBuffSetting(L1PcInstance pc) {

		L1UserRanking rank = getTotalRank(pc.getName());  
		if (rank != null) {    
			int curRank = rank.getCurRank();
			if (curRank >= 1 && curRank <= 3) {
				pc.setSkillEffect(SkillId.RANKING_BUFF_1, -1);

				if (Config.RANK_GAHO_ITEM) {
					ItemInstance item = ItemTable.getInstance().createItem(ItemId.정상의가호);
					if (!pc.getInventory().checkItem(ItemId.정상의가호)
							&& pc.getInventory().checkAddItem(item, 1) == L1Inventory.OK) {
						pc.getInventory().storeItem(item);
						//	pc.sendPackets(new S_SystemMessage("랭킹 1~3위진입 정상의가호를 얻었습니다."));
					}
				}

				if (Config.RANK_GIVE_ITEM) {
					String rank_item_id = Config.RANK_GIVE_ITEM_ID;
					String rank_item_count = Config.RANK_GIVE_ITEM_COUNT;
					int itemid = Integer.valueOf(rank_item_id);
					int itemcount = Integer.valueOf(rank_item_count);
					if (!pc.getInventory().checkItem(itemid)) {
						ItemInstance tem = pc.getInventory().storeItem(itemid, itemcount);
						pc.sendPackets(new S_ServerMessage(813, "랭킹진입", tem.getLogName()));
					}
				}
				pc.addMaxHp(100);
				pc.getAC().addAc(-2);
				setStatBuff(pc, 1); // 스텟
				pc.sendPackets(new S_OwnCharStatus(pc));
				//	pc.sendPackets("전체랭킹버프 1~3위 HP+100 , ac-2 , 올스텟+1 적용중!");

			} else if (curRank >= 4 && curRank <= 15) {
				pc.setSkillEffect(SkillId.RANKING_BUFF_2, -1);
				pc.addMaxHp(50);
				pc.getAC().addAc(-1);
				setStatBuff(pc, 1); // 스텟
				pc.sendPackets(new S_OwnCharStatus(pc));
				//	pc.sendPackets("전체랭킹버프 4~15위 HP+50 , ac-1 , 올스텟+1 적용중!");
			}
		}
		if (!pc.hasSkillEffect(SkillId.RANKING_BUFF_1)) {
			if (Config.RANK_GAHO_ITEM) {
				if (pc.getInventory().checkItem(ItemId.정상의가호)) {
					pc.getInventory().consumeItem(ItemId.정상의가호);
				}
			}
			//if (Config.RANK_GIVE_ITEM) {
			String rank_item_id = Config.RANK_GIVE_ITEM_ID;
			String rank_item_count = Config.RANK_GIVE_ITEM_COUNT;
			int itemid = Integer.valueOf(rank_item_id);
			int itemcount = Integer.valueOf(rank_item_count);
			deleteRankItem(pc, itemid, itemcount); 
			//}
		}
	}

	public void setStatBuff(L1PcInstance pc, int flag) {
		if (pc.isCrown() || pc.isKnight() || pc.isDarkelf()) {
			pc.getAbility().addAddedStr(1 * flag);
		} else if (pc.isElf()) {
			pc.getAbility().addAddedDex(1 * flag);
		} else if (pc.isWizard()) {
			pc.getAbility().addAddedInt(1 * flag);
		}
	}

	private void load() { 
		ArrayList<L1UserRanking> templist = new ArrayList<L1UserRanking>();
		ArrayList<L1UserRanking> templistPrince = new ArrayList<L1UserRanking>();
		ArrayList<L1UserRanking> templistKnight = new ArrayList<L1UserRanking>();
		ArrayList<L1UserRanking> templistElf = new ArrayList<L1UserRanking>();
		ArrayList<L1UserRanking> templistWizard = new ArrayList<L1UserRanking>();
		ArrayList<L1UserRanking> templistDarkElf = new ArrayList<L1UserRanking>();

		for(int a = 0; a < 6; a++)
		{
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			int i = 0;
			try {
				con = L1DatabaseFactory.getInstance().getConnection();
				if(a == 5) {
					pstm = con.prepareStatement("SELECT char_name, Type FROM characters WHERE level >= 1 AND AccessLevel = 0 order by Exp desc limit 200");
					//pstm = con.prepareStatement("SELECT char_name, Type FROM characters WHERE level >= 1 order by Exp desc limit 200");
				} else {
					pstm = con.prepareStatement("SELECT char_name, Type FROM characters WHERE Type = "+a+" AND level >= 1 AND AccessLevel = 0 order by Exp desc limit 200");
					//pstm = con.prepareStatement("SELECT char_name, Type FROM characters WHERE Type = "+a+" AND level >= 1 order by Exp desc limit 200");
				}
				rs = pstm.executeQuery();

				while(rs.next()){
					String name = rs.getString("char_name");
					int type = rs.getInt("Type");

					L1UserRanking rank = new L1UserRanking();

					rank.setName(name);
					rank.setCurRank(++i);

					L1UserRanking oldRank = null;
					if(a == 5) {
						oldRank = getTotalRank(name); 
					} else {
						oldRank = getClassRank(a, name);

					}

					if(oldRank == null)
						rank.setOldRank(rank.getCurRank());
					else
						rank.setOldRank(oldRank.getCurRank());

					rank.setClassId(type);

					if(a == 5)
						templist.add(rank);	
					else if(a == 0)
						templistPrince.add(rank);	
					else if(a == 1)
						templistKnight.add(rank);	
					else if(a == 2)
						templistElf.add(rank);	
					else if(a == 3)
						templistWizard.add(rank);	
					else if(a == 4)
						templistDarkElf.add(rank);
				}
			} catch (SQLException e) {
				_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			} finally {
				SQLUtil.close(rs);
				SQLUtil.close(pstm);
				SQLUtil.close(con);
			}
		}

		synchronized (list) {
			list.clear();
			listPrince.clear();
			listKnight.clear();
			listElf.clear();
			listWizard.clear();
			listDarkElf.clear();

			list.addAll(templist);
			listPrince.addAll(templistPrince);
			listKnight.addAll(templistKnight);
			listElf.addAll(templistElf);
			listWizard.addAll(templistWizard);
			listDarkElf.addAll(templistDarkElf);
		}		
	}

	public static void deleteRankItem(L1PcInstance pc, int itemid, int count) {
		ItemInstance item = pc.getInventory().findItemId(itemid);
		if (item != null) {
			if (item.getItem().getUseType() == 73) {
				if (pc.getMagicDoll() != null) {
					pc.getMagicDoll().deleteDoll();
				}
			}

			if (item.getItem().getType2() != 0) {
				if (item.isEquipped()) {
					pc.getInventory().setEquipped(item, false, false, false);
				}
			}

			pc.getInventory().consumeItem(itemid, count);
		}

		Connection conn = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			conn = L1DatabaseFactory.getInstance().getConnection();
			pstm = conn.prepareStatement("DELETE FROM character_items WHERE item_id=?");
			pstm.setInt(1, itemid);
			pstm.execute();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(conn);
		}
	}
}
