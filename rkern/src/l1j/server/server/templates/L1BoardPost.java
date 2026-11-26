package l1j.server.server.templates;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;

public class L1BoardPost {

	private static final Logger _log = Logger.getLogger(L1BoardPost.class.getName());

	// 테이블 이름 상수로 정리
	public static final String TABLE_FREE = "board_free";
	public static final String TABLE_NOTICE = "board_notice";
	public static final String TABLE_NOTICE1 = "board_notice1";
	public static final String TABLE_NOTICE2 = "board_notice2";
	public static final String TABLE_NOTICE3 = "board_notice3";
	public static final String TABLE_PHONE = "board_posts_fix";
	public static final String TABLE_APPCENTER = "board_mjnotice";

	private static final String POSTS = "board_posts";

	// Logical board IDs preserve independent post number sequences.
	private static int boardId(String table) {
		if (TABLE_FREE.equals(table)) return 1;
		if (TABLE_NOTICE.equals(table)) return 2;
		if (TABLE_NOTICE1.equals(table)) return 3;
		if (TABLE_NOTICE2.equals(table)) return 4;
		if (TABLE_NOTICE3.equals(table)) return 5;
		if (TABLE_PHONE.equals(table)) return 6;
		if (TABLE_APPCENTER.equals(table)) return 8;
		throw new IllegalArgumentException("Unsupported board category: " + table);
	}

	private final int _id;
	private final String _name;
	private final String _date;
	private final String _title;
	private final String _content;

	public int getId() {
		return _id;
	}

	public String getName() {
		return _name;
	}

	public String getDate() {
		return _date;
	}

	public String getTitle() {
		return _title;
	}

	public String getContent() {
		return _content;
	}

	private L1BoardPost(int id, String name, String title, String content) {
		this._id = id;
		this._name = name != null ? name : "";
		this._date = today(Config.TIME_ZONE);
		this._title = title != null ? title : "";
		this._content = content != null ? content : "";
	}

	private L1BoardPost(ResultSet rs) throws SQLException {
		this._id = rs.getInt("id");
		String name = rs.getString("name");
		String date = rs.getString("date");
		String title = rs.getString("title");
		String content = rs.getString("content");

		this._name = name != null ? name : "";
		this._date = date != null ? date : "";
		this._title = title != null ? title : "";
		this._content = content != null ? content : "";
	}

	private static String today(String timeZoneID) {
		TimeZone tz = TimeZone.getTimeZone(timeZoneID);
		Calendar cal = Calendar.getInstance(tz);
		int year = cal.get(Calendar.YEAR) - 2000;
		int month = cal.get(Calendar.MONTH) + 1;
		int date = cal.get(Calendar.DATE);
		return String.format("%02d/%02d/%02d", year, month, date);
	}

	/* =========================================================================
	 *  공통 핵심 DAO
	 * ========================================================================= */

	private synchronized static L1BoardPost createPost(String table, String name, String title, String content) {
		final int board = boardId(table);
		final String selectSql = "SELECT COALESCE(MAX(id), 0) + 1 AS newid FROM " + POSTS + " WHERE board_id=?";
		final String insertSql = "INSERT INTO " + POSTS
				+ " (board_id, id, name, date, title, content) VALUES (?, ?, ?, ?, ?, ?)";
		try (Connection con = DatabaseFactory.getInstance().getConnection()) {
			int id = 1;
			try (PreparedStatement pstm = con.prepareStatement(selectSql)) {
				pstm.setInt(1, board);
				try (ResultSet rs = pstm.executeQuery()) {
					if (rs.next()) id = rs.getInt("newid");
				}
			}
			L1BoardPost topic = new L1BoardPost(id, name, title, content);
			try (PreparedStatement pstm = con.prepareStatement(insertSql)) {
				pstm.setInt(1, board);
				pstm.setInt(2, topic.getId());
				pstm.setString(3, topic.getName());
				pstm.setString(4, topic.getDate());
				pstm.setString(5, topic.getTitle());
				pstm.setString(6, topic.getContent());
				pstm.executeUpdate();
			}
			return topic;
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "L1BoardPost createPost Error - Board: " + table, e);
		}
		return null;
	}

	private static L1BoardPost findByIdGeneric(String table, int id) {
		final int board = boardId(table);
		final String sql = "SELECT id, name, date, title, content FROM " + POSTS
				+ " WHERE board_id=? AND id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, board);
			pstm.setInt(2, id);
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) return new L1BoardPost(rs);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "L1BoardPost findById Error - Board: " + table + ", id: " + id, e);
		}
		return null;
	}

	private static void deleteByIdGeneric(String table, int id) {
		final int board = boardId(table);
		final String sql = "DELETE FROM " + POSTS + " WHERE board_id=? AND id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, board);
			pstm.setInt(2, id);
			pstm.executeUpdate();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "L1BoardPost deleteById Error - Board: " + table + ", id: " + id, e);
		}
	}

	public static List<L1BoardPost> index(int id, int limit, String table) {
		final int board = boardId(table);
		final List<L1BoardPost> result = new ArrayList<L1BoardPost>();
		final String sql = id == 0
				? "SELECT id, name, date, title, content FROM " + POSTS
				  + " WHERE board_id=? ORDER BY id DESC LIMIT ?"
				: "SELECT id, name, date, title, content FROM " + POSTS
				  + " WHERE board_id=? AND id<? ORDER BY id DESC LIMIT ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, board);
			if (id == 0) {
				pstm.setInt(2, limit);
			} else {
				pstm.setInt(2, id);
				pstm.setInt(3, limit);
			}
			try (ResultSet rs = pstm.executeQuery()) {
				while (rs.next()) result.add(new L1BoardPost(rs));
			}
			return result;
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "L1BoardPost index Error - Board: " + table, e);
		}
		return Collections.emptyList();
	}

	/* =========================================================================
	 *  레거시 팩토리 / 조회 메서드
	 * ========================================================================= */

	public static L1BoardPost create(String name, String title, String content) {
		return createPost(TABLE_FREE, name, title, content);
	}

	public static L1BoardPost createGM(String name, String title, String content) {
		return createPost(TABLE_NOTICE, name, title, content);
	}

	public static L1BoardPost createGM1(String name, String title, String content) {
		return createPost(TABLE_NOTICE1, name, title, content);
	}

	public static L1BoardPost createGM2(String name, String title, String content) {
		return createPost(TABLE_NOTICE2, name, title, content);
	}

	public static L1BoardPost createGM3(String name, String title, String content) {
		return createPost(TABLE_NOTICE3, name, title, content);
	}


	public static L1BoardPost createPhone(String name, String title, String content) {
		return createPost(TABLE_PHONE, name, title, content);
	}

	public static L1BoardPost createAppCenterNotice(String name, String title, String content) {
		return createPost(TABLE_APPCENTER, name, title, content);
	}

	/* FindById 시리즈 */
	public static L1BoardPost findById(int id) {
		return findByIdGeneric(TABLE_FREE, id);
	}

	public static L1BoardPost findByIdGM(int id) {
		return findByIdGeneric(TABLE_NOTICE, id);
	}

	public static L1BoardPost findByIdGM1(int id) {
		return findByIdGeneric(TABLE_NOTICE1, id);
	}

	public static L1BoardPost findByIdGM2(int id) {
		return findByIdGeneric(TABLE_NOTICE2, id);
	}

	public static L1BoardPost findByIdGM3(int id) {
		return findByIdGeneric(TABLE_NOTICE3, id);
	}

	public static L1BoardPost findByIdPhone(int id) {
		return findByIdGeneric(TABLE_PHONE, id);
	}


	public static L1BoardPost findByIdAppCenterNotice(int id) {
		return findByIdGeneric(TABLE_APPCENTER, id);
	}

	/* Index 시리즈 */
	public static List<L1BoardPost> index(int limit) {
		return index(0, limit, TABLE_FREE);
	}

	public static List<L1BoardPost> index(int id, int limit) {
		return index(id, limit, TABLE_FREE);
	}

	public static List<L1BoardPost> indexGM(int id, int limit) {
		return index(id, limit, TABLE_NOTICE);
	}

	public static List<L1BoardPost> indexGM1(int id, int limit) {
		return index(id, limit, TABLE_NOTICE1);
	}

	public static List<L1BoardPost> indexGM2(int id, int limit) {
		return index(id, limit, TABLE_NOTICE2);
	}

	public static List<L1BoardPost> indexGM3(int id, int limit) {
		return index(id, limit, TABLE_NOTICE3);
	}

	public static List<L1BoardPost> indexPhone(int id, int limit) {
		return index(id, limit, TABLE_PHONE);
	}


	public static List<L1BoardPost> indexAppCenterNotice(int id, int limit) {
		return index(id, limit, TABLE_APPCENTER);
	}

	/* Delete 시리즈 */
	public void 자유게시판() {
		deleteByIdGeneric(TABLE_FREE, getId());
	}

	public void 서버정보() {
		deleteByIdGeneric(TABLE_NOTICE, getId());
	}

	public void 운영자1() {
		deleteByIdGeneric(TABLE_NOTICE1, getId());
	}

	public void 운영자2() {
		deleteByIdGeneric(TABLE_NOTICE2, getId());
	}

	public void 운영자3() {
		deleteByIdGeneric(TABLE_NOTICE3, getId());
	}

	public void 건의사항() {
		deleteByIdGeneric(TABLE_PHONE, getId());
	}

	public static void delData(int id, String table) {
		deleteByIdGeneric(table, id);
	}
}