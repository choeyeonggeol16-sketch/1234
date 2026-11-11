package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class CraftListTable{
private static Logger _log = Logger.getLogger(CraftListTable.class.getName());
	
	private static CraftListTable _instance;

	public static CraftListTable getInstance() {
		if (_instance == null) {
			_instance = new CraftListTable();
		}
		return _instance;
	}

	public static void reload(){
		CraftListTable oldInstance = _instance;
		_instance = new CraftListTable();
		oldInstance._craftlist.clear();
	}

	private ConcurrentHashMap<Integer, ArrayList<CraftTemp>> _craftlist = new ConcurrentHashMap<Integer, ArrayList<CraftTemp>>();
	
	public CraftListTable() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM craft_list");
			rs = pstm.executeQuery();
			while (rs.next()) {
				
				CraftTemp ctemp = new CraftTemp();
				
				int npcid = rs.getInt("npcid");
				
				ctemp.order_id = rs.getInt("order_id");
				ctemp.create_id = rs.getInt("create_id");
				ctemp.createName = rs.getString("createName");
				ctemp.create_count = rs.getInt("create_count");
				ctemp.create_enchant = rs.getInt("create_enchant");
				ctemp.create_bless = rs.getInt("create_bless");
				ctemp.create_chance = rs.getInt("create_chance");
				
				ctemp.reitem_id = rs.getInt("return_itemid");
				ctemp.reitem_count = rs.getInt("return_count");
				
				
				String material_list = rs.getString("material_list");
				material_list = material_list.replaceAll(" ", "");
				
				StringTokenizer st = new StringTokenizer(material_list, "\r\n");
				while(st.hasMoreTokens()) {
					String templine = st.nextToken();
					String[] temparr = templine.split(",");
					
					CraftMeterialTemp metertemp = new CraftMeterialTemp();
					for(String tarr : temparr) {
						if(tarr.startsWith("이름:")) { //이름은 제외한다.
							continue;
						}
						
						if(tarr.startsWith("아이템번호")) {
							String textTemp = tarr.replace("아이템번호:", "");
							metertemp.itemid = Integer.parseInt(textTemp);
						}else if(tarr.startsWith("수량")) {
							String textTemp = tarr.replace("수량:", "");
							metertemp.count = Integer.parseInt(textTemp);
						}else if(tarr.startsWith("인챈트")) {
							String textTemp = tarr.replace("인챈트:", "");
							metertemp.enchant = Integer.parseInt(textTemp);
						}else if(tarr.startsWith("축복")) {
							String textTemp = tarr.replace("축복:", "");
							metertemp.bless = Integer.parseInt(textTemp);
						}
					}
					
					ctemp._MeterialList.add(metertemp);
				}
				
				if(_craftlist.get(npcid) == null) {
					ArrayList<CraftTemp> list = new ArrayList<CraftTemp>();
					list.add(ctemp);
					_craftlist.put(npcid, list);
				}else {
					_craftlist.get(npcid).add(ctemp);
				}
			}
			
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public boolean isCraftNpc(int npcid) {
		if(_craftlist.get(npcid) == null) {
			return false;
		}
		
		return true;
	}
	
	public ArrayList<CraftTemp> getCraftList(int npcid){
		return _craftlist.get(npcid);
	}
	
	public CraftTemp getCraftTemp(int npcid, int orderid) {
		if(_craftlist.get(npcid) == null || _craftlist.get(npcid).get(orderid) == null) {
			return null;
		}
		
		return _craftlist.get(npcid).get(orderid);
	}
	
	/*
	 * 제작정보
	 */
	public class CraftTemp{
		public int order_id;
		public int create_id;
		public String createName;
		public int create_count;
		public int create_enchant;
		public int create_bless;
		public int create_chance;
		
		public int reitem_id;
		public int reitem_count;
		
		public ArrayList<CraftMeterialTemp> _MeterialList = new ArrayList<CraftMeterialTemp>();
	}
	
	/*
	 * 재료정보
	 */
	public class CraftMeterialTemp{
		public int itemid;
		public int count;
		public int enchant;
		public int bless;
	}
}