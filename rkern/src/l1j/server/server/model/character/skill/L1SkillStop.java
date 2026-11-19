package l1j.server.server.model.character.skill;

import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.cooking.CookingEffectHandler;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Skills;

public final class L1SkillStop {

	private L1SkillStop() {
	}

	public static void stopSkill(L1Character cha, int skillId) {
		if (cha instanceof L1CompanionInstance npc) {
			if (npc.on_buff_stopped(skillId)) {
				return;
			}
		}

		if (cha instanceof L1PcInstance pc) {
			if (CookingEffectHandler.isCookingSkill(skillId)) {
				CookingEffectHandler.onEnd(pc, skillId);
				return;
			}
		}

		SkillStopHandlers.dispatch(cha, skillId);

		if (cha instanceof L1PcInstance pc) {
			sendStopMessage(pc, skillId);
			SC_OwnCharStatus.send(pc);
		}
	}

	private static void sendStopMessage(L1PcInstance pc, int skillId) {
		if (pc == null) {
			return;
		}

		L1Skills skills = SkillsTable.getInstance().getTemplate(skillId);
		if (skills == null) {
			return;
		}

		int msgId = skills.getSysmsgIdStop();
		if (msgId > 0) {
			SC_ServerMessage.send(pc, msgId);
		}
	}
}