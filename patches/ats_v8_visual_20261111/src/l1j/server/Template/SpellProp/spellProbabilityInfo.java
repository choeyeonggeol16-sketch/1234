package l1j.server.Template.SpellProp;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.CommonUtil;

public class spellProbabilityInfo {
	static spellProbabilityInfo newInstance(ResultSet rs) throws SQLException {
		spellProbabilityInfo pInfo = newInstance();
		pInfo._skill_id = rs.getInt("스킬_아이디");
		pInfo._description = rs.getString("스킬_이름");
		pInfo._skill_type = rs.getString("스킬_타입");
		pInfo._pierce_lv_weight = Double.parseDouble(rs.getString("공격자_레벨_확률증가"));
		pInfo._resis_lv_weight = Double.parseDouble(rs.getString("방어자_레벨_확률감소"));
		pInfo._int_weight = Double.parseDouble(rs.getString("인트당_확률"));
		pInfo._mr_weight = Double.parseDouble(rs.getString("마법방어_확률감소"));
		pInfo._pierce_weight = Double.parseDouble(rs.getString("적중_확률증가"));
		pInfo._resis_weight = Double.parseDouble(rs.getString("내성_확률감소"));
		pInfo._default_probability = rs.getInt("기본_확률");
		pInfo._min_probability = rs.getInt("최소_확률");
		pInfo._max_probability = rs.getInt("최대_확률");
		pInfo._magiccoefficientA = rs.getInt("마법_확률_계수a");
		pInfo._magiccoefficientB = rs.getInt("마법_확률_계수b");
		pInfo._is_loggin = rs.getBoolean("멘트_유무");
		return pInfo;
	}
	
	private static spellProbabilityInfo newInstance() {
		return new spellProbabilityInfo();
	}
	
	private boolean _is_loggin;
	private int _skill_id;
	private String _description;
	private String _skill_type;
	private double _pierce_lv_weight;
	private double _resis_lv_weight;
	private double _int_weight;
	private double _mr_weight;
	private double _pierce_weight;
	private double _resis_weight;
	private int _default_probability;
	private int _min_probability;
	private int _max_probability;
	private int _magiccoefficientA;
	private int _magiccoefficientB;
	
	private spellProbabilityInfo() {
	}

	int get_skill_id() {
		return _skill_id;
	}
	
	String get_description() {
		return _description;
	}
	
	String get_skill_type() {
		return _skill_type;
	}
	
	double get_pierce_lv_weight() {
		return _pierce_lv_weight;
	}
	
	double get_resis_lv_weight() {
		return _resis_lv_weight;
	}
	
	double get_int_weight() {
		return _int_weight;
	}
	
	double get_mr_weight() {
		return _mr_weight;
	}
	
	double get_pierce_weight() {
		return _pierce_weight;
	}
	
	double get_resis_weight() {
		return _resis_weight;
	}
	
	int get_default_probability() {
		return _default_probability;
	}
	// 마법 계수 값
	int getMagicCoefficientA() {
		return _magiccoefficientA;
	}
	// 마법 계수 값
	int getMagicCoefficientB() {
		return _magiccoefficientB;
	}
	int get_min_probability() {
		return _min_probability;
	}
	
	int get_max_probability() {
		return _max_probability;
	}
	
	void print() {
	}
	
	int calc_probability(L1PcInstance pc, L1Character target, int attacker_int, int target_mr) {
		int 확률 	= 0, 공격자레벨 	= pc.getLevel(), 방어자레벨 	= target.getLevel(), 적중포인트	= 0, 내성포인트	= 0;
		
		if (get_skill_type().equalsIgnoreCase("MAGICHIT")) {	
			내성포인트	= (int) (target_mr * _mr_weight);			
		} else if (get_skill_type().equalsIgnoreCase("ABILITY")) {
			적중포인트 = (int) ((pc.getResistance().gettechnique_hit()) * _pierce_weight);
			내성포인트 = (int) ((target.getResistance().getregist_technology()) * _resis_weight);
		} else if (get_skill_type().equalsIgnoreCase("SPIRIT")) {
			적중포인트 = (int) ((pc.getResistance().getspirit_hit()) * _pierce_weight);
			내성포인트 = (int) ((target.getResistance().getregist_spirit()) * _resis_weight);
		} else if(get_skill_type().equalsIgnoreCase("TUNDEAD")) {
			적중포인트 = (int) ((attacker_int * _int_weight) + 
					((CalcUtil.getMagicHitStat(pc.getAbility().getTotalInt()) + pc.getMagicHit() * _pierce_weight)));
			내성포인트 = (int) (target_mr * _mr_weight);		
		} else if(get_skill_type().equalsIgnoreCase("TAMINGMONSTER")) {	
			내성포인트 = (int) (target_mr * _mr_weight);		
		}
	    
		// 적중 내성이 업슨 스킬 예 : 캔슬레이션 등등 
		if (get_skill_type().equalsIgnoreCase("MAGICHIT")) {
			if (공격자레벨 >= 방어자레벨) {
				확률 = (int) (_magiccoefficientA * (pc.getMagicBonus() + pc.getMagicLevel()) + 
						(CalcUtil.getMagicHitStat(pc.getAbility().getTotalInt()) + pc.getMagicHit() * _pierce_weight)
						+ _magiccoefficientB + _default_probability) - 내성포인트;
			}		
			else if (공격자레벨 < 방어자레벨) {
				확률 = (int) (_magiccoefficientA * (pc.getMagicBonus() + pc.getMagicLevel()) + 
						(CalcUtil.getMagicHitStat(pc.getAbility().getTotalInt()) + pc.getMagicHit() * _pierce_weight)
						+ _magiccoefficientB + _default_probability) - 내성포인트;
			}	
		} else if(get_skill_type().equalsIgnoreCase("ABILITY") || get_skill_type().equalsIgnoreCase("SPIRIT")) { // 적중 내성이 있는 스킬 쇼크스턴 등등
			if (공격자레벨 >= 방어자레벨) {
				확률 = (int) (((공격자레벨 - 방어자레벨) * _pierce_lv_weight) + (적중포인트 - 내성포인트)) + _default_probability;
			}		
			else if (공격자레벨 < 방어자레벨) {
				확률 = (int) (((공격자레벨 - 방어자레벨) * _resis_lv_weight) + (적중포인트 - 내성포인트)) + _default_probability;
			}			
		} else if(get_skill_type().equalsIgnoreCase("TUNDEAD")) {
			if (공격자레벨 >= 방어자레벨) {
				확률 = (int) (_magiccoefficientA * (pc.getMagicBonus() + pc.getMagicLevel()) + 
						(CalcUtil.getMagicHitStat(pc.getAbility().getTotalInt()) + pc.getMagicHit() * _pierce_weight)
						+ 적중포인트 + _default_probability) - 내성포인트;
			} else if (공격자레벨 < 방어자레벨) {
				확률 = (int) (_magiccoefficientA * (pc.getMagicBonus() + pc.getMagicLevel()) + 
						(CalcUtil.getMagicHitStat(pc.getAbility().getTotalInt()) + pc.getMagicHit() * _pierce_weight)
						+ 적중포인트 + _default_probability) - 내성포인트;
			}
		} else if(get_skill_type().equalsIgnoreCase("TAMINGMONSTER")) {
			if (공격자레벨 >= 방어자레벨) {
				확률 = (int) (CommonUtil.random(pc.getMagicBonus() + pc.getMagicLevel()) + _default_probability) - 내성포인트;

			} else if (공격자레벨 < 방어자레벨) {
				확률 = (int) (CommonUtil.random(pc.getMagicBonus() + pc.getMagicLevel()) + _default_probability) - 내성포인트;
			}
		}	
		if(!(get_skill_type().equalsIgnoreCase("ABILITY") || get_skill_type().equalsIgnoreCase("SPIRIT"))) {
			if(!pc.isWizard()) {
				확률 -= 20;
			}
		}	
		확률 = Math.max(확률, _min_probability);
		확률 = Math.min(확률, _max_probability);
		
		if (_is_loggin) {
			if (pc.isGm()) {
				System.out.println(String.format("스킬 : %s(%d,%d~%d) 공격자:%s(%d), 타겟:%s(%d), 확률:%d", _description, _skill_id, _min_probability, _max_probability, pc.getName(), 적중포인트, target.getName(), 내성포인트, 확률));
			}
		}
		return 확률;
	}
	
}
