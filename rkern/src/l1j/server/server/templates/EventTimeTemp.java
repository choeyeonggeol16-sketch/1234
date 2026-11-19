package l1j.server.server.templates;

import l1j.server.server.utils.CommonUtil;

public class EventTimeTemp {

	private int type;

	public int get_type() {
		return type;
	}

	public void set_type(int i) {
		this.type = i;
	}

	private boolean _yn;
	private int npcid;

	public int get_npcid() {
		return npcid;
	}

	public void set_npcid(int i) {
		this.npcid = i;
	}

	private String boss_message;

	public String get_boss_message() {
		return boss_message;
	}

	public void set_boss_message(String s) {
		this.boss_message = s;
	}

	private int loc_x;

	public int get_loc_x() {
		return loc_x;
	}

	public void set_loc_x(int i) {
		this.loc_x = i;
	}

	public boolean isYn() {
		return _yn;
	}

	private String ynmessage;

	public String getYnMessage() {
		return ynmessage;
	}

	public void setYnMessage(String ment) {
		ynmessage = ment;
	}

	public void setYn(boolean flag) {
		_yn = flag;
	}

	private int loc_y;

	public int get_loc_y() {
		return loc_y;
	}

	public void set_loc_y(int i) {
		this.loc_y = i;
	}

	private int loc_map;

	public int get_loc_map() {
		return loc_map;
	}

	public void set_loc_map(int i) {
		this.loc_map = i;
	}

	private int hour;

	public int get_hour() {
		return hour;
	}

	public void set_hour(int i) {
		this.hour = CommonUtil.get_current(i, 0, 24);
	}

	private int minute;

	public int get_minute() {
		return minute;
	}

	public void set_minute(int i) {
		this.minute = CommonUtil.get_current(i, 0, 60);
	}

}