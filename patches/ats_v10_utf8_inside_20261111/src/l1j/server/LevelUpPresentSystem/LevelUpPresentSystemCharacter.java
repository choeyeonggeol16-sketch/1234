package l1j.server.LevelUpPresentSystem;

import java.sql.Timestamp;
import java.util.HashMap;

public class LevelUpPresentSystemCharacter {
	private final int _CharId;
	private int _ItemId;
	private Timestamp _Time;

	public final HashMap<String, Integer> _present_items = new HashMap<String, Integer>();

	public LevelUpPresentSystemCharacter(int charId) {
		_CharId = charId;
	}

	public int getCharId() {
		return _CharId;
	}

	public int getItemId() {
		return _ItemId;
	}
	
	public void setItemId(int count) {
		_ItemId = count;
	}
	
	public Timestamp getPresentTime() {
		return _Time;
	}
	
	public void setPresentTime(Timestamp time) {
		_Time = time;
	}

	public boolean add(String key, int itemid, Timestamp time) {
		if (_present_items.containsKey(key)) {
			return false;
		}
		_present_items.put(key, itemid);
		setItemId(itemid);
		setPresentTime(time);
		return true;
	}
}
