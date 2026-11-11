package l1j.server.RaidSystem.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import l1j.server.L1DatabaseFactory;
import l1j.server.RaidSystem.Dragon_Raid_Skill;
import l1j.server.RaidSystem.Dragon_Raid_Skill_Combo;
import l1j.server.server.utils.SQLUtil;


public class Dragon_Raid_Skill_Loader {
	private static Dragon_Raid_Skill_Loader _instance;
	public static Dragon_Raid_Skill_Loader getInstance() {
		if(_instance == null)
			_instance = new Dragon_Raid_Skill_Loader();
		return _instance;
	}
	public static void release() {
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	public static void reload() {
		Dragon_Raid_Skill_Loader tmp = _instance;
		_instance = new Dragon_Raid_Skill_Loader();
		tmp.clear();
		tmp = null;
	}
	
	private HashMap<Integer, ArrayList<Dragon_Raid_Skill>> _skills;
	private HashMap<Integer, ArrayList<Dragon_Raid_Skill_Combo>> _combos;
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Dragon_Raid_Skill_Loader(){
		Connection 			con			= null;
		PreparedStatement 	pstm 		= null;
		ResultSet 			rs 			= null;
		Dragon_Raid_Skill		skill		= null;
		ArrayList<Dragon_Raid_Skill> list = null;
		int					id			= 0;
		int[]				raidIds		= null;
		String				column		= "";
		HashMap				skillMap	= new HashMap<Integer, Dragon_Raid_Skill>(64);
		_skills							= new HashMap<Integer, ArrayList<Dragon_Raid_Skill>>(64);
		_combos							= new HashMap<Integer, ArrayList<Dragon_Raid_Skill_Combo>>();
		
		try{
			con 	= L1DatabaseFactory.getInstance().getConnection();
			pstm	= con.prepareStatement("select * from raid_skills");
			rs		= pstm.executeQuery();
			while(rs.next()){
				column	= "className";
				skill 	= createSkillInstance(rs.getString(column));
				if(skill == null) {
					continue;
				}
				column	= "id";
				id		= rs.getInt(column);
				skillMap.put(id, skill);
				
				column	= "raidIds";
				raidIds	= parseIntArray(rs.getString(column));
				
				column	= "actId";
				skill.setActionId(rs.getInt(column));
				
				column	= "rangeType";
				skill.setType(parseRangeType(rs.getString(column)));
				
				column	= "range";
				skill.setRange(rs.getInt(column));
				
				column	= "dmgMin";
				skill.setDamageMin(rs.getInt(column));
				
				column	= "dmgMax";
				skill.setDamageMax(rs.getInt(column));
				
				column	= "effectId";
				skill.setEffectId(rs.getInt(column));
				
				column	= "ratio";
				skill.setRatio(rs.getInt(column));
				
				column	= "isMagic";
				skill.setIsMagic(rs.getBoolean(column));
				
				for(int i=0; i<raidIds.length; i++) {
					list = _skills.get(raidIds[i]);
					if(list == null) {
						list = new ArrayList<Dragon_Raid_Skill>(4);
						_skills.put(raidIds[i], list);
					}
					list.add(skill);
				}				
			}
			
			SQLUtil.close(rs);
			SQLUtil.close(pstm);

			pstm			= con.prepareStatement("select * from raid_combo");
			rs				= pstm.executeQuery();
			int raidId		= 0;
			int sid			= 0;
			int phase		= 0;
			int[] skill_ids	= null;
			String nameid 	= null;
			ArrayList<Dragon_Raid_Skill_Combo> comboList = null;
			Dragon_Raid_Skill_Combo combo = null;
			while(rs.next()){
				column		= "raidid";
				raidId		= rs.getInt(column);
				
				column		= "nameid";
				nameid		= rs.getString(column);
				
				column		= "skill_ids";
				skill_ids	= parseIntArray(rs.getString(column));
				
				column		= "phase";
				phase		= rs.getInt(column);
				
				comboList	= _combos.get(raidId);
				if(comboList == null){
					comboList = new ArrayList<Dragon_Raid_Skill_Combo>();
					_combos.put(raidId, comboList);
				}
				
				combo		= new Dragon_Raid_Skill_Combo();
				combo.setNameId(nameid);
				combo.setPhase(phase);
				for(int i=0; i<skill_ids.length; i++) {
					sid 	= skill_ids[i];
					skill 	= (Dragon_Raid_Skill) skillMap.get(sid);
					if(skill == null) {
						continue;
					}		
					combo.add(skill);
				}
				comboList.add(combo);
			}
			skillMap.clear();
		}catch(Exception e){
			e.printStackTrace();
		}finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	private int parseRangeType(String s){
		if(s.equalsIgnoreCase("once")) {
			return Dragon_Raid_Skill.BSRTYPE_ONCE;
		}
		else if(s.equalsIgnoreCase("line")) {
			return Dragon_Raid_Skill.BSRTYPE_LINE;
		}
		else if(s.equalsIgnoreCase("front")) {
			return Dragon_Raid_Skill.BSRTYPE_FRONT;
		}
		else if(s.equalsIgnoreCase("rect")) {
			return Dragon_Raid_Skill.BSRTYPE_RECT;
		}
		else if(s.equalsIgnoreCase("screen")) {
			return Dragon_Raid_Skill.BSRTYPE_SCREEN;
		}
		return Dragon_Raid_Skill.BSRTYPE_ONCE;
	}
	
	private int[] parseIntArray(String s){
		String[] arrs	= s.split(",");
		int size		= arrs.length;
		int[]	results	= new int[size];
		for(int i=0; i<size; i++)
			results[i] = Integer.parseInt(arrs[i]);
		return results;
	}
	
	private Dragon_Raid_Skill createSkillInstance(String s) {
		Dragon_Raid_Skill skill = null;
		try{
			Class<?> cls	= Class.forName(complementClassName(s));
			skill			= (Dragon_Raid_Skill)cls.newInstance();
		} catch(Exception e) {
			e.printStackTrace();
		}
		return skill;
	}
	
	private String complementClassName(String className) {
		if (className.contains(".")) {
			return className;
		}
		if (className.contains(",")) {
			return className;
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("l1j.server.RaidSystem.Skill.").append(className);
		return sb.toString();
	}
	
	public ArrayList<Dragon_Raid_Skill> get(int i) {
		return _skills.get(i);
	}
	
	public ArrayList<Dragon_Raid_Skill_Combo> getCombo(int i) {
		return _combos.get(i);
	}
	
	public void clear(){
		if(_skills != null){
			_skills.clear();
			_skills = null;
		}
		
		if(_combos != null){
			_combos.clear();
			_combos = null;
		}
	}
}

