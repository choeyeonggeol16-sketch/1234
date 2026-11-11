package l1j.server.Templates;

import l1j.server.server.utils.SystemUtil;

public class L1EventNpcSpawn {
	private int _npcid;
	private int[] _spawnLoc;
	private String _eventName;
	private int _keeptime;
	private int[][] time;
	private String[] yoil;
	private String _ment;
	private int[] _exitmap;
	private int[] _exitloc;
	private String[] _joinloc;
	private int _joinLevel;
	private int[] _joinItem;
	private int _joinMaxLevel;

	public int getJoinLevel() {
		return this._joinLevel;
	}

	public void setJoinLevel(int i) {
		this._joinLevel = i;
	}

	public int getNpcId() {
		return this._npcid;
	}

	public void setNpcId(int i) {
		this._npcid = i;
	}

	public String getEventName() {
		return this._eventName;
	}

	public void setEventName(String name) {
		this._eventName = name;
	}

	public String getMent() {
		return this._ment;
	}

	public void setMent(String name) {
		this._ment = name;
	}

	public int getKeepTime() {
		return this._keeptime;
	}

	public void setKeepTime(int i) {
		this._keeptime = i;
	}

	public int[] getSpawnLoc() {
		return this._spawnLoc;
	}

	public void setSpawnLoc(int[] i) {
		this._spawnLoc = i;
	}

	public int[] getExitLoc() {
		return this._exitloc;
	}

	public void setExitLoc(int[] i) {
		this._exitloc = i;
	}

	public String[] getJoinLoc() {
		return this._joinloc;
	}

	public void setJoinLoc(String[] i) {
		this._joinloc = i;
	}

	public int[] getJoinItem() {
		return this._joinItem;
	}

	public void setJoinItem(int[] i) {
		this._joinItem = i;
	}

	public int getJoinMaxLevel() {
		return this._joinMaxLevel;
	}

	public void setJoinMaxLevel(int i) {
		this._joinMaxLevel = i;
	}

	public int[] getExitMap() {
		return this._exitmap;
	}

	public void setExitMap(int[] i) {
		this._exitmap = i;
	}

	public int[][] getTime() {
		return this.time;
	}

	public void setTime(int[][] time) {
		this.time = time;
	}

	public String[] getYoil() {
		return this.yoil;
	}

	public void setYoil(String[] yoil) {
		this.yoil = yoil;
	}

	public boolean isSpawnTime(int h, int m, long current_time) {
		String now_y = SystemUtil.getYoil(System.currentTimeMillis());
		boolean isYoil = false;
		for (String y : this.yoil) {
			if ((y.equalsIgnoreCase("전체")) || (y.equalsIgnoreCase(now_y)))
				isYoil = true;
		}
		if (!isYoil) {
			return false;
		}
		for (int[] t : this.time) {
			if ((t[0] == h) && (t[1] == m)) {
				return true;
			}
		}
		return false;
	}
}