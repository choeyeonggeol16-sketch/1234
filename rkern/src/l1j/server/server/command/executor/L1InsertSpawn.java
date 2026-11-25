package l1j.server.server.command.executor;

import l1j.server.server.datatables.CObjectDB;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.datatables.CNpcMakerDB;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.L1SpawnUtil;

public class L1InsertSpawn implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1InsertSpawn.class.getName());

	private L1InsertSpawn() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1InsertSpawn();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		String msg = null;

		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String type = tok.nextToken();
			int npcId = Integer.parseInt(tok.nextToken().trim());
			L1Npc template = CObjectDB.GetNPC(npcId);

			if (template == null) {
				msg = "해당 NPC가 발견되지 않습니다. ";
				return;
			}
			if (type.equals("몹")) {
				if (!template.getImpl().equals("L1Monster")) {
					msg = "지정한 NPC는 L1Monster가 아닙니다. ";
					return;
				}
				CNpcMakerDB.storeSpawn(pc, template);
			} else if (type.equals("엔")) {
				NpcSpawnTable.getInstance().storeSpawn(pc, template);
				L1SpawnUtil.spawngmcmd(pc, npcId);
				return;
			}
			L1SpawnUtil.spawn(pc, npcId, 0, 0);
			msg = new StringBuilder().append(template.get_name()).append(" (" + npcId + ") ").append("를 추가했습니다. ").toString();
		} catch (Exception e) {
		//	_log.log(Level.SEVERE, "", e);
			msg = cmdName + " [몹,엔] [NPCID] 라고 입력해 주세요. ";
		} finally {
			if (msg != null) {
				SC_SystemMessage.send(pc, msg);
			}
		}
	}
}
