package l1j.server.server.command.executor;

import java.util.StringTokenizer;

import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.IntRange;

public class L1Level implements L1CommandExecutor {

	private L1Level() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Level();
	}

	public void execute(L1PcInstance pc, String cmdName, String poby) {
		try {
			StringTokenizer token = new StringTokenizer(poby);
			String name = token.nextToken();
			int level = Integer.parseInt(token.nextToken());

			L1PcInstance target = L1World.getInstance().getPlayer(name);
			if (target == null) {
				pc.sendPackets("해당 아이가 없습니다.");
				return;
			}

			if (!IntRange.includes(level, 1, 127)) {
				pc.sendPackets("입력한 레벨이 범위를 벗어났습니다. (1~99)");
				return;
			}
			int percent = 0;
			if (token.hasMoreTokens()) {
				percent = Integer.parseInt(token.nextToken());
			}
			if (!IntRange.includes(percent, 0, 127)) {
				pc.sendPackets("입력한 퍼센트가 범위를 벗어났습니다. (0~99)");
				return;
			}

			int exp = ExpTable.getExpByLevel(level);
			exp = (int) (exp + percent * 0.01D * ExpTable.getNeedExpNextLevel(level));
			target.setExp(exp);
			pc.sendPackets("(레벨 변경) " + target.getName() + " - Lv. " + level + " / Exp. " + percent + "%");
		} catch (Exception e) {
			// e.printStackTrace();
			pc.sendPackets("." + cmdName + " (캐릭터명) (레벨:1-127) (퍼센트:0-99)");
		}
	}
}