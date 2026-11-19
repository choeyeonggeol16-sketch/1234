package l1j.server.MJCompanion.Basic.FriendShip;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJCompanionFriendShipBonus {
	private static HashMap<Integer, MJCompanionFriendShipBonus> m_friend_ship_bonuses;
	public static void do_load(){
		HashMap<Integer, MJCompanionFriendShipBonus> friend_ship_bonuses = new HashMap<Integer, MJCompanionFriendShipBonus>();
		Selector.exec("select * from companion_friend_ship_bonus", new FullSelectorHandler(){

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionFriendShipBonus bInfo = newInstance(rs);
					friend_ship_bonuses.put(bInfo.get_item_id(), bInfo);
				}
			}
			
		});
		m_friend_ship_bonuses = friend_ship_bonuses;
	}
	
	public static MJCompanionFriendShipBonus from_item_id(int item_id){
		return m_friend_ship_bonuses.get(item_id);
	}
	
	private static MJCompanionFriendShipBonus newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_item_id(rs.getInt("item_id"))
				.set_friend_ship_guage(rs.getInt("friend_ship_guage"))
				.set_friend_ship_marble(rs.getInt("friend_ship_marble"))
				.set_effect_id(rs.getInt("effect_id"));
	}
	
	private static MJCompanionFriendShipBonus newInstance(){
		return new MJCompanionFriendShipBonus();
	}
	
	private int m_item_id;
	private int m_friend_ship_guage;
	private int m_friend_ship_marble;
	private int m_effect_id;
	private MJCompanionFriendShipBonus(){	
	}
	
	public MJCompanionFriendShipBonus set_item_id(int item_id){
		m_item_id = item_id;
		return this;
	}
	public MJCompanionFriendShipBonus set_friend_ship_guage(int friend_ship_guage){
		m_friend_ship_guage = friend_ship_guage;
		return this;
	}
	public MJCompanionFriendShipBonus set_friend_ship_marble(int friend_ship_marble){
		m_friend_ship_marble = friend_ship_marble;
		return this;
	}
	public MJCompanionFriendShipBonus set_effect_id(int effect_id){
		m_effect_id = effect_id;
		return this;
	}
	
	public int get_item_id(){
		return m_item_id;
	}
	public int get_friend_ship_guage(){
		return m_friend_ship_guage;
	}
	public int get_friend_ship_marble(){
		return m_friend_ship_marble;
	}
	public int get_effect_id(){
		return m_effect_id;
	}
}
