/**
 * 무인 엔피씨 상점 시작 명령어
 * by - Eva Team.
 */
package l1j.server.server.command.executor;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.NpcShopSystem;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1NpcShopSwitch implements L1CommandExecutor {
	@SuppressWarnings("unused")
	private static Logger _log = Logger.getLogger(L1NpcShopSwitch.class.getName());

	private L1NpcShopSwitch() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1NpcShopSwitch();
	}

	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			String params[] = arg.split(" ");
			int type = 0;
			String mode = null;
			try {
				type = Integer.valueOf(params[0]);
				mode = params[1];
			} catch (Exception e) {
			}

			if (params.length == 2) {
				New(pc, type, mode);
			} else {
				Old(pc, arg);
			}

		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(".영자상점 메소드오류"));
		}
	}

	private void New(L1PcInstance pc, int type, String arg) {
		boolean power = NpcShopSystem.getInstance().isPower(type);
		if (arg.equalsIgnoreCase("켬")) {
			if (power) {
				pc.sendPackets(new S_SystemMessage("이미실행중입니다."));
				return;
			} else {
				NpcShopSystem.getInstance().npcShopStart(type);
			}
		} else if (arg.equalsIgnoreCase("끔")) {
			if (!power) {
				pc.sendPackets(new S_SystemMessage("실행되지 않았습니다."));
				return;
			} else {
				NpcShopSystem.getInstance().npcShopStop(type);
			}
		} else {
			pc.sendPackets(new S_SystemMessage(".영자상점 켬/끔"));
		}
	}

	private void Old(L1PcInstance pc, String arg) {
		boolean power = NpcShopSystem.getInstance().isPower();
		if (arg.equalsIgnoreCase("켬")) {
			if (power) {
				pc.sendPackets(new S_SystemMessage("이미실행중입니다."));
				return;
			} else {
				NpcShopSystem.getInstance().npcShopStart();
			}
		} else if (arg.equalsIgnoreCase("끔")) {
			if (!power) {
				pc.sendPackets(new S_SystemMessage("실행되지 않았습니다."));
				return;
			} else {
				NpcShopSystem.getInstance().npcShopStop();
			}
		} else {
			pc.sendPackets(new S_SystemMessage(".영자상점 켬/끔"));
		}
	}
}
