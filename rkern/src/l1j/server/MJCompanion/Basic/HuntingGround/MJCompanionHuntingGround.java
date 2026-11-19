package l1j.server.MJCompanion.Basic.HuntingGround;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJCompanionHuntingGround {
	private static HashMap<Integer, MJCompanionHuntingGround> m_hunting_grounds;
	public static void do_load(){
		HashMap<Integer, MJCompanionHuntingGround> hunting_grounds = new HashMap<Integer, MJCompanionHuntingGround>();
		Selector.exec("select * from companion_hunting_ground", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionHuntingGround hground = newInstance(rs);
					hunting_grounds.put(hground.get_map_id(), hground);
				}
			}
		});
		m_hunting_grounds = hunting_grounds;
	}
	
	public static MJCompanionHuntingGround get_hunting_ground(int map_id){
		return m_hunting_grounds.get(map_id);
	}
	
	private static MJCompanionHuntingGround newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_map_id(rs.getInt("map_id"))
				.set_magnification_by_exp((double)rs.getInt("magnification_by_exp") * 0.01);
	}
	
	private static MJCompanionHuntingGround newInstance(){
		return new MJCompanionHuntingGround();
	}
	
	private int m_map_id;
	private double m_magnification_by_exp;
	private MJCompanionHuntingGround(){}
	
	public MJCompanionHuntingGround set_map_id(int map_id){
		m_map_id = map_id;
		return this;
	}
	public MJCompanionHuntingGround set_magnification_by_exp(double magnification_by_exp){
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
