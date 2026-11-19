package l1j.server.TowerOfDominance;

import java.util.Calendar;

public class DominanceBoss {
	private int _bossnum;
	private String _bossname;
	private int _npcid;
	private int _mapx;
	private int _mapy;
	private int _mapid;
	private int[][] _bosstime;
	private boolean _isment;
	private String _ment;
	private boolean alleffect;
	private int _effid;
	
	public int getBossNum(){
		return _bossnum;
	}
	public void setBossNum(int num){
		_bossnum = num;
	}
	
	public String getBossName(){
		return _bossname;
	}
	public void setBossName(String name){
		_bossname = name;
	}
	
	public int getNpcId(){
		return _npcid;
	}
	public void setNpcId(int id){
		_npcid = id;
	}
	
	public int getMapX(){
		return _mapx;
	}
	public void setMapX(int x){
		_mapx = x;
	}
	
	public int getMapY(){
		return _mapy;
	}
	public void setMapY(int y){
		_mapy = y;
	}
	
	public int getMapId(){
		return _mapid;
	}
	public void setMapId(int mapid){
		_mapid = mapid;
	}
	
	public int[][] getBossTime(){
		return _bosstime;
	}
	public void setBossTime(int[][] time){
		_bosstime = time;
	}
	
	public boolean isMentuse(){
		return _isment;
	}
	
	public DominanceBoss setMentuse(boolean flag){
		_isment = flag;
		return this;
	}
	
	public String getMent(){
		return _ment;
	}
	
	public void setMent(String ment){
		_ment = ment;
	}
	
	public boolean isAllEffect(){
		return alleffect;
	}
	
	public DominanceBoss setAllEffect(boolean flag){
		alleffect = flag;
		return this;
	}
	
	public int getEffectNum(){
		return _effid;
	}
	
	public void setEffectNum(int num){
		_effid = num;
	}
	
	public boolean isSpawnTime(int h, int m, Calendar oCalendar){
		for(int[] t : _bosstime){
			if(t[0]==h && t[1]==m) {
				return true;
			}
		}
		return false;
	}
}