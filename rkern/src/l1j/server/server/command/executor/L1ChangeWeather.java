package l1j.server.server.command.executor;

import java.util.StringTokenizer;

import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_Weather;
public class L1ChangeWeather implements L1CommandExecutor {

	private L1ChangeWeather() {	}

	public static L1CommandExecutor getInstance() {
		return new L1ChangeWeather();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			
			int weather = Integer.parseInt(tok.nextToken());
			L1World.getInstance(). setWeather(weather);
			SC_Weather.broadcastAll(weather);
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " 0~3(눈), 16~19(비)라고 입력 해주세요.");
		}
	}
}
