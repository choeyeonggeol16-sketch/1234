package l1j.server.server.command.executor;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1SpawnRobot implements L1CommandExecutor {

	private L1SpawnRobot() {}

	public static L1CommandExecutor getInstance() {
		return new L1SpawnRobot();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			SC_SystemMessage.send(pc, "비어 있음");
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [타입 : 0.제자리, 1.움직임]");
		}		
	}
}