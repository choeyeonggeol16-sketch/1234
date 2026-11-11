package l1j.server.server.command.executor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.StringTokenizer;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class L1Search1 implements L1CommandExecutor{
	
	private L1Search1(){ }
	
	public static L1CommandExecutor getInstance(){
		return new L1Search1();
	}
	
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			int type = Integer.parseInt(tok.nextToken()) ;
			String real_name_id_view = tok.nextToken() ;
			searchObject(pc, type, "%" + real_name_id_view + "%") ;			
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".검색 [0~5] [name]을 입력 해주세요.")) ;
			pc.sendPackets(new S_SystemMessage("0=잡템, 1=무기, 2=갑옷, 3=엔피시, 4=변신, 5=엔피시(gfxid)"));
		}
	}
	
	private void searchObject( L1PcInstance gm, int type, String real_name_id_view ) {
		java.sql.Connection con = null ;
		PreparedStatement statement = null ;
		ResultSet rs  = null;
		try {
			String str1 = null ;
			String str2 = null ;
			String s_note = null ;
			int count = 0 ;
			con = L1DatabaseFactory.getInstance().getConnection() ;
			switch (type) {
			case 0://etcitem
				statement = con.prepareStatement("select item_id, name, name_id from item_etc where name Like '" + real_name_id_view + "'");
				break;
			case 1://weapon
				statement = con.prepareStatement("select item_id, name, name_id from item_weapon where name Like '" + real_name_id_view + "'");
				break;
			case 2: // armor
				statement = con.prepareStatement("select item_id, name, name_id from item_armor where name Like '" + real_name_id_view + "'");
				break;
			case 3: // npc
				statement = con.prepareStatement("select npcid, name, note from npc where name Like '" + real_name_id_view + "'");
				break;
			case 4: // polymorphs
				statement = con.prepareStatement("select polyid, name,id from polymorphs where name Like '" + real_name_id_view + "'");
				break;
			case 5: // npc(gfxid)
				statement = con.prepareStatement("select sprite_id, desc_view,note from npc where desc_view Like '" + real_name_id_view + "'");
				break;
			default:
				break;
			}
			rs = statement.executeQuery();
			while (rs.next()) {
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				s_note = rs.getString(3);
				gm.sendPackets(new S_SystemMessage("[" + str1 + "]--[" + str2 + "]--" + s_note));
				count++ ;
			}
			gm.sendPackets(new S_SystemMessage("총 [" + count + "]개 검색이 되었습니다."));
		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			SQLUtil.close(rs, statement, con);
		}
	}

}
