package l1j.server.server.command.executor;

import java.sql.ResultSet;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.L1PcInstance;

public class NCoinCommissionReporter implements L1CommandExecutor {

	private NCoinCommissionReporter() {
	}

	public static L1CommandExecutor getInstance() {
		return new NCoinCommissionReporter();
	}

	public void execute(L1PcInstance pc, String cmdName, String arg) {
		if (pc == null || !pc.isGm())
			return;

		try {
			Selector.exec("select sum(commission_ncoin) as commission_report FROM `board_item_trade`",
					new FullSelectorHandler() {
						@Override
						public void result(ResultSet rs) throws Exception {
							if (rs.next()) {
								pc.sendPackets(String.format("\\fH현재 수수료 예상 수익 총합은 : \\fY%,d원\\fH 입니다.",
										rs.getInt("commission_report")));
							}
						}
					});
			Selector.exec("select sum(commission_ncoin) as commission_report FROM `board_item_trade` where state='완료'",
					new FullSelectorHandler() {
						@Override
						public void result(ResultSet rs) throws Exception {
							if (rs.next()) {
								pc.sendPackets(String.format("\\fH현재 수수료 완료 수익 총합은 : \\fY%,d원\\fH 입니다.",
										rs.getInt("commission_report")));
							}
						}
					});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
