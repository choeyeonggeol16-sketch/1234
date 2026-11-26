package l1j.server.server.model.Instance;

import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
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
