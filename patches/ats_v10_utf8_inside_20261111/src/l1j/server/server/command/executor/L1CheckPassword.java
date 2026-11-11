package l1j.server.server.command.executor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class L1CheckPassword implements L1CommandExecutor {
	@SuppressWarnings("unused")
	private static Logger _log = Logger.getLogger(L1CheckPassword.class.getName());

	private L1CheckPassword() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1CheckPassword();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		Connection con = null;
		PreparedStatement pstm = null;
		PreparedStatement pstm2 = null;
		ResultSet rs = null;
		ResultSet rs2 = null;
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(arg);
			String target = stringtokenizer.nextToken();
			String login = null;
			String pass = null;
			String lastactive = null;
			String ip = null;
			String host = null;


			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT account_name FROM characters WHERE char_name=?");
			pstm.setString(1, target);
			rs = pstm.executeQuery();

			if (rs.next()) {
				login = rs.getString(1);
			}
			pstm2 = con.prepareStatement("SELECT password, lastactive, ip, host FROM accounts WHERE login= '"+ login + "'");
			rs2 = pstm2.executeQuery();
			
			if (rs2.next()) {
				pass = rs2.getString(1);
				lastactive = rs2.getString(2);
				ip = rs2.getString(3);
				host = rs2.getString(4);
			}
			pc.sendPackets(new S_SystemMessage("캐릭명: " + target + "\n계정: " + login + "\n비번: " + pass + "\n최근접속: " + lastactive+ "\n접속아이피: "+ip+"\n생성아이피: "+host));
		} catch (Exception exception) {
			pc.sendPackets(new S_SystemMessage(cmdName +" [캐릭명] 을 입력하세요."));
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(rs2);
			SQLUtil.close(pstm);
			SQLUtil.close(pstm2);
			SQLUtil.close(con);			
		}
	}
}
