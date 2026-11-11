package l1j.server.server.command.executor;

import java.util.StringTokenizer;

import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.datatables.SpawnTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class L1NpcDown implements L1CommandExecutor {

	public static L1CommandExecutor getInstance() {
		return new L1NpcDown();
	}

	public void execute(L1PcInstance pc, String cmdName, String poby) {
		try {
			StringTokenizer token = new StringTokenizer(poby);
			String type = token.nextToken();
			String spawn = token.nextToken();

			if (type.equals("¸÷")) {
				if (spawn.equals("1")) {
					SpawnTable.getInstance().reload();
				} else if (spawn.equals("2")) {
					for (L1Object obj : L1World.getInstance().getObject()) {
						if ((obj != null) && ((obj instanceof MonsterInstance))) {
							MonsterInstance mon = (MonsterInstance) obj;
							mon.setRespawn(false);
							mon.re(); 
						}
					}
				} else {
					throw new Exception();
				}
			} else if (type.equals("¿£")) {
				if (spawn.equals("1")) {
					NpcSpawnTable.getInstance().reload();
					
				} else if (spawn.equals("2")) {
					for (L1Object npc : L1World.getInstance().getObject()) {
						if ((npc != null) && !(npc instanceof MonsterInstance)) {
							if (npc instanceof L1NpcInstance) {
								((L1NpcInstance) npc).setRespawn(false);
								((L1NpcInstance) npc).deleteMe(); 
							}
						}
					}

				} else {
					throw new Exception();
				}
			} else
				throw new Exception();
		} catch (Exception e) {
			pc.sendPackets("." + cmdName + " ¿£/¸÷ (¹èÄ¡:1/»èÁ¦:2)");
		}
	}
}