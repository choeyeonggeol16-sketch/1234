package l1j.server.server.model.item.collection.loader;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.item.collection.CollectionCharInfo;
import l1j.server.server.model.item.collection.CollectionItem;
import l1j.server.server.model.item.collection.CollectionObject;
import l1j.server.server.model.item.collection.CollectionSpell;
import l1j.server.server.model.network.core.sc.packet.SC_CTPacket;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_WAREHOUSE_ITEM_LIST_NOTI;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.SQLUtil;
public class CollectionSystemLoader {
	private static final Object 	_lock = new Object();
	private static volatile CollectionSystemLoader _instance;
	public static CollectionSystemLoader getInstance(){
        CollectionSystemLoader current = _instance;
        if (current != null) return current;
        synchronized (_lock) {
            if (_instance == null) _instance = new CollectionSystemLoader();
            return _instance;
        }
    }
	
    public static void release(){
        // SQL mapping is authoritative and updated inside the item receipt.
        // Never truncate/rebuild it from an incomplete presentation cache.
        synchronized (_lock) {
            if (_instance != null) _instance._objs.clear();
            _instance = null;
        }
    }

	private CollectionCharInfo createCharInfo(ResultSet rs) throws SQLException{
		CollectionCharInfo cInfo = new CollectionCharInfo();
		if (!rs.next()) throw new SQLException("Marble character missing");
        {
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
	
	private void setInfo(Connection con, CollectionObject obj){
		PreparedStatement 		pstm	= null;
		ResultSet 				rs		= null;
		CollectionCharInfo			cInfo	= null;
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
	
	private void setInfo(Connection con, ArrayList<CollectionObject> list){
		PreparedStatement 	pstm	= null;
		ResultSet 				rs		= null;
		CollectionObject				obj	= null;
		CollectionCharInfo			cInfo	= null;
		int							size	= list.size();	
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
	
	private void setSkills(Connection con, CollectionObject obj){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		ArrayDeque<CollectionSpell> spQ	= null;
		CollectionSpell			sp		= null;
		int					rows	= 0;
		try{
			pstm	= con.prepareStatement("select * from character_skills where char_obj_id=?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			pstm.setInt(1, obj.charId);
			rs		= pstm.executeQuery();
			rows	= calcRows(rs);
			if(rows > 0){
				spQ = new ArrayDeque<CollectionSpell>(rows);
				while(rs.next()){
					sp	= CollectionSpellLoader.getInstance().get(rs.getInt("skill_id"));
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
	
	private void setSkills(Connection con, ArrayList<CollectionObject> list){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		CollectionObject			obj		= null;
		ArrayDeque<CollectionSpell> spQ	= null;
		CollectionSpell			sp		= null;
		int					size	= list.size();
		int					rows	= 0;
		try{
			pstm		= con.prepareStatement("select * from character_skills where char_obj_id=?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			for(int i=0; i<size; i++){
				obj		= list.get(i);
				pstm.setInt(1, obj.charId);
				rs		= pstm.executeQuery();
				rows	= calcRows(rs);
				spQ = null;
				if(rows > 0){
					spQ	= new ArrayDeque<CollectionSpell>(rows);
					while(rs.next()){
						sp	= CollectionSpellLoader.getInstance().get(rs.getInt("skill_id"));
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
	
	private ArrayDeque<CollectionItem> createItemQ(ResultSet rs) throws SQLException{
		ArrayDeque<CollectionItem> 	itemQ 	= null;
		CollectionItem				item	= null;
		int 					rows	= calcRows(rs);
		if(rows > 0){
			itemQ = new ArrayDeque<CollectionItem>(rows);
			while(rs.next()){
				item			= new CollectionItem();
				int itemId 		= rs.getInt("item_id");
                L1Item itm 		= CObjectDB.LoadItem().getTemplate(itemId);
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
	
	private void setItems(Connection con, CollectionObject obj){
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		ArrayDeque<CollectionItem> itemQ	= null;
		try{
			pstm		= con.prepareStatement("select * from character_items where char_id=?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
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
	
	private void setItems(Connection con, ArrayList<CollectionObject> list){
		int 				size 	= list.size();
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		CollectionObject			obj		= null;
		ArrayDeque<CollectionItem> itemQ	= null;
		try{
			pstm	= con.prepareStatement("select * from character_items where char_id=?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
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
	
	private final HashMap<Integer, CollectionObject> _objs;
	private CollectionSystemLoader(){
		_objs = new HashMap<Integer, CollectionObject>(64);
		ArrayList<CollectionObject> ctl	= new ArrayList<CollectionObject>(256);
		CollectionObject			obj		= null;
		Connection 			con		= null;
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		try{
			con		= DatabaseFactory.getInstance().getConnection();
			pstm	= con.prepareStatement("select * from tb_mjct_mapping");
			rs		= pstm.executeQuery();
			while(rs.next()){
				obj = new CollectionObject();
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
	
	public ArrayList<CollectionObject> getValues(){
		ArrayList<CollectionObject> list	= null;
		synchronized(_lock){
			list = new ArrayList<CollectionObject>(_objs.values());
		}
		return list;
	}
	
	public CollectionObject get(String name){
		ArrayList<CollectionObject> 	list	= getValues();
		CollectionObject 				obj 	= null;
		int 					size	= list.size();
		for(int i=0; i<size; i++){
			obj = list.get(i);
            if (obj.name != null && obj.name.equalsIgnoreCase(name)) {
                requestPreview(obj);
                return obj;
            }
		}
		return null;
	}
	
	public CollectionObject get(int i){
		synchronized(_lock){
			return _objs.get(i);
		}
	}
	
	public CollectionObject remove(int i){
		synchronized(_lock){
			return _objs.remove(i);
		}
	}
	
	public void reset(CollectionObject obj){
		if(obj == null) return;
		
		synchronized(_lock){
			_objs.put(obj.marbleId, obj);
		}
	}
	
    // Called on the existing receipt DB worker after durable COMMIT. If the
    // loader has been released, its next initialization reads the durable DB.
    public static void publishCommitted(CollectionObject obj) {
        if (obj == null) throw new IllegalStateException("Committed marble missing");
        final CollectionSystemLoader loader;
        synchronized (_lock) {
            loader = _instance;
            if (loader == null) return;
            loader._objs.put(obj.marbleId, obj);
        }
        loader.requestPreview(obj);
    }

    public static void removeCommitted(int marbleId) {
        synchronized (_lock) {
            if (_instance != null) _instance._objs.remove(marbleId);
        }
    }

    public void set(CollectionObject obj) {
        if (obj == null) return;
        synchronized (_lock) {
            if (_instance != this) return;
            _objs.put(obj.marbleId, obj);
        }
        requestPreview(obj);
    }

    private boolean isCurrent(CollectionObject obj) {
        synchronized (_lock) {
            return _instance == this && _objs.get(obj.marbleId) == obj;
        }
    }

    private void requestPreview(CollectionObject obj) {
        if (!isCurrent(obj) || !obj.tryQueuePreview()) return;
        try {
            CacheD.getInstance().submitDbJob(new PckCreator(obj));
        } catch (RuntimeException failure) {
            obj.finishPreview();
            java.util.logging.Logger.getLogger(CollectionSystemLoader.class.getName())
                    .log(java.util.logging.Level.WARNING, "Marble preview rejected", failure);
        }
    }

	private int calcRows(ResultSet rs) throws SQLException{
		rs.last();
		int r = rs.getRow();
		rs.beforeFirst();
		return r;
	}
	
	
	class PckCreator implements Runnable{
		private final CollectionObject obj;
		PckCreator(CollectionObject obj){
			this.obj = obj;
		}

		@Override
		public void run() {
			Connection 			con		= null;
			try{
                if (!isCurrent(obj)) return;
                con = DatabaseFactory.getInstance().getConnection();
                if (obj.invPck == null) setItems(con, obj);
                if (obj.spPck == null) setSkills(con, obj);
                if (obj.infoPck == null) setInfo(con, obj);
			}catch(Exception e){
				e.printStackTrace();
			}finally{
                try { SQLUtil.close(con); }
                finally { obj.finishPreview(); }
			}
		}
	}
}
