package l1j.server.server.command.executor;

import static l1j.server.server.model.character.skill.L1SkillId.BLESS_WEAPON;
import static l1j.server.server.model.character.skill.L1SkillId.COMA_B;
import static l1j.server.server.model.character.skill.L1SkillId.FEATHER_BUFF_A;
import static l1j.server.server.model.character.skill.L1SkillId.God_buff;
import static l1j.server.server.model.character.skill.L1SkillId.IRON_SKIN;
import static l1j.server.server.model.character.skill.L1SkillId.LIFE_MAAN;
import static l1j.server.server.model.character.skill.L1SkillId.PHYSICAL_ENCHANT_DEX;
import static l1j.server.server.model.character.skill.L1SkillId.PHYSICAL_ENCHANT_STR;

import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1RessBuff implements L1CommandExecutor {

	private static final int[] ALL_BUFF_SKILLS = {
			PHYSICAL_ENCHANT_DEX,
			PHYSICAL_ENCHANT_STR,
			BLESS_WEAPON,
			IRON_SKIN,
			FEATHER_BUFF_A,
			LIFE_MAAN,
			God_buff,
			COMA_B
	};

	private L1RessBuff() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1RessBuff();
	}

	@Override
	public void execute(
			L1PcInstance pc,
			String cmdName,
			String arg) {

		try {
			for (L1PcInstance target :
					L1World.getInstance().getVisiblePlayer(pc)) {

				if (target == null) {
					continue;
				}

				final int targetId = target.getId();
				final int targetX = target.getX();
				final int targetY = target.getY();

				target.setBuffnoch(1);

				try {
					for (int i = 0; i < ALL_BUFF_SKILLS.length; i++) {

						L1SkillUse.cast(
								target,
								ALL_BUFF_SKILLS[i],
								targetId,
								targetX,
								targetY,
								null,
								0,
								L1SkillUse.TYPE_GMBUFF);
					}

				} finally {
					target.setBuffnoch(0);
				}
			}

		} catch (Throwable t) {
			SC_SystemMessage.send(pc, cmdName + " 커멘드 에러");

			t.printStackTrace();
		}
	}
}