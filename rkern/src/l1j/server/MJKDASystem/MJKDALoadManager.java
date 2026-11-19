package l1j.server.MJKDASystem;
/**********************************
 * 
 * MJ Kill Death Assist LoadManager.
 * made by mjsoft, 2017.
 *  
 **********************************/
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.Properties;

import l1j.server.DatabaseFactory;
import l1j.server.MJKDASystem.Chart.MJKDAChartScheduler;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.SQLUtil;

public class MJKDALoadManager {
	private static MJKDALoadManager _instance;
	public static MJKDALoadManager getInstance(){
		if(_instance == null)
			_instance = new MJKDALoadManager();
		return _instance;
	}
	
	public static void commands(L1PcInstance gm, String param){
		try{
			ArrayDeque<Integer> argsQ = MJCommons.parseToIntQ(param);
			if(argsQ == null || argsQ.isEmpty())
				throw new Exception("");
			
			switch(argsQ.poll()){
			case 1:
				chartCommands(gm, argsQ);
				break;
			case 2:
				_instance.reload();
				SC_SystemMessage.send(gm, "[MJKDA System Config reload completed.]");
				break;
			case 3:
				String[] arrs = param.split(" ");
				initCommands(gm, arrs[arrs.length - 1]);
				break;
			case 4:
				MJKDALoader.getInstance().store();
				SC_SystemMessage.send(gm, "[MJKDA System Store requested.]");
				break;
			default:
				throw new Exception("");	
			}
			
		}catch(Exception e){
			SC_SystemMessage.send(gm, "[1.킬차트 켬/끔][2. 리로드][3. 유저 초기화][4. 강제저장]");
		}
	}
	
	private static void initCommands(L1PcInstance gm, String s){
		try{
			if(s == null || s.equalsIgnoreCase(""))
				throw new Exception("");
			
			L1PcInstance pc = L1World.getInstance().findpc(s);
			if(pc == null){
				SC_SystemMessage.send(gm, String.format("%s(을)를 찾을 수 없습니다.", s));
			}else{
				pc.getKDA().onInit(pc);
				SC_SystemMessage.send(gm, String.format("%s의 킬 데스를 초기화 하였습니다.", s));
			}
		}catch(Exception e){
			SC_SystemMessage.send(gm, "사용방법 .킬데스 3 [캐릭터 이름]");
		}
	}
	
	private static void chartCommands(L1PcInstance gm, ArrayDeque<Integer> argsQ){
		try{
			if(argsQ.isEmpty())
				throw new Exception("");
			
			switch(argsQ.poll()){
			case 0:
				KDA_CHART_RUN = false;
				MJKDAChartScheduler.release();
				SC_SystemMessage.send(gm, "킬데스 차트 시스템이 종료되었습니다.");
				break;
			case 1:
				if(MJKDAChartScheduler.isLoaded())
					SC_SystemMessage.send(gm, "이미 가동 중입니다.");
				else{	
					KDA_CHART_RUN = true;
					MJKDAChartScheduler.getInstance().run();
					SC_SystemMessage.send(gm, "킬데스 차트 시스템이 시작되었습니다.");
				}
				break;
			default:
				throw new Exception("");
			}
			
		}catch(Exception e){
			SC_SystemMessage.send(gm, "사용방법 .킬데스 1 0(끔) or 1(켬)");
		}
	}
	
	public void reload(){
		loadConfig();
	}
	
	public void release(){
		MJKDALoader.release();	
		MJKDAChartScheduler.release();
	}
	
	private MJKDALoadManager(){
	}
	
	public void load(){
		loadConfig();
		loadTotalPvp();
		MJKDALoader.getInstance();
		if(KDA_CHART_RUN)
			MJKDAChartScheduler.getInstance().run();
	}

	public static int		KDA_TOTAL_PVP;
	public static int 		KDA_CHART_DELAY_SEC;
	public static boolean 	KDA_CHART_RUN;
	public static long 		KDA_DEATH_DELAY_MS;
	public static long 		KDA_KILL_DUPL_DELAY_MS;
	
	private void loadTotalPvp(){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs 		= null;
		try{
			con		= DatabaseFactory.getInstance().getConnection();
			pstm	= con.prepareStatement("SELECT sum(killing) as totalpvp from tb_kda");
			rs		= pstm.executeQuery();			
			if(rs.next())
				KDA_TOTAL_PVP = rs.getInt("totalpvp");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	private void loadConfig(){
		String column = null;
		try{
			Properties 	settings 	= new Properties();
			InputStream is 			= new FileInputStream(new File("./config/mjkda.properties"));
			settings.load(is);
			is.close();
			
			column					= "ChartUpdateDelaySecond";
			KDA_CHART_DELAY_SEC		= Integer.parseInt(settings.getProperty(column, "60"));

			column					= "IsChartUpdate";
			KDA_CHART_RUN			= Boolean.parseBoolean(settings.getProperty(column, "false"));

			column					= "DeathDelaySecond";
			KDA_DEATH_DELAY_MS		= Integer.parseInt(settings.getProperty(column, "60")) * 1000;
			
			column					= "KillDuplicationDelaySecond";
			KDA_KILL_DUPL_DELAY_MS	= Integer.parseInt(settings.getProperty(column, "90")) * 1000;
			
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
