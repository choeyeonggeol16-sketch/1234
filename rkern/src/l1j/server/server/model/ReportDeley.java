package l1j.server.server.model;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;

public class ReportDeley implements Runnable {
	private static Logger _log = Logger.getLogger(ReportDeley.class
			.getName());

	private final L1PcInstance _pc;

	public ReportDeley(L1PcInstance pc) {
		_pc = pc;
	}

	@Override
	public void run() {
		try {
			_pc.completeReportDeley(this);
		} catch (Throwable e) {
			_log.log(Level.WARNING, e.getLocalizedMessage(), e);
		}
	}

}







