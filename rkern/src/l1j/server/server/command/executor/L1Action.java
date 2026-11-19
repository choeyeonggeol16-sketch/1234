package l1j.server.server.command.executor;

import java.util.StringTokenizer;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1Action implements L1CommandExecutor {

	private L1Action() {  }

	public static L1CommandExecutor getInstance() {
		return new L1Action();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int actId = Integer.parseInt(st.nextToken(), 10);
			SC_DoActionGFX.send(pc.getNetConnection(), pc.getId(), actId);
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, cmdName + " [actid] 라고 입력해 주세요. ");
		}
	}
}
