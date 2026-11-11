package l1j.server.LevelUpPresentSystem;

import java.sql.Timestamp;
import java.util.HashMap;

public class LevelUpPresentSystemAccount {
	private String _Account;
	private int _ItemId;
	private Timestamp _Time;

	public final HashMap<String, Integer> _present_items = new HashMap<String, Integer>();

	public LevelUpPresentSystemAccount(String Account) {
		_Account = Account;
	}

	public String getAccount() {
		return _Account;
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

	public boolean add(String key, int itemId, Timestamp time) {
		if (_present_items.containsKey(key)) {
			return false;
		}
		_present_items.put(key, itemId);
		setItemId(itemId);
		setPresentTime(time);
		return true;
	}
}
