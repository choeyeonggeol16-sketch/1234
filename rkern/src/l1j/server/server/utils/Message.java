package l1j.server.server.utils;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class Message {
	
	private static Message instance;
	public static Message getInstance() {
		if (instance == null) {
			instance = new Message();
		}
		return instance;
	}
	
	public void get_system_message(L1PcInstance pc, String msg) {
		StringBuilder sb = new StringBuilder();
		sb.append(msg);
		SC_SystemMessage.send(pc, sb.toString());
	}
	public void get_system_message1(L1PcInstance pc, ServerPacket packet) {
		StringBuilder sb = new StringBuilder();
		SC_SystemMessage.send(pc, sb.toString());
		if(pc != null)
			pc.sendPackets(packet);
	}
	
}