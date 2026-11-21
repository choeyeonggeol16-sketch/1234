package l1j.server.server.model.item.collection.loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.item.collection.CollectionSpell;
import l1j.server.server.utils.SQLUtil;
/** 
 * CollectionCharInfo
 * MJSoft Character TradeSystem - Spell Loader.
 * spell icon loader
 * made by mjsoft, 2016.
 **/
public class CollectionSpellLoader {
	private static CollectionSpellLoader _instance;
	public static CollectionSpellLoader getInstance(){
		if(_instance == null)
			_instance = new CollectionSpellLoader();
		return _instance;
	}
	
	public static void release(){
		
	}
	
	private HashMap<Integer, CollectionSpell> _spells;
	private CollectionSpellLoader(){
		Connection 			con		= null;
		PreparedStatement 	pstm	= null;
		ResultSet 			rs		= null;
		CollectionSpell			sp		= null;
		_spells						= new HashMap<Integer, CollectionSpell>(256);
		try{
			con		= DatabaseFactory.getInstance().getConnection();
			pstm	= con.prepareStatement("select * from tb_mjct_spellIcon");
			rs		= pstm.executeQuery();
			while(rs.next()){
				sp 			= new CollectionSpell();
				sp.id		= rs.getInt("spellId") + 1;
				sp.name		= rs.getString("name");
				sp.icon		= rs.getInt("icon");
				sp.xicon	= rs.getInt("xicon");
				_spells.put(sp.id, sp);
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public CollectionSpell get(int i){
		return _spells.get(i);
	}
	
	public void clear(){
		if(_spells != null){
			_spells.clear();
			_spells = null;
		}
	}
}
