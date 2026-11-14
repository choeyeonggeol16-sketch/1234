package l1j.server.server.model.BookQuest;

import java.sql.ResultSet;

import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.Instance.L1PcInstance;
import LIN.SC_ACHIEVEMENT_TELEPORT_ACk.eResultCode;

public class BQSInformation {
	public static BQSInformation newInstance(ResultSet rs) throws Exception{
		BQSInformation mInfo = newInstance();
		mInfo.criteriaId = rs.getInt("criteria_id");
		mInfo.achievementId =  BQSLoadManager.criteriaIdToAchievementId(mInfo.criteriaId, 1);
		mInfo.npcId = rs.getInt("npc_id");
		mInfo.npcName = rs.getString("npc_name");
		mInfo.bookSteps[0] = rs.getInt("book_step_first");
		mInfo.bookSteps[1] = rs.getInt("book_step_second");
		mInfo.bookSteps[2] = rs.getInt("book_step_third");
		mInfo.week_difficulty = rs.getInt("week_difficulty");
		mInfo.week_completed_count = rs.getInt("week_completed_count");
		mInfo.teleport_x = rs.getInt("tel_x");
		mInfo.teleport_y = rs.getInt("tel_y");
		mInfo.teleport_mapId = rs.getShort("tel_mapId");
		mInfo.teleport_need_item_id = rs.getInt("tel_need_item_id");
		mInfo.teleport_need_item_amount = rs.getInt("tel_need_item_amount");
		return mInfo;
	}
	
	public static BQSInformation newInstance(){
		return new BQSInformation();
	}
	
	private int criteriaId;
	private int achievementId;
	private int npcId;
	private String npcName;
	private int[] bookSteps;
	private int week_difficulty;
	private int week_completed_count;
	private int teleport_x;
	private int teleport_y;
	private short teleport_mapId;
	private int	teleport_need_item_id;
	private int teleport_need_item_amount;
	private BQSInformation(){
		bookSteps = new int[]{0, 0, 0};
	}
	
	public int getCriteriaId(){
		return criteriaId;
	}
	
	public int getAchievementId(){
		return achievementId;
	}
	
	public int getNpcId(){
		return npcId;
	}
	
	public String getNpcName(){
		return npcName;
	}
	
	public int getBookStep(int step){
		return bookSteps[step];
	}
	
	public int getWeekDifficulty(){
		return week_difficulty;
	}
	
	public int getWeekCompletedCount(){
		return week_completed_count;
	}
	
	public int getTeleportX(){
		return teleport_x;
	}
	
	public int getTeleportY(){
		return teleport_y;
	}
	
	public short getTeleportMapId(){
		return teleport_mapId;
	}
	
	public LIN.SC_ACHIEVEMENT_TELEPORT_ACk.eResultCode doTeleport(L1PcInstance pc){
		if(teleport_need_item_id <= 0 || teleport_need_item_amount <= 0 || !pc.getMap().isEscapable())
			return LIN.SC_ACHIEVEMENT_TELEPORT_ACk.eResultCode.TELEPORT_FAIL_WRONG_LOCATION;
		
		if(!pc.getInventory().consumeItem(teleport_need_item_id, teleport_need_item_amount))
			return LIN.SC_ACHIEVEMENT_TELEPORT_ACk.eResultCode.TELEPORT_FAIL_NOT_ENOUGH_ADENA;
		
		pc.start_teleport(teleport_x, teleport_y, teleport_mapId, pc.getHeading(), 169, true, true);
		return LIN.SC_ACHIEVEMENT_TELEPORT_ACk.eResultCode.TELEPORT_SUCCESS;
	}
}
