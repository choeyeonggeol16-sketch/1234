package l1j.server.server.command.executor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collection;

import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class L1GlobalBan implements L1CommandExecutor {

	public static L1CommandExecutor getInstance() {
		return new L1GlobalBan();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(arg);

			if (target != null) {
				CIOClient client = target.getNetConnection();

				if (client == null) {
					SC_SystemMessage.send(pc, "접속중이지 않은 캐릭터에 대해 조회할 수 없습니다.");
					return;
				}

				String targetIp = client.getIp();
				String cClass = getCClass(targetIp);

				Collection<L1PcInstance> pcs = L1World.getInstance().getAllPlayers();

				for (L1PcInstance otherPc : pcs) {

					if (otherPc.getNetConnection() != null) {
						String otherPcIp = otherPc.getNetConnection().getIp();

						if (cClass.equals(getCClass(otherPcIp))) {
							SC_Disconnect.send(otherPc);
							SC_SystemMessage.send(pc, "접속 중인 캐릭터 [" + otherPc.getName() + "]를 추방했습니다.");
						}
					}
				}

				banGlobalIp(targetIp);
				banGlobalAccounts(targetIp);

			} else {
				SC_SystemMessage.send(pc, "그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다. ");
			}

		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명] 으로 입력해 주세요. ");
		}
	}

	private void banGlobalIp(String ip) {
		String cClass = getCClass(ip);

		for (int i = 1; i <= 255; ++i) {
			String newIp = cClass + "." + i;

			IpTable iptable = IpTable.getInstance();

			if (!iptable.isBannedIp(newIp)) {
				iptable.banIp(newIp);
			}
		}
	}

	private void banGlobalAccounts(String ip) {
		Connection conn = null;
		PreparedStatement pstm = null;
		try {

			conn = DatabaseFactory.getInstance().getConnection();
			pstm = conn.prepareStatement("UPDATE accounts SET banned = 1 WHERE ip like CONCAT(?, '.%')");
			pstm.setString(1, getCClass(ip));
			pstm.execute();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(conn);
		}

	}

	static private String getCClass(String ip) {
		return ip.substring(0, ip.lastIndexOf('.'));
	}
}
