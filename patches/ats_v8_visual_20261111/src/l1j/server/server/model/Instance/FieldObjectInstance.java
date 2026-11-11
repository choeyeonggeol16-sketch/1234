package l1j.server.server.model.Instance;

import l1j.server.Templates.L1Npc;
import l1j.server.server.model.L1World;
import l1j.server.server.serverpackets.S_RemoveObject;

public class FieldObjectInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;
	private int moveMapId;
	/** 이동할 맵을 설정한다. @param id */
	public void setMoveMapId(int id) {
		moveMapId = id;
	}
	public FieldObjectInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance pc) {
	}

	@Override
	public void onTalkAction(L1PcInstance pc) {
	}

	@Override
	public void deleteMe() {
		_destroyed = true;
		if (getInventory() != null) {
			getInventory().clearItems();
		}
		L1World.getInstance().removeVisibleObject(this);
		L1World.getInstance().removeObject(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
			pc.removeKnownObject(this);
			pc.sendPackets(S_RemoveObject.get(this));
		}
		removeAllKnownObjects();
	}

}
