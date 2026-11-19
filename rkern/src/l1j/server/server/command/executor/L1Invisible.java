package l1j.server.server.command.executor;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.network.core.sc.packet.SC_Invis;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1Invisible implements L1CommandExecutor {

	private L1Invisible() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Invisible();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {

			if (!pc.hasSkillEffect(L1SkillId.INVISIBILITY)) {
				pc.setGmInvis(true);
				SC_Invis.send(pc, pc.getId(), 1);
				SC_Invis.broadcast(pc, pc.getId(), 1);

				SC_RemoveObject.broadcast(pc);

				pc.setSkillEffect(L1SkillId.INVISIBILITY, 0);
			} else {
				pc.setGmInvis(false);
				pc.killSkillEffectTimer(L1SkillId.INVISIBILITY);
				SC_Invis.send(pc, pc.getId(), 0);

				SC_WorldPutObject.broadcastPc(pc);
			}

		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " 커멘드 에러");
		}
	}
}
