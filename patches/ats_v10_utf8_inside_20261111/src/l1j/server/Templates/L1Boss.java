package l1j.server.Templates;

import l1j.server.server.utils.SystemUtil;

public class L1Boss {
	private int npcid;
	private String mapname;
	private int x;
	private int y;
	private int map;
	private int rnd;
	private int[][] time;
	private int[][] real_time;
	private String monname;
	private String[] yoil;
	private boolean _ment;
	private boolean _yn;
	private int rnd_time_minute;
	private String message;
	private String ynmessage;
	private int nonespawntimernd;
	private int _spawn_rate;
	
	public int getSpawnRate() {
		return _spawn_rate;
	}
	
	public void setSpwanRate(int i) {
		_spawn_rate = i;
	}

	public int getRndMinuteTime() {
		return this.rnd_time_minute;
	}

	public void setRndMinuteTime(int i) {
		this.rnd_time_minute = i;
	}
	public int getNpcId() {
		return npcid;
	}

	public void setNpcId(int id) {
		this.npcid = id;
	}

	public String getMapName() {
		return mapname;
	}

	public void setMapName(String name) {
		this.mapname = name;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getMap() {
		return map;
	}

	public void setMap(int mapid) {
		this.map = mapid;
	}

	public int getRndLoc() {
		return rnd;
	}

	public void setRndLoc(int i) {
		this.rnd = i;
	}

	public String getMonName() {
		return monname;
	}

	public void setMonName(String mon) {
		this.monname = mon;
	}

	public int[][] getRealTime() {
		return this.real_time;
	}

	public void setRealTime(int[][] time) {
		this.real_time = time;
	}
	public int[][] getTime() {
		return time;
	}

	public void setTime(int[][] time) {
		this.time = time;
	}

	public String[] getYoil() {
		return yoil;
	}

	public void setYoil(String[] yoil) {
		this.yoil = yoil;
	}

	public boolean isMent() {
		return _ment;
	}

	public void setMent(boolean flag) {
		this._ment = flag;
	}

	public boolean isYn() {
		return _yn;
	}

	public void setYn(boolean flag) {
		this._yn = flag;
	}

	public String getMentMessage() {
		return message;
	}

	public void setMentMessage(String ment) {
		this.message = ment;
	}

	public String getYnMessage() {
		return ynmessage;
	}

	public void setYnMessage(String ment) {
		this.ynmessage = ment;
	}
	
	public int getnonespawntime() {
		return nonespawntimernd;
	}

	public void setnonespawntime(int i) {
		nonespawntimernd = i;
	}

	/**
	 * 스폰해야할 시간인지 확인해주는 함수.
	 * 
	 * @param h
	 * @param m
	 * @return
	 */
	public boolean isSpawnTime(int h, int m, long current_time) {
		String now_y = SystemUtil.getYoil(System.currentTimeMillis());
		boolean isYoil = false;
		for (String y : yoil) {
			if ((y.equalsIgnoreCase("전체")) || (y.equalsIgnoreCase(now_y)))
				isYoil = true;
		}
		if (!isYoil) {
			return false;
		}
		try {
			for (int[] t : time) {
				if ((t[0] == h) && (t[1] == m)) {
					return true;
				}
			}
		} catch (Exception e) {
			System.out.println("보스몹시간에러 : " + npcid);
		}
		return false;
	}
}

