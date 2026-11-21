package l1j.server.server.model.character.animation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class SprBoundary {
	private static HashMap<Integer, SprBoundary> m_boundaries;
	public static void do_load(){
		HashMap<Integer, SprBoundary> boundaries = new HashMap<Integer, SprBoundary>();
		Selector.exec("select * from spr_boundary", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					SprBoundary o = newInstance(rs);
					boundaries.put(o.get_sprite_id(), o);
				}
			}
		});
		m_boundaries = boundaries;
	}
	
	public static SprBoundary get_boundary(int sprite_id){
		return m_boundaries.get(sprite_id);
	}

	private static SprBoundary newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_sprite_id(rs.getInt("sprite_id"))
				.set_boundary_level(rs.getInt("boundary_level"));
	}

	private static SprBoundary newInstance(){
		return new SprBoundary();
	}

	private int m_sprite_id;
	private int m_boundary_level;
	private SprBoundary(){}

	public SprBoundary set_sprite_id(int sprite_id){
		m_sprite_id = sprite_id;
		return this;
	}
	public SprBoundary set_boundary_level(int boundary_level){
		m_boundary_level = boundary_level;
		return this;
	}
	public int get_sprite_id(){
		return m_sprite_id;
	}
	public int get_boundary_level(){
		return m_boundary_level;
	}

}

