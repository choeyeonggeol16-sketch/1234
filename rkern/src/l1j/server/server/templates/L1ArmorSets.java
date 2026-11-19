package l1j.server.server.templates;

public class L1ArmorSets {

	public L1ArmorSets() {	}

	private int _id;

	public int getId() {  return _id;  }
	public void setId(int i) {  _id = i;  }

	//-- 추가
	private int main_id;
	public int get_main_id() {
		return main_id;
	}
	public void set_main_id(int i) {
		this.main_id = i;
	}
	
	private String _sets;
	public String getSets() {  return _sets;  }
	public void setSets(String s) {  _sets = s;  }

	private int _polyId;
	public int getPolyId() {  return _polyId;  }
	public void setPolyId(int i) {  _polyId = i;  }

	private int _ac;
	public int getAc() {  return _ac;  }
	public void setAc(int i) {  _ac = i;  }

	private int _hp;
	public int getHp() {  return _hp;  }
	public void setHp(int i) {  _hp = i;  }

	private int _mp;
	public int getMp() {  return _mp;  }
	public void setMp(int i) {  _mp = i;  }

	private int _hpr;
	public int getHpr() {  return _hpr;  }
	public void setHpr(int i) {  _hpr = i;  }

	private int _mpr;
	public int getMpr() {  return _mpr;  }
	public void setMpr(int i) {  _mpr = i;  }

	private int _mr;
	public int getMr() {  return _mr;  }
	public void setMr(int i) {  _mr = i;  }

	private int _str;
	public int getStr() {  return _str;  }
	public void setStr(int i) {  _str = i;  }

	private int _dex;
	public int getDex() {  return _dex;  }
	public void setDex(int i) {  _dex = i;  }

	private int _con;
	public int getCon() {  return _con;  }
	public void setCon(int i) {  _con = i;  }

	private int _wis;
	public int getWis() {  return _wis;  }
	public void setWis(int i) {  _wis = i;  }

	private int _cha;
	public int getCha() {  return _cha;  }
	public void setCha(int i) {  _cha = i;  }

	private int _intl;
	public int getIntl() {  return _intl;  }
	public void setIntl(int i) {  _intl = i;  }

	private int _defense_water = 0;
	public int get_defense_water() {  return _defense_water;  }
	public void set_defense_water(int i) {  _defense_water = i;  }
	
	private int _defense_earth = 0;
	public int get_defense_earth() {  return _defense_earth;  }
	public void set_defense_earth(int i) {  _defense_earth = i;  }

	private int _defense_wind = 0;
	public int get_defense_wind() {  return _defense_wind;  }
	public void set_defense_wind(int i) {  _defense_wind = i;  }

	private int _defense_fire = 0;
	public int get_defense_fire() {  return _defense_fire;  }
	public void set_defense_fire(int i) {  _defense_fire = i;  }
	
}