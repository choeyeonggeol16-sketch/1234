package l1j.server.server.command.executor;

import java.util.StringTokenizer;
import l1j.server.Templates.L1Npc;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.SpawnTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1InsertSpawn implements L1CommandExecutor {

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
			L1Npc template = NpcTable.getInstance().getTemplate(npcId);

			if (template == null) {
				msg = "해당 NPC가 발견되지 않습니다. ";
				return;
			}
			if (type.equals("몹")) {
				if (!template.getImpl().equals("L1Monster")) {
					msg = "지정한 NPC는 L1Monster가 아닙니다. ";
					return;
				}
				SpawnTable.storeSpawn(pc, template);
			} else if (type.equals("엔")) {
				NpcSpawnTable.getInstance().storeSpawn(pc, template);
				spawngmcmd(pc, npcId);
				return;
			}
			spawn(pc, npcId);
			msg = new StringBuilder().append(template.get_name()).append(" (" + npcId + ") ").append("를 추가했습니다. ")
					.toString();
		} catch (Exception e) {
			// _log.log(Level.SEVERE, "", e);
			msg = cmdName + " [몹,엔] [NPCID] 라고 입력해 주세요. ";
		} finally {
			if (msg != null) {
				pc.sendPackets(new S_SystemMessage(msg));
			}
		}
	}

	public static void spawngmcmd(L1PcInstance pc, int npcId) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(pc.getMapId());
			npc.getLocation().set(pc.getLocation());
			// npc.getLocation(). forward(pc.getHeading());

			npc.setHomeX(pc.getX());
			npc.setHomeY(pc.getY());
			npc.setHeading(pc.getHeading());

			L1World.getInstance().storeObject(npc);
			L1World.getInstance().addVisibleObject(npc);

			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void spawn(L1PcInstance pc, int npcId) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(pc.getMapId());

			int tryCount = 0;
			do {
				tryCount++;
				npc.setX(pc.getX() + (int) (Math.random()) - (int) (Math.random()));
				npc.setY(pc.getY() + (int) (Math.random()) - (int) (Math.random()));
				if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
					break;
				}
			} while (tryCount < 50);

			if (tryCount >= 50) {
				npc.getLocation().set(pc.getLocation());
				npc.getLocation().forward(pc.getHeading());
			}

			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(pc.getHeading());

			L1World.getInstance().storeObject(npc);
			L1World.getInstance().addVisibleObject(npc);

			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
