package l1j.server.server.command.executor;

import l1j.server.server.datatables.CObjectDB;
import java.util.StringTokenizer;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;

public class L1Summon implements L1CommandExecutor {

	private L1Summon() {
	}

	public static L1Summon getInstance() {
		return new L1Summon();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String nameid = tok.nextToken();
			int npcid = 0;
			try {
				npcid = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				npcid = CObjectDB.LoadNPC(). findNpcIdByNameWithoutSpace(
						nameid);
				if (npcid == 0) {
					SC_SystemMessage.send(pc, "해당 NPC가 발견되지 않습니다. ");
					return;
				}
			}
			int count = 1;
			if (tok.hasMoreTokens()) {
				count = Integer.parseInt(tok.nextToken());
			}
			L1Npc npc = CObjectDB.GetNPC(npcid);
			L1SummonInstance summonInst = null;
			for (int i = 0; i < count; i++) {
				summonInst = new L1SummonInstance(npc, pc);
				summonInst.setPetcost(0);
			}
			nameid = CObjectDB.GetNPC(npcid). get_name();
			SC_SystemMessage.send(pc, nameid + "(ID:" + npcid + ") ("
					+ count + ")를 소환했습니다. ");
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName
					+ " [npcid or name] [서먼수] 라고 입력해 주세요. ");
		}
	}
}
