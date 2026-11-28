package l1j.server.server.templates;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class L1Skills {

	public static final int ATTR_NONE = 0;
	public static final int ATTR_EARTH = 1;
	public static final int ATTR_FIRE = 2;
	public static final int ATTR_WATER = 4;
	public static final int ATTR_WIND = 8;
	public static final int ATTR_RAY = 16;
	public static final int TYPE_PROBABILITY = 1;
	public static final int TYPE_CHANGE = 2;
	public static final int TYPE_CURSE = 4;
	public static final int TYPE_DEATH = 8;
	public static final int TYPE_HEAL = 16;
	public static final int TYPE_RESTORE = 32;
	public static final int TYPE_ATTACK = 64;
	public static final int TYPE_OTHER = 128;
	public static final int TARGET_TO_ME = 0;
	public static final int TARGET_TO_PC = 1;
	public static final int TARGET_TO_NPC = 2;
	public static final int TARGET_TO_CLAN = 4;
	public static final int TARGET_TO_PARTY = 8;
	public static final int TARGET_TO_PET = 16;
	public static final int TARGET_TO_PLACE = 32;
	
	private final String _name;
	private final String _target;
	private final String _nameId;
	private final boolean _isThrough;
	private final int _skillId;
	private final int _skillLevel;
	private final int _skillNumber;
	private final int _damageValue;
	private final int _damageDice;
	private final int _damageDiceCount;
	private final int _probabilityValue;
	private final int _probabilityDice;
	private final int _attr;
	private final int _type;
	private final int _ranged;
	private final int _area;
	private final int _id;
	private final int _actionId;
	private final int _actionId2;
	private final int _actionId3;
	private final int _castGfx;
	private final int _castGfx2;
	private final int _castGfx3;
	private final int _sysmsgIdHappen;
	private final int _sysmsgIdStop;
	private final int _sysmsgIdFail;
	private final int _lawful;
	private final int _mpConsume;
	private final int _hpConsume;
	private final int _itmeConsumeId;
	private final int _itmeConsumeCount;
	private final int _reuseDelay;
	private final int _buffDuration;
	private final int _targetTo; // 대상 0:자신 1:PC 2:NPC 4:혈맹 8:파티 16:펫 32:장소


	public L1Skills(ResultSet row) throws SQLException {
		_skillId = row.getInt("skill_id");
		_name = row.getString("name");
		_skillLevel = row.getInt("skill_level");
		_skillNumber = row.getInt("skill_number");
		_mpConsume = row.getInt("mpConsume");
		_hpConsume = row.getInt("hpConsume");
		_itmeConsumeId = row.getInt("itemConsumeId");
		_itmeConsumeCount = row.getInt("itemConsumeCount");
		_reuseDelay = row.getInt("reuseDelay");
		_buffDuration = row.getInt("buffDuration");
		_target = row.getString("target");
		_targetTo = row.getInt("target_to");
		_damageValue = row.getInt("damage_value");
		_damageDice = row.getInt("damage_dice");
		_damageDiceCount = row.getInt("damage_dice_count");
		_probabilityValue = row.getInt("probability_value");
		_probabilityDice = row.getInt("probability_dice");
		_attr = row.getInt("attr");
		_type = row.getInt("type");
		_lawful = row.getInt("lawful");
		_ranged = row.getInt("ranged");
		_area = row.getInt("area");
		_isThrough = row.getInt("through") != 0;
		_id = row.getInt("id");
		_nameId = row.getString("nameid");
		_actionId = row.getInt("action_id");
		_actionId2 = row.getInt("action_id2");
		_actionId3 = row.getInt("action_id3");
		_castGfx = row.getInt("castgfx");
		_castGfx2 = row.getInt("castgfx2");
		_castGfx3 = row.getInt("castgfx3");
		_sysmsgIdHappen = row.getInt("sysmsgID_happen");
		_sysmsgIdStop = row.getInt("sysmsgID_stop");
		_sysmsgIdFail = row.getInt("sysmsgID_fail");
	}

	public int getSkillId() {
		return _skillId;
	}

	public String getName() {
		return _name;
	}

	public int getSkillLevel() {
		return _skillLevel;
	}

	public int getSkillNumber() {
		return _skillNumber;
	}

	public int getMpConsume() {
		return _mpConsume;
	}

	public int getHpConsume() {
		return _hpConsume;
	}

	public int getItemConsumeId() {
		return _itmeConsumeId;
	}

	public int getItemConsumeCount() {
		return _itmeConsumeCount;
	}

	public int getReuseDelay() {
		return _reuseDelay;
	}

	public int getBuffDuration() {
		return _buffDuration;
	}


	public String getTarget() {
		return _target;
	}

	public int getTargetTo() {
		return _targetTo;
	}

	public int getDamageValue() {
		return _damageValue;
	}

	public int getDamageDice() {
		return _damageDice;
	}

	public int getDamageDiceCount() {
		return _damageDiceCount;
	}

	public int getProbabilityValue() {
		return _probabilityValue;
	}

	public int getProbabilityDice() {
		return _probabilityDice;
	}

	public int getAttr() {
		return _attr;
	}

	public int getType() {
		return _type;
	}

	public int getLawful() {
		return _lawful;
	}

	public int getRanged() {
		return _ranged;
	}

	public int getArea() {
		return _area;
	}

	public boolean getIsThrough() {
		return _isThrough;
	}

	public int getId() {
		return _id;
	}

	public String getNameId() {
		return _nameId;
	}

	public int getActionId() {  return _actionId;  }
	public int getActionId2()  {  return _actionId2;  }
	public int getActionId3(){  return _actionId3;  }

	public int getCastGfx() {  return _castGfx;  }
	public int getCastGfx2()  {  return _castGfx2;  }
	public int getCastGfx3()  {  return _castGfx3;  }

	public int getSysmsgIdHappen() {
		return _sysmsgIdHappen;
	}

	public int getSysmsgIdStop() {
		return _sysmsgIdStop;
	}

	public int getSysmsgIdFail() {
		return _sysmsgIdFail;
	}
}
