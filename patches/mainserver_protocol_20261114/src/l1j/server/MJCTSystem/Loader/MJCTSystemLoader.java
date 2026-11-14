package l1j.server.MJCTSystem.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.MJCTSystem.MJCTCharInfo;
import l1j.server.MJCTSystem.MJCTItem;
import l1j.server.MJCTSystem.MJCTObject;
import l1j.server.MJCTSystem.MJCTSpell;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.packets.Protocol.Packet.SC_WAREHOUSE_ITEM_LIST_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_CTPacket;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.SQLUtil;
/** 
 * MJCTCharInfo
 * MJSoft Character TradeSystem - System Loader
 * boxing character information load.
 * made by mjsoft, 2016.
 **/
public class MJCTSystemLoader {
	private static final Object 	_lock = new Object();
	private static MJCTSystemLoader _instance;
	public static MJCTSystemLoader getInstance(){
		if(_instance == null)
			_instance = new MJCTSystemLoader();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.store();
			_instance = null;
		}
	}
	
	private MJCTCharInfo createCharInfo(ResultSet rs) throws SQLException{
		MJCTCharInfo cInfo = new MJCTCharInfo();
		if(rs.next()){
			cInfo.name 			= rs.getString("char_name");
			cInfo.level			= rs.getInt("level");
			cInfo.type			= rs.getInt("type");
			long tmp			= rs.getLong("einhasad");
			if(tmp > 0)	tmp 	/= 10000;
			if(tmp < 0) tmp 	= 0;
			cInfo.einhasad 		= (int)tmp; 
			cInfo.tamEndTime 	= rs.getTimestamp("TamEndTime");
			cInfo.sex			= rs.getInt("Sex");
			cInfo.clanName 		= rs.getString("Clanname");
			cInfo.str			= rs.getInt("Str");
			cInfo.dex			= rs.getInt("Dex");
			cInfo.con			= rs.getInt("Con");
			cInfo.wis			= rs.getInt("Wis");
			cInfo.intel			= rs.getInt("Intel");
			cInfo.cha			= rs.getInt("Cha");
			cInfo.elixir		= rs.getInt("ElixirStatus");
			cInfo.hp			= rs.getInt("MaxHp");
			cInfo.mp			= rs.getInt("MaxMp");
			cInfo.ac			= rs.getInt("Ac");
		}
		return cInfo;
	}
	
	private void setInfo(Connection con, MJCTObject obj){
		PreparedStatement 		pstm	= null;
		ResultSet 				rs		= null;
		MJCTCharInfo			cInfo	= null;
		try{
			pstm	= con.prepareStatement("select * from characters where objid=?");
			pstm.setInt(1,  obj.charId);
			rs		= pstm.executeQuery();
			cInfo	= createCharInfo(rs);
			obj.infoPck				= SC_CTPacket.getCharacterInfo(cInfo);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
	}
	
	private void setInfo(Connection con, ArrayList<MJCTObject> list){
		PreparedStatement 	pstm	= null;
		ResultSet 				rs		= null;
		MJCTObject				obj	= null;
		MJCTCharInfo			cInfo	= null;
		int							size	= 0;	
		try{
			pstm							= con.prepareStatement("select * from characters where objid=?");
			for(int i=0; i<size; i++){
				obj 						= list.get(i);
				pstm.setInt(1, obj.charId);
				rs							= pstm.executeQuery();
				cInfo						= createCharInfo(rs);
				obj.infoPck				= SC_CTPacket.getCharacterInfo(cInfo);
				SQLUtil.close(rs);
				pstm.clearParameters();
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
	}
	
	private void setSkills(Connection con, MJCTObject obj){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		ArrayDeque<MJCTSpell> spQ	= null;
		MJCTSpell			sp		= null;
		int					rows	= 0;
		try{
			pstm	= con.prepareStatement("select * from character_skills where char_obj_id=?");
			pstm.setInt(1, obj.charId);
			rs		= pstm.executeQuery();
			rows	= calcRows(rs);
			if(rows > 0){
				spQ = new ArrayDeque<MJCTSpell>(rows);
				while(rs.next()){
					sp	= MJCTSpellLoader.getInstance().get(rs.getInt("skill_id"));
					if(sp == null)
						continue;
					spQ.offer(sp);
				}
			}
			obj.spPck  = SC_WAREHOUSE_ITEM_LIST_NOTI.create_ct_sp_info(spQ).encode();
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
	}
	
	private void setSkills(Connection con, ArrayList<MJCTObject> list){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		MJCTObject			obj		= null;
		ArrayDeque<MJCTSpell> spQ	= null;
		MJCTSpell			sp		= null;
		int					size	= list.size();
		int					rows	= 0;
		try{
			pstm		= con.prepareStatement("select * from character_skills where char_obj_id=?");
			for(int i=0; i<size; i++){
				obj		= list.get(i);
				pstm.setInt(1, obj.charId);
				rs		= pstm.executeQuery();
				rows	= calcRows(rs);
				if(rows > 0){
					spQ	= new ArrayDeque<MJCTSpell>(rows);
					while(rs.next()){
						sp	= MJCTSpellLoader.getInstance().get(rs.getInt("skill_id"));
						if(sp == null)
							continue;
						spQ.offer(sp);
					}
				}
				obj.spPck  = SC_WAREHOUSE_ITEM_LIST_NOTI.create_ct_sp_info(spQ).encode();
				SQLUtil.close(rs);
				pstm.clearParameters();
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
	}
	
	private ArrayDeque<MJCTItem> createItemQ(ResultSet rs) throws SQLException{
		ArrayDeque<MJCTItem> 	itemQ 	= null;
		MJCTItem				item	= null;
		int 					rows	= calcRows(rs);
		if(rows > 0){
			itemQ = new ArrayDeque<MJCTItem>(rows);
			while(rs.next()){
				item			= new MJCTItem();
				int itemId 		= rs.getInt("item_id");
				L1Item itm 		= ItemTable.getInstance().getTemplate(itemId);
				item.id 		= rs.getInt("id");
				item.bless		= rs.getInt("bless");
				item.count		= rs.getInt("count");
				item.iden		= rs.getInt("is_id");
				item.enchant	= rs.getInt("enchantlvl");
				item.attr		= rs.getInt("attr_enchantlvl");
				item.item		= itm;
				itemQ.offer(item);
			}
		}
		return itemQ;
	}
	
	private void setItems(Connection con, MJCTObject obj){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		ArrayDeque<MJCTItem> itemQ	= null;
		try{
			pstm		= con.prepareStatement("select * from character_items where char_id=?");
			pstm.setInt(1,  obj.charId);
			rs			= pstm.executeQuery();
			itemQ		= createItemQ(rs);
			obj.invPck = SC_WAREHOUSE_ITEM_LIST_NOTI.create_ct_item_info(itemQ).encode();
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
	}
	
	private void setItems(Connection con, ArrayList<MJCTObject> list){
		int 				size 	= list.size();
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		MJCTObject			obj		= null;
		ArrayDeque<MJCTItem> itemQ	= null;
		try{
			pstm	= con.prepareStatement("select * from character_items where char_id=?");
			for(int i=0; i<size; i++){
				obj 		= list.get(i);
				pstm.setInt(1, obj.charId);
				rs			= pstm.executeQuery();
				itemQ		= createItemQ(rs);
				obj.invPck = SC_WAREHOUSE_ITEM_LIST_NOTI.create_ct_item_info(itemQ).encode();
				SQLUtil.close(rs);
				pstm.clearParameters();
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
		}
	}
	
	private HashMap<Integer, MJCTObject> _objs;
	private MJCTSystemLoader(){
		_objs = new HashMap<Integer, MJCTObject>(64);
		ArrayList<MJCTObject> ctl	= new ArrayList<MJCTObject>(256);
		MJCTObject			obj		= null;
		Connection 			con		= null;
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		try{
			con		= DatabaseFactory.getInstance().getConnection();
			pstm	= con.prepareStatement("select * from tb_mjct_mapping");
			rs		= pstm.executeQuery();
			while(rs.next()){
				obj = new MJCTObject();
				obj.marbleId	= rs.getInt("marble_id");
				obj.charId		= rs.getInt("char_id");
				obj.name		= rs.getString("char_name");
				obj.value1 = rs.getInt("char_str");
				obj.dex = rs.getInt("char_dex");
				obj.con = rs.getInt("char_con");
				obj.wis = rs.getInt("char_wis");
				obj.ints = rs.getInt("char_int");
				obj.cha = rs.getInt("char_cha");
				obj.level = rs.getInt("char_level");
				obj.classn = rs.getInt("char_class");
				obj.hp = rs.getInt("char_hp");
				obj.mp = rs.getInt("char_mp");
				
				_objs.put(obj.marbleId, obj);
				ctl.add(obj);
			}
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			setItems(con, ctl);
			setSkills(con, ctl);
			setInfo(con, ctl);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public ArrayList<MJCTObject> getValues(){
		ArrayList<MJCTObject> list	= null;
		synchronized(_lock){
			list = new ArrayList<MJCTObject>(_objs.values());
		}
		return list;
	}
	
	public MJCTObject get(String name){
		ArrayList<MJCTObject> 	list	= getValues();
		MJCTObject 				obj 	= null;
		int 					size	= list.size();
		for(int i=0; i<size; i++){
			obj = list.get(i);
			if(obj.name.equalsIgnoreCase(name))
				return obj;
		}
		return null;
	}
	
	public MJCTObject get(int i){
		synchronized(_lock){
			return _objs.get(i);
		}
	}
	
	public MJCTObject remove(int i){
		if(!_objs.containsKey(i))
			return null;
		
		synchronized(_lock){
			return _objs.remove(i);
		}
	}
	
	public void reset(MJCTObject obj){
		if(obj == null) return;
		
		synchronized(_lock){
			_objs.put(obj.marbleId, obj);
		}
	}
	
	public void set(MJCTObject obj){
		if(obj == null) return;
		
		ThreadPool.getInstance().execute(new PckCreator(obj));
		synchronized(_lock){
			_objs.put(obj.marbleId, obj);
		}
	}
	
	private void store(){
		if(_objs == null || _objs.size() <= 0)
			return;
		
		ArrayList<MJCTObject> list 	= new ArrayList<MJCTObject>(_objs.values());
		int 				size 	= list.size();
		Connection 			con		= null;
		PreparedStatement 	pstm	= null;
		MJCTObject			obj		= null;
		try{
			con 	= DatabaseFactory.getInstance().getConnection();
			pstm	= con.prepareStatement("delete from tb_mjct_mapping");
			pstm.execute();
			SQLUtil.close(pstm);
			
			con.setAutoCommit(false);
			pstm	= con.prepareStatement("insert into tb_mjct_mapping set marble_id=?, char_id=?, char_str=?, char_dex=?, char_con=?, char_wis=?, char_int=?, char_cha=?, char_potion=?, char_hp=?, char_mp=?, char_class=?, char_level=?, char_name=? on duplicate key update char_id=?, char_name=?");
			for(int i=0; i<size; i++){
				obj = list.get(i);
				if(obj == null)
					continue;
				
				pstm.setInt(1, obj.marbleId);
				pstm.setInt(2, obj.charId);
				pstm.setInt(3, obj.value1);
				pstm.setInt(4, obj.dex);
				pstm.setInt(5, obj.con);
				pstm.setInt(6, obj.wis);
				pstm.setInt(7, obj.ints);
				pstm.setInt(8, obj.cha);
				pstm.setInt(9, obj.ele);
				pstm.setInt(10, obj.hp);
				pstm.setInt(11, obj.mp);
				pstm.setInt(12, obj.classn);
				pstm.setInt(13, obj.level);
				pstm.setString(14, obj.name);
				pstm.setInt(15, obj.charId);
				pstm.setString(16, obj.name);
				obj.dispose();
				pstm.addBatch();
				pstm.clearParameters();
			}
			pstm.executeBatch();
			pstm.clearBatch();
			con.commit();
		}catch(Exception e){
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		}finally{
			try {
				con.setAutoCommit(true);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			SQLUtil.close(pstm, con);
		}
		
		_objs.clear();
	}
	
	private int calcRows(ResultSet rs) throws SQLException{
		rs.last();
		int r = rs.getRow();
		rs.beforeFirst();
		return r;
	}
	
	
	class PckCreator implements Runnable{
		private MJCTObject obj;
		PckCreator(MJCTObject obj){
			this.obj = obj;
		}

		@Override
		public void run() {
			Connection 			con		= null;
			try{
				con		= DatabaseFactory.getInstance().getConnection();
				setItems(con, obj);
				setSkills(con, obj);
				setInfo(con, obj);
			}catch(Exception e){
				e.printStackTrace();
			}finally{
				SQLUtil.close(con);
			}
		}
	}
}
