package l1j.server.server.model.Instance;

import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Npc;

public class L1FieldObjectInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;
	private int moveMapId;

	public L1FieldObjectInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance pc) {  }

	@Override
	public void onTalkAction(L1PcInstance pc) {
		int npcid = getNpcTemplate().get_npcId();

		switch(npcid){

		case 7210011: //발라카스의 안식처
			telValakasRoom(pc); 
			break;
		default: 
			break; 
		}
	}

	/** 지정된 맵의 32명이 넘는지 체크해서 텔시킨다 @param pc @param mapid 
	 * 	1626: 드래곤의 혈흔이 온 몸에서 풍겨집니다. 혈흔의 냄새가 사라질 때까지 드래곤 포탈에 입장 할 수 없습니다.*/
	private void DragonRaidMap(L1PcInstance pc, int mapid){
		int count = 0;
		for(L1PcInstance player : L1World.getInstance().getAllPlayers()){
			if(player == null)
				continue;
			if(player.getMapId() == mapid){
				count += 1;
				if(count > 31)
					return;
			}
		}

		switch(getNpcTemplate().get_npcId()) {
		case 900007:
			if (pc.hasSkillEffect(L1SkillId.ANTA_BUFF) || pc.hasSkillEffect(L1SkillId.FAFU_BUFF) || pc.hasSkillEffect(L1SkillId.RIND_BUFF)){
				SC_ServerMessage.send(pc, 1626); 
				return; 
			} 
//			if(AntarasRaidSystem.getInstance().getAR(mapid).isAntaras()){
//				pc.sendPackets(new SC_ServerMessage(1537));// 드래곤이 깨서 진입 못한다
//				return;
//			} else {
				SC_MessageYN.send(pc, 2923, "");
				pc.DragonPortalLoc[0] = 32600;
				pc.DragonPortalLoc[1] = 32741;
				pc.DragonPortalLoc[2] = mapid;
//			}
			break;
		case 900036:
			if (pc.hasSkillEffect(L1SkillId.ANTA_BUFF) || pc.hasSkillEffect(L1SkillId.FAFU_BUFF) || pc.hasSkillEffect(L1SkillId.RIND_BUFF)){
				SC_ServerMessage.send(pc, 1626); 
				return; 
			} 
//			if(FafurionRaidSystem.getInstance().getAR(mapid).isFafurion()){
//				pc.sendPackets(new SC_ServerMessage(1537));// 드래곤이 깨서 진입 못한다
//				return;
//			} else {
				SC_MessageYN.send(pc, 2923, "");
				pc.DragonPortalLoc[0] = 32976;
				pc.DragonPortalLoc[1] = 32743;
				pc.DragonPortalLoc[2] = mapid;
//			}
			break;
		}
	}
	
	private void telValakasRoom(L1PcInstance pc) {
		pc.start_teleport(32833, 32757, getMapId(), 5, 169, false, false);
		pc.isInValakasBoss = true;
	}
	/** 이동할 맵을 설정한다. @param id */
	public void setMoveMapId(int id){ moveMapId = id; }

	@Override
	public void deleteMe() {
		_destroyed = true;
		if (getInventory() != null) {
			getInventory().clearItems();
		}
		L1World.getInstance().removeVisibleObject(this);
		L1World.getInstance().removeObject(this);
		try {
		try (var sharedRemove = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
		        SC_RemoveObject.nativeWriter(getId()), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			if (pc == null) {
				continue;
			}

			pc.removeKnownObject(this);

			l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(sharedRemove, pc);
		}
		}
		} finally {
			removeAllKnownObjects();
		}
	}
}
