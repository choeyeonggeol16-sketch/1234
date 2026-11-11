package l1j.server.server.command.executor;

import java.util.StringTokenizer;

import l1j.server.server.Controller.FakeCharacterController;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1KickFakeCharacter implements L1CommandExecutor {

	private L1KickFakeCharacter() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1KickFakeCharacter();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(arg);
			String name = stringtokenizer.nextToken();

			L1PcInstance target = L1World.getInstance().getPlayer(name);

			if (target == null) {
				pc.sendPackets(new S_SystemMessage(name + "는 존재하지 않는 캐릭터 명입니다."));
				return;
			}

			if (!target.getFake()) {
				pc.sendPackets(new S_SystemMessage(name + "은 허상 캐릭이 아닙니다."));
				return;
			}

			FakeCharacterController.getInstance().deleteBot(name);
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage((new StringBuilder()).append(".호호종료 [캐릭이름]로 입력해 주세요. ").toString()));
		}
	}
}