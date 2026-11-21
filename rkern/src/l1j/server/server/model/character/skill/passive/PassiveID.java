package l1j.server.server.model.character.skill.passive;

import java.util.HashMap;

public enum PassiveID {
	CRASH(1),
	FURY(2),
	SLAYER(3),
	ARMOR_GUARD(5),
	TITAN_ROCK(6),
	TITAN_BLITZ(7),	
	TITAN_MAGIC(8),
	COUNTER_BARRIER_VETERAN(10),
	ARMOR_BREAK_DESTINY(11),
	DOUBLE_BREAK_DESTINY(12),
	DESPERADO_ABSOLUTE(13),
	THUNDER_GRAP_BRAVE(14),
	FOU_SLAYER_BRAVE(15),
	AURAKIA(16),
	DARK_HORSE(17),
	FINAL_BURN(18),
	RESIST_ELEMENT(21),
	;
	
	private int m_id;
	PassiveID(int val){
		m_id = val;
	}
	
	public int toInt(){
		return m_id;
	}
	
	private static final HashMap<Integer, PassiveID> m_passivesId;
	static{
		m_passivesId = new HashMap<Integer, PassiveID>();
		PassiveID[] ids = PassiveID.values();
		for(PassiveID id : ids)
			m_passivesId.put(id.toInt(), id);
	}
	
	public static PassiveID fromInt(int passiveId){
		return m_passivesId.get(passiveId);
	}
}
