package l1j.server.MJRaidSystem.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.MJRaidSystem.BossSkill.MJRaidBossCombo;
import l1j.server.MJRaidSystem.BossSkill.MJRaidBossSkill;
import l1j.server.server.utils.SQLUtil;

/** 보스 스킬을 로드하고 관리할 클래스 **/
public class MJRaidBossSkillLoader {
	private static MJRaidBossSkillLoader _instance;
	public static MJRaidBossSkillLoader getInstance(){
		if(_instance == null)
			_instance = new MJRaidBossSkillLoader();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		MJRaidBossSkillLoader tmp = _instance;
		_instance = new MJRaidBossSkillLoader();
		tmp.clear();
		tmp = null;
	}
	
	private HashMap<Integer, ArrayList<MJRaidBossSkill>> _skills;
	private HashMap<Integer, ArrayList<MJRaidBossCombo>> _combos;
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private MJRaidBossSkillLoader(){
		Connection 			con			= null;
		PreparedStatement 	pstm 		= null;
		ResultSet 			rs 			= null;
		MJRaidBossSkill		skill		= null;
		ArrayList<MJRaidBossSkill> list = null;
		int					id			= 0;
		int[]				raidIds		= null;
		String				column		= "";
		HashMap				skillMap	= new HashMap<Integer, MJRaidBossSkill>(64);
		_skills							= new HashMap<Integer, ArrayList<MJRaidBossSkill>>(64);
		_combos							= new HashMap<Integer, ArrayList<MJRaidBossCombo>>();
		
		try{
			con 	= DatabaseFactory.getInstance().getConnection();
			pstm	= con.prepareStatement("select * from tb_mjraid_skills");
			rs		= pstm.executeQuery();
			while(rs.next()){
				column	= "className";
				skill 	= createSkillInstance(rs.getString(column));
				if(skill == null)
					continue;
				
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
				
				for(int i=0; i<raidIds.length; i++){
					list = _skills.get(raidIds[i]);
					if(list == null){
						list = new ArrayList<MJRaidBossSkill>(4);
						_skills.put(raidIds[i], list);
					}
					list.add(skill);
				}				
			}
			
			SQLUtil.close(rs);
			SQLUtil.close(pstm);

			pstm			= con.prepareStatement("select * from tb_mjraid_combo");
			rs				= pstm.executeQuery();
			int raidId		= 0;
			int sid			= 0;
			int phase		= 0;
			int[] skill_ids	= null;
			String nameid 	= null;
			ArrayList<MJRaidBossCombo> comboList = null;
			MJRaidBossCombo combo = null;
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
					comboList = new ArrayList<MJRaidBossCombo>();
					_combos.put(raidId, comboList);
				}
				
				combo		= new MJRaidBossCombo();
				combo.setNameId(nameid);
				combo.setPhase(phase);
				for(int i=0; i<skill_ids.length; i++){
					sid 	= skill_ids[i];
					skill 	= (MJRaidBossSkill) skillMap.get(sid);
					if(skill == null)
						continue;
					
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
		if(s.equalsIgnoreCase("once"))
			return MJRaidBossSkill.BSRTYPE_ONCE;
		else if(s.equalsIgnoreCase("line"))
			return MJRaidBossSkill.BSRTYPE_LINE;
		else if(s.equalsIgnoreCase("front"))
			return MJRaidBossSkill.BSRTYPE_FRONT;
		else if(s.equalsIgnoreCase("rect"))
			return MJRaidBossSkill.BSRTYPE_RECT;
		else if(s.equalsIgnoreCase("screen"))
			return MJRaidBossSkill.BSRTYPE_SCREEN;
		return MJRaidBossSkill.BSRTYPE_ONCE;
	}
	
	private int[] parseIntArray(String s){
		String[] arrs	= s.split(",");
		int size		= arrs.length;
		int[]	results	= new int[size];
		for(int i=0; i<size; i++)
			results[i] = Integer.parseInt(arrs[i]);
		return results;
	}
	
	private MJRaidBossSkill createSkillInstance(String s){
		MJRaidBossSkill skill = null;
		try{
			Class<?> cls	= Class.forName(complementClassName(s));
			skill			= (MJRaidBossSkill)cls.newInstance();
		}catch(Exception e){
			e.printStackTrace();
		}
		return skill;
	}
	
	private String complementClassName(String className){
		if (className.contains(".")) {
			return className;
		}
		if (className.contains(",")) {
			return className;
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("l1j.server.MJRaidSystem.BossSkill.").append(className);
		return sb.toString();
	}
	
	public ArrayList<MJRaidBossSkill> get(int i){
		return _skills.get(i);
	}
	
	public ArrayList<MJRaidBossCombo> getCombo(int i){
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
