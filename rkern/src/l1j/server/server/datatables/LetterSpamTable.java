
package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.Server;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public class LetterSpamTable {
	private static final Logger _log = Logger.getLogger(LetterSpamTable.class.getName());
	private volatile static LetterSpamTable uniqueInstance = null;
	
	public LetterSpamTable() {
	}

	public static LetterSpamTable getInstance() {
		if (uniqueInstance == null) {
			synchronized (Server.class) {
				if (uniqueInstance == null) {
					uniqueInstance = new LetterSpamTable();
				}
			}
		}
		return uniqueInstance;
	}

	/**
	 * 편지를 보낼 수 있는지 체크한다. (차단되어 있으면 false)
	 */
	public boolean spamLetterCheck(String senderName, String receiverName) {
		String sql = "SELECT 1 FROM letter_spam WHERE name = ? AND spamname = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setString(1, receiverName);
			pstm.setString(2, senderName);
			
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					return false; // 차단됨
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "LetterSpamTable spamLetterCheck error", e);
		}
		return true;
	}

	/**
	 * 편지 차단자를 저장한다.
	 */
	public void spamLetterAdd(L1PcInstance pc, String excludeName) {
		String countSql = "SELECT COUNT(*) AS cnt FROM letter_spam WHERE name = ?";
		String insertSql = "INSERT INTO letter_spam (name, spamname) VALUES (?, ?)";

		try (Connection con = DatabaseFactory.getInstance().getConnection()) {
			
			// 개인별 차단 목록 수 확인 (전역 MAX 대신 캐릭터 기준 COUNT로 정확하게 제한)
			int count = 0;
			try (PreparedStatement pstm = con.prepareStatement(countSql)) {
				pstm.setString(1, pc.getName());
				try (ResultSet rs = pstm.executeQuery()) {
					if (rs.next()) {
						count = rs.getInt("cnt");
					}
				}
			}

			if (count >= 50) {
				SC_ServerMessage.send(pc, 472); // 차단된 사용자가 너무 많습니다.
				return;
			}
			
			// 차단 등록 
			try (PreparedStatement pstm = con.prepareStatement(insertSql)) {
				pstm.setString(1, pc.getName());
				pstm.setString(2, excludeName);
				pstm.execute();
			}
			
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "LetterSpamTable spamLetterAdd error", e);
		}
	}
	
	/**
	 * 편지 차단자를 삭제한다.
	 */
	public void spamLetterDel(L1PcInstance pc, String excludeName) {
		String sql = "DELETE FROM letter_spam WHERE name = ? AND spamname = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setString(1, pc.getName());
			pstm.setString(2, excludeName);
			pstm.execute();
			
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "LetterSpamTable spamLetterDel error", e);
		}
	}
	
	/**
	 * 내 차단목록에 상대방이 있는지 체크한다.
	 */
	public boolean spamList(String pcName, String spamname) {
		String sql = "SELECT 1 FROM letter_spam WHERE name = ? AND spamname = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setString(1, pcName);
			pstm.setString(2, spamname);
			
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					return true;
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "LetterSpamTable spamList error", e);
		}
		return false;
	}
}