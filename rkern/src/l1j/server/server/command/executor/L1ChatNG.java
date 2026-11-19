package l1j.server.server.command.executor;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import java.util.StringTokenizer;

import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
public class L1ChatNG implements L1CommandExecutor {

	private L1ChatNG() {	}

	public static L1CommandExecutor getInstance() {
		return new L1ChatNG();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken();
			int time = Integer.parseInt(st.nextToken());
//			String reason = st.nextToken();

			L1PcInstance tg = L1World.getInstance(). getPlayer(name);

			if (tg != null) {
				tg.setSkillEffect(L1SkillId.STATUS_CHAT_PROHIBITED, time * 60 * 1000);
				SC_SkillIconGFX.send(tg, 36, time * 60);
				SC_SystemMessage.send(pc, name + " 채팅금지 : "+ String.valueOf(time) + "분 적용완료.");
//				tg.sendPackets(new SC_ServerMessage(286, String.valueOf(time))); // \f3게임에 적합하지 않는 행동이기 (위해)때문에, 향후%0분간 채팅을 금지합니다.
			} else {
				SC_SystemMessage.send(pc, "해당 캐릭터 미접속.");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭명] [분] 입력.");
		}
	}
}
