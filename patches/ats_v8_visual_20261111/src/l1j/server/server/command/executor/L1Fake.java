package l1j.server.server.command.executor;

import java.util.StringTokenizer;

import l1j.server.server.Controller.FakeCharacterController;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1Fake implements L1CommandExecutor {

	private L1Fake() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Fake();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(arg);
			String name = stringtokenizer.nextToken();

			if (!FakeCharacterController.getInstance().createNewBot(name, pc.getX(), pc.getY(), pc.getHeading(),
					pc.getMapId(), false)) {
				pc.sendPackets(new S_SystemMessage("이미 존재하는 캐릭터 이름입니다"));
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage((new StringBuilder()).append(".호호 [캐릭이름]로 입력해 주세요. ").toString()));
		}
	}
}