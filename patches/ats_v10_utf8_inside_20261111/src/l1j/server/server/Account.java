/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.Template.SqlHelper.Executors.Updator;
import l1j.server.Template.SqlHelper.Handler.Handler;
import l1j.server.configure.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.IntRange;
import l1j.server.server.utils.SQLUtil;

/**
 * 로그인을 위한 여러가지 인터페이스를 제공한다.
 */
public class Account {
	/** 어카운트명. */
	private String _name;

	/** 접속처의 IP주소. */
	private String _ip;

	/** 패스워드(암호화되고 있다). */
	private String _password;

	/** 최종 액티브일. */
	private Timestamp _lastActive;

	/** 액세스 레벨(GM인가?). */
	private int _accessLevel;

	/** 접속처의 호스트명. */
	private String _host;

	/** 액세스 금지의 유무(True로 금지). */
	private boolean _banned;

	/** 어카운트가 유효한가 아닌가(True로 유효). */
	private boolean _isValid = false;
	
	/** 캐릭터 슬롯(태고의옥쇄) */
	private int _charslot;
	
	private String _mjct_char_name;
	
	/** 메세지 로그용. */
	private static Logger _log = Logger.getLogger(Account.class.getName());
 

	/**
	 * 어카운트를 신규 작성한다.
	 * @param name        어카운트명
	 * @param rawPassword 평문패스워드
	 * @param ip          접속처의 IP주소
	 * @param host        접속처의 호스트명
	 * @return Account
	 */
	public static Account create(final String name, final String rawPassword, final String ip, final String host) {
		Calendar cal = Calendar.getInstance();
		int 시간 = Calendar.HOUR;
		int 분 = Calendar.MINUTE;
		/** 0 오전 , 1 오후 * */
		String 오전오후 = "오후";
		if (cal.get(Calendar.AM_PM) == 0) {
			오전오후 = "오전";
		}
		Connection con = null;
		PreparedStatement pstm = null;
		try {

			Account account = new Account();
			account._name = name;
			account._password = rawPassword;		
			account._ip = ip;
			account._host = host;
			account._banned = false;
			account._lastActive = new Timestamp(System.currentTimeMillis());

			con = L1DatabaseFactory.getInstance().getConnection();
			String sqlstr = "INSERT INTO accounts SET login=?,password=?,lastactive=?,access_level=?,ip=?,host=?,banned=?,charslot=?,mjct_charname=?";
			pstm = con.prepareStatement(sqlstr);
			pstm.setString(1, account._name);
			pstm.setString(2, account._password);
			pstm.setTimestamp(3, account._lastActive);
			pstm.setInt(4, 0);
			pstm.setString(5, account._ip);
			pstm.setString(6, account._host);
			pstm.setInt(7, account._banned ? 1 : 0);
			pstm.setInt(8, 6); 
			pstm.setString(9, "");
			pstm.execute();
			System.out.println("" + 오전오후 + " " + cal.get(시간) + "시" + cal.get(분) + "분" + "   ■ 신규 계정: [" + name + "] 생성완료■");
			return account;
		} catch (SQLException e) {
			System.out.println(e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return null;
	}

	/**
	 * 어카운트 정보를 DB로부터 추출한다.
	 *
	 * @param name 어카운트명
	 * @return Account
	 */
	public static Account load(final String name) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;

		Account account = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			String sqlstr = "SELECT * FROM accounts WHERE login=? LIMIT 1";
			pstm = con.prepareStatement(sqlstr);
			pstm.setString(1, name);
			rs = pstm.executeQuery();
			if (!rs.next()) {
				return null;
			}
			account = new Account();
			account._name = rs.getString("login");
			account._password = rs.getString("password");
			account._lastActive = rs.getTimestamp("lastactive");
			account._accessLevel = rs.getInt("access_level");
			account._ip = rs.getString("ip");
			account._host = rs.getString("host");
			account._banned = rs.getInt("banned") == 0 ? false : true;
			account._charslot = rs.getInt("charslot");
			account._mjct_char_name = rs.getString("mjct_charname");
			account._blessOfAin = rs.getInt("bless_of_ain");

			_log.fine("account exists");
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}

		return account;
	}

	/**
	 * 최종 로그인일을 DB에 반영한다.
	 *
	 * @param account 어카운트
	 */
	public static void updateLastActive(final Account account) {
		Connection con = null;
		PreparedStatement pstm = null;
		Timestamp ts = new Timestamp(System.currentTimeMillis());

		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			String sqlstr = "UPDATE accounts SET lastactive=? WHERE login = ?";
			pstm = con.prepareStatement(sqlstr);
			pstm.setTimestamp(1, ts);
			pstm.setString(2, account.getName());
			pstm.execute();
			account._lastActive = ts;
			_log.fine("update lastactive for " + account.getName());
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	/**
	 * 캐릭터 소유수를 카운트 한다.
	 *
	 * @return int
	 */
	public int countCharacters() {
		int result = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			String sqlstr = "SELECT count(*) as cnt FROM characters WHERE account_name=?";
			pstm = con.prepareStatement(sqlstr);
			pstm.setString(1, _name);
			rs = pstm.executeQuery();
			if (rs.next()) {
				result = rs.getInt("cnt");
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return result;
	}

	/**
	 * 어카운트를 무효로 한다.
	 *
	 * @param login 어카운트명
	 */
	public static void ban(final String login) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			String sqlstr = "UPDATE accounts SET banned=1 WHERE login=?";
			pstm = con.prepareStatement(sqlstr);
			pstm.setString(1, login);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	/**
	 * 입력된 패스워드와 DB상의 패스워드를 조합한다.
	 *
	 * @param rawPassword 평문패스워드
	 * @return boolean
	 */
	public boolean validatePassword(String accountName, final String rawPassword) {
		try {
			_isValid = _password.equals(rawPassword); // (_password.equals(rawPassword) || checkPassword(accountName, _password, rawPassword));
			return _isValid;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * 어카운트가 유효한가 어떤가를 돌려준다(True로 유효).
	 *
	 * @return boolean
	 */
	public boolean isValid() {
		return _isValid;
	}

	/**
	 * 어카운트가 게임 마스터인가 어떤가 돌려준다(True로 게임 마스터).
	 *
	 * @return boolean
	 */
	public boolean isGameMaster() {
		return 0 < _accessLevel;
	}

	/**
	 * 어카운트명을 취득한다.
	 *
	 * @return String
	 */
	public String getName() {
		return _name;
	}
	public String get_Password() {
		return _password;
	}
	/**
	 * 접속처의 IP주소를 취득한다.
	 *
	 * @return String
	 */
	public String getIp() {
		return _ip;
	}

	/**
	 * 최종 로그인일을 취득한다.
	 */
	public Timestamp getLastActive() {
		return _lastActive;
	}

	/**
	 * 액세스 레벨을 취득한다.
	 *
	 * @return int
	 */
	public int getAccessLevel() {
		return _accessLevel;
	}

	/**
	 * 호스트명을 취득한다.
	 *
	 * @return String
	 */
	public String getHost() {
		return _host;
	}

	/**
	 * 액세스 금지 정보를 취득한다.
	 *
	 * @return boolean
	 */
	public boolean isBanned() {
		return _banned;
	}
	
	public int getCharSlot() {
		return _charslot;
	}	

//## A62 IP당 계정 생성 제한 
	public static boolean Check_LoginIP(String ip) {
		int num = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT count(ip) as cnt FROM accounts WHERE ip=?");
			pstm.setString(1, ip);
			rs = pstm.executeQuery();
			if (rs.next()) {
				num = rs.getInt("cnt");
			}
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
			if (num < Config.ACCOUNT_LIMIT) {
				return false;
			} else {
				return true;
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return false;
	}
//## A62 IP당 계정 생성 제한 
	
	public void setCharSlot(GameClient client, int i) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			String sqlstr = "UPDATE accounts SET charslot=? WHERE login = ?";
			pstm = con.prepareStatement(sqlstr);
			pstm.setInt(1, i);
			pstm.setString(2, client.getAccount().getName());
			pstm.execute();
			client.getAccount()._charslot = i;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	

	public String getMJCT_Char_Name() {
		return _mjct_char_name;
	}
	
	public void setMJCT_Char_Name(String value) {
		_mjct_char_name = value; 
		Updator.exec(
				"UPDATE accounts SET mjct_charname=? WHERE login=?",
				new Handler() {
					@Override
					public void handle(PreparedStatement pstm) throws Exception {
						pstm.setString(1, value);
						pstm.setString(2, getName());
					}
				});
	}
	
	public static boolean checkLoginBanIP(String ip) {
		int num = 0;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;

		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT count(ip) as cnt FROM ban_ip WHERE ip=?");

			pstm.setString(1, ip);
			rs = pstm.executeQuery();

			if (rs.next()) {
				num = rs.getInt("cnt");
			}

			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);

			// Ban IP가 1개 이상인경우
			if (num >= 1) {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return false;
	}
	
	private int _blessOfAin;
	
	public int getBlessOfAin() {
		return _blessOfAin;
	}

	public void setBlessOfAin(int i) {
		_blessOfAin = i;
	}

	public void addBlessOfAin(int i) {
		_blessOfAin = IntRange.ensure(_blessOfAin + i, 0, 15000000);
	}
	
	public void addBlessOfAin(L1PcInstance pc, int i) {
		this._blessOfAin = IntRange.ensure(_blessOfAin + i, 0, 15000000);

	}

	public void updateBlessOfAin() {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			String sqlstr = "UPDATE accounts SET bless_of_ain=? WHERE login = ?";
			pstm = con.prepareStatement(sqlstr);
			pstm.setInt(1, _blessOfAin);
			pstm.setString(2, getName());
			pstm.execute();
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
}
