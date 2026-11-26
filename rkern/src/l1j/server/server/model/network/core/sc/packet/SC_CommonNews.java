package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class SC_CommonNews {

	private static final Logger _log = Logger.getLogger(SC_CommonNews.class.getName());
	private static final String NEXT_NOTICE_SQL =
			"SELECT id, message FROM notice WHERE id > "
			+ "COALESCE((SELECT notice FROM accounts WHERE login=?), ?) ORDER BY id ASC LIMIT 1";

    private SC_CommonNews() {}

    public static final class NoticeData {
        public final String message;
        public final String noticeId;

        private NoticeData(String message, String noticeId) {
            this.message = message;
            this.noticeId = noticeId;
        }
    }

    private static void writePayload(Assemble buffer, String value) {
        buffer.writeC(Opcodes.S_NEWS);
        buffer.writeS(value);
    }

    public static boolean send(CUserSocket client, String message) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client;
        if (conn == null || !conn.isClientConnected()) return false;
        final String text = message == null ? "" : message;
        final long capacity = 4L + ((long) text.length() << 1);
        if (capacity > Integer.MAX_VALUE) return false;
        final CIOFrame frame = conn.beginDirectFrame((int) capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            writePayload(buffer, text);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }

    /** Login suppresses notices when the account or its read date is missing. */
    public static NoticeData loadUnreadNotice(String account) {
        return loadNotice(account, null);
    }

    /** Preserve the direct lookup's empty-date default. */
    public static NoticeData loadNotice(String account) {
        return loadNotice(account, "");
    }

    private static NoticeData loadNotice(String account, String defaultDate) {
        if (account == null || account.isEmpty()) return null;

        NoticeData notice = null;
        try (Connection connection = DatabaseFactory.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(NEXT_NOTICE_SQL)) {
            statement.setString(1, account);
            statement.setString(2, defaultDate);
            try (ResultSet row = statement.executeQuery()) {
                if (row.next()) {
                    final String noticeId = row.getString("id");
                    if (noticeId != null) {
                        final String message = row.getString("message");
                        notice = new NoticeData(formatNotice(message), noticeId);
                    }
                }
            }
        } catch (Exception e) {
            _log.log(Level.SEVERE, "SC_CommonNews notice DB error - account: " + account, e);
        }
        return notice;
    }

    private static String formatNotice(String message) {
        if (message == null || message.isEmpty()) return "";
        final StringTokenizer tokens = new StringTokenizer(message, "-");
        final StringBuilder formatted = new StringBuilder(message.length());
        while (tokens.hasMoreTokens()) formatted.append(tokens.nextToken()).append('\n');
        return formatted.toString();
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
