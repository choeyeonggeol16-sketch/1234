package l1j.server.ItemSkill.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javolution.util.FastMap;
import l1j.server.L1DatabaseFactory;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.ItemSkill.Model.Attack.Chaotic_Magic_Weapon;
import l1j.server.ItemSkill.Model.Attack.Magic_Weapon_Curse_Poison;
import l1j.server.ItemSkill.Model.Attack.Magic_Weapon_Evil_Rivers;
import l1j.server.ItemSkill.Model.Attack.Magic_Weapon_Evil_Trick;
import l1j.server.ItemSkill.Model.Attack.Magic_Weapon_Hpdrain;
import l1j.server.ItemSkill.Model.Attack.Magic_Weapon_Disease;
import l1j.server.ItemSkill.Model.Attack.Magic_Weapon_Mp_Drain_And_Disease;
import l1j.server.ItemSkill.Model.Attack.Magic_Weapon_Pumpkin_Curse;
import l1j.server.ItemSkill.Model.Attack.Magic_Weapon_Chaser;
import l1j.server.ItemSkill.Model.Attack.Nightmare_Long_Bow;
import l1j.server.ItemSkill.Model.Attack.Wide_Area_Magic_Weapon;
import l1j.server.ItemSkill.Model.Attack.Melee_Magic_Weapon_Tun_Undead;
import l1j.server.ItemSkill.Model.Attack.Magic_Weapon_Hold;
import l1j.server.ItemSkill.Model.Attack.Melee_Magic_Waepon;
import l1j.server.ItemSkill.Model.Attack.Melee_Weapon_DiceDagger;
import l1j.server.ItemSkill.Model.Attack.Melee_Weapon_Magic_Dagger;
import l1j.server.ItemSkill.Model.Attack.Magic_Weapon_Mpdrain;
import l1j.server.ItemSkill.Model.Attack.Melee_Weapon_Silence;
import l1j.server.ItemSkill.Model.Attack.Ranged_Magic_Weapon;
import l1j.server.server.utils.SQLUtil;

public class ItemSkillModelLoader {
	private static ItemSkillModelLoader _instance;

	public static ItemSkillModelLoader getInstance() {
		if (_instance == null) {
			_instance = new ItemSkillModelLoader();
		}
		return _instance;
	}
	public static void reload() {
		ItemSkillModelLoader tmp = _instance;
		_instance = new ItemSkillModelLoader();
		if (tmp != null) {
			tmp.clear();
			tmp = null;
		}
	}
	public static void release() {
		if (_instance != null) {
			_instance.clear();
			_instance = null;
		}
	}
	private FastMap<Integer, ItemSkillModel> _models_atk;

	private ItemSkillModelLoader() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from itemskill_model");
			rs = pstm.executeQuery();
			int rows = SQLUtil.calcRows(rs);
			_models_atk = new FastMap<Integer, ItemSkillModel>(rows);
			while (rs.next()) {
				ItemSkillModel model = parseType(rs.getString("타입"));
				if (model != null) {
					model.itemId = rs.getInt("아이템_번호");
					model.d_prob = rs.getInt("기본_확률");
					model.e_prob = rs.getInt("인챈트_확률");	
					model.min_dmg = rs.getInt("최소_데미지");
					model.max_dmg = rs.getInt("최대_데미지");
					model.in_dmg = rs.getInt("고유_데미지");
					model.eff_id = rs.getInt("이펙트_번호");
					model.attr_type = rs.getInt("속성_타입");
					model.is_magic = rs.getBoolean("마법_유무");
					model.is_cri = rs.getBoolean("크리티컬_유무");
					model.is_erase = rs.getBoolean("이레이즈_매직_삭제_유무");
					model.is_counter = rs.getBoolean("카운터_매직_삭제_유무");
					model.skill_range = rs.getInt("스킬_발동_거리");
					model.effectType = rs.getInt("이팩트_타입");
					if (model.isAttack()) {				
						_models_atk.put(model.itemId, model);
					}			
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	public ItemSkillModel getAtk(int i) {
		return _models_atk.get(i);
	}

	public void clear() {
		if (_models_atk != null) {
			_models_atk.clear();
			_models_atk = null;
		}
	}
	
	private ItemSkillModel parseType(String type) {
		if (type.equalsIgnoreCase("무기-단일(근거리)")) {
			return new Melee_Magic_Waepon();
		}
		else if (type.equalsIgnoreCase("무기-단일(원거리)")) {
			return new Ranged_Magic_Weapon();
		}	
		else if (type.equalsIgnoreCase("무기-악운의 단검")) {
			return new Melee_Weapon_DiceDagger();
		}
		else if (type.equalsIgnoreCase("무기-홀드")) {
			return new Magic_Weapon_Hold();
		}
		else if (type.equalsIgnoreCase("무기-사일런스")) {
			return new Melee_Weapon_Silence();
		}
		else if (type.equalsIgnoreCase("무기-마나흡수")) {
			return new Magic_Weapon_Mpdrain();
		}
		else if (type.equalsIgnoreCase("무기-마력의 단검")) {
			return new Melee_Weapon_Magic_Dagger();
		}					
		else if (type.equalsIgnoreCase("무기-턴 언데드")) {
			return new Melee_Magic_Weapon_Tun_Undead();
		}
		else if (type.equalsIgnoreCase("무기-카오틱 추가 타격")) {
			return new Chaotic_Magic_Weapon();
		}
		else if (type.equalsIgnoreCase("무기-대미지 독")) {
			return new Magic_Weapon_Curse_Poison();
		}
		else if (type.equalsIgnoreCase("무기-체력흡수")) {
			return new Magic_Weapon_Hpdrain();
		}
		else if (type.equalsIgnoreCase("무기-펌프킨 커스")) {
			return new Magic_Weapon_Pumpkin_Curse();
		}
		else if (type.equalsIgnoreCase("무기-디지즈")) {
			return new Magic_Weapon_Disease();
		}
		else if (type.equalsIgnoreCase("무기-마나흡수(디지즈)")) {
			return new Magic_Weapon_Mp_Drain_And_Disease();
		}
		else if (type.equalsIgnoreCase("무기-광역(피격)")) {
			return new Wide_Area_Magic_Weapon();
		}
		else if(type.equalsIgnoreCase("이블_리버스")) {
			return new Magic_Weapon_Evil_Rivers();
		}
		else if(type.equalsIgnoreCase("이블_트릭")) {
			return new Magic_Weapon_Evil_Trick();
		}
		else if(type.equalsIgnoreCase("체이서")) {
			return new Magic_Weapon_Chaser();
		}
		else if(type.equalsIgnoreCase("악몽")) {
			return new Nightmare_Long_Bow();
		}
		return null;
	}
}
