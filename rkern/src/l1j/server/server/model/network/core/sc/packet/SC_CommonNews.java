package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_CommonNews {

	private static final Logger _log = Logger.getLogger(SC_CommonNews.class.getName());

    private SC_CommonNews() {}

    public static final class NoticeData {
        public final String message;
        public final String noticeId;

        private NoticeData(String message, String noticeId) {
            this.message = message;
            this.noticeId = noticeId;
        }
    }

    private static void writePayload(WriteBuffer buffer, String value) {
        buffer.writeC(Opcodes.S_NEWS);
        buffer.writeS(value);
    }

    public static boolean send(CIOClient client, String message) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        if (conn == null || !conn.isClientConnected()) return false;
        final String text = message == null ? "" : message;
        final long capacity = 4L + ((long) text.length() << 1);
        if (capacity > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            writePayload(buffer, text);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }

	/**
	 * 계정 공지사항 조회 정적 팩토리 (읽음 상태는 전송 요청 이후 갱신)
	 */
	public static NoticeData loadNotice(String account) {
		if (account == null || account.isEmpty()) {
			return null;
		}

		String date = getDate(account);
		StringBuilder sb = new StringBuilder();
		String nextDate = null;
		boolean found = false;

		String query = "SELECT id, message FROM notice WHERE id > ? ORDER BY id ASC LIMIT 1";

		try (Connection c = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement p = c.prepareStatement(query)) {

			p.setString(1, date);
			try (ResultSet r = p.executeQuery()) {
				if (r.next()) {
					found = true;
					nextDate = r.getString("id");
					String sTemp = r.getString("message");

					if (sTemp != null) {
						StringTokenizer st = new StringTokenizer(sTemp, "-");
						while (st.hasMoreTokens()) {
							sb.append(st.nextToken()).append("\n");
						}
					}
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "SC_CommonNews notice DB error - account: " + account, e);
		}

		if (found && nextDate != null) {
			return new NoticeData(sb.toString(), nextDate);
		}
		return null;
	}

	/**
	 * 계정명으로 읽어야 할 공지 갯수 리턴
	 */
	public static int NoticeCount(String account) {
		if (account == null || account.isEmpty()) {
			return 0;
		}

		int count = 0;
		String query = "SELECT COUNT(id) AS cnt FROM notice WHERE id > (SELECT notice FROM accounts WHERE login=?)";

		try (Connection c = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement p = c.prepareStatement(query)) {

			p.setString(1, account);
			try (ResultSet r = p.executeQuery()) {
				if (r.next()) {
					count = r.getInt("cnt");
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "SC_CommonNews NoticeCount error - account: " + account, e);
		}
		return count;
	}

	private static String getDate(String account) {
		String sTemp = "";
		String query = "SELECT notice FROM accounts WHERE login=?";

		try (Connection c = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement p = c.prepareStatement(query)) {

			p.setString(1, account);
			try (ResultSet r = p.executeQuery()) {
				if (r.next()) {
					sTemp = r.getString("notice");
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "SC_CommonNews getDate error - account: " + account, e);
		}
		return sTemp != null ? sTemp : "";
	}

	public static void updateNoticeDate(String account, String date) {
		String query = "UPDATE accounts SET notice=? WHERE login=?";

		try (Connection c = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement p = c.prepareStatement(query)) {

			p.setString(1, date);
			p.setString(2, account);
			p.executeUpdate();
		} catch (Exception e) {
			_log.log(Level.SEVERE, "SC_CommonNews updateNoticeDate error - account: " + account, e);
		}
	}

}