package l1j.server.server.command.executor;

import java.util.Collection;
import java.util.StringTokenizer;

import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Skills;

public class L1Buff implements L1CommandExecutor {

	private L1Buff() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Buff();
	}

	@Override
	public void execute(
			L1PcInstance pc,
			String cmdName,
			String arg) {

		try {
			final StringTokenizer tok =
					new StringTokenizer(arg);

			String targetType =
					tok.nextToken();

			final boolean selfOnly =
					"나".equals(targetType);

			final Collection<L1PcInstance> players;

			final String skillToken;

			if (selfOnly) {
				players = null;
				skillToken = tok.nextToken();

			} else if ("전체".equals(targetType)) {
				players =
						L1World.getInstance()
								.getAllPlayers();

				skillToken = tok.nextToken();

			} else {
				players =
						L1World.getInstance()
								.getVisiblePlayer(pc);

				skillToken = targetType;
			}

			final int skillId =
					Integer.parseInt(skillToken);

			final int time =
					tok.hasMoreTokens()
							? Integer.parseInt(tok.nextToken())
							: 0;

			final L1Skills skill =
					SkillsTable.getInstance()
							.getTemplate(skillId);

			if (skill == null) {
				SC_SystemMessage.send(pc, "존재하지 않는 스킬입니다.");

				return;
			}

			final String skillTarget =
					skill.getTarget();

			if (!"buff".equals(skillTarget)
					&& !"none".equals(skillTarget)) {

				SC_SystemMessage.send(pc, "버프계의 스킬이 아닙니다. ");

				return;
			}

			/*
			 * "나"는 Collection 만들 필요 없음.
			 */
			if (selfOnly) {
				castBuff(
						pc,
						skillId,
						time);

				return;
			}

			if (players == null || players.isEmpty()) {
				return;
			}

			for (L1PcInstance target : players) {
				if (target == null) {
					continue;
				}

				castBuff(
						target,
						skillId,
						time);
			}

		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName
							+ " [전체, 나] [스킬아이디] [시간] 라고 입력해 주세요. ");
		}
	}


	private static void castBuff(
			L1PcInstance target,
			int skillId,
			int time) {

		L1SkillUse.cast(
				target,
				skillId,
				target.getId(),
				target.getX(),
				target.getY(),
				null,
				time,
				L1SkillUse.TYPE_GMBUFF);
	}
}