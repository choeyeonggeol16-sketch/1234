package l1j.server.server.model.world.instance.lobby.compensate;

import l1j.server.server.model.Instance.L1PcInstance;

public interface LobbyCompensate {
	public void setPartition(int i);
	public int 	getPartition();
	public void setIdentity(int i);
	public void setQuantity(int i);
	public void setLevel(int i);
	public void compensate(L1PcInstance pc);
}
