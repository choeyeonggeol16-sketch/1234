package l1j.server.server.command.executor;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.datatables.CNpcMakerDB;
import l1j.server.server.model.CNpcMaker;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1ToSpawn implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1ToSpawn.class.getName());
	private static final Map<Integer, Integer> _spawnId = new HashMap<Integer, Integer>();

	private L1ToSpawn() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1ToSpawn();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			if (! _spawnId.containsKey(pc.getId())) {
				_spawnId.put(pc.getId(), 0);
			}
			int id = _spawnId.get(pc.getId());
			if (arg.isEmpty() || arg.equals("+")) {
				id++;
			} else if (arg.equals("-")) {
				id--;
			} else {
				StringTokenizer st = new StringTokenizer(arg);
				id = Integer.parseInt(st.nextToken());
			}
			CNpcMaker spawn = NpcSpawnTable.getInstance(). getTemplate(id);
			if (spawn == null) {
				spawn = CNpcMakerDB.getInstance(). getTemplate(id);
			}
			if (spawn != null) {
				pc.start_teleport(spawn.getLocX(), spawn.getLocY(), spawn.getMapId(), 5, 169, false, false);
				SC_SystemMessage.send(pc, "spawnid(" + id	+ ")의 원래로 납니다");
			} else {
				SC_SystemMessage.send(pc, "spawnid(" + id	+ ")(은)는 발견되지 않습니다");
			}
			_spawnId.put(pc.getId(), id);
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, cmdName + " [스폰아이디] [+,-]");
		}
	}
}
