package l1j.server.Template.PacketHelper;

import l1j.server.server.clientpackets.ClientBasePacket;
import l1j.server.server.model.Instance.L1PcInstance;

public interface PacketParser {
	public void parse(L1PcInstance owner, ClientBasePacket pck);
	public void doWork();
}
