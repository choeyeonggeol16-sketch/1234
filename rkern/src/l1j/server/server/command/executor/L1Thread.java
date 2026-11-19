package l1j.server.server.command.executor;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1Thread implements L1CommandExecutor {
	private static Logger _log = Logger.getLogger(L1Burf.class.getName());

	private L1Thread() {}

	public static L1CommandExecutor getInstance() {
		return new L1Thread();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			Thread[] th = new Thread[Thread.activeCount()];
			Thread.enumerate(th); 
			for(int i=0; i<th.length; i++){
			SC_SystemMessage.send(pc, "["+i+"] 사용 쓰레드 : [" + th[i]+"]");
			}
			SC_SystemMessage.send(pc, "현재 사용되는 쓰레드 갯수 : ["+Thread.activeCount()+"]");

		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + "갯수 라고 입력 바랍니다.");
		}
	}
}
