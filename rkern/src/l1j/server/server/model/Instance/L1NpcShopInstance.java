package l1j.server.server.model.Instance;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;

import l1j.server.server.templates.L1Npc;

// npc shop 추가
public class L1NpcShopInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;

	private int _state = 0;

	/**
	 * @param template
	 */
	public L1NpcShopInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		perceivedFrom.addKnownObject(this);
	
		SC_WorldPutObject.get(perceivedFrom.getNetConnection(), this);


	}

	@Override
	public void onTalkAction(L1PcInstance player) {
	}

	public int getState() {
		return _state;
	}

	public void setState(int i) {
		_state = i;
	}

}
