package l1j.server.MJExpAmpSystem;

import l1j.server.server.model.Instance.L1PcInstance;

public class MJExpAmplifier {
	private int		_id;
	private int 	_mapid;
	private int		_msgid;
	private double 	_magnifier;
	
	public boolean equals(MJExpAmplifier amp){
		return amp != null && _id == amp.getId();
	}
	
	public void setId(int i){
		_id = i;
	}
	public int getId(){
		return _id;
	}
	
	public int getMapId(){
		return _mapid;
	}
	public void setMapId(int i){
		_mapid = i;
	}
	
	public int getMessageId(){
		return _msgid;
	}
	public void setMessageId(int i){
		_msgid = i;
	}
	
	public double getMagnifier(){
		return _magnifier;
	}
	public void setMagnifier(double i){
		_magnifier = i;
	}
	public void setMagnifier(int i){
		_magnifier = i * 0.01;
	}
	
	public double calc(double d){
		double td = _magnifier * d;
		return _magnifier + td;
	}
	
	public boolean isIn(L1PcInstance pc){
		return pc.getMap().getBaseMapId() == _mapid;
	}
}
