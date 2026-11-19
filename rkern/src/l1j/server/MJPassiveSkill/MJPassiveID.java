package l1j.server.MJPassiveSkill;

import java.util.HashMap;

public enum MJPassiveID {
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
	MJPassiveID(int val){
		m_id = val;
	}
	
	public int toInt(){
		return m_id;
	}
	
	private static final HashMap<Integer, MJPassiveID> m_passivesId;
	static{
		m_passivesId = new HashMap<Integer, MJPassiveID>();
		MJPassiveID[] ids = MJPassiveID.values();
		for(MJPassiveID id : ids)
			m_passivesId.put(id.toInt(), id);
	}
	
	public static MJPassiveID fromInt(int passiveId){
		return m_passivesId.get(passiveId);
	}
}
