package l1j.server.server.model.Instance;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionShop;
import l1j.server.server.templates.L1Npc;

public class L1NpcCashShopInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;

	private int _state = 0;
	private String _shopName;

	/**
	 * @param template
	 */
	public L1NpcCashShopInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null) return;
		
//		if(perceivedFrom.getAI() != null)
//			return;
//		
		if (SC_WorldPutObject.get(perceivedFrom.getNetConnection(), this)) {
			perceivedFrom.addKnownObject(this);
		}

		if (_state == 1)
			SC_DoActionShop.send(perceivedFrom, getId(), ActionCodes.ACTION_Shop, getShopName().getBytes());
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

	public String getShopName() {
		return _shopName;
	}

	public void setShopName(String name) {
		_shopName = name;
	}

}
