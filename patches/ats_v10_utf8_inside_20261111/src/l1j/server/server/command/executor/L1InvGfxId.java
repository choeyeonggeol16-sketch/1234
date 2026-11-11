package l1j.server.server.command.executor;

import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_RetrieveList;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1InvGfxId implements L1CommandExecutor {
	@SuppressWarnings("unused")
	private static Logger _log = Logger.getLogger(L1InvGfxId.class.getName());

	private L1InvGfxId() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1InvGfxId();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int gfxid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			if (count <= 250) {
				pc.sendPackets(new S_RetrieveList(pc.getId(), pc, gfxid, count));
				pc.sendPackets(new S_SystemMessage("\\aHInvGfxId " + gfxid + "~" + String.valueOf(count) + " 생성 되었습니다."));
			} else {
				pc.sendPackets("이미지는 한번에 250개 까지만 출력이 가능합니다.");
			}
		} catch (Exception exception) {
			pc.sendPackets(new S_SystemMessage(cmdName + " [id] [출현시키는 수]로 입력해 주세요. "));
		}
	}
}
