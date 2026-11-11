package l1j.server.server.command.executor;

import java.util.StringTokenizer;

import l1j.server.server.Controller.FakeCharacterController;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1StartFakeCharacter implements L1CommandExecutor {

	private L1StartFakeCharacter() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1StartFakeCharacter();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String type = tok.nextToken();

			FakeCharacterController.getInstance().start(Integer.parseInt(type));
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(cmdName + " 1 또는 2 로 실행해주세요."));

		}
	}
}