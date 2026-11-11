package l1j.server.RaidSystem;

import java.sql.ResultSet;

import l1j.server.RaidSystem.Objects.Dragon_Raid_Antaras;
import l1j.server.RaidSystem.Objects.Dragon_Raid_Fafurion;
import l1j.server.server.model.Instance.L1PcInstance;

public class Dragon_Raid_Type {
	
	public static final String 	NAME 		= "raid_types";
	
	/** 레이드 ID를 나타낸다. **/
	public static final int		RID_ANTARAS 	= 1;	// 안타라스
	public static final int		RID_FAFURION	= 2;	// 파푸리온
	
	private int 	_id;		// raid id.
	private String 	_name;		// raid name.
	private int		_portalId;	// 레이드에 입장하기 위한 매개체 npc의 id.
	private int		_maxUser;	// 레이드에 입장할 수 있는 최대 인원 수.
	private int		_inputMid;	// 입장 시 이동 될 mapId
	private int		_inputX;	// 입장 시 이동 될 X좌표
	private int		_inputY;	// 입장 시 이동 될 Y좌표
	private int		_outMid;	// 아웃 시 이동 될 mapId.
	private int		_outX;		// 아웃 시 이동 될 x
	private int		_outY;		// 아웃 시 이동 될 y
	private int		_movX;		// 대기 방에서 레이드 장소로 이동 시 이동 될 X좌표
	private int		_movY;		// 대기 방에서 레이드 장소로 이동 시 이동 될 Y좌표
	private long	_raidTime;	// 레이드 시간.
	
	
	public boolean setInformation(ResultSet rs) {
		String column = null;
		try{
			column		= "id";
			_id 		= rs.getInt(column);
			
			column 		= "name";
			_name		= rs.getString(column);
			
			column		= "portal_id";
			_portalId	= rs.getInt(column);
			
			column		= "max_user";
			_maxUser	= rs.getInt(column);
			
			column		= "input_mapId";
			_inputMid	= rs.getInt(column);
			
			column		= "input_x";
			_inputX		= rs.getInt(column);
			
			column		= "input_y";
			_inputY		= rs.getInt(column);
			
			column		= "out_mapId";
			_outMid		= rs.getInt(column);
			
			column		= "out_x";
			_outX		= rs.getInt(column);
			
			column		= "out_y";
			_outY		= rs.getInt(column);
			
			column		= "mov_x";
			_movX		= rs.getInt(column);
			
			column		= "mov_y";
			_movY		= rs.getInt(column);
			
			column		= "raid_time";
			_raidTime	= rs.getLong(column);
		}catch(Exception e){
			StringBuilder sb = new StringBuilder();
			sb.append("RaidType at setInformation()...invalid column! ").append(column);
			System.out.println(sb.toString());
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public int getId(){
		return _id;
	}
	public void setId(int i){
		_id = i;
	}
	
	public String getName(){
		return _name;
	}
	public void setName(String s){
		_name = s;
	}
	
	public int getPortalId(){
		return _portalId;
	}
	
	public int getMaxUser(){
		return _maxUser;
	}
	
	public long getRaidTime(){
		return _raidTime;
	}
	
	public int getInputMapId(){
		return _inputMid;
	}
	
	public int getInputX() {
		return _inputX;
	}
	
	public int getInputY() {
		return _inputY;
	}
	
	public int getOutMapId() {
		return _outMid;
	}
	
	public int getOutX(){
		return _outX;
	}
	
	public int getOutY(){
		return _outY;
	}
	
	public void inputRaid(L1PcInstance pc, int mid) {
		pc.teleport(_inputX, _inputY, (short) mid, 5, 169, true, false);
	}
	
	public void outRaid(L1PcInstance pc) {
		pc.teleport(_outX, _outY, (short) _outMid, 5, 169, true, false);
	}
	
	public void move(L1PcInstance pc, int mid) {
		pc.teleport(_movX, _movY, (short) mid, 5, 169, true, false);
	}
	
	public void create(L1PcInstance pc) {
		Dragon_Raid_Object obj = null;
		switch(_id){
		case RID_ANTARAS:
			obj = new Dragon_Raid_Antaras(pc, this);
			break;
		case RID_FAFURION:
			obj = new Dragon_Raid_Fafurion(pc, this);
			break;
		default:
			break;
		}	
		if(obj != null) {
			Dragon_Raid_Space.getInstance().startInstance(obj);
		}		
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(128);
		sb.append("raid id : ").append(_id).append("\n");
		sb.append("raid name : ").append(_name).append("\n");
		sb.append("portal id : ").append(_portalId).append("\n");
		sb.append("max user : ").append(_maxUser).append("\n");
		sb.append("input map id : ").append(_inputMid).append("\n");
		sb.append("input X : ").append(_inputX).append("\n");
		sb.append("input Y : ").append(_inputY).append("\n");
		sb.append("out map id : ").append(_outMid).append("\n");
		sb.append("out X : ").append(_outX).append("\n");
		sb.append("out y : ").append(_outY).append("\n");
		sb.append("raid time : ").append(_raidTime).append("\n");
		return sb.toString();
	}
	
}


