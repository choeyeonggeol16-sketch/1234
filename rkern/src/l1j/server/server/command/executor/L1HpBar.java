package l1j.server.server.command.executor;

import java.util.logging.Logger;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import l1j.server.server.model.network.core.sc.packet.SC_HPMeter;
public class L1HpBar implements L1CommandExecutor {

	private static Logger _log =
			Logger.getLogger(L1HpBar.class.getName());

	private L1HpBar() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1HpBar();
	}

	@Override
	public void execute(
			L1PcInstance pc,
			String cmdName,
			String arg) {

		if (arg.equalsIgnoreCase("켬")) {

			pc.setSkillEffect(
					L1SkillId.GMSTATUS_HPBAR,
					0);

			/*
			 * HP BAR 전용 300ms wheel task 시작.
			 * 일반 PC에는 task 자체가 존재하지 않는다.
			 */
			CIOThreadCallback.getInstance().startHpBar(pc);

			/*
			 * 켜는 순간에는 다음 300ms를 기다리지 않고
			 * 현재 화면의 HP BAR를 즉시 한 번 보낸다.
			 */
			for (L1Object obj : pc.getKnownObjects()) {
				if (isHpBarTarget(obj)) {
					SC_HPMeter.send(pc, (L1Character) obj);
				}
			}

		} else if (arg.equalsIgnoreCase("끔")) {

			/*
			 * 먼저 recurring task를 끊는다.
			 * 이후 HP BAR 제거 패킷을 보낸 뒤
			 * 다음 tick에서 다시 살아나는 것을 막는다.
			 */
			CIOThreadCallback.getInstance().stopHpBar(pc);

			pc.removeSkillEffect(
					L1SkillId.GMSTATUS_HPBAR);

			/*
			 * 현재 클라이언트에 남아 있는 HP BAR 즉시 삭제.
			 */
			for (L1Object obj : pc.getKnownObjects()) {
				if (isHpBarTarget(obj)) {
					SC_HPMeter.send(pc, obj.getId(), 0xFF, 0xFF);
				}
			}

		} else {
			SC_SystemMessage.send(pc, cmdName
							+ " [켬,끔] 라고 입력해 주세요. ");
		}
	}

	public static boolean isHpBarTarget(L1Object obj) {
		if (obj instanceof L1MonsterInstance) {
			return true;
		}
		if (obj instanceof L1PcInstance) {
			return true;
		}
		if (obj instanceof L1SummonInstance) {
			return true;
		}
		if (obj instanceof L1PetInstance) {
			return true;
		}
		if (obj instanceof L1CompanionInstance) {
			return true;
		}

		return false;
	}
}