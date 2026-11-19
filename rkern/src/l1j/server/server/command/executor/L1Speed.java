package l1j.server.server.command.executor;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1BuffUtil;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1Speed implements L1CommandExecutor {
	
	private static Logger _log = Logger.getLogger(L1Speed.class.getName());

	private L1Speed() {	}
	
	public static L1CommandExecutor getInstance() {
		return new L1Speed();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			L1BuffUtil.haste(pc, 9999 * 1000);
			L1BuffUtil.brave(pc, 9999 * 1000);
		} catch (Exception e) {
			e.printStackTrace();
			SC_SystemMessage.send(pc, ".속도 커멘드 에러");
		}
	}
}
