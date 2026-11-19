package l1j.server.MJItemSkillSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javolution.util.FastMap;
import l1j.server.DatabaseFactory;
import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_ChainSpot;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_Chaotic;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_ChaoticAndNormal;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_CriticalAttack;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_CriticalAttack2;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_DamagePoison;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_DiceDagger;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_Disease;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_HPDrain;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_HPDrainAndGet;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_Hold;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_MPDrain;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_MPDrainAndDisease;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_MPDrainAndGet;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_NormalAttack;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_NormalRangeAttack;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_PumpkinCurse;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_Silence;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_TargetWideAttack;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_TurnUndead;
import l1j.server.MJItemSkillSystem.Model.Attack.Atk_WideAttack;
import l1j.server.MJItemSkillSystem.Model.Defence.Def_HpRegen;
import l1j.server.MJItemSkillSystem.Model.Defence.Def_MpRegen;
import l1j.server.MJItemSkillSystem.Model.Defence.Def_ProbReduction;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.utils.SQLUtil;

public class MJItemSkillModelLoader {
	private static MJItemSkillModelLoader _instance;
	public static MJItemSkillModelLoader getInstance(){
		if(_instance == null)
			_instance = new MJItemSkillModelLoader();
		return _instance;
	}
	
	public static void reload(){
		MJItemSkillModelLoader tmp = _instance;
		_instance = new MJItemSkillModelLoader();
		if(tmp != null){
			tmp.clear();
			tmp = null;
		}
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	private FastMap<Integer, MJItemSkillModel> _models_atk;
	private FastMap<Integer, MJItemSkillModel> _models_def;
	
	private MJItemSkillModelLoader(){
		Connection 			con 	= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs 		= null;
		try{
			con 		= DatabaseFactory.getInstance().getConnection();
			pstm		= con.prepareStatement("select * from tb_itemskill_model");
			rs			= pstm.executeQuery();
			int rows	= SQLUtil.calcRows(rs);
			_models_atk = new FastMap<Integer, MJItemSkillModel>(rows);
			_models_def = new FastMap<Integer, MJItemSkillModel>(rows);
			while(rs.next()){
				MJItemSkillModel model = parseType(rs.getString("type"));
				if(model == null)
					continue;
				
				model.itemId 			= rs.getInt("item_id");
				model.condition			= rs.getInt("condition");
				model.d_prob			= rs.getInt("default_prob");
				model.e_prob			= rs.getInt("enchant_prob");
				model.s_prob			= parseStat(rs.getString("stat_prob"));
				model.s_weight			= rs.getInt("stat_weight") * 0.01D;
				model.min_val			= rs.getInt("min_val");
				model.max_val			= rs.getInt("max_val");
				model.stat_val			= parseStat(rs.getString("stat_val"));
				model.stat_valweight 	= rs.getInt("stat_val_weight") * 0.01D;
				model.e_val				= rs.getInt("enchant_val");
				model.e_valweight		= rs.getInt("enchant_val_weight") * 0.01D;
				model.eff_id			= rs.getInt("effect");
				model.attr_type			= parseAttr(rs.getString("attr_type"));
				model.is_magic			= rs.getBoolean("is_magic");
				
				if(model.isAttack())
					_models_atk.put(model.itemId, model);
				else
					_models_def.put(model.itemId, model);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public MJItemSkillModel getAtk(int i){
		return _models_atk.get(i);
	}
	
	public MJItemSkillModel getDef(int i){
		return _models_def.get(i);		
	}
	
	public void clear(){
		if(_models_atk != null){
			_models_atk.clear();
			_models_atk = null;
		}
		
		if(_models_def != null){
			_models_def.clear();
			_models_def = null;
		}
	}
	
	private int parseAttr(String type){
		if(type.equalsIgnoreCase("earth"))
			return L1Skills.ATTR_EARTH;
		else if(type.equalsIgnoreCase("fire"))
			return L1Skills.ATTR_FIRE;
		else if(type.equalsIgnoreCase("water"))
			return L1Skills.ATTR_WATER;
		else if(type.equalsIgnoreCase("wind"))
			return L1Skills.ATTR_WIND;
		return L1Skills.ATTR_NONE;
	}
	
	private int parseStat(String type){
		if(type.equalsIgnoreCase("str"))
			return 1;
		else if(type.equalsIgnoreCase("dex"))
			return 2;
		else if(type.equalsIgnoreCase("con"))
			return 3;
		else if(type.equalsIgnoreCase("wis"))
			return 4;
		else if(type.equalsIgnoreCase("intel"))
			return 5;
		else if(type.equalsIgnoreCase("cha"))
			return 6;
	
		return 0;
	}
	
	private MJItemSkillModel parseType(String type){
		if(type.equalsIgnoreCase("무기-단일(근거리)"))
			return new Atk_NormalAttack();
		else if(type.equalsIgnoreCase("무기-단일(원거리)"))
			return new Atk_NormalRangeAttack();
		else if(type.equalsIgnoreCase("무기-광역(타격자)"))
			return new Atk_WideAttack();
		else if(type.equalsIgnoreCase("무기-광역(피격자)"))
			return new Atk_TargetWideAttack();
		else if(type.equalsIgnoreCase("무기-약점 노출"))
			return new Atk_ChainSpot();
		else if(type.equalsIgnoreCase("무기-카오틱 추가 타격"))
			return new Atk_Chaotic();
		else if(type.equalsIgnoreCase("무기-카오틱 추가 타격(단일근거리)"))
			return new Atk_ChaoticAndNormal();
		else if(type.equalsIgnoreCase("무기-대미지 독"))
			return new Atk_DamagePoison();
		else if(type.equalsIgnoreCase("무기-악운의 단검"))
			return new Atk_DiceDagger();
		else if(type.equalsIgnoreCase("무기-디지즈"))
			return new Atk_Disease();
		else if(type.equalsIgnoreCase("무기-홀드"))
			return new Atk_Hold();
		else if(type.equalsIgnoreCase("무기-체력흡수"))
			return new Atk_HPDrain();
		else if(type.equalsIgnoreCase("무기-체력흡수(대미지 가중)"))
			return new Atk_HPDrainAndGet();
		else if(type.equalsIgnoreCase("무기-마나흡수"))
			return new Atk_MPDrain();
		else if(type.equalsIgnoreCase("무기-마나흡수(대미지 가중)"))
			return new Atk_MPDrainAndGet();
		else if(type.equalsIgnoreCase("무기-마나흡수(디지즈)"))
			return new Atk_MPDrainAndDisease();
		else if(type.equalsIgnoreCase("무기-펌프킨 커스"))
			return new Atk_PumpkinCurse();
		else if(type.equalsIgnoreCase("무기-사일런스"))
			return new Atk_Silence();
		else if(type.equalsIgnoreCase("무기-턴 언데드"))
			return new Atk_TurnUndead();
		else if(type.equalsIgnoreCase("무기-확률치명타"))
			return new Atk_CriticalAttack();
		else if(type.equalsIgnoreCase("무기-확률치명타(검귀)"))
			return new Atk_CriticalAttack2();
		else if(type.equalsIgnoreCase("방어구-체력회복"))
			return new Def_HpRegen();
		else if(type.equalsIgnoreCase("방어구-마나회복"))
			return new Def_MpRegen();
		else if(type.equalsIgnoreCase("방어구-확률리덕션"))
			return new Def_ProbReduction();
		return null;
	}
}
