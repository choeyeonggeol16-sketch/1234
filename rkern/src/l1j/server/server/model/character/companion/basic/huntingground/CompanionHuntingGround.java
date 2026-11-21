package l1j.server.server.model.character.companion.basic.huntingground;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class CompanionHuntingGround {
	private static HashMap<Integer, CompanionHuntingGround> m_hunting_grounds;
	public static void do_load(){
		HashMap<Integer, CompanionHuntingGround> hunting_grounds = new HashMap<Integer, CompanionHuntingGround>();
		Selector.exec("select * from companion_hunting_ground", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					CompanionHuntingGround hground = newInstance(rs);
					hunting_grounds.put(hground.get_map_id(), hground);
				}
			}
		});
		m_hunting_grounds = hunting_grounds;
	}
	
	public static CompanionHuntingGround get_hunting_ground(int map_id){
		return m_hunting_grounds.get(map_id);
	}
	
	private static CompanionHuntingGround newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_map_id(rs.getInt("map_id"))
				.set_magnification_by_exp((double)rs.getInt("magnification_by_exp") * 0.01);
	}
	
	private static CompanionHuntingGround newInstance(){
		return new CompanionHuntingGround();
	}
	
	private int m_map_id;
	private double m_magnification_by_exp;
	private CompanionHuntingGround(){}
	
	public CompanionHuntingGround set_map_id(int map_id){
		m_map_id = map_id;
		return this;
	}
	public CompanionHuntingGround set_magnification_by_exp(double magnification_by_exp){
		m_magnification_by_exp = magnification_by_exp;
		return this;
	}
	public int get_map_id(){
		return m_map_id;
	}
	public double get_magnification_by_exp(){
		return m_magnification_by_exp;
	}
}
