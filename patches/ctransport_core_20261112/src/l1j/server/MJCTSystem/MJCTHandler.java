package l1j.server.MJCTSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import l1j.server.DatabaseFactory;
import l1j.server.MJCTSystem.Loader.MJCTLoadManager;
import l1j.server.MJCTSystem.Loader.MJCTSystemLoader;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.model.network.core.cs.packet.CS_CommonClick;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.SQLUtil;
import l1j.server.server.model.network.core.sc.packet.SC_CharAmount;
/** 
 * MJCTCharInfo
 * MJSoft Character TradeSystem - Handler
 * made by mjsoft, 2016.
 **/
public class MJCTHandler {
	public static boolean load(L1PcInstance pc, L1ItemInstance item){
		if(!pc.getMap().isSafetyZone(pc.getLocation())){
			SC_SystemMessage.send(pc, "마을에서만 사용하실 수 있습니다.");
			return false;
		}
		
		MJCTObject obj = MJCTSystemLoader.getInstance().remove(item.getId());
		if(obj == null){
			SC_SystemMessage.send(pc, MJCTLoadManager._invalidCharacter);
			return false;			
		}
		
		Account acc	= pc.getAccount();
		if(acc.countCharacters() > 8 || acc.getCharSlot() - acc.countCharacters() <= 0){
			MJCTSystemLoader.getInstance().reset(obj);
			SC_SystemMessage.send(pc, "캐릭터 슬롯이 부족합니다.");
			return false;
		}
		
		Connection con			= null;
		try{
			con	= DatabaseFactory.getInstance().getConnection();
			String charName = isValidCharacter(con, "[MJCTSYSTEM]", obj.charId);
			if(charName == null){
				SC_SystemMessage.send(pc, MJCTLoadManager._invalidCharacter);
				return false;	
			}
			
			updateAccount(con, pc.getAccountName(), obj.charId);
			obj.dispose();
			pc.getInventory().removeItem(item);
			updateCharacterStatus(pc);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(con);
		}
		
		return true;
	}
	
	public static boolean store(L1PcInstance pc, L1ItemInstance item, int objid){
		if(pc.getId() == objid){
			SC_SystemMessage.send(pc, "자기 자신에게는 사용할 수 없습니다.");
			return false;
		}
		
		if(!pc.getMap().isSafetyZone(pc.getLocation())){
			SC_SystemMessage.send(pc, "마을에서만 사용하실 수 있습니다.");
			return false;
		}
		Connection con = null;
		try{
			con	= DatabaseFactory.getInstance().getConnection();
			String charName = isValidCharacter(con, pc.getAccountName(), objid);
			int classn = isClass(con, pc.getAccountName(), objid);
			int str = isStr(con, pc.getAccountName(), objid);
			int dex = isDex(con, pc.getAccountName(), objid);
			int cons = isCon(con, pc.getAccountName(), objid);
			int wis = isWis(con, pc.getAccountName(), objid);
			int ints = isInt(con, pc.getAccountName(), objid);
			int cha = isCha(con, pc.getAccountName(), objid);
			int levels = islevel(con, pc.getAccountName(), objid);
			int hp = isMaxHp(con, pc.getAccountName(), objid);
			int mp = isMaxMp(con, pc.getAccountName(), objid);
			
			if(charName == null || str == 0 || dex == 0 || cons == 0 || wis == 0 || ints == 0 || cha == 0 || levels == 0 || hp == 0 || mp == 0){
				SC_SystemMessage.send(pc, MJCTLoadManager._invalidCharacter);
				return false;			
			}
			
			updateAccount(con, "[MJCTSYSTEM]", objid);
			pc.setTamTime(null);
			pc.getAccount().getTamHandler().tamdel(objid);
			pc.getInventory().removeItem(item, 1);
			L1ItemInstance item2 = pc.getInventory().storeItem(MJCTLoadManager.CTSYSTEM_LOAD_ID, 1);
			
			MJCTObject obj 	= new MJCTObject();
			obj.charId		= objid;
			obj.marbleId	= item2.getId();
			obj.name		= charName;
			obj.value1 = str;
			obj.dex = dex;
			obj.classn = classn;
			obj.con = cons;
			obj.wis = wis;
			obj.ints = ints;
			obj.cha = cha;
			obj.level = levels;
			obj.hp = hp;
			obj.mp = mp;
		//	obj.level = levels;
			
			MJCTSystemLoader.getInstance().set(obj);
			updateCharacterStatus(pc);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(con);
		}
		return true;
	}

	private static void updateCharacterStatus(L1PcInstance pc){
		CIOClient clnt = pc.getNetConnection();
		CS_CharSelect.restartProcess(pc);
        CS_CommonClick.sendCharAmountAndPacks(clnt);
	}
	
	private static void updateAccount(Connection con, String dst_account, int objid){
		PreparedStatement pstm	= null;
		try{
			pstm	= con.prepareStatement("update characters set account_name=?, TamEndTime=null where objid=?");
			pstm.setString(1, dst_account);
			pstm.setInt(2, objid);
			pstm.execute();
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(pstm);
		}
	}
	
	private static String isValidCharacter(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		
		try{
			pstm	= con.prepareStatement("select char_name from characters where account_name=? and objid=?");
			pstm.setString(1, account);
			pstm.setInt(2, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getString("char_name");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return null;
	}
	private static int isStr(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select Str from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("Str");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static int isDex(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select Dex from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("Dex");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static int isWis(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select Wis from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("Wis");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static int isCon(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select Con from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("Con");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static int isInt(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select Intel from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("Intel");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static int isCha(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select Cha from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("Cha");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static int isMaxHp(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select MaxHp from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("MaxHp");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static int isMaxMp(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select MaxMp from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("MaxMp");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static int isClass(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select Class from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("Class");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static int isElixirStatus(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select ElixirStatus from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("ElixirStatus");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static int islevel(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;

		try{
			pstm	= con.prepareStatement("select level from characters where objid=?");
			pstm.setInt(1, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getInt("level");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return 0;
	}
	private static String isClanname(Connection con, String account, int objid){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		
		try{
			pstm	= con.prepareStatement("select Clanname from characters where account_name=? and objid=?");
			pstm.setString(1, account);
			pstm.setInt(2, objid);
			rs		= pstm.executeQuery();
			if(rs.next())
				return rs.getString("Clanname");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
		return null;
	}
}
