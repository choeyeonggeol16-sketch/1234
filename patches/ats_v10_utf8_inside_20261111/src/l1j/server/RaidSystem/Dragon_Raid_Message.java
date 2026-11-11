package l1j.server.RaidSystem;

import java.util.ArrayList;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_SystemMessage;

public enum Dragon_Raid_Message {
	RAID_SMSG_DUMMY("", false),
	RAID_GMSG_DUMMY("", true),
	RAID_OPEN_FAIL_INVALID_TYPE("알 수 없는 레이드 타입입니다.", false),
	RAID_OPEN_FAIL_INVALID_ITEM("아이템 요구 조건을 충족하지 않습니다.", false),
	RAID_OPEN_FAIL_SIZEOVER("현재 모든 인스턴스가 사용 중입니다.", false),
	RAID_OPEN_SUCCESS_MESSAGE("강철 길드 난쟁이: 으...드래곤의 울부짖음이 여기까지 들리오. 필시 누군가 드래곤 포탈을 연 것이 확실하오! 준비된 드래곤 슬레이어에게 영광과 축복을!", true),
	RAID_INPUT_FAIL_BEGIN("레이드가 이미 시작되어 입장할 수 없습니다.", false),
	RAID_INPUT_FAIL_OVERUSER("입장 가능 인원수를 초과하여 입장할 수 없습니다.", false),
	RAID_INPUT_FAIL_STILLBUFF("드래곤 레이드 마법으로 인해 레이드에 참가할 수 없습니다.", false),
	RAID_CLEAR_SUCCESS_MESSAGE("난쟁이의 외침 : 드래곤의 검은 숨결을 멈추게 한 용사들이 탄생 하였습니다.!!", true);
	
	private String 			_msg;
	private boolean 		_isSystemMessage;
	private S_SystemMessage _sMsg;
	private S_PacketBox		_sBox;
	Dragon_Raid_Message(String msg, boolean isSystemMessage) {
		_msg 				= msg;
		_isSystemMessage 	= isSystemMessage;
		_sMsg 				= new S_SystemMessage(msg);
		_sBox				= new S_PacketBox(S_PacketBox.GREEN_MESSAGE, msg);
	}
	
	public String get(){
		return _msg;
	}
	public void sendMessage(L1PcInstance pc) {
		if(_isSystemMessage) {
			sendSystemMessage(pc);
		}
		else {
			sendGreenMessage(pc);
		}		
	}
	public void sendSystemMessage(L1PcInstance pc) {
		pc.sendPackets(_sMsg, false);
	}
	public void sendGreenMessage(L1PcInstance pc) {
		pc.sendPackets(_sBox, false);
	}
	
	public void sendMessage(L1PcInstance pc, String[] arrs) {
		if(_isSystemMessage)
			sendSystemMessage(pc, arrs);
		else
			sendGreenMessage(pc, arrs);
	}
	public void sendSystemMessage(L1PcInstance pc, String[] arrs) {
		StringBuilder sb = new StringBuilder(_msg.length() + arrs.length * 5);
		sb.append(_msg);
		for(int i=0; i<arrs.length; i++)
			sb.append(arrs[i]);
		pc.sendPackets(new S_SystemMessage(sb.toString()), true);
	}
	public void sendGreenMessage(L1PcInstance pc, String[] arrs) {
		StringBuilder sb = new StringBuilder(_msg.length() + arrs.length * 5);
		sb.append(_msg);
		for(int i=0; i<arrs.length; i++)
			sb.append(arrs[i]);
		pc.sendPackets(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, sb.toString()), true);
	}
	
	public void sendMessage(ArrayList<L1PcInstance> pcs) {
		if(_isSystemMessage)
			sendSystemMessage(pcs);
		else
			sendGreenMessage(pcs);
	}
	public void sendSystemMessage(ArrayList<L1PcInstance> pcs) {
		int size = pcs.size();
		L1PcInstance pc;
		for(int i=0; i<size; i++) {
			pc = pcs.get(i);
			if(pc != null)
				pc.sendPackets(_sMsg, false);
		}
	}
	public void sendGreenMessage(ArrayList<L1PcInstance> pcs) {
		int size = pcs.size();
		L1PcInstance pc;
		for(int i=0; i<size; i++) {
			pc = pcs.get(i);
			if(pc != null)
				pc.sendPackets(_sBox, false);
		}
	}
}
