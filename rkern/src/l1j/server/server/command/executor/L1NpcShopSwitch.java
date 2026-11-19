/**
 * 무인 엔피씨 상점 시작 명령어
 * by - Eva Team.
 */
package l1j.server.server.command.executor;

import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.shop.NpcShopManager;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

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
			boolean power = NpcShopManager.getInstance().isPower();
			if (arg.equalsIgnoreCase("켬")) {
				if (power) {
					SC_SystemMessage.send(pc, "이미 운영자 상점이 오픈되어 있습니다.");
					return;
				} else {
					NpcShopManager.getInstance().start();
				}
			} else if (arg.equalsIgnoreCase("끔")) {
				if (!power) {
					SC_SystemMessage.send(pc, "이미 운영자 상점이 오픈되어있지 않습니다.");
					return;
				} else {
					NpcShopManager.getInstance().stop();
				}
			} else {
				SC_SystemMessage.send(pc, ".영자상점1 켬/끔");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".영자상점1 메소드오류");
		}
	}
}
	
	