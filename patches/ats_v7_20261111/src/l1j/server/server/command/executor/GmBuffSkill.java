package l1j.server.server.command.executor;

import java.util.StringTokenizer;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.SkillUtil;

public class GmBuffSkill implements L1CommandExecutor {

	private GmBuffSkill() {
	}

	public static L1CommandExecutor getInstance() {
		return new GmBuffSkill();
	}
	
	public void execute(L1PcInstance pc, String cmdName, String poby) {
		try {
			int buffType = 0;

			StringTokenizer token = new StringTokenizer(poby);
			String type = token.nextToken();
			if (type.equals("1")) {
				buffType = 1;
			} else if (type.equals("2")) {
				buffType = 2;
			} else {
				throw new Exception();
			}

			String range = null;
			if (token.hasMoreTokens()) {
				range = token.nextToken();
			}
			SkillUtil.startAllBuffThread(pc, range, buffType);
			
		} catch (Exception e) {
			if (pc != null) {
				pc.sendPackets(new S_SystemMessage("." + cmdName + " <종류>"));
				pc.sendPackets(new S_SystemMessage("[종류](1)-버프 (2)-코마"));
			}
		}
	}
}

