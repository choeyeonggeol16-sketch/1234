package l1j.server.server.command.executor;

import java.util.logging.Logger;

import l1j.server.server.GameServer;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1SKick implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1SKick.class.getName());

	private L1SKick() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1SKick();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(arg);

			IpTable iptable = IpTable.getInstance();
			if (target != null) {
				if( target.getNetConnection() != null ){
					iptable.banIp(target.getNetConnection().getIp());
				}
				SC_SystemMessage.send(pc, (new StringBuilder()).append(target.getName()).append(" 를 강력 추방 했습니다. ").toString());
				target.setX(33080);
				target.setY(33392);
				target.setMap((short) 4);
				GameServer.disconnectChar(target);
				SC_Disconnect.send(target);
			} else {
				SC_SystemMessage.send(pc, "그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다. ");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명]으로 입력해 주세요. ");
		}
	}
}